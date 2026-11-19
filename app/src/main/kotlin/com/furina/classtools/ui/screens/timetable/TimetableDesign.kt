package com.furina.classtools.ui.screens.timetable

import com.furina.classtools.data.timetable.Course
import com.furina.classtools.data.timetable.TtCalendar
import com.furina.classtools.data.timetable.TtPeriods
import com.furina.classtools.data.timetable.TtSnapshot
import com.furina.classtools.features.export.ExportCardNaming
import com.furina.classtools.features.export.ExportCardSpec
import com.furina.classtools.features.export.ExportKind
import com.furina.classtools.features.export.TextBlock
import com.furina.classtools.features.export.TextLine
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * 课表页纯逻辑（v3 重构；无 Compose/Android 依赖，可单测）。
 *
 * 对齐网页/小程序：
 * - 16 色稳定课程配色（按课程名散列 → 固定索引；同一门课浅/深色同索引）；
 * - 左右滑动切周（锁定/提交阈值/边缘阻尼/点击抑制与 web/mp 同参数）；
 * - 周次边界 1..maxWeek（maxWeek 由课程 weeks 推导，最少 18 周）；
 * - 显示比例：节次行高/左标签列宽/表头高按窗口宽度与视图模式自适应（7 列恒不横向溢出）；
 * - 导出图片 payload（网页 `#tImg` 同款：按天分块 + 参数区）。
 */
object TimetablePalette {

    /** 一组课程配色（容器色 + 内容色，均 0xAARRGGBB）。 */
    data class CourseColorPair(
        val container: Long,
        val content: Long,
    )

    const val COLOR_COUNT: Int = 16

    /** 浅色 16 组马卡龙配色（低饱和 pastel 底 + 深字，对比度 5.3–7.5:1；本任务生成并校验）。 */
    val LIGHT: List<CourseColorPair> = listOf(
        CourseColorPair(0xFFF7D9DE, 0xFF6D3B47), // 樱粉 6.73
        CourseColorPair(0xFFFBE3D1, 0xFF6B4A2F), // 蜜桃 6.43
        CourseColorPair(0xFFF8EDC9, 0xFF6A5A28), // 奶油黄 5.78
        CourseColorPair(0xFFDDEEDD, 0xFF3F6047), // 薄荷 5.83
        CourseColorPair(0xFFE6EBCF, 0xFF58622C), // 鼠尾草 5.36
        CourseColorPair(0xFFDCE9F7, 0xFF3A5575), // 天空 6.23
        CourseColorPair(0xFFE8DEF7, 0xFF4E3F72), // 丁香 7.12
        CourseColorPair(0xFFF6DEE7, 0xFF6B3D51), // 玫瑰 6.85
        CourseColorPair(0xFFE4E2F6, 0xFF46406B), // 薰衣草 7.48
        CourseColorPair(0xFFDCE7EC, 0xFF3C5963), // 雾蓝 5.96
        CourseColorPair(0xFFF6F1D8, 0xFF665A2E), // 柠檬 6.03
        CourseColorPair(0xFFFADFD9, 0xFF6E4038), // 珊瑚 6.77
        CourseColorPair(0xFFD8EDE8, 0xFF356057), // 水青 5.81
        CourseColorPair(0xFFF3DFE0, 0xFF674049), // 胭脂 6.83
        CourseColorPair(0xFFE0E4F5, 0xFF465071), // 长春花 6.27
        CourseColorPair(0xFFE7EFD8, 0xFF4F5F2E), // 开心果 5.89
    )

    /** 深色 16 组马卡龙配色（同色相深底 + 浅字，对比度 6.4–9.1:1）。 */
    val DARK: List<CourseColorPair> = listOf(
        CourseColorPair(0xFF4F1720, 0xFFDDBBC3), // 樱粉 8.11
        CourseColorPair(0xFF4F2F17, 0xFFE2CAB6), // 蜜桃 7.63
        CourseColorPair(0xFF4F4217, 0xFFE5D9B3), // 奶油黄 7.02
        CourseColorPair(0xFF194D19, 0xFFC0D8C6), // 薄荷 6.56
        CourseColorPair(0xFF454F17, 0xFFD9E1B7), // 鼠尾草 6.46
        CourseColorPair(0xFF17324F, 0xFFB9CADF), // 天空 7.83
        CourseColorPair(0xFF2D174F, 0xFFC5BCDC), // 丁香 8.61
        CourseColorPair(0xFF4F172C, 0xFFDBBDCA), // 玫瑰 8.14
        CourseColorPair(0xFF1D174F, 0xFFC2BEDA), // 薰衣草 9.06
        CourseColorPair(0xFF1C3B4A, 0xFFBED3DA), // 雾蓝 7.62
        CourseColorPair(0xFF4F4617, 0xFFE1D8B7), // 柠檬 6.62
        CourseColorPair(0xFF4F2117, 0xFFDEBFBA), // 珊瑚 7.85
        CourseColorPair(0xFF174F42, 0xFFBCDCD5), // 水青 6.42
        CourseColorPair(0xFF4F171A, 0xFFD9BFC5), // 胭脂 8.31
        CourseColorPair(0xFF17224F, 0xFFBFC5D9), // 长春花 8.86
        CourseColorPair(0xFF3C4F17, 0xFFD3DFB9), // 开心果 6.47
    )

