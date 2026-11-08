package com.furina.classtools.data.reminder

import android.content.Intent
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.notices.NoticesData
import com.furina.classtools.data.permission.PermissionController
import com.furina.classtools.data.permission.PermissionSnapshot
import com.furina.classtools.data.permission.PermissionState
import com.furina.classtools.data.permission.PermissionType
import com.furina.classtools.data.timetable.SessionInfo
import com.furina.classtools.data.timetable.SessionState
import com.furina.classtools.data.timetable.TodayState
import com.furina.classtools.data.timetable.TtPlan
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v3 引擎测试：可测试注入（假 store/notifier/permission/data）、诊断字段、测试通知、错过补发。 */
class ReminderEngineTest {

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

    private fun coursePlan(startAt: Long): TtPlan = TtPlan(
        hasData = true,
        week = 1,
        today = emptyList(),
        todayState = TodayState.HAS,
        next = SessionInfo(
            name = "高等数学",
            room = "01104",
            start = 1,
            end = 2,
            startText = "14:30",
            endText = "15:15",
            span = "1-2 节",
            startAt = startAt,
            endAt = startAt + 45 * 60_000L,
            state = SessionState.NEXT,
        ),
    )

    // ------------------------------------------------------------ fakes

    private class FakeSettingsStore(var value: ReminderSettings) : ReminderSettingsStore {
        private val flow = MutableStateFlow(value)
        override val settings: Flow<ReminderSettings> = flow
        override suspend fun current(): ReminderSettings = flow.value
        override suspend fun update(transform: (ReminderSettings) -> ReminderSettings): ReminderSettings {
            flow.value = transform(flow.value).sanitized()
            return flow.value
        }
    }

    private class FakePermissionController(var value: PermissionState) : PermissionController {
        override fun state(type: PermissionType): PermissionState = value
        override fun snapshot(): PermissionSnapshot = PermissionSnapshot(value, value)
        override fun markRequested(type: PermissionType) = Unit
        override fun permissionsToRequest(type: PermissionType): Array<String> = emptyArray()
        override fun settingsIntent(): Intent = throw UnsupportedOperationException("unused in tests")
        override fun notificationsEnabled(): Boolean = value == PermissionState.GRANTED
    }

    private class FakeNotifier(var succeed: Boolean = true) : ReminderNotifier {
        val events = mutableListOf<ReminderEvent>()
        var channelsEnsured = 0
        override fun ensureChannels() {
            channelsEnsured += 1
        }

        override fun notify(event: ReminderEvent): Boolean {
            events += event
            return succeed
        }
    }

    private class FakeDataProvider(
        var rows: List<HomeworkRow> = emptyList(),
        var ttPlan: TtPlan? = null,
        var notices: NoticesData? = null,
        var handledIds: Set<String> = emptySet(),
    ) : ReminderDataProvider {
        override suspend fun homeworkRows(): List<HomeworkRow> = rows
        override suspend fun homeworkDoneIds(): Set<String> = emptySet()
        override suspend fun timetablePlan(nowMillis: Long, zone: ZoneId): TtPlan? = ttPlan
        override suspend fun noticesData(): NoticesData? = notices
        override suspend fun handledNoticeIds(): Set<String> = handledIds
    }

    private fun engine(
        settings: ReminderSettings = ReminderSettings.DEFAULT,
        permission: PermissionState = PermissionState.GRANTED,
        notifier: FakeNotifier = FakeNotifier(),
        data: FakeDataProvider = FakeDataProvider(),
        runStateStore: ReminderRunStateStore = InMemoryReminderRunStateStore(),
        pendingStore: ReminderPendingEventStore = InMemoryReminderPendingEventStore(),
        clock: () -> Long = { now },
    ): ReminderEngine = ReminderEngine(
        settingsStore = FakeSettingsStore(settings),
        dedupeStore = InMemoryReminderDedupeStore(),
        notifier = notifier,
        permissionController = FakePermissionController(permission),
        dataProvider = data,
        runStateStore = runStateStore,
        pendingStore = pendingStore,
        clock = clock,
        zone = zone,
    )

