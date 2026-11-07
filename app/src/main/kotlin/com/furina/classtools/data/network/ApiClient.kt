package com.furina.classtools.data.network

import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull

/**
 * CloudBase PostgREST 网关客户端（契约：docs/BACKEND_CONTRACT.md §1 + docs/api/ENDPOINTS.md）。
 *
 * 线程约定：所有方法都是 `suspend` 且主线程安全；网络 IO 在 [OkHttpTransport] 内切到 Dispatchers.IO。
 * 超时/重试：连接/读 10s；**GET 网络失败重试 1 次（约 350ms 后），写请求不重试**（防重复写入）。
 * 请求头：`Authorization: Bearer <publishableKey>` + `X-Class-Token: <token>`（有身份才加）+ `Cache-Control: no-store`。
 * 错误：401/403 → [ApiError.TokenInvalid]（并回调 [onTokenInvalid]）；其余非 2xx → [ApiError.Http]（解析 PostgREST 错误体）。
 *
 * 注意：本类不直接读 BuildConfig；由装配层（AppContainer）传入 [baseUrl] / [publishableKey]，
 * 避免数据层与构建产物耦合，也方便单测注入假传输层。
 */
class ApiClient(
    baseUrl: String,
    private val publishableKey: String,
    private val tokenProvider: () -> String? = { null },
    private val transport: HttpTransport = OkHttpTransport(),
    private val json: Json = NetJson,
    private val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
    private val onTokenInvalid: () -> Unit = {},
) {
    /** 规范化后的 REST 根（结尾必带 `/`；与网页 `var CB` / 小程序 `restBase` 语义一致）。 */
    val baseUrl: String = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    // ------------------------------------------------------------ URL / 请求头（可单测的纯函数）

    /** `path` 可携带 query（如 `userdata?select=*&id=eq.123`）。 */
    fun url(path: String): String = baseUrl + path.removePrefix("/")

    fun buildHeaders(
        authenticated: Boolean,
        prefer: String? = null,
        extra: Map<String, String> = emptyMap(),
    ): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        if (publishableKey.isNotBlank()) headers["Authorization"] = "Bearer $publishableKey"
        headers["Content-Type"] = "application/json"
        headers["Accept"] = "application/json"
        headers["Cache-Control"] = "no-store"
        if (authenticated) {
            val token = tokenProvider()?.trim().orEmpty()
            if (token.isNotEmpty()) headers["X-Class-Token"] = token
        }
        if (!prefer.isNullOrBlank()) headers["Prefer"] = prefer
        headers.putAll(extra)
        return headers
    }

    // ------------------------------------------------------------ 原始请求

    /** GET：网络失败自动重试一次（约 350ms 后）。 */
    suspend fun get(
        pathAndQuery: String,
        authenticated: Boolean = true,
        extraHeaders: Map<String, String> = emptyMap(),
    ): ApiResult<String> = execute(
        method = HttpMethod.GET,
        path = pathAndQuery,
        body = null,
        prefer = null,
        authenticated = authenticated,
        extraHeaders = extraHeaders,
        retryNetworkFailure = true,
    )

    /** POST：不重试。 */
    suspend fun post(
        path: String,
        body: String,
        prefer: String? = null,
        authenticated: Boolean = true,
        extraHeaders: Map<String, String> = emptyMap(),
    ): ApiResult<String> = execute(
        method = HttpMethod.POST,
        path = path,
        body = body,
        prefer = prefer,
        authenticated = authenticated,
        extraHeaders = extraHeaders,
        retryNetworkFailure = false,
    )

    /** PATCH：不重试。 */
    suspend fun patch(
        path: String,
        body: String,
        prefer: String? = null,
        authenticated: Boolean = true,
        extraHeaders: Map<String, String> = emptyMap(),
    ): ApiResult<String> = execute(
        method = HttpMethod.PATCH,
        path = path,
        body = body,
        prefer = prefer,
        authenticated = authenticated,
        extraHeaders = extraHeaders,
        retryNetworkFailure = false,
    )

    // ------------------------------------------------------------ JSON 便捷助手

    suspend fun getJson(
        pathAndQuery: String,
        authenticated: Boolean = true,
    ): ApiResult<JsonElement> = get(pathAndQuery, authenticated).flatMapJson()

    suspend fun postJson(
        path: String,
        body: String,
        prefer: String? = null,
        authenticated: Boolean = true,
    ): ApiResult<JsonElement> = post(path, body, prefer, authenticated).flatMapJson()

    suspend fun patchJson(
        path: String,
        body: String,
        prefer: String? = null,
        authenticated: Boolean = true,
    ): ApiResult<JsonElement> = patch(path, body, prefer, authenticated).flatMapJson()

    private fun ApiResult<String>.flatMapJson(): ApiResult<JsonElement> = when (this) {
        is ApiResult.Failure -> ApiResult.Failure(error)
        is ApiResult.Success -> {
            val parsed = parseJsonOrNull(json, value)
            if (parsed == null) {
                ApiResult.Failure(ApiError.Parse("响应不是合法 JSON"))
            } else {
                ApiResult.Success(parsed)
            }
        }
    }

    // ------------------------------------------------------------ 执行/错误映射

    private suspend fun execute(
        method: HttpMethod,
        path: String,
        body: String?,
        prefer: String?,
        authenticated: Boolean,
        extraHeaders: Map<String, String>,
        retryNetworkFailure: Boolean,
    ): ApiResult<String> {
        if (baseUrl.isBlank() || publishableKey.isBlank()) {
            return ApiResult.Failure(ApiError.Config("云端未配置（baseUrl/publishableKey 为空）"))
        }
        val request = ApiRequest(
            method = method,
            url = url(path),
            headers = buildHeaders(authenticated, prefer, extraHeaders),
            body = body,
        )
        var attempted = 0
        while (true) {
            val response = try {
                transport.execute(request)
            } catch (t: Throwable) {
                if (retryNetworkFailure && attempted == 0 && isRetriableNetworkFailure(t)) {
                    attempted += 1
                    delay(retryDelayMs)
                    continue
                }
                return ApiResult.Failure(ApiError.Network(cause = t))
            }
            return interpretResponse(response)
        }
    }

    /** 取消/超时/协程取消不重试；只有真正的连接类失败才重试。 */
    private fun isRetriableNetworkFailure(t: Throwable): Boolean {
        if (t is kotlinx.coroutines.CancellationException) return false
        if (t is java.io.IOException) return true
        return t.cause is java.io.IOException
    }

    private fun interpretResponse(response: ApiResponse): ApiResult<String> {
        val status = response.status
        return when {
            status in 200..299 -> ApiResult.Success(response.body)
            status == 401 || status == 403 -> {
                onTokenInvalid()
                ApiResult.Failure(ApiError.TokenInvalid(status))
            }
            else -> ApiResult.Failure(httpError(status, response.body))
        }
    }

    private fun httpError(status: Int, body: String): ApiError.Http {
        val parsed = parsePostgrestError(json, body)
        return ApiError.Http(
            status = status,
            postgrestCode = parsed?.code.orEmpty(),
            serverMessage = parsed?.message.orEmpty(),
        )
    }

    companion object {
        const val DEFAULT_RETRY_DELAY_MS: Long = 350L

        /** 解析 PostgREST 错误体；不是 JSON 对象时返回 null。 */
        fun parsePostgrestError(json: Json, body: String?): PostgrestErrorBody? {
            val element = parseJsonOrNull(json, body) as? kotlinx.serialization.json.JsonObject ?: return null
            fun field(name: String): String =
                (element[name] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
            val code = field("code")
            val message = field("message")
            val requestId = field("requestId")
            if (code.isEmpty() && message.isEmpty() && requestId.isEmpty()) return null
            return PostgrestErrorBody(code = code, message = message, requestId = requestId)
        }

        /** 解析形如 `[{...}, ...]` 的数组；不是数组返回 null。 */
        fun asArrayOrNull(element: JsonElement?): JsonArray? = element as? JsonArray
    }
}
