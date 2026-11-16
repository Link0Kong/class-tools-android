package com.furina.classtools.data.update

import android.content.Context

/**
 * 每日静默检查的本机记录（SharedPreferences）。
 *
 * 只存两个设备级值，不含任何账号/身份信息：
 * - [lastCheckAt]：上次「启动静默检查」的时间（每天最多一次；手动检查不写）；
 * - [dismissedVersionCode]：用户「稍后」跳过的版本（避免手动打开时反复弹同一个弹窗）。
 *
 * 纯策略 [UpdateDailyPolicy] 独立可单测；本接口只负责持久化。
 */
interface UpdateCheckStore {
    fun lastCheckAt(): Long

    fun markChecked(atMs: Long)

    fun dismissedVersionCode(): Int

    fun markDismissed(versionCode: Int)
}

/** SharedPreferences 实现（设备级；`ct_update` 文件）。 */
class PrefsUpdateCheckStore(context: Context) : UpdateCheckStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun lastCheckAt(): Long = prefs.getLong(KEY_LAST_CHECK_AT, 0L)

    override fun markChecked(atMs: Long) {
        prefs.edit().putLong(KEY_LAST_CHECK_AT, atMs).apply()
    }

    override fun dismissedVersionCode(): Int = prefs.getInt(KEY_DISMISSED_VERSION_CODE, 0)

    override fun markDismissed(versionCode: Int) {
        prefs.edit().putInt(KEY_DISMISSED_VERSION_CODE, versionCode).apply()
    }

    companion object {
        const val PREFS_NAME: String = "ct_update"
        const val KEY_LAST_CHECK_AT: String = "last_check_at"
        const val KEY_DISMISSED_VERSION_CODE: String = "dismissed_version_code"
    }
}
