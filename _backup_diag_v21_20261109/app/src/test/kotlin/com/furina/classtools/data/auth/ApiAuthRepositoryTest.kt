package com.furina.classtools.data.auth

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.session.Session
import com.furina.classtools.testApiClient
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiAuthRepositoryTest {

    private val now: Long = 1_700_000_000_000L

    private fun repository(transport: FakeTransport): ApiAuthRepository =
        ApiAuthRepository(api = testApiClient(transport), now = { now })

    @Test
    fun `对象响应解析成功并构造 30 天会话`() = runTest {
        val body = """
            {"ok":true,"token":"fake-token-1","sid":"3260707001","name":"张三",
             "role":"副团支书","badge":"班委","is_admin":true}
        """.trimIndent()
        val transport = FakeTransport { ApiResponse(200, body) }
        val result = repository(transport).verify("3260707001", "张三")

        val session = (result as VerifyResult.Success).session
        assertEquals("fake-token-1", session.token)
        assertEquals("3260707001", session.sid)
        assertEquals("张三", session.name)
        assertEquals("副团支书", session.role)
        assertEquals("班委", session.badge)
        assertTrue(session.isAdmin)
        assertEquals(now, session.issuedAt)
        assertEquals(now + ApiAuthRepository.SESSION_TTL_MS, session.expiresAt)

        val request = transport.lastRequest()
        assertEquals(
            "https://example.invalid/v1/rdb/rest/rpc/class_verify",
            request.url,
        )
        assertTrue(request.body!!.contains("\"p_sid\":\"3260707001\""))
        assertTrue(request.body!!.contains("\"p_name\":\"张三\""))
        assertNull(request.headers["X-Class-Token"])
    }

    @Test
    fun `单元素数组响应同样解析`() = runTest {
        val body = """[{"ok":true,"token":"fake-token-2","sid":"3260707002","name":"李四"}]"""
        val transport = FakeTransport { ApiResponse(200, body) }

        val session = (repository(transport).verify("3260707002", "李四") as VerifyResult.Success).session

        assertEquals("fake-token-2", session.token)
        assertEquals("李四", session.name)
        assertEquals("", session.role)
    }

    @Test
    fun `ok false 使用云端 reason`() = runTest {
        val transport = FakeTransport {
            ApiResponse(200, """{"ok":false,"reason":"姓名或学号不在班级名单中"}""")
        }

        val result = repository(transport).verify("3260707003", "王五")

        val failure = result as VerifyResult.Failure
        assertEquals(VerifyErrorCode.INVALID_CREDENTIALS, failure.code)
        assertEquals("姓名或学号不在班级名单中", failure.message)
    }

    @Test
    fun `HTTP 4xx 映射为凭据错误`() = runTest {
        val transport = FakeTransport {
            ApiResponse(401, """{"code":"PGRST301","message":"no token"}""")
        }

        val failure = repository(transport).verify("3260707004", "赵六") as VerifyResult.Failure

        assertEquals(VerifyErrorCode.INVALID_CREDENTIALS, failure.code)
    }

    @Test
    fun `HTTP 5xx 映射为服务端错误`() = runTest {
        val transport = FakeTransport { ApiResponse(503, "") }

        val failure = repository(transport).verify("3260707005", "钱七") as VerifyResult.Failure

        assertEquals(VerifyErrorCode.SERVER_ERROR, failure.code)
    }

    @Test
    fun `网络异常映射为 network`() = runTest {
        val transport = FakeTransport { throw IOException("offline") }

        val failure = repository(transport).verify("3260707006", "孙八") as VerifyResult.Failure

        assertEquals(VerifyErrorCode.NETWORK, failure.code)
    }

    @Test
    fun `学号格式非法时不发请求`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "{}") }

        val failure = repository(transport).verify("123", "张三") as VerifyResult.Failure

        assertEquals(VerifyErrorCode.INVALID_INPUT, failure.code)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `空姓名不发请求`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "{}") }

        val failure = repository(transport).verify("3260707001", "   ") as VerifyResult.Failure

        assertEquals(VerifyErrorCode.INVALID_INPUT, failure.code)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `ok true 但缺 token 判失败`() = runTest {
        val transport = FakeTransport { ApiResponse(200, """{"ok":true}""") }

        val failure = repository(transport).verify("3260707001", "张三") as VerifyResult.Failure

        assertEquals(VerifyErrorCode.TOKEN_MISSING, failure.code)
    }

    @Test
    fun `坏 JSON 判解析错误`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "<html>oops</html>") }

        val failure = repository(transport).verify("3260707001", "张三") as VerifyResult.Failure

        assertEquals(VerifyErrorCode.PARSE_ERROR, failure.code)
    }

    @Test
    fun `空数组响应判解析错误`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }

        val failure = repository(transport).verify("3260707001", "张三") as VerifyResult.Failure

        assertEquals(VerifyErrorCode.PARSE_ERROR, failure.code)
    }

    @Test
    fun `token 缺失时保留服务端字段缺省值`() {
        // 直接测解析：sid/name 缺省回落到输入值。
        val repo = ApiAuthRepository(api = testApiClient(FakeTransport { ApiResponse(200, "{}") }), now = { now })
        val result = repo.parseVerifyResponse(
            raw = """{"token":"t","ok":true}""",
            fallbackSid = "3260707009",
            fallbackName = "周九",
            nowMs = now,
        )
        val session = (result as VerifyResult.Success).session
        assertEquals("3260707009", session.sid)
        assertEquals("周九", session.name)
        assertEquals(Session::class.java, session::class.java)
    }
}
