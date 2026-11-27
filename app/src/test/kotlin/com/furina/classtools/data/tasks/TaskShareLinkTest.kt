package com.furina.classtools.data.tasks

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TaskShareLinkTest {
    @Test fun roundTripToWebAndMiniProgramPreservesTextAndOmitsPrivateData() {
        val source = newTask("A+B & 中文📝", 1, 0.0).withFields {
            put("notes", "仅自己可见"); put("sourceHwId", "private-id")
            put("localFiles", buildJsonArray { add("private.pdf") })
            put("steps", buildJsonArray { repeat(4) { add(buildJsonObject { put("text", "步骤$it"); put("note", "仅自己可见") }) } })
        }
        val link = TaskShareLink.create(source, "实验")
        assertTrue(link, link.startsWith("https://tool.furina.fun/l/task-share#s="))
        val encoded = link.substringAfter("#s=")
        val shared = requireNotNull(TaskShareLink.parse(link))
        assertEquals(source.text("title"), shared.title)
        assertEquals("实验", shared.groupLabel)
        assertEquals(listOf("步骤0", "步骤1", "步骤2"), shared.steps)
        assertEquals("", shared.notes)
        val fields = Json.parseToJsonElement(java.net.URLDecoder.decode(encoded, "UTF-8")).jsonObject
        assertEquals(setOf("t", "d", "l", "n", "s"), fields.keys)
        for (input in listOf("shared=$encoded", "?shared=$encoded", "/pages/task/task?other=1&shared=$encoded")) {
            assertEquals(shared, TaskShareLink.parse(input))
        }
        val draft = shared.draft(2, -1.0)
        assertNotEquals(source.text("id"), draft.text("id"))
        assertTrue(draft.text("due").isEmpty() && draft.text("list").isEmpty())
        assertTrue(draft.objects("steps").all { !it.flag("done") })
    }

    @Test fun invalidAndOversizedDataCannotBecomeTasks() {
        assertNull(TaskShareLink.parse("https://example.com/#task-share=%7B%22t%22%3A%22X%22%7D"))
        assertNull(TaskShareLink.parse("shared=%XX"))
        assertNull(TaskShareLink.parse("shared={\"t\":{}}"))
        assertNull(TaskShareLink.parse("x".repeat(TaskShareLink.MAX_INPUT + 1)))
        // 旧分享链接（#task-share=）继续可读，不因新格式失效
        val legacy = requireNotNull(TaskShareLink.parse("https://tool.furina.fun/#task-share=%7B%22t%22%3A%22%E5%A4%8D%E4%B9%A0%22%7D"))
        assertEquals("复习", legacy.title)
        assertEquals(legacy, TaskShareLink.parse("#task-share=%7B%22t%22%3A%22%E5%A4%8D%E4%B9%A0%22%7D"))
        val shared = requireNotNull(TaskShareLink.parse("shared={\"t\":\"复习\",\"n\":\"读第一章\",\"s\":[\"完成练习\",12]}"))
        assertEquals("读第一章", shared.notes)
        assertEquals(listOf("完成练习"), shared.steps)
    }
}
