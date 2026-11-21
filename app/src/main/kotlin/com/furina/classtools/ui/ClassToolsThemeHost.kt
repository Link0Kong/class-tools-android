package com.furina.classtools.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furina.classtools.AppContainer
import com.furina.classtools.data.background.BackgroundStore
import com.furina.classtools.data.background.LocalBackgroundStore
import com.furina.classtools.data.remoteconfig.RemoteConfigRepository
import com.furina.classtools.data.settings.LocalAppSettingsStore
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
    backgroundStore: BackgroundStore,
    content: @Composable () -> Unit,
) {
    // 冷启动恢复已保存的背景位图（IO 线程解码，主线程不阻塞；失败静默回退预设）。
    LaunchedEffect(backgroundStore) {
        runCatching { backgroundStore.restoreBitmap() }
    }
    // 设置热更：启动时拉一次白名单配置；失败静默，不影响主题首帧。
    LaunchedEffect(Unit) {
        RemoteConfigRepository().fetch()?.let { config ->
            runCatching { container.appSettings.applyRemoteConfig(config) }
        }
    }
    // 外观偏好存本机（不进 userdata）：壁纸取色 / 外观模式 / 隐藏状态栏均实时生效。
    val dynamicColor by container.appSettings.dynamicColor
        .collectAsStateWithLifecycle(initialValue = false)
    // 首帧用 SharedPreferences 镜像同步初始化，避免冷启动先按系统/默认渲染一帧再跳变。
    val initialAppearanceMode = remember { container.appSettings.startupAppearanceMode() }
    val initialHideStatusBar = remember { container.appSettings.startupHideStatusBar() }
    val appearanceMode by container.appSettings.appearanceMode
        .collectAsStateWithLifecycle(initialValue = initialAppearanceMode)
    val hideStatusBar by container.appSettings.hideStatusBar
        .collectAsStateWithLifecycle(initialValue = initialHideStatusBar)
    // 2026-11-21：按 M3 标准移除「沉浸光感」选择，统一实色 surface（不再读本机档位）。
    val immersiveLight = com.furina.classtools.ui.theme.CtLightSetting.Off
    // 外观模式：跟随系统 / 强制浅色 / 强制深色（我的 → 外观 三选一）。
    val darkTheme = appearanceMode.resolveDark(isSystemInDarkTheme())

    ClassToolsTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        immersiveLight = immersiveLight,
    ) {
        // 「我的 → 外观」直接读写本机设置；背景 Store 同层提供（全局背景层订阅）。
        CompositionLocalProvider(
            LocalAppSettingsStore provides container.appSettings,
            LocalBackgroundStore provides backgroundStore,
        ) {
            // 全局隐藏系统状态栏（下滑临时显示）；关闭时恢复 statusBarsPadding 的常规行为。
            CtImmersiveStatusBar(enabled = hideStatusBar) {
                content()
            }
        }
    }
}


