package com.furina.classtools.data.timetable

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 「课表接入手机」纯逻辑核心（**无 Android 依赖**，可直接 JVM 单测）。
 *
 * 目标（用户需求）：把枫丹工具箱课表「模拟 WakeUp 课表」接入手机原生能力——
 * 1. 生成 `.ics`（RFC 5545）日历文件：时区固定 `Asia/Shanghai`，每门课按周次生成 `RRULE`，
 *    `VALARM` 提前提醒；UID 稳定（同一学期同一门课重复导出不产生新事件）。
 * 2. 生成 WakeUp 兼容备份 `.wakeup_schedule`（5 行 Gson JSON；格式依据见
 *    [WakeUpSchedule] 的 KDoc）。
 * 3. 系统日历写入的「会话计划」：按真实日期/节次时间展开，供 `CalendarContract` 插入 + 提醒。
 *
 * 口径与网页/小程序一致：`TtPeriods` 固定 12 节；`weeks` 为空=全学期；`parity`=单/双周；
 * 周次表达式容忍中英文分隔符（复用 [TtCalendar.parseWeeks]）。
 *
 * 注意：学习/放假周、课表调整等不在此处理（只忠实还原快照里的 `weeks`/`parity`）。
 */
object TimetableLink {

    /** 中国大陆时区：课表节次时间固定按北京时间解释（设备时区不影响提醒）。 */
    val CHINA_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 默认课前提醒分钟数（任务口径：VALARM 提前 15 分钟；UI 另给 30 分钟选项）。 */
    const val DEFAULT_REMINDER_MINUTES: Int = 15

    /** UI 可选的提醒分钟数。 */
    val REMINDER_MINUTES_OPTIONS: List<Int> = listOf(15, 30)

    /** 课表未声明周次时按 18 周学期生成（与网页/小程序 `DEFAULT_MAX_WEEK` 一致）。 */
    const val DEFAULT_SEMESTER_WEEKS: Int = 18

    /** 学期周次上限（防止脏数据生成超长 RRULE）。 */
    const val MAX_SEMESTER_WEEKS: Int = 25

    /** 写入系统日历的描述标记：用于「本 App 写入」的幂等清理兜底（事件 id 丢失时按标记删）。 */
    const val CALENDAR_TAG: String = "#ct_timetable"

    /** ICS UID 域名后缀（稳定 UID 便于系统日历去重/更新）。 */
    const val ICS_UID_DOMAIN: String = "tool.furina.fun"

    /** 单次写入系统日历的事件上限（超大课表截断，避免系统日历卡顿）。 */
    const val MAX_CALENDAR_EVENTS: Int = 1000

    /** 单份 ICS 的事件上限（每门课每个连续周次段一个 VEVENT）。 */
    const val MAX_ICS_EVENTS: Int = 400

    // =========================================================================
    // 周次
    // =========================================================================

    /** 学期总周数：取所有课程周次表达式的最大值，至少 [DEFAULT_SEMESTER_WEEKS] 周，最多 [MAX_SEMESTER_WEEKS]。 */
    fun semesterWeeks(courses: List<Course>): Int {
        var max = DEFAULT_SEMESTER_WEEKS
        courses.forEach { course ->
            TtCalendar.parseWeeks(course.weeks).forEach { week -> if (week > max) max = week }
        }
        return max.coerceIn(1, MAX_SEMESTER_WEEKS)
    }

    /**
     * RRULE 用的连续周次段。
     *
     * @param step 1=每周；2=隔周（单周/双周）。首个周次已按单双周对齐。
     */
    data class WeekGroup(val first: Int, val last: Int, val step: Int) {
        val size: Int get() = if (last < first || step <= 0) 0 else ((last - first) / step) + 1

        fun contains(week: Int): Boolean = week in first..last && step > 0 && (week - first) % step == 0

        /** 该段覆盖的全部周次（升序）。 */
        fun weeks(): List<Int> = if (size <= 0) emptyList() else (0 until size).map { first + it * step }
    }

    /**
     * 某门课的连续周次段（用于 ICS RRULE / WakeUp 课程安排行）。
     *
     * - `weeks` 为空 → 全学期 `1..maxWeek`；
     * - `weeks` 非空 → 先取连续区间，再按 `parity` 对齐单/双周（step=2）；
     * - 非法/空区间返回空列表。
     */
    fun weekGroups(course: Course, maxWeek: Int = DEFAULT_SEMESTER_WEEKS): List<WeekGroup> {
        if (maxWeek < 1) return emptyList()
        val parsed = TtCalendar.parseWeeks(course.weeks)
        val ranges = if (parsed.isEmpty()) listOf(1..maxWeek) else contiguousRanges(parsed)
        val step = when (course.parity) {
            "odd", "even" -> 2
            else -> 1
        }
        val wantOdd = course.parity == "odd"
        return ranges.mapNotNull { range ->
            var first = range.first.coerceAtLeast(1)
            var last = range.last.coerceAtMost(maxWeek)
            if (step == 2) {
                if (first % 2 == 1 != wantOdd) first += 1
                if (last % 2 == 1 != wantOdd) last -= 1
            }
            if (first > last) null else WeekGroup(first = first, last = last, step = step)
        }.sortedBy { it.first }
    }