    /** 稳定配色索引：同一课程名永远同一色（跨设备/跨会话一致，深色模式同索引）。 */
    fun indexOf(courseName: String): Int {
        val name = courseName.trim()
        if (name.isEmpty()) return 0
        var hash = 0x811C9DC5.toInt() // FNV-1a，避免 String.hashCode 的分布问题
        name.forEach { ch ->
            hash = hash xor ch.code
            hash *= 0x01000193
        }
        return ((hash % COLOR_COUNT) + COLOR_COUNT) % COLOR_COUNT
    }

    fun lightOf(courseName: String): CourseColorPair = LIGHT[indexOf(courseName)]
    fun darkOf(courseName: String): CourseColorPair = DARK[indexOf(courseName)]

    /** WCAG 2.1 相对亮度对比度（用于单测/设计校验，不参与运行时绘制）。 */
    fun contrastRatio(argbA: Long, argbB: Long): Double {
        fun channel(argb: Long, shift: Int): Double {
            val c = ((argb shr shift) and 0xFF).toDouble() / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        fun lum(argb: Long): Double =
            0.2126 * channel(argb, 16) + 0.7152 * channel(argb, 8) + 0.0722 * channel(argb, 0)
        val la = lum(argbA)
        val lb = lum(argbB)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }
}

/** 课表内容左右滑动切周（参数与 web `SW_*` / mp `tt.js` 一致，单位 px）。 */
object TimetableSwipe {

    const val LOCK_PX: Float = 12f
    const val LOCK_RATIO: Float = 1.35f
    const val COMMIT_PX: Float = 56f
    const val EDGE_DAMP: Float = 0.28f
    const val TAP_GUARD_MS: Long = 320L
    const val FLY_OUT_MS: Int = 150
    const val FLY_IN_MS: Int = 230

    enum class Direction(val delta: Int) {
        PREV(-1),
        NEXT(+1),
        NONE(0),
    }

    /** 是否横向锁定（|dx|≥12 且 |dx| > |dy|×1.35）。 */
    fun shouldLock(dx: Float, dy: Float): Boolean =
        abs(dx) >= LOCK_PX && abs(dx) > abs(dy) * LOCK_RATIO

    /** 松手方向（按位移符号；提交与否见 [shouldCommit]）。 */
    fun directionOf(dx: Float): Direction = when {
        dx <= -COMMIT_PX -> Direction.NEXT
        dx >= COMMIT_PX -> Direction.PREV
        else -> Direction.NONE
    }

    /** 松手是否提交：横向占优 + 位移 ≥ 56px。 */
    fun shouldCommit(dx: Float, dy: Float): Boolean =
        abs(dx) >= COMMIT_PX && abs(dx) > abs(dy) * 1.1f

    /** 跟手位移：有目标周 1:1；没有目标（第一/最后周）橡皮筋阻尼 0.28。 */
    fun damped(dx: Float, hasTarget: Boolean): Float =
        if (hasTarget) dx else dx * EDGE_DAMP

    /** 提交方向 → 目标周（越界返回 null）。 */
    fun targetWeek(currentWeek: Int, direction: Direction, maxWeek: Int): Int? = when (direction) {
        Direction.NEXT -> if (currentWeek < maxWeek) currentWeek + 1 else null
        Direction.PREV -> if (currentWeek > TimetableWeekLogic.MIN_WEEK) currentWeek - 1 else null
        Direction.NONE -> null
    }
}

/** 「现在」指示线状态（纯逻辑，可单测）。 */
sealed interface TimetableNowState {
    /** [fraction] 为 0..1 的网格高度比例（第 1 节顶部 = 0，第 12 节底部 = 1）。 */
    data class Visible(val fraction: Float, val label: String) : TimetableNowState

