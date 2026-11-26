package com.furina.classtools.data.calendar

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import com.furina.classtools.data.tasks.taskDeadline
import com.furina.classtools.data.tasks.taskReminderTime
import com.furina.classtools.data.tasks.text
import kotlinx.serialization.json.JsonObject

/** A calendar copy follows the explicitly selected reminder, including an at-time alarm. */
internal object ReminderCalendar {
    fun event(key: String, title: String, at: Long, description: String = "", location: String = "") =
        CalendarEvent(title = title, beginMillis = at, endMillis = at + 60_000L,
            description = description, location = location, reminderMinutes = 0, sourceKey = key)

    fun taskKey(owner: String, id: String) = "reminder:$owner:task:$id"

    fun task(owner: String, task: JsonObject, now: Long = System.currentTimeMillis()): CalendarEvent? {
        val at = taskReminderTime(task).takeIf { it > now } ?: return null
        val description = listOfNotNull(taskDeadline(task)?.let { "截止 ${NoticeCalendarMapper.formatAt(it)}" },
            task.text("notes").trim().takeIf { it.isNotEmpty() }?.take(300)).joinToString("\n")
        return event(taskKey(owner, task.text("id")), task.text("title").ifBlank { "个人任务" }, at, description)
    }

    private fun row(context: Context, key: String): Long? =
        context.contentResolver.query(Events.CONTENT_URI, arrayOf(Events._ID),
            "${Events.CUSTOM_APP_PACKAGE}=? AND ${Events.CUSTOM_APP_URI}=? AND ${Events.DELETED}=0",
            arrayOf(context.packageName, key), null)?.use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun permitted(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** If the user removed the copy in Calendar, background edits leave that choice intact. */
    @Synchronized
    fun save(context: Context, event: CalendarEvent, calendarId: Long?): Long? {
        val id = row(context, event.sourceKey)
        if (id == null && calendarId == null) return null
        val values = ContentValues().apply {
            put(Events.TITLE, event.title)
            put(Events.DESCRIPTION, event.description)
            put(Events.EVENT_LOCATION, event.location)
            put(Events.DTSTART, event.beginMillis)
            put(Events.DTEND, event.endMillis)
            put(Events.EVENT_TIMEZONE, event.timeZone)
            put(Events.HAS_ALARM, 1)
            put(Events.CUSTOM_APP_PACKAGE, context.packageName)
            put(Events.CUSTOM_APP_URI, event.sourceKey)
        }
        val ops = arrayListOf<ContentProviderOperation>()
        val reminder = ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
            .withValue(Reminders.MINUTES, 0).withValue(Reminders.METHOD, Reminders.METHOD_ALERT)
        if (id == null) {
            values.put(Events.CALENDAR_ID, calendarId)
            ops += ContentProviderOperation.newInsert(Events.CONTENT_URI).withValues(values).build()
            reminder.withValueBackReference(Reminders.EVENT_ID, 0)
        } else {
            ops += ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Events.CONTENT_URI, id)).withValues(values).build()
            ops += ContentProviderOperation.newDelete(Reminders.CONTENT_URI)
                .withSelection("${Reminders.EVENT_ID}=?", arrayOf(id.toString())).build()
            reminder.withValue(Reminders.EVENT_ID, id)
        }
        ops += reminder.build()
        val results = context.contentResolver.applyBatch(CalendarContract.AUTHORITY, ops)
        return id ?: ContentUris.parseId(checkNotNull(results.first().uri))
    }

    @Synchronized
    fun remove(context: Context, key: String) {
        if (permitted(context)) row(context, key)?.let {
            context.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, it), null, null)
        }
    }

    fun syncTask(context: Context, owner: String, id: String, current: JsonObject?) {
        if (!permitted(context)) return
        val event = current?.let { task(owner, it) }
        if (event != null) save(context, event, calendarId = null)
        else remove(context, taskKey(owner, id))
    }
}
