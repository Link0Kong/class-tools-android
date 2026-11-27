package com.furina.classtools.data.settings

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.furina.classtools.ui.theme.AppearanceMode
import com.furina.classtools.ui.theme.AppearanceSettings
import com.furina.classtools.ui.theme.CtLightSetting
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.features.home.HOME_RECENT_MS
import com.furina.classtools.features.home.HomeContentPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@kotlinx.serialization.Serializable
internal data class HomeContentSettings(
    val preferences: HomeContentPreferences = HomeContentPreferences(),
    val seen: Map<String, Long> = emptyMap(),
)

/**
 * App 级本机外观设置（不进云端 userdata：主题是设备偏好，与账号无关）。
 *
 * 设置项：
 * - 壁纸取色 `dynamic_color`（Material You，可选）；
 * - 外观模式 `appearance_mode`（system/light/dark，默认跟随系统）；
 * - 隐藏状态栏 `hide_status_bar`（默认 true，沉浸式 sticky，下滑临时显示）。
 *
 * 实时性：以 DataStore Flow 暴露，写入后「我的 → 外观」与 [com.furina.classtools.ui.theme.ClassToolsTheme]
 * 立即重组生效；重启后仍保留。
 *
 * 冷启动不闪烁：外观模式 / 隐藏状态栏另写一份 SharedPreferences 镜像（启动时同步读，
 * 用于首帧 initialValue），DataStore 仍是唯一真源。默认使用与网页一致的配色。
 */
class AppSettingsStore(private val context: Context) {

    private val appContext = context.applicationContext
    private val store: DataStore<Preferences> = appContext.settingsDataStore

    /** 冷启动镜像：只用于首帧 initialValue，避免「先按系统/默认渲染一帧再跳变」。 */
    private val startupPrefs = appContext.getSharedPreferences(STARTUP_PREFS, Context.MODE_PRIVATE)

    val dynamicColor: Flow<Boolean> = store.data.map { prefs ->
        val enabled = prefs[KEY_DYNAMIC_COLOR] ?: false
        if (startupDynamicColor() != enabled) startupPrefs.edit().putBoolean("dynamic_color", enabled).apply()
        enabled
    }

    /** 外观模式：跟随系统 / 浅色 / 深色（本机持久化）。 */
    val appearanceMode: Flow<AppearanceMode> = store.data.map { prefs ->
        AppearanceMode.fromStorage(prefs[KEY_APPEARANCE_MODE])
    }

    /** 是否隐藏系统状态栏（默认开启）。 */
    val hideStatusBar: Flow<Boolean> = store.data.map { prefs ->
        AppearanceSettings.hideStatusBarOrDefault(prefs[KEY_HIDE_STATUS_BAR])
    }

    /**
     * 沉浸光感（强 / 均衡 / 弱 / 关闭；默认「均衡」）。
     *
     * 该 Flow 被 `ClassToolsTheme` 注入 `LocalCtLight`：写入后全应用（含所有深层页面/弹层）立即重组生效，
     * 冷启动首帧用 [startupImmersiveLight] 同步镜像，避免「先按均衡画一帧再跳变」。
     */
    val immersiveLight: Flow<CtLightSetting> = store.data.map { prefs ->
        prefs[KEY_IMMERSIVE_LIGHT]?.let { CtLightSetting.fromStorage(it) } ?: CtLightSetting.Off
    }

    /** 远程公告（热更）；null = 无公告。 */
    val remoteAnnouncement: Flow<String?> = store.data.map { prefs -> prefs[KEY_REMOTE_ANNOUNCEMENT] }

    /** 远程公告对应的配置版本。 */
    val remoteAnnouncementVersion: Flow<Int> = store.data.map { prefs ->
        prefs[KEY_REMOTE_ANNOUNCEMENT_VERSION] ?: 0
    }

    /** 用户已关闭的公告版本；version 更大才再次展示。 */
    val dismissedAnnouncementVersion: Flow<Int> = store.data.map { prefs ->
        prefs[KEY_DISMISSED_ANNOUNCEMENT_VERSION] ?: 0
    }

    /** 首帧同步读取外观模式（未写入过 = 跟随系统）。 */
    fun startupAppearanceMode(): AppearanceMode =
        AppearanceMode.fromStorage(startupPrefs.getString(STARTUP_KEY_APPEARANCE_MODE, null))

    /** 首帧同步读取「隐藏状态栏」开关（未写入过 = 默认开启）。 */
    fun startupHideStatusBar(): Boolean = startupPrefs.getBoolean(
        STARTUP_KEY_HIDE_STATUS_BAR,
        AppearanceSettings.DEFAULT_HIDE_STATUS_BAR,
    )

    /** 首帧同步读取沉浸光感（未写入过 = 关闭，保持当前实色 M3 卡片）。 */
    fun startupImmersiveLight(): CtLightSetting =
        startupPrefs.getString(STARTUP_KEY_IMMERSIVE_LIGHT, null)
            ?.let { CtLightSetting.fromStorage(it) }
            ?: CtLightSetting.Off

    /** 应用远程配置（只覆盖白名单项；version 递增才生效一次）。 */
    suspend fun applyRemoteConfig(config: com.furina.classtools.data.remoteconfig.RemoteConfig) {
        val currentVersion = store.data.first()[KEY_REMOTE_CONFIG_VERSION] ?: 0
        if (config.version <= currentVersion) return
        config.appearance?.let { setAppearanceMode(AppearanceMode.fromStorage(it)) }
        config.hideStatusBar?.let { setHideStatusBar(it) }
        // 壁纸配色由用户在本机选择。
        store.edit { prefs ->
            if (!config.announcement.isNullOrBlank()) {
                prefs[KEY_REMOTE_ANNOUNCEMENT] = config.announcement
                prefs[KEY_REMOTE_ANNOUNCEMENT_VERSION] = config.version
            } else {
                // 新配置不再带公告 → 清掉旧公告，避免旧版本公告永久挂着。
                prefs.remove(KEY_REMOTE_ANNOUNCEMENT)
                prefs.remove(KEY_REMOTE_ANNOUNCEMENT_VERSION)
            }
        }
        store.edit { prefs -> prefs[KEY_REMOTE_CONFIG_VERSION] = config.version }
    }

