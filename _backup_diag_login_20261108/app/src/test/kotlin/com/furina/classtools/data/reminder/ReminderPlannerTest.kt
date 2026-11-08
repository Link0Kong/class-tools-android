package com.furina.classtools.data.reminder

import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.timetable.SessionInfo
import com.furina.classtools.data.timetable.SessionState
import com.furina.classtools.data.timetable.TodayState
import com.furina.classtools.data.timetable.TtPlan
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlannerTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Long = LocalDateTime.of(2026, 11, 9, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun homework(
        id: String,
        month: Int = 11,
        day: Int = 10,
        hour: Int = 23,
        minute: Int = 59,
        subject: String = "高等数学",
        content: String = "习题 3.1",
    ): HomeworkRow = HomeworkRow(
        id = id,
        subject = subject,
        content = content,
        date = LocalDate.of(2026, month, day).toString(),
        time = "%02d:%02d".format(hour, minute),
        ts = 1L,
    )

    private fun coursePlan(
        startAt: Long,
        name: String = "高等数学",
        room: String = "01104",
        hasData: Boolean = true,
    ): TtPlan = TtPlan(
        hasData = hasData,
        week = 1,
        today = emptyList(),
        todayState = TodayState.HAS,
        next = SessionInfo(
            name = name,
            room = room,
            start = 1,
            end = 2,
            startText = "14:30",
            endText = "15:15",
            span = "1-2 节",
            startAt = startAt,
            endAt = startAt + 45 * 60_000L,
            state = SessionState.NEXT,
            dayOffset = 0,
            dateText = "11-09",
            label = "$name · $room",
        ),
    )

    private fun defaultSettings() = ReminderSettings.DEFAULT

    // ------------------------------------------------------------ 作业：每日汇总

    @Test
    fun `每日 20 点汇总明日截止作业`() {
        val events = ReminderPlanner.planHomework(
            rows = listOf(homework("hw1", day = 10, subject = "高等数学")),
            nowMillis = now,
            settings = defaultSettings(),
            zone = zone,
        )
        val digest = events.firstOrNull { it.kind == ReminderKind.HOMEWORK_DIGEST }
        assertNotNull(digest)
        assertEquals(at(11, 9, 20), digest!!.triggerAtMillis)
        assertTrue(digest.body.contains("高等数学"))
        assertTrue(digest.body.contains("项作业即将截止"))
        assertEquals("hw", digest.toolId)
        assertEquals("hw-digest:2026-11-09", digest.key)
    }

    @Test
    fun `汇总覆盖 48 小时窗口 后天截止也汇总`() {
        val rows = listOf(
            homework("a", day = 10),
            homework("b", day = 11),
        )
        val digest = ReminderPlanner.planHomework(rows, nowMillis = now, zone = zone)
            .first { it.kind == ReminderKind.HOMEWORK_DIGEST }
        assertTrue(digest.body.contains("2 项"))
    }

    @Test
    fun `汇总不含已完成作业`() {
        val rows = listOf(homework("hw1", day = 10))
        val events = ReminderPlanner.planHomework(
            rows = rows,
            doneIds = setOf("hw1"),
            nowMillis = now,
            zone = zone,
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun `已过期与无截止作业不提醒`() {
        val rows = listOf(
            homework("old", day = 8),
            homework("nodue").copy(date = "", time = ""),
        )
        assertTrue(ReminderPlanner.planHomework(rows, nowMillis = now, zone = zone).isEmpty())
    }

    @Test
    fun `已过当日汇总时间则安排次日汇总`() {
        val evening = LocalDateTime.of(2026, 11, 9, 21, 0).atZone(zone).toInstant().toEpochMilli()
        val next = ReminderPlanner.nextDigestAt(evening, defaultSettings(), zone)
        assertEquals(at(11, 10, 20), next)

        val before = LocalDateTime.of(2026, 11, 9, 19, 59).atZone(zone).toInstant().toEpochMilli()
        assertEquals(at(11, 9, 20), ReminderPlanner.nextDigestAt(before, defaultSettings(), zone))
    }

    // ------------------------------------------------------------ 作业：截止前 N 天

    @Test
    fun `提前 3 天提醒按截止时间减三天`() {
        val settings = defaultSettings().copy(homeworkLeadDays = 3)
        // 11-12 23:59 截止；11-09 20:00 汇总窗口只到 11-11 20:00，不覆盖 → 单独提醒
        val events = ReminderPlanner.planHomework(
            rows = listOf(homework("hw3", day = 12)),
            nowMillis = now,
            settings = settings,
            zone = zone,
        )
        val due = events.firstOrNull { it.kind == ReminderKind.HOMEWORK_DUE }
        assertNotNull(due)
        assertEquals(at(11, 9, 23, 59), due!!.triggerAtMillis)
        assertTrue(due.body.contains("截止"))
        assertTrue(due.body.contains("11-12 23:59"))
    }

    @Test
    fun `进入提前窗口后立即提醒`() {
        // 11-09 18:00 截止，提前 1 天 → 原始触发 11-08，已过期 → 立即
        val dueSoon = homework("hw-now", day = 9, hour = 18, minute = 0)
        val events = ReminderPlanner.planHomework(
            rows = listOf(dueSoon),
            nowMillis = now,
            zone = zone,
        )
        val due = events.firstOrNull { it.kind == ReminderKind.HOMEWORK_DUE }
        assertNotNull(due)
        assertEquals(now + ReminderPlanner.IMMEDIATE_DELAY_MS, due!!.triggerAtMillis)
    }

    @Test
    fun `当日汇总已覆盖的作业不重复单独提醒`() {
        val settings = defaultSettings().copy(homeworkLeadDays = 1)
        val events = ReminderPlanner.planHomework(
            rows = listOf(homework("hw1", day = 10)),
            nowMillis = now,
            settings = settings,
            zone = zone,
        )
        assertEquals(listOf(ReminderKind.HOMEWORK_DIGEST), events.map { it.kind })
    }

    // ------------------------------------------------------------ 开关 / 安静时段 / 排序

    @Test
    fun `作业提醒关闭则无事件`() {
        val settings = defaultSettings().copy(homeworkEnabled = false)
        assertTrue(ReminderPlanner.planHomework(listOf(homework("a")), nowMillis = now, settings = settings, zone = zone).isEmpty())
    }

    @Test
    fun `总开关关闭则全部无事件`() {
        val settings = defaultSettings().copy(enabled = false)
        assertTrue(ReminderPlanner.planHomework(listOf(homework("a")), nowMillis = now, settings = settings, zone = zone).isEmpty())
        assertTrue(ReminderPlanner.planCourse(coursePlan(now + 10 * 60_000L), now, settings, zone).isEmpty())
    }

    @Test
    fun `安静时段内到点提醒被过滤`() {
        val settings = defaultSettings().copy(
            quietHoursEnabled = true,
            quietStartHour = 20,
            quietEndHour = 7,
        )
        // 汇总 20:00 处于安静时段；作业截止 11-10 也只在汇总里 → 空
        assertTrue(ReminderPlanner.planHomework(listOf(homework("a", day = 10)), nowMillis = now, settings = settings, zone = zone).isEmpty())
    }

    @Test
    fun `安静时段不覆盖白天提醒`() {
        val settings = defaultSettings().copy(
            homeworkLeadDays = 1,
            quietHoursEnabled = true,
            quietStartHour = 23,
            quietEndHour = 7,
        )
        // 11-11 23:59 截止：提前 1 天 → 11-10 23:59 落在安静时段 → 单独提醒被过滤；
        // 但当天 20:00 的汇总（非安静时段）仍包含该作业 → 只剩汇总。
        val events = ReminderPlanner.planHomework(
            rows = listOf(homework("a", day = 11)),
            nowMillis = now,
            settings = settings,
            zone = zone,
        )
        assertEquals(listOf(ReminderKind.HOMEWORK_DIGEST), events.map { it.kind })
    }

    @Test
    fun `合并计划按触发时间升序且 key 唯一`() {
        val settings = defaultSettings()
        val events = ReminderPlanner.plan(
            rows = listOf(homework("hw1", day = 10), homework("hw2", day = 12)),
            plan = coursePlan(now + 20 * 60_000L),
            nowMillis = now,
            settings = settings,
            zone = zone,
        ).events
        assertTrue(events.size >= 2)
        assertEquals(events.map { it.triggerAtMillis }.sorted(), events.map { it.triggerAtMillis })
        assertEquals(events.map { it.key }.distinct().size, events.size)
        assertTrue(events.any { it.kind == ReminderKind.COURSE_SOON })
    }

    @Test
    fun `filterDue 只保留当前窗口内事件`() {
        val events = listOf(
            ReminderEvent(ReminderKind.HOMEWORK_DUE, "past", now - 30 * 60_000L, "t", "b"),
            ReminderEvent(ReminderKind.HOMEWORK_DUE, "now", now + 5 * 60_000L, "t", "b"),
            ReminderEvent(ReminderKind.HOMEWORK_DUE, "far", now + 2 * 60 * 60_000L, "t", "b"),
        )
        val due = ReminderPlanner.filterDue(events, now)
        assertEquals(listOf("now"), due.map { it.key })
    }

    // ------------------------------------------------------------ 课表

    @Test
    fun `下一节课提前 15 分钟提醒`() {
        val start = now + 60 * 60_000L
        val events = ReminderPlanner.planCourse(coursePlan(start), now, defaultSettings(), zone)
        assertEquals(1, events.size)
        val event = events.single()
        assertEquals(ReminderKind.COURSE_SOON, event.kind)
        assertEquals(start - 15 * 60_000L, event.triggerAtMillis)
        assertTrue(event.title.contains("高等数学"))
        assertTrue(event.body.contains("01104"))
        assertEquals("tt", event.toolId)
        assertEquals("class:$start", event.key)
    }

    @Test
    fun `已在上课提醒窗口内立即提醒`() {
        val events = ReminderPlanner.planCourse(
            coursePlan(now + 5 * 60_000L),
            now,
            defaultSettings(),
            zone,
        )
        assertEquals(now + ReminderPlanner.IMMEDIATE_DELAY_MS, events.single().triggerAtMillis)
    }

    @Test
    fun `已开始或没有下一节课不提醒`() {
        assertTrue(ReminderPlanner.planCourse(coursePlan(now - 60_000L), now, defaultSettings(), zone).isEmpty())
        assertTrue(ReminderPlanner.planCourse(null, now, defaultSettings(), zone).isEmpty())
        assertTrue(ReminderPlanner.planCourse(coursePlan(now + 60_000L, hasData = false), now, defaultSettings(), zone).isEmpty())
    }

    @Test
    fun `课表提醒关闭无事件`() {
        val settings = defaultSettings().copy(courseEnabled = false)
        assertTrue(ReminderPlanner.planCourse(coursePlan(now + 60 * 60_000L), now, settings, zone).isEmpty())
    }

    @Test
    fun `不同提前分钟数影响触发时间`() {
        val start = now + 30 * 60_000L
        listOf(5, 10, 15).forEach { lead ->
            val settings = defaultSettings().copy(courseLeadMinutes = lead)
            val event = ReminderPlanner.planCourse(coursePlan(start), now, settings, zone).single()
            assertEquals(start - lead * 60_000L, event.triggerAtMillis)
        }
    }

    // ------------------------------------------------------------ 去重 / 冷却

    @Test
    fun `同一 key 当日不重复 次日可再次提醒`() {
        val sent = now - 2 * 60 * 60_000L
        assertFalse(ReminderDedupe.shouldNotify("hw-due:hw1", sent, now, zone))

        val nextDay = LocalDateTime.of(2026, 11, 10, 9, 0).atZone(zone).toInstant().toEpochMilli()
        assertTrue(ReminderDedupe.shouldNotify("hw-due:hw1", sent, nextDay, zone))
    }

    @Test
    fun `冷却窗口内跨日也不重复`() {
        // 23:30 发送，次日 00:10 仍在 20h 冷却内？同 key 不同日但冷却 20h → 应允许？
        // 规则：同本地日期禁止；跨日且超过冷却窗口才允许。次日 00:10 距发送 40 分钟 < 20h → 仍禁止。
        val sent = LocalDateTime.of(2026, 11, 9, 23, 30).atZone(zone).toInstant().toEpochMilli()
        val nextDayEarly = LocalDateTime.of(2026, 11, 10, 0, 10).atZone(zone).toInstant().toEpochMilli()
        assertFalse(ReminderDedupe.shouldNotify("class:123", sent, nextDayEarly, zone, cooldownMillis = 20L * 60 * 60_000))
    }

    @Test
    fun `去重存储在内存实现中生效并可清理`() = kotlinx.coroutines.test.runTest {
        val store = InMemoryReminderDedupeStore()
        assertNull(store.lastSent("a"))
        store.markSent("a", now)
        assertEquals(now, store.lastSent("a"))
        store.prune(now - ReminderDedupe.RETENTION_MS - 1)
        assertNotNull(store.lastSent("a"))
        store.prune(now + 1)
        assertNull(store.lastSent("a"))
    }

    @Test
    fun `汇总正文超过五项时折叠提示`() {
        val items = (1..7).map { i ->
            ReminderPlanner.HomeworkDeadline(
                id = "hw$i",
                dueAtMillis = at(11, 10, 20 + i % 3),
                subject = "科目$i",
                content = "",
            )
        }
        val body = ReminderPlanner.digestBody(items, zone)
        assertTrue(body.contains("等 7 项"))
        assertTrue(body.contains("科目1"))
    }

    @Test
    fun `工具映射与日期格式`() {
        assertEquals("hw", ReminderIntentFactory.toolIdFor(ReminderKind.HOMEWORK_DIGEST))
        assertEquals("hw", ReminderIntentFactory.toolIdFor(ReminderKind.HOMEWORK_DUE))
        assertEquals("tt", ReminderIntentFactory.toolIdFor(ReminderKind.COURSE_SOON))
        assertEquals("hw", ReminderIntentFactory.toolIdFromExtras("hw"))
        assertNull(ReminderIntentFactory.toolIdFromExtras("power"))
        assertEquals("2026-11-09", ReminderPlanner.formatDate(now, zone))
        assertEquals("11-09 20:00", ReminderPlanner.formatAt(at(11, 9, 20), zone))
    }

    @Test
    fun `设置规范化钳制非法值`() {
        val settings = ReminderSettings(
            homeworkDigestHour = 99,
            homeworkDigestMinute = -1,
            homeworkLeadDays = 9,
            courseLeadMinutes = 1,
            quietStartHour = 42,
            quietEndHour = -7,
        ).sanitized()
        assertEquals(23, settings.homeworkDigestHour)
        assertEquals(0, settings.homeworkDigestMinute)
        assertEquals(3, settings.homeworkLeadDays)
        assertEquals(5, settings.courseLeadMinutes)
        assertEquals(23, settings.quietStartHour)
        assertEquals(0, settings.quietEndHour)
    }

    @Test
    fun `安静时段跨午夜判断`() {
        val settings = ReminderSettings(quietHoursEnabled = true, quietStartHour = 23, quietEndHour = 7)
        assertTrue(settings.isQuiet(at(11, 10, 23, 30), zone))
        assertTrue(settings.isQuiet(at(11, 10, 3, 0), zone))
        assertFalse(settings.isQuiet(at(11, 10, 7, 0), zone))
        assertFalse(settings.isQuiet(at(11, 10, 12, 0), zone))
        assertFalse(settings.copy(quietHoursEnabled = false).isQuiet(at(11, 10, 3, 0), zone))
    }

    @Test
    fun `未来超出规划窗口的截止不提醒`() {
        val far = homework("far", month = 12, day = 20)
        assertTrue(ReminderPlanner.planHomework(listOf(far), nowMillis = now, zone = zone).isEmpty())
    }

    @Test
    fun `默认设置值符合产品口径`() {
        val settings = ReminderSettings.DEFAULT
        assertTrue(settings.enabled)
        assertTrue(settings.homeworkEnabled)
        assertTrue(settings.courseEnabled)
        assertEquals(20, settings.homeworkDigestHour)
        assertEquals(1, settings.homeworkLeadDays)
        assertEquals(15, settings.courseLeadMinutes)
        assertEquals(listOf(0, 1, 3), ReminderSettings.HOMEWORK_LEAD_DAY_OPTIONS)
        assertEquals(listOf(5, 10, 15), ReminderSettings.COURSE_LEAD_MINUTE_OPTIONS)
    }

    @Test
    fun `作业内容为空时回退内容首行作为名称`() {
        val row = homework("hw-empty", subject = "", content = "第三章习题", day = 10)
        val events = ReminderPlanner.planHomework(listOf(row), nowMillis = now, zone = zone)
        val digest = events.first { it.kind == ReminderKind.HOMEWORK_DIGEST }
        assertTrue(digest.body.contains("第三章习题"))
    }
}
