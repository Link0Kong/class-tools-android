package com.furina.classtools.data.cloudspace

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 云空间仓库单测：会话生命周期、增量/全量事件合并、分片上传进度、删除/清空、下载前刷新。
 * 全部走 [FakeCloudSpaceTransport] 与内存字节源，不发真实网络、不使用真实账号 token。
 */
class CloudSpaceRepositoryTest {

    private class FakeByteSource(
        override val name: String,
        private val bytes: ByteArray,
        override val mimeType: String = "application/octet-stream",
    ) : CloudSpaceByteSource {
        override val size: Long get() = bytes.size.toLong()

        override suspend fun read(offset: Long, length: Int): ByteArray {
            val start = offset.toInt()
            if (start >= bytes.size) return ByteArray(0)
            val end = minOf(bytes.size, start + length)
            return bytes.copyOfRange(start, end)
        }
    }

    private fun repository(
        transport: CloudSpaceTransport,
        chunkSize: Int = CloudSpaceDefaults.CHUNK_SIZE,
        nowMillis: Long = 5_000L,
    ): CloudSpaceRepository = CloudSpaceRepository(
        api = testCloudSpaceApi(transport),
        now = { nowMillis },
        chunkSize = chunkSize,
    )

    private fun fileJson(
        id: String,
        name: String = "$id.bin",
        size: Long = 1024L,
        at: Long = 1000L,
        url: String = "https://example.invalid/drop/file/$id",
        expired: Boolean = false,
    ): String = buildString {
        append("""{"fileId":"$id","name":"$name","size":$size,"mime":"application/octet-stream"""")
        if (expired) append(""","expired":true""") else append(""","downloadUrl":"$url"""")
        append("}")
    }

    private fun fileEvent(seq: Long, fileJson: String, at: Long = 1000L): String =
        """{"seq":$seq,"kind":"file","at":$at,"payload":$fileJson}"""

    private fun stateJson(events: String, cursor: Long, sessionId: String = "s1"): String =
        """{"ok":true,"session":{"id":"$sessionId"},"events":[$events],"cursor":$cursor}"""

    /** v2.5：带服务端文件清单 files[] 的 /state 响应 */
    private fun stateAllJson(
        files: List<String>,
        cursor: Long,
        events: String = "",
        expiredNotice: Int = 0,
        sessionId: String = "s1",
    ): String =
        """{"ok":true,"session":{"id":"$sessionId"},"events":[$events],"cursor":$cursor,""" +
            """"files":[${files.joinToString(",")}],"expiredNotice":$expiredNotice}"""

    /** v2.5 files[] 单项（字段名 id / at，与事件 payload 的 fileId 不同） */
    private fun listFileJson(
        id: String,
        name: String = "$id.bin",
        size: Long = 1024L,
        at: Long = 1000L,
    ): String =
        """{"id":"$id","name":"$name","size":$size,"mime":"application/octet-stream",""" +
            """"at":$at,"downloadUrl":"https://example.invalid/drop/file/$id"}"""

    @Test
    fun conversionUsesObservedAttemptAndOnlyDownloadsSameOwnerManifestAssets() = runTest {
        var status = "failed"
        val initial = listFileJson("f", "source.pptx")
        val manifest = """{"files":{"pdf":{"name":"result.pdf","s":"http://example.invalid/drop/ppt-result/s1/f/pdf?sig=qa"},"compat":{"s":"https://other.invalid/x"}},"media_files":[]}"""
        val transport = FakeCloudSpaceTransport { req ->
            val reply = when {
                req.url.endsWith("/mine") -> """{"ok":true,"session":{"id":"s1"}}"""
                req.url.contains("/state?") -> {
                    val item = initial.dropLast(1) + ""","conversion":{"status":"$status","attempt":3}}"""
                    stateAllJson(listOf(item, listFileJson("ordinary", "doc.zip")), 8).dropLast(1) + ""","pptConversion":true}"""
                }
                req.method == CloudSpaceHttpMethod.POST -> {
                    assertTrue(req.body!!.decodeToString().contains(""""retryAttempt":3"""))
                    status = "ready"
                    """{"ok":true}"""
                }
                else -> """{"ok":true,"name":"source.pptx","conversion":{"status":"ready"},"manifest":$manifest}"""
            }
            CloudSpaceResponse(200, reply)
        }
        val repo = repository(transport)
        val first = (repo.ensureSpace() as CloudSpaceResult.Success).value
        assertTrue(first.pptConversion)
        val failed = first.files.first { it.id == "f" }
        assertFalse(first.files.first { it.id == "ordinary" }.canConvert)
        assertTrue(repo.submitConversion(failed) is CloudSpaceResult.Success)
        assertTrue(repo.submitConversion(failed) is CloudSpaceResult.Failure)
        assertEquals(1, transport.requests.count { it.method == CloudSpaceHttpMethod.POST })
        val assets = (repo.assets("f") as CloudSpaceResult.Success).value
        assertEquals(listOf("pdf"), assets.map { it.key })
        assertEquals("test-token", transport.requests.last().headers["X-Device-Id"])
        assertTrue((repo.findFreshFile("f") as CloudSpaceResult.Success).value.downloadUrl.contains("/file/"))
    }

    // ---------------------------------------------------------------- 会话 / 列表

    @Test
    fun `ensureSpace 无会话时 mine open state 三步建连`() = runTest {
        val transport = FakeCloudSpaceTransport { request ->
            when {
                request.url.endsWith("/mine") ->
                    CloudSpaceResponse(200, """{"ok":true,"session":null}""")

                request.url.endsWith("/open") ->
                    CloudSpaceResponse(200, """{"ok":true,"session":{"id":"s1","code":"042317"}}""")

                request.url.contains("/state") -> CloudSpaceResponse(
                    200,
                    stateJson(fileEvent(seq = 4, fileJson = fileJson("f1", name = "讲义.pdf")), cursor = 4),
                )

                else -> CloudSpaceResponse(200, """{"ok":true}""")
            }
        }
        val repository = repository(transport)

        val result = repository.ensureSpace()

        assertTrue(result is CloudSpaceResult.Success)
        val snapshot = (result as CloudSpaceResult.Success).value
        assertEquals("s1", snapshot.session?.id)
        assertEquals(1, snapshot.count)
        assertEquals("讲义.pdf", snapshot.files[0].name)
        assertEquals(listOf("/mine", "/open", "/state?sessionId=s1&all=1&ppt=1&since=0"), transport.paths())
    }

    @Test
    fun `refresh 增量轮询带 since 且 filedel 移除文件`() = runTest {
        var stateCalls = 0
        val transport = FakeCloudSpaceTransport { request ->
            when {
                request.url.endsWith("/mine") ->
                    CloudSpaceResponse(200, """{"ok":true,"session":{"id":"s1"}}""")

                request.url.contains("/state") -> {
                    stateCalls += 1
                    if (stateCalls == 1) {
                        CloudSpaceResponse(
                            200,
                            stateJson(
                                fileEvent(seq = 1, fileJson = fileJson("keep")) + "," +
                                    fileEvent(seq = 2, fileJson = fileJson("drop"), at = 900L),
                                cursor = 2,
                            ),
                        )
                    } else {
                        CloudSpaceResponse(
                            200,
                            """{"ok":true,"session":{"id":"s1"},"events":[
                                {"seq":3,"kind":"filedel","at":1100,"payload":{"fileId":"drop"}}
                            ],"cursor":3}""",
                        )
                    }
                }

                else -> CloudSpaceResponse(200, """{"ok":true}""")
            }
        }
        val repository = repository(transport)

        repository.ensureSpace()
        val result = repository.refresh(force = false)

        assertTrue(result is CloudSpaceResult.Success)
        val snapshot = (result as CloudSpaceResult.Success).value
        assertEquals(listOf("keep"), snapshot.files.map { it.id })
        assertEquals("/state?sessionId=s1&all=1&ppt=1&since=2", transport.paths().last())
    }

    @Test
    fun `refresh 优先使用服务端文件清单（事件为空也看得到文件）`() = runTest {
        var stateCalls = 0
        val transport = FakeCloudSpaceTransport { request ->
            when {
                request.url.endsWith("/mine") ->
                    CloudSpaceResponse(200, """{"ok":true,"session":{"id":"s1"}}""")

                request.url.contains("/state") -> {
                    stateCalls += 1
                    if (stateCalls == 1) {
                        CloudSpaceResponse(
                            200,
                            stateAllJson(files = listOf(listFileJson("old", at = 100L)), cursor = 5),
                        )
                    } else {
                        // 增量轮询：since 之后没有新事件（事件表已截断），但服务端清单仍有 2 个文件
                        CloudSpaceResponse(
                            200,
                            stateAllJson(
                                files = listOf(listFileJson("new", at = 300L), listFileJson("old", at = 100L)),
                                cursor = 9,
                                expiredNotice = 2,
                            ),
                        )
                    }
                }

                else -> CloudSpaceResponse(200, """{"ok":true}""")
            }
        }
        val repository = repository(transport)
        repository.ensureSpace()

        val result = repository.refresh(force = false)

        assertTrue(result is CloudSpaceResult.Success)
        val snapshot = (result as CloudSpaceResult.Success).value
        assertEquals(listOf("new", "old"), snapshot.files.map { it.id })
        assertEquals(2, snapshot.expiredCount)
        assertTrue(transport.paths().last().contains("all=1"))
    }

    @Test
    fun `refresh 失败保留上次列表`() = runTest {        var failState = false
        val transport = FakeCloudSpaceTransport { request ->
            when {
                request.url.endsWith("/mine") ->
                    CloudSpaceResponse(200, """{"ok":true,"session":{"id":"s1"}}""")

                request.url.contains("/state") -> {
                    if (failState) throw IOException("boom")
                    CloudSpaceResponse(200, stateJson(fileEvent(1, fileJson("f1")), cursor = 1))
                }

                else -> CloudSpaceResponse(200, """{"ok":true}""")
            }
        }
        val repository = repository(transport)
        repository.ensureSpace()
        failState = true

        val result = repository.refresh(force = true)

        assertTrue(result is CloudSpaceResult.Failure)
        assertTrue((result as CloudSpaceResult.Failure).error is CloudSpaceError.Network)
        assertEquals(1, repository.snapshot().count)
    }

    // ---------------------------------------------------------------- 上传

    @Test
    fun `上传按 8MiB 分片规划逐片发送并回调进度`() = runTest {
        val chunkBodies = mutableListOf<ByteArray>()
        val transport = FakeCloudSpaceTransport { request ->
            when {
                request.url.endsWith("/mine") ->
                    CloudSpaceResponse(200, """{"ok":true,"session":{"id":"s1"}}""")

                request.url.endsWith("/upload/init") ->
                    CloudSpaceResponse(200, """{"ok":true,"uploadId":"up1","chunkSize":4}""")

                request.url.contains("/upload/chunk") -> {
                    chunkBodies.add(request.body ?: ByteArray(0))
                    CloudSpaceResponse(200, """{"ok":true,"received":4}""")
                }

                request.url.endsWith("/upload/finish") -> CloudSpaceResponse(
                    200,
                    """{"ok":true,"file":{"id":"f1","name":"a.bin","size":10,
                         "mime":"application/octet-stream","downloadUrl":"https://example.invalid/drop/file/f1"}}""",
                )

                else -> CloudSpaceResponse(200, """{"ok":true}""")
            }
        }
        val repository = repository(transport, chunkSize = 4)
        val progresses = mutableListOf<CloudSpaceUploadProgress>()

        val result = repository.upload(
            FakeByteSource("a.bin", byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)),
            onProgress = { progresses.add(it) },
        )

        assertTrue(result is CloudSpaceResult.Success)
        val uploaded = (result as CloudSpaceResult.Success).value
        assertEquals("f1", uploaded.id)
        assertEquals(5_000L, uploaded.uploadedAt)
        assertEquals(1, repository.snapshot().count)
        assertEquals(
            listOf("/mine", "/upload/init", "/upload/chunk?uploadId=up1&index=0",
                "/upload/chunk?uploadId=up1&index=1", "/upload/chunk?uploadId=up1&index=2",
                "/upload/finish"),
            transport.paths(),
        )
        assertEquals(3, chunkBodies.size)
        assertTrue(chunkBodies[0].contentEquals(byteArrayOf(0, 1, 2, 3)))
        assertTrue(chunkBodies[1].contentEquals(byteArrayOf(4, 5, 6, 7)))
        assertTrue(chunkBodies[2].contentEquals(byteArrayOf(8, 9)))
        transport.requests.filter { it.url.contains("/upload/chunk") }.forEach {
            assertEquals("application/octet-stream", it.headers["Content-Type"])
            assertEquals("test-token", it.headers["X-Device-Id"])
        }
        // 进度：首个 WAITING、至少一次 UPLOADING、最后 DONE
        assertEquals(CloudSpaceUploadState.WAITING, progresses.first().state)
        assertTrue(progresses.any { it.state == CloudSpaceUploadState.UPLOADING })
        assertEquals(CloudSpaceUploadState.DONE, progresses.last().state)
        assertTrue(progresses.last().sentBytes == 10L)
    }

    @Test
    fun `分片上传失败立即停止且不调用 finish`() = runTest {
        var finishCalled = false
        val transport = FakeCloudSpaceTransport { request ->
            when {
                request.url.endsWith("/mine") ->
                    CloudSpaceResponse(200, """{"ok":true,"session":{"id":"s1"}}""")

                request.url.endsWith("/upload/init") ->
                    CloudSpaceResponse(200, """{"ok":true,"uploadId":"up1","chunkSize":4}""")

                request.url.contains("/upload/chunk") ->
                    CloudSpaceResponse(507, """{"ok":false,"reason":"服务器空间不足，请稍后再试"}""")

                request.url.endsWith("/upload/finish") -> {
                    finishCalled = true
                    CloudSpaceResponse(200, """{"ok":true}""")
                }

                else -> CloudSpaceResponse(200, """{"ok":true}""")
            }
        }
        val repository = repository(transport, chunkSize = 4)
        val progresses = mutableListOf<CloudSpaceUploadProgress>()

        val result = repository.upload(
            FakeByteSource("a.bin", ByteArray(10)),
            onProgress = { progresses.add(it) },
        )

        assertTrue(result is CloudSpaceResult.Failure)
        val error = (result as CloudSpaceResult.Failure).error
        assertEquals(507, (error as CloudSpaceError.Http).status)
        assertEquals("服务器空间不足，请稍后再试", error.displayMessage)
        assertFalse(finishCalled)
        assertEquals(0, repository.snapshot().count)
        assertEquals(CloudSpaceUploadState.FAILED, progresses.last().state)
    }

    @Test
    fun `空文件拒绝上传且不发任何请求`() = runTest {
        val transport = FakeCloudSpaceTransport()
        val repository = repository(transport)

        val result = repository.upload(FakeByteSource("empty.txt", ByteArray(0)))

        assertTrue(result is CloudSpaceResult.Failure)
        assertTrue((result as CloudSpaceResult.Failure).error is CloudSpaceError.Local)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `读取字节不足时失败`() = runTest {
        val transport = FakeCloudSpaceTransport { request ->
            when {
                request.url.endsWith("/mine") ->
                    CloudSpaceResponse(200, """{"ok":true,"session":{"id":"s1"}}""")

                request.url.endsWith("/upload/init") ->
                    CloudSpaceResponse(200, """{"ok":true,"uploadId":"up1","chunkSize":4}""")

                else -> CloudSpaceResponse(200, """{"ok":true}""")
            }
        }
        val repository = repository(transport, chunkSize = 4)
        // 声明 10 字节，但实际只能读到 2 字节（模拟文件被截断/替换）
        val truncated = object : CloudSpaceByteSource {
            override val name: String = "a.bin"
            override val size: Long = 10L
            override val mimeType: String = "application/octet-stream"
            override suspend fun read(offset: Long, length: Int): ByteArray =
                if (offset == 0L) ByteArray(2) else ByteArray(0)
        }

        val result = repository.upload(truncated)

        assertTrue(result is CloudSpaceResult.Failure)
        assertTrue((result as CloudSpaceResult.Failure).error is CloudSpaceError.Local)
        assertTrue(transport.requests.none { it.url.contains("/upload/chunk") })
    }

    // ---------------------------------------------------------------- 删除 / 清空

    @Test
    fun `delete 调用 DELETE 并从列表移除`() = runTest {
        val transport = FakeCloudSpaceTransport { request ->
            when {
                request.url.endsWith("/mine") ->
                    CloudSpaceResponse(200, """{"ok":true,"session":{"id":"s1"}}""")

                request.url.contains("/state") ->
                    CloudSpaceResponse(200, stateJson(fileEvent(1, fileJson("f1")), cursor = 1))

                request.url.contains("/file/f1") ->
                    CloudSpaceResponse(200, """{"ok":true,"deleted":true,"name":"f1.bin","size":1024}""")

                else -> CloudSpaceResponse(200, """{"ok":true}""")
            }
        }
        val repository = repository(transport)
        repository.ensureSpace()

        val result = repository.delete("f1")

        assertTrue(result is CloudSpaceResult.Success)
        assertEquals(0, (result as CloudSpaceResult.Success).value.count)
        assertEquals(CloudSpaceHttpMethod.DELETE, transport.lastRequest().method)
        assertEquals("/file/f1?sessionId=s1", transport.paths().last())
    }

    @Test
    fun `clearSpace 关闭会话后自动重开新空间`() = runTest {
        var closed = false
        val transport = FakeCloudSpaceTransport { request ->
            when {
                request.url.endsWith("/mine") ->
                    if (closed) {
                        CloudSpaceResponse(200, """{"ok":true,"session":null}""")
                    } else {
                        CloudSpaceResponse(200, """{"ok":true,"session":{"id":"s1"}}""")
                    }

                request.url.endsWith("/open") ->
                    CloudSpaceResponse(200, """{"ok":true,"session":{"id":"s2"}}""")

                request.url.contains("/state") -> CloudSpaceResponse(
                    200,
                    stateJson("", cursor = 0, sessionId = if (closed) "s2" else "s1"),
                )

                request.url.endsWith("/close") -> {
                    closed = true
                    CloudSpaceResponse(200, """{"ok":true}""")
                }

                else -> CloudSpaceResponse(200, """{"ok":true}""")
            }
        }
        val repository = repository(transport)
        repository.ensureSpace()

        val result = repository.clearSpace()

        assertTrue(result is CloudSpaceResult.Success)
        val snapshot = (result as CloudSpaceResult.Success).value
        assertEquals("s2", snapshot.session?.id)
        assertEquals(0, snapshot.count)
        assertTrue(transport.paths().contains("/close"))
        assertTrue(transport.paths().contains("/open"))
    }

    // ---------------------------------------------------------------- 下载前刷新

    @Test
    fun `findFreshFile 返回新签名下载链接`() = runTest {
        val transport = FakeCloudSpaceTransport { request ->
            when {
                request.url.endsWith("/mine") ->
                    CloudSpaceResponse(200, """{"ok":true,"session":{"id":"s1"}}""")

                request.url.contains("/state") -> CloudSpaceResponse(
                    200,
                    stateJson(
                        fileEvent(
                            1,
                            fileJson("f1", url = "https://example.invalid/drop/file/f1?exp=2&sig=new"),
                        ),
                        cursor = 1,
                    ),
                )

                else -> CloudSpaceResponse(200, """{"ok":true}""")
            }
        }
        val repository = repository(transport)

        val result = repository.findFreshFile("f1")

        assertTrue(result is CloudSpaceResult.Success)
        val file = (result as CloudSpaceResult.Success).value
        assertTrue(file.downloadUrl.contains("sig=new"))
        // findFreshFile 内部强制全量刷新（since=0）
        assertEquals("/state?sessionId=s1&all=1&ppt=1&since=0", transport.paths().last())
    }

    @Test
    fun `findFreshFile 对已过期文件返回中文错误`() = runTest {
        val transport = FakeCloudSpaceTransport { request ->
            when {
                request.url.endsWith("/mine") ->
                    CloudSpaceResponse(200, """{"ok":true,"session":{"id":"s1"}}""")

                request.url.contains("/state") -> CloudSpaceResponse(
                    200,
                    stateJson(fileEvent(1, fileJson("f1", expired = true)), cursor = 1),
                )

                else -> CloudSpaceResponse(200, """{"ok":true}""")
            }
        }
        val repository = repository(transport)

        val result = repository.findFreshFile("f1")

        assertTrue(result is CloudSpaceResult.Failure)
        assertTrue((result as CloudSpaceResult.Failure).error.displayMessage.contains("过期"))
        assertEquals(1, repository.snapshot().expiredCount)
    }

    @Test
    fun `reset 清空内存状态`() = runTest {
        val transport = FakeCloudSpaceTransport { request ->
            when {
                request.url.endsWith("/mine") ->
                    CloudSpaceResponse(200, """{"ok":true,"session":{"id":"s1"}}""")

                request.url.contains("/state") ->
                    CloudSpaceResponse(200, stateJson(fileEvent(1, fileJson("f1")), cursor = 1))

                else -> CloudSpaceResponse(200, """{"ok":true}""")
            }
        }
        val repository = repository(transport)
        repository.ensureSpace()

        repository.reset()

        assertEquals(0, repository.snapshot().count)
        assertEquals(false, repository.snapshot().connected)
    }
}
