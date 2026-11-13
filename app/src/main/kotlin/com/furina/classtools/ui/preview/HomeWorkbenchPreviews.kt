package com.furina.classtools.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeRow
import com.furina.classtools.data.notices.NoticesData
import com.furina.classtools.data.power.PowerRow
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.timetable.Course
import com.furina.classtools.data.timetable.TtCalendar
import com.furina.classtools.data.timetable.TtMeta
import com.furina.classtools.data.timetable.TtPlan
import com.furina.classtools.data.timetable.TtSnapshot
import com.furina.classtools.data.timetable.TodayState
import com.furina.classtools.data.tool.ToolCatalog
import com.furina.classtools.ui.screens.all.AllToolsScreen
import com.furina.classtools.ui.screens.home.HomeScreen
import com.furina.classtools.ui.theme.ClassToolsTheme
import java.time.ZoneId

/**
 * 工作台 + 全部工具 v5 预览（参考 HFUT-Schedule / xiaoying 的布局与动效语言）。
 *
 * 全部使用注入状态（initial*），不读网络、不读 userdata：
 * - 工作台：上课中 / 下一节 / 今天没课 / 课表未同步 / 深色 / 平板双栏 1000dp / 平板 800dp /
 *   大字体 1.3x / 常用工具空态；
 * - 全部工具：浅色 2 列 / 深色 / 拖动中 / 大字体 / 平板 800dp（3 列）/ 平板 1000dp（4 列）。
 *
 * 预览不要网络：HomeScreen 不传 repository，全部走 initial* 注入；
 * 首屏错峰淡入在 `LocalInspectionMode` 下自动关闭，静态预览看到的是完整内容。
 */
private const val HOME_NOW_FRIDAY_1000: Long = 1_793_930_400_000L // 2026-11-06 10:00 +08
private const val HOME_NOW_FRIDAY_0830: Long = 1_793_925_000_000L // 2026-11-06 08:30 +08

private val homePreviewSession = Session(
    sid = "3260707024",
    name = "陈子麒",
    token = "preview-token",
    role = "副团支部书记",
    badge = "班委",
)

private val homePreviewSnapshot: TtSnapshot = TtSnapshot(
    c = listOf(
        Course("数据结构", "龚志伟", "明理楼(教室02201)", day = 4, start = 1, end = 2, weeks = "1-16"),
        Course("大学英语", "王老师", "明理楼(教室02301)", day = 4, start = 6, end = 7, weeks = "1-16"),
        Course("晚间选修", "李老师", "综合楼(教室04102)", day = 4, start = 10, end = 11, weeks = "1-16"),
        Course("高等数学", "刘老师", "自强楼(教室01104)", day = 0, start = 1, end = 2, weeks = "1-16"),
        Course("大学物理", "陈老师", "格物楼(教室03201)", day = 2, start = 3, end = 4, weeks = "1-16"),
    ),
    t = "2026-08-31",
    meta = TtMeta(source = "preview", count = 5, termStart = "2026-08-31"),
)

private val homePreviewNoClassSnapshot: TtSnapshot = TtSnapshot(
    c = listOf(
        Course("高等数学", "刘老师", "自强楼(教室01104)", day = 0, start = 1, end = 2, weeks = "1-16"),
        Course("大学物理", "陈老师", "格物楼(教室03201)", day = 2, start = 3, end = 4, weeks = "1-16"),
    ),
    t = "2026-08-31",
    meta = TtMeta(source = "preview", count = 2, termStart = "2026-08-31"),
)

private val homePreviewNotSyncedPlan = TtPlan(
    hasData = false,
    week = null,
    today = emptyList(),
    todayState = TodayState.NODATA,
    next = null,
)

private fun homePreviewPlan(snapshot: TtSnapshot, now: Long): TtPlan =
    TtCalendar.plan(snapshot, now, ZoneId.of("Asia/Shanghai"))

private val homePreviewHomework: List<HomeworkRow> = listOf(
    HomeworkRow(
        id = "hw-1",
        subject = "数据结构实验报告",
        content = "完成二叉树遍历实验并提交报告",
        due = "2026-11-09",
        date = "2026-11-09",
        time = "20:00",
        ts = HOME_NOW_FRIDAY_1000 - 3_600_000L,
    ),
    HomeworkRow(
        id = "hw-2",
        subject = "大学英语作文",
        content = "Unit 5 写作练习",
        due = "2026-11-10",
        date = "2026-11-10",
        time = "18:00",
        ts = HOME_NOW_FRIDAY_1000 - 7_200_000L,
    ),
    HomeworkRow(
        id = "hw-3",
        subject = "英语听力练习",
        content = "Unit 5 听力",
        due = "2026-11-07",
        date = "2026-11-07",
        time = "20:00",
        ts = HOME_NOW_FRIDAY_1000 - 9_000_000L,
    ),
)

