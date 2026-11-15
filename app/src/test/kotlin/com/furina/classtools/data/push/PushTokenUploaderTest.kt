package com.furina.classtools.data.push

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpMethod
import com.furina.classtools.testApiClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 推送 token 上传器单测：用假传输层断言 URL / headers / body（不发真实网络、不用真实 token）。
 *
 * 服务端契约（Lead 定义）：`POST {REST_BASE_URL}push_tokens?on_conflict=provider,token`
 * + `Prefer: resolution=merge-duplicates,return=minimal`。
 */
class PushTokenUploaderTest {

    @Test
    fun `上传构造正确 URL headers 与 body`() = runTest {
        val transport = FakeTransport { ApiResponse(201, "") }
        val uploader = ApiPushTokenUploader(
            testApiClient(transport, token = "test-class-token"),
        )
        val payload = PushTokenPayload.of("reg-abc", "OPPO PGZ110", "4.51.0")!!

        val result = uploader.upload(payload)

        assertEquals(PushUploadResult.Uploaded, result)
        assertEquals(1, transport.requests.size)
        val request = transport.lastRequest()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals(
            "https://example.invalid/v1/rdb/rest/push_tokens?on_conflict=provider,token",
            request.url,
        )
        // 身份/公开 key/内容类型/upsert 语义
        assertEquals("Bearer test-publishable-key", request.headers["Authorization"])
        assertEquals("test-class-token", request.headers["X-Class-Token"])
        assertEquals("application/json", request.headers["Content-Type"])
        assertEquals(
            "resolution=merge-duplicates,return=minimal",
            request.headers["Prefer"],
        )
        val body = Json.parseToJsonElement(request.body!!) as JsonObject
        assertEquals("oppo", body["provider"]!!.jsonPrimitive.content)
        assertEquals("reg-abc", body["token"]!!.jsonPrimitive.content)
        assertEquals("OPPO PGZ110", body["device"]!!.jsonPrimitive.content)
        assertEquals("4.51.0", body["app_version"]!!.jsonPrimitive.content)
        // 请求体不得携带班级 token / 学号等敏感信息
        assertFalse(request.body!!.contains("test-class-token"))
    }

    @Test
    fun `HTTP 失败返回 Failed 供降级日志`() = runTest {
        val transport = FakeTransport { ApiResponse(500, """{"message":"boom"}""") }
        val uploader = ApiPushTokenUploader(testApiClient(transport))
        val result = uploader.upload(PushTokenPayload.of("reg-abc", "device", "1.0")!!)
        assertTrue(result is PushUploadResult.Failed)
    }

    @Test
    fun `无 RegistrationID 时不上传 Skip 0 请求`() = runTest {
        val transport = FakeTransport { ApiResponse(201, "") }
        val uploader = ApiPushTokenUploader(testApiClient(transport))
        val result = uploader.upload(PushTokenPayload(token = " ", device = "d", appVersion = "1"))
        assertEquals(PushUploadResult.Skipped, result)
        assertTrue(transport.requests.isEmpty())
    }
}
