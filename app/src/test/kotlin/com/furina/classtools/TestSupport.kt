package com.furina.classtools

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiRequest
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpTransport
import com.furina.classtools.data.network.NetJson
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * 单测公共设施：假传输层 + 夹具加载 + 临时目录。
 *
 * 不引入 MockWebServer：所有网络交互都通过 [HttpTransport] 假实现，测试不发真实请求。
 * 夹具来源：`mp-class-tools-tests/_sync_fixtures/`（只读拷贝到 test resources，见 DATA_LAYER_IMPL.md）。
 */
object Fixtures {
    private val classLoader: ClassLoader =
        Fixtures::class.java.classLoader ?: error("no classloader")

    fun text(name: String): String {
        val stream = classLoader.getResourceAsStream("sync_fixtures/$name")
            ?: error("fixture not found: $name")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun jsonObject(name: String, json: Json = NetJson): JsonObject =
        json.parseToJsonElement(text(name)) as JsonObject
}

/** 假传输层：记录全部请求，按 [responder] 返回；可在 responder 里抛异常模拟网络失败。 */
class FakeTransport(
    var responder: suspend (ApiRequest) -> ApiResponse = { ApiResponse(200, "[]") },
) : HttpTransport {
    val requests: MutableList<ApiRequest> = mutableListOf()

    override suspend fun execute(request: ApiRequest): ApiResponse {
        requests.add(request)
        return responder(request)
    }

    fun lastRequest(): ApiRequest = requests.last()

    fun paths(): List<String> = requests.map { it.url }
}

/** 测试用 ApiClient：显式 baseUrl/publishableKey，不读 BuildConfig（token 全为假值）。 */
fun testApiClient(
    transport: HttpTransport,
    token: String? = "test-token",
    publishableKey: String = "test-publishable-key",
    baseUrl: String = "https://example.invalid/v1/rdb/rest/",
    onTokenInvalid: () -> Unit = {},
): ApiClient = ApiClient(
    baseUrl = baseUrl,
    publishableKey = publishableKey,
    tokenProvider = { token },
    transport = transport,
    onTokenInvalid = onTokenInvalid,
)

/** JUnit4 没有 @TempDir；用 JVM 临时目录（测试结束由系统清理）。 */
fun createTempDir(prefix: String = "ct-test-"): File =
    Files.createTempDirectory(prefix).toFile()
