package com.furina.classtools.features.feedback

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpMethod
import com.furina.classtools.testApiClient
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackRepositoryTest {

    private val now: Long = 1_700_000_000_000L
    private val sid: String = "3260707001"

    private fun repository(transport: FakeTransport, sidProvider: () -> String? = { sid }): DefaultFeedbackRepository =
        DefaultFeedbackRepository(
            api = testApiClient(transport),
            currentSid = sidProvider,
            clock = { now },
            randomSuffix = { "abc123" },
        )

    @Test
    fun `加载解析反馈数组并按 ts 原样接受`() = runTest {
        val body = """
            [{"id":"f_1","content":"建议 A","contact":"","acc":"$sid","done":false,"ts":1700000000001},
             {"id":"f_2","content":"建议 B","contact":"","acc":"$sid","done":true,"ts":1700000000002}]
        """.trimIndent()
        val transport = FakeTransport { ApiResponse(200, body) }
        val result = repository(transport).load(force = true)

        val rows = (result as FeedbackLoadResult.Success).rows
        assertEquals(2, rows.size)
        assertEquals("建议 A", rows[0].content)
        assertFalse(rows[0].done)
        assertTrue(rows[1].done)
        assertEquals("feedback?select=*&order=ts.desc", transport.lastRequest().url.substringAfterLast('/'))
        assertEquals("test-token", transport.lastRequest().headers["X-Class-Token"])
    }

    @Test
    fun `加载失败时降级缓存`() = runTest {
        var fail = false
        val body = """[{"id":"f_1","content":"缓存","acc":"$sid","done":false,"ts":1}]"""
        val transport = FakeTransport {
            if (fail) throw IOException("offline") else ApiResponse(200, body)
        }
        val repository = repository(transport)
        repository.load(force = true)
        fail = true
        val result = repository.load(force = true) as FeedbackLoadResult.Success
        assertTrue(result.fromCache)
        assertEquals("缓存", result.rows.single().content)
    }

    @Test
    fun `无身份且无缓存时返回失败文案`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val result = repository(transport, sidProvider = { null }).load(force = true)
        assertEquals("请先完成身份验证", (result as FeedbackLoadResult.Failure).message)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `提交成功写入数组 body 且缓存置顶`() = runTest {
        val transport = FakeTransport { request ->
            if (request.method == HttpMethod.POST) ApiResponse(201, "[]") else ApiResponse(200, "[]")
        }
        val repository = repository(transport)
        val result = repository.submit("  希望增加提醒  ") as FeedbackSubmitResult.Success
        assertEquals("希望增加提醒", result.row.content)
        assertEquals(sid, result.row.acc)
        assertEquals("", result.row.contact)
        assertFalse(result.row.done)
        assertEquals(now, result.row.ts)
        assertEquals(FeedbackRules.newId(now, "abc123"), result.row.id)

        val request = transport.lastRequest()
        assertEquals(HttpMethod.POST, request.method)
        assertTrue(request.url.endsWith("/feedback"))
        val body = request.body.orEmpty()
        assertTrue(body.contains("希望增加提醒"))
        assertTrue(body.contains("\"contact\":\"\""))
        assertTrue(body.contains("\"acc\":\"$sid\""))
        assertTrue(body.contains("\"done\":false"))
        assertEquals(1, repository.cached().size)
    }

    @Test
    fun `提交空内容与超长内容被拒绝且不发请求`() = runTest {
        val transport = FakeTransport { ApiResponse(201, "[]") }
        val repository = repository(transport)
        assertEquals(
            "写点反馈内容",
            (repository.submit("   ") as FeedbackSubmitResult.Failure).message,
        )
        val longText = "字".repeat(FeedbackRules.MAX_CONTENT_LENGTH + 1)
        assertEquals(
            "反馈内容最多 500 字",
            (repository.submit(longText) as FeedbackSubmitResult.Failure).message,
        )
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `提交网络失败返回可读文案`() = runTest {
        val transport = FakeTransport { ApiResponse(500, """{"message":"server error"}""") }
        val result = repository(transport).submit("建议") as FeedbackSubmitResult.Failure
        assertTrue(result.message.isNotBlank())
    }

    @Test
    fun `管理员标记已处理走 PATCH 并更新缓存`() = runTest {
        var patchCalled = false
        val body = """[{"id":"f_1","content":"建议","acc":"$sid","done":false,"ts":1}]"""
        val transport = FakeTransport { request ->
            when {
                request.method == HttpMethod.PATCH -> {
                    patchCalled = true
                    ApiResponse(200, "[]")
                }

                else -> ApiResponse(200, body)
            }
        }
        val repository = repository(transport)
        repository.load(force = true)
        assertEquals(FeedbackDoneResult.Success, repository.markDone("f_1"))
        assertTrue(patchCalled)
        val request = transport.lastRequest()
        assertEquals(HttpMethod.PATCH, request.method)
        assertTrue(request.url.contains("feedback?id=eq.f_1"))
        assertEquals("""{"done":true}""", request.body)
        assertTrue(repository.cached().single().done)
    }

    @Test
    fun `PATCH 失败回退 POST upsert`() = runTest {
        val body = """[{"id":"f_1","content":"建议","acc":"$sid","done":false,"ts":1}]"""
        val methods = mutableListOf<String>()
        val transport = FakeTransport { request ->
            methods += request.method.name
            when (request.method.wire) {
                "PATCH" -> ApiResponse(500, "{}")
                "POST" -> ApiResponse(201, "[]")
                else -> ApiResponse(200, body)
            }
        }
        val repository = repository(transport)
        repository.load(force = true)
        assertEquals(FeedbackDoneResult.Success, repository.markDone("f_1"))
        assertEquals(listOf("GET", "PATCH", "POST"), methods)
    }

    @Test
    fun `TTL 内重复加载走缓存不发请求`() = runTest {
        val body = """[{"id":"f_1","content":"建议","acc":"$sid","done":false,"ts":1}]"""
        val transport = FakeTransport { ApiResponse(200, body) }
        val repository = repository(transport)
        repository.load(force = true)
        val second = repository.load(force = false) as FeedbackLoadResult.Success
        assertTrue(second.fromCache)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `坏 JSON 与坏行容错`() {
        assertNull(DefaultFeedbackRepository.parseRows(com.furina.classtools.data.network.NetJson, "not-json"))
        assertNull(DefaultFeedbackRepository.parseRows(com.furina.classtools.data.network.NetJson, "{}"))
        val rows = DefaultFeedbackRepository.parseRows(
            com.furina.classtools.data.network.NetJson,
            """[{"content":"无 id 行"},{"id":"ok","content":"有效"}]""",
        )
        assertEquals(1, rows?.size)
        assertEquals("ok", rows?.single()?.id)
    }

    @Test
    fun `提交 body 使用数组并固定 contact 空串`() {
        val row = FeedbackRow(id = "f_x", content = "内容", acc = "111", ts = 42L)
        val body = DefaultFeedbackRepository.buildSubmitBody(row)
        assertTrue(body.startsWith("["))
        assertTrue(body.contains("\"id\":\"f_x\""))
        assertTrue(body.contains("\"contact\":\"\""))
        assertTrue(body.contains("\"ts\":42"))
    }
}
