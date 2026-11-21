package com.furina.classtools.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.furina.classtools.data.member.ClassMember
import com.furina.classtools.data.session.Session
import com.furina.classtools.features.draw.DrawConfig
import com.furina.classtools.features.draw.DrawEngine
import com.furina.classtools.features.draw.DrawEntry
import com.furina.classtools.features.draw.DrawOutcome
import com.furina.classtools.features.draw.DrawResult
import com.furina.classtools.features.draw.DrawSortMode
import com.furina.classtools.features.draw.DrawStep
import com.furina.classtools.features.draw.SeededDrawRandom
import com.furina.classtools.features.fit.FitGrade
import com.furina.classtools.features.fit.FitHistory
import com.furina.classtools.features.fit.FitInput
import com.furina.classtools.features.fit.FitRecord
import com.furina.classtools.features.fit.FitScoring
import com.furina.classtools.features.fit.FitSex
import com.furina.classtools.features.group.GroupConfig
import com.furina.classtools.features.group.GroupEngine
import com.furina.classtools.features.group.GroupMember
import com.furina.classtools.features.group.GroupMode
import com.furina.classtools.features.group.GroupStep
import com.furina.classtools.features.group.SeededGroupRandom
import com.furina.classtools.ui.screens.draw.DrawScreen
import com.furina.classtools.ui.screens.fit.FitScreen
import com.furina.classtools.ui.screens.group.GroupScreen
import com.furina.classtools.ui.theme.ClassToolsTheme

/**
 * 工具页「简洁化 v1」预览（task-50）：抽签/分组/体测紧凑版（≤6 张）。
 *
 * 只走注入数据 + skipRestore，不读写 userdata、不发网络请求。
 */
private const val T50_NOW: Long = 1_794_016_800_000L // 2026-11-07 10:00 Asia/Shanghai

private val t50Session = Session(
    sid = "2024000001",
    name = "陈同学",
    token = "preview-token",
    role = "副团支部书记",
    badge = "班委",
)

private val t50DrawMembers: List<ClassMember> = listOf(
    ClassMember(seat = 1, sid = "s1", name = "张伟"),
    ClassMember(seat = 2, sid = "s2", name = "李四"),
    ClassMember(seat = 5, sid = "s5", name = "王五"),
    ClassMember(seat = 8, sid = "s8", name = "赵六"),
    ClassMember(seat = 12, sid = "s12", name = "钱七"),
    ClassMember(seat = 18, sid = "s18", name = "孙八"),
    ClassMember(seat = 23, sid = "s23", name = "周九"),
    ClassMember(seat = 31, sid = "s31", name = "吴十"),
)

private val t50DrawConfig = DrawConfig(
    start = 1,
    end = 50,
    count = 8,
    allowDuplicates = false,
    excludedText = "3,7,10-12",
    sort = DrawSortMode.DRAW_ORDER,
)

private fun t50DrawCandidates(): List<DrawEntry> =
    (1..50)
        .filter { it !in setOf(3, 7, 10, 11, 12) }
        .map { number ->
            DrawEntry(number = number, name = t50DrawMembers.firstOrNull { it.seat == number }?.name)
        }

private fun t50DrawResult(): DrawResult {
    val outcome = DrawEngine.drawFromCandidates(
        config = t50DrawConfig,
        candidates = t50DrawCandidates(),
        random = SeededDrawRandom(7L),
    )
    return (outcome as DrawOutcome.Success).result
}

private val t50GroupMembers: List<GroupMember> = listOf(
    "张伟", "王芳", "李娜", "刘洋", "陈静", "杨帆",
    "赵磊", "黄敏", "周涛", "吴婷", "徐强", "孙丽",
).mapIndexed { index, name -> GroupMember(seat = index + 1, name = name) }

private val t50GroupConfig = GroupConfig(mode = GroupMode.GROUPS, n = 4, shuffle = true)

private val t50GroupResult = GroupEngine.group(
    members = t50GroupMembers,
    config = t50GroupConfig,
    random = SeededGroupRandom(7),
)

private val t50FitInput = FitInput(
    sex = FitSex.M,
    grade = FitGrade.G12,
    h = "175", w = "68", vc = "4800", r50 = "7.1", sit = "18.5", jump = "250", pull = "15",
    durM = "3", durS = "40",
)

private fun t50FitHistory(): List<FitRecord> {
    val first = FitScoring.calc(t50FitInput)
    val saved1 = FitHistory.save(
        records = emptyList(),
        input = t50FitInput,
        calc = first,
        id = "fit-t50-preview-1",
        now = T50_NOW - 3 * 24 * 3600_000L,
        name = "期中体测",
    )
    val secondInput = t50FitInput.copy(vc = "5000", r50 = "6.9", pull = "16")
    val second = FitScoring.calc(secondInput)
    val saved2 = FitHistory.save(
        records = saved1.records,
        input = secondInput,
        calc = second,
        id = "fit-t50-preview-2",
        now = T50_NOW - 24 * 3600_000L,
        name = "补测",
    )
    return saved2.records
}

@Preview(name = "240 抽签 紧凑配置 · 浅色", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun ToolT50DrawConfigPreview() {
    ClassToolsTheme(darkTheme = false) {
        DrawScreen(
            onBack = {},
            session = t50Session,
            onExport = {},
            members = t50DrawMembers,
            initialConfig = t50DrawConfig,
            initialStep = DrawStep.CONFIG,
            skipRestore = true,
        )
    }
}

@Preview(name = "241 抽签 紧凑结果 · 浅色", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun ToolT50DrawResultPreview() {
    ClassToolsTheme(darkTheme = false) {
        DrawScreen(
            onBack = {},
            session = t50Session,
            onExport = {},
            members = t50DrawMembers,
            initialConfig = t50DrawConfig,
            initialStep = DrawStep.RESULT,
            initialCandidates = t50DrawCandidates(),
            initialResult = t50DrawResult(),
            skipRestore = true,
        )
    }
}

@Preview(name = "242 分组 紧凑配置 · 浅色", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun ToolT50GroupConfigPreview() {
    ClassToolsTheme(darkTheme = false) {
        GroupScreen(
            onBack = {},
            onExport = {},
            prevMembers = t50GroupMembers,
            initialConfig = t50GroupConfig,
            skipRestore = true,
            randomSeed = 7L,
        )
    }
}

@Preview(name = "243 分组 紧凑结果 · 浅色", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun ToolT50GroupResultPreview() {
    ClassToolsTheme(darkTheme = false) {
        GroupScreen(
            onBack = {},
            onExport = {},
            prevMembers = t50GroupMembers,
            initialConfig = t50GroupResult.config,
            initialResult = t50GroupResult,
            initialStep = GroupStep.RESULT,
            skipRestore = true,
            randomSeed = 7L,
        )
    }
}

@Preview(name = "244 体测 紧凑录入 · 浅色", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun ToolT50FitInputPreview() {
    ClassToolsTheme(darkTheme = false) {
        FitScreen(
            onBack = {},
            initialInput = t50FitInput,
            skipRestore = true,
            nowMillis = T50_NOW,
        )
    }
}

@Preview(name = "245 体测 紧凑评分+历史 · 深色", showBackground = true, widthDp = 412, heightDp = 1200)
@Composable
private fun ToolT50FitHistoryDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        FitScreen(
            onBack = {},
            initialInput = t50FitInput,
            initialHistory = t50FitHistory(),
            skipRestore = true,
            nowMillis = T50_NOW,
        )
    }
}
