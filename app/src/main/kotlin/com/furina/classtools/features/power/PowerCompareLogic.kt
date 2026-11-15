package com.furina.classtools.features.power

import com.furina.classtools.data.power.PowerCompareRange
import com.furina.classtools.data.power.PowerRange
import com.furina.classtools.data.power.PowerStats
import com.furina.classtools.data.power.PowerPoint
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 自然周期中的一个比较窗口（含闭开边界：[start, end)）。 */
data class PowerCompareWindow(
    val label: String,
    val start: Long,
    val end: Long,
) {
    val durationMillis: Long get() = (end - start).coerceAtLeast(0L)
}

/**
 * 同期比较的**取数来源**（对齐网页 `POWER.cmpSource`）：
 *
 * - 24h：直接用已加载的 7d 窗口（含昨日同一时段）；
 * - 7d：慢频房（13/16 日更）用已加载的 30d，其它楼栋按需补拉隐藏的 14d；
 * - 30d：按需补拉隐藏的 70d（覆盖上月同期）。
 *
 * [loadedRange] 非空 = 已加载区间（UI 直接从历史表取）；[hiddenRange] 非空 = 需仓库补拉。
 */
data class PowerCompareSource(
    val key: String,
    val windowMs: Long,
    val loadedRange: PowerRange? = null,
    val hiddenRange: PowerCompareRange? = null,
)

/**
 * 自然周期同期比较结果（电费 tile 徽标用）。
 *
 * - [available]=false 时 UI 隐藏徽标（历史不足/无有效用电，不硬凑）；
 * - [percent] 为「当前周期用电 vs 上一周期用电」的变化百分比（正=用电更多）；
 * - [label] 固定「较昨日同期 / 较上周同期 / 较上月同期」；
 * - [title] 说明两侧时段（如「今日 00:00–21:00 vs 昨日 00:00–21:00」）。
 */
data class PowerCompareResult(
    val range: PowerRange,
    val available: Boolean,
    val label: String,
    val percent: Double? = null,
    val currentUse: Double? = null,
    val previousUse: Double? = null,
    val currentWindow: PowerCompareWindow,
    val previousWindow: PowerCompareWindow,
    val currentCoverage: Double = 0.0,
    val previousCoverage: Double = 0.0,
    val reason: String? = null,
    val title: String = "",
) {
    /** 徽标文案：`+12.3%` / `-4.5%`；不可比较时 null。 */
    val badgeText: String?
        get() = if (available) percent?.let { PowerCompareLogic.formatPercent(it) } else null
}

/**
 * 电费「自然周期同期比较」纯逻辑（对齐 `web/AGENTS.md §34` 与网页电费实现）。
 *
 * 口径：
 * - 24h：今日 00:00–现在 vs 昨日 00:00–昨日同一时刻；
 * - 7d：本周一 00:00–现在 vs 上周一 00:00–上周同一时刻；
 * - 30d：本月 1 日 00:00–现在 vs 上月 1 日 00:00–上月同一日同时刻（月末无同日按月末）；
 * - 只有**两侧真实采样覆盖均 ≥ 窗口 35%** 且**两侧有效用电均 >0.01 度**才显示比较；
 *   用电 = 窗口内真实采样下降和（**不插值**，忽略充值上升/噪声与异常扣减），与网页 `deriveRange` 同口径；
 * - 取数窗口与网页 `POWER.cmpSource` 同口径（见 [compareSource]：隐藏 14d/70d 按需补拉）；
 * - 现网 power_history 只有约 6 天 → 上周/上月比较通常不可用，数据积累后自动出现（不硬凑）。
 *
 * 另外提供 13/16 号楼隐藏 24h 区间的规则 helper：[showRange] / [normRange]。
 */
object PowerCompareLogic {

    /** 两侧真实采样覆盖要求：≥窗口时长 35%。 */
    const val MIN_COVERAGE: Double = 0.35

    /** 相邻采样下降超过该值才算有效用电（与 web/mp `CH` 同口径，忽略噪声）。 */
    const val MIN_DELTA: Double = 0.005

    /** 一个周期内的有效用电下限（对齐网页 `naturalCompare`：`drop > 0.01` 才比较）。 */
    const val MIN_USAGE: Double = 0.01

