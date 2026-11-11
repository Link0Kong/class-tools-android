package com.furina.classtools.data.diag

import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asBooleanOrNull
import com.furina.classtools.data.network.asIntOrNull
import com.furina.classtools.data.network.asLongOrNull
import com.furina.classtools.data.network.asObjectOrNull
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * 诊断相关 PostgREST / RPC 响应解析（纯函数，容错；坏行跳过，不抛异常）。
 *
 * - 行集合兼容「数组 / 单对象 / 单元素数组」；
 * - jsonb 列（service_health.meta、diag_analysis.suggestion/flow、client_diag.detail/device）兼容
 *   对象与「JSON 字符串」两种形态（CloudBase jsonb 实测两种都出现过）；
 * - 解析结果里的 detail/device 在入库前已脱敏；展示前仍由 UI 层再调 [DiagSanitizer.sanitizeDisplay]。
 */
object DiagParser {

    /** 云巡检心跳 15 分钟未更新 → 云端行降级为「注意」（web 同口径）。 */
    const val CLOUD_PROBE_STALE_MS: Long = 15L * 60 * 1000

    /** 5 组服务健康行（web `health_monitor_spec.md` §9.1）。 */
    val GROUP_IDS: List<Pair<String, List<String>>> = listOf(
        "站点与网页" to listOf("site_static", "site_tls", "dns_domain"),
        "云端后台" to listOf(
            "cloudbase", "tt_cloud", "cloud_power_sync", "cloud_xgj_sync", "cloud_health_probe",
        ),
        "信息采集" to listOf("qqflow", "weflow", "watcher", "briefdesk", "qq_client", "wechat_client"),
        "定时任务" to listOf("jwxt", "hwauto", "ai"),
        "采集主机" to listOf("collector_host"),
    )

    private val CLOUD_GROUP_IDS: Set<String> = GROUP_IDS[1].second.toSet()

    // ------------------------------------------------------------ 通用

    /** 行集合解析：`[...]` → 数组；`{...}` → 单行；坏 JSON/其它 → null。 */
    fun parseRows(raw: String?, json: Json = NetJson): List<JsonObject>? {
        val element = parseJsonOrNull(json, raw?.trim()) ?: return null
        return when (element) {
            is JsonArray -> element.mapNotNull { it.asObjectOrNull() }
            is JsonObject -> listOf(element)
            else -> null
        }
    }

    /** 字段兼容「JSON 对象」与「JSON 字符串」两种形态（jsonb 列 / PostgREST 文本列）。 */
    fun jsonObjectField(element: JsonElement?, json: Json = NetJson): JsonObject? = when (element) {
        is JsonObject -> element
        is JsonPrimitive -> {
            val text = element.contentOrNull
            if (text.isNullOrBlank()) null else parseJsonOrNull(json, text)?.asObjectOrNull()
        }
        else -> null
    }

    /** JSON 数组列（jsonb 数组 / JSON 字符串）→ 元素列表。 */
    fun jsonArrayField(element: JsonElement?, json: Json = NetJson): List<JsonElement> = when (element) {
        is JsonArray -> element.toList()
        is JsonPrimitive -> {
            val text = element.contentOrNull
            if (text.isNullOrBlank()) emptyList() else (parseJsonOrNull(json, text) as? JsonArray)?.toList().orEmpty()
        }
        else -> emptyList()
    }

    /** jsonb 值的展示文本（对象 → 紧凑 JSON；字符串 → 原文）。 */
    fun jsonText(element: JsonElement?): String = when (element) {
        null -> ""
        is JsonPrimitive -> element.contentOrNull.orEmpty()
        else -> element.toString()
    }

