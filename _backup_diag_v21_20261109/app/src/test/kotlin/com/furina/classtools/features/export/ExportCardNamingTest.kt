package com.furina.classtools.features.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExportCardNamingTest {

    private val fixedNow = 1_760_000_000_000L // 2025-10-09 16:53 UTC 附近，仅用于稳定断言（测试不依赖本地时区显示）

    @Test
    fun `safeName 替换非法字符并保留中文`() {
        assertEquals("抽签结果_20261009-2024_范围1-50", ExportCardNaming.safeName("抽签结果_20261009-2024_范围1-50"))
        assertEquals("a-b-c", ExportCardNaming.safeName("a/b\\c"))
        assertEquals("a-b", ExportCardNaming.safeName("a  b"))
        assertEquals("导出", ExportCardNaming.safeName("///"))
    }

    @Test
    fun `recordId 格式 PREFIX-YYYYMMDD-HHMM-XXXX`() {
        val rec = ExportCardNaming.recordId(prefix = "DRAW", now = fixedNow, random = Random(7))
        val parts = rec.split("-")
        assertEquals(4, parts.size)
        assertEquals("DRAW", parts[0])
        assertEquals(8, parts[1].length)
        assertEquals(4, parts[2].length)
        assertEquals(4, parts[3].length)
        assertTrue(parts[3].all { it.isLetterOrDigit() })
    }

    @Test
    fun `recordId 相同种子可复现`() {
        val a = ExportCardNaming.recordId(prefix = "CT", now = fixedNow, random = Random(42))
        val b = ExportCardNaming.recordId(prefix = "CT", now = fixedNow, random = Random(42))
        assertEquals(a, b)
    }

    @Test
    fun `文件名包含类型-时间-参数 且以 png 结尾`() {
        val name = ExportCardNaming.fileName(
            kind = ExportKind.DRAW_STEP1,
            params = listOf("范围1-50", "抽10个", "不重复"),
            now = fixedNow,
        )
        assertTrue(name.startsWith("抽签结果_"))
        assertTrue(name.endsWith(".png"))
        assertTrue(name.contains("范围1-50"))
        assertTrue(name.contains("抽10个"))
        assertTrue(name.contains("不重复"))
    }

    @Test
    fun `文件名过滤空参数且限制长度`() {
        val name = ExportCardNaming.fileName(
            kind = ExportKind.DRAW_STEP2,
            params = listOf("", "  ", "a/b"),
            now = fixedNow,
        )
        assertTrue(name.contains("a-b"))
        assertTrue(name.length <= 124)
    }

    @Test
    fun `footText 以生成时间开头`() {
        assertTrue(ExportCardNaming.footText(fixedNow).startsWith("生成时间 "))
    }
}
