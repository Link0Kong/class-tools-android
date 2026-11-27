package com.furina.classtools.widget

import com.furina.classtools.data.homework.*
import com.furina.classtools.data.timetable.SessionState
import com.furina.classtools.data.timetable.TtPlan
import com.furina.classtools.data.timetable.CourseFocus
import com.furina.classtools.data.timetable.SessionInfo
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable

internal enum class WidgetKind(val label: String, val destination: String) {
    TIMETABLE("班级课表", "tt"), WORKBENCH("工作台", "home"),
    NOTICES("班级通知", "nt"), TASKS("待办任务", "hw"),
}
@Serializable
internal data class WidgetLine(val title: String, val detail: String = "", val destination: String = "", val priority: Int = 5, val homeworkId: String = "", val focus: String = "",
    val deadlineAt: Long? = null, val starred: Boolean = false, val canComplete: Boolean = false,
    val excerpt: String = "")

@Serializable
internal data class WidgetList(
    val count: Int,
    val rows: List<WidgetLine>,
    val fetchedAt: Long,
    val taskVersion: Long? = null,
    val importedHomeworkIds: Set<String> = rows.mapNotNullTo(mutableSetOf()) { it.homeworkId.takeIf(String::isNotBlank) },
    // Older feeds kept one leading record per quadrant in addition to the compact cache.
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
        return WidgetList(items.size, lines, fetchedAt)
    }
}

internal data class WidgetContent(
    val compactStatus: String,
    val first: WidgetLine,
    val more: List<WidgetLine> = emptyList(),
    val summary: String = "",
    val sections: List<WidgetSection> = emptyList(),
    val createTask: Boolean = false,
    val hasRecords: Boolean = true,
    val cachedRemainder: Int = 0,
    val compactDetail: String = first.detail,
)

internal data class WidgetSection(
    val title: String,
    val link: WidgetLine,
    val lines: List<WidgetLine>,
    val empty: String,
    val status: String = "",
)

internal fun quadrantOf(line: WidgetLine, tomorrow: Long): Int {
    val urgent = line.deadlineAt?.let { it < tomorrow } == true
    return (if (line.starred) 0 else 2) + (if (urgent) 0 else 1)
}

private fun scheduleLine(course: SessionInfo, day: String = "") = WidgetLine(
    course.name, listOf(day, "${course.startText}–${course.endText}", course.fullRoom).filter(String::isNotBlank).joinToString(" · "),
    "tt", focus = CourseFocus.of(course).encode(),
)

