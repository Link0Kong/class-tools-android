package com.furina.classtools.data.notices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 附件形态判定/文件名/MIME 单测（迁移第 2 步：data URI ↔ 签名 URL 双形态，纯 JVM）。
 */
class NoticeAttachmentSourceTest {

    private val signedUrl = "https://tool.furina.fun/att/6f1a2b3c4d5e6f708192a3b4c5d6e7f8?exp=1799999999&sig=" +
        "a".repeat(64)

    // ---- 形态判定 ------------------------------------------------------------

    @Test
    fun `isHttpUrl 只认 http 与 https`() {
        assertTrue(NoticeAttachmentSource.isHttpUrl(signedUrl))
        assertTrue(NoticeAttachmentSource.isHttpUrl("http://121.196.47.2/att/abc?exp=1&sig=aa"))
        assertTrue(NoticeAttachmentSource.isHttpUrl("  HTTPS://example.com/a.png  "))
        assertFalse(NoticeAttachmentSource.isHttpUrl("data:image/png;base64,AAAA"))
        assertFalse(NoticeAttachmentSource.isHttpUrl("ftp://example.com/a"))
        assertFalse(NoticeAttachmentSource.isHttpUrl("//example.com/a"))
        assertFalse(NoticeAttachmentSource.isHttpUrl("javascript:alert(1)"))
        assertFalse(NoticeAttachmentSource.isHttpUrl(""))
        assertFalse(NoticeAttachmentSource.isHttpUrl(null))
    }

    @Test
    fun `kindOf 区分内联 外置 与无内容`() {
        assertEquals(NoticeAttachmentSource.Kind.URL, NoticeAttachmentSource.kindOf(signedUrl))
        assertEquals(
            NoticeAttachmentSource.Kind.INLINE,
            NoticeAttachmentSource.kindOf("data:application/pdf;base64,AAAA"),
        )
        assertEquals(
            NoticeAttachmentSource.Kind.INLINE,
            NoticeAttachmentSource.kindOf("DATA:TEXT/PLAIN;base64,AAAA"),
        )
        assertEquals(NoticeAttachmentSource.Kind.NONE, NoticeAttachmentSource.kindOf("ftp://x/y"))
        assertEquals(NoticeAttachmentSource.Kind.NONE, NoticeAttachmentSource.kindOf(""))
        assertEquals(NoticeAttachmentSource.Kind.NONE, NoticeAttachmentSource.kindOf(null))
    }

    @Test
    fun `urlOf 规范化并拒绝异常内容`() {
        assertEquals(signedUrl, NoticeAttachmentSource.urlOf("  $signedUrl  "))
        assertEquals("", NoticeAttachmentSource.urlOf("data:image/png;base64,AAAA"))
        assertEquals("", NoticeAttachmentSource.urlOf("ftp://example.com/a"))
        // 含空白字符（异常拼接）拒绝
        assertEquals("", NoticeAttachmentSource.urlOf("https://example.com/a b"))
        // 超长拒绝（正常签名 URL < 400 字符）
        assertEquals("", NoticeAttachmentSource.urlOf("https://h/" + "a".repeat(NoticeAttachmentSource.MAX_URL_LENGTH)))
    }

    @Test
    fun `inlineOf 与 mimeOfDataUri`() {
        assertEquals("data:image/png;base64,AAAA", NoticeAttachmentSource.inlineOf("data:image/png;base64,AAAA"))
        assertNull(NoticeAttachmentSource.inlineOf(signedUrl))

        assertEquals("image/png", NoticeAttachmentSource.mimeOfDataUri("data:image/png;base64,AAAA"))
        assertEquals("application/pdf", NoticeAttachmentSource.mimeOfDataUri("data:application/pdf;base64,AA"))
        assertNull(NoticeAttachmentSource.mimeOfDataUri("data:base64,AA"))
        assertNull(NoticeAttachmentSource.mimeOfDataUri(signedUrl))
        assertNull(NoticeAttachmentSource.mimeOfDataUri(null))
    }

