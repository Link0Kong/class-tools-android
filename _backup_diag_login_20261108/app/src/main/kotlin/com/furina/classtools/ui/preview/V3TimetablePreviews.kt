package com.furina.classtools.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

/**
 * v3 课表页预览：手机周视图（浅/深/滑动中/7 列/小屏）、日视图、平板双栏、空态/错误、详情、16 色板。
 */
private const val V3_NOW: Long = 1_793_930_400_000L // 2026-11-06 10:00 +08（周五，第 10 周）

private val v3TermStart = LocalDate.of(2026, 8, 31)

private val v3Snapshot: TtSnapshot = TtSnapshot(
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

private val v3SnapshotWithWeekend: TtSnapshot = v3Snapshot.copy(
    c = v3Snapshot.c + Course(
        "周末选修",
        "周老师",
        "综合楼(教室04102)",
        day = 5,
        start = 2,
        end = 3,
        weeks = "1-16",
    ),
)

private object V3PreviewTimetableRepository : TimetableRepository {
    override suspend fun load(force: Boolean): TtLoadResult =
        TtLoadResult.Success(v3Snapshot, fromCache = false, fetchedAt = V3_NOW)

    override suspend fun cached(): TtSnapshot = v3Snapshot

    override suspend fun clear() = Unit
}

@Composable
private fun TimetablePreviewFrame(
    dark: Boolean,
    snapshot: TtSnapshot = v3Snapshot,
    dayView: Boolean = false,
    weekOffset: Int = 0,
    swipeOffset: Float = 0f,
) {
    ClassToolsTheme(darkTheme = dark) {
        TimetableViewScreen(
            onBack = {},
            repository = V3PreviewTimetableRepository,
            initialSnapshot = snapshot,
            initialDayView = dayView,
            initialWeekOffset = weekOffset,
            initialSwipeOffsetPx = swipeOffset,
            skipLoad = true,
            nowMillis = V3_NOW,
            onAddToCalendar = { _, _, _ -> },
            onExportTimetable = {},
        )
    }
}

@Preview(name = "120 课表 周视图 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun TimetableWeekLightPreview() {
    TimetablePreviewFrame(dark = false)
}

@Preview(name = "121 课表 周视图 · 深色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun TimetableWeekDarkPreview() {
    TimetablePreviewFrame(dark = true)
}

@Preview(name = "122 课表 周视图 · 滑动中（切下一周）", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun TimetableSwipePreview() {
    TimetablePreviewFrame(dark = false, swipeOffset = -96f)
}

@Preview(name = "123 课表 周视图 · 7 列（周末有课）", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun TimetableWeekendColumnsPreview() {
    TimetablePreviewFrame(dark = false, snapshot = v3SnapshotWithWeekend)
}

@Preview(name = "124 课表 日视图 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun TimetableDayLightPreview() {
    TimetablePreviewFrame(dark = false, dayView = true)
}

@Preview(name = "125 课表 日视图 · 深色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun TimetableDayDarkPreview() {
    TimetablePreviewFrame(dark = true, dayView = true)
}

@Preview(name = "126 课表 平板双栏 · 浅色", showBackground = true, widthDp = 1000, heightDp = 900)
@Composable
private fun TimetableTabletPreview() {
    TimetablePreviewFrame(dark = false)
}

@Preview(name = "127 课表 平板双栏 · 深色", showBackground = true, widthDp = 1000, heightDp = 900)
@Composable
private fun TimetableTabletDarkPreview() {
    TimetablePreviewFrame(dark = true)
}

@Preview(name = "128 课表 下一周 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun TimetableNextWeekPreview() {
    TimetablePreviewFrame(dark = false, weekOffset = 1)
}

@Preview(name = "129 课表 空态 · 浅色", showBackground = true, widthDp = 412, heightDp = 640)
@Composable
private fun TimetableEmptyPreview() {
    ClassToolsTheme(darkTheme = false) {
        TimetableViewScreen(
            onBack = {},
            repository = V3PreviewTimetableRepository,
            initialSnapshot = TtSnapshot(),
            skipLoad = true,
            nowMillis = V3_NOW,
        )
    }
}

@Preview(name = "130 课表 网络错误 · 深色", showBackground = true, widthDp = 412, heightDp = 640)
@Composable
private fun TimetableErrorPreview() {
    ClassToolsTheme(darkTheme = true) {
        TimetableViewScreen(
            onBack = {},
            repository = V3PreviewTimetableRepository,
            initialError = "网络不可用，请稍后重试",
            initialErrorKind = com.furina.classtools.data.timetable.TtFailureKind.NETWORK,
            skipLoad = true,
            nowMillis = V3_NOW,
        )
    }
}

@Preview(name = "131 课表 小屏 360dp", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun TimetableSmallScreenPreview() {
    TimetablePreviewFrame(dark = false)
}

@Preview(name = "132 课程详情（写日程入口）", showBackground = true, widthDp = 412, heightDp = 620)
@Composable
private fun CourseDetailPreview() {
    ClassToolsTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.padding(12.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            TimetableCourseDetailContent(
                course = v3Snapshot.c.first(),
                displayWeek = 10,
                termStart = v3TermStart,
                onAddToCalendar = { _, _, _ -> },
            )
        }
    }
}

@Preview(name = "133 16 色课程配色板", showBackground = true, widthDp = 412, heightDp = 760)
@Composable
private fun TimetablePalettePreview() {
    ClassToolsTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("16 色 · 浅色", style = MaterialTheme.typography.titleSmall)
            PaletteGrid(pairs = TimetablePalette.LIGHT)
            Text("16 色 · 深色（同索引）", style = MaterialTheme.typography.titleSmall)
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

@Suppress("unused")
private val v3PreviewHint = 412.dp
