package com.furina.classtools.widget

import com.furina.classtools.data.timetable.SessionState
import com.furina.classtools.data.timetable.TtPlan
import kotlinx.serialization.Serializable

internal enum class WidgetKind(val label: String, val destination: String) {
    TIMETABLE("班级课表", "tt"), WORKBENCH("工作台", "home"),
    NOTICES("班级通知", "nt"), TASKS("待办任务", "hw"),
}

@Serializable
internal data class WidgetLine(val title: String, val detail: String = "", val destination: String = "")

@Serializable
internal data class WidgetList(val count: Int, val rows: List<WidgetLine>, val fetchedAt: Long, val taskVersion: Long? = null)

@Serializable
internal data class WidgetFeed(val tasks: WidgetList? = null, val notices: WidgetList? = null,
                              val latestNotices: WidgetList? = null)

internal data class WidgetContent(
    val heading: String,
    val first: WidgetLine,
    val more: List<WidgetLine> = emptyList(),
    val summary: String = "",
)

internal fun widgetContent(kind: WidgetKind, signedIn: Boolean, plan: TtPlan?, feed: WidgetFeed, now: Long): WidgetContent {
    if (!signedIn) return WidgetContent(kind.label, WidgetLine("登录后查看", "点按打开班级工具站"))
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
    fun inbox(label: String, list: WidgetList?, unit: String, destination: String): WidgetLine =
        WidgetLine("$label · " + countText(list, unit), list?.rows?.firstOrNull()?.title.orEmpty(), destination)
    return when (kind) {
        WidgetKind.TIMETABLE -> {
            val upcoming = plan?.today.orEmpty().filter { it.startAt > (course?.startAt ?: now) }
                .map { WidgetLine(it.name, "${it.startText}–${it.endText}  ${it.fullRoom}".trim(), "tt") }.toMutableList()
            plan?.next?.takeIf { active != null && it.dayOffset > 0 }?.let {
                upcoming.add(WidgetLine(it.name, "${it.dayText} ${it.startText}  ${it.fullRoom}".trim(), "tt"))
            }
            WidgetContent(courseLabel, courseLine, upcoming, plan?.week?.let { "第 ${it} 周" }.orEmpty())
        }
        WidgetKind.WORKBENCH -> WidgetContent("工作台", courseLine, listOf(
            inbox("任务", feed.tasks, "项待办", "hw"), inbox("通知", feed.notices, "条待处理", "nt"),
        ), courseLabel)
        WidgetKind.NOTICES, WidgetKind.TASKS -> {
            val tasks = kind == WidgetKind.TASKS
            val list = if (tasks) feed.tasks else feed.latestNotices
            val empty = when {
                list == null -> "等待同步"
                tasks -> "暂无待办任务"
                else -> "暂无班级通知"
            }
            val summary = if (list != null && now - list.fetchedAt >= 60 * 60_000L) "上次同步"
                else countText(list, if (tasks) "项待办" else "条通知")
            WidgetContent(kind.label, list?.rows?.firstOrNull() ?: WidgetLine(empty,
                if (list == null) "点按查看并刷新" else ""), list?.rows.orEmpty().drop(1), summary)
        }
    }
}
