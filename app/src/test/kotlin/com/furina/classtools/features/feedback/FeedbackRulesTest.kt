package com.furina.classtools.features.feedback

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackRulesTest {

    private fun row(
        id: String = "f1",
        acc: String = "2024000002",
        content: String = "建议",
        done: Boolean = false,
        ts: Long = 1_740_000_000_000L,
    ) = FeedbackRow(id = id, content = content, acc = acc, done = done, ts = ts)

    // ------------------------------------------------------------ 表单校验

    @Test
    fun `空内容与纯空白被拒绝`() {
        assertEquals("写点反馈内容", FeedbackRules.validateContent(""))
        assertEquals("写点反馈内容", FeedbackRules.validateContent("   \n "))
        assertNull(FeedbackRules.validateContent("  有内容  "))
    }

    @Test
    fun `超过 500 字被拒绝 边界 500 合法`() {
        val text = "字".repeat(FeedbackRules.MAX_CONTENT_LENGTH)
        assertNull(FeedbackRules.validateContent(text))
        assertNotNull(FeedbackRules.validateContent(text + "多"))
        assertEquals(500, FeedbackRules.MAX_CONTENT_LENGTH)
    }

    @Test
    fun `提交前规范化 trim`() {
        assertEquals("希望增加提醒", FeedbackRules.normalizeContent("  希望增加提醒 \n"))
    }

    // ------------------------------------------------------------ ID / 时间

    @Test
    fun `反馈 id 前缀为时间戳 base36`() {
        val now = 1_700_000_000_000L
        val id = FeedbackRules.newId(now, "abc123")
        assertTrue(id.startsWith(now.toString(36)))
        assertEquals(now.toString(36).length + 6, id.length)
        assertEquals(id, FeedbackRules.newId(now, "abc123"))
    }

    @Test
    fun `随机 id 后缀非空且为 base36`() {
        val suffix = FeedbackRules.randomBase36()
        assertEquals(6, suffix.length)
        assertTrue(suffix.all { it in "0123456789abcdefghijklmnopqrstuvwxyz" })
    }

    @Test
    fun `时间格式与 mp 一致 非法时间返回空串`() {
        val millis = java.time.LocalDateTime.of(2026, 11, 8, 9, 5)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals("2026-11-08 09:05", FeedbackRules.formatTime(millis))
        assertEquals("", FeedbackRules.formatTime(0L))
    }

    // ------------------------------------------------------------ 过滤 / 作者名

    @Test
    fun `普通用户只看自己的反馈`() {
        val rows = listOf(row("a", acc = "111", ts = 3), row("b", acc = "222", ts = 2))
        val filtered = FeedbackRules.filterRows(rows, sid = "111", FeedbackFilter.MINE, isAdmin = false)
        assertEquals(listOf("a"), filtered.map { it.id })
        // 即使传入管理员档位，普通用户仍只看自己
        val forcedAll = FeedbackRules.filterRows(rows, sid = "111", FeedbackFilter.ALL, isAdmin = false)
        assertEquals(listOf("a"), forcedAll.map { it.id })
    }

    @Test
    fun `管理员三档筛选`() {
        val rows = listOf(
            row("a", acc = "111", done = true, ts = 3),
            row("b", acc = "222", done = false, ts = 2),
            row("c", acc = "111", done = false, ts = 1),
        )
        assertEquals(listOf("a", "b", "c"), FeedbackRules.filterRows(rows, "111", FeedbackFilter.ALL, true).map { it.id })
        assertEquals(listOf("b", "c"), FeedbackRules.filterRows(rows, "111", FeedbackFilter.TODO, true).map { it.id })
        assertEquals(listOf("a"), FeedbackRules.filterRows(rows, "111", FeedbackFilter.DONE, true).map { it.id })
    }

    @Test
    fun `作者名展示 普通用户为我自己 管理员回退未知成员`() {
        val mine = row(acc = "111")
        val other = row(acc = "222")
        assertEquals("我", FeedbackRules.displayAuthor(mine, "111", isAdmin = false))
        assertEquals("我", FeedbackRules.displayAuthor(other, "111", isAdmin = false))

        assertEquals("张三", FeedbackRules.displayAuthor(mine, "111", isAdmin = true) { "张三" })
        assertEquals("（未知成员）", FeedbackRules.displayAuthor(other, "111", isAdmin = true) { null })
        assertEquals("我", FeedbackRules.displayAuthor(mine, "111", isAdmin = true) { "" })
    }

    @Test
    fun `副标题口径与 mp 一致`() {
        assertEquals("把建议或问题发给管理员", FeedbackRules.subtitle(3, isAdmin = false))
        assertEquals("共 3 条 · 可标记已处理", FeedbackRules.subtitle(3, isAdmin = true))
    }

    @Test
    fun `管理员与普通用户筛选档位集合`() {
        assertEquals(
            listOf(FeedbackFilter.ALL, FeedbackFilter.TODO, FeedbackFilter.DONE),
            FeedbackFilter.adminFilters,
        )
        assertEquals(listOf(FeedbackFilter.MINE), FeedbackFilter.userFilters)
        assertEquals("我的反馈", FeedbackFilter.MINE.label)
        assertFalse(FeedbackFilter.MINE in FeedbackFilter.adminFilters)
    }

    @Test
    fun `展示时间不早于纪元`() {
        val ts = Instant.now().toEpochMilli()
        assertTrue(FeedbackRules.formatTime(ts).matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""")))
    }
}
