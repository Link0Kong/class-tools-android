package com.furina.classtools.data.timetable

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.floor

/**
 * 课表日历纯函数（与小程序 `utils/ttplan.js` 同口径；契约 docs/BACKEND_CONTRACT.md §7.4）。
 *
 * 全部函数无 Android 依赖、无副作用，可注入 [ZoneId] 与「现在」时间戳，便于单测。
 * - 周次：`week = floor(days(today - termStart) / 7) + 1`（开学当周为第 1 周；开学前 = null）。
 * - 上课判定：`weeks` 为空 = 全学期；非空时需包含该周；再按 `parity` 过滤单双周。
 * - 「下一节」：今天起 14 天内、按真实时间找最近一节（今天已结束的跳过）。
 */
object TtCalendar {

    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 解析开学日（容忍前 10 位是 YYYY-MM-DD 的 ISO 字符串）。 */
    fun parseTermStart(raw: String): LocalDate? {
        val text = raw.trim()
        if (text.length < 10) return null
        return runCatching { LocalDate.parse(text.substring(0, 10), DATE_FORMATTER) }.getOrNull()
    }

    /** 解析周次表达式："1-16,18" / "2-4,6-9" / "1~16周"；与网页端 parseWeeks 同口径。 */
    fun parseWeeks(spec: String): Set<Int> {
        val text = spec.replace("周", "").trim()
        if (text.isEmpty()) return emptySet()
        val out = LinkedHashSet<Int>()
        text.split(',', '，', ';', '；', ' ').forEach { tokenRaw ->
            val token = tokenRaw.trim().replace('~', '-').replace('—', '-')
            if (token.isEmpty()) return@forEach
            val dash = token.indexOf('-')
            if (dash > 0 && dash < token.length - 1) {
                val a = token.substring(0, dash).trim().toIntOrNull()
                val b = token.substring(dash + 1).trim().toIntOrNull()
                if (a != null && b != null) {
                    val lo = minOf(a, b)
                    val hi = maxOf(a, b)
                    for (w in lo..hi) if (w in 1..60) out.add(w)
                }
            } else {
                token.toIntOrNull()?.let { w -> if (w in 1..60) out.add(w) }
            }
        }
        return out
    }

    /** 指定日期属于第几周（开学当周=1；开学前或开学日无效 → null）。 */
    fun weekOf(termStart: LocalDate?, date: LocalDate): Int? {
        val term = termStart ?: return null
        val diffDays = (date.toEpochDay() - term.toEpochDay()).toInt()
        val week = floor(diffDays / 7.0).toInt() + 1
        return week.takeIf { it > 0 }
    }

    /** 某门课在第 [week] 周是否上课（week=null 视为不按周过滤）。 */
    fun isActive(course: Course, week: Int?): Boolean {
        if (week == null) return true
        val weeks = parseWeeks(course.weeks)
        if (weeks.isNotEmpty() && week !in weeks) return false
        return when (course.parity) {
            "odd" -> week % 2 == 1
            "even" -> week % 2 == 0
            else -> true
        }
    }

    /** 某日期（本地时区）的星期索引：0=周一 … 6=周日。 */
    fun dayIndex(date: LocalDate): Int = date.dayOfWeek.value - 1

