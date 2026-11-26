package com.furina.classtools.widget

import com.furina.classtools.data.homework.*
import com.furina.classtools.data.timetable.SessionState
import com.furina.classtools.data.timetable.TtPlan
import com.furina.classtools.data.timetable.CourseFocus
import kotlinx.serialization.Serializable

internal enum class WidgetKind(val label: String, val destination: String) {
    TIMETABLE("班级课表", "tt"), WORKBENCH("工作台", "home"),
    NOTICES("班级通知", "nt"), TASKS("待办任务", "hw"),
}

@Serializable
internal data class WidgetLine(val title: String, val detail: String = "", val destination: String = "", val priority: Int = 5, val homeworkId: String = "", val focus: String = "",
    val deadlineAt: Long? = null, val starred: Boolean = false, val canComplete: Boolean = false)

@Serializable
internal data class WidgetList(
    val count: Int,
    val rows: List<WidgetLine>,
    val fetchedAt: Long,
    val taskVersion: Long? = null,
    val importedHomeworkIds: Set<String> = rows.mapNotNullTo(mutableSetOf()) { it.homeworkId.takeIf(String::isNotBlank) },
    val quadrantLeads: List<WidgetLine> = emptyList(),
)

@Serializable
internal data class WidgetFeed(val tasks: WidgetList? = null, val notices: WidgetList? = null,
                              val latestNotices: WidgetList? = null, val homework: WidgetHomework? = null)

/** Only pending titles and deadlines are persisted for the current account. */
@Serializable
internal data class WidgetHomework(val rows: List<HomeworkRow>, val fetchedAt: Long) {
    fun current(now: Long, importedIds: Set<String> = emptySet()): WidgetList {
        val items = HomeworkDueCalculator.sort(HomeworkDueCalculator.pruneExpired(rows.filterNot { it.id in importedIds }.map {
            HomeworkItem(it, false, HomeworkDueCalculator.evaluate(it, now), emptyList())
        }))
        val lines = items.map { item ->
            val priority = when {
                item.due?.expired == true -> 0
                item.due?.state == HomeworkDueState.HOURS_LEFT -> 1
                item.due != null -> 4
                else -> 5
            }
            val deadline = item.due?.let { HomeworkDueCalculator.shortDueText(item.row) + " 截止 · " + it.label }
                ?: item.row.due.ifBlank { "无截止日期" }
            val title = item.row.briefTitle()
            val subject = item.row.subject.trim().takeIf { it.isNotEmpty() && it != title }
            WidgetLine("作业 · " + title, listOfNotNull(subject, deadline).joinToString(" · "),
                "class-homework", priority, item.id, focus = item.id, deadlineAt = item.due?.atMillis)
        }
        return WidgetList(items.size, lines.take(8), fetchedAt, quadrantLeads = quadrantLeads(lines, now))
    }
}

internal data class WidgetContent(
    val heading: String,
    val first: WidgetLine,
    val more: List<WidgetLine> = emptyList(),
    val summary: String = "",
    val number: String = "",
    val label: String = "",
    val sections: List<WidgetSection> = emptyList(),
    val createTask: Boolean = false,
)

internal data class WidgetSection(val title: String, val link: WidgetLine, val lines: List<WidgetLine>, val empty: String)

internal fun quadrantOf(line: WidgetLine, tomorrow: Long): Int {
    val urgent = line.deadlineAt?.let { it < tomorrow } == true
    return (if (line.starred) 0 else 2) + (if (urgent) 0 else 1)
}

private fun scheduleLine(course: com.furina.classtools.data.timetable.SessionInfo) = WidgetLine(
    course.name, listOf("${course.startText}–${course.endText}", course.fullRoom).filter(String::isNotBlank).joinToString(" · "),
    "tt", focus = CourseFocus.of(course).encode(),
)

