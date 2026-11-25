package com.furina.classtools.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.member.MemberCategory
import com.furina.classtools.data.power.PowerHistoryResult
import com.furina.classtools.data.power.PowerPoint
import com.furina.classtools.data.power.PowerRange
import com.furina.classtools.data.power.PowerRepository
import com.furina.classtools.data.power.PowerRow
import com.furina.classtools.data.power.PowerRowResult
import com.furina.classtools.data.session.Session
import com.furina.classtools.features.roster.RosterMember
import com.furina.classtools.features.roster.RosterState
import com.furina.classtools.ui.components.NotificationPermissionBanner
import com.furina.classtools.ui.components.NotificationPermissionBannerState
import com.furina.classtools.ui.screens.power.PowerScreen
import com.furina.classtools.ui.screens.roster.RosterScreen
import com.furina.classtools.ui.theme.ClassToolsTheme
import com.furina.classtools.ui.theme.GradeColors

/**
 * v4 第三批预览：名单三分类、电费同期比较/13·16 隐藏 24h、全局通知权限横幅、体测等级语义色。
 */
private const val V4_PREVIEW_NOW: Long = 1_793_930_400_000L // 2026-11-06 10:00 +08（周五）

private val v4Session = Session(
    sid = "2024000001",
    name = "陈同学",
    token = "preview-token",
    role = "副团支部书记",
    badge = "班委",
)

private fun v4RosterMembers(): List<RosterMember> = listOf(
    RosterMember(1, "陈同学", "副团支部书记", "班委"),
    RosterMember(2, "张三", "班长", "班委"),
    RosterMember(3, "李四", "团支书", "班委"),
    RosterMember(4, "王五", "学习委员", "班委"),
    RosterMember(5, "赵六", "生活委员", "班委"),
    RosterMember(6, "钱七", "体育委员", "班委"),
    RosterMember(7, "吴十", "", ""),
    RosterMember(8, "郑十一", "", ""),
    RosterMember(9, "王十二", "", ""),
    RosterMember(10, "李十三", "", ""),
)

private fun v4RosterState(): RosterState = RosterState(
    rosterSel = (1..10).toList(),
    rollSel = listOf(1, 2, 3, 4, 5),
    rollStatus = mapOf(0 to com.furina.classtools.features.roster.RollStatus.IN),
    rollNamesText = "",
)

private fun v4LeagueMembers(): List<RosterMember> = listOf(
    RosterMember(1, "陈同学"),
    RosterMember(2, "张三"),
    RosterMember(3, "李四"),
    RosterMember(4, "王五"),
    RosterMember(5, "赵六"),
    RosterMember(6, "钱七"),
    RosterMember(7, "吴十"),
)

private fun v4HourOf(offsetHours: Int): Long = V4_PREVIEW_NOW + offsetHours * 3_600_000L

/** 7 天逐小时采样：今天下降更快（用于展示「较昨日同期 ↑」）。 */
private fun v4CompareHistories(): List<PowerPoint> {
    val points = ArrayList<PowerPoint>(7 * 24 + 1)
    var q = 500.0
    for (i in 7 * 24 downTo 0) {
        val drop = if (i <= 10) 0.30 else 0.12
        q -= drop
        points.add(PowerPoint(q = q, t = v4HourOf(-i)))
    }
    return points
}

private fun v4SlowHistories24h(): List<PowerPoint> =
    (0..23).map { i -> PowerPoint(q = 42.5, t = v4HourOf(i - 23)) }

private fun v4SlowHistories30d(): List<PowerPoint> = listOf(
    PowerPoint(q = 70.0, t = V4_PREVIEW_NOW - 29L * 24 * 3_600_000L),
    PowerPoint(q = 62.0, t = V4_PREVIEW_NOW - 14L * 24 * 3_600_000L),
    PowerPoint(q = 58.0, t = V4_PREVIEW_NOW - 7L * 24 * 3_600_000L),
    PowerPoint(q = 42.5, t = V4_PREVIEW_NOW - 24L * 3_600_000L),
)

