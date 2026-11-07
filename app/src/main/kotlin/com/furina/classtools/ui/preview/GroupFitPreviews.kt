package com.furina.classtools.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.furina.classtools.features.fit.FitExportFactory
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
import com.furina.classtools.ui.screens.fit.FitScreen
import com.furina.classtools.ui.screens.group.GroupScreen
import com.furina.classtools.ui.theme.ClassToolsTheme

// =============================================================================
// 分组 + 体测 预览（独立文件；供 Lead `android preview render` 验收）
//
// 全部走注入数据 + skipRestore，不读写 userdata、不发网络请求。
// =============================================================================

private const val PREVIEW_NOW: Long = 1_794_016_800_000L // 2026-11-07 10:00 Asia/Shanghai

private val previewMembers: List<GroupMember> = listOf(
    "张伟", "王芳", "李娜", "刘洋", "陈静", "杨帆",
    "赵磊", "黄敏", "周涛", "吴婷", "徐强", "孙丽",
).mapIndexed { index, name -> GroupMember(seat = index + 1, name = name) }

private val previewGroupResult = GroupEngine.group(
    members = previewMembers,
    config = GroupConfig(mode = GroupMode.GROUPS, n = 4, shuffle = true),
    random = SeededGroupRandom(7),
)

private val previewFitInput = FitInput(
    sex = FitSex.M,
    grade = FitGrade.G12,
    h = "175", w = "68", vc = "4800", r50 = "7.1", sit = "18.5", jump = "250", pull = "15",
    durM = "3", durS = "40",
)

private fun previewHistory(): List<FitRecord> {
    val first = FitScoring.calc(previewFitInput)
    val saved1 = FitHistory.save(
        records = emptyList(),
        input = previewFitInput,
        calc = first,
        id = "fit-preview-1",
        now = PREVIEW_NOW - 3 * 24 * 3600_000L,
        name = "期中体测",
    )
    val secondInput = previewFitInput.copy(vc = "5000", r50 = "6.9", pull = "16")
    val second = FitScoring.calc(secondInput)
    val saved2 = FitHistory.save(
        records = saved1.records,
        input = secondInput,
        calc = second,
        id = "fit-preview-2",
        now = PREVIEW_NOW - 24 * 3600_000L,
        name = "补测",
    )
    return saved2.records
}

@Preview(name = "30 分组 配置 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun GroupConfigLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        GroupScreen(
            onBack = {},
            onExport = {},
            prevMembers = previewMembers,
            initialConfig = GroupConfig(mode = GroupMode.GROUPS, n = 4, shuffle = true),
            skipRestore = true,
            randomSeed = 7L,
        )
    }
}

@Preview(name = "31 分组 结果 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun GroupResultLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        GroupScreen(
            onBack = {},
            onExport = {},
            prevMembers = previewMembers,
            initialConfig = previewGroupResult.config,
            initialResult = previewGroupResult,
            initialStep = GroupStep.RESULT,
            skipRestore = true,
            randomSeed = 7L,
        )
    }
}

@Preview(name = "32 分组 结果 · 深色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun GroupResultDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        GroupScreen(
            onBack = {},
            onExport = {},
            prevMembers = previewMembers,
            initialConfig = previewGroupResult.config,
            initialResult = previewGroupResult,
            initialStep = GroupStep.RESULT,
            skipRestore = true,
            randomSeed = 7L,
        )
    }
}

@Preview(name = "33 体测 录入 · 浅色", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun FitInputLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        FitScreen(
            onBack = {},
            initialInput = previewFitInput,
            skipRestore = true,
            nowMillis = PREVIEW_NOW,
        )
    }
}

@Preview(name = "34 体测 录入 · 深色", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun FitInputDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        FitScreen(
            onBack = {},
            initialInput = previewFitInput.copy(sex = FitSex.F, pull = "40", durM = "3", durS = "55"),
            skipRestore = true,
            nowMillis = PREVIEW_NOW,
        )
    }
}

@Preview(name = "35 体测 历史记录 · 浅色", showBackground = true, widthDp = 412, heightDp = 1200)
@Composable
private fun FitHistoryPreview() {
    ClassToolsTheme(darkTheme = false) {
        FitScreen(
            onBack = {},
            initialInput = previewFitInput,
            initialHistory = previewHistory(),
            skipRestore = true,
            nowMillis = PREVIEW_NOW,
        )
    }
}

@Preview(name = "36 体测 空态 · 浅色", showBackground = true, widthDp = 412, heightDp = 640)
@Composable
private fun FitEmptyPreview() {
    ClassToolsTheme(darkTheme = false) {
        FitScreen(
            onBack = {},
            initialInput = FitInput(),
            skipRestore = true,
            nowMillis = PREVIEW_NOW,
        )
    }
}

/** 保留引用，避免预览文件被优化掉（导出工厂在真实流程使用）。 */
@Suppress("unused")
private val previewExportSample = FitExportFactory.randomSuffix(PREVIEW_NOW)
