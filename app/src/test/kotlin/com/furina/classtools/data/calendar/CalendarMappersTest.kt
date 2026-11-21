package com.furina.classtools.data.calendar

import com.furina.classtools.data.notices.NoticeExtension
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeRecurrenceDto
import com.furina.classtools.data.notices.NoticeRow
import com.furina.classtools.data.notices.NoticeSegment
import com.furina.classtools.data.timetable.Course
import com.furina.classtools.data.timetable.TtSnapshot
import com.furina.classtools.data.permission.PermissionState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarMappersTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Long = LocalDateTime.of(2026, 11, 9, 12, 0).atZone(zone).toInstant().toEpochMilli()
    private val termStart: LocalDate = LocalDate.of(2026, 11, 9)

    private fun course(
        name: String = "高等数学",
        teacher: String = "王老师",
        room: String = "自强楼(教室01104)",
        day: Int = 0,
        start: Int = 1,
        end: Int = 2,
        weeks: String = "1-16",
        parity: String = "all",
    ) = Course(name = name, teacher = teacher, room = room, day = day, start = start, end = end, weeks = weeks, parity = parity)

    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun noticeGroup(
        title: String = "关于主题班会的通知",
        deadline: String = "",
        eventAt: String = "",
        extensions: List<NoticeExtension> = emptyList(),
        recurrence: NoticeRecurrenceDto? = null,
        segments: List<NoticeSegment> = emptyList(),
        summary: String = "本周五召开班会",
        source: String = "班级通知群",
    ) = NoticeGroupDto(
        id = "g_1",
        title = title,
        source = source,
        senderName = "陈同学",
        aiSummary = summary,
        deadline = deadline,
        deadlineAction = "截止",
        eventAt = eventAt,
        eventAction = "开始",
        extensions = extensions,
        recurrence = recurrence,
        aiSegments = segments,
    )

    // ------------------------------------------------------------ 课表 → 日程

    @Test
    fun `单节课映射字段完整`() {
        val event = CourseCalendarMapper.eventFor(course(), week = 1, termStart = termStart, zone = zone)!!
        assertEquals("高等数学", event.title)
        assertEquals("自强楼(教室01104)", event.location)
        assertEquals(at(11, 9, 8, 10), event.beginMillis)
        assertEquals(at(11, 9, 9, 40), event.endMillis)
        assertEquals(CalendarEvent.DEFAULT_REMINDER_MINUTES, event.reminderMinutes)
        assertEquals("Asia/Shanghai", event.timeZone)
        assertTrue(event.description.contains("第 1 周"))
        assertTrue(event.description.contains("每周"))
        assertTrue(event.description.contains("王老师"))
        assertTrue(event.sourceKey.startsWith("course:高等数学"))
    }

    @Test
    fun `单双周与周次范围过滤`() {
        val odd = course(parity = "odd")
        assertNotNull(CourseCalendarMapper.eventFor(odd, week = 1, termStart = termStart, zone = zone))
        assertNull(CourseCalendarMapper.eventFor(odd, week = 2, termStart = termStart, zone = zone))

        val limited = course(weeks = "1-4")
        assertNull(CourseCalendarMapper.eventFor(limited, week = 5, termStart = termStart, zone = zone))
        assertNotNull(CourseCalendarMapper.eventFor(limited, week = 4, termStart = termStart, zone = zone))
    }

    @Test
    fun `非法课程不生成事件`() {
        assertNull(CourseCalendarMapper.eventFor(course(name = ""), 1, termStart, zone))
        assertNull(CourseCalendarMapper.eventFor(course(start = 99), 1, termStart, zone))
        assertNull(CourseCalendarMapper.eventFor(course(start = 5, end = 2), 1, termStart, zone))
        assertNull(CourseCalendarMapper.eventFor(course(), week = 0, termStart = termStart, zone = zone))
    }

    @Test
    fun `一周多节课按开始时间升序`() {
        val snapshot = TtSnapshot(
            c = listOf(
                course(name = "体育", day = 3, start = 3, end = 4, weeks = "1-16"),
                course(name = "高数", day = 0, start = 1, end = 2, weeks = "1-16"),
            ),
            t = "2026-11-09",
        )
        val events = CourseCalendarMapper.eventsForWeek(snapshot, week = 1, zone = zone)
        assertEquals(listOf("高数", "体育"), events.map { it.title })
        assertTrue(events[0].beginMillis < events[1].beginMillis)
    }

    @Test
    fun `未来多周映射去重且数量正确`() {
        val snapshot = TtSnapshot(
            c = listOf(course(name = "高数", weeks = "1-16", day = 0)),
            t = "2026-11-09",
        )
        val events = CourseCalendarMapper.eventsForNextWeeks(snapshot, nowMillis = now, weeksAhead = 2, zone = zone)
        assertEquals(2, events.size)
        assertEquals(events.map { it.sourceKey }.distinct().size, events.size)
        assertEquals(LocalDate.of(2026, 11, 9), LocalDate.ofInstant(java.time.Instant.ofEpochMilli(events[0].beginMillis), zone))
    }

    @Test
    fun `无开学日或无课程时返回空`() {
        assertTrue(CourseCalendarMapper.eventsForWeek(TtSnapshot(c = listOf(course()), t = ""), 1, zone).isEmpty())
        assertTrue(CourseCalendarMapper.eventsForNextWeeks(TtSnapshot(c = emptyList(), t = "2026-11-09"), now, 2, zone).isEmpty())
        assertNull(CourseCalendarMapper.currentWeek(TtSnapshot(c = emptyList(), t = ""), now, zone))
        assertEquals(1, CourseCalendarMapper.currentWeek(TtSnapshot(c = emptyList(), t = "2026-11-09"), now, zone))
    }

    @Test
    fun `节次时间表缺失时安全返回`() {
        assertNull(CourseCalendarMapper.eventFor(course(start = 13, end = 13), 1, termStart, zone))
    }

    // ------------------------------------------------------------ 通知 → 日程

    @Test
    fun `有未来截止的通知可写日程`() {
        val group = noticeGroup(deadline = "2026-11-10T18:00:00+08:00")
        assertTrue(NoticeCalendarMapper.eligible(group, now, zone))
        val event = NoticeCalendarMapper.eventFor(group, now, zone)!!
        assertEquals(group.title, event.title)
        assertEquals(at(11, 10, 18, 0), event.beginMillis)
        assertEquals(event.beginMillis + 60 * 60_000L, event.endMillis)
        assertTrue(event.description.contains("本周五召开班会"))
        assertTrue(event.description.contains("来源：班级通知群"))
        assertTrue(event.sourceKey.startsWith("notice:g_1"))
    }

    @Test
    fun `无时间 已过期 长期安排的通知不可写`() {
        assertFalse(NoticeCalendarMapper.eligible(noticeGroup(), now, zone))
        assertFalse(NoticeCalendarMapper.eligible(noticeGroup(deadline = "2026-11-08T18:00:00+08:00"), now, zone))
        assertFalse(
            NoticeCalendarMapper.eligible(
                noticeGroup(
                    deadline = "2026-11-10T18:00:00+08:00",
                    recurrence = NoticeRecurrenceDto(freq = "weekly", weekday = 5),
                ),
                now,
                zone,
            ),
        )
        assertNull(NoticeCalendarMapper.eventFor(noticeGroup(), now, zone))
    }

    @Test
    fun `活动时间优先于截止时间`() {
        val group = noticeGroup(
            eventAt = "2026-11-11T16:00:00+08:00",
            deadline = "2026-11-12T18:00:00+08:00",
        )
        assertEquals(at(11, 11, 16, 0), NoticeCalendarMapper.targetMillis(group, now, zone))
        assertTrue(NoticeCalendarMapper.eventFor(group, now, zone)!!.description.contains("开始"))
    }

    @Test
    fun `延期 extensions 的最后一条作为通知截止`() {
        val group = noticeGroup(
            deadline = "2026-11-10T18:00:00+08:00",
            extensions = listOf(
                NoticeExtension(deadline = "2026-11-11T18:00:00+08:00"),
                NoticeExtension(deadline = "2026-11-12T18:00:00+08:00"),
            ),
        )
        assertEquals(at(11, 12, 18, 0), NoticeCalendarMapper.targetMillis(group, now, zone))
    }

    @Test
    fun `地点从结构化段落提取 摘要截断`() {
        val group = noticeGroup(
            deadline = "2026-11-10T18:00:00+08:00",
            segments = listOf(
                NoticeSegment(type = "时间", text = "周五 16:00"),
                NoticeSegment(type = "地点", text = "自强楼 01104"),
            ),
            summary = "长".repeat(300),
        )
        assertEquals("自强楼 01104", NoticeCalendarMapper.locationOf(group))
        assertTrue(NoticeCalendarMapper.descriptionOf(group, "截止").length < 300)
        assertTrue(NoticeCalendarMapper.formatAt(at(11, 10, 18, 0), zone).startsWith("11-10"))
    }

    @Test
    fun `无地点段落时 location 为空`() {
        assertEquals("", NoticeCalendarMapper.locationOf(noticeGroup()))
    }

    // ------------------------------------------------------------ 写入判定

    @Test
    fun `事件字段校验`() {
        val valid = CalendarEvent(title = "班会", beginMillis = now, endMillis = now + 3_600_000L)
        assertNull(CalendarWritePolicy.validate(valid))
        assertNotNull(CalendarWritePolicy.validate(valid.copy(title = " ")))
        assertNotNull(CalendarWritePolicy.validate(valid.copy(endMillis = valid.beginMillis)))
        assertNotNull(
            CalendarWritePolicy.validate(valid.copy(endMillis = valid.beginMillis + 8L * 24 * 60 * 60 * 1000)),
        )
        assertNotNull(CalendarWritePolicy.validate(valid.copy(reminderMinutes = -1)))
    }

    @Test
    fun `权限状态映射写入决策`() {
        assertEquals(CalendarWriteDecision.PROCEED, CalendarWritePolicy.decide(PermissionState.GRANTED))
        assertEquals(CalendarWriteDecision.NEEDS_PERMISSION, CalendarWritePolicy.decide(PermissionState.REQUESTABLE))
        assertEquals(CalendarWriteDecision.NEEDS_PERMISSION, CalendarWritePolicy.decide(PermissionState.DENIED))
        assertEquals(CalendarWriteDecision.DENIED, CalendarWritePolicy.decide(PermissionState.PERMANENTLY_DENIED))

        val event = CalendarEvent(title = "班会", beginMillis = now, endMillis = now + 3_600_000L)
        assertNull(CalendarWritePolicy.precheck(event, PermissionState.GRANTED))
        assertEquals(
            CalendarWriteResult.NeedsPermission,
            CalendarWritePolicy.precheck(event, PermissionState.REQUESTABLE),
        )
        assertEquals(
            CalendarWriteResult.NeedsPermission,
            CalendarWritePolicy.precheck(event, PermissionState.PERMANENTLY_DENIED),
        )
        assertTrue(CalendarWritePolicy.precheck(event.copy(title = ""), PermissionState.GRANTED) is CalendarWriteResult.Invalid)
    }

    @Test
    fun `sourceKey 判重`() {
        val event = CalendarEvent(title = "班会", beginMillis = now, endMillis = now + 3_600_000L, sourceKey = "notice:g1")
        assertTrue(CalendarWritePolicy.isDuplicate(event, listOf(event)))
        assertFalse(CalendarWritePolicy.isDuplicate(event, emptyList()))
        assertFalse(
            CalendarWritePolicy.isDuplicate(
                event.copy(sourceKey = ""),
                listOf(event.copy(sourceKey = "")),
            ),
        )
    }

    @Test
    fun `事件默认时长与提醒`() {
        val event = CalendarEvent(title = "班会", beginMillis = now, endMillis = now + 60_000L)
        assertEquals(60_000L, event.durationMillis)
        assertEquals(10, event.reminderMinutes)
        assertEquals(ZoneId.systemDefault().id, event.timeZone)
    }

    @Test
    fun `通知行模型可用作来源标识`() {
        val group = noticeGroup(deadline = "2026-11-10T18:00:00+08:00")
        val row = NoticeRow(id = "auto_g_1", title = group.title)
        assertEquals("auto_g_1", row.id)
        assertTrue(NoticeCalendarMapper.eventFor(group, now, zone)!!.sourceKey.contains(group.id))
    }
}
