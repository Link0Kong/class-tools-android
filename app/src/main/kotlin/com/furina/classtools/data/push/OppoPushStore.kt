package com.furina.classtools.data.push

import android.content.Context

/**
 * OPPO 推送本机状态存储（RegistrationID + 最后状态）。
 *
 * 只存设备级推送信息（不含学号/姓名/token）；SharedPreferences 私有目录，随 App 卸载清除。
 * 独立接口便于单测注入内存实现。
 */
interface OppoPushStore {
    /** 已缓存的 RegistrationID（云端上传与状态恢复用；无则 null）。 */
    fun registrationId(): String?

    /** 覆盖保存状态（REGISTERED 时保存 registrationId；其它状态只更新状态字段）。 */
    fun save(status: OppoPushStatus)
}

/** SharedPreferences 实现（`oppo_push` 私有文件；不参与云端 userdata 同步）。 */
class PrefsOppoPushStore(context: Context) : OppoPushStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun registrationId(): String? =
        prefs.getString(KEY_REGISTRATION_ID, null)?.trim()?.takeIf { it.isNotEmpty() }

    override fun save(status: OppoPushStatus) {
        val editor = prefs.edit()
            .putString(KEY_STATE, status.state.name)
            .putInt(KEY_ERROR_CODE, status.errorCode)
        if (status.registrationId.isNotBlank()) {
            editor.putString(KEY_REGISTRATION_ID, status.registrationId)
        }
        editor.apply()
    }

    private companion object {
        const val PREFS_NAME = "oppo_push"
        const val KEY_REGISTRATION_ID = "registration_id"
        const val KEY_STATE = "state"
        const val KEY_ERROR_CODE = "error_code"
    }
}
