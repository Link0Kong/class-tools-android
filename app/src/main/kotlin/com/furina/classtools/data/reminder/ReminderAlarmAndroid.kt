package com.furina.classtools.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.furina.classtools.data.permission.AndroidPermissionController
import com.furina.classtools.data.permission.PermissionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager 精确闹钟 + 触发广播（v3.7）。
 *
 * - Android 12+ 通过 [ReminderExactAlarmSupport.canScheduleExactAlarms] 判断；未授权时上层
 *   [HybridOneShotScheduler] 已回退 WorkManager，本类不会被调用（rearm 也先判断）。
 * - 触发由 [ReminderAlarmReceiver] 直接发通知（不依赖 Activity/UI 进程）。
 * - 开机/应用更新后由 `ReminderBootReceiver` 调 [ReminderAlarmRuntime.rearmPending] 重排。
 */
object ReminderExactAlarmSupport {

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.applicationContext.getSystemService(AlarmManager::class.java) ?: return false
        return runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)
    }
}

/** 闹钟 Intent 编解码（extras 与 [AlarmReminderPayload] 一一对应）。 */
object ReminderAlarmIntents {

    const val ACTION_FIRE: String = "com.furina.classtools.action.REMINDER_ALARM"

    const val EXTRA_KIND: String = "ct.alarm.kind"
    const val EXTRA_TARGET_ID: String = "ct.alarm.targetId"
    const val EXTRA_LABEL: String = "ct.alarm.label"
    const val EXTRA_TARGET_AT: String = "ct.alarm.targetAt"
    const val EXTRA_FIRE_AT: String = "ct.alarm.fireAt"

