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
 * v6 工具页核心预览：抽签 / 分组 / 体测（统一卡片/表单/步骤/按钮后）。
 *
 * 对应 task-45；内容只走注入数据 + skipRestore，不读写 userdata、不发网络请求。
 * 旧预览 `DrawPreviews.kt` / `GroupFitPreviews.kt` 保留（回归对照）。
 */
private const val V6_NOW: Long = 1_794_016_800_000L // 2026-11-07 10:00 Asia/Shanghai

private val v6Session = Session(
    sid = "3260707024",
    name = "陈子麒",
    token = "preview-token",
    role = "副团支部书记",
    badge = "班委",
)

private val v6DrawMembers: List<ClassMember> = listOf(
    ClassMember(seat = 1, sid = "s1", name = "张伟"),
    ClassMember(seat = 2, sid = "s2", name = "李四"),
    ClassMember(seat = 5, sid = "s5", name = "王五"),
    ClassMember(seat = 8, sid = "s8", name = "赵六"),
    ClassMember(seat = 12, sid = "s12", name = "钱七"),
    ClassMember(seat = 18, sid = "s18", name = "孙八"),
    ClassMember(seat = 23, sid = "s23", name = "周九"),
    ClassMember(seat = 31, sid = "s31", name = "吴十"),
)

private val v6DrawConfig = DrawConfig(
    start = 1,
    end = 50,
    count = 8,
    allowDuplicates = false,
    excludedText = "3,7,10-12",
    sort = DrawSortMode.DRAW_ORDER,
)

private fun v6DrawCandidates(): List<DrawEntry> =
    (1..50)
        .filter { it !in setOf(3, 7, 10, 11, 12) }
        .map { number ->
            DrawEntry(number = number, name = v6DrawMembers.firstOrNull { it.seat == number }?.name)
        }

private fun v6DrawResult(): DrawResult {
    val outcome = DrawEngine.drawFromCandidates(
        config = v6DrawConfig,
        candidates = v6DrawCandidates(),
        random = SeededDrawRandom(7L),
    )
    return (outcome as DrawOutcome.Success).result
}

private val v6GroupMembers: List<GroupMember> = listOf(
    "张伟", "王芳", "李娜", "刘洋", "陈静", "杨帆",
    "赵磊", "黄敏", "周涛", "吴婷", "徐强", "孙丽",
).mapIndexed { index, name -> GroupMember(seat = index + 1, name = name) }

private val v6GroupConfig = GroupConfig(mode = GroupMode.GROUPS, n = 4, shuffle = true)

private val v6GroupResult = GroupEngine.group(
    members = v6GroupMembers,
    config = v6GroupConfig,
    random = SeededGroupRandom(7),
)

private val v6FitInput = FitInput(
    sex = FitSex.M,
    grade = FitGrade.G12,
    h = "175", w = "68", vc = "4800", r50 = "7.1", sit = "18.5", jump = "250", pull = "15",
    durM = "3", durS = "40",
)

private fun v6FitHistory(): List<FitRecord> {
    val first = FitScoring.calc(v6FitInput)
    val saved1 = FitHistory.save(
        records = emptyList(),
        input = v6FitInput,
        calc = first,
        id = "fit-v6-preview-1",
        now = V6_NOW - 3 * 24 * 3600_000L,
        name = "期中体测",
    )
    val secondInput = v6FitInput.copy(vc = "5000", r50 = "6.9", pull = "16")
    val second = FitScoring.calc(secondInput)
    val saved2 = FitHistory.save(
        records = saved1.records,
        input = secondInput,
        calc = second,
        id = "fit-v6-preview-2",
        now = V6_NOW - 24 * 3600_000L,
        name = "补测",
    )
    return saved2.records
}

// =============================================================================
// 抽签
// =============================================================================

@Preview(name = "220 抽签 v6 配置 · 浅色", showBackground = true, widthDp = 412, heightDp = 960)
@Composable
private fun ToolV6DrawConfigPreview() {
    ClassToolsTheme(darkTheme = false) {
        DrawScreen(
            onBack = {},
            session = v6Session,
            onExport = {},
            members = v6DrawMembers,
            initialConfig = v6DrawConfig,
            initialStep = DrawStep.CONFIG,
            skipRestore = true,
        )
    }
}

@Preview(name = "221 抽签 v6 候选 · 浅色", showBackground = true, widthDp = 412, heightDp = 960)
@Composable
private fun ToolV6DrawPreviewPreview() {
    ClassToolsTheme(darkTheme = false) {
        DrawScreen(
            onBack = {},
            session = v6Session,
            onExport = {},
            members = v6DrawMembers,
            initialConfig = v6DrawConfig,
            initialStep = DrawStep.PREVIEW,
            initialCandidates = v6DrawCandidates(),
            skipRestore = true,
        )
    }
}