    // ------------------------------------------------------------ 运行结果/诊断字段

    @Test
    fun `无到点事件时不发送但返回诊断摘要`() = runTest {
        val notifier = FakeNotifier()
        val engine = engine(notifier = notifier, data = FakeDataProvider(rows = listOf(homework())))
        val result = engine.runNow()

        assertEquals(0, result.sent)
        assertEquals("当前没有到点提醒", result.reason)
        assertTrue(result.enabled)
        assertEquals("GRANTED", result.permission)
        assertEquals(now, result.checkedAtMillis)
        assertTrue(result.settingsSummary.contains("作业=开"))
        assertTrue(result.cacheSummary.contains("作业 1 条"))
        assertTrue(notifier.events.isEmpty())
        assertEquals(0, notifier.channelsEnsured)
    }

    @Test
    fun `到点课程事件发送并二次运行去重`() = runTest {
        val notifier = FakeNotifier()
        val data = FakeDataProvider(ttPlan = coursePlan(now + 10 * 60_000L))
        val engine = engine(notifier = notifier, data = data)

        val first = engine.runNow()
        assertEquals(1, first.sent)
        assertTrue(first.reason.contains("已发送 1 条"))
        assertEquals(ReminderKind.COURSE_SOON, notifier.events.single().kind)
        assertEquals(1, notifier.channelsEnsured)

        val second = engine.runNow()
        assertEquals(0, second.sent)
        assertEquals(1, second.skipped)
        assertTrue(second.reason.contains("去重"))
        assertEquals(1, notifier.events.size)
    }

    @Test
    fun `通知权限缺失时静默并返回原因`() = runTest {
        val notifier = FakeNotifier()
        val engine = engine(permission = PermissionState.REQUESTABLE, notifier = notifier)
        val result = engine.runNow()
        assertEquals("通知权限未开启", result.disabledReason)
        assertEquals("通知权限未开启", result.reason)
        assertEquals("REQUESTABLE", result.permission)
        assertTrue(notifier.events.isEmpty())
    }

    @Test
    fun `总开关关闭与全部类型关闭时禁用`() = runTest {
        val off = engine(settings = ReminderSettings.DEFAULT.copy(enabled = false)).runNow()
        assertEquals("提醒总开关已关闭", off.disabledReason)

        val sectionsOff = engine(
            settings = ReminderSettings.DEFAULT.copy(
                homeworkEnabled = false,
                courseEnabled = false,
                noticeEnabled = false,
            ),
        ).runNow()
        assertEquals("作业/课表/通知提醒均已关闭", sectionsOff.disabledReason)
    }

    @Test
    fun `诊断读取缓存条数 权限 设置 上次运行`() = runTest {
        val runState = InMemoryReminderRunStateStore(initialLastRunAt = now - 30 * 60_000L, initialSummary = "sent=1")
        val engine = engine(
            runStateStore = runState,
            data = FakeDataProvider(rows = listOf(homework("a"), homework("b")), ttPlan = coursePlan(now + 60_000L)),
        )
        val diagnostics = engine.diagnostics()
        assertEquals(2, diagnostics.homeworkCacheCount)
        assertTrue(diagnostics.timetableReady)
        assertEquals(PermissionState.GRANTED, diagnostics.permission)
        assertEquals(now - 30 * 60_000L, diagnostics.lastRunAtMillis)
        assertEquals("sent=1", diagnostics.lastRunSummary)
        assertEquals(ReminderCatchUp.DEFAULT_GRACE_MS * 3, diagnostics.catchUpLookbackMillis)
        assertTrue(diagnostics.settingsSummary.contains("通知=开"))
    }

    // ------------------------------------------------------------ 测试通知

    @Test
    fun `发送测试通知走假 notifier 不真发`() = runTest {
        val notifier = FakeNotifier()
        val result = engine(notifier = notifier).testNotification()
        assertEquals(ReminderTestNotificationResult.Sent, result)
        val event = notifier.events.single()
        assertEquals("测试通知", event.title)
        assertTrue(event.key.startsWith("test:"))
        assertEquals("hw", event.toolId)
        assertEquals(1, notifier.channelsEnsured)
    }