    /** 快照内所有课程的周次段（保持课程顺序）。 */
    fun weekGroupsOf(snapshot: TtSnapshot, maxWeek: Int = semesterWeeks(snapshot.c)): List<Pair<Course, List<WeekGroup>>> =
        snapshot.c.map { course -> course to weekGroups(course, maxWeek) }

    private fun contiguousRanges(weeks: Set<Int>): List<IntRange> {
        val sorted = weeks.filter { it in 1..MAX_SEMESTER_WEEKS }.sorted()
        if (sorted.isEmpty()) return emptyList()
        val out = ArrayList<IntRange>()
        var start = sorted.first()
        var prev = start
        for (index in 1 until sorted.size) {
            val week = sorted[index]
            if (week == prev + 1) {
                prev = week
            } else {
                out.add(start..prev)
                start = week
                prev = week
            }
        }
        out.add(start..prev)
        return out
    }

    // =========================================================================
    // 系统日历会话（真实日期/时间展开）
    // =========================================================================

    /**
     * 一次课（某个教学周的某一节次占用）。
     *
     * @param day 0=周一 … 6=周日（与 [Course.day] 一致）
     * @param startMillis/endMillis 本地时区（[CHINA_ZONE]）毫秒时间戳
     * @param sourceKey 稳定去重键（同一门课同一天同一日期唯一）
     */
    data class PlannedSession(
        val courseName: String,
        val teacher: String,
        val room: String,
        val day: Int,
        val week: Int,
        val startPeriod: Int,
        val endPeriod: Int,
        val startMillis: Long,
        val endMillis: Long,
        val sourceKey: String,
    ) {
        val periodText: String
            get() = if (endPeriod > startPeriod) "第${startPeriod}-${endPeriod}节" else "第${startPeriod}节"
    }

    /** 单门课在指定教学周、指定开学日下的一次占用；不合法（无课/脏数据）返回 null。 */
    fun sessionFor(
        course: Course,
        week: Int,
        termStart: LocalDate,
        zone: ZoneId = CHINA_ZONE,
    ): PlannedSession? {
        if (course.name.isBlank()) return null
        if (week < 1) return null
        if (course.day !in 0..6) return null
        if (course.start !in 1..TtPeriods.COUNT) return null
        if (course.end !in course.start..TtPeriods.COUNT) return null
        if (!TtCalendar.isActive(course, week)) return null
        val startTime = parseTime(TtPeriods.startText(course.start)) ?: return null
        val endTime = parseTime(TtPeriods.endText(course.end)) ?: return null
        val date = termStart.plusDays(weekOffsetDays(week, course.day))
        val startMillis = LocalDateTime.of(date, startTime).atZone(zone).toInstant().toEpochMilli()
        val endMillis = LocalDateTime.of(date, endTime).atZone(zone).toInstant().toEpochMilli()
        if (endMillis <= startMillis) return null
        return PlannedSession(
            courseName = course.name.trim(),
            teacher = course.teacher.trim(),
            room = course.room.trim(),
            day = course.day,
            week = week,
            startPeriod = course.start,
            endPeriod = course.end,
            startMillis = startMillis,
            endMillis = endMillis,
            sourceKey = "course:${course.name.trim()}:${course.day}:${course.start}:$date",
        )
    }

    /**
     * 从 [fromWeek] 到 [toWeek] 展开全部课程占用（按开始时间升序、按 [PlannedSession.sourceKey] 去重）。
     *
     * @param limit 上限（默认 [MAX_CALENDAR_EVENTS]），超出按时间顺序截断
     */
    fun planSessions(
        snapshot: TtSnapshot,
        zone: ZoneId = CHINA_ZONE,
        fromWeek: Int = 1,
        toWeek: Int = semesterWeeks(snapshot.c),
        limit: Int = MAX_CALENDAR_EVENTS,
    ): List<PlannedSession> {
        if (limit <= 0) return emptyList()
        val termStart = TtCalendar.parseTermStart(snapshot.termStart) ?: return emptyList()
        if (snapshot.c.isEmpty()) return emptyList()
        val lastWeek = toWeek.coerceIn(1, MAX_SEMESTER_WEEKS)
        val startWeek = fromWeek.coerceIn(1, lastWeek)
        val out = ArrayList<PlannedSession>()
        for (week in startWeek..lastWeek) {
            snapshot.c.forEach { course ->
                sessionFor(course, week, termStart, zone)?.let(out::add)
            }
        }
        return out.sortedBy { it.startMillis }
            .distinctBy { it.sourceKey }
            .take(limit)
    }

