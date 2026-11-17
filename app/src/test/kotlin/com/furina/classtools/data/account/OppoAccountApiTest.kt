package com.furina.classtools.data.account

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiRequest
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpMethod
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ECS 账号接口客户端单测：URL/方法/headers/body 构造 + 响应解析 + 错误降级。
 * 全程 FakeTransport（不发真实网络请求），覆盖登录/绑定/列表/解绑/健康检查。
 */
class OppoAccountApiTest {

    private val baseUrl = "http://example.invalid/account"

    private fun api(transport: FakeTransport, timeoutMs: Long = 15_000): EcsOppoAccountApi =
        EcsOppoAccountApi(
            transport = transport,
            baseUrl = baseUrl,
            requestTimeoutMs = timeoutMs,
        )

    // ------------------------------------------------------------ login

    @Test
    fun `login posts code to account endpoint with json headers and no class token`() = runTest {
        val transport = FakeTransport { ApiResponse(200, """{"ok":true,"token":"t","sid":"3260799999","name":"测试账号","nickname":"n"}""") }
        val result = api(transport).login("  auth-code-1  ")

        assertEquals(OppoLoginResult.Success(token = "t", sid = "3260799999", name = "测试账号", nickname = "n"), result)
        val request = transport.lastRequest()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("$baseUrl/oppo/login", request.url)
        assertEquals("application/json", request.headers["Accept"])
        assertEquals("application/json; charset=utf-8", request.headers["Content-Type"])
        assertNull(request.headers["X-Class-Token"])
        val body = Json.parseToJsonElement(request.body!!).jsonObject
        assertEquals("auth-code-1", body["code"]?.jsonPrimitive?.content)
    }

    @Test
    fun `login unbound returns guidance message`() = runTest {
        val transport = FakeTransport {
            ApiResponse(
                200,
                """{"ok":false,"code":"unbound","message":"该 OPPO 账号未绑定学号，请先用学号+姓名登录后在『我的』绑定"}""",
            )
        }
        val result = api(transport).login("code")
        assertTrue(result is OppoLoginResult.Unbound)
        assertTrue((result as OppoLoginResult.Unbound).message.contains("绑定"))
    }

    @Test
    fun `login unbound without message uses fallback`() = runTest {
        val transport = FakeTransport { ApiResponse(200, """{"ok":false,"code":"unbound"}""") }
        val result = api(transport).login("code")
        assertEquals(OppoLoginResult.Unbound(OppoAccountText.UNBOUND_FALLBACK), result)
    }

    @Test
    fun `login server error code is preserved`() = runTest {
        val transport = FakeTransport { ApiResponse(200, """{"ok":false,"code":"server_error","message":"服务异常"}""") }
        val result = api(transport).login("code")
        assertEquals(OppoLoginResult.Failure("server_error", "服务异常"), result)
    }

    @Test
    fun `login oppo exchange failed 提示账号服务未开通或凭证不匹配`() = runTest {
        val transport = FakeTransport {
            ApiResponse(200, """{"ok":false,"code":"oppo_exchange_failed","message":"invalid_client"}""")
        }
        val result = api(transport).login("code")
        assertTrue(result is OppoLoginResult.Failure)
        result as OppoLoginResult.Failure
        assertEquals(OppoAccountErrorCode.EXCHANGE_FAILED, result.code)
        assertEquals(
            "OPPO 账号服务未开通或凭证不匹配（invalid_client），请联系管理员",
            result.message,
        )
    }

    @Test
    fun `login 非 2xx oppo exchange failed 也可诊断`() = runTest {
        val transport = FakeTransport {
            ApiResponse(502, """{"ok":false,"code":"oppo_exchange_failed","message":"invalid_client"}""")
        }
        val result = api(transport).login("code")
        assertEquals(OppoAccountErrorCode.EXCHANGE_FAILED, (result as OppoLoginResult.Failure).code)
        assertTrue(result.message.contains("invalid_client"))
    }

