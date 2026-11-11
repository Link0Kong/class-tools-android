package com.furina.classtools.data.reminder

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.furina.classtools.data.homework.HomeworkDueCalculator
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeStatusMachine
import com.furina.classtools.data.permission.AndroidPermissionController
import com.furina.classtools.data.permission.PermissionController
import com.furina.classtools.data.permission.PermissionState
import com.furina.classtools.data.permission.PermissionType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 单条提醒（v3，OneTime WorkManager）。
 *
 * 与 mp「用户选择提醒时间」对齐：作业可选 截止前 1 天 / 1 小时 / 截止时刻；
 * 通知可选 活动前 1 小时 + 截止前 1 天 / 1 小时 / 截止时刻。
 *
 * 取舍（WorkManager vs AlarmManager）：
 * - 本实现用 **WorkManager OneTime**（近似）：无需 `SCHEDULE_EXACT_ALARM` 特殊权限，符合当前 Manifest；
 *   精度受 Doze/省电影响（分钟级偏差），与周期提醒一致；
 * - AlarmManager 精确闹钟可做到秒级，但 Android 12+ 需申请特殊权限且商店审核成本高，v3 不采用；
 * - WorkManager 自带持久化（进程重启/重启后保留）；排程元数据同时写本机 SharedPreferences
 *   （`ct_oneshot_pending`）供 [ReminderOneShotScheduler.listPending]/取消；Worker 执行后移除该条。
 *
 * 上限：每类（作业/通知）最多 [ReminderOneShot.MAX_PER_CATEGORY] 条；权限缺失/时间已过/超限
 * 返回**明确错误**（不静默）。
 */
enum class OneShotTargetKind(val toolId: String, val label: String) {
    HOMEWORK("hw", "作业"),
    NOTICE("nt", "通知"),
}

/** 可选提前量（作业/通知的截止类；活动类见 [OneShotReminderOption.BEFORE_EVENT_1_HOUR]）。 */
enum class OneShotReminderOption(val label: String, val leadMillis: Long) {
    BEFORE_1_DAY("截止前 1 天", 24L * 60 * 60 * 1000),
    BEFORE_1_HOUR("截止前 1 小时", 60L * 60 * 1000),
    AT_DEADLINE("截止时刻", 0L),
    BEFORE_EVENT_1_HOUR("活动前 1 小时", 60L * 60 * 1000),
}

/** UI 可选的一项提醒时间（含实际触发时刻）。 */
data class OneShotPlannedTime(
    val option: OneShotReminderOption,
    val label: String,
    val fireAtMillis: Long,
    val targetAtMillis: Long,
)

/** 单条提醒的稳定 key（取消/列表用）。 */
data class OneShotKey(
    val targetKind: OneShotTargetKind,
    val targetId: String,
    val fireAtMillis: Long,
) {
    /** WorkManager 唯一任务名（净化非法字符，避免 OEM/多进程异常）。 */
    val workName: String get() = ReminderOneShot.workName(targetKind, targetId, fireAtMillis)
}

/** 单条提醒调度结果（明确错误，不静默）。 */
sealed interface OneShotScheduleResult {
    data class Scheduled(
        val key: OneShotKey,
        val fireAtMillis: Long,
    ) : OneShotScheduleResult

    /** 通知权限缺失：需要 UI 引导用户开启（不排程）。 */
    data object PermissionMissing : OneShotScheduleResult

    /** 触发时间已过（或距现在太近）——提示用户选择更晚时间。 */
    data class TooLate(val targetAtMillis: Long) : OneShotScheduleResult

    /** 该类已达到上限（默认 20 条）。 */
    data class LimitReached(val limit: Int) : OneShotScheduleResult

    /** 目标没有可提醒的时间。 */
    data object EmptyTarget : OneShotScheduleResult

    data class Failed(val message: String) : OneShotScheduleResult
}

/** 已排程的单条提醒（UI 列表/取消/开机重排用）。 */
data class OneShotPending(
    val key: OneShotKey,
    val label: String,
    val fireAtMillis: Long,
    /** 目标时间（截止/活动时刻；仅用于通知文案与重排，默认与触发时刻相同以兼容旧记录）。 */
    val targetAtMillis: Long = fireAtMillis,
)

/** 单条提醒纯逻辑（可单测）：时间计算、key、上限、校验、通知事件。 */
object ReminderOneShot {

    const val MAX_PER_CATEGORY: Int = 20