    /** ISO-8601 / RFC 1123 / 纯数字毫秒 → epoch ms（失败 → null）。仅供「年龄」判断用。 */
    fun parseTimeMillis(value: String?): Long? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null
        text.toLongOrNull()?.let { return it }
        return try {
            Instant.parse(text).toEpochMilli()
        } catch (_: Throwable) {
            try {
                OffsetDateTime.parse(text).toInstant().toEpochMilli()
            } catch (_: Throwable) {
                null
            }
        }
    }

    // ------------------------------------------------------------ service_health

    fun parseServiceHealth(raw: String?, json: Json = NetJson): List<ServiceHealthRow> {
        val rows = parseRows(raw, json) ?: return emptyList()
        return rows.mapNotNull { row ->
            val id = row["id"].asStringOrNull()?.trim().orEmpty()
            if (id.isEmpty()) return@mapNotNull null
            ServiceHealthRow(
                id = id,
                name = row["name"].asStringOrNull()?.trim().orEmpty(),
                kind = row["kind"].asStringOrNull()?.trim().orEmpty(),
                status = row["status"].asStringOrNull()?.trim().orEmpty(),
                detail = row["detail"].asStringOrNull().orEmpty(),
                meta = jsonText(row["meta"]),
                checkedAt = row["checked_at"].asStringOrNull()?.trim().orEmpty()
                    .ifBlank { row["updated_at"].asStringOrNull()?.trim().orEmpty() },
            )
        }
    }

    /**
     * 按 5 组聚合；未知 id 进「其它」。
     *
     * 云端巡检过期降级（web 同口径）：`cloud_health_probe` 缺失或 checked_at 超过 15 分钟 →
     * 云端后台组里除 `cloudbase` 外的 `ok` 行展示为「注意 · 巡检未更新」。
     */
    fun groupServiceHealth(
        rows: List<ServiceHealthRow>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<ServiceHealthGroup> {
        val probe = rows.firstOrNull { it.id == "cloud_health_probe" }
        val probeAt = probe?.let { parseTimeMillis(it.checkedAt) }
        val probeStale = probe == null || probeAt == null || nowMillis - probeAt > CLOUD_PROBE_STALE_MS

        val adjusted = rows.map { row ->
            if (probeStale && row.id in CLOUD_GROUP_IDS && row.id != "cloudbase" &&
                row.displayStatus == DiagStatus.PASS
            ) {
                row.copy(
                    status = "warn",
                    detail = listOf(row.detail.trim(), "巡检未更新").filter { it.isNotEmpty() }
                        .joinToString(" · "),
                )
            } else {
                row
            }
        }
        val byId = adjusted.associateBy { it.id }
        val grouped = LinkedHashSet<String>()
        val out = ArrayList<ServiceHealthGroup>(GROUP_IDS.size + 1)
        GROUP_IDS.forEach { (title, ids) ->
            val groupRows = ids.mapNotNull { byId[it] }
            grouped.addAll(ids)
            if (groupRows.isNotEmpty()) out.add(ServiceHealthGroup(title, groupRows))
        }
        val others = adjusted.filter { it.id !in grouped }
        if (others.isNotEmpty()) out.add(ServiceHealthGroup("其它", others))
        return out
    }

    // ------------------------------------------------------------ diag_analysis

    fun parseAnalyses(raw: String?, json: Json = NetJson): List<DiagAnalysis> {
        val rows = parseRows(raw, json) ?: return emptyList()
        return rows.mapNotNull { row ->
            val id = row["id"].asStringOrNull()?.trim().orEmpty()
            if (id.isEmpty()) return@mapNotNull null
            val suggestions = jsonArrayField(row["suggestion"], json)
                .mapNotNull { it.asStringOrNull()?.trim() }
                .filter { it.isNotEmpty() }
            val flow = jsonArrayField(row["flow"], json).mapNotNull { step ->
                val obj = step.asObjectOrNull() ?: return@mapNotNull null
                val text = obj["text"].asStringOrNull()?.trim().orEmpty()
                if (text.isEmpty()) return@mapNotNull null
                FlowStep(
                    ts = obj["t"].asLongOrNull() ?: parseTimeMillis(obj["t"].asStringOrNull()) ?: 0L,
                    text = text,
                    state = obj["st"].asStringOrNull().orEmpty(),
                )
            }
            val related = parseRelated(row["related"], json)
            DiagAnalysis(
                id = id,
                ts = row["ts"].asLongOrNull() ?: 0L,
                platform = row["platform"].asStringOrNull()?.trim().orEmpty(),
                scope = row["scope"].asStringOrNull()?.trim().orEmpty(),
                severity = row["severity"].asStringOrNull()?.trim().orEmpty(),
                title = row["title"].asStringOrNull()?.trim().orEmpty(),
                cause = row["cause"].asStringOrNull()?.trim().orEmpty(),
                suggestions = suggestions,
                fixable = row["fixable"].asBooleanOrNull() ?: false,
                action = row["action"].asStringOrNull()?.trim().orEmpty(),
                status = row["status"].asStringOrNull()?.trim().orEmpty(),
                confidence = row["confidence"].asStringOrNull()?.toDoubleOrNull()
                    ?: (row["confidence"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
                    ?: 0.0,
                flow = flow,
                relatedHealthIds = related.healthIds,
                relatedChecks = related.checks,
                relatedRepairId = related.repairId,
            )
        }
    }

    /** `diag_analysis.related` 解析：`{health_ids:[], checks:[], repair_id}`（兼容 JSON 字符串 / 旧数据缺失）。 */
    data class RelatedRefs(
        val healthIds: List<String> = emptyList(),
        val checks: List<String> = emptyList(),
        val repairId: String = "",
    )

    fun parseRelated(element: JsonElement?, json: Json = NetJson): RelatedRefs {
        val obj = jsonObjectField(element, json) ?: return RelatedRefs()
        fun stringList(key: String): List<String> =
            jsonArrayField(obj[key], json)
                .mapNotNull { it.asStringOrNull()?.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        val repairElement = obj["repair_id"] as? JsonPrimitive
        val repairId = if (repairElement?.isString == true) {
            repairElement.content.trim()
        } else {
            ""
        }
        return RelatedRefs(
            healthIds = stringList("health_ids"),
            checks = stringList("checks"),
            repairId = repairId,
        )
    }

    // ------------------------------------------------------------ repair_tasks

    fun parseRepairTasks(raw: String?, json: Json = NetJson): List<RepairTask> {
        val rows = parseRows(raw, json) ?: return emptyList()
        return rows.mapNotNull { row ->
            val id = row["id"].asStringOrNull()?.trim().orEmpty()
            if (id.isEmpty()) return@mapNotNull null
            RepairTask(
                id = id,
                createdAt = row["created_at"].asLongOrNull() ?: 0L,
                action = row["action"].asStringOrNull()?.trim().orEmpty(),
                reason = row["reason"].asStringOrNull().orEmpty(),
                status = row["status"].asStringOrNull()?.trim().orEmpty(),
                claimedAt = row["claimed_at"].asLongOrNull() ?: 0L,
                finishedAt = row["finished_at"].asLongOrNull() ?: 0L,
                result = row["result"].asStringOrNull().orEmpty(),
                analysisId = row["analysis_id"].asStringOrNull()?.trim().orEmpty(),
                node = row["node"].asStringOrNull()?.trim().orEmpty(),
            )
        }
    }

    // ------------------------------------------------------------ rpc/diag_agent_info

    fun parseAgentInfo(raw: String?, json: Json = NetJson): DiagAgentInfo? {
        val element = parseJsonOrNull(json, raw?.trim()) ?: return null
        val body = when (element) {
            is JsonObject -> element
            is JsonArray -> element.firstOrNull().asObjectOrNull() ?: return null
            else -> return null
        }
        val ok = body["ok"].asBooleanOrNull()
        if (ok == false) return null
        return DiagAgentInfo(
            online = body["online"].asBooleanOrNull() ?: false,
            seenAt = body["seen_at"].asLongOrNull() ?: 0L,
            host = body["host"].asStringOrNull()?.trim().orEmpty(),
            version = body["version"].asStringOrNull()?.trim().orEmpty(),
        )
    }

    // ------------------------------------------------------------ client_diag（只看自己）

    fun parseClientDiagRows(raw: String?, json: Json = NetJson): List<ClientDiagRow> {
        val rows = parseRows(raw, json) ?: return emptyList()
        return rows.mapNotNull { row ->
            val id = row["id"].asStringOrNull()?.trim().orEmpty()
            if (id.isEmpty()) return@mapNotNull null
            ClientDiagRow(
                id = id,
                platform = row["platform"].asStringOrNull()?.trim().orEmpty(),
                appVer = row["app_ver"].asStringOrNull()?.trim().orEmpty(),
                kind = row["kind"].asStringOrNull()?.trim().orEmpty(),
                level = row["level"].asStringOrNull()?.trim().orEmpty(),
                message = row["message"].asStringOrNull().orEmpty(),
                fingerprint = row["fingerprint"].asStringOrNull().orEmpty(),
                ts = row["ts"].asLongOrNull() ?: 0L,
                status = row["status"].asStringOrNull()?.trim().orEmpty(),
                detail = jsonObjectField(row["detail"], json) ?: JsonObject(emptyMap()),
                device = jsonObjectField(row["device"], json) ?: JsonObject(emptyMap()),
            )
        }
    }
}
