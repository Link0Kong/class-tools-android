package com.furina.classtools.data.update

import android.content.Context

/**
 * 更新弹窗的本机记录（SharedPreferences）。
 *
 * 只存设备级值，不含任何账号/身份信息：
 * - [dismissedVersionCode]：用户「稍后」跳过的版本（避免手动打开时反复弹同一个弹窗）。
 *
 * 2026-11-16：自动检查改为「每次冷启动一次」（[UpdateAutoCheckGate]，进程内状态），
 * 不再持久化 last_check_at（每日节流已去掉）。
 */
interface UpdateCheckStore {
    fun dismissedVersionCode(): Int

    fun markDismissed(versionCode: Int)
}

/** SharedPreferences 实现（设备级；`ct_update` 文件）。 */
class PrefsUpdateCheckStore(context: Context) : UpdateCheckStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun dismissedVersionCode(): Int = prefs.getInt(KEY_DISMISSED_VERSION_CODE, 0)

    override fun markDismissed(versionCode: Int) {
        prefs.edit().putInt(KEY_DISMISSED_VERSION_CODE, versionCode).apply()
    }

    companion object {
        const val PREFS_NAME: String = "ct_update"
        const val KEY_DISMISSED_VERSION_CODE: String = "dismissed_version_code"
    }
}
