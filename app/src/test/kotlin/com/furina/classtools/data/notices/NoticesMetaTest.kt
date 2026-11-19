package com.furina.classtools.data.notices

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiRequest
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.testApiClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知列表轻量化（2026-11-19 P0 收口）——light meta 优先 + 懒加载附件 + 不再后台翻页。
 *
 * 契约（网页/小程序同一口径，服务端 `deploy/cloudbase/2026-11-17_notice_meta.sql`）：
 * - `POST rpc/class_notices_meta`（body `{}`，需要班级 token）返回**全部**未归档行，
 *   body 内 `items[].url/src` 的内联 data URI 被置空并写 `lazy=true`（整表 ~0.3MB，旧整表 ~14MB）；
 * - 接口不存在/无权限（400/401/403/404/405/501）→ 回退旧分页链路（本类只验证回退仍可用）；
 * - 打开详情时 `notices?select=*&id=eq.<rowId>` 拉**单行**完整 body（`hydrateRow`），
 *   合并回缓存后重开不再请求；
 * - 没有任何后台「自动翻完所有页」逻辑：`load()` 只有 lite + meta 两条请求。
 */
class NoticesMetaTest {

    // ------------------------------------------------------------ 夹具

    private fun escapeForJson(text: String): String =
        text.replace("\\", "\\\\").replace("\"", "\\\"")

    /** 自动通知 group JSON；[lazy] = true 时模拟服务端 light 版本（图片/附件内容被置空 + lazy=true）。 */
    private fun groupJson(groupId: String, title: String, lazy: Boolean): String {
        val image = if (lazy) {
            """{"kind":"img","src":"","caption":"现场图","lazy":true}"""
        } else {
            """{"kind":"img","src":"data:image/png;base64,AAAA","caption":"现场图"}"""
        }
        val file = if (lazy) {
            """{"kind":"file","name":"a.pdf","url":"","size":"10KB","lazy":true}"""
        } else {
            """{"kind":"file","name":"a.pdf","url":"https://tool.furina.fun/att/x?exp=1&sig=ab","size":"10KB"}"""
        }
        return """{"id":"$groupId","title":"$title","cat":"班会",""" +
            """"publishedAt":"2026-11-18T10:00:00+08:00","items":[$image,$file]}"""
    }

    private fun autoRow(
        rowId: String,
        groupId: String = rowId.removePrefix("auto_"),
        title: String = "关于主题班会的通知",
        ts: Long,
        hidden: Boolean = false,
        lazy: Boolean = false,
    ): String {
        val body = escapeForJson(NOTICE_BODY_PREFIX + groupJson(groupId, title, lazy))
        return """{"id":"$rowId","title":"","body":"$body","ts":$ts,"hidden":$hidden,"edited_at":0}"""
    }

    /** 人工通知行：标题带上 id，避免跨行被 NoticeDedupe 当成重复通知合并。 */
    private fun manualRow(id: String, ts: Long): String =
        """{"id":"$id","title":"班费公示 $id","body":"正文 $id","ts":$ts,"hidden":false}"""

    /** 由正文行生成 lite 签名响应（与生产 lite 查询字段一致）。 */
    private fun liteOf(rows: List<String>): String = rows.joinToString(",", "[", "]") { row ->
        val id = Regex("\"id\":\"([^\"]+)\"").find(row)?.groupValues?.get(1).orEmpty()
        val ts = Regex("\"ts\":(\\d+)").find(row)?.groupValues?.get(1).orEmpty().ifBlank { "0" }
        val hidden = Regex("\"hidden\":(true|false)").find(row)?.groupValues?.get(1).orEmpty().ifBlank { "false" }
        """{"id":"$id","ts":$ts,"hidden":$hidden,"edited_at":0}"""
    }

    private fun tsOf(row: String): Long =
        Regex("\"ts\":(\\d+)").find(row)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    private class Fixture(
        var rows: List<String>,
        /** meta 返回的 HTTP 状态（200 = 可用；404/403 等 = 回退旧链路）。 */
        var metaStatus: Int = 200,
    ) {
        val metaRequests: MutableList<ApiRequest> = mutableListOf()
        val pageRequests: MutableList<ApiRequest> = mutableListOf()
        val rowRequests: MutableList<ApiRequest> = mutableListOf()
    }

