package com.furina.classtools.data.notices

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiRequest
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.testApiClient
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// =============================================================================
// 分页测试的 JSON 构造（顶层函数，供测试类与 PagedTransport 共用）
// =============================================================================

private fun manualRow(id: String, ts: Long): String =
    """{"id":"$id","title":"$id","body":"正文","ts":$ts,"hidden":false}"""

private fun autoRow(
    rowId: String,
    groupId: String,
    title: String = "关于主题班会的通知",
    ts: Long,
    hidden: Boolean = false,
    source: String = "班级通知群",
    deadline: String = "2026-11-15T16:00:00+08:00",
): String {
    val group = """{"id":"$groupId","title":"$title","cat":"班会","source":"$source",""" +
        """"deadline":"$deadline"}"""
    val body = (NOTICE_BODY_PREFIX + group)
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return """{"id":"$rowId","title":"","body":"$body","ts":$ts,"hidden":$hidden,"edited_at":0}"""
}

private val idRegex = Regex("\"id\":\"([^\"]+)\"")
private val tsRegex = Regex("\"ts\":(\\d+)")
private val hiddenRegex = Regex("\"hidden\":(true|false)")
private val editedAtRegex = Regex("\"edited_at\":(\\d+)")

/** 由正文行 JSON 生成 lite 签名响应（与生产 lite 查询字段一致）。 */
private fun liteOf(rows: List<String>): String = rows.joinToString(",", "[", "]") { row ->
    val id = idRegex.find(row)?.groupValues?.get(1).orEmpty()
    val ts = tsRegex.find(row)?.groupValues?.get(1).orEmpty()
    val hidden = hiddenRegex.find(row)?.groupValues?.get(1).orEmpty()
    val editedAt = editedAtRegex.find(row)?.groupValues?.get(1).orEmpty()
    """{"id":"$id","ts":${ts.ifBlank { "0" }},"hidden":${hidden.ifBlank { "false" }},"edited_at":${editedAt.ifBlank { "0" }}}"""
}

private fun limitOf(url: String): Int =
    Regex("""limit=(\d+)""").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: -1

private fun offsetOf(url: String): Int =
    Regex("""offset=(\d+)""").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: -1

/** 行 JSON 的 ts（服务端按 ts 倒序分页；假传输需模拟该排序）。 */
private fun tsOf(row: String): Long =
    tsRegex.find(row)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

/**
 * 通知仓库分页/超时（task-52 网络修复）单测。
 *
 * 背景：`notices?select=*` 全表约 13.9MB，App 10s 超时 → 只显示本机缓存。修复后：
 * - 首屏只拉 lite 签名 + 最新一页（ts 倒序 limit=20）；「加载更多」按 offset 追加；
 * - 签名变化时重载第一页；失败保留已加载内容；单请求 30s 协程超时 + 超时重试 1 次；
 * - 跨页仍走 NoticeDedupe（不得回退）。
 */
class NoticesPaginationTest {

    // ------------------------------------------------------------ 假传输（lite + 分页正文）

    private class PagedTransport(var rows: List<String>) {
        val pageRequests: MutableList<ApiRequest> = mutableListOf()
        var pageDelayMs: Long? = null
        val pageDelayByLimit: MutableMap<Int, Long> = mutableMapOf()
        var pageFailures: Int = 0
        var liteFailures: Int = 0
        var pageAttempts: Int = 0

        val transport: FakeTransport = FakeTransport { request ->
            if (request.url.contains("select=id,ts,hidden,edited_at")) {
                if (liteFailures > 0) {
                    liteFailures -= 1
                    throw IOException("offline")
                }
                ApiResponse(200, liteOf(rows))
            } else {
                pageRequests.add(request)
                pageAttempts += 1
                val limitNow = limitOf(request.url).takeIf { it > 0 } ?: rows.size
                (pageDelayByLimit[limitNow] ?: pageDelayMs)?.let { delay(it) }
                if (pageFailures > 0) {
                    pageFailures -= 1
                    throw IOException("offline")
                }
                val ordered = rows.sortedByDescending { tsOf(it) }
                val limit = limitOf(request.url).takeIf { it > 0 } ?: ordered.size
                val offset = offsetOf(request.url).coerceAtLeast(0)
                val page = ordered.drop(offset).take(limit)
                ApiResponse(200, "[" + page.joinToString(",") + "]")
            }
        }
    }

