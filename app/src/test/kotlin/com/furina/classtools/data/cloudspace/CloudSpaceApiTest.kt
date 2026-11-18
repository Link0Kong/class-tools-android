package com.furina.classtools.data.cloudspace

import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `/drop` API 客户端单测：请求构造（方法/URL/头/JSON body/原始分片）、响应解析、错误映射。
 * 全部走 [FakeCloudSpaceTransport]，不发真实网络、不使用任何真实账号 token。
 */
class CloudSpaceApiTest {

    private fun jsonObject(text: String): JsonObject =
        Json.parseToJsonElement(text) as JsonObject

    @Test
    fun `GET mine 构造 URL 与请求头且不带 Content-Type`() = runTest {
        val transport = FakeCloudSpaceTransport { CloudSpaceResponse(200, """{"ok":true,"session":null}""") }
        val api = testCloudSpaceApi(transport)

        val result = api.mine()

        assertTrue(result is CloudSpaceResult.Success)
        assertNull((result as CloudSpaceResult.Success).value)
        val request = transport.lastRequest()
        assertEquals(CloudSpaceHttpMethod.GET, request.method)
        assertEquals("http://example.invalid/drop/mine", request.url)
        assertEquals("test-token", request.headers["X-Device-Id"])
        assertEquals("application/json", request.headers["Accept"])
        assertEquals("no-store", request.headers["Cache-Control"])
        assertNull(request.headers["Content-Type"])
        assertNull(request.body)
    }

    @Test
    fun `baseUrl 结尾多余斜杠被规范化`() = runTest {
        val transport = FakeCloudSpaceTransport { CloudSpaceResponse(200, """{"ok":true}""") }
        val api = testCloudSpaceApi(transport, baseUrl = "http://example.invalid/drop///")

        api.mine()

        assertEquals("http://example.invalid/drop/mine", transport.lastRequest().url)
    }

    @Test
    fun `无 token 时不发 X-Device-Id`() = runTest {
        val transport = FakeCloudSpaceTransport { CloudSpaceResponse(200, """{"ok":true,"session":null}""") }
        val api = testCloudSpaceApi(transport, token = null)

        api.mine()

        assertNull(transport.lastRequest().headers["X-Device-Id"])
    }

    @Test
    fun `open 用 POST 空 JSON 并解析 session`() = runTest {
        val transport = FakeCloudSpaceTransport {
            CloudSpaceResponse(
                200,
                """{"ok":true,"session":{"id":"1a2b3c4d5e6f7a8b","code":"042317","createdAt":1762860000000,"expiresAt":1763119200000}}""",
            )
        }
        val api = testCloudSpaceApi(transport)

        val result = api.openSpace()

        assertTrue(result is CloudSpaceResult.Success)
        val session = (result as CloudSpaceResult.Success).value
        assertEquals("1a2b3c4d5e6f7a8b", session.id)
        assertEquals("042317", session.code)
        assertEquals(1762860000000L, session.createdAt)
        val request = transport.lastRequest()
        assertEquals(CloudSpaceHttpMethod.POST, request.method)
        assertEquals("http://example.invalid/drop/open", request.url)
        assertEquals("application/json", request.headers["Content-Type"])
        assertEquals("{}", transport.lastBodyText())
    }

