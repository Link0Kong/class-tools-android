package com.furina.classtools.data.diag

import kotlinx.serialization.json.JsonObject

/**
 * 诊断 v2 领域模型（契约：`_diag_task/CONTRACT.md` §1/§2 + `web/deploy/cloudbase/2026-11-08_diag_login.sql`）。
 *
 * 覆盖：服务健康（service_health）、设备诊断（本机检查）、client_diag 上报、AI 分析（diag_analysis）、
 * 修复任务（repair_tasks）、本机 agent 状态（rpc/diag_agent_info）。
 *
 * 铁律：本包内所有 model 只承载「契约允许的脱敏字段」；任何 token/密码/学号/姓名/宿舍号/完整 URL/
 * 绝对路径都不得进入 [ClientDiagRow] 的 detail/device（见 [DiagSanitizer] / [DiagPayload]）。
 */

/** 上报等级（client_diag.level：error | warn | info）。 */
enum class DiagLevel(val wire: String) {
    ERROR("error"),
    WARN("warn"),
    INFO("info");

    companion object {
        fun fromWire(value: String?): DiagLevel = when (value?.trim()?.lowercase()) {
            "error" -> ERROR
            "info" -> INFO
            else -> WARN
        }
    }
}

/** 展示状态（通过 / 注意 / 异常 / 信息）。 */
enum class DiagStatus {
    PASS,
    WARN,
    FAIL,
    INFO,
    ;

    val label: String
        get() = when (this) {
            PASS -> "通过"
            WARN -> "注意"
            FAIL -> "异常"
            INFO -> "信息"
        }
}

/**
 * 一条本机设备诊断项。
 *
 * @param id 稳定标识（用于 fingerprint/去重；如 `memory`、`gateway`）
 * @param label 展示名
 * @param value 展示值（已脱敏的短文本）
 * @param hint 通过/注意/异常时的补充说明（脱敏）
 * @param reportKind 上报 client_diag.kind（error|network|storage|perf|manual）
 */
data class DiagCheck(
    val id: String,
    val label: String,
    val status: DiagStatus,
    val value: String = "",
    val hint: String = "",
    val reportKind: String = "error",
) {
    /** 只有「注意/异常」才自动上报（契约 §2：页面加载不计）。 */
    val shouldReport: Boolean
        get() = status == DiagStatus.WARN || status == DiagStatus.FAIL

    val level: DiagLevel
        get() = when (status) {
            DiagStatus.FAIL -> DiagLevel.ERROR
            DiagStatus.WARN -> DiagLevel.WARN
            else -> DiagLevel.INFO
        }
}

/** 设备诊断汇总（带计数，UI 顶部展示）。 */
data class DiagSummary(
    val checks: List<DiagCheck> = emptyList(),
    val ranAt: Long = 0L,
) {
    val passCount: Int get() = checks.count { it.status == DiagStatus.PASS }
    val warnCount: Int get() = checks.count { it.status == DiagStatus.WARN }
    val failCount: Int get() = checks.count { it.status == DiagStatus.FAIL }
    val anomalies: List<DiagCheck> get() = checks.filter { it.status == DiagStatus.WARN || it.status == DiagStatus.FAIL }

    /** 总状态：有异常→FAIL；有注意→WARN；否则 PASS。 */
    val overall: DiagStatus
        get() = when {
            failCount > 0 -> DiagStatus.FAIL
            warnCount > 0 -> DiagStatus.WARN
            else -> DiagStatus.PASS
        }
}

