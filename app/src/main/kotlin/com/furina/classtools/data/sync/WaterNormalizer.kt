package com.furina.classtools.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * `water` section 归一化（契约：`_water_contract.md` §7.1 + docs/api/USERDATA_V6.md §1.3）。
 *
 * 结构：`{ "rooms": { "<roomverify>": { "p": 1..6, "recs": [ {"m":"YYYY-MM","r":<吨>,"ts":<ms>} ] } } }`
 * 规则：p 只允许 1–6；月份合法 `YYYY-MM`；读数有限且 ≥0、保留 2 位；按月唯一升序；≤120 条；坏房间丢弃。
 *
 * 用途：push 前归一（避免本机旧脏数据回写云端）；远端 merge 结果落本机前也可归一。
 */
object WaterNormalizer {
    const val MAX_RECORDS: Int = 120
    const val PERSONS_DEFAULT: Int = 4
    private val MONTH_REGEX = Regex("^\\d{4}-(0[1-9]|1[0-2])$")

    fun normalize(section: JsonElement?): JsonObject {
        val roomsElement = ((section as? JsonObject)?.get("rooms")) as? JsonObject
        val rooms = LinkedHashMap<String, JsonElement>()
        if (roomsElement != null) {
            for ((roomVerify, roomElement) in roomsElement) {
                val room = roomElement as? JsonObject ?: continue
                if (roomVerify.isBlank()) continue
                val persons = (room["p"] as? JsonPrimitive)?.intOrNull
                    ?.takeIf { it in 1..6 } ?: PERSONS_DEFAULT
                val records = normalizeRecords(room["recs"])
                rooms[roomVerify] = buildJsonObject {
                    put("p", JsonPrimitive(persons))
                    put("recs", JsonArray(records))
                }
            }
        }
        return buildJsonObject { put("rooms", JsonObject(rooms)) }
    }

    /** 归一记录列表（同月后者覆盖；按月升序；最多 120 条保留最新月份）。 */
    fun normalizeRecords(input: JsonElement?): List<JsonElement> {
        val array = input as? JsonArray ?: return emptyList()
        val byMonth = LinkedHashMap<String, JsonElement>()
        for (element in array) {
            val record = element as? JsonObject ?: continue
            val month = (record["m"] as? JsonPrimitive)?.contentOrNull
                ?.takeIf { MONTH_REGEX.matches(it) } ?: continue
            val reading = (record["r"] as? JsonPrimitive)?.contentOrNull
                ?.toDoubleOrNull() ?: continue
            if (!reading.isFinite() || reading < 0.0) continue
            val ts = (record["ts"] as? JsonPrimitive)?.longOrNull ?: 0L
            val rounded = round2(reading)
            // 与网页 `Math.round(r*100)/100` 的数字形态保持一致：整数读数输出 80 而不是 80.0，
            // 这样归一前后 JSON 语义/字节都稳定（避免误判脏 section）。
            val readingElement = if (rounded % 1.0 == 0.0 && kotlin.math.abs(rounded) < 1e15) {
                JsonPrimitive(rounded.toLong())
            } else {
                JsonPrimitive(rounded)
            }
            byMonth[month] = buildJsonObject {
                put("m", JsonPrimitive(month))
                put("r", readingElement)
                put("ts", JsonPrimitive(ts))
            }
        }
        var months = byMonth.keys.sorted()
        if (months.size > MAX_RECORDS) months = months.takeLast(MAX_RECORDS)
        return months.map { byMonth.getValue(it) }
    }

    /** 读数保留 2 位小数（与网页 `Math.round(r*100)/100` 一致）。 */
    fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0
}
