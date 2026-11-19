package com.furina.classtools.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.furina.classtools.ui.theme.CtLightIntensity
import com.furina.classtools.ui.theme.CtLightSetting

/**
 * 沉浸光感（Immersive Light）本机偏好。
 *
 * 与「外观模式」同级：都是界面偏好，不进 userdata v6 云同步（换设备不跟随），
 * 只在本机 SharedPreferences 保存一个字符串："strong" / "balanced" / "weak" / "off"。
 *
 * 兼容：读取到未知值 / 缺失 → [CtLightSetting.Default]（开启 + 均衡），不覆盖用户数据。
 */
class ImmersiveLightPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 当前设置。 */
    fun load(): CtLightSetting = CtLightSetting.fromStorage(prefs.getString(KEY, null))

    /** 保存设置（立即生效：主题通过 StateFlow/Compose state 重新注入）。 */
    fun save(setting: CtLightSetting) {
        prefs.edit { putString(KEY, setting.storageValue) }
    }

    /** 便捷：按强度保存（enabled = true）。 */
    fun saveIntensity(intensity: CtLightIntensity) {
        save(CtLightSetting(intensity = intensity, enabled = true))
    }

    /** 便捷：关闭 / 恢复默认。 */
    fun saveEnabled(enabled: Boolean) {
        val current = load()
        save(if (enabled) CtLightSetting(current.intensity, true) else CtLightSetting.Off)
    }

    companion object {
        /** SharedPreferences 文件名（与外观/状态栏开关同一层，禁止改名）。 */
        const val FILE: String = "ct_appearance_prefs"

        /** 键名（持久化契约，禁止改名）。 */
        const val KEY: String = "ct_immersive_light"
    }
}
