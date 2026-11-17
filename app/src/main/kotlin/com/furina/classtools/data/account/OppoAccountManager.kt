package com.furina.classtools.data.account

import android.content.Context
import com.furina.classtools.data.session.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * OPPO 账号服务编排器（隐藏 SDK/HTTP 细节，UI 只调用本类）。
 *
 * 两条业务线（服务端在 ECS `/account` 完成 OPPO 验证，客户端只传 authCode，不碰 token/openid）：
 * 1. **快捷登录**：[login] 拉起 OPPO 授权 → authCode → `POST /oppo/login` → 服务端返回班级 token →
 *    构造既有 [Session]（与学号+姓名核验后的会话一致，由调用方落盘/更新 active session）；
 * 2. **绑定管理**：[bind]（绑定当前 OPPO 账号到当前学号，一个学号最多 3 个）/ [refreshBindings] /
 *    [unbind]；[bindingsState] 供「我的」行与绑定弹层共享。
 *
 * 未配置凭证 / 非 OPPO 系设备：入口隐藏（[quickLoginAvailable]/[bindingsAvailable]=false），
 * 所有方法返回可展示的失败结果，不抛异常、不崩。
 */
class OppoAccountManager(
    val config: OppoAccountConfig,
    private val api: OppoAccountApi,
    private val oauth: OppoOAuthSdk,
    private val deviceSupported: Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    private val stateFactory: () -> String = OppoOAuthState::random,
    private val sessionTtlMs: Long = DEFAULT_SESSION_TTL_MS,
) {

    val isConfigured: Boolean get() = config.isConfigured

    /** 身份页是否展示「OPPO 账号快捷登录」（已配置 + OPPO 系设备）。 */
    val quickLoginAvailable: Boolean get() = config.isConfigured && deviceSupported

    /** 「我的」是否展示「OPPO 账号」入口（已配置 + OPPO 系设备）。 */
    val bindingsAvailable: Boolean get() = config.isConfigured && deviceSupported

    private val _bindingsState = MutableStateFlow<OppoBindingsUiState>(OppoBindingsUiState.Idle)
    val bindingsState: StateFlow<OppoBindingsUiState> = _bindingsState.asStateFlow()

    // ------------------------------------------------------------ 快捷登录

    /**
     * OPPO 账号快捷登录：OPPO 授权 → authCode → 服务端换取班级 token → 构造 [Session]。
     * 未绑定（服务端 `unbound`）→ [OppoSessionLoginResult.Unbound]（UI 引导先学号+姓名登录再绑定）。
     */
    suspend fun login(context: Context): OppoSessionLoginResult {
        if (!quickLoginAvailable) {
            return OppoSessionLoginResult.Failure(
                OppoAccountErrorCode.NOT_AVAILABLE,
                "当前设备或配置不支持 OPPO 快捷登录",
            )
        }
        val auth = oauth.authorize(context, OppoOAuthRequest.of(config, stateFactory()))
        val authCode = when (auth) {
            is OppoAuthResult.Failure -> return OppoSessionLoginResult.Failure(
                code = auth.code.toString(),
                message = auth.message,
            )

            is OppoAuthResult.Success -> auth.authCode
        }
        return when (val result = api.login(authCode)) {
            is OppoLoginResult.Success -> {
                if (result.token.isBlank()) {
                    OppoSessionLoginResult.Failure(OppoAccountErrorCode.PARSE, "账号服务未返回身份凭证")
                } else {
                    val issuedAt = now()
                    OppoSessionLoginResult.Success(
                        Session(
                            sid = result.sid.trim(),
                            name = result.name.trim(),
                            token = result.token.trim(),
                            issuedAt = issuedAt,
                            expiresAt = issuedAt + sessionTtlMs,
                        ),
                    )
                }
            }

            is OppoLoginResult.Unbound -> OppoSessionLoginResult.Unbound(
                result.message.ifBlank { OppoAccountText.UNBOUND_FALLBACK },
            )

            is OppoLoginResult.Failure -> OppoSessionLoginResult.Failure(result.code, result.message)
        }
    }

    // ------------------------------------------------------------ 绑定管理

    /**
     * 绑定当前 OPPO 账号到当前学号：OPPO 授权 → authCode → `POST /oppo/bind`（带当前班级 token）。
     * 成功后尽力刷新 [bindingsState]（失败不影响本次绑定结果）。
     */
    suspend fun bind(context: Context, classToken: String): OppoBindResult {
        if (!bindingsAvailable) {
            return OppoBindResult.Failure(
                OppoAccountErrorCode.NOT_AVAILABLE,
                "当前设备或配置不支持 OPPO 账号绑定",
            )
        }
        if (classToken.isBlank()) {
            return OppoBindResult.Failure(OppoAccountErrorCode.UNAUTHORIZED, "登录已失效，请重新验证")
        }
        val auth = oauth.authorize(context, OppoOAuthRequest.of(config, stateFactory()))
        val authCode = when (auth) {
            is OppoAuthResult.Failure -> return OppoBindResult.Failure(
                code = "oauth_${auth.code}",
                message = auth.message,
            )

            is OppoAuthResult.Success -> auth.authCode
        }
        val result = api.bind(authCode, classToken)
        if (result is OppoBindResult.Success) {
            refreshBindingsBestEffort(classToken)
        }
        return result
    }

    /** 拉取绑定列表并更新 [bindingsState]（「我的」行 / 绑定弹层进入时调用）。 */
    suspend fun refreshBindings(classToken: String): OppoBindingsResult {
        if (!bindingsAvailable) {
            _bindingsState.value = OppoBindingsUiState.Error("当前设备或配置不支持 OPPO 账号")
            return OppoBindingsResult.Failure(
                OppoAccountErrorCode.NOT_AVAILABLE,
                "当前设备或配置不支持 OPPO 账号",
            )
        }
        if (classToken.isBlank()) {
            _bindingsState.value = OppoBindingsUiState.Error("登录已失效，请重新验证")
            return OppoBindingsResult.Failure(OppoAccountErrorCode.UNAUTHORIZED, "登录已失效，请重新验证")
        }
        _bindingsState.value = OppoBindingsUiState.Loading
        val result = api.bindings(classToken)
        _bindingsState.value = when (result) {
            is OppoBindingsResult.Success -> OppoBindingsUiState.Ready(result.page)
            is OppoBindingsResult.Failure -> OppoBindingsUiState.Error(result.message)
        }
        return result
    }

    /** 解绑一条记录；成功后刷新 [bindingsState]。 */
    suspend fun unbind(classToken: String, bindingId: Long): OppoUnbindResult {
        if (!bindingsAvailable) {
            return OppoUnbindResult.Failure(
                OppoAccountErrorCode.NOT_AVAILABLE,
                "当前设备或配置不支持 OPPO 账号",
            )
        }
        if (classToken.isBlank()) {
            return OppoUnbindResult.Failure(OppoAccountErrorCode.UNAUTHORIZED, "登录已失效，请重新验证")
        }
        val result = api.unbind(bindingId, classToken)
        if (result is OppoUnbindResult.Success) {
            refreshBindingsBestEffort(classToken)
        }
        return result
    }

    /** 退出登录 / 切换账号：清空内存中的绑定列表（不影响云端）。 */
    fun clearBindings() {
        _bindingsState.value = OppoBindingsUiState.Idle
    }

    private suspend fun refreshBindingsBestEffort(classToken: String) {
        runCatching { refreshBindings(classToken) }
    }

    companion object {
        /** 与学号+姓名核验一致：服务端 token 30 天。 */
        const val DEFAULT_SESSION_TTL_MS: Long = 30L * 24 * 60 * 60 * 1000
    }
}
