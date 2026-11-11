package com.furina.classtools.data.reminder

import com.furina.classtools.data.permission.PermissionState
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v3.7 单条提醒可靠性测试：投递方式决策（精确闹钟 vs WorkManager 回退）、重排计划、
 * 闹钟载荷编解码、混合调度器（含取消/上限/权限）、排程元数据 targetAt 往返。
 */
class ReminderAlarmTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Long = LocalDateTime.of(2026, 11, 11, 21, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun pending(
        kind: OneShotTargetKind = OneShotTargetKind.HOMEWORK,
        id: String = "hw1",
        fireAt: Long = now + 3_600_000L,
        label: String = "高等数学",
        targetAt: Long = now + 4_600_000L,
    ): OneShotPending = OneShotPending(
        key = ReminderOneShot.key(kind, id, fireAt),
        label = label,
        fireAtMillis = fireAt,
        targetAtMillis = targetAt,
    )

    // ------------------------------------------------------------ fakes

    private class FakeFallback : ReminderOneShotScheduler {
        data class HomeworkCall(val rowId: String, val fireAtMillis: Long, val label: String, val targetAtMillis: Long)
        data class NoticeCall(val groupId: String, val fireAtMillis: Long, val label: String, val targetAtMillis: Long)

        val homeworkCalls = mutableListOf<HomeworkCall>()
        val noticeCalls = mutableListOf<NoticeCall>()
        val cancelled = mutableListOf<OneShotKey>()
        var cancelAllCalls = 0
        var listPendingValue: List<OneShotPending> = emptyList()

        override suspend fun scheduleHomeworkReminder(
            rowId: String,
            fireAtMillis: Long,
            label: String,
            targetAtMillis: Long,
        ): OneShotScheduleResult {
            homeworkCalls += HomeworkCall(rowId, fireAtMillis, label, targetAtMillis)
            return OneShotScheduleResult.Scheduled(ReminderOneShot.key(OneShotTargetKind.HOMEWORK, rowId, fireAtMillis), fireAtMillis)
        }

        override suspend fun scheduleNoticeReminder(
            groupId: String,
            fireAtMillis: Long,
            label: String,
            targetAtMillis: Long,
        ): OneShotScheduleResult {
            noticeCalls += NoticeCall(groupId, fireAtMillis, label, targetAtMillis)
            return OneShotScheduleResult.Scheduled(ReminderOneShot.key(OneShotTargetKind.NOTICE, groupId, fireAtMillis), fireAtMillis)
        }

        override suspend fun cancel(key: OneShotKey) {
            cancelled += key
        }

        override suspend fun cancelAll() {
            cancelAllCalls += 1
        }

        override suspend fun listPending(): List<OneShotPending> = listPendingValue
    }

    private class FakeAlarmScheduler : OneShotAlarmScheduler {
        val scheduled = mutableListOf<AlarmReminderPayload>()
        val cancelled = mutableListOf<OneShotKey>()
        var cancelAllCalls = 0

        override fun schedule(payload: AlarmReminderPayload) {
            scheduled += payload
        }

        override fun cancel(key: OneShotKey) {
            cancelled += key
        }

        override fun cancelAll(payloads: List<AlarmReminderPayload>) {
            cancelAllCalls += 1
            payloads.forEach { cancelled += it.key() }
        }
    }

    private fun hybrid(
        fallback: FakeFallback = FakeFallback(),
        alarms: FakeAlarmScheduler = FakeAlarmScheduler(),
        store: OneShotPendingStore = InMemoryOneShotPendingStore(),
        permission: PermissionState = PermissionState.GRANTED,
        exact: Boolean = true,
        clock: () -> Long = { now },
    ): HybridOneShotScheduler = HybridOneShotScheduler(
        fallback = fallback,
        alarmScheduler = alarms,
        pendingStore = store,
        permissionProvider = { permission },
        exactAlarmAvailability = { exact },
        clock = clock,
    )

    // ------------------------------------------------------------ 决策

    @Test
    fun `精确闹钟可用时选择AlarmManager`() {
        val plan = OneShotDeliveryDecision.decide(PermissionState.GRANTED, canScheduleExactAlarms = true)
        assertEquals(OneShotDeliveryMode.EXACT_ALARM, plan.mode)
        assertTrue(plan.reason.contains("setExactAndAllowWhileIdle"))
    }

    @Test
    fun `精确闹钟未授权回退WorkManager`() {
        val plan = OneShotDeliveryDecision.decide(PermissionState.GRANTED, canScheduleExactAlarms = false)
        assertEquals(OneShotDeliveryMode.WORK_MANAGER, plan.mode)
        assertTrue(plan.reason.contains("canScheduleExactAlarms"))
    }

    @Test
    fun `通知权限缺失回退WorkManager`() {
        val plan = OneShotDeliveryDecision.decide(
            PermissionState.PERMANENTLY_DENIED,
            canScheduleExactAlarms = true,
        )
        assertEquals(OneShotDeliveryMode.WORK_MANAGER, plan.mode)
        assertTrue(plan.reason.contains("权限"))
    }

    @Test
    fun `投递模式中文标签`() {
        assertEquals("精确闹钟", OneShotDeliveryMode.EXACT_ALARM.label)
        assertEquals("WorkManager 近似", OneShotDeliveryMode.WORK_MANAGER.label)
    }

    // ------------------------------------------------------------ 载荷

    @Test
    fun `闹钟载荷由排程元数据生成并可还原`() {
        val item = pending(label = "  数学作业  ")
        val payload = AlarmReminderPayload.of(item)
        assertEquals(OneShotTargetKind.HOMEWORK, payload.kind)
        assertEquals("hw1", payload.targetId)
        assertEquals("数学作业", payload.label)
        assertEquals(item.fireAtMillis, payload.fireAtMillis)
        assertEquals(item.targetAtMillis, payload.targetAtMillis)
        assertEquals(item.key, payload.key())
        assertEquals("数学作业", payload.toPending().label)
        assertEquals(item.fireAtMillis, payload.toPending().fireAtMillis)
    }

    @Test
    fun `标签净化空值与超长`() {
        assertEquals("班级提醒", AlarmReminderPayload.sanitizeLabel("   "))
        assertEquals(AlarmReminderPayload.MAX_LABEL_LENGTH, AlarmReminderPayload.sanitizeLabel("x".repeat(500)).length)
    }

    @Test
    fun `闹钟触发事件带工具深链`() {
        val homework = pending().let { AlarmReminderPayload.of(it) }.event(zone)
        assertEquals(ReminderKind.HOMEWORK_DUE, homework.kind)
        assertEquals("hw", homework.toolId)
        assertNull(homework.channelIdOverride)

        val notice = pending(kind = OneShotTargetKind.NOTICE, id = "g1").let { AlarmReminderPayload.of(it) }.event(zone)
        assertEquals(ReminderKind.NOTICE_DUE, notice.kind)
        assertEquals("nt", notice.toolId)
    }

    @Test
    fun `排程元数据targetAt编解码往返`() {
        val item = pending()
        val decoded = OneShotPendingCodec.decode(OneShotPendingCodec.encode(listOf(item))).single()
        assertEquals(item.key, decoded.key)
        assertEquals(item.targetAtMillis, decoded.targetAtMillis)
    }

    @Test
    fun `旧版排程元数据缺失targetAt时回退fireAt`() {
        val raw = "[{\"kind\":\"HOMEWORK\",\"id\":\"hw1\",\"label\":\"数学\",\"fireAt\":${now + 1000}}]"
        val decoded = OneShotPendingCodec.decode(raw).single()
        assertEquals(now + 1000, decoded.fireAtMillis)
        assertEquals(now + 1000, decoded.targetAtMillis)
    }

    // ------------------------------------------------------------ 重排

    @Test
    fun `重排计划区分未来已到点与过期`() {
        val future = pending(id = "f", fireAt = now + 3_600_000L)
        val grace = pending(id = "g", fireAt = now - 60_000L)
        val expired = pending(id = "e", fireAt = now - OneShotRearmPlanner.GRACE_MS - 60_000L)
        val farFuture = pending(id = "x", fireAt = now + OneShotRearmPlanner.MAX_FUTURE_MS + 60_000L)
        val plan = OneShotRearmPlanner.plan(listOf(future, grace, expired, farFuture), now)

        assertEquals(listOf(grace, future), plan.toSchedule)
        assertEquals(listOf(expired), plan.expired)
    }

    @Test
    fun `重排计划去重排序且忽略非法项`() {
        val a = pending(id = "a", fireAt = now + 2000L)
        val b = pending(id = "b", fireAt = now + 1000L)
        val invalid = OneShotPending(
            key = OneShotKey(OneShotTargetKind.HOMEWORK, "", 1L),
            label = "",
            fireAtMillis = now + 500L,
        )
        val plan = OneShotRearmPlanner.plan(listOf(a, b, a, invalid), now)
        assertEquals(listOf(b, a), plan.toSchedule)
    }

    @Test
    fun `重排触发时刻对未来保持原值 对已过点尽快补发`() {
        val future = pending(fireAt = now + 60_000L)
        assertEquals(future.fireAtMillis, OneShotRearmPlanner.triggerAtMillis(future, now))
        val grace = pending(fireAt = now - 60_000L)
        assertEquals(
            now + OneShotRearmPlanner.IMMEDIATE_DELAY_MS,
            OneShotRearmPlanner.triggerAtMillis(grace, now),
        )
    }

    // ------------------------------------------------------------ 混合调度器

    @Test
    fun `精确闹钟可用时排程走AlarmManager并写元数据`() = runTest {
        val alarms = FakeAlarmScheduler()
        val fallback = FakeFallback()
        val store = InMemoryOneShotPendingStore()
        val scheduler = hybrid(fallback = fallback, alarms = alarms, store = store)

        val target = now + 7_200_000L
        val result = scheduler.scheduleHomeworkReminder("hw1", target, "数学", target)
        assertTrue(result is OneShotScheduleResult.Scheduled)
        assertEquals(1, alarms.scheduled.size)
        val payload = alarms.scheduled.single()
        assertEquals(target, payload.fireAtMillis)
        assertEquals(target, payload.targetAtMillis)
        assertTrue(fallback.homeworkCalls.isEmpty())
        val saved = store.load().single()
        assertEquals(target, saved.fireAtMillis)
        assertEquals(target, saved.targetAtMillis)
    }

    @Test
    fun `精确闹钟不可用时回退WorkManager`() = runTest {
        val alarms = FakeAlarmScheduler()
        val fallback = FakeFallback()
        val store = InMemoryOneShotPendingStore()
        val scheduler = hybrid(fallback = fallback, alarms = alarms, store = store, exact = false)

        val target = now + 3_600_000L
        val result = scheduler.scheduleNoticeReminder("g1", target, "班会", target)
        assertTrue(result is OneShotScheduleResult.Scheduled)
        assertEquals(1, fallback.noticeCalls.size)
        assertEquals("g1", fallback.noticeCalls.single().groupId)
        assertEquals(target, fallback.noticeCalls.single().fireAtMillis)
        assertTrue(alarms.scheduled.isEmpty())
    }

    @Test
    fun `切换投递方式时清理旧任务`() = runTest {
        val alarms = FakeAlarmScheduler()
        val fallback = FakeFallback()
        val store = InMemoryOneShotPendingStore()
        val scheduler = hybrid(fallback = fallback, alarms = alarms, store = store)

        val target = now + 3_600_000L
        scheduler.scheduleHomeworkReminder("hw1", target, "数学", target)
        val key = ReminderOneShot.key(OneShotTargetKind.HOMEWORK, "hw1", target)
        assertTrue(fallback.cancelled.contains(key))
        assertTrue(alarms.cancelled.contains(key))
    }

    @Test
    fun `权限缺失返回明确结果且不排程`() = runTest {
        val alarms = FakeAlarmScheduler()
        val fallback = FakeFallback()
        val scheduler = hybrid(
            fallback = fallback,
            alarms = alarms,
            permission = PermissionState.REQUESTABLE,
        )
        val result = scheduler.scheduleHomeworkReminder("hw1", now + 3_600_000L, "数学", now + 3_600_000L)
        assertEquals(OneShotScheduleResult.PermissionMissing, result)
        assertTrue(alarms.scheduled.isEmpty())
        assertTrue(fallback.homeworkCalls.isEmpty())
    }

    @Test
    fun `时间过近返回TooLate`() = runTest {
        val scheduler = hybrid()
        val result = scheduler.scheduleHomeworkReminder(
            "hw1",
            now + ReminderOneShot.MIN_LEAD_MILLIS / 2,
            "数学",
            now + 3_600_000L,
        )
        assertTrue(result is OneShotScheduleResult.TooLate)
    }

    @Test
    fun `超过每类上限返回LimitReached`() = runTest {
        val store = InMemoryOneShotPendingStore(
            (1..ReminderOneShot.MAX_PER_CATEGORY).map { index ->
                pending(id = "h$index", fireAt = now + 3_600_000L + index)
            },
        )
        val scheduler = hybrid(store = store)
        val result = scheduler.scheduleHomeworkReminder("new", now + 7_200_000L, "数学", now + 7_200_000L)
        assertTrue(result is OneShotScheduleResult.LimitReached)
        assertEquals(ReminderOneShot.MAX_PER_CATEGORY, (result as OneShotScheduleResult.LimitReached).limit)
    }

    @Test
    fun `取消同时清理闹钟与回退任务`() = runTest {
        val alarms = FakeAlarmScheduler()
        val fallback = FakeFallback()
        val item = pending()
        val store = InMemoryOneShotPendingStore(listOf(item))
        val scheduler = hybrid(fallback = fallback, alarms = alarms, store = store)

        scheduler.cancel(item.key)
        assertTrue(alarms.cancelled.contains(item.key))
        assertTrue(fallback.cancelled.contains(item.key))
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun `取消全部同时清理两侧`() = runTest {
        val alarms = FakeAlarmScheduler()
        val fallback = FakeFallback()
        val store = InMemoryOneShotPendingStore(
            listOf(pending(id = "a", fireAt = now + 1000L), pending(id = "b", fireAt = now + 2000L)),
        )
        val scheduler = hybrid(fallback = fallback, alarms = alarms, store = store)

        scheduler.cancelAll()
        assertEquals(1, alarms.cancelAllCalls)
        assertEquals(1, fallback.cancelAllCalls)
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun `待触发列表过滤过期并排序`() = runTest {
        val stale = pending(id = "old", fireAt = now - WorkManagerOneShotScheduler.STALE_WINDOW_MS - 60_000L)
        val later = pending(id = "later", fireAt = now + 2000L)
        val sooner = pending(id = "sooner", fireAt = now + 1000L)
        val store = InMemoryOneShotPendingStore(listOf(later, stale, sooner))
        val scheduler = hybrid(store = store)
        assertEquals(listOf(sooner.key, later.key), scheduler.listPending().map { it.key })
    }

    @Test
    fun `事件渠道覆盖为空时用提醒类型既有渠道`() {
        val event = ReminderEvent(
            kind = ReminderKind.COURSE_SOON,
            key = "class:1",
            triggerAtMillis = now,
            title = "即将上课",
            body = "数学",
            toolId = "tt",
        )
        assertNull(event.channelIdOverride)
        assertEquals(ReminderChannels.CLASS_ID, ReminderChannels.channelIdFor(event))
    }
}
