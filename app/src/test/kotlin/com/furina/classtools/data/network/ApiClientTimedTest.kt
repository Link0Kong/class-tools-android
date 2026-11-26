package com.furina.classtools.data.network

import com.furina.classtools.FakeTransport
import com.furina.classtools.testApiClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 诊断计时请求单测（`getTimed`：网关延迟 + HTTP Date 头 ↔ 本机时钟偏差来源）。
 */
class ApiClientTimedTest {

    @Test
    fun getTimed_returnsBodyLatencyAndServerEpoch() = runTest {
        val transport = FakeTransport {
            ApiResponse(
                status = 200,
                body = """[{"id":"cloudbase"}]""",
                headers = mapOf("date" to "Sun, 08 Nov 2026 05:31:00 GMT"),
            )
        }
        val result = testApiClient(transport).getTimed("service_health?select=id&limit=1")
        assertTrue(result is ApiResult.Success)
        val timed = (result as ApiResult.Success).value
        assertTrue(timed.latencyMs >= 0)
        assertEquals("""[{"id":"cloudbase"}]""", timed.body)
        assertEquals(java.time.Instant.parse("2026-11-08T05:31:00Z").toEpochMilli(), timed.serverEpochMs)
        assertEquals("test-token", transport.lastRequest().headers["X-Class-Token"])
        assertTrue(transport.lastRequest().headers["Authorization"].orEmpty().startsWith("Bearer "))
    }

    @Test
    fun getTimed_noDateHeader_serverEpochNull() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val result = testApiClient(transport).getTimed("x")
        assertNull((result as ApiResult.Success).value.serverEpochMs)
    }

    @Test
    fun getTimed_networkFailure_doesNotRetry() = runTest {
        val transport = FakeTransport { throw java.io.IOException("offline") }
        val result = testApiClient(transport).getTimed("x")
        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is ApiError.Network)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun getTimed_permissionProbe_keepsSession() = runTest {
        var invalidated = false
        val transport = FakeTransport { ApiResponse(401, """{"code":"DATABASE_42501"}""") }
        val result = testApiClient(transport, onTokenInvalid = { invalidated = true })
            .getTimed("x")
        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is ApiError.Http)
        assertTrue(!invalidated)
    }

    @Test
    fun headerEpochMs_handlesCaseAndBadValues() {
        val epoch = ApiClient.headerEpochMs(mapOf("DATE" to "Sun, 08 Nov 2026 05:31:00 GMT"))
        assertEquals(java.time.Instant.parse("2026-11-08T05:31:00Z").toEpochMilli(), epoch)
        assertNull(ApiClient.headerEpochMs(emptyMap()))
        assertNull(ApiClient.headerEpochMs(mapOf("Date" to "not-a-date")))
    }

    @Test
    fun apiResponse_defaultHeadersEmpty() {
        assertEquals(emptyMap<String, String>(), ApiResponse(200, "[]").headers)
    }
}
