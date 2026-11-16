package com.furina.classtools.data.update

/**
 * App 内更新的数据模型（纯 Kotlin，无 android.*；全部可 JVM 单测）。
 *
 * 远端 `latest.json` 字段契约（服务端示例见 docs 或交付说明）：
 * ```json
 * {
 *   "versionCode": 79,
 *   "versionName": "4.57.0-test1",
 *   "apkUrl": "http://121.196.47.2/android/classtools-4.57.0-test1.apk",
 *   "notes": "1. 检查更新与自动更新\n2. 测试版本 1",
 *   "force": false,
 *   "channel": "test",
 *   "sha256": "…64 位小写 hex…"
 * }
 * ```
 * 解析容错见 [UpdateJsonParser]：允许 versionCode 为字符串、force 为布尔/0-1/字符串、
 * notes 为字符串或字符串数组；apkUrl 必须以 http(s) 开头。
 */

/** 一次「发现的新版本」信息（字段缺失时由解析器补默认值）。 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String = "",
    val force: Boolean = false,
    val channel: String = DEFAULT_CHANNEL,
    val sha256: String = "",
) {
    /** 是否带 APK 完整性校验值（sha256 允许缺省：老服务端还没算）。 */
    val hasSha256: Boolean
        get() = sha256.isNotBlank()

    companion object {
        const val DEFAULT_CHANNEL: String = "stable"
    }
}

/** 检查更新的结果（成功发现新版 / 已是最新 / 失败，失败静默不打扰离线用户）。 */
sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult

    data class UpToDate(
        val currentVersionName: String,
        val currentVersionCode: Int,
    ) : UpdateCheckResult

    data class Failed(val reason: String) : UpdateCheckResult
}

/** 下载来源：优先系统 DownloadManager（公共 Downloads），不可用时回退应用内 OkHttp（cache）。 */
enum class UpdateDownloadSource {
    DOWNLOAD_MANAGER,
    IN_APP_OKHTTP,
}

/**
 * 一次已完成下载的 APK。
 *
 * - [contentUri]：DownloadManager 返回的 `content://`（系统下载提供者，安装首选，带读授权）；
 * - [filePath]：本机绝对路径（DownloadManager 公共目录可读时 / OkHttp 回退 cache 一定有）；
 * 二者至少一个非空；安装 Intent 解析顺序 contentUri → FileProvider(filePath)。
 */
data class DownloadedApk(
    val fileName: String,
    val filePath: String? = null,
    val contentUri: String? = null,
    val source: UpdateDownloadSource = UpdateDownloadSource.IN_APP_OKHTTP,
)

/** 下载进度（DownloadManager 轮询 / OkHttp 流式写入都会产生）。 */
data class UpdateDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    /** 0..100；总量未知 → 0。 */
    val percent: Int,
)

/** 下载状态机（UI 直接消费；状态迁移由 [UpdateDownloadReducer] 完成，可单测）。 */
sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState

    data class Downloading(val progress: UpdateDownloadProgress) : UpdateDownloadState

    data class Ready(val apk: DownloadedApk) : UpdateDownloadState

    data class Failed(val reason: String) : UpdateDownloadState
}

/** 下载事件（Flow 在 IO 线程产生；UI 在主线程 collect）。 */
sealed interface UpdateDownloadEvent {
    data class Progress(val downloadedBytes: Long, val totalBytes: Long) : UpdateDownloadEvent

    data class Done(val apk: DownloadedApk) : UpdateDownloadEvent

    data class Failed(val reason: String) : UpdateDownloadEvent
}

/** 更新弹窗的完整 UI 状态（由 [UpdateManager] 持有，弹窗只做无状态渲染）。 */
data class UpdateDialogUi(
    val info: UpdateInfo,
    val download: UpdateDownloadState = UpdateDownloadState.Idle,
    /** 系统「安装未知应用」未授权：弹窗提示并给「去授权」按钮。 */
    val installPermissionBlocked: Boolean = false,
    /** 已拉起系统安装器（等待用户确认）；可再次点击「重新安装」。 */
    val installLaunched: Boolean = false,
)

/** 手动「检查更新」行的状态（我的 → 关于 → 检查更新 副标题）。 */
sealed interface UpdateCheckStatus {
    data object Idle : UpdateCheckStatus

    data object Checking : UpdateCheckStatus

    data class UpToDate(val currentVersionName: String) : UpdateCheckStatus

    data class Available(val info: UpdateInfo) : UpdateCheckStatus

    data class Failed(val reason: String) : UpdateCheckStatus
}

/** 安装动作判定（纯逻辑，便于单测「未知来源未授权 / 无安装器」两个分支）。 */
enum class UpdateInstallAction {
    /** 可直接拉起系统安装器。 */
    INSTALL,

    /** 需要用户先到系统设置允许「安装未知应用」。 */
    NEED_UNKNOWN_SOURCES_PERMISSION,

    /** 找不到可处理安装 Intent 的应用（极少数 ROM）/ 安装包不存在。 */
    NO_HANDLER,
}
