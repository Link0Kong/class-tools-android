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

    /** 浅色 16 组（取自网页 `TT_COLORS`，对比度均 ≥ 4.5:1）。 */
    val LIGHT: List<CourseColorPair> = listOf(
        CourseColorPair(0xFFDCE6F0, 0xFF3D5872),
        CourseColorPair(0xFFE7D9EC, 0xFF5A4368),
        CourseColorPair(0xFFDDEBDD, 0xFF3F6047),
        CourseColorPair(0xFFF1DDD9, 0xFF6E4441),
        CourseColorPair(0xFFF0E5CD, 0xFF66572F),
        CourseColorPair(0xFFD9E3E8, 0xFF3E5A63),
        CourseColorPair(0xFFEEDCE4, 0xFF6B4356),
        CourseColorPair(0xFFDFE6CD, 0xFF546132),
        CourseColorPair(0xFFD8E7E4, 0xFF35605A),
        CourseColorPair(0xFFE8DBD2, 0xFF644A3A),
        CourseColorPair(0xFFDEDBEC, 0xFF474569),
        CourseColorPair(0xFFF2E3D4, 0xFF6D4F36),
        CourseColorPair(0xFFD5E3F2, 0xFF3B5578),
        CourseColorPair(0xFFE9E2C9, 0xFF625A33),
        CourseColorPair(0xFFE5D8DE, 0xFF5D4250),
        CourseColorPair(0xFFD3E5DB, 0xFF39604E),
    )

    /** 深色 16 组（同色相深底 + 浅字，对比度 6.2–9.0:1）。 */
    val DARK: List<CourseColorPair> = listOf(
        CourseColorPair(0xFF1A3856, 0xFFC5D1DE),
        CourseColorPair(0xFF441F51, 0xFFD3C8DA),
        CourseColorPair(0xFF244C24, 0xFFC9DACD),
        CourseColorPair(0xFF572419, 0xFFDCC8C6),
        CourseColorPair(0xFF574419, 0xFFE0D8C2),
        CourseColorPair(0xFF253E4B, 0xFFC8D6DB),
        CourseColorPair(0xFF521E35, 0xFFDBC8D1),
        CourseColorPair(0xFF43511F, 0xFFD7DEC4),
        CourseColorPair(0xFF264A43, 0xFFC5DDDA),
        CourseColorPair(0xFF513420, 0xFFDCCEC6),
        CourseColorPair(0xFF292150, 0xFFCAC9DA),
        CourseColorPair(0xFF573819, 0xFFDFD0C3),
        CourseColorPair(0xFF193757, 0xFFC3CFDF),
        CourseColorPair(0xFF574919, 0xFFDEDAC4),
        CourseColorPair(0xFF472937, 0xFFD8CAD1),
        CourseColorPair(0xFF254C36, 0xFFC7DCD2),
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

    /** 手机（<600dp）压缩比例；平板/横屏放大行高与字号。 */
    fun metricsFor(widthDp: Int, twoPane: Boolean): TimetableMetrics = when {
        widthDp >= 840 || twoPane -> TimetableMetrics(
            rowHeightDp = 60,
            labelWidthDp = 52,
            headerHeightDp = 46,
            blockCornerDp = 10,
            courseNameMaxLines = 3,
            showEndTime = true,
            blockPaddingDp = 6,
        )

        widthDp >= 600 -> TimetableMetrics(
            rowHeightDp = 56,
            labelWidthDp = 48,
            headerHeightDp = 44,
            blockCornerDp = 9,
            courseNameMaxLines = 2,
            showEndTime = true,
            blockPaddingDp = 5,
        )

        else -> TimetableMetrics(
            rowHeightDp = 52,
            labelWidthDp = 38,
            headerHeightDp = 40,
            blockCornerDp = 8,
            courseNameMaxLines = 2,
            showEndTime = false,
            blockPaddingDp = 4,
        )
    }

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

