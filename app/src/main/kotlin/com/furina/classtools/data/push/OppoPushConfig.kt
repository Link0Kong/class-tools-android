package com.furina.classtools.data.push

/**
 * OPPO PUSH 密钥配置（构建期来自 `app/oppo.properties` → `BuildConfig.OPPO_PUSH_APP_KEY/OPPO_PUSH_APP_SECRET`）。
 *
 * 空值 = 未配置：客户端**不 init / 不 register / 不上传 token**，设置页显示「未配置」，
 * 其余功能完全不受影响（用户尚未在 OPPO 开放平台开通推送服务时即为此态）。
 */
data class OppoPushConfig(
    val appKey: String = "",
    val appSecret: String = "",
) {
    /** 官方 `register(appKey, appSecret, callback)` 两个参数都必需，缺一即视为未配置。 */
    val isConfigured: Boolean
        get() = appKey.isNotBlank() && appSecret.isNotBlank()

    companion object {
        fun of(appKey: String?, appSecret: String?): OppoPushConfig = OppoPushConfig(
            appKey = appKey?.trim().orEmpty(),
            appSecret = appSecret?.trim().orEmpty(),
        )
    }
}

/** OPPO 推送客户端状态（设置页展示 / 注册流程；顺序即状态机流转）。 */
enum class OppoPushState {
    /** 未配置 AppKey/AppSecret（安全跳过，不联网不注册）。 */
    NOT_CONFIGURED,

    /** 设备/系统不支持 OPPO PUSH（非 ColorOS 或 MCS 不可用）。 */
    UNSUPPORTED,

    /** 已发起注册，等待 SDK 回调。 */
    REGISTERING,

    /** 已拿到 RegistrationID（可上传云端）。 */
    REGISTERED,

    /** 注册失败（错误码见 [OppoPushText.errorHint]）。 */
    FAILED,
}

/** 推送状态快照（只含展示必需信息；RegistrationID 在 UI 上永远掩码显示）。 */
data class OppoPushStatus(
    val state: OppoPushState,
    val registrationId: String = "",
    val errorCode: Int = 0,
    val errorMessage: String = "",
) {
    /** 设置页副标题（如「未配置」「已注册 · ****a1b2」「注册失败（14）」）。 */
    val label: String get() = OppoPushText.label(this)

    /** 是否可上传/可重试（有 RegistrationID 或失败可重试）。 */
    val canUpload: Boolean
        get() = registrationId.isNotBlank() &&
            (state == OppoPushState.REGISTERED || state == OppoPushState.REGISTERING)

    companion object {
        val NotConfigured: OppoPushStatus = OppoPushStatus(OppoPushState.NOT_CONFIGURED)
        val Unsupported: OppoPushStatus = OppoPushStatus(OppoPushState.UNSUPPORTED)
        val Registering: OppoPushStatus = OppoPushStatus(OppoPushState.REGISTERING)
    }
}

/**
 * 推送状态文案 / 掩码 / 错误码映射（纯函数，单测直接覆盖）。
 *
 * 错误码与官方 `com.heytap.msp.push.mode.ErrorCode` 一致（不直接引用 SDK 常量，
 * 保证本文件可在不含 Android SDK 的 JVM 单测中加载）。
 */
object OppoPushText {

    /** 官方 `ErrorCode.SUCCESS`。 */
    const val SUCCESS_CODE: Int = 0

    /** RegistrationID 掩码：保留末 4 位（不足则全掩码），例如 `****a1b2`。 */
    fun maskRegistrationId(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return ""
        if (text.length <= 4) return "****"
        return "****" + text.takeLast(4)
    }

    /** 设置页状态文案。 */
    fun label(status: OppoPushStatus): String = when (status.state) {
        OppoPushState.NOT_CONFIGURED -> "未配置"
        OppoPushState.UNSUPPORTED -> "当前设备不支持"
        OppoPushState.REGISTERING -> "注册中…"
        OppoPushState.REGISTERED -> {
            val masked = maskRegistrationId(status.registrationId)
            if (masked.isBlank()) "已注册" else "已注册 · $masked"
        }

        OppoPushState.FAILED -> buildString {
            append("注册失败")
            if (status.errorCode != 0) append("（").append(status.errorCode).append("）")
        }
    }

    /** 失败原因的简短中文提示（诊断/文档用；UI 只显示错误码，避免长说明）。 */
    fun errorHint(code: Int): String = when (code) {
        SUCCESS_CODE -> "成功"
        11 -> "应用无权限"
        12 -> "HTTP 动作不允许"
        13 -> "调用频率受限"
        14 -> "AppKey 无效"
        15 -> "缺少 AppKey"
        16 -> "签名无效（AppSecret 不匹配）"
        17 -> "缺少签名"
        18 -> "缺少时间戳"
        19 -> "时间戳无效"
        20 -> "方法无效"
        21 -> "缺少方法"
        22 -> "缺少版本信息"
        23 -> "版本无效"
        24 -> "版本不支持"
        25 -> "编码无效"
        26 -> "IP 黑名单"
        40 -> "缺少必填参数"
        41 -> "参数无效"
        -1 -> "MCS 服务不可用"
        -2 -> "未知错误"
        40001 -> "请求过于频繁"
        else -> "未知错误"
    }

    /**
     * 注册回调 → 状态：
     * - code == 0 且 RegistrationID 非空 → REGISTERED；
     * - code == 0 但回调未带 RegistrationID → 用 SDK `getRegisterID()` 兜底，仍空则 FAILED（-2）；
     * - 其它 code → FAILED（保留错误码）。
     */
    fun fromRegisterResult(
        code: Int,
        registrationId: String?,
        fallbackRegistrationId: String? = null,
    ): OppoPushStatus {
        if (code == SUCCESS_CODE) {
            val regId = registrationId?.trim().orEmpty()
                .ifBlank { fallbackRegistrationId?.trim().orEmpty() }
            if (regId.isNotEmpty()) {
                return OppoPushStatus(OppoPushState.REGISTERED, registrationId = regId)
            }
            return OppoPushStatus(
                state = OppoPushState.FAILED,
                registrationId = "",
                errorCode = -2,
                errorMessage = "注册回调未返回 RegistrationID",
            )
        }
        return OppoPushStatus(
            state = OppoPushState.FAILED,
            registrationId = registrationId?.trim().orEmpty(),
            errorCode = code,
            errorMessage = errorHint(code),
        )
    }

    /** SDK `onError` 回调 → FAILED 状态。 */
    fun fromError(code: Int, message: String?): OppoPushStatus = OppoPushStatus(
        state = OppoPushState.FAILED,
        errorCode = code,
        errorMessage = message?.trim().orEmpty().ifBlank { errorHint(code) },
    )
}
