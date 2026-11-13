package com.furina.classtools.data.jw

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.furina.classtools.features.jw.JwLoginLogic
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 本机加密保存的教务凭据（只在内存中传递，绝不上传/同步/打日志）。 */
data class JwSavedCredential(
    val sid: String,
    val password: String,
)

/**
 * 教务密码**仅本机**加密存储（契约 §5.3-2）。
 *
 * - 加密：Android Keystore 中的 AES-256/GCM 密钥（不可导出，密钥别名固定、随 App 卸载失效）；
 * - 存储：SharedPreferences 只存 `sid` + 密文(Base64) + IV(Base64)，不存明文；
 * - 绑定：凭据与当前身份 `sid` 绑定；sid 变化 / 解密失败 → 视为无凭据（要求重新输入）；
 * - 清除：[clear] 删除 SharedPreferences 记录（UI 的「清除登录」同时清 Cookie/缓存）；
 * - 红线：不写日志、不上报、不参与 userdata 同步；`android:allowBackup="false"` 已禁止备份。
 */
class JwCredentialStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 本机已存凭据绑定的 sid（无则 null）。 */
    fun savedSid(): String? = runCatching { prefs.getString(KEY_SID, null) }.getOrNull()

    /**
     * 读取当前身份可用的凭据：sid 不匹配 / 数据损坏 / 解密失败返回 null（并自愈清掉坏数据）。
     */
    fun load(currentSid: String): JwSavedCredential? {
        val storedSid = savedSid()?.takeIf { it.isNotBlank() } ?: return null
        if (!JwLoginLogic.isCredentialCompatible(storedSid, currentSid)) return null
        val ivBase64 = prefs.getString(KEY_IV, null) ?: return null
        val cipherBase64 = prefs.getString(KEY_CT, null) ?: return null
        return runCatching {
            val iv = JwLoginLogic.decodeBase64(ivBase64) ?: return null
            val cipherBytes = JwLoginLogic.decodeBase64(cipherBase64) ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(TAG_BITS, iv),
            )
            val plain = cipher.doFinal(cipherBytes)
            JwSavedCredential(storedSid, String(plain, Charsets.UTF_8))
        }.getOrElse {
            // 密钥失效 / 数据损坏：清掉残留，避免每次启动都失败（不打印任何内容）。
            runCatching { clear() }
            null
        }
    }

    /** 加密保存当前身份的密码；返回是否成功（失败时绝不明文落盘）。 */
    fun save(currentSid: String, password: String): Boolean {
        if (currentSid.isBlank() || password.isEmpty()) return false
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val cipherBytes = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
            val ivBase64 = JwLoginLogic.encodeBase64(cipher.iv) ?: return false
            val cipherBase64 = JwLoginLogic.encodeBase64(cipherBytes) ?: return false
            prefs.edit()
                .putString(KEY_SID, currentSid)
                .putString(KEY_IV, ivBase64)
                .putString(KEY_CT, cipherBase64)
                .commit()
        }.getOrDefault(false)
    }

    /** 删除本机保存的教务密码（Cookie/缓存由调用方清理）。 */
    fun clear() {
        runCatching { prefs.edit().clear().commit() }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        /** SharedPreferences 文件名（只存密文）。 */
        const val PREFS_NAME: String = "jw_login"

        private const val KEY_SID = "sid"
        private const val KEY_IV = "iv"
        private const val KEY_CT = "ct"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "jw_login_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val KEY_SIZE_BITS = 256
    }
}