    /** 距现在小于该值视为“太晚”（避免刚排就立刻触发，UI 应选更晚时间）。 */
    const val MIN_LEAD_MILLIS: Long = 60L * 1000

    private val HH_MM = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)

    /** 作业可选时间：截止前 1 天 / 1 小时 / 截止时刻（过滤已过去的选项）。 */
    fun homeworkPlannedTimes(
        row: HomeworkRow,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<OneShotPlannedTime> {
        val dueAt = HomeworkDueCalculator.parseDue(row.date, row.time, zone) ?: return emptyList()
        return listOf(
            OneShotReminderOption.BEFORE_1_DAY,
            OneShotReminderOption.BEFORE_1_HOUR,
            OneShotReminderOption.AT_DEADLINE,
        ).map { option ->
            OneShotPlannedTime(
                option = option,
                label = labelOf(option, dueAt, zone),
                fireAtMillis = triggerAt(dueAt, option),
                targetAtMillis = dueAt,
            )
        }.filter { it.fireAtMillis > nowMillis + MIN_LEAD_MILLIS / 2 }
    }

    /** 通知可选时间：活动前 1 小时；截止前 1 天/1 小时/截止时刻（过滤已过去的选项）。 */
    fun noticePlannedTimes(
        group: NoticeGroupDto,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<OneShotPlannedTime> {
        val out = mutableListOf<OneShotPlannedTime>()
        val eventAt = NoticeStatusMachine.eventAtOf(group, zone)?.takeIf { it > nowMillis }
        if (eventAt != null) {
            out += OneShotPlannedTime(
                option = OneShotReminderOption.BEFORE_EVENT_1_HOUR,
                label = labelOf(OneShotReminderOption.BEFORE_EVENT_1_HOUR, eventAt, zone),
                fireAtMillis = triggerAt(eventAt, OneShotReminderOption.BEFORE_EVENT_1_HOUR),
                targetAtMillis = eventAt,
            )
        }
        val deadline = NoticeStatusMachine.deadlineOf(group, zone)?.takeIf { it > nowMillis }
        if (deadline != null) {
            listOf(
                OneShotReminderOption.BEFORE_1_DAY,
                OneShotReminderOption.BEFORE_1_HOUR,
                OneShotReminderOption.AT_DEADLINE,
            ).forEach { option ->
                out += OneShotPlannedTime(
                    option = option,
                    label = labelOf(option, deadline, zone),
                    fireAtMillis = triggerAt(deadline, option),
                    targetAtMillis = deadline,
                )
            }
        }
        return out.filter { it.fireAtMillis > nowMillis + MIN_LEAD_MILLIS / 2 }
    }

    fun triggerAt(targetAtMillis: Long, option: OneShotReminderOption): Long =
        (targetAtMillis - option.leadMillis).coerceAtLeast(0L)

    fun labelOf(
        option: OneShotReminderOption,
        targetAtMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = "${option.label}（${HH_MM.format(Instant.ofEpochMilli(targetAtMillis).atZone(zone))}）"

    /** 稳定任务名（WorkManager unique work）：`ct_oneshot_<kind>_<id>_<fireAt>`。 */
    fun workName(kind: OneShotTargetKind, targetId: String, fireAtMillis: Long): String {
        val cleanId = targetId.trim().map { ch ->
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '_'
        }.joinToString("").take(48)
        return "ct_oneshot_${kind.name.lowercase()}_${cleanId}_$fireAtMillis"
    }

    /** 取消/排程用的 key。 */
    fun key(kind: OneShotTargetKind, targetId: String, fireAtMillis: Long): OneShotKey =
        OneShotKey(kind, targetId.trim(), fireAtMillis)

    /** 排程校验（权限/时间/上限）；null 表示可排程。 */
    fun validate(
        targetKind: OneShotTargetKind,
        targetId: String,
        fireAtMillis: Long,
        nowMillis: Long,
        permission: PermissionState,
        pendingOfCategory: Int,
        targetAtMillis: Long = fireAtMillis,
    ): OneShotScheduleResult? {
        if (targetId.isBlank()) return OneShotScheduleResult.EmptyTarget
        if (permission != PermissionState.GRANTED) return OneShotScheduleResult.PermissionMissing
        if (fireAtMillis <= nowMillis + MIN_LEAD_MILLIS) {
            return OneShotScheduleResult.TooLate(targetAtMillis)
        }
        if (pendingOfCategory >= MAX_PER_CATEGORY) {
            return OneShotScheduleResult.LimitReached(MAX_PER_CATEGORY)
        }
        return null
    }

    /** 构造单条提醒的通知事件（Worker 与测试共用）。 */
    fun eventFor(
        kind: OneShotTargetKind,
        targetId: String,
        label: String,
        targetAtMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ReminderEvent {
        val title = if (kind == OneShotTargetKind.HOMEWORK) "作业提醒" else "通知提醒"
        return ReminderEvent(
            kind = if (kind == OneShotTargetKind.HOMEWORK) ReminderKind.HOMEWORK_DUE else ReminderKind.NOTICE_DUE,
            key = "oneshot:${kind.name.lowercase()}:$targetId",
            triggerAtMillis = targetAtMillis,
            title = title,
            body = "$label · ${HH_MM.format(Instant.ofEpochMilli(targetAtMillis).atZone(zone))}",
            toolId = kind.toolId,
        )
    }
}

// ---------------------------------------------------------------- 持久化

/** 单条提醒排程元数据存储（SharedPreferences；WorkManager 自身也持久化，两者配合用于列表/取消/上限）。 */
interface OneShotPendingStore {
    suspend fun load(): List<OneShotPending>

    suspend fun add(item: OneShotPending)

    suspend fun remove(key: OneShotKey)

    suspend fun clear()
}

class InMemoryOneShotPendingStore(
    initial: List<OneShotPending> = emptyList(),
) : OneShotPendingStore {
    private val items = initial.toMutableList()

    override suspend fun load(): List<OneShotPending> = items.toList()

    override suspend fun add(item: OneShotPending) {
        items.removeAll { it.key == item.key }
        items += item
    }

    override suspend fun remove(key: OneShotKey) {
        items.removeAll { it.key == key }
    }

    override suspend fun clear() {
        items.clear()
    }
}

/** 编解码（纯函数，可单测）：JSON 数组，字段 kind/id/label/fireAt/targetAt。 */
object OneShotPendingCodec {

    fun encode(items: List<OneShotPending>): String = buildJsonArray {
        items.forEach { item ->
            add(
                buildJsonObject {
                    put("kind", JsonPrimitive(item.key.targetKind.name))
                    put("id", JsonPrimitive(item.key.targetId))
                    put("label", JsonPrimitive(item.label))
                    put("fireAt", JsonPrimitive(item.fireAtMillis))
                    put("targetAt", JsonPrimitive(item.targetAtMillis))
                },
            )
        }
    }.toString()

    fun decode(raw: String?): List<OneShotPending> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = parseJsonOrNull(NetJson, raw) as? JsonArray ?: return emptyList()
        val out = ArrayList<OneShotPending>(array.size)
        for (element in array) {
            val obj = element as? kotlinx.serialization.json.JsonObject ?: continue
            val kind = runCatching {
                OneShotTargetKind.valueOf(obj["kind"].asStringOrNull().orEmpty())
            }.getOrNull() ?: continue
            val id = obj["id"].asStringOrNull().orEmpty().trim()
            if (id.isEmpty()) continue
            val fireAt = (obj["fireAt"] as? JsonPrimitive)?.longOrNull ?: 0L
            if (fireAt <= 0L) continue
            val targetAt = (obj["targetAt"] as? JsonPrimitive)?.longOrNull
                ?.takeIf { it > 0L } ?: fireAt
            out += OneShotPending(
                key = OneShotKey(kind, id, fireAt),
                label = obj["label"].asStringOrNull().orEmpty(),
                fireAtMillis = fireAt,
                targetAtMillis = targetAt,
            )
        }
        return out
    }
}

class SharedPrefsOneShotPendingStore(context: Context) : OneShotPendingStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun load(): List<OneShotPending> =
        OneShotPendingCodec.decode(prefs.getString(KEY_ITEMS, null))

    override suspend fun add(item: OneShotPending) {
        val next = load().filterNot { it.key == item.key } + item
        write(next)
    }

    override suspend fun remove(key: OneShotKey) {
        write(load().filterNot { it.key == key })
    }

    override suspend fun clear() {
        prefs.edit().remove(KEY_ITEMS).apply()
    }

    private fun write(items: List<OneShotPending>) {
        prefs.edit().putString(KEY_ITEMS, OneShotPendingCodec.encode(items.take(MAX_ITEMS))).apply()
    }

    private companion object {
        const val PREFS_NAME = "ct_oneshot_pending"
        const val KEY_ITEMS = "items"
        const val MAX_ITEMS = 100
    }
}

// ---------------------------------------------------------------- 调度器

/** 单条提醒调度器（接口；Android 实现见 [WorkManagerOneShotScheduler]）。 */
interface ReminderOneShotScheduler {
    suspend fun scheduleHomeworkReminder(
        rowId: String,
        fireAtMillis: Long,
        label: String,
        targetAtMillis: Long = fireAtMillis,
    ): OneShotScheduleResult

    suspend fun scheduleNoticeReminder(
        groupId: String,
        fireAtMillis: Long,
        label: String,
        targetAtMillis: Long = fireAtMillis,
    ): OneShotScheduleResult

    suspend fun cancel(key: OneShotKey)

    suspend fun cancelAll()

    suspend fun listPending(): List<OneShotPending>
}

/**
 * WorkManager 实现：OneTimeWorkRequest + unique work（同名 REPLACE → 同一目标同时间重复点击幂等）。
 * 持久化：WorkManager 保留任务；元数据另存 SharedPreferences（[OneShotPendingStore]），Worker 执行后移除。
 */
class WorkManagerOneShotScheduler(
    context: Context,
    private val permissionController: PermissionController,
    private val pendingStore: OneShotPendingStore = SharedPrefsOneShotPendingStore(context),
    private val clock: () -> Long = System::currentTimeMillis,
) : ReminderOneShotScheduler {

    private val appContext: Context = context.applicationContext
    private val workManager: WorkManager by lazy { WorkManager.getInstance(appContext) }

    override suspend fun scheduleHomeworkReminder(
        rowId: String,
        fireAtMillis: Long,
        label: String,
        targetAtMillis: Long,
    ): OneShotScheduleResult = schedule(OneShotTargetKind.HOMEWORK, rowId, fireAtMillis, label, targetAtMillis)

    override suspend fun scheduleNoticeReminder(
        groupId: String,
        fireAtMillis: Long,
        label: String,
        targetAtMillis: Long,
    ): OneShotScheduleResult = schedule(OneShotTargetKind.NOTICE, groupId, fireAtMillis, label, targetAtMillis)

    private suspend fun schedule(
        kind: OneShotTargetKind,
        targetId: String,
        fireAtMillis: Long,
        label: String,
        targetAtMillis: Long,
    ): OneShotScheduleResult {
        val now = clock()
        val pending = runCatching { pendingStore.load() }.getOrDefault(emptyList())
        ReminderOneShot.validate(
            targetKind = kind,
            targetId = targetId,
            fireAtMillis = fireAtMillis,
            nowMillis = now,
            permission = permissionController.state(PermissionType.NOTIFICATIONS),
            pendingOfCategory = pending.count { it.key.targetKind == kind },
            targetAtMillis = targetAtMillis,
        )?.let { return it }

        val key = ReminderOneShot.key(kind, targetId, fireAtMillis)
        val item = OneShotPending(
            key = key,
            label = label.ifBlank { "班级提醒" }.take(120),
            fireAtMillis = fireAtMillis,
            targetAtMillis = targetAtMillis,
        )
        return runCatching {
            enqueue(item)
            pendingStore.add(item)
            OneShotScheduleResult.Scheduled(key, fireAtMillis)
        }.getOrElse { error ->
            OneShotScheduleResult.Failed("排程失败：${error.message ?: "未知错误"}")
        }
    }

    /** 构造 WorkManager 请求（OneTime + 初始延迟 + 输入数据；排程/重排共用）。 */
    private fun requestFor(item: OneShotPending): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<ReminderOneShotWorker>()
            .setInitialDelay(
                (item.fireAtMillis - clock()).coerceAtLeast(0L),
                TimeUnit.MILLISECONDS,
            )
            .setInputData(
                Data.Builder()
                    .putString(KEY_KIND, item.key.targetKind.name)
                    .putString(KEY_TARGET_ID, item.key.targetId)
                    .putString(KEY_LABEL, item.label.ifBlank { "班级提醒" }.take(120))
                    .putLong(KEY_TARGET_AT, item.targetAtMillis)
                    .putLong(KEY_FIRE_AT, item.fireAtMillis)
                    .build(),
            )
            .addTag(TAG)
            .addTag(tagOf(item.key.targetKind))
            .build()

    private fun enqueue(item: OneShotPending) {
        workManager.enqueueUniqueWork(item.key.workName, ExistingWorkPolicy.REPLACE, requestFor(item))
    }

    /**
     * 重排已持久化的单条提醒（开机/应用更新/App 启动后；WorkManager 任务自身跨重启持久化，
     * 此方法用于「精确闹钟不可用」回退模式或系统清理后的重新入队）。
     */
    suspend fun rearm(item: OneShotPending) {
        if (item.fireAtMillis <= 0L) return
        runCatching { enqueue(item) }
    }

    override suspend fun cancel(key: OneShotKey) {
        runCatching { workManager.cancelUniqueWork(key.workName) }
        runCatching { pendingStore.remove(key) }
    }

    override suspend fun cancelAll() {
        runCatching { workManager.cancelAllWorkByTag(TAG) }
        runCatching { pendingStore.clear() }
    }

    override suspend fun listPending(): List<OneShotPending> = withContext(Dispatchers.IO) {
        val now = clock()
        runCatching {
            pendingStore.load()
                .filter { it.fireAtMillis > now - STALE_WINDOW_MS }
                .sortedBy { it.fireAtMillis }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val TAG: String = "ct_oneshot_reminder"

        /** 列表保留窗口：过期超过 1 天的排程元数据视为脏数据。 */
        const val STALE_WINDOW_MS: Long = 24L * 60 * 60 * 1000

        internal const val KEY_KIND: String = "kind"
        internal const val KEY_TARGET_ID: String = "targetId"
        internal const val KEY_LABEL: String = "label"
        internal const val KEY_TARGET_AT: String = "targetAt"
        internal const val KEY_FIRE_AT: String = "fireAt"

        private fun tagOf(kind: OneShotTargetKind): String = "$TAG.${kind.name.lowercase()}"
    }
}

/** 单条提醒 Worker：权限缺失静默成功（UI 已在排程时拦截并提示），否则发通知并清理排程元数据。 */
class ReminderOneShotWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val kind = runCatching {
            OneShotTargetKind.valueOf(inputData.getString(WorkManagerOneShotScheduler.KEY_KIND).orEmpty())
        }.getOrNull() ?: return Result.success()
        val targetId = inputData.getString(WorkManagerOneShotScheduler.KEY_TARGET_ID).orEmpty()
        if (targetId.isBlank()) return Result.success()
        val label = inputData.getString(WorkManagerOneShotScheduler.KEY_LABEL).orEmpty()
        val targetAt = inputData.getLong(WorkManagerOneShotScheduler.KEY_TARGET_AT, 0L)
            .takeIf { it > 0L } ?: System.currentTimeMillis()
        val fireAt = inputData.getLong(WorkManagerOneShotScheduler.KEY_FIRE_AT, 0L)
        val key = if (fireAt > 0L) OneShotKey(kind, targetId, fireAt) else null

        val permission = AndroidPermissionController(applicationContext)
            .state(PermissionType.NOTIFICATIONS)
        if (!permission.isGranted) {
            key?.let { runCatching { SharedPrefsOneShotPendingStore(applicationContext).remove(it) } }
            Log.i(ReminderLog.TAG, "[CT_REMINDER] oneshot skipped: permission")
            return Result.success()
        }
        val notifier = AndroidReminderNotifier(applicationContext)
        notifier.ensureChannels()
        val event = ReminderOneShot.eventFor(kind, targetId, label, targetAt)
        val sent = notifier.notify(event)
        key?.let { runCatching { SharedPrefsOneShotPendingStore(applicationContext).remove(it) } }
        Log.i(ReminderLog.TAG, "[CT_REMINDER] oneshot sent=$sent kind=${kind.name} fireAt=$fireAt")
        return Result.success()
    }
}

/**
 * UI 便捷工厂：`ReminderOneShotFactory.create(context, container.permissionController)`。
 *
 * v3.7：返回 [HybridOneShotScheduler] —— 精确闹钟可用时用 `AlarmManager.setExactAndAllowWhileIdle`，
 * Android 12+ 未授权（`canScheduleExactAlarms=false`）时**自动回退**到旧 [WorkManagerOneShotScheduler]。
 */
object ReminderOneShotFactory {
    fun create(
        context: Context,
        permissionController: PermissionController,
    ): ReminderOneShotScheduler {
        val appContext = context.applicationContext
        return HybridOneShotScheduler(
            fallback = WorkManagerOneShotScheduler(appContext, permissionController),
            alarmScheduler = AlarmManagerOneShotScheduler(appContext),
            pendingStore = SharedPrefsOneShotPendingStore(appContext),
            permissionProvider = {
                permissionController.state(PermissionType.NOTIFICATIONS)
            },
            exactAlarmAvailability = {
                ReminderExactAlarmSupport.canScheduleExactAlarms(appContext)
            },
        )
    }
}
