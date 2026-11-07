package com.furina.classtools

import android.content.Context
import com.furina.classtools.data.auth.ApiAuthRepository
import com.furina.classtools.data.auth.AuthRepository
import com.furina.classtools.data.fitrecs.DefaultFitRecsRepository
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
import com.furina.classtools.data.privacy.CallbackLocalCleaner
import com.furina.classtools.data.privacy.DefaultPrivacyRepository
import com.furina.classtools.data.privacy.PrivacyRepository
import com.furina.classtools.data.privacy.RepositoryPrivacyUserDataAccess
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
