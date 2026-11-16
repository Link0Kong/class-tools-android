package com.furina.classtools.data.timetable

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ICS（`.ics`）生成单测：转义 / 折叠 / 时区 / RRULE 周次与单双周 / VALARM / UID 稳定。
 *
 * 时间基准：2026-08-31（周一）开学；第 1 节 08:10-08:55，第 2 节 08:55-09:40，第 3-4 节 09:50-11:25。
 */
class TimetableIcsTest {

    private val termStart: LocalDate = LocalDate.of(2026, 8, 31)

    private fun course(
        name: String = "高等数学",
        teacher: String = "王老师",
        room: String = "自强楼(教室01105)",
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

    private fun snapshot(vararg courses: Course) =
        TtSnapshot(c = courses.toList(), t = "2026-08-31")

    // ---- 文件结构 -----------------------------------------------------------

    @Test
    fun build_writesCalendarEnvelopeWithShanghaiTimezone() {
        val doc = TimetableIcs.build(snapshot(course()), nowMillis = 0L)
        assertNotNull(doc)
        val text = doc!!.text
        assertTrue(text.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(text.endsWith("END:VCALENDAR\r\n"))
        assertTrue(text.contains("VERSION:2.0\r\n"))
        assertTrue(text.contains("X-WR-TIMEZONE:Asia/Shanghai\r\n"))
        assertTrue(text.contains("TZID:Asia/Shanghai\r\n"))
        assertTrue(text.contains("TZOFFSETTO:+0800\r\n"))
        assertEquals(1, doc.eventCount)
        assertEquals(15, doc.reminderMinutes)
    }

    @Test
    fun build_returnsNullWithoutTermStartOrCourses() {
        assertNull(TimetableIcs.build(TtSnapshot(c = listOf(course()), t = "")))
        assertNull(TimetableIcs.build(TtSnapshot(c = emptyList(), t = "2026-08-31")))
    }

    // ---- 时间与 DTSTART/DTEND ----------------------------------------------

    @Test
    fun build_firstEventUsesLocalClassTimes() {
        val text = TimetableIcs.build(snapshot(course(day = 0, start = 1, end = 2)), nowMillis = 0L)!!.text
        assertTrue(text.contains("DTSTART;TZID=Asia/Shanghai:20260831T081000\r\n"))
        assertTrue(text.contains("DTEND;TZID=Asia/Shanghai:20260831T094000\r\n"))
    }

    @Test
    fun build_dayIndexMapsToDateInFirstWeek() {
        // day=2（周三）→ 2026-09-02；day=4（周五）→ 2026-09-04
        val text = TimetableIcs.build(
            snapshot(course(name = "物理", day = 2, start = 3, end = 4, parity = "all")),
            nowMillis = 0L,
        )!!.text
        assertTrue(text.contains("DTSTART;TZID=Asia/Shanghai:20260902T095000"))
        assertTrue(text.contains("DTEND;TZID=Asia/Shanghai:20260902T112500"))
    }

    // ---- RRULE --------------------------------------------------------------

    @Test
    fun build_everyWeekCourseUsesWeeklyRruleWithUtcUntil() {
        val text = TimetableIcs.build(
            snapshot(course(weeks = "1-16", parity = "all")),
            nowMillis = 0L,
        )!!.text
        // 第 16 周周一 = 2026-12-14；结束 09:40 +08 → 01:40Z
        assertTrue(text.contains("RRULE:FREQ=WEEKLY;INTERVAL=1;UNTIL=20261214T014000Z"))
    }

    @Test
    fun build_oddWeekCourseUsesIntervalTwoAlignedToOddWeek() {
        val text = TimetableIcs.build(
            snapshot(course(day = 2, start = 3, end = 4, weeks = "1-16", parity = "odd")),
            nowMillis = 0L,
        )!!.text
        // 最后一次单周=第 15 周，周三 2026-12-09 11:25 +08 → 03:25Z
        assertTrue(text.contains("RRULE:FREQ=WEEKLY;INTERVAL=2;UNTIL=20261209T032500Z"))
    }

    @Test
    fun build_evenWeekCourseStartsOnEvenWeek() {
        val doc = TimetableIcs.build(
            snapshot(course(name = "双周课", day = 0, start = 1, end = 2, weeks = "1-16", parity = "even")),
            nowMillis = 0L,
        )!!
        // 第一个事件在第 2 周（2026-09-07）而不是第 1 周
        assertTrue(doc.text.contains("DTSTART;TZID=Asia/Shanghai:20260907T081000"))
        assertFalse(doc.text.contains("DTSTART;TZID=Asia/Shanghai:20260831T081000"))
    }

    @Test
    fun build_singleWeekCourseHasNoRrule() {
        val text = TimetableIcs.build(
            snapshot(course(weeks = "8", parity = "all")),
            nowMillis = 0L,
        )!!.text
        assertFalse(text.contains("RRULE:"))
        assertTrue(text.contains("DTSTART;TZID=Asia/Shanghai:20261019T081000"))
    }

    @Test
    fun build_nonContiguousWeeksProduceSeparateEventsWithDistinctUids() {
        val doc = TimetableIcs.build(
            snapshot(course(weeks = "1-4,8-10", parity = "all")),
            nowMillis = 0L,
        )!!
        assertEquals(2, doc.eventCount)
        val uids = doc.text.lineSequence().filter { it.startsWith("UID:") }.toList()
        assertEquals(2, uids.size)
        assertEquals(2, uids.distinct().size)
    }

    @Test
    fun build_maxWeeksClampsUntil() {
        val text = TimetableIcs.build(
            snapshot(course(weeks = "1-16")),
            nowMillis = 0L,
            maxWeeks = 8,
        )!!.text
        // 第 8 周周一 = 2026-10-19；09:40 +08 → 01:40Z
        assertTrue(text.contains("RRULE:FREQ=WEEKLY;INTERVAL=1;UNTIL=20261019T014000Z"))
    }

    // ---- 指定周（「当前周」导出） --------------------------------------------

    @Test
    fun build_onlyWeekProducesSingleWeekEventsWithoutRrule() {
        val doc = TimetableIcs.build(
            snapshot(course(weeks = "1-16", parity = "all")),
            nowMillis = 0L,
            onlyWeek = 3,
        )!!
        assertEquals(1, doc.eventCount)
        assertFalse(doc.text.contains("RRULE:"))
        // 第 3 周周一 = 2026-09-14
        assertTrue(doc.text.contains("DTSTART;TZID=Asia/Shanghai:20260914T081000"))
    }

    @Test
    fun build_onlyWeekSkipsParityInactiveCourses() {
        val doc = TimetableIcs.build(
            snapshot(
                course(name = "每周课", weeks = "1-16", parity = "all"),
                course(name = "单周课", day = 1, weeks = "1-16", parity = "odd"),
                course(name = "双周课", day = 2, weeks = "1-16", parity = "even"),
            ),
            nowMillis = 0L,
            onlyWeek = 3,
        )!!
        assertEquals(2, doc.eventCount)
        assertTrue(doc.text.contains("SUMMARY:每周课"))
        assertTrue(doc.text.contains("SUMMARY:单周课"))
        assertFalse(doc.text.contains("SUMMARY:双周课"))
    }

    @Test
    fun build_onlyWeekOutOfRangeOrInactiveReturnsNull() {
        assertNull(TimetableIcs.build(snapshot(course()), nowMillis = 0L, onlyWeek = 99))
        assertNull(TimetableIcs.build(snapshot(course()), nowMillis = 0L, onlyWeek = 0))
        assertNull(TimetableIcs.build(snapshot(course(weeks = "5-8")), nowMillis = 0L, onlyWeek = 3))
    }

    // ---- VALARM -------------------------------------------------------------

    @Test
    fun build_includesValarmWithConfiguredMinutes() {
        val text15 = TimetableIcs.build(snapshot(course()), nowMillis = 0L)!!.text
        assertTrue(text15.contains("BEGIN:VALARM"))
        assertTrue(text15.contains("ACTION:DISPLAY"))
        assertTrue(text15.contains("TRIGGER:-PT15M"))
        val text30 = TimetableIcs.build(snapshot(course()), nowMillis = 0L, reminderMinutes = 30)!!.text
        assertTrue(text30.contains("TRIGGER:-PT30M"))
        val textNone = TimetableIcs.build(snapshot(course()), nowMillis = 0L, reminderMinutes = 0)!!.text
        assertFalse(textNone.contains("BEGIN:VALARM"))
    }

    // ---- UID 稳定 -----------------------------------------------------------

    @Test
    fun build_uidStableAcrossRegeneration() {
        val first = TimetableIcs.build(snapshot(course()), nowMillis = 0L)!!
        val second = TimetableIcs.build(snapshot(course()), nowMillis = 123456789L)!!
        val uidOf = { text: String ->
            text.lineSequence().filter { it.startsWith("UID:") }.toList()
        }
        assertEquals(uidOf(first.text), uidOf(second.text))
        assertTrue(uidOf(first.text).all { it.endsWith("@tool.furina.fun") })
    }

    // ---- 转义 ---------------------------------------------------------------

    @Test
    fun escapeText_escapesRfcSpecialCharacters() {
        assertEquals("线代\\,复习\\;A\\\\B\\n第二行", TimetableIcs.escapeText("线代,复习;A\\B\n第二行"))
        assertEquals("", TimetableIcs.escapeText(""))
        assertEquals("abc", TimetableIcs.escapeText("a\rbc"))
    }

    @Test
    fun build_escapesSummaryAndDescription() {
        val text = TimetableIcs.build(
            snapshot(
                course(
                    name = "线代,复习;A\\B",
                    teacher = "李老师",
                    room = "教室;A,B",
                ),
            ),
            nowMillis = 0L,
        )!!.text
        assertTrue(text.contains("SUMMARY:线代\\,复习\\;A\\\\B\r\n"))
        assertTrue(text.contains("LOCATION:教室\\;A\\,B\r\n"))
        assertTrue(text.contains("教师：李老师"))
    }

    // ---- 折叠 ---------------------------------------------------------------

    @Test
    fun foldLine_keepsUtf8ByteLengthWithin75() {
        val line = "DESCRIPTION:" + "课".repeat(40)
        val folded = TimetableIcs.foldLine(line)
        val lines = folded.split("\r\n")
        assertTrue(lines.size > 1)
        assertTrue(lines.all { it.toByteArray(Charsets.UTF_8).size <= 75 })
        assertTrue(lines.drop(1).all { it.startsWith(" ") })
        assertEquals(line, folded.replace("\r\n ", ""))
    }

    @Test
    fun foldLine_shortLineUnchanged() {
        val line = "SUMMARY:高数"
        assertEquals(line, TimetableIcs.foldLine(line))
    }

    @Test
    fun build_longDescriptionNeverExceedsLineLimit() {
        val text = TimetableIcs.build(
            snapshot(course(name = "很长很长的课程名称".repeat(4), teacher = "张老师", room = "实验楼(教室01105)")),
            nowMillis = 0L,
        )!!.text
        // 注意 foldLine 输出本身已含 CRLF+空格续行；按 CRLF 切分后每行（含续行前导空格）≤75 字节
        text.split("\r\n").filter { it.isNotEmpty() }.forEach { line ->
            assertTrue("line too long: $line", line.toByteArray(Charsets.UTF_8).size <= 75)
        }
    }
}
