package com.furina.classtools.data.account

import com.furina.classtools.data.network.ApiRequest
import com.furina.classtools.data.network.HttpMethod
import com.furina.classtools.data.network.HttpTransport
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asBooleanOrNull
import com.furina.classtools.data.network.asIntOrNull
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * ECS 账号服务（OPPO 账号绑定学号 / OPPO 快捷登录）。
 *
 * 唯一契约（Lead 定义，ECS `http://121.196.47.2/account`，JSON）：
 * - `POST /oppo/login`  body `{"code":"<authCode>"}`；
 * - `POST /oppo/bind`   header `X-Class-Token`，body `{"code":"<authCode>"}`；
 * - `GET  /oppo/bindings` header `X-Class-Token`；
 * - `POST /oppo/unbind` header `X-Class-Token`，body `{"id":<bindingId>}`；
 * - `GET  /__health`（排错用）。
 *
 * 客户端**不**验 OPPO token/openid：只把 OPPO SDK 返回的 authCode 交给服务端。
 * 所有方法不抛异常：网络/超时/非 2xx/非 JSON 都映射为对应结果（UI 只做简短提示）。
 */
interface OppoAccountApi {
    suspend fun login(authCode: String): OppoLoginResult

    suspend fun bind(authCode: String, classToken: String): OppoBindResult

    suspend fun bindings(classToken: String): OppoBindingsResult

    suspend fun unbind(bindingId: Long, classToken: String): OppoUnbindResult

    /** `GET /__health`：仅排错/联调使用。 */
    suspend fun health(): OppoHealthResult
}

/** `/__health` 结果（configured=false 表示 ECS 侧未配置 OPPO 凭证）。 */
data class OppoHealthResult(
    val ok: Boolean,
    val configured: Boolean,
    val version: String = "",
)

/** HTTP 层的中间结果（用于统一错误映射）。 */
internal sealed interface OppoHttpOutcome {
    data class Body(val status: Int, val text: String) : OppoHttpOutcome
    data class Failure(val code: String, val message: String) : OppoHttpOutcome
}

/**
 * ECS 账号接口客户端（OPPO `/oppo` 与荣耀 `/honor` 共用同一契约，仅路径与文案前缀不同）。
 *
 * @param pathPrefix 服务端路径前缀（`/oppo` 或 `/honor`）
 * @param providerLabel 展示用厂商名（OPPO / 荣耀）
 */