    data object Hidden : TimetableNowState
}

/**
 * 当前时间在节次网格中的位置（v5「现在」指示线）+ 进行中课程进度。
 *
 * 口径：以 [TtPeriods.PERIODS] 的 12 节起止时间为准；节内按分钟线性插值到该行；
 * 课间间隙映射到下一行顶部；课前/课后/无节次数据返回 [TimetableNowState.Hidden]。
 */
object TimetableNowLogic {

    private val TIME_FORMAT = java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

    /** 解析 `HH:mm` → 当天分钟数。 */
    fun minutesOf(text: String): Int? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    fun label(nowMillis: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        val time = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalTime()
        return "现在 " + TIME_FORMAT.format(time)
    }

    fun indicator(
        nowMillis: Long,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): TimetableNowState {
        val periods = TtPeriods.PERIODS
        if (periods.isEmpty()) return TimetableNowState.Hidden
        val local = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone)
        val minutes = local.hour * 60 + local.minute
        val firstStart = minutesOf(periods.first().first) ?: return TimetableNowState.Hidden
        val lastEnd = minutesOf(periods.last().second) ?: return TimetableNowState.Hidden
        if (minutes < firstStart || minutes > lastEnd) return TimetableNowState.Hidden
        for (index in periods.indices) {
            val start = minutesOf(periods[index].first) ?: continue
            val end = minutesOf(periods[index].second) ?: continue
            if (minutes in start..end) {
                val within = if (end == start) 0f else (minutes - start).toFloat() / (end - start)
                val fraction = ((index + within) / periods.size).coerceIn(0f, 1f)
                return TimetableNowState.Visible(fraction, label(nowMillis, zone))
            }
            val next = periods.getOrNull(index + 1) ?: continue
            val nextStart = minutesOf(next.first) ?: continue
            if (minutes > end && minutes < nextStart) {
                return TimetableNowState.Visible(
                    fraction = ((index + 1).toFloat() / periods.size).coerceIn(0f, 1f),
                    label = label(nowMillis, zone),
                )
            }
        }
        return TimetableNowState.Hidden
    }

    /** 进行中课程进度（0..1；未开始 0，已结束 1）。 */
    fun progress(startAt: Long, endAt: Long, nowMillis: Long): Float {
        if (endAt <= startAt) return 0f
        return ((nowMillis - startAt).toFloat() / (endAt - startAt).toFloat()).coerceIn(0f, 1f)
    }
}

/** 周次计算（浏览周 ≠ 当前教学周；weekOffset=0 表示本周）。 */
object TimetableWeekLogic {

    const val MIN_WEEK: Int = 1
    const val DEFAULT_MAX_WEEK: Int = 18

    /** 数据里出现的最大周次（至少 18 周，与 web/mp 一致）。 */
    fun maxWeekOf(courses: List<Course>): Int {
        var max = DEFAULT_MAX_WEEK
        courses.forEach { course ->
            TtCalendar.parseWeeks(course.weeks).forEach { week -> if (week > max) max = week }
        }
        return max
    }

    /** 当前教学周 + 偏移 → 实际展示周（夹到 1..maxWeek；无当前周返回 null）。 */
    fun displayWeek(currentWeek: Int?, weekOffset: Int, maxWeek: Int): Int? {
        if (currentWeek == null) return null
        return (currentWeek + weekOffset).coerceIn(MIN_WEEK, maxOf(MIN_WEEK, maxWeek))
    }

    /** 单步切换（按钮/滑动提交）：越界返回 null。 */
    fun navigate(displayWeek: Int, direction: TimetableSwipe.Direction, maxWeek: Int): Int? {
        val target = displayWeek + direction.delta
        return if (target in MIN_WEEK..maxOf(MIN_WEEK, maxWeek)) target else null
    }

    /** 当前偏移是否「回到本周」。 */
    fun isThisWeek(weekOffset: Int): Boolean = weekOffset == 0

    /** 顶部周标题副标：`11/02 – 11/08`（开学日未知 → 空串）。 */
    fun weekRangeLabel(termStart: LocalDate?, week: Int?): String {
        val range = termStart?.let { start -> week?.let { TtCalendar.weekRange(start, it) } } ?: return ""
        val (first, last) = range
        return "${first.monthValue}/${first.dayOfMonth} – ${last.monthValue}/${last.dayOfMonth}"
    }
}

