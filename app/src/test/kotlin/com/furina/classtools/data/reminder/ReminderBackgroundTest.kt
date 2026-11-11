package com.furina.classtools.data.reminder

import android.content.Intent
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeRow
import com.furina.classtools.data.notices.NoticeSigRow
import com.furina.classtools.data.permission.PermissionController
import com.furina.classtools.data.permission.PermissionSnapshot
import com.furina.classtools.data.permission.PermissionState
import com.furina.classtools.data.permission.PermissionType
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v3.7 后台通知轮询测试：首次建基线、签名比较、新增去重/通知、安静时段、失败静默、
 * 本地状态编解码、诊断文案（全部纯逻辑，不联网/不真发通知）。
 */
class ReminderBackgroundTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Long = LocalDateTime.of(2026, 11, 11, 21, 0).atZone(zone).toInstant().toEpochMilli()

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

    private class FakeGateway(
        var signatureRows: List<NoticeSigRow>? = emptyList(),
        var entries: List<NoticeEntry>? = emptyList(),
    ) : BackgroundNoticeGateway {
        var signatureCalls = 0
        var entryCalls = 0

        override suspend fun fetchSignatureRows(): List<NoticeSigRow>? {
            signatureCalls += 1
            return signatureRows
        }

        override suspend fun fetchEntries(): List<NoticeEntry>? {
            entryCalls += 1
            return entries
        }
    }

    private fun sig(id: String, ts: Long = 1L, hidden: Boolean = false): NoticeSigRow =
        NoticeSigRow(id = id, ts = ts, hidden = hidden, editedAt = 0L)

    private fun entry(
        id: String,
        title: String = "主题班会",
        aiSummary: String = "周一下午班会",
        source: String = "班级群",
        hidden: Boolean = false,
        ts: Long = 1L,
        cat: String = "活动",
    ): NoticeEntry = NoticeEntry(
        row = NoticeRow(id = "auto_$id", title = title, ts = ts),
        group = NoticeGroupDto(
            id = id,
            title = title,
            cat = cat,
            source = source,
            aiSummary = aiSummary,
            summary = aiSummary,
        ),
        items = emptyList(),
        hidden = hidden,
        edited = false,
    )

    private fun runner(
        gateway: FakeGateway,
        stateStore: BackgroundPollStateStore = InMemoryBackgroundPollStateStore(),
        notifier: FakeNotifier = FakeNotifier(),
        permission: PermissionState = PermissionState.GRANTED,
        settings: ReminderSettings = ReminderSettings.DEFAULT,
        clock: () -> Long = { now },
    ): ReminderBackgroundRunner = ReminderBackgroundRunner(
        gateway = gateway,
        stateStore = stateStore,
        settingsStore = FakeSettingsStore(settings),
        notifier = notifier,
        permissionController = FakePermissionController(permission),
        clock = clock,
        zone = zone,
    )

    // ------------------------------------------------------------ 纯逻辑

    @Test
    fun `周期下限为15分钟`() {
        assertEquals(15L, ReminderBackgroundLogic.intervalMinutes(5L))
        assertEquals(15L, ReminderBackgroundLogic.intervalMinutes(-1L))
        assertEquals(60L, ReminderBackgroundLogic.intervalMinutes(60L))
        assertEquals(8L * 60, ReminderBackgroundLogic.intervalMinutes(8L * 60))
        assertEquals(24L * 60, ReminderBackgroundLogic.intervalMinutes(10_000L))
    }

    @Test
    fun `轮询开关跟随总开关与通知开关`() {
        assertTrue(ReminderBackgroundLogic.pollEnabled(ReminderSettings.DEFAULT))
        assertFalse(
            ReminderBackgroundLogic.pollEnabled(ReminderSettings.DEFAULT.copy(enabled = false)),
        )
        assertFalse(
            ReminderBackgroundLogic.pollEnabled(ReminderSettings.DEFAULT.copy(noticeEnabled = false)),
        )
    }

    @Test
    fun `新增行按ts升序且过滤空id`() {
        val rows = listOf(sig("b", 2L), sig("a", 1L), sig("", 3L))
        val added = ReminderBackgroundLogic.newRows(knownIds = setOf("a"), rows = rows)
        assertEquals(listOf("b"), added.map { it.id })
    }

    @Test
    fun `已知id集合保留当前全部id并按上限截断`() {
        val current = (1..10).map { sig("cur$it", it.toLong()) }
        val old = (1..500).map { "old$it" }.toSet()
        val merged = ReminderBackgroundLogic.pruneKnownIds(old, current, max = 400)
        assertEquals(400, merged.size)
        assertTrue(merged.containsAll(current.map { it.id }))
    }

    @Test
    fun `签名口径与通知仓库一致`() {
        val rows = listOf(sig("a", 5L), sig("b", 6L, hidden = true))
        assertEquals("a:5:false:0|b:6:true:0", ReminderBackgroundLogic.signatureOf(rows))
    }

    @Test
    fun `新通知事件映射标题正文渠道与深链`() {
        val entries = listOf(
            entry("g1", title = "运动会报名", aiSummary = "周五前报名", source = "体育委员"),
            entry("g2", title = "班会", aiSummary = "", source = ""),
            entry("g3", title = "已隐藏", hidden = true),
        )
        val events = ReminderBackgroundLogic.newNoticeEvents(entries, setOf("g1", "g2", "g3"), now)
        assertEquals(2, events.size)
        val first = events.first { it.key == "nt-new:g1" }
        assertEquals(ReminderKind.NOTICE_DUE, first.kind)
        assertEquals("nt", first.toolId)
        assertEquals(ReminderChannels.NEW_NOTICE_ID, first.channelIdOverride)
        assertEquals("新通知：运动会报名", first.title)
        assertTrue(first.body.contains("活动"))
        assertTrue(first.body.contains("周五前报名"))
        assertEquals(now, first.triggerAtMillis)
    }

    @Test
    fun `新通知超过上限合并汇总`() {
        val entries = (1..5).map { entry("g$it", title = "通知$it", ts = it.toLong()) }
        val events = ReminderBackgroundLogic.newNoticeEvents(
            entries,
            entries.map { it.id }.toSet(),
            now,
        )
        assertEquals(ReminderBackgroundLogic.MAX_NOTIFY_PER_POLL + 1, events.size)
        val summary = events.last()
        assertTrue(summary.key.startsWith("nt-new-summary:"))
        assertTrue(summary.body.contains("共 5 条新通知"))
        assertEquals(ReminderChannels.NEW_NOTICE_ID, summary.channelIdOverride)
    }

    @Test
    fun `新通知正文无摘要时用兜底文案`() {
        val empty = entry("g1", title = "", aiSummary = "", source = "", cat = "")
        assertEquals("点击查看通知详情", ReminderBackgroundLogic.newNoticeBody(empty))
    }

    // ------------------------------------------------------------ 状态编解码

    @Test
    fun `状态编解码往返一致`() {
        val state = BackgroundPollState(
            baselineDone = true,
            knownIds = setOf("a", "b"),
            signature = "a:1:false:0|b:2:false:0",
            lastMaxTs = 2L,
            lastPollAtMillis = now,
            lastSuccessAtMillis = now - 1000,
            lastNewCount = 2,
            lastResult = "发现 2 条新通知",
            pollCount = 7L,
        )
        assertEquals(state, BackgroundPollCodec.decode(BackgroundPollCodec.encode(state)))
    }

    @Test
    fun `状态解码容错`() {
        assertNull(BackgroundPollCodec.decode(null))
        assertNull(BackgroundPollCodec.decode(""))
        assertNull(BackgroundPollCodec.decode("not-json"))
        val partial = BackgroundPollCodec.decode("{\"baseline\":true}")
        assertNotNull(partial)
        assertTrue(partial!!.baselineDone)
        assertEquals(emptySet<String>(), partial.knownIds)
    }

    // ------------------------------------------------------------ 运行器

    @Test
    fun `首次运行只建基线不通知`() = runTest {
        val notifier = FakeNotifier()
        val store = InMemoryBackgroundPollStateStore()
        val gateway = FakeGateway(
            signatureRows = listOf(sig("old1", 1L), sig("old2", 2L)),
            entries = listOf(entry("old1"), entry("old2")),
        )
        val outcome = runner(gateway, store, notifier).run()

        assertTrue(outcome.baselineEstablished)
        assertEquals(0, outcome.notified)
        assertTrue(notifier.events.isEmpty())
        assertEquals(1, gateway.signatureCalls)
        assertEquals(0, gateway.entryCalls) // 基线不需要整表
        val state = store.load()!!
        assertTrue(state.baselineDone)
        assertEquals(setOf("old1", "old2"), state.knownIds)
        assertEquals(1L, state.pollCount)
    }

    @Test
    fun `签名未变化不拉整表也不通知`() = runTest {
        val store = InMemoryBackgroundPollStateStore(
            BackgroundPollState(
                baselineDone = true,
                knownIds = setOf("a"),
                signature = "a:1:false:0",
                lastPollAtMillis = now - 900_000,
                pollCount = 1L,
            ),
        )
        val notifier = FakeNotifier()
        val gateway = FakeGateway(signatureRows = listOf(sig("a", 1L)))
        val outcome = runner(gateway, store, notifier).run()

        assertFalse(outcome.signatureChanged)
        assertFalse(outcome.failed)
        assertEquals(0, gateway.entryCalls)
        assertTrue(notifier.events.isEmpty())
        assertTrue(outcome.reason.contains("无变化"))
        assertEquals(2L, store.load()!!.pollCount)
        assertEquals(now, store.load()!!.lastPollAtMillis)
    }

    @Test
    fun `发现新通知时发高优先级通知并更新去重状态`() = runTest {
        val store = InMemoryBackgroundPollStateStore(
            BackgroundPollState(
                baselineDone = true,
                knownIds = setOf("a"),
                signature = "a:1:false:0",
                pollCount = 1L,
            ),
        )
        val notifier = FakeNotifier()
        val gateway = FakeGateway(
            signatureRows = listOf(sig("a", 1L), sig("b", 2L)),
            entries = listOf(entry("a"), entry("b", title = "新活动", aiSummary = "请报名")),
        )
        val outcome = runner(gateway, store, notifier).run()

        assertTrue(outcome.signatureChanged)
        assertEquals(listOf("b"), outcome.newIds)
        assertEquals(1, outcome.notified)
        assertEquals(1, notifier.events.size)
        val event = notifier.events.single()
        assertEquals("nt-new:b", event.key)
        assertEquals(ReminderChannels.NEW_NOTICE_ID, event.channelIdOverride)
        assertEquals("nt", event.toolId)
        assertTrue(notifier.channelsEnsured >= 1)

        val state = store.load()!!
        assertTrue(state.knownIds.containsAll(setOf("a", "b")))
        assertEquals(1, state.lastNewCount)
        assertTrue(state.lastResult.contains("已通知"))
        assertTrue(state.signature.startsWith("a:1:false:0"))
    }

    @Test
    fun `隐藏的新通知不发但仍记录已读`() = runTest {
        val store = InMemoryBackgroundPollStateStore(
            BackgroundPollState(
                baselineDone = true,
                knownIds = setOf("a"),
                signature = "a:1:false:0",
                pollCount = 1L,
            ),
        )
        val notifier = FakeNotifier()
        val gateway = FakeGateway(
            signatureRows = listOf(sig("a", 1L), sig("h", 2L, hidden = true)),
            entries = listOf(entry("a"), entry("h", hidden = true)),
        )
        val outcome = runner(gateway, store, notifier).run()

        assertTrue(outcome.signatureChanged)
        assertEquals(0, outcome.notified)
        assertTrue(notifier.events.isEmpty())
        assertTrue(store.load()!!.knownIds.contains("h"))
    }

    @Test
    fun `无通知权限时不联网并记录原因`() = runTest {
        val gateway = FakeGateway(signatureRows = listOf(sig("a")))
        val store = InMemoryBackgroundPollStateStore()
        val outcome = runner(gateway, store, permission = PermissionState.PERMANENTLY_DENIED).run()

        assertFalse(outcome.failed)
        assertEquals(0, gateway.signatureCalls)
        assertTrue(outcome.reason.contains("通知权限"))
        assertTrue(store.load()!!.lastPollAtMillis > 0L)
    }

    @Test
    fun `提醒总开关关闭时不联网`() = runTest {
        val gateway = FakeGateway(signatureRows = listOf(sig("a")))
        val outcome = runner(
            gateway,
            settings = ReminderSettings.DEFAULT.copy(enabled = false),
        ).run()
        assertEquals(0, gateway.signatureCalls)
        assertTrue(outcome.reason.contains("已关闭"))
    }

    @Test
    fun `签名接口失败时静默且保留旧基线`() = runTest {
        val previous = BackgroundPollState(
            baselineDone = true,
            knownIds = setOf("a"),
            signature = "a:1:false:0",
            pollCount = 3L,
        )
        val store = InMemoryBackgroundPollStateStore(previous)
        val gateway = FakeGateway(signatureRows = null)
        val outcome = runner(gateway, store).run()

        assertTrue(outcome.failed)
        val state = store.load()!!
        assertEquals(previous.knownIds, state.knownIds)
        assertEquals(previous.signature, state.signature)
        assertEquals(4L, state.pollCount)
        assertTrue(state.lastResult.contains("失败"))
    }

    @Test
    fun `整表拉取失败时保留已知id等待重试`() = runTest {
        val previous = BackgroundPollState(
            baselineDone = true,
            knownIds = setOf("a"),
            signature = "a:1:false:0",
            pollCount = 1L,
        )
        val store = InMemoryBackgroundPollStateStore(previous)
        val gateway = FakeGateway(
            signatureRows = listOf(sig("a", 1L), sig("b", 2L)),
            entries = null,
        )
        val outcome = runner(gateway, store).run()

        assertTrue(outcome.failed)
        assertEquals(setOf("a"), store.load()!!.knownIds)
        assertFalse(store.load()!!.knownIds.contains("b"))
    }

    @Test
    fun `安静时段静默但更新基线`() = runTest {
        val quietNow = LocalDateTime.of(2026, 11, 11, 23, 30)
            .atZone(zone).toInstant().toEpochMilli()
        val settings = ReminderSettings.DEFAULT.copy(
            quietHoursEnabled = true,
            quietStartHour = 23,
            quietEndHour = 7,
        )
        val store = InMemoryBackgroundPollStateStore(
            BackgroundPollState(
                baselineDone = true,
                knownIds = setOf("a"),
                signature = "a:1:false:0",
                pollCount = 1L,
            ),
        )
        val notifier = FakeNotifier()
        val gateway = FakeGateway(
            signatureRows = listOf(sig("a", 1L), sig("b", 2L)),
            entries = listOf(entry("b")),
        )
        val outcome = runner(
            gateway,
            store,
            notifier,
            settings = settings,
            clock = { quietNow },
        ).run()

        assertEquals(0, outcome.notified)
        assertTrue(notifier.events.isEmpty())
        assertTrue(outcome.reason.contains("安静时段"))
        val state = store.load()!!
        assertTrue(state.knownIds.contains("b"))
        assertEquals(1, state.lastNewCount)
    }

    @Test
    fun `通知失败时计入未通知但不抛异常`() = runTest {
        val store = InMemoryBackgroundPollStateStore(
            BackgroundPollState(
                baselineDone = true,
                knownIds = emptySet(),
                signature = "",
                pollCount = 1L,
            ),
        )
        val notifier = FakeNotifier(succeed = false)
        val gateway = FakeGateway(
            signatureRows = listOf(sig("a", 1L)),
            entries = listOf(entry("a")),
        )
        val outcome = runner(gateway, store, notifier).run()

        assertFalse(outcome.failed)
        assertEquals(0, outcome.notified)
        assertEquals(1, notifier.events.size)
        assertTrue(store.load()!!.knownIds.contains("a"))
    }

    // ------------------------------------------------------------ 诊断

    @Test
    fun `后台诊断文案包含关键项`() {
        val diagnostics = BackgroundReminderDiagnostics(
            lastPollAtMillis = now,
            lastSuccessAtMillis = now,
            lastNewCount = 2,
            lastResult = "发现 2 条新通知，已通知 2 条",
            pollCount = 5L,
            baselineDone = true,
            permission = PermissionState.GRANTED,
            notificationsEnabled = true,
            newNoticeChannelImportance = ReminderChannels.IMPORTANCE_HIGH,
            batteryOptimizationIgnored = false,
            exactAlarmAllowed = true,
        )
        assertTrue(diagnostics.ready)
        val lines = diagnostics.formatLines(zone)
        assertTrue(lines.any { it.contains("已启用") })
        assertTrue(lines.any { it.contains("上次轮询") && it.contains("11-11") })
        assertTrue(lines.any { it.contains("新增 2 条") })
        assertTrue(lines.any { it.contains("通知权限：已开启") })
        assertTrue(lines.any { it.contains("新通知渠道：高") })
        assertTrue(lines.any { it.contains("电池优化：可能受限") })
        assertTrue(lines.any { it.contains("精确闹钟：已授权") })
    }

    @Test
    fun `后台诊断未运行时给出明确文案`() {
        val diagnostics = BackgroundReminderDiagnostics(
            permission = PermissionState.REQUESTABLE,
            notificationsEnabled = false,
        )
        assertFalse(diagnostics.ready)
        val lines = diagnostics.formatLines(zone)
        assertTrue(lines.any { it.contains("尚未建立基线") })
        assertTrue(lines.any { it.contains("尚未运行") })
        assertTrue(lines.any { it.contains("系统通知总开关已关闭") })
    }

    @Test
    fun `诊断数据类无隐私正文`() {
        val diagnostics = BackgroundReminderDiagnostics(lastResult = "无变化（3 条通知）")
        assertFalse(diagnostics.toString().contains("学号"))
        assertFalse(diagnostics.toString().contains("高数"))
    }

    // ------------------------------------------------------------ 状态存储（内存实现）

    @Test
    fun `内存状态存储读写`() = runTest {
        val store = InMemoryBackgroundPollStateStore()
        assertNull(store.load())
        store.save(BackgroundPollState(baselineDone = true, knownIds = setOf("x")))
        assertEquals(setOf("x"), store.load()!!.knownIds)
    }

    @Test
    fun `结果日志摘要不含通知正文`() {
        val outcome = BackgroundPollOutcome(
            newIds = listOf("a", "b"),
            notified = 2,
            signatureChanged = true,
            state = BackgroundPollState(),
            reason = "发现 2 条新通知，已通知 2 条",
        )
        val summary = outcome.logSummary()
        assertTrue(summary.contains("new=2"))
        assertTrue(summary.contains("notified=2"))
        assertFalse(summary.contains("高数"))
    }
}
