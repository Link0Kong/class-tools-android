package com.furina.classtools.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.furina.classtools.data.privacy.PrivacySummary
import com.furina.classtools.data.session.Session
import com.furina.classtools.ui.screens.data.DataPrivacyDialog
import com.furina.classtools.ui.screens.data.DataPrivacyScreen
import com.furina.classtools.ui.theme.ClassToolsTheme

// =============================================================================
// 数据与隐私页预览（task-15）
//
// - 41 数据与隐私 · 正常（浅色）
// - 42 数据与隐私 · 清除云端确认（深色）
// - 43 数据与隐私 · 清除本机确认（浅色）
// - 44 数据与隐私 · 操作结果反馈（浅色）
// - 45 数据与隐私 · 错误态（深色）
// =============================================================================

private val previewSession = Session(
    sid = "3260707024",
    name = "陈子麒",
    token = "preview-token",
    role = "副团支部书记",
    badge = "班委",
)

private val previewSummary = PrivacySummary(
    userdataSections = 11,
    marksCount = 23,
    fitRecsCount = 2,
)

@Preview(name = "41 数据与隐私 · 正常 · 浅色", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun DataPrivacyNormalLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        DataPrivacyScreen(
            onBack = {},
            session = previewSession,
            repository = null,
            initialSummary = previewSummary,
            skipLoad = true,
        )
    }
}

@Preview(name = "42 数据与隐私 · 清除云端确认 · 深色", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun DataPrivacyClearCloudDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        DataPrivacyScreen(
            onBack = {},
            session = previewSession,
            repository = null,
            initialSummary = previewSummary,
            initialDialog = DataPrivacyDialog.CLEAR_CLOUD,
            skipLoad = true,
        )
    }
}

@Preview(name = "43 数据与隐私 · 清除本机确认 · 浅色", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun DataPrivacyClearLocalLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        DataPrivacyScreen(
            onBack = {},
            session = previewSession,
            repository = null,
            initialSummary = previewSummary,
            initialDialog = DataPrivacyDialog.CLEAR_LOCAL,
            skipLoad = true,
        )
    }
}

@Preview(name = "44 数据与隐私 · 操作结果反馈 · 浅色", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun DataPrivacyFeedbackLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        DataPrivacyScreen(
            onBack = {},
            session = previewSession,
            repository = null,
            initialSummary = previewSummary,
            initialFeedback = "部分数据清除失败：fitrecs（读取失败：网络不可用，请稍后重试）",
            skipLoad = true,
        )
    }
}

@Preview(name = "45 数据与隐私 · 错误态 · 深色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun DataPrivacyErrorDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        DataPrivacyScreen(
            onBack = {},
            session = previewSession,
            repository = null,
            skipLoad = true,
            forcedErrorMessage = "网络异常，请稍后重试",
        )
    }
}
