package com.furina.classtools.data.cloudspace

/**
 * 云空间错误（中文文案可直接展示给用户）。
 *
 * 映射口径（drop 服务契约 §1.3）：
 * - 网络不可用 → [Network]（不重试写请求，页面提示重试）；
 * - 401 → [TokenInvalid]（身份失效，建议回身份页重新验证）；
 * - 4xx/5xx → [Http]：优先用服务端 `{ok:false,reason:"中文"}` 的 reason，缺失时按状态码给默认中文；
 * - 2xx 但响应不是合法 JSON / 不符合契约 → [Parse]；
 * - 本地前置条件（空文件、未连接等）→ [Local]。
 *
 * 红线：任何错误文案都不包含班级 token / 学号（服务端 reason 本身也不含）。
 */
sealed interface CloudSpaceError {
    val displayMessage: String

    data object Network : CloudSpaceError {
        override val displayMessage: String = "云空间服务不可达，请检查网络后重试"
    }

    data class TokenInvalid(
        val serverReason: String = "",
    ) : CloudSpaceError {
        override val displayMessage: String
            get() = serverReason.trim().ifBlank { "身份已失效，请重新验证" }
    }

    data class Http(
        val status: Int,
        val serverReason: String = "",
    ) : CloudSpaceError {
        override val displayMessage: String
            get() = serverReason.trim().ifBlank { CloudSpaceErrors.defaultReason(status) }
    }

    data object Parse : CloudSpaceError {
        override val displayMessage: String = "云空间服务响应异常，请稍后重试"
    }

    data class Local(override val displayMessage: String) : CloudSpaceError
}

/** 数据层统一返回：成功 [Success] / 失败 [Failure]；仓库公开方法不抛异常（协程取消除外）。 */
sealed interface CloudSpaceResult<out T> {
    data class Success<T>(val value: T) : CloudSpaceResult<T>
    data class Failure(val error: CloudSpaceError) : CloudSpaceResult<Nothing>
}

/** HTTP 状态码 / 服务端 reason → 中文错误的唯一映射入口（纯函数，可单测）。 */
object CloudSpaceErrors {

    fun network(): CloudSpaceError = CloudSpaceError.Network

    fun http(status: Int, serverReason: String? = null): CloudSpaceError {
        val reason = sanitize(serverReason)
        return if (status == 401) {
            CloudSpaceError.TokenInvalid(reason)
        } else {
            CloudSpaceError.Http(status = status, serverReason = reason)
        }
    }

    /** HTTP 200 但业务返回 `ok:false`（服务端拒绝；reason 优先）。 */
    fun rejected(serverReason: String? = null): CloudSpaceError {
        val reason = sanitize(serverReason)
        return CloudSpaceError.Http(
            status = 200,
            serverReason = reason.ifBlank { "云空间操作失败，请稍后重试" },
        )
    }

    fun parse(): CloudSpaceError = CloudSpaceError.Parse

    fun local(message: String): CloudSpaceError = CloudSpaceError.Local(message)

    fun defaultReason(status: Int): String = when (status) {
        400 -> "请求参数有误，请稍后重试"
        401 -> "身份已失效，请重新验证"
        403 -> "没有操作权限，或文件已过期"
        404 -> "云空间会话或文件不存在（可能已清空或过期）"
        405 -> "云空间服务暂不支持该操作"
        411 -> "上传数据不完整，请重试"
        413 -> "文件数量或大小超出服务限制"
        500 -> "云空间服务内部错误，请稍后重试"
        503 -> "身份服务暂不可用，请稍后重试"
        507 -> "服务器空间不足，请稍后再试"
        else -> "服务错误（HTTP $status）"
    }

    /** 服务端 reason 一律是短中文句；防御性截断，避免异常文本撑破 UI。 */
    private fun sanitize(reason: String?): String {
        val text = reason?.trim().orEmpty()
        if (text.isEmpty()) return ""
        return if (text.length <= 120) text else text.take(119) + "…"
    }
}
