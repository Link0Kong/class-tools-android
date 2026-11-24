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
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import com.furina.classtools.R
import com.furina.classtools.data.reminder.ReminderIntentFactory
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.session.SessionStore
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
    private val feed = WidgetFeed(tasks = WidgetList(5, (1..5).map { WidgetLine("报告 $it", "周五截止") }, 1),
        latestNotices = WidgetList(5, (1..5).map { WidgetLine("通知 $it", "周三报名") }, 1))

    private fun options(w: Int, h: Int) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h)
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

    @Test fun independentSizesChangeContentAndSignedOutRemovesPrivateRows() {
        val kind = WidgetKind.TASKS
        val content = widgetContent(kind, true, null, feed, 2)
        fun layout(w: Int, h: Int) = host(WidgetViews.forHost(app, kind, content, options(w,h)),w,h)
        val small = layout(125, 56)
        assertEquals("报告 1", small.findViewById<TextView>(R.id.widget_course).text.toString())
        assertEquals(View.GONE, small.findViewById<View>(R.id.widget_details).visibility)
        val banner = layout(300, 56)
        assertEquals("周五截止", banner.findViewById<TextView>(R.id.widget_details).text.toString())
        val tall = layout(130, 170)
        assertEquals(2, tall.findViewById<LinearLayout>(R.id.widget_list).childCount)
        val large = layout(300, 245)
        assertEquals(4, large.findViewById<LinearLayout>(R.id.widget_list).childCount)
        assertEquals(View.VISIBLE, large.findViewById<View>(R.id.widget_row_detail).visibility)
        assertEquals(View.GONE, tall.findViewById<View>(R.id.widget_row_detail).visibility)
        large.updateAppWidget(parcelled(WidgetViews.forHost(app, kind, widgetContent(kind,false,null,feed,2), options(300,245))))
        assertEquals(0, large.findViewById<LinearLayout>(R.id.widget_list).childCount)
        assertEquals("登录后查看", large.findViewById<TextView>(R.id.widget_course).text.toString())
    }

    @Test @Config(sdk = [34]) fun launcherSelectsWideAndTallVariantsFromTheSameRemoteViews() {
        val opts = options(125, 56).apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(125f,56f),SizeF(300f,245f)))
        }
        val model = widgetContent(WidgetKind.NOTICES,true,null,feed,2)
        val variants = WidgetViews.forHost(app,WidgetKind.NOTICES,model,opts)
        val host = host(variants,125,56)
        assertEquals(0, host.findViewById<LinearLayout>(R.id.widget_list).childCount)
        host.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(245, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, 300, 245)
        assertEquals(4, host.findViewById<LinearLayout>(R.id.widget_list).childCount)
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
        WidgetFeedStore.save(app, testSid, feed)
        assertNull(WidgetFeedStore.read(app, "99990001").tasks)
        DesktopWidgets.update(app, TasksWidget::class.java, false)
        fun root(index: Int) = registry.getViewFor(ids[index])
        assertEquals("报告 1", root(0).findViewById<TextView>(R.id.widget_course).text.toString())
        assertEquals(0, root(0).findViewById<LinearLayout>(R.id.widget_list).childCount)
        assertEquals(4, root(1).findViewById<LinearLayout>(R.id.widget_list).childCount)
        sessionStore.clear()
        DesktopWidgets.update(app, TasksWidget::class.java, false)
        assertEquals("登录后查看", root(1).findViewById<TextView>(R.id.widget_course).text.toString())
        assertEquals(0, root(1).findViewById<LinearLayout>(R.id.widget_list).childCount)
    }
}