    @Test
    fun `测试通知权限缺失返回明确结果`() = runTest {
        val notifier = FakeNotifier()
        val result = engine(permission = PermissionState.PERMANENTLY_DENIED, notifier = notifier).testNotification()
        assertEquals(ReminderTestNotificationResult.PermissionMissing, result)
        assertTrue(notifier.events.isEmpty())
    }

    @Test
    fun `测试通知发送失败返回可读错误`() = runTest {
        val notifier = FakeNotifier(succeed = false)
        val result = engine(notifier = notifier).testNotification(ReminderKind.NOTICE_DUE)
        assertTrue(result is ReminderTestNotificationResult.Failed)
        assertTrue((result as ReminderTestNotificationResult.Failed).message.contains("失败"))
        assertEquals("nt", notifier.events.single().toolId)
    }

    // ------------------------------------------------------------ 错过补发

    @Test
    fun `上次遗留的已过点事件会被补发一次`() = runTest {
        val missed = ReminderEvent(
            kind = ReminderKind.HOMEWORK_DUE,
            key = "hw-due:old",
            triggerAtMillis = now - 2 * 60 * 60_000L,
            title = "作业即将截止",
            body = "高数 · 11-10 23:59 截止",
            toolId = "hw",
        )
        val notifier = FakeNotifier()
        val engine = engine(
            notifier = notifier,
            runStateStore = InMemoryReminderRunStateStore(initialLastRunAt = now - 3 * 60 * 60_000L),
            pendingStore = InMemoryReminderPendingEventStore(listOf(missed)),
        )
        val result = engine.runNow()
        assertEquals(1, result.sent)
        assertEquals(1, result.catchUpEvents)
        assertTrue(result.reason.contains("补发"))
        assertEquals("hw-due:old", notifier.events.single().key)
    }

    @Test
    fun `规划出的未来事件写入 pending 并在错过运行后补发`() = runTest {
        var clockMillis = at(11, 9, 15, 0)
        val pendingStore = InMemoryReminderPendingEventStore()
        val runState = InMemoryReminderRunStateStore()
        val notifier = FakeNotifier()
        val data = FakeDataProvider(rows = listOf(homework()))
        val engine = engine(
            notifier = notifier,
            runStateStore = runState,
            pendingStore = pendingStore,
            data = data,
            clock = { clockMillis },
        )
        // 15:00 运行：20:00 作业汇总事件写入 pending，但未到点不发
        val first = engine.runNow()
        assertEquals(0, first.sent)
        assertTrue(pendingStore.load().any { it.key.startsWith("hw-digest:") })

        // 20:05 运行（Worker 被 Doze 延迟）：补发 20:00 汇总
        clockMillis = at(11, 9, 20, 5)
        val second = engine.runNow()
        assertEquals(1, second.sent)
        assertEquals(1, second.catchUpEvents)
        assertTrue(notifier.events.single().key.startsWith("hw-digest:"))
    }

    @Test
    fun `补发受回看上限保护 过旧事件不补发`() = runTest {
        val old = ReminderEvent(ReminderKind.HOMEWORK_DUE, "too-old", now - 7 * 60 * 60_000L, "t", "b", "hw")
        val notifier = FakeNotifier()
        val engine = engine(
            notifier = notifier,
            runStateStore = InMemoryReminderRunStateStore(initialLastRunAt = now - 8 * 60 * 60_000L),
            pendingStore = InMemoryReminderPendingEventStore(listOf(old)),
        )
        val result = engine.runNow()
        assertEquals(0, result.sent)
        assertEquals(0, result.catchUpEvents)
    }

    @Test
    fun `运行后记录上次运行摘要供诊断`() = runTest {
        val runState = InMemoryReminderRunStateStore()
        val engine = engine(runStateStore = runState)
        engine.runNow()
        assertEquals(now, runState.lastRunAt())
        assertTrue(runState.lastRunSummary().orEmpty().contains("sent="))
        assertFalse(runState.lastRunSummary().orEmpty().contains("通知即将"))
    }
}
