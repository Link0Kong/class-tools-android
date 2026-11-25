package com.furina.classtools.data.timetable

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表日历纯函数单测（与小程序 utils/ttplan.js 同口径）。
 *
 * 时间基准：2026-11-07 10:00 Asia/Shanghai（周五，第 10 周；开学日 2026-08-31 周一）。
 */
class TimetableCalendarTest {

    @Test
    fun nextBellInsideDoublePeriodIsAvailableBeforeTheBreak() {
        val morning = ZonedDateTime.of(2026, 11, 6, 8, 45, 0, 0, TimetableLink.CHINA_ZONE)
        val result = TtCalendar.nextSession(listOf(course("连堂", day = 4, start = 1, end = 2)),
            termStart, morning.toInstant().toEpochMilli(), TimetableLink.CHINA_ZONE)
        assertEquals("09:00", result?.startText)
        assertEquals("09:40", result?.endText)
        assertEquals(morning.withHour(9).withMinute(0).toInstant().toEpochMilli(), result?.startAt)
    }

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    // 2026-11-06（周五）10:00 —— 周五有 14:30 的课，便于测试「下一节/今日状态」。
    private val now: Long = ZonedDateTime.of(2026, 11, 6, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val termStart: LocalDate = LocalDate.of(2026, 8, 31)

    private fun course(
        name: String = "高等数学",
        day: Int = 0,
        start: Int = 1,
        end: Int = 2,
        weeks: String = "1-16",
        parity: String = "all",
    ) = Course(name = name, day = day, start = start, end = end, weeks = weeks, parity = parity)

    @Test
    fun doublePeriodPausesAndResumesAtTheBell() {
        val snapshot = TtSnapshot(c = listOf(course()), t = "2026-08-31")
        fun plan(hour: Int, minute: Int) = TtCalendar.plan(snapshot,
            termStart.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli(), zone)
        assertEquals("08:50", plan(8, 49).today.single().endText)
        val rest = plan(8, 50)
        assertEquals(SessionState.NEXT, rest.today.single().state)
        assertEquals("09:00", rest.next?.startText)
        assertEquals(SessionState.ONGOING, plan(9, 0).today.single().state)
        assertEquals(SessionState.DONE, plan(9, 40).today.single().state)
    }

    // ---- parseWeeks ---------------------------------------------------------

    @Test
    fun parseWeeks_handlesRangesAndSingles() {
        assertEquals(setOf(1, 2, 3, 4, 5), TtCalendar.parseWeeks("1-5"))
        assertEquals(setOf(1, 2, 3, 4, 16, 18), TtCalendar.parseWeeks("1-4,16,18"))
        assertEquals(setOf(2, 3, 4, 6, 7, 8, 9), TtCalendar.parseWeeks("2-4,6-9"))
        assertEquals(setOf(1, 2, 3, 4, 5), TtCalendar.parseWeeks("1~5周"))
        assertEquals(setOf(1, 2, 3, 4, 5), TtCalendar.parseWeeks("1—5"))
        assertEquals(setOf(1, 2, 3, 4, 5), TtCalendar.parseWeeks("1-5周"))
    }

    @Test
    fun parseWeeks_toleratesSpacesAndInvalidTokens() {
        assertEquals(setOf(3, 4), TtCalendar.parseWeeks(" 3 - 4 , x, 0, 61 "))
        assertEquals(emptySet<Int>(), TtCalendar.parseWeeks(""))
        assertEquals(setOf(18), TtCalendar.parseWeeks("18"))
    }

    // ---- weekOf -------------------------------------------------------------

    @Test
    fun weekOf_firstWeekAndLaterWeeks() {
        assertEquals(1, TtCalendar.weekOf(termStart, LocalDate.of(2026, 8, 31)))
        assertEquals(1, TtCalendar.weekOf(termStart, LocalDate.of(2026, 9, 6)))
        assertEquals(2, TtCalendar.weekOf(termStart, LocalDate.of(2026, 9, 7)))
        assertEquals(10, TtCalendar.weekOf(termStart, LocalDate.of(2026, 11, 7)))
    }

    @Test
    fun weekOf_beforeTermOrInvalidIsNull() {
        assertNull(TtCalendar.weekOf(termStart, LocalDate.of(2026, 8, 30)))
        assertNull(TtCalendar.weekOf(null, LocalDate.of(2026, 11, 7)))
        assertNull(TtCalendar.weekOf(null, LocalDate.EPOCH))
    }

    @Test
    fun parseTermStart_acceptsIsoAndRejectsGarbage() {
        assertEquals(termStart, TtCalendar.parseTermStart("2026-08-31"))
        assertEquals(termStart, TtCalendar.parseTermStart("2026-08-31T00:00:00+08:00"))
        assertNull(TtCalendar.parseTermStart(""))
        assertNull(TtCalendar.parseTermStart("2026/08/31"))
    }

    // ---- isActive -----------------------------------------------------------

    @Test
    fun isActive_weeksAndParity() {
        val basic = course(weeks = "5-10")
        assertTrue(TtCalendar.isActive(basic, 5))
        assertTrue(TtCalendar.isActive(basic, 10))
        assertFalse(TtCalendar.isActive(basic, 3))
        assertFalse(TtCalendar.isActive(basic, 11))

        val odd = course(weeks = "", parity = "odd")
        assertTrue(TtCalendar.isActive(odd, 3))
        assertFalse(TtCalendar.isActive(odd, 4))

        val even = course(weeks = "", parity = "even")
        assertTrue(TtCalendar.isActive(even, 4))
        assertFalse(TtCalendar.isActive(even, 3))

        // 空 weeks = 全学期；week=null 不做周过滤
        assertTrue(TtCalendar.isActive(course(weeks = ""), 7))
        assertTrue(TtCalendar.isActive(course(weeks = "1-3"), null))
    }

    // ---- sessionsOn ---------------------------------------------------------

    @Test
    fun sessionsOn_computesTimesAndStates() {
        val courses = listOf(
            course("早课", day = 4, start = 1, end = 2),
            course("下午课", day = 4, start = 6, end = 7),
            course("晚课", day = 4, start = 10, end = 11),
        )
        val sessions = TtCalendar.sessionsOn(courses, termStart, LocalDate.of(2026, 11, 6), now, zone)
        assertEquals(3, sessions.size)
        // 10:00 时：早课已结束，下午课未开始
        assertEquals(SessionState.DONE, sessions[0].state)
        assertEquals(SessionState.NEXT, sessions[1].state)
        assertEquals("14:30", sessions[1].startText)
        assertEquals("16:00", sessions[1].endText)
        assertEquals("第6-7节", sessions[1].span)
        assertTrue(sessions[1].startAt > now)
    }

    @Test
    fun sessionsOn_filtersByWeekAndParity() {
        val oddCourse = course("单周课", day = 0, start = 1, end = 2, weeks = "1-16", parity = "odd")
        // 第 10 周为双周 → 单周课不上
        val courses = listOf(oddCourse)
        val week10 = TtCalendar.weekOf(termStart, LocalDate.of(2026, 11, 2))
        assertEquals(10, week10)
        assertTrue(
            TtCalendar.sessionsOn(courses, termStart, LocalDate.of(2026, 11, 2), now, zone).isEmpty(),
        )
        // 第 11 周（11-09）为单周 → 上课
        assertFalse(
            TtCalendar.sessionsOn(courses, termStart, LocalDate.of(2026, 11, 9), now, zone).isEmpty(),
        )
    }

    @Test
    fun sessionsOn_roomShort() {
        val courses = listOf(course("课", day = 0, start = 1, end = 1).copy(room = "自强楼(教室01104)"))
        val session = TtCalendar.sessionsOn(courses, termStart, LocalDate.of(2026, 11, 2), now, zone).single()
        assertEquals("01104", session.room)
    }

    // ---- nextSession / plan -------------------------------------------------

    @Test
    fun nextSession_findsNearestUpcoming() {
        val courses = listOf(
            course("周五下午", day = 4, start = 6, end = 7),
            course("下周一早课", day = 0, start = 1, end = 2, weeks = "1-16"),
        )
        val next = TtCalendar.nextSession(courses, termStart, now, zone)
        assertEquals("周五下午", next?.name)
        assertEquals("今天", next?.dayText)
    }

    @Test
    fun nextSession_skipsFinishedAndUsesTomorrow() {
        val courses = listOf(
            course("周五早课", day = 4, start = 1, end = 2),
            course("周一早课", day = 0, start = 1, end = 2),
        )
        val next = TtCalendar.nextSession(courses, termStart, now, zone)
        assertEquals("周一早课", next?.name)
        assertEquals("周一", next?.dayText)
        assertEquals(3, next?.dayOffset)
    }

    @Test
    fun plan_hasDataAndTodayState() {
        val empty = TtCalendar.plan(TtSnapshot(), now, zone)
        assertFalse(empty.hasData)
        assertEquals(TodayState.NODATA, empty.todayState)

        val noTerm = TtCalendar.plan(TtSnapshot(c = listOf(course())), now, zone)
        assertFalse(noTerm.hasData)

        val onlyDone = TtCalendar.plan(
            TtSnapshot(c = listOf(course("早课", day = 4, start = 1, end = 2)), t = "2026-08-31"),
            now,
            zone,
        )
        assertTrue(onlyDone.hasData)
        assertEquals(10, onlyDone.week)
        assertEquals(TodayState.DONE, onlyDone.todayState)

        val hasNoClass = TtCalendar.plan(
            TtSnapshot(c = listOf(course("周一课", day = 0, start = 1, end = 2)), t = "2026-08-31"),
            now,
            zone,
        )
        assertEquals(TodayState.NONE, hasNoClass.todayState)
    }

    // ---- 周范围 / 标题 ------------------------------------------------------

    @Test
    fun weekRangeAndTitle() {
        val range = TtCalendar.weekRange(termStart, 10)
        assertEquals(LocalDate.of(2026, 11, 2), range?.first)
        assertEquals(LocalDate.of(2026, 11, 8), range?.second)
        assertEquals("第 10 周 · 11/2-11/8", TtCalendar.weekTitle(termStart, 10))
        assertEquals("课表", TtCalendar.weekTitle(termStart, null))
    }

    @Test
    fun todayIndexAndLabels() {
        assertEquals(4, TtCalendar.dayIndex(LocalDate.of(2026, 11, 6)))
        assertEquals("周五", TtCalendar.dayLabel(4))
        assertEquals(0, TtCalendar.dayIndex(LocalDate.of(2026, 11, 9)))
    }
}


