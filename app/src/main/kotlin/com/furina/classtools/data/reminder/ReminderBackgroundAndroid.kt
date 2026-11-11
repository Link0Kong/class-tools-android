package com.furina.classtools.data.reminder

import android.content.Context
import android.os.PowerManager
import com.furina.classtools.AppContainer
import com.furina.classtools.ClassToolsApp
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.notices.DefaultNoticesRepository
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeSigRow
import com.furina.classtools.data.notices.NoticesLoadResult
import com.furina.classtools.data.notices.NoticesRepository
import com.furina.classtools.data.permission.AndroidPermissionController
import com.furina.classtools.data.permission.PermissionState
import com.furina.classtools.data.permission.PermissionType
import com.furina.classtools.data.session.SessionState
import kotlinx.coroutines.flow.first

/**
 * 后台通知轮询的 Android 装配层（v3.7）。
 *
 * 关键点（对应"App 被杀后收不到通知"的根因修复）：
 * 1. **恢复身份 token**：`AppContainer.activeSession` 只由 UI 赋值；Worker 冷启动进程时先读
 *    `SessionStore`（DataStore）恢复会话，否则 `X-Class-Token` 为空、所有数据请求都会失败。
 * 2. **真正联网**：拉通知轻量签名（约 2KB）→ 变化才拉整表；作业仓库 `load()` 也会在网络约束下刷新。
 * 3. **只读缓存不再依赖 UI**：进程冷启动时内存缓存为空，这里主动拉一次（满足 TTL 时复用）。
 * 4. **静默失败**：无 token / 离线 / 401 一律记录诊断原因，不抛异常、不打扰用户。
 */
class SharedPrefsBackgroundPollStateStore(context: Context) : BackgroundPollStateStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun load(): BackgroundPollState? =
        BackgroundPollCodec.decode(prefs.getString(KEY_STATE, null))

    override suspend fun save(state: BackgroundPollState) {
        prefs.edit()
            .putString(KEY_STATE, BackgroundPollCodec.encode(state))
            .apply()
    }

    companion object {
        const val PREFS_NAME: String = "ct_reminder_bg_poll"
        const val KEY_STATE: String = "state"

        /** 后台轮询 Worker 唯一名（与 [WorkManagerReminderScheduler.UNIQUE_WORK_NAME] 一致：周期任务即后台轮询任务）。 */
        const val WORK_NAME: String = WorkManagerReminderScheduler.UNIQUE_WORK_NAME
    }
}

/** 生产通知网关：签名走 ApiClient（E8），整表复用 [NoticesRepository]（带签名/TTL/解析）。 */
class ContainerBackgroundNoticeGateway(
    private val container: AppContainer,
) : BackgroundNoticeGateway {

    override suspend fun fetchSignatureRows(): List<NoticeSigRow>? =
        when (val result = container.apiClient.get(DefaultNoticesRepository.LITE_PATH)) {
            is ApiResult.Failure -> null
            is ApiResult.Success -> DefaultNoticesRepository.parseSigRows(NetJson, result.value)
        }

    override suspend fun fetchEntries(): List<NoticeEntry>? =
        when (val result = container.noticesRepository.load(force = true)) {
            is NoticesLoadResult.Success -> result.data.auto
            is NoticesLoadResult.Failure -> null
        }
}

/** 后台轮询 + 既有提醒引擎的组合执行结果（Worker 日志/诊断用）。 */
data class ReminderBackgroundPipelineResult(
    val sessionReady: Boolean = false,
    val poll: BackgroundPollOutcome? = null,
    val reminder: ReminderRunResult? = null,
    val homeworkLoaded: Boolean = false,
    val noticeCacheSeeded: Boolean = false,
    val reason: String = "",
) {
    fun logSummary(): String = buildString {
        append("[CT_BG] session=").append(sessionReady)
        poll?.let { append(" ").append(it.logSummary()) }
        reminder?.let { append(" engine[").append(it.logSummary()).append("]") }
        append(" homeworkLoaded=").append(homeworkLoaded)
        append(" noticeCacheSeeded=").append(noticeCacheSeeded)
        if (reason.isNotBlank()) append(" reason=").append(reason)
    }
}

/** Worker 编排：恢复会话 → 后台轮询新通知 → 刷新作业缓存 → 既有提醒引擎（作业/课表/通知到点提醒）。 */
object ReminderBackgroundPipeline {

