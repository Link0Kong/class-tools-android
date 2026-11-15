package com.furina.classtools.data.activity

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** 活动列表项（第二课堂 `/organizations/activity/page/for_sign`）。 */
data class ScActivity(
    val id: String,
    val name: String,
    val state: Int,
    val actBeginTime: Long?,
    val actEndTime: Long?,
    val supBeginTime: Long?,
    val supEndTime: Long?,
)

/** 活动里的一个报名角色。 */
data class ScRole(
    val supRoleId: String,
    val name: String,
    val numLimit: Int?,
    val signUpNum: Int,
) {
    val left: Int? get() = numLimit?.let { (it - signUpNum).coerceAtLeast(0) }
    val full: Boolean get() = numLimit != null && signUpNum >= numLimit
}

data class ScDetail(
    val id: String,
    val name: String,
    val state: Int,
    val isSignUp: Int,
    val roles: List<ScRole>,
)

/** 本机保存的学校会话（只在本机，不上云）。 */
data class ActivityToken(
    val token: String,
    val userId: String,
    val classId: String,
    val savedAt: Long,
)

/** 请求签名（2026-11-14 用抓包样本逆向并 30/30 验证通过）。 */
object ActivitySigner {

    const val SALT = "rndAgtFzYJpyKlRY"

    private val rnd = SecureRandom()
    private const val ALPHABET =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    fun nonce(): String = buildString(16) {
        repeat(16) { append(ALPHABET[rnd.nextInt(ALPHABET.length)]) }
    }

    /** base64(ts:nonce:md5(salt+url+ts+nonce):token)；url 必须是完整 URL 且不含 query。 */
    fun sign(url: String, token: String, ts: String, nonce: String): String {
        val md5 = MessageDigest.getInstance("MD5")
            .digest((SALT + url + ts + nonce).toByteArray(Charsets.UTF_8))
        val hex = buildString(32) {
            md5.forEach { b -> append("%02x".format(b.toInt() and 0xFF)) }
        }
        val raw = "$ts:$nonce:$hex:$token"
        return Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
    }

    fun header(url: String, token: String, nowSeconds: Long = System.currentTimeMillis() / 1000): String =
        sign(url, token, nowSeconds.toString(), nonce())
}
