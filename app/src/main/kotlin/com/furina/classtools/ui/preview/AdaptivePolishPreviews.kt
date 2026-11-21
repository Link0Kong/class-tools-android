package com.furina.classtools.ui.preview

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.auth.StubAuthRepository
import com.furina.classtools.data.permission.PermissionController
import com.furina.classtools.data.permission.PermissionState
import com.furina.classtools.data.permission.PermissionSnapshot
import com.furina.classtools.data.permission.PermissionType
import com.furina.classtools.data.reminder.ReminderSettings
import com.furina.classtools.data.reminder.ReminderSettingsStore
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.tool.ToolCatalog
import com.furina.classtools.ui.adaptive.CtAdaptive
import com.furina.classtools.ui.screens.all.AllToolsScreen
import com.furina.classtools.ui.screens.identity.IdentityScreen
import com.furina.classtools.ui.screens.profile.ProfileScreen
import com.furina.classtools.ui.screens.settings.PermissionSettingsScreen
import com.furina.classtools.ui.screens.settings.ReminderSettingsScreen
import com.furina.classtools.ui.theme.ClassToolsTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * task-25 v2 自适应收尾预览：工具网格 3/4 列拖动、我的平板双栏、权限/提醒深色、小屏身份页。
 */
private val polishSession = Session(
    sid = "2024000001",
    name = "陈同学",
    token = "preview-token",
    role = "副团支部书记",
    badge = "班委",
    isAdmin = false,
)

private class PreviewPermissionController : PermissionController {
    override fun state(type: PermissionType): PermissionState = when (type) {
        PermissionType.NOTIFICATIONS -> PermissionState.GRANTED
        PermissionType.CALENDAR -> PermissionState.DENIED
    }

    override fun snapshot(): PermissionSnapshot = PermissionSnapshot(
        notifications = PermissionState.GRANTED,
        calendar = PermissionState.DENIED,
    )

    override fun markRequested(type: PermissionType) = Unit

    override fun permissionsToRequest(type: PermissionType): Array<String> = emptyArray()

    override fun settingsIntent(): Intent = Intent()

    override fun notificationsEnabled(): Boolean = true
}

private class PreviewReminderStore : ReminderSettingsStore {
    private val state = MutableStateFlow(ReminderSettings.DEFAULT)
    override val settings: Flow<ReminderSettings> = state
    override suspend fun current(): ReminderSettings = state.value
    override suspend fun update(transform: (ReminderSettings) -> ReminderSettings): ReminderSettings {
        state.value = transform(state.value)
        return state.value
    }
}

private val allToolIds = ToolCatalog.tools.map { it.id }

@Preview(name = "97 全部工具 · 3 列 700dp 浅色", showBackground = true, widthDp = 700, heightDp = 900)
@Composable
private fun AllToolsThreeColumnsPreview() {
    ClassToolsTheme(darkTheme = false) {
        AllToolsScreen(onOpenTool = {}, skipRestore = true, initialOrder = allToolIds)
    }
}

@Preview(name = "98 全部工具 · 3 列 拖动中", showBackground = true, widthDp = 700, heightDp = 900)
@Composable
private fun AllToolsThreeColumnsDraggingPreview() {
    ClassToolsTheme(darkTheme = false) {
        AllToolsScreen(
            onOpenTool = {},
            skipRestore = true,
            initialOrder = allToolIds,
            previewDraggingToolId = "hw",
            previewDragOffset = androidx.compose.ui.geometry.Offset(360f, 300f),
        )
    }
}

@Preview(name = "99 全部工具 · 4 列 拖动中", showBackground = true, widthDp = 1000, heightDp = 900)
@Composable
private fun AllToolsFourColumnsDraggingPreview() {
    ClassToolsTheme(darkTheme = false) {
        AllToolsScreen(
            onOpenTool = {},
            skipRestore = true,
            initialOrder = allToolIds,
            previewDraggingToolId = "draw",
            previewDragOffset = androidx.compose.ui.geometry.Offset(520f, 420f),
        )
    }
}

@Preview(name = "101 全部工具 · 4 列 深色", showBackground = true, widthDp = 1000, heightDp = 900)
@Composable
private fun AllToolsFourColumnsDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        AllToolsScreen(onOpenTool = {}, skipRestore = true, initialOrder = allToolIds)
    }
}

@Preview(name = "94 我的 · 平板双栏 · 浅色", showBackground = true, widthDp = 1100, heightDp = 850)
@Composable
private fun ProfileTabletTwoPaneLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        ProfileScreen(
            session = polishSession,
            onSignOut = {},
            adaptiveSpec = CtAdaptive.specFor(1100),
        )
    }
}

@Preview(name = "95 我的 · 平板双栏 · 深色", showBackground = true, widthDp = 1100, heightDp = 850)
@Composable
private fun ProfileTabletTwoPaneDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        ProfileScreen(
            session = polishSession,
            onSignOut = {},
            adaptiveSpec = CtAdaptive.specFor(1100),
        )
    }
}

@Preview(name = "96 我的 · 中屏单栏（700dp）", showBackground = true, widthDp = 700, heightDp = 1000)
@Composable
private fun ProfileMediumSingleColumnPreview() {
    ClassToolsTheme(darkTheme = false) {
        ProfileScreen(
            session = polishSession,
            onSignOut = {},
            adaptiveSpec = CtAdaptive.specFor(700),
        )
    }
}

@Preview(name = "97 权限管理 · 深色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun PermissionSettingsDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        PermissionSettingsScreen(
            onBack = {},
            controller = PreviewPermissionController(),
        )
    }
}

@Preview(name = "98 提醒设置 · 深色", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun ReminderSettingsDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        ReminderSettingsScreen(
            onBack = {},
            settingsStore = PreviewReminderStore(),
            permissionController = PreviewPermissionController(),
        )
    }
}

@Preview(name = "99 身份页 · 小屏 360x640", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun IdentitySmallScreenPreview() {
    ClassToolsTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            IdentityScreen(authRepository = StubAuthRepository(), onVerified = {})
        }
    }
}

@Preview(name = "102 工作台 · 平板 700dp 深色", showBackground = true, widthDp = 700, heightDp = 1000)
@Composable
private fun WorkbenchTabletDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        com.furina.classtools.ui.screens.home.HomeScreen(
            session = polishSession,
            onOpenTool = {},
            onHomeToolsChange = {},
            forceEmptyQuickTools = true,
        )
    }
}

@Suppress("unused")
private val polishPreviewHint = 1000.dp
