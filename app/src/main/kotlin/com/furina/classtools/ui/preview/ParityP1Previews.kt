package com.furina.classtools.ui.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.furina.classtools.data.session.Session
import com.furina.classtools.features.feedback.FeedbackRow
import com.furina.classtools.ui.screens.feedback.FeedbackScreen
import com.furina.classtools.ui.screens.guide.UserGuideScreen
import com.furina.classtools.ui.screens.me.MeStats
import com.furina.classtools.ui.screens.me.MeStatsSection
import com.furina.classtools.ui.screens.me.ReminderStatus
import com.furina.classtools.ui.theme.ClassToolsTheme

/**
 * v2.0.0 P1 对齐页面预览：反馈建议 / 使用说明 / 我的数据指标卡。
 */

private val previewSession = Session(
    sid = "3260707024",
    name = "陈子麒",
    token = "preview-token",
    role = "班长",
)

private val previewRows = listOf(
    FeedbackRow(
        id = "f_1",
        content = "课表页能按周切换了，建议在无课时也显示下一节课。",
        acc = "3260707024",
        done = false,
        ts = 1_741_000_000_000L,
    ),
    FeedbackRow(
        id = "f_2",
        content = "希望作业提醒增加「提前 3 天」选项。",
        acc = "3260707024",
        done = true,
        ts = 1_740_900_000_000L,
    ),
)

@Preview(name = "79 反馈建议 · 手机（我的反馈）", showBackground = true, widthDp = 400, heightDp = 820)
@Composable
private fun FeedbackCompactPreview() {
    ClassToolsTheme(darkTheme = false) {
        FeedbackScreen(
            onBack = {},
            session = previewSession,
            repository = null,
            skipLoad = true,
            initialRows = previewRows,
        )
    }
}

@Preview(name = "80 反馈建议 · 管理员（深色）", showBackground = true, widthDp = 400, heightDp = 820)
@Composable
private fun FeedbackAdminDarkPreview() {
    val admin = previewSession.copy(isAdmin = true)
    ClassToolsTheme(darkTheme = true) {
        FeedbackScreen(
            onBack = {},
            session = admin,
            repository = null,
            skipLoad = true,
            initialRows = previewRows + previewRows.map { it.copy(id = it.id + "_x", acc = "3260707999") },
            nameOfSid = { sid -> if (sid == "3260707024") "陈子麒" else "李同学" },
        )
    }
}

@Preview(name = "81 反馈建议 · 错误态", showBackground = true, widthDp = 400, heightDp = 620)
@Composable
private fun FeedbackErrorPreview() {
    ClassToolsTheme(darkTheme = false) {
        FeedbackScreen(
            onBack = {},
            session = previewSession,
            repository = null,
            forcedErrorMessage = "网络不可用，请稍后重试",
        )
    }
}

@Preview(name = "82 反馈建议 · 平板双栏", showBackground = true, widthDp = 1000, heightDp = 760)
@Composable
private fun FeedbackTabletPreview() {
    ClassToolsTheme(darkTheme = false) {
        FeedbackScreen(
            onBack = {},
            session = previewSession.copy(isAdmin = true),
            repository = null,
            skipLoad = true,
            initialRows = previewRows,
            nameOfSid = { "陈子麒" },
        )
    }
}

@Preview(name = "83 使用说明 · 手机", showBackground = true, widthDp = 400, heightDp = 820)
@Composable
private fun GuideCompactPreview() {
    ClassToolsTheme(darkTheme = false) {
        UserGuideScreen(onBack = {})
    }
}

@Preview(name = "84 使用说明 · 平板深色", showBackground = true, widthDp = 1000, heightDp = 760)
@Composable
private fun GuideTabletDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        UserGuideScreen(onBack = {})
    }
}

@Preview(name = "85 我的数据 · 手机", showBackground = true, widthDp = 400, heightDp = 340)
@Composable
private fun MeStatsPreview() {
    ClassToolsTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                MeStatsSection(
                    stats = MeStats(
                        pendingHomework = 3,
                        doneHomework = 12,
                        drawRecords = 2,
                        fitRecords = 1,
                        pendingNotices = 5,
                        classMembers = 68,
                        powerRemaining = 36.5,
                        powerRoomLabel = "23号楼A座321",
                    ),
                    reminder = ReminderStatus(
                        enabled = true,
                        notificationPermission = true,
                        enabledModuleCount = 9,
                    ),
                    onOpenReminder = {},
                )
            }
        }
    }
}

@Preview(name = "86 我的数据 · 深色（提醒未开权限）", showBackground = true, widthDp = 400, heightDp = 340)
@Composable
private fun MeStatsDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                MeStatsSection(
                    stats = MeStats(
                        pendingHomework = 3,
                        doneHomework = 12,
                        drawRecords = 2,
                        fitRecords = 1,
                    ),
                    reminder = ReminderStatus(
                        enabled = true,
                        notificationPermission = false,
                        enabledModuleCount = 4,
                    ),
                    onOpenReminder = {},
                )
            }
        }
    }
}
