package com.furina.classtools.data.update

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiResponse
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新检查器单测：URL fallback（主入口失败 → OSS 兜底）、HTTP/网络/坏 JSON 容错、
 * versionCode 判定、请求头与端点顺序（FakeTransport，不发真实网络）。
 */
class UpdateCheckerTest {

    private val availableJson = """
        {"versionCode":79,"versionName":"4.57.0-test1",
         "apkUrl":"http://121.196.47.2/android/classtools-4.57.0-test1.apk",
         "notes":"测试版本 1","force":false,"channel":"test"}
    """.trimIndent()

    private val sameVersionJson = availableJson.replace("\"versionCode\":79", "\"versionCode\":79")

    private fun checker(
        transport: FakeTransport,
        endpoints: List<String> = listOf(
            "http://primary.invalid/latest.json",
            "https://fallback.invalid/latest.json",
        ),
        currentVersionCode: Int = 79,
    ) = UpdateChecker(
        currentVersionCode = currentVersionCode,
        currentVersionName = "4.57.0-test1",
        transport = transport,
        endpoints = endpoints,
    )

    @Test
    fun `主入口成功 直接返回新版 不再请求兜底`() = runBlocking {
        val transport = FakeTransport { ApiResponse(200, availableJson) }
        val result = checker(transport, currentVersionCode = 78).check()

        assertTrue(result is UpdateCheckResult.Available)
        val info = (result as UpdateCheckResult.Available).info
        assertEquals(79, info.versionCode)
        assertEquals("4.57.0-test1", info.versionName)
        assertEquals(1, transport.requests.size)
        assertEquals("http://primary.invalid/latest.json", transport.requests[0].url)
        assertEquals("GET", transport.requests[0].method.wire)
        assertEquals("application/json", transport.requests[0].headers["Accept"])
    }

    @Test
    fun `versionCode 相同或更小 返回已是最新`() = runBlocking {
        val transport = FakeTransport { ApiResponse(200, sameVersionJson) }
        val result = checker(transport, currentVersionCode = 79).check()
        assertTrue(result is UpdateCheckResult.UpToDate)
        assertEquals(79, (result as UpdateCheckResult.UpToDate).currentVersionCode)
        assertEquals("4.57.0-test1", result.currentVersionName)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `主入口 404 时回退第二个 URL`() = runBlocking {
        val transport = FakeTransport { request ->
            if (request.url.contains("primary")) {
                ApiResponse(404, "not found")
            } else {
                ApiResponse(200, availableJson)
            }
        }
        val result = checker(transport, currentVersionCode = 78).check()

        assertTrue(result is UpdateCheckResult.Available)
        assertEquals(2, transport.requests.size)
        assertEquals("http://primary.invalid/latest.json", transport.requests[0].url)
        assertEquals("https://fallback.invalid/latest.json", transport.requests[1].url)
    }

    @Test
    fun `主入口网络异常时回退第二个 URL`() = runBlocking {
        val transport = FakeTransport { request ->
            if (request.url.contains("primary")) throw IOException("offline") else ApiResponse(200, availableJson)
        }
        val result = checker(transport, currentVersionCode = 78).check()
        assertTrue(result is UpdateCheckResult.Available)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `主入口坏 JSON 时回退第二个 URL`() = runBlocking {
        val transport = FakeTransport { request ->
            if (request.url.contains("primary")) ApiResponse(200, "<html>not json</html>") else ApiResponse(200, availableJson)
        }
        val result = checker(transport, currentVersionCode = 78).check()
        assertTrue(result is UpdateCheckResult.Available)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `两个端点都失败时返回失败 不抛异常`() = runBlocking {
        val transport = FakeTransport { ApiResponse(503, "down") }
        val result = checker(transport, currentVersionCode = 78).check()
        assertTrue(result is UpdateCheckResult.Failed)
        assertTrue((result as UpdateCheckResult.Failed).reason.contains("503"))
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `超时异常映射为检查失败`() = runBlocking {
        val transport = FakeTransport { throw SocketTimeoutException("timeout") }
        val result = checker(transport, currentVersionCode = 78).check()
        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals("连接超时", (result as UpdateCheckResult.Failed).reason)
    }

    @Test
    fun `空端点列表直接失败`() = runBlocking {
        val transport = FakeTransport { ApiResponse(200, availableJson) }
        val result = checker(transport, endpoints = emptyList(), currentVersionCode = 78).check()
        assertTrue(result is UpdateCheckResult.Failed)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `force 与 sha256 字段透传`() = runBlocking {
        val forcedJson = availableJson
            .replace("\"force\":false", "\"force\":true")
            .replace("\"channel\":\"test\"", "\"channel\":\"test\",\"sha256\":\"${"c".repeat(64)}\"")
        val transport = FakeTransport { ApiResponse(200, forcedJson) }
        val result = checker(transport, currentVersionCode = 78).check()
        val info = (result as UpdateCheckResult.Available).info
        assertTrue(info.force)
        assertEquals("c".repeat(64), info.sha256)
    }
}
