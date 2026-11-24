package com.furina.classtools.data.timetable

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 课表 → WakeUp 课程表备份文件 `.wakeup_schedule`（纯函数，可单测）。
 *
 * **格式依据（2026-11 核实）**
 * - WakeUp 开源版导出实现（`ScheduleViewModel.exportData`）把 5 个 Gson 对象按顺序写进同一文件，每行一个：
 *   1. `TimeTableBean`（`id/name/sameLen/courseLen` 时间表元数据）
 *   2. `List<TimeDetailBean>`（`node/startTime/endTime/timeTable` 节次时间）
 *   3. `TableBean`（`id/tableName/nodes/startDate/maxWeek/timeTable/...` 课表元数据）
 *   4. `List<CourseBaseBean>`（`id/courseName/color/tableId` 课程定义）
 *   5. `List<CourseDetailBean>`（`id/day/room/teacher/startNode/step/startWeek/endWeek/type/tableId` 课程安排）
 *   - 来源：https://github.com/gitleon/WakeupSchedule_Kotlin
 *     （`app/src/main/java/com/suda/yzune/wakeupschedule/schedule/ScheduleViewModel.kt#exportData`、
 *      `bean/TableBean.kt`、`bean/TimeDetailBean.kt`、`bean/CourseBaseBean.kt`、`bean/CourseDetailBean.kt`）
 *   - 导入侧（`ImportViewModel.importFromFile`）按同一顺序 `readLines()` 解析且要求文件名含 `wakeup_schedule`；
 *     导入时会重命名课表为 `分享_<名称>` 并重排 id/tableId，因此本文件只需保证结构与字段齐全。
 * - 第三方转换器 [Xtao-Labs/WakeUp2XiaoAi](https://github.com/Xtao-Labs/WakeUp2XiaoAi) 只读第 4、5 行，
 *   并注明 `type: 0=每周 / 1=单周 / 2=双周`；节次时间（第 2 行）是 `startTime/endTime` 字符串 `HH:mm`。
 * - WakeUp 的「分享口令」是**服务端生成**的短码（如 `PaJ_8Kj_zeelspJs2HBL1`），离线无法伪造；
 *   因此本 App 只生成可离线导入的备份文件，不伪造分享口令。
 *
 * 兼容性说明：该格式来自 WakeUp 开源版（3.612）与 2026 年第三方解析器文档，字段缺失时 Gson 会置默认值，
 * 所以这里**写全 Gson 会序列化的字段**（含颜色/尺寸等展示设置），避免导入后样式错乱。
 */
object WakeUpSchedule {

    /** 备份文件扩展名（WakeUp 导入按文件名含该串判断）。 */
    const val EXTENSION: String = "wakeup_schedule"

    /** 课表名（WakeUp 导入后显示为「分享_班级课表」）。 */
    const val DEFAULT_TABLE_NAME: String = "班级课表"

    /** WakeUp 课程颜色（`#AARRGGBB`；WakeUp2XiaoAi 用 `color[3:]` 当背景色，故前两位必须存在）。 */
    private val PALETTE: List<String> = listOf(
        "#ff8fb7c7", "#ffb7c78f", "#ffc79f8f", "#ff9f8fc7",
        "#ff8fc7a9", "#ffc7b88f", "#ff8f9fc7", "#ffc78fa6",
        "#ffa9c78f", "#ffc7a98f", "#ff8fc4c7", "#ffb6a9c7",
    )

    /** 生成结果（`text` 为 5 行 JSON；`courseCount`=课程定义数，`detailCount`=课程安排行数）。 */
    data class WakeUpDocument(
        val text: String,
        val courseCount: Int,
        val detailCount: Int,
        val maxWeek: Int,
        val startDate: String,
        val tableName: String,
    )

    /**
     * 生成 WakeUp 备份文件内容。
     *
     * @return 无开学日（无法确定 `startDate`）或无合法课程时返回 null。
     */
    fun build(
        snapshot: TtSnapshot,
        tableName: String = DEFAULT_TABLE_NAME,
        maxWeeks: Int = TimetableLink.semesterWeeks(snapshot.c),
    ): WakeUpDocument? {
        val termStart = TtCalendar.parseTermStart(snapshot.termStart) ?: return null
        val weeks = maxWeeks.coerceIn(1, TimetableLink.MAX_SEMESTER_WEEKS)
        val courses = snapshot.c.filter(::validCourse)
        if (courses.isEmpty()) return null
        val name = tableName.trim().ifBlank { DEFAULT_TABLE_NAME }
        val startDate = termStart.toString()
        val maxWeek = weeks

        // 课程定义：同一课程名共用一条 base（WakeUp 用 id 关联安排行）。
        val baseIds = LinkedHashMap<String, Int>()
        courses.sortedWith(compareBy({ it.name }, { it.day }, { it.start }, { it.end })).forEach { course ->
            baseIds.getOrPut(course.name.trim()) { baseIds.size + 1 }
        }
        val baseList = baseIds.entries.mapIndexed { index, entry ->
            buildJsonObject {
                put("id", entry.value)
                put("courseName", entry.key)
                put("color", PALETTE[index % PALETTE.size])
                put("tableId", TABLE_ID)
            }
        }

        // 课程安排：每门课每个连续周次段一行（非连续周次/单双周可拆成多行，WakeUp 用主键区分）。
        val detailList = ArrayList<JsonObject>()
        courses.sortedWith(compareBy({ it.day }, { it.start }, { it.name })).forEach { course ->
            val baseId = baseIds[course.name.trim()] ?: return@forEach
            val type = when (course.parity) {
                "odd" -> TYPE_ODD
                "even" -> TYPE_EVEN
                else -> TYPE_ALL
            }
            TimetableLink.weekGroups(course, weeks).forEach { group ->
                if (group.size <= 0) return@forEach
                detailList.add(
                    buildJsonObject {
                        put("id", baseId)
                        put("day", course.day + 1) // WakeUp: 1=周一 … 7=周日
                        put("room", course.room.trim())
                        put("teacher", course.teacher.trim())
                        put("startNode", course.start)
                        put("step", course.end - course.start + 1)
                        put("startWeek", group.first)
                        put("endWeek", group.last)
                        put("type", type)
                        put("tableId", TABLE_ID)
                    },
                )
            }
        }
        if (detailList.isEmpty()) return null

        val lines = listOf(
            timeTableLine(name),
            periodTimesLine(),
            tableLine(name = name, startDate = startDate, maxWeek = maxWeek),
            JsonArray(baseList).toString(),
            JsonArray(detailList).toString(),
        )
        return WakeUpDocument(
            text = lines.joinToString("\n"),
            courseCount = baseList.size,
            detailCount = detailList.size,
            maxWeek = maxWeek,
            startDate = startDate,
            tableName = name,
        )
    }

    // =========================================================================
    // 5 行内容
    // =========================================================================

    /** 第 1 行：`TimeTableBean`（时间表元数据）。 */
    private fun timeTableLine(name: String): String = buildJsonObject {
        put("id", TIME_TABLE_ID)
        put("name", name)
        put("sameLen", false) // 课程节数不同（2 节/4 节等）
        put("courseLen", COURSE_LEN)
    }.toString()

    /** 第 2 行：`List<TimeDetailBean>`（12 节固定时间；WakeUp 用 node 对应 startNode/step 的起止时间）。 */
    private fun periodTimesLine(): String {
        val array = buildJsonArray {
            for (node in 1..TtPeriods.COUNT) {
                add(
                    buildJsonObject {
                        put("node", node)
                        put("startTime", TtPeriods.startText(node))
                        put("endTime", TtPeriods.endText(node))
                        put("timeTable", TIME_TABLE_ID)
                    },
                )
            }
        }
        return array.toString()
    }

    /**
     * 第 3 行：`TableBean`（课表元数据 + 展示设置）。
     *
     * 字段与 WakeUp 开源版 `TableBean` 一一对应；颜色沿用其默认（`0x80ffffff` 描边、黑字、白课程字）。
     */
    private fun tableLine(name: String, startDate: String, maxWeek: Int): String = buildJsonObject {
        put("id", TABLE_ID)
        put("tableName", name)
        put("nodes", TtPeriods.COUNT) // 节次数（本课表固定 12 节）
        put("background", "")
        put("timeTable", TIME_TABLE_ID)
        put("startDate", startDate)
        put("maxWeek", maxWeek)
        put("itemHeight", ITEM_HEIGHT)
        put("itemAlpha", ITEM_ALPHA)
        put("itemTextSize", ITEM_TEXT_SIZE)
        put("widgetItemHeight", ITEM_HEIGHT)
        put("widgetItemAlpha", ITEM_ALPHA)
        put("widgetItemTextSize", ITEM_TEXT_SIZE)
        // 0x80ffffff.toInt() / 0xff000000.toInt() / 0xffffffff.toInt()（Gson 按 Int 解析）
        put("strokeColor", STROKE_COLOR)
        put("widgetStrokeColor", STROKE_COLOR)
        put("textColor", TEXT_COLOR)
        put("widgetTextColor", TEXT_COLOR)
        put("courseTextColor", COURSE_TEXT_COLOR)
        put("widgetCourseTextColor", COURSE_TEXT_COLOR)
        put("showSat", true)
        put("showSun", true)
        put("sundayFirst", false)
        put("showOtherWeekCourse", true)
        put("showTime", false)
        put("type", 0)
    }.toString()

    private fun validCourse(course: Course): Boolean =
        course.name.isNotBlank() &&
            course.day in 0..6 &&
            course.start in 1..TtPeriods.COUNT &&
            course.end in course.start..TtPeriods.COUNT

    // 常量（与 WakeUp 开源版默认值对齐）
    private const val TABLE_ID = 1
    private const val TIME_TABLE_ID = 1
    private const val COURSE_LEN = 40
    private const val ITEM_HEIGHT = 56
    private const val ITEM_ALPHA = 60
    private const val ITEM_TEXT_SIZE = 12
    private const val STROKE_COLOR = -2130706433 // 0x80ffffff
    private const val TEXT_COLOR = -16777216 // 0xff000000
    private const val COURSE_TEXT_COLOR = -1 // 0xffffffff
    private const val TYPE_ALL = 0
    private const val TYPE_ODD = 1
    private const val TYPE_EVEN = 2
}