internal fun widgetContent(kind: WidgetKind, signedIn: Boolean, plan: TtPlan?, feed: WidgetFeed, now: Long, tomorrowCourses: List<SessionInfo> = emptyList()): WidgetContent {
    if (!signedIn) return WidgetContent(kind.label, WidgetLine("登录后查看", "点按打开枫丹工具箱"), hasRecords = false)
    val zone = ZoneId.systemDefault()
    val localDay = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val tomorrow = localDay.plusDays(1)
    val active = plan?.today?.firstOrNull { it.state == SessionState.ONGOING }
    val course = active ?: plan?.next
    val courseLine = course?.let { scheduleLine(it, if (it.dayOffset > 0) it.dayText else "") }
        ?: WidgetLine(if (plan?.hasData == true) "近期暂无课程" else "打开课表同步课程", destination = "tt")
    val courseLabel = when {
        active != null -> "正在上课"
        course != null -> listOf(course.dateText.takeIf { course.dayOffset > 0 }.orEmpty(), course.dayText, "下一节").filter(String::isNotBlank).joinToString(" · ")
        else -> "今日课表"
    }
    fun countText(list: WidgetList?, unit: String) = list?.let { "${it.count} $unit" } ?: "待同步"
    return when (kind) {
        WidgetKind.TIMETABLE -> {
            val later = (plan?.today.orEmpty() + tomorrowCourses + listOfNotNull(plan?.next))
                .filter { it.startAt > (course?.startAt ?: now) }
                .distinctBy { CourseFocus.of(it) }.sortedBy { it.startAt }
            val upcoming = later.map {
                scheduleLine(it, when (Instant.ofEpochMilli(it.startAt).atZone(zone).toLocalDate()) {
                    localDay -> ""
                    tomorrow -> "明天"
                    else -> it.dayText
                })
            }
            val daySummary = plan?.takeIf { it.hasData }?.let {
                listOfNotNull(it.week?.let { week -> "第 ${week} 周" }, "今日 ${it.today.size} 门").joinToString(" · ")
            }.orEmpty()
            val days = if (plan?.hasData == true && (course == null || course.dayOffset <= 1)) {
                listOf(localDay to plan.today, tomorrow to tomorrowCourses).mapIndexed { index, (date, sessions) ->
                    val link = WidgetLine("", destination = "tt", focus = CourseFocus(date.toString()).encode())
                    val ordered = sessions.filterNot { it.state == SessionState.DONE }
                    WidgetSection(if (index == 0) "今天" else "明天", link, ordered.map { scheduleLine(it) },
                        if (sessions.isEmpty()) "没有课程" else "课程已结束")
                }
            } else emptyList()
            val quickStatus = when {
                active != null -> "${active.endText} 下课"
                course != null -> "${course.startText} 开始"
                else -> "今日课表"
            }
            WidgetContent(quickStatus, courseLine, upcoming,
                listOf(quickStatus, daySummary).filter(String::isNotBlank).joinToString(" · "),
                sections = days, hasRecords = course != null, compactDetail = course?.let {
                    listOf(if (it.dayOffset > 0) it.dayText else "", it.fullRoom)
                        .filter(String::isNotBlank).joinToString(" · ")
                }.orEmpty())
        }
        WidgetKind.WORKBENCH -> {
            val tasks = feed.tasks
            val notices = feed.notices
            val overview = listOf(tasks?.let { "${it.count} 待办" } ?: "待办待同步",
                notices?.let { "${it.count} 待处理" } ?: "通知待同步")
            val assignments = feed.homework?.current(now, tasks?.importedHomeworkIds.orEmpty())
            val highlights = buildList {
                assignments?.rows?.firstOrNull()?.let { add(it) }
                tasks?.rows?.firstOrNull()?.let { add(it.copy(title = "待办 · " + it.title, destination = "hw")) }
                notices?.rows?.firstOrNull()?.let { add(it.copy(title = "通知 · " + it.title, destination = "nt")) }
                if (course != null) add(courseLine.copy(title = courseLabel + " · " + courseLine.title,
                    priority = if (course.startAt in now..(now + 15 * 60_000L)) 2 else 6))
            }.sortedBy { it.priority }
            fun section(title: String, dest: String, list: WidgetList?, empty: String) =
                WidgetSection(title, WidgetLine("", destination = dest),
                    listOfNotNull(list?.rows?.firstOrNull()?.copy(destination = dest)),
                    if (list == null) "待同步" else empty, list?.count?.takeIf { it > 0 }?.toString().orEmpty())
            val tiles = listOf(
                WidgetSection("课程", WidgetLine("", destination = "tt"), listOfNotNull(courseLine.takeIf { course != null }),
                    if (plan?.hasData == true) "暂无课程" else "待同步",
                    if (active != null) "上课中" else if (course != null) "下一节" else ""),
                section("个人待办", "hw", tasks, "暂无待办"),
                section("班级作业", "class-homework", assignments, "暂无作业"),
                section("班级通知", "nt", notices, "暂无待处理"),
            )
            WidgetContent("${tiles.size} 个入口", WidgetLine(overview.joinToString(" · "),
                highlights.firstOrNull()?.title.orEmpty()), highlights, "${localDay.monthValue}月${localDay.dayOfMonth}日", tiles)
        }
        WidgetKind.NOTICES, WidgetKind.TASKS -> {
            val tasks = kind == WidgetKind.TASKS
            val list = if (tasks) feed.tasks else feed.latestNotices
            val assignments = if (tasks) feed.homework?.current(now, list?.importedHomeworkIds.orEmpty()) else null
            val cached = list?.rows.orEmpty()
            val cachedIds = cached.mapTo(hashSetOf()) { it.focus }
            val legacyLeads = if (tasks) list?.quadrantLeads.orEmpty().filterNot { it.focus in cachedIds } else emptyList()
            val personal = (cached + legacyLeads).map {
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
                else countText(list, if (tasks) "项待办" else "条通知") +
                    if (list != null && now - list.fetchedAt >= 60 * 60_000L) " · 上次同步" else ""
            val primary = rows.firstOrNull()
            val nextMidnight = tomorrow.atStartOfDay(zone).toInstant().toEpochMilli()
            val byQuadrant = rows.groupBy { quadrantOf(it, nextMidnight) }
            val panels = if (tasks) listOf("重要且紧急", "重要不紧急", "未标星且紧急", "未标星不紧急").mapIndexed { i, text ->
                val group = byQuadrant[i].orEmpty().sortedBy { it.deadlineAt ?: Long.MAX_VALUE }
                WidgetSection(text, WidgetLine("", destination = "hw"), group, "暂无事项")
            } else emptyList()
            val count = (list?.count ?: 0) + (assignments?.count ?: 0)
            WidgetContent(when {
                    tasks && list != null -> "${list.count} 待办"
                    tasks && assignments != null -> "${assignments.count} 作业"
                    list != null -> "${list.count} 条通知"
                    else -> kind.label
                }, primary ?: WidgetLine(empty, if (list == null) "点按查看并刷新" else ""),
                rows.drop(1), summary, panels, createTask = tasks, hasRecords = primary != null,
                cachedRemainder = (count - rows.size).coerceAtLeast(0))
        }
    }
}
