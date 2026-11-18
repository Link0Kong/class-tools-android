package com.furina.classtools.data.network

import com.furina.classtools.FakeTransport
import com.furina.classtools.testApiClient
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-11-18 网络体验优化（安卓）：
 * - 瞬时 502/503/504 重试（幂等只读请求）；写请求不重试；
 * - 相同 GET 并发在途合并（多屏/回前台同时拉同一张表只发 1 个请求）；
 * - 超时分级（timeoutMs 透传到传输层 / 登录档 20s）。
 */
class ApiClientNetOptimizationTest {

    @Test
    fun `GET 502 瞬时网关错误重试一次后成功（请求数 2）`() = runTest {
        var calls = 0
        val transport = FakeTransport {
            calls += 1
            if (calls == 1) ApiResponse(502, "{}") else ApiResponse(200, "[]")
        }
        val api = testApiClient(transport)

        val result = api.get("homework?select=*")

        assertTrue(result is ApiResult.Success)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `GET 连续 502 只重试一次（总 2 次尝试，不无限重试）`() = runTest {
        val transport = FakeTransport { ApiResponse(503, "{}") }
        val api = testApiClient(transport)

        val result = api.get("homework?select=*")

        assertTrue((result as ApiResult.Failure).error is ApiError.Http)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `写请求（POST）遇到 502 不重试（避免重复写入）`() = runTest {
        val transport = FakeTransport { ApiResponse(502, "{}") }
        val api = testApiClient(transport)

        val result = api.post("marks", "[]", prefer = "return=representation")

        assertEquals((result as ApiResult.Failure).error.let { (it as ApiError.Http).status }, 502)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `相同 GET 并发在途合并：5 个并发只发 1 个请求且都拿到结果`() = runTest {
        val transport = FakeTransport {
            delay(30)
            ApiResponse(200, """[{"id":"hw1"}]""")
        }
        val api = testApiClient(transport)

        val results = coroutineScope {
            (1..5).map { async { api.get("homework?select=*&archived=eq.false") } }.awaitAll()
        }

        assertEquals(1, transport.requests.size)
        assertTrue(results.all { it is ApiResult.Success })
        assertEquals("""[{"id":"hw1"}]""", (results.first() as ApiResult.Success).value)
    }

    @Test
    fun `在途合并可关闭（coalesce=false）时并发各自请求`() = runTest {
        val transport = FakeTransport {
            delay(30)
            ApiResponse(200, "[]")
        }
        val api = testApiClient(transport)

        coroutineScope {
            (1..2).map { async { api.get("homework", coalesce = false) } }.awaitAll()
        }

        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `在途合并只发生在请求进行中：请求结束后同样的 GET 会重新请求（不缓存旧数据）`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val api = testApiClient(transport)

        api.get("homework")
        api.get("homework")

        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `GET 网络失败重试一次（既有契约不回退）`() = runTest {
        var calls = 0
        val transport = FakeTransport {
            calls += 1
            if (calls == 1) throw IOException("boom") else ApiResponse(200, "[]")
        }
        val api = testApiClient(transport)

        val result = api.get("homework")

        assertTrue(result is ApiResult.Success)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `超时分级：timeoutMs 透传到传输层`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "{}") }
        val api = testApiClient(transport)

        api.get("rpc/class_verify", timeoutMs = 20_000)
        assertEquals(20_000L, transport.lastRequest().timeoutMs)

        api.post("rpc/class_verify", "{}", timeoutMs = 20_000)
        assertEquals(20_000L, transport.lastRequest().timeoutMs)

        api.get("homework")
        assertEquals(null, transport.lastRequest().timeoutMs)
    }
}
