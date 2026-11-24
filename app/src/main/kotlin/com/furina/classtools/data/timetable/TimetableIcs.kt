package com.furina.classtools.data.timetable

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 课表 → `.ics`（RFC 5545）日历文件生成（纯函数，可单测）。
 *
 * 生成策略（与 WakeUp 的 ICS 导出同思路，但按本项目课表结构重写）：
 * - 时区固定 `Asia/Shanghai`：`DTSTART;TZID=Asia/Shanghai:...` + 内置 VTIMEZONE（+0800）；
 * - **每门课每个连续周次段一个 VEVENT**：连续周次用 `RRULE:FREQ=WEEKLY;INTERVAL=1;UNTIL=...`，
 *   单周/双周用 `INTERVAL=2`（`UNTIL` 按最后一次课的结束时刻换算成 UTC）；
 * - `VALARM` 提前 [TimetableLink.DEFAULT_REMINDER_MINUTES] 分钟（可传 30）`ACTION:DISPLAY`；
 * - `UID` 由学期+课程+周次段哈希得到（[TimetableLink.icsUid]）：重复导出/重新导入时系统日历更新同一系列，
 *   不会产生重复日程；
 * - 文本按 RFC 5545 转义（`\\` `;` `,` 换行）并按 **UTF-8 字节数 75 折叠**（中文不会写出超长行）。
 *
 * 说明：ICS 本身不是「写入系统日历」——系统日历写入见 `TimetableCalendarSync`（CalendarContract），
 * ICS 用于分享给系统日历/WakeUp/其他日历应用导入。
 */
object TimetableIcs {

    /** 默认日历名（写入 `X-WR-CALNAME`）。 */
    const val CALENDAR_NAME: String = "班级课表"

    /** 生成结果：`text` 为完整 ICS 内容（CRLF 行尾，以 CRLF 结束）。 */
    data class IcsDocument(
        val text: String,
        val eventCount: Int,
        val reminderMinutes: Int,
        val maxWeek: Int,
    )

    /**
     * 生成 ICS。
     *
     * @param onlyWeek 非空时只导出该教学周的课（一次性事件，不带 RRULE；用于「当前周」导出）；
     *   为空（默认）导出整学期（每门课每个连续周次段一个 RRULE 系列）
     * @return 无开学日 / 无课程 / 无合法事件时返回 null（由 UI 给出「开学日未知，暂时无法生成」提示）
     */
    fun build(
        snapshot: TtSnapshot,
        zone: ZoneId = TimetableLink.CHINA_ZONE,
        reminderMinutes: Int = TimetableLink.DEFAULT_REMINDER_MINUTES,
        maxWeeks: Int = TimetableLink.semesterWeeks(snapshot.c),
        nowMillis: Long = System.currentTimeMillis(),
        calendarName: String = CALENDAR_NAME,
        onlyWeek: Int? = null,
    ): IcsDocument? {
        val termStart = TtCalendar.parseTermStart(snapshot.termStart) ?: return null
        if (snapshot.c.isEmpty()) return null
        val weeks = maxWeeks.coerceIn(1, TimetableLink.MAX_SEMESTER_WEEKS)
        val reminder = reminderMinutes.coerceAtLeast(0)
        val events = ArrayList<PendingEvent>()
        for (course in snapshot.c) {
            if (!validCourse(course)) continue
            val groups = if (onlyWeek == null) {
                TimetableLink.weekGroups(course, weeks)
            } else {
                if (onlyWeek in 1..weeks && TtCalendar.isActive(course, onlyWeek)) {
                    listOf(TimetableLink.WeekGroup(first = onlyWeek, last = onlyWeek, step = 1))
                } else {
                    emptyList()
                }
            }
            for (group in groups) {
                if (group.size <= 0) continue
                val lines = eventLines(
                    termStart = termStart,
                    course = course,
                    group = group,
                    zone = zone,
                    reminderMinutes = reminder,
                    nowMillis = nowMillis,
                ) ?: continue
                val sortKey = termStart.toEpochDay() + ((group.first - 1) * 7L) + course.day
                events.add(PendingEvent(sortKey, lines))
                if (events.size >= TimetableLink.MAX_ICS_EVENTS) break
            }
            if (events.size >= TimetableLink.MAX_ICS_EVENTS) break
        }
        if (events.isEmpty()) return null
        events.sortWith(compareBy({ it.sortKey }, { it.lines.firstOrNull().orEmpty() }))
        val builder = StringBuilder(4096)
        header(zone, calendarName).forEach { builder.append(foldLine(it)).append(CRLF) }
        events.forEach { event ->
            event.lines.forEach { line -> builder.append(foldLine(line)).append(CRLF) }
        }
        builder.append("END:VCALENDAR").append(CRLF)
        return IcsDocument(
            text = builder.toString(),
            eventCount = events.size,
            reminderMinutes = reminder,
            maxWeek = weeks,
        )
    }