internal fun widgetContent(kind: WidgetKind, signedIn: Boolean, plan: TtPlan?, feed: WidgetFeed, now: Long, tomorrowCourses: List<com.furina.classtools.data.timetable.SessionInfo> = emptyList()): WidgetContent {
    if (!signedIn) return WidgetContent(kind.label, WidgetLine("登录后查看", "点按打开枫丹工具箱"))
    val localDay = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val tomorrow = localDay.plusDays(1)
    val active = plan?.today?.firstOrNull { it.state == SessionState.ONGOING }
    val course = active ?: plan?.next
    val courseLine = course?.let {
        WidgetLine(it.name, listOf("${it.startText}–${it.endText}", it.fullRoom, it.span)
            .filter(String::isNotBlank).joinToString(" · "), "tt", focus = CourseFocus.of(it).encode())
    } ?: WidgetLine(if (plan?.hasData == true) "近期暂无课程" else "打开课表同步课程", destination = "tt")
    val courseLabel = when {
        active != null -> "正在上课"
        course != null -> listOf(course.dateText.takeIf { course.dayOffset > 0 }.orEmpty(), course.dayText, "下一节").filter(String::isNotBlank).joinToString(" · ")
        else -> "今日课表"
    }
    fun countText(list: WidgetList?, unit: String) = list?.let { "${it.count} $unit" } ?: "待同步"
    return when (kind) {
        WidgetKind.TIMETABLE -> {
            val upcoming = plan?.today.orEmpty().filter { it.startAt > (course?.startAt ?: now) }
                .map { WidgetLine(it.name, "${it.startText}–${it.endText}  ${it.fullRoom}".trim(), "tt", focus = CourseFocus.of(it).encode()) }.toMutableList()
            plan?.next?.takeIf { active != null && it.dayOffset > 0 }?.let {
                upcoming.add(WidgetLine(it.name, "${it.dayText} ${it.startText}  ${it.fullRoom}".trim(), "tt", focus = CourseFocus.of(it).encode()))
            }
            val daySummary = plan?.takeIf { it.hasData }?.let {
                listOfNotNull(it.week?.let { week -> "第 ${week} 周" }, "今日 ${it.today.size} 门").joinToString(" · ")
            }.orEmpty()
            val timeHeading = if (course == null) courseLabel else
                courseLabel + " · " + if (active == null) course.startText + " 开始" else course.endText + " 下课"
            val columns = if (plan?.hasData == true && (course == null || course.dayOffset <= 1)) {
                listOf(localDay to plan.today, tomorrow to tomorrowCourses).mapIndexed { index, (date, sessions) ->
                    val link = WidgetLine("", destination = "tt", focus = CourseFocus(date.toString()).encode())
                    val ordered = sessions.filterNot { it.state == SessionState.DONE }
                    WidgetSection(if (index == 0) "今天" else "明天", link, ordered.map(::scheduleLine),
                        if (sessions.isEmpty()) "没有课程" else "课程已结束")
                }
            } else emptyList()
            WidgetContent(timeHeading, courseLine, upcoming, daySummary,
                localDay.dayOfMonth.toString(), "课表", columns)
        }
        WidgetKind.WORKBENCH -> {
            val tasks = feed.tasks
            val notices = feed.notices
            val overview = listOf(tasks?.let { "${it.count} 待办" } ?: "任务待同步",
                notices?.let { "${it.count} 待处理通知" } ?: "通知待同步")
            val assignments = feed.homework?.current(now, tasks?.importedHomeworkIds.orEmpty())
            val sections = buildList {
                assignments?.rows?.firstOrNull()?.let { add(it) }
                tasks?.rows?.take(2)?.forEach { task -> add(task.copy(title = "待办 · " + task.title, destination = "hw")) }
                notices?.rows?.take(2)?.forEach { notice -> add(notice.copy(title = "通知 · " + notice.title, destination = "nt")) }
                if (course != null) add(courseLine.copy(title = courseLabel + " · " + courseLine.title,
                    priority = if (course.startAt in now..(now + 15 * 60_000L)) 2 else 6))
            }.sortedBy { it.priority }
            fun section(title: String, dest: String, line: WidgetLine?, empty: String) =
                WidgetSection(title, WidgetLine("", destination = dest), listOfNotNull(line), empty)
            val tiles = listOf(section("课程", "tt", courseLine.takeIf { course != null }, if (plan?.hasData == true) "暂无课程" else "待同步"),
                section("个人待办", "hw", tasks?.rows?.firstOrNull()?.copy(destination = "hw"), if (tasks == null) "待同步" else "暂无待办"),
                section("班级作业", "class-homework", assignments?.rows?.firstOrNull(), if (assignments == null) "待同步" else "暂无作业"),
                section("班级通知", "nt", notices?.rows?.firstOrNull()?.copy(destination = "nt"), if (notices == null) "待同步" else "暂无待处理"))
            WidgetContent("工作台", WidgetLine(overview.joinToString(" · "),
                sections.firstOrNull()?.title.orEmpty()), sections, java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("M月d日 E", java.util.Locale.CHINA)), localDay.dayOfMonth.toString(), "工作台", tiles)
        }
        WidgetKind.NOTICES, WidgetKind.TASKS -> {
            val tasks = kind == WidgetKind.TASKS
            val list = if (tasks) feed.tasks else feed.latestNotices
            val assignments = if (tasks) feed.homework?.current(now, list?.importedHomeworkIds.orEmpty()) else null
            val personal = list?.rows.orEmpty().map {
                if (tasks) it.copy(title = "待办 · " + it.title, destination = "hw") else it.copy(destination = "nt")
            }
            val rows = if (assignments == null) personal else {
                (assignments.rows + personal).sortedWith(compareBy<WidgetLine> { it.priority }.thenBy { it.deadlineAt ?: Long.MAX_VALUE })
            }
            val empty = when {
                list == null -> "等待同步"
                tasks -> "暂无待办任务"
                else -> "暂无班级通知"
            }
            val summary = if (assignments != null && assignments.count > 0) listOf(
                list?.let { "${it.count} 待办" } ?: "待办待同步", "${assignments.count} 作业",
                ).joinToString(" · ") + if (now - assignments.fetchedAt >= 60 * 60_000L) " · 上次同步" else ""
                else if (list != null && now - list.fetchedAt >= 60 * 60_000L) "上次同步"
                else countText(list, if (tasks) "项待办" else "条通知")
            val primary = rows.firstOrNull()
            val emphasis = primary?.detail?.split(" · ")?.firstOrNull { it.contains("截止") || it.contains("逾期") }
            val heading = if (tasks) listOfNotNull(kind.label, emphasis).joinToString(" · ")
                else listOf(kind.label, primary?.detail.orEmpty()).filter(String::isNotBlank).joinToString(" · ")
            val nextMidnight = tomorrow.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val byQuadrant = (rows + list?.quadrantLeads.orEmpty().map { it.copy(destination = "hw") } +
                assignments?.quadrantLeads.orEmpty()).groupBy { quadrantOf(it, nextMidnight) }
            val panels = if (tasks && list != null) listOf("重要且紧急", "重要不紧急", "未标星且紧急", "未标星不紧急").mapIndexed { i, text ->
                val entry = byQuadrant[i]?.minWithOrNull(compareBy<WidgetLine> { it.deadlineAt ?: Long.MAX_VALUE }.thenBy { it.priority })
                WidgetSection(text, WidgetLine("", destination = "hw"), listOfNotNull(entry),
                    if (list.count > list.rows.size && (list.quadrantLeads.isEmpty() ||
                        java.time.Instant.ofEpochMilli(list.fetchedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() != localDay)) "查看列表" else "暂无事项")
            } else emptyList()
            val count = (list?.count ?: 0) + (assignments?.count ?: 0)
            WidgetContent(heading, primary ?: WidgetLine(empty,
                if (list == null) "点按查看并刷新" else ""), rows.drop(1), summary, count.toString().takeIf { list != null || assignments != null }.orEmpty(),
                if (tasks) "待完成" else "条通知", panels, createTask = tasks)
        }
    }
}

internal fun quadrantLeads(lines: List<WidgetLine>, now: Long): List<WidgetLine> {
    val zone = java.time.ZoneId.systemDefault()
    val tomorrow = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return lines.groupBy { quadrantOf(it, tomorrow) }.values.map { quadrant ->
        quadrant.minBy { it.deadlineAt ?: Long.MAX_VALUE }
    }
}