    @Test
    fun `login invalid auth code is rejected locally without request`() = runTest {
        val transport = FakeTransport()
        val result = api(transport).login("bad code")
        assertTrue(result is OppoLoginResult.Failure)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `login invalid json degrades to parse failure`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "not-json") }
        val result = api(transport).login("code")
        assertTrue(result is OppoLoginResult.Failure)
        assertEquals(OppoAccountErrorCode.PARSE, (result as OppoLoginResult.Failure).code)
    }

    @Test
    fun `login success without sid is rejected`() = runTest {
        val transport = FakeTransport { ApiResponse(200, """{"ok":true,"token":"t"}""") }
        val result = api(transport).login("code")
        assertEquals(OppoAccountErrorCode.PARSE, (result as OppoLoginResult.Failure).code)
    }

    @Test
    fun `login non-2xx maps server message`() = runTest {
        val transport = FakeTransport { ApiResponse(500, """{"ok":false,"message":"服务暂时不可用"}""") }
        val result = api(transport).login("code")
        assertEquals("服务暂时不可用", (result as OppoLoginResult.Failure).message)
        assertEquals("http_500", result.code)
    }

    @Test
    fun `login network exception degrades to network failure`() = runTest {
        val transport = FakeTransport { throw java.io.IOException("boom") }
        val result = api(transport).login("code")
        assertEquals(OppoAccountErrorCode.NETWORK, (result as OppoLoginResult.Failure).code)
    }

    @Test
    fun `login timeout degrades to timeout failure`() = runTest {
        val transport = FakeTransport {
            delay(60_000)
            ApiResponse(200, "{}")
        }
        val deferred = async { api(transport).login("code") }
        advanceTimeBy(16_000)
        val result = deferred.await()
        assertEquals(OppoAccountErrorCode.TIMEOUT, (result as OppoLoginResult.Failure).code)
    }

    // ------------------------------------------------------------ bind

    @Test
    fun `bind posts code with class token and parses binding`() = runTest {
        val transport = FakeTransport {
            ApiResponse(
                200,
                """{"ok":true,"binding":{"id":7,"nickname":"小O","createdAt":"2026-11-17T12:00:00+08:00"},"count":1}""",
            )
        }
        val result = api(transport).bind("auth-code", "class-token-abc")
        assertEquals(
            OppoBindResult.Success(
                binding = OppoBinding(id = 7, nickname = "小O", createdAt = "2026-11-17T12:00:00+08:00"),
                count = 1,
            ),
            result,
        )
        val request = transport.lastRequest()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("$baseUrl/oppo/bind", request.url)
        assertEquals("class-token-abc", request.headers["X-Class-Token"])
        assertEquals("auth-code", Json.parseToJsonElement(request.body!!).jsonObject["code"]?.jsonPrimitive?.content)
    }

    @Test
    fun `bind limit error maps to Limit`() = runTest {
        val transport = FakeTransport {
            ApiResponse(200, """{"ok":false,"code":"limit","message":"一个学号最多绑定3个OPPO账号"}""")
        }
        val result = api(transport).bind("auth-code", "token")
        assertEquals(OppoBindResult.Limit("一个学号最多绑定3个OPPO账号"), result)
    }

    @Test
    fun `bind bound else maps to BoundElsewhere`() = runTest {
        val transport = FakeTransport { ApiResponse(200, """{"ok":false,"code":"openid_bound_else"}""") }
        val result = api(transport).bind("auth-code", "token")
        assertEquals(OppoBindResult.BoundElsewhere("该 OPPO 账号已绑定其他学号"), result)
    }

    @Test
    fun `bind oppo exchange failed 提示可诊断`() = runTest {
        val transport = FakeTransport {
            ApiResponse(200, """{"ok":false,"code":"oppo_exchange_failed","message":"invalid_client"}""")
        }
        val result = api(transport).bind("auth-code", "token") as OppoBindResult.Failure
        assertEquals(OppoAccountErrorCode.EXCHANGE_FAILED, result.code)
        assertTrue(result.message.contains("invalid_client"))
    }

    @Test
    fun `bind success without binding is rejected`() = runTest {
        val transport = FakeTransport { ApiResponse(200, """{"ok":true,"count":1}""") }
        val result = api(transport).bind("auth-code", "token")
        assertEquals(OppoAccountErrorCode.PARSE, (result as OppoBindResult.Failure).code)
    }

    @Test
    fun `bind requires class token before request`() = runTest {
        val transport = FakeTransport()
        val result = api(transport).bind("auth-code", "   ")
        assertEquals(OppoAccountErrorCode.UNAUTHORIZED, (result as OppoBindResult.Failure).code)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `bind 401 maps unauthorized`() = runTest {
        val transport = FakeTransport { ApiResponse(401, """{"ok":false,"code":"unauthorized"}""") }
        val result = api(transport).bind("auth-code", "token")
        assertEquals(OppoAccountErrorCode.UNAUTHORIZED, (result as OppoBindResult.Failure).code)
    }

    // ------------------------------------------------------------ bindings

    @Test
    fun `bindings gets list with token header`() = runTest {
        val transport = FakeTransport {
            ApiResponse(
                200,
                """{"ok":true,"max":3,"count":2,"items":[
                    {"id":1,"nickname":"A","createdAt":"2026-11-16T10:00:00+08:00"},
                    {"id":2,"nickname":"B","createdAt":"2026-11-17T10:00:00+08:00"}
                ]}""",
            )
        }
        val result = api(transport).bindings("class-token")
        assertTrue(result is OppoBindingsResult.Success)
        val page = (result as OppoBindingsResult.Success).page
        assertEquals(3, page.max)
        assertEquals(2, page.count)
        assertEquals(listOf(1L, 2L), page.items.map { it.id })
        assertEquals("A", page.items[0].nickname)
        val request = transport.lastRequest()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("$baseUrl/oppo/bindings", request.url)
        assertEquals("class-token", request.headers["X-Class-Token"])
        assertNull(request.body)
    }

    @Test
    fun `bindings skips malformed items and clamps count`() = runTest {
        val transport = FakeTransport {
            ApiResponse(
                200,
                """{"ok":true,"max":3,"count":9,"items":[
                    {"id":1,"nickname":"A"},
                    {"id":"bad","nickname":"B"},
                    {"nickname":"C"}
                ]}""",
            )
        }
        val page = (api(transport).bindings("token") as OppoBindingsResult.Success).page
        assertEquals(1, page.items.size)
        assertEquals(3, page.count)
        assertTrue(page.isFull)
    }

    @Test
    fun `bindings failure ok false returns error`() = runTest {
        val transport = FakeTransport { ApiResponse(200, """{"ok":false,"code":"db_down","message":"读取失败"}""") }
        val result = api(transport).bindings("token")
        assertEquals(OppoBindingsResult.Failure("db_down", "读取失败"), result)
    }

    @Test
    fun `bindings invalid json degrades to parse failure`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "<html>") }
        assertEquals(OppoAccountErrorCode.PARSE, (api(transport).bindings("token") as OppoBindingsResult.Failure).code)
    }

    @Test
    fun `bindings blank token rejected without request`() = runTest {
        val transport = FakeTransport()
        assertEquals(
            OppoAccountErrorCode.UNAUTHORIZED,
            (api(transport).bindings("") as OppoBindingsResult.Failure).code,
        )
        assertEquals(0, transport.requests.size)
    }

    // ------------------------------------------------------------ unbind

    @Test
    fun `unbind posts id with token and parses count`() = runTest {
        val transport = FakeTransport { ApiResponse(200, """{"ok":true,"count":1}""") }
        val result = api(transport).unbind(5, "class-token")
        assertEquals(OppoUnbindResult.Success(1), result)
        val request = transport.lastRequest()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("$baseUrl/oppo/unbind", request.url)
        assertEquals("class-token", request.headers["X-Class-Token"])
        assertEquals("5", Json.parseToJsonElement(request.body!!).jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `unbind invalid id rejected locally`() = runTest {
        val transport = FakeTransport()
        val result = api(transport).unbind(0, "token")
        assertEquals(OppoAccountErrorCode.PARSE, (result as OppoUnbindResult.Failure).code)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `unbind failure maps message`() = runTest {
        val transport = FakeTransport { ApiResponse(200, """{"ok":false,"code":"not_found","message":"记录不存在"}""") }
        assertEquals(OppoUnbindResult.Failure("not_found", "记录不存在"), api(transport).unbind(5, "token"))
    }

    // ------------------------------------------------------------ health / base url

    @Test
    fun `health parses configured flag`() = runTest {
        val transport = FakeTransport { ApiResponse(200, """{"ok":true,"configured":false,"version":"1.0"}""") }
        val health = api(transport).health()
        assertTrue(health.ok)
        assertFalse(health.configured)
        assertEquals("1.0", health.version)
        assertEquals("$baseUrl/__health", transport.lastRequest().url)
    }

    @Test
    fun `health failure degrades`() = runTest {
        val transport = FakeTransport { throw java.io.IOException("down") }
        assertEquals(OppoHealthResult(ok = false, configured = false), api(transport).health())
    }

    @Test
    fun `base url trailing slash normalized and blank falls back`() {
        val transport = FakeTransport()
        assertEquals(
            "${OppoAccountConfig.DEFAULT_BASE_URL}/oppo/login",
            EcsOppoAccountApi(transport, baseUrl = "  ").let { it.baseUrl + "/oppo/login" },
        )
        assertEquals("http://x/account", EcsOppoAccountApi(transport, baseUrl = "http://x/account/").baseUrl)
    }

    @Test
    fun `default timeout constant is realistic`() {
        assertTrue(EcsOppoAccountApi.DEFAULT_TIMEOUT_MS in 5_000..60_000)
    }

    @Test
    fun `requests never include publishable cloud key`() = runTest {
        val transport = FakeTransport { ApiResponse(200, """{"ok":true,"token":"t","sid":"1"}""") }
        api(transport).login("code")
        val request = transport.lastRequest()
        assertFalse(request.headers.keys.any { it.equals("Authorization", ignoreCase = true) })
    }
}
