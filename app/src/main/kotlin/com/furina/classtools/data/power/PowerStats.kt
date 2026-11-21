package com.furina.classtools.data.power

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * 电费纯算法（逐函数对齐 `mp-class-tools/utils/power.js` 与网页 `POWER` 模块；
 * 契约 docs/BACKEND_CONTRACT.md §7.5 + web/AGENTS.md §4b-8/11/16/17）。
 *
 * 全部纯函数、无 Android 依赖；时间相关函数可注入 [ZoneId]（默认系统时区），可单测。
 *
 * 口径速查：
 * - 区间统计：跳过充值跳变；异常扣减（水费扣除/大跌）不计入用电；
 * - 估算：24h/7d 各自有效 → rate=0.65*r24+0.35*r7（仅一档用该档）；rate 下限 0.05 只作用于有效结果；
 * - 慢频房（13/16 号楼，24h 变化 <6 次）：30d 真实变化点分段统计 slowStats；
 * - 慢更新：24h 相邻 |Δq|>0.005 的变化次数 ≤2 → 「数据更新较慢 / 数据未更新」；
 * - 上次充值：取 30d 全量历史（=云端保留上限）最后一次电量上升的 `delta`（充值量），不随所选区间变化。
 */
object PowerStats {

    // ---- 常量（与 web/mp 同名同值）------------------------------------------------

    /** 默认低电阈值。 */
    const val LOW: Int = 30

    /** 低电阈值可调档位（无 70/90）。 */
    val LOW_OPTIONS: List<Int> = listOf(10, 20, 30, 40, 50, 60, 80, 100)

    /** 电量环参考天数：估算可用 30 天 = 满环。 */
    const val ARC_DAYS: Double = 30.0
    const val ARC_MIN: Double = 0.02
    const val ARC_FALLBACK: Double = 0.04

    /** 慢更新判定阈值：相邻 |Δq| > CH 计一次变化。 */
    const val SLOW_DIFF: Double = 0.005

    /**
     * 趋势「充值跳变」阈值（对齐网页 `POWER.renderTrend` 的 `dq>0.5`）：
     * 区间内相邻采样上升超过该值视为充值/修正，绘图值扣除累计充值后显示。
     */
    const val RECHARGE_JUMP: Double = 0.5

    /** 慢频统计常量（power_cadence_spec.md §1）。 */
    const val CH: Double = 0.005
    const val MIN_SEG_H: Double = 6.0
    const val EXPECT_H: Double = 24.0
    const val SLOW_COVER_CHANGES: Int = 6
    val SLOW_BUILDINGS: List<String> = listOf("13号楼", "16号楼")

    /** 趋势降采样上限（总数 / 分区间）。 */
    const val TREND_MAX: Int = 240
    val TREND_LIMIT: Map<String, Int> = mapOf("24h" to 240, "7d" to 170, "30d" to 240)

    /** 计费常量（与水量模块 RULES 同源：电 0.58 元/度、水 3.03 元/吨、免费 5 度+2 吨/人/月）。 */
    const val ELEC_PRICE: Double = 0.58
    const val WATER_PRICE: Double = 3.03
    const val ELEC_FREE_PER_PERSON: Int = 5
    const val WATER_FREE_PER_PERSON: Int = 2
    const val DEFAULT_PERSONS: Int = 4

    private val RR = Locale.CHINA

    // ---- 基础工具 -----------------------------------------------------------------

    fun round2(v: Double): Double = if (!v.isFinite()) 0.0 else (v * 100).roundToLong() / 100.0

    fun fmtQ(q: Double?): String =
        if (q == null || !q.isFinite()) "—" else String.format(RR, "%.2f", q)

    /** 大号电量（网站 fmtMain 同口径：≥100 取整，否则 1 位小数）。 */
    fun fmtMain(q: Double?): String {
        if (q == null || !q.isFinite()) return "—"
        return if (q >= 100) q.roundToLong().toString() else String.format(RR, "%.1f", q)
    }

    fun fmtDrop(v: Double?): String = if (v == null || !v.isFinite()) "—" else String.format(RR, "%.2f", v)

    fun fmtRate(rate: Double?): String = if (rate == null || !rate.isFinite()) "—" else String.format(RR, "%.1f", rate)

