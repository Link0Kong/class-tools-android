package com.furina.classtools.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.furina.classtools.AppContainer
import com.furina.classtools.ClassToolsApp
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.notices.NoticesData
import com.furina.classtools.data.permission.AndroidPermissionController
import com.furina.classtools.data.permission.PermissionController
import com.furina.classtools.data.permission.PermissionState
import com.furina.classtools.data.permission.PermissionType
import com.furina.classtools.data.timetable.TtCalendar
import com.furina.classtools.data.timetable.TtPlan
import com.furina.classtools.data.vote.PollSummary
import com.furina.classtools.features.water.WaterCodec
import com.furina.classtools.features.water.WaterRoomState
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 提醒调度（WorkManager）：一个 15 分钟周期任务（Android 允许的最小周期），
 * 每次只处理「当前窗口内到点」的提醒；设置变更/开机后调用 [ReminderRuntime.reschedule] 幂等重建。
 *
 * 设计取舍：
 * - **不使用精确闹钟**（AlarmManager 精确闹钟在 Android 12+ 需 SCHEDULE_EXACT_ALARM 特殊权限）；
 *   周期任务在 Doze 下可能延迟，属于可接受的本机提醒精度；
 * - 通知权限/总开关任一关闭时不打扰用户（Worker 静默成功）；
 * - 去重由 [ReminderDedupe] + [ReminderDedupeStore] 保证「同 id 当日不重复」。
 */
interface ReminderScheduler {
    /** 幂等建立/更新周期调度。 */
    fun ensureScheduled()

    /** 取消全部提醒调度（用户关闭提醒时调用）。 */
    fun cancelAll()
}

class WorkManagerReminderScheduler(
    context: Context,
) : ReminderScheduler {

    private val appContext: Context = context.applicationContext

    override fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
            .build()
        runCatching {
            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }

    override fun cancelAll() {
        runCatching { WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK_NAME) }
    }

    companion object {
        const val UNIQUE_WORK_NAME: String = "ct_reminder_periodic"

        /** WorkManager 周期任务最小值 15 分钟（API 契约，不可调小）。 */
        const val INTERVAL_MINUTES: Long = 15
    }
}

/** 便捷入口（UI/AppContainer/设置页调用）。 */
object ReminderRuntime {

    /** 设置变更后立即触发一次 run 的唯一任务名（OneTimeWork；短延迟让 UI 先落盘设置）。 */
    const val IMMEDIATE_WORK_NAME: String = "ct_reminder_now"

    /** 用户开启提醒/修改设置后调用：重建周期调度，并**立即补跑一次**（避免「开了但要等下一轮」）。 */
    fun reschedule(context: Context) {
        WorkManagerReminderScheduler(context).ensureScheduled()
        runNow(context)
    }

    /** 立即触发一次提醒扫描（OneTimeWorkRequest；不等待、不阻塞 UI）。 */
    fun runNow(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(1, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
            Log.i(ReminderLog.TAG, ReminderLog.manualTrigger("settings/changed"))
        }
    }

    /** 用户关闭提醒时调用（取消周期与待执行的立即任务）。 */
    fun cancel(context: Context) {
        val appContext = context.applicationContext
        WorkManagerReminderScheduler(context).cancelAll()
        runCatching { WorkManager.getInstance(appContext).cancelUniqueWork(IMMEDIATE_WORK_NAME) }
    }
}

/** 供 Worker/UI 读取「提醒所需数据」的窄接口（可注入测试；默认从 AppContainer 本机缓存读，不额外联网）。 */
interface ReminderDataProvider {
    suspend fun homeworkRows(): List<HomeworkRow>

    suspend fun homeworkDoneIds(): Set<String>

    suspend fun timetablePlan(nowMillis: Long, zone: ZoneId): TtPlan?

    /** 通知快照（本机缓存；没有则 null）。 */
    suspend fun noticesData(): NoticesData?

