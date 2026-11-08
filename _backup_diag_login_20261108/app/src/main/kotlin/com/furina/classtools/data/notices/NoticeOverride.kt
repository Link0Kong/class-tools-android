package com.furina.classtools.data.notices

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 管理员编辑覆盖（`notices.override_json`，契约：`2026-11-05_notice_admin_override.sql` + `docs/api/NOTICES.md` §4）。
 *
 * 白名单：`title / cat / summary / aiSummary / deadline / eventAt / expireAt / timeLabel`。
 * 语义：字段**存在**时覆盖；值为 `null` 或空串 → 清空该字段（回落为空，由状态机按无该时间处理）。
 * 本轮客户端只读（不提供管理员编辑入口）。
 */
object NoticeOverride {

    val WHITELIST: List<String> = listOf(
        "title", "cat", "summary", "aiSummary", "deadline", "eventAt", "expireAt", "timeLabel",
    )

    /** 解析 override_json；坏 JSON / 非对象 → null（视为无覆盖）。 */
    fun parse(raw: String?, json: Json = kotlinx.serialization.json.Json): JsonObject? {
        if (raw.isNullOrBlank()) return null
        val element = try {
            json.parseToJsonElement(raw)
        } catch (_: Throwable) {
            return null
        }
        return element as? JsonObject
    }

    /** 应用覆盖（不可变 copy）。 */
    fun apply(group: NoticeGroupDto, override: JsonObject?): NoticeGroupDto {
        if (override == null) return group
        fun value(key: String): String? {
            if (!override.containsKey(key)) return null
            return when (val element = override[key]) {
                null, is JsonNull -> ""
                is JsonPrimitive -> element.contentOrNull
                else -> null
            }
        }

        var result = group
        if (override.containsKey("title")) result = result.copy(title = value("title").orEmpty())
        if (override.containsKey("cat")) result = result.copy(cat = value("cat").orEmpty())
        if (override.containsKey("summary")) result = result.copy(summary = value("summary").orEmpty())
        if (override.containsKey("aiSummary")) result = result.copy(aiSummary = value("aiSummary").orEmpty())
        if (override.containsKey("deadline")) result = result.copy(deadline = value("deadline").orEmpty())
        if (override.containsKey("eventAt")) result = result.copy(eventAt = value("eventAt").orEmpty())
        if (override.containsKey("expireAt")) result = result.copy(expireAt = value("expireAt").orEmpty())
        if (override.containsKey("timeLabel")) result = result.copy(timeLabel = value("timeLabel").orEmpty())
        return result
    }
}
