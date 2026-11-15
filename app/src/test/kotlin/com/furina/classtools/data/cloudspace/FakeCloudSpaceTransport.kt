package com.furina.classtools.data.cloudspace

/**
 * 云空间单测假传输层：记录全部请求，按 [responder] 返回；可在 responder 里抛异常模拟网络失败。
 *
 * 与 `com.furina.classtools.FakeTransport`（PostgREST 的 HttpTransport）同思路，但 drop 服务需要
 * DELETE / 原始字节分片，因此用本包独立的 [CloudSpaceTransport] 假实现，不引入 MockWebServer 依赖。
 */
class FakeCloudSpaceTransport(
    var responder: suspend (CloudSpaceRequest) -> CloudSpaceResponse = {
        CloudSpaceResponse(200, """{"ok":true}""")
    },
) : CloudSpaceTransport {

    val requests: MutableList<CloudSpaceRequest> = mutableListOf()

    override suspend fun execute(request: CloudSpaceRequest): CloudSpaceResponse {
        requests.add(request)
        return responder(request)
    }

    fun lastRequest(): CloudSpaceRequest = requests.last()

    /** 请求体 UTF-8 文本（原始分片请用 [lastRequest]`.body` 逐字节断言）。 */
    fun lastBodyText(): String = requests.last().body?.toString(Charsets.UTF_8).orEmpty()

    fun bodyText(index: Int): String = requests[index].body?.toString(Charsets.UTF_8).orEmpty()

    /** 去掉 `http://host/drop` 前缀后的 path（含 query），便于断言接口。 */
    fun pathOf(index: Int): String {
        val url = requests[index].url
        val marker = "/drop"
        val markerIndex = url.indexOf(marker)
        return if (markerIndex >= 0) url.substring(markerIndex + marker.length) else url
    }

    fun paths(): List<String> = requests.indices.map { pathOf(it) }
}

/** 测试用 CloudSpaceApi：显式 baseUrl/token（全为假值），不读 BuildConfig / 不访问网络。 */
fun testCloudSpaceApi(
    transport: CloudSpaceTransport,
    token: String? = "test-token",
    baseUrl: String = "http://example.invalid/drop/",
    onUnauthorized: () -> Unit = {},
): CloudSpaceApi = CloudSpaceApi(
    baseUrl = baseUrl,
    tokenProvider = { token },
    transport = transport,
    onUnauthorized = onUnauthorized,
)
