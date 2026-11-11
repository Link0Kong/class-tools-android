package com.furina.classtools.data.network

/**
 * 数据层统一返回类型。
 *
 * 约定：所有仓库的公开 suspend 方法都返回 [ApiResult]，**不抛异常**（网络/解析/HTTP 错误都在返回值里），
 * 方便 UI 层统一处理「离线仍可用、失败保留缓存」的契约语义（见 docs/BACKEND_CONTRACT.md §1.3/§11）。
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

/** 数据层错误分类（UI 可据此决定：回身份页 / 重试 / 静默降级到本机缓存）。 */
sealed interface ApiError {
    /** 网络不可用（DNS/超时/连接失败）。可重试。 */
    data class Network(
        val cause: Throwable? = null,
        val message: String = "网络不可用，请稍后重试",
    ) : ApiError

    /**
     * 云端返回 401/403：班级 token 缺失/过期/无效。
     * 契约 `docs/BACKEND_CONTRACT.md` §2.3：Android 端统一拦截 → 清 token 回身份页。
     */
    data class TokenInvalid(
        val status: Int,
        val message: String = "身份已失效，请重新验证",
    ) : ApiError

    /** 其它非 2xx（PostgREST 错误体 {code,message,requestId} 尽量解析）。 */
    data class Http(
        val status: Int,
        val postgrestCode: String = "",
        val serverMessage: String = "",
        val message: String = "",
    ) : ApiError

    /** 2xx 但响应 JSON 不符合契约（结构错/坏 JSON）。 */
    data class Parse(
        val message: String,
        val cause: Throwable? = null,
    ) : ApiError

    /** 本地配置/前置条件不满足（未配置 baseUrl、未登录、云同步已关闭等）。 */
    data class Config(
        val message: String,
    ) : ApiError

    val displayMessage: String
        get() = when (this) {
            is Network -> message
            is TokenInvalid -> message
            is Http -> serverMessage.ifBlank {
                if (status in 500..599) "服务端暂时不可用（HTTP $status）" else "请求失败（HTTP $status）"
            }
            is Parse -> "数据格式异常"
            is Config -> message
        }
}

/** PostgREST 错误体：`{"code":"DATABASE_42501","message":"...","requestId":"..."}`。 */
data class PostgrestErrorBody(
    val code: String = "",
    val message: String = "",
    val requestId: String = "",
)

// ---------------------------------------------------------------- 便捷扩展

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

fun <T> ApiResult<T>.valueOrNull(): T? = (this as? ApiResult.Success)?.value

fun <T> ApiResult<T>.errorOrNull(): ApiError? = (this as? ApiResult.Failure)?.error

fun ApiResult<*>.isTokenInvalid(): Boolean =
    (this as? ApiResult.Failure)?.error is ApiError.TokenInvalid
