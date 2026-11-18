package com.furina.classtools.data.cloudspace

import com.furina.classtools.data.network.NetworkClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** drop 服务用到的 HTTP 方法（JSON API + 分片原始字节 + 单文件删除）。 */
enum class CloudSpaceHttpMethod(val wire: String) {
    GET("GET"),
    POST("POST"),
    DELETE("DELETE"),
}

/**
 * 一次 drop 调用的最小描述（纯数据，便于单测注入假传输层）。
 *
 * @param body 原始请求体（分片上传 = 原始字节；JSON 请求 = UTF-8 JSON 字节）
 * @param contentType 仅写请求需要：`application/json` 或 `application/octet-stream`
 */
data class CloudSpaceRequest(
    val method: CloudSpaceHttpMethod,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val contentType: String? = null,
)

data class CloudSpaceResponse(
    val status: Int,
    val body: String = "",
    val headers: Map<String, String> = emptyMap(),
)

/**
 * 云空间 HTTP 传输层抽象。
 *
 * 生产实现 [OkHttpCloudSpaceTransport]；单测用 `FakeCloudSpaceTransport` 断言请求构造
 * （URL / X-Device-Id / Content-Type / 原始分片字节），不引入 MockWebServer 依赖。
 */
fun interface CloudSpaceTransport {
    suspend fun execute(request: CloudSpaceRequest): CloudSpaceResponse
}

/**
 * OkHttp 实现（drop 服务专用）。
 *
 * - 连接 15s；普通读/写 120s；callTimeout 10 分钟（大文件 8MiB 分片在弱网下需要更久）；
 * - **关闭自动重试**：写请求（init/chunk/finish/delete/close）重复提交可能产生重复上传/误删，
 *   重试策略交给用户手动「重试」；幂等的 GET(/state、/mine) 也不自动重试（避免轮询叠加）。
 * - 不记录任何日志（token 只在 [CloudSpaceRequest.headers] 中出现，绝不打印）。
 */
class OkHttpCloudSpaceTransport(
    private val client: OkHttpClient = defaultClient(),
) : CloudSpaceTransport {

    override suspend fun execute(request: CloudSpaceRequest): CloudSpaceResponse =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(request.url)
            request.headers.forEach { (name, value) ->
                if (value.isNotEmpty()) builder.header(name, value)
            }
            val mediaType = request.contentType?.toMediaTypeOrNull()
            when (request.method) {
                CloudSpaceHttpMethod.GET -> builder.get()
                CloudSpaceHttpMethod.POST -> builder.post(
                    request.body?.toRequestBody(mediaType) ?: EMPTY_REQUEST_BODY,
                )

                CloudSpaceHttpMethod.DELETE -> builder.delete()
            }
            client.newCall(builder.build()).execute().use { response ->
                val headers = LinkedHashMap<String, String>()
                for (index in 0 until response.headers.size) {
                    headers[response.headers.name(index)] = response.headers.value(index)
                }
                CloudSpaceResponse(
                    status = response.code,
                    body = response.body.string(),
                    headers = headers,
                )
            }
        }

    companion object {
        private val EMPTY_REQUEST_BODY = ByteArray(0).toRequestBody(null)

        /** JSON/分片请求客户端（2026-11-18：共享全局连接池/Dispatcher，仅放宽读/写）。 */
        fun defaultClient(): OkHttpClient = NetworkClients.cloudSpaceClient()

        /** 文件下载客户端：读超时 10 分钟、不设 callTimeout（共享连接池）。 */
        fun downloadClient(): OkHttpClient = NetworkClients.downloadClient()
    }
}