    /** 已处理通知组 id（marks `ntdone` 命中）。 */
    suspend fun handledNoticeIds(): Set<String>

    /** 投票摘要（只读本机缓存，不联网；没有则空）。 */
    suspend fun votePolls(): List<PollSummary> = emptyList()

    /** 关注宿舍的水量记录（`userdata.power.sel` → `userdata.water`；没有则 null）。 */
    suspend fun watchedWaterRoom(): WaterWatch? = null
}

/** 关注宿舍 + 水量记录（水费提醒用；只读本机缓存）。 */
data class WaterWatch(
    val roomKey: String,
    val room: WaterRoomState,
)

class ContainerReminderDataProvider(context: Context) : ReminderDataProvider {

    private val appContext: Context = context.applicationContext

    private fun container(): AppContainer? = (appContext as? ClassToolsApp)?.container

    override suspend fun homeworkRows(): List<HomeworkRow> = runCatching {
        container()?.homeworkRepository?.cached().orEmpty()
    }.getOrDefault(emptyList())

    override suspend fun homeworkDoneIds(): Set<String> = runCatching {
        container()?.marksRepository?.marks?.value?.hwDone
            ?.filterValues { it }
            ?.keys
            .orEmpty()
    }.getOrDefault(emptySet())

    override suspend fun timetablePlan(nowMillis: Long, zone: ZoneId): TtPlan? = runCatching {
        container()?.timetableRepository?.cached()?.let { snapshot ->
            TtCalendar.plan(snapshot, nowMillis, zone)
        }
    }.getOrNull()

    override suspend fun noticesData(): NoticesData? = runCatching {
        container()?.noticesRepository?.cached()
    }.getOrNull()

    override suspend fun handledNoticeIds(): Set<String> = runCatching {
        container()?.marksRepository?.marks?.value?.ntDone
            ?.filterValues { it }
            ?.keys
            .orEmpty()
    }.getOrDefault(emptySet())

    /** 只读投票列表缓存（不发起网络；列表缓存由投票页刷新）。 */
    override suspend fun votePolls(): List<PollSummary> = runCatching {
        container()?.voteRepository?.cachedPolls().orEmpty()
    }.getOrDefault(emptyList())

    /**
     * 关注宿舍的水量记录：
     * 1) `userdata.power.sel`（关注宿舍）→ `water.rooms[sel]`；
     * 2) 未选关注宿舍时退回第一条有记录的宿舍（与鸿蒙端一致，避免漏提醒）。
     */
    override suspend fun watchedWaterRoom(): WaterWatch? = runCatching {
        val sections = container()?.userDataRepository?.cachedBlob() ?: return@runCatching null
        val state = WaterCodec.fromJson(sections["water"])
        if (state.rooms.isEmpty()) return@runCatching null
        val sel = ((sections["power"] as? kotlinx.serialization.json.JsonObject)
            ?.get("sel") as? kotlinx.serialization.json.JsonPrimitive)
            ?.content
            ?.trim()
            .orEmpty()
        val key = sel.takeIf { state.rooms.containsKey(it) }
            ?: state.rooms.keys.firstOrNull().orEmpty()
        val room = state.rooms[key] ?: return@runCatching null
        WaterWatch(roomKey = key, room = room)
    }.getOrNull()
}

/**
 * 提醒执行引擎（一次扫描）：读设置 → 权限/开关检查 → 本机缓存规划 → 去重 → 发通知。
 *
 * v3 可测试/可观测：
 * - 主构造器**不依赖 Context**（全部依赖可注入，单测用假实现，见 ReminderEngineTest）；
 * - 生产用 secondary constructor（`ReminderEngine(context)`）自动装配 Android 实现；
 * - [runNow] 与 [runOnce] 等价但语义更明确（设置变更后立即检查）；
 * - [diagnostics] 返回权限/设置/缓存/上次运行摘要（不含通知正文/隐私数据）；
 * - Doze 延迟补偿：按上次运行时间回看（默认 10 分钟，最多 6 小时），配合去重避免重复。
 */
