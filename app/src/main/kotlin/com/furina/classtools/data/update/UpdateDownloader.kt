package com.furina.classtools.data.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.furina.classtools.data.network.NetworkClients
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call

/**
 * APK 下载能力（两条实现 + 协调器）：
 *
 * 1) [DownloadManagerUpdateDownloader]（首选）：系统 DownloadManager 下载到公共 Downloads/ClassTools/ 目录，
 *    通知栏进度、系统托底传输；完成后拿 `getUriForDownloadedFile()` 的 content URI / 本机路径用于安装；
 * 2) [OkHttpUpdateDownloader]（回退）：DownloadManager 不可用（服务缺失/公共目录不可写等）→
 *    OkHttp 流式下载到 `cache/updates/`，边下边写、sha256 校验、支持取消；
 * 3) [UpdateDownloadCoordinator]：首选返回 null → 自动回退。
 *
 * 事件约定与 `data/ppt/PptFileDownloader` 一致：Flow 在 IO 线程产生，UI 主线程 collect。
 */
interface UpdateDownloadSession {
    val events: Flow<UpdateDownloadEvent>

    /** 用户取消（强制更新时 Manager 不会调用）。 */
    fun cancel()
}

/** 下载能力抽象（UI/Manager 只依赖本接口，便于替换与测试）。 */
interface UpdateApkDownloader {
    /** 入队下载；返回 null = 当前实现无法开始（调用方回退/提示失败）。 */
    fun enqueue(info: UpdateInfo, fileName: String): UpdateDownloadSession?
}

/** DownloadManager 实现（公共 Downloads/ClassTools/）。 */
class DownloadManagerUpdateDownloader(context: Context) : UpdateApkDownloader {

    private val appContext: Context = context.applicationContext ?: context

    override fun enqueue(info: UpdateInfo, fileName: String): UpdateDownloadSession? {
        if (!UpdateJsonParser.isHttpUrl(info.apkUrl)) return null
        val manager = runCatching {
            appContext.getSystemService(DownloadManager::class.java)
        }.getOrNull() ?: return null
        return runCatching {
            val relativePath = "${UpdateFileNames.PUBLIC_DOWNLOAD_SUB_DIR}/$fileName"
            val request = DownloadManager.Request(Uri.parse(info.apkUrl))
                .setTitle("枫丹工具箱 ${info.versionName}")
                .setDescription("正在下载新版本…")
                .setMimeType(APK_MIME_TYPE)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, relativePath)
            val downloadId = manager.enqueue(request)
            DownloadManagerSession(
                manager = manager,
                downloadId = downloadId,
                fileName = fileName,
            )
        }.getOrNull()
    }
}

/** 一次 DownloadManager 下载会话（轮询 cursor → 事件流）。 */
private class DownloadManagerSession(
    private val manager: DownloadManager,
    private val downloadId: Long,
    private val fileName: String,
) : UpdateDownloadSession {

    @Volatile
    private var cancelled = false

    override val events: Flow<UpdateDownloadEvent> = flow {
        val query = DownloadManager.Query().setFilterById(downloadId)
        var lastPercent = -1
        var failedPolls = 0
        while (true) {
            if (cancelled) return@flow
            var finishedEvent: UpdateDownloadEvent? = null
            runCatching {
                manager.query(query)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use
                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS),
                    )
                    val downloadedBytes = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                    )
                    val totalBytes = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                    )
                    val percent = UpdateProgressLogic.percent(downloadedBytes, totalBytes)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        emit(UpdateDownloadEvent.Progress(downloadedBytes, totalBytes))
                    }
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val localUri = cursor.getString(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI),
                            )
                            val contentUri = runCatching {
                                manager.getUriForDownloadedFile(downloadId)?.toString()
                            }.getOrNull()
                            finishedEvent = UpdateDownloadEvent.Done(
                                DownloadedApk(
                                    fileName = fileName,
                                    filePath = readableLocalPath(localUri),
                                    contentUri = contentUri,
                                    source = UpdateDownloadSource.DOWNLOAD_MANAGER,
                                ),
                            )
                        }

                        DownloadManager.STATUS_FAILED -> {
                            val reasonCode = cursor.getInt(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON),
                            )
                            finishedEvent = UpdateDownloadEvent.Failed(
                                downloadFailedReason(reasonCode),
                            )
                        }
                    }
                }
            }.onFailure { failedPolls += 1 }
            finishedEvent?.let { event ->
                emit(event)
                return@flow
            }
            if (failedPolls >= MAX_FAILED_POLLS) {
                emit(UpdateDownloadEvent.Failed("下载状态读取失败"))
                return@flow
            }
            delay(POLL_INTERVAL_MS)
        }
    }.flowOn(Dispatchers.IO)

    override fun cancel() {
        cancelled = true
        runCatching { manager.remove(downloadId) }
    }

    /** `file://` 且当前进程可读才返回路径（Android 10+ 公共 Downloads 可能不可直接读，改用 content URI）。 */
    private fun readableLocalPath(localUri: String?): String? {
        val raw = localUri?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        if (uri.scheme == "content") return null
        val path = uri.path ?: return null
        val file = File(path)
        return if (file.isFile && file.canRead()) file.absolutePath else null
    }
}

