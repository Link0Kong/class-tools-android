package com.furina.classtools.data.account

/**
 * 荣耀账号服务配置（构建期来自 `app/honor.properties` → BuildConfig）。
 *
 * 未配置（ClientId 为空）→ 身份页「荣耀账号快捷登录」与「我的 → 荣耀账号」入口隐藏，
 * 其它功能不受影响。客户端只拿 SDK 返回的 authCode 调 ECS `/honor` 系列接口，
 * **不自行验证** openId/token（Client Secret 只在服务端）。
 */
data class HonorAccountConfig(
    val clientId: String = "",
    val scope: String = DEFAULT_SCOPE,
    val baseUrl: String = DEFAULT_BASE_URL,
) {
    val isConfigured: Boolean get() = clientId.isNotBlank()

    /** 授权 scope：必须包含 `openid`；缺省补全（荣耀要求 openid 必选）。 */
    val effectiveScope: String
        get() {
            val parts = scope.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
            if (parts.none { it == "openid" }) parts.add(0, "openid")
            return parts.joinToString(" ")
        }

    val normalizedBaseUrl: String
        get() = baseUrl.trim().trimEnd('/').ifBlank { DEFAULT_BASE_URL }

    companion object {
        const val DEFAULT_SCOPE: String = "openid profile"

        /** 与 OPPO 共用同一个 ECS 账号服务根地址（gradle 属性 ct.accountBaseUrl 可覆盖）。 */
        const val DEFAULT_BASE_URL: String = OppoAccountConfig.DEFAULT_BASE_URL

        fun of(clientId: String?, scope: String?, baseUrl: String?): HonorAccountConfig =
            HonorAccountConfig(
                clientId = clientId?.trim().orEmpty(),
                scope = scope?.trim().orEmpty().ifBlank { DEFAULT_SCOPE },
                baseUrl = baseUrl?.trim().orEmpty().ifBlank { DEFAULT_BASE_URL },
            )
    }
}

/**
 * 设备是否可以使用荣耀账号授权（纯函数，便于 JVM 单测）。
 *
 * 荣耀 SDK 只在荣耀设备（MagicOS）上可用：以 `Build.MANUFACTURER == HONOR` 与 SDK 的
 * `HonorIdSignInManager.isHonorMachine()` 任一为 true 作为判定；模拟器/其它品牌一律隐藏入口。
 */
object HonorAccountDeviceSupport {
    fun isSupported(manufacturer: String?, sdkSaysHonorMachine: Boolean): Boolean =
        sdkSaysHonorMachine || manufacturer?.trim().equals("HONOR", ignoreCase = true) == true
}

/** 荣耀账号展示文案 / 错误码（纯函数）。 */
object HonorAccountText {

    const val LABEL: String = "荣耀"

    /** 快捷登录 / 绑定失败的简短可诊断提示（保留 SDK 错误码）。 */
    fun oAuthErrorText(code: Int): String = when (code) {
        3002 -> "已取消荣耀账号授权（code=3002）"
        5, 2005 -> "网络不可用，请稍后重试（code=$code）"
        30 -> "荣耀账号登录状态已失效，请重新登录（code=30）"
        12 -> "荣耀账号授权参数不完整（code=12）"
        34 -> "当前设备未安装荣耀账号服务，无法快捷登录（code=34）"
        35 -> "荣耀账号服务版本过低，请升级后重试（code=35）"
        67 -> "荣耀账号登录失败，请重试（code=67）"
        else -> "荣耀授权失败（code=$code）"
    }

    fun unboundFallback(): String = OppoAccountText.unboundFallback(LABEL)
}

/** 荣耀「快捷登录」按钮规则（与 OPPO 同一套交互：只依赖协议勾选与授权中状态）。 */
object HonorQuickLoginRule {

    fun isButtonEnabled(authorizing: Boolean, verifying: Boolean): Boolean =
        OppoQuickLoginRule.isButtonEnabled(authorizing, verifying)

    fun onClick(consentAccepted: Boolean, authorizing: Boolean, verifying: Boolean): OppoQuickLoginClick =
        OppoQuickLoginRule.onClick(consentAccepted, authorizing, verifying)

    const val CONSENT_HINT: String = OppoQuickLoginRule.CONSENT_HINT
}

