package com.furina.classtools.data.homework

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.testApiClient
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeworkRepositoryTest {

    private val rowsJson = """
        [
          {"id":"h1","subject":"高等数学","content":"P42","date":"2026-11-10","time":"23:59",
           "author":"李四","owner":"2024000003","attachments":"","done":false,"archived":false,"ts":100},
          {"id":"h2","subject":"英语","content":"Unit 5","date":"2026-11-08","time":"18:00",
           "author":"","owner":"","attachments":"","done":false,"archived":false,"ts":200}
        ]
    """.trimIndent()

    private fun repository(transport: FakeTransport, clock: () -> Long = { 1_700_000_000_000L }) =
        DefaultHomeworkRepository(api = testApiClient(transport), clock = clock)

    @Test
    fun `加载作业走契约路径并解析`() = runTest {
        val transport = FakeTransport { ApiResponse(200, rowsJson) }
        val repo = repository(transport)

        val result = repo.load(force = true)

        val success = result as HomeworkLoadResult.Success
        assertFalse(success.fromCache)
        assertEquals(listOf("h1", "h2"), success.rows.map { it.id })
        assertEquals("高等数学", success.rows[0].subject)
        // 单次请求
        assertEquals(1, transport.requests.size)
        assertEquals(
            "https://example.invalid/v1/rdb/rest/homework?select=*&order=ts.asc&archived=eq.false",
            transport.lastRequest().url,
        )
        assertEquals("test-token", transport.lastRequest().headers["X-Class-Token"])
    }

    @Test
    fun `TTL 内第二次不请求 且标记 fromCache`() = runTest {
        var now = 1_000_000L
        val transport = FakeTransport { ApiResponse(200, rowsJson) }
        val repo = repository(transport) { now }

        repo.load()
        now += DefaultHomeworkRepository.DEFAULT_TTL_MS - 1
        val second = repo.load() as HomeworkLoadResult.Success

        assertEquals(1, transport.requests.size)
        assertTrue(second.fromCache)
        assertNull(second.refreshError)
    }

    @Test
    fun `force 绕过 TTL`() = runTest {
        val transport = FakeTransport { ApiResponse(200, rowsJson) }
        val repo = repository(transport)

        repo.load()
        repo.load(force = true)

        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `空数组是合法结果`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val repo = repository(transport)

        val result = repo.load(force = true) as HomeworkLoadResult.Success

        assertTrue(result.rows.isEmpty())
        assertFalse(result.fromCache)
    }

    @Test
    fun `坏 JSON 无缓存时失败`() = runTest {
        val transport = FakeTransport { ApiResponse(200, """{"not":"array"}""") }
        val repo = repository(transport)

        assertTrue(repo.load(force = true) is HomeworkLoadResult.Failure)
    }

    @Test
    fun `网络失败有缓存时降级返回缓存`() = runTest {
        var fail = false
        val transport = FakeTransport {
            if (fail) throw IOException("offline") else ApiResponse(200, rowsJson)
        }
        val repo = repository(transport)
        repo.load()

        fail = true
        val result = repo.load(force = true) as HomeworkLoadResult.Success

        assertTrue(result.fromCache)
        assertEquals(2, result.rows.size)
        assertNotNull(result.refreshError)
    }

    @Test
    fun previouslySyncedEmptyAssignmentsStayAvailableWhenTheNextRequestFails() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val repository = repository(transport)
        assertNull(repository.cachedSnapshot())
        repository.load(force = true)
        transport.responder = { throw IOException("offline") }

        val offline = repository.load(force = true) as HomeworkLoadResult.Success

        assertEquals(emptyList<HomeworkRow>(), repository.cachedSnapshot())
        assertTrue(offline.rows.isEmpty())
        assertNotNull(offline.refreshError)
    }

    @Test
    fun `坏行被跳过 不影响其它行`() {
        val parsed = DefaultHomeworkRepository.parseRows(
            NetJson,
            """
            [
              {"id":"ok","subject":"a"},
              {"subject":"缺 id"},
              "not-an-object",
              {"id":"ok2"}
            ]
            """.trimIndent(),
        )
        assertEquals(listOf("ok", "ok2"), parsed!!.map { it.id })
    }

    @Test
    fun `parseRows 非数组返回 null`() {
        assertEquals(null, DefaultHomeworkRepository.parseRows(NetJson, "{}"))
        assertEquals(null, DefaultHomeworkRepository.parseRows(NetJson, "not-json"))
    }

    @Test
    fun `本轮写接口保持只读`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val repo = repository(transport)

        assertFalse(repo.publish(HomeworkRow(id = "x")))
        assertFalse(repo.archive("x"))
        assertEquals(0, transport.requests.size)
    }
}
