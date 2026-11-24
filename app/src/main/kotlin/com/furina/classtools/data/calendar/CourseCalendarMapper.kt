package com.furina.classtools.data.calendar

import com.furina.classtools.data.timetable.Course
import com.furina.classtools.data.timetable.TtCalendar
import com.furina.classtools.data.timetable.TtPeriods
import com.furina.classtools.data.timetable.TtSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * 课表 → 系统日历事件映射（纯函数，可单测）。
 *
 * 口径：
 * - 每门课按 `day`（0=周一）+ 节次 `start..end` 落到具体日期，时间取 `TtPeriods` 固定时间表；
 * - 周次说明写入描述（`第 N 周 · 单/双周 · 原周次表达式`）；仅映射在 `weeks` 范围内且单双周匹配的周；
 * - 标题=课程名，地点=教室，描述=教师 + 周次说明 + 来源标记；
 * - 默认提前 [CalendarEvent.DEFAULT_REMINDER_MINUTES] 分钟提醒；时区=本机。
 */
object CourseCalendarMapper {

    fun eventFor(
        course: Course,
        week: Int,
        termStart: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        reminderMinutes: Int = CalendarEvent.DEFAULT_REMINDER_MINUTES,
    ): CalendarEvent? {
        if (course.name.isBlank()) return null
        if (week < 1) return null
        if (!TtCalendar.isActive(course, week)) return null
        if (course.start !in 1..TtPeriods.COUNT || course.end < course.start) return null
        val weekRange = TtCalendar.weekRange(termStart, week) ?: return null
        val date = weekRange.first.plusDays(course.day.toLong().coerceIn(0L, 6L))
        val startTime = parseTime(TtPeriods.startText(course.start)) ?: return null
        val endTime = parseTime(TtPeriods.endText(course.end)) ?: return null
        val begin = LocalDateTime.of(date, startTime).atZone(zone).toInstant().toEpochMilli()
        val end = LocalDateTime.of(date, endTime).atZone(zone).toInstant().toEpochMilli()
        if (end <= begin) return null
        return CalendarEvent(
            title = course.name.trim(),
            beginMillis = begin,
            endMillis = end,
            description = descriptionOf(course, week),
            location = course.room.trim(),
            reminderMinutes = reminderMinutes,
            timeZone = zone.id,
            sourceKey = "course:${course.name}:${course.day}:${course.start}:$date",
        )
    }

    /** 某一教学周的全部课程事件（按开始时间升序；无开学日/无课程 → 空）。 */
    fun eventsForWeek(
        snapshot: TtSnapshot,
        week: Int,
        zone: ZoneId = ZoneId.systemDefault(),
        reminderMinutes: Int = CalendarEvent.DEFAULT_REMINDER_MINUTES,
    ): List<CalendarEvent> {
        val termStart = TtCalendar.parseTermStart(snapshot.termStart) ?: return emptyList()
        return snapshot.c
            .mapNotNull { eventFor(it, week, termStart, zone, reminderMinutes) }
            .sortedBy { it.beginMillis }
    }

    /**
     * 从「当前周」开始映射 [weeksAhead] 个教学周（默认 2 周，最多 20 周）。
     * @param nowMillis 用于计算当前教学周（开学前按第 1 周）
     */
    fun eventsForNextWeeks(
        snapshot: TtSnapshot,
        nowMillis: Long = System.currentTimeMillis(),
        weeksAhead: Int = DEFAULT_WEEKS_AHEAD,
        zone: ZoneId = ZoneId.systemDefault(),
        reminderMinutes: Int = CalendarEvent.DEFAULT_REMINDER_MINUTES,
    ): List<CalendarEvent> {
        val termStart = TtCalendar.parseTermStart(snapshot.termStart) ?: return emptyList()
        if (snapshot.c.isEmpty()) return emptyList()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val currentWeek = TtCalendar.weekOf(termStart, today)?.coerceAtLeast(1) ?: 1
        val weeks = weeksAhead.coerceIn(1, MAX_WEEKS_AHEAD)
        return (currentWeek until currentWeek + weeks)
            .flatMap { week -> eventsForWeek(snapshot, week, zone, reminderMinutes) }
            .sortedBy { it.beginMillis }
            .distinctBy { it.sourceKey }
    }

    /** 周次说明：`第 3 周 · 单周 · 1-16`。 */
    fun descriptionOf(course: Course, week: Int): String {
        val parity = when (course.parity) {
            "odd" -> "单周"
            "even" -> "双周"
            else -> "每周"
        }
        val weeks = course.weeks.trim()
        val weekText = buildString {
            append("第 $week 周")
            append(" · ")
            append(parity)
            if (weeks.isNotEmpty()) append(" · 周次 $weeks")
        }
        return buildString {
            append(weekText)
            if (course.teacher.isNotBlank()) {
                append('\n')
                append("教师：")
                append(course.teacher.trim())
            }
            append('\n')
            append("来自枫丹工具箱课表")
        }
    }

    /** 事件标题（供日历列表展示；固定加「课」前缀避免与个人日程混淆）。 */
    fun titleOf(course: Course): String = course.name.trim()

    /** 当前教学周（开学前/无开学日为 null）。 */
    fun currentWeek(snapshot: TtSnapshot, nowMillis: Long, zone: ZoneId): Int? {
        val termStart = TtCalendar.parseTermStart(snapshot.termStart) ?: return null
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return TtCalendar.weekOf(termStart, today)
    }

    const val DEFAULT_WEEKS_AHEAD: Int = 2
    const val MAX_WEEKS_AHEAD: Int = 20

    private fun parseTime(text: String): LocalTime? =
        runCatching { LocalTime.parse(text.trim(), DateTimeFormatterHolder.HH_MM) }.getOrNull()

    private object DateTimeFormatterHolder {
        val HH_MM: java.time.format.DateTimeFormatter =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    }

    @Suppress("unused")
    private val unusedDate = LocalDate.MIN
}