    @Test
    fun `hasContent 覆盖两种形态`() {
        assertTrue(NoticeAttachmentSource.hasContent(signedUrl))
        assertTrue(NoticeAttachmentSource.hasContent("data:text/plain;base64,AA"))
        assertFalse(NoticeAttachmentSource.hasContent(""))
        assertFalse(NoticeAttachmentSource.hasContent("待获取"))
    }

    // ---- 大小显示 ------------------------------------------------------------

    @Test
    fun `formatSize 对 0 返回空串不显示 0 B`() {
        assertEquals("", NoticeAttachmentSource.formatSize(0))
        assertEquals("", NoticeAttachmentSource.formatSize(-1))
        assertEquals("512 B", NoticeAttachmentSource.formatSize(512))
        assertEquals("2.0 KB", NoticeAttachmentSource.formatSize(2048))
        assertEquals("1.5 MB", NoticeAttachmentSource.formatSize(1024L * 1024 + 512 * 1024))
    }

    // ---- 缓存文件名 / MIME / cache key ---------------------------------------

    @Test
    fun `fileNameFor 保留展示名并去掉路径与遍历`() {
        assertEquals("通知.pdf", NoticeAttachmentPreparer.fileNameFor(signedUrl, "通知.pdf"))
        val traversal = NoticeAttachmentPreparer.fileNameFor(signedUrl, "../../etc/passwd.pdf")
        assertFalse(traversal.contains("/"))
        assertFalse(traversal.contains("\\"))
        assertFalse(traversal.contains(".."))
        assertTrue(traversal.endsWith(".pdf"))

        // 空展示名：从 URL 路径补扩展名；签名 URL 无扩展名 → 附件
        assertEquals("附件.xlsx", NoticeAttachmentPreparer.fileNameFor("https://h/a/b/报告.xlsx?exp=1", ""))
        assertEquals("附件", NoticeAttachmentPreparer.fileNameFor(signedUrl, "  "))
        // 展示名无扩展名 + URL 有扩展名 → 补上
        assertEquals("材料.docx", NoticeAttachmentPreparer.fileNameFor("https://h/f.docx?sig=x", "材料"))
        // 超长截断但仍保留扩展名
        val longName = "很长的附件名".repeat(40) + ".pdf"
        val truncated = NoticeAttachmentPreparer.fileNameFor(signedUrl, longName)
        assertTrue(truncated.length <= 96)
        assertTrue(truncated.endsWith(".pdf"))
    }

    @Test
    fun `mimeForFileName 常见类型映射`() {
        assertEquals("application/pdf", NoticeAttachmentPreparer.mimeForFileName("a.pdf"))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            NoticeAttachmentPreparer.mimeForFileName("B.XLSX"),
        )
        assertEquals("application/zip", NoticeAttachmentPreparer.mimeForFileName("包.zip"))
        assertEquals("image/png", NoticeAttachmentPreparer.mimeForFileName("图.png"))
        assertEquals("application/octet-stream", NoticeAttachmentPreparer.mimeForFileName("未知"))
        assertEquals("application/octet-stream", NoticeAttachmentPreparer.mimeForFileName(""))
    }

    @Test
    fun `cacheKey 稳定且区分内容`() {
        // sha256("abc") = ba7816bf... → 取前 16 hex
        assertEquals("ba7816bf8f01cfea", NoticeAttachmentPreparer.cacheKey("abc"))
        assertEquals(NoticeAttachmentPreparer.cacheKey(signedUrl), NoticeAttachmentPreparer.cacheKey(" $signedUrl "))
        assertEquals(16, NoticeAttachmentPreparer.cacheKey(signedUrl).length)
        assertTrue(NoticeAttachmentPreparer.cacheKey(signedUrl).matches(Regex("[0-9a-f]{16}")))
        assertFalse(NoticeAttachmentPreparer.cacheKey(signedUrl) == NoticeAttachmentPreparer.cacheKey("$signedUrl/2"))
    }
}
