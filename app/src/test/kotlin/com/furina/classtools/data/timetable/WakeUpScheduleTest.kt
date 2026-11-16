package com.furina.classtools.data.timetable

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WakeUp 备份文件（`.wakeup_schedule`）单测：5 行 JSON 结构 / 字段映射 / 单双周 / 非连续周次。
 *
 * 格式依据：WakeUp 开源版 `ScheduleViewModel.exportData` + `ImportViewModel.importFromFile`
 * （https://github.com/gitleon/WakeupSchedule_Kotlin）；第三方解析器
 * [Xtao-Labs/WakeUp2XiaoAi](https://github.com/Xtao-Labs/WakeUp2XiaoAi)（`type: 0=每周/1=单周/2=双周`）。
 */
class WakeUpScheduleTest {

    private fun course(
        name: String = "高等数学",
        teacher: String = "王老师",
        room: String = "01105",
        day: Int = 0,
        start: Int = 1,
        end: Int = 2,
        weeks: String = "1-16",
        parity: String = "all",
    ) = Course(
        name = name,
        teacher = teacher,
        room = room,
        day = day,
        start = start,
        end = end,
        weeks = weeks,
        parity = parity,
    )

    private fun snapshot(vararg courses: Course) =
        TtSnapshot(c = courses.toList(), t = "2026-08-31")

    private fun parseLines(document: WakeUpSchedule.WakeUpDocument): List<String> =
        document.text.split('\n').also { assertEquals(5, it.size) }

    private fun elementAt(lines: List<String>, index: Int) = Json.parseToJsonElement(lines[index])

    private fun jsonObjectAt(lines: List<String>, index: Int): JsonObject = elementAt(lines, index).jsonObject

    private fun jsonArrayAt(lines: List<String>, index: Int): JsonArray = elementAt(lines, index).jsonArray

    // ---- 结构 ---------------------------------------------------------------

    @Test
    fun build_producesExactlyFiveJsonLines() {
        val doc = WakeUpSchedule.build(snapshot(course()))
        assertNotNull(doc)
        val lines = parseLines(doc!!)
        // 每行都必须是合法 JSON（WakeUp 用 Gson 逐行解析）
        lines.forEach { line -> assertNotNull(Json.parseToJsonElement(line)) }
    }

    @Test
    fun build_returnsNullWithoutTermStartOrCourses() {
        assertNull(WakeUpSchedule.build(TtSnapshot(c = listOf(course()), t = "")))
        assertNull(WakeUpSchedule.build(TtSnapshot(c = emptyList(), t = "2026-08-31")))
        assertNull(WakeUpSchedule.build(TtSnapshot(c = listOf(course(day = 9)), t = "2026-08-31")))
    }

    // ---- 第 1 行：TimeTableBean --------------------------------------------

    @Test
    fun line1_timeTableMetadata() {
        val lines = parseLines(WakeUpSchedule.build(snapshot(course()))!!)
        val obj = jsonObjectAt(lines, 0)
        assertEquals(1, obj["id"]!!.jsonPrimitive.int)
        assertEquals("班级课表", obj["name"]!!.jsonPrimitive.content)
        assertFalse(obj["sameLen"]!!.jsonPrimitive.boolean)
        assertEquals(50, obj["courseLen"]!!.jsonPrimitive.int)
    }

    // ---- 第 2 行：TimeDetailBean 列表 --------------------------------------

    @Test
    fun line2_periodTimesMatchFixedTimetable() {
        val lines = parseLines(WakeUpSchedule.build(snapshot(course()))!!)
        val periods = jsonArrayAt(lines, 1)
        assertEquals(12, periods.size)
        val first = periods[0].jsonObject
        assertEquals(1, first["node"]!!.jsonPrimitive.int)
        assertEquals("08:10", first["startTime"]!!.jsonPrimitive.content)
        assertEquals("08:55", first["endTime"]!!.jsonPrimitive.content)
        assertEquals(1, first["timeTable"]!!.jsonPrimitive.int)
        val last = periods[11].jsonObject
        assertEquals(12, last["node"]!!.jsonPrimitive.int)
        assertEquals("20:30", last["startTime"]!!.jsonPrimitive.content)
        assertEquals("21:20", last["endTime"]!!.jsonPrimitive.content)
    }

    // ---- 第 3 行：TableBean ------------------------------------------------

    @Test
    fun line3_tableMetadataCarriesStartDateAndMaxWeek() {
        val lines = parseLines(WakeUpSchedule.build(snapshot(course(weeks = "1-16")))!!)
        val table = jsonObjectAt(lines, 2)
        assertEquals("班级课表", table["tableName"]!!.jsonPrimitive.content)
        assertEquals("2026-08-31", table["startDate"]!!.jsonPrimitive.content)
        assertEquals(18, table["maxWeek"]!!.jsonPrimitive.int)
        assertEquals(12, table["nodes"]!!.jsonPrimitive.int)
        assertEquals(1, table["timeTable"]!!.jsonPrimitive.int)
        assertEquals(0, table["type"]!!.jsonPrimitive.int)
        assertEquals(-2130706433, table["strokeColor"]!!.jsonPrimitive.int)
        assertEquals(-16777216, table["textColor"]!!.jsonPrimitive.int)
        assertEquals(-1, table["courseTextColor"]!!.jsonPrimitive.int)
        assertTrue(table["showSat"]!!.jsonPrimitive.boolean)
        assertTrue(table["showSun"]!!.jsonPrimitive.boolean)
        assertFalse(table["sundayFirst"]!!.jsonPrimitive.boolean)
        assertFalse(table["showTime"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun line3_maxWeekFollowsSpec() {
        val document = WakeUpSchedule.build(snapshot(course(weeks = "1-20")))!!
        assertEquals(20, document.maxWeek)
        val table = jsonObjectAt(parseLines(document), 2)
        assertEquals(20, table["maxWeek"]!!.jsonPrimitive.int)
    }

    // ---- 第 4 行：CourseBaseBean 列表 --------------------------------------

    @Test
    fun line4_uniqueCourseNamesAndColors() {
        val lines = parseLines(
            WakeUpSchedule.build(
                snapshot(
                    course(name = "高等数学"),
                    course(name = "高等数学", day = 2),
                    course(name = "大学物理", day = 4),
                ),
            )!!,
        )
        val bases = jsonArrayAt(lines, 3)
        assertEquals(2, bases.size)
        assertEquals(
            setOf("高等数学", "大学物理"),
            bases.map { it.jsonObject["courseName"]!!.jsonPrimitive.content }.toSet(),
        )
        bases.forEach { base ->
            val color = base.jsonObject["color"]!!.jsonPrimitive.content
            assertEquals(9, color.length)
            assertTrue(color.startsWith("#ff"))
            assertEquals(1, base.jsonObject["tableId"]!!.jsonPrimitive.int)
        }
    }

    // ---- 第 5 行：CourseDetailBean 列表 ------------------------------------

    @Test
    fun line5_courseDetailMapping() {
        val document = WakeUpSchedule.build(snapshot(course()))!!
        val details = jsonArrayAt(parseLines(document), 4)
        assertEquals(1, details.size)
        val detail = details[0].jsonObject
        assertEquals(1, detail["id"]!!.jsonPrimitive.int)
        assertEquals(1, detail["day"]!!.jsonPrimitive.int) // 0=周一 → WakeUp 1=周一
        assertEquals("01105", detail["room"]!!.jsonPrimitive.content)
        assertEquals("王老师", detail["teacher"]!!.jsonPrimitive.content)
        assertEquals(1, detail["startNode"]!!.jsonPrimitive.int)
        assertEquals(2, detail["step"]!!.jsonPrimitive.int)
        assertEquals(1, detail["startWeek"]!!.jsonPrimitive.int)
        assertEquals(16, detail["endWeek"]!!.jsonPrimitive.int)
        assertEquals(0, detail["type"]!!.jsonPrimitive.int)
        assertEquals(1, detail["tableId"]!!.jsonPrimitive.int)
        assertEquals(1, document.detailCount)
    }

    @Test
    fun line5_oddAndEvenParityUseDifferentTypes() {
        val oddDoc = WakeUpSchedule.build(snapshot(course(name = "单周课", weeks = "1-16", parity = "odd")))!!
        val odd = jsonArrayAt(parseLines(oddDoc), 4)[0].jsonObject
        assertEquals(1, odd["type"]!!.jsonPrimitive.int)
        assertEquals(1, odd["startWeek"]!!.jsonPrimitive.int)
        assertEquals(15, odd["endWeek"]!!.jsonPrimitive.int)

        val evenDoc = WakeUpSchedule.build(snapshot(course(name = "双周课", weeks = "1-16", parity = "even")))!!
        val even = jsonArrayAt(parseLines(evenDoc), 4)[0].jsonObject
        assertEquals(2, even["type"]!!.jsonPrimitive.int)
        assertEquals(2, even["startWeek"]!!.jsonPrimitive.int)
        assertEquals(16, even["endWeek"]!!.jsonPrimitive.int)
    }

    @Test
    fun line5_nonContiguousWeeksSplitIntoRows() {
        val document = WakeUpSchedule.build(snapshot(course(weeks = "1-4,8-10")))!!
        val details = jsonArrayAt(parseLines(document), 4)
        assertEquals(2, details.size)
        assertEquals(2, document.detailCount)
        val ranges = details.map {
            it.jsonObject["startWeek"]!!.jsonPrimitive.int to it.jsonObject["endWeek"]!!.jsonPrimitive.int
        }
        assertEquals(listOf(1 to 4, 8 to 10), ranges)
        // 同一门课共用同一个 course id
        assertEquals(1, details.map { it.jsonObject["id"]!!.jsonPrimitive.int }.distinct().size)
    }

    @Test
    fun line5_blankWeeksCoversWholeSemester() {
        val document = WakeUpSchedule.build(snapshot(course(weeks = "")))!!
        val detail = jsonArrayAt(parseLines(document), 4)[0].jsonObject
        assertEquals(1, detail["startWeek"]!!.jsonPrimitive.int)
        assertEquals(18, detail["endWeek"]!!.jsonPrimitive.int)
    }

    @Test
    fun build_skipsInvalidCoursesButKeepsValidOnes() {
        val document = WakeUpSchedule.build(
            snapshot(
                course(name = "合法课", day = 1),
                course(name = "", day = 2),
                course(name = "坏节次", start = 13),
                course(name = "坏星期", day = 8),
            ),
        )!!
        assertEquals(1, document.courseCount)
        assertEquals(1, document.detailCount)
    }

    @Test
    fun build_reportsCountsAndName() {
        val document = WakeUpSchedule.build(
            snapshot(course(name = "高数"), course(name = "物理", day = 1, start = 3, end = 4)),
        )!!
        assertEquals("班级课表", document.tableName)
        assertEquals("2026-08-31", document.startDate)
        assertEquals(2, document.courseCount)
        assertEquals(2, document.detailCount)
        assertEquals(18, document.maxWeek)
    }

    @Test
    fun build_nameOverride() {
        val document = WakeUpSchedule.build(snapshot(course()), tableName = "2026秋")
        assertNotNull(document)
        assertEquals("2026秋", document!!.tableName)
        val line1 = jsonObjectAt(parseLines(document), 0)
        assertEquals("2026秋", line1["name"]!!.jsonPrimitive.content)
        val line3 = jsonObjectAt(parseLines(document), 2)
        assertEquals("2026秋", line3["tableName"]!!.jsonPrimitive.content)
    }

    @Test
    fun build_extensionMatchesWakeUpImportCheck() {
        assertEquals("wakeup_schedule", WakeUpSchedule.EXTENSION)
    }

    @Test
    fun build_overflowLongOffsetHandled() {
        val document = WakeUpSchedule.build(snapshot(course(weeks = "1-25")))!!
        val detail = jsonArrayAt(parseLines(document), 4)[0].jsonObject
        assertEquals(1, detail["startWeek"]!!.jsonPrimitive.int)
        assertEquals(25, detail["endWeek"]!!.jsonPrimitive.int)
        assertEquals(25, document.maxWeek)
    }

    @Test
    fun build_longFieldsRoundTrip() {
        val name = "高等数学（含,逗号）"
        val document = WakeUpSchedule.build(
            snapshot(course(name = name, room = "教学楼;A", teacher = "王\\老师")),
        )!!
        parseLines(document) // 结构合法
        val base = jsonArrayAt(parseLines(document), 3)[0].jsonObject
        assertEquals(name, base["courseName"]!!.jsonPrimitive.content)
        val detail = jsonArrayAt(parseLines(document), 4)[0].jsonObject
        assertEquals("教学楼;A", detail["room"]!!.jsonPrimitive.content)
        assertEquals("王\\老师", detail["teacher"]!!.jsonPrimitive.content)
        assertEquals(1, detail["startNode"]!!.jsonPrimitive.int)
        assertEquals(2, detail["step"]!!.jsonPrimitive.int)
        assertEquals(1, base["id"]!!.jsonPrimitive.int)
        assertEquals(1, base["id"]!!.jsonPrimitive.long.toInt())
    }
}
