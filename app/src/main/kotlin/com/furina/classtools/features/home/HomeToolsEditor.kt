package com.furina.classtools.features.home

import com.furina.classtools.data.tool.ToolCatalog
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
 *
 * 2026-11-22：写入时把本机 id 换成跨端 canonical id（fit→ft、roster→committee），
 * 读取时再换回本机 id（兼容历史 fit/roster 与网页 committee、小程序 bz）；
 * 否则网页/小程序写入的 ft/committee 会被当未知项丢掉，换端后体测/名单从首页消失。
 */
object HomeToolsEditor {

    const val MAX_QUICK_TOOLS = 4

    val availableTools = ToolCatalog.tools.filterNot { it.adminOnly || it.id == "settings" }

    fun availableIds(ids: List<String>): List<String> = ToolCatalog.fromSyncIds(ids)
        .filter { id -> availableTools.any { it.id == id } }
        .take(MAX_QUICK_TOOLS)

    fun apply(sections: JsonObject, ids: List<String>, now: Long = System.currentTimeMillis()): JsonObject {
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
            put("homeTools", section(ids))
            put("homeToolsAt", JsonPrimitive(now))
        }
    }

    /** 同步 id 转为本机 id，保留无法显示的 id 用于提交时比较基线。 */
    fun read(sections: JsonObject?): List<String> = readSection(sections?.get("homeTools"))

    fun readSection(section: JsonElement?): List<String> {
        val array = section as? JsonArray ?: return emptyList()
        return ToolCatalog.fromSyncIds(array.mapNotNull { (it as? JsonPrimitive)?.contentOrNullSafe() })
    }

    fun section(ids: List<String>): JsonArray = JsonArray(
        ToolCatalog.toSyncIds(availableIds(ids)).map(::JsonPrimitive),
    )

    fun atOf(sections: JsonObject?): Long =
        ((sections?.get("at") as? JsonObject)?.get("homeTools") as? JsonPrimitive)?.longOrNull ?: 0L

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (isString) content else null

}

data class HomeToolsDraft(val page: Int, val baseline: List<String>, val ids: List<String>) {
    val dirty: Boolean get() = ids != HomeToolsEditor.availableIds(baseline)

    fun toggle(id: String): HomeToolsDraft {
        if (id in ids) return copy(ids = ids - id)
        return copy(ids = HomeToolsEditor.availableIds(ids + id))
    }

    fun reorder(order: List<String>): HomeToolsDraft =
        if (order.size == ids.size && order.toSet() == ids.toSet()) copy(ids = order) else this

    companion object {
        fun begin(page: Int, baseline: List<String>) =
            HomeToolsDraft(page, baseline, HomeToolsEditor.availableIds(baseline))
    }
}
