package com.furina.classtools.data.network

import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** 全局 JSON 配置（与 docs/api/KOTLIN_MODELS.md 一致）：忽略未知键，容忍 null → 默认值。 */
val NetJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
    encodeDefaults = true
    coerceInputValues = true
}

enum class HttpMethod(val wire: String) {
    GET("GET"),
    POST("POST"),
    PATCH("PATCH"),
}

/** 一次 HTTP 调用的最小描述（纯数据，方便单测注入假实现）。 */
data class ApiRequest(
    val method: HttpMethod,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

data class ApiResponse(
    val status: Int,
    val body: String = "",
    /** 响应头（键大小写不敏感访问由调用方处理；诊断页「服务器时钟偏差」读 Date）。 */
    val headers: Map<String, String> = emptyMap(),
)

/**
 * HTTP 传输层抽象。
 *
 * 生产实现：[OkHttpTransport]；单测使用 FakeTransport 断言请求头/请求体，
 * 避免真实网络（没有引入 MockWebServer 依赖）。
 */
fun interface HttpTransport {
    suspend fun execute(request: ApiRequest): ApiResponse
}

/** OkHttp 实现：10s 超时、禁用自动重试（重试策略由 [ApiClient] 控制）、调用方负责 Dispatchers。 */
class OkHttpTransport(
    private val client: okhttp3.OkHttpClient = defaultClient(),
) : HttpTransport {

    override suspend fun execute(request: ApiRequest): ApiResponse = withContext(Dispatchers.IO) {
        val builder = okhttp3.Request.Builder().url(request.url)
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        when (request.method) {
            HttpMethod.GET -> builder.get()
            HttpMethod.POST -> builder.post(
                request.body?.toRequestBody(mediaType) ?: EMPTY_REQUEST_BODY,
            )
            HttpMethod.PATCH -> builder.patch(
                request.body?.toRequestBody(mediaType) ?: EMPTY_REQUEST_BODY,
            )
        }
        client.newCall(builder.build()).execute().use { response ->
            val headers = LinkedHashMap<String, String>()
            for (i in 0 until response.headers.size) {
                headers[response.headers.name(i)] = response.headers.value(i)
            }
            ApiResponse(status = response.code, body = response.body.string(), headers = headers)
        }
    }

    companion object {
        private val EMPTY_REQUEST_BODY = ByteArray(0).toRequestBody(null)

        /**
         * 契约：连接/读 10s（docs/BACKEND_CONTRACT.md §1.3）。
         * `retryOnConnectionFailure(false)`：GET 的重试由 [ApiClient] 显式做一次，写请求绝不自动重试。
         */
        fun defaultClient(): okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(10))
            .writeTimeout(Duration.ofSeconds(10))
            .callTimeout(Duration.ofSeconds(20))
            .retryOnConnectionFailure(false)
            .build()
    }
}

// ---------------------------------------------------------------- 解析小工具

fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

fun JsonElement?.asStringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

fun JsonElement?.asLongOrNull(): Long? = (this as? JsonPrimitive)?.longOrNull

fun JsonElement?.asIntOrNull(): Int? = (this as? JsonPrimitive)?.intOrNull

fun JsonElement?.asBooleanOrNull(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

/** 安全解析 JSON：失败返回 null（调用方按「无云端数据 / 坏数据降级」处理）。 */
fun parseJsonOrNull(json: Json, text: String?): JsonElement? {
    if (text.isNullOrBlank()) return null
    return try {
        json.parseToJsonElement(text)
    } catch (_: Throwable) {
        null
    }
}