    // =========================================================================
    // 单事件
    // =========================================================================

    private data class PendingEvent(val sortKey: Long, val lines: List<String>)

    private fun header(zone: ZoneId, calendarName: String): List<String> = listOf(
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//class-tools//timetable-ics//CN",
        "CALSCALE:GREGORIAN",
        "METHOD:PUBLISH",
        "X-WR-CALNAME:${escapeText(calendarName)}",
        "X-WR-TIMEZONE:${zone.id}",
        "BEGIN:VTIMEZONE",
        "TZID:${zone.id}",
        "X-LIC-LOCATION:${zone.id}",
        "BEGIN:STANDARD",
        "TZOFFSETFROM:+0800",
        "TZOFFSETTO:+0800",
        "TZNAME:CST",
        "DTSTART:19700101T000000",
        "END:STANDARD",
        "END:VTIMEZONE",
    )

    private fun eventLines(
        termStart: LocalDate,
        course: Course,
        group: TimetableLink.WeekGroup,
        zone: ZoneId,
        reminderMinutes: Int,
        nowMillis: Long,
    ): List<String>? {
        val startTime = parseTime(TtPeriods.startText(course.start)) ?: return null
        val endTime = parseTime(TtPeriods.endText(course.end)) ?: return null
        val firstDate = termStart.plusDays(((group.first - 1) * 7L) + course.day)
        val lastDate = termStart.plusDays(((group.last - 1) * 7L) + course.day)
        val start = LocalDateTime.of(firstDate, startTime).atZone(zone)
        val end = LocalDateTime.of(firstDate, endTime).atZone(zone)
        val until = LocalDateTime.of(lastDate, endTime).atZone(zone).toInstant()
        val lines = ArrayList<String>(18)
        lines.add("BEGIN:VEVENT")
        lines.add("UID:${TimetableLink.icsUid(termStart, course, group)}")
        lines.add("DTSTAMP:${formatUtc(Instant.ofEpochMilli(nowMillis))}")
        lines.add("DTSTART;TZID=${zone.id}:${formatLocal(start.toLocalDateTime())}")
        lines.add("DTEND;TZID=${zone.id}:${formatLocal(end.toLocalDateTime())}")
        if (group.size > 1) {
            lines.add("RRULE:FREQ=WEEKLY;INTERVAL=${group.step};UNTIL=${formatUtc(until)}")
        }
        lines.add("SUMMARY:${escapeText(course.name.trim())}")
        if (course.room.isNotBlank()) lines.add("LOCATION:${escapeText(course.room.trim())}")
        lines.add("DESCRIPTION:${escapeText(description(course, group))}")
        lines.add("STATUS:CONFIRMED")
        lines.add("TRANSP:OPAQUE")
        if (reminderMinutes > 0) {
            lines.add("BEGIN:VALARM")
            lines.add("ACTION:DISPLAY")
            lines.add("DESCRIPTION:${escapeText("${course.name.trim()} ${reminderMinutes} 分钟后上课")}")
            lines.add("TRIGGER:-PT${reminderMinutes}M")
            lines.add("END:VALARM")
        }
        lines.add("END:VEVENT")
        return lines
    }

