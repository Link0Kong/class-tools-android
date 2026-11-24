package com.furina.classtools.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.furina.classtools.data.timetable.SessionState
import com.furina.classtools.data.timetable.TtCalendar
import com.furina.classtools.data.timetable.deviceTimetable
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Keep the original component name so existing timetable widgets survive an update.
class TimetableWidget : DesktopWidgetProvider() {
    companion object {
        fun pin(context: Context) = DesktopWidgets.pin(context, WidgetKind.TIMETABLE)
    }
}
class WorkbenchWidget : DesktopWidgetProvider()
class NoticesWidget : DesktopWidgetProvider()
class TasksWidget : DesktopWidgetProvider()

abstract class DesktopWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in DesktopWidgets.refreshActions) {
            super.onReceive(context, intent)
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DesktopWidgets.update(context, this@DesktopWidgetProvider.javaClass, intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = WidgetPalette.prefs(context).edit()
        appWidgetIds.forEach { prefs.remove(it.toString()) }
        prefs.apply()
    }

    override fun onDisabled(context: Context) = DesktopWidgets.cancelAlarm(context, javaClass)
}

internal object DesktopWidgets {
    val components = mapOf(WidgetKind.TIMETABLE to TimetableWidget::class.java, WidgetKind.WORKBENCH to WorkbenchWidget::class.java,
        WidgetKind.NOTICES to NoticesWidget::class.java, WidgetKind.TASKS to TasksWidget::class.java)
    private const val REFRESH = "com.furina.classtools.widget.REFRESH"
    val refreshActions = setOf(REFRESH, AppWidgetManager.ACTION_APPWIDGET_UPDATE,
        AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED, Intent.ACTION_DATE_CHANGED,
        Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
    private val lock = Mutex()

    private fun ids(context: Context, provider: Class<out DesktopWidgetProvider>) =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider))

    fun feedKinds(context: Context) = components.filter { (kind, provider) ->
        kind != WidgetKind.TIMETABLE && ids(context, provider).isNotEmpty()
    }.keys

    fun refresh(context: Context) {
        components.values.filter { ids(context, it).isNotEmpty() }.forEach {
            context.sendBroadcast(Intent(context, it).setAction(REFRESH))
        }
    }

    @androidx.annotation.RequiresApi(35)
    fun publishPickerPreviews(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val category = android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
        val prefs = context.getSharedPreferences("widget_picker_previews", Context.MODE_PRIVATE)
        val layouts = mapOf(
            WidgetKind.TIMETABLE to com.furina.classtools.R.layout.widget_preview_timetable,
            WidgetKind.WORKBENCH to com.furina.classtools.R.layout.widget_preview_workbench,
            WidgetKind.NOTICES to com.furina.classtools.R.layout.widget_preview_notices,
            WidgetKind.TASKS to com.furina.classtools.R.layout.widget_preview_tasks,
        )
        layouts.forEach { (kind, layout) ->
            if (prefs.getInt(kind.name, 0) != com.furina.classtools.BuildConfig.VERSION_CODE) {
                val accepted = runCatching { manager.setWidgetPreview(ComponentName(context, components.getValue(kind)),
                    category, android.widget.RemoteViews(context.packageName, layout)) }.getOrDefault(false)
                if (accepted) prefs.edit().putInt(kind.name, com.furina.classtools.BuildConfig.VERSION_CODE).apply()
            }
        }
    }

    fun pin(context: Context, kind: WidgetKind): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return manager.isRequestPinAppWidgetSupported &&
            manager.requestPinAppWidget(ComponentName(context, components.getValue(kind)), null, null)
    }

    private fun alarmIntent(context: Context, provider: Class<out DesktopWidgetProvider>) =
        PendingIntent.getBroadcast(context, 0, Intent(context, provider).setAction(REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun cancelAlarm(context: Context, provider: Class<out DesktopWidgetProvider>) =
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, provider))

    suspend fun update(context: Context, provider: Class<out DesktopWidgetProvider>, periodic: Boolean) = lock.withLock {
        val manager = AppWidgetManager.getInstance(context)
        val ids = ids(context, provider)
        if (ids.isEmpty()) return@withLock
        val kind = components.entries.first { it.value == provider }.key
        val session = WidgetFeedStore.session(context)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val timetable = kind == WidgetKind.TIMETABLE || kind == WidgetKind.WORKBENCH
        val plan = if (timetable && session != null) deviceTimetable(context, session.sid)?.let {
            TtCalendar.plan(it, now, zone)
        } else null
        val feed = if (kind != WidgetKind.TIMETABLE && session != null) WidgetFeedStore.read(context, session.sid) else WidgetFeed()
        val model = widgetContent(kind, session != null, plan, feed, now)
        for (id in ids) manager.updateAppWidget(id, WidgetViews.forHost(context, kind, model, manager.getAppWidgetOptions(id), WidgetPalette.get(context, id)))
        if (periodic && session != null && !session.debug && kind != WidgetKind.TIMETABLE) WidgetFeedStore.request(context, session.sid)
        val current = plan?.today?.firstOrNull { it.state == SessionState.ONGOING }
        val boundary = listOfNotNull(current?.endAt, plan?.next?.startAt, session?.expiresAt,
            session?.issuedAt?.takeIf { it > 0 }?.plus(29L * 86_400_000),
            if (timetable) LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() else null)
            .filter { it > now }.minOrNull()
        if (session != null && boundary != null) context.getSystemService(AlarmManager::class.java)
            .set(AlarmManager.RTC, boundary + 1000, alarmIntent(context, provider))
        else cancelAlarm(context, provider)
    }
}
