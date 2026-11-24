package com.furina.classtools.data.scan

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrLoginRepositoryTest {

    private val baseUrl = "http://example.invalid/account"
    private val ticket = "0123456789abcdef0123456789abcdef"

    private fun repo(transport: FakeTransport): QrLoginRepository =
        QrLoginRepository(transport = transport, baseUrl = baseUrl)

    @Test
    fun `authorize posts ticket with class token and via app`() = runTest {
        val transport = FakeTransport { ApiResponse(200, """{"ok":true,"name":"测试账号"}""") }
        val result = repo(transport).authorize(ticket, harmony = false, classToken = "class-token-1")

        assertTrue(result.ok)
        assertEquals("测试账号", result.name)
        val request = transport.lastRequest()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("$baseUrl/qr/authorize", request.url)
        assertEquals("class-token-1", request.headers["X-Class-Token"])
        assertEquals("application/json", request.headers["Accept"])
        val body = Json.parseToJsonElement(request.body!!).jsonObject
        assertEquals(ticket, body["t"]?.jsonPrimitive?.content)
        assertEquals("any", body["p"]?.jsonPrimitive?.content)
        assertEquals("app", body["via"]?.jsonPrimitive?.content)
    }

    @Test
    fun `harmony flag is sent as p harmony`() = runTest {
        val transport = FakeTransport { ApiResponse(200, """{"ok":true,"name":"测试账号"}""") }
        repo(transport).authorize(ticket, harmony = true, classToken = "t")
        val body = Json.parseToJsonElement(transport.lastRequest().body!!).jsonObject
        assertEquals("harmony", body["p"]?.jsonPrimitive?.content)
    }

    @Test
    fun `expired ticket returns server message`() = runTest {
        val transport = FakeTransport {
            ApiResponse(200, """{"ok":false,"code":"ticket_expired","message":"二维码已过期，请在电脑上刷新后重扫"}""")
        }
        val result = repo(transport).authorize(ticket, harmony = false, classToken = "t")
        assertFalse(result.ok)
        assertEquals("ticket_expired", result.code)
        assertTrue(result.message.contains("过期"))
    }

    @Test
    fun `401 maps to session expired`() = runTest {
        val result = repo(FakeTransport { ApiResponse(401, """{"ok":false,"code":"unauthorized"}""") })
            .authorize(ticket, harmony = false, classToken = "t")
        assertFalse(result.ok)
        assertEquals("unauthorized", result.code)
        assertTrue(result.message.contains("登录已失效"))
    }

    @Test
    fun `429 maps to retry hint`() = runTest {
        val result = repo(FakeTransport { ApiResponse(429, """{"ok":false,"code":"rate_limited"}""") })
            .authorize(ticket, harmony = false, classToken = "t")
        assertFalse(result.ok)
        assertEquals("rate_limited", result.code)
    }

    @Test
    fun `invalid ticket does not hit network`() = runTest {
        val transport = FakeTransport()
        val result = repo(transport).authorize("not-a-ticket", harmony = false, classToken = "t")
        assertFalse(result.ok)
        assertEquals("bad_ticket", result.code)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `empty class token does not hit network`() = runTest {
        val transport = FakeTransport()
        val result = repo(transport).authorize(ticket, harmony = false, classToken = "  ")
        assertFalse(result.ok)
        assertEquals("unauthorized", result.code)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `network failure is mapped`() = runTest {
        val transport = FakeTransport { throw java.io.IOException("offline") }
        val result = repo(transport).authorize(ticket, harmony = false, classToken = "t")
        assertFalse(result.ok)
        assertEquals("network", result.code)
        assertNull(result.name.ifBlank { null })
    }
}