/** 一节课的展示尺寸规格（按窗口宽度/视图模式自适应）。 */
data class TimetableMetrics(
    val rowHeightDp: Int,
    /** 左侧节次时间列宽。 */
    val labelWidthDp: Int,
    val headerHeightDp: Int,
    /** 课程块圆角。 */
    val blockCornerDp: Int,
    /** 课程名最多行数（窄列 2 行，宽列 2 行；小屏 1 行）。 */
    val courseNameMaxLines: Int,
    /** 是否显示节次结束时间（仅平板）。 */
    val showEndTime: Boolean,
    /** 课程块内边距。 */
    val blockPaddingDp: Int,
)

object TimetableLayout {

    /** 手机（<600dp）压缩比例；平板/横屏放大行高与字号。大字体时按 fontScale 放大行高，减少课程名截断。 */
    fun metricsFor(widthDp: Int, twoPane: Boolean, fontScale: Float = 1f): TimetableMetrics {
        val base = when {
            widthDp >= 840 || twoPane -> TimetableMetrics(
                rowHeightDp = 60,
                labelWidthDp = 52,
                headerHeightDp = 46,
                blockCornerDp = 16,
                courseNameMaxLines = 3,
                showEndTime = true,
                blockPaddingDp = 6,
            )

            widthDp >= 600 -> TimetableMetrics(
                rowHeightDp = 56,
                labelWidthDp = 48,
                headerHeightDp = 44,
                blockCornerDp = 14,
                courseNameMaxLines = 2,
                showEndTime = true,
                blockPaddingDp = 5,
            )

            else -> TimetableMetrics(
                rowHeightDp = 52,
                labelWidthDp = 38,
                headerHeightDp = 40,
                blockCornerDp = 14,
                courseNameMaxLines = 2,
                showEndTime = false,
                blockPaddingDp = 4,
            )
        }
        val scale = fontScale.coerceIn(1f, 1.35f)
        if (scale <= 1.01f) return base
        return base.copy(
            rowHeightDp = (base.rowHeightDp * scale).toInt(),
            labelWidthDp = (base.labelWidthDp * scale).toInt(),
            headerHeightDp = (base.headerHeightDp * scale).toInt(),
        )
    }

    /** 平板两栏阈值（v5：800dp 起即两栏；仍兼容 spec.twoPane 的 840dp 判断）。 */
    const val TABLET_MIN_WIDTH_DP: Int = 800

    fun twoPaneFor(widthDp: Int, specTwoPane: Boolean = false): Boolean =
        specTwoPane || widthDp >= TABLET_MIN_WIDTH_DP

    /** 课程块错峰入场延迟：24ms/块，最多 15 块后不再增加（避免长列表尾块等待过久）。 */
    const val ENTRANCE_STAGGER_MS: Int = 24

    fun entranceDelayMs(index: Int): Int = index.coerceIn(0, 15) * ENTRANCE_STAGGER_MS

    /** 7 列恒不横向溢出：可用宽 = 内容宽 - 左标签列；每列 = 可用宽 / 天数。 */
    fun columnWidthDp(availableWidthDp: Float, dayCount: Int): Float {
        if (dayCount <= 0) return 0f
        return (availableWidthDp / dayCount).coerceAtLeast(1f)
    }

    /** 周末两天都无课时隐藏周末列（5 列），否则 7 列。 */
    fun visibleDayCount(weekendEmpty: Boolean): Int = if (weekendEmpty) 5 else 7
}

/** 课程详情文案（纯函数，便于单测）。 */
object TimetableDetail {

    fun periodText(start: Int, end: Int): String =
        if (end > start) "第${start}-${end}节" else "第${start}节"

    fun timeRangeText(start: Int, end: Int): String {
        val startText = TtPeriods.startText(start.coerceIn(1, TtPeriods.COUNT))
        val endText = TtPeriods.endText(end.coerceIn(1, TtPeriods.COUNT))
        return "$startText–$endText"
    }

    /** 周次文案：`1-16,18 · 单周`；空 weeks → 全学期。 */
    fun weekText(course: Course, displayWeek: Int?): String = buildString {
        append("周次：")
        append(course.weeks.ifBlank { "全学期" })
        when (course.parity) {
            "odd" -> append(" · 单周")
            "even" -> append(" · 双周")
        }
        displayWeek?.let { append(" · 当前第 $it 周") }
    }

    /** 详情弹层字段（顺序即展示顺序）。 */
    fun detailRows(course: Course, displayWeek: Int?): List<Pair<String, String>> = buildList {
        add("节次" to periodText(course.start, course.end))
        add("时间" to timeRangeText(course.start, course.end))
        if (course.teacher.isNotBlank()) add("教师" to course.teacher)
        if (course.room.isNotBlank()) add("教室" to course.room)
        add("星期" to TtCalendar.dayLabel(course.day))
        add("周次" to buildString {
            append(course.weeks.ifBlank { "全学期" })
            when (course.parity) {
                "odd" -> append(" · 单周")
                "even" -> append(" · 双周")
            }
            displayWeek?.let { append(" · 当前第 $it 周") }
        })
    }
}

