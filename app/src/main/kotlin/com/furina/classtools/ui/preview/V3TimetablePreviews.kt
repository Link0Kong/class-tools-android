package com.furina.classtools.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.timetable.Course
import com.furina.classtools.data.timetable.TimetableRepository
import com.furina.classtools.data.timetable.TtLoadResult
import com.furina.classtools.data.timetable.TtSnapshot
import com.furina.classtools.ui.screens.timetable.TimetableCourseDetailContent
import com.furina.classtools.ui.screens.timetable.TimetablePalette
import com.furina.classtools.ui.screens.timetable.TimetableViewScreen
import com.furina.classtools.ui.theme.ClassToolsTheme
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 课表页 v5 预览（马卡龙配色 / 日期头 / 现在指示 / 详情 / 平板 / 大字体 / 状态页）。
 * 对应 `docs/design/UI_REFERENCE_20261113.md`。
 */
private const val V5_NOW: Long = 1_793_930_400_000L // 2026-11-06 10:00 +08（周五，第 10 周，第 3 节）

private fun v5Millis(hour: Int, minute: Int): Long =
    ZonedDateTime.of(2026, 11, 6, hour, minute, 0, 0, ZoneId.of("Asia/Shanghai"))
        .toInstant()
        .toEpochMilli()

private val v5TermStart = LocalDate.of(2026, 8, 31)

private val v5Snapshot: TtSnapshot = TtSnapshot(
    c = listOf(
        Course("数据结构", "龚志伟", "明理楼(教室02201)", day = 0, start = 1, end = 2, weeks = "1-16"),
        Course("高等数学", "刘老师", "自强楼(教室01104)", day = 0, start = 3, end = 4, weeks = "1-16"),
        Course("大学英语", "王老师", "明理楼(教室02301)", day = 1, start = 6, end = 7, weeks = "2-15", parity = "odd"),
        Course("大学物理", "陈老师", "格物楼(教室03201)", day = 2, start = 1, end = 2, weeks = "1-18"),
        Course("程序设计实验", "李老师", "实验楼(机房04012)", day = 3, start = 3, end = 4, weeks = "2-16"),
        Course("体育", "赵老师", "体育场", day = 4, start = 1, end = 2, weeks = "1-16"),
        Course("马克思主义基本原理", "孙老师", "明理楼(教室02101)", day = 4, start = 8, end = 9, weeks = "1-16"),
    ),
    t = "2026-08-31",
)

private val v5SnapshotWithWeekend: TtSnapshot = v5Snapshot.copy(
    c = v5Snapshot.c + Course(
        "周末选修",
        "周老师",
        "综合楼(教室04102)",
        day = 5,
        start = 2,
        end = 3,
        weeks = "1-16",
    ),
)

private object V5PreviewTimetableRepository : TimetableRepository {
    override suspend fun load(force: Boolean): TtLoadResult =
        TtLoadResult.Success(v5Snapshot, fromCache = false, fetchedAt = V5_NOW)

    override suspend fun cached(): TtSnapshot = v5Snapshot

    override suspend fun clear() = Unit
}

@Composable
private fun TimetablePreviewFrame(
    dark: Boolean,
    snapshot: TtSnapshot = v5Snapshot,
    dayView: Boolean = false,
    weekOffset: Int = 0,
    swipeOffset: Float = 0f,
    nowMillis: Long = V5_NOW,
) {
    ClassToolsTheme(darkTheme = dark) {
        TimetableViewScreen(
            onBack = {},
            repository = V5PreviewTimetableRepository,
            initialSnapshot = snapshot,
            initialDayView = dayView,
            initialWeekOffset = weekOffset,
            initialSwipeOffsetPx = swipeOffset,
            skipLoad = true,
            nowMillis = nowMillis,
            onAddToCalendar = { _, _, _ -> },
            onExportTimetable = {},
        )
    }
}

@Preview(name = "200 课表 v5 周视图 · 浅色", showBackground = true, widthDp = 412, heightDp = 960)
@Composable
private fun V5WeekLightPreview() {
    TimetablePreviewFrame(dark = false)
}

@Preview(name = "201 课表 v5 周视图 · 深色", showBackground = true, widthDp = 412, heightDp = 960)
@Composable
private fun V5WeekDarkPreview() {
    TimetablePreviewFrame(dark = true)
}

@Preview(name = "202 课表 v5 滑动中（切下一周）", showBackground = true, widthDp = 412, heightDp = 960)
@Composable
private fun V5SwipePreview() {
    TimetablePreviewFrame(dark = false, swipeOffset = -96f, weekOffset = 0)
}

