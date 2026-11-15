package com.furina.classtools.data.settings

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.furina.classtools.ui.theme.AppearanceMode
import com.furina.classtools.ui.theme.AppearanceSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * App 级本机外观设置（不进云端 userdata：主题是设备偏好，与账号无关）。
 *
 * 设置项：
 * - 壁纸取色 `dynamic_color`（Material You 混合动态色，默认关闭）；
 * - 外观模式 `appearance_mode`（system/light/dark，默认跟随系统）；
 * - 隐藏状态栏 `hide_status_bar`（默认 true，沉浸式 sticky，下滑临时显示）。
 *
 * 实时性：以 DataStore Flow 暴露，写入后「我的 → 外观」与 [com.furina.classtools.ui.theme.ClassToolsTheme]
 * 立即重组生效；重启后仍保留。
 *
 * 冷启动不闪烁：外观模式 / 隐藏状态栏另写一份 SharedPreferences 镜像（启动时同步读，
 * 用于首帧 initialValue），DataStore 仍是唯一真源。动态取色沿用旧行为（默认 false）。
 */
class AppSettingsStore(private val context: Context) {

    private val appContext = context.applicationContext
    private val store: DataStore<Preferences> = appContext.settingsDataStore

    /** 冷启动镜像：只用于首帧 initialValue，避免「先按系统/默认渲染一帧再跳变」。 */
    private val startupPrefs = appContext.getSharedPreferences(STARTUP_PREFS, Context.MODE_PRIVATE)

    val dynamicColor: Flow<Boolean> = store.data.map { prefs ->
        prefs[KEY_DYNAMIC_COLOR] ?: false
    }

    /** 外观模式：跟随系统 / 浅色 / 深色（本机持久化）。 */
    val appearanceMode: Flow<AppearanceMode> = store.data.map { prefs ->
        AppearanceMode.fromStorage(prefs[KEY_APPEARANCE_MODE])
    }

    /** 是否隐藏系统状态栏（默认开启）。 */
    val hideStatusBar: Flow<Boolean> = store.data.map { prefs ->
        AppearanceSettings.hideStatusBarOrDefault(prefs[KEY_HIDE_STATUS_BAR])
    }

    /** 首帧同步读取外观模式（未写入过 = 跟随系统）。 */
    fun startupAppearanceMode(): AppearanceMode =
        AppearanceMode.fromStorage(startupPrefs.getString(STARTUP_KEY_APPEARANCE_MODE, null))

    /** 首帧同步读取「隐藏状态栏」开关（未写入过 = 默认开启）。 */
    fun startupHideStatusBar(): Boolean = startupPrefs.getBoolean(
        STARTUP_KEY_HIDE_STATUS_BAR,
        AppearanceSettings.DEFAULT_HIDE_STATUS_BAR,
    )

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { prefs -> prefs[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAppearanceMode(mode: AppearanceMode) {
        startupPrefs.edit().putString(STARTUP_KEY_APPEARANCE_MODE, mode.storageValue).apply()
        store.edit { prefs -> prefs[KEY_APPEARANCE_MODE] = mode.storageValue }
    }

    suspend fun setHideStatusBar(enabled: Boolean) {
        startupPrefs.edit().putBoolean(STARTUP_KEY_HIDE_STATUS_BAR, enabled).apply()
        store.edit { prefs -> prefs[KEY_HIDE_STATUS_BAR] = enabled }
    }

    private companion object {
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_APPEARANCE_MODE = stringPreferencesKey(AppearanceSettings.KEY_APPEARANCE_MODE)
        val KEY_HIDE_STATUS_BAR = booleanPreferencesKey(AppearanceSettings.KEY_HIDE_STATUS_BAR)

        /** 冷启动镜像（仅外观模式 + 隐藏状态栏；DataStore 仍是真源）。 */
        const val STARTUP_PREFS = "ct_appearance_startup"
        const val STARTUP_KEY_APPEARANCE_MODE = "appearance_mode"
        const val STARTUP_KEY_HIDE_STATUS_BAR = "hide_status_bar"

        val Context.settingsDataStore by preferencesDataStore(name = "ct_settings")
    }
}

/**
 * [AppSettingsStore] 的 CompositionLocal：MainActivity 在主题根节点提供。
 *
 * 预览 / 孤立组件为 null → 「我的」页回退到入参（预览可控），生产环境直接读写本机设置。
 */
val LocalAppSettingsStore = staticCompositionLocalOf<AppSettingsStore?> { null }
