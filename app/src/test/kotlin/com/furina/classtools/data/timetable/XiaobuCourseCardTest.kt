package com.furina.classtools.data.timetable

import java.time.LocalDateTime
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class XiaobuCourseCardTest {
    private val course = Course(name = "数学", teacher = "教师甲", room = "教学楼 A201", day = 0,
        start = 1, end = 2, weeks = "1-4", parity = "odd")
    private val table = TtSnapshot(c = listOf(course), t = "2026-08-31")
    private fun time(value: String) = LocalDateTime.parse(value).atZone(TimetableLink.CHINA_ZONE)
        .toInstant().toEpochMilli()
    private val start = time("2026-08-31T08:10")
    private val lead = 10 * 60_000L

    @Test fun onlyExplicitSystemReceiptAcknowledgesSharing() {
        assertEquals(0, XiaobuCourseCard.responseCode("""{"code":0,"message":"ok"}"""))
        assertEquals(10101001, XiaobuCourseCard.responseCode("""{"code":10101001}"""))
        listOf(null, "{}", "[]", "broken", """{"code":false}""").forEach {
            assertNull(XiaobuCourseCard.responseCode(it))
        }
    }

    @Test fun opensAtLeadAndWithdrawsAtBellEvenWhenWorkerIsLate() {
        val before = XiaobuCourseCard.window(table, start - lead - 1, lead)
        assertNull(before.course)
        assertEquals(start - lead, before.nextChange)
        val due = XiaobuCourseCard.window(table, start - lead, lead)
        assertEquals("数学", due.course!!.courseName)
        assertEquals(start, due.nextChange)
        val after = XiaobuCourseCard.window(table, start + 1, lead)
        assertNull(after.course)
        assertEquals(time("2026-09-14T08:00"), after.nextChange)
        assertNull(XiaobuCourseCard.window(null, start, lead).nextChange)
        assertNull(XiaobuCourseCard.window(table, time("2026-11-24T10:00"), lead).course)
    }

    @Test fun changesUseOriginalCourseEntityAndOnlySendDisplayFields() {
        val event = XiaobuCourseCard.window(table, start - lead, lead).course!!
        val scene = XiaobuCourseScene("test.intent", "test-card", "test-scene", 1, "test-state")
        val created = XiaobuCourseCard.payload(scene, event, false, "request1", start)
        val changed = XiaobuCourseCard.payload(scene, event.copy(room = "教学楼 A202"), true, "request2", start)
        val original = created.getValue("intentEntity").jsonObject
        val update = changed.getValue("intentEntity").jsonObject
        assertEquals(original["entityId"], update["entityId"])
        assertEquals("0", created.getValue("intentAction").jsonObject.getValue("actionStatus").jsonPrimitive.content)
        assertEquals("1", changed.getValue("intentAction").jsonObject.getValue("actionStatus").jsonPrimitive.content)
        assertEquals("08:10–09:40 · 教学楼 A202", update.getValue("primary").jsonObject.getValue("content").jsonPrimitive.content)
        assertEquals(setOf("launcher"), created.getValue("serviceId").jsonObject.keys)
        assertFalse(changed.toString().contains("教师甲"))
    }
}
