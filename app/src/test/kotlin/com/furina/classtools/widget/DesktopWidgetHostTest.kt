package com.furina.classtools.widget

import android.app.Application
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Parcel
import android.util.SizeF
import android.view.View
import android.widget.ListView
import android.widget.RemoteViews
import android.widget.TextView
import com.furina.classtools.R
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.reminder.ReminderIntentFactory
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.session.SessionStore
import com.furina.classtools.data.timetable.SessionInfo
import com.furina.classtools.data.timetable.SessionState
import com.furina.classtools.data.timetable.TodayState
import com.furina.classtools.data.timetable.TtPlan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements

// Robolectric's default host always calls RemoteViews.reapply, even for a new
// hierarchy. Exercise the AOSP host's real inflation and size-selection code.
@Implements(AppWidgetHostView::class)
class FrameworkWidgetHost : org.robolectric.shadows.ShadowViewGroup()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], application = Application::class, qualifiers = "en-port-mdpi",
    instrumentedPackages = ["com.vivo.push.sdk"], shadows = [FrameworkWidgetHost::class])
class DesktopWidgetHostTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val feed = WidgetFeed(tasks = WidgetList(5, (1..5).map { WidgetLine("报告 $it", "周五截止") }, 1, taskVersion = 1),
        latestNotices = WidgetList(5, (1..5).map { WidgetLine("通知 $it", "周三报名") }, 1))

    private fun options(w: Int, h: Int) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h)
    }

    @Test fun widgetPickerHasDrawablePreviewsAndInstancesKeepIndependentTints() {
        for (provider in DesktopWidgets.components.values) {
            val info = app.packageManager.getReceiverInfo(ComponentName(app, provider), PackageManager.GET_META_DATA)
            info.loadXmlMetaData(app.packageManager, AppWidgetManager.META_DATA_APPWIDGET_PROVIDER).use { xml ->
                while (xml.next() != org.xmlpull.v1.XmlPullParser.START_TAG) { }
                val res = xml.getAttributeResourceValue("http://schemas.android.com/apk/res/android", "previewImage", 0)
                assertTrue(app.getDrawable(res)!!.intrinsicWidth > 100)
            }
        }
        WidgetPalette.prefs(app).edit().putString("27", "LILAC").commit()
        assertEquals(WidgetPalette.LILAC, WidgetPalette.get(app, 27))
        assertEquals(WidgetPalette.SYSTEM, WidgetPalette.get(app, 28))
        val model = widgetContent(WidgetKind.WORKBENCH, true, null, feed, 5)
        assertEquals("报告 1", model.first.title)
        assertEquals("5 待办", model.compactStatus)
        val root = host(WidgetViews.forHost(app, WidgetKind.WORKBENCH, model, options(300,245), WidgetPalette.LILAC),300,245)
        assertEquals(app.getColor(WidgetPalette.LILAC.accent), root.findViewById<TextView>(R.id.widget_heading).currentTextColor)
    }

    @Test fun taskWidgetShowsClassDeadlinesWithoutCountingThemAsPersonalTasks() {
        val now = com.furina.classtools.data.homework.HomeworkDueCalculator.parseDue("2026-11-24", "12:00")!!
        val assignment = com.furina.classtools.data.homework.HomeworkRow("h1", "物理", content = "\n  实验报告  \n交到课代表处", date = "2026-11-24", time = "16:00")
        val data = feed.copy(homework = WidgetHomework(listOf(assignment), now))
        val model = widgetContent(WidgetKind.TASKS, true, null, data, now)
        assertEquals("作业 · 实验报告", model.first.title)
        assertTrue(model.first.detail.startsWith("物理 · "))
        assertEquals("class-homework", model.first.destination)
        assertEquals("5 待办 · 1 作业", model.summary)
        assertEquals(0, data.homework!!.current(now + 4L * 86_400_000L).count)
    }

    @Test fun legacyHomeworkWidgetCacheIsReplacedByPersonalTasks() {
        val sid = "3260799999"
        WidgetFeedStore.save(app, sid, feed.copy(tasks = feed.tasks?.copy(taskVersion = null)))
        assertNull(WidgetFeedStore.read(app, sid).tasks)
        app.getSharedPreferences("tasks_" + sid, 0).edit()
            .putString("local", """{"updatedAt":2,"items":[{"id":"t1","title":"实验"},{"id":"t2","title":"已交","done":true}]}""").commit()
        val tasks = WidgetFeedStore.read(app, sid).tasks!!
        assertEquals(1, tasks.count)
        assertEquals("实验", tasks.rows.single().title)
    }

    @Test fun taskAndWorkbenchWidgetsRetainEveryPendingTaskAndFilterImportedHomework() {
        val sid = "3260799999"
        val now = 1000L
        val assignments = (1..10).map { HomeworkRow("h$it", subject = "作业 $it", ts = it.toLong()) }
        val tasks = (1..9).joinToString(",") {
            """{"id":"t$it","title":"我的任务 $it","sourceHwId":"h$it","order":$it,"done":${it == 1}}"""
        }
        val prefs = app.getSharedPreferences("tasks_" + sid, 0)
        prefs.edit().putString("local", """{"updatedAt":5,"items":[$tasks,{"id":"ranked-first","title":"自建","order":0}]}""").commit()
        WidgetFeedStore.save(app, sid, feed.copy(homework = WidgetHomework(assignments, now)))
        val data = WidgetFeedStore.read(app, sid)
        val personal = requireNotNull(data.tasks)
        assertEquals(9, personal.rows.size)
        assertFalse(personal.rows.any { it.homeworkId == "h1" })
        assertTrue(personal.rows.any { it.homeworkId == "h9" })
        fun homeworkLines(kind: WidgetKind, source: WidgetFeed): List<String> {
            val content = widgetContent(kind, true, null, source, now)
            val lines = if (kind == WidgetKind.WORKBENCH) content.sections.flatMap { it.lines }
                else listOf(content.first) + content.more
            return lines.filter { it.destination == "class-homework" }.map { it.homeworkId }
        }
        for (kind in listOf(WidgetKind.TASKS, WidgetKind.WORKBENCH)) {
            assertEquals(listOf("h10"), homeworkLines(kind, data))
            val allImported = data.copy(homework = WidgetHomework(assignments.take(9), now))
            assertTrue(homeworkLines(kind, allImported).isEmpty())
            assertFalse(widgetContent(kind, true, null, allImported, now).summary.contains("作业"))
        }
        assertEquals("9 待办 · 1 作业", widgetContent(WidgetKind.TASKS, true, null, data, now).summary)
    }

    private fun parcelled(views: RemoteViews): RemoteViews = Parcel.obtain().let { parcel ->
        try {
            views.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            RemoteViews.CREATOR.createFromParcel(parcel)
        } finally { parcel.recycle() }
    }

    private fun host(views: RemoteViews, w: Int, h: Int) = AppWidgetHostView(app).apply {
        val info = AppWidgetProviderInfo().apply {
            provider = ComponentName(app, TimetableWidget::class.java)
            org.robolectric.util.ReflectionHelpers.setField(this, "providerInfo",
                app.packageManager.getReceiverInfo(provider, PackageManager.GET_META_DATA))
        }
        setAppWidget(1, info)
        setPadding(0, 0, 0, 0)
        updateAppWidget(parcelled(views))
        measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        layout(0, 0, w, h)
    }

    @Test fun allFourProvidersAreRegisteredResizeableAndOpenTheirOwnDestination() {
        for ((kind, provider) in DesktopWidgets.components) {
            val info = app.packageManager.getReceiverInfo(ComponentName(app, provider), PackageManager.GET_META_DATA)
            assertEquals(kind.label, info.loadLabel(app.packageManager))
            info.loadXmlMetaData(app.packageManager, AppWidgetManager.META_DATA_APPWIDGET_PROVIDER).use { xml ->
                while (xml.next() != org.xmlpull.v1.XmlPullParser.START_TAG) { }
                val ns = "http://schemas.android.com/apk/res/android"
                assertEquals(3, xml.getAttributeIntValue(ns, "resizeMode", 0))
                assertTrue(xml.getAttributeResourceValue(ns, "initialLayout", 0) != 0)
            }
            val content = widgetContent(kind, true, null, feed, 2)
            val host = host(WidgetViews.forHost(app, kind, content, options(300, 240)), 300, 240)
            host.findViewById<View>(R.id.widget_root).performClick()
            assertEquals(kind.destination, shadowOf(app).nextStartedActivity.getStringExtra(ReminderIntentFactory.EXTRA_TOOL_ID))
        }
    }

    @Test fun toolAndWidgetUseTheSameSavedMonetChoice() = runBlocking {
        val settings = com.furina.classtools.data.settings.AppSettingsStore(app)
        assertFalse(settings.startupDynamicColor())
        assertFalse(settings.dynamicColor.first())
        for (enabled in listOf(false, true)) {
            settings.setDynamicColor(enabled)
            assertEquals(enabled, com.furina.classtools.data.settings.AppSettingsStore(app).startupDynamicColor())
            assertEquals(enabled, settings.dynamicColor.first())
        }
    }

    @Test fun wideTaskGroupingIncludesEveryRecordAndKeepsCreateAction() {
        val tomorrow = java.time.LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val lines = (1..16).map {
            WidgetLine("同名事项", focus = "t/$it", starred = it % 2 == 0,
                deadlineAt = if (it % 4 < 2) tomorrow - it else tomorrow + it)
        }
        val content = widgetContent(WidgetKind.TASKS, true, null, WidgetFeed(tasks = WidgetList(lines.size, lines, tomorrow - 20)), tomorrow - 20)
        val style = WidgetLayout.fit(375f, 245f, app.resources.displayMetrics).rows
        val entries = WidgetRows.entries(WidgetKind.TASKS, content, style)
        assertEquals(4, entries.filterIsInstance<WidgetEntry.Header>().size)
        assertEquals(lines.map { it.focus }.toSet(), entries.filterIsInstance<WidgetEntry.Record>().map { it.line.focus }.toSet())
        assertEquals(16, entries.filterIsInstance<WidgetEntry.Record>().size)
        val widget = host(WidgetViews.forHost(app, WidgetKind.TASKS, content, options(375, 245)), 375, 245)
        widget.findViewById<View>(R.id.widget_add).performClick()
        assertEquals(com.furina.classtools.MainActivity.WIDGET_ACTION + "new-task", shadowOf(app).nextStartedActivity.action)
    }

    @Test fun compactPrimaryAndCompleteAndAllAreIndependent() {
        val task = WidgetLine("设计任务", "周五截止", destination = "hw", focus = "t/1", canComplete = true)
        val data = WidgetContent("2 待办", task, summary = "2 项待办")
        val widget = host(WidgetViews.forHost(app, WidgetKind.TASKS, data, options(300, 60), owner = "debug:9999"), 300, 60)
        val check = widget.findViewById<View>(R.id.widget_primary_check)
        assertEquals(View.VISIBLE, check.visibility)
        assertTrue(check.performClick())
        val action = shadowOf(app).broadcastIntents.last()
        assertEquals(WidgetCommandReceiver::class.java.name, action.component?.className)
        assertEquals(listOf("complete", "t/1"), action.data?.pathSegments)
        assertEquals("debug:9999", action.data?.getQueryParameter("owner"))
        assertNull(shadowOf(app).nextStartedActivity)
        for ((id, expected) in listOf(R.id.widget_primary to "t/1", R.id.widget_summary_open to "")) {
            assertTrue(widget.findViewById<View>(id).performClick())
            assertEquals(expected, shadowOf(app).nextStartedActivity.getStringExtra(com.furina.classtools.MainActivity.CARD_FOCUS))
        }
    }

    @Test fun rowTapForwardsExactFocusAndCompletionReturnsToHost() {
        val lines = listOf(WidgetLine("同名", destination = "hw", focus = "任务/1"),
            WidgetLine("同名", destination = "class-homework", focus = "作业?2"),
            WidgetLine("同名", destination = "tt", focus = """{"date":"2026-11-27","name":"课次"}"""),
            WidgetLine("同名", destination = "nt", focus = "通知@occ:20261127"))
        for (line in lines) {
            val tap = WidgetIntents.line(line, WidgetKind.WORKBENCH).setClass(app, WidgetRowActivity::class.java)
            val activity = org.robolectric.Robolectric.buildActivity(WidgetRowActivity::class.java, tap).create().get()
            val opened = shadowOf(activity).nextStartedActivity
            assertEquals(com.furina.classtools.MainActivity.WIDGET_ACTION + line.destination, opened.action)
            assertEquals(line.focus, opened.getStringExtra(com.furina.classtools.MainActivity.CARD_FOCUS))
            assertTrue(activity.isFinishing)
        }
        val complete = android.content.Intent(app, WidgetRowActivity::class.java)
            .setData(WidgetCommandReceiver.commandUri("debug:9999", WidgetCommandReceiver.COMPLETE, "任务/1"))
        val activity = org.robolectric.Robolectric.buildActivity(WidgetRowActivity::class.java, complete).create().get()
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(listOf("complete", "任务/1"), shadowOf(app).broadcastIntents.last().data?.pathSegments)
        assertTrue(activity.isFinishing)
    }

    @Test fun minimumDefaultAndDifferentAspectRatiosAllocateTheFullViewport() {
        val kind = WidgetKind.TASKS
        val model = widgetContent(kind, true, null, feed, 2)
        fun layout(w: Int, h: Int) = host(WidgetViews.forHost(app, kind, model, options(w,h)),w,h)
        val small = layout(132, 60)
        assertEquals("报告 1", small.findViewById<TextView>(R.id.widget_course).text.toString())
        assertEquals("5 待办", small.findViewById<TextView>(R.id.widget_summary_status).text.toString())
        assertEquals(48, small.findViewById<View>(R.id.widget_summary_open).width)
        assertEquals(View.GONE, small.findViewById<View>(R.id.widget_list).visibility)
        val banner = layout(350, 60)
        assertEquals("周五截止", banner.findViewById<TextView>(R.id.widget_side_details).text.toString())
        assertEquals(View.VISIBLE, banner.findViewById<View>(R.id.widget_summary_add).visibility)
        assertEquals(View.GONE, layout(180, 99).findViewById<View>(R.id.widget_list).visibility)
        val first = layout(180, 100).findViewById<ListView>(R.id.widget_list)
        val legacy = android.os.Build.VERSION.SDK_INT < 31
        assertEquals(if (legacy) 48 else 52, first.height)
        for ((w, h) in listOf(180 to 112, 132 to 260, 375 to 280)) {
            val widget = layout(w, h)
            val surface = widget.findViewById<View>(R.id.widget_root)
            assertEquals(!legacy, surface.clipToOutline)
            assertTrue(surface.background is android.graphics.drawable.GradientDrawable)
            assertEquals(h, widget.findViewById<View>(R.id.widget_backplate).height)
            assertEquals(w, widget.findViewById<View>(R.id.widget_backplate).width)
            val list = widget.findViewById<ListView>(R.id.widget_list)
            assertEquals(View.VISIBLE, list.visibility)
            assertEquals(View.OVER_SCROLL_NEVER, list.overScrollMode)
            val safeBottom = if (legacy) { if (w < 148) 8 else 4 } else 0
            assertEquals(h - safeBottom, list.bottom)
            assertEquals(h - safeBottom - widget.findViewById<View>(R.id.widget_header).height, list.height)
        }
        val defaults = host(WidgetViews.forHost(app, kind, model, Bundle()), 180, 112)
        assertEquals(View.VISIBLE, defaults.findViewById<View>(R.id.widget_list).visibility)
    }

    @Test fun fontScalingKeepsFullEntryAndAdaptsColumns() {
        for (scale in listOf(1.3f, 2f)) {
            val config = android.content.res.Configuration(app.resources.configuration).apply { fontScale = scale }
            val context = app.createConfigurationContext(config)
            val narrow = WidgetLayout.fit(132f, 60f, context.resources.displayMetrics)
            val wide = WidgetLayout.fit(350f, 280f, context.resources.displayMetrics)
            assertFalse(narrow.collection)
            assertTrue(wide.collection)
            assertFalse(WidgetLayout.fit(132f, 280f, context.resources.displayMetrics).rows.twoColumns)
            if (scale == 2f) assertFalse(wide.rows.twoColumns)
            val model = widgetContent(WidgetKind.TASKS, true, null,
                WidgetFeed(tasks = WidgetList(1, listOf(WidgetLine("人机交互设计项目开题汇报与原型评审提交时间提醒", "本周五十八点前")), 2)), 2)
            val views = WidgetViews.forHost(context, WidgetKind.TASKS, model, options(132, 60))
            val root = host(views, 132, 60)
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_summary_open).visibility)
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_course).visibility)
        }
    }

    @Test fun compactCourseKeepsRoomSeparateFromStartTime() {
        val now = System.currentTimeMillis()
        val course = SessionInfo("示例课程", "创意楼 306", 6, 6, "14:30", "15:10", "",
            now + 60_000, now + 2_460_000, SessionState.NEXT)
        val kind = WidgetKind.TIMETABLE
        val model = widgetContent(kind, true, TtPlan(true, 3, listOf(course), TodayState.HAS, course), WidgetFeed(), now)
        val narrow = host(WidgetViews.forHost(app, kind, model, options(132, 60)), 132, 60)
        assertEquals("14:30 开始", narrow.findViewById<TextView>(R.id.widget_summary_status).text.toString())
        assertEquals("创意楼 306", narrow.findViewById<TextView>(R.id.widget_details).text.toString())
        val wide = host(WidgetViews.forHost(app, kind, model, options(350, 60)), 350, 60)
        assertEquals("14:30–15:10 · 创意楼 306", wide.findViewById<TextView>(R.id.widget_side_details).text.toString())
    }

    @Test fun workbenchKeepsAllEnabledSectionsAndStableOrderAtEitherWidth() {
        val kind = WidgetKind.WORKBENCH
        val model = widgetContent(kind, true, null, feed, 2)
        val expected = listOf("tt", "hw", "class-homework", "nt")
        assertEquals(expected, model.sections.map { it.link.destination })
        for ((width, count) in listOf(145f to 4, 355f to 2)) {
            val style = WidgetLayout.fit(width, 275f, app.resources.displayMetrics).rows
            val rows = WidgetRows.entries(kind, model, style).filterIsInstance<WidgetEntry.Tiles>()
            assertEquals(count, rows.size)
            assertEquals(expected, rows.flatMap { it.sections }.map { it.link.destination })
            val fewerSections = model.copy(sections = model.sections.take(3))
            val fewer = WidgetRows.entries(kind, fewerSections, style).filterIsInstance<WidgetEntry.Tiles>()
            assertEquals(expected.take(3), fewer.flatMap { it.sections }.map { it.link.destination })
        }
    }

    @Test fun workbenchTwoColumnCardsAlignWithDifferentTitleLengthsAndKeepSeparateListEntry() {
        val kind = WidgetKind.WORKBENCH
        val style = WidgetLayout.fit(375f, 220f, app.resources.displayMetrics).rows
        val longTitle = "数字媒体教学实践项目场地调整\n与展板材料提交"
        val sections = listOf(
            WidgetSection("课程", WidgetLine("", destination = "tt"), emptyList(), "暂无课程"),
            WidgetSection("班级通知", WidgetLine("", destination = "nt"),
                listOf(WidgetLine(longTitle, "今天发布", destination = "nt", focus = "t1")), "暂无待处理", "12"),
        )
        val remote = WidgetRows.render(app, WidgetPaint.resolve(app, WidgetPalette.SYSTEM), kind,
            WidgetEntry.Tiles(0, sections), style, null, interactive = false)
        val row = remote.apply(app, android.widget.FrameLayout(app))
        row.measure(View.MeasureSpec.makeMeasureSpec(359, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        row.layout(0, 0, row.measuredWidth, row.measuredHeight)
        val left = row.findViewById<View>(R.id.widget_tile_left)
        val right = row.findViewById<View>(R.id.widget_tile_right)
        assertEquals(left.height, right.height)
        assertEquals(left.findViewById<View>(R.id.widget_tile_fill).height, right.findViewById<View>(R.id.widget_tile_fill).height)
        assertEquals(left.findViewById<View>(R.id.widget_tile_caption).top, right.findViewById<View>(R.id.widget_tile_caption).top)
        assertTrue(left.height >= 48)
        val title = right.findViewById<TextView>(R.id.widget_tile_title)
        val detail = right.findViewById<TextView>(R.id.widget_tile_detail)
        assertEquals(2, title.lineCount)
        assertTrue(title.height >= title.layout.height)
        assertTrue(detail.height >= detail.layout.height)
        assertTrue(detail.bottom <= right.findViewById<View>(R.id.widget_tile_primary).height)
        assertEquals("通知 · 12", right.findViewById<TextView>(R.id.widget_tile_caption).text.toString())
        assertEquals("列表", right.findViewById<TextView>(R.id.widget_tile_open).text.toString())
        assertEquals("打开班级通知列表", right.findViewById<View>(R.id.widget_tile_open).contentDescription)
        assertEquals(View.GONE, left.findViewById<View>(R.id.widget_tile_open).visibility)
    }

    @Test fun workbenchStartsWithOngoingCourseAndUsesRemainingSpaceForAllSections() {
        val now = System.currentTimeMillis()
        val course = SessionInfo("交互设计", "创意楼 302", 1, 2, "09:50", "11:20", "",
            now - 60_000, now + 2_460_000, SessionState.ONGOING)
        val kind = WidgetKind.WORKBENCH
        val model = widgetContent(kind, true, TtPlan(true, 3, listOf(course), TodayState.HAS, null), feed, now)
        assertEquals("11:20 下课", model.compactStatus)
        assertEquals("创意楼 302", model.compactDetail)
        assertEquals("tt", model.first.destination)
        val compact = host(WidgetViews.forHost(app, kind, model, options(132, 60)), 132, 60)
        assertEquals("交互设计", compact.findViewById<TextView>(R.id.widget_course).text.toString())
        assertEquals("总览", compact.findViewById<TextView>(R.id.widget_summary_open).text.toString())
        compact.findViewById<View>(R.id.widget_primary).performClick()
        assertEquals(model.first.focus, shadowOf(app).nextStartedActivity.getStringExtra(com.furina.classtools.MainActivity.CARD_FOCUS))

        val wide = WidgetLayout.fit(375f, 200f, app.resources.displayMetrics, workbench = true).rows
        val tall = WidgetLayout.fit(375f, 290f, app.resources.displayMetrics, workbench = true).rows
        val flat = WidgetRows.entries(kind, model, wide).filterIsInstance<WidgetEntry.Tiles>()
        assertEquals(listOf(2, 2), flat.map { it.sections.size })
        val entries = WidgetRows.entries(kind, model, tall).filterIsInstance<WidgetEntry.Tiles>()
        assertEquals(listOf(1, 2, 1), entries.map { it.sections.size })
        assertTrue(entries.first().featured)
        assertEquals(model.sections, entries.flatMap { it.sections })
        val hero = WidgetRows.render(app, WidgetPaint.resolve(app, WidgetPalette.SYSTEM), kind,
            entries.first(), tall, null, interactive = false).apply(app, android.widget.FrameLayout(app))
        assertEquals("09:50–11:20", hero.findViewById<TextView>(R.id.widget_tile_detail).text.toString())
        assertEquals("创意楼 302", hero.findViewById<TextView>(R.id.widget_tile_context).text.toString())
    }

    @Test fun workbenchExposesNewTaskAndInlineCompletionInTwoColumns() {
        val kind = WidgetKind.WORKBENCH
        val tasks = WidgetList(1, listOf(WidgetLine("核对分组", "今天截止", focus = "task-focus", canComplete = true)), 0)
        val model = widgetContent(kind, true, null, WidgetFeed(tasks = tasks), 0)
        val options = options(375, 200)
        val board = host(WidgetViews.forHost(app, kind, model, options, owner = "debug:9999"), 375, 200)
        board.findViewById<View>(R.id.widget_add).performClick()
        assertEquals(com.furina.classtools.MainActivity.WIDGET_ACTION + "new-task", shadowOf(app).nextStartedActivity.action)
        val style = WidgetLayout.fit(375f, 200f, app.resources.displayMetrics, workbench = true).rows
        val row = WidgetRows.render(app, WidgetPaint.resolve(app, WidgetPalette.SYSTEM), kind,
            WidgetRows.entries(kind, model, style).first(), style, "debug:9999", interactive = false)
            .apply(app, android.widget.FrameLayout(app))
        val task = row.findViewById<View>(R.id.widget_tile_right)
        assertEquals(View.VISIBLE, task.findViewById<View>(R.id.widget_row_check).visibility)
        assertEquals("完成任务，核对分组", task.findViewById<View>(R.id.widget_row_check).contentDescription)
        assertEquals(View.GONE, task.findViewById<View>(R.id.widget_tile_open).visibility)
    }

    @Test fun restoredWidgetIdsKeepTheirOwnPaletteEvenWhenIdsOverlap() {
        WidgetPalette.prefs(app).edit().putString("27", "LILAC").putString("28", "AMBER").commit()
        TimetableWidget().onRestored(app, intArrayOf(27, 28), intArrayOf(28, 29))
        assertEquals(WidgetPalette.SYSTEM, WidgetPalette.get(app, 27))
        assertEquals(WidgetPalette.LILAC, WidgetPalette.get(app, 28))
        assertEquals(WidgetPalette.AMBER, WidgetPalette.get(app, 29))
    }

    @Test fun legacyCacheShowsAnAccurateRemainderAndEmptyWidgetsHaveAnEntry() {
        val kind = WidgetKind.NOTICES
        val cached = widgetContent(kind, true, null, feed.copy(latestNotices = feed.latestNotices?.copy(count = 12)), 2)
        val opts = options(300, 245)
        val large = host(WidgetViews.forHost(app, kind, cached, opts), 300, 245)
        assertEquals("另 7 项见全部", large.findViewById<TextView>(R.id.widget_header_status).text.toString())
        val empty = widgetContent(kind, true, null, feed.copy(latestNotices = WidgetList(0, emptyList(), 2)), 2)
        large.updateAppWidget(parcelled(WidgetViews.forHost(app, kind, empty, opts)))
        assertEquals(View.GONE, large.findViewById<View>(R.id.widget_list).visibility)
        assertEquals("暂无班级通知", large.findViewById<TextView>(R.id.widget_course).text.toString())
        assertEquals(View.VISIBLE, large.findViewById<View>(R.id.widget_summary_open).visibility)
    }

    @Test @Config(sdk = [34]) fun launcherSelectsWideAndTallVariantsFromTheSameRemoteViews() {
        val opts = options(125, 56).apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(125f,56f),SizeF(300f,245f)))
        }
        val model = widgetContent(WidgetKind.NOTICES,true,null,feed,2)
        val variants = WidgetViews.forHost(app,WidgetKind.NOTICES,model,opts)
        val host = host(variants,125,56)
        assertEquals(View.GONE, host.findViewById<View>(R.id.widget_list).visibility)
        host.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(245, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, 300, 245)
        assertEquals(View.VISIBLE, host.findViewById<View>(R.id.widget_list).visibility)
    }

    @Test fun providerUpdatesBothBoundInstancesAndClearsPreviousAccount() = runBlocking {
        val manager = AppWidgetManager.getInstance(app)
        val registry = shadowOf(manager)
        val ids = registry.createWidgets(TasksWidget::class.java, R.layout.widget_initial, 2)
        manager.updateAppWidgetOptions(ids[0], options(125, 56))
        manager.updateAppWidgetOptions(ids[1], options(300, 245))
        val sessionStore = SessionStore(app)
        val testSid = "3260799999"
        sessionStore.save(Session(sid = testSid, name = "测试", token = "", debug = true))
        val foreignFeed = feed.copy(tasks = feed.tasks?.copy(rows = listOf(WidgetLine("正式账号的任务"))))
        WidgetFeedStore.save(app, testSid, foreignFeed)
        val debugItems = (1..5).joinToString(",") { """{"id":"t$it","title":"报告 $it","order":$it}""" }
        app.getSharedPreferences("tasks_debug_" + testSid, 0).edit()
            .putString("local", """{"updatedAt":5,"items":[$debugItems]}""").commit()
        assertNull(WidgetFeedStore.read(app, "99990001").tasks)
        DesktopWidgets.update(app, TasksWidget::class.java, false)
        fun root(index: Int) = registry.getViewFor(ids[index])
        assertEquals("报告 1", root(0).findViewById<TextView>(R.id.widget_course).text.toString())
        assertEquals(View.GONE, root(0).findViewById<View>(R.id.widget_list).visibility)
        assertEquals(View.VISIBLE, root(1).findViewById<View>(R.id.widget_list).visibility)
        val style = WidgetLayout.fit(300f, 245f, app.resources.displayMetrics).rows
        val collection = WidgetCollectionFactory(app, ids[1], WidgetKind.TASKS, style)
        collection.onDataSetChanged()
        assertEquals(5, collection.getCount())
        assertNotNull(collection.getViewAt(4))
        assertNotEquals(collection.getItemId(3), collection.getItemId(4))
        sessionStore.clear()
        DesktopWidgets.update(app, TasksWidget::class.java, false)
        assertEquals("登录后查看", root(1).findViewById<TextView>(R.id.widget_course).text.toString())
        assertEquals(View.GONE, root(1).findViewById<View>(R.id.widget_list).visibility)
        collection.onDataSetChanged()
        assertEquals(0, collection.getCount())
    }
}
