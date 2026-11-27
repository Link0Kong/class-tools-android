package com.furina.classtools.data.reminder

import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.notices.ManualNotice
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeRow
import com.furina.classtools.data.notices.NoticesData
import com.furina.classtools.data.timetable.SessionInfo
import com.furina.classtools.data.timetable.SessionState
import com.furina.classtools.data.timetable.TodayState
import com.furina.classtools.data.timetable.TtPlan
import com.furina.classtools.data.vote.PollMode
import com.furina.classtools.data.vote.PollResultVisibility
import com.furina.classtools.data.vote.PollSettings
import com.furina.classtools.data.vote.PollSummary
import com.furina.classtools.features.water.WaterRecord
import com.furina.classtools.features.water.WaterRoomState
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

    private fun noticeGroup(
        id: String,
        title: String = "关于主题班会的通知",
        deadline: String = "",
        eventAt: String = "",
        signupStart: String = "",
        senderName: String = "陈同学",
    ): NoticeGroupDto = NoticeGroupDto(
        id = id,
        title = title,
        source = "班级通知群",
        senderName = senderName,
        deadline = deadline,
        deadlineAction = "截止",
        eventAt = eventAt,
        eventAction = "开始",
        signupStart = signupStart,
    )

    private fun noticeEntry(group: NoticeGroupDto, hidden: Boolean = false): NoticeEntry = NoticeEntry(
        row = NoticeRow(id = "auto_${group.id}", title = group.title, ts = 1L),
        group = group,
        items = emptyList(),
        hidden = hidden,
        edited = false,
    )

    private fun notices(vararg entries: NoticeEntry): NoticesData = NoticesData(
        auto = entries.toList(),
        manual = emptyList<ManualNotice>(),
        hiddenCount = 0,
        fetchedAt = now,
        fromCache = false,
    )

    private fun poll(
        id: String,
        title: String = "周三班会时间投票",
        closed: Boolean = false,
        deadline: Long = 0L,
        visibility: PollResultVisibility = PollResultVisibility.AFTER_CLOSE,
    ): PollSummary = PollSummary(
        id = id,
        title = title,
        mode = PollMode.LONG,
        settings = PollSettings(resultVisibility = visibility),
        deadline = deadline,
        status = if (closed) "closed" else "open",
        closed = closed,
        createdAt = now - 60_000L,
    )

    /** 已建立基线（有历史 id）的快照；新项提醒只在基线存在时才生效。 */
    private fun baseline(
        hwIds: List<String> = listOf("hw-old"),
        ntIds: List<String> = listOf("nt-old"),
        pollIds: List<String> = listOf("poll-old"),
        closedPollIds: List<String> = emptyList(),
        waterKey: String = "",
    ): ReminderSnapshot = ReminderSnapshot(
        initialized = true,
        hwIds = hwIds,
        ntIds = ntIds,
        pollIds = pollIds,
        closedPollIds = closedPollIds,
        waterKey = waterKey,
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

    @Test
    fun `关闭每日汇总后只有单条截止提醒`() {
        val settings = defaultSettings().copy(digestEnabled = false)
        val events = ReminderPlanner.planHomework(
            rows = listOf(homework("hw1", day = 10)),
            nowMillis = now,
            settings = settings,
            zone = zone,
        )
        assertEquals(listOf(ReminderKind.HOMEWORK_DUE), events.map { it.kind })
    }

    // ------------------------------------------------------------ 作业：统一提前时间

    @Test
    fun `统一提前 1 天按截止时间减一天`() {
        val settings = defaultSettings().copy(leadMinutes = 1440)
        // 11-12 23:59 截止；11-09 20:00 汇总窗口只到 11-11 23:59，不覆盖 → 单独提醒
        val events = ReminderPlanner.planHomework(
            rows = listOf(homework("hw3", day = 12)),
            nowMillis = now,
            settings = settings,
            zone = zone,
        )
        val due = events.firstOrNull { it.kind == ReminderKind.HOMEWORK_DUE }
        assertNotNull(due)
        assertEquals(at(11, 11, 23, 59), due!!.triggerAtMillis)
        assertTrue(due.body.contains("截止"))
        assertTrue(due.body.contains("11-12 23:59"))
    }

    @Test
    fun `进入提前窗口后立即提醒`() {
        // 11-09 12:30 截止，提前 60 分钟 → 原始触发 11:30 已过期 → 立即
        val dueSoon = homework("hw-now", day = 9, hour = 12, minute = 30)
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
        val events = ReminderPlanner.planHomework(
            rows = listOf(homework("hw1", day = 10)),
            nowMillis = now,
            settings = defaultSettings(),
            zone = zone,
        )
        assertEquals(listOf(ReminderKind.HOMEWORK_DIGEST), events.map { it.kind })
    }

    // ------------------------------------------------------------ 新作业

    @Test
    fun `新作业首轮只建基线不推送`() {
        val events = ReminderPlanner.planHomeworkNew(
            rows = listOf(homework("hw-new", day = 11)),
            nowMillis = now,
            settings = defaultSettings(),
            snapshot = ReminderSnapshot(initialized = false),
            zone = zone,
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun `新作业与快照比较后推送并带截止时间`() {
        val events = ReminderPlanner.planHomeworkNew(
            rows = listOf(homework("hw-old"), homework("hw-new", day = 11, subject = "大学英语")),
            nowMillis = now,
            settings = defaultSettings(),
            snapshot = baseline(hwIds = listOf("hw-old")),
            zone = zone,
        )
        val event = events.single()
        assertEquals(ReminderKind.HOMEWORK_NEW, event.kind)
        assertEquals("hw-new:hw-new", event.key)
        assertEquals("新作业", event.title)
        assertTrue(event.body.contains("大学英语"))
        assertTrue(event.body.contains("截止 11-11 23:59"))
        assertEquals("hw", event.toolId)
    }

    @Test
    fun `新作业已完成或已关闭新作业通知时不提醒`() {
        val rows = listOf(homework("hw-new", day = 11))
        assertTrue(
            ReminderPlanner.planHomeworkNew(
                rows = rows,
                doneIds = setOf("hw-new"),
                nowMillis = now,
                snapshot = baseline(),
                zone = zone,
            ).isEmpty(),
        )
        assertTrue(
            ReminderPlanner.planHomeworkNew(
                rows = rows,
                nowMillis = now,
                settings = defaultSettings().copy(homeworkNewEnabled = false),
                snapshot = baseline(),
                zone = zone,
            ).isEmpty(),
        )
    }

    // ------------------------------------------------------------ 开关 / 安静时段 / 排序

    @Test
    fun `作业提醒关闭则无事件`() {
        val settings = defaultSettings().copy(homeworkDueEnabled = false, digestEnabled = false)
        assertTrue(
            ReminderPlanner.planHomework(
                listOf(homework("a")),
                nowMillis = now,
                settings = settings,
                zone = zone,
            ).isEmpty(),
        )
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
        // 汇总 20:00 处于安静时段；单条截止 22:59 也在安静时段 → 空
        assertTrue(ReminderPlanner.planHomework(listOf(homework("a", day = 10)), nowMillis = now, settings = settings, zone = zone).isEmpty())
    }

    @Test
    fun `安静时段不覆盖白天提醒`() {
        val settings = defaultSettings().copy(
            quietHoursEnabled = true,
            quietStartHour = 23,
            quietEndHour = 7,
        )
        // 11-11 23:59 截止：汇总（20:00，非安静时段）覆盖 → 只剩汇总。
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
    fun `下一节课默认提前 10 分钟提醒`() {
        val start = now + 60 * 60_000L
        val events = ReminderPlanner.planCourse(coursePlan(start), now, defaultSettings(), zone)
        assertEquals(1, events.size)
        val event = events.single()
        assertEquals(ReminderKind.COURSE_SOON, event.kind)
        assertEquals(start - 10 * 60_000L, event.triggerAtMillis)
        assertTrue(event.title.contains("高等数学"))
        assertEquals("上课提醒 · 高等数学", event.title)
        assertTrue(event.body.contains("01104"))
        assertFalse(event.body.contains("还有"))
        assertEquals(start, event.course?.startAt)
        assertEquals("tt", event.toolId)
        assertTrue(event.key.startsWith("class:$start:"))
        assertEquals(event.course?.room, event.course?.focus?.room)
    }

    @Test
    fun oldTrackingRequiresACustomCourseSettingAndEnabledReminders() {
        assertFalse(migratedCourseTracking(true, true, null, null))
        assertFalse(migratedCourseTracking(true, true, ReminderSettings.DEFAULT_CLASS_LEAD_MINUTES,
            ReminderSettings.DEFAULT_BREAK_LEAD_MINUTES))
        assertTrue(migratedCourseTracking(true, true, 15, null))
        assertFalse(migratedCourseTracking(false, true, 15, null))
        assertFalse(migratedCourseTracking(true, false, 15, null))
        assertFalse(migratedCourseTracking(true, true, 99, 99))
    }

    @Test
    fun focusedPeriodKeepsOriginalCourseRangeAndTargetWeek() {
        val start = LocalDateTime.of(2026, 11, 16, 10, 0).atZone(zone).toInstant().toEpochMilli()
        val base = coursePlan(start)
        val next = base.next!!.copy(start = 2, end = 4, span = "第3节", fullRoom = "第一教学楼 B302",
            teacher = "张老师", targetWeek = 12)
        val event = ReminderPlanner.planCourse(base.copy(week = 11, next = next), start - 60_000,
            defaultSettings(), zone).single()
        val info = event.course!!
        assertEquals("第3节", info.span)
        assertEquals(2, info.focus?.start)
        assertEquals(4, info.focus?.end)
        assertEquals("2026-11-16", info.focus?.date)
        assertEquals(12, info.week)
        assertEquals("第一教学楼 B302", info.room)
        assertEquals("张老师", info.teacher)
    }

    @Test
    fun `已在上课提醒窗口内立即提醒`() {
        val events = ReminderPlanner.planCourse(
            coursePlan(now + 5 * 60_000L),
            now,
            defaultSettings(),
            zone,
        )
        assertEquals(now, events.single().triggerAtMillis)
    }

    @Test
    fun `已开始或没有下一节课不提醒`() {
        assertTrue(ReminderPlanner.planCourse(coursePlan(now - 60_000L), now, defaultSettings(), zone).isEmpty())
        assertTrue(ReminderPlanner.planCourse(null, now, defaultSettings(), zone).isEmpty())
        assertTrue(ReminderPlanner.planCourse(coursePlan(now + 60_000L, hasData = false), now, defaultSettings(), zone).isEmpty())
    }

    @Test
    fun `课表提醒关闭无事件`() {
        val settings = defaultSettings().copy(classRemindEnabled = false)
        assertTrue(ReminderPlanner.planCourse(coursePlan(now + 60 * 60_000L), now, settings, zone).isEmpty())
    }

    @Test
    fun `不同上课提前分钟数影响触发时间`() {
        val start = now + 2 * 60 * 60_000L
        ReminderSettings.CLASS_LEAD_OPTIONS.forEach { lead ->
            val settings = defaultSettings().copy(classLeadMinutes = lead)
            val event = ReminderPlanner.planCourse(coursePlan(start), now, settings, zone).single()
            assertEquals(start - lead * 60_000L, event.triggerAtMillis)
        }
    }

    // ------------------------------------------------------------ 通知：新通知 / 开抢

    @Test
    fun `新通知带发布人并在首轮不推送`() {
        val data = notices(noticeEntry(noticeGroup("g-new", title = "图书馆闭馆通知", deadline = "2026-11-10T18:00:00+08:00")))
        assertTrue(
            ReminderPlanner.planNoticesNew(
                notices = data,
                nowMillis = now,
                snapshot = ReminderSnapshot(initialized = false),
                zone = zone,
            ).isEmpty(),
        )
        val event = ReminderPlanner.planNoticesNew(
            notices = data,
            nowMillis = now,
            snapshot = baseline(ntIds = listOf("g-old")),
            zone = zone,
        ).single()
        assertEquals(ReminderKind.NOTICE_NEW, event.kind)
        assertEquals("nt-new:g-new", event.key)
        assertEquals("新通知", event.title)
        assertTrue(event.body.contains("陈同学"))
        assertTrue(event.body.contains("图书馆闭馆通知"))
        assertEquals("nt", event.toolId)
    }

    @Test
    fun `新通知已处理或不显示时不提醒`() {
        val data = notices(noticeEntry(noticeGroup("g-new", deadline = "2026-11-10T18:00:00+08:00")))
        assertTrue(
            ReminderPlanner.planNoticesNew(
                notices = data,
                handledIds = setOf("g-new"),
                nowMillis = now,
                snapshot = baseline(),
                zone = zone,
            ).isEmpty(),
        )
        val hidden = notices(noticeEntry(noticeGroup("g-hidden"), hidden = true))
        assertTrue(
            ReminderPlanner.planNoticesNew(
                notices = hidden,
                nowMillis = now,
                snapshot = baseline(),
                zone = zone,
            ).isEmpty(),
        )
    }

    @Test
    fun `活动开抢在报名开始前 5 分钟提醒`() {
        val data = notices(
            noticeEntry(
                noticeGroup(
                    id = "g-signup",
                    title = "秋游报名",
                    eventAt = "2026-11-12T09:00:00+08:00",
                    signupStart = "2026-11-10T20:00:00+08:00",
                ),
            ),
        )
        val event = ReminderPlanner.planNoticeSignup(
            notices = data,
            nowMillis = now,
            settings = defaultSettings(),
            zone = zone,
        ).single()
        assertEquals(ReminderKind.NOTICE_SIGNUP, event.kind)
        assertEquals(at(11, 10, 19, 55), event.triggerAtMillis)
        assertEquals("活动开抢提醒", event.title)
        assertTrue(event.body.contains("秋游报名"))
        assertTrue(event.body.contains("11-10 20:00"))
        assertEquals("nt", event.toolId)
    }

    @Test
    fun `已过报名开始时间或无报名时间不提醒开抢`() {
        val past = notices(
            noticeEntry(noticeGroup("g1", signupStart = "2026-11-09T09:00:00+08:00", eventAt = "2026-11-12T09:00:00+08:00")),
        )
        assertTrue(ReminderPlanner.planNoticeSignup(notices = past, nowMillis = now, zone = zone).isEmpty())
        val noSignup = notices(noticeEntry(noticeGroup("g2", eventAt = "2026-11-12T09:00:00+08:00")))
        assertTrue(ReminderPlanner.planNoticeSignup(notices = noSignup, nowMillis = now, zone = zone).isEmpty())
    }

    // ------------------------------------------------------------ 投票

    @Test
    fun `新投票与投票结果按快照提醒`() {
        val polls = listOf(
            poll("p-new", title = "班会时间"),
            poll("p-closed", title = "班服颜色", closed = true),
        )
        val events = ReminderPlanner.planVotes(
            polls = polls,
            nowMillis = now,
            settings = defaultSettings(),
            snapshot = baseline(pollIds = listOf("p-old"), closedPollIds = emptyList()),
            zone = zone,
        )
        val kinds = events.map { it.kind }.toSet()
        assertTrue(kinds.contains(ReminderKind.VOTE_NEW))
        assertTrue(kinds.contains(ReminderKind.VOTE_RESULT))
        val newPoll = events.first { it.kind == ReminderKind.VOTE_NEW }
        assertEquals("新投票", newPoll.title)
        assertEquals("班会时间", newPoll.body)
        assertEquals("vote", newPoll.toolId)
        val result = events.first { it.kind == ReminderKind.VOTE_RESULT }
        assertEquals("投票结果可查看", result.title)
        assertEquals("班服颜色", result.body)
    }

    @Test
    fun `投票首轮只建基线且已结束的投票不重复提醒`() {
        val polls = listOf(poll("p1", closed = true))
        assertTrue(
            ReminderPlanner.planVotes(
                polls = polls,
                nowMillis = now,
                snapshot = ReminderSnapshot(initialized = false),
                zone = zone,
            ).isEmpty(),
        )
        assertTrue(
            ReminderPlanner.planVotes(
                polls = polls,
                nowMillis = now,
                snapshot = baseline(pollIds = listOf("p1"), closedPollIds = listOf("p1")),
                zone = zone,
            ).isEmpty(),
        )
    }

    @Test
    fun `截止后可见结果的投票在截止后 1 分钟提醒`() {
        val deadline = now + 2 * 60 * 60_000L
        val polls = listOf(poll("p1", closed = false, deadline = deadline))
        val event = ReminderPlanner.planVotes(
            polls = polls,
            nowMillis = now,
            snapshot = baseline(pollIds = listOf("p1")),
            zone = zone,
        ).first { it.kind == ReminderKind.VOTE_RESULT }
        assertEquals(deadline + ReminderPlanner.VOTE_RESULT_DELAY_MS, event.triggerAtMillis)
    }

    @Test
    fun `投票开关关闭时无事件`() {
        val polls = listOf(poll("p1", closed = true))
        val settings = defaultSettings().copy(voteNewEnabled = false, voteResultEnabled = false)
        assertTrue(
            ReminderPlanner.planVotes(
                polls = polls,
                nowMillis = now,
                settings = settings,
                snapshot = baseline(),
                zone = zone,
            ).isEmpty(),
        )
    }

    // ------------------------------------------------------------ 水费

    private fun waterRoom(vararg records: WaterRecord): WaterRoomState =
        WaterRoomState(persons = 4, records = records.toList())

    @Test
    fun `水费产生用量时提醒并带费用与扣电`() {
        val room = waterRoom(
            WaterRecord(month = "2026-10", reading = 100.0),
            WaterRecord(month = "2026-11", reading = 110.0),
        )
        val event = ReminderPlanner.planWater(
            roomKey = "4-23A---305",
            room = room,
            nowMillis = now,
            settings = defaultSettings(),
            snapshot = baseline(waterKey = "4-23A---305|2026-10|100.00"),
            zone = zone,
        ).single()
        assertEquals(ReminderKind.WATER_DEDUCT, event.kind)
        assertEquals("水费扣除提醒", event.title)
        assertTrue(event.body.contains("2026-11"))
        assertTrue(event.body.contains("用量 10"))
        // 4 人间免费 8 吨 → 超 2 吨 × 3.03 元 = 6.06 元
        assertTrue(event.body.contains("水费 ¥6.06"))
        assertTrue(event.body.contains("扣电"))
        assertEquals("water", event.toolId)
        assertTrue(event.key.startsWith("water:"))
    }

    @Test
    fun `水量在免费额度内或首轮不提醒`() {
        val room = waterRoom(
            WaterRecord(month = "2026-10", reading = 100.0),
            WaterRecord(month = "2026-11", reading = 104.0),
        )
        assertTrue(
            ReminderPlanner.planWater(
                roomKey = "rv",
                room = room,
                nowMillis = now,
                settings = defaultSettings(),
                snapshot = baseline(waterKey = "rv|2026-10|100.00"),
                zone = zone,
            ).isEmpty(),
        )
        val charged = waterRoom(
            WaterRecord(month = "2026-10", reading = 100.0),
            WaterRecord(month = "2026-11", reading = 120.0),
        )
        assertTrue(
            ReminderPlanner.planWater(
                roomKey = "rv",
                room = charged,
                nowMillis = now,
                snapshot = ReminderSnapshot(initialized = false),
                zone = zone,
            ).isEmpty(),
        )
    }

    @Test
    fun `同一抄表指纹只提醒一次`() {
        val room = waterRoom(
            WaterRecord(month = "2026-10", reading = 100.0),
            WaterRecord(month = "2026-11", reading = 120.0),
        )
        val key = ReminderPlanner.waterFingerprint("rv", "2026-11", 120.0)
        assertEquals("rv|2026-11|120.00", key)
        assertTrue(
            ReminderPlanner.planWater(
                roomKey = "rv",
                room = room,
                nowMillis = now,
                snapshot = baseline(waterKey = key),
                zone = zone,
            ).isEmpty(),
        )
    }

    // ------------------------------------------------------------ 快照

    @Test
    fun `快照合并去重并保留最近 300 条`() {
        val previous = ReminderSnapshot(
            initialized = true,
            hwIds = (1..300).map { "hw$it" },
            waterKey = "rv|2026-10|1.00",
        )
        val next = ReminderPlanner.nextSnapshot(
            rows = listOf(homework("hw-new", day = 11)),
            previous = previous,
        )
        assertTrue(next.initialized)
        assertEquals(300, next.hwIds.size)
        assertTrue(next.hwIds.contains("hw-new"))
        assertFalse(next.hwIds.contains("hw1"))
        assertEquals("rv|2026-10|1.00", next.waterKey)
    }

    @Test
    fun `快照编解码容错`() {
        val snapshot = ReminderSnapshot(
            initialized = true,
            hwIds = listOf("a", "b"),
            ntIds = listOf("n"),
            pollIds = listOf("p"),
            closedPollIds = listOf("c"),
            waterKey = "rv|2026-11|120.00",
        )
        assertEquals(snapshot, ReminderSnapshotCodec.decode(ReminderSnapshotCodec.encode(snapshot)))
        assertEquals(ReminderSnapshot(), ReminderSnapshotCodec.decode("not-json"))
        assertEquals(ReminderSnapshot(), ReminderSnapshotCodec.decode(""))
    }

    @Test
    fun `快照存储在内存实现中生效`() = kotlinx.coroutines.test.runTest {
        val store = InMemoryReminderSnapshotStore()
        assertEquals(ReminderSnapshot(), store.load())
        val saved = ReminderSnapshot(initialized = true, hwIds = listOf("hw1"))
        store.save(saved)
        assertEquals(saved, store.load())
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
        assertEquals("hw", ReminderIntentFactory.toolIdFor(ReminderKind.HOMEWORK_NEW))
        assertEquals("hw", ReminderIntentFactory.toolIdFor(ReminderKind.HOMEWORK_DIGEST))
        assertEquals("hw", ReminderIntentFactory.toolIdFor(ReminderKind.HOMEWORK_DUE))
        assertEquals("tt", ReminderIntentFactory.toolIdFor(ReminderKind.COURSE_SOON))
        assertEquals("nt", ReminderIntentFactory.toolIdFor(ReminderKind.NOTICE_SIGNUP))
        assertEquals("vote", ReminderIntentFactory.toolIdFor(ReminderKind.VOTE_RESULT))
        assertEquals("water", ReminderIntentFactory.toolIdFor(ReminderKind.WATER_DEDUCT))
        assertEquals("hw", ReminderIntentFactory.toolIdFromExtras("hw"))
        assertEquals("vote", ReminderIntentFactory.toolIdFromExtras("vote"))
        assertEquals("water", ReminderIntentFactory.toolIdFromExtras("water"))
        assertEquals("ppt", ReminderIntentFactory.toolIdFromExtras("ppt"))
        assertEquals("file", ReminderIntentFactory.toolIdFromExtras("file"))
        assertNull(ReminderIntentFactory.toolIdFromExtras("power"))
        assertNull(ReminderIntentFactory.toolIdFromExtras(""))
        assertEquals("2026-11-09", ReminderPlanner.formatDate(now, zone))
        assertEquals("11-09 20:00", ReminderPlanner.formatAt(at(11, 9, 20), zone))
    }

    @Test
    fun `设置规范化钳制非法值`() {
        val settings = ReminderSettings(
            homeworkDigestHour = 99,
            homeworkDigestMinute = -1,
            leadMinutes = 45,
            classLeadMinutes = 1,
            quietStartHour = 42,
            quietEndHour = -7,
        ).sanitized()
        assertEquals(23, settings.homeworkDigestHour)
        assertEquals(0, settings.homeworkDigestMinute)
        assertEquals(ReminderSettings.DEFAULT_LEAD_MINUTES, settings.leadMinutes)
        assertEquals(ReminderSettings.DEFAULT_CLASS_LEAD_MINUTES, settings.classLeadMinutes)
        assertEquals(23, settings.quietStartHour)
        assertEquals(0, settings.quietEndHour)
    }

    @Test
    fun `提前档位规范化与文案`() {
        assertEquals(10, ReminderSettings.normalizeLead(10))
        assertEquals(1440, ReminderSettings.normalizeLead(1440))
        assertEquals(60, ReminderSettings.normalizeLead(999))
        assertEquals(30, ReminderSettings.normalizeClassLead(30))
        assertEquals(10, ReminderSettings.normalizeClassLead(7))
        assertEquals("10 分钟", ReminderSettings.leadLabel(10))
        assertEquals("1 小时", ReminderSettings.leadLabel(60))
        assertEquals("1 天", ReminderSettings.leadLabel(1440))
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
        assertTrue(settings.homeworkNewEnabled)
        assertTrue(settings.homeworkDueEnabled)
        assertTrue(settings.classRemindEnabled)
        assertTrue(settings.noticeNewEnabled)
        assertTrue(settings.noticeDeadlineEnabled)
        assertTrue(settings.noticeSignupEnabled)
        assertTrue(settings.voteNewEnabled)
        assertTrue(settings.voteResultEnabled)
        assertTrue(settings.waterDeductEnabled)
        assertTrue(settings.digestEnabled)
        assertEquals(20, settings.homeworkDigestHour)
        assertEquals(60, settings.leadMinutes)
        assertEquals(10, settings.classLeadMinutes)
        assertEquals(listOf(10, 30, 60, 120, 1440), ReminderSettings.LEAD_OPTIONS)
        assertEquals(listOf(5, 10, 15, 30), ReminderSettings.CLASS_LEAD_OPTIONS)
        assertEquals(10, settings.enabledCount())
    }

    @Test
    fun `作业内容为空时回退内容首行作为名称`() {
        val row = homework("hw-empty", subject = "", content = "第三章习题", day = 10)
        val events = ReminderPlanner.planHomework(listOf(row), nowMillis = now, zone = zone)
        val digest = events.first { it.kind == ReminderKind.HOMEWORK_DIGEST }
        assertTrue(digest.body.contains("第三章习题"))
    }
}