    fun buildIntent(context: Context, payload: AlarmReminderPayload): Intent =
        Intent(context.applicationContext, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_KIND, payload.kind.name)
            putExtra(EXTRA_TARGET_ID, payload.targetId)
            putExtra(EXTRA_LABEL, payload.label)
            putExtra(EXTRA_TARGET_AT, payload.targetAtMillis)
            putExtra(EXTRA_FIRE_AT, payload.fireAtMillis)
        }

    fun requestCodeOf(payload: AlarmReminderPayload): Int = payload.key().workName.hashCode()

    fun pendingIntent(context: Context, payload: AlarmReminderPayload): PendingIntent = PendingIntent.getBroadcast(
        context.applicationContext,
        requestCodeOf(payload),
        buildIntent(context, payload),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 仅查找已存在的 PendingIntent（取消用；不存在返回 null）。 */
    fun existingPendingIntent(context: Context, key: OneShotKey): PendingIntent? = PendingIntent.getBroadcast(
        context.applicationContext,
        key.workName.hashCode(),
        Intent(context.applicationContext, ReminderAlarmReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    fun decode(intent: Intent?): AlarmReminderPayload? {
        if (intent == null) return null
        val kind = runCatching {
            OneShotTargetKind.valueOf(intent.getStringExtra(EXTRA_KIND).orEmpty())
        }.getOrNull() ?: return null
        val targetId = intent.getStringExtra(EXTRA_TARGET_ID).orEmpty().trim()
        if (targetId.isBlank()) return null
        val fireAt = intent.getLongExtra(EXTRA_FIRE_AT, 0L)
        if (fireAt <= 0L) return null
        val targetAt = intent.getLongExtra(EXTRA_TARGET_AT, fireAt).takeIf { it > 0L } ?: fireAt
        return AlarmReminderPayload(
            kind = kind,
            targetId = targetId,
            label = AlarmReminderPayload.sanitizeLabel(intent.getStringExtra(EXTRA_LABEL).orEmpty()),
            targetAtMillis = targetAt,
            fireAtMillis = fireAt,
        )
    }
}

/** AlarmManager 实现：`setExactAndAllowWhileIdle`（API 23+）/ `setExact`（API 26 以下不会出现，minSdk 26）。 */
class AlarmManagerOneShotScheduler(context: Context) : OneShotAlarmScheduler {

    private val appContext: Context = context.applicationContext

    override fun schedule(payload: AlarmReminderPayload) {
        val manager = appContext.getSystemService(AlarmManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !ReminderExactAlarmSupport.canScheduleExactAlarms(appContext)) {
            Log.i(ReminderLog.TAG, "[CT_REMINDER] exact alarm not allowed, skip alarm scheduling")
            return
        }
        val pendingIntent = ReminderAlarmIntents.pendingIntent(appContext, payload)
        val triggerAt = OneShotRearmPlanner.triggerAtMillis(payload.toPending(), System.currentTimeMillis())
        runCatching {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            Log.i(ReminderLog.TAG, "[CT_REMINDER] exact alarm scheduled fireAt=${payload.fireAtMillis}")
        }.onFailure { error ->
            Log.i(ReminderLog.TAG, "[CT_REMINDER] exact alarm failed: ${error.javaClass.simpleName}")
        }
    }

    override fun cancel(key: OneShotKey) {
        val manager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = ReminderAlarmIntents.existingPendingIntent(appContext, key) ?: return
        runCatching { manager.cancel(pendingIntent) }
        runCatching { pendingIntent.cancel() }
    }

    override fun cancelAll(payloads: List<AlarmReminderPayload>) {
        payloads.forEach { cancel(it.key()) }
    }
}

/**
 * 精确闹钟触发广播：直接发本地通知并清理排程元数据。
 * `goAsync()` 保证广播进程在通知发送完成前不被杀；任何异常静默（不影响系统）。
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ReminderAlarmIntents.ACTION_FIRE) return
        val payload = ReminderAlarmIntents.decode(intent) ?: return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val permission = runCatching {
                    AndroidPermissionController(appContext).state(PermissionType.NOTIFICATIONS)
                }.getOrNull()
                if (permission?.isGranted == true) {
                    val notifier = AndroidReminderNotifier(appContext)
                    runCatching { notifier.ensureChannels() }
                    val sent = runCatching { notifier.notify(payload.event()) }.getOrDefault(false)
                    Log.i(ReminderLog.TAG, "[CT_REMINDER] alarm fired sent=$sent fireAt=${payload.fireAtMillis}")
                } else {
                    Log.i(ReminderLog.TAG, "[CT_REMINDER] alarm skipped: permission")
                }
                runCatching { ReminderChannelInstaller.ensureCreated(appContext) }
                runCatching { SharedPrefsOneShotPendingStore(appContext).remove(payload.key()) }
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }
}

/** 开机/更新/App 启动后重排单条提醒（AlarmManager 闹钟不跨重启；WorkManager 任务自身持久化）。 */
object ReminderAlarmRuntime {

    /**
     * 重排持久化的单条提醒：
     * - 精确闹钟可用 → AlarmManager（已到点但在宽限内 → 立即补发）；
     * - 不可用 → 重新入队 WorkManager OneTime（与旧行为一致）；
     * - 过旧（> 宽限）→ 清理排程元数据。
     *
     * @return 实际重排条数（诊断/日志用）。
     */
    suspend fun rearmPending(context: Context): Int {
        val appContext = context.applicationContext
        val store = SharedPrefsOneShotPendingStore(appContext)
        val pending = runCatching { store.load() }.getOrDefault(emptyList())
        if (pending.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val plan = OneShotRearmPlanner.plan(pending, now)
        val exactAllowed = ReminderExactAlarmSupport.canScheduleExactAlarms(appContext)
        val permissionController = AndroidPermissionController(appContext)
        val permission = runCatching {
            permissionController.state(PermissionType.NOTIFICATIONS)
        }.getOrNull()
        val alarmScheduler = AlarmManagerOneShotScheduler(appContext)
        val workScheduler = WorkManagerOneShotScheduler(appContext, permissionController)
        var scheduled = 0
        plan.toSchedule.forEach { item ->
            if (permission?.isGranted != true) return@forEach
            val ok = runCatching {
                val payload = AlarmReminderPayload.of(item)
                if (exactAllowed) {
                    alarmScheduler.schedule(payload)
                } else {
                    workScheduler.rearm(item)
                }
                true
            }.getOrDefault(false)
            if (ok) scheduled += 1
        }
        plan.expired.forEach { item ->
            runCatching { store.remove(item.key) }
        }
        Log.i(
            ReminderLog.TAG,
            "[CT_REMINDER] rearm pending: total=${pending.size} scheduled=$scheduled expired=${plan.expired.size} exact=$exactAllowed",
        )
        return scheduled
    }
}
