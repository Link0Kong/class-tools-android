package com.furina.classtools.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.furina.classtools.features.draw.DrawConfig
import com.furina.classtools.features.draw.DrawEntry
import com.furina.classtools.features.draw.DrawRemoveLog
import com.furina.classtools.features.draw.DrawResult
import com.furina.classtools.features.draw.DrawSortMode
import com.furina.classtools.features.draw.DrawStep
import com.furina.classtools.features.fit.FitFields
import com.furina.classtools.features.fit.FitRecord
import com.furina.classtools.features.group.GroupConfig
import com.furina.classtools.features.group.GroupMember
import com.furina.classtools.features.group.GroupMode
import com.furina.classtools.ui.screens.draw.DrawScreen
import com.furina.classtools.ui.screens.fit.FitScreen
import com.furina.classtools.ui.screens.group.GroupScreen
import com.furina.classtools.ui.theme.ClassToolsTheme

// =============================================================================
// fitrecs + 抽签→分组 联动预览（task-13）
//
// - 37 抽签 STEP3 结果页 · 结果单 + 用此结果分组（浅色）
// - 38 抽签 STEP3 结果页 · 有剔除记录（深色）
// - 39 分组 · 上一步抽签结果入口（浅色）
// - 40 体测历史 · 云端两条记录（浅色）
// =============================================================================

private const val PREVIEW_NOW = 1_760_000_000_000L

private fun previewDrawResult(logged: Boolean = true): DrawResult {
    val config = DrawConfig(
        start = 1,
        end = 40,
        count = 4,
        excludedText = "3,7",
        sort = DrawSortMode.ASCENDING,
    )
    val entries = listOf(
        DrawEntry(number = 5, name = null),
        DrawEntry(number = 12, name = "张伟"),
        DrawEntry(number = 23, name = "李四"),
        DrawEntry(number = 31, name = "王五"),
    )
    val log = if (logged) {
        listOf(
            DrawRemoveLog(removed = DrawEntry(9, null), added = DrawEntry(12, "张伟")),
            DrawRemoveLog(removed = DrawEntry(23, "李四"), added = null),
        )
    } else {
        emptyList()
    }
    return DrawResult(
        entries = entries,
        pool = (1..40).filter { it !in listOf(3, 7) }.map { DrawEntry(number = it, name = null) },
        config = config,
        log = log,
    )
}

private fun previewFitHistory(): List<FitRecord> = listOf(
    FitRecord(
        id = "f_preview_2",
        ts = PREVIEW_NOW,
        name = "",
        sex = "m",
        gradeBucket = "12",
        fields = FitFields(h = "175", w = "65", vc = "4200", r50 = "7.2", sit = "12.5", jump = "250", pull = "12", durM = "4", durS = "05"),
        total = 78.5,
        grade = "及格",
    ),
    FitRecord(
        id = "f_preview_1",
        ts = PREVIEW_NOW - 7 * 24 * 3600_000L,
        name = "补测",
        sex = "m",
        gradeBucket = "12",
        fields = FitFields(h = "175", w = "66", vc = "4100", r50 = "7.3", sit = "11.0", jump = "248", pull = "11", durM = "4", durS = "10"),
        total = 76.0,
        grade = "及格",
    ),
)

private fun previewDrawGroupMembers(): List<GroupMember> = previewDrawResult().entries.map { entry ->
    GroupMember(seat = entry.number, name = entry.name.orEmpty())
}

@Preview(name = "37 抽签 STEP3 · 结果单/分组入口 · 浅色", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun DrawResultSheetLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        DrawScreen(
            onBack = {},
            session = null,
            onExport = {},
            onGroup = {},
            onExportSheet = {},
            initialConfig = previewDrawResult().config,
            initialStep = DrawStep.RESULT,
            initialResult = previewDrawResult(),
            skipRestore = true,
        )
    }
}

@Preview(name = "38 抽签 STEP3 · 有剔除记录 · 深色", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun DrawResultSheetDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        DrawScreen(
            onBack = {},
            session = null,
            onExport = {},
            onGroup = {},
            onExportSheet = {},
            initialConfig = previewDrawResult().config,
            initialStep = DrawStep.RESULT,
            initialResult = previewDrawResult(logged = true),
            skipRestore = true,
        )
    }
}

@Preview(name = "39 分组 · 上一步抽签结果入口 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun DrawToGroupPreview() {
    ClassToolsTheme(darkTheme = false) {
        GroupScreen(
            onBack = {},
            onExport = {},
            prevMembers = previewDrawGroupMembers(),
            initialConfig = GroupConfig(mode = GroupMode.GROUPS, n = 2, shuffle = true),
            skipRestore = true,
            randomSeed = 7L,
        )
    }
}

@Preview(name = "40 体测 · 云端两条历史 · 浅色", showBackground = true, widthDp = 412, heightDp = 1200)
@Composable
private fun FitCloudHistoryPreview() {
    ClassToolsTheme(darkTheme = false) {
        FitScreen(
            onBack = {},
            initialHistory = previewFitHistory(),
            skipRestore = true,
            nowMillis = PREVIEW_NOW,
        )
    }
}
