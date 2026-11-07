package com.furina.classtools.features.home

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 首页「常用工具」编辑的数据层适配（userdata v6 `homeTools` section）。
 *
 * 契约：homeTools 为工具 id 数组（≤4），`at.homeTools` 与顶层 `homeToolsAt` 镜像该 section 时间戳。
 * 本函数返回一个完整 sections JsonObject（其余 section 原样保留），交给 [com.furina.classtools.data.sync.UserDataRepository.updateCurrent]。
 */
object HomeToolsEditor {

    const val MAX_QUICK_TOOLS = 4

    fun apply(sections: JsonObject, ids: List<String>, now: Long = System.currentTimeMillis()): JsonObject {
        val normalized = ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_QUICK_TOOLS)
        val oldAt = sections["at"] as? JsonObject
        val newAt = buildJsonObject {
            oldAt?.forEach { (k, v) -> put(k, v) }
            put("homeTools", JsonPrimitive(now))
        }
        return buildJsonObject {
            sections.forEach { (k, v) ->
                if (k != "at" && k != "homeTools" && k != "homeToolsAt") put(k, v)
            }
            put("at", newAt)
            put("homeTools", JsonArray(normalized.map { JsonPrimitive(it) }))
            put("homeToolsAt", JsonPrimitive(now))
        }
    }

    /** 从 sections 中读取 homeTools（无则返回空列表，由调用方回落默认值）。 */
    fun read(sections: JsonObject?): List<String> {
        val array = sections?.get("homeTools") as? JsonArray ?: return emptyList()
        return array.mapNotNull { (it as? JsonPrimitive)?.contentOrNullSafe() }
    }

    fun atOf(sections: JsonObject?): Long =
        ((sections?.get("at") as? JsonObject)?.get("homeTools") as? JsonPrimitive)?.longOrNull ?: 0L

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (isString) content else null

    @Suppress("unused")
    private fun JsonElement.unused() = Unit
}
