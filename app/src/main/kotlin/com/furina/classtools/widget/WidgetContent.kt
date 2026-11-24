package com.furina.classtools.widget

import com.furina.classtools.data.homework.*
import com.furina.classtools.data.timetable.SessionState
import com.furina.classtools.data.timetable.TtPlan
import kotlinx.serialization.Serializable

internal enum class WidgetKind(val label: String, val destination: String) {
    TIMETABLE("班级课表", "tt"), WORKBENCH("工作台", "home"),
    NOTICES("班级通知", "nt"), TASKS("待办任务", "hw"),
}

@Serializable
internal data class WidgetLine(val title: String, val detail: String = "", val destination: String = "", val priority: Int = 5, val homeworkId: String = "")

@Serializable
internal data class WidgetList(val count: Int, val rows: List<WidgetLine>, val fetchedAt: Long, val taskVersion: Long? = null)

@Serializable
internal data class WidgetFeed(val tasks: WidgetList? = null, val notices: WidgetList? = null,
                              val latestNotices: WidgetList? = null, val homework: WidgetHomework? = null)

/** Only pending titles and deadlines are persisted for the current account. */
@Serializable
internal data class WidgetHomework(val rows: List<HomeworkRow>, val fetchedAt: Long) {
    fun current(now: Long): WidgetList {
        val items = HomeworkDueCalculator.sort(HomeworkDueCalculator.pruneExpired(rows.map {
            HomeworkItem(it, false, HomeworkDueCalculator.evaluate(it, now), emptyList())
        }))
        val lines = items.take(8).map { item ->
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
                "class-homework", priority, item.id)
        }
        return WidgetList(items.size, lines, fetchedAt)
    }
}

internal data class WidgetContent(
    val heading: String,
    val first: WidgetLine,
    val more: List<WidgetLine> = emptyList(),
    val summary: String = "",
)

internal fun widgetContent(kind: WidgetKind, signedIn: Boolean, plan: TtPlan?, feed: WidgetFeed, now: Long): WidgetContent {
    if (!signedIn) return WidgetContent(kind.label, WidgetLine("登录后查看", "点按打开枫丹工具箱"))
    val active = plan?.today?.firstOrNull { it.state == SessionState.ONGOING }
    val course = active ?: plan?.next
    val courseLine = course?.let {
        WidgetLine(it.name, listOf("${it.startText}–${it.endText}", it.fullRoom, it.span)
            .filter(String::isNotBlank).joinToString(" · "), "tt")
    } ?: WidgetLine(if (plan?.hasData == true) "近期暂无课程" else "打开课表同步课程", destination = "tt")
    val courseLabel = when {
        active != null -> "正在上课"
        course != null -> listOf(course.dayText, "下一节").filter(String::isNotBlank).joinToString(" · ")
        else -> "今日课表"
    }
    fun countText(list: WidgetList?, unit: String) = list?.let { "${it.count} $unit" } ?: "待同步"
    return when (kind) {
        WidgetKind.TIMETABLE -> {
            val upcoming = plan?.today.orEmpty().filter { it.startAt > (course?.startAt ?: now) }
                .map { WidgetLine(it.name, "${it.startText}–${it.endText}  ${it.fullRoom}".trim(), "tt") }.toMutableList()
            plan?.next?.takeIf { active != null && it.dayOffset > 0 }?.let {
                upcoming.add(WidgetLine(it.name, "${it.dayText} ${it.startText}  ${it.fullRoom}".trim(), "tt"))
            }
            val daySummary = plan?.takeIf { it.hasData }?.let {
                listOfNotNull(it.week?.let { week -> "第 ${week} 周" }, "今日 ${it.today.size} 门").joinToString(" · ")
            }.orEmpty()
            val timeHeading = if (course == null) courseLabel else
                courseLabel + " · " + if (active == null) course.startText + " 开始" else course.endText + " 下课"
            WidgetContent(timeHeading, courseLine, upcoming, daySummary)
        }
        WidgetKind.WORKBENCH -> {
            val tasks = feed.tasks
            val notices = feed.notices
            val overview = listOf(tasks?.let { "${it.count} 待办" } ?: "任务待同步",
                notices?.let { "${it.count} 通知" } ?: "通知待同步")
            val assignments = feed.homework?.current(now)
            val sections = buildList {
                assignments?.rows?.firstOrNull { row -> tasks?.rows.orEmpty().none { it.homeworkId == row.homeworkId } }?.let { add(it) }
                tasks?.rows?.take(2)?.forEach { task -> add(task.copy(title = "待办 · " + task.title, destination = "hw")) }
                notices?.rows?.take(2)?.forEach { notice -> add(notice.copy(title = "通知 · " + notice.title, destination = "nt")) }
                if (course != null) add(courseLine.copy(title = courseLabel + " · " + courseLine.title,
                    priority = if (course.startAt in now..(now + 15 * 60_000L)) 2 else 6))
            }.sortedBy { it.priority }
            WidgetContent("工作台", WidgetLine(overview.joinToString(" · "),
                sections.firstOrNull()?.title.orEmpty()), sections, java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("M月d日 E", java.util.Locale.CHINA)))
        }
        WidgetKind.NOTICES, WidgetKind.TASKS -> {
            val tasks = kind == WidgetKind.TASKS
            val assignments = if (tasks) feed.homework?.current(now) else null
            val list = if (tasks) feed.tasks else feed.latestNotices
            val personal = list?.rows.orEmpty()
            val rows = if (assignments == null) personal else {
                val imported = personal.mapNotNull { it.homeworkId.takeIf(String::isNotBlank) }.toSet()
                (assignments.rows.filterNot { it.homeworkId in imported } + personal).sortedBy { it.priority }
            }
            val empty = when {
                list == null -> "等待同步"
                tasks -> "暂无待办任务"
                else -> "暂无班级通知"
            }
            val summary = if (tasks && assignments != null) listOf(
                list?.let { "${it.count} 待办" } ?: "待办待同步", "${assignments.count} 作业",
                ).joinToString(" · ") + if (now - assignments.fetchedAt >= 60 * 60_000L) " · 上次同步" else ""
                else if (list != null && now - list.fetchedAt >= 60 * 60_000L) "上次同步"
                else countText(list, if (tasks) "项待办" else "条通知")
            val primary = rows.firstOrNull()
            val emphasis = primary?.detail?.substringBefore(" · ")?.takeIf { it.contains("截止") || it.contains("逾期") }
            val heading = listOfNotNull(kind.label, emphasis).joinToString(" · ")
            WidgetContent(heading, primary ?: WidgetLine(empty,
                if (list == null) "点按查看并刷新" else ""), rows.drop(1), summary)
        }
    }
}
