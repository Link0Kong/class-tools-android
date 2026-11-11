package com.furina.classtools.data.diag

/**
 * 诊断 v2.1 的纯逻辑层（可单测，不依赖 Compose/Android）：
 *
 * 1. [DiagAnalysisIndex]：把 `diag_analysis` 按 `related.health_ids` / `related.checks` 下挂到
 *    服务健康行/设备检查行；无法归因的进 `others`；`repair_tasks` 按 analysis 关联，历史无主修复单列。
 * 2. [DiagUiText]：折叠行标题截断、状态胶囊/可自动修复徽标/排队文案、`run` 步文案、顶部统计。
 * 3. [DiagPolling]：诊断进展轮询（契约 v2.1：页面可见时 10s，进入/恢复前台立即拉，隐藏停止）。
 */
data class DiagAnalysisIndex(
    /** 服务健康行 id → 挂在它下面的 AI 结论。 */
    val byHealthId: Map<String, List<DiagAnalysis>> = emptyMap(),
    /** 设备检查 id → 挂在它下面的 AI 结论。 */
    val byCheckId: Map<String, List<DiagAnalysis>> = emptyMap(),
    /** 无关联（旧数据/无法归因）的 AI 结论 →「其它诊断」折叠区。 */
    val others: List<DiagAnalysis> = emptyList(),
    /** analysis.id → 关联 repair（related.repair_id 优先，其次 repair.analysis_id）。 */
    val repairByAnalysis: Map<String, RepairTask> = emptyMap(),
    /** 没有 analysis 关联的历史修复记录（放「其它诊断」折叠区）。 */
    val orphanRepairs: List<RepairTask> = emptyList(),
) {
    fun analysesForHealth(healthId: String): List<DiagAnalysis> = byHealthId[healthId].orEmpty()

    fun analysesForCheck(checkId: String): List<DiagAnalysis> = byCheckId[checkId].orEmpty()

    fun repairFor(analysis: DiagAnalysis): RepairTask? = repairByAnalysis[analysis.id]

    /** 「其它诊断」折叠区总条数（AI 结论 + 历史无主修复）。 */
    val othersCount: Int get() = others.size + orphanRepairs.size

    companion object {
        /**
         * @param anomalyHealthIds 当前处于「注意/异常」的服务健康行 id（正常行不挂 AI，契约 v2.1 §A-4）
         * @param anomalyCheckIds 当前处于「注意/异常」的设备检查 id
         */
        fun build(
            analyses: List<DiagAnalysis>,
            repairs: List<RepairTask> = emptyList(),
            anomalyHealthIds: Set<String> = emptySet(),
            anomalyCheckIds: Set<String> = emptySet(),
        ): DiagAnalysisIndex {
            val analysisById = analyses.associateBy { it.id }
            val byHealth = LinkedHashMap<String, MutableList<DiagAnalysis>>()
            val byCheck = LinkedHashMap<String, MutableList<DiagAnalysis>>()
            val others = ArrayList<DiagAnalysis>()

            analyses.forEach { analysis ->
                val healthMatches = analysis.relatedHealthIds.filter { it in anomalyHealthIds }
                val checkMatches = analysis.relatedChecks.filter { it in anomalyCheckIds }
                if (healthMatches.isEmpty() && checkMatches.isEmpty()) {
                    others.add(analysis)
                }
                healthMatches.forEach { id ->
                    byHealth.getOrPut(id) { ArrayList() }.add(analysis)
                }
                checkMatches.forEach { id ->
                    byCheck.getOrPut(id) { ArrayList() }.add(analysis)
                }
            }

            val repairByAnalysis = LinkedHashMap<String, RepairTask>()
            analyses.forEach { analysis ->
                val repair = repairs.firstOrNull { it.id == analysis.relatedRepairId }
                    ?: repairs.firstOrNull { it.analysisId == analysis.id }
                if (repair != null) repairByAnalysis[analysis.id] = repair
            }
            val orphanRepairs = repairs.filter { repair ->
                repair.analysisId.isBlank() || !analysisById.containsKey(repair.analysisId)
            }
            return DiagAnalysisIndex(
                byHealthId = byHealth.mapValues { it.value.toList() },
                byCheckId = byCheck.mapValues { it.value.toList() },
                others = others,
                repairByAnalysis = repairByAnalysis,
                orphanRepairs = orphanRepairs,
            )
        }
    }
}

/** 折叠/展开 UI 文案与统计（契约 v2.1 §A-2/3/5）。 */
object DiagUiText {

    /** 折叠态一句话标题最大字数（含省略号共 ≤22 字）。 */
    const val TITLE_MAX: Int = 22