    private fun repository(
        transport: FakeTransport,
        pageSize: Int = 2,
        requestTimeoutMs: Long = 5_000L,
        clock: () -> Long = { 1_700_000_000_000L },
    ) = DefaultNoticesRepository(
        api = testApiClient(transport),
        isAdmin = { false },
        clock = clock,
        pageSize = pageSize,
        requestTimeoutMs = requestTimeoutMs,
        retryDelayMs = 1L,
    )

    private fun manualRows(count: Int): List<String> =
        (1..count).map { i -> manualRow("m$i", i.toLong() * 100L) }

    // ------------------------------------------------------------ 分页

    @Test
    fun `首屏只拉最新一页 并给出剩余条数`() = runTest {
        val paged = PagedTransport(manualRows(5))
        val repo = repository(paged.transport, pageSize = 2)

        val data = (repo.load(force = true) as NoticesLoadResult.Success).data

        assertEquals(2, data.manual.size)
        // ts 倒序：最新 m5、m4
        assertEquals(listOf("m5", "m4"), data.manual.map { it.row.id })
        assertTrue(data.hasMore)
        assertEquals(3, data.remainingCount)
        assertEquals(2, data.loadedRawCount)
        assertEquals(1, paged.pageRequests.size)
        val pageUrl = paged.pageRequests.single().url
        assertTrue(pageUrl.contains("select=*"))
        assertTrue(pageUrl.contains("order=ts.desc"))
        assertTrue(pageUrl.contains("limit=2"))
        assertTrue(pageUrl.contains("offset=0"))
    }

    @Test
    fun `首屏大页超时自动缩小 limit 后成功（通知整表 15MB 弱网兜底）`() = runTest {
        val paged = PagedTransport(manualRows(25))
        // 首屏 pageSize=20 时故意让 limit=20 超时；10 条正常返回。
        paged.pageDelayByLimit[20] = 1_000L
        val repo = repository(paged.transport, pageSize = 20, requestTimeoutMs = 100L)

        val data = (repo.load(force = true) as NoticesLoadResult.Success).data

        assertEquals(10, data.manual.size)
        assertTrue(data.hasMore)
        assertEquals(10, data.loadedRawCount)
        assertEquals(listOf(20, 10), paged.pageRequests.map { limitOf(it.url) }.distinct())
    }

    @Test
    fun `加载更多按 offset 追加`() = runTest {
        val paged = PagedTransport(manualRows(5))
        val repo = repository(paged.transport, pageSize = 2)

        val first = (repo.load(force = true) as NoticesLoadResult.Success).data
        assertEquals(2, first.manual.size)

        val second = (repo.loadMore() as NoticesLoadResult.Success).data
        assertEquals(4, second.manual.size)
        assertEquals(1, second.remainingCount)
        assertTrue(second.hasMore)

        val third = (repo.loadMore() as NoticesLoadResult.Success).data
        assertEquals(5, third.manual.size)
        assertFalse(third.hasMore)
        assertEquals(0, third.remainingCount)

        assertEquals(listOf(0, 2, 4), paged.pageRequests.map { offsetOf(it.url) })
        assertEquals(listOf("m5", "m4", "m3", "m2", "m1"), third.manual.map { it.row.id })
    }

