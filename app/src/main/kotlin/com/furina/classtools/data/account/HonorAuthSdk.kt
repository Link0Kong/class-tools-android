package com.furina.classtools.data.account

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.hihonor.cloudservice.common.ApiException
import com.hihonor.cloudservice.support.account.HonorIdSignInManager
import com.hihonor.cloudservice.support.account.request.SignInOptionBuilder
import com.hihonor.cloudservice.support.api.entity.auth.Scope
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** 荣耀账号授权请求（Client ID + scope；SDK 不接受 state 参数，防重放由 SDK/服务端保证）。 */
data class HonorAuthRequest(
    val clientId: String,
    val scope: String,
) {
    val isValid: Boolean get() = clientId.isNotBlank() && scope.isNotBlank()

    /** 逐项构造 Scope（必须含 openid；多 scope 空格分隔）。 */
    fun scopes(): List<Scope> = scope.trim().split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .map { Scope(it) }

    companion object {
        fun of(config: HonorAccountConfig): HonorAuthRequest = HonorAuthRequest(
            clientId = config.clientId,
            scope = config.effectiveScope,
        )
    }
}

/** 授权结果（已归一化荣耀 SDK 回调）。 */
sealed interface HonorAuthResult {
    data class Success(val authCode: String) : HonorAuthResult
    data class Failure(val code: Int, val message: String) : HonorAuthResult
}

/**
 * 荣耀账号 OAuth SDK 最小接口隔离。
 *
 * 荣耀 SDK 的授权不是简单回调：需要 Activity `startActivityForResult(getSignInIntent())`，
 * 再由 `HonorIdSignInManager.parseAuthResultFromIntent(requestCode, data)` 解析。
 * 真实实现 [HonorIdAuthSdk] 调官方 SDK；单测使用假实现，不依赖荣耀真机。
 */
interface HonorAuthSdk {
    /** 构建拉起荣耀账号授权页的 Intent；失败返回 null（调用方给可诊断提示）。 */
    fun signInIntent(activity: Activity, request: HonorAuthRequest): Intent?

    /** 解析 Activity 结果并返回 authCode。 */
    suspend fun parseResult(requestCode: Int, data: Intent?): HonorAuthResult

    /** SDK 设备判定（荣耀设备 true）。 */
    fun isHonorMachine(): Boolean
}

/** 官方 SDK 适配实现（honorid-opensdk 8.0.1.351）。 */
class HonorIdAuthSdk : HonorAuthSdk {

    override fun signInIntent(activity: Activity, request: HonorAuthRequest): Intent? {
        if (!request.isValid) return null
        return runCatching {
            val options = SignInOptionBuilder()
                .setClientId(request.clientId)
                .setScopeList(request.scopes())
                .setReqiureAuthCode(true)
                .createParams()
            HonorIdSignInManager.getService(activity, options)?.signInIntent
        }.getOrNull()
    }

    override suspend fun parseResult(requestCode: Int, data: Intent?): HonorAuthResult {
        if (data == null) {
            return HonorAuthResult.Failure(3002, HonorAccountText.oAuthErrorText(3002))
        }
        return suspendCancellableCoroutine { continuation ->
            val task = runCatching {
                HonorIdSignInManager.parseAuthResultFromIntent(requestCode, data)
            }.getOrNull()
            if (task == null) {
                if (continuation.isActive) {
                    continuation.resume(
                        HonorAuthResult.Failure(-2, "荣耀账号授权结果解析失败，请重试"),
                    )
                }
                return@suspendCancellableCoroutine
            }
            task.addOnSuccessListener { info ->
                if (!continuation.isActive) return@addOnSuccessListener
                val code = OppoAuthCode.normalize(runCatching { info.authorizationCode }.getOrNull())
                if (code == null) {
                    continuation.resume(HonorAuthResult.Failure(-2012, "荣耀授权未返回有效凭证，请重试"))
                } else {
                    continuation.resume(HonorAuthResult.Success(code))
                }
            }.addOnFailureListener { error ->
                if (!continuation.isActive) return@addOnFailureListener
                val api = error as? ApiException
                val code = runCatching { api?.statusCode }.getOrNull() ?: -1
                val detail = runCatching { api?.statusMessage }.getOrNull()
                    ?.trim().orEmpty()
                continuation.resume(
                    HonorAuthResult.Failure(
                        code = code,
                        message = HonorAccountText.oAuthErrorText(code).let { text ->
                            if (detail.isBlank() || text.contains("code=")) text else "$text（$detail）"
                        },
                    ),
                )
            }
        }
    }

    override fun isHonorMachine(): Boolean = runCatching {
        HonorIdSignInManager.isHonorMachine()
    }.getOrDefault(false)
}

/** 仅本机调试/单测可用的设备判定（不加载 SDK 类）。 */
internal fun honorMachineFromSystem(context: Context): Boolean = runCatching {
    val systemProperties = Class.forName("android.os.SystemProperties")
    val get = systemProperties.getMethod("get", String::class.java)
    listOf("ro.build.version.magic", "ro.build.version.magicuisdk", "ro.product.brand")
        .any { key ->
            (runCatching { get.invoke(null, key) as? String }.getOrNull())
                ?.trim().orEmpty().isNotEmpty()
        }
}.getOrDefault(false)
