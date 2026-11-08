package com.furina.classtools.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.notices.ManualNotice
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeItemMeta
import com.furina.classtools.data.notices.NoticeManualAttachment
import com.furina.classtools.data.notices.NoticeRow
import com.furina.classtools.data.notices.NoticesData
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.tool.ToolCatalog
import com.furina.classtools.ui.screens.all.AllToolsScreen
import com.furina.classtools.ui.screens.home.HomeScreen
import com.furina.classtools.ui.screens.notices.NoticesScreen
import com.furina.classtools.ui.screens.profile.ProfileScreen
import com.furina.classtools.ui.theme.ClassToolsTheme

/**
 * 无障碍 / 大字体（fontScale = 1.3）验证预览。
 *
 * 用途：
 * - 验证首页 / 我的 / 通知 / 全部工具在系统字体放大 1.3x 时是否截断、换行是否正常；
 * - 全部工具页同时覆盖「拖动中」态的语义位置（静态图看不到 TalkBack，但可看布局）；
 * - 阅读顺序与触控区域由 docs/ACCESSIBILITY.md 说明。
 */
private val a11yPreviewSession = Session(
    sid = "3260707024",
    name = "陈子麒",
    token = "preview-token",
    role = "副团支部书记",
    badge = "班委",
)

private fun a11yNoticesData(): NoticesData {
    val now = 1_794_016_800_000L
    val autoRow = NoticeRow(
        id = "auto_g1",
        title = "关于开展 2026 年秋季学期期中教学检查的通知",
        body = "",
        ts = now - 3_600_000L,
    )
    val autoGroup = NoticeGroupDto(
        id = "g1",
        title = "关于开展 2026 年秋季学期期中教学检查的通知",
        cat = "教务",
        source = "教务处",
        summary = "第 10-11 周开展期中教学检查，请各班提前准备课堂考勤与作业归档材料，并按通知要求提交自查表。",
        items = emptyList(),
    )
    val autoEntry = NoticeEntry(
        row = autoRow,
        group = autoGroup,
        items = listOf(
            NoticeItemMeta.Text(
                kind = "text",
                label = "原始消息",
                fromName = "辅导员",
                fromRole = "班委",
                timeText = "11-07 10:00",
                text = "请各班注意：期中教学检查材料需在 11 月 14 日前提交。",
                isFollow = false,
            ),
        ),
        hidden = false,
        edited = false,
    )
    val manualRow = NoticeRow(
        id = "manual_1",
        title = "周五班会通知",
        body = "本周五 19:00 在 04102 教室召开班会，请全体同学准时参加；请假同学提前在班级群报备。",
        ts = now - 7_200_000L,
    )
    val manual = ManualNotice(
        row = manualRow,
        attachments = listOf(
            NoticeManualAttachment(
                index = 0,
                name = "班会材料.pdf",
                mime = "application/pdf",
                sizeText = "1.2 MB",
                hasData = false,
            ),
        ),
    )
    return NoticesData(
        auto = listOf(autoEntry),
        manual = listOf(manual),
        hiddenCount = 0,
        fetchedAt = now,
        fromCache = false,
    )
}

@Preview(name = "50 工作台 · 大字体 1.3x", showBackground = true, widthDp = 412, heightDp = 1000, fontScale = 1.3f)
@Composable
private fun HomeLargeFontPreview() {
    ClassToolsTheme(darkTheme = false) {
        HomeScreen(session = a11yPreviewSession, onOpenTool = {}, onHomeToolsChange = {})
    }
}

@Preview(name = "51 我的 · 大字体 1.3x", showBackground = true, widthDp = 412, heightDp = 1100, fontScale = 1.3f)
@Composable
private fun ProfileLargeFontPreview() {
    ClassToolsTheme(darkTheme = false) {
        ProfileScreen(session = a11yPreviewSession, onSignOut = {})
    }
}

@Preview(name = "52 通知 · 大字体 1.3x", showBackground = true, widthDp = 412, heightDp = 1000, fontScale = 1.3f)
@Composable
private fun NoticesLargeFontPreview() {
    ClassToolsTheme(darkTheme = false) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = a11yNoticesData(),
            skipLoad = true,
        )
    }
}

@Preview(name = "53 通知 · 大字体 1.3x 深色", showBackground = true, widthDp = 412, heightDp = 1000, fontScale = 1.3f)
@Composable
private fun NoticesLargeFontDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = a11yNoticesData(),
            skipLoad = true,
        )
    }
}

@Preview(name = "54 全部工具 · 大字体 1.3x", showBackground = true, widthDp = 412, heightDp = 1100, fontScale = 1.3f)
@Composable
private fun AllToolsLargeFontPreview() {
    ClassToolsTheme(darkTheme = false) {
        AllToolsScreen(
            onOpenTool = {},
            skipRestore = true,
            initialOrder = ToolCatalog.tools.map { it.id },
        )
    }
}

@Preview(name = "55 全部工具 · 拖动中 · 大字体 1.3x", showBackground = true, widthDp = 412, heightDp = 1100, fontScale = 1.3f)
@Composable
private fun AllToolsDraggingLargeFontPreview() {
    ClassToolsTheme(darkTheme = false) {
        AllToolsScreen(
            onOpenTool = {},
            skipRestore = true,
            initialOrder = listOf("hw", "draw", "nt", "tt", "fit", "group", "power", "water", "roster", "settings"),
            previewDraggingToolId = "hw",
            previewDragOffset = androidx.compose.ui.geometry.Offset(210f, 300f),
        )
    }
}

@Suppress("unused")
private val a11yPreviewWidth = 412.dp
