package com.furina.classtools.features.draw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawValidationTest {

    // ------------------------------------------------------------ 排除号解析

    @Test
    fun `解析排除号支持逗号顿号空白与区间`() {
        val parsed = DrawValidator.parseExcluded("3, 7，10-12、15~16 20—21")
        assertTrue(parsed.isValid)
        assertEquals(setOf(3, 7, 10, 11, 12, 15, 16, 20, 21), parsed.numbers)
    }

    @Test
    fun `空文本解析为空集合`() {
        val parsed = DrawValidator.parseExcluded("  ")
        assertTrue(parsed.isValid)
        assertTrue(parsed.numbers.isEmpty())
    }

    @Test
    fun `非法排除片段被记录`() {
        val parsed = DrawValidator.parseExcluded("3,abc,5-9,12-x,-4")
        assertFalse(parsed.isValid)
        assertEquals(listOf("abc", "12-x", "-4"), parsed.invalidTokens)
        assertTrue(3 in parsed.numbers)
        assertEquals((5..9).toSet(), parsed.numbers.filter { it in 5..9 }.toSet())
    }

    @Test
    fun `反向区间与超边界片段非法`() {
        assertFalse(DrawValidator.parseExcluded("12-5").isValid)
        assertFalse(DrawValidator.parseExcluded("1000").isValid)
        assertFalse(DrawValidator.parseExcluded("0").isValid)
    }

    // ------------------------------------------------------------ 参数校验

    @Test
    fun `合法参数返回升序候选池`() {
        val result = DrawValidator.validate(DrawConfig(start = 1, end = 10, count = 3))
        val numbers = (result as DrawValidation.Valid).numbers
        assertEquals((1..10).toList(), numbers)
    }

    @Test
    fun `排除号从候选池剔除`() {
        val result = DrawValidator.validate(
            DrawConfig(start = 1, end = 10, count = 3, excludedText = "2,4-5"),
        )
        assertEquals(listOf(1, 3, 6, 7, 8, 9, 10), (result as DrawValidation.Valid).numbers)
    }

    @Test
    fun `范围越界与起点大于终点报错`() {
        val outOfBounds = DrawValidator.validate(DrawConfig(start = 0, end = 10))
        assertEquals(DrawErrorCode.RANGE_INVALID, (outOfBounds as DrawValidation.Invalid).error.code)

        val tooBig = DrawValidator.validate(DrawConfig(start = 1, end = 1000))
        assertEquals(DrawErrorCode.RANGE_INVALID, (tooBig as DrawValidation.Invalid).error.code)

        val reversed = DrawValidator.validate(DrawConfig(start = 20, end = 10))
        assertEquals(DrawErrorCode.RANGE_INVALID, (reversed as DrawValidation.Invalid).error.code)
    }

    @Test
    fun `个数越界报错`() {
        val zero = DrawValidator.validate(DrawConfig(count = 0))
        assertEquals(DrawErrorCode.COUNT_INVALID, (zero as DrawValidation.Invalid).error.code)

        val tooMany = DrawValidator.validate(DrawConfig(count = 1000))
        assertEquals(DrawErrorCode.COUNT_INVALID, (tooMany as DrawValidation.Invalid).error.code)
    }

    @Test
    fun `排除格式错误优先于池为空报错`() {
        val result = DrawValidator.validate(
            DrawConfig(start = 1, end = 3, count = 1, excludedText = "1-3,bad"),
        )
        assertEquals(DrawErrorCode.EXCLUSION_INVALID, (result as DrawValidation.Invalid).error.code)
    }

    @Test
    fun `排除完没有候选报错`() {
        val result = DrawValidator.validate(
            DrawConfig(start = 1, end = 3, count = 1, excludedText = "1-3"),
        )
        assertEquals(DrawErrorCode.POOL_EMPTY, (result as DrawValidation.Invalid).error.code)
    }

    @Test
    fun `不重复时个数超过池大小报错并带可用数`() {
        val result = DrawValidator.validate(DrawConfig(start = 1, end = 5, count = 6))
        val error = (result as DrawValidation.Invalid).error
        assertEquals(DrawErrorCode.COUNT_EXCEEDS_POOL, error.code)
        assertTrue(error.message.contains("5"))
    }

    @Test
    fun `允许重复时个数可以超过池大小`() {
        val result = DrawValidator.validate(
            DrawConfig(start = 1, end = 2, count = 10, allowDuplicates = true),
        )
        assertTrue(result is DrawValidation.Valid)
    }

    @Test
    fun `名单来源必须提供名单`() {
        val result = DrawValidator.validate(
            DrawConfig(start = 1, end = 10, count = 2, poolSource = DrawPoolSource.ROSTER),
            rosterSeats = emptyList(),
        )
        assertEquals(DrawErrorCode.ROSTER_REQUIRED, (result as DrawValidation.Invalid).error.code)
    }

    @Test
    fun `名单来源与范围取交集并过滤非法座号`() {
        val result = DrawValidator.validate(
            DrawConfig(start = 1, end = 10, count = 2, poolSource = DrawPoolSource.ROSTER),
            rosterSeats = listOf(2, 4, 6, 99, -1),
        )
        assertEquals(listOf(2, 4, 6), (result as DrawValidation.Valid).numbers)
    }

    @Test
    fun `单个候选人可抽 1 个`() {
        val valid = DrawValidator.validate(DrawConfig(start = 7, end = 7, count = 1))
        assertEquals(listOf(7), (valid as DrawValidation.Valid).numbers)
    }
}
