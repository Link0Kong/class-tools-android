package com.furina.classtools.data.network

import kotlinx.coroutines.async
import com.furina.classtools.FakeTransport
import com.furina.classtools.testApiClient
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiClientTest {

    @Test
    fun lateResponsesAndRetriesCannotInvalidateTheNextGeneration() = kotlinx.coroutines.test.runTest {
        for (status in listOf(401, 403, 503)) {
            val response = kotlinx.coroutines.CompletableDeferred<ApiResponse>()
            val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
            var owner = ApiIdentity("same-token", 1)
            var callbacks = 0
            val transport = FakeTransport { entered.complete(Unit); response.await() }
            val api = ApiClient("https://qa.invalid", "qa", transport = transport,
                identityProvider = { owner }, invalidateIdentity = { callbacks++ }, retryDelayMs = 0, retryJitterMs = 0)
            val old = async { api.get("userdata") }
            entered.await()
            owner = owner.copy(generation = 2)
            response.complete(ApiResponse(status, ""))
            assertTrue(old.await() is ApiResult.Failure)
            assertEquals(0, callbacks)
            assertEquals(1, transport.requests.size)
            transport.responder = { ApiResponse(401, """{"code":"PGRST301"}""") }
            api.get("current")
            assertEquals(1, callbacks)
        }
    }

    @Test
    fun `GET 带契约要求的请求头`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val api = testApiClient(transport)

        val result = api.get("homework?select=*&archived=eq.false")

        assertTrue(result is ApiResult.Success)
        val request = transport.lastRequest()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("Bearer test-publishable-key", request.headers["Authorization"])
        assertEquals("test-token", request.headers["X-Class-Token"])
        assertEquals("no-store", request.headers["Cache-Control"])
        assertEquals("application/json", request.headers["Content-Type"])
        assertEquals("application/json", request.headers["Accept"])
        assertEquals(
            "https://example.invalid/v1/rdb/rest/homework?select=*&archived=eq.false",
            request.url,
        )
    }

    @Test
    fun `无 token 时不发 X-Class-Token 头`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val api = testApiClient(transport, token = null)

        api.get("marks?select=*")

        assertNull(transport.lastRequest().headers["X-Class-Token"])
    }

    @Test
    fun `免身份请求不带 token（class_verify）`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "{}") }
        val api = testApiClient(transport, token = "should-not-be-sent")

        api.post("rpc/class_verify", "{}", authenticated = false)

        assertNull(transport.lastRequest().headers["X-Class-Token"])
    }

    @Test
    fun `baseUrl 结尾斜杠缺失时自动补齐`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val api = testApiClient(transport, baseUrl = "https://example.invalid/v1/rdb/rest")

        api.get("notices")

        assertEquals("https://example.invalid/v1/rdb/rest/notices", transport.lastRequest().url)
    }

    @Test
    fun `未配置 baseUrl 或 key 时直接失败且不发请求`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val api = testApiClient(transport, publishableKey = "")

        val result = api.get("homework")

        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is ApiError.Config)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `GET 网络失败自动重试一次`() = runTest {
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
    fun `写请求网络失败不重试`() = runTest {
        val transport = FakeTransport { throw IOException("boom") }
        val api = testApiClient(transport)

        val result = api.post("marks", "[]")

        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is ApiError.Network)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `401 映射 TokenInvalid 并回调`() = runTest {
        var invalidated = false
        val transport = FakeTransport { ApiResponse(401, """{"code":"PGRST301"}""") }
        val api = testApiClient(transport, onTokenInvalid = { invalidated = true })

        val result = api.get("userdata?select=*")

        assertTrue(result is ApiResult.Failure)
        val error = (result as ApiResult.Failure).error
        assertTrue(error is ApiError.TokenInvalid)
        assertEquals(401, (error as ApiError.TokenInvalid).status)
        assertTrue(invalidated)
        // 401 不重试
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `403 同样映射 TokenInvalid`() = runTest {
        val transport = FakeTransport { ApiResponse(403, "") }
        val api = testApiClient(transport)

        val result = api.patch("fitrecs?id=eq.x", "{}")

        assertTrue((result as ApiResult.Failure).error is ApiError.TokenInvalid)
    }

    @Test
    fun `PostgREST 错误体被解析`() = runTest {
        val body = """{"code":"DATABASE_42501","message":"permission denied for table marks","requestId":"req-1"}"""
        val transport = FakeTransport { ApiResponse(500, body) }
        val api = testApiClient(transport)

        val result = api.getJson("marks")

        val error = (result as ApiResult.Failure).error as ApiError.Http
        assertEquals(500, error.status)
        assertEquals("DATABASE_42501", error.postgrestCode)
        assertEquals("permission denied for table marks", error.serverMessage)
    }

    @Test
    fun `非 JSON 错误体不崩溃`() = runTest {
        val transport = FakeTransport { ApiResponse(502, "bad gateway") }
        val api = testApiClient(transport)

        val error = (api.get("marks") as ApiResult.Failure).error as ApiError.Http
        assertEquals(502, error.status)
        assertEquals("", error.postgrestCode)
        assertTrue(error.displayMessage.isNotBlank())
    }

    @Test
    fun `getJson 解析成功与坏 JSON 失败`() = runTest {
        var payload = """{"ok":true}"""
        val transport = FakeTransport { ApiResponse(200, payload) }
        val api = testApiClient(transport)

        val ok = api.getJson("rpc/class_verify")
        assertTrue(ok is ApiResult.Success)
        assertEquals("""{"ok":true}""", (ok as ApiResult.Success).value.toString())

        payload = "{invalid-json"
        val bad = api.getJson("rpc/class_verify")
        assertTrue((bad as ApiResult.Failure).error is ApiError.Parse)
    }

    @Test
    fun `POST 带 Prefer 头且解析 Postgrest 错误为 null 安全`() = runTest {
        val transport = FakeTransport { ApiResponse(201, "[]") }
        val api = testApiClient(transport)

        api.post("marks", "[]", prefer = "return=representation,resolution=merge-duplicates")

        assertEquals(
            "return=representation,resolution=merge-duplicates",
            transport.lastRequest().headers["Prefer"],
        )
        assertNull(ApiClient.parsePostgrestError(NetJson, "[1,2,3]"))
        assertFalse(transport.requests.isEmpty())
    }
}
