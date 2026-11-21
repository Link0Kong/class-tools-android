package com.furina.classtools.data.push

/**
 * vivo 推送客户端配置（构建期来自 `app/vivo.properties` → BuildConfig）。
 *
 * 空值 = 未配置：不初始化 SDK、不注册、不上传 token；其余功能不受影响。
 * AppSecret **不在客户端**（只在服务端 部署机密钥文件（不入库））。
 */
data class VivoPushConfig(
    val appId: String = "",
    val apiKey: String = "",
) {
    val isConfigured: Boolean get() = appId.isNotBlank() && apiKey.isNotBlank()

    companion object {
        fun of(appId: String?, apiKey: String?): VivoPushConfig = VivoPushConfig(
            appId = appId?.trim().orEmpty(),
            apiKey = apiKey?.trim().orEmpty(),
        )
    }
}

/** vivo 推送客户端状态（设置页展示 / 注册流程）。 */
enum class VivoPushState {
    NOT_CONFIGURED,
    UNSUPPORTED,
    REGISTERING,
    REGISTERED,
    FAILED,
}

/** 推送状态快照（只含展示必需信息；regId 永远掩码显示）。 */
data class VivoPushStatus(
    val state: VivoPushState,
    val registrationId: String = "",
    val errorCode: Int = 0,
    val errorMessage: String = "",
) {
    val label: String get() = VivoPushText.label(this)

    companion object {
        val NotConfigured: VivoPushStatus = VivoPushStatus(VivoPushState.NOT_CONFIGURED)
        val Unsupported: VivoPushStatus = VivoPushStatus(VivoPushState.UNSUPPORTED)
        val Registering: VivoPushStatus = VivoPushStatus(VivoPushState.REGISTERING)
    }
}

/** vivo 推送状态文案 / regId 掩码 / 错误码映射（纯函数，单测直接覆盖）。 */
object VivoPushText {

    /** 注册回调成功码（0=成功；1=已订阅）。 */
    const val SUCCESS_CODE: Int = 0

    fun maskRegistrationId(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return ""
        if (text.length <= 4) return "****"
        return "****" + text.takeLast(4)
    }

    fun label(status: VivoPushStatus): String = when (status.state) {
        VivoPushState.NOT_CONFIGURED -> "未配置"
        VivoPushState.UNSUPPORTED -> "当前设备不支持"
        VivoPushState.REGISTERING -> "注册中…"
        VivoPushState.REGISTERED -> {
            val masked = maskRegistrationId(status.registrationId)
            if (masked.isBlank()) "已注册" else "已注册 · $masked"
        }

        VivoPushState.FAILED -> buildString {
            append("注册失败")
            if (status.errorCode != 0) append("（").append(status.errorCode).append("）")
        }
    }

    /**
     * vivo 官方客户端错误码（doc/364 / doc/368）：
     * 0 成功、1 已订阅、101/1004/1005 设备不支持、104 未同意隐私、1003 超时等。
     */
    fun errorHint(code: Int): String = when (code) {
        0 -> "成功"
        1 -> "已订阅"
        101 -> "当前设备不支持 vivo 推送"
        102 -> "初始化上下文为空"
        104 -> "未同意隐私声明"
        1001 -> "今日开关次数超限"
        1002 -> "重复请求"
        1003 -> "订阅超时（检查网络/Manifest 配置）"
        1004, 1005 -> "当前设备不支持 vivo 推送"
        10000 -> "未知异常"
        10001 -> "Manifest 未配置 app_id / api_key"
        10003 -> "包名与开放平台不一致"
        10004 -> "api_key 与开放平台不一致"
        10005 -> "app_id 与开放平台不一致"
        10083 -> "vivo 服务繁忙，请稍后重试"
        10084 -> "请求受限"
        else -> "未知错误"
    }

    fun fromRegId(regId: String?): VivoPushStatus {
        val id = regId?.trim().orEmpty()
        return if (id.isEmpty()) {
            VivoPushStatus(VivoPushState.FAILED, errorCode = -2, errorMessage = "未返回 regId")
        } else {
            VivoPushStatus(VivoPushState.REGISTERED, registrationId = id)
        }
    }

    fun fromError(code: Int, message: String? = null): VivoPushStatus = VivoPushStatus(
        state = VivoPushState.FAILED,
        errorCode = code,
        errorMessage = message?.trim().orEmpty().ifBlank { errorHint(code) },
    )
}
