package com.furina.classtools.ui.theme

/**
 * 本机「外观模式」（我的 → 外观）：跟随系统 / 浅色 / 深色。
 *
 * 纯逻辑（无 Android/Compose 依赖，可直接 JVM 单测）：
 * - [storageValue] 是持久化契约（DataStore 字符串），**不可随意改名**，否则老用户设置会丢；
 * - [resolveDark] 把「外观模式 + 系统是否深色」解析成 [com.furina.classtools.ui.theme.ClassToolsTheme]
 *   需要的 `darkTheme` 布尔值（跟随系统时取系统值，浅色/深色时强制）；
 * - [fromStorage] 对未知/缺失/大小写异常的值一律回退 [DEFAULT]，保证升级/降级可平滑读取。
 *
 * 该设置只存本机（设备偏好），不进入 userdata v6 云同步。
 */
enum class AppearanceMode(
    /** DataStore 持久化值（契约，禁止改名）。 */
    val storageValue: String,
    /** 界面展示文案（三选一单选行）。 */
    val label: String,
) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色"),
    ;

    /**
     * 是否使用深色主题。
     *
     * @param systemDark 系统当前是否处于深色模式（`isSystemInDarkTheme()`）
     */
    fun resolveDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        val DEFAULT: AppearanceMode = SYSTEM

        /** 解析持久化字符串：未知/空/大小写异常 → [DEFAULT]（不覆盖用户数据，只回退展示）。 */
        fun fromStorage(raw: String?): AppearanceMode {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.storageValue == normalized } ?: DEFAULT
        }
    }
}

/** 「外观」相关本机设置键与默认值（DataStore + 冷启动 SharedPreferences 镜像共用）。 */
object AppearanceSettings {

    /** DataStore（`ct_settings`）键：外观模式字符串。 */
    const val KEY_APPEARANCE_MODE: String = "appearance_mode"

    /** DataStore（`ct_settings`）键：是否隐藏系统状态栏。 */
    const val KEY_HIDE_STATUS_BAR: String = "hide_status_bar"

    /** 「隐藏状态栏」默认开启（用户要求：app 使用时顶部通知栏隐藏）。 */
    const val DEFAULT_HIDE_STATUS_BAR: Boolean = true

    /** 缺失（首次安装/旧版本）时回退默认值；显式 false 才显示状态栏。 */
    fun hideStatusBarOrDefault(stored: Boolean?): Boolean = stored ?: DEFAULT_HIDE_STATUS_BAR
}
