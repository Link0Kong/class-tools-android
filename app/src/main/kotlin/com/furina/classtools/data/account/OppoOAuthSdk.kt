package com.furina.classtools.data.account

import android.content.Context
import com.platform.usercenter.account.ams.api.AcOauthCallback
import com.platform.usercenter.account.ams.bean.AcOauthApiResponse
import com.platform.usercenter.account.ams.bean.AcOauthRequest
import com.platform.usercenter.account.ams.bean.AcOauthResult
import com.platform.usercenter.oauth.agents.AcOAuthSdkManager
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * OPPO 账号授权请求参数（官方 `AcOauthRequest.Builder` 四项均必填，缺一 build() 抛 IllegalArgumentException）。
 *
 * @param appId 开放平台账号服务 AppID（app/oppo.properties → oppo.accountAppId）
 * @param appKey 开放平台账号服务 AppKey（→ oppo.accountAppKey）
 * @param scope 授权范围（默认 openid；多 scope 空格分隔，需先在开放平台申请权限）
 * @param state 防重放随机串（每次授权新生成；本地随请求传入，不落盘）
 */
data class OppoOAuthRequest(
    val appId: String,
    val appKey: String,
    val scope: String,
    val state: String,
) {
    val isValid: Boolean
        get() = appId.isNotBlank() && appKey.isNotBlank() && scope.isNotBlank() &&
            OppoOAuthState.isValid(state)

    companion object {
        fun of(config: OppoAccountConfig, state: String): OppoOAuthRequest = OppoOAuthRequest(
            appId = config.appId,
            appKey = config.appKey,
            scope = config.effectiveScope,
            state = state,
        )
    }
}

/** OAuth 授权结果（已归一化 SDK 回调）。 */
sealed interface OppoAuthResult {
    data class Success(val authCode: String) : OppoAuthResult
    data class Failure(val code: Int, val message: String) : OppoAuthResult
}

/** SDK 原始回调 → 归一化结果（纯函数，单测覆盖 authCode 解析与错误映射）。 */
object OppoOAuthResultParser {

    fun parse(
        success: Boolean,
        code: Int,
        message: String?,
        authCode: String?,
    ): OppoAuthResult {
        if (success) {
            val normalized = OppoAuthCode.normalize(authCode)
                ?: return OppoAuthResult.Failure(CODE_BAD_AUTH_CODE, "OPPO 授权未返回有效凭证，请重试")
            return OppoAuthResult.Success(normalized)
        }
        return OppoAuthResult.Failure(
            code = code,
            message = OppoAccountText.oAuthErrorText(code),
        )
    }

    /** 成功回调但没有可用 authCode。 */
    const val CODE_BAD_AUTH_CODE: Int = -2012
}

/**
 * OPPO 账号 OAuth SDK 的最小接口隔离。
 *
 * 真实实现 [HeytapOppoOAuthSdk] 调官方 `AcOAuthSdkManager`；单测使用假实现，
 * **授权流程/状态/接口构造的单元测试不依赖真机、ColorOS 或真实 AppID/AppKey**。
 */
interface OppoOAuthSdk {
    /**
     * 拉起 OPPO 账号授权并等待 authCode。
     *
     * 注意：必须用 **Activity Context**（H5 兜底流程需要启动 `OAuthWebActivity`）；
     * 回调会 resume 协程（SDK 可能在线程池/主线程回调，调用方无需关心）。
     */
    suspend fun authorize(context: Context, request: OppoOAuthRequest): OppoAuthResult
}

/**
 * 官方 SDK 适配实现（Android 运行时）。
 *
 * 流程：`AcOauthRequest.Builder().setAppId().setAppKey().setScope().setState().build()`
 * → `AcOAuthSdkManager.getInstance().auth(activityContext, request, callback)` →
 * 回调 `AcOauthApiResponse<AcOauthResult>.data.authCode`。
 */
class HeytapOppoOAuthSdk : OppoOAuthSdk {

    override suspend fun authorize(context: Context, request: OppoOAuthRequest): OppoAuthResult {
        if (!request.isValid) {
            return OppoAuthResult.Failure(-2002, "OPPO 授权参数不完整")
        }
        return suspendCancellableCoroutine { continuation ->
            val callback = object : AcOauthCallback<AcOauthApiResponse<AcOauthResult>> {
                override fun call(response: AcOauthApiResponse<AcOauthResult>) {
                    if (!continuation.isActive) return
                    val result = OppoOAuthResultParser.parse(
                        success = response.isSuccess,
                        code = response.code,
                        message = response.msg,
                        authCode = runCatching { response.data?.authCode }.getOrNull(),
                    )
                    continuation.resume(result)
                }
            }
            val launched = runCatching {
                val acRequest = AcOauthRequest.Builder()
                    .setAppId(request.appId)
                    .setAppKey(request.appKey)
                    .setScope(request.scope)
                    .setState(request.state)
                    .build()
                AcOAuthSdkManager.getInstance().auth(context, acRequest, callback)
            }
            if (launched.isFailure) {
                val error = launched.exceptionOrNull()
                if (continuation.isActive) {
                    continuation.resume(
                        OppoAuthResult.Failure(
                            code = -2,
                            message = "无法拉起 OPPO 账号授权（${error?.javaClass?.simpleName ?: "unknown"}）",
                        ),
                    )
                }
            }
        }
    }
}
