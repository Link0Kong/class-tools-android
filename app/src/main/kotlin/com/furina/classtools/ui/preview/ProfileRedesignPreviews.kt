package com.furina.classtools.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.furina.classtools.data.session.Session
import com.furina.classtools.ui.adaptive.CtAdaptive
import com.furina.classtools.ui.screens.me.MeStats
import com.furina.classtools.ui.screens.me.ReminderStatus
import com.furina.classtools.ui.screens.profile.ProfileScreen
import com.furina.classtools.ui.theme.ClassToolsTheme

/**
 * v5「我的」重做预览：浅/深/大字体/平板/空数据/退出确认/清缓存确认/通知权限横幅。
 * 对应 `docs/design/MP_UI_ALIGN.md`。
 */

private val profilePreviewSession = Session(
    sid = "2024000001",
    name = "陈同学",
    token = "preview-token",
    role = "副团支部书记",
    badge = "班委",
    isAdmin = true,
)

private val profilePreviewStats = MeStats(
    pendingHomework = 7,
    doneHomework = 21,
    drawRecords = 45,
    fitRecords = 2,
)

private val profilePreviewReminder = ReminderStatus(
    enabled = true,
    notificationPermission = true,
    enabledModuleCount = 9,
)

private val emptyStats = MeStats()

@Composable
private fun ProfilePreview(
    dark: Boolean,
    widthDp: Int = 412,
    stats: MeStats = profilePreviewStats,
    reminder: ReminderStatus = profilePreviewReminder,
    syncEnabled: Boolean = true,
    dynamicColorEnabled: Boolean = false,
    showBanner: Boolean = false,
    initialSignOut: Boolean = false,
    initialClearCache: Boolean = false,
    onSignOut: () -> Unit = {},
) {
    ClassToolsTheme(darkTheme = dark) {
        ProfileScreen(
            session = profilePreviewSession,
            onSignOut = onSignOut,
            adaptiveSpec = CtAdaptive.specFor(widthDp),
            meStats = stats,
            reminderStatus = reminder,
            syncEnabled = syncEnabled,
            onSyncEnabledChange = {},
            showNotificationPermissionBanner = showBanner,
            onOpenDataPrivacy = {},
            onOpenPermissions = {},
            onClearLocalCache = {},
            onOpenReminderSettings = {},
            onOpenFeedback = {},
            onOpenGuide = {},
            initialShowSignOutConfirm = initialSignOut,
            initialShowClearCacheConfirm = initialClearCache,
        )
    }
}

@Preview(name = "171 我的 · 浅色", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun ProfileLightPreview() {
    ProfilePreview(dark = false)
}

@Preview(name = "172 我的 · 深色", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun ProfileDarkPreview() {
    ProfilePreview(dark = true)
}

@Preview(
    name = "173 我的 · 大字体 1.3x",
    showBackground = true,
    widthDp = 412,
    heightDp = 1200,
    fontScale = 1.3f,
)
@Composable
private fun ProfileLargeFontPreview() {
    ProfilePreview(dark = false)
}

@Preview(name = "174 我的 · 平板双栏", showBackground = true, widthDp = 1000, heightDp = 900)
@Composable
private fun ProfileTabletPreview() {
    ProfilePreview(dark = false, widthDp = 1000)
}

@Preview(name = "175 我的 · 空数据（未产生记录）", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun ProfileEmptyPreview() {
    ProfilePreview(
        dark = false,
        stats = emptyStats,
        reminder = ReminderStatus(enabled = false, notificationPermission = false),
        syncEnabled = false,
    )
}

@Preview(name = "176 我的 · 退出确认", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun ProfileSignOutConfirmPreview() {
    ProfilePreview(dark = true, initialSignOut = true)
}

@Preview(name = "177 我的 · 清除缓存确认", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun ProfileClearCacheConfirmPreview() {
    ProfilePreview(dark = false, initialClearCache = true)
}

@Preview(name = "178 我的 · 通知权限横幅", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun ProfilePermissionBannerPreview() {
    ProfilePreview(
        dark = false,
        showBanner = true,
        reminder = ReminderStatus(enabled = true, notificationPermission = false),
    )
}

@Preview(name = "179 我的 · 平板深色 · 取色开启", showBackground = true, widthDp = 1000, heightDp = 900)
@Composable
private fun ProfileTabletDarkPreview() {
    ProfilePreview(dark = true, widthDp = 1000, dynamicColorEnabled = true)
}

@Suppress("unused")
private val profilePreviewNotes: List<String> = listOf(
    "身份卡不显示头像/学号",
    "我的数据 2×2",
    "云同步/隐私/清缓存同组",
    "通知与权限合并",
    "关于与帮助含协议/版本",
    "退出登录 error 描边 + 页脚版本",
)
