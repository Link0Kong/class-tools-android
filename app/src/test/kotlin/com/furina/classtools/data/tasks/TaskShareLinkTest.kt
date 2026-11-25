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
        val encoded = link.substringAfter("#task-share=")
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
        val shared = requireNotNull(TaskShareLink.parse("shared={\"t\":\"复习\",\"n\":\"读第一章\",\"s\":[\"完成练习\",12]}"))
        assertEquals("读第一章", shared.notes)
        assertEquals(listOf("完成练习"), shared.steps)
    }
}