    /** 当前教学周（1..maxWeek；开学前/无开学日返回 null）。 */
    fun currentWeek(
        snapshot: TtSnapshot,
        nowMillis: Long,
        zone: ZoneId = CHINA_ZONE,
        maxWeek: Int = semesterWeeks(snapshot.c),
    ): Int? {
        val termStart = TtCalendar.parseTermStart(snapshot.termStart) ?: return null
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return TtCalendar.weekOf(termStart, today)?.coerceIn(1, maxWeek.coerceAtLeast(1))
    }

    private fun weekOffsetDays(week: Int, dayIndex: Int): Long = ((week - 1) * 7L) + dayIndex.toLong()

    private fun parseTime(text: String): LocalTime? = runCatching {
        val parts = text.trim().split(':')
        LocalTime.of(parts[0].toIntOrNull() ?: 0, parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }.getOrNull()

    // =========================================================================
    // 文案 / UID / 哈希
    // =========================================================================

    /** 周次说明：`1-16 周` / `1-16 周 · 单周` / `全学期 · 双周`。 */
    fun weekText(course: Course): String = buildString {
        append(course.weeks.trim().ifBlank { "全学期" })
        if (!course.weeks.isBlank()) append(" 周")
        when (course.parity) {
            "odd" -> append(" · 单周")
            "even" -> append(" · 双周")
        }
    }

    /** 日历事件描述（系统日历详情页展示；含 App 标记用于清理兜底）。 */
    fun calendarDescription(session: PlannedSession, screenTitle: String = "枫丹工具箱课表"): String = buildString {
        append(session.periodText)
        append(" · 第 ")
        append(session.week)
        append(" 周")
        if (session.teacher.isNotBlank()) {
            append('\n')
            append("教师：")
            append(session.teacher)
        }
        append('\n')
        append("来自")
        append(screenTitle)
        append(' ')
        append(CALENDAR_TAG)
    }

    /** 稳定 UID（`ct-<sha1 前 32 位>@tool.furina.fun`）：同一学期、同一门课、同一周次段重复导出不变。 */
    fun icsUid(termStart: LocalDate, course: Course, group: WeekGroup): String {
        val key = buildString {
            append(termStart)
            append('|')
            append(course.name.trim())
            append('|')
            append(course.day)
            append('|')
            append(course.start)
            append('|')
            append(course.end)
            append('|')
            append(course.weeks.trim())
            append('|')
            append(course.parity)
            append('|')
            append(group.first)
            append('-')
            append(group.last)
            append('-')
            append(group.step)
        }
        return "ct-" + sha1Hex(key).take(32) + "@" + ICS_UID_DOMAIN
    }

    /** SHA-1 十六进制（小写；纯 JVM/Android 通用）。 */
    fun sha1Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    // =========================================================================
    // 权限 / 前置判定（纯函数，便于单测）
    // =========================================================================

    /** 系统日历写入前的判定结果。 */
    enum class CalendarLinkDecision {
        /** 可写入。 */
        PROCEED,

        /** 未授权，可弹系统授权框。 */
        REQUEST_PERMISSION,

        /** 永久拒绝（或系统不允许再弹框）：只能引导系统设置。 */
        DENIED_PERMANENTLY,

        /** 有权限但本机没有可写日历（未登录日历账号等）。 */
        NO_WRITABLE_CALENDAR,

        /** 没有可写的课程（空课表/开学日未知）。 */
        NO_EVENTS,
    }

    /** 系统日历前置判定（READ + WRITE 同时授予才可写）。 */
    object CalendarLinkPolicy {

        fun hasAccess(readGranted: Boolean, writeGranted: Boolean): Boolean = readGranted && writeGranted

        fun decide(
            readGranted: Boolean,
            writeGranted: Boolean,
            writableCalendarCount: Int,
            eventCount: Int,
            permanentlyDenied: Boolean = false,
        ): CalendarLinkDecision = when {
            !hasAccess(readGranted, writeGranted) ->
                if (permanentlyDenied) CalendarLinkDecision.DENIED_PERMANENTLY else CalendarLinkDecision.REQUEST_PERMISSION

            writableCalendarCount <= 0 -> CalendarLinkDecision.NO_WRITABLE_CALENDAR
            eventCount <= 0 -> CalendarLinkDecision.NO_EVENTS
            else -> CalendarLinkDecision.PROCEED
        }

        /** 有权限但无日历账号时的可读文案（UI 直接展示，避免各处写不同说法）。 */
        fun failureText(decision: CalendarLinkDecision): String? = when (decision) {
            CalendarLinkDecision.PROCEED -> null
            CalendarLinkDecision.REQUEST_PERMISSION -> "需要日历权限"
            CalendarLinkDecision.DENIED_PERMANENTLY -> "日历权限已被拒绝，请在系统设置中允许"
            CalendarLinkDecision.NO_WRITABLE_CALENDAR -> "本机没有可写日历，请先在系统日历登录账号"
            CalendarLinkDecision.NO_EVENTS -> "没有可写入的课程"
        }
    }
}
