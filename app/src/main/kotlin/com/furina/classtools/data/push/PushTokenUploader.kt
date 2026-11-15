package com.furina.classtools.data.push

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 推送 token 上传（云端表 `public.push_tokens`；服务端契约由 Lead 定义）。
 *
 * 请求（PostgREST）：
 * ```
 * POST {REST_BASE_URL}push_tokens?on_conflict=provider,token
 * headers: Authorization: Bearer {PUBLISHABLE_KEY}
 *          X-Class-Token: {班级 token}      ← 由 ApiClient.authenticated=true 自动注入
 *          Content-Type: application/json
 *          Prefer: resolution=merge-duplicates,return=minimal
 * body: {"provider":"oppo","token":"<RegistrationID>","device":"<MANUFACTURER> <MODEL>","app_version":"<versionName>"}
 * ```
 * `on_conflict=provider,token` + merge-duplicates → 同一 token 重复上传是幂等的（更新 device/app_version）。
 */
data class PushTokenPayload(
    val token: String,
    val device: String,
    val appVersion: String,
    val provider: String = PROVIDER_OPPO,
) {
    val isValid: Boolean get() = token.isNotBlank()

    /** 请求体（字段顺序固定，便于单测断言）。 */
    fun toJson(): String = buildJsonObject {
        put("provider", JsonPrimitive(provider))
        put("token", JsonPrimitive(token.trim()))
        put("device", JsonPrimitive(device.trim()))
        put("app_version", JsonPrimitive(appVersion.trim()))
    }.toString()

    companion object {
        const val PROVIDER_OPPO: String = "oppo"

        /** PostgREST 资源路径 + 冲突键（服务端 upsert 语义）。 */
        const val PATH: String = "push_tokens?on_conflict=provider,token"

        /** PostgREST upsert：冲突时合并（重复上传幂等），只写不回传行。 */
        const val PREFER: String = "resolution=merge-duplicates,return=minimal"

        fun of(registrationId: String?, device: String, appVersion: String): PushTokenPayload? {
            val token = registrationId?.trim().orEmpty()
            if (token.isEmpty()) return null
            return PushTokenPayload(
                token = token,
                device = device.trim(),
                appVersion = appVersion.trim(),
            )
        }
    }
}

/** 上传结果（失败只记录日志，不阻塞主流程）。 */
sealed interface PushUploadResult {
    data object Uploaded : PushUploadResult

    /** 未登录 / 无 RegistrationID / 重复上传被幂等门跳过。 */
    data object Skipped : PushUploadResult

    data class Failed(val message: String) : PushUploadResult
}

/** token 上传器接口（单测注入假实现；真实实现走 [ApiClient]）。 */
interface PushTokenUploader {
    suspend fun upload(payload: PushTokenPayload): PushUploadResult
}

/** ApiClient 实现：写请求不重试（ApiClient 语义），失败错误就地转文案。 */
class ApiPushTokenUploader(private val api: ApiClient) : PushTokenUploader {

    override suspend fun upload(payload: PushTokenPayload): PushUploadResult {
        if (!payload.isValid) return PushUploadResult.Skipped
        return when (
            val result = api.post(
                path = PushTokenPayload.PATH,
                body = payload.toJson(),
                prefer = PushTokenPayload.PREFER,
                authenticated = true,
            )
        ) {
            is ApiResult.Success -> PushUploadResult.Uploaded
            is ApiResult.Failure -> PushUploadResult.Failed(
                result.error.displayMessage.ifBlank { "上传失败" },
            )
        }
    }
}

/**
 * 重复上传幂等门（内存）：同一账号（sid）+ 同一 RegistrationID + 同一版本只上传一次。
 *
 * - 换账号 / 换 RegistrationID / 换版本 → 重新上传（服务端按 provider+token upsert，天然幂等）；
 * - 上传失败时由 [OppoPushManager] 调用 [reset]，让下次登录/重试可以补传；
 * - 未登录（sid 空）或 regId 空 → 不上传（等登录后由 onSessionChanged 触发）。
 */
class PushTokenUploadGate {

    private var lastKey: String? = null

    @Synchronized
    fun shouldUpload(sid: String?, registrationId: String?, appVersion: String): Boolean {
        val session = sid?.trim().orEmpty()
        val token = registrationId?.trim().orEmpty()
        if (session.isEmpty() || token.isEmpty()) return false
        val key = "$session|$token|${appVersion.trim()}"
        if (key == lastKey) return false
        lastKey = key
        return true
    }

    @Synchronized
    fun reset() {
        lastKey = null
    }
}
