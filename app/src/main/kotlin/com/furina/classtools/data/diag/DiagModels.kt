package com.furina.classtools.data.diag

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
) {
    /** `run` = 本机正在执行（页面显示「进行中」）。 */
    val running: Boolean
        get() = state.trim().lowercase() == "run"
}

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
    /** `related.health_ids`：AI 结论对应挂载的 service_health 行 id（v2.1 下挂）。 */
    val relatedHealthIds: List<String> = emptyList(),
    /** `related.checks`：AI 结论对应挂载的设备检查 id（agent 从 client_diag.detail.check 推导）。 */
    val relatedChecks: List<String> = emptyList(),
    /** `related.repair_id`：关联的 repair_tasks.id（展开态「关联修复」）。 */
    val relatedRepairId: String = "",
) {
    val severityLevel: DiagLevel
        get() = DiagLevel.fromWire(severity)

    val statusLabel: String
        get() = statusTextOf(status)

    companion object {
        fun statusTextOf(raw: String?): String = when (raw?.trim()?.lowercase()) {
            "new" -> "待处理"
            "queued" -> "待处理"
            "running" -> "修复中"
            "fixed" -> "已修复"
            "failed" -> "修复失败"
            "manual" -> "建议"
            "ignored" -> "已忽略"
            else -> "待处理"
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

/**
 * 设备信息最小化（契约 v2.1 §B android 白名单）：
 * `platform / osVersion / appVer / webview / screen{w,h,dpr} / memoryMB / storagePct / network / clockSkewMs`。
 *
 * 品牌、型号、语言、API level、时区、存储总量等诊断价值低的字段一律不采集/不上报/不展示。
 * `screen` 是嵌套对象（w/h 为物理像素，dpr 为 density）。
 */
data class DiagDeviceInfo(
    val platform: String = "android",
    val osVersion: String = "",
    val appVer: String = "",
    val webview: String = "",
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val screenDpr: Double = 0.0,
    /** 可用内存 MB（导致不可用的量；总量不上报）。 */
    val memoryMB: Long = 0,
    /** 可用存储百分比 0–100。 */
    val storagePct: Int = 0,
    val network: String = "",
    val clockSkewMs: Long? = null,
) {
    /** 上报 device JSON（白名单字段；值本身已最小化，[DiagPayload] 落库前再过滤一遍）。 */
    fun toJson(): JsonObject = buildJsonObject {
        put("platform", JsonPrimitive(platform))
        if (osVersion.isNotBlank()) put("osVersion", JsonPrimitive(DiagSanitizer.sanitizeText(osVersion, 40)))
        if (appVer.isNotBlank()) put("appVer", JsonPrimitive(DiagSanitizer.sanitizeText(appVer, 40)))
        if (webview.isNotBlank()) put("webview", JsonPrimitive(DiagSanitizer.sanitizeText(webview, 40)))
        if (screenWidth > 0 && screenHeight > 0 && screenDpr > 0.0) {
            put(
                "screen",
                buildJsonObject {
                    put("w", JsonPrimitive(screenWidth))
                    put("h", JsonPrimitive(screenHeight))
                    put("dpr", JsonPrimitive(kotlin.math.round(screenDpr * 100.0) / 100.0))
                },
            )
        }
        if (memoryMB > 0) put("memoryMB", JsonPrimitive(memoryMB))
        if (storagePct in 0..100) put("storagePct", JsonPrimitive(storagePct))
        if (network.isNotBlank()) put("network", JsonPrimitive(DiagSanitizer.sanitizeText(network, 20)))
        clockSkewMs?.let { put("clockSkewMs", JsonPrimitive(it)) }
    }

    companion object {
        val KEYS: Set<String> = setOf(
            "platform", "osVersion", "appVer", "webview", "screen",
            "memoryMB", "storagePct", "network", "clockSkewMs",
        )
        val SCREEN_KEYS: Set<String> = setOf("w", "h", "dpr")
    }
}
