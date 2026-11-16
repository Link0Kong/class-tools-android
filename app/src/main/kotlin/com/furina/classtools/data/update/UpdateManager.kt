package com.furina.classtools.data.update

import android.content.Context
import com.furina.classtools.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * 更新流程编排（检测 → 弹窗 → 自动下载 → sha256 校验 → 拉起系统安装器）。
 *
 * 行为契约：
 * - [maybeCheckDaily]：启动登录后调用，SharedPreferences 每天最多一次（失败也写时间，离线不反复请求）；
 * - 自动检查发现新版：弹窗 + **默认开始下载**（自动更新；进度可见，非强制可取消）；
 * - 手动检查（我的 → 检查更新）：不受节流限制，弹窗等用户点「立即更新」；
 * - `force=true`：弹窗不可取消（无「稍后」、下载中无「取消」）；
 * - 下载完成自动拉起系统安装器（Android 不允许静默安装，由用户确认）；
 * - 所有网络失败静默降级（仅更新 [checkStatus]，不弹错误弹窗，不打扰离线使用）。
 *
 * 线程：公开方法主线程安全；网络/下载在 [scope]（IO）执行，UI 通过 [dialog]/[checkStatus] StateFlow 消费。
 */
class UpdateManager(
    private val context: Context,
    private val store: UpdateCheckStore,
    private val checker: UpdateChecker,
    private val downloader: UpdateApkDownloader,
    /** 自动（启动静默）检查发现新版时是否默认开始下载（自动更新）。 */
    private val autoDownloadOnAutomaticCheck: Boolean = true,
    private val now: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    private val _dialog = MutableStateFlow<UpdateDialogUi?>(null)

    /** 当前更新弹窗状态；null = 不显示。 */
    val dialog: StateFlow<UpdateDialogUi?> = _dialog.asStateFlow()

    private val _checkStatus = MutableStateFlow<UpdateCheckStatus>(UpdateCheckStatus.Idle)

    /** 「我的 → 检查更新」行副标题状态。 */
    val checkStatus: StateFlow<UpdateCheckStatus> = _checkStatus.asStateFlow()

    private val _hostAttached = MutableStateFlow(false)

    /** 是否已有 UiHost（全局 UpdateHost）在渲染弹窗；Profile 页据此避免重复弹窗。 */
    val hostAttached: StateFlow<Boolean> = _hostAttached.asStateFlow()

    private val checkMutex = Mutex()
    private var downloadJob: Job? = null
    private var downloadSession: UpdateDownloadSession? = null
    private var downloadedApk: DownloadedApk? = null

    fun attachHost() {
        _hostAttached.value = true
    }

    fun detachHost() {
        _hostAttached.value = false
    }

    // ------------------------------------------------------------ 检测

    /**
     * 启动登录后静默检查（每天最多一次，SharedPreferences `last_check_at`）。
     * 不抛异常；发现新版 → 弹窗（自动升级路径默认开始下载）。
     */
    fun maybeCheckDaily() {
        val nowMs = now()
        if (!UpdateDailyPolicy.shouldCheck(store.lastCheckAt(), nowMs)) return
        if (!checkMutex.tryLock()) return
        // 先落盘检查时间：即使失败/离线，今天也不再反复请求。
        store.markChecked(nowMs)
        scope.launch {
            try {
                _checkStatus.value = UpdateCheckStatus.Checking
                val result = runCatching { checker.check() }.getOrElse { error ->
                    UpdateCheckResult.Failed(failureReason(error))
                }
                applyCheckResult(result, manual = false)
            } finally {
                checkMutex.unlock()
            }
        }
    }

    /** 手动检查（我的 → 检查更新；不受每日节流限制）。返回结果给 UI 做 Toast/Snackbar 提示。 */
    suspend fun checkNow(manual: Boolean = true): UpdateCheckResult {
        if (!checkMutex.tryLock()) {
            return UpdateCheckResult.Failed("正在检查，请稍候")
        }
        try {
            _checkStatus.value = UpdateCheckStatus.Checking
            val result = runCatching { checker.check() }.getOrElse { error ->
                UpdateCheckResult.Failed(failureReason(error))
            }
            applyCheckResult(result, manual = manual)
            return result
        } finally {
            checkMutex.unlock()
        }
    }

    private fun applyCheckResult(result: UpdateCheckResult, manual: Boolean) {
        when (result) {
            is UpdateCheckResult.Available -> {
                _checkStatus.value = UpdateCheckStatus.Available(result.info)
                openDialog(result.info)
                if (!manual && autoDownloadOnAutomaticCheck) {
                    startDownload()
                }
            }

            is UpdateCheckResult.UpToDate -> {
                _checkStatus.value = UpdateCheckStatus.UpToDate(result.currentVersionName)
            }

            is UpdateCheckResult.Failed -> {
                _checkStatus.value = UpdateCheckStatus.Failed(result.reason)
            }
        }
    }

    private fun openDialog(info: UpdateInfo) {
        val current = _dialog.value
        if (current != null && current.info.versionCode == info.versionCode) return
        downloadedApk = null
        _dialog.value = UpdateDialogUi(info = info)
    }

    // ------------------------------------------------------------ 下载

    /** 开始下载（已有下载/已下载完成时幂等）。 */
    fun startDownload() {
        val ui = _dialog.value ?: return
        when (ui.download) {
            is UpdateDownloadState.Downloading -> return
            is UpdateDownloadState.Ready -> {
                install()
                return
            }

            else -> Unit
        }
        val info = ui.info
        val fileName = UpdateFileNames.apkFileName(info)
        val session = runCatching { downloader.enqueue(info, fileName) }.getOrNull()
        if (session == null) {
            _dialog.value = ui.copy(download = UpdateDownloadState.Failed("无法开始下载，请稍后重试"))
            return
        }
        downloadSession = session
        downloadedApk = null
        _dialog.value = ui.copy(
            download = UpdateDownloadState.Downloading(UpdateDownloadProgress(0L, 0L, 0)),
            installPermissionBlocked = false,
            installLaunched = false,
        )
        downloadJob = scope.launch {
            try {
                session.events.collect { event -> onDownloadEvent(info, event) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                onDownloadEvent(info, UpdateDownloadEvent.Failed(failureReason(error)))
            } finally {
                if (downloadSession === session) downloadSession = null
            }
        }
    }

    private fun onDownloadEvent(info: UpdateInfo, event: UpdateDownloadEvent) {
        val current = _dialog.value ?: return
        if (current.info.versionCode != info.versionCode) return
        when (event) {
            is UpdateDownloadEvent.Progress -> {
                _dialog.value = current.copy(
                    download = UpdateDownloadReducer.reduce(current.download, event),
                )
            }

            is UpdateDownloadEvent.Done -> {
                // DownloadManager 公共目录：用返回的 content URI 兜底校验 sha256（读不到则跳过，不卡更新）。
                if (event.apk.source == UpdateDownloadSource.DOWNLOAD_MANAGER && info.sha256.isNotBlank()) {
                    if (UpdateApkVerifier.verify(context, event.apk, info.sha256) == false) {
                        _dialog.value = current.copy(
                            download = UpdateDownloadState.Failed("安装包校验失败，请重新下载"),
                            installPermissionBlocked = false,
                            installLaunched = false,
                        )
                        return
                    }
                }
                downloadedApk = event.apk
                _dialog.value = current.copy(
                    download = UpdateDownloadState.Ready(event.apk),
                    installPermissionBlocked = false,
                    installLaunched = false,
                )
                // 下载完成自动拉起系统安装器（仍需用户确认；不静默安装）。
                install()
            }

            is UpdateDownloadEvent.Failed -> {
                _dialog.value = current.copy(
                    download = UpdateDownloadState.Failed(event.reason),
                    installPermissionBlocked = false,
                    installLaunched = false,
                )
            }
        }
    }

    /** 取消下载（force 版本不可取消；取消后回到 Idle，可重新点「立即更新」）。 */
    fun cancelDownload() {
        val ui = _dialog.value ?: return
        if (!UpdateDialogPolicy.canCancelDownload(ui.info)) return
        downloadJob?.cancel()
        downloadJob = null
        downloadSession?.cancel()
        downloadSession = null
        downloadedApk = null
        _dialog.value = ui.copy(
            download = UpdateDownloadState.Idle,
            installPermissionBlocked = false,
            installLaunched = false,
        )
    }

    // ------------------------------------------------------------ 安装

    /** 拉起系统安装器；未授权「安装未知应用」时标记 [UpdateDialogUi.installPermissionBlocked]。 */
    fun install() {
        val ui = _dialog.value ?: return
        val apk = downloadedApk ?: (ui.download as? UpdateDownloadState.Ready)?.apk ?: return
        when (UpdateInstaller.launch(context, apk)) {
            UpdateInstallLaunch.LAUNCHED -> {
                _dialog.value = _dialog.value?.copy(
                    installPermissionBlocked = false,
                    installLaunched = true,
                )
            }

            UpdateInstallLaunch.NEED_UNKNOWN_SOURCES_PERMISSION -> {
                _dialog.value = _dialog.value?.copy(
                    installPermissionBlocked = true,
                    installLaunched = false,
                )
            }

            UpdateInstallLaunch.NO_APK, UpdateInstallLaunch.NO_HANDLER -> {
                _dialog.value = _dialog.value?.copy(
                    download = UpdateDownloadState.Failed("安装包不可用，请重新下载"),
                    installPermissionBlocked = false,
                    installLaunched = false,
                )
            }
        }
    }

    /** 下拉「安装未知应用」授权页；返回后用户可再点「立即安装」。 */
    fun openInstallPermissionSettings(): Boolean =
        UpdateInstaller.openUnknownSourcesSettings(context)

    /** 「稍后」：force 版本忽略；非强制关闭弹窗并记住已跳过的版本。 */
    fun dismissDialog() {
        val ui = _dialog.value ?: return
        if (!UpdateDialogPolicy.isDismissible(ui.info)) return
        store.markDismissed(ui.info.versionCode)
        downloadJob?.cancel()
        downloadJob = null
        downloadSession?.cancel()
        downloadSession = null
        downloadedApk = null
        _dialog.value = null
    }

    // ------------------------------------------------------------ 内部

    private fun failureReason(error: Throwable): String = when (error) {
        is java.net.SocketTimeoutException -> "检查超时"
        is java.net.UnknownHostException -> "无法连接更新服务器"
        is java.io.IOException -> "网络不可用"
        else -> "检查更新失败"
    }

    companion object {
        /** 生产装配：当前版本来自 BuildConfig；检查走 10s 超时 OkHttp；下载优先 DownloadManager。 */
        fun create(context: Context): UpdateManager {
            val appContext: Context = context.applicationContext ?: context
            val currentVersionCode = BuildConfig.VERSION_CODE
            val currentVersionName = UpdateVersion.normalize(BuildConfig.VERSION_NAME)
            return UpdateManager(
                context = appContext,
                store = PrefsUpdateCheckStore(appContext),
                checker = UpdateChecker(
                    currentVersionCode = currentVersionCode,
                    currentVersionName = currentVersionName,
                    transport = UpdateHttpTransport.create(),
                ),
                downloader = UpdateDownloadCoordinator(
                    preferred = DownloadManagerUpdateDownloader(appContext),
                    fallback = OkHttpUpdateDownloader(appContext),
                ),
            )
        }
    }
}

/** 进程内单例（Application 级；Compose 的 UpdateHost 与「我的」页共用同一份弹窗/检查状态）。 */
object UpdateRuntime {

    @Volatile
    private var instance: UpdateManager? = null

    fun manager(context: Context): UpdateManager {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: UpdateManager.create(context).also { instance = it }
        }
    }
}
