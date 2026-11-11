package com.furina.classtools.data.reminder

import com.furina.classtools.data.permission.PermissionState
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 单条提醒的「尽量精确」投递（v3.7，用户反馈第 12 条：进程被杀/重启后仍能触发提醒）。
 *
 * 旧实现（v3.2–v3.6）：只有 WorkManager OneTime（`setInitialDelay`）。问题：
 * - Doze/省电下 WorkManager 可能延迟 **数十分钟到数小时**，且系统不保证准点；
 * - 用户设「截止前 1 小时」实际可能在截止后才弹。
 *
 * v3.7 策略（任务口径）：
 * 1. 优先 `AlarmManager.setExactAndAllowWhileIdle`（Doze 下仍准点，RTC_WAKEUP）；
 * 2. Android 12+ 必须检查 `canScheduleExactAlarms()`；未授权 → **回退现有 WorkManager OneTime**（不崩、不弹系统豁免框）；
 * 3. 开机/应用更新后由 [ReminderBootReceiver] 重排（AlarmManager 闹钟不跨重启；WorkManager 任务自身持久化）；
 * 4. 点击/触发由 `ReminderAlarmReceiver` 直接发本地通知（不依赖 UI 进程）。
 *
 * 本文件为纯逻辑（可 JVM 单测）；Android 实现见 `ReminderAlarmAndroid.kt`。
 */

/** 单条提醒投递方式。 */
enum class OneShotDeliveryMode(val label: String) {
    /** AlarmManager 精确闹钟（setExactAndAllowWhileIdle）。 */
    EXACT_ALARM("精确闹钟"),

    /** WorkManager OneTime 近似调度（旧行为；省电/Doze 下可能延迟）。 */
    WORK_MANAGER("WorkManager 近似"),
}

data class OneShotDeliveryPlan(
    val mode: OneShotDeliveryMode,
    val reason: String,
)

/** 投递方式决策（纯函数）：权限缺失优先回退（Worker 内仍会再检查权限，静默跳过）。 */
object OneShotDeliveryDecision {

    fun decide(
        permission: PermissionState,
        canScheduleExactAlarms: Boolean,
    ): OneShotDeliveryPlan = when {
        !permission.isGranted -> OneShotDeliveryPlan(
            mode = OneShotDeliveryMode.WORK_MANAGER,
            reason = "通知权限未开启，回退 WorkManager（触发时静默跳过）",
        )

        canScheduleExactAlarms -> OneShotDeliveryPlan(
            mode = OneShotDeliveryMode.EXACT_ALARM,
            reason = "精确闹钟已授权，使用 setExactAndAllowWhileIdle",
        )

        else -> OneShotDeliveryPlan(
            mode = OneShotDeliveryMode.WORK_MANAGER,
            reason = "Android 12+ 精确闹钟未授权（canScheduleExactAlarms=false），回退 WorkManager",
        )
    }
}

/**
 * 闹钟载荷（纯数据）：由已排程的 [OneShotPending] 生成，经 PendingIntent extras 传给
 * `ReminderAlarmReceiver`；触发时无需再读 SharedPreferences（少一次 IO、少一个失败点）。
 */
data class AlarmReminderPayload(
    val kind: OneShotTargetKind,
    val targetId: String,
    val label: String,
    val targetAtMillis: Long,
    val fireAtMillis: Long,
) {
    fun key(): OneShotKey = ReminderOneShot.key(kind, targetId, fireAtMillis)

    fun toPending(): OneShotPending = OneShotPending(
        key = key(),
        label = label,
        fireAtMillis = fireAtMillis,
        targetAtMillis = targetAtMillis,
    )

    fun event(zone: ZoneId = ZoneId.systemDefault()): ReminderEvent =
        ReminderOneShot.eventFor(kind, targetId, label, targetAtMillis, zone)

    companion object {
        const val MAX_LABEL_LENGTH: Int = 120

        fun sanitizeLabel(label: String): String =
            label.trim().ifBlank { "班级提醒" }.take(MAX_LABEL_LENGTH)

        fun of(item: OneShotPending): AlarmReminderPayload = AlarmReminderPayload(
            kind = item.key.targetKind,
            targetId = item.key.targetId,
            label = sanitizeLabel(item.label),
            targetAtMillis = item.targetAtMillis,
            fireAtMillis = item.fireAtMillis,
        )
    }
}

/** AlarmManager 排程抽象（生产 = `AlarmManagerOneShotScheduler`；单测注入假实现）。 */
interface OneShotAlarmScheduler {
    fun schedule(payload: AlarmReminderPayload)

    fun cancel(key: OneShotKey)

    fun cancelAll(payloads: List<AlarmReminderPayload>)
}

/** 重排计划（开机/更新/App 启动时从持久化元数据恢复 AlarmManager 闹钟）。 */
data class RearmPlan(
    val toSchedule: List<OneShotPending> = emptyList(),
    val expired: List<OneShotPending> = emptyList(),
)

object OneShotRearmPlanner {

    /** 已过点但在该宽限内的事件仍补发（避免重启瞬间错过）。 */
    const val GRACE_MS: Long = 5L * 60L * 1000L

    /** 重排窗口上限：更远的排程元数据视为脏数据（正常用户不会设 30 天后的提醒）。 */
    const val MAX_FUTURE_MS: Long = 30L * 24L * 60L * 60L * 1000L

    /** 重排触发时刻：未来 → 原时刻；已到点但在宽限内 → now + 1s（尽快补发一次）。 */
    const val IMMEDIATE_DELAY_MS: Long = 1_000L

