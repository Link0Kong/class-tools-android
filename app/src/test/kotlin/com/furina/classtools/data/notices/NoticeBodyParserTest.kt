package com.furina.classtools.data.notices

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeBodyParserTest {

    private fun item(vararg pairs: Pair<String, String>): JsonObject = buildJsonObject {
        pairs.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
    }

    private fun autoBody(groupJson: String): String = NOTICE_BODY_PREFIX + groupJson

    // ------------------------------------------------------------ body 前缀 / group 解析

    @Test
    fun `识别并解析 CLASS_NOTICE_V1 前缀`() {
        assertTrue(NoticeBodyParser.isAuto(NOTICE_BODY_PREFIX + "{}"))
        assertFalse(NoticeBodyParser.isAuto("普通通知正文"))

        val body = autoBody(
            """{"id":"g_1","title":"班会","cat":"班会","source":"班级群",
               "aiSummary":"摘要","aiPoints":["a","b"],"deadline":"2026-11-08T16:00:00+08:00"}""",
        )
        val group = NoticeBodyParser.parseGroup(body)!!
        assertEquals("g_1", group.id)
        assertEquals("班会", group.title)
        assertEquals(2, group.aiPoints.size)
        assertEquals("2026-11-08T16:00:00+08:00", group.deadline)
    }

    @Test
    fun `坏 JSON 返回 null 而非崩溃`() {
        assertNull(NoticeBodyParser.parseGroup(autoBody("{oops")))
        assertNull(NoticeBodyParser.parseGroup(autoBody("")))
        assertNull(NoticeBodyParser.parseGroup("普通正文"))
    }

    // ------------------------------------------------------------ items 元信息

    @Test
    fun `文本与补充通知条目解析`() {
        val items = NoticeBodyParser.parseItems(
            listOf(
                item("kind" to "text", "text" to "原文", "time" to "11-05 12:00", "fromName" to "张三"),
                item("kind" to "follow", "text" to "更正：地点改为 01105"),
            ),
        )
        assertEquals(2, items.size)
        val first = items[0] as NoticeItemMeta.Text
        assertEquals("原始消息", first.label)
        assertEquals("张三", first.fromName)
        assertEquals("11-05 12:00", first.timeText)
        assertFalse(first.isFollow)

        val follow = items[1] as NoticeItemMeta.Text
        assertEquals("补充通知", follow.label)
        assertTrue(follow.isFollow)
    }

    @Test
    fun `图片条目只保留元信息不含 base64`() {
        val dataUri = "data:image/jpeg;base64," + "A".repeat(4000)
        val items = NoticeBodyParser.parseItems(
            listOf(item("kind" to "img", "src" to dataUri, "caption" to "现场图", "ocr" to "true")),
        )
        val image = items.single() as NoticeItemMeta.Image
        assertTrue(image.hasData)
        assertTrue(image.ocr)
        assertEquals("现场图", image.caption)
        assertTrue(image.approxBytes > 0)
        // 模型里只有元信息，没有 4KB base64
        assertFalse(image.toString().contains("AAAA"))
    }

    @Test
    fun `文件条目区分待获取与有内容`() {
        val items = NoticeBodyParser.parseItems(
            listOf(
                item("kind" to "file", "name" to "通知.pdf", "size" to "1.2 MB", "url" to "https://x/a.pdf"),
                item("kind" to "file", "name" to "缺失文件.docx"),
            ),
        )
        val withUrl = items[0] as NoticeItemMeta.File
        assertEquals("pdf", withUrl.ext)
        assertTrue(withUrl.hasContent)
        assertFalse(withUrl.pending)

        val missing = items[1] as NoticeItemMeta.File
        assertFalse(missing.hasContent)
        assertTrue(missing.pending)
        assertTrue(missing.sizeText.isNotBlank())
    }

    @Test
    fun `收集表与链接解析`() {
        val items = NoticeBodyParser.parseItems(
            listOf(
                item(
                    "kind" to "form", "name" to "报名表", "realTitle" to "秋游报名",
                    "realUrl" to "https://docs.qq.com/x", "close" to "11-10",
                ),
                item("kind" to "link", "name" to "活动页面", "url" to "https://example.com/a", "account" to "公众号"),
            ),
        )
        val form = items[0] as NoticeItemMeta.Form
        assertEquals("秋游报名", form.name)
        assertTrue(form.statusText.contains("11-10"))
        assertTrue(form.hasUrl)

        val link = items[1] as NoticeItemMeta.Link
        assertEquals("example.com", link.host)
        assertEquals("公众号", link.account)
    }

    @Test
    fun `合并消息最多保留 8 条`() {
        val messages = (1..12).joinToString(",") { i ->
            """{"fromName":"同学$i","time":"11-05 12:0${i % 10}","text":"消息 $i"}"""
        }
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"kind":"merged","name":"聊天记录","count":12,"messages":[$messages]}""",
        ) as JsonObject
        val meta = NoticeBodyParser.parseItems(listOf(obj)).single() as NoticeItemMeta.Merged
        assertEquals(12, meta.count)
        assertEquals(8, meta.messages.size)
        assertEquals("同学1", meta.messages.first().fromName)
    }

    @Test
    fun `未知 kind 只返回占位元信息`() {
        val meta = NoticeBodyParser.parseItems(
            listOf(item("kind" to "video", "src" to "data:video/mp4;base64,AAAA")),
        ).single() as NoticeItemMeta.Unknown
        assertEquals("video", meta.kind)
        assertTrue(meta.keys.contains("src"))
    }

    @Test
    fun `首条文本与附件数统计`() {
        val items = NoticeBodyParser.parseItems(
            listOf(
                item("kind" to "text", "text" to "正文内容"),
                item("kind" to "file", "name" to "a.pdf"),
                item("kind" to "img", "src" to "data:image/png;base64,AA"),
            ),
        )
        assertEquals("正文内容", NoticeBodyParser.firstTextOf(items))
        assertEquals(2, NoticeBodyParser.attachmentCount(items))
    }

    // ------------------------------------------------------------ 人工通知附件

    @Test
    fun `人工通知附件元信息解析`() {
        val attachments = NoticeBodyParser.parseManualAttachments(
            """[{"n":"a.pdf","t":"application/pdf","s":2048,"d":"data:application/pdf;base64,AA"},
                {"n":"b.jpg","t":"image/jpeg","d":"data:image/jpeg;base64,AAAA"}]""",
        )
        assertEquals(2, attachments.size)
        assertEquals("2.0 KB", attachments[0].sizeText)
        assertTrue(attachments[0].hasData)
        assertTrue(attachments[1].sizeText.isNotBlank())
        assertEquals(0, NoticeBodyParser.parseManualAttachments("bad").size)
    }

    // ------------------------------------------------------------ override 白名单

    @Test
    fun `override 白名单字段覆盖与清空`() {
        val base = NoticeGroupDto(
            id = "g",
            title = "原标题",
            cat = "通知",
            aiSummary = "原摘要",
            deadline = "2026-11-08T16:00:00+08:00",
            eventAt = "2026-11-09T16:00:00+08:00",
            timeLabel = "原标签",
        )
        val override = NoticeOverride.parse(
            """{"title":"新标题","aiSummary":null,"deadline":"","timeLabel":"新标签","unknown":"x"}""",
        )
        val applied = NoticeOverride.apply(base, override)
        assertEquals("新标题", applied.title)
        assertEquals("", applied.aiSummary)
        assertEquals("", applied.deadline)
        assertEquals("新标签", applied.timeLabel)
        // 未覆盖字段保持
        assertEquals("2026-11-09T16:00:00+08:00", applied.eventAt)
        assertEquals("通知", applied.cat)
    }

    @Test
    fun `override 坏 JSON 视为无覆盖`() {
        assertNull(NoticeOverride.parse("{bad"))
        assertNull(NoticeOverride.parse(""))
        val base = NoticeGroupDto(title = "t")
        assertEquals(base, NoticeOverride.apply(base, null))
    }

    // ------------------------------------------------------------ 两级签名

    @Test
    fun `签名包含 id ts hidden edited_at`() {
        val sig = NoticeSignature.ofRows(
            listOf(
                NoticeSigRow(id = "auto_g_1", ts = 100, hidden = false, editedAt = 0),
                NoticeSigRow(id = "auto_g_2", ts = 200, hidden = true, editedAt = 300),
            ),
        )
        assertEquals("auto_g_1:100:false:0|auto_g_2:200:true:300", sig)
        assertEquals("", NoticeSignature.ofRows(emptyList()))
    }

    @Test
    fun `buildData 分流自动与人工 并过滤 hidden`() {
        val fullRows = listOf(
            NoticeRow(
                id = "auto_g_1",
                title = "",
                body = autoBody("""{"id":"g_1","title":"自动通知"}"""),
                ts = 100,
            ),
            NoticeRow(id = "manual_1", title = "人工通知", body = "正文", ts = 50),
            NoticeRow(
                id = "auto_g_2",
                body = autoBody("""{"id":"g_2","title":"隐藏通知"}"""),
                hidden = true,
                ts = 80,
            ),
        )
        val nonAdmin = DefaultNoticesRepository.buildData(fullRows, admin = false, now = 1)
        assertEquals(1, nonAdmin.auto.size)
        assertEquals(1, nonAdmin.manual.size)
        assertEquals(1, nonAdmin.hiddenCount)

        val admin = DefaultNoticesRepository.buildData(fullRows, admin = true, now = 1)
        assertEquals(2, admin.auto.size)
        assertEquals(1, admin.manual.size)
        assertEquals(1, admin.hiddenCount)
        assertTrue(admin.auto.any { it.hidden })
    }

    @Test
    fun `buildData 清空大 body 但保留 items 元信息`() {
        val body = autoBody(
            """{"id":"g_1","title":"通知","items":[{"kind":"text","text":"正文"}]}""",
        )
        val data = DefaultNoticesRepository.buildData(
            listOf(NoticeRow(id = "auto_g_1", body = body, ts = 1)),
            admin = false,
            now = 1,
        )
        val entry = data.auto.single()
        assertEquals("", entry.row.body) // 大 body 不常驻
        assertEquals(1, entry.items.size)
        assertEquals("正文", (entry.items.single() as NoticeItemMeta.Text).text)
        assertNotNull(entry.group.title)
    }
}
