package com.furina.classtools.data.account

import com.furina.classtools.BuildConfig
import java.security.SecureRandom
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * OPPO 账号服务配置（构建期来自 `app/oppo.properties` → BuildConfig）。
 *
 * 未配置（AppID 或 AppKey 为空）→ 身份页「OPPO 账号快捷登录」入口隐藏、「我的 → 账号」入口隐藏，
 * 其它功能不受影响。客户端**不**自行验证 OPPO token/openid：只拿 SDK 返回的 authCode 调 ECS 接口。
 */
data class OppoAccountConfig(
    val appId: String = "",
    val appKey: String = "",
    val scope: String = DEFAULT_SCOPE,
    val baseUrl: String = DEFAULT_BASE_URL,
) {
    /** AppID + AppKey 都非空才可用（官方 AcOauthRequest.Builder 要求两者均非空）。 */
    val isConfigured: Boolean
        get() = appId.isNotBlank() && appKey.isNotBlank()

    /** 授权 scope（空格分隔；默认仅 openid = 静默授权最小权限）。 */
    val effectiveScope: String
        get() = scope.trim().ifBlank { DEFAULT_SCOPE }

    /** 账号服务根地址（去掉结尾 `/`；空 → 构建期注入的默认地址）。 */
    val normalizedBaseUrl: String
        get() = baseUrl.trim().trimEnd('/').ifBlank { DEFAULT_BASE_URL }

    companion object {
        const val DEFAULT_SCOPE: String = "openid"

        /**
         * ECS 账号接口默认根：**由 BuildConfig 按 buildType 注入**（与 Lead 契约一致；
         * debug = 本机调试 IP，release = `https://tool.furina.fun/account`；gradle 属性
         * `ct.accountBaseUrl` / `ct.accountBaseUrlRelease` 可覆盖）。
         */
        val DEFAULT_BASE_URL: String = BuildConfig.ACCOUNT_BASE_URL

        fun of(appId: String?, appKey: String?, scope: String?, baseUrl: String?): OppoAccountConfig =
            OppoAccountConfig(
                appId = appId?.trim().orEmpty(),
                appKey = appKey?.trim().orEmpty(),
                scope = scope?.trim().orEmpty().ifBlank { DEFAULT_SCOPE },
                baseUrl = baseUrl?.trim().orEmpty().ifBlank { DEFAULT_BASE_URL },
            )
    }
}

/**
 * 设备/系统是否可以使用 OPPO 账号授权（**严格 ColorOS**，纯函数便于 JVM 单测）。
 *
 * 用户 2026-11-17 晚反馈：OPPO 账号入口只应在 ColorOS 手机上出现。
 * 因此仅依据 **ColorOS/OPPO ROM 系统属性**（`ro.build.version.oplusrom` / `ro.build.version.opporom` /
 * `ro.build.version.oplusos` 非空）判断；**不再**仅凭 manufacturer/brand 含 oppo/oneplus/realme/oplus 等
 * 品牌字样（一加/realme 国际版/模拟器等可能无 ColorOS 账号服务）。非 ColorOS → 快捷登录与「我的 → 账号」入口隐藏。
 */
object OppoAccountDeviceSupport {

    /** ColorOS ROM 属性键（任一非空即视为 ColorOS 系；见 AppContainer.hasColorOsRom）。 */
    val COLOR_OS_ROM_PROPERTIES: List<String> = listOf(
        "ro.build.version.oplusrom",
        "ro.build.version.opporom",
        "ro.build.version.oplusos",
    )

    /** 严格 ColorOS：只有探测到 ColorOS ROM 特征才返回 true（不看厂商/品牌名）。 */
    fun isSupported(hasColorOsRom: Boolean): Boolean = hasColorOsRom
}

/**
 * OAuth 授权 state 生成（防重放；官方要求随机字符即可，我们同时用于本地回调校验）：
 * SecureRandom → 32 位小写十六进制。
 */
object OppoOAuthState {

    private val random = SecureRandom()
    private val HEX = "0123456789abcdef".toCharArray()

    fun random(): String = buildString(32) {
        repeat(32) { append(HEX[random.nextInt(HEX.size)]) }
    }

    fun isValid(state: String?): Boolean =
        state != null && STATE_REGEX.matches(state)

    private val STATE_REGEX = Regex("^[0-9a-f]{16,64}$")
}

/**
 * 账号绑定展示文案 / 日期格式化（纯函数）。
 *
 * 隐私：绑定列表只展示 OPPO **昵称** 与绑定日期；不展示 openid/unionid/手机号。
 */
object OppoAccountText {

    /** 服务端约定的单学号最多绑定数（列表接口也会返回 max，以服务端为准）。 */
    const val DEFAULT_MAX_BINDINGS: Int = 3