    suspend fun run(context: Context): ReminderBackgroundPipelineResult {
        val appContext = context.applicationContext
        val app = appContext as? ClassToolsApp
            ?: return ReminderBackgroundPipelineResult(reason = "非 ClassToolsApp 进程，跳过后台提醒")
        val container = app.container
        val now = System.currentTimeMillis()
        val stateStore = SharedPrefsBackgroundPollStateStore(appContext)

        // 1) 后台恢复身份（UI 未运行时的 token 唯一来源）。
        val sessionState = runCatching { container.sessionStore.state.first() }.getOrNull()
        val session = (sessionState as? SessionState.SignedIn)?.session
        if (session == null || session.debug || !session.isLikelyValid()) {
            val reason = when {
                session == null -> "未登录，跳过后台提醒"
                session.debug -> "本地预览会话，跳过后台提醒"
                else -> "身份 token 已过期（>29 天），跳过后台提醒"
            }
            runCatching {
                val previous = stateStore.load() ?: BackgroundPollState()
                stateStore.save(
                    previous.copy(
                        lastPollAtMillis = now,
                        lastResult = reason,
                        pollCount = previous.pollCount + 1L,
                    ),
                )
            }
            return ReminderBackgroundPipelineResult(reason = reason)
        }
        container.updateActiveSession(session)
        runCatching { ReminderChannelInstaller.ensureCreated(appContext) }

        // 2) 新通知轮询（幂等；失败静默）。
        val runner = ReminderBackgroundRunner(
            gateway = ContainerBackgroundNoticeGateway(container),
            stateStore = stateStore,
            settingsStore = container.reminderSettingsStore,
            notifier = AndroidReminderNotifier(appContext),
            permissionController = container.permissionController,
            clock = System::currentTimeMillis,
        )
        val poll = runCatching { runner.run() }.getOrNull()

        // 3) 刷新作业缓存（进程冷启动时内存为空；load() 自带 TTL，在线才拉）。
        val homeworkLoaded = runCatching {
            container.homeworkRepository.load(force = false) is com.furina.classtools.data.homework.HomeworkLoadResult.Success
        }.getOrDefault(false)

        // 4) 通知缓存为空（冷进程）时补一次整表；签名未变时 runner 不拉整表，这里保证到点提醒可用。
        val noticeCacheSeeded = runCatching {
            if (container.noticesRepository.cached() == null) {
                container.noticesRepository.load(force = false) is NoticesLoadResult.Success
            } else {
                false
            }
        }.getOrDefault(false)

        // 5) 既有提醒引擎（作业汇总/截止、课表下一节、通知到点提醒；内部自带权限/开关/去重检查）。
        val reminder = runCatching { ReminderEngine(appContext).runNow() }.getOrNull()
        return ReminderBackgroundPipelineResult(
            sessionReady = true,
            poll = poll,
            reminder = reminder,
            homeworkLoaded = homeworkLoaded,
            noticeCacheSeeded = noticeCacheSeeded,
            reason = poll?.reason.orEmpty(),
        )
    }
}

/** 后台提醒诊断读取（诊断页用；只读，不联网）。 */
object BackgroundReminderDiagnosticsReader {

    suspend fun read(context: Context): BackgroundReminderDiagnostics {
        val appContext = context.applicationContext
        val state = runCatching { SharedPrefsBackgroundPollStateStore(appContext).load() }.getOrNull()
        val permission = runCatching {
            AndroidPermissionController(appContext).state(PermissionType.NOTIFICATIONS)
        }.getOrDefault(PermissionState.REQUESTABLE)
        val batteryIgnored = runCatching {
            (appContext.getSystemService(PowerManager::class.java))
                ?.isIgnoringBatteryOptimizations(appContext.packageName) == true
        }.getOrDefault(false)
        return BackgroundReminderDiagnostics(
            lastPollAtMillis = state?.lastPollAtMillis?.takeIf { it > 0L },
            lastSuccessAtMillis = state?.lastSuccessAtMillis?.takeIf { it > 0L },
            lastNewCount = state?.lastNewCount ?: 0,
            lastResult = state?.lastResult.orEmpty(),
            pollCount = state?.pollCount ?: 0L,
            baselineDone = state?.baselineDone == true,
            permission = permission,
            notificationsEnabled = ReminderChannelInstaller.areNotificationsEnabled(appContext),
            newNoticeChannelImportance = ReminderChannelInstaller.importanceOf(
                appContext,
                ReminderChannels.NEW_NOTICE_ID,
            ),
            batteryOptimizationIgnored = batteryIgnored,
            exactAlarmAllowed = ReminderExactAlarmSupport.canScheduleExactAlarms(appContext),
            periodicIntervalMinutes = ReminderBackgroundLogic.DEFAULT_INTERVAL_MINUTES,
        )
    }
}

/** UI 便捷入口（「我的 → 权限与提醒」诊断卡 / 设置页）。 */
object ReminderBackgroundActions {

    suspend fun diagnostics(context: Context): BackgroundReminderDiagnostics =
        BackgroundReminderDiagnosticsReader.read(context)

    /** 立即在后台执行一次完整检查（WorkManager OneTime，与设置变更同一入口）。 */
    fun runNow(context: Context) = ReminderRuntime.runNow(context)
}
