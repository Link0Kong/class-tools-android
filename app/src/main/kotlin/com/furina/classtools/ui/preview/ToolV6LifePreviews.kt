package com.furina.classtools.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.furina.classtools.data.homework.HomeworkFilter
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.power.PowerHistoryResult
import com.furina.classtools.data.power.PowerPoint
import com.furina.classtools.data.power.PowerRange
import com.furina.classtools.data.power.PowerRepository
import com.furina.classtools.data.power.PowerRow
import com.furina.classtools.data.power.PowerRowResult
import com.furina.classtools.data.session.Session
import com.furina.classtools.features.water.WaterRecord
import com.furina.classtools.features.water.WaterRoomState
import com.furina.classtools.ui.screens.homework.HomeworkScreen
import com.furina.classtools.ui.screens.power.PowerScreen
import com.furina.classtools.ui.screens.water.WaterScreen
import com.furina.classtools.ui.theme.ClassToolsTheme
import java.time.LocalDate
import java.time.ZoneId

/**
 * 工具简洁化 v1 预览（作业 / 电费 / 水量）。
 *
 * 共 6 张（≤6）：覆盖「滑动切换后的目标状态」+ 浅/深色：
 * - 170 作业 · 全部页 · 浅色；
 * - 171 作业 · 已完成页（左右滑动可达）· 深色；
 * - 172 电费 · 24h · 浅色；
 * - 173 电费 · 7 天（左右滑动切换后）· 深色；
 * - 174 水量 · 当月 · 浅色；
 * - 175 水量 · 上一月（左右滑动切换后）· 深色。
 */
private val v6Session = Session(
    sid = "3260707024",
    name = "陈子麒",
    token = "preview-token",
    role = "副团支部书记",
    badge = "班委",
)

private const val V6_PREVIEW_NOW: Long = 1_793_930_400_000L // 2026-11-06 10:00 +08（周五）

// =============================================================================
// 作业数据
// =============================================================================

private fun v6DateText(daysFromNow: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    LocalDate.now(zone).plusDays(daysFromNow).toString()

private fun v6HomeworkRows(): List<HomeworkRow> {
    val now = System.currentTimeMillis()
    return listOf(
        HomeworkRow(
            id = "hw-1",
            subject = "高等数学",
            content = "第三章习题 3.1–3.5，写在作业本上，下节课交。",
            due = "明天 23:59",
            date = v6DateText(1),
            time = "23:59",
            author = "学习委员",
            owner = "3260707001",
            attachments = """[{"n":"第三章习题.pdf","t":"application/pdf","s":245760,"d":"data:application/pdf;base64,AAAA"}]""",
            ts = now - 3_600_000,
        ),
        HomeworkRow(
            id = "hw-2",
            subject = "C 语言程序设计",
            content = "实验 4：循环结构（提交 .c 源码 + 运行截图）。",
            due = "3 天后",
            date = v6DateText(3),
            time = "18:00",
            author = "陈子麒",
            ts = now - 7_200_000,
        ),
        HomeworkRow(
            id = "hw-3",
            subject = "英语 1",
            content = "Unit 5 单词抄写 2 遍。",
            due = "已截止",
            date = v6DateText(-1),
            time = "20:00",
            author = "英语课代表",
            ts = now - 86_400_000 * 2,
        ),
        HomeworkRow(
            id = "hw-4",
            subject = "体育 1",
            content = "800 米 / 1000 米测试，穿运动服。",
            date = "",
            due = "下周上课时",
            author = "体育委员",
            ts = now - 86_400_000,
        ),
    )
}

private val v6Marks: MarksSnapshot = MarksSnapshot(hwDone = mapOf("hw-3" to true))

// =============================================================================
// 电费数据
// =============================================================================

private fun v6PowerRow(
    q: Double = 86.42,
    rv: String = "4-23A---321",
    label: String = "23号楼A座321",
    building: String = "23号楼A座",
): PowerRow = PowerRow(
    id = rv,
    label = label,
    building = building,
    room = label.takeLast(3),
    q = q,
    unit = "度",
    updatedAt = "2026-11-06T02:00:00+00:00",
)

private fun v6Histories24h(endQ: Double = 86.42): List<PowerPoint> =
    (0..24).map { i ->
        PowerPoint(q = endQ + (24 - i) * 0.55, t = V6_PREVIEW_NOW - (24 - i) * 3_600_000L)
    }

private fun v6Histories7d(endQ: Double = 86.42): List<PowerPoint> =
    (0..(7 * 8)).map { i ->
        PowerPoint(q = endQ + (7 * 8 - i) * 0.10, t = V6_PREVIEW_NOW - (7 * 8 - i) * 3L * 3_600_000L)
    }

private fun v6Histories30d(endQ: Double = 86.42): List<PowerPoint> =
    (0..30).map { i ->
        PowerPoint(q = endQ + (30 - i) * 0.9, t = V6_PREVIEW_NOW - (30 - i) * 24L * 3_600_000L)
    }

private object V6PreviewPowerRepository : PowerRepository {
    override suspend fun loadRow(rv: String, force: Boolean): PowerRowResult =
        PowerRowResult.Success(v6PowerRow(), fromCache = false)

    override suspend fun loadHistory(rv: String, range: PowerRange, force: Boolean): PowerHistoryResult =
        PowerHistoryResult.Success(
            points = when (range) {
                PowerRange.H24 -> v6Histories24h()
                PowerRange.D7 -> v6Histories7d()
                PowerRange.D30 -> v6Histories30d()
            },
            range = range,
            fromCache = false,
        )

    override suspend fun clearCache() = Unit
}

// =============================================================================
// 水量数据
// =============================================================================

private fun v6WaterRooms(): Map<String, WaterRoomState> = mapOf(
    "1-13---324" to WaterRoomState(
        persons = 4,
        records = listOf(
            WaterRecord("2026-06", 112.40),
            WaterRecord("2026-07", 116.80),
            WaterRecord("2026-08", 118.40),
            WaterRecord("2026-09", 121.20),
            WaterRecord("2026-10", 125.75),
            WaterRecord("2026-11", 130.75),
        ),
    ),
    "4-23A---321" to WaterRoomState(
        persons = 6,
        records = listOf(
            WaterRecord("2026-09", 235.00),
            WaterRecord("2026-10", 240.20),
            WaterRecord("2026-11", 247.35),
        ),
    ),
)

// =============================================================================
// 预览
// =============================================================================

@Preview(name = "170 作业 v1 · 全部页 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun HomeworkV1AllLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        HomeworkScreen(
            onBack = {},
            session = v6Session,
            marksRepository = null,
            homeworkRepository = null,
            initialRows = v6HomeworkRows(),
            initialMarks = v6Marks,
            skipLoad = true,
            onRemindHomework = {},
        )
    }
}

