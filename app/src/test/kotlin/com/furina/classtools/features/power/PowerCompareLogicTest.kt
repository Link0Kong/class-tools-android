package com.furina.classtools.features.power

import com.furina.classtools.data.power.PowerPoint
import com.furina.classtools.data.power.PowerRange
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerCompareLogicTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Long = LocalDateTime.of(2026, 11, 11, 21, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    /** 从 start 起每小时一个采样，q 线性下降（dropPerHour）。 */
    private fun decliningSeries(start: Long, hours: Int, dropPerHour: Double, startQ: Double = 100.0): List<PowerPoint> =
        (0..hours).map { h -> PowerPoint(q = startQ - dropPerHour * h, t = start + h * 3_600_000L) }

    // ------------------------------------------------------------ 自然周期窗口

    @Test
    fun `24h 窗口今日 0 点到现在 vs 昨日同时刻`() {
        val (current, previous) = PowerCompareLogic.windows(PowerRange.H24, now, zone)
        assertEquals(at(11, 11, 0, 0), current.start)
        assertEquals(now, current.end)
        assertEquals(at(11, 10, 0, 0), previous.start)
        assertEquals(at(11, 10, 21, 0), previous.end)
        assertEquals("今日", current.label)
        assertEquals("昨日", previous.label)
    }

    @Test
    fun `7d 窗口本周一 vs 上周一同一时刻`() {
        val (current, previous) = PowerCompareLogic.windows(PowerRange.D7, now, zone)
        assertEquals(at(11, 9, 0, 0), current.start) // 2026-11-09 周一
        assertEquals(at(11, 2, 0, 0), previous.start)
        assertEquals(at(11, 4, 21, 0), previous.end)
        assertEquals("本周", current.label)
        assertEquals("上周", previous.label)
    }

    @Test
    fun `30d 窗口本月 1 日 vs 上月 1 日同一日同时刻`() {
        val (current, previous) = PowerCompareLogic.windows(PowerRange.D30, now, zone)
        assertEquals(at(11, 1, 0, 0), current.start)
        assertEquals(at(10, 1, 0, 0), previous.start)
        assertEquals(at(10, 11, 21, 0), previous.end)
        assertEquals("本月", current.label)
        assertEquals("上月", previous.label)
    }

    // ------------------------------------------------------------ 覆盖 / 用电

    @Test
    fun `覆盖比例按真实采样跨度计算`() {
        val (current, _) = PowerCompareLogic.windows(PowerRange.H24, now, zone)
        // 21h 窗口中覆盖约 20h → >0.9
        val points = decliningSeries(current.start, hours = 20, dropPerHour = 0.5)
        assertTrue(PowerCompareLogic.coverage(points, current) > 0.9)
        // 只有两个点跨度 2h → 约 0.095
        val sparse = listOf(PowerPoint(10.0, current.start + 0), PowerPoint(9.0, current.start + 2 * 3_600_000L))
        assertTrue(PowerCompareLogic.coverage(sparse, current) < 0.2)
        // 窗口外采样不计
        val outside = listOf(PowerPoint(10.0, current.start - 3_600_000L), PowerPoint(9.0, current.end))
        assertEquals(0.0, PowerCompareLogic.coverage(outside, current), 0.0)
        assertTrue(PowerCompareLogic.coverage(emptyList(), current) == 0.0)
        assertTrue(PowerCompareLogic.coverage(listOf(PowerPoint(1.0, current.start)), current) == 0.0)
    }

    @Test
    fun `用电量为窗口内真实下降和 不插值 忽略上升`() {
        val (current, _) = PowerCompareLogic.windows(PowerRange.H24, now, zone)
        val points = listOf(
            PowerPoint(100.0, current.start + 3_600_000L), // 01:00
            PowerPoint(98.5, current.start + 2 * 3_600_000L), // -1.5
            PowerPoint(99.0, current.start + 3 * 3_600_000L), // 充值上升忽略
            PowerPoint(97.0, current.start + 4 * 3_600_000L), // -2.0
            PowerPoint(96.998, current.start + 5 * 3_600_000L), // 噪声 <0.005 忽略
        )
        val usage = PowerCompareLogic.usageIn(points, current)
        assertEquals(3.5, usage!!, 0.0001)
        assertNull(PowerCompareLogic.usageIn(listOf(PowerPoint(1.0, current.start)), current))
    }

    // ------------------------------------------------------------ 比较

    @Test
    fun `今日与昨日同期比较百分比`() {
        val (current, previous) = PowerCompareLogic.windows(PowerRange.H24, now, zone)
        val history = decliningSeries(current.start, 21, dropPerHour = 1.0) +
            decliningSeries(previous.start, 21, dropPerHour = 0.8, startQ = 90.0)
        val result = PowerCompareLogic.compare(PowerRange.H24, history, now, zone)
        assertTrue(result.available)
        assertEquals("较昨日同期", result.label)
        assertEquals(20.0, result.currentUse!!, 0.0001)
        assertEquals(16.0, result.previousUse!!, 0.0001)
        assertEquals((20.0 - 16.0) / 16.0 * 100.0, result.percent!!, 0.0001)
        assertTrue(result.badgeText!!.startsWith("+"))
        assertEquals("+25.0%", result.badgeText)
        assertTrue(result.title.contains("今日"))
        assertTrue(result.title.contains("昨日"))
    }

    @Test
    fun `当前覆盖不足时不可比较 数据积累中`() {
        val history = listOf(
            PowerPoint(100.0, now - 3_600_000L),
            PowerPoint(99.0, now - 1_800_000L),
        )
        val result = PowerCompareLogic.compare(PowerRange.H24, history, now, zone)
        assertFalse(result.available)
        assertEquals("数据积累中", result.reason)
        assertNull(result.badgeText)
    }

    @Test
    fun `上一周期覆盖不足时不硬凑`() {
        val (current, _) = PowerCompareLogic.windows(PowerRange.H24, now, zone)
        // 只有今天数据（昨天无采样）
        val history = decliningSeries(current.start, 20, dropPerHour = 1.0)
        val result = PowerCompareLogic.compare(PowerRange.H24, history, now, zone)
        assertFalse(result.available)
        assertEquals("上一周期数据不足", result.reason)
        assertEquals(0.0, result.previousCoverage, 0.0)
    }

    @Test
    fun `上一周期无有效下降时不可比较`() {
        val (current, previous) = PowerCompareLogic.windows(PowerRange.H24, now, zone)
        // 昨天全是上升/充电（无下降）
        val history = decliningSeries(current.start, 20, dropPerHour = 1.0) +
            (0..20).map { h -> PowerPoint(50.0 + h, previous.start + h * 3_600_000L) }
        val result = PowerCompareLogic.compare(PowerRange.H24, history, now, zone)
        assertFalse(result.available)
        assertEquals("上一周期无有效用电数据", result.reason)
    }

    @Test
    fun `7d 与 30d 历史不足时自动隐藏`() {
        val (current, _) = PowerCompareLogic.windows(PowerRange.H24, now, zone)
        val history = decliningSeries(current.start, 20, dropPerHour = 1.0)
        assertFalse(PowerCompareLogic.compare(PowerRange.D7, history, now, zone).available)
        assertFalse(PowerCompareLogic.compare(PowerRange.D30, history, now, zone).available)
        val all = PowerCompareLogic.compareAll(history, now, zone)
        assertEquals(3, all.size)
        assertTrue(all.containsKey(PowerRange.H24))
    }

    // ------------------------------------------------------------ 13/16 隐藏 24h

    @Test
    fun `13 与 16 号楼隐藏 24h 其它楼栋显示`() {
        assertTrue(PowerCompareLogic.isSlowBuilding("13号楼"))
        assertTrue(PowerCompareLogic.isSlowBuilding("16号楼"))
        assertFalse(PowerCompareLogic.isSlowBuilding("23号楼A座"))
        assertFalse(PowerCompareLogic.isSlowBuilding(null))
        assertFalse(PowerCompareLogic.showRange(PowerRange.H24, "13号楼"))
        assertFalse(PowerCompareLogic.showRange(PowerRange.H24, "16号楼"))
        assertTrue(PowerCompareLogic.showRange(PowerRange.H24, "23号楼B座"))
        assertTrue(PowerCompareLogic.showRange(PowerRange.D7, "13号楼"))
        assertTrue(PowerCompareLogic.showRange(PowerRange.D30, "16号楼"))
    }

    @Test
    fun `可见区间与默认档回退`() {
        assertEquals(
            listOf(PowerRange.D7, PowerRange.D30),
            PowerCompareLogic.showRange(building = "13号楼"),
        )
        assertEquals(
            listOf(PowerRange.H24, PowerRange.D7, PowerRange.D30),
            PowerCompareLogic.showRange(building = "23号楼A座"),
        )
        assertEquals(PowerRange.D7, PowerCompareLogic.normRange(PowerRange.H24, "13号楼"))
        assertEquals(PowerRange.H24, PowerCompareLogic.normRange(PowerRange.H24, "23号楼A座"))
        assertEquals(PowerRange.D30, PowerCompareLogic.normRange(PowerRange.D30, "16号楼"))
    }

    // ------------------------------------------------------------ 文案

    @Test
    fun `百分比文案与窗口标题`() {
        assertEquals("+12.3%", PowerCompareLogic.formatPercent(12.34))
        assertEquals("-4.5%", PowerCompareLogic.formatPercent(-4.5))
        assertEquals("0%", PowerCompareLogic.formatPercent(Double.NaN))
        val (current, previous) = PowerCompareLogic.windows(PowerRange.H24, now, zone)
        val title = PowerCompareLogic.windowTitle(current, previous, zone)
        assertTrue(title.contains("今日 00:00–21:00"))
        assertTrue(title.contains("昨日 00:00–21:00"))
        assertEquals(0.0, PowerCompareLogic.MIN_COVERAGE - 0.35, 0.0000001)
        assertEquals(0.005, PowerCompareLogic.MIN_DELTA, 0.0)
    }
}
