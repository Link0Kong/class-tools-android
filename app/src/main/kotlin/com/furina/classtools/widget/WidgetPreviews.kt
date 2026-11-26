package com.furina.classtools.widget

import android.content.Context
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.widget.RemoteViews

/** Fictional data shared by the picker and the small-screen compatibility previews. */
internal object WidgetPreviews {
    private val course = WidgetLine("交互设计", "09:50–11:20 · 创意楼 302", "tt")
    private val lab = WidgetLine("材料实验", "14:00–15:30 · 实验楼 206", "tt")
    private val tomorrow = WidgetLine("视觉表达", "08:10–09:40 · 教学楼 203", "tt")
    private val task = WidgetLine("整理访谈记录", "今天 17:00 截止", "hw", priority = 1, starred = true, focus = "sample-task", canComplete = true)
    private val homework = WidgetLine("作业 · 提交实验草图", "明天 12:00 截止", "class-homework")
    private val notice = WidgetLine("作品交流会教室调整", "今天 09:20 发布", "nt")
    private fun section(title: String, vararg items: WidgetLine) =
        WidgetSection(title, WidgetLine("", destination = items.firstOrNull()?.destination.orEmpty()), items.toList(), "暂无事项")

    fun model(kind: WidgetKind): WidgetContent = when (kind) {
        WidgetKind.TIMETABLE -> WidgetContent("下一节 · 09:50 开始", course, listOf(lab), "第 6 周", "26", "课表",
            listOf(section("今天 · 周四", course, lab), section("明天 · 周五", tomorrow)))
        WidgetKind.TASKS -> WidgetContent("待办任务 · 今天 17:00 截止", task, listOf(homework, WidgetLine("核对分组名单", "周五截止", "hw", focus = "sample-group", canComplete = true)),
            "2 待办 · 1 作业", "3", "待完成",
            listOf(section("重要且紧急", task), section("重要不紧急"), section("未标星且紧急"), section("未标星不紧急", homework)), true)
        WidgetKind.WORKBENCH -> WidgetContent("工作台", WidgetLine("2 待办 · 1 待处理通知"), listOf(course, task, notice),
            "11月26日 周四", "26", "工作台",
            listOf(section("课程", course), section("个人待办", task), section("班级作业", homework), section("班级通知", notice)))
        WidgetKind.NOTICES -> WidgetContent("班级通知 · 今天 09:20", notice, listOf(WidgetLine("班会资料发布", "昨天 14:00 发布", "nt")),
            "2 条通知", "2", "条通知")
    }

    fun render(context: Context, kind: WidgetKind, width: Int, height: Int): RemoteViews {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
        }
        return WidgetViews.forHost(context, kind, model(kind), options)
    }
}