    @Test
    fun `state 解析 file 与 filedel 事件并忽略 text`() = runTest {
        val body = """
            {"ok":true,
             "session":{"id":"s1","code":"000001","createdAt":1,"expiresAt":2},
             "events":[
               {"seq":3,"kind":"text","at":10,"from":{"name":"测试账号"},"payload":{"text":"你好"}},
               {"seq":4,"kind":"file","at":1762860002234,"from":{"name":"测试账号"},
                "payload":{"fileId":"f1","name":"讲义.pdf","size":1048576,"mime":"application/pdf",
                           "downloadUrl":"http://121.196.47.2/drop/file/f1?s=x&exp=1&sig=y"}},
               {"seq":5,"kind":"file","at":1762860003234,"payload":{"fileId":"f2","name":"旧资料.zip",
                "size":10,"mime":"application/zip","expired":true}},
               {"seq":6,"kind":"filedel","at":1762860004234,"payload":{"fileId":"f3","name":"x","size":1}}
             ],
             "cursor":6}
        """.trimIndent()
        val transport = FakeCloudSpaceTransport { CloudSpaceResponse(200, body) }
        val api = testCloudSpaceApi(transport)

        val result = api.state("s1", since = 0)

        assertTrue(result is CloudSpaceResult.Success)
        val state = (result as CloudSpaceResult.Success).value
        assertEquals("s1", state.session?.id)
        assertEquals(6L, state.cursor)
        assertEquals(3, state.events.size)

        val added = state.events.filterIsInstance<CloudSpaceEvent.FileAdded>()
        assertEquals(2, added.size)
        val first = added[0].file
        assertEquals("f1", first.id)
        assertEquals("讲义.pdf", first.name)
        assertEquals(1048576L, first.sizeBytes)
        assertEquals("application/pdf", first.mimeType)
        assertTrue(first.downloadUrl.startsWith("http://121.196.47.2/drop/file/f1"))
        assertFalse(first.expired)
        assertEquals(1762860002234L, first.uploadedAt)
        // 过期文件：保留事件但清空下载链接
        assertEquals("f2", added[1].file.id)
        assertTrue(added[1].file.expired)
        assertEquals("", added[1].file.downloadUrl)

        val deleted = state.events.filterIsInstance<CloudSpaceEvent.FileDeleted>()
        assertEquals(1, deleted.size)
        assertEquals("f3", deleted[0].fileId)

        assertEquals(
            "/state?sessionId=s1&all=1&since=0",
            transport.pathOf(0),
        )
    }

    @Test
    fun `state all=1 解析服务端文件清单`() = runTest {
        val body = """
            {"ok":true,
             "session":{"id":"s1","code":"000001","createdAt":1,"expiresAt":2},
             "events":[],
             "cursor":42,
             "files":[
               {"id":"f9","name":"清单也有.txt","size":10,"mime":"text/plain",
                "at":1762860009234,"downloadUrl":"http://x/drop/file/f9?s=x&exp=1&sig=y"},
               {"id":"f8","name":"已过期.bin","size":5,"mime":"","at":1762860000234,"expired":true}
             ],
             "expiredNotice":3}
        """.trimIndent()
        val transport = FakeCloudSpaceTransport { CloudSpaceResponse(200, body) }
        val api = testCloudSpaceApi(transport)

        val result = api.state("s1", since = 42)

        assertTrue(result is CloudSpaceResult.Success)
        val state = (result as CloudSpaceResult.Success).value
        assertEquals(0, state.events.size)
        val files = state.files ?: error("files 未解析")
        assertEquals(1, files.size)
        assertEquals("f9", files[0].id)
        assertEquals(1762860009234L, files[0].uploadedAt)
        assertEquals(3, state.expiredCount)
        assertEquals("/state?sessionId=s1&all=1&since=42", transport.pathOf(0))
    }

    @Test
    fun `state since 参数用于增量轮询`() = runTest {
        val transport = FakeCloudSpaceTransport {
            CloudSpaceResponse(200, """{"ok":true,"session":{"id":"s1"},"events":[],"cursor":9}""")
        }
        val api = testCloudSpaceApi(transport)

        api.state("s1", since = 9)

        assertEquals("/state?sessionId=s1&all=1&since=9", transport.pathOf(0))
    }

    @Test
    fun `initUpload 构造 JSON 字段并解析 uploadId 与 chunkSize`() = runTest {
        val transport = FakeCloudSpaceTransport {
            CloudSpaceResponse(200, """{"ok":true,"uploadId":"up-1","chunkSize":8388608}""")
        }
        val api = testCloudSpaceApi(transport)

        val result = api.initUpload(
            sessionId = "s1",
            name = "讲义.pdf",
            size = 20_971_520L,
            mimeType = "application/pdf",
            totalChunks = 3,
        )

        assertTrue(result is CloudSpaceResult.Success)
        val init = (result as CloudSpaceResult.Success).value
        assertEquals("up-1", init.uploadId)
        assertEquals(8_388_608, init.chunkSize)
        val request = transport.lastRequest()
        assertEquals(CloudSpaceHttpMethod.POST, request.method)
        assertEquals("http://example.invalid/drop/upload/init", request.url)
        assertEquals("application/json", request.headers["Content-Type"])
        val body = jsonObject(transport.lastBodyText())
        assertEquals("s1", body["sessionId"]!!.jsonPrimitive.contentOrNull)
        assertEquals("讲义.pdf", body["name"]!!.jsonPrimitive.contentOrNull)
        assertEquals(20_971_520L, body["size"]!!.jsonPrimitive.long)
        assertEquals("application/pdf", body["mime"]!!.jsonPrimitive.contentOrNull)
        assertEquals(3, body["totalChunks"]!!.jsonPrimitive.int)
    }

