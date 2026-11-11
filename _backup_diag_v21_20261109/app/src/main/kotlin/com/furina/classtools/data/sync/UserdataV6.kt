package com.furina.classtools.data.sync

import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.parseJsonOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * `userdata` v6 blob 结构常量与编解码（契约：docs/BACKEND_CONTRACT.md §4 + docs/api/USERDATA_V6.md）。
 *
 * 顶层 14 键、固定顺序：`v, at, homeTools, fit, draw, group, roster, hide, tt, prefs, nick, power, water, homeToolsAt`。
 * 11 个 section 顺序即合并顺序（必须与网页 `UD_KEYS` / 小程序 `SECTION_KEYS` / `merge_ref.js` 完全一致）。
 *
 * 实现建议：合并/比较一律用 [JsonObject]（保序、保留未知字段），不要用 data class 直接序列化 blob——
 * data class 会丢掉「未拥有 section 原样保留」和未知字段，导致跨端覆盖。
 */
object UserdataV6 {
    const val VERSION: Int = 6

    val SECTION_KEYS: List<String> = listOf(
        "homeTools", "fit", "draw", "group", "roster", "hide",
        "tt", "prefs", "nick", "power", "water",
    )

    val TOP_LEVEL_KEYS: List<String> = listOf("v", "at") + SECTION_KEYS + listOf("homeToolsAt")

    /** 某个 section 的默认值（与 mp privacy.DEFAULTS / web UD_DEF / merge_ref.js DEFAULTS 逐字段一致）。 */
    fun defaultSection(key: String): JsonElement = when (key) {
        "homeTools" -> JsonArray(emptyList())
        "fit", "draw", "group" -> JsonObject(emptyMap())
        "roster" -> buildJsonObject {
            put("rosterSel", JsonArray(emptyList()))
            put("rollSel", JsonArray(emptyList()))
            put("rollSt", JsonObject(emptyMap()))
            put("rollNames", JsonPrimitive(""))
        }
        "hide" -> buildJsonObject {
            put("hwHide", JsonObject(emptyMap()))
            put("ntHide", JsonObject(emptyMap()))
        }
        "tt" -> buildJsonObject {
            put("ttView", JsonPrimitive(""))
            put("ttUseMine", JsonPrimitive(false))
            put("ttLocal", JsonNull)
            put("termStart", JsonPrimitive(""))
        }
        "prefs" -> buildJsonObject {
            put("hwRemind", JsonObject(emptyMap()))
            put("ntSub", JsonPrimitive(false))
            put("ntSubAt", JsonPrimitive(0))
        }
        "nick" -> JsonPrimitive("")
        "power" -> buildJsonObject {
            put("sel", JsonPrimitive(""))
            put("at", JsonPrimitive(0))
            put("low", JsonPrimitive(30))
        }
        "water" -> buildJsonObject { put("rooms", JsonObject(emptyMap())) }
        else -> JsonNull
    }

    /** 空 v6 blob（等价 mp `privacy.emptyV6()`：清除云端副本/初始化基线用）。 */
    fun empty(): JsonObject = buildJsonObject {
        put("v", JsonPrimitive(VERSION))
        val at = LinkedHashMap<String, JsonElement>()
        SECTION_KEYS.forEach { at[it] = JsonPrimitive(0) }
        put("at", JsonObject(at))
        SECTION_KEYS.forEach { put(it, defaultSection(it)) }
        put("homeToolsAt", JsonPrimitive(0))
    }

    fun emptyBlob(json: Json = NetJson): String = encode(empty(), json)

    /** 解析 blob 字符串：坏 JSON / 非对象 → null（契约：视为无云端数据，不覆盖本机）。 */
    fun decode(blobText: String?, json: Json = NetJson): JsonObject? =
        parseJsonOrNull(json, blobText) as? JsonObject

    /** 序列化为紧凑 JSON（服务端存储格式；键序 = [JsonObject] 的插入顺序）。 */
    fun encode(blob: JsonObject, json: Json = NetJson): String =
        json.encodeToString(JsonObject.serializer(), blob)

    /** 只保留 11 个 section 的值（去掉 v/at/homeToolsAt），用于本机视图/落盘 current。 */
    fun sectionsOf(blob: JsonObject?): JsonObject {
        if (blob == null) return JsonObject(emptyMap())
        val out = LinkedHashMap<String, JsonElement>()
        SECTION_KEYS.forEach { key -> blob[key]?.let { out[key] = it } }
        return JsonObject(out)
    }

    fun atMap(blob: JsonObject?): JsonObject = (blob?.get("at") as? JsonObject) ?: JsonObject(emptyMap())

    /** 读取 `at[key]`（ms），非法/缺失 → 0。 */
    fun atValue(blob: JsonObject?, key: String): Long = numMs(atMap(blob)[key])

    /** 时间戳解析：只有 >0 的有限数才有效（与 web/小程序 `num(v)=isFinite(v)&&v>0?v:0` 等价）。 */
    fun numMs(element: JsonElement?): Long {
        val primitive = element as? JsonPrimitive ?: return 0L
        if (primitive.isString) return 0L
        val value = primitive.content.toDoubleOrNull() ?: return 0L
        if (!value.isFinite() || value <= 0.0) return 0L
        return value.toLong()
    }
}