/** OkHttp 回退实现：下载到 cache/updates/，流式写入 + 进度 + sha256 校验 + 取消。 */
class OkHttpUpdateDownloader(
    context: Context,
    private val client: OkHttpClient = defaultClient(),
) : UpdateApkDownloader {

    private val appContext: Context = context.applicationContext ?: context

    override fun enqueue(info: UpdateInfo, fileName: String): UpdateDownloadSession? {
        if (!UpdateJsonParser.isHttpUrl(info.apkUrl)) return null
        val dir = File(appContext.cacheDir, UpdateFileNames.CACHE_SUB_DIR)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        return OkHttpDownloadSession(
            client = client,
            info = info,
            dir = dir,
            fileName = fileName,
        )
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val READ_TIMEOUT_MS = 30_000L

        /** 下载客户端：连接 10s / 读 30s；不设 callTimeout（APK 可能较大）。2026-11-18 共享全局连接池。 */
        fun defaultClient(): OkHttpClient = NetworkClients.builder()
            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

/** 一次 OkHttp 下载会话（写入 dir/<fileName>.download → 校验 → 重命名为 <fileName>）。 */
private class OkHttpDownloadSession(
    private val client: OkHttpClient,
    private val info: UpdateInfo,
    private val dir: File,
    private val fileName: String,
) : UpdateDownloadSession {

    @Volatile
    private var call: Call? = null

    @Volatile
    private var cancelled = false

    private val tempFile = File(dir, "$fileName.download")

    override val events: Flow<UpdateDownloadEvent> = flow {
        var lastPercent = -1
        var lastEmitAt = 0L
        try {
            val request = Request.Builder()
                .url(info.apkUrl)
                .header("Accept", APK_MIME_TYPE)
                .get()
                .build()
            val httpCall = client.newCall(request)
            call = httpCall
            httpCall.execute().use { response ->
                if (!response.isSuccessful) {
                    emit(UpdateDownloadEvent.Failed("下载失败（HTTP ${response.code}）"))
                    return@use
                }
                val body = response.body
                val totalBytes = body.contentLength()
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var downloadedBytes = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            val percent = UpdateProgressLogic.percent(downloadedBytes, totalBytes)
                            val nowMs = System.currentTimeMillis()
                            if (percent != lastPercent || nowMs - lastEmitAt >= PROGRESS_EMIT_INTERVAL_MS) {
                                lastPercent = percent
                                lastEmitAt = nowMs
                                emit(
                                    UpdateDownloadEvent.Progress(
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = totalBytes,
                                    ),
                                )
                            }
                        }
                        output.flush()
                    }
                }
                if (cancelled) {
                    tempFile.delete()
                    return@use
                }
                // sha256 校验（远端未提供则跳过；不匹配 → 删除文件并提示重试）。
                val actualSha256 = runCatching { UpdateDigest.sha256Hex(tempFile) }.getOrElse { "" }
                if (!UpdateDigest.matches(info.sha256, actualSha256)) {
                    tempFile.delete()
                    emit(UpdateDownloadEvent.Failed("安装包校验失败，请重试"))
                    return@use
                }
                val target = File(dir, fileName)
                if (target.exists()) target.delete()
                if (!tempFile.renameTo(target)) {
                    // 极少数文件系统不允许 rename：复制后删除临时文件。
                    val copied = runCatching {
                        tempFile.copyTo(target, overwrite = true)
                        tempFile.delete()
                        true
                    }.getOrDefault(false)
                    if (!copied) {
                        tempFile.delete()
                        emit(UpdateDownloadEvent.Failed("安装包保存失败"))
                        return@use
                    }
                }
                emit(
                    UpdateDownloadEvent.Done(
                        DownloadedApk(
                            fileName = fileName,
                            filePath = target.absolutePath,
                            contentUri = null,
                            source = UpdateDownloadSource.IN_APP_OKHTTP,
                        ),
                    ),
                )
            }
        } catch (cancelledException: CancellationException) {
            tempFile.delete()
            throw cancelledException
        } catch (io: IOException) {
            tempFile.delete()
            emit(UpdateDownloadEvent.Failed(if (cancelled) "已取消下载" else "下载失败：网络不可用"))
        } finally {
            call = null
        }
    }.flowOn(Dispatchers.IO)

    override fun cancel() {
        cancelled = true
        runCatching { call?.cancel() }
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_EMIT_INTERVAL_MS = 250L
    }
}

/**
 * 下载协调器：优先 DownloadManager，返回 null（服务不可用/无法写公共目录/URL 非法）时回退 OkHttp。
 * 已开始的系统下载失败不会中途切换实现（用户点「重试」时会再次按同样顺序尝试）。
 */
class UpdateDownloadCoordinator(
    private val preferred: UpdateApkDownloader,
    private val fallback: UpdateApkDownloader,
) : UpdateApkDownloader {

    override fun enqueue(info: UpdateInfo, fileName: String): UpdateDownloadSession? {
        val primary = runCatching { preferred.enqueue(info, fileName) }.getOrNull()
        if (primary != null) return primary
        return runCatching { fallback.enqueue(info, fileName) }.getOrNull()
    }
}

// ---------------------------------------------------------------- 常量 / 错误文案

/** APK MIME（系统安装器 + DownloadManager 通用）。 */
const val APK_MIME_TYPE: String = "application/vnd.android.package-archive"

private const val POLL_INTERVAL_MS: Long = 600L
private const val MAX_FAILED_POLLS: Int = 20

private fun downloadFailedReason(reasonCode: Int): String = when (reasonCode) {
    DownloadManager.ERROR_CANNOT_RESUME -> "下载失败：无法续传"
    DownloadManager.ERROR_DEVICE_NOT_FOUND -> "下载失败：存储不可用"
    DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "下载失败：文件已存在"
    DownloadManager.ERROR_FILE_ERROR -> "下载失败：文件读写错误"
    DownloadManager.ERROR_HTTP_DATA_ERROR -> "下载失败：网络数据错误"
    DownloadManager.ERROR_INSUFFICIENT_SPACE -> "下载失败：存储空间不足"
    DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "下载失败：重定向过多"
    DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "下载失败：服务器响应异常"
    DownloadManager.ERROR_UNKNOWN -> "下载失败"
    else -> "下载失败（$reasonCode）"
}