    /** 解析 ISO timestamptz（`2026-11-07T03:01:00.123+00:00`）→ epoch ms；失败 null。 */
    fun parseIsoMillis(iso: String?): Long? {
        val text = iso?.trim().orEmpty()
        if (text.isEmpty()) return null
        runCatching { return OffsetDateTime.parse(text).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(text).toEpochMilli() }
        return null
    }

    /** ISO → 本地「MM-dd HH:mm」（网页/小程序 updText 同口径）；无效返回 ""。 */
    fun updText(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String {
        val millis = parseIsoMillis(iso) ?: return ""
        return updText(millis, zone)
    }

    fun updText(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val dt = Instant.ofEpochMilli(millis).atZone(zone)
        return String.format(RR, "%02d-%02d %02d:%02d", dt.monthValue, dt.dayOfMonth, dt.hour, dt.minute)
    }

    /** 中位数（空 → 0；非法值剔除）。 */
    fun medianOf(values: List<Double>): Double {
        val list = values.filter { it.isFinite() }.sorted()
        if (list.isEmpty()) return 0.0
        val mid = list.size / 2
        return if (list.size % 2 == 1) list[mid] else (list[mid - 1] + list[mid]) / 2.0
    }

    // ---- 低电阈值 / 状态 ----------------------------------------------------------

    /** 归一到合法档位：非法值 → 30；否则取最近档（四舍五入到 10 后找最近合法档）。 */
    fun normLow(value: Int?): Int {
        val n = value ?: return LOW
        if (n < 10 || n > 100) return LOW
        val r = Math.round(n / 10.0) * 10
        return LOW_OPTIONS.minByOrNull { abs(it - r) } ?: LOW
    }

    fun stateOf(row: PowerRow?, low: Int?): PowerState {
        val q = row?.q ?: return PowerState.IDLE
        val l = normLow(low)
        return when {
            q < l -> PowerState.LOW
            q < l * 2 -> PowerState.WARN
            else -> PowerState.OK
        }
    }

    fun stateText(state: PowerState): String = when (state) {
        PowerState.LOW -> "低电"
        PowerState.WARN -> "偏低"
        PowerState.OK -> "充足"
        PowerState.IDLE -> "未选择"
    }

    fun isLow(row: PowerRow?, low: Int?): Boolean {
        val q = row?.q ?: return false
        return q < normLow(low)
    }

    /** 电量环填充比例 = estimate 可用天数 / 30（valid 时 clamp 到 [0.02,1]，否则 0.04 小段；未选择 0）。 */
    fun ringArc(row: PowerRow?, estimate: PowerEstimate?): Float {
        if (row == null) return 0f
        if (estimate != null && estimate.valid && estimate.days != null && estimate.days.isFinite()) {
            return max(ARC_MIN, min(1.0, estimate.days / ARC_DAYS)).toFloat()
        }
        return ARC_FALLBACK.toFloat()
    }

    /** 低电提示条文案：剩 X 度 [· 约 Y 天]。 */
    fun lowText(row: PowerRow?, estimate: PowerEstimate?): String {
        val q = row?.q ?: return "—"
        var s = "剩 ${fmtQ(q)} 度"
        if (estimate != null && estimate.valid && estimate.days != null) {
            s += " · 约 " + daysMain(estimate) + " 天"
        }
        return s
    }

    // ---- 异常扣减 / 区间统计 / 估算 -------------------------------------------------

    /** 异常扣减识别：U=正常单段用电中位数；候选 dq > max(5, 4U)。 */
    fun deductOf(points: List<PowerPoint>): DeductInfo {
        val list = points.filter { it.q.isFinite() }
        val ups = ArrayList<Double>()
        for (i in 1 until list.size) {
            val dq = list[i - 1].q - list[i].q
            if (dq > 0.05) ups.add(dq)
        }
        val u = medianOf(ups)
        val thr = max(5.0, 4 * u)
        var n = 0
        var deg = 0.0
        for (i in 1 until list.size) {
            val dq = list[i - 1].q - list[i].q
            if (dq > 0.05 && dq > thr) {
                n += 1
                deg += dq
            }
        }
        return DeductInfo(
            n = n,
            deg = round2(deg),
            yuan = round2(deg * ELEC_PRICE),
            waterN = 0,
            thr = round2(thr),
            u = round2(u),
        )
    }

    /** 区间统计（deriveRange）：累计下降（跳过充值上升/异常扣减段）、窗口小时、日均、可用天数。 */
    fun deriveRange(points: List<PowerPoint>, row: PowerRow?): RangeStats {
        val list = points.filter { it.q.isFinite() }
        val cur = row?.q
        val deduct = deductOf(list)
        if (list.isEmpty()) return RangeStats(deduct = deduct)
        val first = list.first()
        val last = list.last()
        var hours = 0.0
        var drop = 0.0
        var recharge = false
        var lastUp: RechargePoint? = null
        var interval: Double? = null
        var perDay: Double? = null
        var days: Double? = null
        if (list.size >= 2) {
            hours = (last.t - first.t) / 3_600_000.0
            for (i in 1 until list.size) {
                val diff = list[i].q - list[i - 1].q
                if (diff < -0.005) {
                    val dq = -diff
                    if (dq > deduct.thr) continue // 异常扣减段：不计入用电
                    drop += dq
                }
                if (diff > 0.05) {
                    recharge = true
                    lastUp = RechargePoint(
                        t = list[i].t,
                        reset = list[i].q,
                        delta = round2(diff),
                    )
                }
            }
            interval = if (hours > 0) hours / (list.size - 1) else null
            if (hours > 0.15 && drop > 0.005) {
                perDay = drop / hours * 24
                if (cur != null && perDay > 0.01) days = cur / perDay
            }
        }
        var valid = list.size >= 2 && hours >= 6 && days != null && days.isFinite() && days > 0
        if (valid && days!! > 999) days = 999.0
        return RangeStats(
            n = list.size,
            start = first.q,
            end = last.q,
            drop = round2(drop),
            hours = hours,
            perDay = perDay,
            days = days,
            interval = interval,
            recharge = recharge,
            lastUp = lastUp,
            valid = valid,
            deduct = deduct,
        )
    }

    /** 单档速率统计：dq>0.05 正常用电；dq<-0.05 充值跳变整段跳过；其余只计时长。 */
    fun rateFrom(points: List<PowerPoint>): RateStats {
        val list = points.filter { it.q.isFinite() }
        val deduct = deductOf(list)
        if (list.size < 2) return RateStats(n = list.size, deductN = deduct.n)
        var used = 0.0
        var hours = 0.0
        var recharge = false
        for (i in 1 until list.size) {
            val dt = (list[i].t - list[i - 1].t) / 3_600_000.0
            if (!(dt > 0)) continue
            val dq = list[i - 1].q - list[i].q
            when {
                dq > 0.05 && dq <= deduct.thr -> {
                    used += dq
                    hours += dt
                }
                dq < -0.05 -> recharge = true
                else -> hours += dt
            }
        }
        val span = (list.last().t - list.first().t) / 3_600_000.0
        return RateStats(
            rate = if (hours > 0) used / hours * 24 else null,
            used = used,
            hours = hours,
            span = span,
            n = list.size,
            recharge = recharge,
            deductN = deduct.n,
        )
    }

    /** 估算可用天数（实时口径）。窗口有效条件：n≥2 且 span≥6h 且真实用电 >0.05。 */
    fun estimate(pts24: List<PowerPoint>, pts7: List<PowerPoint>, row: PowerRow?): PowerEstimate {
        val r24 = rateFrom(pts24)
        val r7 = rateFrom(pts7)
        val v24 = r24.n >= 2 && r24.span >= 6 && r24.rate != null && r24.rate.isFinite() && r24.used > 0.05
        val v7 = r7.n >= 2 && r7.span >= 6 && r7.rate != null && r7.rate.isFinite() && r7.used > 0.05
        var rate: Double? = when {
            v24 && v7 -> 0.65 * r24.rate!! + 0.35 * r7.rate!!
            v24 -> r24.rate
            v7 -> r7.rate
            else -> null
        }
        if (rate != null) {
            if (!rate.isFinite() || rate < 0) rate = 0.0
            rate = max(rate, 0.05)
        }
        val q = row?.q
        val days = if (rate != null && q != null) q / rate!! else null
        val valid = rate != null && days != null && days.isFinite() && days >= 0
        return PowerEstimate(
            valid = valid,
            rate = rate,
            days = days,
            q = q,
            slow = false,
            ref = false,
            v24 = v24,
            v7 = v7,
            r24 = r24,
            r7 = r7,
        )
    }

    fun daysMain(estimate: PowerEstimate?): String {
        if (estimate == null || !estimate.valid || estimate.days == null) return "—"
        return if (estimate.days >= 30) "≥30" else String.format(RR, "%.1f", estimate.days)
    }

    fun daysText(estimate: PowerEstimate?): String {
        if (estimate == null || !estimate.valid || estimate.days == null) return "数据积累中"
        return if (estimate.days >= 30) "≥30 天" else String.format(RR, "≈ %.1f 天", estimate.days)
    }

    // ---- 慢更新 / 慢频（13/16 号楼日更） -----------------------------------------

    /** 慢更新提示（24h 窗口；|Δq|>0.005 计一次变化；≤2 次视为慢）。 */
    fun slowUpdate(hist24: List<PowerPoint>, zone: ZoneId = ZoneId.systemDefault()): SlowUpdate {
        val list = cleanPts(hist24)
        val out = SlowUpdate()
        if (list.size < 2) return out
        var changes = 0
        var lastAt: Long? = null
        var lastRecharge: RechargePoint? = null
        for (i in 1 until list.size) {
            if (abs(list[i].q - list[i - 1].q) > SLOW_DIFF) {
                changes += 1
                lastAt = list[i].t
            }
            if (list[i - 1].q - list[i].q < -0.05) {
                lastRecharge = RechargePoint(
                    t = list[i].t,
                    reset = list[i].q,
                    delta = round2(list[i].q - list[i - 1].q),
                )
            }
        }
        val slow = changes <= 2
        val text = if (!slow) "" else {
            val at = lastAt
            if (at != null) "数据更新较慢 · 上次变化 ${updText(at, zone)}" else "数据未更新"
        }
        return SlowUpdate(
            changes = changes,
            slow = slow,
            lastAt = lastAt,
            text = text,
            lastChange = lastAt,
            lastRecharge = lastRecharge,
        )
    }

    /** 清洗采样：只留合法 {q,t} 并按时间升序（不插值、不改采样）。 */
    fun cleanPts(points: List<PowerPoint>): List<PowerPoint> =
        points.filter { it.q.isFinite() && it.t > 0 }.sortedBy { it.t }

    /**
     * 最近一次充值/上调：按时间升序扫描，取最后一次相邻上升（`Δq > [minDelta]`）。
     *
     * 与 [deriveRange] 的 `lastUp` 不同，本函数**不限制所选区间**：调用方传入 30d 全量历史
     * （云端历史保留 30 天，见 `scripts/sync_power_cloud.py` 的 `HISTORY_KEEP_DAYS`），
     * 因此用户切到 24h/7d 时「上次充值」仍能显示最近一次充值。
     *
     * @return [RechargePoint.delta] = 本次充值量（度），[RechargePoint.reset] = 充值后余额。
     */
    fun lastRecharge(points: List<PowerPoint>, minDelta: Double = 0.05): RechargePoint? {
        val list = cleanPts(points)
        var out: RechargePoint? = null
        for (i in 1 until list.size) {
            val diff = list[i].q - list[i - 1].q
            if (diff.isFinite() && diff > minDelta) {
                out = RechargePoint(t = list[i].t, reset = list[i].q, delta = round2(diff))
            }
        }
        return out
    }

    /** 窗口内相邻 |Δq| > CH 的次数。 */
    fun changesIn(points: List<PowerPoint>): Int {
        val list = cleanPts(points)
        var n = 0
        for (i in 1 until list.size) if (abs(list[i].q - list[i - 1].q) > CH) n += 1
        return n
    }

    /** 真实变化点序列：从首点开始，保留与「上一个保留点」|Δq|>CH 的点。 */
    fun eventsOf(points: List<PowerPoint>): List<PowerPoint> {
        val list = cleanPts(points)
        if (list.isEmpty()) return emptyList()
        val ev = ArrayList<PowerPoint>(list.size)
        ev.add(list.first())
        for (i in 1 until list.size) {
            if (abs(list[i].q - ev.last().q) > CH) ev.add(list[i])
        }
        return ev
    }

    /** 慢频统计（30d 真实变化点分段）：1 段按 24h 折算（ref=true）；≥2 段时间加权；0 段无效。 */
    fun slowStats(hist30: List<PowerPoint>, row: PowerRow?): SlowStats {
        val q = row?.q
        val ev = eventsOf(hist30)
        val out = SlowStats(events = ev.size, changes = changesIn(hist30))
        var lastChange: Long? = null
        var lastRecharge: RechargePoint? = null
        if (ev.size >= 2) lastChange = ev.last().t
        val candidates = ArrayList<Pair<Double, Double>>() // drop to hours
        for (i in 1 until ev.size) {
            val dq = ev[i - 1].q - ev[i].q
            val dtH = (ev[i].t - ev[i - 1].t) / 3_600_000.0
            if (ev[i - 1].q - ev[i].q < -0.05) {
                lastRecharge = RechargePoint(
                    t = ev[i].t,
                    reset = ev[i].q,
                    delta = round2(ev[i].q - ev[i - 1].q),
                )
                continue
            }
            if (dq > 0.05 && dtH >= MIN_SEG_H) candidates.add(dq to dtH)
        }
        val base = medianOf(candidates.map { it.first })
        val thr = max(5.0, 4 * base)
        val segs = candidates.filter { it.first <= thr }
        var rate: Double? = null
        var ref = false
        when {
            segs.size == 1 -> {
                val (drop, hours) = segs[0]
                rate = if (hours < EXPECT_H * 1.5) drop / EXPECT_H * 24 else drop / hours * 24
                ref = true
            }
            segs.size >= 2 -> {
                var drop = 0.0
                var hours = 0.0
                segs.forEach {
                    drop += it.first
                    hours += it.second
                }
                if (hours > 0) rate = drop / hours * 24
            }
        }
        var valid = false
        var days: Double? = null
        if (rate != null && rate.isFinite() && rate > 0) {
            if (q != null) days = q / rate!!
            valid = days != null && days!!.isFinite()
            if (!valid) rate = null
        }
        return SlowStats(
            valid = valid,
            rate = rate,
            days = days,
            ref = valid && ref,
            nSeg = segs.size,
            events = ev.size,
            changes = out.changes,
            lastChange = lastChange,
            lastRecharge = lastRecharge,
        )
    }

    /** 慢频估算（与 estimate 返回同口径 + slow 标记）。 */
    fun estimateSlow(hist30: List<PowerPoint>, row: PowerRow?): PowerEstimate {
        val s = slowStats(hist30, row)
        return PowerEstimate(
            valid = s.valid,
            rate = s.rate,
            days = s.days,
            q = row?.q,
            slow = true,
            ref = s.ref,
            nSeg = s.nSeg,
            changes = s.changes,
            lastChange = s.lastChange,
            lastRecharge = s.lastRecharge,
        )
    }

    fun isSlowBuilding(building: String?): Boolean =
        SLOW_BUILDINGS.contains(building?.trim().orEmpty())

    /** 慢频判定：楼栋 ∈ 13/16 号楼 且 24h 变化 <6 次（网关提速后自动降级实时口径）。 */
    fun isSlowRoom(rv: String?, pts24: List<PowerPoint>): Boolean {
        if (!isSlowBuilding(PowerRooms.buildingOf(rv))) return false
        return changesIn(pts24) < SLOW_COVER_CHANGES
    }

    /** 所选区间内变化次数为 0 → 无更新提示文案；否则 ""（不显示）。 */
    fun noUpdateText(range: PowerRange, points: List<PowerPoint>): String {
        if (changesIn(points) > 0) return ""
        return when (range) {
            PowerRange.H24 -> "近 24h 无更新"
            PowerRange.D7 -> "近 7 天无更新"
            PowerRange.D30 -> "近 30 天无更新"
        }
    }

    // ---- 趋势折线 / 游标 ----------------------------------------------------------

    /**
     * 趋势「已折算充值」：区间内相邻采样 `Δq > [RECHARGE_JUMP]`（0.5 度）视为充值/修正，
     * 绘图值 = `真实剩余 - 累计充值`（连续下降）；真实剩余仍保留在 [PowerPoint.q]。
     *
     * 口径对齐网页 `POWER.renderTrend`（web/CHANGELOG.md 增量 53 ②）：
     * 只做「加回累计充值」，不插值、不改真实采样；`≤0.5` 度的小幅上升不算充值（噪声/网关抖动）。
     */
    fun foldRecharge(points: List<PowerPoint>): RechargeFold {
        val list = cleanPts(points)
        if (list.isEmpty()) return RechargeFold()
        val values = ArrayList<Double>(list.size)
        val marks = ArrayList<TrendRechargeMark>()
        var cumRecharge = 0.0
        val t0 = list.first().t
        val tSpan = max(1L, list.last().t - t0)
        values.add(list.first().q)
        for (i in 1 until list.size) {
            val delta = list[i].q - list[i - 1].q
            if (delta > RECHARGE_JUMP) {
                cumRecharge += delta
                marks.add(
                    TrendRechargeMark(
                        t = list[i].t,
                        x = ((list[i].t - t0).toDouble() / tSpan).toFloat().coerceIn(0f, 1f),
                        delta = round2(delta),
                    ),
                )
            }
            values.add(list[i].q - cumRecharge)
        }
        return RechargeFold(
            values = values,
            marks = marks,
            total = round2(cumRecharge),
            count = marks.size,
        )
    }

    /**
     * 趋势折线纯数据：按真实时间比例铺满（x），y=0 顶部/1 底部；全平垂直居中。
     *
     * 2026-11-15 对齐网页「充值后趋势可读」：区间内有 `Δq>0.5` 充值跳变时，
     * [TrendLine.points] 的 y 按**折算剩余**计算（连续下降），[TrendPoint.q] 仍是真实剩余；
     * y 轴刻度（[TrendLine.hi]/[TrendLine.mid]/[TrendLine.lo]）也按折算范围，充值位置见
     * [TrendLine.rechargeMarks]（竖虚线）。
     */
    fun trendLine(history: List<PowerPoint>, maxPoints: Int = TREND_MAX): TrendLine {
        val pts = cleanPts(history)
        if (pts.isEmpty()) return TrendLine(empty = true, note = "未选择宿舍")
        if (pts.size < 2) return TrendLine(empty = true, note = "数据积累中")
        val fold = foldRecharge(pts)
        val cap = max(2, maxPoints)
        // 先按真实时间等距抽稀下标（首/尾必保留），再取折算值：抽稀不改变折算口径。
        val indices: List<Int> = if (pts.size > cap) {
            val sampled = ArrayList<Int>(cap)
            for (i in 0 until cap) {
                val idx = (i.toDouble() * (pts.size - 1) / (cap - 1)).roundToLong().toInt()
                    .coerceIn(0, pts.size - 1)
                if (sampled.isEmpty() || pts[idx].t != pts[sampled.last()].t) sampled.add(idx)
            }
            sampled
        } else {
            pts.indices.toList()
        }
        val list = indices.map { pts[it] }
        val plotValues = indices.map { fold.values[it] }
        var lo = plotValues.first()
        var hi = plotValues.first()
        plotValues.forEach {
            if (it < lo) lo = it
            if (it > hi) hi = it
        }
        val flat = (hi - lo) < CH
        if (hi - lo < 0.05) hi = lo + 0.05
        val span = hi - lo
        val t0 = list.first().t
        val t1 = list.last().t
        val tSpan = max(1L, t1 - t0)
        val outPoints = list.mapIndexed { index, p ->
            TrendPoint(
                x = ((p.t - t0).toDouble() / tSpan).toFloat().coerceIn(0f, 1f),
                y = if (flat) 0.5f else ((hi - plotValues[index]) / span).toFloat().coerceIn(0f, 1f),
                q = p.q, // 真实剩余（游标/图例「当前」展示用，不随折算变化）
                t = p.t,
            )
        }
        val marks = fold.marks.map { mark ->
            mark.copy(x = ((mark.t - t0).toDouble() / tSpan).toFloat().coerceIn(0f, 1f))
        }
        return TrendLine(
            empty = false,
            note = "",
            count = list.size,
            points = outPoints,
            hi = hi,
            mid = (hi + lo) / 2,
            lo = lo,
            min = lo,
            max = hi,
            flat = flat,
            foldedRecharge = fold.total,
            rechargeMarks = marks,
        )
    }

    fun trendLimit(range: PowerRange): Int = TREND_LIMIT[range.key] ?: TREND_MAX

    /** 按 x 比例（0..1，真实时间比例）找最近的真实采样点。 */
    fun nearestPoint(points: List<TrendPoint>, frac: Float): CursorHit? {
        if (points.size < 2) return null
        val f = frac.coerceIn(0f, 1f)
        var best = 0
        var bestDiff = Float.MAX_VALUE
        points.forEachIndexed { index, point ->
            val diff = abs(point.x - f)
            if (diff < bestDiff) {
                bestDiff = diff
                best = index
            }
        }
        return CursorHit(best, points[best])
    }

    /** 游标气泡文案：24h HH:mm；7d/30d MM-dd HH:mm。 */
    fun cursorLabel(t: Long, range: PowerRange, zone: ZoneId = ZoneId.systemDefault()): String {
        val dt = Instant.ofEpochMilli(t).atZone(zone)
        return if (range == PowerRange.H24) {
            String.format(RR, "%02d:%02d", dt.hour, dt.minute)
        } else {
            String.format(RR, "%02d-%02d %02d:%02d", dt.monthValue, dt.dayOfMonth, dt.hour, dt.minute)
        }
    }

    /** x 轴刻度文案：24h HH:mm；7d/30d MM-dd。 */
    fun xLabel(t: Long, range: PowerRange, zone: ZoneId = ZoneId.systemDefault()): String {
        val dt = Instant.ofEpochMilli(t).atZone(zone)
        return if (range == PowerRange.H24) {
            String.format(RR, "%02d:%02d", dt.hour, dt.minute)
        } else {
            String.format(RR, "%02d-%02d", dt.monthValue, dt.dayOfMonth)
        }
    }

    /** x 轴刻度（7%–93% 均分取 3/4/5 个最近真实点；同屏重复日期向前找不同日期）。 */
    fun xTicks(points: List<TrendPoint>, range: PowerRange, zone: ZoneId = ZoneId.systemDefault()): List<XTick> {
        if (points.size < 2) return emptyList()
        val count = min(range.xTickCount, points.size)
        val out = ArrayList<XTick>(count)
        val seen = HashSet<String>()
        for (i in 0 until count) {
            val target = if (count == 1) 0.5f else (0.07f + 0.86f * i / (count - 1))
            var best = 0
            var bestDiff = Float.MAX_VALUE
            points.forEachIndexed { index, p ->
                val diff = abs(p.x - target)
                if (diff < bestDiff) {
                    bestDiff = diff
                    best = index
                }
            }
            var label = xLabel(points[best].t, range, zone)
            if (seen.contains(label)) {
                for (k in 1..8) {
                    val next = min(points.size - 1, best + k)
                    val nextLabel = xLabel(points[next].t, range, zone)
                    if (nextLabel != label) {
                        best = next
                        label = nextLabel
                        break
                    }
                }
            }
            seen.add(label)
            out.add(XTick(frac = points[best].x, label = label))
        }
        return out
    }

    /** 慢频阶梯线（先水平后垂直）：输入 trendLine points，输出折线路径（含起点）。 */
    fun stepSegments(points: List<TrendPoint>): List<TrendPoint> {
        val pts = points.filter { it.x.isFinite() && it.y.isFinite() }
        if (pts.isEmpty()) return emptyList()
        val out = ArrayList<TrendPoint>(pts.size * 2)
        out.add(pts.first())
        for (i in 1 until pts.size) {
            out.add(pts[i].copy(y = pts[i - 1].y)) // 水平：保持旧值到新点时间
            out.add(pts[i])                        // 垂直：跳变到新值
        }
        return out
    }

    /** 慢频真实变化点（相邻 |Δq|>CH 的后一个采样；最多 60 个）。 */
    fun changeDots(points: List<TrendPoint>): List<TrendPoint> {
        val pts = points.filter { it.x.isFinite() && it.y.isFinite() }
        val out = ArrayList<TrendPoint>()
        for (i in 1 until pts.size) {
            if (abs(pts[i].q - pts[i - 1].q) > CH) out.add(pts[i])
        }
        return if (out.size > 60) out.subList(out.size - 60, out.size) else out
    }

    // ---- 指标 tile / 计费 ----------------------------------------------------------

    /**
     * 4 个指标 tile（估算可用 / 区间用电 / 低电阈值 / 上次充值）。
     *
     * 「上次充值」优先使用调用方传入的**跨区间**结果 [lastRecharge]（30d 全量历史里的最后上升），
     * 值显示 `+delta`（充值量，度），而不是充值后余额；切 24h/7d/30d 不变。
     * 未传入（预览、30d 加载失败）时回退旧口径：慢频房 [PowerEstimate.lastRecharge]、实时房 [RangeStats.lastUp]。
     */
    fun tilesOf(
        row: PowerRow?,
        estimate: PowerEstimate?,
        rangeStats: RangeStats?,
        range: PowerRange,
        low: Int?,
        slowUpdate: SlowUpdate?,
        lastRecharge: RechargePoint? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<PowerTile> {
        val has = row != null
        val l = normLow(low)
        val isSlow = estimate?.slow == true
        val days = if (has && estimate != null) daysMain(estimate) else "—"
        val sub = if (has && estimate != null && estimate.valid && estimate.rate != null) {
            "≈ ${fmtRate(estimate.rate)} 度/天"
        } else {
            ""
        }
        val hasDrop = has && rangeStats?.drop != null && rangeStats.drop > 0
        val use = if (hasDrop) String.format(RR, "%.1f", rangeStats!!.drop) else "—"
        val useSub = if (isSlow && !hasDrop && rangeStats != null) "该区间无更新" else ""
        val rec = lastRecharge ?: if (isSlow) estimate.lastRecharge else rangeStats?.lastUp
        val recAmount = rec?.delta?.takeIf { it.isFinite() && it > 0.0 }
        val recAt = if (recAmount != null) rec.t else null
        val ref = if (isSlow) {
            estimate.valid && estimate.ref
        } else {
            slowUpdate?.slow == true && days != "—"
        }
        return listOf(
            PowerTile(
                key = "days",
                label = "估算可用",
                value = days,
                unit = "天",
                sub = sub,
                highlight = true,
                ref = ref,
            ),
            PowerTile(
                key = "use",
                label = range.tileLabel,
                value = use,
                unit = "度",
                sub = useSub,
            ),
            PowerTile(
                key = "threshold",
                label = "低电阈值",
                value = l.toString(),
                unit = "度",
                clickable = true,
            ),
            PowerTile(
                key = "recharge",
                label = "上次充值",
                value = if (recAmount != null) "+${fmtQ(recAmount)}" else "—",
                unit = if (recAmount != null) "度" else "",
                sub = if (recAt != null) updText(recAt, zone) else "暂无记录",
            ),
        )
    }

    /** 计费规则条（电价 / 区间电费 / 免费额度 / 水费折算 / 已忽略扣减）。 */
    fun billOf(rangeStats: RangeStats?, persons: Int = DEFAULT_PERSONS): BillInfo {
        val p = persons.coerceIn(1, 6)
        val drop = rangeStats?.drop?.takeIf { it.isFinite() && it > 0 }
        val fee = drop?.let { round2(it * ELEC_PRICE) }
        val ddn = rangeStats?.deduct?.n ?: 0
        val dedYuan = round2(rangeStats?.deduct?.yuan ?: 0.0)
        val dedDeg = rangeStats?.deduct?.deg ?: 0.0
        return BillInfo(
            persons = p,
            elecText = String.format(RR, "%.2f 元/度", ELEC_PRICE),
            feeText = if (fee == null) "—" else String.format(RR, "≈ ¥%.2f", fee),
            freeText = "$ELEC_FREE_PER_PERSON 度电/人/月（$p 人间 = ${ELEC_FREE_PER_PERSON * p} 度）",
            waterText = String.format(
                RR,
                "%.2f 元/吨从电费账户扣 · 1 吨≈%.1f 度",
                WATER_PRICE,
                WATER_PRICE / ELEC_PRICE,
            ),
            hasDeduct = ddn > 0,
            deductText = if (ddn > 0) {
                "已忽略 $ddn 次异常扣减 · ${String.format(RR, "%.2f", dedYuan)} 元 ≈ ${String.format(RR, "%.1f", dedDeg)} 度"
            } else {
                ""
            },
        )
    }
}
