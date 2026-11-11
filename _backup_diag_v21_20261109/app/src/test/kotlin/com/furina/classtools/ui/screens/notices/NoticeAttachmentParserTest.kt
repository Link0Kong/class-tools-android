package com.furina.classtools.ui.screens.notices

import com.furina.classtools.data.notices.NOTICE_BODY_PREFIX
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知附件原始数据解析单测（详情按需预览的数据层，纯 JVM）。
 */
class NoticeAttachmentParserTest {

    /** 96x64 PNG（382B）—— 用于 base64 解码/尺寸校验。 */
    private val pngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAGAAAABACAYAAADlNHIOAAABRUlEQVR42u3coW4CQRCA4WXDs2GrSGUNAtequiocqnUIDJKgavtixbWKhFyA291wub3j+zWIm39nduaSm8nv8fgX0BtRCAggAAQQgH6YlvzpebMWuSvsl+9Zv5/ktKECf38RyQKawf9+/RDlBk9fq2wJSQLOgy/weSLaJLQKOAVf4MtF3JIQ1fzuuRXHpDbU6S8jJW7R6e83C6LT328WmISHOAlf6nnHVpuDd0HByzgQQAAI0AWZD2QAASCAABCgCzqxOxxEr8HLfC4DlCAQ8BB3QEm9gwwgAASYAx6pz5cBShAIMAdABhAAAsZ0B/x8Lqp9qNnbVgaAAALQ4R0wpDorA0DAYAUM+VOkUOH6gmQBuVs/EIqWd8Sxf5BX8+lvFSALul9dY1tK7dtS7AuqYF+Q/REVbMwiooKdcTAJEwACCMD9+Ad+mXqGYQC4eQAAAABJRU5ErkJggg=="
    private val pngDataUri = "data:image/png;base64,$pngBase64"

    private val autoBody = NOTICE_BODY_PREFIX + """
        {"id":"g1","title":"测试通知","items":[
          {"kind":"text","text":"正文"},
          {"kind":"img","src":"data:image/png;base64,AAAA","caption":"截图1","ocr":true},
          {"kind":"img","url":"https://example.com/b.png","caption":"远程图"},
          {"kind":"file","name":"a.pdf","url":"https://example.com/a.pdf"}
        ]}
    """.trimIndent()

    private val manualAttachments = """
        [{"n":"照片.png","t":"image/png","s":382,"d":"data:image/png;base64,AAAA"},
         {"n":"文档.pdf","t":"application/pdf","s":2048,"d":""}]
    """.trimIndent()

    // ---- 行解析 -------------------------------------------------------------

    @Test
    fun parseRow_arrayAndObject() {
        val escapedBody = autoBody.replace("\\", "\\\\").replace("\"", "\\\"")
        val array = """[{"id":"auto_g1","body":"$escapedBody","attachments":""}]"""
        val row = NoticeAttachmentParser.parseRow(array)
        assertNotNull(row)
        assertEquals(autoBody, row!!.body)

        val escapedAtt = manualAttachments.replace("\\", "\\\\").replace("\"", "\\\"")
        val obj = """{"id":"m1","body":"正文","attachments":"$escapedAtt"}"""
        val single = NoticeAttachmentParser.parseRow(obj)
        assertEquals("正文", single!!.body)
        assertEquals(manualAttachments, single.attachments)
    }

    @Test
    fun parseRow_blankOrBadReturnsNull() {
        assertNull(NoticeAttachmentParser.parseRow(null))
        assertNull(NoticeAttachmentParser.parseRow(""))
        assertNull(NoticeAttachmentParser.parseRow("oops"))
        assertNull(NoticeAttachmentParser.parseRow("[]"))
        assertNull(NoticeAttachmentParser.parseRow("[1,2]"))
    }

    // ---- 自动通知 items -----------------------------------------------------

    @Test
    fun extractAutoImageData_byImageOrdinal() {
        assertTrue(NoticeAttachmentParser.extractAutoImageData(autoBody, 0)!!.startsWith("data:image/png;base64,"))
        assertEquals("https://example.com/b.png", NoticeAttachmentParser.extractAutoImageData(autoBody, 1))
        assertNull(NoticeAttachmentParser.extractAutoImageData(autoBody, 2))
        assertNull(NoticeAttachmentParser.extractAutoImageData(autoBody, -1))
        assertNull(NoticeAttachmentParser.extractAutoImageData(null, 0))
    }

    @Test
    fun extractAutoImageData_badJsonReturnsNull() {
        assertNull(NoticeAttachmentParser.extractAutoImageData(NOTICE_BODY_PREFIX + "not-json", 0))
        assertNull(NoticeAttachmentParser.extractAutoImageData("""{"items":[]}""", 0))
    }

    // ---- 人工通知 attachments ----------------------------------------------

    @Test
    fun extractManualData_byIndex() {
        assertTrue(NoticeAttachmentParser.extractManualData(manualAttachments, 0)!!.startsWith("data:image/png;base64,"))
        assertNull(NoticeAttachmentParser.extractManualData(manualAttachments, 1)) // 空 d
        assertNull(NoticeAttachmentParser.extractManualData(manualAttachments, 9))
        assertNull(NoticeAttachmentParser.extractManualData("not-json", 0))
    }

    // ---- data URI 工具 ------------------------------------------------------

    @Test
    fun imageDataUriDetection() {
        assertTrue(NoticeAttachmentParser.isImageDataUri(pngDataUri))
        assertTrue(NoticeAttachmentParser.isImageDataUri("DATA:IMAGE/PNG;base64,AAAA"))
        assertTrue(NoticeAttachmentParser.isImageDataUri("data:image/jpeg;base64,/9j/4AAQ"))
        assertFalse(NoticeAttachmentParser.isImageDataUri("https://example.com/a.png"))
        assertFalse(NoticeAttachmentParser.isImageDataUri("data:application/pdf;base64,AAAA"))
        assertFalse(NoticeAttachmentParser.isImageDataUri("data:image/png,plain"))
        assertFalse(NoticeAttachmentParser.isImageDataUri(null))
    }

    @Test
    fun mimeOf() {
        assertEquals("image/png", NoticeAttachmentParser.mimeOf(pngDataUri))
        assertEquals("application/pdf", NoticeAttachmentParser.mimeOf("data:application/pdf;base64,AA"))
        assertNull(NoticeAttachmentParser.mimeOf("https://x/y.png"))
        assertNull(NoticeAttachmentParser.mimeOf("data:"))
        assertNull(NoticeAttachmentParser.mimeOf(null))
    }

    @Test
    fun decodeBase64Payload_pngMagic() {
        val bytes = NoticeAttachmentParser.decodeBase64Payload(pngDataUri)
        assertNotNull(bytes)
        assertEquals(382, bytes!!.size)
        assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47), bytes.copyOfRange(0, 4))
        assertNull(NoticeAttachmentParser.decodeBase64Payload("data:image/png;base64,"))
        assertNull(NoticeAttachmentParser.decodeBase64Payload("not-a-data-uri"))
    }

    @Test
    fun sampleSizeFor_downscalesToLimit() {
        assertEquals(1, NoticeAttachmentParser.sampleSizeFor(400, 300, 480))
        assertEquals(2, NoticeAttachmentParser.sampleSizeFor(960, 720, 480))
        assertEquals(4, NoticeAttachmentParser.sampleSizeFor(1800, 1200, 480))
        assertEquals(1, NoticeAttachmentParser.sampleSizeFor(0, 0, 480))
        assertEquals(1, NoticeAttachmentParser.sampleSizeFor(100, 100, 0))
    }
}

