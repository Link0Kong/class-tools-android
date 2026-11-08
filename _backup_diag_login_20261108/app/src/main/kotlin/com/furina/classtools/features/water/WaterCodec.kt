package com.furina.classtools.features.water

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * `userdata.water` section 编解码（契约：`docs/api/USERDATA_V6.md` §1.2 + `_water_contract.md` §2）。
 *
 * 输出键序固定：`rooms` → `<roomverify>` → `p`、`recs` → `m`、`r`、`ts`（与网页/小程序逐字段一致）。
 * 输入容错（坏数据丢弃，不崩）：人数非 4/6 → 4；月份非法 → 丢；读数非有限/负数 → 丢；
 * 同月重复保留最后一条；按月升序；每间 ≤120 条。**整段 LWW**（不按间合并）。
 */
object WaterCodec {

    private val MONTH_REGEX = Regex("^\\d{4}-(0[1-9]|1[0-2])$")

    fun fromJson(element: JsonElement?): WaterState {
        val obj = element as? JsonObject ?: return WaterState()
        val roomsElement = obj["rooms"] as? JsonObject ?: return WaterState()
        val rooms = LinkedHashMap<String, WaterRoomState>()
        for ((roomVerify, roomElement) in roomsElement) {
            val key = roomVerify.trim()
            if (key.isEmpty()) continue
            val room = roomElement as? JsonObject ?: continue
            val persons = (room["p"] as? JsonPrimitive)?.intOrNull
                ?.takeIf { it in WaterRules.PERSONS_OPTIONS } ?: WaterRules.PERSONS_DEFAULT
            val records = normalizeRecords(room["recs"])
            rooms[key] = WaterRoomState(persons = persons, records = records)
        }
        return WaterState(rooms = rooms)
    }

    fun toJson(state: WaterState): JsonObject = buildJsonObject {
        put(
            "rooms",
            buildJsonObject {
                state.rooms.toSortedMap().forEach { (roomVerify, room) ->
                    put(
                        roomVerify,
                        buildJsonObject {
                            put("p", JsonPrimitive(normalizePersons(room.persons)))
                            put(
                                "recs",
                                JsonArray(
                                    normalizeRecords(JsonArray(room.records.map { recordToJson(it) })).map {
                                        recordToJson(it)
                                    },
                                ),
                            )
                        },
                    )
                }
            },
        )
    }

    /** 空 section（新建账号/无数据）。 */
    fun empty(): WaterState = WaterState()

    fun normalizePersons(persons: Int): Int =
        if (persons in WaterRules.PERSONS_OPTIONS) persons else WaterRules.PERSONS_DEFAULT

    fun normalizeMonth(month: String?): String? {
        val text = month?.trim().orEmpty()
        return if (MONTH_REGEX.matches(text)) text else null
    }

    /** 归一记录：合法月份+读数；同月后者覆盖；升序；≤120。 */
    fun normalizeRecords(element: JsonElement?): List<WaterRecord> {
        val array = element as? JsonArray ?: return emptyList()
        val byMonth = LinkedHashMap<String, WaterRecord>()
        for (item in array) {
            val obj = item as? JsonObject ?: continue
            val month = normalizeMonth((obj["m"] as? JsonPrimitive)?.contentOrNull) ?: continue
            val reading = (obj["r"] as? JsonPrimitive)?.let { primitive ->
                primitive.doubleOrNull ?: primitive.contentOrNull?.toDoubleOrNull()
            } ?: continue
            if (!reading.isFinite() || reading < 0.0) continue
            val ts = (obj["ts"] as? JsonPrimitive)?.longOrNull ?: 0L
            byMonth[month] = WaterRecord(month = month, reading = round2(reading), ts = ts)
        }
        var months = byMonth.keys.sorted()
        if (months.size > WaterRules.MAX_RECORDS_PER_ROOM) {
            months = months.takeLast(WaterRules.MAX_RECORDS_PER_ROOM)
        }
        return months.map { byMonth.getValue(it) }
    }

    fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0

    fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0

    private fun recordToJson(record: WaterRecord): JsonObject = buildJsonObject {
        put("m", JsonPrimitive(record.month))
        val rounded = round2(record.reading)
        if (rounded % 1.0 == 0.0 && kotlin.math.abs(rounded) < 1e15) {
            put("r", JsonPrimitive(rounded.toLong()))
        } else {
            put("r", JsonPrimitive(rounded))
        }
        put("ts", JsonPrimitive(record.ts))
    }

    private fun JsonElement?.orNull(): JsonElement? = if (this is JsonNull) null else this
}
