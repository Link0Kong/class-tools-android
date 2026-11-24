package com.furina.classtools.data.timetable

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表接入纯逻辑单测：周次分组 / 会话展开 / 权限判定 / UID 稳定性 / 文案。
 *
 * 时间基准：2026-08-31（周一）开学，第 1 周 = 8/31 - 9/6；第 16 周 = 12/14 - 12/20。
 */
class TimetableLinkCoreTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val termStart: LocalDate = LocalDate.of(2026, 8, 31)

    private fun course(
        name: String = "高等数学",
        teacher: String = "",
        room: String = "",
        day: Int = 0,
        start: Int = 1,
        end: Int = 2,
        weeks: String = "1-16",
        parity: String = "all",
    ) = Course(
        name = name,
        teacher = teacher,
        room = room,
        day = day,
        start = start,
        end = end,
        weeks = weeks,
        parity = parity,
    )

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    // ---- semesterWeeks ------------------------------------------------------

    @Test
    fun semesterWeeks_usesMaxSpecAndDefaultFloor() {
        assertEquals(18, TimetableLink.semesterWeeks(listOf(course(weeks = "1-16"))))
        assertEquals(20, TimetableLink.semesterWeeks(listOf(course(weeks = "1-16"), course(weeks = "1-20"))))
        assertEquals(18, TimetableLink.semesterWeeks(listOf(course(weeks = ""))))
        assertEquals(18, TimetableLink.semesterWeeks(emptyList()))
        assertEquals(25, TimetableLink.semesterWeeks(listOf(course(weeks = "1-99"))))
    }

    // ---- weekGroups ---------------------------------------------------------

    @Test
    fun weekGroups_contiguousIsSingleWeeklyGroup() {
        val groups = TimetableLink.weekGroups(course(weeks = "1-16"), maxWeek = 18)
        assertEquals(listOf(TimetableLink.WeekGroup(1, 16, 1)), groups)
        assertEquals(16, groups.first().size)
    }

    @Test
    fun weekGroups_blankWeeksCoversWholeSemester() {
        val groups = TimetableLink.weekGroups(course(weeks = ""), maxWeek = 18)
        assertEquals(listOf(TimetableLink.WeekGroup(1, 18, 1)), groups)
    }

    @Test
    fun weekGroups_oddParityUsesStepTwoAlignedToOddWeeks() {
        val groups = TimetableLink.weekGroups(course(weeks = "1-16", parity = "odd"), maxWeek = 18)
        assertEquals(listOf(TimetableLink.WeekGroup(1, 15, 2)), groups)
        assertEquals(8, groups.first().size)
        assertTrue(groups.first().contains(1))
        assertTrue(groups.first().contains(15))
        assertFalse(groups.first().contains(2))
    }

    @Test
    fun weekGroups_evenParityUsesStepTwoAlignedToEvenWeeks() {
        val groups = TimetableLink.weekGroups(course(weeks = "1-16", parity = "even"), maxWeek = 18)
        assertEquals(listOf(TimetableLink.WeekGroup(2, 16, 2)), groups)
        assertEquals(8, groups.first().size)
        assertTrue(groups.first().contains(2))
        assertFalse(groups.first().contains(1))
    }

    @Test
    fun weekGroups_nonContiguousWeeksSplit() {
        val groups = TimetableLink.weekGroups(course(weeks = "1-4,8-10"), maxWeek = 18)
        assertEquals(
            listOf(TimetableLink.WeekGroup(1, 4, 1), TimetableLink.WeekGroup(8, 10, 1)),
            groups,
        )
    }

    @Test
    fun weekGroups_clampedToMaxWeek() {
        val groups = TimetableLink.weekGroups(course(weeks = "1-16"), maxWeek = 10)
        assertEquals(listOf(TimetableLink.WeekGroup(1, 10, 1)), groups)
        val odd = TimetableLink.weekGroups(course(weeks = "1-16", parity = "odd"), maxWeek = 10)
        assertEquals(listOf(TimetableLink.WeekGroup(1, 9, 2)), odd)
    }

    @Test
    fun weekGroups_garbageWeeksTreatedAsWholeSemester() {
        val groups = TimetableLink.weekGroups(course(weeks = "abc,0,-3"), maxWeek = 18)
        assertEquals(listOf(TimetableLink.WeekGroup(1, 18, 1)), groups)
    }

    // ---- sessionFor / planSessions -----------------------------------------

    @Test
    fun sessionFor_computesDateAndPeriodTimes() {
        val session = TimetableLink.sessionFor(course(day = 2, start = 1, end = 2), week = 1, termStart = termStart)
        assertNotNull(session)
        session!!
        assertEquals("高等数学", session.courseName)
        assertEquals(2, session.day)
        assertEquals(1, session.week)
        assertEquals(millis(2026, 9, 2, 8, 10), session.startMillis)
        assertEquals(millis(2026, 9, 2, 9, 40), session.endMillis)
        assertEquals(2, session.endPeriod)
    }

    @Test
    fun sessionFor_secondWeekShiftsSevenDays() {
        val session = TimetableLink.sessionFor(course(day = 0, start = 3, end = 4), week = 2, termStart = termStart)
        assertNotNull(session)
        assertEquals(millis(2026, 9, 7, 9, 50), session!!.startMillis)
        assertEquals(millis(2026, 9, 7, 11, 20), session.endMillis)
    }

    @Test
    fun sessionFor_rejectsInvalidOrInactive() {
        assertNull(TimetableLink.sessionFor(course(day = 7), 1, termStart))
        assertNull(TimetableLink.sessionFor(course(start = 13), 1, termStart))
        assertNull(TimetableLink.sessionFor(course(start = 5, end = 2), 1, termStart))
        assertNull(TimetableLink.sessionFor(course(name = " "), 1, termStart))
        assertNull(TimetableLink.sessionFor(course(weeks = "5-8"), 1, termStart))
        assertNull(TimetableLink.sessionFor(course(weeks = "1-16", parity = "odd"), 2, termStart))
    }

    @Test
    fun planSessions_sortedAndFilteredByWeek() {
        val snapshot = TtSnapshot(
            c = listOf(
                course(name = "物理", day = 1, start = 3, end = 4, weeks = "1-16"),
                course(name = "高数", day = 0, start = 1, end = 2, weeks = "1-16"),
            ),
            t = "2026-08-31",
        )
        val all = TimetableLink.planSessions(snapshot, fromWeek = 1, toWeek = 2)
        assertEquals(4, all.size)
        assertEquals("高数", all.first().courseName) // 周一 08:10 最早
        assertTrue(all[0].startMillis < all[1].startMillis)
        val week2 = TimetableLink.planSessions(snapshot, fromWeek = 2, toWeek = 2)
        assertEquals(2, week2.size)
        assertTrue(week2.all { it.week == 2 })
        val limited = TimetableLink.planSessions(snapshot, fromWeek = 1, toWeek = 2, limit = 2)
        assertEquals(2, limited.size)
    }

    @Test
    fun planSessions_emptyWithoutTermStartOrCourses() {
        assertTrue(TimetableLink.planSessions(TtSnapshot(c = listOf(course()), t = ""), toWeek = 2).isEmpty())
        assertTrue(TimetableLink.planSessions(TtSnapshot(c = emptyList(), t = "2026-08-31")).isEmpty())
    }

    @Test
    fun planSessions_parityCoursesOnlyOnMatchingWeeks() {
        val snapshot = TtSnapshot(
            c = listOf(course(name = "单周实验", day = 2, start = 6, end = 7, weeks = "1-8", parity = "odd")),
            t = "2026-08-31",
        )
        val sessions = TimetableLink.planSessions(snapshot, fromWeek = 1, toWeek = 8)
        assertEquals(listOf(1, 3, 5, 7), sessions.map { it.week })
    }

    @Test
    fun currentWeek_clampsAndHandlesBeforeTerm() {
        val snapshot = TtSnapshot(c = listOf(course()), t = "2026-08-31")
        val week2 = LocalDateTime.of(2026, 9, 7, 10, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(2, TimetableLink.currentWeek(snapshot, week2))
        val before = LocalDateTime.of(2026, 8, 1, 10, 0).atZone(zone).toInstant().toEpochMilli()
        assertNull(TimetableLink.currentWeek(snapshot, before))
    }

    // ---- 文案 / UID ---------------------------------------------------------

    @Test
    fun weekText_coversBlankAndParity() {
        assertEquals("1-16 周", TimetableLink.weekText(course(weeks = "1-16")))
        assertEquals("1-16 周 · 单周", TimetableLink.weekText(course(weeks = "1-16", parity = "odd")))
        assertEquals("全学期 · 双周", TimetableLink.weekText(course(weeks = "", parity = "even")))
    }

    @Test
    fun calendarDescription_containsWeekAndTag() {
        val session = TimetableLink.sessionFor(course(teacher = "王老师"), 3, termStart)!!
        val text = TimetableLink.calendarDescription(session)
        assertTrue(text.contains("第 3 周"))
        assertTrue(text.contains("王老师"))
        assertTrue(text.contains(TimetableLink.CALENDAR_TAG))
    }

    @Test
    fun icsUid_isStableAndGroupSensitive() {
        val math = course()
        val group1 = TimetableLink.weekGroups(math, 18).first()
        val group2 = TimetableLink.weekGroups(course(weeks = "1-8"), 18).first()
        val uid1 = TimetableLink.icsUid(termStart, math, group1)
        val uid2 = TimetableLink.icsUid(termStart, math, group1)
        val uidOtherGroup = TimetableLink.icsUid(termStart, math, group2)
        assertEquals(uid1, uid2)
        assertFalse(uid1 == uidOtherGroup)
        assertTrue(uid1.startsWith("ct-"))
        assertTrue(uid1.endsWith("@${TimetableLink.ICS_UID_DOMAIN}"))
    }

    @Test
    fun sha1Hex_matchesKnownVector() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", TimetableLink.sha1Hex("abc"))
    }

    // ---- 权限判定 -----------------------------------------------------------

    @Test
    fun calendarPolicy_requestsPermissionBeforeCalendarChecks() {
        assertEquals(
            TimetableLink.CalendarLinkDecision.REQUEST_PERMISSION,
            TimetableLink.CalendarLinkPolicy.decide(
                readGranted = false,
                writeGranted = false,
                writableCalendarCount = 1,
                eventCount = 10,
            ),
        )
        assertEquals(
            TimetableLink.CalendarLinkDecision.REQUEST_PERMISSION,
            TimetableLink.CalendarLinkPolicy.decide(true, false, 1, 10),
        )
        assertEquals(
            TimetableLink.CalendarLinkDecision.DENIED_PERMANENTLY,
            TimetableLink.CalendarLinkPolicy.decide(false, false, 1, 10, permanentlyDenied = true),
        )
    }

    @Test
    fun calendarPolicy_noCalendarOrNoEvents() {
        assertEquals(
            TimetableLink.CalendarLinkDecision.NO_WRITABLE_CALENDAR,
            TimetableLink.CalendarLinkPolicy.decide(true, true, 0, 10),
        )
        assertEquals(
            TimetableLink.CalendarLinkDecision.NO_EVENTS,
            TimetableLink.CalendarLinkPolicy.decide(true, true, 2, 0),
        )
        assertEquals(
            TimetableLink.CalendarLinkDecision.PROCEED,
            TimetableLink.CalendarLinkPolicy.decide(true, true, 2, 10),
        )
    }

    @Test
    fun calendarPolicy_failureTextsAreConcrete() {
        assertNull(TimetableLink.CalendarLinkPolicy.failureText(TimetableLink.CalendarLinkDecision.PROCEED))
        assertEquals(
            "本机没有可写日历，请先在系统日历登录账号",
            TimetableLink.CalendarLinkPolicy.failureText(TimetableLink.CalendarLinkDecision.NO_WRITABLE_CALENDAR),
        )
        assertEquals(
            "没有可写入的课程",
            TimetableLink.CalendarLinkPolicy.failureText(TimetableLink.CalendarLinkDecision.NO_EVENTS),
        )
        assertTrue(
            TimetableLink.CalendarLinkPolicy.failureText(TimetableLink.CalendarLinkDecision.DENIED_PERMANENTLY)!!
                .contains("系统设置"),
        )
    }
}
