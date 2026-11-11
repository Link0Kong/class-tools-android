package com.furina.classtools.data.auth

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiError
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asBooleanOrNull
import com.furina.classtools.data.network.asObjectOrNull
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import com.furina.classtools.data.session.Session
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 真实身份仓库：`POST rpc/class_verify`（docs/api/ENDPOINTS.md E1）。
 *
 * - 请求：`{ "p_sid": "10 位学号", "p_name": "真实姓名" }`，**不带** X-Class-Token（唯一免身份接口）。
 * - 响应兼容「对象」与「单元素数组」两种（网页/小程序实测都出现过）。
 * - `ok:false` 直接用云端 `reason`；HTTP 4xx → invalid_credentials；网络失败 → network。
 * - 成功后会话：`issuedAt=now`、`expiresAt=now+30d`（服务端 token 30 天，见契约 §2.3）。
 *
 * 本类不落盘（落盘由 SessionStore 负责），也不打印任何 token/学号。
 */
class ApiAuthRepository(
    private val api: ApiClient,
    private val now: () -> Long = System::currentTimeMillis,
    private val json: Json = NetJson,
    private val sessionTtlMs: Long = SESSION_TTL_MS,
) : AuthRepository {

    override suspend fun verify(studentId: String, name: String): VerifyResult {
        val sid = studentId.trim()
        val nm = name.trim()
        if (!STUDENT_ID_REGEX.matches(sid)) {
            return VerifyResult.Failure(VerifyErrorCode.INVALID_INPUT, "请输入 10 位学号")
        }
        if (nm.isEmpty()) {
            return VerifyResult.Failure(VerifyErrorCode.INVALID_INPUT, "请输入姓名")
        }

        val requestBody = buildJsonObject {
            put("p_sid", JsonPrimitive(sid))
            put("p_name", JsonPrimitive(nm))
        }.toString()

        return when (val result = api.post(path = VERIFY_PATH, body = requestBody, authenticated = false)) {
            is ApiResult.Failure -> mapError(result.error)
            is ApiResult.Success -> parseVerifyResponse(
                raw = result.value,
                fallbackSid = sid,
                fallbackName = nm,
                nowMs = now(),
            )
        }
    }

    // ------------------------------------------------------------ 解析（internal 便于单测）

    internal fun parseVerifyResponse(
        raw: String,
        fallbackSid: String,
        fallbackName: String,
        nowMs: Long,
    ): VerifyResult {
        val element = parseJsonOrNull(json, raw)
            ?: return VerifyResult.Failure(VerifyErrorCode.PARSE_ERROR, "身份服务返回了无法解析的内容")
        val body: JsonObject = when (element) {
            is JsonObject -> element
            is JsonArray -> element.firstOrNull()?.asObjectOrNull()
            else -> null
        } ?: return VerifyResult.Failure(VerifyErrorCode.PARSE_ERROR, "身份服务返回结构异常")

        val okFlag = body["ok"].asBooleanOrNull()
        val token = body["token"].asStringOrNull().orEmpty()
        val reason = body["reason"].asStringOrNull().orEmpty()

        if (okFlag == false) {
            return VerifyResult.Failure(
                code = VerifyErrorCode.INVALID_CREDENTIALS,
                message = reason.ifBlank { "姓名或学号不在班级名单中" },
            )
        }
        // 兼容 ok 字段缺失的响应：只要拿到 token 即视为成功。
        if (token.isBlank()) {
            return VerifyResult.Failure(
                code = VerifyErrorCode.TOKEN_MISSING,
                message = reason.ifBlank { "云端未返回身份凭证，请稍后重试" },
            )
        }

        val session = Session(
            sid = body["sid"].asStringOrNull()?.trim().orEmpty().ifBlank { fallbackSid },
            name = body["name"].asStringOrNull()?.trim().orEmpty().ifBlank { fallbackName },
            token = token,
            role = body["role"].asStringOrNull().orEmpty(),
            badge = body["badge"].asStringOrNull().orEmpty(),
            isAdmin = body["is_admin"].asBooleanOrNull() ?: false,
            issuedAt = nowMs,
            expiresAt = nowMs + sessionTtlMs,
        )
        return VerifyResult.Success(session)
    }

    internal fun mapError(error: ApiError): VerifyResult.Failure = when (error) {
        is ApiError.TokenInvalid -> VerifyResult.Failure(
            VerifyErrorCode.INVALID_CREDENTIALS,
            "姓名或学号不在班级名单中",
        )
        is ApiError.Http -> {
            if (error.status in 400..499) {
                VerifyResult.Failure(
                    VerifyErrorCode.INVALID_CREDENTIALS,
                    error.serverMessage.ifBlank { "姓名或学号不在班级名单中" },
                )
            } else {
                VerifyResult.Failure(
                    VerifyErrorCode.SERVER_ERROR,
                    error.displayMessage,
                )
            }
        }
        is ApiError.Network -> VerifyResult.Failure(VerifyErrorCode.NETWORK, error.displayMessage)
        is ApiError.Parse -> VerifyResult.Failure(VerifyErrorCode.PARSE_ERROR, error.displayMessage)
        is ApiError.Config -> VerifyResult.Failure(VerifyErrorCode.NOT_CONFIGURED, error.displayMessage)
    }

    companion object {
        const val VERIFY_PATH: String = "rpc/class_verify"
        const val SESSION_TTL_MS: Long = 30L * 24 * 60 * 60 * 1000
        val STUDENT_ID_REGEX: Regex = Regex("^\\d{10}$")
    }
}

/** 身份相关错误码（UI 可按 code 做特殊提示；message 已是可直接展示的中文）。 */
object VerifyErrorCode {
    const val INVALID_INPUT = "invalid_input"
    const val INVALID_CREDENTIALS = "invalid_credentials"
    const val TOKEN_MISSING = "token_missing"
    const val NETWORK = "network"
    const val SERVER_ERROR = "server_error"
    const val PARSE_ERROR = "parse_error"
    const val NOT_CONFIGURED = "not_configured"
}