    @Test
    fun `签名变化时 loadMore 重载第一页而不是拼接旧页`() = runTest {
        val paged = PagedTransport(manualRows(4))
        val repo = repository(paged.transport, pageSize = 2)
        repo.load(force = true)

        paged.rows = listOf(manualRow("m9", 9_000L)) + manualRows(4)
        val reloaded = (repo.loadMore() as NoticesLoadResult.Success).data

        assertEquals(2, reloaded.manual.size)
        assertEquals(listOf("m9", "m4"), reloaded.manual.map { it.row.id })
        assertTrue(reloaded.hasMore)
        // 最后一次正文请求必须是第一页（offset=0）
        assertEquals(0, offsetOf(paged.pageRequests.last().url))
    }

    @Test
    fun `无更多数据时 loadMore 不新增正文请求`() = runTest {
        val paged = PagedTransport(manualRows(2))
        val repo = repository(paged.transport, pageSize = 2)
        repo.load(force = true)
        assertEquals(1, paged.pageRequests.size)

        val again = (repo.loadMore() as NoticesLoadResult.Success).data
        assertEquals(2, again.manual.size)
        assertFalse(again.hasMore)
        assertEquals(1, paged.pageRequests.size)
    }

    // ------------------------------------------------------------ 失败 / 超时

    @Test
    fun `加载更多失败保留已加载内容并返回明确错误`() = runTest {
        val paged = PagedTransport(manualRows(5))
        val repo = repository(paged.transport, pageSize = 2)
        val first = (repo.load(force = true) as NoticesLoadResult.Success).data
        assertEquals(2, first.manual.size)

        paged.pageFailures = 10 // 正文持续失败（含 ApiClient 自动重试）
        val failure = repo.loadMore()

        assertTrue(failure is NoticesLoadResult.Failure)
        assertTrue((failure as NoticesLoadResult.Failure).message.isNotBlank())
        val cached = repo.cached()
        assertNotNull(cached)
        assertEquals(2, cached!!.manual.size)
        assertTrue(cached.hasMore)
    }

    @Test
    fun `超时自动重试一次后成功`() = runTest {
        val rows = manualRows(3)
        var slowOnce = true
        val pageRequests = mutableListOf<ApiRequest>()
        val transport = FakeTransport { request ->
            if (request.url.contains("select=id,ts,hidden,edited_at")) {
                ApiResponse(200, liteOf(rows))
            } else {
                pageRequests.add(request)
                if (slowOnce) {
                    slowOnce = false
                    delay(200) // > 50ms 超时，触发重试
                }
                val ordered = rows.sortedByDescending { tsOf(it) }
                val limit = limitOf(request.url).takeIf { it > 0 } ?: 2
                val offset = offsetOf(request.url).coerceAtLeast(0)
                ApiResponse(200, "[" + ordered.drop(offset).take(limit).joinToString(",") + "]")
            }
        }
        val repo = repository(transport, pageSize = 2, requestTimeoutMs = 50L)

        val data = (repo.load(force = true) as NoticesLoadResult.Success).data

        assertEquals(2, data.manual.size)
        assertEquals(2, pageRequests.size) // 超时一次 + 重试成功
    }

    @Test
    fun `连续超时后保留缓存并给出「网络较慢或服务器无响应」`() = runTest {
        val paged = PagedTransport(manualRows(5))
        val repo = repository(paged.transport, pageSize = 2, requestTimeoutMs = 50L)
        repo.load(force = true)

        paged.pageDelayMs = 200L
        val failure = repo.loadMore()

        assertTrue(failure is NoticesLoadResult.Failure)
        val message = (failure as NoticesLoadResult.Failure).message
        assertTrue("message=$message", message.contains("网络较慢") || message.contains("无响应"))
        assertNotNull(repo.cached())
        assertEquals(2, repo.cached()!!.manual.size)
    }

    @Test
    fun `无缓存时首屏超时返回 Failure`() = runTest {
        val paged = PagedTransport(manualRows(5))
        paged.pageDelayMs = 200L
        val repo = repository(paged.transport, pageSize = 2, requestTimeoutMs = 50L)

        val result = repo.load(force = true)

        assertTrue(result is NoticesLoadResult.Failure)
        assertTrue((result as NoticesLoadResult.Failure).message.isNotBlank())
    }

