package com.furina.classtools.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furina.classtools.AppContainer
import androidx.compose.runtime.withFrameNanos
import com.furina.classtools.data.background.LocalBackgroundStore
import com.furina.classtools.data.settings.LocalAppSettingsStore
import com.furina.classtools.features.home.LocalHomeContextSource
import com.furina.classtools.ui.components.CtFeedbackRoot
import com.furina.classtools.ui.theme.ClassToolsTheme
import com.furina.classtools.ui.theme.CtImmersiveStatusBar

/**
 * 全局主题/外观宿主：MainActivity 与共享元素目标 [com.furina.classtools.ToolActivity] 共用。
 *
 * 两处必须保持完全一致，否则从工具卡进入独立 Activity 时会看到：
 * - 深浅色/壁纸取色跳变；
 * - 自定义背景丢失；
 * - 状态栏/导航栏图标颜色突变。
 *
 * 只负责「外观与本机设置」，不参与会话/导航；导航由调用方决定。
 */
@Composable
fun ClassToolsThemeHost(
    container: AppContainer,
    content: @Composable () -> Unit,
) {
    val backgroundStore = container.backgroundStore
    val background by backgroundStore.selection.collectAsStateWithLifecycle()
    LaunchedEffect(container) {
        withFrameNanos { }
        container.refreshRemoteConfig()
        backgroundStore.restoreBitmap()
    }
    // 外观偏好存本机（不进 userdata）：外观模式 / 隐藏状态栏 / 玻璃材质均实时生效。
    val dynamicColor by container.appSettings.dynamicColor
        .collectAsStateWithLifecycle(initialValue = container.appSettings.startupDynamicColor())
    // 首帧用 SharedPreferences 镜像同步初始化，避免冷启动先按系统/默认渲染一帧再跳变。
    val initialAppearanceMode = remember { container.appSettings.startupAppearanceMode() }
    val initialHideStatusBar = remember { container.appSettings.startupHideStatusBar() }
    val initialImmersiveLight = remember { container.appSettings.startupImmersiveLight() }
    val appearanceMode by container.appSettings.appearanceMode
        .collectAsStateWithLifecycle(initialValue = initialAppearanceMode)
    val hideStatusBar by container.appSettings.hideStatusBar
        .collectAsStateWithLifecycle(initialValue = initialHideStatusBar)
    // 沉浸光感卡片：用户可自由切回旧玻璃材质（强/均衡/弱）或关闭为实色 M3 卡片。
    val immersiveLight by container.appSettings.immersiveLight
        .collectAsStateWithLifecycle(initialValue = initialImmersiveLight)
    // 外观模式：跟随系统 / 强制浅色 / 强制深色（我的 → 外观 三选一）。
    val darkTheme = appearanceMode.resolveDark(isSystemInDarkTheme())

    ClassToolsTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        preset = background.preset,
        immersiveLight = immersiveLight,
    ) {
        // 「我的 → 外观」直接读写本机设置；背景 Store 同层提供（全局背景层订阅）。
        CompositionLocalProvider(
            LocalAppSettingsStore provides container.appSettings,
            LocalHomeContextSource provides container.homeContextRepository,
            LocalBackgroundStore provides backgroundStore,
        ) {
            // 全局隐藏系统状态栏（下滑临时显示）；关闭时恢复 statusBarsPadding 的常规行为。
            CtImmersiveStatusBar(enabled = hideStatusBar) {
                CtFeedbackRoot(content)
            }
        }
    }
}