    /** 折叠态标题：脱敏 + 超长截断（`…` 计入字数）。 */
    fun collapseTitle(raw: String?): String {
        val sanitized = DiagSanitizer.sanitizeDisplay(raw, 120).ifBlank { "未命名诊断" }
        if (sanitized.length <= TITLE_MAX) return sanitized
        return sanitized.take(TITLE_MAX - 1) + "…"
    }

    /** 状态胶囊：设备端问题显示「建议」，其余按 new/queued/running/fixed/failed 映射。 */
    fun pillLabel(analysis: DiagAnalysis): String {
        if (isDeviceScoped(analysis)) return "建议"
        return DiagAnalysis.statusTextOf(analysis.status)
    }

    /** 设备端问题（挂在设备检查行下）→ 状态胶囊显示「建议」。 */
    fun isDeviceScoped(analysis: DiagAnalysis): Boolean =
        analysis.relatedChecks.isNotEmpty() && analysis.relatedHealthIds.isEmpty()

    /** 可自动修复：fixable 且有白名单 action。 */
    fun autoFixable(analysis: DiagAnalysis): Boolean =
        analysis.fixable && analysis.action.isNotBlank()

    /** 顶部摘要「可自动修复 N」：仍有待处理/修复中/失败的 fixable 结论。 */
    fun autoFixableCount(analyses: List<DiagAnalysis>): Int {
        val terminal = setOf("fixed", "ignored")
        return analyses.count { autoFixable(it) && it.status.trim().lowercase() !in terminal }
    }

    /** 顶部摘要「已修复 M」。 */
    fun fixedCount(analyses: List<DiagAnalysis>): Int =
        analyses.count { it.status.trim().lowercase() == "fixed" }

    /** 异常项还没有 AI 结论时的折叠行文案（灰，不可展开）。 */
    fun pendingText(agentOnline: Boolean): String =
        if (agentOnline) "AI 分析排队中…" else "本机离线，已留存待开机修复"

    /** flow 步状态文案（`run` = 进行中）。 */
    fun flowStateText(state: String): String = when (state.trim().lowercase()) {
        "run" -> "进行中"
        "done" -> "完成"
        "fail", "failed" -> "失败"
        "fixed" -> "已修复"
        "skip", "skipped" -> "跳过"
        else -> state.trim()
    }

    /** 修复任务状态胶囊文案（v2.1：简明）。 */
    fun repairStatusLabel(status: String): String = when (status.trim().lowercase()) {
        "pending" -> "等待执行"
        "running" -> "修复中"
        "done" -> "已完成"
        "failed" -> "失败"
        "skipped" -> "已跳过"
        else -> "未知"
    }

    /** 摘要行右侧「最后更新 HH:mm:ss」（无则空）。 */
    fun lastUpdatedText(ts: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        if (ts <= 0) return ""
        return runCatching {
            val time = java.time.Instant.ofEpochMilli(ts).atZone(zone).toLocalTime()
            String.format(java.util.Locale.US, "%02d:%02d:%02d", time.hour, time.minute, time.second)
        }.getOrDefault("")
    }
}

/** 诊断进展轮询配置（契约 v2.1 §A-7：15s → 10s）。 */
data class DiagPollConfig(
    val intervalMs: Long = INTERVAL_MS,
) {
    init {
        require(intervalMs > 0) { "intervalMs must be > 0" }
    }

    companion object {
        const val INTERVAL_MS: Long = 10_000L
    }
}

/** 一次轮询决策（纯状态机，UI 按此安排「立即拉 / 等待 / 停止」）。 */
sealed interface DiagPollTick {
    /** 进入页面 / 恢复前台 / 手动刷新：立即拉一次。 */
    data object FetchNow : DiagPollTick

    /** 可见状态下的常规等待（默认 10s）。 */
    data class Wait(val delayMs: Long) : DiagPollTick

    /** 页面不可见：停止轮询（隐藏/切走）。 */
    data object Stopped : DiagPollTick
}

object DiagPolling {

    /** 进入页面 / ON_RESUME 恢复前台：立即拉。 */
    fun onVisible(): DiagPollTick = DiagPollTick.FetchNow

    /** 一帧结束后（无论刚拉过还是等待中）：可见 → 等 [DiagPollConfig.intervalMs]；隐藏 → 停。 */
    fun afterTick(
        visible: Boolean,
        config: DiagPollConfig = DiagPollConfig(),
    ): DiagPollTick = if (visible) DiagPollTick.Wait(config.intervalMs) else DiagPollTick.Stopped
}
