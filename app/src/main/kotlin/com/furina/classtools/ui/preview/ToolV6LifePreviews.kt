package com.furina.classtools.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
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
 * 工具页 v6 迁移预览：作业 / 电费 / 水量（统一 CtToolCard / 统计 / 筛选 / 空态）。
 *
 * - 作业：列表、空态、深色、平板、大字体；
 * - 电费：有数据、深色、空态（未关注）、13 号楼日更（隐藏 24h）、平板；
 * - 水量：记录（4 人间）、6 人间深色、空态、大字体。
 *
 * 全部用注入状态（initial*），不读网络 / 不读 userdata。
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

/** 近 24h 逐小时下降（末点 = 当前读数）。 */
private fun v6Histories24h(endQ: Double = 86.42): List<PowerPoint> =
    (0..24).map { i ->
        PowerPoint(q = endQ + (24 - i) * 0.55, t = V6_PREVIEW_NOW - (24 - i) * 3_600_000L)
    }

/** 近 7 天逐 3 小时下降。 */
private fun v6Histories7d(endQ: Double = 86.42): List<PowerPoint> =
    (0..(7 * 8)).map { i ->
        PowerPoint(q = endQ + (7 * 8 - i) * 0.10, t = V6_PREVIEW_NOW - (7 * 8 - i) * 3L * 3_600_000L)
    }

/** 近 30 天逐日下降。 */
private fun v6Histories30d(endQ: Double = 86.42): List<PowerPoint> =
    (0..30).map { i ->
        PowerPoint(q = endQ + (30 - i) * 0.9, t = V6_PREVIEW_NOW - (30 - i) * 24L * 3_600_000L)
    }

/** 13 号楼慢频（日更）：24h 基本无变化，30d 有几次真实下降。 */
private fun v6SlowHistories24h(): List<PowerPoint> =
    (0..23).map { i -> PowerPoint(q = 42.5, t = V6_PREVIEW_NOW - (23 - i) * 3_600_000L) }

private fun v6SlowHistories7d(): List<PowerPoint> =
    (0..27).map { i -> PowerPoint(q = 42.5, t = V6_PREVIEW_NOW - (27 - i) * 6L * 3_600_000L) }

private fun v6SlowHistories30d(): List<PowerPoint> = listOf(
    PowerPoint(q = 70.0, t = V6_PREVIEW_NOW - 29L * 24 * 3_600_000L),
    PowerPoint(q = 62.0, t = V6_PREVIEW_NOW - 14L * 24 * 3_600_000L),
    PowerPoint(q = 58.0, t = V6_PREVIEW_NOW - 7L * 24 * 3_600_000L),
    PowerPoint(q = 42.5, t = V6_PREVIEW_NOW - 24L * 3_600_000L),
)

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
// 作业预览
// =============================================================================

@Composable
private fun HomeworkV6Preview(dark: Boolean, rows: List<HomeworkRow> = v6HomeworkRows()) {
    ClassToolsTheme(darkTheme = dark) {
        HomeworkScreen(
            onBack = {},
            session = v6Session,
            marksRepository = null,
            homeworkRepository = null,
            initialRows = rows,
            initialMarks = v6Marks,
            skipLoad = true,
            onRemindHomework = {},
        )
    }
}

@Preview(name = "170 作业 v6 · 列表 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun HomeworkV6LightPreview() {
    HomeworkV6Preview(dark = false)
}

@Preview(name = "171 作业 v6 · 空态 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun HomeworkV6EmptyPreview() {
    HomeworkV6Preview(dark = false, rows = emptyList())
}

@Preview(name = "172 作业 v6 · 列表 · 深色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun HomeworkV6DarkPreview() {
    HomeworkV6Preview(dark = true)
}

@Preview(name = "173 作业 v6 · 平板 1000dp", showBackground = true, widthDp = 1000, heightDp = 800)
@Composable
private fun HomeworkV6TabletPreview() {
    HomeworkV6Preview(dark = false)
}

@Preview(name = "174 作业 v6 · 大字体 1.3x", showBackground = true, widthDp = 412, heightDp = 1200, fontScale = 1.3f)
@Composable
private fun HomeworkV6LargeFontPreview() {
    HomeworkV6Preview(dark = false)
}

// =============================================================================
// 电费预览
// =============================================================================