    private val HH_MM = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val MM_DD_HH_MM = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)

    // ------------------------------------------------------------ 13/16 号楼隐藏 24h

    fun isSlowBuilding(building: String?): Boolean = PowerStats.isSlowBuilding(building)

    /** 该楼栋是否显示某区间（13/16 号楼隐藏 24h；其它档正常）。 */
    fun showRange(range: PowerRange, building: String?): Boolean =
        !(range == PowerRange.H24 && isSlowBuilding(building))

    /** 过滤可见区间（输入默认 [PowerRange.entries]；13/16 返回 [D7, D30]）。 */
    fun showRange(ranges: List<PowerRange> = PowerRange.entries, building: String?): List<PowerRange> =
        ranges.filter { showRange(it, building) }

    /** 选中了当前楼栋不可见的档 → 回落到 7 天（切宿舍时归位，不覆盖用户显式选择）。 */
    fun normRange(range: PowerRange, building: String?): PowerRange =
        if (showRange(range, building)) range else PowerRange.D7

    // ------------------------------------------------------------ 比较取数窗口

    /**
     * 比较取数来源（**与网页 `POWER.cmpSource` 同口径**）：
     *
     * - 24h → 已加载的 7d；
     * - 7d → 慢频房已加载的 30d，其余隐藏补拉 14d；
     * - 30d → 隐藏补拉 70d。
     *
     * @param slowRoom 当前宿舍是否为慢频房（13/16 号楼 && 24h 变化 <6 次，与 [PowerStats.isSlowRoom] 一致）
     */
    fun compareSource(range: PowerRange, slowRoom: Boolean): PowerCompareSource = when (range) {
        PowerRange.H24 -> PowerCompareSource(
            key = PowerRange.D7.key,
            windowMs = PowerRange.D7.windowMs,
            loadedRange = PowerRange.D7,
        )

        PowerRange.D7 -> if (slowRoom) {
            PowerCompareSource(
                key = PowerRange.D30.key,
                windowMs = PowerRange.D30.windowMs,
                loadedRange = PowerRange.D30,
            )
        } else {
            PowerCompareSource(
                key = PowerCompareRange.D14.key,
                windowMs = PowerCompareRange.D14.windowMs,
                hiddenRange = PowerCompareRange.D14,
            )
        }

        PowerRange.D30 -> PowerCompareSource(
            key = PowerCompareRange.D70.key,
            windowMs = PowerCompareRange.D70.windowMs,
            hiddenRange = PowerCompareRange.D70,
        )
    }

    // ------------------------------------------------------------ 自然周期窗口

    /**
     * 计算「当前周期」与「上一周期同期」两个窗口。
     *
     * 上一周期结束 = 当前时刻 - 1 个自然周期（日/周/月），因此两端均为「同一时刻」；
     * 上一周期开始 = 该日/该周/该月自然起点。
     */
    fun windows(
        range: PowerRange,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Pair<PowerCompareWindow, PowerCompareWindow> {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val currentStart: ZonedDateTime
        val previousStart: ZonedDateTime
        val previousEnd: ZonedDateTime
        when (range) {
            PowerRange.H24 -> {
                previousEnd = now.minusDays(1)
                currentStart = now.toLocalDate().atStartOfDay(zone)
                previousStart = previousEnd.toLocalDate().atStartOfDay(zone)
            }

            PowerRange.D7 -> {
                previousEnd = now.minusWeeks(1)
                currentStart = now.toLocalDate().with(DayOfWeek.MONDAY).atStartOfDay(zone)
                previousStart = previousEnd.toLocalDate().with(DayOfWeek.MONDAY).atStartOfDay(zone)
            }

            PowerRange.D30 -> {
                // 上月同一日同时刻；若上月无该日（如 3-31→2 月），ZonedDateTime.minusMonths 会钳到月末
                previousEnd = now.minusMonths(1)
                currentStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay(zone)
                previousStart = previousEnd.toLocalDate().withDayOfMonth(1).atStartOfDay(zone)
            }
        }
        val currentLabel = when (range) {
            PowerRange.H24 -> "今日"
            PowerRange.D7 -> "本周"
            PowerRange.D30 -> "本月"
        }
        val previousLabel = when (range) {
            PowerRange.H24 -> "昨日"
            PowerRange.D7 -> "上周"
            PowerRange.D30 -> "上月"
        }
        return PowerCompareWindow(
            label = currentLabel,
            start = currentStart.toInstant().toEpochMilli(),
            end = nowMillis,
        ) to PowerCompareWindow(
            label = previousLabel,
            start = previousStart.toInstant().toEpochMilli(),
            end = previousEnd.toInstant().toEpochMilli(),
        )
    }

    /** 窗口内真实采样覆盖比例：`(last-first)/窗口时长`（0..1；样本 <2 → 0）。 */
    fun coverage(points: List<PowerPoint>, window: PowerCompareWindow): Double {
        val inside = pointsIn(points, window)
        if (inside.size < 2) return 0.0
        val span = (inside.last().t - inside.first().t).toDouble()
        val duration = window.durationMillis.toDouble()
        if (duration <= 0.0) return 0.0
        return (span / duration).coerceIn(0.0, 1.0)
    }

    /**
     * 窗口内用电量 = 相邻真实采样下降之和（不插值；上升视为充值/异常跳过；
     * `> max(5, 4×中位数)` 的异常扣减（水费从电费账户扣等）不计入，与网页 `deriveRange` 同口径）。
     * 结果保留 2 位小数（网页 `d.drop` 同口径）。样本不足 2 或窗口时长为 0 → null。
     */
    fun usageIn(points: List<PowerPoint>, window: PowerCompareWindow): Double? {
        val inside = pointsIn(points, window)
        if (inside.size < 2) return null
        val deduct = PowerStats.deductOf(inside)
        var used = 0.0
        for (index in 1 until inside.size) {
            val drop = inside[index - 1].q - inside[index].q
            // 正常下降计入；充值上升（drop<0）/噪声（≤MIN_DELTA）/异常扣减（>deduct.thr）都跳过。
            if (drop > MIN_DELTA && drop <= deduct.thr) used += drop
        }
        // 与网页 deriveRange 的 d.drop 一致：2 位小数（比较百分比也基于同一数值）。
        return PowerStats.round2(used)
    }

    /**
     * 窗口内真实采样点（按时间升序；窗口闭区间 `[start,end]`，与网页 `cmpWindowPts` 一致）。
     */
    fun pointsIn(points: List<PowerPoint>, window: PowerCompareWindow): List<PowerPoint> =
        points.asSequence()
            .filter { it.q.isFinite() && it.t >= window.start && it.t <= window.end }
            .sortedBy { it.t }
            .toList()

    // ------------------------------------------------------------ 比较

    /**
     * 计算某档的自然周期同期比较；不可比较时返回 `available=false` + 中文 [PowerCompareResult.reason]。
     */
    fun compare(
        range: PowerRange,
        history: List<PowerPoint>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): PowerCompareResult {
        val (currentWindow, previousWindow) = windows(range, nowMillis, zone)
        val currentCoverage = coverage(history, currentWindow)
        val previousCoverage = coverage(history, previousWindow)
        val currentUse = usageIn(history, currentWindow)
        val previousUse = usageIn(history, previousWindow)
        val label = when (range) {
            PowerRange.H24 -> "较昨日同期"
            PowerRange.D7 -> "较上周同期"
            PowerRange.D30 -> "较上月同期"
        }
        val title = windowTitle(currentWindow, previousWindow, zone)
        val unavailable = { reason: String ->
            PowerCompareResult(
                range = range,
                available = false,
                label = label,
                currentUse = currentUse,
                previousUse = previousUse,
                currentWindow = currentWindow,
                previousWindow = previousWindow,
                currentCoverage = currentCoverage,
                previousCoverage = previousCoverage,
                reason = reason,
                title = title,
            )
        }
        if (currentUse == null || currentCoverage < MIN_COVERAGE) {
            return unavailable("数据积累中")
        }
        if (currentUse <= MIN_USAGE) {
            return unavailable("当前周期无有效用电数据")
        }
        if (previousUse == null || previousCoverage < MIN_COVERAGE) {
            return unavailable("上一周期数据不足")
        }
        if (previousUse <= MIN_USAGE) {
            return unavailable("上一周期无有效用电数据")
        }
        val percent = (currentUse - previousUse) / previousUse * 100.0
        if (!percent.isFinite()) return unavailable("数据异常")
        return PowerCompareResult(
            range = range,
            available = true,
            label = label,
            percent = percent,
            currentUse = currentUse,
            previousUse = previousUse,
            currentWindow = currentWindow,
            previousWindow = previousWindow,
            currentCoverage = currentCoverage,
            previousCoverage = previousCoverage,
            title = title,
        )
    }

    /** 三档一次算完（UI 切档用；结果只依赖 history/now）。 */
    fun compareAll(
        history: List<PowerPoint>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<PowerRange, PowerCompareResult> = PowerRange.entries.associateWith { range ->
        compare(range, history, nowMillis, zone)
    }

    /** `+12.3%` / `-4.5%` / `0%`（1 位小数；正数带 +，负数自带 -）。 */
    fun formatPercent(value: Double): String {
        if (!value.isFinite()) return "0%"
        val rounded = String.format(Locale.CHINA, "%.1f", value)
        val prefix = if (value > 0.05) "+" else ""
        return "$prefix$rounded%"
    }

    /** 比较标题：`今日 00:00–21:00 vs 昨日 00:00–21:00`。 */
    fun windowTitle(
        current: PowerCompareWindow,
        previous: PowerCompareWindow,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val currentText = windowText(current, zone)
        val previousText = windowText(previous, zone)
        return "$currentText vs $previousText"
    }

    /** 单窗口时间文案：`今日 00:00–21:00`（跨日时带上月日）。 */
    fun windowText(window: PowerCompareWindow, zone: ZoneId = ZoneId.systemDefault()): String {
        val start = Instant.ofEpochMilli(window.start).atZone(zone)
        val end = Instant.ofEpochMilli(window.end).atZone(zone)
        val sameDay = start.toLocalDate() == end.toLocalDate()
        val startText = if (sameDay) {
            HH_MM.format(start)
        } else {
            MM_DD_HH_MM.format(start)
        }
        val endText = if (sameDay) {
            HH_MM.format(end)
        } else {
            MM_DD_HH_MM.format(end)
        }
        return "${window.label} $startText–$endText"
    }

    /** 覆盖不足时 UI 可展示的原因映射（未来可扩展）。 */
    fun unavailableHint(result: PowerCompareResult): String = result.reason.orEmpty()
}
