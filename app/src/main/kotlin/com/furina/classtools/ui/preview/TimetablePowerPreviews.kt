package com.furina.classtools.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.furina.classtools.data.power.PowerHistoryResult
import com.furina.classtools.data.power.PowerPoint
import com.furina.classtools.data.power.PowerRange
import com.furina.classtools.data.power.PowerRepository
import com.furina.classtools.data.power.PowerRow
import com.furina.classtools.data.power.PowerRowResult
import com.furina.classtools.data.timetable.Course
import com.furina.classtools.data.timetable.TimetableRepository
import com.furina.classtools.data.timetable.TtLoadResult
import com.furina.classtools.data.timetable.TtMeta
import com.furina.classtools.data.timetable.TtFailureKind
import com.furina.classtools.data.timetable.TtSnapshot
import com.furina.classtools.ui.screens.power.PowerScreen
import com.furina.classtools.ui.screens.timetable.TimetableViewScreen
import com.furina.classtools.ui.theme.ClassToolsTheme

// =============================================================================
// 课表 + 电费 预览（独立文件；供 Lead `android preview render` 验收）
//
// 固定「现在」= 2026-11-07 10:00 Asia/Shanghai（周五，第 10 周），保证预览稳定。
// 预览全部走注入数据 + skipLoad，不发网络请求。
// =============================================================================

private const val PREVIEW_NOW: Long = 1_793_930_400_000L // 2026-11-06 10:00 Asia/Shanghai（周五）

private val previewTtSnapshot = TtSnapshot(
    c = listOf(
        Course("高等数学", "龚志伟", "自强楼(教室01104)", day = 0, start = 1, end = 2, weeks = "1-16"),
        Course("大学英语", "李晓", "明理楼(教室02201)", day = 2, start = 3, end = 4, weeks = "1-16"),
        Course("数据结构", "王强", "知行楼(教室03105)", day = 1, start = 3, end = 4, weeks = "2-15", parity = "odd"),
        Course("大学物理", "陈晨", "明理楼(教室02102)", day = 3, start = 6, end = 7, weeks = "1-16", parity = "even"),
        Course("体育（排球）", "赵阳", "体育馆", day = 4, start = 6, end = 7, weeks = "1-16"),
        Course("形势与政策", "刘敏", "自强楼(教室01201)", day = 4, start = 10, end = 11, weeks = "5-16"),
    ),
    t = "2026-08-31",
    meta = TtMeta(source = "jwxt", fetchedAt = PREVIEW_NOW, count = 6, termStart = "2026-08-31", syncedAt = "2026-11-07T10:00:00+08:00"),
)

private object PreviewTimetableRepository : TimetableRepository {
    override suspend fun load(force: Boolean): TtLoadResult =
        TtLoadResult.Success(previewTtSnapshot, fromCache = false, fetchedAt = PREVIEW_NOW)

    override suspend fun cached(): TtSnapshot? = previewTtSnapshot

    override suspend fun clear() = Unit
}

private object PreviewPowerRepository : PowerRepository {
    override suspend fun loadRow(rv: String, force: Boolean): PowerRowResult =
        PowerRowResult.Failure("预览模式")

    override suspend fun loadHistory(rv: String, range: PowerRange, force: Boolean): PowerHistoryResult =
        PowerHistoryResult.Success(emptyList(), range, fromCache = false)

    override suspend fun clearCache() = Unit
}

private fun hourOf(offsetHours: Int): Long = PREVIEW_NOW + offsetHours * 3_600_000L
private fun dayOf(offsetDays: Int): Long = PREVIEW_NOW + offsetDays * 24L * 3_600_000L

private fun powerRow(q: Double, rv: String = "4-23A---321", label: String = "23号楼A座321", building: String = "23号楼A座"): PowerRow =
    PowerRow(
        id = rv,
        label = label,
        building = building,
        room = label.takeLast(3),
        q = q,
        unit = "度",
        updatedAt = "2026-11-07T02:00:00+00:00",
    )

/** 24h：每小时缓慢下降（正常实时房）。 */
private fun previewHistories24h(query: Double = 86.42): List<PowerPoint> {
    val start = query + 4.0
    return (0..23).map { i ->
        PowerPoint(q = start - 4.0 * i / 23.0, t = hourOf(i - 23))
    }
}

