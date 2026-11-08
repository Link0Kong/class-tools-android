package com.furina.classtools.data.session

/**
 * 已通过班级身份核验的会话（对齐网页/小程序本机身份字段）。
 *
 * @param sid 学号（本机展示/导出命名用；不写日志、不外发）
 * @param name 姓名
 * @param token 云端班级 token（请求头 X-Class-Token）
 * @param role 职务（班委才有，可为空）
 * @param badge 徽章（可为空）
 * @param isAdmin 是否管理员
 * @param issuedAt token 签发时间（毫秒）
 * @param expiresAt 过期时间戳（毫秒，0 表示未知；服务端 30 天）
 */
data class Session(
    val sid: String,
    val name: String,
    val token: String,
    val role: String = "",
    val badge: String = "",
    val isAdmin: Boolean = false,
    /** Debug 构建的本地预览会话（不向云端发身份请求）。 */
    val debug: Boolean = false,
    val issuedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = 0L,
) {
    /** 只用于 UI 展示的脱敏学号。 */
    val maskedSid: String
        get() = when {
            sid.length <= 4 -> "***"
            else -> sid.take(2) + "***" + sid.takeLast(2)
        }

    fun isExpired(now: Long = System.currentTimeMillis()): Boolean =
        expiresAt > 0 && now > expiresAt

    /**
     * 与网页一致的 29 天本地判定：小于 29 天直接进主界面，不重新核验。
     * 若未记录签发时间则视为可用（旧数据兼容）。
     */
    fun isLikelyValid(now: Long = System.currentTimeMillis()): Boolean {
        if (isExpired(now)) return false
        if (issuedAt <= 0L) return true
        return now - issuedAt < 29L * 24 * 60 * 60 * 1000
    }
}

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val session: Session) : SessionState
}
