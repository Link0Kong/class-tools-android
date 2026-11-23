package com.furina.classtools.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.furina.classtools.data.member.ClassMember
import com.furina.classtools.data.session.Session
import com.furina.classtools.features.draw.DrawConfig
import com.furina.classtools.features.draw.DrawEngine
import com.furina.classtools.features.draw.DrawEntry
import com.furina.classtools.features.draw.DrawOutcome
import com.furina.classtools.features.draw.DrawPoolSource
import com.furina.classtools.features.draw.DrawResult
import com.furina.classtools.features.draw.SeededDrawRandom
import com.furina.classtools.features.draw.DrawSortMode
import com.furina.classtools.features.draw.DrawStep
import com.furina.classtools.ui.screens.draw.DrawScreen
import com.furina.classtools.ui.theme.ClassToolsTheme

// =============================================================================
// 抽签工具预览（独立文件，避免与 ScreenPreviews.kt 冲突）
//
// 供 Lead `android preview render` 验收：
// - 10 STEP1 配置 · 浅色
// - 11 STEP2 候选 · 浅色
// - 12 STEP3 结果 · 浅色
// - 13 STEP3 结果 · 深色
// - 14 错误态 · 浅色（四态验收）
// =============================================================================

private val previewSession = Session(
    sid = "2024000001",
    name = "陈同学",
    token = "preview-token",
    role = "副团支部书记",
    badge = "班委",
)

private val previewMembers: List<ClassMember> = listOf(
    ClassMember(seat = 1, sid = "s1", name = "张伟"),
    ClassMember(seat = 2, sid = "s2", name = "李四"),
    ClassMember(seat = 5, sid = "s5", name = "王五"),
    ClassMember(seat = 8, sid = "s8", name = "赵六"),
    ClassMember(seat = 12, sid = "s12", name = "钱七"),
    ClassMember(seat = 18, sid = "s18", name = "孙八"),
    ClassMember(seat = 23, sid = "s23", name = "周九"),
    ClassMember(seat = 31, sid = "s31", name = "吴十"),
)

private val previewConfig = DrawConfig(
    start = 1,
    end = 50,
    count = 8,
    allowDuplicates = false,
    excludedText = "3,7,10-12",
    sort = DrawSortMode.DRAW_ORDER,
)

private fun previewCandidates(): List<DrawEntry> =
    (1..50)
        .filter { it !in setOf(3, 7, 10, 11, 12) }
        .map { number ->
            DrawEntry(number = number, name = previewMembers.firstOrNull { it.seat == number }?.name)
        }

private fun previewResult(): DrawResult {
    val outcome = DrawEngine.drawFromCandidates(
        config = previewConfig,
        candidates = previewCandidates(),
        random = SeededDrawRandom(7L),
    )
    return (outcome as DrawOutcome.Success).result
}

@Preview(name = "10 抽签 STEP1 配置 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun DrawStep1LightPreview() {
    ClassToolsTheme(darkTheme = false) {
        DrawScreen(
            onBack = {},
            session = previewSession,
            onExport = {},
            members = previewMembers,
            initialConfig = previewConfig,
            initialStep = DrawStep.CONFIG,
            skipRestore = true,
        )
    }
}

@Preview(name = "11 抽签更多设置", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun DrawOptionsPreview() {
    ClassToolsTheme(darkTheme = false) {
        DrawScreen(
            onBack = {},
            session = previewSession,
            onExport = {},
            members = previewMembers,
            initialConfig = previewConfig.copy(poolSource = DrawPoolSource.ROSTER),
            skipRestore = true,
        )
    }
}

@Preview(name = "抽签结果 · 窄屏大字体", showBackground = true, widthDp = 320, heightDp = 760, fontScale = 2f)
@Composable
private fun DrawLargeFontResultPreview() {
    ClassToolsTheme(darkTheme = true) {
        val result = previewResult()
        DrawScreen(
            onBack = {},
            session = previewSession,
            onExport = {},
            initialConfig = previewConfig,
            initialStep = DrawStep.RESULT,
            initialResult = result.copy(
                entries = listOf(result.entries.first().copy(name = "用于验证长名称展示的抽签参与者")) + result.entries.drop(1),
            ),
            skipRestore = true,
        )
    }
}

@Preview(name = "12 抽签 STEP3 结果 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun DrawStep3LightPreview() {
    ClassToolsTheme(darkTheme = false) {
        DrawScreen(
            onBack = {},
            session = previewSession,
            onExport = {},
            members = previewMembers,
            initialConfig = previewConfig,
            initialStep = DrawStep.RESULT,
            initialCandidates = previewCandidates(),
            initialResult = previewResult(),
            skipRestore = true,
        )
    }
}

@Preview(name = "13 抽签 STEP3 结果 · 深色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun DrawStep3DarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        DrawScreen(
            onBack = {},
            session = previewSession,
            onExport = {},
            members = previewMembers,
            initialConfig = previewConfig.copy(sort = DrawSortMode.DESCENDING),
            initialStep = DrawStep.RESULT,
            initialCandidates = previewCandidates(),
            initialResult = previewResult(),
            skipRestore = true,
        )
    }
}

@Preview(name = "14 抽签 错误态 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun DrawErrorPreview() {
    ClassToolsTheme(darkTheme = false) {
        DrawScreen(
            onBack = {},
            session = null,
            onExport = {},
            members = emptyList(),
            forcedErrorMessage = "请先完成身份验证后再使用抽签",
            skipRestore = true,
        )
    }
}

@Preview(name = "15 抽签 STEP1 配置 · 平板", showBackground = true, widthDp = 840, heightDp = 1024)
@Composable
private fun DrawStep1WidePreview() {
    ClassToolsTheme(darkTheme = false) {
        DrawScreen(
            onBack = {},
            session = previewSession,
            onExport = {},
            members = previewMembers,
            initialConfig = previewConfig,
            initialStep = DrawStep.CONFIG,
            skipRestore = true,
        )
    }
}