    @Test
    fun `uploadChunk 发送原始字节与 octet-stream`() = runTest {
        val transport = FakeCloudSpaceTransport {
            CloudSpaceResponse(200, """{"ok":true,"received":1048576}""")
        }
        val api = testCloudSpaceApi(transport)
        val bytes = byteArrayOf(1, 2, 3, 4, 5)

        val result = api.uploadChunk(uploadId = "up-1", index = 2, bytes = bytes)

        assertTrue(result is CloudSpaceResult.Success)
        assertEquals(1048576L, (result as CloudSpaceResult.Success).value)
        val request = transport.lastRequest()
        assertEquals(CloudSpaceHttpMethod.POST, request.method)
        assertEquals("application/octet-stream", request.headers["Content-Type"])
        assertEquals("/upload/chunk?uploadId=up-1&index=2", transport.pathOf(0))
        assertTrue(request.body!!.contentEquals(bytes))
        // 原始字节不经过 JSON/base64 包装
        assertFalse(transport.lastBodyText().contains("received"))
        assertEquals("test-token", request.headers["X-Device-Id"])
    }

    @Test
    fun `uploadChunk 对 uploadId 做 URL 编码`() = runTest {
        val transport = FakeCloudSpaceTransport {
            CloudSpaceResponse(200, """{"ok":true,"received":0}""")
        }
        val api = testCloudSpaceApi(transport)

        api.uploadChunk(uploadId = "a b/c", index = 1, bytes = byteArrayOf(7))

        assertEquals("/upload/chunk?uploadId=a+b%2Fc&index=1", transport.pathOf(0))
    }

    @Test
    fun `finishUpload 解析 file 的 id 字段`() = runTest {
        val transport = FakeCloudSpaceTransport {
            CloudSpaceResponse(
                200,
                """{"ok":true,"file":{"id":"f9","name":"讲义.pdf","size":1048576,
                     "mime":"application/pdf","downloadUrl":"http://121.196.47.2/drop/file/f9?s=x"}}""",
            )
        }
        val api = testCloudSpaceApi(transport)

        val result = api.finishUpload(sessionId = "s1", uploadId = "up-1")

        assertTrue(result is CloudSpaceResult.Success)
        val file = (result as CloudSpaceResult.Success).value
        assertEquals("f9", file.id)
        assertEquals("讲义.pdf", file.name)
        assertFalse(file.expired)
        assertTrue(file.downloadUrl.isNotBlank())
        val body = jsonObject(transport.lastBodyText())
        assertEquals("s1", body["sessionId"]!!.jsonPrimitive.contentOrNull)
        assertEquals("up-1", body["uploadId"]!!.jsonPrimitive.contentOrNull)
    }

    @Test
    fun `deleteFile 用 DELETE 且带 sessionId 查询`() = runTest {
        val transport = FakeCloudSpaceTransport {
            CloudSpaceResponse(200, """{"ok":true,"deleted":true,"name":"a.pdf","size":12}""")
        }
        val api = testCloudSpaceApi(transport)

        val result = api.deleteFile(fileId = "f1", sessionId = "s1")

        assertTrue(result is CloudSpaceResult.Success)
        assertTrue((result as CloudSpaceResult.Success).value)
        assertEquals(CloudSpaceHttpMethod.DELETE, transport.lastRequest().method)
        assertEquals("/file/f1?sessionId=s1", transport.pathOf(0))
        assertNull(transport.lastRequest().body)
    }