    /** 绑定日期：`2026-11-17T12:00:00+08:00` / `2026-11-17 12:00:00` / `2026-11-17` → `2026-11-17`。 */
    fun formatCreatedAt(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return ""
        // 优先按 ISO 带偏移解析（服务端 createdAt 为 ISO 字符串）
        runCatching { return OffsetDateTime.parse(text).toLocalDate().toString() }
        // 退化：取前 10 位（yyyy-MM-dd）并校验
        val head = text.take(10)
        runCatching { return LocalDate.parse(head).toString() }
        return text.take(19)
    }

    /** 「我的」行副标题：已绑定 N/3。 */
    fun bindingsLabel(count: Int, max: Int = DEFAULT_MAX_BINDINGS): String {
        val safeMax = if (max in 1..MAX_LIMIT) max else DEFAULT_MAX_BINDINGS
        val safeCount = count.coerceIn(0, safeMax)
        return "已绑定 $safeCount/$safeMax"
    }

    /**
     * 快捷登录/绑定失败的**可诊断**提示（简短，不写长文；错误码保留给用户反馈给管理员）。
     * - OPPO SDK 回调错误码：中文原因 + `（code=xxx）`；未知码 → `OPPO 授权失败（code=xxx）`；
     * - 服务端 `oppo_exchange_failed` 见 [exchangeFailedMessage]。
     */
    fun oAuthErrorText(code: Int): String = when (code) {
        -2015 -> "请先在系统设置登录 OPPO 账号（code=-2015）"
        -2011 -> "授权状态校验失败，请重试（code=-2011）"
        -2012, -2013, -2014 -> "OPPO 授权未完成，请重试（code=$code）"
        -210, -2002, -2003, -2009 -> "OPPO 账号服务不可用，请重试（code=$code）"
        1117001 -> "OPPO 账号授权环境校验失败（code=1117001）"
        else -> "OPPO 授权失败（code=$code）"
    }

    /**
     * ECS 返回 `oppo_exchange_failed`（服务端用 authCode 换 openid 失败，常见 message=`invalid_client`：
     * 账号服务未开通 / AppID·AppKey·包名·签名不匹配）时的可诊断提示。
     */
    fun exchangeFailedMessage(detail: String?, providerLabel: String = "OPPO"): String {
        val reason = detail?.trim().orEmpty().take(120)
        return if (reason.isEmpty()) {
            "$providerLabel 账号服务未开通或凭证不匹配，请联系管理员"
        } else {
            "$providerLabel 账号服务未开通或凭证不匹配（$reason），请联系管理员"
        }
    }

    /** 服务端未绑定提示的兜底文案（服务端会带 message，优先用服务端）。 */
    fun unboundFallback(providerLabel: String = "OPPO"): String =
        "该 $providerLabel 账号未绑定学号，请先用学号+姓名登录，再到「我的 → 账号」绑定"

    /** @deprecated 使用 [unboundFallback]（荣耀与 OPPO 共用同一套文案生成）。 */
    const val UNBOUND_FALLBACK: String =
        "该 OPPO 账号未绑定学号，请先用学号+姓名登录，再到「我的 → 账号」绑定"

    private const val MAX_LIMIT = 20
}

/**
 * 「OPPO 账号快捷登录」按钮的纯交互规则（用户 2026-11-17 晚反馈：未填学号/姓名也能用）。
 *
 * 关键约束：**只依赖协议勾选与授权中状态**，与学号/姓名/表单校验完全无关；
 * 未勾选协议时点击给「请先勾选同意协议」提示，而不是静默禁用。
 */
object OppoQuickLoginRule {

    /** 授权/校核进行中时按钮禁用（防重复拉起授权页）；其余情况一律可点。 */
    fun isButtonEnabled(authorizing: Boolean, verifying: Boolean): Boolean = !authorizing && !verifying

    /** 点击后的动作：未勾选协议 → 提示；授权/校核中 → 忽略；否则拉起 OPPO 授权。 */
    fun onClick(consentAccepted: Boolean, authorizing: Boolean, verifying: Boolean): OppoQuickLoginClick = when {
        authorizing || verifying -> OppoQuickLoginClick.Ignore
        !consentAccepted -> OppoQuickLoginClick.ShowConsentHint
        else -> OppoQuickLoginClick.Trigger
    }

    /** 未勾选协议时的简短提示文案（不写长文）。 */
    const val CONSENT_HINT: String = "请先勾选同意《用户协议》《隐私政策》"
}

/** [OppoQuickLoginRule.onClick] 的动作结果。 */
enum class OppoQuickLoginClick {
    /** 拉起 OPPO 账号授权。 */
    Trigger,

    /** 未勾选协议：在按钮下方显示简短提示。 */
    ShowConsentHint,

    /** 授权/校核进行中：忽略点击。 */
    Ignore,
}
