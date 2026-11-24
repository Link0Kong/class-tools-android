package com.furina.classtools.data.tasks

import android.content.Context
import androidx.work.*
import com.furina.classtools.data.reminder.AndroidReminderNotifier
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

internal fun scheduleTaskReminders(context: Context, sid: String, before: JsonObject, after: JsonObject) {
    fun times(snapshot: JsonObject) = snapshot.objects("items").associate { it.text("id") to taskReminderTime(it) }
    val old = times(before)
    val next = times(after)
    val now = System.currentTimeMillis()
    for (id in old.keys + next.keys) {
        val time = next[id] ?: 0L
        if (time == (old[id] ?: 0L)) continue
        val work = WorkManager.getInstance(context)
        val key = "personal-task:$sid:$id"
        if (time <= now) {
            if ((old[id] ?: 0L) > 0) work.cancelUniqueWork(key)
            continue
        }
        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInputData(workDataOf("owner" to sid, "id" to id, "time" to time))
            .setInitialDelay(time - now, TimeUnit.MILLISECONDS)
            .build()
        work.enqueueUniqueWork(key, ExistingWorkPolicy.REPLACE, request)
    }
}

class TaskReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sid = inputData.getString("owner") ?: return Result.success()
        val id = inputData.getString("id") ?: return Result.success()
        val time = inputData.getLong("time", 0L)
        val session = (SessionStore(applicationContext).state.first() as? SessionState.SignedIn)?.session
        if (session?.sid != sid || session.debug || !session.isLikelyValid()) return Result.success()
        val local = TaskPreferences(applicationContext.getSharedPreferences("tasks_" + sid, Context.MODE_PRIVATE)).read("local")
        val snapshot = local?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val task = snapshot?.objects("items")?.find { it.text("id") == id } ?: return Result.success()
        if (time <= 0 || taskReminderTime(task) != time) return Result.success()
        val notifier = AndroidReminderNotifier(applicationContext)
        notifier.ensureChannels()
        notifier.notify(ReminderEvent(ReminderKind.PERSONAL_TASK, "task:$id", time, "任务提醒", task.text("title")))
        return Result.success()
    }
}
