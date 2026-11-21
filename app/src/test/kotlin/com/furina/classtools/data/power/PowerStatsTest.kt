package com.furina.classtools.data.power

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 电费纯算法单测（与 web POWER / mp utils/power.js 同口径；契约 docs/BACKEND_CONTRACT.md §7.5）。
 */
class PowerStatsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Long = ZonedDateTime.of(2026, 11, 7, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val hour = 3_600_000L

    private fun pp(q: Double, t: Long) = PowerPoint(q = q, t = t)
    private fun row(q: Double) = PowerRow(id = "4-23A---305", label = "23号楼A座305", q = q)

    // ---- 基础 / 低电状态 ----------------------------------------------------

    @Test
    fun normLow_defaultsAndNearestOption() {
        assertEquals(30, PowerStats.normLow(null))
        assertEquals(30, PowerStats.normLow(5))
        assertEquals(100, PowerStats.normLow(95))
        assertEquals(20, PowerStats.normLow(15))
        // 65 → 四舍五入 70 → 无 70 档 → 最近档为 60（首个最小差）
        assertEquals(60, PowerStats.normLow(65))
        assertEquals(10, PowerStats.normLow(10))
    }

    @Test
    fun stateOf_lowWarnOkIdle() {
        assertEquals(PowerState.LOW, PowerStats.stateOf(row(20.0), 30))
        assertEquals(PowerState.WARN, PowerStats.stateOf(row(45.0), 30))
        assertEquals(PowerState.OK, PowerStats.stateOf(row(70.0), 30))
        assertEquals(PowerState.IDLE, PowerStats.stateOf(null, 30))
        assertEquals("低电", PowerStats.stateText(PowerState.LOW))
        assertEquals("偏低", PowerStats.stateText(PowerState.WARN))
        assertEquals("充足", PowerStats.stateText(PowerState.OK))
    }

    @Test
    fun ringArc_estimateDaysAndFallbacks() {
        assertEquals(0f, PowerStats.ringArc(null, null))
        val withoutEstimate = PowerStats.ringArc(row(50.0), null)
        assertEquals(0.04f, withoutEstimate, 0.0001f)
        val valid = PowerStats.ringArc(row(50.0), PowerEstimate(valid = true, days = 15.0))
        assertEquals(0.5f, valid, 0.0001f)
        val clampMax = PowerStats.ringArc(row(50.0), PowerEstimate(valid = true, days = 90.0))
        assertEquals(1f, clampMax, 0.0001f)
    }

    @Test
    fun formatHelpers() {
        assertEquals("—", PowerStats.fmtQ(null))
        assertEquals("35.62", PowerStats.fmtQ(35.62))
        assertEquals("86.4", PowerStats.fmtMain(86.4))
        assertEquals("100", PowerStats.fmtMain(100.0))
        assertEquals("12.3", PowerStats.fmtMain(12.3))
        assertEquals("—", PowerStats.fmtDrop(null))
        assertEquals(1_794_016_800_000L, PowerStats.parseIsoMillis("2026-11-07T10:00:00+08:00"))
        assertNull(PowerStats.parseIsoMillis("bad"))
        assertTrue(PowerStats.medianOf(listOf(1.0)) > 0)
    }

    // ---- 异常扣减 / 区间统计 -------------------------------------------------

    @Test
    fun deductOf_flagsHugeDropOnly() {
        val points = listOf(
            pp(100.0, 0),
            pp(99.5, hour),
            pp(99.0, 2 * hour),
            pp(98.5, 3 * hour),
            pp(98.0, 4 * hour),
            pp(60.0, 5 * hour),   // 大跌 38 度 = 异常扣减（水费/充值后大跌）
            pp(59.5, 6 * hour),
        )
        val deduct = PowerStats.deductOf(points)
        assertEquals(1, deduct.n)
        assertEquals(38.0, deduct.deg, 0.001)
        assertEquals(Math.round(38.0 * PowerStats.ELEC_PRICE * 100) / 100.0, deduct.yuan, 0.001)
        assertTrue(deduct.thr >= 5.0)
    }

    @Test
    fun deriveRange_normalConsumption() {
        val points = (0..12).map { i -> pp(100.0 - i * (10.0 / 12), i * hour) }
        val stats = PowerStats.deriveRange(points, row(90.0))
        assertEquals(13, stats.n)
        assertTrue(stats.valid)
        assertTrue(stats.drop!! > 9.9 && stats.drop!! < 10.1)
        assertEquals(12.0, stats.hours, 0.001)
        assertTrue(stats.days!! > 0)
        assertFalse(stats.recharge)
    }

    @Test
    fun deriveRange_skipsAbnormalAndTracksRecharge() {
        val points = listOf(
            pp(100.0, 0),
            pp(99.5, hour),
            pp(99.0, 2 * hour),
            pp(98.5, 3 * hour),
            pp(98.0, 4 * hour),
            pp(60.0, 5 * hour),    // 异常大跌：不计入 drop
            pp(59.5, 6 * hour),
            pp(80.0, 7 * hour),    // 充值：不计入 drop，记录 lastUp
            pp(79.5, 8 * hour),
        )
        val stats = PowerStats.deriveRange(points, row(79.5))
        assertEquals(9, stats.n)
        // 正常 drop = 0.5*4 + 0.5 + 0.5 = 3.0（60 那次 38 度被剔除；80 上升是充值）
        assertEquals(3.0, stats.drop!!, 0.01)
        assertEquals(1, stats.deduct.n)
        assertTrue(stats.recharge)
        assertNotNull(stats.lastUp)
        assertEquals(80.0, stats.lastUp!!.reset, 0.001)
        assertTrue(stats.valid)
    }

    @Test
    fun deriveRange_invalidWhenTooShortOrTooFew() {
        assertFalse(PowerStats.deriveRange(emptyList(), row(50.0)).valid)
        assertFalse(PowerStats.deriveRange(listOf(pp(50.0, 0)), row(50.0)).valid)
        // 3 小时窗口：不足 6h → 无效
        val short = listOf(pp(50.0, 0), pp(49.0, hour), pp(48.0, 2 * hour), pp(47.0, 3 * hour))
        assertFalse(PowerStats.deriveRange(short, row(47.0)).valid)
    }

    @Test
    fun lastRecharge_usesFullHistoryNotSelectedRange() {
        val points = listOf(
            pp(100.0, now - 10 * 24 * hour),
            pp(95.0, now - 9 * 24 * hour),
            pp(120.0, now - 8 * 24 * hour),   // 充值 +25
            pp(118.0, now - 7 * 24 * hour),
            pp(130.0, now - 2 * 24 * hour),   // 最近充值 +12
            pp(129.0, now - hour),
        )
        val rec = PowerStats.lastRecharge(points)
        assertNotNull(rec)
        assertEquals(now - 2 * 24 * hour, rec!!.t)
        assertEquals(130.0, rec.reset, 0.001)
        assertEquals(12.0, rec.delta, 0.001)

        // 只看 24h 子窗口：没有上升 → 旧口径（区间 lastUp）拿不到；30d 全量能拿到（本次修复目标）。
        assertNull(PowerStats.lastRecharge(points.filter { it.t >= now - 24 * hour }))
        // 下降/持平不算充值
        assertNull(PowerStats.lastRecharge(listOf(pp(130.0, now), pp(129.0, now + hour))))
        assertNull(PowerStats.lastRecharge(listOf(pp(130.0, now), pp(130.0, now + hour))))
    }

    @Test
    fun rateFrom_skipsRechargeSegments() {
        val points = listOf(
            pp(100.0, 0),
            pp(94.0, 6 * hour),    // 6 度 / 6h
            pp(114.0, 12 * hour),  // 充值：整段跳过
            pp(108.0, 18 * hour),  // 6 度 / 6h
        )
        val rate = PowerStats.rateFrom(points)
        assertTrue(rate.recharge)
        assertEquals(24.0, rate.rate!!, 0.001) // used=12 度 / 12h → 24 度/天
    }

    // ---- 估算（estimate） ---------------------------------------------------

    @Test
    fun estimate_requiresMinimumWindowAndUsage() {
        assertFalse(PowerStats.estimate(emptyList(), emptyList(), row(50.0)).valid)
        // 只有 1 个点 → 无效
        assertFalse(PowerStats.estimate(listOf(pp(50.0, 0)), emptyList(), row(50.0)).valid)
        // 窗口够长但真实用电 ≤0.05 → 无效（不允许 0.05 下限伪造天数）
        val tiny = listOf(pp(50.0, 0), pp(49.99, 24 * hour))
        assertFalse(PowerStats.estimate(tiny, emptyList(), row(49.99)).valid)
    }

    @Test
    fun estimate_blends24hAnd7d() {
        // 24h：6 小时下降 3 度 → rate=12/h*24? used=3, hours=6 → 12 度/天
        val pts24 = listOf(pp(100.0, 0), pp(97.0, 6 * hour))
        // 7d：168 小时下降 14 度 → rate=2 度/天
        val pts7 = listOf(pp(100.0, 0), pp(86.0, 168 * hour))
        val estimate = PowerStats.estimate(pts24, pts7, row(60.0))
        assertTrue(estimate.v24)
        assertTrue(estimate.v7)
        assertTrue(estimate.valid)
        assertEquals(0.65 * 12.0 + 0.35 * 2.0, estimate.rate!!, 0.01)
        assertEquals(60.0 / estimate.rate!!, estimate.days!!, 0.01)
    }

    @Test
    fun estimate_appliesRateLowerBound() {
        // 30h 只下降 0.06 度 → rate≈0.048 → 有效但下限 0.05
        val pts24 = listOf(pp(100.0, 0), pp(99.94, 30 * hour))
        val estimate = PowerStats.estimate(pts24, emptyList(), row(60.0))
        assertTrue(estimate.valid)
        assertEquals(0.05, estimate.rate!!, 0.0001)
        assertEquals(1200.0, estimate.days!!, 0.1)
    }

    // ---- 慢更新 / 慢频房 ----------------------------------------------------

    @Test
    fun slowUpdate_detectsNoChangeAndChanges() {
        val flat = (0..23).map { pp(42.5, now - (23 - it) * hour.toLong()) }
        val update = PowerStats.slowUpdate(flat, zone)
        assertTrue(update.slow)
        assertEquals(0, update.changes)
        assertEquals("数据未更新", update.text)

        val oneChange = listOf(
            pp(42.5, now - 2 * hour),
            pp(42.4, now - hour),
            pp(42.4, now),
        )
        val slowOne = PowerStats.slowUpdate(oneChange, zone)
        assertTrue(slowOne.slow)
        assertEquals(1, slowOne.changes)
        assertTrue(slowOne.text.contains("上次变化"))

        val many = (0..4).map { pp(42.5 - it * 0.1, now - (4 - it) * hour) }
        assertFalse(PowerStats.slowUpdate(many, zone).slow)
    }

    @Test
    fun changesInAndEventsOf() {
        val points = listOf(pp(50.0, 0), pp(50.0, hour), pp(49.0, 2 * hour), pp(49.0, 3 * hour))
        assertEquals(1, PowerStats.changesIn(points))
        assertEquals(2, PowerStats.eventsOf(points).size)
    }

    @Test
    fun slowStats_singleSegmentUsesExpectedCycleAndMarksRef() {
        val points = listOf(pp(70.0, now - 10 * 24 * hour), pp(60.0, now))
        val stats = PowerStats.slowStats(points, row(50.0))
        assertTrue(stats.valid)
        assertTrue(stats.ref)
        assertEquals(1, stats.nSeg)
        // 1 段且 dtH=240 ≥ EXPECT_H*1.5 → rate=drop/hours*24 = 10/240*24 = 1.0
        assertEquals(1.0, stats.rate!!, 0.001)
        assertEquals(50.0, stats.days!!, 0.01)
    }

    @Test
    fun slowStats_multipleSegmentsTimeWeightedAndRecharge() {
        val points = listOf(
            pp(70.0, now - 24 * 24 * hour),
            pp(60.0, now - 14 * 24 * hour),  // 10 度 / 10 天
            pp(62.0, now - 9 * 24 * hour),   // 充值
            pp(55.0, now - 1 * 24 * hour),   // 7 度 / 8 天
        )
        val stats = PowerStats.slowStats(points, row(50.0))
        assertTrue(stats.valid)
        assertFalse(stats.ref)
        assertEquals(2, stats.nSeg)
        val expected = (10.0 + 7.0) / (10.0 * 24 + 8.0 * 24) * 24
        assertEquals(expected, stats.rate!!, 0.001)
        assertNotNull(stats.lastRecharge)
        assertEquals(62.0, stats.lastRecharge!!.reset, 0.001)
        assertEquals(2.0, stats.lastRecharge!!.delta, 0.001)
    }

    @Test
    fun slowStats_noSegmentsInvalid() {
        val flat = (0..5).map { pp(50.0, now - (5 - it) * 24 * hour) }
        assertFalse(PowerStats.slowStats(flat, row(50.0)).valid)
    }

    @Test
    fun isSlowRoom_dependsOnBuildingAndChanges() {
        val flat24 = (0..23).map { pp(42.5, now - (23 - it) * hour) }
        assertTrue(PowerStats.isSlowRoom("1-13---301", flat24))
        assertTrue(PowerStats.isSlowRoom("1-16---303", flat24))
        assertFalse(PowerStats.isSlowRoom("4-23A---305", flat24))
        assertFalse(PowerStats.isSlowRoom("unknown", flat24))

        // 24h 变化 ≥6 次 → 自动降级实时口径
        val many = (0..10).map { pp(42.5 - it * 0.1, now - (10 - it) * hour) }
        assertFalse(PowerStats.isSlowRoom("1-13---301", many))
    }

    @Test
    fun noUpdateText_onlyWhenNoChanges() {
        val flat = (0..5).map { pp(50.0, it * hour.toLong()) }
        assertEquals("近 24h 无更新", PowerStats.noUpdateText(PowerRange.H24, flat))
        assertEquals("近 7 天无更新", PowerStats.noUpdateText(PowerRange.D7, flat))
        assertEquals("近 30 天无更新", PowerStats.noUpdateText(PowerRange.D30, flat))
        val changed = listOf(pp(50.0, hour), pp(49.0, 2 * hour))
        assertEquals("", PowerStats.noUpdateText(PowerRange.H24, changed))
    }

    // ---- 趋势 / 游标 --------------------------------------------------------

    @Test
    fun trendLine_emptyAndFlat() {
        assertEquals("未选择宿舍", PowerStats.trendLine(emptyList()).note)
        assertEquals("数据积累中", PowerStats.trendLine(listOf(pp(50.0, hour))).note)
        val flat = PowerStats.trendLine((0..5).map { pp(42.5, it * hour.toLong()) })
        assertFalse(flat.empty)
        assertTrue(flat.flat)
        flat.points.forEach { assertEquals(0.5f, it.y, 0.0001f) }
    }

    @Test
    fun trendLine_descendingByTimeRatio() {
        val points = (0..10).map { i -> pp(100.0 - i, (i + 1) * hour.toLong()) }
        val line = PowerStats.trendLine(points)
        assertFalse(line.empty)
        assertEquals(0f, line.points.first().x, 0.0001f)
        assertEquals(1f, line.points.last().x, 0.0001f)
        assertEquals(0f, line.points.first().y, 0.0001f)
        assertEquals(1f, line.points.last().y, 0.0001f)
        assertEquals(11, line.count)
    }

    @Test
    fun trendLine_downsamplesToCap() {
        val points = (0..1000).map { i -> pp(100.0 - i * 0.01, i * 60_000L) }
        val line = PowerStats.trendLine(points, maxPoints = 240)
        assertTrue(line.count <= 240)
        assertFalse(line.empty)
    }

    @Test
    fun nearestPoint_hitsClosestRealSample() {
        val line = PowerStats.trendLine((0..10).map { i -> pp(100.0 - i, i * hour.toLong()) })
        val hit = PowerStats.nearestPoint(line.points, 0.52f)
        assertNotNull(hit)
        assertTrue(hit!!.index in 4..6)
        assertEquals(line.points[hit.index], hit.point)
        assertNull(PowerStats.nearestPoint(emptyList(), 0.5f))
    }

    @Test
    fun cursorLabelsAndXTicks() {
        val t = ZonedDateTime.of(2026, 11, 7, 9, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("09:30", PowerStats.cursorLabel(t, PowerRange.H24, zone))
        assertEquals("11-07 09:30", PowerStats.cursorLabel(t, PowerRange.D7, zone))
        assertEquals("09:30", PowerStats.xLabel(t, PowerRange.H24, zone))
        assertEquals("11-07", PowerStats.xLabel(t, PowerRange.D30, zone))

        val line = PowerStats.trendLine((0..10).map { i -> pp(100.0 - i, t - (10 - i) * hour) })
        val ticks = PowerStats.xTicks(line.points, PowerRange.H24, zone)
        assertEquals(3, ticks.size)
        assertEquals(3, ticks.map { it.label }.distinct().size)
    }

    @Test
    fun stepSegmentsAndChangeDots() {
        val line = PowerStats.trendLine((0..4).map { i -> pp(100.0 - i, i * hour.toLong()) })
        val steps = PowerStats.stepSegments(line.points)
        assertTrue(steps.size >= line.points.size)
        val dots = PowerStats.changeDots(line.points)
        assertTrue(dots.size >= line.points.size - 1)
    }

    // ---- 趋势充值折算（web/CHANGELOG.md 增量 53 ②） -------------------------

    @Test
    fun foldRecharge_addsBackJumpsAndKeepsRealQ() {
        // 注意：cleanPts 过滤 t<=0，采样时间从 1h 起（生产时间戳恒 >0）
        val points = listOf(
            pp(100.0, hour),
            pp(99.0, 2 * hour),
            pp(119.0, 3 * hour), // 充值 +20（>0.5 跳变）
            pp(118.0, 4 * hour),
            pp(117.0, 5 * hour),
        )
        val fold = PowerStats.foldRecharge(points)
        assertEquals(20.0, fold.total, 0.0001)
        assertEquals(1, fold.count)
        assertEquals(3 * hour, fold.marks.single().t)
        assertEquals(20.0, fold.marks.single().delta, 0.0001)
        // 绘图值 = 真实剩余 - 累计充值：连续下降
        assertEquals(
            listOf(100.0, 99.0, 99.0, 98.0, 97.0),
            fold.values.map { Math.round(it * 100.0) / 100.0 },
        )

        val line = PowerStats.trendLine(points)
        assertFalse(line.empty)
        assertEquals(20.0, line.foldedRecharge, 0.0001)
        assertEquals(1, line.rechargeMarks.size)
        // 真实剩余保留在 points.q（游标/图例「当前」用真实值）
        assertEquals(listOf(100.0, 99.0, 119.0, 118.0, 117.0), line.points.map { it.q })
        // y 轴按折算范围：起点顶部、终点底部
        assertEquals(0f, line.points.first().y, 0.0001f)
        assertEquals(1f, line.points.last().y, 0.0001f)
        assertEquals(100.0, line.hi!!, 0.0001)
        assertEquals(97.0, line.lo!!, 0.0001)
        assertEquals(97.0, line.min!!, 0.0001)
    }

    @Test
    fun foldRecharge_ignoresSmallJumps() {
        // 0.4 度 ≤ 0.5 阈值：按噪声处理，不折算
        val points = listOf(pp(100.0, hour), pp(100.4, 2 * hour), pp(99.0, 3 * hour))
        val fold = PowerStats.foldRecharge(points)
        assertEquals(0.0, fold.total, 0.0)
        assertTrue(fold.marks.isEmpty())
        val line = PowerStats.trendLine(points)
        assertEquals(0.0, line.foldedRecharge, 0.0)
        assertTrue(line.rechargeMarks.isEmpty())
    }

    @Test
    fun foldRecharge_downsamplesWithoutLosingMarks() {
        // 1001 个采样（每 10 分钟），第 500 个点充值 +30 → 抽稀到 200 点仍保留充值标记
        val points = (0..1000).map { i ->
            val q = 100.0 - i * 0.01 + if (i >= 500) 30.01 else 0.0
            pp(q, (i + 1) * 600_000L)
        }
        val line = PowerStats.trendLine(points, maxPoints = 200)
        assertTrue(line.count <= 200)
        assertEquals(30.0, line.foldedRecharge, 0.001)
        assertEquals(1, line.rechargeMarks.size)
        // 折算后曲线连续下降 → y 单调不减（不出现充值上跳）
        line.points.zipWithNext().forEach { (a, b) ->
            assertTrue("y 应单调不减：${a.y} -> ${b.y}", b.y >= a.y - 0.001f)
        }
    }

    // ---- tiles / 计费 -------------------------------------------------------

    @Test
    fun tilesOf_realtimeAndSlowRoomRules() {
        val row = row(50.0)
        val estimate = PowerEstimate(valid = true, rate = 1.0, days = 20.0)
        val rangeStats = PowerStats.deriveRange(
            listOf(pp(60.0, 0), pp(55.0, 12 * hour)),
            row,
        )
        // 30d 跨区间最近充值：+20 度（充值后余额 80），不应显示 80.00 余额
        val crossRange = RechargePoint(t = now - 2 * 24 * hour, reset = 80.0, delta = 20.0)
        val tiles = PowerStats.tilesOf(
            row = row,
            estimate = estimate,
            rangeStats = rangeStats,
            range = PowerRange.H24,
            low = 30,
            slowUpdate = SlowUpdate(slow = false),
            lastRecharge = crossRange,
            zone = zone,
        )
        assertEquals(4, tiles.size)
        assertEquals("估算可用", tiles[0].label)
        assertEquals("20.0", tiles[0].value)
        assertEquals("低电阈值", tiles[2].label)
        assertTrue(tiles[2].clickable)
        assertEquals("上次充值", tiles[3].label)
        assertEquals("+20.00", tiles[3].value)
        assertEquals("度", tiles[3].unit)
        assertEquals("11-05 10:00", tiles[3].sub)

        // 未传跨区间结果时回退区间 lastUp，仍显示充值量（+10）
        val rangeWithRecharge = PowerStats.deriveRange(
            listOf(pp(50.0, now - 12 * hour), pp(60.0, now - 6 * hour), pp(59.0, now)),
            row(59.0),
        )
        val fallback = PowerStats.tilesOf(
            row = row(59.0),
            estimate = estimate,
            rangeStats = rangeWithRecharge,
            range = PowerRange.H24,
            low = 30,
            slowUpdate = SlowUpdate(slow = false),
            lastRecharge = null,
            zone = zone,
        )
        assertEquals("+10.00", fallback[3].value)

        // 慢频房回退 estimate.lastRecharge（30d slowStats 变化点）
        val slowEstimate = PowerEstimate(
            valid = true,
            rate = 1.0,
            days = 50.0,
            slow = true,
            ref = true,
            lastRecharge = RechargePoint(t = now - 9 * 24 * hour, reset = 62.0, delta = 2.0),
        )
        val slowTiles = PowerStats.tilesOf(
            row = null,
            estimate = slowEstimate,
            rangeStats = null,
            range = PowerRange.D30,
            low = 30,
            slowUpdate = SlowUpdate(slow = true),
            zone = zone,
        )
        assertTrue(slowTiles[0].ref) // 只有 est.valid && est.ref 才显示参考
        assertEquals("—", slowTiles[1].value)
        assertEquals("+2.00", slowTiles[3].value)
        assertEquals("10-29 10:00", slowTiles[3].sub)
    }

    @Test
    fun billOf_formatsAndDeductions() {
        val empty = PowerStats.billOf(null)
        assertEquals("—", empty.feeText)
        assertEquals(4, empty.persons)

        val rangeStats = PowerStats.deriveRange(
            listOf(pp(100.0, 0), pp(90.0, 24 * hour)),
            row(90.0),
        )
        val bill = PowerStats.billOf(rangeStats)
        assertTrue(bill.feeText.startsWith("≈ ¥"))
        assertTrue(bill.freeText.contains("5 度电/人/月"))
        assertTrue(bill.waterText.contains("3.03 元/吨"))
    }
}