/** 7d：含一次充值跳变（用于「期间有充值」展示）。 */
private fun previewHistories7d(query: Double = 86.42): List<PowerPoint> {
    val points = ArrayList<PowerPoint>()
    var q = query + 30.0
    for (i in 0 until 7 * 24) {
        q -= 0.115
        if (i == 96) q += 20.0 // 充值
        points.add(PowerPoint(q = q, t = hourOf(i - 7 * 24)))
    }
    points[points.size - 1] = points.last().copy(q = query)
    return points
}

/** 30d：每天一个采样，含一次增长（实时房）。 */
private fun previewHistories30d(query: Double = 86.42): List<PowerPoint> {
    val points = ArrayList<PowerPoint>()
    var q = query + 55.0
    for (i in 0 until 30) {
        q -= 1.5
        if (i == 18) q += 8.0
        points.add(PowerPoint(q = q, t = dayOf(i - 29)))
    }
    points[points.size - 1] = points.last().copy(q = query)
    return points
}

/** 慢频房（13号楼）30d：稀疏真实变化点（1 段大下降 + 一次充值 + 1 段下降）。 */
private fun previewSlowHistories30d(): List<PowerPoint> = listOf(
    PowerPoint(q = 70.0, t = dayOf(-29)),
    PowerPoint(q = 70.0, t = dayOf(-25)),
    PowerPoint(q = 60.0, t = dayOf(-14)),
    PowerPoint(q = 62.0, t = dayOf(-9)),   // 上升：充值
    PowerPoint(q = 55.0, t = dayOf(-1)),
)

/** 慢频房 24h：完全没变化（数据未更新）。 */
private fun previewSlowHistories24h(): List<PowerPoint> =
    (0..23).map { PowerPoint(q = 42.5, t = hourOf(it - 23)) }

@Preview(name = "20 课表 周视图 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun TimetableWeekLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        TimetableViewScreen(
            onBack = {},
            repository = PreviewTimetableRepository,
            initialSnapshot = previewTtSnapshot,
            skipLoad = true,
            nowMillis = PREVIEW_NOW,
        )
    }
}

@Preview(
    name = "20b 课表 周视图 · 大字体 1.3",
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.3f,
)
@Composable
private fun TimetableWeekLargeFontPreview() {
    ClassToolsTheme(darkTheme = false) {
        TimetableViewScreen(
            onBack = {},
            repository = PreviewTimetableRepository,
            initialSnapshot = previewTtSnapshot,
            skipLoad = true,
            nowMillis = PREVIEW_NOW,
        )
    }
}

@Preview(name = "21 课表 日视图 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun TimetableDayLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        TimetableViewScreen(
            onBack = {},
            repository = PreviewTimetableRepository,
            initialSnapshot = previewTtSnapshot,
            skipLoad = true,
            initialDayView = true,
            nowMillis = PREVIEW_NOW,
        )
    }
}

@Preview(
    name = "21b 课表 周视图 + 全部课程折叠卡（手机，整页）",
    showBackground = true,
    widthDp = 412,
    heightDp = 2600,
)
@Composable
private fun TimetableAllCoursesPreview() {
    ClassToolsTheme(darkTheme = false) {
        TimetableViewScreen(
            onBack = {},
            repository = PreviewTimetableRepository,
            initialSnapshot = previewTtSnapshot,
            skipLoad = true,
            nowMillis = PREVIEW_NOW,
        )
    }
}

@Preview(name = "22 课表 周视图 · 深色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun TimetableWeekDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        TimetableViewScreen(
            onBack = {},
            repository = PreviewTimetableRepository,
            initialSnapshot = previewTtSnapshot,
            skipLoad = true,
            nowMillis = PREVIEW_NOW,
        )
    }
}

private val previewTtSnapshotWithWeekend = previewTtSnapshot.copy(
    c = previewTtSnapshot.c + Course(
        "周末实验", "刘敏", "实验楼(教室04102)", day = 5, start = 2, end = 3, weeks = "1-16",
    ),
)

@Preview(name = "23a 课表 周末有课（保留 7 列） · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun TimetableWeekendKeptPreview() {
    ClassToolsTheme(darkTheme = false) {
        TimetableViewScreen(
            onBack = {},
            repository = PreviewTimetableRepository,
            initialSnapshot = previewTtSnapshotWithWeekend,
            skipLoad = true,
            nowMillis = PREVIEW_NOW,
        )
    }
}

@Preview(name = "23 课表 空态 · 浅色", showBackground = true, widthDp = 412, heightDp = 640)
@Composable
private fun TimetableEmptyPreview() {
    ClassToolsTheme(darkTheme = false) {
        TimetableViewScreen(
            onBack = {},
            repository = PreviewTimetableRepository,
            initialSnapshot = TtSnapshot(),
            skipLoad = true,
            nowMillis = PREVIEW_NOW,
        )
    }
}

