package com.furina.classtools.data.notices

import java.io.File
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 附件「下载/解码 → 缓存文件」编排单测（真实网络用假 fetcher/sink 替代，纯 JVM）。
 *
 * 覆盖：外置 https URL（下载 + 磁盘缓存命中）、旧数据 data URI（不联网解码）、
 * 超限、网络失败、不可识别、写盘失败。
 */
class NoticeAttachmentFilesTest {

    private val signedUrl = "https://tool.furina.fun/att/6f1a2b3c4d5e6f708192a3b4c5d6e7f8?exp=1799999999&sig=" +
        "a".repeat(64)

    /** 假抓取器：记录调用参数并返回预设结果。 */
    private class FakeFetcher(
        private val result: NoticeAttachmentFetch = NoticeAttachmentFetch.Failed,
    ) : NoticeAttachmentFetcher {
        var lastUrl: String? = null
        var lastMaxBytes: Long = 0L
        var calls = 0

        override suspend fun fetch(url: String, maxBytes: Long): NoticeAttachmentFetch {
            calls += 1
            lastUrl = url
            lastMaxBytes = maxBytes
            return result
        }
    }

    /** 假落盘：内存记录写入；可预置磁盘缓存。 */
    private class FakeSink : NoticeAttachmentSink {
        val written = LinkedHashMap<String, ByteArray>()
        val cache = LinkedHashMap<String, File>()
        var failWrite = false

        override fun cached(key: String, fileName: String): File? = cache["$key/$fileName"]

        override fun write(key: String, fileName: String, bytes: ByteArray): File? {
            if (failWrite) return null
            written["$key/$fileName"] = bytes
            return File("/fake-cache/$key/$fileName")
        }
    }

    private fun preparer(
        fetcher: NoticeAttachmentFetcher,
        sink: NoticeAttachmentSink,
        maxUrlBytes: Long = NoticeAttachmentLimits.MAX_URL_BYTES,
        maxInlineBytes: Long = NoticeAttachmentLimits.MAX_INLINE_BYTES,
    ): NoticeAttachmentPreparer = NoticeAttachmentPreparer(fetcher, sink, maxUrlBytes, maxInlineBytes)

    // ---- 外置签名 URL（新数据） ---------------------------------------------

    @Test
    fun `https URL 下载字节并写缓存 返回 Ok 与 MIME`() = runTest {
        val fetcher = FakeFetcher(NoticeAttachmentFetch.Bytes("PDF-BYTES".toByteArray()))
        val sink = FakeSink()
        val result = preparer(fetcher, sink).prepare(signedUrl, "通知.pdf")

        assertTrue(result is NoticeAttachmentFileResult.Ok)
        val ok = result as NoticeAttachmentFileResult.Ok
        assertEquals("application/pdf", ok.mime)
        assertFalse(ok.fromCache)
        // 抓取参数：原 URL + 20MB 上限
        assertEquals(signedUrl, fetcher.lastUrl)
        assertEquals(NoticeAttachmentLimits.MAX_URL_BYTES, fetcher.lastMaxBytes)
        // 落盘：key 与展示名
        val key = NoticeAttachmentPreparer.cacheKey(signedUrl)
        assertArrayEquals("PDF-BYTES".toByteArray(), sink.written["$key/通知.pdf"])
    }

    @Test
    fun `http 调试域名同样支持 不写死域名`() = runTest {
        val debugUrl = "http://121.196.47.2/att/abc?exp=1&sig=aa"
        val fetcher = FakeFetcher(NoticeAttachmentFetch.Bytes("x".toByteArray()))
        val result = preparer(fetcher, FakeSink()).prepare(debugUrl, "a.txt")
        assertTrue(result is NoticeAttachmentFileResult.Ok)
        assertEquals(debugUrl, fetcher.lastUrl)
    }

    @Test
    fun `磁盘缓存命中不再下载`() = runTest {
        val fetcher = FakeFetcher(NoticeAttachmentFetch.Failed) // 被调用会得到 Failed
        val sink = FakeSink()
        val key = NoticeAttachmentPreparer.cacheKey(signedUrl)
        val cachedFile = File("/fake-cache/$key/通知.pdf")
        sink.cache["$key/通知.pdf"] = cachedFile

        val result = preparer(fetcher, sink).prepare(signedUrl, "通知.pdf")
        assertTrue(result is NoticeAttachmentFileResult.Ok)
        assertTrue((result as NoticeAttachmentFileResult.Ok).fromCache)
        assertEquals(cachedFile, result.file)
        assertEquals(0, fetcher.calls)
    }

