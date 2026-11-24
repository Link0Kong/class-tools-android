package com.furina.classtools

import android.content.Context
import com.furina.classtools.data.account.EcsOppoAccountApi
import com.furina.classtools.data.account.HeytapOppoOAuthSdk
import com.furina.classtools.data.account.HonorAccountConfig
import com.furina.classtools.data.account.HonorAccountDeviceSupport
import com.furina.classtools.data.account.HonorAccountManager
import com.furina.classtools.data.account.HonorIdAuthSdk
import com.furina.classtools.data.account.OppoAccountConfig
import com.furina.classtools.data.account.OppoAccountDeviceSupport
import com.furina.classtools.data.account.OppoAccountManager
import com.furina.classtools.data.auth.ApiAuthRepository
import com.furina.classtools.data.auth.AuthRepository
import com.furina.classtools.data.background.BackgroundStore
import com.furina.classtools.data.diag.AndroidDeviceProbe
import com.furina.classtools.data.diag.ClientDiagPendingStore
import com.furina.classtools.data.diag.ClientDiagReporter
import com.furina.classtools.data.diag.CrashDiagRecorder
import com.furina.classtools.data.diag.DefaultDiagRepository
import com.furina.classtools.data.diag.DiagRepository
import com.furina.classtools.data.diag.FileClientDiagPendingStore
import com.furina.classtools.data.diag.installCrashHandler
import com.furina.classtools.data.draw.SharedPreferencesDrawLastResultStore
import com.furina.classtools.data.draw.DrawProofRepository
import com.furina.classtools.data.fitrecs.DefaultFitRecsRepository
import com.furina.classtools.data.notify.LiveUpdateNotifier
import com.furina.classtools.data.calendar.AndroidCalendarWriter
import com.furina.classtools.data.cloudspace.CloudSpaceApi
import com.furina.classtools.data.cloudspace.CloudSpaceDefaults
import com.furina.classtools.data.cloudspace.CloudSpaceRepository
import com.furina.classtools.data.calendar.CalendarWriter
import com.furina.classtools.features.feedback.DefaultFeedbackRepository
import com.furina.classtools.features.feedback.FeedbackRepository
import com.furina.classtools.data.fitrecs.FitRecsRepository
import com.furina.classtools.data.homework.DefaultHomeworkRepository
import com.furina.classtools.data.homework.HomeworkRepository
import com.furina.classtools.data.marks.DefaultMarksRepository
import com.furina.classtools.data.marks.MarksRepository
import com.furina.classtools.data.member.ClassLeagueRepository
import com.furina.classtools.data.member.ClassMembersRepository
import com.furina.classtools.data.member.DefaultClassLeagueRepository
import com.furina.classtools.data.member.DefaultClassMembersRepository
import com.furina.classtools.data.member.FileClassMemberCache
import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.scan.QrLoginRepository
import com.furina.classtools.data.network.OkHttpTransport
import com.furina.classtools.data.notices.DefaultNoticesRepository
import com.furina.classtools.data.notices.NoticesRepository
import com.furina.classtools.data.power.DefaultPowerDirectoryRepository
import com.furina.classtools.data.power.DefaultPowerRepository
import com.furina.classtools.data.power.PowerDirectoryRepository
import com.furina.classtools.data.power.PowerRepository
import com.furina.classtools.data.ppt.AndroidNetworkQualityProvider
import com.furina.classtools.data.ppt.AndroidPptFileDownloader
import com.furina.classtools.data.ppt.DefaultPptRepository
import com.furina.classtools.data.ppt.OkHttpPptImageLoader
import com.furina.classtools.data.ppt.PptImageLoader
import com.furina.classtools.data.ppt.PptFileDownloader
import com.furina.classtools.data.ppt.PptUploadPhase
import com.furina.classtools.data.ppt.PptLocalStore
import com.furina.classtools.data.ppt.PptNetworkQualityProvider
import com.furina.classtools.data.ppt.PptRepository
import com.furina.classtools.data.push.ApiPushTokenUploader
import com.furina.classtools.data.push.HeytapOppoPushSdk
import com.furina.classtools.data.push.OppoPushConfig
import com.furina.classtools.data.push.OppoPushManager
import com.furina.classtools.data.push.PrefsOppoPushStore
import com.furina.classtools.data.push.PrefsVivoPushStore
import com.furina.classtools.data.push.VivoPushConfig
import com.furina.classtools.data.push.VivoPushManager
import com.furina.classtools.data.push.VivoPushSdkImpl
import com.furina.classtools.data.push.PushChannels
import com.furina.classtools.data.vote.DefaultVoteRepository
import com.furina.classtools.data.vote.VoteRepository
import com.furina.classtools.data.visits.VisitDeviceId
import com.furina.classtools.data.visits.VisitReporter
import com.furina.classtools.data.visits.VisitsRepository
import com.furina.classtools.data.visits.androidDeviceLabel
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
import com.furina.classtools.data.remoteconfig.RemoteConfigRepository
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.session.SessionStore
import com.furina.classtools.data.settings.AppSettingsStore
import com.furina.classtools.data.sync.DefaultUserDataRepository
import com.furina.classtools.data.sync.FileUserDataLocalStore
import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.data.sync.UserdataV6
import com.furina.classtools.data.timetable.DefaultTimetableLoginRepository
import com.furina.classtools.data.timetable.DefaultTimetableRepository
import com.furina.classtools.data.timetable.FileTimetableCache
import com.furina.classtools.data.timetable.TimetableLoginRepository
import com.furina.classtools.data.timetable.TimetablePersonalStore
import com.furina.classtools.data.timetable.TimetableRepository
import com.furina.classtools.data.timetable.UserDataTimetablePersonalStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    val backgroundStore: BackgroundStore by lazy { BackgroundStore(appContext) }
    val drawLastResultStore by lazy { SharedPreferencesDrawLastResultStore(appContext) }

    private val sessionPreparation = Mutex()
    @Volatile
    private var preparedSession: Session? = null

    private val remoteConfigRefresh = appScope.launch(start = CoroutineStart.LAZY) {
        RemoteConfigRepository().fetch()?.let { config ->
            runCatching { appSettings.applyRemoteConfig(config) }
        }
    }

    fun refreshRemoteConfig() {
        remoteConfigRefresh.start()
    }

    @Volatile
    private var activeSession: Session? = null

    /** ApiClient 的 token 提供者与仓库的 sid 来源都读取这里。 */
    val currentSession: Session?
        get() = activeSession

    internal fun isSessionPrepared(session: Session): Boolean =
        preparedSession == session && activeSession == session

    /** 本机设置就绪后即可进入页面，网络刷新由前台生命周期调度。 */
    internal suspend fun prepareSession(session: Session) = sessionPreparation.withLock {
        if (isSessionPrepared(session)) return@withLock
        updateActiveSession(session)
        userDataRepository.hydrateFromLocal()
        preparedSession = session
        warmReminderCaches()
    }

    internal fun updateActiveSession(session: Session?) {
        val previousSid = activeSession?.sid
        activeSession = session
        // 启动/登录后在线时补报本机待发的崩溃/诊断队列（契约 §2：启动后补报）。
        if (session != null && !session.debug && previousSid != session.sid) {
            appScope.launch { runCatching { clientDiagReporter.drainPending() } }
        }
        // 访问上报（契约 §3 安卓：启动恢复/登录成功后 best-effort 一次，client=android）。
        // 失败静默（reportOnce 内部吞掉），不影响登录与首屏；debug 会话由 reporter 自行跳过。
        if (session != null && previousSid != session.sid) {
            appScope.launch { runCatching { visitReporter.reportOnce() } }
        }
        // OPPO PUSH：登录 / 切换账号 / token 恢复后，用缓存 RegistrationID 重新上传到当前账号
        // （未登录时 manager 内部跳过，等下次 onSessionChanged；重复上传由幂等门跳过）。
        if (previousSid != session?.sid) {
            if (session != null && !session.debug) {
                runCatching { oppoPushManager.onSessionChanged() }
                runCatching { vivoPushManager.onSessionChanged() }
            }
            // 云空间会话绑定班级身份；切号/退出时丢弃内存会话与游标，避免串号。
            if (cloudSpace.isInitialized()) cloudSpaceRepository.reset()
            // 账号绑定列表按学号区分：切号/退出时丢弃内存列表，避免显示上一个账号的绑定。
            if (oppoAccount.isInitialized()) oppoAccountManager.clearBindings()
            if (honorAccount.isInitialized()) honorAccountManager.clearBindings()
        }
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

    /** 扫一扫 → 抽签存证核验（`rpc/draw_record_get`，只读）。 */
    val drawProofRepository: DrawProofRepository by lazy { DrawProofRepository(apiClient) }

    /** 扫一扫 → 电脑网页扫码登录（ECS `/account/qr/authorize`，App 不接收 token）。 */
    val qrLoginRepository: QrLoginRepository by lazy {
        QrLoginRepository(OkHttpTransport(), BuildConfig.ACCOUNT_BASE_URL)
    }

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
        syncAllowed = { activeSession?.debug == false },
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

    /** 个人课表存储：userdata v6 `tt.ttLocal`（课表登录产物，跨端同步）。 */
    val timetablePersonalStore: TimetablePersonalStore = UserDataTimetablePersonalStore(
        userDataRepository = userDataRepository,
    )

    /** 课表仓库：个人 ttLocal > 公共 site_assets 快照 > 本机缓存。 */
    val timetableRepository: TimetableRepository = DefaultTimetableRepository(
        api = apiClient,
        cache = FileTimetableCache(File(appContext.cacheDir, "timetable")),
        personalStore = timetablePersonalStore,
    )

    /** 课表登录（rpc/tt_login_enqueue + rpc/tt_login_status；密码只在请求体内存中出现一次）。 */
    val timetableLoginRepository: TimetableLoginRepository = DefaultTimetableLoginRepository(api = apiClient)

    // ---------------------------------------------------------------- 诊断 v2.1

    /** 本机设备检查采集（系统版本/屏幕/内存/存储/网络/WebView；v2.1 不再采集品牌/型号/语言）。 */
    val deviceProbe: AndroidDeviceProbe = AndroidDeviceProbe(
        context = appContext,
        appVersion = BuildConfig.VERSION_NAME,
    )

    /** 诊断数据仓库：service_health / diag_analysis / repair_tasks / rpc/diag_agent_info / client_diag。 */
    val diagRepository: DiagRepository = DefaultDiagRepository(
        api = apiClient,
        currentSid = { activeSession?.sid },
    )

    private val diagPendingStore: ClientDiagPendingStore =
        FileClientDiagPendingStore(File(appContext.filesDir, "diag"))

    /** client_diag 上报器（fingerprint 10 分钟去重 + 每批 ≤3 条 + 失败/崩溃落本机队列补报）。 */
    val clientDiagReporter: ClientDiagReporter = ClientDiagReporter(
        repository = diagRepository,
        pending = diagPendingStore,
    )

    // ---------------------------------------------------------------- 访问上报

    /**
     * 本机访问去重设备码（私有 SharedPreferences；16 位 hex，不含任何个人信息）。
     * 懒加载：启动首帧不需要读 SharedPreferences（首次上报在后台作用域触发）。
     */
    private val visitDeviceId: VisitDeviceId by lazy { VisitDeviceId.from(appContext) }

    /**
     * 访问上报（契约 `web/统计多端_详细统计_契约_20261120.md` §1/§5 安卓）：
     * `POST visits` 一行（`client=android`、`id=<sid>|<slot>|android`、小时去重），
     * 并随行写 `ver=BuildConfig.VERSION_NAME`、`dev=<品牌 机型 · Android 版本>`（截断 48）。
     * 由 [updateActiveSession] 在会话恢复/登录成功后 best-effort 触发；失败静默。
     */
    val visitReporter: VisitReporter by lazy {
        VisitReporter(
            api = apiClient,
            currentSession = { activeSession },
            deviceIdProvider = { visitDeviceId.get() },
            clock = { System.currentTimeMillis() },
            versionProvider = { BuildConfig.VERSION_NAME },
            deviceLabelProvider = { androidDeviceLabel() },
        )
    }

    /** 访问统计页读取（仅管理员；RLS 再校验 token）。 */
    val visitsRepository: VisitsRepository by lazy { VisitsRepository(apiClient) }

    /**
     * 回前台补报访问（同一账号同一小时只会写一行，reporter 内部去重）：
     * App 进程长期存活跨过整点时，仅靠登录/恢复会话不会产生新行；MainActivity.onResume 调用。
     */
    fun reportVisitOnResume() {
        if (activeSession == null) return
        appScope.launch { runCatching { visitReporter.reportOnce() } }
    }

    /** 崩溃设备信息快照（启动时算一次；崩溃线程不做系统查询；v2.1 最小化白名单 JSON）。 */
    private val crashDeviceSnapshot: kotlinx.serialization.json.JsonObject by lazy {
        runCatching { deviceProbe.collectBase().deviceJson() }
            .getOrDefault(kotlinx.serialization.json.JsonObject(emptyMap()))
    }

    /** 未捕获异常记录器（只落脱敏行到本机队列，不打印堆栈/不发网络）。 */
    val crashDiagRecorder: CrashDiagRecorder = CrashDiagRecorder(
        pending = diagPendingStore,
        appVersion = BuildConfig.VERSION_NAME,
        sidProvider = { activeSession?.sid },
        deviceProvider = { crashDeviceSnapshot },
    )

    /** 安装 Application 级全局 uncaught exception handler（幂等由调用方控制）。 */
    fun installGlobalCrashHandler() {
        runCatching { installCrashHandler(crashDiagRecorder) }
    }

    /** 补报本机待发诊断/崩溃队列（在线时调用；失败保留队列下次再试）。 */
    fun drainPendingDiagReports() {
        appScope.launch { runCatching { clientDiagReporter.drainPending() } }
    }

    val powerRepository: PowerRepository = DefaultPowerRepository(api = apiClient)

    /**
     * 宿舍目录仓库（E21）：读云端 `power` 表，不在客户端硬编码真实房号。
     *
     * 2026-11-21 修复：此前目录是硬编码常量，一次脱敏把房号换成虚构值后真实宿舍全部无法显示。
     */
    val powerDirectoryRepository: PowerDirectoryRepository =
        DefaultPowerDirectoryRepository(api = apiClient)

    /** 投票仓库（契约 web/vote/CONTRACT.md；模型不含 sid）。 */
    /** 懒加载：启动首帧不需要投票仓库。 */
    val voteRepository: VoteRepository by lazy { DefaultVoteRepository(
        api = apiClient,
        currentSid = { activeSession?.sid },
    ) }

    /** 临时云空间（ECS drop 服务）：同账号跨设备；token 只放 X-Device-Id 请求头。 */
    private val cloudSpace = lazy {
        CloudSpaceRepository(
            api = CloudSpaceApi(
                baseUrl = CloudSpaceDefaults.BASE_URL,
                tokenProvider = { activeSession?.takeUnless { it.debug }?.token },
            ),
        )
    }

    /** 团员名单仓库（RPC class_league；仅姓名/座号，无 sid）。 */
    /** 懒加载：启动首帧不需要团员名单仓库。 */
    val classLeagueRepository: ClassLeagueRepository by lazy { DefaultClassLeagueRepository(
        api = apiClient,
        currentSid = { activeSession?.sid },
    ) }

    /** 演示（PPT）仓库：列表/播放清单/分片上传/软删/重试 + 转换机心跳（web/ppt/CONTRACT.md §4）。 */
    /** 懒加载：启动首帧不需要 PPT 仓库。 */
    val pptRepository: PptRepository by lazy {
        DefaultPptRepository(
            api = apiClient,
            currentSession = { activeSession },
        ).also { repository ->
            // Live Updates：PPT 上传/转换进度以系统实时活动（Android 16 实时更新 / ColorOS 流体云）呈现。
            appScope.launch {
                repository.uploadProgress.collect { progress ->
                    when (progress.phase) {
                        PptUploadPhase.DONE -> {
                            LiveUpdateNotifier.cancelPptUpload(appContext)
                            LiveUpdateNotifier.postPptFinished(appContext, success = true, message = progress.message)
                        }

                        PptUploadPhase.FAILED -> {
                            LiveUpdateNotifier.cancelPptUpload(appContext)
                            LiveUpdateNotifier.postPptFinished(appContext, success = false, message = progress.message)
                        }

                        PptUploadPhase.IDLE,
                        PptUploadPhase.CANCELLED,
                        -> LiveUpdateNotifier.cancelPptUpload(appContext)

                        else -> LiveUpdateNotifier.postPptUpload(appContext, progress)
                    }
                }
            }
        }
    }

    /** 演示图片加载器：自建 OkHttp + BitmapFactory + LRU（项目无 Coil，不新增依赖）。 */
    /** 懒加载：启动首帧不需要图片加载器（内部会创建 OkHttp 客户端）。 */
    val pptImageLoader: PptImageLoader by lazy { OkHttpPptImageLoader() }

    /** 演示本机偏好：续播位置（按 deckId，7 天）+ 「原样/分步」模式偏好（契约 §11.3.1/§11.3.4）。 */
    val pptLocalStore: PptLocalStore by lazy { PptLocalStore(appContext) }

    /** 演示默认模式判定用的网络质量（ConnectivityManager：WiFi/以太网/计量/慢网/Data Saver）。 */
    val pptNetworkQuality: PptNetworkQualityProvider by lazy { AndroidNetworkQualityProvider(appContext) }

    /** v2.0 演示文件下载：DownloadManager → 应用外部下载目录；FileProvider 打开/分享。 */
    val pptFileDownloader: PptFileDownloader by lazy { AndroidPptFileDownloader(appContext) }

    /** 体测历史云端读写（E14/E15/E16：最多 2 条、软删 arch、pending 补发、2min TTL）。 */
    /** 懒加载：启动首帧不需要体测仓库。 */
    val fitRecsRepository: FitRecsRepository by lazy { DefaultFitRecsRepository(
        api = apiClient,
        currentSid = { activeSession?.sid },
    ) }

    /** 反馈建议（E19/E20/E21：GET 30s TTL、POST、管理员 PATCH done）。 */
    /** 懒加载：启动首帧不需要反馈仓库。 */
    val feedbackRepository: FeedbackRepository by lazy { DefaultFeedbackRepository(
        api = apiClient,
        currentSid = { activeSession?.sid },
    ) }

    /** 数据与隐私：导出我的数据（脱敏/白名单）、清除云端与本机数据。 */
    /** 懒加载：启动首帧不需要隐私仓库。 */
    val privacyRepository: PrivacyRepository by lazy { DefaultPrivacyRepository(
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
    ) }

    /** 本机外观偏好（壁纸取色等）：设备级设置，不进云端 userdata。 */
    val appSettings: AppSettingsStore = AppSettingsStore(appContext)

    /** 运行时权限（通知/日历）：四态判定 + 请求 + 跳系统设置。 */
    val permissionController: PermissionController = AndroidPermissionController(appContext)

    /** 本机提醒设置（设备偏好，不进云端；作业/课表提醒开关与提前量）。 */
    val reminderSettingsStore: ReminderSettingsStore = DataStoreReminderSettingsStore(appContext)

    /** 写系统日历：有权限直接插入；无权限返回 NeedsPermission 由 UI 走 ACTION_INSERT 兜底。 */
    /** 懒加载：启动首帧不需要日历写入。 */
    val calendarWriter: CalendarWriter by lazy { AndroidCalendarWriter(
        context = appContext,
        permissionController = permissionController,
    ) }

    /** 本地通知提醒调度（WorkManager 15min 周期，幂等）。 */
    val reminderScheduler: ReminderScheduler by lazy { WorkManagerReminderScheduler(appContext) }

    /**
     * OPPO PUSH（官方 SDK V4.0.0）：
     * - 密钥来自 `app/oppo.properties`（构建期注入 BuildConfig；空 = 未配置 → 安全跳过）；
     * - [ClassToolsApp] 在 onCreate 调 [OppoPushManager.onAppCreated]（创建全部渠道 + init/register）；
     * - 注册成功后 token 上传云端 `push_tokens`；登录/切号时 [OppoPushManager.onSessionChanged] 补传。
     * 懒加载：未进入「我的」页/未注册时不额外创建对象（Application.onCreate 会首次触发）。
     */
    val oppoPushManager: OppoPushManager by lazy {
        OppoPushManager(
            config = OppoPushConfig.of(
                appKey = BuildConfig.OPPO_PUSH_APP_KEY,
                appSecret = BuildConfig.OPPO_PUSH_APP_SECRET,
            ),
            sdk = HeytapOppoPushSdk(appContext),
            store = PrefsOppoPushStore(appContext),
            uploader = ApiPushTokenUploader(apiClient),
            scope = appScope,
            sessionProvider = { activeSession },
            deviceLabel = { deviceLabel() },
            appVersion = BuildConfig.VERSION_NAME,
            debug = BuildConfig.DEBUG,
            ensureNotificationChannel = { PushChannels.ensureClassNoticeChannel(appContext) },
        )
    }

    /**
     * vivo 推送（官方 vpush 4.1.7.0_517）：
     * - AppID/AppKey 来自 `app/vivo.properties`（空 = 未配置 → 安全跳过）；AppSecret 只在服务端；
     * - 不在 Application.onCreate 初始化：登录成功后 [VivoPushManager.onSessionChanged] 首次启动
     *   （用户已同意隐私政策），注册成功 regId 上传 `push_tokens(provider='vivo')`。
     */
    val vivoPushManager: VivoPushManager by lazy {
        VivoPushManager(
            context = appContext,
            config = VivoPushConfig.of(
                appId = BuildConfig.VIVO_PUSH_APP_ID,
                apiKey = BuildConfig.VIVO_PUSH_API_KEY,
            ),
            sdk = VivoPushSdkImpl(),
            store = PrefsVivoPushStore(appContext),
            uploader = ApiPushTokenUploader(apiClient),
            scope = appScope,
            sessionProvider = { activeSession },
            deviceLabel = { deviceLabel() },
            appVersion = BuildConfig.VERSION_NAME,
            ensureNotificationChannel = { PushChannels.ensureClassNoticeChannel(appContext) },
        )
    }

    /** 上报云端 token 时展示的设备标签（只含厂商/型号，不含任何个人隐私；与网页/小程序无关）。 */
    private fun deviceLabel(): String {
        val parts = listOf(android.os.Build.MANUFACTURER, android.os.Build.MODEL)
            .map { it?.trim().orEmpty() }
            .filter { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }
        return parts.joinToString(" ").ifBlank { "Android" }
    }

    /**
     * OPPO 账号服务（OPPO 账号绑定学号 ≤3 个 + OPPO 账号快捷登录）：
     * - 凭证来自 `app/oppo.properties`（BuildConfig；空 = 未配置 → 身份页/「我的」入口隐藏）；
     * - 客户端只把 OPPO SDK 的 authCode 交给 ECS `/account` 服务端验证，**不自行验证** OPPO token/openid；
     * - 仅 ColorOS 手机展示入口（严格按 ColorOS/OPPO ROM 系统属性判断；非 ColorOS 隐藏）。
     * 懒加载：未进入身份页/「我的」前不创建。
     */
    private val oppoAccount = lazy {
        OppoAccountManager(
            config = OppoAccountConfig.of(
                appId = BuildConfig.OPPO_ACCOUNT_APP_ID,
                appKey = BuildConfig.OPPO_ACCOUNT_APP_KEY,
                scope = BuildConfig.OPPO_ACCOUNT_SCOPE,
                baseUrl = BuildConfig.ACCOUNT_BASE_URL,
            ),
            api = EcsOppoAccountApi(transport = OkHttpTransport()),
            oauth = HeytapOppoOAuthSdk(),
            // 严格 ColorOS：只认 ColorOS/OPPO ROM 系统属性（用户 2026-11-17 晚要求，不再看品牌名）。
            deviceSupported = OppoAccountDeviceSupport.isSupported(hasColorOsRom = hasColorOsRom()),
        )
    }

    /**
     * 荣耀账号服务（荣耀账号绑定学号 ≤3 个 + 快捷登录）：
     * - 客户端凭证来自 `app/honor.properties`（BuildConfig；空 = 未配置 → 入口隐藏）；
     * - 客户端只把荣耀 SDK 的 authorizationCode 交给 ECS `/honor` 系列接口，**不自行验证** openId；
     * - 仅荣耀设备展示入口（Build.MANUFACTURER=HONOR 或 SDK isHonorMachine）。
     */
    private val honorAccount = lazy {
        val authSdk = HonorIdAuthSdk()
        HonorAccountManager(
            config = HonorAccountConfig.of(
                clientId = BuildConfig.HONOR_ACCOUNT_CLIENT_ID,
                scope = BuildConfig.HONOR_ACCOUNT_SCOPE,
                baseUrl = BuildConfig.ACCOUNT_BASE_URL,
            ),
            api = EcsOppoAccountApi(
                transport = OkHttpTransport(),
                baseUrl = BuildConfig.ACCOUNT_BASE_URL,
                pathPrefix = "/honor",
                providerLabel = "荣耀",
            ),
            auth = authSdk,
            deviceSupported = HonorAccountDeviceSupport.isSupported(
                manufacturer = android.os.Build.MANUFACTURER,
                sdkSaysHonorMachine = runCatching { authSdk.isHonorMachine() }.getOrDefault(false),
            ),
        )
    }

    val cloudSpaceRepository: CloudSpaceRepository by cloudSpace
    val oppoAccountManager: OppoAccountManager by oppoAccount
    val honorAccountManager: HonorAccountManager by honorAccount

    /** ColorOS 系系统属性探测（探测异常按「非 ColorOS」处理）。 */
    private fun hasColorOsRom(): Boolean = runCatching {
        val systemProperties = Class.forName("android.os.SystemProperties")
        val get = systemProperties.getMethod("get", String::class.java)
        OppoAccountDeviceSupport.COLOR_OS_ROM_PROPERTIES
            .any { key ->
                (runCatching { get.invoke(null, key) as? String }.getOrNull())
                    ?.trim().orEmpty().isNotEmpty()
            }
    }.getOrDefault(false)

    init {
        // 启动不阻塞主线程：WorkManager 初始化与 ensureScheduled 放到后台作用域。
        appScope.launch { runCatching { reminderScheduler.ensureScheduled() } }
    }

    /** 用户修改提醒设置后重建调度（或关闭时取消）。 */
    fun rescheduleReminders() {
        runCatching { ReminderRuntime.reschedule(appContext) }
    }

    fun cancelReminders() {
        runCatching { ReminderRuntime.cancel(appContext) }
    }

    /**
     * 登录后预热提醒所需的作业、课表缓存。
     * 只读网络拉取，幂等（各仓库有 TTL）。
     */
    private fun warmReminderCaches() {
        appScope.launch {
            runCatching { homeworkRepository.load(force = false) }
            runCatching { timetableRepository.load(force = false) }
        }
    }

    /** 退出登录：清内存会话、名单缓存（本机业务数据按账号隔离，下次登录同账号仍可用）。 */
    suspend fun signOut() {
        updateActiveSession(null)
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
    suspend fun refreshCoreData(): Unit = coroutineScope {
        launch { classMembersRepository.load(force = false) }
        launch { marksRepository.refresh(force = false) }
        launch { userDataRepository.pull(force = false) }
    }
}