    @Test
    fun `lite 失败时 loadMore 返回失败但保留已加载内容`() = runTest {
        val paged = PagedTransport(manualRows(5))
        val repo = repository(paged.transport, pageSize = 2)
        repo.load(force = true)

        paged.liteFailures = 10 // 之后 lite 持续失败（含 ApiClient 自动重试）
        val failure = repo.loadMore()

        assertTrue(failure is NoticesLoadResult.Failure)
        assertNotNull(repo.cached())
        assertEquals(2, repo.cached()!!.manual.size)
    }

    // ------------------------------------------------------------ 与旧行为/去重兼容

    @Test
    fun `签名一致时 load 直接命中缓存 不发正文请求`() = runTest {
        val paged = PagedTransport(manualRows(3))
        val repo = repository(paged.transport, pageSize = 2)
        repo.load(force = true)
        val pageRequestsAfterFirst = paged.pageRequests.size

        val second = (repo.load(force = true) as NoticesLoadResult.Success).data

        assertTrue(second.fromCache)
        assertEquals(pageRequestsAfterFirst, paged.pageRequests.size)
    }

    @Test
    fun `跨页自动通知仍走去重合并`() = runTest {
        val rows = listOf(
            autoRow("auto_g1_new", "g1", ts = 2_000L),
            autoRow("auto_g1_old", "g1", ts = 1_900L),
            autoRow("auto_g2", "g2", title = "关于团课的通知", ts = 1_800L),
        )
        val paged = PagedTransport(rows)
        val repo = repository(paged.transport, pageSize = 2)

        repo.load(force = true)
        val data = (repo.loadMore() as NoticesLoadResult.Success).data

        // 3 行原始 → 同逻辑 g1 去重合并后 2 条
        assertEquals(2, data.auto.size)
        assertFalse(data.hasMore)
        assertEquals(2_000L, data.auto.first { it.id == "g1" }.ts)
    }

    // ------------------------------------------------------------ 自动全部加载（task-56 B）

    @Test
    fun `自动全部加载在签名变化时重置基线并加载到底`() = runTest {
        val paged = PagedTransport(manualRows(6))
        val repo = repository(paged.transport, pageSize = 2)
        val first = (repo.load(force = true) as NoticesLoadResult.Success).data
        assertEquals(2, first.manual.size)
        assertTrue(first.hasMore)

        // 云端新增一条（签名变化）→ loadMore 内部必须重载第一页（offset=0），不能拼接旧页。
        paged.rows = listOf(manualRow("m9", 9_000L)) + manualRows(6)
        var callbacks = 0
        val loader = NoticesAutoLoader(repository = repo, retryDelayMs = 1L, maxSleep = { })
        val result = loader.loadAll { callbacks += 1 }

        assertTrue(result is NoticesAutoLoadResult.Completed)
        assertTrue(callbacks >= 1)
        val done = repo.cached()!!
        assertFalse(done.hasMore)
        assertEquals(0, done.remainingCount)
        // 新行 + 全部旧行都在，无重复（跨页去重不得回退）。
        val ids = done.manual.map { it.row.id }
        assertEquals(7, ids.size)
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.contains("m9"))
        // 首次加载 + 签名变化后各一次 offset=0 请求。
        assertEquals(2, paged.pageRequests.count { offsetOf(it.url) == 0 })
    }

    @Test
    fun `自动全部加载失败重试一次仍失败时保留首屏`() = runTest {
        val paged = PagedTransport(manualRows(6))
        val repo = repository(paged.transport, pageSize = 2)
        repo.load(force = true)

        paged.pageFailures = 99 // 正文持续失败（含 ApiClient 自动重试）
        val loader = NoticesAutoLoader(
            repository = repo,
            retryDelayMs = 1L,
            maxSleep = { },
        )
        val result = loader.loadAll { }

        assertTrue(result is NoticesAutoLoadResult.Failed)
        assertEquals(2, repo.cached()!!.manual.size) // 首屏未丢
        assertTrue(repo.cached()!!.hasMore)
    }
}
