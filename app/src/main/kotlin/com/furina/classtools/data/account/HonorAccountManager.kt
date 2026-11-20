package com.furina.classtools.data.account

import android.app.Activity
import android.content.Intent
import com.furina.classtools.data.session.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 荣耀账号编排器（隐藏 SDK/HTTP 细节，UI 只调用本类）。
 *
 * 荣耀 SDK 的授权需要两步（与 OPPO 的 suspend 授权不同）：
 * 1. UI：`rememberLauncherForActivityResult` 拉起 [signInIntent]；
 * 2. 回调：把 `requestCode/data` 交给 [login] / [bind]，本类解析 authCode → 调 ECS `/honor` 系列接口。
 *
 * 绑定列表 / 登录结果复用与 OPPO 相同的 wire 模型（服务端契约一致，仅 provider 路径不同）。
 * 未配置 / 非荣耀设备：入口隐藏，所有方法返回可展示失败，不抛异常。
 */
class HonorAccountManager(
    val config: HonorAccountConfig,
    private val api: OppoAccountApi,
    private val auth: HonorAuthSdk,
    private val deviceSupported: Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    private val sessionTtlMs: Long = DEFAULT_SESSION_TTL_MS,
) {

    val isConfigured: Boolean get() = config.isConfigured

    val quickLoginAvailable: Boolean get() = config.isConfigured && deviceSupported

    val bindingsAvailable: Boolean get() = config.isConfigured && deviceSupported

    private val _bindingsState = MutableStateFlow<OppoBindingsUiState>(OppoBindingsUiState.Idle)
    val bindingsState: StateFlow<OppoBindingsUiState> = _bindingsState.asStateFlow()

    /** 构建荣耀授权 Intent（失败 null → UI 提示当前设备不支持/账号服务不可用）。 */
    fun signInIntent(activity: Activity): Intent? =
        if (quickLoginAvailable) auth.signInIntent(activity, HonorAuthRequest.of(config)) else null

    /** 处理荣耀授权结果并服务端换取班级 token（快捷登录）。 */
    suspend fun login(requestCode: Int, data: Intent?): OppoSessionLoginResult {
        if (!quickLoginAvailable) {
            return OppoSessionLoginResult.Failure(
                OppoAccountErrorCode.NOT_AVAILABLE,
                "当前设备或配置不支持荣耀快捷登录",
            )
        }
        val authCode = when (val result = auth.parseResult(requestCode, data)) {
            is HonorAuthResult.Failure -> return OppoSessionLoginResult.Failure(
                code = result.code.toString(),
                message = result.message,
            )

            is HonorAuthResult.Success -> result.authCode
        }
        return loginWithCode(authCode)
    }

    /** 已拿到 authCode 时的登录（供测试/复用）。 */
    suspend fun loginWithCode(authCode: String): OppoSessionLoginResult {
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
                result.message.ifBlank { HonorAccountText.unboundFallback() },
            )

            is OppoLoginResult.Failure -> OppoSessionLoginResult.Failure(result.code, result.message)
        }
    }

    /** 处理荣耀授权结果并绑定到当前学号（需登录态班级 token）。 */
    suspend fun bind(requestCode: Int, data: Intent?, classToken: String): OppoBindResult {
        if (!bindingsAvailable) {
            return OppoBindResult.Failure(
                OppoAccountErrorCode.NOT_AVAILABLE,
                "当前设备或配置不支持荣耀账号绑定",
            )
        }
        if (classToken.isBlank()) {
            return OppoBindResult.Failure(OppoAccountErrorCode.UNAUTHORIZED, "登录已失效，请重新验证")
        }
        val authCode = when (val result = auth.parseResult(requestCode, data)) {
            is HonorAuthResult.Failure -> return OppoBindResult.Failure(
                code = "oauth_${result.code}",
                message = result.message,
            )

            is HonorAuthResult.Success -> result.authCode
        }
        val result = api.bind(authCode, classToken)
        if (result is OppoBindResult.Success) refreshBindingsBestEffort(classToken)
        return result
    }

    suspend fun refreshBindings(classToken: String): OppoBindingsResult {
        if (!bindingsAvailable) {
            _bindingsState.value = OppoBindingsUiState.Error("当前设备或配置不支持荣耀账号")
            return OppoBindingsResult.Failure(
                OppoAccountErrorCode.NOT_AVAILABLE,
                "当前设备或配置不支持荣耀账号",
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

    suspend fun unbind(classToken: String, bindingId: Long): OppoUnbindResult {
        if (!bindingsAvailable) {
            return OppoUnbindResult.Failure(
                OppoAccountErrorCode.NOT_AVAILABLE,
                "当前设备或配置不支持荣耀账号",
            )
        }
        if (classToken.isBlank()) {
            return OppoUnbindResult.Failure(OppoAccountErrorCode.UNAUTHORIZED, "登录已失效，请重新验证")
        }
        val result = api.unbind(bindingId, classToken)
        if (result is OppoUnbindResult.Success) refreshBindingsBestEffort(classToken)
        return result
    }

    fun clearBindings() {
        _bindingsState.value = OppoBindingsUiState.Idle
    }

    private suspend fun refreshBindingsBestEffort(classToken: String) {
        runCatching { refreshBindings(classToken) }
    }

    companion object {
        const val DEFAULT_SESSION_TTL_MS: Long = 30L * 24 * 60 * 60 * 1000
    }
}

