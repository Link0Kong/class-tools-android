package com.furina.classtools.data.marks

import com.furina.classtools.FakeTransport
import com.furina.classtools.Fixtures
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpMethod
import com.furina.classtools.testApiClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarksRepositoryTest {

    private val sid = "3260707024"
    private val fixedNow = 1_700_000_000_000L

    private fun repository(
        transport: FakeTransport,
        local: MarksLocalStore = InMemoryMarksLocalStore(),
    ): DefaultMarksRepository = DefaultMarksRepository(
        api = testApiClient(transport),
        currentSid = { sid },
        local = local,
        clock = { fixedNow },
    )

    private fun postRow(transport: FakeTransport): JsonObject {
        val post = transport.requests.last { it.method == HttpMethod.POST }
        val array = Json.parseToJsonElement(post.body!!) as JsonArray
        return array[0] as JsonObject
    }

    @Test
    fun `refresh 解析夹具并生成快照`() = runTest {
        val transport = FakeTransport { ApiResponse(200, Fixtures.text("marks_ntdone_rows.json")) }
        val repo = repository(transport)

        val result = repo.refresh(force = true)

        assertTrue(result is com.furina.classtools.data.network.ApiResult.Success)
        val snapshot = repo.marks.value
        assertTrue(snapshot.isDone(MarkKind.NT_DONE, "auto_g_testA"))
        assertFalse(snapshot.isDone(MarkKind.NT_DONE, "auto_g_testB")) // val=false 视为未处理
        assertTrue(snapshot.isDone(MarkKind.HW, "hw_test1"))
        assertTrue(snapshot.isDone(MarkKind.NT, "notice_test1"))
        assertTrue(transport.lastRequest().url.contains("marks?select=*&seat=eq.$sid"))
    }

    @Test
    fun `2 分钟 TTL 内 0 请求，force 绕过`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val repo = repository(transport)

        repo.refresh(force = true)
        repo.refresh()
        assertEquals(1, transport.requests.size)

        repo.refresh(force = true)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `setMark upsert 行结构与 Prefer 头正确`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val repo = repository(transport)

        val result = repo.setMark(MarkKind.NT_DONE, "g_123", true)

        assertEquals(MarksUpdateResult.Success, result)
        val post = transport.requests.single { it.method == HttpMethod.POST }
        assertEquals("return=representation,resolution=merge-duplicates", post.headers["Prefer"])
        assertTrue(post.url.endsWith("/marks"))
        val row = postRow(transport)
        assertEquals("$sid|ntdone|g_123", row["id"]!!.jsonPrimitive.content)
        assertEquals(sid, row["seat"]!!.jsonPrimitive.content)
        assertEquals("ntdone", row["kind"]!!.jsonPrimitive.content)
        assertEquals("g_123", row["ref"]!!.jsonPrimitive.content)
        assertTrue(row["val"]!!.jsonPrimitive.boolean)
        assertEquals(fixedNow, row["ts"]!!.jsonPrimitive.content.toLong())
        // 乐观更新
        assertTrue(repo.marks.value.isDone(MarkKind.NT_DONE, "g_123"))
    }

    @Test
    fun `取消写 val false（不 DELETE）`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val repo = repository(transport)

        repo.setMark(MarkKind.HW, "hw_1", false)

        assertFalse(postRow(transport)["val"]!!.jsonPrimitive.boolean)
        assertTrue(transport.requests.none { it.method.wire == "DELETE" })
    }

    @Test
    fun `写入失败进待补发队列，恢复后补发`() = runTest {
        var failPost = true
        val transport = FakeTransport { request ->
            when (request.method) {
                HttpMethod.POST -> if (failPost) {
                    ApiResponse(500, """{"code":"XX000","message":"boom"}""")
                } else {
                    ApiResponse(200, "[]")
                }
                else -> ApiResponse(200, "[]")
            }
        }
        val repo = repository(transport)

        val failed = repo.setMark(MarkKind.NT, "nt_1", true)

        assertTrue(failed is MarksUpdateResult.Failure)
        assertEquals(1, repo.pendingCount())

        failPost = false
        assertEquals(1, repo.flushPending())
        assertEquals(0, repo.pendingCount())
    }

    @Test
    fun `refresh 时待补发覆盖云端旧值`() = runTest {
        val local = InMemoryMarksLocalStore()
        local.writePending(
            listOf(
                MarkRow(
                    id = "$sid|ntdone|auto_g_x",
                    seat = sid,
                    kind = "ntdone",
                    ref = "auto_g_x",
                    value = true,
                    ts = fixedNow,
                ),
            ),
        )
        val cloudRows = """[{"id":"$sid|ntdone|auto_g_x","seat":"$sid","kind":"ntdone","ref":"auto_g_x","val":false,"ts":1}]"""
        val transport = FakeTransport { ApiResponse(200, cloudRows) }
        val repo = repository(transport, local)

        repo.refresh(force = true)

        assertTrue(repo.marks.value.isDone(MarkKind.NT_DONE, "auto_g_x"))
    }

    @Test
    fun `parseRows 对非法响应返回 null`() {
        assertNull(DefaultMarksRepository.parseRows(Json, "not-json"))
        assertNull(DefaultMarksRepository.parseRows(Json, """{"a":1}"""))
    }

    @Test
    fun `parseRows 跳过坏行与空 ref`() {
        val rows = DefaultMarksRepository.parseRows(
            Json,
            """
            [
              {"id":"x","seat":"s","kind":"hw","ref":"r1","val":true,"ts":1},
              {"id":"y","seat":"s","kind":"hw","ref":"","val":true},
              {"id":"z","seat":"s","kind":"hw"},
              "not-an-object"
            ]
            """.trimIndent(),
        )
        assertEquals(1, rows!!.size)
        assertEquals("r1", rows.single().ref)
    }

    @Test
    fun `未登录时 refresh 与 setMark 返回配置错误`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val repo = DefaultMarksRepository(
            api = testApiClient(transport),
            currentSid = { "" },
        )

        val refresh = repo.refresh()
        assertTrue(refresh is com.furina.classtools.data.network.ApiResult.Failure)
        val update = repo.setMark(MarkKind.HW, "x", true)
        assertTrue(update is MarksUpdateResult.Failure)
        assertEquals(0, transport.requests.size)
    }
}