    /** 今天（按 [nowMillis] 与时区）的星期索引。 */
    fun todayIndex(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        dayIndex(Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate())

    /** 教室短标识：优先取房间号（如「自强楼(教室01105)」→ "01105"）。 */
    fun roomShort(room: String): String {
        val s = room.trim()
        if (s.isEmpty()) return ""
        if (s.length <= 7) return s
        Regex("""\d{3,6}""").find(s)?.let { return it.value }
        Regex("""\d+-\d+""").find(s)?.let { return it.value }
        return s.take(7) + "…"
    }

    /**
     * 指定日期（[date]）某门课的全部占用，按开始节次排序。
     *
     * @param nowMillis 「现在」时间戳，用于计算 NEXT/ONGOING/DONE 状态
     */
    fun sessionsOn(
        courses: List<Course>,
        termStart: LocalDate?,
        date: LocalDate,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        focusOnBell: Boolean = false,
        upcomingBell: Boolean = false,
    ): List<SessionInfo> {
        val week = weekOf(termStart, date)
        val di = dayIndex(date)
        val now = Instant.ofEpochMilli(nowMillis)
        val out = ArrayList<SessionInfo>()
        for (course in courses) {
            if (course.day != di) continue
            if (!isActive(course, week)) continue
            val startPeriod = TtPeriods.PERIODS.getOrNull(course.start - 1) ?: continue
            val endPeriod = TtPeriods.PERIODS.getOrNull(course.end - 1) ?: continue
            val clock = now.atZone(zone)
            val focus = if (focusOnBell && date == clock.toLocalDate() && clock.toLocalTime() >= LocalTime.parse(startPeriod.first))
                (course.start..course.end).firstOrNull {
                    val boundary = if (upcomingBell) TtPeriods.startText(it) else TtPeriods.endText(it)
                    clock.toLocalTime() < LocalTime.parse(boundary)
                }
            else null
            val activeSlot = focus?.let { TtPeriods.PERIODS[it - 1] }
            val startTime = parseTime(activeSlot?.first ?: startPeriod.first) ?: continue
            val endTime = parseTime(activeSlot?.second ?: endPeriod.second) ?: continue
            val startAt = LocalDateTime.of(date, startTime).atZone(zone).toInstant()
            val endAt = LocalDateTime.of(date, endTime).atZone(zone).toInstant()
            val state = when {
                !now.isBefore(endAt) -> SessionState.DONE
                !now.isBefore(startAt) -> SessionState.ONGOING
                else -> SessionState.NEXT
            }
            out.add(
                SessionInfo(
                    name = course.name.ifBlank { "课程" },
                    room = roomShort(course.room),
                    fullRoom = course.room.trim(),
                    start = course.start,
                    end = course.end,
                    startText = activeSlot?.first ?: startPeriod.first,
                    endText = activeSlot?.second ?: endPeriod.second,
                    span = if (focus != null) spanText(focus, focus) else spanText(course.start, course.end),
                    startAt = startAt.toEpochMilli(),
                    endAt = endAt.toEpochMilli(),
                    state = state,
                ),
            )
        }
        out.sortBy { it.startAt }
        return out
    }

    /** 今天起 [lookAheadDays] 天内最近的一节课（今天已开始的已结束节次跳过）。 */
    fun nextSession(
        courses: List<Course>,
        termStart: LocalDate?,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        lookAheadDays: Int = 14,
    ): SessionInfo? {
        val now = Instant.ofEpochMilli(nowMillis)
        val today = now.atZone(zone).toLocalDate()
        for (off in 0 until lookAheadDays) {
            val date = today.plusDays(off.toLong())
            if (weekOf(termStart, date) == null) continue
            val candidate = sessionsOn(courses, termStart, date, nowMillis, zone, focusOnBell = true, upcomingBell = true)
                .firstOrNull { it.startAt > now.toEpochMilli() }
                ?: continue
            val dayText = when (off) {
                0 -> "今天"
                1 -> "明天"
                else -> TtPeriods.DAY_LABELS[dayIndex(date)]
            }
            return candidate.copy(
                dayOffset = off,
                dayText = dayText,
                dateText = "${date.monthValue}/${date.dayOfMonth}",
                label = "$dayText ${candidate.span}",
            )
        }
        return null
    }

    /** 工作台/页面入口：一次算出 周次 + 今日课程 + 今日状态 + 下一节。 */
    fun plan(
        snapshot: TtSnapshot,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): TtPlan {
        val courses = snapshot.c
        val termStart = parseTermStart(snapshot.termStart)
        val hasData = courses.isNotEmpty() && termStart != null
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val week = weekOf(termStart, today)
        if (!hasData) {
            return TtPlan(hasData = false, week = week, today = emptyList(), todayState = TodayState.NODATA, next = null)
        }
        val sessions = if (week == null) emptyList() else sessionsOn(courses, termStart, today, nowMillis, zone, focusOnBell = true)
        val todayState = when {
            sessions.isEmpty() -> TodayState.NONE
            sessions.any { it.state != SessionState.DONE } -> TodayState.HAS
            else -> TodayState.DONE
        }
        return TtPlan(
            hasData = true,
            week = week,
            today = sessions,
            todayState = todayState,
            next = nextSession(courses, termStart, nowMillis, zone),
        )
    }

    /** 第 [week] 周的日期范围（周一~周日）；开学日无效返回 null。 */
    fun weekRange(termStart: LocalDate?, week: Int): Pair<LocalDate, LocalDate>? {
        val term = termStart ?: return null
        if (week < 1) return null
        val monday = term.plusDays(((week - 1) * 7).toLong())
        return monday to monday.plusDays(6)
    }

    /** 「第 N 周 · MM/dd-MM/dd」标题；无开学日时回退到「课表」。 */
    fun weekTitle(termStart: LocalDate?, week: Int?): String {
        if (week == null) return "课表"
        val range = weekRange(termStart, week) ?: return "第 $week 周"
        return "第 $week 周 · ${range.first.monthValue}/${range.first.dayOfMonth}-${range.second.monthValue}/${range.second.dayOfMonth}"
    }

    /** 第 [week] 周内的周一日期（用于周视图日期列头）；无效返回 null。 */
    fun mondayOf(termStart: LocalDate?, week: Int): LocalDate? = weekRange(termStart, week)?.first

    /** 课程在指定周是否在该星期几有课（周视图用）。 */
    fun coursesOnDay(courses: List<Course>, termStart: LocalDate?, week: Int?, dayIndex: Int): List<Course> =
        courses.filter { it.day == dayIndex && isActive(it, week) }

    private fun parseTime(text: String): LocalTime? = runCatching {
        val parts = text.trim().split(':')
        LocalTime.of(parts[0].toIntOrNull() ?: 0, parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }.getOrNull()

    private fun spanText(start: Int, end: Int): String =
        if (end > start) "第${start}-${end}节" else "第${start}节"

    /** 星期索引 → 中文（越界回退空串）。 */
    fun dayLabel(dayIndex: Int): String = TtPeriods.DAY_LABELS.getOrNull(dayIndex).orEmpty()

    /** 判断 [date] 是否为周一（测试/周计算辅助）。 */
    fun isMonday(date: LocalDate): Boolean = date.dayOfWeek == DayOfWeek.MONDAY
}