class ReminderEngine(
    private val settingsStore: ReminderSettingsStore,
    private val dedupeStore: ReminderDedupeStore,
    private val notifier: ReminderNotifier,
    private val permissionController: PermissionController,
    private val dataProvider: ReminderDataProvider,
    private val runStateStore: ReminderRunStateStore = InMemoryReminderRunStateStore(),
    private val pendingStore: ReminderPendingEventStore = InMemoryReminderPendingEventStore(),
    private val snapshotStore: ReminderSnapshotStore = InMemoryReminderSnapshotStore(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val catchUpMaxMillis: Long = ReminderCatchUp.MAX_LOOKBACK_MS,
) {

    constructor(context: Context) : this(
        settingsStore = DataStoreReminderSettingsStore(context),
        dedupeStore = SharedPrefsReminderDedupeStore(context),
        notifier = AndroidReminderNotifier(context),
        permissionController = AndroidPermissionController(context),
        dataProvider = ContainerReminderDataProvider(context),
        runStateStore = SharedPrefsReminderRunStateStore(context),
        pendingStore = SharedPrefsReminderPendingEventStore(context),
        snapshotStore = SharedPrefsReminderSnapshotStore(context),
    )

    /** 兼容旧名（v3 起语义等价于 [runNow]）。 */
    suspend fun runOnce(): ReminderRunResult = runNow()

    /** 立即执行一次提醒扫描（Worker / 设置变更触发 / 诊断用）。 */
    suspend fun runNow(): ReminderRunResult {
        val now = clock()
        val permission = runCatching {
            permissionController.state(PermissionType.NOTIFICATIONS)
        }.getOrDefault(PermissionState.REQUESTABLE)
        val settings = runCatching { settingsStore.current() }.getOrDefault(ReminderSettings.DEFAULT)
        val rows = runCatching { dataProvider.homeworkRows() }.getOrDefault(emptyList())
        val doneIds = runCatching { dataProvider.homeworkDoneIds() }.getOrDefault(emptySet())
        val ttPlan = runCatching { dataProvider.timetablePlan(now, zone) }.getOrNull()
        val notices = runCatching { dataProvider.noticesData() }.getOrNull()
        val handledNoticeIds = runCatching { dataProvider.handledNoticeIds() }.getOrDefault(emptySet())
        val polls = runCatching { dataProvider.votePolls() }.getOrDefault(emptyList())
        val water = runCatching { dataProvider.watchedWaterRoom() }.getOrNull()
        val snapshot = runCatching { snapshotStore.load() }.getOrDefault(ReminderSnapshot())
        val cacheSummary = buildString {
            append("作业 ").append(rows.size).append(" 条")
            append(" · 课表").append(if (ttPlan?.hasData == true) "可用" else "无")
            append(" · 通知 ").append(notices?.auto?.size ?: 0).append(" 条")
            append(" · 投票 ").append(polls.size).append(" 条")
            append(" · 水量").append(if (water != null) "可用" else "无")
        }
        val settingsSummary = settingsSummaryOf(settings)

        suspend fun saveSnapshot() {
            runCatching {
                snapshotStore.save(
                    ReminderPlanner.nextSnapshot(
                        rows = rows,
                        notices = notices,
                        polls = polls,
                        waterRoomKey = water?.roomKey.orEmpty(),
                        waterRoom = water?.room,
                        previous = snapshot,
                    ),
                )
            }
        }

        suspend fun finish(result: ReminderRunResult): ReminderRunResult {
            val enriched = result.copy(
                permission = permission.name,
                settingsSummary = settingsSummary,
                cacheSummary = cacheSummary,
                checkedAtMillis = now,
            )
            runCatching { runStateStore.recordRun(now, enriched.logSummary()) }
            return enriched
        }

        if (!settings.enabled) {
            return finish(ReminderRunResult(disabledReason = "通知总开关已关闭", reason = "总开关关闭"))
        }
        if (!settings.anyModuleEnabled) {
            return finish(ReminderRunResult(disabledReason = "所有通知类型均已关闭", reason = "全部类型关闭"))
        }
        if (!permission.isGranted) {
            // 权限缺失时仍建立基线：用户授权后不会把历史消息当成「新增」轰炸。
            saveSnapshot()
            return finish(ReminderRunResult(disabledReason = "通知权限未开启", reason = "通知权限未开启"))
        }

        val plan = ReminderPlanner.plan(
            rows = rows,
            doneIds = doneIds,
            plan = ttPlan,
            nowMillis = now,
            settings = settings,
            zone = zone,
            notices = notices,
            handledNoticeIds = handledNoticeIds,
            snapshot = snapshot,
            polls = polls,
            waterRoomKey = water?.roomKey.orEmpty(),
            waterRoom = water?.room,
        )

        // Doze/省电延迟补偿：
        // 1) 从上次运行时间回看（10min..6h），补发上次规划留下、但已过点的事件；
        // 2) 本次规划中已进入 grace/回看窗口的事件照常发送；
        // 3) 去重（ReminderDedupe）保证同一事件不会重复。
        val lastRunAt = runCatching { runStateStore.lastRunAt() }.getOrNull()
        val lookback = ReminderCatchUp.lookbackMillis(
            nowMillis = now,
            lastRunAtMillis = lastRunAt,
            maxLookbackMillis = catchUpMaxMillis,
        )
        val pending = runCatching { pendingStore.load() }.getOrDefault(emptyList())
        val due = ReminderPlanner.filterDue(plan.events, now, graceMillis = lookback)
        val catchUpPending = ReminderPendingCatchUp.dueCatchUp(
            pending = pending,
            nowMillis = now,
            lookbackMillis = lookback,
            keysAlreadyPlanned = plan.events.map { it.key }.toSet(),
        )
        val selected = (catchUpPending + due)
            .sortedBy { it.triggerAtMillis }
            .distinctBy { it.key }
        if (selected.isEmpty()) {
            runCatching {
                pendingStore.save(ReminderPendingCatchUp.pendingForNextRun(plan.events, now))
            }
            saveSnapshot()
            return finish(
                ReminderRunResult(
                    candidates = plan.events.size,
                    reason = if (plan.isEmpty) "没有需要提醒的项" else "当前没有到点提醒",
                ),
            )
        }
        val catchUpKeys = catchUpPending.map { it.key }.toSet()

        notifier.ensureChannels()
        var sent = 0
        var skipped = 0
        var catchUpSent = 0
        selected.forEach { event ->
            val last = dedupeStore.lastSent(event.key)
            if (!ReminderDedupe.shouldNotify(event.key, last, now, zone)) {
                skipped += 1
                return@forEach
            }
            if (notifier.notify(event)) {
                dedupeStore.markSent(event.key, now)
                sent += 1
                if (event.key in catchUpKeys) catchUpSent += 1
            }
        }
        dedupeStore.prune(now - ReminderDedupe.RETENTION_MS)
        runCatching {
            pendingStore.save(ReminderPendingCatchUp.pendingForNextRun(plan.events, now))
        }
        saveSnapshot()
        val reason = when {
            sent > 0 && catchUpSent > 0 -> "已发送 $sent 条（含补发 $catchUpSent 条）"
            sent > 0 -> "已发送 $sent 条"
            skipped > 0 -> "已到点但全部已去重（$skipped 条）"
            else -> "通知发送失败（系统限制/渠道被关闭）"
        }
        return finish(
            ReminderRunResult(
                sent = sent,
                skipped = skipped,
                candidates = plan.events.size,
                reason = reason,
                catchUpEvents = catchUpSent,
            ),
        )
    }

    /** 通知系统诊断快照（设置页展示；缓存/权限/设置/上次运行；不含通知正文）。 */
    suspend fun diagnostics(): ReminderDiagnostics {
        val now = clock()
        val permission = runCatching {
            permissionController.state(PermissionType.NOTIFICATIONS)
        }.getOrDefault(PermissionState.REQUESTABLE)
        val settings = runCatching { settingsStore.current() }.getOrDefault(ReminderSettings.DEFAULT)
        val rows = runCatching { dataProvider.homeworkRows() }.getOrDefault(emptyList())
        val ttPlan = runCatching { dataProvider.timetablePlan(now, zone) }.getOrNull()
        val notices = runCatching { dataProvider.noticesData() }.getOrNull()
        val polls = runCatching { dataProvider.votePolls() }.getOrDefault(emptyList())
        val water = runCatching { dataProvider.watchedWaterRoom() }.getOrNull()
        val lastRunAt = runCatching { runStateStore.lastRunAt() }.getOrNull()
        val lastRunSummary = runCatching { runStateStore.lastRunSummary() }.getOrNull()
        return ReminderDiagnostics(
            checkedAtMillis = now,
            permission = permission,
            settings = settings,
            lastRunAtMillis = lastRunAt,
            lastRunSummary = lastRunSummary,
            homeworkCacheCount = rows.size,
            noticeCacheCount = notices?.auto?.size ?: 0,
            voteCacheCount = polls.size,
            waterReady = water != null,
            timetableReady = ttPlan?.hasData == true,
            catchUpLookbackMillis = ReminderCatchUp.lookbackMillis(now, lastRunAt),
        )
    }

    /**
     * 「发送测试通知」（对齐鸿蒙 `NotifyCenter.sendTest`）：
     * 立即发一条固定内容的本地通知，用于检查系统权限与通知样式。
     */
    suspend fun testNotification(): ReminderTestNotificationResult {
        val permission = runCatching {
            permissionController.state(PermissionType.NOTIFICATIONS)
        }.getOrDefault(PermissionState.REQUESTABLE)
        if (!permission.isGranted) return ReminderTestNotificationResult.PermissionMissing
        notifier.ensureChannels()
        val sent = runCatching {
            notifier.notify(ReminderTestNotification.event(clock()))
        }.getOrDefault(false)
        return if (sent) {
            ReminderTestNotificationResult.Sent
        } else {
            ReminderTestNotificationResult.Failed("通知发送失败（系统限制或通知渠道被关闭）")
        }
    }

    private fun settingsSummaryOf(settings: ReminderSettings): String = buildString {
        append("总开关=").append(if (settings.enabled) "开" else "关")
        append("；模块=").append(settings.enabledCount()).append("/10 开")
        append("；截止提前=").append(ReminderSettings.leadLabel(settings.leadMinutes))
        append("；上课提前=").append(ReminderSettings.leadLabel(settings.classLeadMinutes))
    }
}

/** WorkManager 周期任务：执行一次提醒扫描；异常吞掉（避免重试风暴），调度由周期任务继续保证。 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val result = runCatching { ReminderEngine(applicationContext).runNow() }.getOrNull()
        if (result != null) {
            Log.i(ReminderLog.TAG, ReminderLog.summary(result))
        } else {
            Log.i(ReminderLog.TAG, "[CT_REMINDER] run failed (exception swallowed)")
        }
        return Result.success()
    }
}

/**
 * 开机/应用更新后重新调度（WorkManager 自身会持久化任务，但厂商清理/强制停止后需要重排）。
 *
 * Manifest 声明（Lead 添加）：
 * ```xml
 * <receiver android:name=".data.reminder.ReminderBootReceiver" android:exported="false">
 *   <intent-filter>
 *     <action android:name="android.intent.action.BOOT_COMPLETED" />
 *     <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
 *     <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
 *   </intent-filter>
 * </receiver>
 * ```
 * 并需 Manifest 权限：`RECEIVE_BOOT_COMPLETED`（Lead 已加）。
 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            -> WorkManagerReminderScheduler(context).ensureScheduled()
            else -> Unit
        }
    }
}