    @Test
    fun `deleteFile 幂等返回 deleted false 仍算成功`() = runTest {
        val transport = FakeCloudSpaceTransport {
            CloudSpaceResponse(200, """{"ok":true,"deleted":false}""")
        }
        val api = testCloudSpaceApi(transport)

        val result = api.deleteFile(fileId = "f1", sessionId = "s1")

        assertTrue(result is CloudSpaceResult.Success)
        assertFalse((result as CloudSpaceResult.Success).value)
    }

    @Test
    fun `closeSpace 构造 sessionId body`() = runTest {
        val transport = FakeCloudSpaceTransport { CloudSpaceResponse(200, """{"ok":true}""") }
        val api = testCloudSpaceApi(transport)

        val result = api.closeSpace("s1")

        assertTrue(result is CloudSpaceResult.Success)
        assertEquals("/close", transport.pathOf(0))
        val body = jsonObject(transport.lastBodyText())
        assertEquals("s1", body["sessionId"]!!.jsonPrimitive.contentOrNull)
    }

    // ---------------------------------------------------------------- 错误映射

    @Test
    fun `401 映射 TokenInvalid 并触发回调`() = runTest {
        var unauthorized = false
        val transport = FakeCloudSpaceTransport {
            CloudSpaceResponse(401, """{"ok":false,"reason":"身份无效"}""")
        }
        val api = testCloudSpaceApi(transport, onUnauthorized = { unauthorized = true })

        val result = api.mine()

        assertTrue(result is CloudSpaceResult.Failure)
        val error = (result as CloudSpaceResult.Failure).error
        assertTrue(error is CloudSpaceError.TokenInvalid)
        assertEquals("身份无效", error.displayMessage)
        assertTrue(unauthorized)
    }

    @Test
    fun `507 优先使用服务端中文 reason`() = runTest {
        val transport = FakeCloudSpaceTransport {
            CloudSpaceResponse(507, """{"ok":false,"reason":"服务器空间不足，请稍后再试"}""")
        }
        val api = testCloudSpaceApi(transport)

        val error = (api.initUpload("s1", "a.bin", 1L, "application/octet-stream", 1)
            as CloudSpaceResult.Failure).error

        assertTrue(error is CloudSpaceError.Http)
        assertEquals(507, (error as CloudSpaceError.Http).status)
        assertEquals("服务器空间不足，请稍后再试", error.displayMessage)
    }

    @Test
    fun `413 无 reason 时给默认中文文案`() = runTest {
        val transport = FakeCloudSpaceTransport { CloudSpaceResponse(413, "") }
        val api = testCloudSpaceApi(transport)

        val error = (api.mine() as CloudSpaceResult.Failure).error

        assertTrue(error is CloudSpaceError.Http)
        assertEquals(CloudSpaceErrors.defaultReason(413), error.displayMessage)
        assertTrue(error.displayMessage.isNotBlank())
    }

    @Test
    fun `404 与 503 有可读中文默认文案`() = runTest {
        assertTrue(CloudSpaceErrors.defaultReason(404).contains("不存在"))
        assertTrue(CloudSpaceErrors.defaultReason(503).contains("暂不可用"))
        assertTrue(CloudSpaceErrors.defaultReason(507).contains("空间不足"))
        assertTrue(CloudSpaceErrors.defaultReason(999).contains("HTTP 999"))
    }

    @Test
    fun `网络异常映射 Network 且文案不含异常信息`() = runTest {
        val transport = FakeCloudSpaceTransport { throw IOException("http://secret-token@host/drop") }
        val api = testCloudSpaceApi(transport, token = "secret-token")

        val error = (api.mine() as CloudSpaceResult.Failure).error

        assertTrue(error is CloudSpaceError.Network)
        assertFalse(error.displayMessage.contains("secret-token"))
        assertFalse(error.displayMessage.contains("http"))
    }

    @Test
    fun `2xx 坏 JSON 映射 Parse`() = runTest {
        val transport = FakeCloudSpaceTransport { CloudSpaceResponse(200, "not-json") }
        val api = testCloudSpaceApi(transport)

        val error = (api.mine() as CloudSpaceResult.Failure).error

        assertTrue(error is CloudSpaceError.Parse)
    }

