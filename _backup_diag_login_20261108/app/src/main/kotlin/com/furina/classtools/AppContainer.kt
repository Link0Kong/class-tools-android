package com.furina.classtools

import android.content.Context
import com.furina.classtools.data.auth.ApiAuthRepository
import com.furina.classtools.data.auth.AuthRepository
import com.furina.classtools.data.fitrecs.DefaultFitRecsRepository
import com.furina.classtools.data.calendar.AndroidCalendarWriter
import com.furina.classtools.data.calendar.CalendarWriter
import com.furina.classtools.features.feedback.DefaultFeedbackRepository
import com.furina.classtools.features.feedback.FeedbackRepository
import com.furina.classtools.data.fitrecs.FitRecsRepository
import com.furina.classtools.data.homework.DefaultHomeworkRepository
import com.furina.classtools.data.homework.HomeworkRepository
import com.furina.classtools.data.marks.DefaultMarksRepository
import com.furina.classtools.data.marks.MarksRepository
import com.furina.classtools.data.member.ClassMembersRepository
import com.furina.classtools.data.member.DefaultClassMembersRepository
import com.furina.classtools.data.member.FileClassMemberCache
import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.notices.DefaultNoticesRepository
import com.furina.classtools.data.notices.NoticesRepository
import com.furina.classtools.data.power.DefaultPowerRepository
import com.furina.classtools.data.power.PowerRepository
import com.furina.classtools.data.permission.AndroidPermissionController
import com.furina.classtools.data.permission.PermissionController
import com.furina.classtools.data.privacy.CallbackLocalCleaner
import com.furina.classtools.data.privacy.DefaultPrivacyRepository
import com.furina.classtools.data.privacy.PrivacyRepository
import com.furina.classtools.data.privacy.RepositoryPrivacyUserDataAccess
import com.furina.classtools.data.reminder.DataStoreReminderSettingsStore
import com.furina.classtools.data.reminder.ReminderRuntime
import com.furina.classtools.data.reminder.ReminderScheduler
import com.furina.classtools.data.reminder.ReminderSettingsStore
import com.furina.classtools.data.reminder.WorkManagerReminderScheduler
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.session.SessionStore
import com.furina.classtools.data.settings.AppSettingsStore
import com.furina.classtools.data.sync.DefaultUserDataRepository
import com.furina.classtools.data.sync.FileUserDataLocalStore
import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.data.sync.UserdataV6
import com.furina.classtools.data.timetable.DefaultTimetableRepository
import com.furina.classtools.data.timetable.FileTimetableCache
import com.furina.classtools.data.timetable.TimetableRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 极简手工依赖注入容器（不引入 Hilt/Koin，保持构建与运行轻量）。
 *
 * 装配顺序与线程约定见 `docs/api/DATA_LAYER_IMPL.md` §2/§4：
 * - [activeSession] 是 ApiClient 同步读取 token 的唯一来源（启动恢复 / 验证成功 / 退出时更新）；
 * - 401/403 由 ApiClient 回调 onTokenInvalid：清会话 → UI 自动回身份页；
 * - 数据层所有 suspend 方法主线程安全，IO 在数据层内部切线程。
 */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    /** App 级作用域：同步防抖、后台补发使用。 */
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessionStore: SessionStore = SessionStore(appContext)

    @Volatile
    private var activeSession: Session? = null

    /** ApiClient 的 token 提供者与仓库的 sid 来源都读取这里。 */
    val currentSession: Session?
        get() = activeSession

    fun updateActiveSession(session: Session?) {
        activeSession = session
    }

    val apiClient: ApiClient = ApiClient(
        baseUrl = BuildConfig.REST_BASE_URL,
        publishableKey = BuildConfig.PUBLISHABLE_KEY,
        tokenProvider = { activeSession?.takeUnless { it.debug }?.token },
        onTokenInvalid = {
            android.util.Log.w("ClassTools", "onTokenInvalid -> clear session")
            // 统一 401：清会话（不打印 token / 学号），UI 会随 SessionState 回到身份页。
            activeSession = null
            appScope.launch { sessionStore.clear() }
        },
    )

    val authRepository: AuthRepository = ApiAuthRepository(apiClient)

    val classMembersRepository: ClassMembersRepository = DefaultClassMembersRepository(
        api = apiClient,
        cache = FileClassMemberCache(File(appContext.cacheDir, "class_members")),
        currentSid = { activeSession?.sid },
    )

    val userDataRepository: UserDataRepository = DefaultUserDataRepository(
        api = apiClient,
        local = FileUserDataLocalStore(
            directory = File(appContext.filesDir, "userdata"),
            sidProvider = { activeSession?.sid },
        ),
        currentSid = { activeSession?.sid },
        scope = appScope,
    )

    val marksRepository: MarksRepository = DefaultMarksRepository(
        api = apiClient,
        currentSid = { activeSession?.sid },
    )

    val homeworkRepository: HomeworkRepository = DefaultHomeworkRepository(api = apiClient)

    val noticesRepository: NoticesRepository = DefaultNoticesRepository(
        api = apiClient,
        isAdmin = { activeSession?.isAdmin == true },
    )

    val timetableRepository: TimetableRepository = DefaultTimetableRepository(
        api = apiClient,
        cache = FileTimetableCache(File(appContext.cacheDir, "timetable")),
    )

    val powerRepository: PowerRepository = DefaultPowerRepository(api = apiClient)

    /** 体测历史云端读写（E14/E15/E16：最多 2 条、软删 arch、pending 补发、2min TTL）。 */
    val fitRecsRepository: FitRecsRepository = DefaultFitRecsRepository(
        api = apiClient,
        currentSid = { activeSession?.sid },
    )

    /** 反馈建议（E19/E20/E21：GET 30s TTL、POST、管理员 PATCH done）。 */
    val feedbackRepository: FeedbackRepository = DefaultFeedbackRepository(
        api = apiClient,
        currentSid = { activeSession?.sid },
    )

    /** 数据与隐私：导出我的数据（脱敏/白名单）、清除云端与本机数据。 */
    val privacyRepository: PrivacyRepository = DefaultPrivacyRepository(
        api = apiClient,
        marksRepository = marksRepository,
        userDataAccess = RepositoryPrivacyUserDataAccess(userDataRepository),
        currentSid = { activeSession?.sid },
        currentSession = { activeSession },
        localCleaner = CallbackLocalCleaner(
            listOf(
                CallbackLocalCleaner.Step("班级名单缓存") { classMembersRepository.clear() },
                CallbackLocalCleaner.Step("userdata 本机基线") {
                    userDataRepository.updateCurrent(UserdataV6.sectionsOf(UserdataV6.empty()))
                },
            ),
        ),
    )

    /** 本机外观偏好（壁纸取色等）：设备级设置，不进云端 userdata。 */
    val appSettings: AppSettingsStore = AppSettingsStore(appContext)

    /** 运行时权限（通知/日历）：四态判定 + 请求 + 跳系统设置。 */
    val permissionController: PermissionController = AndroidPermissionController(appContext)

    /** 本机提醒设置（设备偏好，不进云端；作业/课表提醒开关与提前量）。 */
    val reminderSettingsStore: ReminderSettingsStore = DataStoreReminderSettingsStore(appContext)

    /** 写系统日历：有权限直接插入；无权限返回 NeedsPermission 由 UI 走 ACTION_INSERT 兜底。 */
    val calendarWriter: CalendarWriter = AndroidCalendarWriter(
        context = appContext,
        permissionController = permissionController,
    )

    /** 本地通知提醒调度（WorkManager 15min 周期，幂等）。 */
    val reminderScheduler: ReminderScheduler = WorkManagerReminderScheduler(appContext)

    init {
        runCatching { reminderScheduler.ensureScheduled() }
    }

    /** 用户修改提醒设置后重建调度（或关闭时取消）。 */
    fun rescheduleReminders() {
        runCatching { ReminderRuntime.reschedule(appContext) }
    }

    fun cancelReminders() {
        runCatching { ReminderRuntime.cancel(appContext) }
    }

    /**
     * 登录后预热提醒所需的本机缓存（作业/课表/marks），保证次日提醒的数据可用。
     * 只读网络拉取，幂等（各仓库有 TTL）。
     */
    fun warmReminderCaches() {
        appScope.launch {
            runCatching { homeworkRepository.load(force = false) }
            runCatching { timetableRepository.load(force = false) }
            runCatching { marksRepository.refresh(force = false) }
        }
    }

    /** 退出登录：清内存会话、名单缓存（本机业务数据按账号隔离，下次登录同账号仍可用）。 */
    suspend fun signOut() {
        activeSession = null
        sessionStore.clear()
        classMembersRepository.clear()
    }

    /** 云同步开关（设置页）。 */
    suspend fun setSyncEnabled(enabled: Boolean) = userDataRepository.setSyncEnabled(enabled)

    suspend fun isSyncEnabled(): Boolean = userDataRepository.syncEnabled()

    /** 清除本机可重建缓存（名单等）；不动云端数据。 */
    suspend fun clearLocalCaches() {
        classMembersRepository.clear()
    }

    /** 刷新主数据（回前台 / 进入主页调用）。失败静默，由页面展示缓存。 */
    suspend fun refreshCoreData() {
        classMembersRepository.load(force = false)
        marksRepository.refresh(force = false)
        userDataRepository.pull(force = false)
    }
}
