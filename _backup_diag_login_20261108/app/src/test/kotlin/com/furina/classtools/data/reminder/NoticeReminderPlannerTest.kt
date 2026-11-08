package com.furina.classtools.data.reminder

import com.furina.classtools.data.notices.ManualNotice
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeExtension
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeRecurrenceDto
import com.furina.classtools.data.notices.NoticeRow
import com.furina.classtools.data.notices.NoticesData
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 通知提醒（ntremind）规划器测试：event/deadline、汇总窗口、临近提醒、recurrence/已处理/过期排除、开关、去重。 */
class NoticeReminderPlannerTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Long = LocalDateTime.of(2026, 11, 9, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun group(
        id: String = "g1",
        title: String = "关于主题班会的通知",
        deadline: String = "",
        eventAt: String = "",
        deadlineAction: String = "截止",
        eventAction: String = "开始",
        recurrence: NoticeRecurrenceDto? = null,
        extensions: List<NoticeExtension> = emptyList(),
        source: String = "班级通知群",
    ): NoticeGroupDto = NoticeGroupDto(
        id = id,
        title = title,
        source = source,
        senderName = "陈子麒",
        deadline = deadline,
        deadlineAction = deadlineAction,
        eventAt = eventAt,
        eventAction = eventAction,
        extensions = extensions,
        recurrence = recurrence,
    )

    private fun entry(group: NoticeGroupDto, hidden: Boolean = false): NoticeEntry = NoticeEntry(
        row = NoticeRow(id = "auto_${group.id}", title = group.title, ts = 1L),
        group = group,
        items = emptyList(),
        hidden = hidden,
        edited = false,
    )

    private fun notices(vararg entries: NoticeEntry, manual: List<ManualNotice> = emptyList()): NoticesData =
        NoticesData(
            auto = entries.toList(),
            manual = manual,
            hiddenCount = 0,
            fetchedAt = now,
            fromCache = false,
        )

    private fun defaultSettings() = ReminderSettings.DEFAULT

    // ------------------------------------------------------------ event/deadline 选择

    @Test
    fun `活动时间优先于截止时间`() {
        val data = notices(
            entry(
                group(
                    eventAt = "2026-11-10T16:00:00+08:00",
                    deadline = "2026-11-12T18:00:00+08:00",
                ),
            ),
        )
        val targets = ReminderPlanner.noticeTargets(data, nowMillis = now, zone = zone)
        assertEquals(1, targets.size)
        val target = targets.single()
        assertEquals(ReminderPlanner.NoticeTargetType.EVENT, target.type)
        assertEquals(at(11, 10, 16, 0), target.targetAtMillis)
        assertEquals("notice:g1:event", target.dedupeKey)
    }

    @Test
    fun `无活动时间时使用真实截止时间`() {
        val data = notices(entry(group(deadline = "2026-11-10T18:00:00+08:00", deadlineAction = "提交截止")))
        val target = ReminderPlanner.noticeTargets(data, nowMillis = now, zone = zone).single()
        assertEquals(ReminderPlanner.NoticeTargetType.DEADLINE, target.type)
        assertEquals(at(11, 10, 18, 0), target.targetAtMillis)
        assertTrue(target.action.isNotBlank())
    }

    @Test
    fun `延期 extensions 的最后一条作为截止时间`() {
        val data = notices(
            entry(
                group(
                    deadline = "2026-11-10T18:00:00+08:00",
                    extensions = listOf(
                        NoticeExtension(deadline = "2026-11-11T18:00:00+08:00"),
                        NoticeExtension(deadline = "2026-11-12T18:00:00+08:00"),
                    ),
                ),
            ),
        )
        val target = ReminderPlanner.noticeTargets(data, nowMillis = now, zone = zone).single()
        assertEquals(at(11, 12, 18, 0), target.targetAtMillis)
    }

    // ------------------------------------------------------------ 不可提醒（排除）

    @Test
    fun `长期安排 recurrence 不提醒`() {
        val data = notices(
            entry(
                group(
                    deadline = "2026-11-10T18:00:00+08:00",
                    recurrence = NoticeRecurrenceDto(freq = "weekly", weekday = 5),
                ),
            ),
        )
        assertTrue(ReminderPlanner.noticeTargets(data, nowMillis = now, zone = zone).isEmpty())
        assertTrue(ReminderPlanner.planNotices(data, nowMillis = now, zone = zone).isEmpty())
    }

    @Test
    fun `已处理通知不提醒`() {
        val data = notices(entry(group(deadline = "2026-11-10T18:00:00+08:00")))
        assertTrue(ReminderPlanner.noticeTargets(data, handledIds = setOf("g1"), nowMillis = now, zone = zone).isEmpty())
        assertTrue(ReminderPlanner.planNotices(data, handledIds = setOf("g1"), nowMillis = now, zone = zone).isEmpty())
    }

    @Test
    fun `已过期或无时间不提醒`() {
        val expired = notices(entry(group(deadline = "2026-11-08T18:00:00+08:00")))
        assertTrue(ReminderPlanner.noticeTargets(expired, nowMillis = now, zone = zone).isEmpty())
        val noTime = notices(entry(group()))
        assertTrue(ReminderPlanner.noticeTargets(noTime, nowMillis = now, zone = zone).isEmpty())
        val tooFar = notices(entry(group(deadline = "2026-12-20T18:00:00+08:00")))
        assertTrue(ReminderPlanner.noticeTargets(tooFar, nowMillis = now, zone = zone).isEmpty())
    }

    @Test
    fun `隐藏通知与人工通知不提醒`() {
        val hidden = notices(entry(group(deadline = "2026-11-10T18:00:00+08:00"), hidden = true))
        assertTrue(ReminderPlanner.noticeTargets(hidden, nowMillis = now, zone = zone).isEmpty())
        val manual = notices(
            manual = listOf(
                ManualNotice(
                    row = NoticeRow(id = "m1", title = "人工通知", ts = 1L),
                    attachments = emptyList(),
                ),
            ),
        )
        assertTrue(ReminderPlanner.noticeTargets(manual, nowMillis = now, zone = zone).isEmpty())
    }

    // ------------------------------------------------------------ 汇总 / 临近

    @Test
    fun `每日汇总包含明天与后天截止的通知`() {
        val data = notices(
            entry(group(id = "g1", title = "班会", deadline = "2026-11-10T18:00:00+08:00")),
            entry(group(id = "g2", title = "团课", deadline = "2026-11-11T20:00:00+08:00")),
        )
        val events = ReminderPlanner.planNotices(data, nowMillis = now, zone = zone)
        val digest = events.firstOrNull { it.kind == ReminderKind.NOTICE_DIGEST }
        assertNotNull(digest)
        assertEquals(at(11, 9, 20), digest!!.triggerAtMillis)
        assertTrue(digest.body.contains("2 条通知"))
        assertTrue(digest.body.contains("班会"))
        assertTrue(digest.body.contains("团课"))
        assertEquals("nt", digest.toolId)
        assertEquals("nt-digest:2026-11-09", digest.key)
    }

    @Test
    fun `已进入 24 小时窗口且早于汇总时间的通知立即提醒`() {
        // 今天 18:00 截止：早于今晚 20:00 汇总窗口 → 24h 临近提醒（立即）
        val data = notices(entry(group(id = "g1", title = "材料提交", deadline = "2026-11-09T18:00:00+08:00")))
        val events = ReminderPlanner.planNotices(data, nowMillis = now, zone = zone)
        val due = events.firstOrNull { it.kind == ReminderKind.NOTICE_DUE }
        assertNotNull(due)
        assertEquals(now + ReminderPlanner.IMMEDIATE_DELAY_MS, due!!.triggerAtMillis)
        assertEquals("notice:g1:deadline", due.key)
        assertTrue(due.title.contains("材料提交"))
        assertEquals("nt", due.toolId)
    }

    @Test
    fun `明天到期的通知只进汇总不重复临近提醒`() {
        val data = notices(entry(group(id = "g1", title = "班会", deadline = "2026-11-10T18:00:00+08:00")))
        val events = ReminderPlanner.planNotices(data, nowMillis = now, zone = zone)
        assertEquals(listOf(ReminderKind.NOTICE_DIGEST), events.map { it.kind })
    }

    @Test
    fun `汇总正文最多列五条并折叠`() {
        val entries = (1..7).map { index ->
            entry(group(id = "g$index", title = "通知$index", deadline = "2026-11-10T18:00:00+08:00"))
        }.toTypedArray()
        val digest = ReminderPlanner.planNotices(notices(*entries), nowMillis = now, zone = zone)
            .first { it.kind == ReminderKind.NOTICE_DIGEST }
        assertTrue(digest.body.contains("等 7 条"))
        assertTrue(digest.body.contains("通知1"))
    }

    @Test
    fun `安静时段内汇总与临近提醒都被过滤`() {
        val settings = defaultSettings().copy(
            quietHoursEnabled = true,
            quietStartHour = 20,
            quietEndHour = 7,
        )
        // 明天 18:00 截止 → 只在今晚 20:00 汇总（安静时段）→ 无事件
        val data = notices(entry(group(deadline = "2026-11-10T18:00:00+08:00")))
        assertTrue(ReminderPlanner.planNotices(data, nowMillis = now, settings = settings, zone = zone).isEmpty())
    }

    // ------------------------------------------------------------ 开关 / 空数据

    @Test
    fun `通知提醒关闭或总开关关闭时无事件`() {
        val data = notices(entry(group(deadline = "2026-11-10T18:00:00+08:00")))
        assertTrue(
            ReminderPlanner.planNotices(
                data,
                nowMillis = now,
                settings = defaultSettings().copy(noticeEnabled = false),
                zone = zone,
            ).isEmpty(),
        )
        assertTrue(
            ReminderPlanner.planNotices(
                data,
                nowMillis = now,
                settings = defaultSettings().copy(enabled = false),
                zone = zone,
            ).isEmpty(),
        )
    }

    @Test
    fun `没有通知快照或无 auto 组时无事件`() {
        assertTrue(ReminderPlanner.planNotices(null, nowMillis = now, zone = zone).isEmpty())
        val empty = NoticesData(emptyList(), emptyList(), 0, now, false)
        assertTrue(ReminderPlanner.planNotices(empty, nowMillis = now, zone = zone).isEmpty())
    }

    // ------------------------------------------------------------ 合并规划 / 配置

    @Test
    fun `合并规划包含通知且按触发时间排序去重`() {
        val notices = notices(
            entry(group(id = "g1", title = "材料", deadline = "2026-11-09T18:00:00+08:00")),
        )
        val merged = ReminderPlanner.plan(
            rows = emptyList(),
            plan = null,
            nowMillis = now,
            settings = defaultSettings(),
            zone = zone,
            notices = notices,
            handledNoticeIds = setOf("other"),
        ).events
        assertTrue(merged.any { it.kind == ReminderKind.NOTICE_DUE })
        assertEquals(merged.map { it.triggerAtMillis }.sorted(), merged.map { it.triggerAtMillis })
        assertEquals(merged.map { it.key }.distinct().size, merged.size)
    }

    @Test
    fun `通知开关默认开启且渠道与去重键口径正确`() {
        assertTrue(ReminderSettings.DEFAULT.noticeEnabled)
        assertEquals("nt", ReminderIntentFactory.toolIdFor(ReminderKind.NOTICE_DIGEST))
        assertEquals("nt", ReminderIntentFactory.toolIdFor(ReminderKind.NOTICE_DUE))
        assertEquals("nt", ReminderIntentFactory.toolIdFromExtras("nt"))
        assertEquals("notice_due", ReminderKind.NOTICE_DUE.channelId)
        assertEquals(ReminderKind.NOTICE_DIGEST.channelId, ReminderKind.NOTICE_DUE.channelId)
        assertTrue(ReminderKind.NOTICE_DUE.highPriority)
    }

    @Test
    fun `已处理集合只影响命中的通知`() {
        val data = notices(
            entry(group(id = "g1", title = "已读", deadline = "2026-11-10T18:00:00+08:00")),
            entry(group(id = "g2", title = "未读", deadline = "2026-11-10T18:00:00+08:00")),
        )
        val digest = ReminderPlanner.planNotices(data, handledIds = setOf("g1"), nowMillis = now, zone = zone)
            .first { it.kind == ReminderKind.NOTICE_DIGEST }
        assertFalse(digest.body.contains("已读"))
        assertTrue(digest.body.contains("未读"))
        assertEquals(1, ReminderPlanner.noticeTargets(data, handledIds = setOf("g1"), nowMillis = now, zone = zone).size)
    }

    @Test
    fun `空标题通知回退为班级通知`() {
        val data = notices(entry(group(id = "g1", title = "", deadline = "2026-11-09T18:00:00+08:00")))
        val target = ReminderPlanner.noticeTargets(data, nowMillis = now, zone = zone).single()
        assertEquals("班级通知", target.displayName)
        val due = ReminderPlanner.planNotices(data, nowMillis = now, zone = zone)
            .first { it.kind == ReminderKind.NOTICE_DUE }
        assertTrue(due.title.contains("班级通知"))
    }

    @Test
    fun `去重键区分 event 与 deadline`() {
        val data = notices(
            entry(
                group(
                    id = "g1",
                    eventAt = "2026-11-10T16:00:00+08:00",
                    deadline = "2026-11-12T18:00:00+08:00",
                ),
            ),
            entry(group(id = "g2", deadline = "2026-11-09T18:00:00+08:00")),
        )
        val keys = ReminderPlanner.planNotices(data, nowMillis = now, zone = zone)
            .filter { it.kind == ReminderKind.NOTICE_DUE }
            .map { it.key }
        assertTrue(keys.contains("notice:g2:deadline"))
        assertTrue(keys.none { it.startsWith("notice:g1:") })
    }

    @Test
    fun `通知只有活动时间且无截止也可提醒`() {
        val data = notices(entry(group(id = "g1", title = "主题班会", eventAt = "2026-11-10T16:00:00+08:00")))
        val events = ReminderPlanner.planNotices(data, nowMillis = now, zone = zone)
        assertTrue(events.any { it.kind == ReminderKind.NOTICE_DIGEST })
        assertNull(events.firstOrNull { it.kind == ReminderKind.NOTICE_DUE })
    }
}
