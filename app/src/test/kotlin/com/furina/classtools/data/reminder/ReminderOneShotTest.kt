package com.furina.classtools.data.reminder

import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.notices.NoticeGroupDto
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
import kotlinx.coroutines.test.runTest

/** v3 单条提醒纯逻辑测试：可选时间、key/上限/校验、通知事件、持久化编解码、补发窗口/分组/图标。 */
class ReminderOneShotTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Long = LocalDateTime.of(2026, 11, 9, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun homework(
        id: String = "hw1",
        month: Int = 11,
        day: Int = 10,
        hour: Int = 23,
        minute: Int = 59,
    ): HomeworkRow = HomeworkRow(
        id = id,
        subject = "高等数学",
        content = "习题",
        date = LocalDate.of(2026, month, day).toString(),
        time = "%02d:%02d".format(hour, minute),
        ts = 1L,
    )

    private fun noticeGroup(
        eventAt: String = "",
        deadline: String = "",
        title: String = "主题班会",
    ): NoticeGroupDto = NoticeGroupDto(
        id = "g1",
        title = title,
        eventAt = eventAt,
        deadline = deadline,
        eventAction = "开始",
        deadlineAction = "截止",
    )

    // ------------------------------------------------------------ 可选时间 helper

    @Test
    fun `作业提供三种提前时间且顺序固定`() {
        val options = ReminderOneShot.homeworkPlannedTimes(homework(), now, zone)
        assertEquals(3, options.size)
        assertEquals(
            listOf(
                OneShotReminderOption.BEFORE_1_DAY,
                OneShotReminderOption.BEFORE_1_HOUR,
                OneShotReminderOption.AT_DEADLINE,
            ),
            options.map { it.option },
        )
        assertEquals(at(11, 9, 23, 59), options[0].fireAtMillis)
        assertEquals(at(11, 10, 22, 59), options[1].fireAtMillis)
        assertEquals(at(11, 10, 23, 59), options[2].fireAtMillis)
        assertEquals(at(11, 10, 23, 59), options[2].targetAtMillis)
        assertTrue(options.all { it.label.contains("11-") })
    }

    @Test
    fun `已过期作业没有可选提醒时间`() {
        assertTrue(ReminderOneShot.homeworkPlannedTimes(homework(day = 9, hour = 10), now, zone).isEmpty())
        assertTrue(ReminderOneShot.homeworkPlannedTimes(homework(day = 8), now, zone).isEmpty())
    }

    @Test
    fun `无截止日期的作业没有可选时间`() {
        val row = homework().copy(date = "", time = "")
        assertTrue(ReminderOneShot.homeworkPlannedTimes(row, now, zone).isEmpty())
    }

    @Test
    fun `通知活动前1小时与截止三种时间`() {
        val group = noticeGroup(
            eventAt = "2026-11-10T16:00:00+08:00",
            deadline = "2026-11-12T18:00:00+08:00",
        )
        val options = ReminderOneShot.noticePlannedTimes(group, now, zone)
        assertEquals(4, options.size)
        val eventOption = options.first()
        assertEquals(OneShotReminderOption.BEFORE_EVENT_1_HOUR, eventOption.option)
        assertEquals(at(11, 10, 15, 0), eventOption.fireAtMillis)
        assertEquals(at(11, 10, 16, 0), eventOption.targetAtMillis)
        assertEquals(at(11, 11, 18, 0), options.first { it.option == OneShotReminderOption.BEFORE_1_DAY }.fireAtMillis)
        assertEquals(at(11, 12, 17, 0), options.first { it.option == OneShotReminderOption.BEFORE_1_HOUR }.fireAtMillis)
        assertEquals(at(11, 12, 18, 0), options.first { it.option == OneShotReminderOption.AT_DEADLINE }.fireAtMillis)
    }

    @Test
    fun `无时间通知没有可选时间`() {
        assertTrue(ReminderOneShot.noticePlannedTimes(noticeGroup(), now, zone).isEmpty())
        assertTrue(
            ReminderOneShot.noticePlannedTimes(
                noticeGroup(eventAt = "2026-11-08T16:00:00+08:00", deadline = "2026-11-08T18:00:00+08:00"),
                now,
                zone,
            ).isEmpty(),
        )
    }

    @Test
    fun `触发时间与文案`() {
        val target = at(11, 10, 23, 59)
        assertEquals(target - OneShotReminderOption.BEFORE_1_DAY.leadMillis, ReminderOneShot.triggerAt(target, OneShotReminderOption.BEFORE_1_DAY))
        assertEquals(target, ReminderOneShot.triggerAt(target, OneShotReminderOption.AT_DEADLINE))
        assertTrue(ReminderOneShot.labelOf(OneShotReminderOption.BEFORE_1_HOUR, target, zone).contains("11-10 23:59"))
        assertTrue(ReminderOneShot.labelOf(OneShotReminderOption.BEFORE_EVENT_1_HOUR, target, zone).contains("活动前 1 小时"))
    }

    // ------------------------------------------------------------ key / 校验 / 事件

    @Test
    fun `key 稳定且 workName 净化非法字符`() {
        val fireAt = at(11, 10, 23, 59)
        val key = ReminderOneShot.key(OneShotTargetKind.HOMEWORK, " hw 1/2 ", fireAt)
        assertEquals("hw 1/2", key.targetId)
        assertEquals("ct_oneshot_homework_hw_1_2_$fireAt", key.workName)
        assertEquals(key.workName, ReminderOneShot.key(OneShotTargetKind.HOMEWORK, "hw 1/2", fireAt).workName)
        assertTrue(ReminderOneShot.workName(OneShotTargetKind.NOTICE, "g1", fireAt).startsWith("ct_oneshot_notice_"))
    }

    @Test
    fun `排程校验返回明确错误`() {
        val fireAt = now + 60 * 60_000L
        assertEquals(
            OneShotScheduleResult.EmptyTarget,
            ReminderOneShot.validate(OneShotTargetKind.HOMEWORK, "", fireAt, now, PermissionState.GRANTED, 0),
        )
        assertEquals(
            OneShotScheduleResult.PermissionMissing,
            ReminderOneShot.validate(OneShotTargetKind.HOMEWORK, "hw1", fireAt, now, PermissionState.REQUESTABLE, 0),
        )
        assertEquals(
            OneShotScheduleResult.TooLate(fireAt),
            ReminderOneShot.validate(OneShotTargetKind.HOMEWORK, "hw1", fireAt, now + 60 * 60_000L, PermissionState.GRANTED, 0),
        )
        assertEquals(
            OneShotScheduleResult.LimitReached(ReminderOneShot.MAX_PER_CATEGORY),
            ReminderOneShot.validate(
                OneShotTargetKind.HOMEWORK,
                "hw1",
                fireAt,
                now,
                PermissionState.GRANTED,
                ReminderOneShot.MAX_PER_CATEGORY,
            ),
        )
        assertNull(ReminderOneShot.validate(OneShotTargetKind.HOMEWORK, "hw1", fireAt, now, PermissionState.GRANTED, 19))
    }

    @Test
    fun `单条提醒事件工具与 key 正确`() {
        val target = at(11, 10, 23, 59)
        val homeworkEvent = ReminderOneShot.eventFor(OneShotTargetKind.HOMEWORK, "hw1", "高数", target, zone)
        assertEquals(ReminderKind.HOMEWORK_DUE, homeworkEvent.kind)
        assertEquals("hw", homeworkEvent.toolId)
        assertEquals("oneshot:homework:hw1", homeworkEvent.key)
        assertTrue(homeworkEvent.body.contains("高数"))

        val noticeEvent = ReminderOneShot.eventFor(OneShotTargetKind.NOTICE, "g1", "班会", target, zone)
        assertEquals(ReminderKind.NOTICE_DUE, noticeEvent.kind)
        assertEquals("nt", noticeEvent.toolId)
        assertEquals("oneshot:notice:g1", noticeEvent.key)
    }

    // ------------------------------------------------------------ 持久化编解码 / 列表

    @Test
    fun `单条排程元数据编解码往返`() {
        val items = listOf(
            OneShotPending(
                key = OneShotKey(OneShotTargetKind.HOMEWORK, "hw-1", 100L),
                label = "截止前 1 天（11-09 23:59）",
                fireAtMillis = 100L,
            ),
            OneShotPending(
                key = OneShotKey(OneShotTargetKind.NOTICE, "g1", 200L),
                label = "活动前 1 小时",
                fireAtMillis = 200L,
            ),
        )
        assertEquals(items, OneShotPendingCodec.decode(OneShotPendingCodec.encode(items)))
        assertTrue(OneShotPendingCodec.decode(null).isEmpty())
        assertTrue(OneShotPendingCodec.decode("not-json").isEmpty())
        assertTrue(OneShotPendingCodec.decode("""[{"kind":"BAD","id":"x","fireAt":1}]""").isEmpty())
    }

    @Test
    fun `内存单条排程存储增删清`() = runTest {
        val store = InMemoryOneShotPendingStore()
        val item = OneShotPending(OneShotKey(OneShotTargetKind.HOMEWORK, "hw1", 10L), "label", 10L)
        store.add(item)
        store.add(item)
        assertEquals(1, store.load().size)
        store.remove(item.key)
        assertTrue(store.load().isEmpty())
        store.add(item)
        store.clear()
        assertTrue(store.load().isEmpty())
    }

    // ------------------------------------------------------------ 错过补发

    @Test
    fun `补发回看窗口计算`() {
        assertEquals(ReminderCatchUp.DEFAULT_GRACE_MS, ReminderCatchUp.lookbackMillis(now, null))
        assertEquals(30 * 60_000L, ReminderCatchUp.lookbackMillis(now, now - 30 * 60_000L))
        assertEquals(
            ReminderCatchUp.MAX_LOOKBACK_MS,
            ReminderCatchUp.lookbackMillis(now, now - 24L * 60 * 60_000L),
        )
        assertEquals(ReminderCatchUp.DEFAULT_GRACE_MS, ReminderCatchUp.lookbackMillis(now, now + 1000L))
        assertTrue(ReminderCatchUp.isCatchUp(now - 30 * 60_000L, now))
        assertFalse(ReminderCatchUp.isCatchUp(now - 5 * 60_000L, now))
        assertFalse(ReminderCatchUp.isCatchUp(now - 24L * 60 * 60_000L, now))
    }

    @Test
    fun `下次运行只保留未来事件并去重限流`() {
        val events = listOf(
            ReminderEvent(ReminderKind.HOMEWORK_DUE, "past", now - 1000L, "t", "b"),
            ReminderEvent(ReminderKind.HOMEWORK_DUE, "future1", now + 1000L, "t", "b"),
            ReminderEvent(ReminderKind.HOMEWORK_DUE, "future2", now + 2000L, "t", "b"),
            ReminderEvent(ReminderKind.HOMEWORK_DUE, "future1", now + 1000L, "t", "b"),
        )
        val pending = ReminderPendingCatchUp.pendingForNextRun(events, now)
        assertEquals(listOf("future1", "future2"), pending.map { it.key })
        assertEquals(1, ReminderPendingCatchUp.pendingForNextRun(events, now, maxEvents = 1).size)
    }

    @Test
    fun `错过事件补发窗口与已规划去重`() {
        val pending = listOf(
            ReminderEvent(ReminderKind.HOMEWORK_DUE, "missed", now - 60 * 60_000L, "t", "b"),
            ReminderEvent(ReminderKind.HOMEWORK_DUE, "too-old", now - 7L * 60 * 60_000L, "t", "b"),
            ReminderEvent(ReminderKind.HOMEWORK_DUE, "future", now + 60_000L, "t", "b"),
            ReminderEvent(ReminderKind.HOMEWORK_DUE, "planned", now - 30 * 60_000L, "t", "b"),
        )
        val catchUp = ReminderPendingCatchUp.dueCatchUp(
            pending = pending,
            nowMillis = now,
            lookbackMillis = 6L * 60 * 60_000L,
            keysAlreadyPlanned = setOf("planned"),
        )
        assertEquals(listOf("missed"), catchUp.map { it.key })
    }

    @Test
    fun `待发事件编解码往返且过滤坏行`() {
        val event = ReminderEvent(
            kind = ReminderKind.NOTICE_DUE,
            key = "notice:g1:deadline",
            triggerAtMillis = 123456L,
            title = "通知即将截止：班会",
            body = "截止 · 11-10 18:00",
            toolId = "nt",
        )
        val decoded = ReminderPendingCodec.decode(ReminderPendingCodec.encode(listOf(event)))
        assertEquals(listOf(event), decoded)
        assertTrue(ReminderPendingCodec.decode(null).isEmpty())
        assertTrue(ReminderPendingCodec.decode("[]").isEmpty())
        assertTrue(ReminderPendingCodec.decode("""[{"kind":"NOTICE_DUE"}]""").isEmpty())
    }

    // ------------------------------------------------------------ 通知分组 / 小图标 / 日志

    @Test
    fun `通知分组按类型折叠且摘要 id 稳定`() {
        assertEquals(
            ReminderGrouping.GROUP_HOMEWORK,
            ReminderGrouping.groupKey(ReminderKind.HOMEWORK_DIGEST),
        )
        assertEquals(
            ReminderGrouping.GROUP_HOMEWORK,
            ReminderGrouping.groupKey(ReminderKind.HOMEWORK_DUE),
        )
        assertEquals(ReminderGrouping.GROUP_COURSE, ReminderGrouping.groupKey(ReminderKind.COURSE_SOON))
        assertEquals(ReminderGrouping.GROUP_NOTICE, ReminderGrouping.groupKey(ReminderKind.NOTICE_DUE))
        assertEquals(3, setOf(
            ReminderGrouping.GROUP_HOMEWORK,
            ReminderGrouping.GROUP_COURSE,
            ReminderGrouping.GROUP_NOTICE,
        ).size)
        assertEquals(
            ReminderGrouping.summaryId(ReminderKind.HOMEWORK_DUE),
            ReminderGrouping.summaryId(ReminderKind.HOMEWORK_DIGEST),
        )
        assertTrue(ReminderGrouping.summaryText(3).contains("3 条"))
        assertTrue(ReminderGrouping.summaryTitle(ReminderKind.COURSE_SOON).contains("上课"))
    }

    @Test
    fun `小图标优先专用 drawable 缺失回退系统占位`() {
        val found = ReminderSmallIcon.resolve { name -> if (name == ReminderSmallIcon.RES_NAME) 42 else null }
        assertEquals(42, found)
        assertFalse(ReminderSmallIcon.isFallback(found))
        assertEquals(ReminderSmallIcon.FALLBACK_RES, ReminderSmallIcon.resolve { null })
        assertEquals(ReminderSmallIcon.FALLBACK_RES, ReminderSmallIcon.resolve { 0 })
        assertTrue(ReminderSmallIcon.isFallback(ReminderSmallIcon.FALLBACK_RES))
    }

    @Test
    fun `运行结果日志摘要不含通知正文`() {
        val result = ReminderRunResult(
            sent = 2,
            skipped = 1,
            candidates = 5,
            reason = "已发送 2 条",
            permission = "GRANTED",
            catchUpEvents = 1,
        )
        val summary = ReminderLog.summary(result)
        assertTrue(summary.startsWith("[CT_REMINDER]"))
        assertTrue(summary.contains("sent=2"))
        assertTrue(summary.contains("catchUp=1"))
        assertFalse(summary.contains("通知即将"))
        assertTrue(ReminderLog.summary(result).length < 200)
    }

    @Test
    fun `诊断摘要包含设置与缓存字段`() {
        val diagnostics = ReminderDiagnostics(
            settings = ReminderSettings.DEFAULT,
            permission = PermissionState.GRANTED,
            homeworkCacheCount = 3,
            noticeCacheCount = 2,
            timetableReady = true,
        )
        assertTrue(diagnostics.settingsSummary.contains("模块=10/10 开"))
        assertTrue(diagnostics.cacheSummary.contains("作业 3 条"))
        assertTrue(diagnostics.cacheSummary.contains("课表可用"))
        assertTrue(diagnostics.cacheSummary.contains("投票 0 条"))
        assertEquals(2, diagnostics.noticeCacheCount)
        assertTrue(diagnostics.channelIds.contains("notice_due"))
        assertTrue(diagnostics.channelIds.contains("vote_alert"))
        assertTrue(diagnostics.channelIds.contains("water_alert"))
        assertEquals(ReminderSmallIcon.RES_NAME, diagnostics.smallIconName)
    }
}