    @Test
    fun `URL 超限与网络失败给出可读结果`() = runTest {
        val tooLarge = preparer(FakeFetcher(NoticeAttachmentFetch.TooLarge), FakeSink())
            .prepare(signedUrl, "big.zip")
        assertTrue(tooLarge is NoticeAttachmentFileResult.TooLarge)
        assertEquals(
            NoticeAttachmentLimits.MAX_URL_BYTES,
            (tooLarge as NoticeAttachmentFileResult.TooLarge).limitBytes,
        )

        val failed = preparer(FakeFetcher(NoticeAttachmentFetch.Failed), FakeSink())
            .prepare(signedUrl, "a.pdf")
        assertTrue(failed is NoticeAttachmentFileResult.Failed)
        assertTrue((failed as NoticeAttachmentFileResult.Failed).message.contains("下载失败"))
    }

    @Test
    fun `自定义下载上限生效`() = runTest {
        val fetcher = FakeFetcher(NoticeAttachmentFetch.Bytes(ByteArray(10)))
        val result = preparer(fetcher, FakeSink(), maxUrlBytes = 5).prepare(signedUrl, "a.bin")
        assertTrue(result is NoticeAttachmentFileResult.TooLarge)
        assertEquals(5L, (result as NoticeAttachmentFileResult.TooLarge).limitBytes)
    }

    // ---- 旧数据内联 data URI（向后兼容） ------------------------------------

    @Test
    fun `data URI 解码写缓存且不联网`() = runTest {
        val payload = Base64.getEncoder().encodeToString("PDF-DATA".toByteArray())
        val source = "data:application/pdf;base64,$payload"
        val fetcher = FakeFetcher(NoticeAttachmentFetch.Bytes(ByteArray(0)))
        val sink = FakeSink()

        val result = preparer(fetcher, sink).prepare(source, "旧附件.pdf")
        assertTrue(result is NoticeAttachmentFileResult.Ok)
        val ok = result as NoticeAttachmentFileResult.Ok
        assertFalse(ok.fromCache)
        assertEquals("application/pdf", ok.mime)
        assertEquals(0, fetcher.calls)
        val key = NoticeAttachmentPreparer.cacheKey(source)
        assertArrayEquals("PDF-DATA".toByteArray(), sink.written["$key/旧附件.pdf"])
    }

    @Test
    fun `data URI 超限提前拒绝`() = runTest {
        val payload = Base64.getEncoder().encodeToString(ByteArray(32))
        val source = "data:image/png;base64,$payload"
        val result = preparer(FakeFetcher(), FakeSink(), maxInlineBytes = 8).prepare(source, "a.png")
        assertTrue(result is NoticeAttachmentFileResult.TooLarge)
        assertEquals(8L, (result as NoticeAttachmentFileResult.TooLarge).limitBytes)
    }

    @Test
    fun `坏 data URI 与写盘失败给出失败结果`() = runTest {
        val bad = preparer(FakeFetcher(), FakeSink()).prepare("data:image/png;base64,", "a.png")
        assertTrue(bad is NoticeAttachmentFileResult.Failed)

        val sink = FakeSink().apply { failWrite = true }
        val writeFail = preparer(FakeFetcher(NoticeAttachmentFetch.Bytes("x".toByteArray())), sink)
            .prepare(signedUrl, "a.pdf")
        assertTrue(writeFail is NoticeAttachmentFileResult.Failed)
        assertTrue((writeFail as NoticeAttachmentFileResult.Failed).message.contains("缓存文件"))
    }

    // ---- 不可识别 ------------------------------------------------------------

    @Test
    fun `不可识别形态返回 Unsupported 且不写盘`() = runTest {
        val fetcher = FakeFetcher()
        val sink = FakeSink()
        val instance = preparer(fetcher, sink)

        assertTrue(instance.prepare(null, "a") is NoticeAttachmentFileResult.Unsupported)
        assertTrue(instance.prepare("", "a") is NoticeAttachmentFileResult.Unsupported)
        assertTrue(instance.prepare("ftp://example.com/a", "a") is NoticeAttachmentFileResult.Unsupported)
        assertTrue(instance.prepare("javascript:alert(1)", "a") is NoticeAttachmentFileResult.Unsupported)
        assertEquals(0, fetcher.calls)
        assertTrue(sink.written.isEmpty())
    }

    @Test
    fun `准备结果不含附件内容以外的敏感字段`() = runTest {
        // URL 本身出现在返回结果的 File 路径里（缓存命中路径用 cacheKey 分目录），不做内容判断；
        // 这里只断言失败结果可读且没有远程内容。
        val result = preparer(FakeFetcher(NoticeAttachmentFetch.Failed), FakeSink()).prepare(signedUrl, "a.pdf")
        val message = (result as NoticeAttachmentFileResult.Failed).message
        assertNotNull(message)
        assertFalse(message.contains("sig="))
    }
}
