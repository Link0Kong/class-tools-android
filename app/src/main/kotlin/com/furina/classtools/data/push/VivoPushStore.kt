package com.furina.classtools.data.push

import android.content.Context

/** A possible server binding, recorded before upload so an interrupted request can be cleaned up. */
data class VivoBinding(val sid: String, val regId: String, val legacyOwnerUnknown: Boolean = false)

/** Private device state. Former class login credentials are never stored here. */
interface VivoPushStore {
    fun registrationId(): String?
    fun binding(): VivoBinding?
    fun rememberBinding(binding: VivoBinding?): Boolean
    fun revokedId(): String?
    fun markRevoked(regId: String): Boolean

    fun save(status: VivoPushStatus)
}

/** SharedPreferences 实现（`vivo_push` 私有文件；不参与云端 userdata 同步）。 */
class PrefsVivoPushStore(context: Context) : VivoPushStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun registrationId(): String? =
        prefs.getString(KEY_REGISTRATION_ID, null)?.trim()?.takeIf { it.isNotEmpty() }

    override fun binding(): VivoBinding? {
        val sid = prefs.getString("binding_sid", null) ?: return null
        val regId = prefs.getString("binding_reg_id", null) ?: return null
        return VivoBinding(sid, regId, prefs.getBoolean("legacy_owner_unknown", false))
    }

    override fun rememberBinding(binding: VivoBinding?): Boolean = prefs.edit()
        .putString("binding_sid", binding?.sid)
        .putString("binding_reg_id", binding?.regId)
        .putBoolean("legacy_owner_unknown", binding?.legacyOwnerUnknown == true)
        .commit()

    override fun revokedId(): String? = prefs.getString("revoked_reg_id", null)

    override fun markRevoked(regId: String): Boolean = prefs.edit()
        .putString("revoked_reg_id", regId)
        .remove("binding_sid")
        .remove("binding_reg_id")
        .remove("legacy_owner_unknown")
        .remove(KEY_REGISTRATION_ID)
        .commit()

    override fun save(status: VivoPushStatus) {
        val editor = prefs.edit()
            .putString(KEY_STATE, status.state.name)
            .putInt(KEY_ERROR_CODE, status.errorCode)
        if (status.registrationId.isNotBlank()) {
            editor.putString(KEY_REGISTRATION_ID, status.registrationId)
        }
        editor.apply()
    }

    private companion object {
        const val PREFS_NAME = "vivo_push"
        const val KEY_REGISTRATION_ID = "registration_id"
        const val KEY_STATE = "state"
        const val KEY_ERROR_CODE = "error_code"
    }
}

/**
 * SDK 回调 → 进程内 manager 的桥。
 *
 * vivo 的 `OpenClientPushMessageReceiver` 由系统按 Manifest 实例化，拿不到 AppContainer；
 * 因此用进程级单例把 `onReceiveRegId` 转给 [VivoPushManager]。
 */
object VivoPushBus {

    @Volatile
    private var onRegId: ((String) -> Unit)? = null

    fun register(listener: (String) -> Unit) {
        onRegId = listener
    }

    fun clear() {
        onRegId = null
    }

    fun dispatchRegId(regId: String?) {
        val value = regId?.trim().orEmpty()
        if (value.isNotEmpty()) onRegId?.invoke(value)
    }
}
