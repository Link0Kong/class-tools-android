package com.furina.classtools.features.water

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/**
 * 月份状态机（纯函数）：`YYYY-MM` 归一/步进/年切换/12 月网格。
 *
 * 边界：年份 2000–2100（与网页 `normMonth` 一致），步进越界钳制，不抛异常。
 */
object WaterMonth {

    const val MIN_YEAR: Int = 2000
    const val MAX_YEAR: Int = 2100

    private val MONTH_REGEX = Regex("^\\d{4}-(0[1-9]|1[0-2])$")

    fun normalize(value: String?): String? {
        val text = value?.trim().orEmpty()
        if (!MONTH_REGEX.matches(text)) return null
        val year = text.substring(0, 4).toIntOrNull() ?: return null
        if (year < MIN_YEAR || year > MAX_YEAR) return null
        return text
    }

    /** 当前月份 `YYYY-MM`。 */
    fun current(
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = format(Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate())

    /** Date → `YYYY-MM`。 */
    fun format(date: java.time.LocalDate): String =
        String.format(Locale.CHINA, "%04d-%02d", date.year, date.monthValue)

    /** 月份步进（delta 可正负）；越界钳制到 2000-01 / 2100-12。 */
    fun shift(month: String, delta: Int): String {
        val normalized = normalize(month) ?: current()
        val ym = YearMonth.parse(normalized)
        var shifted = ym.plusMonths(delta.toLong())
        if (shifted.year < MIN_YEAR) shifted = YearMonth.of(MIN_YEAR, 1)
        if (shifted.year > MAX_YEAR) shifted = YearMonth.of(MAX_YEAR, 12)
        return String.format(Locale.CHINA, "%04d-%02d", shifted.year, shifted.monthValue)
    }

    /** 指定年份的 12 个月（升序）。 */
    fun monthsOfYear(year: Int): List<String> {
        val safeYear = year.coerceIn(MIN_YEAR, MAX_YEAR)
        return (1..12).map { String.format(Locale.CHINA, "%04d-%02d", safeYear, it) }
    }

    /** 换年份（保留月份；越界钳制）。 */
    fun withYear(month: String, year: Int): String {
        val normalized = normalize(month) ?: current()
        val monthPart = normalized.substring(5, 7)
        val safeYear = year.coerceIn(MIN_YEAR, MAX_YEAR)
        return "$safeYear-$monthPart"
    }

    fun yearOf(month: String): Int =
        (normalize(month) ?: current()).substring(0, 4).toInt()

    fun monthOf(month: String): Int =
        (normalize(month) ?: current()).substring(5, 7).toInt()

    /** `2026年11月`。 */
    fun label(month: String): String {
        val safe = normalize(month) ?: current()
        return "${safe.substring(0, 4)}年${safe.substring(5, 7).toInt()}月"
    }

    /** `11月`（缩写）。 */
    fun shortLabel(month: String): String = "${monthOf(month)}月"

    fun daysInMonth(month: String): Int {
        val safe = normalize(month) ?: current()
        return YearMonth.parse(safe).lengthOfMonth()
    }

    /** 当前月份已过天数（1..当月天数）；非当前月返回当月总天数。 */
    fun elapsedDays(
        month: String,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val safe = normalize(month) ?: current(nowMillis, zone)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return if (format(today) == safe) today.dayOfMonth else daysInMonth(safe)
    }

    /** 比较：a < b 返回负。 */
    fun compare(a: String, b: String): Int = a.compareTo(b)

    fun isCurrent(
        month: String,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean = month == current(nowMillis, zone)
}
