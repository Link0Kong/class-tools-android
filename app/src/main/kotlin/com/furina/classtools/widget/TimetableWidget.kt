package com.furina.classtools.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.furina.classtools.R
import com.furina.classtools.data.timetable.SessionState
import com.furina.classtools.data.timetable.TtCalendar
import com.furina.classtools.data.timetable.TtPlan
import com.furina.classtools.data.timetable.deviceTimetable
import com.furina.classtools.data.session.Session
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
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
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                DesktopWidgets.update(context, this@DesktopWidgetProvider.javaClass,
                    periodic = intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE,
                    resizedId = id.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID &&
                        intent.action == AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED })
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

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val prefs = WidgetPalette.prefs(context)
        val colors = oldWidgetIds.map { prefs.getString(it.toString(), null) }
        prefs.edit().apply {
            oldWidgetIds.forEach { remove(it.toString()) }
            newWidgetIds.zip(colors).forEach { (id, color) -> putString(id.toString(), color) }
        }.apply()
        DesktopWidgets.refresh(context)
    }
}

internal data class WidgetSnapshot(val session: Session?, val plan: TtPlan?, val content: WidgetContent) {
    companion object {
        suspend fun read(context: Context, kind: WidgetKind, now: Long): WidgetSnapshot {
            val session = WidgetFeedStore.session(context)
            val zone = ZoneId.systemDefault()
            val timetable = kind == WidgetKind.TIMETABLE || kind == WidgetKind.WORKBENCH
            val snapshot = if (timetable && session != null) deviceTimetable(context, session.sid) else null
            val plan = snapshot?.let { TtCalendar.plan(it, now, zone) }
            val tomorrow = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1)
            val tomorrowCourses = if (kind == WidgetKind.TIMETABLE && snapshot != null)
                TtCalendar.sessionsOn(snapshot.c, TtCalendar.parseTermStart(snapshot.termStart), tomorrow, now, zone) else emptyList()
            val feed = if (kind != WidgetKind.TIMETABLE && session != null)
                WidgetFeedStore.read(context, session.sid, session.debug) else WidgetFeed()
            return WidgetSnapshot(session, plan, widgetContent(kind, session != null, plan, feed, now, tomorrowCourses))
        }
    }
}

internal object DesktopWidgets {
    val components = mapOf(WidgetKind.TIMETABLE to TimetableWidget::class.java, WidgetKind.WORKBENCH to WorkbenchWidget::class.java,
        WidgetKind.NOTICES to NoticesWidget::class.java, WidgetKind.TASKS to TasksWidget::class.java)
    private const val REFRESH = "com.furina.classtools.widget.REFRESH"
    val refreshActions = setOf(REFRESH, AppWidgetManager.ACTION_APPWIDGET_UPDATE,
        AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED, Intent.ACTION_DATE_CHANGED,
        Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_CONFIGURATION_CHANGED, Intent.ACTION_WALLPAPER_CHANGED)
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
        val settings = com.furina.classtools.data.settings.AppSettingsStore(context)
        val systemDark = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val dark = settings.startupAppearanceMode().resolveDark(systemDark)
        val scheme = com.furina.classtools.ui.theme.appColorScheme(context, dark, settings.startupDynamicColor())
        val signature = listOf(com.furina.classtools.BuildConfig.VERSION_CODE, scheme.primary, scheme.surfaceContainerLow, scheme.tertiary).hashCode()
        components.keys.forEach { kind ->
            if (prefs.getInt(kind.name, 0) != signature) {
                val height = when (kind) {
                    WidgetKind.TIMETABLE -> 240
                    WidgetKind.WORKBENCH -> 200
                    else -> 220
                }
                val accepted = runCatching { manager.setWidgetPreview(ComponentName(context, components.getValue(kind)),
                    category, WidgetPreviews.render(context, kind, 320, height)) }.getOrDefault(false)
                if (accepted) prefs.edit().putInt(kind.name, signature).apply()
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

    @Suppress("DEPRECATION")
    suspend fun update(context: Context, provider: Class<out DesktopWidgetProvider>, periodic: Boolean, resizedId: Int? = null) = lock.withLock {
        val manager = AppWidgetManager.getInstance(context)
        val ids = ids(context, provider).filter { resizedId == null || it == resizedId }
        if (ids.isEmpty()) return@withLock
        val kind = components.entries.first { it.value == provider }.key
        val monet = com.furina.classtools.data.settings.AppSettingsStore(context).dynamicColor.first()
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val timetable = kind == WidgetKind.TIMETABLE || kind == WidgetKind.WORKBENCH
        val (session, plan, model) = WidgetSnapshot.read(context, kind, now)
        // New Android versions can implement invalidation by re-publishing the last RemoteViews.
        // Send the new shell after that operation so the header and list cannot diverge.
        if (resizedId == null && Build.VERSION.SDK_INT >= 35)
            manager.notifyAppWidgetViewDataChanged(ids.toIntArray(), R.id.widget_list)
        for (id in ids) manager.updateAppWidget(id, WidgetViews.forHost(context, kind, model,
            manager.getAppWidgetOptions(id), WidgetPalette.get(context, id), monet, session?.widgetOwner, widgetId = id))
        if (resizedId == null && Build.VERSION.SDK_INT < 35)
            manager.notifyAppWidgetViewDataChanged(ids.toIntArray(), R.id.widget_list)
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
