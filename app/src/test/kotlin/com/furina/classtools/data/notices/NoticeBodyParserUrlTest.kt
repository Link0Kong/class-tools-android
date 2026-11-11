package com.furina.classtools.data.notices

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知 items 附件「data URI ↔ 签名 URL」双形态解析单测（迁移第 2 步）。
 *
 * 重点：
 * 1. 新数据 URL（图片 src / 文件 url / archive.files[].url）→ UI 模型保留 URL，不再需要按需拉 base64；
 * 2. 旧数据 data URI 行为不变（hasData=true，模型里不含大 base64）；
 * 3. URL 形态下大小显示用 `item.size` 或「附件」，**不得显示 0 B**。
 */
class NoticeBodyParserUrlTest {

    private fun item(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    private fun autoBody(groupJson: String): String = NOTICE_BODY_PREFIX + groupJson

    private fun parseBody(groupJson: String): List<NoticeItemMeta> {
        val group = NoticeBodyParser.parseGroup(autoBody(groupJson))!!
        return NoticeBodyParser.parseItems(group.items)
    }

    private val signedImage =
        "https://tool.furina.fun/att/6f1a2b3c4d5e6f708192a3b4c5d6e7f8?exp=1799999999&sig=" + "a".repeat(64)
    private val signedFile =
        "https://tool.furina.fun/att/00112233445566778899aabbccddeeff?exp=1799999999&sig=" + "b".repeat(64)

    // ---- 图片 ----------------------------------------------------------------

    @Test
    fun `图片 src 为签名 URL 时保留 URL 且不再算内联体积`() {
        val items = NoticeBodyParser.parseItems(
            listOf(item("""{"kind":"img","src":"$signedImage","caption":"现场图","ocr":true}""")),
        )
        val image = items.single() as NoticeItemMeta.Image
        assertEquals(signedImage, image.url)
        assertFalse(image.hasData)
        assertEquals(0L, image.approxBytes)
        assertEquals("现场图", image.caption)
        assertTrue(image.ocr)
        // URL 很小，保留在模型里是安全的；不含 base64
        assertFalse(image.toString().contains("AAAA"))
    }

    @Test
    fun `图片 URL 也可从 url 字段读 且 http 调试域名兼容`() {
        val items = NoticeBodyParser.parseItems(
            listOf(
                item("""{"kind":"img","url":"https://example.com/b.png"}"""),
                item("""{"kind":"img","src":"http://121.196.47.2/att/x?exp=1&sig=aa"}"""),
            ),
        )
        assertEquals("https://example.com/b.png", (items[0] as NoticeItemMeta.Image).url)
        assertEquals("http://121.196.47.2/att/x?exp=1&sig=aa", (items[1] as NoticeItemMeta.Image).url)
    }

    @Test
    fun `图片 data URI 旧数据行为不变且模型不含 base64`() {
        val dataUri = "data:image/jpeg;base64," + "A".repeat(4000)
        val items = NoticeBodyParser.parseItems(
            listOf(item("""{"kind":"img","src":"$dataUri"}""")),
        )
        val image = items.single() as NoticeItemMeta.Image
        assertTrue(image.hasData)
        assertEquals("", image.url)
        assertTrue(image.approxBytes > 0)
        assertFalse(image.toString().contains("AAAA"))
    }

    // ---- 文件 ----------------------------------------------------------------

    @Test
    fun `文件 URL 形态大小显示 item size 或附件 不显示 0 B`() {
        val items = NoticeBodyParser.parseItems(
            listOf(
                item("""{"kind":"file","name":"通知.pdf","url":"$signedFile","size":"82 KB"}"""),
                item("""{"kind":"file","name":"名单.xlsx","url":"$signedFile"}"""),
            ),
        )
        val withSize = items[0] as NoticeItemMeta.File
        assertEquals(signedFile, withSize.url)
        assertEquals("82 KB", withSize.sizeText)
        assertTrue(withSize.hasContent)
        assertFalse(withSize.pending)
        assertEquals("pdf", withSize.ext)

        val noSize = items[1] as NoticeItemMeta.File
        assertEquals(signedFile, noSize.url)
        assertEquals("附件", noSize.sizeText)
        assertFalse(noSize.sizeText.contains("0 B"))
    }

    @Test
    fun `文件 data URI 旧数据仍按 base64 估算体积`() {
        val dataUri = "data:application/pdf;base64," + "A".repeat(4000)
        val items = NoticeBodyParser.parseItems(
            listOf(item("""{"kind":"file","name":"旧.pdf","url":"$dataUri"}""")),
        )
        val file = items.single() as NoticeItemMeta.File
        assertEquals("", file.url)
        assertTrue(file.hasContent)
        assertFalse(file.pending)
        assertFalse(file.sizeText.isBlank())
        assertTrue(file.sizeText.contains("KB"))
    }

    @Test
    fun `文件无内容仍显示待获取`() {
        val file = NoticeBodyParser.parseItems(
            listOf(item("""{"kind":"file","name":"缺.docx"}""")),
        ).single() as NoticeItemMeta.File
        assertEquals("", file.url)
        assertFalse(file.hasContent)
        assertTrue(file.pending)
        assertEquals("附件", file.sizeText)
    }

    // ---- 压缩包内文件 ---------------------------------------------------------

    @Test
    fun `archive files 内 url 解析为可打开项`() {
        val items = NoticeBodyParser.parseItems(
            listOf(
                item(
                    """{"kind":"file","name":"材料包.zip","size":"2.1 MB","url":"$signedFile",
                       "archive":{"status":"ok","count":3,"summary":"包含报名表与照片",
                         "files":[
                           {"name":"报名表.docx","kind":"文档","size":"18 KB","url":"$signedFile"},
                           {"name":"照片.jpg","kind":"图片","size":"220 KB","url":"$signedImage"},
                           {"name":"说明.txt","kind":"文本","size":"1 KB"}
                         ]}}""",
                ),
            ),
        )
        val file = items.single() as NoticeItemMeta.File
        assertTrue(file.archiveOk)
        assertEquals(3, file.archiveCount)
        assertEquals("包含报名表与照片", file.archiveSummary)
        assertEquals(3, file.archiveFiles.size)

        val doc = file.archiveFiles[0]
        assertEquals("报名表.docx", doc.name)
        assertEquals("文档", doc.kind)
        assertEquals("18 KB", doc.sizeText)
        assertEquals(signedFile, doc.url)
        assertFalse(doc.hasData)

        val txt = file.archiveFiles[2]
        assertEquals("", txt.url)
        assertFalse(txt.hasData)
    }

    @Test
    fun `archive count 缺省时用清单长度 且截断异常大清单`() {
        val manyFiles = (1..70).joinToString(",") {
            """{"name":"f$it.pdf","size":"1 KB","url":"https://h/att/$it?exp=1&sig=aa"}"""
        }
        val items = NoticeBodyParser.parseItems(
            listOf(item("""{"kind":"file","name":"a.zip","archive":{"files":[$manyFiles]}}""")),
        )
        val file = items.single() as NoticeItemMeta.File
        // 清单截断到 60 条，count 缺省 = 截断后的数量
        assertEquals(60, file.archiveFiles.size)
        assertEquals(60, file.archiveCount)
        assertTrue(file.archiveOk)
        assertFalse(file.pending)
    }

    @Test
    fun `archive 内 data URI 只标记存在不保留原文`() {
        val items = NoticeBodyParser.parseItems(
            listOf(
                item(
                    """{"kind":"file","name":"a.zip","archive":{"files":[
                        {"name":"x.png","size":"1 KB","url":"data:image/png;base64,${"A".repeat(200)}"}
                    ]}}""",
                ),
            ),
        )
        val entry = items.single() as NoticeItemMeta.File
        val archiveFile = entry.archiveFiles.single()
        assertTrue(archiveFile.hasData)
        assertEquals("", archiveFile.url)
        assertFalse(entry.toString().contains("AAAA"))
    }

    // ---- 人工通知附件 --------------------------------------------------------

    @Test
    fun `人工通知附件 url 字段同样支持`() {
        val attachments = NoticeBodyParser.parseManualAttachments(
            """[{"n":"a.pdf","t":"application/pdf","u":"$signedFile"},
                {"n":"b.png","t":"image/png","url":"$signedImage"},
                {"n":"旧.png","t":"image/png","d":"data:image/png;base64,AA","s":2048}]""",
        )
        assertEquals(3, attachments.size)
        assertEquals(signedFile, attachments[0].url)
        assertFalse(attachments[0].hasData)
        assertEquals("附件", attachments[0].sizeText)

        assertEquals(signedImage, attachments[1].url)
        assertEquals("", attachments[2].url)
        assertTrue(attachments[2].hasData)
        assertEquals("2.0 KB", attachments[2].sizeText)
    }

    // ---- 端到端（仓库层） ----------------------------------------------------

    @Test
    fun `buildData 清空大 body 但保留附件 URL 元信息`() {
        val body = autoBody(
            """{"id":"g_url","title":"外置附件通知","items":[
                {"kind":"img","src":"$signedImage"},
                {"kind":"file","name":"a.pdf","url":"$signedFile","size":"1.2 MB"},
                {"kind":"text","text":"正文"}
            ]}""",
        )
        val data = DefaultNoticesRepository.buildData(
            listOf(NoticeRow(id = "auto_g_url", body = body, ts = 1)),
            admin = false,
            now = 1,
        )
        val entry = data.auto.single()
        assertEquals("", entry.row.body) // 大 body 不常驻
        val image = entry.items[0] as NoticeItemMeta.Image
        assertEquals(signedImage, image.url)
        val file = entry.items[1] as NoticeItemMeta.File
        assertEquals(signedFile, file.url)
        assertEquals("1.2 MB", file.sizeText)
        assertEquals("正文", (entry.items[2] as NoticeItemMeta.Text).text)
    }
}
