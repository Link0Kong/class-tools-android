package com.furina.classtools.features.water

import java.util.Locale

/**
 * 水量状态变更（纯函数）：
 * - 录入：月份合法、读数 ≥0、2 位小数；**不得小于上一条、不得大于下一条**；同月覆盖需确认；
 * - 删除：按月份删除；
 * - 人数切换：1–6 人间（影响免费额度与计费）；
 * - 每次变更返回新 [WaterState]（不可变，整段 LWW 写回 userdata）。
 */
object WaterMutator {

    fun review(
        room: WaterRoomState,
        monthRaw: String,
        readingRaw: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): WaterRecordReview {
        val month = WaterCodec.normalizeMonth(monthRaw)
            ?: return WaterRecordReview.Invalid("月份格式应为 YYYY-MM")
        val reading = readingRaw.trim().replace(" ", "").toDoubleOrNull()
            ?: return WaterRecordReview.Invalid("请输入水表读数（吨）")
        if (!reading.isFinite() || reading < 0.0) {
            return WaterRecordReview.Invalid("读数需为 ≥0 的数字")
        }
        val rounded = WaterCodec.round2(reading)
        val previous = WaterCalculator.previousRecord(room, month)
        if (previous != null && rounded < previous.reading) {
            return WaterRecordReview.Invalid(
                "读数不能小于上月（${previous.month}）的 ${formatReading(previous.reading)} 吨",
            )
        }
        val next = WaterCalculator.nextRecord(room, month)
        if (next != null && rounded > next.reading) {
            return WaterRecordReview.Invalid(
                "读数不能大于下一条记录（${next.month}）的 ${formatReading(next.reading)} 吨",
            )
        }
        val existing = room.records.firstOrNull { it.month == month }
        return WaterRecordReview.Valid(
            record = WaterRecord(month = month, reading = rounded, ts = nowMillis),
            replaceExisting = existing != null,
        )
    }

    /** 写入/覆盖一条记录（同月覆盖，返回新状态）。 */
    fun put(
        state: WaterState,
        roomVerify: String,
        record: WaterRecord,
    ): WaterState {
        val key = roomVerify.trim()
        if (key.isEmpty()) return state
        val month = WaterCodec.normalizeMonth(record.month) ?: return state
        val normalized = WaterRecord(month = month, reading = WaterCodec.round2(record.reading), ts = record.ts)
        val room = state.roomOrDefault(key)
        val merged = LinkedHashMap<String, WaterRecord>()
        room.records.forEach { merged[it.month] = it }
        merged[month] = normalized
        var months = merged.keys.sorted()
        if (months.size > WaterRules.MAX_RECORDS_PER_ROOM) {
            months = months.takeLast(WaterRules.MAX_RECORDS_PER_ROOM)
        }
        val records = months.map { merged.getValue(it) }
        val nextRoom = room.copy(records = records)
        val rooms = LinkedHashMap(state.rooms)
        rooms[key] = nextRoom
        return WaterState(rooms)
    }

    /** 删除某月记录。 */
    fun delete(state: WaterState, roomVerify: String, month: String): WaterState {
        val key = roomVerify.trim()
        val safeMonth = WaterCodec.normalizeMonth(month) ?: return state
        val room = state.rooms[key] ?: return state
        val records = room.records.filterNot { it.month == safeMonth }
        if (records.size == room.records.size) return state
        val rooms = LinkedHashMap(state.rooms)
        rooms[key] = room.copy(records = records)
        return WaterState(rooms)
    }

    /** 切换 1–6 人间（影响免费额度与计费；历史记录不变）。 */
    fun withPersons(state: WaterState, roomVerify: String, persons: Int): WaterState {
        val key = roomVerify.trim()
        if (key.isEmpty()) return state
        val room = state.roomOrDefault(key)
        val safePersons = WaterCodec.normalizePersons(persons)
        if (room.persons == safePersons && state.rooms.containsKey(key)) return state
        val rooms = LinkedHashMap(state.rooms)
        rooms[key] = room.copy(persons = safePersons)
        return WaterState(rooms)
    }

    /** 删除整间房间数据（隐私：清除本机缓存用）。 */
    fun clearRoom(state: WaterState, roomVerify: String): WaterState {
        val key = roomVerify.trim()
        if (!state.rooms.containsKey(key)) return state
        val rooms = LinkedHashMap(state.rooms)
        rooms.remove(key)
        return WaterState(rooms)
    }

    fun formatReading(value: Double): String = String.format(Locale.CHINA, "%.2f", value)
}