private fun homePreviewNotices(): NoticesData {
    val auto = listOf(
        NoticeEntry(
            row = NoticeRow(id = "auto_g1", title = "期中教学检查通知", ts = HOME_NOW_FRIDAY_1000 - 2_000_000L),
            group = NoticeGroupDto(
                id = "g1",
                title = "关于开展期中教学检查的通知",
                cat = "教务",
                summary = "第 10-11 周开展期中教学检查",
            ),
            items = emptyList(),
            hidden = false,
            edited = false,
        ),
        NoticeEntry(
            row = NoticeRow(id = "auto_g2", title = "奖学金材料提交", ts = HOME_NOW_FRIDAY_1000 - 5_000_000L),
            group = NoticeGroupDto(
                id = "g2",
                title = "奖学金材料提交提醒",
                cat = "学工",
                summary = "请于 11 月 20 日前提交材料",
            ),
            items = emptyList(),
            hidden = false,
            edited = false,
        ),
    )
    return NoticesData(
        auto = auto,
        manual = emptyList(),
        hiddenCount = 0,
        fetchedAt = HOME_NOW_FRIDAY_1000,
        fromCache = false,
    )
}

private val homePreviewPowerRow = PowerRow(
    id = "4-23A---321",
    label = "23号楼A座 321",
    building = "23号楼A座",
    room = "321",
    q = 86.42,
    unit = "度",
    updatedAt = "2026-11-06T09:40:00+08:00",
)

@Composable
private fun HomePreviewFrame(
    nowMillis: Long,
    dark: Boolean,
    plan: TtPlan,
    emptyQuick: Boolean = false,
    quickToolIds: List<String> = ToolCatalog.defaultQuickIds,
) {
    ClassToolsTheme(darkTheme = dark) {
        HomeScreen(
            session = homePreviewSession,
            onOpenTool = {},
            onHomeToolsChange = {},
            quickToolIds = quickToolIds,
            initialTtPlan = plan,
            initialHomeworkRows = homePreviewHomework,
            initialNoticesData = homePreviewNotices(),
            initialPowerSel = "4-23A---321",
            initialPowerRow = homePreviewPowerRow,
            nowMillis = nowMillis,
            forceEmptyQuickTools = emptyQuick,
        )
    }
}

// =============================================================================
// 工作台
// =============================================================================

@Preview(name = "80 工作台 v5 · 上课中（浅色）", showBackground = true, widthDp = 412, heightDp = 1400)
@Composable
private fun HomeWorkbenchOngoingPreview() {
    HomePreviewFrame(
        nowMillis = HOME_NOW_FRIDAY_0830,
        dark = false,
        plan = homePreviewPlan(homePreviewSnapshot, HOME_NOW_FRIDAY_0830),
    )
}

@Preview(name = "81 工作台 v5 · 下一节（浅色）", showBackground = true, widthDp = 412, heightDp = 1400)
@Composable
private fun HomeWorkbenchNextPreview() {
    HomePreviewFrame(
        nowMillis = HOME_NOW_FRIDAY_1000,
        dark = false,
        plan = homePreviewPlan(homePreviewSnapshot, HOME_NOW_FRIDAY_1000),
    )
}

@Preview(name = "82 工作台 v5 · 深色", showBackground = true, widthDp = 412, heightDp = 1400)
@Composable
private fun HomeWorkbenchDarkPreview() {
    HomePreviewFrame(
        nowMillis = HOME_NOW_FRIDAY_1000,
        dark = true,
        plan = homePreviewPlan(homePreviewSnapshot, HOME_NOW_FRIDAY_1000),
    )
}

@Preview(name = "83 工作台 v5 · 大字体 1.3x", showBackground = true, widthDp = 412, heightDp = 1600, fontScale = 1.3f)
@Composable
private fun HomeWorkbenchLargeFontPreview() {
    HomePreviewFrame(
        nowMillis = HOME_NOW_FRIDAY_1000,
        dark = false,
        plan = homePreviewPlan(homePreviewSnapshot, HOME_NOW_FRIDAY_1000),
    )
}

@Preview(name = "84 工作台 v5 · 今天没课", showBackground = true, widthDp = 412, heightDp = 1300)
@Composable
private fun HomeWorkbenchNoClassPreview() {
    HomePreviewFrame(
        nowMillis = HOME_NOW_FRIDAY_1000,
        dark = false,
        plan = homePreviewPlan(homePreviewNoClassSnapshot, HOME_NOW_FRIDAY_1000),
    )
}