    /** 关闭某个版本的远程公告。 */
    suspend fun dismissRemoteAnnouncement(version: Int) {
        store.edit { prefs -> prefs[KEY_DISMISSED_ANNOUNCEMENT_VERSION] = version }
    }

    fun startupDynamicColor(): Boolean = startupPrefs.getBoolean("dynamic_color", false)

    suspend fun setDynamicColor(enabled: Boolean) {
        startupPrefs.edit().putBoolean("dynamic_color", enabled).apply()
        store.edit { prefs -> prefs[KEY_DYNAMIC_COLOR] = enabled }
        com.furina.classtools.widget.DesktopWidgets.refresh(appContext)
    }

    suspend fun setAppearanceMode(mode: AppearanceMode) {
        startupPrefs.edit().putString(STARTUP_KEY_APPEARANCE_MODE, mode.storageValue).apply()
        store.edit { prefs -> prefs[KEY_APPEARANCE_MODE] = mode.storageValue }
        com.furina.classtools.widget.DesktopWidgets.refresh(appContext)
    }

    suspend fun setHideStatusBar(enabled: Boolean) {
        startupPrefs.edit().putBoolean(STARTUP_KEY_HIDE_STATUS_BAR, enabled).apply()
        store.edit { prefs -> prefs[KEY_HIDE_STATUS_BAR] = enabled }
    }

    /** 写沉浸光感（强/均衡/弱/关闭）；DataStore 真源 + 冷启动镜像同步写。 */
    suspend fun setImmersiveLight(setting: CtLightSetting) {
        startupPrefs.edit().putString(STARTUP_KEY_IMMERSIVE_LIGHT, setting.storageValue).apply()
        store.edit { prefs -> prefs[KEY_IMMERSIVE_LIGHT] = setting.storageValue }
    }

    internal fun homeContent(account: String): Flow<HomeContentSettings> {
        val key = homeKey(account)
        return store.data.map { decodeHomeContent(it[key]) }
    }

    internal suspend fun changeHomeContent(account: String, change: (HomeContentPreferences) -> HomeContentPreferences) {
        val key = homeKey(account)
        store.edit { prefs ->
            val current = decodeHomeContent(prefs[key])
            prefs[key] = NetJson.encodeToString(HomeContentSettings.serializer(), current.copy(
                preferences = change(current.preferences),
            ))
        }
    }

    internal suspend fun recordHomePhrase(account: String, id: String, seenAt: Long) {
        val key = homeKey(account)
        store.edit { prefs ->
            val current = decodeHomeContent(prefs[key])
            val seen = (current.seen + (id to maxOf(seenAt, current.seen[id] ?: 0L)))
                .filterValues { it <= seenAt && seenAt - it < HOME_RECENT_MS }
                .toList().sortedByDescending { it.second }.take(96).toMap()
            prefs[key] = NetJson.encodeToString(HomeContentSettings.serializer(), current.copy(seen = seen))
        }
    }

    private fun decodeHomeContent(value: String?): HomeContentSettings = value?.let {
        runCatching { NetJson.decodeFromString<HomeContentSettings>(it) }.getOrNull()
    } ?: HomeContentSettings()

    private fun homeKey(account: String): Preferences.Key<String> {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(account.encodeToByteArray())
        return stringPreferencesKey("home_content_" + digest.take(16).joinToString("") { "%02x".format(it) })
    }

    private companion object {
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_APPEARANCE_MODE = stringPreferencesKey(AppearanceSettings.KEY_APPEARANCE_MODE)
        val KEY_HIDE_STATUS_BAR = booleanPreferencesKey(AppearanceSettings.KEY_HIDE_STATUS_BAR)
        val KEY_IMMERSIVE_LIGHT = stringPreferencesKey(AppearanceSettings.KEY_IMMERSIVE_LIGHT)
        val KEY_REMOTE_CONFIG_VERSION = intPreferencesKey("remote_config_version")
        val KEY_REMOTE_ANNOUNCEMENT = stringPreferencesKey("remote_announcement")
        val KEY_REMOTE_ANNOUNCEMENT_VERSION = intPreferencesKey("remote_announcement_version")
        val KEY_DISMISSED_ANNOUNCEMENT_VERSION = intPreferencesKey("dismissed_announcement_version")

        /** 冷启动镜像（仅外观模式 + 隐藏状态栏 + 沉浸光感；DataStore 仍是真源）。 */
        const val STARTUP_PREFS = "ct_appearance_startup"
        const val STARTUP_KEY_APPEARANCE_MODE = "appearance_mode"
        const val STARTUP_KEY_HIDE_STATUS_BAR = "hide_status_bar"
        const val STARTUP_KEY_IMMERSIVE_LIGHT = "immersive_light"

        val Context.settingsDataStore by preferencesDataStore(name = "ct_settings")
    }
}

/**
 * [AppSettingsStore] 的 CompositionLocal：MainActivity 在主题根节点提供。
 *
 * 预览 / 孤立组件为 null → 「我的」页回退到入参（预览可控），生产环境直接读写本机设置。
 */
val LocalAppSettingsStore = staticCompositionLocalOf<AppSettingsStore?> { null }



