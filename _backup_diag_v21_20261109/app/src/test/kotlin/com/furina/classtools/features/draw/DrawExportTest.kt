package com.furina.classtools.features.draw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawExportTest {

    private fun sampleResult(
        config: DrawConfig = DrawConfig(start = 1, end = 30, count = 2, excludedText = "3,7", sort = DrawSortMode.ASCENDING),
        log: List<DrawRemoveLog> = emptyList(),
    ): DrawResult {
        val pool = listOf(
            DrawEntry(5, null),
            DrawEntry(7, null),
            DrawEntry(12, "李四"),
            DrawEntry(23, "王五"),
        )
        return DrawResult(
            entries = listOf(DrawEntry(7, null), DrawEntry(23, "王五")),
            pool = pool,
            config = config,
            log = log,
        )
    }

    @Test
    fun `文件名符合网页命名规则`() {
        val payload = DrawExportFactory.create(
            result = sampleResult(),
            nowMillis = 1_760_000_000_000L,
            randomSuffix = "ab12",
        )

        assertTrue(payload.fileName.startsWith("抽签结果_"))
        assertTrue(payload.fileName.endsWith(".png"))
        assertTrue(payload.fileName.contains("_范围1-30_"))
        assertTrue(payload.fileName.contains("_抽2个_"))
        assertTrue(payload.fileName.contains("_不重复_"))
        assertTrue(payload.fileName.contains("_从小到大"))
        assertTrue(payload.fileName.none { it == '/' || it == ':' || it == '*' || it == '?' || it == ' ' })
    }

    @Test
    fun `允许重复与排序标签进入文件名`() {
        val payload = DrawExportFactory.create(
            result = sampleResult(
                config = DrawConfig(start = 2, end = 9, count = 3, allowDuplicates = true, sort = DrawSortMode.DESCENDING),
            ),
            nowMillis = 1_760_000_000_000L,
            randomSuffix = "zz99",
        )
        assertTrue(payload.fileName.contains("_范围2-9_"))
        assertTrue(payload.fileName.contains("_允许重复_"))
        assertTrue(payload.fileName.contains("_从大到小"))
    }

    @Test
    fun `唯一编号含 DRAW 前缀与注入后缀`() {
        val payload = DrawExportFactory.create(
            result = sampleResult(),
            nowMillis = 1_760_000_000_000L,
            randomSuffix = "ab12",
        )
        assertTrue(payload.recordId.startsWith("DRAW-"))
        assertTrue(payload.recordId.endsWith("-AB12"))
        // 形如 DRAW-20251009-1234-AB12（stamp 内本身含连字符）
        assertTrue(payload.recordId.length > "DRAW-".length + 4)
    }

    @Test
    fun `参数区包含契约要求的字段`() {
        val fields = DrawExportFactory.create(sampleResult(), randomSuffix = "AAAA").config
        val map = fields.associate { it.label to it.value }
        assertEquals("1 – 30", map["范围"])
        assertEquals("2", map["抽取个数"])
        assertEquals("否", map["允许重复"])
        assertEquals("3,7", map["排除序号"])
        assertEquals("从小到大", map["结果排序"])
        assertEquals("共 4 个", map["可用池"])
    }

    @Test
    fun `排除号为空时显示无`() {
        val fields = DrawExportFactory.create(
            sampleResult(config = DrawConfig(start = 1, end = 10, count = 1)),
            randomSuffix = "AAAA",
        ).config
        assertEquals("无", fields.associate { it.label to it.value }["排除序号"])
    }

    @Test
    fun `chips 块保留序号与姓名`() {
        val payload = DrawExportFactory.create(sampleResult(), randomSuffix = "AAAA")
        val block = payload.blocks.single()
        assertEquals("抽签结果", block.label)
        assertEquals(listOf(7, 23), block.chips.map { it.number })
        assertNull(block.chips.first().name)
        assertEquals("王五", block.chips.last().name)
    }

    @Test
    fun `剔除记录生成文本与二维码内容`() {
        val log = listOf(
            DrawRemoveLog(removed = DrawEntry(7, null), added = DrawEntry(9, null)),
            DrawRemoveLog(removed = DrawEntry(23, "王五"), added = null),
        )
        val payload = DrawExportFactory.create(sampleResult(log = log), randomSuffix = "AAAA")

        assertEquals(2, payload.removeLogLines.size)
        assertTrue(payload.removeLogLines[0].contains("剔除 7"))
        assertTrue(payload.removeLogLines[0].contains("重抽 9"))
        assertTrue(payload.removeLogLines[1].contains("未补抽"))
        assertTrue(payload.qrText!!.startsWith("tool.furina.fun"))
        assertTrue(payload.qrText!!.contains("范围1-30"))
    }

    @Test
    fun `无剔除记录时二维码内容为空`() {
        val payload = DrawExportFactory.create(sampleResult(), randomSuffix = "AAAA")
        assertTrue(payload.removeLogLines.isEmpty())
        assertNull(payload.qrText)
    }

    @Test
    fun `页脚包含生成时间`() {
        val payload = DrawExportFactory.create(sampleResult(), nowMillis = 1_760_000_000_000L, randomSuffix = "AAAA")
        assertTrue(payload.footer.startsWith("生成时间 "))
        assertEquals(1_760_000_000_000L, payload.generatedAtMillis)
        assertEquals("随机抽签结果", payload.title)
    }

    @Test
    fun `safeName 与网页规则一致`() {
        // `/ : *` 与空白 → `-`；字母/中文/下划线保留。
        assertEquals("a-b-c-d-e.png", DrawExportFactory.safeName("a/b:c*d   e.png"))
        assertEquals("abc", DrawExportFactory.safeName("--abc--"))
        assertEquals("无空格.png", DrawExportFactory.safeName("无空格.png"))
        assertEquals("抽签结果_20261009-2024_范围1-50.png", DrawExportFactory.safeName("抽签结果_20261009-2024_范围1-50.png"))
    }

    @Test
    fun `结果摘要行`() {
        val result = sampleResult()
        assertTrue(DrawExportFactory.summaryLine(result).contains("7"))
        assertTrue(DrawExportFactory.summaryLine(result).contains("王五"))
    }
}
