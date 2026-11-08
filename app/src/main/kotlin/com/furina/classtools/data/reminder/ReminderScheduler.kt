package com.furina.classtools.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.furina.classtools.AppContainer
import com.furina.classtools.ClassToolsApp
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.notices.NoticesData
import com.furina.classtools.data.permission.AndroidPermissionController
import com.furina.classtools.data.permission.PermissionController
import com.furina.classtools.data.permission.PermissionType
import com.furina.classtools.data.timetable.TtCalendar
import com.furina.classtools.data.timetable.TtPlan
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

    /** 用户开启提醒/修改设置后调用：重建周期调度并立即补跑一次（可选）。 */
    fun reschedule(context: Context) {
        WorkManagerReminderScheduler(context).ensureScheduled()
    }

    /** 用户关闭提醒时调用。 */
    fun cancel(context: Context) {
        WorkManagerReminderScheduler(context).cancelAll()
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
}

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
}

/**
 * 提醒执行引擎（一次扫描）：读设置 → 权限/开关检查 → 本机缓存规划 → 去重 → 发通知。
 * 不联网（作业/课表用本机缓存；缓存为空则不提醒），可在 IO/Worker 线程调用。
 */
class ReminderEngine(
    context: Context,
    private val settingsStore: ReminderSettingsStore = DataStoreReminderSettingsStore(context),
    private val dedupeStore: ReminderDedupeStore = SharedPrefsReminderDedupeStore(context),
    private val notifier: ReminderNotifier = AndroidReminderNotifier(context),
    private val permissionController: PermissionController = AndroidPermissionController(context),
    private val dataProvider: ReminderDataProvider = ContainerReminderDataProvider(context),
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun runOnce(): ReminderRunResult {
        val settings = runCatching { settingsStore.current() }.getOrDefault(ReminderSettings.DEFAULT)
        if (!settings.enabled) return ReminderRunResult(disabledReason = "提醒总开关已关闭")
        if (!settings.anySectionEnabled) return ReminderRunResult(disabledReason = "作业/课表/通知提醒均已关闭")
        if (!permissionController.state(PermissionType.NOTIFICATIONS).isGranted) {
            return ReminderRunResult(disabledReason = "通知权限未开启")
        }

        val now = clock()
        val rows = dataProvider.homeworkRows()
        val doneIds = dataProvider.homeworkDoneIds()
        val ttPlan = runCatching { dataProvider.timetablePlan(now, zone) }.getOrNull()
        val notices = runCatching { dataProvider.noticesData() }.getOrNull()
        val handledNoticeIds = runCatching { dataProvider.handledNoticeIds() }.getOrDefault(emptySet())
        val plan = ReminderPlanner.plan(
            rows = rows,
            doneIds = doneIds,
            plan = ttPlan,
            nowMillis = now,
            settings = settings,
            zone = zone,
            notices = notices,
            handledNoticeIds = handledNoticeIds,
        )
        if (plan.isEmpty) return ReminderRunResult(candidates = 0)

        val due = ReminderPlanner.filterDue(plan.events, now)
        if (due.isEmpty()) return ReminderRunResult(candidates = plan.events.size)

        notifier.ensureChannels()
        var sent = 0
        var skipped = 0
        due.forEach { event ->
            val last = dedupeStore.lastSent(event.key)
            if (!ReminderDedupe.shouldNotify(event.key, last, now, zone)) {
                skipped += 1
                return@forEach
            }
            if (notifier.notify(event)) {
                dedupeStore.markSent(event.key, now)
                sent += 1
            }
        }
        dedupeStore.prune(now - ReminderDedupe.RETENTION_MS)
        return ReminderRunResult(sent = sent, skipped = skipped, candidates = plan.events.size)
    }
}

/** WorkManager 周期任务：执行一次提醒扫描；异常吞掉（避免重试风暴），调度由周期任务继续保证。 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        ReminderEngine(applicationContext).runOnce()
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.success() },
    )
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
