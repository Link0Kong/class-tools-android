package com.furina.classtools.data.notices

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeDedupeTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun iso(month: Int, day: Int, hour: Int, minute: Int = 0): String =
        LocalDateTime.of(2026, month, day, hour, minute).toString() + "+08:00"

    private fun group(
        id: String,
        title: String = "关于主题班会的通知",
        cat: String = "班会",
        source: String = "2026级信管1班",
        sender: String = "陈子麒",
        deadline: String = "",
        eventAt: String = "",
        mergedSources: List<String> = emptyList(),
        crossSource: Boolean = false,
    ): NoticeGroupDto = NoticeGroupDto(
        id = id,
        title = title,
        cat = cat,
        source = source,
        senderName = sender,
        deadline = deadline,
        eventAt = eventAt,
        mergedSources = mergedSources,
        crossSource = crossSource,
    )

    private fun entry(
        rowId: String,
        group: NoticeGroupDto,
        ts: Long,
        items: List<NoticeItemMeta> = emptyList(),
        hidden: Boolean = false,
        editedAt: Long = 0L,
    ): NoticeEntry = NoticeEntry(
        row = NoticeRow(id = rowId, ts = ts, hidden = hidden, editedAt = editedAt),
        group = group,
        items = items,
        hidden = hidden,
        edited = editedAt > 0L,
    )

    private fun text(text: String, follow: Boolean = false): NoticeItemMeta = NoticeItemMeta.Text(
        kind = if (follow) "follow" else "text",
        label = if (follow) "补充通知" else "原始消息",
        fromName = "陈子麒",
        fromRole = "班长",
        timeText = "11-10 10:00",
        text = text,
        isFollow = follow,
    )

    private fun file(name: String, size: String = "1.2 MB"): NoticeItemMeta = NoticeItemMeta.File(
        kind = "file",
        name = name,
        ext = "pdf",
        sizeText = size,
        note = "",
        hasContent = true,
        pending = false,
        archiveOk = false,
        archiveCount = 0,
        archiveSummary = "",
    )

    // ------------------------------------------------------------ 同 group id

    @Test
    fun `同一 group id 的重复行合并为一条 并保留较新与补充条目`() {
        val old = entry("auto_g1", group("g1"), ts = 100L, items = listOf(text("原始消息")))
        val newer = entry(
            "auto_g1_dup",
            group("g1"),
            ts = 200L,
            items = listOf(text("原始消息"), text("补充：地点改到 01104", follow = true)),
            editedAt = 0L,
        )
        val result = NoticeDedupe.dedupeAuto(listOf(old, newer))
        assertEquals(1, result.entries.size)
        assertEquals(1, result.mergedCount)
        val merged = result.entries.single()
        assertEquals(200L, merged.ts)
        assertEquals(2, merged.items.size)
        assertTrue(merged.items.any { it is NoticeItemMeta.Text && it.isFollow })
    }

    @Test
    fun `同 group id 合并时优先管理员编辑过的行`() {
        val plain = entry("auto_g1", group("g1", title = "原标题"), ts = 300L)
        val edited = entry(
            "auto_g1_dup",
            group("g1", title = "管理员改后的标题"),
            ts = 200L,
            editedAt = 999L,
        )
        val merged = NoticeDedupe.dedupeAuto(listOf(plain, edited)).entries.single()
        assertEquals("管理员改后的标题", merged.title)
        assertTrue(merged.edited)
    }

    @Test
    fun `同 group id 隐藏与可见重复时管理员保留可见一条`() {
        val hidden = entry("auto_g1", group("g1"), ts = 100L, hidden = true)
        val visible = entry("auto_g1_dup", group("g1"), ts = 200L, hidden = false)
        val merged = NoticeDedupe.dedupeAuto(listOf(hidden, visible)).entries.single()
        assertFalse(merged.hidden)
    }

    // ------------------------------------------------------------ 语义重复（不同 id）

    @Test
    fun `不同 id 同标题分类来源且时间接近的重复通知合并`() {
        val a = entry(
            "auto_old",
            group("g_old", deadline = iso(11, 15, 16, 0), mergedSources = listOf("微信")),
            ts = 500L,
            items = listOf(text("10月15日16:00 班会")),
        )
        val b = entry(
            "auto_new",
            group("g_new", deadline = iso(11, 15, 16, 5), mergedSources = listOf("QQ")),
            ts = 600L,
            items = listOf(text("10月15日16:00 班会"), text("补充：带笔记本", follow = true)),
        )
        val result = NoticeDedupe.dedupeAuto(listOf(a, b))
        assertEquals(1, result.entries.size)
        assertEquals(1, result.mergedCount)
        val merged = result.entries.single()
        assertEquals(600L, merged.ts)
        assertEquals(2, merged.items.size)
        assertTrue(merged.group.mergedSources.containsAll(listOf("微信", "QQ")))
        assertTrue(merged.group.crossSource)
    }

    @Test
    fun `无结构化时间时同来源且条目重叠才合并`() {
        val a = entry("auto_a", group("ga"), ts = 100L, items = listOf(text("请提交材料")))
        val b = entry("auto_b", group("gb"), ts = 200L, items = listOf(text("请提交材料")))
        assertEquals(1, NoticeDedupe.dedupeAuto(listOf(a, b)).entries.size)

        val c = entry("auto_c", group("gc"), ts = 100L, items = listOf(text("另一条内容")))
        val d = entry("auto_d", group("gd"), ts = 200L, items = listOf(text("请提交材料")))
        assertEquals(2, NoticeDedupe.dedupeAuto(listOf(c, d)).entries.size)
    }

    @Test
    fun `不同标题或分类不合并`() {
        val a = entry("auto_a", group("ga", title = "关于班会的通知", deadline = iso(11, 15, 16)), ts = 1L)
        val b = entry("auto_b", group("gb", title = "关于团课的通知", deadline = iso(11, 15, 16)), ts = 2L)
        assertEquals(2, NoticeDedupe.dedupeAuto(listOf(a, b)).entries.size)

        val c = entry("auto_c", group("gc", cat = "班会", deadline = iso(11, 15, 16)), ts = 1L)
        val d = entry("auto_d", group("gd", cat = "通知", deadline = iso(11, 15, 16)), ts = 2L)
        assertEquals(2, NoticeDedupe.dedupeAuto(listOf(c, d)).entries.size)
    }

    @Test
    fun `时间相差超过窗口不合并 保留两场次`() {
        val morning = entry(
            "auto_am",
            group("g_am", deadline = iso(11, 15, 9, 0)),
            ts = 1L,
        )
        val afternoon = entry(
            "auto_pm",
            group("g_pm", deadline = iso(11, 15, 16, 0)),
            ts = 2L,
        )
        assertEquals(2, NoticeDedupe.dedupeAuto(listOf(morning, afternoon)).entries.size)
    }

    @Test
    fun `无时间同标题但来源不同不合并`() {
        val a = entry("auto_a", group("ga", source = "微信群A"), ts = 1L, items = listOf(text("内容A")))
        val b = entry("auto_b", group("gb", source = "微信群B"), ts = 2L, items = listOf(text("内容B")))
        assertEquals(2, NoticeDedupe.dedupeAuto(listOf(a, b)).entries.size)
    }

    @Test
    fun `mergedSources 交集也作为同一通知证据`() {
        val a = entry(
            "auto_a",
            group("ga", deadline = iso(11, 15, 16), mergedSources = listOf("微信群")),
            ts = 1L,
        )
        val b = entry(
            "auto_b",
            group("gb", deadline = iso(11, 15, 16, 30), mergedSources = listOf("微信群")),
            ts = 2L,
        )
        assertEquals(1, NoticeDedupe.dedupeAuto(listOf(a, b)).entries.size)
    }

    // ------------------------------------------------------------ 条目级

    @Test
    fun `条目合并按内容指纹去重并保持补充顺序`() {
        val primary = listOf(text("原始消息"), text("补充1", follow = true))
        val secondary = listOf(text("原始消息"), text("补充1", follow = true), text("补充2", follow = true))
        val merged = NoticeDedupe.mergeItems(primary, secondary)
        assertEquals(3, merged.size)
        assertTrue(merged[2] is NoticeItemMeta.Text && (merged[2] as NoticeItemMeta.Text).text == "补充2")
        assertTrue(NoticeDedupe.itemsOverlap(primary, secondary))
    }

    @Test
    fun `条目指纹规范化空白与大小写 且不同内容不相等`() {
        val a = text("  请提交   材料 ")
        val b = text("请提交 材料")
        assertEquals(NoticeDedupe.fingerprintOf(a), NoticeDedupe.fingerprintOf(b))
        assertFalse(NoticeDedupe.fingerprintOf(a) == NoticeDedupe.fingerprintOf(text("另一条")))
        assertEquals(
            NoticeDedupe.fingerprintOf(file("名单.pdf", "1 MB")),
            NoticeDedupe.fingerprintOf(file("名单.PDF", "1 MB")),
        )
        assertFalse(
            NoticeDedupe.fingerprintOf(file("名单.pdf", "1 MB")) ==
                NoticeDedupe.fingerprintOf(file("名单.pdf", "2 MB")),
        )
    }

    @Test
    fun `条目合并不超过上限`() {
        val primary = (1..3).map { text("p$it") }
        val secondary = (1..100).map { text("s$it") }
        assertEquals(NoticeDedupe.MAX_MERGED_ITEMS, NoticeDedupe.mergeItems(primary, secondary).size)
    }

    // ------------------------------------------------------------ 人工通知

    @Test
    fun `人工通知同标题正文十分钟内去重`() {
        val a = noticeRow("m1", "班费公示", "标题", ts = 1_000_000L)
        val b = noticeRow("m2", "班费公示", "标题", ts = 1_000_000L + 5 * 60_000L)
        val result = NoticeDedupe.dedupeManual(listOf(a, b))
        assertEquals(1, result.entries.size)
        assertEquals(1, result.mergedCount)
    }

    @Test
    fun `人工通知同标题正文但超过窗口保留`() {
        val a = noticeRow("m1", "班费公示", "标题", ts = 1_000_000L)
        val b = noticeRow("m2", "班费公示", "标题", ts = 1_000_000L + 60 * 60_000L)
        assertEquals(2, NoticeDedupe.dedupeManual(listOf(a, b)).entries.size)
    }

    @Test
    fun `人工通知不同标题或正文不合并`() {
        val a = noticeRow("m1", "班费公示", "正文A", ts = 1_000_000L)
        val b = noticeRow("m2", "班费公示", "正文B", ts = 1_000_000L + 60_000L)
        assertEquals(2, NoticeDedupe.dedupeManual(listOf(a, b)).entries.size)
        val c = noticeRow("m3", "卫生检查", "正文A", ts = 1_000_000L)
        assertEquals(2, NoticeDedupe.dedupeManual(listOf(a, c)).entries.size)
    }

    @Test
    fun `人工通知合并保留附件更完整的一条`() {
        val few = noticeRow("m1", "班费公示", "正文", ts = 1_000_000L, attachmentCount = 0)
        val more = noticeRow("m2", "班费公示", "正文", ts = 1_000_000L + 60_000L, attachmentCount = 2)
        val merged = NoticeDedupe.dedupeManual(listOf(few, more)).entries.single()
        assertEquals(2, merged.attachments.size)
    }

    private fun noticeRow(
        id: String,
        title: String,
        body: String,
        ts: Long,
        attachmentCount: Int = 0,
    ): ManualNotice = ManualNotice(
        row = NoticeRow(id = id, title = title, body = body, ts = ts),
        attachments = (0 until attachmentCount).map { index ->
            NoticeManualAttachment(
                index = index,
                name = "f$index.pdf",
                mime = "application/pdf",
                sizeText = "1 MB",
                hasData = false,
            )
        },
    )

    // ------------------------------------------------------------ 排序/稳定性

    @Test
    fun `去重结果按 ts 倒序且稳定`() {
        val entries = listOf(
            entry("auto_a", group("ga", title = "A", deadline = iso(11, 15, 16)), ts = 100L),
            entry("auto_b", group("gb", title = "B", deadline = iso(11, 16, 16)), ts = 300L),
            entry("auto_c", group("gc", title = "C", deadline = iso(11, 17, 16)), ts = 200L),
        )
        val sorted = NoticeDedupe.dedupeAuto(entries).entries.map { it.ts }
        assertEquals(listOf(300L, 200L, 100L), sorted)
    }

    @Test
    fun `空输入与单条输入直接返回`() {
        assertEquals(0, NoticeDedupe.dedupeAuto(emptyList()).entries.size)
        assertEquals(0, NoticeDedupe.dedupeManual(emptyList()).entries.size)
        val one = entry("auto_a", group("ga"), ts = 1L)
        assertEquals(1, NoticeDedupe.dedupeAuto(listOf(one)).entries.size)
    }
}