@Preview(name = "203 课表 v5 7 列（周末有课）", showBackground = true, widthDp = 412, heightDp = 960)
@Composable
private fun V5WeekendPreview() {
    TimetablePreviewFrame(dark = false, snapshot = v5SnapshotWithWeekend)
}

@Preview(name = "204 课表 v5 日视图 · 浅色", showBackground = true, widthDp = 412, heightDp = 960)
@Composable
private fun V5DayLightPreview() {
    TimetablePreviewFrame(dark = false, dayView = true)
}

@Preview(name = "205 课表 v5 日视图 · 深色", showBackground = true, widthDp = 412, heightDp = 960)
@Composable
private fun V5DayDarkPreview() {
    TimetablePreviewFrame(dark = true, dayView = true)
}

@Preview(name = "206 课表 v5 平板两栏 · 浅色", showBackground = true, widthDp = 1000, heightDp = 900)
@Composable
private fun V5TabletPreview() {
    TimetablePreviewFrame(dark = false)
}

@Preview(name = "207 课表 v5 平板两栏 · 深色", showBackground = true, widthDp = 1000, heightDp = 900)
@Composable
private fun V5TabletDarkPreview() {
    TimetablePreviewFrame(dark = true)
}

@Preview(
    name = "208 课表 v5 大字体 1.3x",
    showBackground = true,
    widthDp = 412,
    heightDp = 1100,
    fontScale = 1.3f,
)
@Composable
private fun V5LargeFontPreview() {
    TimetablePreviewFrame(dark = false)
}

@Preview(name = "209 课表 v5 空态 · 浅色", showBackground = true, widthDp = 412, heightDp = 640)
@Composable
private fun V5EmptyPreview() {
    ClassToolsTheme(darkTheme = false) {
        TimetableViewScreen(
            onBack = {},
            repository = V5PreviewTimetableRepository,
            initialSnapshot = TtSnapshot(),
            skipLoad = true,
            nowMillis = V5_NOW,
        )
    }
}

@Preview(name = "210 课表 v5 网络错误 · 深色", showBackground = true, widthDp = 412, heightDp = 640)
@Composable
private fun V5ErrorPreview() {
    ClassToolsTheme(darkTheme = true) {
        TimetableViewScreen(
            onBack = {},
            repository = V5PreviewTimetableRepository,
            initialError = "网络不可用，请稍后重试",
            initialErrorKind = com.furina.classtools.data.timetable.TtFailureKind.NETWORK,
            skipLoad = true,
            nowMillis = V5_NOW,
        )
    }
}

@Preview(name = "211 课表 v5 课程详情", showBackground = true, widthDp = 412, heightDp = 640)
@Composable
private fun V5CourseDetailPreview() {
    ClassToolsTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.padding(12.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            TimetableCourseDetailContent(
                course = v5Snapshot.c.first(),
                displayWeek = 10,
                termStart = v5TermStart,
                onAddToCalendar = { _, _, _ -> },
            )
        }
    }
}

@Preview(name = "212 课表 v5 马卡龙 16 色板", showBackground = true, widthDp = 412, heightDp = 820)
@Composable
private fun V5PalettePreview() {
    ClassToolsTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("马卡龙 16 色 · 浅色（对比度 5.3–7.5）", style = MaterialTheme.typography.titleSmall)
            PaletteGrid(pairs = TimetablePalette.LIGHT)
            Text("马卡龙 16 色 · 深色（同索引，对比度 6.4–9.1）", style = MaterialTheme.typography.titleSmall)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111414), RoundedCornerShape(16.dp))
                    .padding(8.dp),
            ) {
                PaletteGrid(pairs = TimetablePalette.DARK)
            }
        }
    }
}

@Composable
private fun PaletteGrid(pairs: List<TimetablePalette.CourseColorPair>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        pairs.chunked(4).forEachIndexed { rowIndex, rowPairs ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowPairs.forEachIndexed { colIndex, pair ->
                    val index = rowIndex * 4 + colIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .background(Color(pair.container), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$index",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(pair.content),
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "213 课表 v5 小屏 360dp", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun V5SmallScreenPreview() {
    TimetablePreviewFrame(dark = false)
}

@Preview(name = "214 课表 v5 上课中与现在指示", showBackground = true, widthDp = 412, heightDp = 960)
@Composable
private fun V5OngoingPreview() {
    TimetablePreviewFrame(dark = false, nowMillis = v5Millis(8, 20))
}

@Suppress("unused")
private val v5PreviewHint = 412.dp