@Preview(name = "23b 课表 空数据文案 · 浅色", showBackground = true, widthDp = 412, heightDp = 640)
@Composable
private fun TimetableEmptyDataCopyPreview() {
    ClassToolsTheme(darkTheme = false) {
        TimetableViewScreen(
            onBack = {},
            repository = PreviewTimetableRepository,
            initialError = "暂无课表数据",
            initialErrorKind = TtFailureKind.EMPTY,
            skipLoad = true,
            nowMillis = PREVIEW_NOW,
        )
    }
}

@Preview(name = "23c 课表 网络异常 · 深色", showBackground = true, widthDp = 412, heightDp = 640)
@Composable
private fun TimetableNetworkErrorPreview() {
    ClassToolsTheme(darkTheme = true) {
        TimetableViewScreen(
            onBack = {},
            repository = PreviewTimetableRepository,
            initialError = "网络不可用，请稍后重试",
            initialErrorKind = TtFailureKind.NETWORK,
            skipLoad = true,
            nowMillis = PREVIEW_NOW,
        )
    }
}

@Preview(name = "24 电费 仪表盘 · 浅色", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun PowerReadyLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        PowerScreen(
            onBack = {},
            repository = PreviewPowerRepository,
            initialSelectedRv = "4-23A---321",
            initialRow = powerRow(86.42),
            initialHistories = mapOf(
                PowerRange.H24 to previewHistories24h(),
                PowerRange.D7 to previewHistories7d(),
                PowerRange.D30 to previewHistories30d(),
            ),
            skipLoad = true,
        )
    }
}

@Preview(
    name = "24b 电费 仪表盘 · 大字体 1.3",
    showBackground = true,
    widthDp = 412,
    heightDp = 1100,
    fontScale = 1.3f,
)
@Composable
private fun PowerReadyLargeFontPreview() {
    ClassToolsTheme(darkTheme = false) {
        PowerScreen(
            onBack = {},
            repository = PreviewPowerRepository,
            initialSelectedRv = "4-23A---321",
            initialRow = powerRow(86.42),
            initialHistories = mapOf(
                PowerRange.H24 to previewHistories24h(),
                PowerRange.D7 to previewHistories7d(),
                PowerRange.D30 to previewHistories30d(),
            ),
            skipLoad = true,
        )
    }
}

@Preview(name = "25 电费 仪表盘 · 深色", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun PowerReadyDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        PowerScreen(
            onBack = {},
            repository = PreviewPowerRepository,
            initialSelectedRv = "4-23A---321",
            initialRow = powerRow(86.42),
            initialHistories = mapOf(
                PowerRange.H24 to previewHistories24h(),
                PowerRange.D7 to previewHistories7d(),
                PowerRange.D30 to previewHistories30d(),
            ),
            skipLoad = true,
        )
    }
}

@Preview(name = "26 电费 慢频房（13号楼日更） · 浅色", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun PowerSlowRoomPreview() {
    ClassToolsTheme(darkTheme = false) {
        PowerScreen(
            onBack = {},
            repository = PreviewPowerRepository,
            initialSelectedRv = "1-13---324",
            initialRow = powerRow(
                q = 42.5,
                rv = "1-13---324",
                label = "13号楼324",
                building = "13号楼",
            ),
            initialHistories = mapOf(
                PowerRange.H24 to previewSlowHistories24h(),
                PowerRange.D7 to previewSlowHistories24h(),
                PowerRange.D30 to previewSlowHistories30d(),
            ),
            initialRange = PowerRange.D30,
            initialSlowRoom = true,
            skipLoad = true,
        )
    }
}

@Preview(name = "27 电费 未选择宿舍 · 浅色", showBackground = true, widthDp = 412, heightDp = 640)
@Composable
private fun PowerIdlePreview() {
    ClassToolsTheme(darkTheme = false) {
        PowerScreen(
            onBack = {},
            repository = PreviewPowerRepository,
            skipLoad = true,
        )
    }
}

@Preview(name = "28 电费 错误态 · 深色", showBackground = true, widthDp = 412, heightDp = 640)
@Composable
private fun PowerErrorPreview() {
    ClassToolsTheme(darkTheme = true) {
        PowerScreen(
            onBack = {},
            repository = PreviewPowerRepository,
            initialSelectedRv = "4-23A---321",
            initialError = "网络不可用，请稍后重试",
            skipLoad = true,
        )
    }
}



