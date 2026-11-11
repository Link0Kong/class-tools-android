package com.furina.classtools.features.draw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawEngineTest {

    private fun draw(
        config: DrawConfig,
        seed: Long = 42L,
        roster: List<Int> = emptyList(),
        nameOf: (Int) -> String? = { null },
    ): DrawResult {
        val outcome = DrawEngine.draw(config, roster, nameOf, SeededDrawRandom(seed))
        assertTrue("draw failed: $outcome", outcome is DrawOutcome.Success)
        return (outcome as DrawOutcome.Success).result
    }

    @Test
    fun `同 seed 结果可复现`() {
        val config = DrawConfig(start = 1, end = 30, count = 5)
        val first = draw(config, seed = 7)
        val second = draw(config, seed = 7)
        assertEquals(first.entries.map { it.number }, second.entries.map { it.number })
    }

    @Test
    fun `不同 seed 结果通常不同`() {
        val config = DrawConfig(start = 1, end = 50, count = 6)
        val first = draw(config, seed = 1).entries.map { it.number }
        val second = draw(config, seed = 2).entries.map { it.number }
        assertFalse(first == second)
    }

    @Test
    fun `结果都在范围内且不重复`() {
        val config = DrawConfig(start = 10, end = 20, count = 8)
        val result = draw(config, seed = 123)
        assertEquals(8, result.entries.size)
        assertEquals(8, result.entries.map { it.number }.toSet().size)
        assertTrue(result.entries.all { it.number in 10..20 })
    }

    @Test
    fun `排除号不会出现在结果中`() {
        val config = DrawConfig(start = 1, end = 20, count = 5, excludedText = "3,7,10-12")
        val numbers = draw(config, seed = 5).entries.map { it.number }
        assertTrue(numbers.none { it == 3 || it == 7 || it in 10..12 })
    }

    @Test
    fun `人数等于范围上限时抽完全部`() {
        val config = DrawConfig(start = 1, end = 6, count = 6)
        val numbers = draw(config, seed = 9).entries.map { it.number }.toSet()
        assertEquals((1..6).toSet(), numbers)
    }

    @Test
    fun `不重复且个数超过池时报错`() {
        val outcome = DrawEngine.draw(
            DrawConfig(start = 1, end = 3, count = 4),
            random = SeededDrawRandom(1),
        )
        assertTrue(outcome is DrawOutcome.Failure)
        assertEquals(
            DrawErrorCode.COUNT_EXCEEDS_POOL,
            (outcome as DrawOutcome.Failure).error.code,
        )
    }

    @Test
    fun `允许重复时按个数抽取且可重复`() {
        val config = DrawConfig(start = 1, end = 3, count = 12, allowDuplicates = true)
        val entries = draw(config, seed = 3).entries
        assertEquals(12, entries.size)
        assertTrue(entries.all { it.number in 1..3 })
    }

    @Test
    fun `排序模式 从小到大 与 从大到小`() {
        val asc = DrawEngine.draw(
            DrawConfig(start = 1, end = 30, count = 6, sort = DrawSortMode.ASCENDING),
            random = SeededDrawRandom(11),
        ) as DrawOutcome.Success
        assertEquals(asc.result.entries.map { it.number }.sorted(), asc.result.entries.map { it.number })

        val desc = DrawEngine.draw(
            DrawConfig(start = 1, end = 30, count = 6, sort = DrawSortMode.DESCENDING),
            random = SeededDrawRandom(11),
        ) as DrawOutcome.Success
        assertEquals(
            desc.result.entries.map { it.number }.sortedDescending(),
            desc.result.entries.map { it.number },
        )
    }

    @Test
    fun `随机排序不改变集合`() {
        val base = draw(DrawConfig(start = 1, end = 30, count = 6, sort = DrawSortMode.DRAW_ORDER), seed = 21)
        val shuffled = draw(DrawConfig(start = 1, end = 30, count = 6, sort = DrawSortMode.RANDOM), seed = 21)
        assertEquals(base.entries.map { it.number }.toSet(), shuffled.entries.map { it.number }.toSet())
    }

    @Test
    fun `名单来源返回姓名映射`() {
        val roster = listOf(1, 3, 5, 7)
        val nameOf: (Int) -> String? = { n -> if (n == 5) "李四" else null }
        val config = DrawConfig(start = 1, end = 10, count = 2, poolSource = DrawPoolSource.ROSTER)
        val result = draw(config, seed = 4, roster = roster, nameOf = nameOf)
        assertTrue(result.entries.all { it.number in roster })
        val fiveEntry = result.pool.first { it.number == 5 }
        assertEquals("李四", fiveEntry.name)
        assertNull(result.pool.first { it.number == 1 }.name)
    }

    @Test
    fun `剔除并自动重抽保持条目数`() {
        val result = draw(DrawConfig(start = 1, end = 10, count = 3), seed = 31)
        val removed = result.entries.first()
        val outcome = DrawEngine.removeAndRedraw(result, 0, autoRedraw = true, random = SeededDrawRandom(99))
        assertTrue(outcome is DrawOutcome.Success)
        val next = (outcome as DrawOutcome.Success).result
        assertEquals(3, next.entries.size)
        assertFalse(next.entries.any { it.number == removed.number })
        assertEquals(1, next.log.size)
        assertEquals(removed.number, next.log.first().removed.number)
        assertNotNull(next.log.first().added)
    }

    @Test
    fun `剔除不重抽时少一条且 log 记录未补抽`() {
        val result = draw(DrawConfig(start = 1, end = 10, count = 3), seed = 32)
        val outcome = DrawEngine.removeAndRedraw(result, 1, autoRedraw = false, random = SeededDrawRandom(1))
        val next = (outcome as DrawOutcome.Success).result
        assertEquals(2, next.entries.size)
        assertNull(next.log.single().added)
    }

    @Test
    fun `剔除越界下标报错`() {
        val result = draw(DrawConfig(start = 1, end = 10, count = 3), seed = 33)
        val outcome = DrawEngine.removeAndRedraw(result, 99, autoRedraw = true, random = SeededDrawRandom(1))
        assertEquals(DrawErrorCode.INVALID_INDEX, (outcome as DrawOutcome.Failure).error.code)
    }

    @Test
    fun `重新抽签保持池与个数`() {
        val result = draw(DrawConfig(start = 1, end = 20, count = 5), seed = 41)
        val outcome = DrawEngine.reroll(result, SeededDrawRandom(42))
        val next = (outcome as DrawOutcome.Success).result
        assertEquals(result.pool.map { it.number }, next.pool.map { it.number })
        assertEquals(5, next.entries.size)
    }

    @Test
    fun `候选为空时报池空错误`() {
        val outcome = DrawEngine.drawFromCandidates(DrawConfig(count = 1), emptyList(), SeededDrawRandom(1))
        assertEquals(DrawErrorCode.POOL_EMPTY, (outcome as DrawOutcome.Failure).error.code)
    }

    @Test
    fun `随机引擎边界可靠`() {
        val random = SeededDrawRandom(123)
        repeat(200) {
            val value = random.nextInt(7)
            assertTrue(value in 0 until 7)
        }
    }
}
