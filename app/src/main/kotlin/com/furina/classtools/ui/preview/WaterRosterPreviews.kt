package com.furina.classtools.ui.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.session.Session
import com.furina.classtools.features.roster.RollStatus
import com.furina.classtools.features.roster.RosterLogic
import com.furina.classtools.features.roster.RosterMember
import com.furina.classtools.features.roster.RosterState
import com.furina.classtools.features.water.WaterRecord
import com.furina.classtools.features.water.WaterRoomState
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.screens.roster.RollCallScreen
import com.furina.classtools.ui.screens.roster.RosterScreen
import com.furina.classtools.ui.screens.water.WaterMonthGrid
import com.furina.classtools.ui.screens.water.WaterScreen
import com.furina.classtools.ui.theme.ClassToolsTheme
import com.furina.classtools.ui.theme.CtShapeTokens

// =============================================================================
// 水量 / 名册 / 点名 预览（独立文件；Lead 用 android preview render 验收）
//
// - 24 水量 · 记录列表（浅色）
// - 25 水量 · 6 人间（深色）
// - 26 水量 · 空态（浅色）
// - 27 水量 · 月份选择器网格（浅色）
// - 28 名册 · 名单（浅色）
// - 29 名册 · 名单（深色）
// - 30 点名 · 进行中（浅色）
// - 31 点名 · 空名单（深色）
// =============================================================================

private val previewSession = Session(
    sid = "2024000001",
    name = "陈同学",
    token = "preview-token",
    role = "副团支部书记",
    badge = "班委",
)

private fun waterRooms(): Map<String, WaterRoomState> = mapOf(
    "1-13---301" to WaterRoomState(
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
    "4-23A---305" to WaterRoomState(
        persons = 6,
        records = listOf(
            WaterRecord("2026-09", 235.00),
            WaterRecord("2026-10", 240.20),
            WaterRecord("2026-11", 247.35),
        ),
    ),
)

private fun rosterMembers(): List<RosterMember> = listOf(
    RosterMember(1, "陈同学", "副团支部书记", "班委"),
    RosterMember(2, "张三", "班长", "班委"),
    RosterMember(3, "李四", "团支书", "班委"),
    RosterMember(4, "王五", "学习委员", "班委"),
    RosterMember(5, "赵六", "生活委员", "班委"),
    RosterMember(6, "钱七", "体育委员", "班委"),
    RosterMember(7, "孙八", "文娱委员", "班委"),
    RosterMember(8, "周九", "组织委员", "班委"),
    RosterMember(9, "吴十", "", ""),
    RosterMember(10, "郑十一", "", ""),
    RosterMember(11, "王十二", "", ""),
    RosterMember(12, "李十三", "", ""),
)

private fun previewRosterState(): RosterState {
    val members = rosterMembers()
    val roll = listOf(1, 2, 3, 4, 5, 6, 9, 10)
    return RosterState(
        rosterSel = (1..12).toList(),
        rollSel = roll,
        rollStatus = mapOf(0 to RollStatus.IN, 1 to RollStatus.IN, 2 to RollStatus.AB),
        rollNamesText = RosterLogic.buildRollNames(roll, members),
    )
}

@Preview(name = "24 水量 · 记录列表 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun WaterListLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        WaterScreen(
            onBack = {},
            session = previewSession,
            userDataRepository = null,
            initialRooms = waterRooms(),
            initialSelectedRv = "1-13---301",
            initialMonth = "2026-11",
            skipLoad = true,
        )
    }
}

@Preview(name = "25 水量 · 6 人间 · 深色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun WaterSixPersonsDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        WaterScreen(
            onBack = {},
            session = previewSession,
            userDataRepository = null,
            initialRooms = waterRooms(),
            initialSelectedRv = "4-23A---305",
            initialMonth = "2026-11",
            skipLoad = true,
        )
    }
}

@Preview(name = "26 水量 · 空态 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun WaterEmptyLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        WaterScreen(
            onBack = {},
            session = previewSession,
            userDataRepository = null,
            initialRooms = emptyMap(),
            initialSelectedRv = "",
            skipLoad = true,
        )
    }
}

@Preview(name = "27 水量 · 月份选择器 · 浅色", showBackground = true, widthDp = 360, heightDp = 520)
@Composable
private fun WaterMonthGridPreview() {
    ClassToolsTheme(darkTheme = false) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = CtShapeTokens.dialog,
            modifier = Modifier.padding(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CtTopBar(title = "选择抄表月份", onBack = {})
                WaterMonthGrid(
                    year = 2026,
                    month = "2026-11",
                    monthsWithRecords = setOf("2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11"),
                    onPick = {},
                    onYearChange = {},
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Preview(name = "28 名册 · 名单 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun RosterListLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        RosterScreen(
            onBack = {},
            session = previewSession,
            classMembersRepository = null,

            initialMembers = rosterMembers(),


            skipLoad = true,
        )
    }
}

@Preview(name = "29 名册 · 名单 · 深色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun RosterListDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        RosterScreen(
            onBack = {},
            session = previewSession,
            classMembersRepository = null,

            initialMembers = rosterMembers(),


            skipLoad = true,
        )
    }
}

@Preview(name = "30 点名 · 进行中 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun RollLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        RollCallScreen(
            onBack = {},
            session = previewSession,
            classMembersRepository = null,
            userDataRepository = null,
            initialMembers = rosterMembers(),
            initialRoster = previewRosterState(),
            skipLoad = true,
        )
    }
}

@Preview(name = "31 点名 · 空名单 · 深色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun RollEmptyDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        RollCallScreen(
            onBack = {},
            session = previewSession,
            classMembersRepository = null,
            userDataRepository = null,
            initialMembers = rosterMembers(),
            initialRoster = RosterState(),
            skipLoad = true,
        )
    }
}

@Preview(name = "32 名册 · 空态 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun RosterEmptyLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        RosterScreen(
            onBack = {},
            session = previewSession,
            classMembersRepository = null,

            initialMembers = emptyList(),


            skipLoad = true,
        )
    }
}