package com.furina.classtools.data.notify

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.furina.classtools.R
import com.furina.classtools.data.notices.NoticesData
import com.furina.classtools.data.reminder.AndroidReminderNotifier
import com.furina.classtools.data.reminder.ReminderKind
import com.furina.classtools.data.reminder.UserReminderAlarms
import com.furina.classtools.data.reminder.reminderOwner
import com.furina.classtools.data.reminder.reminderSession
import com.furina.classtools.ui.screens.notices.SignupCountdownActivity
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json

internal object SignupCountdowns {
    private const val LIVE_ID = 7420
    private const val OPEN_ID = 7421
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    private fun key(owner: String, id: String) = "${Uri.encode(owner)}/${Uri.encode(id)}"
    fun prefs(context: Context) = context.getSharedPreferences("signup_countdowns", Context.MODE_PRIVATE)
    private fun decode(raw: String?) = raw?.let { runCatching { Json.decodeFromString<SignupCountdown>(it) }.getOrNull() }
    fun get(context: Context, owner: String, id: String) = decode(prefs(context).getString(key(owner, id), null))
    fun all(context: Context) = prefs(context).all.values.mapNotNull { decode(it as? String) }
    private fun save(context: Context, watch: SignupCountdown) =
        prefs(context).edit().putString(key(watch.owner, watch.id), Json.encodeToString(watch)).commit()

    fun noticeIntent(context: Context, owner: String, id: String) = ActivityLinks.intent(context, owner, "signup", id)

