package com.furina.classtools.features.water

import java.time.ZoneId
import java.util.Locale

/**
 * 水量计算（纯函数，口径见 `_water_contract.md` §0/§7）：
 * - 本期用量 = 本次读数 − 该宿舍月份升序的上一条读数；
 * - 水费 = `max(0, 用量 − 2×人数) × 3.03`（2 位）；
 * - 折合扣电 = `水费 ÷ 0.58`（1 位）；
 * - 无上一条 → 首次记录（用量/水费/扣电均 null，不伪造 0）。
 */
object WaterCalculator {

    fun freeWater(persons: Int): Double =
        WaterRules.WATER_FREE_PER_PERSON * WaterCodec.normalizePersons(persons)

    fun freeElec(persons: Int): Double =
        WaterRules.ELEC_FREE_PER_PERSON * WaterCodec.normalizePersons(persons)

    /** 同宿舍、月份升序的上一条记录（不含 month 本身）。 */
    fun previousRecord(room: WaterRoomState, month: String): WaterRecord? =
        room.records.filter { it.month < month }.maxByOrNull { it.month }

    /** 同宿舍、下一条记录（用于录入校验）。 */
    fun nextRecord(room: WaterRoomState, month: String): WaterRecord? =
        room.records.filter { it.month > month }.minByOrNull { it.month }

    fun recordOf(room: WaterRoomState, month: String): WaterRecord? =
        room.records.firstOrNull { it.month == month }

    /** 某条记录的用量/水费/折合扣电。 */
    fun charge(
        room: WaterRoomState,
        record: WaterRecord,
        personsOverride: Int? = null,
    ): WaterCharge {
        val persons = WaterCodec.normalizePersons(personsOverride ?: room.persons)
        val free = freeWater(persons)
        val previous = previousRecord(room, record.month)
            ?: return WaterCharge(
                usage = null,
                free = free,
                excess = null,
                fee = null,
                degree = null,
                within = false,
                hasPrevious = false,
            )
        val usage = WaterCodec.round2(maxOf(0.0, record.reading - previous.reading))
        val excess = WaterCodec.round2(maxOf(0.0, usage - free))
        val fee = WaterCodec.round2(excess * WaterRules.WATER_PRICE)
        val degree = WaterCodec.round1(fee / WaterRules.ELEC_PRICE)
        return WaterCharge(
            usage = usage,
            free = free,
            excess = excess,
            fee = fee,
            degree = degree,
            within = excess <= 0.001,
            hasPrevious = true,
        )
    }

    /** 某个月份的统计卡数据。 */
    fun stats(
        room: WaterRoomState,
        month: String,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): WaterMonthStats {
        val safeMonth = WaterCodec.normalizeMonth(month) ?: WaterMonth.current(nowMillis, zone)
        val persons = WaterCodec.normalizePersons(room.persons)
        val record = recordOf(room, safeMonth)
        val previous = previousRecord(room, safeMonth)
        val charge = if (record != null) {
            charge(room, record, persons)
        } else {
            WaterCharge(
                usage = null,
                free = freeWater(persons),
                excess = null,
                fee = null,
                degree = null,
                within = false,
                hasPrevious = previous != null,
            )
        }

        val dailyAverage = charge.usage?.let { usage ->
            val days = WaterMonth.elapsedDays(safeMonth, nowMillis, zone).coerceAtLeast(1)
            WaterCodec.round2(usage / days)
        }

        val previousMonth = WaterMonth.shift(safeMonth, -1)
        val previousMonthRecord = recordOf(room, previousMonth)
        val previousMonthUsage = previousMonthRecord
            ?.let { charge(room, it, persons) }
            ?.usage
        val delta = if (charge.usage != null && previousMonthUsage != null) {
            WaterCodec.round2(charge.usage - previousMonthUsage)
        } else {
            null
        }

        return WaterMonthStats(
            month = safeMonth,
            persons = persons,
            record = record,
            previous = previous,
            charge = charge,
            dailyAverage = dailyAverage,
            previousMonthUsage = previousMonthUsage,
            deltaVsPreviousMonth = delta,
        )
    }

    fun fmtReading(value: Double?): String =
        if (value == null || !value.isFinite()) "—" else String.format(Locale.CHINA, "%.2f", value)

    fun fmtMoney(value: Double?): String =
        if (value == null || !value.isFinite()) "—" else String.format(Locale.CHINA, "%.2f", value)

    fun fmtDegree(value: Double?): String =
        if (value == null || !value.isFinite()) "—" else String.format(Locale.CHINA, "%.1f", value)

    /** 规则卡文案（短句，与网页一致）。 */
    fun ruleLines(persons: Int): List<Pair<String, String>> {
        val safe = WaterCodec.normalizePersons(persons)
        val quota = freeWater(safe)
        return listOf(
            "单价" to "水 ${WaterRules.WATER_PRICE} 元/吨 · 电 ${WaterRules.ELEC_PRICE} 元/度",
            "免费额度" to "每人 ${WaterRules.WATER_FREE_PER_PERSON.toInt()} 吨/月（$safe 人间 = ${quota.toInt()} 吨）",
            "水费公式" to "超出定额部分 × ${WaterRules.WATER_PRICE}（定额 $quota 吨）",
            "折合扣电" to "水费 ÷ ${WaterRules.ELEC_PRICE}（1 吨水 ≈ ${String.format(Locale.CHINA, "%.1f", WaterRules.TON_TO_DEGREE)} 度）",
            "计费周期" to "无阶梯、无额外加价；定额当月有效、每学年 ${WaterRules.CYCLE_MONTHS} 个月，寒暑假无",
            "以学校最新通知为准" to "",
        )
    }
}
