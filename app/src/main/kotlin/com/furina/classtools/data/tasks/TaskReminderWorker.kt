package com.furina.classtools.data.tasks

import android.content.Context
import androidx.work.*
import com.furina.classtools.data.reminder.AndroidReminderNotifier
import com.furina.classtools.data.reminder.ReminderCatchUp
import com.furina.classtools.data.reminder.ReminderEvent
import com.furina.classtools.data.reminder.ReminderKind
import com.furina.classtools.data.reminder.UserReminderAlarms
import com.furina.classtools.data.reminder.reminderSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.*

internal fun taskReminderTime(task: JsonObject): Long {
    if (task.flag("done")) return 0
    val whenDue = maxOf(task.number("remindAt"), task.number("snoozeUntil"))
    return whenDue.takeIf { it > task.number("lastNotifyAt") } ?: 0
}

internal fun taskReminderDelay(time: Long, now: Long): Long? =
    time.takeIf { it > 0 && it >= now - ReminderCatchUp.MAX_LOOKBACK_MS }?.let { (it - now).coerceAtLeast(0L) }

internal fun scheduleTaskReminders(context: Context, sid: String, before: JsonObject?, after: JsonObject) {
    fun times(snapshot: JsonObject) = snapshot.objects("items").associate { it.text("id") to taskReminderTime(it) }
    // On a cold start WorkManager may have no request for an unchanged task imported on another client.
    val old = before?.let(::times).orEmpty()
    val next = times(after)
    val now = System.currentTimeMillis()
    val prefs = context.getSharedPreferences("tasks_" + sid, Context.MODE_PRIVATE)
    val work = WorkManager.getInstance(context)
    for (id in old.keys + next.keys) {
        val time = next[id] ?: 0L
        if (time == (old[id] ?: 0L) || (time > 0 && prefs.getLong("notified:$id", 0L) == time)) continue
        val key = "personal-task:$sid:$id"
        val delay = taskReminderDelay(time, now)
        if (delay == null) {
            UserReminderAlarms.cancel(context, UserReminderAlarms.TASK, sid, id, "due")
            if ((old[id] ?: 0L) > 0) work.cancelUniqueWork(key)
            continue
        }
        if (delay > 0) UserReminderAlarms.set(context, UserReminderAlarms.TASK, sid, id, "due", time)
        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInputData(workDataOf("owner" to sid, "id" to id, "time" to time))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        work.enqueueUniqueWork(key, if (before == null) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE, request)
    }
}

private fun localTasks(context: Context, sid: String): JsonObject? {
    val prefs = context.getSharedPreferences("tasks_" + sid, Context.MODE_PRIVATE)
    return TaskPreferences(prefs).read("local")?.let {
        runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull()
    }
}

internal fun restoreTaskReminders(context: Context, sid: String) {
    localTasks(context, sid)?.let { scheduleTaskReminders(context, sid, null, it) }
}

internal fun updateTaskCalendarCopies(context: Context, owner: String, before: JsonObject, after: JsonObject) {
    fun byId(snapshot: JsonObject) = snapshot.objects("items").associateBy { it.text("id") }
    val previous = byId(before)
    val next = byId(after)
    synchronized(taskCalendarLock) {
        val current = localTasks(context, owner)?.let(::byId) ?: return
        for (id in previous.keys + next.keys) {
            if (previous[id] != next[id]) runCatching {
                com.furina.classtools.data.calendar.ReminderCalendar.syncTask(context, owner, id, current[id])
            }
        }
    }
}

private val taskCalendarLock = Any()
private val taskDelivery = Mutex()

internal suspend fun deliverTaskReminder(context: Context, sid: String, id: String, time: Long): Boolean =
    taskDelivery.withLock {
        val session = reminderSession(context)
        if (session?.sid != sid || session.debug) return true
        val task = localTasks(context, sid)?.objects("items")?.find { it.text("id") == id } ?: return true
        if (time <= 0 || taskReminderTime(task) != time) return true
        val now = System.currentTimeMillis()
        if (time > now) {
            UserReminderAlarms.set(context, UserReminderAlarms.TASK, sid, id, "due", time)
            return true
        }
        if (taskReminderDelay(time, now) == null) return true
        val prefs = context.getSharedPreferences("tasks_" + sid, Context.MODE_PRIVATE)
        if (prefs.getLong("notified:$id", 0L) == time) return true
        val notifier = AndroidReminderNotifier(context)
        notifier.ensureChannels()
        val posted = notifier.notify(ReminderEvent(ReminderKind.PERSONAL_TASK, "task:$id", time, "任务提醒", task.text("title")))
        if (posted) prefs.edit().putLong("notified:$id", time).commit()
        posted
    }

class TaskReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sid = inputData.getString("owner") ?: return Result.success()
        val id = inputData.getString("id") ?: return Result.success()
        val posted = deliverTaskReminder(applicationContext, sid, id, inputData.getLong("time", 0L))
        return if (posted || runAttemptCount >= 3) Result.success() else Result.retry()
    }
}