@Preview(name = "222 抽签 v6 结果 · 深色", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun ToolV6DrawResultDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        DrawScreen(
            onBack = {},
            session = v6Session,
            onExport = {},
            members = v6DrawMembers,
            initialConfig = v6DrawConfig.copy(sort = DrawSortMode.DESCENDING),
            initialStep = DrawStep.RESULT,
            initialCandidates = v6DrawCandidates(),
            initialResult = v6DrawResult(),
            skipRestore = true,
        )
    }
}

@Preview(
    name = "223 抽签 v6 配置 · 平板",
    showBackground = true,
    widthDp = 840,
    heightDp = 1024,
)
@Composable
private fun ToolV6DrawConfigWidePreview() {
    ClassToolsTheme(darkTheme = false) {
        DrawScreen(
            onBack = {},
            session = v6Session,
            onExport = {},
            members = v6DrawMembers,
            initialConfig = v6DrawConfig,
            initialStep = DrawStep.CONFIG,
            skipRestore = true,
        )
    }
}

@Preview(
    name = "224 抽签 v6 配置 · 大字体 1.3x",
    showBackground = true,
    widthDp = 412,
    heightDp = 1100,
    fontScale = 1.3f,
)
@Composable
private fun ToolV6DrawConfigLargeFontPreview() {
    ClassToolsTheme(darkTheme = false) {
        DrawScreen(
            onBack = {},
            session = v6Session,
            onExport = {},
            members = v6DrawMembers,
            initialConfig = v6DrawConfig,
            initialStep = DrawStep.CONFIG,
            skipRestore = true,
        )
    }
}

// =============================================================================
// 分组
// =============================================================================

@Preview(name = "225 分组 v6 配置 · 浅色", showBackground = true, widthDp = 412, heightDp = 960)
@Composable
private fun ToolV6GroupConfigPreview() {
    ClassToolsTheme(darkTheme = false) {
        GroupScreen(
            onBack = {},
            onExport = {},
            prevMembers = v6GroupMembers,
            initialConfig = v6GroupConfig,
            skipRestore = true,
            randomSeed = 7L,
        )
    }
}

@Preview(name = "226 分组 v6 结果 · 深色", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun ToolV6GroupResultDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        GroupScreen(
            onBack = {},
            onExport = {},
            prevMembers = v6GroupMembers,
            initialConfig = v6GroupResult.config,
            initialResult = v6GroupResult,
            initialStep = GroupStep.RESULT,
            skipRestore = true,
            randomSeed = 7L,
        )
    }
}

@Preview(
    name = "227 分组 v6 结果 · 平板",
    showBackground = true,
    widthDp = 840,
    heightDp = 1024,
)
@Composable
private fun ToolV6GroupResultWidePreview() {
    ClassToolsTheme(darkTheme = false) {
        GroupScreen(
            onBack = {},
            onExport = {},
            prevMembers = v6GroupMembers,
            initialConfig = v6GroupResult.config,
            initialResult = v6GroupResult,
            initialStep = GroupStep.RESULT,
            skipRestore = true,
            randomSeed = 7L,
        )
    }
}

// =============================================================================
// 体测
// =============================================================================

@Preview(name = "228 体测 v6 录入 · 浅色", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun ToolV6FitInputPreview() {
    ClassToolsTheme(darkTheme = false) {
        FitScreen(
            onBack = {},
            initialInput = v6FitInput,
            skipRestore = true,
            nowMillis = V6_NOW,
        )
    }
}

@Preview(name = "229 体测 v6 评分与历史 · 浅色", showBackground = true, widthDp = 412, heightDp = 1200)
@Composable
private fun ToolV6FitHistoryPreview() {
    ClassToolsTheme(darkTheme = false) {
        FitScreen(
            onBack = {},
            initialInput = v6FitInput,
            initialHistory = v6FitHistory(),
            skipRestore = true,
            nowMillis = V6_NOW,
        )
    }
}

@Preview(name = "230 体测 v6 录入 · 深色", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun ToolV6FitInputDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        FitScreen(
            onBack = {},
            initialInput = v6FitInput.copy(sex = FitSex.F, pull = "40", durM = "3", durS = "55"),
            skipRestore = true,
            nowMillis = V6_NOW,
        )
    }
}

@Preview(
    name = "231 体测 v6 评分 · 平板",
    showBackground = true,
    widthDp = 840,
    heightDp = 1024,
)
@Composable
private fun ToolV6FitWidePreview() {
    ClassToolsTheme(darkTheme = false) {
        FitScreen(
            onBack = {},
            initialInput = v6FitInput,
            initialHistory = v6FitHistory(),
            skipRestore = true,
            nowMillis = V6_NOW,
        )
    }
}

@Suppress("unused")
private val v6PreviewHint: List<String> = listOf(
    "抽签配置/候选/结果",
    "分组配置/结果",
    "体测录入/评分/历史",
    "深色/大字体/平板",
)