    @Test
    fun `HTTP 200 ok false 用 reason 拒绝`() = runTest {
        val transport = FakeCloudSpaceTransport {
            CloudSpaceResponse(200, """{"ok":false,"reason":"会话不存在"}""")
        }
        val api = testCloudSpaceApi(transport)

        val error = (api.state("missing") as CloudSpaceResult.Failure).error

        assertEquals("会话不存在", error.displayMessage)
    }

    @Test
    fun `open 响应缺少 session 映射 Parse`() = runTest {
        val transport = FakeCloudSpaceTransport { CloudSpaceResponse(200, """{"ok":true}""") }
        val api = testCloudSpaceApi(transport)

        val error = (api.openSpace() as CloudSpaceResult.Failure).error

        assertTrue(error is CloudSpaceError.Parse)
    }

    // ---------------------------------------------------------------- 安全红线

    @Test
    fun `token 只出现在 X-Device-Id 不出现在 URL 请求体与错误文案`() = runTest {
        val token = "TOKEN_SHOULD_NOT_LEAK_9f2c"
        val transport = FakeCloudSpaceTransport { request ->
            when {
                request.url.endsWith("/mine") ->
                    CloudSpaceResponse(200, """{"ok":true,"session":{"id":"s1"}}""")

                request.url.contains("/state") ->
                    CloudSpaceResponse(200, """{"ok":true,"session":{"id":"s1"},"events":[],"cursor":0}""")

                request.url.endsWith("/upload/init") ->
                    CloudSpaceResponse(200, """{"ok":true,"uploadId":"up-1","chunkSize":4}""")

                request.url.contains("/upload/chunk") ->
                    CloudSpaceResponse(200, """{"ok":true,"received":4}""")

                request.url.endsWith("/upload/finish") ->
                    CloudSpaceResponse(
                        200,
                        """{"ok":true,"file":{"id":"f1","name":"a.bin","size":4,"mime":"application/octet-stream","downloadUrl":"http://x/f1"}}""",
                    )

                request.url.contains("/file/") -> CloudSpaceResponse(200, """{"ok":true,"deleted":true}""")

                else -> CloudSpaceResponse(503, """{"ok":false,"reason":"鉴权服务暂不可用"}""")
            }
        }
        val api = testCloudSpaceApi(transport, token = token)

        api.mine()
        api.state("s1")
        api.initUpload("s1", "a.bin", 4L, "application/octet-stream", 1)
        api.uploadChunk("up-1", 0, byteArrayOf(1, 2, 3, 4))
        api.finishUpload("s1", "up-1")
        api.deleteFile("f1", "s1")
        api.closeSpace("s1")

        transport.requests.forEach { request ->
            assertFalse("URL 泄漏 token: ${request.url}", request.url.contains(token))
            val bodyText = request.body?.toString(Charsets.UTF_8).orEmpty()
            assertFalse("请求体泄漏 token", bodyText.contains(token))
            assertEquals(token, request.headers["X-Device-Id"])
        }
        val failure = api.closeSpace("s1") as CloudSpaceResult.Failure
        assertFalse(failure.error.displayMessage.contains(token))
    }

    @Test
    fun `未配置 baseUrl 时直接失败且不发请求`() = runTest {
        val transport = FakeCloudSpaceTransport()
        val api = testCloudSpaceApi(transport, baseUrl = "  ")

        val error = (api.mine() as CloudSpaceResult.Failure).error

        assertTrue(error is CloudSpaceError.Local)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `tokenProvider 每次请求实时读取`() = runTest {
        var token: String? = "first"
        val transport = FakeCloudSpaceTransport { CloudSpaceResponse(200, """{"ok":true,"session":null}""") }
        val api = CloudSpaceApi(
            baseUrl = "http://example.invalid/drop",
            tokenProvider = { token },
            transport = transport,
        )

        api.mine()
        token = "second"
        api.mine()

        assertEquals("first", transport.requests[0].headers["X-Device-Id"])
        assertEquals("second", transport.requests[1].headers["X-Device-Id"])
        assertNotNull(transport.requests[1].headers["Accept"])
    }
}
