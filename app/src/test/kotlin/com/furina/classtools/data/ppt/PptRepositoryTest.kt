package com.furina.classtools.data.ppt

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiRequest
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpMethod
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.session.Session
import com.furina.classtools.features.ppt.PptLimits
import com.furina.classtools.testApiClient
import java.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PPT 仓库全链路单测（FakeTransport，不发真实网络）。
 *
 * 覆盖：列表/心跳（轻字段、失败回退缓存、Token 失效）、播放清单 ready/not-ready、
 * 上传（建行 → 3 分片 → 提交、base64/Prefer/顺序/进度 StateFlow）、重试 2 次、
 * 失败标记、64MiB/扩展名拒绝、取消软删、软删/重试请求体。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PptRepositoryTest {

    private val now = 1_791_590_400_000L
    private val session = Session(
        sid = "3260707024",
        name = "陈子麒",
        token = "class-token",
        issuedAt = now,
    )

    private val agentJson = """[{"body":{"online":true,"seen_at":${now - 5_000},"queued":1,"version":"1.0","host":"PC"},
                                    "updated_at":"2026-11-09T12:00:00+00:00"}]"""

    private val listJson = """[
        {"id":"dmg2k4x9ab12c","title":"班会 PPT","owner_sid":"3260707024","owner_name":"陈子麒",
         "status":"ready","progress":100,"error":"","slide_count":12,"step_count":37,
         "aspect":"16:9","cover_url":"https://x/c.webp","source_name":"班会.pptx","source_size":100,
         "created_at":$now,"converted_at":$now,"client":"android"},
        {"id":"dzzzzzzzzzzz","title":"别班","owner_sid":"other","owner_name":"别的",
         "status":"queued","progress":0,"slide_count":0,"step_count":0,"created_at":${now - 1000}}
    ]"""

    private fun testRepo(
        transport: FakeTransport,
        scope: TestScope,
        chunkBytes: Int = 4,
        concurrency: Int = 2,
        sessionProvider: () -> Session? = { session },
    ): DefaultPptRepository = DefaultPptRepository(
        api = testApiClient(transport),
        currentSession = sessionProvider,
        ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler) as CoroutineDispatcher,
        clock = { now },
        chunkBytes = chunkBytes,
        concurrency = concurrency,
        retryDelays = listOf(1L, 2L),
    )

    private class FakeSource(
        override val name: String,
        override val size: Long,
        private val data: ByteArray = ByteArray(size.coerceAtMost(1024 * 1024).toInt()) { (it % 251).toByte() },
    ) : PptByteSource {
        var readCalls: Int = 0
            private set

        override suspend fun read(offset: Long, length: Int): ByteArray {
            readCalls += 1
            if (offset >= data.size) return ByteArray(0)
            val end = (offset + length).coerceAtMost(data.size.toLong())
            return data.copyOfRange(offset.toInt(), end.toInt())
        }
    }

    private fun parsedBody(request: ApiRequest): JsonArray =
        NetJson.parseToJsonElement(request.body.orEmpty()) as JsonArray

    // ------------------------------------------------------------ 列表 / 心跳

    @Test
    fun loadList_requestsLightFieldsAndParsesAgent() = runTest {
        val transport = FakeTransport { request ->
            when {
                request.url.contains("site_assets") -> ApiResponse(200, agentJson)
                request.url.contains("ppt_decks?select=") -> ApiResponse(200, listJson)
                else -> ApiResponse(404, "")
            }
        }
        val repo = testRepo(transport, this)
        val result = repo.loadList()
        assertTrue(result is PptListResult.Success)
        result as PptListResult.Success
        assertEquals(2, result.decks.size)
        assertEquals("班会 PPT", result.decks[0].title)
        assertNotNull(result.agent)
        assertEquals(1, result.agent!!.queued)
        assertTrue(result.agent!!.isOnline(now))

        val listRequest = transport.requests.first { it.url.contains("ppt_decks?select=") }
        assertTrue(listRequest.url.contains("deleted=eq.false"))
        assertTrue(listRequest.url.contains("order=created_at.desc"))
        assertTrue(listRequest.url.contains("limit=100"))
        // v2.0：列表只用 jsonb 路径取轻量 manifest->files（不拉完整 manifest）
        assertTrue("列表需带 manifest->files 轻量子路径（v2.0 §12.4.1）", listRequest.url.contains("manifest-%3Efiles") || listRequest.url.contains("manifest->files"))
        assertFalse("列表禁止带完整 manifest", listRequest.url.contains("select=manifest,"))
        // 身份头走现有 ApiClient（X-Class-Token）
        assertEquals("test-token", listRequest.headers["X-Class-Token"])
        assertTrue(listRequest.headers["Authorization"]!!.startsWith("Bearer "))
    }

    @Test
    fun loadList_failureFallsBackToCache() = runTest {
        var fail = false
        val transport = FakeTransport { request ->
            when {
                request.url.contains("site_assets") -> ApiResponse(200, agentJson)
                fail -> ApiResponse(500, """{"code":"XX","message":"boom"}""")
                else -> ApiResponse(200, listJson)
            }
        }
        val repo = testRepo(transport, this)
        assertTrue(repo.loadList() is PptListResult.Success)
        fail = true
        val failed = repo.loadList()
        assertTrue(failed is PptListResult.Failure)
        failed as PptListResult.Failure
        assertEquals(2, failed.cachedDecks!!.size)
        assertNotNull(failed.cachedAgent)
        assertFalse(failed.tokenInvalid)
    }

    @Test
    fun loadList_tokenInvalidMarksFlag() = runTest {
        val transport = FakeTransport { request ->
            if (request.url.contains("site_assets")) ApiResponse(200, "[]") else ApiResponse(403, "{}")
        }
        val repo = testRepo(transport, this)
        val result = repo.loadList()
        assertTrue(result is PptListResult.Failure)
        assertTrue((result as PptListResult.Failure).tokenInvalid)
    }

    @Test
    fun loadAgentStatus_parsesAndCaches() = runTest {
        val transport = FakeTransport { ApiResponse(200, agentJson) }
        val repo = testRepo(transport, this)
        val status = repo.loadAgentStatus()
        assertNotNull(status)
        assertTrue(status!!.online)
        assertTrue(transport.requests.single().url.contains("site_assets?id=eq.ppt_agent"))
    }

    // ------------------------------------------------------------ 播放清单

    @Test
    fun loadPlayer_ready() = runTest {
        val manifest = """{"v":1,"base":"https://x/ppt/a/d1/b/","w":1600,"h":900,
            "slides":[{"tr":"fade","steps":[{"img":"s1.webp"}]}]}"""
        val transport = FakeTransport {
            ApiResponse(200, """[{"title":"班会","slide_count":1,"step_count":1,"manifest":$manifest}]""")
        }
        val repo = testRepo(transport, this)
        val result = repo.loadPlayer("dabc123456")
        assertTrue(result is PptPlayerResult.Success)
        val detail = (result as PptPlayerResult.Success).detail
        assertEquals("班会", detail.title)
        assertTrue(detail.isPlayable)
        val request = transport.requests.single()
        assertTrue(request.url.contains("select=manifest"))
        assertTrue(request.url.contains("id=eq.dabc123456"))
    }

    @Test
    fun loadPlayer_notReady() = runTest {
        val transport = FakeTransport { ApiResponse(200, """[{"title":"x","manifest":{}}]""") }
        val repo = testRepo(transport, this)
        assertTrue(repo.loadPlayer("dabc123456") is PptPlayerResult.NotReady)
    }

    @Test
    fun loadPlayer_httpFailure() = runTest {
        val transport = FakeTransport { ApiResponse(403, "{}") }
        val repo = testRepo(transport, this)
        val result = repo.loadPlayer("dabc123456")
        assertTrue(result is PptPlayerResult.Failure)
        assertTrue((result as PptPlayerResult.Failure).tokenInvalid)
    }

    // ------------------------------------------------------------ 上传

    private fun healthyTransport(source: FakeSource): FakeTransport = FakeTransport { request ->
        when {
            request.method == HttpMethod.POST && request.url.endsWith("/ppt_decks") ->
                ApiResponse(201, """[{"id":"dcreated123456"}]""")
            request.method == HttpMethod.POST && request.url.endsWith("/ppt_upload_chunks") ->
                ApiResponse(201, "")
            request.method == HttpMethod.PATCH -> ApiResponse(200, "[]")
            else -> ApiResponse(404, "")
        }
    }

    @Test
    fun upload_happyPath_createChunksQueue() = runTest {
        val source = FakeSource("班会.pptx", 10L)
        val transport = healthyTransport(source)
        val repo = testRepo(transport, this)
        val result = repo.upload(PptUploadInput(title = "", source = source))
        assertTrue(result is PptUploadResult.Success)
        val deckId = (result as PptUploadResult.Success).deckId

        val posts = transport.requests.filter { it.method == HttpMethod.POST }
        val patches = transport.requests.filter { it.method == HttpMethod.PATCH }
        assertEquals(1 + 3, posts.size) // 1 建行 + 3 分片（4+4+2 字节）
        assertEquals(1, patches.size)

        // §4.1 建行 body
        val create = posts.first { it.url.endsWith("/ppt_decks") }
        val createRow = parsedBody(create)[0].jsonObject
        assertEquals(deckId, createRow["id"]!!.jsonPrimitive.content)
        assertTrue(deckId.startsWith("d"))
        assertEquals("班会", createRow["title"]!!.jsonPrimitive.content) // 空标题 → 文件名去扩展名
        assertEquals("3260707024", createRow["owner_sid"]!!.jsonPrimitive.content)
        assertEquals("陈子麒", createRow["owner_name"]!!.jsonPrimitive.content)
        assertEquals("android", createRow["client"]!!.jsonPrimitive.content)
        assertEquals("uploading", createRow["status"]!!.jsonPrimitive.content)
        assertEquals(3, createRow["chunk_count"]!!.jsonPrimitive.int)
        assertEquals(10, createRow["source_size"]!!.jsonPrimitive.int)
        assertEquals(false, createRow["deleted"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("return=representation", create.headers["Prefer"])

        // §4.2 分片：idx 连续、base64 可还原、带 upsert Prefer
        val chunkPosts = posts.filter { it.url.endsWith("/ppt_upload_chunks") }
        assertEquals(setOf(0, 1, 2), chunkPosts.map { parsedBody(it)[0].jsonObject["idx"]!!.jsonPrimitive.int }.toSet())
        chunkPosts.forEach { request ->
            val row = parsedBody(request)[0].jsonObject
            assertEquals(deckId, row["deck_id"]!!.jsonPrimitive.content)
            assertEquals("3260707024", row["owner_sid"]!!.jsonPrimitive.content)
            assertEquals("resolution=merge-duplicates,return=minimal", request.headers["Prefer"])
            val decoded = Base64.getDecoder().decode(row["data"]!!.jsonPrimitive.content)
            assertTrue(decoded.size in 1..4)
        }

        // §4.3 提交转换
        val patch = patches.single()
        assertTrue(patch.url.contains("ppt_decks?id=eq.$deckId"))
        val patchBody = NetJson.parseToJsonElement(patch.body!!).jsonObject
        assertEquals("queued", patchBody["status"]!!.jsonPrimitive.content)
        assertEquals(3, patchBody["chunk_count"]!!.jsonPrimitive.int)
        assertEquals(10, patchBody["source_size"]!!.jsonPrimitive.int)

        // 进度 StateFlow
        assertEquals(PptUploadPhase.DONE, repo.uploadProgress.value.phase)
        assertEquals(100, repo.uploadProgress.value.percent)
        assertEquals(deckId, repo.uploadProgress.value.deckId)
        assertEquals(3, source.readCalls)
    }

    @Test
    fun upload_titleFromFileName_andSourceBytesOrder() = runTest {
        val data = ByteArray(10) { (0x41 + it).toByte() }
        val source = object : PptByteSource {
            override val name: String = "期末总结.PPTX"
            override val size: Long = 10L
            override suspend fun read(offset: Long, length: Int): ByteArray =
                data.copyOfRange(offset.toInt(), (offset + length).coerceAtMost(10).toInt())
        }
        val transport = healthyTransport(FakeSource("x", 1))
        val repo = testRepo(transport, this)
        assertTrue(repo.upload(PptUploadInput(title = "", source = source)) is PptUploadResult.Success)
        val create = transport.requests.first { it.method == HttpMethod.POST && it.url.endsWith("/ppt_decks") }
        assertEquals("期末总结", parsedBody(create)[0].jsonObject["title"]!!.jsonPrimitive.content)

        // base64 还原后与源字节逐片对上
        val chunkRequests = transport.requests.filter { it.url.endsWith("/ppt_upload_chunks") }
            .sortedBy { parsedBody(it)[0].jsonObject["idx"]!!.jsonPrimitive.int }
        val reconstructed = chunkRequests.flatMap { request ->
            Base64.getDecoder().decode(parsedBody(request)[0].jsonObject["data"]!!.jsonPrimitive.content).toList()
        }
        assertEquals(data.toList(), reconstructed)
    }

    @Test
    fun upload_retriesChunkThenSucceeds() = runTest {
        val attempts = HashMap<Int, Int>()
        val transport = FakeTransport { request ->
            when {
                request.method == HttpMethod.POST && request.url.endsWith("/ppt_decks") ->
                    ApiResponse(201, "[]")
                request.method == HttpMethod.POST && request.url.endsWith("/ppt_upload_chunks") -> {
                    val idx = parsedBody(request)[0].jsonObject["idx"]!!.jsonPrimitive.int
                    val n = (attempts[idx] ?: 0) + 1
                    attempts[idx] = n
                    if (idx == 1 && n <= 2) ApiResponse(500, """{"message":"flaky"}""") else ApiResponse(201, "")
                }
                request.method == HttpMethod.PATCH -> ApiResponse(200, "[]")
                else -> ApiResponse(404, "")
            }
        }
        val repo = testRepo(transport, this, concurrency = 1)
        val result = repo.upload(PptUploadInput("t", FakeSource("a.pptx", 10L)))
        assertTrue(result is PptUploadResult.Success)
        assertEquals(3, attempts[1]) // 1 次 + 重试 2 次
        assertEquals(5, transport.requests.count { it.url.endsWith("/ppt_upload_chunks") }) // idx0 1 次 + idx1 3 次 + idx2 1 次
    }

    @Test
    fun upload_chunkFailure_marksFailedAndReturnsDeckId() = runTest {
        val transport = FakeTransport { request ->
            when {
                request.method == HttpMethod.POST && request.url.endsWith("/ppt_decks") -> ApiResponse(201, "[]")
                request.method == HttpMethod.POST && request.url.endsWith("/ppt_upload_chunks") ->
                    ApiResponse(500, """{"message":"down"}""")
                request.method == HttpMethod.PATCH -> ApiResponse(200, "[]")
                else -> ApiResponse(404, "")
            }
        }
        val repo = testRepo(transport, this, concurrency = 1)
        val result = repo.upload(PptUploadInput("t", FakeSource("a.pptx", 10L)))
        assertTrue(result is PptUploadResult.Failure)
        result as PptUploadResult.Failure
        assertEquals("上传中断，请重试", result.message)
        assertNotNull(result.deckId)
        assertFalse(result.tokenInvalid)
        // 应把云端行标记 failed（契约 §4.2）
        val failedPatch = transport.requests.firstOrNull { it.method == HttpMethod.PATCH }
        assertNotNull(failedPatch)
        val body = NetJson.parseToJsonElement(failedPatch!!.body!!).jsonObject
        assertEquals("failed", body["status"]!!.jsonPrimitive.content)
        assertEquals("上传中断，请重试", body["error"]!!.jsonPrimitive.content)
        assertEquals(PptUploadPhase.FAILED, repo.uploadProgress.value.phase)
    }

    @Test
    fun upload_rejectsTooLargeAndWrongExtensionWithoutRequests() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val repo = testRepo(transport, this)
        val big = repo.upload(
            PptUploadInput("x", FakeSource("big.pptx", PptLimits.MAX_SOURCE_BYTES + 1, byteArrayOf())),
        )
        assertTrue(big is PptUploadResult.Failure)
        assertEquals("文件超过 64 MB，请压缩后再上传", (big as PptUploadResult.Failure).message)

        val wrong = repo.upload(PptUploadInput("x", FakeSource("a.pdf", 10L)))
        assertTrue(wrong is PptUploadResult.Failure)
        assertEquals("仅支持 .ppt / .pptx 文件", (wrong as PptUploadResult.Failure).message)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun upload_withoutSession_failsTokenInvalid() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val repo = testRepo(transport, this, sessionProvider = { null })
        val result = repo.upload(PptUploadInput("x", FakeSource("a.pptx", 10L)))
        assertTrue(result is PptUploadResult.Failure)
        assertTrue((result as PptUploadResult.Failure).tokenInvalid)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun upload_tokenInvalidDuringChunk_noRetry() = runTest {
        val transport = FakeTransport { request ->
            when {
                request.method == HttpMethod.POST && request.url.endsWith("/ppt_decks") -> ApiResponse(201, "[]")
                request.method == HttpMethod.POST && request.url.endsWith("/ppt_upload_chunks") ->
                    ApiResponse(403, "{}")
                request.method == HttpMethod.PATCH -> ApiResponse(409, "{}")
                else -> ApiResponse(404, "")
            }
        }
        val repo = testRepo(transport, this, concurrency = 1)
        val result = repo.upload(PptUploadInput("t", FakeSource("a.pptx", 10L)))
        assertTrue(result is PptUploadResult.Failure)
        result as PptUploadResult.Failure
        assertTrue(result.tokenInvalid)
        // token 失效不重试：3 片各 1 次（第一片失败后协程取消，可能少于 3 次）
        assertTrue(transport.requests.count { it.url.endsWith("/ppt_upload_chunks") } <= 3)
    }

    @Test
    fun upload_cancel_softDeletesDraftRow() = runTest {
        val transport = FakeTransport { request ->
            when {
                request.method == HttpMethod.POST && request.url.endsWith("/ppt_decks") ->
                    ApiResponse(201, "[]")
                request.method == HttpMethod.POST && request.url.endsWith("/ppt_upload_chunks") ->
                    awaitCancellation() // 卡住模拟正在上传
                request.method == HttpMethod.PATCH -> ApiResponse(200, "[]")
                else -> ApiResponse(404, "")
            }
        }
        val repo = testRepo(transport, this)
        val job = launch { repo.upload(PptUploadInput("t", FakeSource("a.pptx", 10L))) }
        advanceUntilIdle()
        assertTrue(transport.requests.any { it.url.endsWith("/ppt_upload_chunks") })
        job.cancelAndJoin()
        val deletePatch = transport.requests.lastOrNull { it.method == HttpMethod.PATCH }
        assertNotNull(deletePatch)
        val body = NetJson.parseToJsonElement(deletePatch!!.body!!).jsonObject
        assertEquals(true, body["deleted"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(PptUploadPhase.CANCELLED, repo.uploadProgress.value.phase)
    }

    // ------------------------------------------------------------ 软删 / 重试

    @Test
    fun softDelete_and_retry_bodies() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val repo = testRepo(transport, this)
        assertTrue(repo.softDelete("dabc123456") is com.furina.classtools.data.network.ApiResult.Success)
        assertTrue(repo.retryConversion("dabc123456") is com.furina.classtools.data.network.ApiResult.Success)

        val deleteBody = NetJson.parseToJsonElement(transport.requests[0].body!!).jsonObject
        assertEquals(true, deleteBody["deleted"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(transport.requests[0].url.contains("id=eq.dabc123456"))

        val retryBody = NetJson.parseToJsonElement(transport.requests[1].body!!).jsonObject
        assertEquals("queued", retryBody["status"]!!.jsonPrimitive.content)
        assertEquals(0, retryBody["progress"]!!.jsonPrimitive.int)
        assertEquals("", retryBody["error"]!!.jsonPrimitive.content)
    }

    @Test
    fun softDelete_emptyIdConfig() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val repo = testRepo(transport, this)
        val result = repo.retryConversion("  ")
        assertTrue(result is com.furina.classtools.data.network.ApiResult.Failure)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun loadPlayer_emptyId() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val repo = testRepo(transport, this)
        assertTrue(repo.loadPlayer(" ") is PptPlayerResult.Failure)
        assertTrue(transport.requests.isEmpty())
    }

    // ------------------------------------------------------------ v2.0 下载详情（§12.4.1）

    private val v2Manifest = """{"v":2,"base":"https://x/ppt/a/d1/b/","w":960,"h":540,"slides":[],
        "files":{"compat":{"s":"compat.pptx","size":18432000,"sha256":"a","ext":"pptx","name":"班会 兼容版.pptx"},
                 "pdf":{"s":"preview.pdf","size":6291456,"sha256":"b","ext":"pdf","name":"班会.pdf","pages":16},
                 "source":{"s":"original.pptx","size":17825792,"sha256":"c","ext":"pptx","name":"班会.pptx"}},
        "compat":{"warnings":["外链视频 1 处未随文件携带（第 7 页）"],"fonts_embedded":2}}"""

    @Test
    fun loadDownloadDetail_compatDeck() = runTest {
        val transport = FakeTransport {
            ApiResponse(
                200,
                """[{"title":"班会","status":"ready","progress":100,"owner_name":"陈子麒",
                       "slide_count":16,"source_size":17825792,"cover_url":"https://x/c.webp",
                       "manifest":$v2Manifest}]""",
            )
        }
        val repo = testRepo(transport, this)
        val result = repo.loadDownloadDetail("dabc123456")
        assertTrue(result is PptDetailResult.Success)
        val detail = (result as PptDetailResult.Success).detail
        assertTrue(detail.isReady)
        assertEquals("陈子麒", detail.ownerName)
        assertEquals(16, detail.slideCount)
        assertTrue(detail.manifest.hasCompatFile)
        assertTrue(detail.manifest.hasPdfFile)
        assertEquals(16, detail.manifest.files!!.pdf!!.pages)
        assertEquals(
            "https://x/ppt/a/d1/b/compat.pptx",
            detail.manifest.fileUrl(detail.manifest.files!!.compat!!),
        )
        assertEquals(1, detail.manifest.compat!!.warnings.size)

        val request = transport.requests.single()
        assertTrue(request.url.contains("select=manifest"))
        assertTrue(request.url.contains("status"))
        assertTrue(request.url.contains("id=eq.dabc123456"))
    }

    @Test
    fun loadDownloadDetail_notReadyAndEmptyId() = runTest {
        val transport = FakeTransport {
            ApiResponse(200, """[{"title":"处理中","status":"processing","progress":61,"manifest":{}}]""")
        }
        val repo = testRepo(transport, this)
        val result = repo.loadDownloadDetail("dabc123456")
        assertTrue(result is PptDetailResult.Success)
        val detail = (result as PptDetailResult.Success).detail
        assertFalse(detail.isReady)
        assertEquals(61, detail.progress)
        assertTrue(detail.manifest.isLegacyDeck)

        assertTrue(repo.loadDownloadDetail("  ") is PptDetailResult.Failure)
    }

    @Test
    fun loadDownloadDetail_tokenInvalid() = runTest {
        val transport = FakeTransport { ApiResponse(403, "{}") }
        val repo = testRepo(transport, this)
        val result = repo.loadDownloadDetail("dabc123456")
        assertTrue(result is PptDetailResult.Failure)
        assertTrue((result as PptDetailResult.Failure).tokenInvalid)
    }

    @Test
    fun loadPlayer_compatDeckWithoutPreview_isNotReady() = runTest {
        val transport = FakeTransport {
            ApiResponse(200, """[{"title":"班会","status":"ready","manifest":$v2Manifest}]""")
        }
        val repo = testRepo(transport, this)
        assertTrue(repo.loadPlayer("dabc123456") is PptPlayerResult.NotReady)
    }

    @Test
    fun loadPlayer_legacyDeckWithSlides_stillPreviewable() = runTest {
        val legacyManifest = """{"v":1,"base":"https://x/b/","slides":[{"steps":[{"img":"s1.webp"}]}]}"""
        val transport = FakeTransport {
            ApiResponse(200, """[{"title":"旧班会","status":"ready","manifest":$legacyManifest}]""")
        }
        val repo = testRepo(transport, this)
        val result = repo.loadPlayer("dabc123456")
        assertTrue(result is PptPlayerResult.Success)
        assertEquals("旧班会", (result as PptPlayerResult.Success).detail.title)
    }
}
