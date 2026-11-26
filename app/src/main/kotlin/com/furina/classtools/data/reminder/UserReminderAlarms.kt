package com.furina.classtools.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.furina.classtools.data.notify.SignupCountdowns
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.session.SessionState
import com.furina.classtools.data.session.SessionStore
import com.furina.classtools.data.tasks.deliverTaskReminder
import com.furina.classtools.data.tasks.restoreTaskReminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal val Session.reminderOwner get() = if (debug) "debug_$sid" else sid

internal suspend fun reminderSession(context: Context): Session? =
    (SessionStore(context.applicationContext).state.first() as? SessionState.SignedIn)?.session
        ?.takeIf { it.isLikelyValid() }

/** Alarm identity includes the actual IDs. Extras alone do not distinguish PendingIntents. */
internal object UserReminderAlarms {
    const val TASK = "task"
    const val SIGNUP = "signup"

    fun exactAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun openSettings(context: Context): Boolean = Build.VERSION.SDK_INT >= 31 && runCatching {
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}")))
    }.isSuccess

    private fun intent(context: Context, kind: String, owner: String, id: String, slot: String): Intent {
        val data = Uri.Builder().scheme("ct-alarm").authority("local")
            .appendPath(kind).appendPath(owner).appendPath(id).appendPath(slot).build()
        return Intent(context, UserReminderAlarmReceiver::class.java).setData(data)
    }

    fun set(context: Context, kind: String, owner: String, id: String, slot: String, at: Long) {
        val intent = intent(context, kind, owner, id, slot).putExtra("at", at)
        val pending = PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (exactAllowed(context)) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                return
            } catch (_: SecurityException) {
                // Permission can be revoked between the check and binder call.
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    fun cancel(context: Context, kind: String, owner: String, id: String, slot: String) {
        val pending = PendingIntent.getBroadcast(context, 0, intent(context, kind, owner, id, slot),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
    }

    fun restore(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("ct_restore_user_reminders", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RestoreUserRemindersWorker>().build())
    }
}

class UserReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val parts = intent.data?.pathSegments?.takeIf { it.size == 4 } ?: return
        val (kind, owner, id, slot) = parts
        val at = intent.getLongExtra("at", 0)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (kind) {
                    UserReminderAlarms.TASK -> deliverTaskReminder(context, owner, id, at)
                    UserReminderAlarms.SIGNUP -> SignupCountdowns.onBoundary(context, owner, id)
                    else -> OneShotTargetKind.entries.find { it.name == kind }?.let {
                        deliverOneShot(context, OneShotKey(it, id, slot.toLongOrNull() ?: return@let), owner)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w(ReminderLog.TAG, "User reminder alarm: " + e.javaClass.simpleName)
            } finally {
                pending.finish()
            }
        }
    }
}

class RestoreUserRemindersWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val session = reminderSession(applicationContext)
        if (session != null) {
            restoreOneShotAlarms(applicationContext, session.reminderOwner)
            if (!session.debug) restoreTaskReminders(applicationContext, session.sid)
        }
        SignupCountdowns.restore(applicationContext, session?.reminderOwner)
        return Result.success()
    }
}