/** `public.service_health` 一行（18 行 / 5 组；detail/meta 展示前再脱敏）。 */
data class ServiceHealthRow(
    val id: String,
    val name: String,
    val kind: String,
    val status: String,
    val detail: String,
    val meta: String,
    val checkedAt: String,
) {
    /** 行状态 → 展示状态（ok | warn | down | unknown）。 */
    val displayStatus: DiagStatus
        get() = statusOf(status)

    val statusLabel: String
        get() = when (displayStatus) {
            DiagStatus.PASS -> "正常"
            DiagStatus.WARN -> "注意"
            DiagStatus.FAIL -> "异常"
            DiagStatus.INFO -> "未知"
        }

    companion object {
        fun statusOf(raw: String?): DiagStatus = when (raw?.trim()?.lowercase()) {
            "ok" -> DiagStatus.PASS
            "warn" -> DiagStatus.WARN
            "down" -> DiagStatus.FAIL
            else -> DiagStatus.INFO
        }
    }
}

/** 服务健康分组（契约 §9.1：站点与网页 / 云端后台 / 信息采集 / 定时任务 / 采集主机）。 */
data class ServiceHealthGroup(
    val title: String,
    val rows: List<ServiceHealthRow>,
) {
    val worstStatus: DiagStatus
        get() = when {
            rows.any { it.displayStatus == DiagStatus.FAIL } -> DiagStatus.FAIL
            rows.any { it.displayStatus == DiagStatus.WARN } -> DiagStatus.WARN
            rows.any { it.displayStatus == DiagStatus.PASS } -> DiagStatus.PASS
            else -> DiagStatus.INFO
        }
}

/** AI 分析流程步骤（diag_analysis.flow / repair_tasks 回写）。 */
data class FlowStep(
    val ts: Long = 0L,
    val text: String = "",
    val state: String = "",
)

/** `public.diag_analysis` 一行（本机 agent 用 AI 产出的原因 + 建议 + 流程）。 */
data class DiagAnalysis(
    val id: String,
    val ts: Long,
    val platform: String,
    val scope: String,
    val severity: String,
    val title: String,
    val cause: String,
    val suggestions: List<String>,
    val fixable: Boolean,
    val action: String,
    val status: String,
    val confidence: Double,
    val flow: List<FlowStep>,
) {
    val severityLevel: DiagLevel
        get() = DiagLevel.fromWire(severity)

    val statusLabel: String
        get() = statusTextOf(status)

    companion object {
        fun statusTextOf(raw: String?): String = when (raw?.trim()?.lowercase()) {
            "new" -> "待分析"
            "queued" -> "排队修复"
            "running" -> "修复中"
            "fixed" -> "已修复"
            "failed" -> "修复失败"
            "manual" -> "需人工处理"
            "ignored" -> "已忽略"
            else -> "未知"
        }
    }
}

/** `public.repair_tasks` 一行（本机自动修复任务/记录）。 */
data class RepairTask(
    val id: String,
    val createdAt: Long,
    val action: String,
    val reason: String,
    val status: String,
    val claimedAt: Long,
    val finishedAt: Long,
    val result: String,
    val analysisId: String,
    val node: String,
) {
    val statusLabel: String
        get() = when (status.trim().lowercase()) {
            "pending" -> "等待执行"
            "running" -> "执行中"
            "done" -> "已完成"
            "failed" -> "失败"
            "skipped" -> "已跳过"
            else -> "未知"
        }

    val finished: Boolean
        get() = status.trim().lowercase() in setOf("done", "failed", "skipped")
}

/** `rpc/diag_agent_info` 响应（全班可读）。 */
data class DiagAgentInfo(
    val online: Boolean = false,
    val seenAt: Long = 0L,
    val host: String = "",
    val version: String = "",
)

/** 网关计时探测结果（诊断页「CloudBase 网关延迟」/「时钟偏差」）。 */
data class GatewayProbe(
    val latencyMs: Long,
    val serverEpochMs: Long? = null,
)

/** `client_diag` 上报行 / 读取行（本机最近上报，全部为脱敏字段）。 */
data class ClientDiagRow(
    val id: String,
    val platform: String,
    val appVer: String,
    val kind: String,
    val level: String,
    val message: String,
    val fingerprint: String,
    val ts: Long,
    val status: String,
    val detail: JsonObject,
    val device: JsonObject,
)
