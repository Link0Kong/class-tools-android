package com.furina.classtools.data.timetable

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.RemoteException
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.furina.classtools.AppContainer
import com.furina.classtools.BuildConfig
import com.furina.classtools.ClassToolsApp
import com.furina.classtools.data.reminder.CourseActivities
import com.furina.classtools.data.reminder.UserReminderAlarms
import com.furina.classtools.data.session.SessionState
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class XiaobuCourseSync(private val context: Context, private val container: AppContainer) {
    private val scene = BuildConfig.OPPO_COURSE_MILESTONE_CODE.toIntOrNull()?.let { code ->
        XiaobuCourseScene(BuildConfig.OPPO_COURSE_INTENT, BuildConfig.OPPO_COURSE_LAUNCHER,
            BuildConfig.OPPO_COURSE_ENTITY, code, BuildConfig.OPPO_COURSE_MILESTONE_TEXT)
    }?.takeIf { listOf(it.intentName, it.launcherId, it.entityName, it.milestoneText).all(String::isNotBlank) }
    val configured = scene != null
    private val prefs by lazy { context.getSharedPreferences("xiaobu_course", Context.MODE_PRIVATE) }
    private val mutex = Mutex()
    private val message = MutableStateFlow("")
    val status = message.asStateFlow()

    fun clickTarget(entity: String): Pair<String, String>? {
        if (entity != prefs.getString("clickEntity", null)) return null
        val owner = prefs.getString("clickOwner", null) ?: return null
        val focus = prefs.getString("clickFocus", null) ?: return null
        return "account:$owner" to focus
    }

    fun enabledFor(sid: String) = sid.isNotBlank() && prefs.getString("owner", null) == sid

    suspend fun setEnabled(sid: String, enabled: Boolean) = serialized {
        if (!configured || sid.isBlank() || currentSid() != sid) return@serialized
        if (!prefs.edit().putString("owner", if (enabled) sid else null).commit()) {
            throw IOException("Could not persist course card preference")
        }
        reconcile()
    }

    suspend fun refresh(): Boolean = !configured || serialized { reconcile() }

    private suspend fun serialized(action: suspend () -> Unit): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                action()
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message.value = "暂时无法同步小布课程卡片"
                false
            }
        }
    }

    private suspend fun currentSid() = (container.sessionStore.state.first() as? SessionState.SignedIn)
        ?.session?.takeIf { it.isLikelyValid() }?.sid

    private suspend fun reconcile() {
        val config = scene ?: return
        val owner = prefs.getString("owner", null)
        val previous = prefs.getString("entity", null)
        if (owner == null && previous == null) {
            message.value = ""
            return
        }
        val now = System.currentTimeMillis()
        val sid = currentSid()
        val settings = container.reminderSettingsStore.current()
        val enabled = sid != null && sid == owner && settings.enabled && settings.classRemindEnabled
        val snapshot = if (enabled) deviceTimetable(context, sid) else null
        val window = XiaobuCourseCard.window(snapshot, now, settings.classLeadMillis)
        val scheduled = prefs.getLong("nextBoundary", 0)
        val alarmOwner = prefs.getString("alarmOwner", "").orEmpty()
        val boundary = window.nextChange
        if (scheduled != 0L && (scheduled != boundary || sid != alarmOwner)) {
            UserReminderAlarms.cancel(context,
                UserReminderAlarms.XIAOBU, alarmOwner, "course", scheduled.toString())
            WorkManager.getInstance(context).cancelUniqueWork("ct_xiaobu_$scheduled")
            prefs.edit().remove("nextBoundary").remove("alarmOwner").commit()
        }
        if (boundary != null && sid != null) {
            prefs.edit().putLong("nextBoundary", boundary).putString("alarmOwner", sid).commit()
            UserReminderAlarms.set(context,
                UserReminderAlarms.XIAOBU, sid, "course", boundary.toString(), boundary)
            schedule(boundary)
        }
        val course = window.course?.takeUnless {
            CourseActivities.ended(context, sid.orEmpty(), XiaobuCourseCard.focus(it), it.startMillis) ||
                settings.isQuiet(it.startMillis - settings.classLeadMillis, TimetableLink.CHINA_ZONE) ||
                settings.isQuiet(now, TimetableLink.CHINA_ZONE)
        }
        val entity = course?.let { XiaobuCourseCard.entityId(it, sid.orEmpty()) }
        if (previous != null && (previous != entity || prefs.getString("entityOwner", null) != sid)) {
            val result = call("deleteIntent", Bundle().apply {
                putString("intentName", prefs.getString("intent", config.intentName))
                putStringArrayList("entityIds", arrayListOf(previous))
            })
            if (!accepted(result)) return
            prefs.edit().remove("entity").remove("entityOwner").remove("intent").remove("fingerprint").commit()
        }
        if (course == null || entity == null) {
            message.value = when {
                owner == null || owner != sid -> ""
                !enabled -> "请在通知工具中开启上课提醒"
                window.nextChange != null -> "等待下一次课前提醒"
                else -> "当前课表没有待提醒的课程"
            }
            return
        }
        // A refresh with changed classroom or time updates the same system entity.
        val fingerprint = TimetableLink.sha1Hex(
            XiaobuCourseCard.payload(config, course, false, "", 0, sid.orEmpty()).toString()
        )
        val update = prefs.getString("entity", null) == entity
        if (update && prefs.getString("fingerprint", null) == fingerprint) {
            message.value = "系统已接收本节课程"
            return
        }
        // Journal before IPC so that process death after a successful call still permits withdrawal.
        if (currentSid() != sid || System.currentTimeMillis() >= course.startMillis) return
        if (!prefs.edit().putString("entity", entity).putString("entityOwner", sid)
                .putString("clickEntity", entity).putString("clickOwner", sid)
                .putString("clickFocus", XiaobuCourseCard.focus(course).encode())
                .putString("intent", config.intentName).commit()) throw IOException("Could not persist course card")
        val payload = XiaobuCourseCard.payload(config, course, update, UUID.randomUUID().toString(), now, sid.orEmpty())
        val result = call("shareIntent", Bundle().apply { putString("intentData", payload.toString()) })
        if (accepted(result)) {
            prefs.edit().putString("fingerprint", fingerprint).commit()
            message.value = "系统已接收本节课程"
        } else if (!update && result != null) {
            // A definite rejection or unavailable provider could not have created a card.
            prefs.edit().remove("entity").remove("entityOwner").remove("intent").commit()
        }
    }

    private fun schedule(at: Long) {
        val request = OneTimeWorkRequestBuilder<XiaobuCourseWorker>()
            .setInitialDelay((at - System.currentTimeMillis()).coerceAtLeast(0L), TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("ct_xiaobu_$at", ExistingWorkPolicy.KEEP, request)
    }

    private fun accepted(code: Int?): Boolean {
        if (code == 0) return true
        message.value = when (code) {
            ACCESS_DENIED -> "系统拒绝了小布建议的访问请求"
            UNAVAILABLE -> "本机尚无可用的小布建议接口"
            10101001, 10101002, 10201001 -> "系统未授权本应用的课程意图（$code）"
            10103003 -> "请在系统设置中开启小布建议的课程提醒"
            null -> throw IOException("No acknowledgement from course card provider")
            else -> "小布建议返回错误码 $code"
        }
        return false
    }

    private fun call(method: String, args: Bundle): Int? {
        return try {
            val provider = context.packageManager.resolveContentProvider(AUTHORITY, 0) ?: return UNAVAILABLE
            val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
            if (provider.applicationInfo.flags and systemFlags == 0) return UNAVAILABLE
            val client = context.contentResolver.acquireUnstableContentProviderClient(AUTHORITY) ?: return UNAVAILABLE
            client.use { XiaobuCourseCard.responseCode(it.call(method, null, args)?.getString("result")) }
        } catch (_: SecurityException) {
            ACCESS_DENIED
        } catch (_: RemoteException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        const val AUTHORITY = "IntelligentIntent"
        const val UNAVAILABLE = -1
        const val ACCESS_DENIED = -2
    }
}

class XiaobuCourseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val completed = (applicationContext as ClassToolsApp).container.xiaobuCourseSync.refresh()
        return if (completed || runAttemptCount >= 3) Result.success() else Result.retry()
    }
}
