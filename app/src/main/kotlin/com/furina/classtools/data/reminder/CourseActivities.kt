package com.furina.classtools.data.reminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.furina.classtools.data.timetable.CourseFocus
import com.furina.classtools.data.timetable.TimetableLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
internal data class CourseActivity(
    val owner: String,
    val key: String,
    val generation: String,
    val info: CourseReminderInfo,
) {
    val deadline: Long get() = info.startAt
    val notificationId: Int get() = key.hashCode()
    val tag: String get() = "preclass:$generation"
}

/** Only this object owns the course notification and its expiration alarm. Journal before publishing
 * so a fresh process can withdraw it using local data even if the timetable cannot be read. */
internal object CourseActivities {
    private val codec = Json { ignoreUnknownKeys = true }
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()
    @Volatile var accountRevision: Long = 0
        private set
    private fun prefs(context: Context) = context.getSharedPreferences("ct_preclass", Context.MODE_PRIVATE)

    private fun active(context: Context): CourseActivity? = prefs(context).getString("active", null)?.let {
        runCatching { codec.decodeFromString<CourseActivity>(it) }.getOrNull()
    }

    @Synchronized
    fun current(context: Context, owner: String): CourseActivity? {
        expire(context)
        return active(context)?.takeIf { it.owner == owner }
    }

    private fun historyKey(owner: String, key: String) = "end." + TimetableLink.sha1Hex(owner + "\u0000" + key)

    @Synchronized
    fun bind(context: Context, owner: String?) {
        val store = prefs(context)
        if (store.getString("owner", null) != owner.orEmpty()) {
            check(store.edit().putString("owner", owner.orEmpty()).commit())
            accountRevision++
        }
        active(context)?.takeIf { it.owner != owner }?.let { finish(context, it) }
    }

    @Synchronized
    fun expire(context: Context, now: Long = System.currentTimeMillis()) {
        active(context)?.takeIf { now >= it.deadline }?.let { finish(context, it) }
    }

    @Synchronized
    fun reconcile(context: Context, owner: String, dueKey: String?) {
        expire(context)
        active(context)?.takeIf { it.owner == owner && it.key != dueKey }?.let { finish(context, it) }
    }

    @Synchronized
    fun show(context: Context, owner: String, event: ReminderEvent, publish: (CourseActivity) -> Boolean): Boolean {
        val info = event.course ?: return false
        val now = System.currentTimeMillis()
        expire(context, now)
        val store = prefs(context)
        if (owner.isBlank() || store.getString("owner", null) != owner ||
            info.startAt <= now || now < event.triggerAtMillis) return false
        if (store.contains(historyKey(owner, event.key))) return false
        val previous = active(context)
        val activity = if (previous?.owner == owner && previous.key == event.key) previous.copy(info = info)
        else {
            previous?.let { finish(context, it) }
            CourseActivity(owner, event.key, UUID.randomUUID().toString(), info)
        }
        if (activity != previous) {
            if (!store.edit().putString("active", codec.encodeToString(activity)).commit()) return false
            revision.value++
        }
        UserReminderAlarms.set(context, UserReminderAlarms.COURSE, owner, activity.generation, "end", info.startAt)
        if (System.currentTimeMillis() >= info.startAt) {
            finish(context, activity)
            return false
        }
        return publish(activity)
    }

    @Synchronized
    fun onBoundary(context: Context, owner: String, generation: String) {
        val activity = active(context)?.takeIf { it.owner == owner && it.generation == generation } ?: return
        if (System.currentTimeMillis() >= activity.deadline) finish(context, activity)
        else UserReminderAlarms.set(context, UserReminderAlarms.COURSE, owner, generation, "end", activity.deadline)
    }

    @Synchronized
    fun hide(context: Context, owner: String, generation: String) {
        val activity = active(context)?.takeIf { it.owner == owner && it.generation == generation } ?: return
        finish(context, activity)
        if (context.applicationContext is com.furina.classtools.ClassToolsApp) {
            ReminderRuntime.scheduleCourseAt(context, System.currentTimeMillis())
        }
    }

    fun dismissIntent(context: Context, activity: CourseActivity): PendingIntent {
        val data = Uri.Builder().scheme("ct-alarm").authority("local").appendPath(UserReminderAlarms.COURSE)
            .appendPath(activity.owner).appendPath(activity.generation).appendPath("hide").build()
        return PendingIntent.getBroadcast(context, 0, Intent(context, UserReminderAlarmReceiver::class.java).setData(data),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    @Synchronized
    fun pauseEarly(context: Context, owner: String, key: String) {
        val activity = active(context)?.takeIf { it.owner == owner && it.key == key } ?: return
        withdraw(context, activity)
    }

    @Synchronized
    fun ended(context: Context, owner: String, focus: CourseFocus, deadline: Long): Boolean {
        val key = "class:$deadline:" + TimetableLink.sha1Hex(focus.encode())
        return prefs(context).contains(historyKey(owner, key))
    }

    private fun finish(context: Context, activity: CourseActivity) {
        val store = prefs(context)
        val edit = store.edit().putLong(historyKey(activity.owner, activity.key), activity.deadline)
        // Keep recent closed instances across clock corrections and remove older class records.
        val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        store.all.forEach { (key, value) -> if (key.startsWith("end.") && value is Long && value < cutoff) edit.remove(key) }
        // Keep the active journal until both side effects succeed so interrupted cleanup is retryable.
        check(edit.commit()) { "Could not persist course closure" }
        withdraw(context, activity)
    }

    private fun withdraw(context: Context, activity: CourseActivity) {
        val store = prefs(context)
        context.getSystemService(NotificationManager::class.java).cancel(activity.tag, activity.notificationId)
        UserReminderAlarms.cancel(context, UserReminderAlarms.COURSE, activity.owner, activity.generation, "end")
        check(store.edit().remove("active").commit()) { "Could not clear course journal" }
        revision.value++
    }

    @Synchronized
    fun restore(context: Context, owner: String?, expectedAccountRevision: Long): Boolean {
        // A login switch may have finished while the receiver/worker was reading SessionStore.
        if (accountRevision != expectedAccountRevision &&
            prefs(context).getString("owner", null) != owner.orEmpty()) return false
        bind(context, owner)
        expire(context)
        val activity = active(context)
        if (activity != null) {
            if (prefs(context).contains(historyKey(activity.owner, activity.key))) finish(context, activity)
            else UserReminderAlarms.set(context, UserReminderAlarms.COURSE, activity.owner, activity.generation, "end", activity.deadline)
        }
        val store = prefs(context)
        if (store.getBoolean("legacyCleaned", false)) return true
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { card ->
            card.tag == null && card.notification.channelId == ReminderKind.COURSE_SOON.channelId &&
                (card.id == "class:${card.notification.`when`}".hashCode() ||
                    card.id == ReminderGrouping.summaryId(ReminderKind.COURSE_SOON))
        }.forEach { manager.cancel(it.id) }
        store.edit().putBoolean("legacyCleaned", true).apply()
        return true
    }
}
