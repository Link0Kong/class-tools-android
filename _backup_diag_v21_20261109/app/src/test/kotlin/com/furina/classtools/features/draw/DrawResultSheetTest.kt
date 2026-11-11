package com.furina.classtools.features.draw

import com.furina.classtools.features.export.ChipsBlock
import com.furina.classtools.features.export.GroupsBlock
import com.furina.classtools.features.export.TextBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawResultSheetTest {

    private fun sampleResult(
        config: DrawConfig = DrawConfig(start = 1, end = 30, count = 2, excludedText = "3,7", sort = DrawSortMode.ASCENDING),
        log: List<DrawRemoveLog> = emptyList(),
    ): DrawResult = DrawResult(
        entries = listOf(DrawEntry(7, null), DrawEntry(23, "王五")),
        pool = listOf(DrawEntry(5, null), DrawEntry(7, null), DrawEntry(12, "李四"), DrawEntry(23, "王五")),
        config = config,
        log = log,
    )

    private fun sampleLog(): List<DrawRemoveLog> = listOf(
        DrawRemoveLog(removed = DrawEntry(7, null), added = DrawEntry(9, null)),
        DrawRemoveLog(removed = DrawEntry(23, "王五"), added = null),
    )

    @Test
    fun `结果单标题 文件名 编号`() {
        val payload = DrawSheetFactory.create(
            result = sampleResult(),
            nowMillis = 1_760_000_000_000L,
            randomSuffix = "ab12",
        )
        assertEquals("抽签结果单", payload.title)
        assertTrue(payload.subtitle.contains("Step 3"))
        assertTrue(payload.fileName.startsWith("抽签结果单_"))
        assertTrue(payload.fileName.endsWith(".png"))
        assertTrue(payload.fileName.contains("_范围1-30_"))
        assertTrue(payload.fileName.contains("_抽2个_"))
        assertTrue(payload.fileName.contains("_不重复_"))
        assertTrue(payload.fileName.contains("_从小到大"))
        assertTrue(payload.fileName.none { it == '/' || it == ':' || it == '*' || it == '?' || it == ' ' })
        assertTrue(payload.recordId.startsWith("SHEET-"))
        assertTrue(payload.recordId.endsWith("-AB12"))
        assertEquals(1_760_000_000_000L, payload.generatedAtMillis)
        assertTrue(payload.footer.startsWith("生成时间 "))
    }

    @Test
    fun `chips 保留序号姓名 参数区与抽签图一致`() {
        val payload = DrawSheetFactory.create(sampleResult(), randomSuffix = "AAAA")
        assertEquals(listOf(7, 23), payload.chips.map { it.number })
        assertNull(payload.chips.first().name)
        assertEquals("王五", payload.chips.last().name)
        val expected = DrawExportFactory.configFields(sampleResult()).map { it.label to it.value }
        assertEquals(expected, payload.config.map { it.label to it.value })
    }

    @Test
    fun `剔除重抽记录行与抽签图同口径`() {
        val result = sampleResult(log = sampleLog())
        val payload = DrawSheetFactory.create(result, randomSuffix = "AAAA")
        assertEquals(2, payload.removeLogLines.size)
        assertEquals(DrawExportFactory.create(result, randomSuffix = "AAAA").removeLogLines, payload.removeLogLines)
        assertTrue(payload.removeLogLines[0].contains("剔除 7"))
        assertTrue(payload.removeLogLines[0].contains("重抽 9"))
        assertTrue(payload.removeLogLines[1].contains("未补抽"))
    }

    @Test
    fun `结果单卡片 spec chips 加剔除文本块`() {
        val payload = DrawSheetFactory.create(sampleResult(log = sampleLog()), randomSuffix = "AAAA")
        val spec = payload.toCardSpec()
        assertEquals(2, spec.blocks.size)
        val chips = spec.blocks[0] as ChipsBlock
        assertEquals("抽签结果", chips.label)
        assertEquals(listOf(7, 23), chips.chips.map { it.n })
        val logBlock = spec.blocks[1] as TextBlock
        assertEquals("剔除重抽记录", logBlock.label)
        assertEquals(2, logBlock.lines.size)
        assertEquals(payload.removeLogLines.take(3), spec.qrNote)
        assertNull(spec.qr)
        assertEquals(payload.fileName, spec.file)
    }

    @Test
    fun `无剔除记录时只有 chips 块`() {
        val payload = DrawSheetFactory.create(sampleResult(), randomSuffix = "AAAA")
        val spec = payload.toCardSpec()
        assertEquals(1, spec.blocks.size)
        assertTrue(spec.blocks.single() is ChipsBlock)
        assertTrue(spec.qrNote.isEmpty())
    }

    @Test
    fun `已分组时插入 GroupsBlock 且保持 chips 在前`() {
        val groups = listOf(
            DrawSheetGroup(members = listOf(DrawExportChip(7, null), DrawExportChip(23, "王五"))),
            DrawSheetGroup(members = listOf(DrawExportChip(12, "李四"))),
        )
        val payload = DrawSheetFactory.create(sampleResult(log = sampleLog()), groups = groups, randomSuffix = "AAAA")
        assertEquals(2, payload.groups.size)
        val spec = payload.toCardSpec()
        assertEquals(3, spec.blocks.size)
        assertTrue(spec.blocks[0] is ChipsBlock)
        val groupsBlock = spec.blocks[1] as GroupsBlock
        assertEquals("分组结果", groupsBlock.label)
        assertEquals(2, groupsBlock.groups.size)
        assertEquals(listOf(7, 23), groupsBlock.groups[0].map { it.n })
        assertEquals("王五", groupsBlock.groups[0][1].nm)
        assertTrue(spec.blocks[2] is TextBlock)
    }

    @Test
    fun `空分组列表不生成 GroupsBlock`() {
        val payload = DrawSheetFactory.create(sampleResult(), groups = emptyList(), randomSuffix = "AAAA")
        assertTrue(payload.groups.isEmpty())
        assertFalse(payload.toCardSpec().blocks.any { it is GroupsBlock })
    }

    @Test
    fun `允许重复与降序进入文件名`() {
        val config = DrawConfig(start = 2, end = 9, count = 3, allowDuplicates = true, sort = DrawSortMode.DESCENDING)
        val payload = DrawSheetFactory.create(
            result = sampleResult(config = config),
            nowMillis = 1_760_000_000_000L,
            randomSuffix = "zz99",
        )
        assertTrue(payload.fileName.contains("_范围2-9_"))
        assertTrue(payload.fileName.contains("_允许重复_"))
        assertTrue(payload.fileName.contains("_从大到小"))
    }
}
