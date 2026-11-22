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
 * 更新流程编排（检测 → 弹窗 → 用户选择「立即更新」→ 下载 → sha256 校验 → 用户点「立即安装」拉起系统安装器）。
 *
 * 行为契约（用户 2026-11-17 晚要求）：
 * - [checkOnStart]：根级 `UpdateHost` 挂载（App 冷启动）时调用，**每次冷启动自动检查一次**；
 *   发现新版本后**自动后台下载**（弹窗展示进度，不自动安装）；
 * - 手动检查（我的 → 检查更新）：同样只弹窗，等用户点「立即更新」；
 * - 下载完成**不自动拉起安装器**：弹窗显示「立即安装」，由用户点击决定；
 * - `force=true`：弹窗不可取消（无「稍后」、下载中无「取消下载」）；
 * - 所有网络失败静默降级（仅更新 [checkStatus]，不弹错误弹窗，不打扰离线使用）。
 *
 * 线程：公开方法主线程安全；网络/下载在 [scope]（IO）执行，UI 通过 [dialog]/[checkStatus] StateFlow 消费。
 */
class UpdateManager(
    private val context: Context,
    private val store: UpdateCheckStore,
    private val checker: UpdateChecker,
    private val downloader: UpdateApkDownloader,
    /** 当前安装版本名（弹窗副标题「当前 vX → 新版本 vY」；生产 = BuildConfig.VERSION_NAME）。 */
    private val currentVersionName: String = UpdateVersion.normalize(BuildConfig.VERSION_NAME),
    /** 拉起系统安装器（单测注入假实现，断言「下载完成不自动安装」）。 */
    private val installLauncher: (Context, DownloadedApk) -> UpdateInstallLaunch = { ctx, apk ->
        UpdateInstaller.launch(ctx, apk)
    },
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
    /** 冷启动自动检查闸门（同一进程只自动检查一次 → 去掉每日节流后的失败退避）。 */
    private val autoCheckGate = UpdateAutoCheckGate()
    private var downloadJob: Job? = null
    private var downloadSession: UpdateDownloadSession? = null
    private var downloadedApk: DownloadedApk? = null
    private var backgroundDownload = false
    private var backgroundInfo: UpdateInfo? = null

    fun attachHost() {
        _hostAttached.value = true
    }

    fun detachHost() {
        _hostAttached.value = false
    }

    // ------------------------------------------------------------ 检测

    /**
     * App 冷启动自动检查（根级 `UpdateHost` 挂载时调用）：
     * 去掉每日节流后改为「每次冷启动检查一次」，发现新版本直接弹窗并自动后台下载。
     *
     * - [UpdateAutoCheckGate]：同一进程内只自动检查一次；失败不重置闸门 = 失败退避（下次冷启动再试）；
     * - [checkMutex] 防并发：已有检查在运行时跳过本次自动检查（不阻塞、不重复请求）；
     * - 不抛异常；所有失败只更新 [checkStatus]，静默降级不打扰离线使用。
     */
    fun checkOnStart() {
        if (!autoCheckGate.tryBegin()) return
        if (!checkMutex.tryLock()) return
        scope.launch {
            try {
                _checkStatus.value = UpdateCheckStatus.Checking
                val result = runCatching { checker.check() }.getOrElse { error ->
                    UpdateCheckResult.Failed(failureReason(error))
                }
                applyCheckResult(result)
            } finally {
                checkMutex.unlock()
            }
        }
    }

    /** 手动检查（我的 → 检查更新；不受自动检查闸门限制）。返回结果给 UI 做 Toast/Snackbar 提示。 */
    suspend fun checkNow(): UpdateCheckResult {
        if (!checkMutex.tryLock()) {
            return UpdateCheckResult.Failed("正在检查，请稍候")
        }
        try {
            _checkStatus.value = UpdateCheckStatus.Checking
            val result = runCatching { checker.check() }.getOrElse { error ->
                UpdateCheckResult.Failed(failureReason(error))
            }
            applyCheckResult(result)
            return result
        } finally {
            checkMutex.unlock()
        }
    }

    private fun applyCheckResult(result: UpdateCheckResult) {
        when (result) {
            is UpdateCheckResult.Available -> {
                // 2026-11-22 用户要求：检测到新版本只弹窗，由用户选择「立即更新 / 后台下载 / 稍后」，
                // 不再自动开始下载。
                _checkStatus.value = UpdateCheckStatus.Available(result.info)
                openDialog(result.info)
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
        _dialog.value = UpdateDialogUi(info = info, currentVersionName = currentVersionName)
    }

    // ------------------------------------------------------------ 下载

    /** 开始下载（已有下载/已下载完成时幂等）。 */
    fun startDownload() {
        val ui = _dialog.value ?: return
        when (ui.download) {
            is UpdateDownloadState.Downloading -> return
            is UpdateDownloadState.Ready -> return

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

    /**
     * 后台下载：立即关闭弹窗、继续下载；下载完成后再弹「立即安装」，下载期间不占前台。
     * 强制更新不支持后台（必须保持弹窗进度）。
     */
    fun startBackgroundDownload() {
        val ui = _dialog.value ?: return
        if (!UpdateDialogPolicy.isDismissible(ui.info)) return
        val canStart = when (ui.download) {
            is UpdateDownloadState.Idle, is UpdateDownloadState.Failed -> true
            else -> false
        }
        if (!canStart) return
        backgroundDownload = true
        backgroundInfo = ui.info
        startDownload()
        if (_dialog.value?.download is UpdateDownloadState.Downloading) {
            _dialog.value = null
        } else {
            backgroundDownload = false
            backgroundInfo = null
        }
    }

    private fun onDownloadEvent(info: UpdateInfo, event: UpdateDownloadEvent) {
        val current = _dialog.value
        if (current == null) {
            // 后台下载：弹窗已关闭，仅在完成/失败时重新给出可操作结果，进度不占前台。
            if (!backgroundDownload || backgroundInfo?.versionCode != info.versionCode) return
            when (event) {
                is UpdateDownloadEvent.Done -> {
                    if (event.apk.source == UpdateDownloadSource.DOWNLOAD_MANAGER && info.sha256.isNotBlank()) {
                        if (UpdateApkVerifier.verify(context, event.apk, info.sha256) == false) {
                            backgroundDownload = false
                            backgroundInfo = null
                            _dialog.value = UpdateDialogUi(
                                info = info,
                                currentVersionName = currentVersionName,
                                download = UpdateDownloadState.Failed("安装包校验失败，请重新下载"),
                            )
                            return
                        }
                    }
                    downloadedApk = event.apk
                    backgroundDownload = false
                    backgroundInfo = null
                    _dialog.value = UpdateDialogUi(
                        info = info,
                        currentVersionName = currentVersionName,
                        download = UpdateDownloadState.Ready(event.apk),
                    )
                }

                is UpdateDownloadEvent.Failed -> {
                    backgroundDownload = false
                    backgroundInfo = null
                    _dialog.value = UpdateDialogUi(
                        info = info,
                        currentVersionName = currentVersionName,
                        download = UpdateDownloadState.Failed(event.reason),
                    )
                }

                is UpdateDownloadEvent.Progress -> Unit
            }
            return
        }
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
                // 下载完成只更新状态：**不自动拉起安装器**，弹窗显示「立即安装」由用户点击。
                _dialog.value = current.copy(
                    download = UpdateDownloadState.Ready(event.apk),
                    installPermissionBlocked = false,
                    installLaunched = false,
                )
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
        when (installLauncher(context, apk)) {
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
        // 自动后台下载进行中不让「稍后/返回」把它取消；要停只能点明确的「取消下载」。
        if (ui.download is UpdateDownloadState.Downloading) return
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
                currentVersionName = currentVersionName,
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