    fun plan(items: List<OneShotPending>, nowMillis: Long): RearmPlan {
        val valid = items
            .filter { it.fireAtMillis > 0L && it.key.targetId.isNotBlank() }
            .distinctBy { it.key }
            .sortedBy { it.fireAtMillis }
        val toSchedule = valid.filter {
            it.fireAtMillis > nowMillis - GRACE_MS && it.fireAtMillis <= nowMillis + MAX_FUTURE_MS
        }
        val expired = valid.filter { it.fireAtMillis <= nowMillis - GRACE_MS }
        return RearmPlan(toSchedule = toSchedule, expired = expired)
    }

    fun triggerAtMillis(item: OneShotPending, nowMillis: Long): Long =
        item.fireAtMillis.coerceAtLeast(nowMillis + IMMEDIATE_DELAY_MS)
}

/**
 * 混合单条提醒调度器（生产默认实现）：
 * - 先做与旧实现相同的校验（权限/时间/上限），保证 UI 行为一致；
 * - 若精确闹钟可用 → AlarmManager（并写本机排程元数据供开机重排）；
 * - 否则 → 委托 [WorkManagerOneShotScheduler]（旧行为不变）；
 * - 切换模式时先清理另一侧的旧任务，避免同一提醒双发。
 */
class HybridOneShotScheduler(
    private val fallback: ReminderOneShotScheduler,
    private val alarmScheduler: OneShotAlarmScheduler,
    private val pendingStore: OneShotPendingStore,
    private val permissionProvider: () -> PermissionState,
    private val exactAlarmAvailability: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) : ReminderOneShotScheduler {

    override suspend fun scheduleHomeworkReminder(
        rowId: String,
        fireAtMillis: Long,
        label: String,
        targetAtMillis: Long,
    ): OneShotScheduleResult = schedule(
        kind = OneShotTargetKind.HOMEWORK,
        targetId = rowId,
        fireAtMillis = fireAtMillis,
        label = label,
        targetAtMillis = targetAtMillis,
    )

    override suspend fun scheduleNoticeReminder(
        groupId: String,
        fireAtMillis: Long,
        label: String,
        targetAtMillis: Long,
    ): OneShotScheduleResult = schedule(
        kind = OneShotTargetKind.NOTICE,
        targetId = groupId,
        fireAtMillis = fireAtMillis,
        label = label,
        targetAtMillis = targetAtMillis,
    )

    private suspend fun schedule(
        kind: OneShotTargetKind,
        targetId: String,
        fireAtMillis: Long,
        label: String,
        targetAtMillis: Long,
    ): OneShotScheduleResult {
        val now = clock()
        val permission = permissionProvider()
        val pending = runCatching { pendingStore.load() }.getOrDefault(emptyList())
        ReminderOneShot.validate(
            targetKind = kind,
            targetId = targetId,
            fireAtMillis = fireAtMillis,
            nowMillis = now,
            permission = permission,
            pendingOfCategory = pending.count { it.key.targetKind == kind },
            targetAtMillis = targetAtMillis,
        )?.let { return it }

        val key = ReminderOneShot.key(kind, targetId, fireAtMillis)
        val item = OneShotPending(
            key = key,
            label = AlarmReminderPayload.sanitizeLabel(label),
            fireAtMillis = fireAtMillis,
            targetAtMillis = targetAtMillis,
        )
        val plan = OneShotDeliveryDecision.decide(permission, exactAlarmAvailability())
        return runCatching {
            // 切换投递方式时清掉另一侧旧任务，防同一提醒双发（REPLACE 语义）。
            runCatching { fallback.cancel(key) }
            alarmScheduler.cancel(key)
            when (plan.mode) {
                OneShotDeliveryMode.EXACT_ALARM -> {
                    pendingStore.add(item)
                    alarmScheduler.schedule(AlarmReminderPayload.of(item))
                    OneShotScheduleResult.Scheduled(key, fireAtMillis)
                }

                OneShotDeliveryMode.WORK_MANAGER -> when (kind) {
                    OneShotTargetKind.HOMEWORK -> fallback.scheduleHomeworkReminder(
                        rowId = targetId,
                        fireAtMillis = fireAtMillis,
                        label = item.label,
                        targetAtMillis = targetAtMillis,
                    )

                    OneShotTargetKind.NOTICE -> fallback.scheduleNoticeReminder(
                        groupId = targetId,
                        fireAtMillis = fireAtMillis,
                        label = item.label,
                        targetAtMillis = targetAtMillis,
                    )
                }
            }
        }.getOrElse { error ->
            OneShotScheduleResult.Failed("排程失败：${error.message ?: "未知错误"}")
        }
    }

    override suspend fun cancel(key: OneShotKey) {
        runCatching { alarmScheduler.cancel(key) }
        runCatching { fallback.cancel(key) }
        runCatching { pendingStore.remove(key) }
    }

    override suspend fun cancelAll() {
        val pending = runCatching { pendingStore.load() }.getOrDefault(emptyList())
        runCatching { alarmScheduler.cancelAll(pending.map { AlarmReminderPayload.of(it) }) }
        runCatching { fallback.cancelAll() }
        runCatching { pendingStore.clear() }
    }

    override suspend fun listPending(): List<OneShotPending> {
        val now = clock()
        val stale = WorkManagerOneShotScheduler.STALE_WINDOW_MS
        return runCatching {
            pendingStore.load()
                .filter { it.fireAtMillis > now - stale }
                .sortedBy { it.fireAtMillis }
        }.getOrDefault(emptyList())
    }

    companion object {
        /** 单条提醒最长提前量（UI 只提供 1 天/1 小时/截止时刻，这里只做防御性上限）。 */
        val MAX_DELIVERY_DELAY_MILLIS: Long = TimeUnit.DAYS.toMillis(30)
    }
}