class EcsOppoAccountApi(
    private val transport: HttpTransport,
    baseUrl: String = OppoAccountConfig.DEFAULT_BASE_URL,
    private val requestTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val json: Json = NetJson,
    private val pathPrefix: String = "/oppo",
    private val providerLabel: String = "OPPO",
) : OppoAccountApi {

    /** 规范化根地址（结尾无 `/`）。 */
    val baseUrl: String = baseUrl.trim().trimEnd('/').ifBlank { OppoAccountConfig.DEFAULT_BASE_URL }

    override suspend fun login(authCode: String): OppoLoginResult {
        val code = OppoAuthCode.normalize(authCode)
            ?: return OppoLoginResult.Failure(OppoAccountErrorCode.PARSE, "$providerLabel 授权码无效，请重试")
        val body = buildJsonObject { put("code", JsonPrimitive(code)) }.toString()
        return when (val outcome = call(HttpMethod.POST, "$pathPrefix/login", body, classToken = null)) {
            is OppoHttpOutcome.Failure -> OppoLoginResult.Failure(outcome.code, outcome.message)
            is OppoHttpOutcome.Body -> {
                if (outcome.status !in 200..299) {
                    httpError(outcome) { codeText, message ->
                        OppoLoginResult.Failure(codeText, message)
                    }
                } else {
                    parseLogin(outcome.text)
                }
            }
        }
    }

    override suspend fun bind(authCode: String, classToken: String): OppoBindResult {
        val code = OppoAuthCode.normalize(authCode)
            ?: return OppoBindResult.Failure(OppoAccountErrorCode.PARSE, "$providerLabel 授权码无效，请重试")
        val body = buildJsonObject { put("code", JsonPrimitive(code)) }.toString()
        return when (val outcome = call(HttpMethod.POST, "$pathPrefix/bind", body, classToken)) {
            is OppoHttpOutcome.Failure -> OppoBindResult.Failure(outcome.code, outcome.message)
            is OppoHttpOutcome.Body -> {
                if (outcome.status !in 200..299) {
                    httpError(outcome) { codeText, message -> OppoBindResult.Failure(codeText, message) }
                } else {
                    parseBind(outcome.text)
                }
            }
        }
    }

    override suspend fun bindings(classToken: String): OppoBindingsResult {
        return when (val outcome = call(HttpMethod.GET, "$pathPrefix/bindings", body = null, classToken)) {
            is OppoHttpOutcome.Failure -> OppoBindingsResult.Failure(outcome.code, outcome.message)
            is OppoHttpOutcome.Body -> {
                if (outcome.status !in 200..299) {
                    httpError(outcome) { codeText, message ->
                        OppoBindingsResult.Failure(codeText, message)
                    }
                } else {
                    parseBindings(outcome.text)
                }
            }
        }
    }

    override suspend fun unbind(bindingId: Long, classToken: String): OppoUnbindResult {
        if (bindingId <= 0) {
            return OppoUnbindResult.Failure(OppoAccountErrorCode.PARSE, "绑定记录无效，请刷新后重试")
        }
        val body = buildJsonObject { put("id", JsonPrimitive(bindingId)) }.toString()
        return when (val outcome = call(HttpMethod.POST, "$pathPrefix/unbind", body, classToken)) {
            is OppoHttpOutcome.Failure -> OppoUnbindResult.Failure(outcome.code, outcome.message)
            is OppoHttpOutcome.Body -> {
                if (outcome.status !in 200..299) {
                    httpError(outcome) { codeText, message -> OppoUnbindResult.Failure(codeText, message) }
                } else {
                    parseUnbind(outcome.text)
                }
            }
        }
    }

    override suspend fun health(): OppoHealthResult {
        return when (val outcome = call(HttpMethod.GET, "/__health", body = null, classToken = null)) {
            is OppoHttpOutcome.Failure -> OppoHealthResult(ok = false, configured = false)
            is OppoHttpOutcome.Body -> {
                val obj = (parseJsonOrNull(json, outcome.text) as? JsonObject)
                OppoHealthResult(
                    ok = obj?.get("ok").asBooleanOrNull() ?: (outcome.status in 200..299),
                    configured = obj?.get("configured").asBooleanOrNull() ?: false,
                    version = obj?.get("version").asStringOrNull().orEmpty(),
                )
            }
        }
    }

    // ------------------------------------------------------------ 请求 / 错误映射

    private suspend fun call(
        method: HttpMethod,
        path: String,
        body: String?,
        classToken: String?,
    ): OppoHttpOutcome {
        val requiresToken = path != "$pathPrefix/login" && path != "/__health"
        val token = classToken?.trim().orEmpty()
        if (requiresToken && token.isEmpty()) {
            return OppoHttpOutcome.Failure(OppoAccountErrorCode.UNAUTHORIZED, "登录已失效，请重新验证")
        }
        val headers = LinkedHashMap<String, String>()
        headers["Accept"] = "application/json"
        if (body != null) headers["Content-Type"] = "application/json; charset=utf-8"
        if (token.isNotEmpty()) headers["X-Class-Token"] = token
        val request = ApiRequest(
            method = method,
            url = baseUrl + path,
            headers = headers,
            body = body,
        )
        return try {
            val response = withTimeout(requestTimeoutMs) { transport.execute(request) }
            OppoHttpOutcome.Body(status = response.status, text = response.body)
        } catch (timeout: TimeoutCancellationException) {
            OppoHttpOutcome.Failure(OppoAccountErrorCode.TIMEOUT, "网络超时，请稍后重试")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            OppoHttpOutcome.Failure(OppoAccountErrorCode.NETWORK, "网络不可用，请稍后重试")
        }
    }

    private fun <T> httpError(
        outcome: OppoHttpOutcome.Body,
        map: (code: String, message: String) -> T,
    ): T {
        val obj = (parseJsonOrNull(json, outcome.text) as? JsonObject)
        val serverCode = obj?.get("code").asStringOrNull().orEmpty()
        val serverMessage = obj?.get("message").asStringOrNull().orEmpty()
            .ifBlank { obj?.get("msg").asStringOrNull().orEmpty() }
        if (outcome.status == 401 || outcome.status == 403) {
            return map(OppoAccountErrorCode.UNAUTHORIZED, "登录已失效，请重新验证")
        }
        val message = when {
            // 服务端换 openid 失败（账号服务未开通/凭证不匹配）：给出可诊断提示（含 invalid_client 等原文）。
            serverCode == OppoAccountErrorCode.EXCHANGE_FAILED ->
                OppoAccountText.exchangeFailedMessage(serverMessage, providerLabel)

            serverMessage.isNotBlank() -> serverMessage
            outcome.status in 500..599 -> "服务暂时不可用，请稍后重试"
            else -> "请求失败（${outcome.status}）"
        }
        return map(serverCode.ifBlank { "http_${outcome.status}" }, message)
    }

    // ------------------------------------------------------------ 响应解析（internal 供单测）

    internal fun parseLogin(raw: String): OppoLoginResult {
        val obj = (parseJsonOrNull(json, raw) as? JsonObject)
            ?: return OppoLoginResult.Failure(OppoAccountErrorCode.PARSE, "账号服务返回了无法解析的内容")
        val okFlag = obj["ok"].asBooleanOrNull()
        val token = obj["token"].asStringOrNull().orEmpty().trim()
        val errorCode = obj["code"].asStringOrNull().orEmpty().trim()
        val message = obj["message"].asStringOrNull().orEmpty().trim()
            .ifBlank { obj["reason"].asStringOrNull().orEmpty().trim() }
        if (okFlag == false) {
            if (errorCode == OppoAccountErrorCode.UNBOUND) {
                return OppoLoginResult.Unbound(message.ifBlank { OppoAccountText.unboundFallback(providerLabel) })
            }
            if (errorCode == OppoAccountErrorCode.EXCHANGE_FAILED) {
                return OppoLoginResult.Failure(
                    code = errorCode,
                    message = OppoAccountText.exchangeFailedMessage(message, providerLabel),
                )
            }
            return OppoLoginResult.Failure(
                code = errorCode.ifBlank { "login_failed" },
                message = message.ifBlank { "$providerLabel 登录失败，请稍后重试" },
            )
        }
        // 兼容 ok 字段缺失：只要拿到 token 即视为成功。
        if (token.isEmpty()) {
            return OppoLoginResult.Failure(
                code = errorCode.ifBlank { "token_missing" },
                message = message.ifBlank { "账号服务未返回身份凭证，请稍后重试" },
            )
        }
        val sid = obj["sid"].asStringOrNull().orEmpty().trim()
        if (sid.isEmpty()) {
            return OppoLoginResult.Failure(OppoAccountErrorCode.PARSE, "账号服务返回的身份不完整")
        }
        return OppoLoginResult.Success(
            token = token,
            sid = sid,
            name = obj["name"].asStringOrNull().orEmpty().trim(),
            nickname = obj["nickname"].asStringOrNull().orEmpty().trim(),
        )
    }

    internal fun parseBind(raw: String): OppoBindResult {
        val obj = (parseJsonOrNull(json, raw) as? JsonObject)
            ?: return OppoBindResult.Failure(OppoAccountErrorCode.PARSE, "账号服务返回了无法解析的内容")
        val okFlag = obj["ok"].asBooleanOrNull()
        val errorCode = obj["code"].asStringOrNull().orEmpty().trim()
        val message = obj["message"].asStringOrNull().orEmpty().trim()
        if (okFlag == false) {
            return when (errorCode) {
                OppoAccountErrorCode.LIMIT -> OppoBindResult.Limit(
                    message.ifBlank { "一个学号最多绑定 3 个 $providerLabel 账号" },
                )

                OppoAccountErrorCode.BOUND_ELSEWHERE -> OppoBindResult.BoundElsewhere(
                    message.ifBlank { "该 $providerLabel 账号已绑定其他学号" },
                )

                OppoAccountErrorCode.EXCHANGE_FAILED -> OppoBindResult.Failure(
                    code = errorCode,
                    message = OppoAccountText.exchangeFailedMessage(message, providerLabel),
                )

                else -> OppoBindResult.Failure(
                    code = errorCode.ifBlank { "bind_failed" },
                    message = message.ifBlank { "绑定失败，请稍后重试" },
                )
            }
        }
        val binding = parseBinding(obj["binding"] as? JsonObject)
            ?: return OppoBindResult.Failure(OppoAccountErrorCode.PARSE, "账号服务返回的绑定信息不完整")
        return OppoBindResult.Success(
            binding = binding,
            count = obj["count"].asIntOrNull() ?: 1,
        )
    }

    internal fun parseBindings(raw: String): OppoBindingsResult {
        val obj = (parseJsonOrNull(json, raw) as? JsonObject)
            ?: return OppoBindingsResult.Failure(OppoAccountErrorCode.PARSE, "账号服务返回了无法解析的内容")
        val okFlag = obj["ok"].asBooleanOrNull()
            ?: return OppoBindingsResult.Failure(OppoAccountErrorCode.PARSE, "账号服务返回结构异常")
        if (!okFlag) {
            return OppoBindingsResult.Failure(
                code = obj["code"].asStringOrNull().orEmpty().ifBlank { "list_failed" },
                message = obj["message"].asStringOrNull().orEmpty().ifBlank { "读取绑定列表失败，请稍后重试" },
            )
        }
        val items = (obj["items"] as? JsonArray).orEmpty().mapNotNull { element ->
            parseBinding(element as? JsonObject)
        }
        val max = obj["max"].asIntOrNull()?.takeIf { it in 1..MAX_BINDINGS_LIMIT }
            ?: OppoAccountText.DEFAULT_MAX_BINDINGS
        val count = obj["count"].asIntOrNull() ?: items.size
        return OppoBindingsResult.Success(
            OppoBindingsPage(max = max, count = count.coerceIn(0, max), items = items),
        )
    }

    internal fun parseUnbind(raw: String): OppoUnbindResult {
        val obj = (parseJsonOrNull(json, raw) as? JsonObject)
            ?: return OppoUnbindResult.Failure(OppoAccountErrorCode.PARSE, "账号服务返回了无法解析的内容")
        val okFlag = obj["ok"].asBooleanOrNull()
            ?: return OppoUnbindResult.Failure(OppoAccountErrorCode.PARSE, "账号服务返回结构异常")
        if (!okFlag) {
            return OppoUnbindResult.Failure(
                code = obj["code"].asStringOrNull().orEmpty().ifBlank { "unbind_failed" },
                message = obj["message"].asStringOrNull().orEmpty().ifBlank { "解绑失败，请稍后重试" },
            )
        }
        return OppoUnbindResult.Success(count = obj["count"].asIntOrNull() ?: 0)
    }

    private fun parseBinding(obj: JsonObject?): OppoBinding? {
        if (obj == null) return null
        val id = (obj["id"] as? JsonPrimitive)?.let { primitive ->
            primitive.contentOrNull?.toLongOrNull()
        } ?: return null
        if (id <= 0) return null
        return OppoBinding(
            id = id,
            nickname = obj["nickname"].asStringOrNull().orEmpty().trim(),
            createdAt = obj["createdAt"].asStringOrNull().orEmpty().trim(),
        )
    }

    companion object {
        /** 请求超时（ECS 直连 IP；超时映射为可重试提示）。 */
        const val DEFAULT_TIMEOUT_MS: Long = 15_000L

        /** 服务端 max 上限保护（防止异常响应把「已绑定 N/x」显示错位）。 */
        const val MAX_BINDINGS_LIMIT: Int = 20
    }
}

/** OPPO authCode 规范化（纯逻辑）：
 * SDK 回调可能带空白；空/超长/含控制字符一律视为无效（防把异常字符串当授权码发到服务端）。
 */
object OppoAuthCode {

    private const val MAX_LENGTH = 2048

    fun normalize(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.length > MAX_LENGTH) return null
        if (text.any { it.isISOControl() || it.isWhitespace() }) return null
        return text
    }
}



