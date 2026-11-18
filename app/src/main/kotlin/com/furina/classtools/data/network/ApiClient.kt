package com.furina.classtools.data.network

import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull

/**
 * CloudBase PostgREST 网关客户端（契约：docs/BACKEND_CONTRACT.md §1 + docs/api/ENDPOINTS.md）。
 *
 * 线程约定：所有方法都是 `suspend` 且主线程安全；网络 IO 在 [OkHttpTransport] 内切到 Dispatchers.IO。
 * 超时/重试：
 * - 连接 15s / 读 30s / 整体 60s（[NetworkClients.apiClient]）；调用方可按接口分级传 [timeoutMs]
 *   （2026-11-18 超时分级：登录/身份验证用更宽的档，普通读写用默认档）；
 * - **幂等只读请求（GET）网络失败重试 [maxRetries] 次（默认 1 次，约 350ms + 抖动）**，
 *   并对 502/503/504 瞬时网关错误同样重试一次；写请求不重试（防重复写入）。
 * - **在途合并（2026-11-18）**：同一会话下并发的相同 GET 只发一个 HTTP 请求，其它调用复用结果。
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
    /** GET 网络失败/瞬时 5xx 的最大重试次数（默认 1 次 = 总共 2 次尝试）。 */
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    /** 重试退避抖动上限（毫秒）：避免多请求同时重试形成尖峰。 */
    private val retryJitterMs: Long = DEFAULT_RETRY_JITTER_MS,
    /** 幂等只读请求遇到这些网关瞬时状态码时重试一次。 */
    private val transientStatusCodes: Set<Int> = DEFAULT_TRANSIENT_STATUS,
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

    /**
     * GET（幂等只读）：
     * - 网络失败 / 502/503/504 瞬时错误自动重试 [maxRetries] 次（默认 1 次，约 350ms + 抖动）；
     * - 同一会话下并发的相同 GET 在途合并（[coalesce]=false 可关闭）；
     * - [timeoutMs]：本次调用整体超时（2026-11-18 超时分级；null = client 默认 60s）。
     */
    suspend fun get(
        pathAndQuery: String,
        authenticated: Boolean = true,
        extraHeaders: Map<String, String> = emptyMap(),
        timeoutMs: Long? = null,
        coalesce: Boolean = true,
    ): ApiResult<String> {
        val block: suspend () -> ApiResult<String> = {
            execute(
                method = HttpMethod.GET,
                path = pathAndQuery,
                body = null,
                prefer = null,
                authenticated = authenticated,
                extraHeaders = extraHeaders,
                retryNetworkFailure = true,
                timeoutMs = timeoutMs,
            )
        }
        if (!coalesce) return block()
        return executeCoalesced(coalesceKey(pathAndQuery, authenticated, extraHeaders), block)
    }

    /** POST：不重试。 */
    suspend fun post(
        path: String,
        body: String,
        prefer: String? = null,
        authenticated: Boolean = true,
        extraHeaders: Map<String, String> = emptyMap(),
        timeoutMs: Long? = null,
    ): ApiResult<String> = execute(
        method = HttpMethod.POST,
        path = path,
        body = body,
        prefer = prefer,
        authenticated = authenticated,
        extraHeaders = extraHeaders,
        retryNetworkFailure = false,
        timeoutMs = timeoutMs,
    )

    /** PATCH：不重试。 */
    suspend fun patch(
        path: String,
        body: String,
        prefer: String? = null,
        authenticated: Boolean = true,
        extraHeaders: Map<String, String> = emptyMap(),
        timeoutMs: Long? = null,
    ): ApiResult<String> = execute(
        method = HttpMethod.PATCH,
        path = path,
        body = body,
        prefer = prefer,
        authenticated = authenticated,
        extraHeaders = extraHeaders,
        retryNetworkFailure = false,
        timeoutMs = timeoutMs,
    )

    // ------------------------------------------------------------ JSON 便捷助手

    suspend fun getJson(
        pathAndQuery: String,
        authenticated: Boolean = true,
        timeoutMs: Long? = null,
        coalesce: Boolean = true,
    ): ApiResult<JsonElement> = get(
        pathAndQuery,
        authenticated,
        timeoutMs = timeoutMs,
        coalesce = coalesce,
    ).flatMapJson()

    suspend fun postJson(
        path: String,
        body: String,
        prefer: String? = null,
        authenticated: Boolean = true,
        timeoutMs: Long? = null,
    ): ApiResult<JsonElement> = post(path, body, prefer, authenticated, timeoutMs = timeoutMs).flatMapJson()

    suspend fun patchJson(
        path: String,
        body: String,
        prefer: String? = null,
        authenticated: Boolean = true,
        timeoutMs: Long? = null,
    ): ApiResult<JsonElement> = patch(path, body, prefer, authenticated, timeoutMs = timeoutMs).flatMapJson()

    /**
     * 诊断用：单次 GET 并返回「耗时 + 服务器 Date 头」。
     *
     * - 不做网络失败重试（延迟测量只认一次真实往返）；
     * - [TimedResponse.serverEpochMs] 为响应头 `Date` 解析结果（毫秒，UTC），缺失/非法 → null；
     *   Android 端用它估算本机时钟偏差（诊断页「时钟偏差」检查），不涉及任何隐私数据。
     */
    suspend fun getTimed(
        pathAndQuery: String,
        authenticated: Boolean = true,
    ): ApiResult<TimedResponse> {
        if (baseUrl.isBlank() || publishableKey.isBlank()) {
            return ApiResult.Failure(ApiError.Config("云端未配置（baseUrl/publishableKey 为空）"))
        }
        val request = ApiRequest(
            method = HttpMethod.GET,
            url = url(pathAndQuery),
            headers = buildHeaders(authenticated),
            body = null,
        )
        val startedNanos = System.nanoTime()
        val response = try {
            transport.execute(request)
        } catch (t: Throwable) {
            return ApiResult.Failure(ApiError.Network(cause = t))
        }
        val latencyMs = (System.nanoTime() - startedNanos) / 1_000_000L
        return when (val result = interpretResponse(response)) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(
                TimedResponse(
                    body = result.value,
                    latencyMs = latencyMs,
                    serverEpochMs = headerEpochMs(response.headers),
                ),
            )
        }
    }

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

    // ------------------------------------------------------------ 在途合并（2026-11-18）

    /** 在途 GET 表：key = path + 身份/头指纹 → 共享结果（不保存任何 token 明文）。 */
    private val inFlightGets = ConcurrentHashMap<String, CompletableDeferred<ApiResult<String>>>()

    /**
     * 合并键：同一会话（token 指纹）+ 相同 path/头 → 视为同一逻辑请求。
     * 只用于 GET（幂等只读）；写请求永不合并/重试。
     */
    private fun coalesceKey(
        pathAndQuery: String,
        authenticated: Boolean,
        extraHeaders: Map<String, String>,
    ): String {
        val tokenFingerprint = if (authenticated) (tokenProvider()?.hashCode() ?: 0).toString() else "anon"
        val headers = extraHeaders.entries.sortedBy { it.key }.joinToString(",") { it.key + "=" + it.value }
        return pathAndQuery + "|" + authenticated + "|" + tokenFingerprint + "|" + headers
    }

    /**
     * 在途合并：首个调用者真正发请求，其余同键调用者等待并复用同一结果。
     * - 首个调用被取消/抛异常时，等待者收到同一异常（可读的失败语义，不会悬挂）；
     * - 请求结束即从表里移除（不缓存旧数据，后续调用仍会真正发请求）。
     */
    private suspend fun executeCoalesced(
        key: String,
        block: suspend () -> ApiResult<String>,
    ): ApiResult<String> {
        while (true) {
            inFlightGets[key]?.let { return it.await() }
            val deferred = CompletableDeferred<ApiResult<String>>()
            if (inFlightGets.putIfAbsent(key, deferred) == null) {
                return try {
                    val result = block()
                    deferred.complete(result)
                    result
                } catch (t: Throwable) {
                    deferred.completeExceptionally(t)
                    throw t
                } finally {
                    inFlightGets.remove(key, deferred)
                }
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
        timeoutMs: Long? = null,
    ): ApiResult<String> {
        if (baseUrl.isBlank() || publishableKey.isBlank()) {
            return ApiResult.Failure(ApiError.Config("云端未配置（baseUrl/publishableKey 为空）"))
        }
        val request = ApiRequest(
            method = method,
            url = url(path),
            headers = buildHeaders(authenticated, prefer, extraHeaders),
            body = body,
            timeoutMs = timeoutMs,
        )
        var attempt = 0
        while (true) {
            val response = try {
                transport.execute(request)
            } catch (t: Throwable) {
                if (retryNetworkFailure && attempt < maxRetries && isRetriableNetworkFailure(t)) {
                    attempt += 1
                    delay(retryDelayOf(attempt))
                    continue
                }
                return ApiResult.Failure(ApiError.Network(cause = t))
            }
            // 幂等只读请求遇到网关瞬时错误（502/503/504）重试一次（写请求 retryNetworkFailure=false 不会走到）
            if (retryNetworkFailure && attempt < maxRetries && response.status in transientStatusCodes) {
                attempt += 1
                delay(retryDelayOf(attempt))
                continue
            }
            return interpretResponse(response)
        }
    }

    /** 重试退避：第 1 次 ≈ 350ms，第 2 次 ≈ 700ms；各加 [0, retryJitterMs] 随机抖动防尖峰。 */
    private fun retryDelayOf(attempt: Int): Long {
        val base = retryDelayMs * attempt
        if (retryJitterMs <= 0) return base
        return base + Random.nextLong(0, retryJitterMs + 1)
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

        /** 幂等只读请求默认最多重试 1 次（总 2 次尝试）。 */
        const val DEFAULT_MAX_RETRIES: Int = 1

        /** 重试退避抖动上限（毫秒）。 */
        const val DEFAULT_RETRY_JITTER_MS: Long = 120L

        /** 网关瞬时错误（幂等只读请求会重试一次）。 */
        val DEFAULT_TRANSIENT_STATUS: Set<Int> = setOf(502, 503, 504)

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

        /** 取响应头（大小写不敏感）；不存在 → null。 */
        fun headerValue(headers: Map<String, String>, name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

        /** 解析 HTTP `Date` 头（RFC 1123，如 `Wed, 08 Nov 2026 12:00:00 GMT`）→ epoch ms。 */
        fun headerEpochMs(headers: Map<String, String>): Long? {
            val raw = headerValue(headers, "Date")?.trim().orEmpty()
            if (raw.isEmpty()) return null
            return try {
                java.time.ZonedDateTime.parse(raw, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant()
                    .toEpochMilli()
            } catch (_: Throwable) {
                null
            }
        }
    }
}

/** 一次计时请求的结果（诊断页网关延迟 / 服务器时钟偏差用）。 */
data class TimedResponse(
    val body: String,
    val latencyMs: Long,
    val serverEpochMs: Long? = null,
)
