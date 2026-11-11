package com.furina.classtools.data.ppt

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** 一次已入队的下载：系统下载 id + 目标文件名/文件（用于 FileProvider 打开/分享）。 */
data class PptDownloadTicket(
    val downloadId: Long,
    val fileName: String,
    val file: File,
    val mimeType: String,
)

/** 下载事件（Flow 在 IO 线程产生，UI 在主线程 collect → 安全更新状态）。 */
sealed interface PptDownloadEvent {
    data class Progress(
        val downloadedBytes: Long,
        val totalBytes: Long,
        /** 0..100；总量未知 → 0。 */
        val percent: Int,
    ) : PptDownloadEvent

    /** 下载完成（file 已落盘，可直接 FileProvider 打开/分享）。 */
    data class Done(val ticket: PptDownloadTicket) : PptDownloadEvent

    /** 下载失败（系统原因，如网络/无空间）。 */
    data class Failed(val reason: String) : PptDownloadEvent
}

/** 下载能力抽象（生产实现 [AndroidPptFileDownloader]；UI 只依赖本接口，便于预览/测试注入）。 */
interface PptFileDownloader {
    /** 入队下载；返回 null = 无法开始（URL 非法/系统服务不可用）。 */
    fun enqueue(url: String, fileName: String, mimeType: String): PptDownloadTicket?

    /** 观察下载进度/结果（轮询 DownloadManager）。 */
    fun observe(ticket: PptDownloadTicket): Flow<PptDownloadEvent>

    /** 取消下载（可选能力；系统服务不可用返回 false）。 */
    fun cancel(ticket: PptDownloadTicket): Boolean
}

/**
 * Android DownloadManager 实现（契约 §12.4.2）：
 *
 * - 保存到**应用外部下载目录**（`Android/data/<pkg>/files/Download/`）：
 *   本项目红线「不申请存储读取权限」，因此不写公共 Downloads；系统下载通知照常显示，
 *   完成后通过 FileProvider 打开/分享（见 `PptFileIntents`）；
 * - 大文件不读入内存：全程由系统 DownloadManager 流式下载，本类只用 cursor 轮询进度；
 * - 同名文件自动追加 ` (1)` 后缀，不覆盖。
 */
class AndroidPptFileDownloader(context: Context) : PptFileDownloader {

    private val appContext = context.applicationContext

    /** 下载目录（FileProvider 映射见 res/xml/ct_file_paths.xml 的 `ppt_downloads`）。 */
    val downloadDir: File
        get() = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(appContext.filesDir, "Download")

    override fun enqueue(url: String, fileName: String, mimeType: String): PptDownloadTicket? {
        if (url.isBlank()) return null
        val manager = runCatching {
            appContext.getSystemService(DownloadManager::class.java)
        }.getOrNull() ?: return null
        val dir = downloadDir
        runCatching { dir.mkdirs() }
        val finalName = PptDownloadLogicDelegate.uniqueName(dir, fileName)
        return runCatching {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(finalName)
                .setMimeType(mimeType)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, finalName)
            val id = manager.enqueue(request)
            PptDownloadTicket(
                downloadId = id,
                fileName = finalName,
                file = File(dir, finalName),
                mimeType = mimeType,
            )
        }.getOrNull()
    }

    override fun observe(ticket: PptDownloadTicket): Flow<PptDownloadEvent> = flow {
        val manager = appContext.getSystemService(DownloadManager::class.java)
        if (manager == null) {
            emit(PptDownloadEvent.Failed("系统下载服务不可用"))
            return@flow
        }
        val query = DownloadManager.Query().setFilterById(ticket.downloadId)
        var lastPercent = -1
        var failedPolls = 0
        while (true) {
            var finished = false
            var result: PptDownloadEvent? = null
            runCatching {
                manager.query(query)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use
                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS),
                    )
                    val downloaded = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                    )
                    val total = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                    )
                    val percent = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
                    if (percent != lastPercent) {
                        lastPercent = percent
                        emit(
                            PptDownloadEvent.Progress(
                                downloadedBytes = downloaded.coerceAtLeast(0L),
                                totalBytes = total.coerceAtLeast(0L),
                                percent = percent,
                            ),
                        )
                    }
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val localUri = cursor.getString(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI),
                            )
                            val file = localFileOf(localUri) ?: ticket.file
                            result = PptDownloadEvent.Done(ticket.copy(file = file))
                            finished = true
                        }

                        DownloadManager.STATUS_FAILED -> {
                            val reasonCode = cursor.getInt(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON),
                            )
                            result = PptDownloadEvent.Failed("下载失败（$reasonCode）")
                            finished = true
                        }
                    }
                }
            }.onFailure { failedPolls += 1 }
            result?.let { emit(it) }
            if (finished) return@flow
            if (failedPolls >= 20) {
                emit(PptDownloadEvent.Failed("下载状态读取失败"))
                return@flow
            }
            delay(POLL_INTERVAL_MS)
        }
    }.flowOn(Dispatchers.IO)

    override fun cancel(ticket: PptDownloadTicket): Boolean {
        val manager = runCatching {
            appContext.getSystemService(DownloadManager::class.java)
        }.getOrNull() ?: return false
        return runCatching { manager.remove(ticket.downloadId) }.getOrDefault(0) > 0
    }

    private fun localFileOf(localUri: String?): File? {
        val raw = localUri?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return runCatching { Uri.parse(raw).path?.let { File(it) } }.getOrNull()
    }

    companion object {
        const val POLL_INTERVAL_MS: Long = 600L
    }
}

/** 文件名去重（目录内已有同名 → 追加 ` (n)`；纯逻辑委托 PptDownloadLogic，便于单测）。 */
internal object PptDownloadLogicDelegate {
    fun uniqueName(dir: File, desired: String): String {
        val existing = runCatching { dir.list()?.toSet() }.getOrNull().orEmpty()
        return com.furina.classtools.features.ppt.PptDownloadLogic.nextAvailableFileName(existing, desired)
    }
}
