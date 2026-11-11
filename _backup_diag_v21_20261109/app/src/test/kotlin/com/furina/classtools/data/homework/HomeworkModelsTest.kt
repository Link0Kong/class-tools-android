package com.furina.classtools.data.homework

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeworkModelsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val nowMillis: Long = LocalDateTime.of(2026, 11, 7, 12, 0)
        .atZone(zone).toInstant().toEpochMilli()

    private fun row(
        id: String = "hw-1",
        date: String = "",
        time: String = "",
        subject: String = "数学",
        content: String = "作业内容",
        ts: Long = 0,
        attachments: String = "",
    ) = HomeworkRow(
        id = id,
        subject = subject,
        content = content,
        date = date,
        time = time,
        ts = ts,
        attachments = attachments,
    )

    // ------------------------------------------------------------ 截止解析/状态

    @Test
    fun `解析截止日期时间 非法返回 null`() {
        assertEquals(
            LocalDateTime.of(2026, 11, 7, 23, 59).atZone(zone).toInstant().toEpochMilli(),
            HomeworkDueCalculator.parseDue("2026-11-07", "23:59", zone),
        )
        assertEquals(
            LocalDateTime.of(2026, 11, 7, 0, 0).atZone(zone).toInstant().toEpochMilli(),
            HomeworkDueCalculator.parseDue("2026-11-07", "", zone),
        )
        assertNull(HomeworkDueCalculator.parseDue("", "23:59", zone))
        assertNull(HomeworkDueCalculator.parseDue("2026-13-40", "23:59", zone))
    }

    @Test
    fun `剩余小时与剩余天与过期状态`() {
        val hours = HomeworkDueCalculator.evaluate(row(date = "2026-11-07", time = "18:00"), nowMillis)!!
        assertEquals(HomeworkDueState.HOURS_LEFT, hours.state)
        assertEquals(HomeworkDueTone.URGENT, hours.tone)
        assertEquals("还剩 6 小时", hours.label)
        assertFalse(hours.expired)

        val days = HomeworkDueCalculator.evaluate(row(date = "2026-11-09", time = "23:59"), nowMillis)!!
        assertEquals(HomeworkDueState.DAYS_LEFT, days.state)
        assertEquals(HomeworkDueTone.WARN, days.tone) // ~2.5 天 → 3 天（≤3 预警）

        val overdueHours =
            HomeworkDueCalculator.evaluate(row(date = "2026-11-07", time = "06:00"), nowMillis)!!
        assertEquals(HomeworkDueState.OVERDUE_HOURS, overdueHours.state)
        assertEquals("已过期 6 小时", overdueHours.label)
        assertTrue(overdueHours.expired)

        val overdueDays =
            HomeworkDueCalculator.evaluate(row(date = "2026-11-05", time = "00:00"), nowMillis)!!
        assertEquals(HomeworkDueState.OVERDUE_DAYS, overdueDays.state)
        assertEquals("已过期 2 天", overdueDays.label)
    }

    @Test
    fun `无截止日期返回 null 且不崩溃`() {
        assertNull(HomeworkDueCalculator.evaluate(row(), nowMillis))
        assertNull(HomeworkDueCalculator.toItem(row(), done = false, nowMillis = nowMillis).due)
    }

    @Test
    fun `过期超过 3 天才进入内存清理`() {
        val fourDaysAgo = HomeworkDueCalculator.evaluate(row(date = "2026-11-02", time = "00:00"), nowMillis)!!
        assertTrue(fourDaysAgo.prunable)

        val yesterday = HomeworkDueCalculator.evaluate(row(date = "2026-11-06", time = "00:00"), nowMillis)!!
        assertFalse(yesterday.prunable)
    }

    @Test
    fun `prune 过滤过期超过 3 天的项`() {
        val items = listOf(
            HomeworkDueCalculator.toItem(row(id = "a", date = "2026-10-01"), false, nowMillis),
            HomeworkDueCalculator.toItem(row(id = "b", date = "2026-11-09"), false, nowMillis),
        )
        val pruned = HomeworkDueCalculator.pruneExpired(items)
        assertEquals(listOf("b"), pruned.map { it.id })
    }

    // ------------------------------------------------------------ 排序 / 筛选 / 分组

    @Test
    fun `未完成在前且按截止升序 已完成沉底`() {
        val items = listOf(
            HomeworkDueCalculator.toItem(row(id = "done", date = "2026-11-06"), true, nowMillis),
            HomeworkDueCalculator.toItem(row(id = "late", date = "2026-11-12"), false, nowMillis),
            HomeworkDueCalculator.toItem(row(id = "soon", date = "2026-11-08"), false, nowMillis),
            HomeworkDueCalculator.toItem(row(id = "none"), false, nowMillis),
        )
        val sorted = HomeworkDueCalculator.sort(items)
        assertEquals(listOf("soon", "late", "none", "done"), sorted.map { it.id })
    }

    @Test
    fun `筛选 全部 未完成 已完成`() {
        val items = listOf(
            HomeworkDueCalculator.toItem(row(id = "a"), false, nowMillis),
            HomeworkDueCalculator.toItem(row(id = "b"), true, nowMillis),
        )
        assertEquals(2, HomeworkDueCalculator.filter(items, HomeworkFilter.ALL).size)
        assertEquals(listOf("a"), HomeworkDueCalculator.filter(items, HomeworkFilter.UNFINISHED).map { it.id })
        assertEquals(listOf("b"), HomeworkDueCalculator.filter(items, HomeworkFilter.DONE).map { it.id })
    }

    @Test
    fun `分组标题 今天 明天 过期 无截止`() {
        val today = HomeworkDueCalculator.toItem(row(date = "2026-11-07", time = "23:00"), false, nowMillis)
        assertEquals("今天 · 11-07", HomeworkDueCalculator.groupLabel(today, nowMillis))

        val tomorrow = HomeworkDueCalculator.toItem(row(date = "2026-11-08", time = "23:00"), false, nowMillis)
        assertEquals("明天 · 11-08", HomeworkDueCalculator.groupLabel(tomorrow, nowMillis))

        val expired = HomeworkDueCalculator.toItem(row(date = "2026-11-01"), false, nowMillis)
        assertEquals("已过期", HomeworkDueCalculator.groupLabel(expired, nowMillis))

        val none = HomeworkDueCalculator.toItem(row(), false, nowMillis)
        assertEquals("无截止日期", HomeworkDueCalculator.groupLabel(none, nowMillis))
    }

    @Test
    fun `短截止文本与完整截止文本`() {
        val r = row(date = "2026-11-09", time = "18:30")
        assertEquals("11-09 18:30", HomeworkDueCalculator.shortDueText(r))
        assertEquals("2026-11-09 18:30", HomeworkDueCalculator.dueText(r))
    }

    // ------------------------------------------------------------ 附件解析

    @Test
    fun `附件解析只保留元信息且估算 data URI 体积`() {
        val raw = """
            [
              {"n":"作业.jpg","t":"image/jpeg","s":12345,"d":"data:image/jpeg;base64,AAAA"},
              {"n":"要求.txt","t":"text/plain","d":"https://example.com/a.txt"},
              {"t":"application/pdf","d":"data:application/pdf;base64,AAAAAAAAAAAA"}
            ]
        """.trimIndent()
        val metas = HomeworkAttachments.parse(raw)
        assertEquals(3, metas.size)
        assertTrue(metas[0].isImage)
        assertEquals(12345L, metas[0].sizeBytes)
        assertEquals(HomeworkAttachmentKind.DATA_URI, metas[0].kind)
        assertEquals(HomeworkAttachmentKind.URL, metas[1].kind)
        assertEquals("作业.jpg", metas[0].name)
        // 未给 s 时按 base64 长度估算（16 字符 → 12 字节）
        assertTrue(metas[2].sizeBytes > 0)
        assertNotNull(HomeworkAttachments.dataOf(raw, 1))
        assertNull(HomeworkAttachments.dataOf(raw, 9))
    }

    @Test
    fun `坏 attachments JSON 返回空列表不崩`() {
        assertEquals(0, HomeworkAttachments.parse("not-json").size)
        assertEquals(0, HomeworkAttachments.parse("""{"n":"x"}""").size)
        assertEquals(0, HomeworkAttachments.parse(null).size)
        assertEquals(0, HomeworkAttachments.count("["))
    }

    @Test
    fun `附件体积格式化`() {
        assertEquals("—", HomeworkAttachmentMeta.formatSize(0))
        assertEquals("512 B", HomeworkAttachmentMeta.formatSize(512))
        assertEquals("1.0 KB", HomeworkAttachmentMeta.formatSize(1024))
        assertEquals("1.5 MB", HomeworkAttachmentMeta.formatSize(1024L * 1024 + 512 * 1024))
    }

    @Test
    fun `toItem 组合完成状态与附件`() {
        val item = HomeworkDueCalculator.toItem(
            row(date = "2026-11-09", attachments = """[{"n":"a.pdf","t":"application/pdf","s":10}]"""),
            done = true,
            nowMillis = nowMillis,
        )
        assertTrue(item.done)
        assertNotNull(item.due)
        assertEquals(1, item.attachments.size)
        assertEquals("数学", item.title)
    }
}
