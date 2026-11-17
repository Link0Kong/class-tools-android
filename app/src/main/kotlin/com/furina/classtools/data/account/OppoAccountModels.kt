package com.furina.classtools.data.account

/** 一条 OPPO 账号绑定（服务端 `public.oppo_bindings` 视图；不含任何 OPPO 隐私标识）。 */
data class OppoBinding(
    val id: Long,
    val nickname: String,
    /** 服务端原始 createdAt（ISO 字符串；展示用 [OppoAccountText.formatCreatedAt]）。 */
    val createdAt: String = "",
) {
    /** 绑定日期展示（`yyyy-MM-dd`；不可解析时原样截断，避免崩溃）。 */
    val createdDateText: String get() = OppoAccountText.formatCreatedAt(createdAt)
}

/** 绑定列表（`GET /oppo/bindings`）。max/count 以服务端为准。 */
data class OppoBindingsPage(
    val max: Int = OppoAccountText.DEFAULT_MAX_BINDINGS,
    val count: Int = 0,
    val items: List<OppoBinding> = emptyList(),
) {
    val isFull: Boolean get() = count >= max

    companion object {
        val EMPTY: OppoBindingsPage = OppoBindingsPage()
    }
}

/** `POST /oppo/login` 结果。 */
sealed interface OppoLoginResult {
    /** 成功：服务端返回班级 token 与身份（sid/name），客户端据此建立既有 Session。 */
    data class Success(
        val token: String,
        val sid: String,
        val name: String,
        val nickname: String = "",
    ) : OppoLoginResult

    /** 该 OPPO 账号未绑定学号（引导先学号+姓名登录再绑定）。 */
    data class Unbound(val message: String) : OppoLoginResult

    /** 其它失败（网络/服务端/解析）；[code] 为服务端 code 或本地错误码。 */
    data class Failure(val code: String, val message: String) : OppoLoginResult
}

/** `POST /oppo/bind` 结果。 */
sealed interface OppoBindResult {
    data class Success(val binding: OppoBinding, val count: Int) : OppoBindResult

    /** 一个学号最多绑定 3 个 OPPO 账号（服务端 code=`limit`）。 */
    data class Limit(val message: String) : OppoBindResult

    /** 该 OPPO 账号已绑定其他学号（服务端 code=`openid_bound_else`）。 */
    data class BoundElsewhere(val message: String) : OppoBindResult

    data class Failure(val code: String, val message: String) : OppoBindResult
}

/** `GET /oppo/bindings` 结果。 */
sealed interface OppoBindingsResult {
    data class Success(val page: OppoBindingsPage) : OppoBindingsResult
    data class Failure(val code: String, val message: String) : OppoBindingsResult
}

/** `POST /oppo/unbind` 结果。 */
sealed interface OppoUnbindResult {
    data class Success(val count: Int) : OppoUnbindResult
    data class Failure(val code: String, val message: String) : OppoUnbindResult
}

/** 账号绑定列表的 UI 状态（「我的」行 + 绑定弹层共用）。 */
sealed interface OppoBindingsUiState {
    data object Idle : OppoBindingsUiState
    data object Loading : OppoBindingsUiState
    data class Ready(val page: OppoBindingsPage) : OppoBindingsUiState
    data class Error(val message: String) : OppoBindingsUiState
}

/** 快捷登录的会话结果（manager 把服务端返回转换成既有 [com.furina.classtools.data.session.Session]）。 */
sealed interface OppoSessionLoginResult {
    data class Success(val session: com.furina.classtools.data.session.Session) : OppoSessionLoginResult
    data class Unbound(val message: String) : OppoSessionLoginResult
    data class Failure(val code: String, val message: String) : OppoSessionLoginResult
}

/** OPPO 账号服务错误码（本地/服务端通用，UI 只作简短提示）。 */
object OppoAccountErrorCode {
    const val NOT_CONFIGURED: String = "not_configured"
    const val NOT_AVAILABLE: String = "not_available"
    const val NETWORK: String = "network"
    const val TIMEOUT: String = "timeout"
    const val PARSE: String = "parse"
    const val UNBOUND: String = "unbound"
    const val LIMIT: String = "limit"
    const val BOUND_ELSEWHERE: String = "openid_bound_else"
    const val UNAUTHORIZED: String = "unauthorized"
    const val OAUTH_CANCELLED: String = "oauth_cancelled"

    /** ECS 用 authCode 换 openid 失败（常见 message=`invalid_client`：账号服务未开通/凭证不匹配）。 */
    const val EXCHANGE_FAILED: String = "oppo_exchange_failed"
}