    /** 事件描述（多行，输出前统一转义）。 */
    fun description(course: Course, group: TimetableLink.WeekGroup): String = buildString {
        append("节次：")
        append(periodText(course.start, course.end))
        append('\n')
        append("时间：")
        append(TtPeriods.startText(course.start))
        append('-')
        append(TtPeriods.endText(course.end))
        append('\n')
        append("周次：")
        append(TimetableLink.weekText(course))
        if (group.step == 2) {
            append("（第 ")
            append(group.first)
            append('-')
            append(group.last)
            append(" 周）")
        }
        if (course.teacher.isNotBlank()) {
            append('\n')
            append("教师：")
            append(course.teacher.trim())
        }
        append('\n')
        append("来自枫丹工具箱课表")
    }.trimEnd()

    private fun validCourse(course: Course): Boolean =
        course.name.isNotBlank() &&
            course.day in 0..6 &&
            course.start in 1..TtPeriods.COUNT &&
            course.end in course.start..TtPeriods.COUNT

    private fun periodText(start: Int, end: Int): String =
        if (end > start) "第${start}-${end}节" else "第${start}节"

    // =========================================================================
    // RFC 5545 文本规则（公开便于单测）
    // =========================================================================

    /** 内容行转义：`\` `;` `,` 与换行；其余控制字符丢弃。 */
    fun escapeText(text: String): String = buildString(text.length + 8) {
        for (ch in text) {
            when (ch) {
                '\\' -> append("\\\\")
                ';' -> append("\\;")
                ',' -> append("\\,")
                '\n' -> append("\\n")
                '\r' -> Unit
                else -> if (!ch.isISOControl()) append(ch)
            }
        }
    }

    /**
     * 内容行折叠：单行 UTF-8 **不超过 75 字节**；超长按「一行 75 字节 / 续行以单个空格开头」规则折行。
     * 续行最多 74 字节内容（1 字节空格 + 74 = 75）。中文按 UTF-8 3 字节计，不会把字符切成半个。
     */
    fun foldLine(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= MAX_LINE_BYTES) return line
        val out = StringBuilder(line.length + 16)
        var used = 0
        var limit = MAX_LINE_BYTES
        var index = 0
        while (index < line.length) {
            val codePoint = line.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            val piece = line.substring(index, index + charCount)
            val size = piece.toByteArray(Charsets.UTF_8).size
            if (used + size > limit) {
                out.append(CRLF).append(' ')
                used = 0
                limit = MAX_LINE_BYTES - 1
            }
            out.append(piece)
            used += size
            index += charCount
        }
        return out.toString()
    }

    // =========================================================================
    // 时间格式
    // =========================================================================

    /** `yyyyMMdd'T'HHmmss`（本地时间，配合 `;TZID=` 使用）。 */
    fun formatLocal(dateTime: LocalDateTime): String = LOCAL_FORMAT.format(dateTime)

    /** `yyyyMMdd'T'HHmmss'Z'`（UTC，用于 DTSTAMP/UNTIL）。 */
    fun formatUtc(instant: Instant): String = UTC_FORMAT.format(instant)

    private fun parseTime(text: String): LocalTime? = runCatching {
        val parts = text.trim().split(':')
        LocalTime.of(parts[0].toIntOrNull() ?: 0, parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }.getOrNull()

    /** RFC 5545 行终止符。 */
    const val CRLF: String = "\r\n"

    private const val MAX_LINE_BYTES: Int = 75

    private val LOCAL_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.US)

    private val UTC_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US).withZone(ZoneOffset.UTC)
}