@Preview(name = "85 工作台 v5 · 课表未同步", showBackground = true, widthDp = 412, heightDp = 1200)
@Composable
private fun HomeWorkbenchNotSyncedPreview() {
    HomePreviewFrame(
        nowMillis = HOME_NOW_FRIDAY_1000,
        dark = false,
        plan = homePreviewNotSyncedPlan,
    )
}

@Preview(name = "86 工作台 v5 · 平板双栏（1000dp）", showBackground = true, widthDp = 1000, heightDp = 1100)
@Composable
private fun HomeWorkbenchTabletPreview() {
    HomePreviewFrame(
        nowMillis = HOME_NOW_FRIDAY_1000,
        dark = false,
        plan = homePreviewPlan(homePreviewSnapshot, HOME_NOW_FRIDAY_1000),
    )
}

@Preview(name = "87 工作台 v5 · 平板 800dp 单栏", showBackground = true, widthDp = 800, heightDp = 1200)
@Composable
private fun HomeWorkbenchMediumPreview() {
    HomePreviewFrame(
        nowMillis = HOME_NOW_FRIDAY_1000,
        dark = false,
        plan = homePreviewPlan(homePreviewSnapshot, HOME_NOW_FRIDAY_1000),
    )
}

@Preview(name = "88 工作台 v5 · 平板深色双栏", showBackground = true, widthDp = 1000, heightDp = 1100)
@Composable
private fun HomeWorkbenchTabletDarkPreview() {
    HomePreviewFrame(
        nowMillis = HOME_NOW_FRIDAY_1000,
        dark = true,
        plan = homePreviewPlan(homePreviewSnapshot, HOME_NOW_FRIDAY_1000),
    )
}

@Preview(name = "89 工作台 v5 · 常用工具空态", showBackground = true, widthDp = 412, heightDp = 1300)
@Composable
private fun HomeWorkbenchEmptyQuickPreview() {
    HomePreviewFrame(
        nowMillis = HOME_NOW_FRIDAY_1000,
        dark = false,
        plan = homePreviewPlan(homePreviewSnapshot, HOME_NOW_FRIDAY_1000),
        emptyQuick = true,
        quickToolIds = emptyList(),
    )
}

// =============================================================================
// 全部工具
// =============================================================================

private val allToolIds = ToolCatalog.tools.map { it.id }

@Preview(name = "90 全部工具 v5 · 浅色 2 列", showBackground = true, widthDp = 412, heightDp = 1200)
@Composable
private fun AllToolsLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        AllToolsScreen(onOpenTool = {}, skipRestore = true, initialOrder = allToolIds)
    }
}

@Preview(name = "91 全部工具 v5 · 深色 2 列", showBackground = true, widthDp = 412, heightDp = 1200)
@Composable
private fun AllToolsDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        AllToolsScreen(onOpenTool = {}, skipRestore = true, initialOrder = allToolIds)
    }
}

@Preview(name = "92 全部工具 v5 · 拖动中（浅色）", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun AllToolsDraggingPreview() {
    ClassToolsTheme(darkTheme = false) {
        AllToolsScreen(
            onOpenTool = {},
            skipRestore = true,
            initialOrder = allToolIds,
            previewDraggingToolId = "hw",
            previewDragOffset = Offset(206f, 430f),
        )
    }
}

@Preview(name = "93 全部工具 v5 · 大字体 1.3x", showBackground = true, widthDp = 412, heightDp = 1200, fontScale = 1.3f)
@Composable
private fun AllToolsLargeFontPreview() {
    ClassToolsTheme(darkTheme = false) {
        AllToolsScreen(onOpenTool = {}, skipRestore = true, initialOrder = allToolIds)
    }
}

@Preview(name = "94 全部工具 v5 · 平板 800dp（3 列）", showBackground = true, widthDp = 800, heightDp = 1000)
@Composable
private fun AllToolsMediumPreview() {
    ClassToolsTheme(darkTheme = false) {
        AllToolsScreen(onOpenTool = {}, skipRestore = true, initialOrder = allToolIds)
    }
}

@Preview(name = "95 全部工具 v5 · 平板 1000dp（4 列）", showBackground = true, widthDp = 1000, heightDp = 900)
@Composable
private fun AllToolsTabletPreview() {
    ClassToolsTheme(darkTheme = false) {
        AllToolsScreen(onOpenTool = {}, skipRestore = true, initialOrder = allToolIds)
    }
}

@Preview(name = "96 全部工具 v5 · 拖动中（深色 4 列）", showBackground = true, widthDp = 1000, heightDp = 900)
@Composable
private fun AllToolsTabletDraggingDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        AllToolsScreen(
            onOpenTool = {},
            skipRestore = true,
            initialOrder = allToolIds,
            previewDraggingToolId = "draw",
            previewDragOffset = Offset(500f, 360f),
        )
    }
}