    fun timerIntent(context: Context, owner: String, id: String): Intent {
        val instance = get(context, owner, id)?.instance.orEmpty()
        val uri = Uri.Builder().scheme("ct-signup").authority("timer").appendPath(owner)
            .appendPath(id).appendPath(instance).build()
        return Intent(context, SignupCountdownActivity::class.java).setData(uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    @Synchronized
    fun hide(context: Context, owner: String, id: String, generation: String) {
        val watch = get(context, owner, id)?.takeIf { it.instance == generation } ?: return
        if (!save(context, watch.copy(hidden = true))) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(key(owner, id), LIVE_ID)
        manager.cancel(key(owner, id), OPEN_ID)
    }

    fun available(context: Context): Boolean {
        LiveUpdateNotifier.ensureChannel(context)
        return LiveUpdateNotifier.isAvailable(context) && context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(LiveUpdateNotifier.CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    @Synchronized
    fun start(context: Context, watch: SignupCountdown): Boolean {
        val now = System.currentTimeMillis()
        if (watch.start <= now || watch.owner.isBlank() || watch.id.isBlank()) return false
        val next = watch.copy(generation = java.util.UUID.randomUUID().toString())
        if (!save(context, next)) return false
        schedule(context, next, now)
        return true
    }

    private fun schedule(context: Context, watch: SignupCountdown, now: Long) {
        for ((slot, at) in listOf("show" to watch.showAt, "open" to watch.start, "expire" to watch.expiresAt)) {
            val name = "signup:${key(watch.owner, watch.id)}:$slot"
            if (at > now) {
                UserReminderAlarms.set(context, UserReminderAlarms.SIGNUP, watch.owner, watch.id, slot, at)
                val request = OneTimeWorkRequestBuilder<SignupBoundaryWorker>()
                    .setInputData(workDataOf("owner" to watch.owner, "id" to watch.id))
                    .setInitialDelay(at - now, TimeUnit.MILLISECONDS).build()
                WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
            } else {
                UserReminderAlarms.cancel(context, UserReminderAlarms.SIGNUP, watch.owner, watch.id, slot)
                WorkManager.getInstance(context).cancelUniqueWork(name)
            }
        }
        render(context, watch, now)
    }

    @Synchronized
    fun cancel(context: Context, owner: String, id: String) {
        prefs(context).edit().remove(key(owner, id)).commit()
        for (slot in listOf("show", "open", "expire")) {
            UserReminderAlarms.cancel(context, UserReminderAlarms.SIGNUP, owner, id, slot)
            WorkManager.getInstance(context).cancelUniqueWork("signup:${key(owner, id)}:$slot")
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(key(owner, id), LIVE_ID)
        manager.cancel(key(owner, id), OPEN_ID)
    }

    @Synchronized
    fun restore(context: Context, owner: String?) {
        val now = System.currentTimeMillis()
        for (watch in all(context)) {
            if (watch.owner != owner || now >= watch.expiresAt) cancel(context, watch.owner, watch.id)
            else schedule(context, watch, now)
        }
    }

    /** Missing entries in a partial page say nothing about deletion. Authoritative edits do. */
    @Synchronized
    fun refresh(context: Context, owner: String, notices: NoticesData) {
        val now = System.currentTimeMillis()
        val entries = notices.auto.associateBy { it.id }
        for (watch in all(context).filter { it.owner == owner && it.start > now }) {
            val entry = entries[watch.id] ?: continue
            val updated = SignupCountdown.from(owner, entry.group.copy(id = entry.id, title = entry.title), entry.items, now,
                watch.start.takeIf { watch.customTime })?.let { candidate ->
                if (candidate.start == watch.start) candidate.copy(leadMinutes = watch.leadMinutes,
                    hidden = watch.hidden, generation = watch.generation, opened = watch.opened)
                else candidate.copy(leadMinutes = watch.leadMinutes, generation = java.util.UUID.randomUUID().toString())
            }
            if (entry.hidden || entry.group.expired || updated == null) cancel(context, owner, watch.id)
            else if (updated != watch) {
                save(context, updated)
                schedule(context, updated, now)
            }
        }
    }

    suspend fun onBoundary(context: Context, owner: String, id: String) {
        if (reminderSession(context)?.reminderOwner != owner) return
        synchronized(this) {
            get(context, owner, id)?.let { render(context, it, System.currentTimeMillis()) }
        }
    }

    /** An independently requested alarm at the same opening uses the countdown's single result. */
    @Synchronized
    fun deliverOpening(context: Context, owner: String, id: String, at: Long): Boolean {
        val watch = get(context, owner, id) ?: return false
        val now = System.currentTimeMillis()
        if (watch.start != at || now !in at until watch.expiresAt || watch.hidden) return false
        render(context, watch, now)
        return get(context, owner, id)?.opened == true
    }

    private fun render(context: Context, watch: SignupCountdown, now: Long) {
        val tag = key(watch.owner, watch.id)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (now >= watch.expiresAt) {
            cancel(context, watch.owner, watch.id)
            return
        }
        if (watch.hidden || now < watch.showAt) {
            manager.cancel(tag, LIVE_ID)
            return
        }
        val open = now >= watch.start
        if (open) manager.cancel(tag, LIVE_ID)
        if (open && watch.opened || !LiveUpdateNotifier.isAvailable(context)) return
        LiveUpdateNotifier.ensureChannel(context)
        if (open) AndroidReminderNotifier(context).ensureChannels()
        val channel = if (open) ReminderKind.NOTICE_SIGNUP.channelId else LiveUpdateNotifier.CHANNEL_ID
        if (manager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return
        val timer = PendingIntent.getActivity(context, 0, timerIntent(context, watch.owner, watch.id), FLAGS)
        val detail = ActivityLinks.open(context, watch.owner, "signup", watch.id)
        val hide = ActivityLinks.hide(context, watch.owner, "signup", watch.id, watch.instance)
        val firstLine = if (open) "已到报名时间" else "报名开始  ${SignupCountdown.timeText(watch.start)}"
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_live_timer)
            .setColor(context.getColor(R.color.live_vote_accent))
            .setContentTitle(watch.title)
            .setSubText(if (open) "活动报名" else "报名倒计时")
            .setContentText(listOf(firstLine, watch.location).filter { it.isNotBlank() }.joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(watch.details()))
            .setWhen(watch.start).setShowWhen(true)
            .setContentIntent(detail)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
        if (open) {
            builder.setAutoCancel(true).setTimeoutAfter(watch.expiresAt - now)
        } else {
            builder.setOngoing(true).setRequestPromotedOngoing(LiveCapabilities.canPromote(context, channel))
                .setSilent(true)
                .setDeleteIntent(hide)
                .addAction(0, "隐藏本次展示", hide)
                .setUsesChronometer(true).setChronometerCountDown(true)
                .setTimeoutAfter(watch.start - now)
                .addAction(0, "毫秒倒计时", timer)
        }
        manager.notify(tag, if (open) OPEN_ID else LIVE_ID, if (open) builder.build() else builder.buildLiveUpdate())
        if (open) save(context, watch.copy(opened = true))
    }
}

class SignupBoundaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val owner = inputData.getString("owner") ?: return Result.success()
        val id = inputData.getString("id") ?: return Result.success()
        SignupCountdowns.onBoundary(applicationContext, owner, id)
        return Result.success()
    }
}