private fun v4PowerRow(
    q: Double = 86.42,
    rv: String = "4-23A---305",
    label: String = "23号楼A座305",
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

private object V4PreviewPowerRepository : PowerRepository {
    override suspend fun loadRow(rv: String, force: Boolean): PowerRowResult =
        PowerRowResult.Success(v4PowerRow(), fromCache = false)

    override suspend fun loadHistory(rv: String, range: PowerRange, force: Boolean): PowerHistoryResult =
        PowerHistoryResult.Success(range = range, points = v4CompareHistories(), fromCache = false)

    override suspend fun clearCache() = Unit
}

// =============================================================================
// 名单三分类
// =============================================================================

@Composable
private fun RosterPreview(
    dark: Boolean,
    category: MemberCategory,
    league: List<RosterMember> = v4LeagueMembers(),
    members: List<RosterMember> = v4RosterMembers(),
) {
    ClassToolsTheme(darkTheme = dark) {
        RosterScreen(
            onBack = {},
            session = v4Session,
            classMembersRepository = null,

            initialMembers = members,

            initialCategory = category,
            initialLeagueMembers = league,
            skipLeagueLoad = true,
            skipLoad = true,
        )
    }
}

@Preview(name = "160 名册 · 班委分类 · 浅色", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun RosterCommitteeLightPreview() {
    RosterPreview(dark = false, category = MemberCategory.COMMITTEE)
}

@Preview(name = "161 名册 · 团员分类 · 深色", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun RosterLeagueDarkPreview() {
    RosterPreview(dark = true, category = MemberCategory.LEAGUE)
}

@Preview(name = "162 名册 · 全班分类 · 平板", showBackground = true, widthDp = 1000, heightDp = 800)
@Composable
private fun RosterAllTabletPreview() {
    RosterPreview(dark = false, category = MemberCategory.ALL)
}

@Preview(name = "163 名册 · 团员空态 · 深色", showBackground = true, widthDp = 412, heightDp = 700)
@Composable
private fun RosterLeagueEmptyPreview() {
    ClassToolsTheme(darkTheme = true) {
        RosterScreen(
            onBack = {},
            session = v4Session,
            classMembersRepository = null,

            initialMembers = v4RosterMembers(),

            initialCategory = MemberCategory.LEAGUE,
            initialLeagueMembers = emptyList(),
            skipLeagueLoad = true,
            skipLoad = true,
        )
    }
}

@Preview(
    name = "164 名册 · 大字体 1.3x",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
    fontScale = 1.3f,
)
@Composable
private fun RosterLargeFontPreview() {
    RosterPreview(dark = false, category = MemberCategory.ALL)
}

// =============================================================================
// 电费：同期比较 + 13/16 隐藏 24h
// =============================================================================

@Preview(name = "165 电费 · 同期比较徽标 · 浅色", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun PowerCompareLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        PowerScreen(
            onBack = {},
            repository = V4PreviewPowerRepository,
            clock = { V4_PREVIEW_NOW },
            initialSelectedRv = "4-23A---305",
            initialRow = v4PowerRow(),
            initialHistories = mapOf(PowerRange.D7 to v4CompareHistories()),
            initialRange = PowerRange.H24,
            skipLoad = true,
        )
    }
}

@Preview(name = "166 电费 · 同期比较徽标 · 深色", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun PowerCompareDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        PowerScreen(
            onBack = {},
            repository = V4PreviewPowerRepository,
            clock = { V4_PREVIEW_NOW },
            initialSelectedRv = "4-23A---305",
            initialRow = v4PowerRow(),
            initialHistories = mapOf(PowerRange.D7 to v4CompareHistories()),
            initialRange = PowerRange.H24,
            skipLoad = true,
        )
    }
}

@Preview(name = "167 电费 · 13号楼隐藏 24h · 浅色", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun PowerSlowHiddenLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        PowerScreen(
            onBack = {},
            repository = V4PreviewPowerRepository,
            clock = { V4_PREVIEW_NOW },
            initialSelectedRv = "1-13---301",
            initialRow = v4PowerRow(q = 42.5, rv = "1-13---301", label = "13号楼301", building = "13号楼"),
            initialHistories = mapOf(
                PowerRange.H24 to v4SlowHistories24h(),
                PowerRange.D7 to v4SlowHistories24h(),
                PowerRange.D30 to v4SlowHistories30d(),
            ),
            initialRange = PowerRange.H24,
            initialSlowRoom = true,
            skipLoad = true,
        )
    }
}

@Preview(name = "168 电费 · 13号楼隐藏 24h · 深色", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun PowerSlowHiddenDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        PowerScreen(
            onBack = {},
            repository = V4PreviewPowerRepository,
            clock = { V4_PREVIEW_NOW },
            initialSelectedRv = "1-16---303",
            initialRow = v4PowerRow(q = 30.5, rv = "1-16---303", label = "16号楼303", building = "16号楼"),
            initialHistories = mapOf(
                PowerRange.H24 to v4SlowHistories24h(),
                PowerRange.D7 to v4SlowHistories24h(),
                PowerRange.D30 to v4SlowHistories30d(),
            ),
            initialRange = PowerRange.H24,
            initialSlowRoom = true,
            skipLoad = true,
        )
    }
}

// =============================================================================
// 全局：通知权限横幅 / 体测等级色
// =============================================================================

@Preview(name = "169 通知权限横幅 · 浅/深", showBackground = true, widthDp = 412, heightDp = 320)
@Composable
private fun NotificationPermissionBannerPreview() {
    NotificationPermissionBannerState.reset()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(12.dp)) {
        ClassToolsTheme(darkTheme = false) {
            NotificationPermissionBanner(visible = true, onOpenPermissions = {})
        }
        ClassToolsTheme(darkTheme = true) {
            Surface(color = MaterialTheme.colorScheme.background) {
                NotificationPermissionBanner(visible = true, onOpenPermissions = {})
            }
        }
    }
}

@Preview(name = "170 体测等级语义色 · 浅/深", showBackground = true, widthDp = 412, heightDp = 420)
@Composable
private fun GradeColorsPreview() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GradeColorRow(title = "浅色", dark = false)
        GradeColorRow(title = "深色", dark = true)
    }
}

@Composable
private fun GradeColorRow(title: String, dark: Boolean) {
    ClassToolsTheme(darkTheme = dark) {
        val palette = if (dark) GradeColors.dark else GradeColors.light
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (dark) Color(0xFF111414) else MaterialTheme.colorScheme.background,
                    RoundedCornerShape(16.dp),
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = if (dark) Color.White else Color.Black,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("优秀" to palette.excellent, "良好" to palette.good, "及格" to palette.pass, "不及格" to palette.fail)
                    .forEach { (label, tone) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = tone.container,
                            contentColor = tone.content,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                    }
            }
        }
    }
}

@Suppress("unused")
private val v4PreviewHighlights: List<String> = listOf(
    "名单三分类",
    "电费同期比较",
    "13/16 隐藏 24h",
    "通知权限横幅",
    "体测等级色",
)

@Suppress("unused")
private val v4PreviewHeight = 900.dp