    private fun transportOf(fixture: Fixture): FakeTransport = FakeTransport { request ->
        val url = request.url
        when {
            url.contains(DefaultNoticesRepository.META_PATH) -> {
                fixture.metaRequests.add(request)
                if (fixture.metaStatus == 200) {
                    ApiResponse(200, "[" + fixture.rows.joinToString(",") + "]")
                } else {
                    ApiResponse(fixture.metaStatus, """{"message":"unsupported"}""")
                }
            }

            // 单行完整 body（hydrate）
            url.contains("&id=eq.") -> {
                fixture.rowRequests.add(request)
                val id = url.substringAfter("&id=eq.").substringBefore("&")
                val row = fixture.rows.firstOrNull { it.contains("\"id\":\"$id\"") }
                if (row == null) ApiResponse(200, "[]") else ApiResponse(200, "[$row]")
            }

            url.contains("select=id,ts,hidden,edited_at") -> ApiResponse(200, liteOf(fixture.rows))

            else -> {
                fixture.pageRequests.add(request)
                val ordered = fixture.rows.sortedByDescending { tsOf(it) }
                val limit = Regex("limit=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: ordered.size
                val offset = Regex("offset=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                ApiResponse(200, "[" + ordered.drop(offset).take(limit).joinToString(",") + "]")
            }
        }
    }

    private fun repository(
        transport: FakeTransport,
        onTokenInvalid: () -> Unit = {},
        pageSize: Int = 2,
    ) = DefaultNoticesRepository(
        api = testApiClient(transport, onTokenInvalid = onTokenInvalid),
        isAdmin = { false },
        clock = { 1_700_000_000_000L },
        pageSize = pageSize,
        requestTimeoutMs = 5_000L,
        retryDelayMs = 1L,
    )

    // ------------------------------------------------------------ meta 优先

    @Test
    fun `meta 可用时一次拿全量且不再发分页正文请求`() = runTest {
        val fixture = Fixture(listOf(manualRow("m1", 100L), manualRow("m2", 200L), manualRow("m3", 300L)))
        val transport = transportOf(fixture)
        val repo = repository(transport, pageSize = 2)

        val data = (repo.load(force = true) as NoticesLoadResult.Success).data

        // 全量（3 条）一次到位：不再有「首屏 2 条 + 后台翻页」。
        assertEquals(3, data.manual.size)
        assertFalse(data.hasMore)
        assertEquals(0, data.remainingCount)
        assertEquals(3, data.loadedRawCount)
        // 请求形态：lite（签名）+ POST rpc/class_notices_meta 两条，且 body 为 {}
        assertEquals(1, fixture.metaRequests.size)
        val meta = fixture.metaRequests.single()
        assertEquals("POST", meta.method.wire)
        assertTrue(meta.url.endsWith("rpc/class_notices_meta"))
        assertEquals("{}", meta.body)
        assertTrue("meta 需要班级 token", meta.headers.containsKey("X-Class-Token"))
        // 没有任何分页正文请求（旧实现会连续拉 limit=20/10/5… 的整表页）
        assertTrue("不应再发 notices?select=* 分页请求", fixture.pageRequests.isEmpty())
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `meta 链路下 loadMore 不发请求（一次已拿全量）`() = runTest {
        val fixture = Fixture(listOf(manualRow("m1", 100L), manualRow("m2", 200L)))
        val transport = transportOf(fixture)
        val repo = repository(transport)
        repo.load(force = true)
        val requestsAfterLoad = transport.requests.size

        val again = (repo.loadMore() as NoticesLoadResult.Success).data

        assertEquals(2, again.manual.size)
        assertFalse(again.hasMore)
        assertEquals("loadMore 不应新增请求", requestsAfterLoad, transport.requests.size)
    }

    @Test
    fun `签名未变时第二次 load 命中缓存不发请求`() = runTest {
        val fixture = Fixture(listOf(manualRow("m1", 100L)))
        val transport = transportOf(fixture)
        val repo = repository(transport)
        repo.load(force = true)
        val requestsAfterFirst = transport.requests.size

        val second = (repo.load(force = true) as NoticesLoadResult.Success).data

        assertTrue(second.fromCache)
        assertEquals(requestsAfterFirst + 1, transport.requests.size) // 只有 lite 签名请求
        assertEquals(1, fixture.metaRequests.size)
    }

    // ------------------------------------------------------------ 回退旧链路

    @Test
    fun `meta 404 时回退旧分页（首屏一页 + 可继续加载）`() = runTest {
        val fixture = Fixture(listOf(manualRow("m1", 100L), manualRow("m2", 200L), manualRow("m3", 300L)), metaStatus = 404)
        val transport = transportOf(fixture)
        val repo = repository(transport, pageSize = 2)

        val first = (repo.load(force = true) as NoticesLoadResult.Success).data

        assertEquals(1, fixture.metaRequests.size)
        assertEquals(2, first.manual.size)
        assertTrue("旧链路仍有下一页", first.hasMore)
        assertEquals(1, first.remainingCount)
        assertEquals(1, fixture.pageRequests.size)

        val second = (repo.loadMore() as NoticesLoadResult.Success).data
        assertEquals(3, second.manual.size)
        assertFalse(second.hasMore)
    }

    @Test
    fun `meta 403 视为不可用且不清会话（不回调 onTokenInvalid）`() = runTest {
        var tokenInvalidCalls = 0
        val fixture = Fixture(listOf(manualRow("m1", 100L), manualRow("m2", 200L)), metaStatus = 403)
        val repo = repository(transportOf(fixture), onTokenInvalid = { tokenInvalidCalls += 1 }, pageSize = 2)

        val data = (repo.load(force = true) as NoticesLoadResult.Success).data

        assertEquals(0, tokenInvalidCalls)
        assertEquals(2, data.manual.size)
        assertEquals(1, fixture.metaRequests.size)
        assertTrue(fixture.pageRequests.isNotEmpty())
    }

    @Test
    fun `meta 坏 JSON 时无缓存返回 Failure`() = runTest {
        val fixture = Fixture(listOf(manualRow("m1", 100L)))
        val transport = FakeTransport { request ->
            when {
                request.url.contains(DefaultNoticesRepository.META_PATH) ->
                    ApiResponse(200, "\"not-an-array\"")

                request.url.contains("select=id,ts,hidden,edited_at") -> ApiResponse(200, liteOf(fixture.rows))
                else -> ApiResponse(200, "[" + fixture.rows.joinToString(",") + "]")
            }
        }
        val repo = repository(transport)

        val result = repo.load(force = true)

        assertTrue(result is NoticesLoadResult.Failure)
        assertTrue((result as NoticesLoadResult.Failure).message.isNotBlank())
        // 坏 JSON 不静默切到 14MB 旧整表；也不写入半截缓存。
        assertEquals(null, repo.cached())
    }

    // ------------------------------------------------------------ 懒加载附件（hydrate）

    @Test
    fun `light meta 的 lazy 图片与附件被标记为需懒加载`() = runTest {
        val fixture = Fixture(listOf(autoRow("auto_g1", ts = 100L, lazy = true)))
        val repo = repository(transportOf(fixture))

        val entry = (repo.load(force = true) as NoticesLoadResult.Success).data.auto.single()

        assertTrue("lazy 条目需要打开详情时再拉单行 body", entry.hasLazyContent)
        val image = entry.items.filterIsInstance<NoticeItemMeta.Image>().single()
        assertFalse(image.hasData)
        assertEquals("", image.url)
        assertTrue(image.lazy)
        val file = entry.items.filterIsInstance<NoticeItemMeta.File>().single()
        assertEquals("", file.url)
        assertTrue(file.lazy)
        assertTrue(file.pending)
    }

    @Test
    fun `打开详情 hydrateRow 只拉单行完整 body 并合并回缓存`() = runTest {
        val lazyRow = autoRow("auto_g1", ts = 100L, lazy = true)
        val fullRow = autoRow("auto_g1", ts = 100L, lazy = false)
        val fixture = Fixture(listOf(lazyRow))
        // 单行接口返回完整 body（服务端 RLS 不变，只是没被 light 化）
        val transport = FakeTransport { request ->
            val url = request.url
            when {
                url.contains(DefaultNoticesRepository.META_PATH) -> {
                    fixture.metaRequests.add(request)
                    ApiResponse(200, "[$lazyRow]")
                }

                url.contains("&id=eq.") -> {
                    fixture.rowRequests.add(request)
                    ApiResponse(200, "[$fullRow]")
                }

                url.contains("select=id,ts,hidden,edited_at") -> ApiResponse(200, liteOf(fixture.rows))
                else -> ApiResponse(200, "[$lazyRow]")
            }
        }
        val repo = repository(transport)
        val before = (repo.load(force = true) as NoticesLoadResult.Success).data
        assertTrue(before.auto.single().hasLazyContent)
        assertEquals(0, fixture.rowRequests.size)

        val hydrated = (repo.hydrateRow("auto_g1") as NoticesLoadResult.Success).data

        // 单行请求：select=* + id=eq.<rowId>
        assertEquals(1, fixture.rowRequests.size)
        val rowRequest = fixture.rowRequests.single()
        assertTrue(rowRequest.url.contains("notices?select=*"))
        assertTrue(rowRequest.url.contains("&id=eq.auto_g1"))
        // 合并后：图片有内联 data、文件有外置 url，且不再标记 lazy
        val entry = hydrated.auto.single()
        assertFalse("hydrate 后不需要再次懒加载", entry.hasLazyContent)
        val image = entry.items.filterIsInstance<NoticeItemMeta.Image>().single()
        assertTrue(image.hasData)
        val file = entry.items.filterIsInstance<NoticeItemMeta.File>().single()
        assertTrue(file.url.startsWith("https://"))
        assertFalse(file.pending)
        // 缓存已更新（重开详情不再请求）
        val cached = repo.cached()
        assertNotNull(cached)
        assertFalse(cached!!.auto.single().hasLazyContent)
    }

    @Test
    fun `hydrateRow 拉到不在列表里的行时返回当前缓存而不是失败`() = runTest {
        // meta 只返回 m1；单行接口能取到 auto_g9（例如列表被过滤/分页重建后不在内存里）。
        val listed = manualRow("m1", 100L)
        val other = autoRow("auto_g9", ts = 900L, lazy = false)
        val transport = FakeTransport { request ->
            val url = request.url
            when {
                url.contains(DefaultNoticesRepository.META_PATH) -> ApiResponse(200, "[$listed]")
                url.contains("&id=eq.") -> ApiResponse(200, "[$other]")
                url.contains("select=id,ts,hidden,edited_at") -> ApiResponse(200, liteOf(listOf(listed)))
                else -> ApiResponse(200, "[$listed]")
            }
        }
        val repo = repository(transport)
        repo.load(force = true)

        val result = repo.hydrateRow("auto_g9")

        assertTrue("行不在列表里不应打断阅读", result is NoticesLoadResult.Success)
        assertTrue((result as NoticesLoadResult.Success).data.fromCache)
        assertEquals(1, result.data.manual.size)
    }

    @Test
    fun `loadRow 返回单行完整行且不存在时返回 null`() = runTest {
        val fixture = Fixture(listOf(autoRow("auto_g1", ts = 100L, lazy = false)))
        val repo = repository(transportOf(fixture))

        val row = repo.loadRow("auto_g1")
        assertNotNull(row)
        assertEquals("auto_g1", row!!.id)
        assertTrue(row.body.startsWith(NOTICE_BODY_PREFIX))

        assertEquals(null, repo.loadRow("auto_nope"))
        assertEquals(null, repo.loadRow(""))
    }
}
