package com.furina.classtools.ui.screens.notices

import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeItemMeta
import com.furina.classtools.data.notices.NoticeRow
import com.furina.classtools.data.notices.NoticesData
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「最新发布」发布时间 / 排序单测（2026-11-16 用户反馈「排序结果不对」的回归）。
 *
 * 根因：旧实现把首条 item 的消息时间也算进发布时间，而 item.time 常见只有 `HH:mm`（扫描当天的时钟），
 * 按渲染日解析会把历史通知算成「今天」，导致旧通知被顶到「最新发布」最前。
 *
 * 修复口径：发布时间 = max(行 `ts`, 组 `updatedAt`)；缺失时用 `ts`；不再使用 item 消息时间 / edited_at。
 */
class NoticesPublishSortTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Long = at(2026, 11, 16, 20, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun entry(
        id: String,
        title: String,
        ts: Long,
        updatedAt: String = "",
        itemTimeText: String = "",
        cat: String = "教务",
    ): NoticeEntry = NoticeEntry(
        row = NoticeRow(id = "auto_$id", title = title, ts = ts),
        group = NoticeGroupDto(
            id = id,
            title = title,
            cat = cat,
            updatedAt = updatedAt,
            // 普通通知：deadline 提供时间锚点，避免被判为「资料 / 无时间」
            deadline = "2026-11-20T18:00:00+08:00",
            deadlineAction = "截止",
        ),
        items = if (itemTimeText.isBlank()) {
            emptyList()
        } else {
            listOf(
                NoticeItemMeta.Text(
                    kind = "text",
                    label = "消息",
                    fromName = "班委",
                    fromRole = "团支书",
                    timeText = itemTimeText,
                    text = "正文",
                    isFollow = false,
                ),
            )
        },
        hidden = false,
        edited = false,
    )

    private fun data(vararg auto: NoticeEntry) = NoticesData(
        auto = auto.toList(),
        manual = emptyList(),
        hiddenCount = 0,
        fetchedAt = now,
        fromCache = false,
    )

    // ------------------------------------------------------------ 发布时间键

    @Test
    fun `item 消息时间只有 HH_mm 时不算发布时间`() {
        // 历史通知：11-10 发布；item 消息时间只有「21:30」（扫描当天时钟）——旧实现会误算成今天 21:30。
        val old = entry("old", "旧通知", ts = at(2026, 11, 10, 12, 49), itemTimeText = "21:30")
        assertEquals(at(2026, 11, 10, 12, 49), publishedAtOf(old, zone))
    }

    @Test
    fun `组 updatedAt 更新时取较大值`() {
        val e = entry(
            "merged",
            "合并后的通知",
            ts = at(2026, 11, 12, 8, 0),
            updatedAt = "2026-11-14T17:30:00+08:00",
        )
        assertEquals(at(2026, 11, 14, 17, 30), publishedAtOf(e, zone))
    }

    @Test
    fun `updatedAt 早于 ts 时仍用 ts`() {
        val e = entry(
            "stale",
            "旧 updatedAt",
            ts = at(2026, 11, 15, 9, 0),
            updatedAt = "2026-11-13T09:00:00+08:00",
        )
        assertEquals(at(2026, 11, 15, 9, 0), publishedAtOf(e, zone))
    }

    @Test
    fun `updatedAt 非法或缺失时回退 ts`() {
        val broken = entry("broken", "坏 updatedAt", ts = at(2026, 11, 9, 7, 0), updatedAt = "not-a-date")
        assertEquals(at(2026, 11, 9, 7, 0), publishedAtOf(broken, zone))
        val missing = entry("missing", "无 updatedAt", ts = at(2026, 11, 8, 7, 0))
        assertEquals(at(2026, 11, 8, 7, 0), publishedAtOf(missing, zone))
    }

    // ------------------------------------------------------------ 「最新发布」排序

    @Test
    fun `最新发布按真实发布时间倒序 旧通知不会被 HH_mm 顶到最前`() {
        val old = entry("old", "旧通知", ts = at(2026, 11, 10, 12, 49), itemTimeText = "21:30")
        val middle = entry("mid", "中间通知", ts = at(2026, 11, 13, 9, 0), itemTimeText = "08:00")
        val newest = entry(
            "new",
            "最新通知",
            ts = at(2026, 11, 15, 9, 0),
            updatedAt = "2026-11-16T11:00:00+08:00",
        )
        val items = buildUiItems(data(old, middle, newest), MarksSnapshot(), now, zone)
        val latest = viewContentOf(items, cardFilter = NoticeCardFilter.LATEST, nowMillis = now)
        assertTrue(latest.latestOnly)
        assertEquals(listOf("new", "mid", "old"), latest.latest.map { it.auto!!.id })
    }

    @Test
    fun `最新发布卡片的 postedText 与排序键一致 不显示成今天`() {
        val old = entry("old", "旧通知", ts = at(2026, 11, 10, 12, 49), itemTimeText = "21:30")
        val items = buildUiItems(data(old), MarksSnapshot(), now, zone)
        val item = items.first()
        assertEquals(at(2026, 11, 10, 12, 49), item.publishedMillis)
        assertTrue("应显示真实发布日：${item.postedText}", item.postedText.contains("11月10日"))
        assertFalse("不应显示成今天的时钟：${item.postedText}", item.postedText == "21:30")
    }

    @Test
    fun `最新发布只显示网页简报中的采集通知`() {
        val auto = entry("auto", "自动通知", ts = at(2026, 11, 10, 9, 0))
        val manual = com.furina.classtools.data.notices.ManualNotice(
            row = NoticeRow(id = "m1", title = "人工通知", body = "正文", ts = at(2026, 11, 16, 8, 0)),
            attachments = emptyList(),
        )
        val items = buildUiItems(
            NoticesData(
                auto = listOf(auto),
                manual = listOf(manual),
                hiddenCount = 0,
                fetchedAt = now,
                fromCache = false,
            ),
            MarksSnapshot(),
            now,
            zone,
        )
        val latest = viewContentOf(items, cardFilter = NoticeCardFilter.LATEST, nowMillis = now)
        // 自动通知 key = rowId（auto_<gid>）；人工通知 key = 行 id。
        assertEquals(listOf("auto_auto"), latest.latest.map { it.key })
    }
}
