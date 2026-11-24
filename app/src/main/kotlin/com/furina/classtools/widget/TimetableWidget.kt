package com.furina.classtools.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.furina.classtools.ClassToolsApp
import com.furina.classtools.MainActivity
import com.furina.classtools.R
import com.furina.classtools.data.reminder.ReminderIntentFactory
import com.furina.classtools.data.session.SessionState
import com.furina.classtools.data.timetable.deviceTimetable
import com.furina.classtools.data.timetable.SessionState as CourseState
import com.furina.classtools.data.timetable.TtCalendar
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TimetableWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in refreshActions) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    lock.withLock { render(context) }
                } finally {
                    pending.finish()
                }
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
    }

    companion object {
        private const val REFRESH = "com.furina.classtools.widget.REFRESH"
        private val refreshActions = setOf(REFRESH, AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED, Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
        private val lock = Mutex()

        private fun component(context: Context) = ComponentName(context, TimetableWidget::class.java)
        private fun intent(context: Context) = Intent(context, TimetableWidget::class.java).setAction(REFRESH)
        private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(context, 0, intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        fun refresh(context: Context) {
            if (AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty()) {
                context.sendBroadcast(intent(context))
            }
        }

        fun pin(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            return manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(component(context), null, null)
        }

        private suspend fun render(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(component(context))
            if (ids.isEmpty()) return
            val sessionStore = (context.applicationContext as ClassToolsApp).container.sessionStore
            val session = (sessionStore.state.first() as? SessionState.SignedIn)?.session
                ?.takeIf { it.isLikelyValid() }
            val snapshot = session?.let { deviceTimetable(context, it.sid) }
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val plan = snapshot?.let { TtCalendar.plan(it, now, zone) }
            val current = plan?.today?.firstOrNull { it.state == CourseState.ONGOING }
            val course = current ?: plan?.next
            val heading = when {
                current != null -> "正在上课"
                course != null -> "${course.dayText} · 下一节"
                else -> "今日课程"
            }
            val title = course?.name ?: when {
                session == null -> "登录后查看课表"
                plan?.hasData != true -> "打开课表同步课程"
                else -> "近期暂无课程"
            }
            val details = course?.let { listOf(it.span, "${it.startText}–${it.endText}", it.room)
                .filter(String::isNotBlank).joinToString(" · ") }.orEmpty()
            val views = RemoteViews(context.packageName, R.layout.timetable_widget)
            views.setTextViewText(R.id.widget_heading, heading)
            views.setTextViewText(R.id.widget_course, title)
            views.setTextViewText(R.id.widget_details, details)
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(ReminderIntentFactory.EXTRA_TOOL_ID, "tt")
            }
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 4302,
                open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(ids, views)
            val alarm = context.getSystemService(AlarmManager::class.java)
            val boundary = listOfNotNull(current?.endAt, plan?.next?.startAt, session?.expiresAt,
                LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                .filter { it > now }.minOrNull()
            if (session != null && boundary != null) alarm.set(AlarmManager.RTC, boundary + 1000, alarmIntent(context))
            else alarm.cancel(alarmIntent(context))
        }
    }
}