@Composable
private fun PowerV6Preview(
    dark: Boolean,
    selectedRv: String? = "4-23A---321",
    row: PowerRow? = v6PowerRow(),
    histories: Map<PowerRange, List<PowerPoint>> = mapOf(
        PowerRange.H24 to v6Histories24h(),
        PowerRange.D7 to v6Histories7d(),
        PowerRange.D30 to v6Histories30d(),
    ),
    slowRoom: Boolean = false,
) {
    ClassToolsTheme(darkTheme = dark) {
        PowerScreen(
            onBack = {},
            repository = V6PreviewPowerRepository,
            clock = { V6_PREVIEW_NOW },
            initialSelectedRv = selectedRv,
            initialRow = row,
            initialHistories = histories,
            initialSlowRoom = slowRoom,
            skipLoad = true,
        )
    }
}

@Preview(name = "175 电费 v6 · 有数据 · 浅色", showBackground = true, widthDp = 412, heightDp = 1400)
@Composable
private fun PowerV6LightPreview() {
    PowerV6Preview(dark = false)
}

@Preview(name = "176 电费 v6 · 有数据 · 深色", showBackground = true, widthDp = 412, heightDp = 1400)
@Composable
private fun PowerV6DarkPreview() {
    PowerV6Preview(dark = true)
}

@Preview(name = "177 电费 v6 · 空态（未关注）· 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun PowerV6EmptyPreview() {
    PowerV6Preview(
        dark = false,
        selectedRv = "",
        row = null,
        histories = emptyMap(),
    )
}

@Preview(name = "178 电费 v6 · 13 号楼日更（隐藏 24h）", showBackground = true, widthDp = 412, heightDp = 1400)
@Composable
private fun PowerV6SlowRoomPreview() {
    PowerV6Preview(
        dark = false,
        selectedRv = "1-13---324",
        row = v6PowerRow(q = 42.5, rv = "1-13---324", label = "13号楼324", building = "13号楼"),
        histories = mapOf(
            PowerRange.H24 to v6SlowHistories24h(),
            PowerRange.D7 to v6SlowHistories7d(),
            PowerRange.D30 to v6SlowHistories30d(),
        ),
        slowRoom = true,
    )
}

@Preview(name = "179 电费 v6 · 平板 1000dp", showBackground = true, widthDp = 1000, heightDp = 1000)
@Composable
private fun PowerV6TabletPreview() {
    PowerV6Preview(dark = false)
}

// =============================================================================
// 水量预览
// =============================================================================

@Composable
private fun WaterV6Preview(
    dark: Boolean,
    selectedRv: String = "1-13---324",
    initialMonth: String = "2026-11",
) {
    ClassToolsTheme(darkTheme = dark) {
        WaterScreen(
            onBack = {},
            session = v6Session,
            userDataRepository = null,
            initialRooms = v6WaterRooms(),
            initialSelectedRv = selectedRv,
            initialMonth = initialMonth,
            skipLoad = true,
        )
    }
}

@Preview(name = "180 水量 v6 · 记录 · 浅色", showBackground = true, widthDp = 412, heightDp = 1400)
@Composable
private fun WaterV6LightPreview() {
    WaterV6Preview(dark = false)
}

@Preview(name = "181 水量 v6 · 6 人间 · 深色", showBackground = true, widthDp = 412, heightDp = 1400)
@Composable
private fun WaterV6SixPersonsDarkPreview() {
    WaterV6Preview(dark = true, selectedRv = "4-23A---321")
}

@Preview(name = "182 水量 v6 · 空态 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun WaterV6EmptyPreview() {
    ClassToolsTheme(darkTheme = false) {
        WaterScreen(
            onBack = {},
            session = v6Session,
            userDataRepository = null,
            initialRooms = emptyMap(),
            initialSelectedRv = "",
            skipLoad = true,
        )
    }
}

@Preview(name = "183 水量 v6 · 大字体 1.3x", showBackground = true, widthDp = 412, heightDp = 1600, fontScale = 1.3f)
@Composable
private fun WaterV6LargeFontPreview() {
    WaterV6Preview(dark = false)
}

@Preview(name = "184 水量 v6 · 平板 1000dp", showBackground = true, widthDp = 1000, heightDp = 900)
@Composable
private fun WaterV6TabletPreview() {
    WaterV6Preview(dark = false)
}