/** 课表导出图片 payload（网页 `#tImg` 同款：按天分块 TextBlock + 参数区）。 */
object TimetableExport {

    fun buildSpec(
        snapshot: TtSnapshot,
        termStart: LocalDate?,
        week: Int?,
        nowMillis: Long = System.currentTimeMillis(),
        recordSuffix: String? = null,
    ): ExportCardSpec? {
        val blocks = dayBlocks(snapshot, termStart, week)
        if (blocks.isEmpty()) return null
        val weekLabel = week?.let { "第 $it 周课表" } ?: "我的课表"
        val suffix = recordSuffix ?: randomSuffix(nowMillis)
        return ExportCardSpec(
            title = weekLabel,
            sub = "课程表",
            file = fileName(week, nowMillis),
            rec = recordId(nowMillis, suffix),
            config = configFields(snapshot, termStart, week),
            blocks = blocks,
            foot = ExportCardNaming.footText(nowMillis),
        )
    }

    /** 按天分块：`周一 11/02` + 每节课一行（网页同款文本）。 */
    fun dayBlocks(snapshot: TtSnapshot, termStart: LocalDate?, week: Int?): List<TextBlock> {
        val blocks = ArrayList<TextBlock>(7)
        for (dayIndex in 0..6) {
            val courses = TtCalendar.coursesOnDay(snapshot.c, termStart, week, dayIndex)
                .sortedBy { it.start }
            if (courses.isEmpty()) continue
            val label = buildString {
                append(TtPeriods.DAY_LABELS.getOrElse(dayIndex) { "" })
                val date = termStart?.let { start -> week?.let { TtCalendar.weekRange(start, it) } }
                    ?.let { (first, _) -> first.plusDays(dayIndex.toLong()) }
                if (date != null) append(" ${date.monthValue}/${date.dayOfMonth}")
            }
            blocks.add(
                TextBlock(
                    label = label,
                    lines = courses.map { course ->
                        TextLine.of(
                            buildString {
                                append(TimetableDetail.periodText(course.start, course.end))
                                append(" ")
                                append(
                                    TtPeriods.startText(course.start.coerceIn(1, TtPeriods.COUNT)),
                                )
                                append("–")
                                append(TtPeriods.endText(course.end.coerceIn(1, TtPeriods.COUNT)))
                                append("  ")
                                append(course.name)
                                if (course.room.isNotBlank()) append("  @").append(course.room)
                                if (course.teacher.isNotBlank()) append("  ").append(course.teacher)
                            },
                        )
                    },
                ),
            )
        }
        return blocks
    }

    /** 参数区（网页 config：周次/日期/课程数/学期起始）。 */
    fun configFields(
        snapshot: TtSnapshot,
        termStart: LocalDate?,
        week: Int?,
    ): List<Pair<String, String>> {
        val range = termStart?.let { start -> week?.let { TtCalendar.weekRange(start, it) } }
        val dateText = if (range != null) {
            val (first, last) = range
            "${first.monthValue}/${first.dayOfMonth} – ${last.monthValue}/${last.dayOfMonth}"
        } else {
            "未设置学期起始"
        }
        return listOf(
            "周次" to (week?.let { "第 $it 周" } ?: "全部"),
            "日期" to dateText,
            "课程数" to snapshot.c.size.toString(),
            "学期起始" to (snapshot.termStart.ifBlank { termStart?.toString() ?: "未设置" }),
        )
    }

    /** `课表_<stamp>_第N周.png`（走统一 ExportCardNaming）。 */
    fun fileName(week: Int?, nowMillis: Long): String = ExportCardNaming.fileName(
        kind = ExportKind.TIMETABLE,
        params = listOfNotNull(week?.let { "第${it}周" }),
        now = nowMillis,
    )

    /** 唯一编号：`TT-YYYYMMDD-HHMM-XXXX`。 */
    fun recordId(nowMillis: Long, suffix: String): String =
        "TT-" + ExportCardNaming.datePart(nowMillis) + "-" +
            ExportCardNaming.timePart(nowMillis) + "-" + suffix.uppercase(Locale.ROOT).take(4)

    fun randomSuffix(seed: Long): String {
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        val random = Random(seed)
        return buildString { repeat(4) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }
}