@Preview(name = "171 作业 v1 · 已完成页（滑动切换后）· 深色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun HomeworkV1DoneDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        HomeworkScreen(
            onBack = {},
            session = v6Session,
            marksRepository = null,
            homeworkRepository = null,
            initialRows = v6HomeworkRows(),
            initialMarks = v6Marks,
            skipLoad = true,
            onRemindHomework = {},
            initialFilter = HomeworkFilter.DONE,
        )
    }
}

@Preview(name = "172 电费 v1 · 24h · 浅色", showBackground = true, widthDp = 412, heightDp = 1400)
@Composable
private fun PowerV1LightPreview() {
    ClassToolsTheme(darkTheme = false) {
        PowerScreen(
            onBack = {},
            repository = V6PreviewPowerRepository,
            clock = { V6_PREVIEW_NOW },
            initialSelectedRv = "4-23A---321",
            initialRow = v6PowerRow(),
            initialHistories = mapOf(
                PowerRange.H24 to v6Histories24h(),
                PowerRange.D7 to v6Histories7d(),
                PowerRange.D30 to v6Histories30d(),
            ),
            initialRange = PowerRange.H24,
            skipLoad = true,
        )
    }
}

@Preview(name = "173 电费 v1 · 7 天（滑动切换后）· 深色", showBackground = true, widthDp = 412, heightDp = 1400)
@Composable
private fun PowerV1SevenDaysDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        PowerScreen(
            onBack = {},
            repository = V6PreviewPowerRepository,
            clock = { V6_PREVIEW_NOW },
            initialSelectedRv = "4-23A---321",
            initialRow = v6PowerRow(),
            initialHistories = mapOf(
                PowerRange.H24 to v6Histories24h(),
                PowerRange.D7 to v6Histories7d(),
                PowerRange.D30 to v6Histories30d(),
            ),
            initialRange = PowerRange.D7,
            skipLoad = true,
        )
    }
}

@Preview(name = "174 水量 v1 · 当月 · 浅色", showBackground = true, widthDp = 412, heightDp = 1400)
@Composable
private fun WaterV1CurrentLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        WaterScreen(
            onBack = {},
            session = v6Session,
            userDataRepository = null,
            initialRooms = v6WaterRooms(),
            initialSelectedRv = "1-13---324",
            initialMonth = "2026-11",
            skipLoad = true,
        )
    }
}

@Preview(name = "175 水量 v1 · 上一月（滑动切换后）· 深色", showBackground = true, widthDp = 412, heightDp = 1400)
@Composable
private fun WaterV1PrevMonthDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        WaterScreen(
            onBack = {},
            session = v6Session,
            userDataRepository = null,
            initialRooms = v6WaterRooms(),
            initialSelectedRv = "4-23A---321",
            initialMonth = "2026-10",
            skipLoad = true,
        )
    }
}
