package com.furina.classtools.data.tasks

import android.content.Context
import androidx.work.*
import com.furina.classtools.data.reminder.AndroidReminderNotifier
import com.furina.classtools.data.reminder.ReminderCatchUp
import com.furina.classtools.data.reminder.ReminderEvent
import com.furina.classtools.data.reminder.ReminderKind
import com.furina.classtools.data.session.SessionState
import com.furina.classtools.data.session.SessionStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
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
    for (id in old.keys + next.keys) {
        val time = next[id] ?: 0L
        if (time == (old[id] ?: 0L) || (time > 0 && prefs.getLong("notified:$id", 0L) == time)) continue
        val work = WorkManager.getInstance(context)
        val key = "personal-task:$sid:$id"
        val delay = taskReminderDelay(time, now)
        if (delay == null) {
            if ((old[id] ?: 0L) > 0) work.cancelUniqueWork(key)
            continue
        }
        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInputData(workDataOf("owner" to sid, "id" to id, "time" to time))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        work.enqueueUniqueWork(key, if (before == null) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE, request)
    }
}

class TaskReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sid = inputData.getString("owner") ?: return Result.success()
        val id = inputData.getString("id") ?: return Result.success()
        val time = inputData.getLong("time", 0L)
        val session = (SessionStore(applicationContext).state.first() as? SessionState.SignedIn)?.session
        if (session?.sid != sid || session.debug || !session.isLikelyValid()) return Result.success()
        val prefs = applicationContext.getSharedPreferences("tasks_" + sid, Context.MODE_PRIVATE)
        if (prefs.getLong("notified:$id", 0L) == time) return Result.success()
        val local = TaskPreferences(prefs).read("local")
        val snapshot = local?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val task = snapshot?.objects("items")?.find { it.text("id") == id } ?: return Result.success()
        if (time <= 0 || taskReminderTime(task) != time) return Result.success()
        val notifier = AndroidReminderNotifier(applicationContext)
        notifier.ensureChannels()
        val posted = notifier.notify(ReminderEvent(ReminderKind.PERSONAL_TASK, "task:$id", time, "任务提醒", task.text("title")))
        if (posted) prefs.edit().putLong("notified:$id", time).apply()
        return if (posted || runAttemptCount >= 3) Result.success() else Result.retry()
    }
}
