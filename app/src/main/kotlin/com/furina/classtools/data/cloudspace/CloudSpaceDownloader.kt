package com.furina.classtools.data.cloudspace

import android.content.Context
import android.os.Environment
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request

/** 一次成功下载的落盘结果（供 FileProvider 打开/分享）。 */
data class CloudSpaceDownloadTicket(
    val file: File,
    val fileName: String,
    val mimeType: String,
)

/** 下载事件（Flow 在 IO 线程产生；UI 在主线程 collect）。 */
sealed interface CloudSpaceDownloadEvent {
    data class Progress(
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : CloudSpaceDownloadEvent {
        /** 0..100；总量未知（-1）→ 0。 */
        val percent: Int
            get() = if (totalBytes > 0L) {
                ((downloadedBytes.coerceIn(0L, totalBytes) * 100L) / totalBytes).toInt().coerceIn(0, 100)
            } else {
                0
            }
    }

    data class Done(val ticket: CloudSpaceDownloadTicket) : CloudSpaceDownloadEvent

    data class Failed(val message: String) : CloudSpaceDownloadEvent
}

/** 下载能力抽象（生产实现 [OkHttpCloudSpaceDownloader]；UI 只依赖本接口，便于预览/测试注入）。 */
interface CloudSpaceDownloader {
    /** 流式下载签名 URL 到本机下载目录（同名自动追加 ` (n)`，不覆盖）。 */
    fun download(url: String, fileName: String, mimeType: String): Flow<CloudSpaceDownloadEvent>
}

/**
 * OkHttp 流式下载实现（不申请存储权限，红线对齐项目 PPT 下载）：
 *
 * - 落盘目录：`getExternalFilesDir(DIRECTORY_DOWNLOADS)`（对应 `res/xml/ct_file_paths.xml`
 *   已有的 `external-files-path … path="Download/"`，可直接 FileProvider 打开）；
 *   外部目录不可用时退回 `cacheDir/exports`（对应已有的 `cache-path name="exports"`），
 *   两条路径都在现有 FileProvider 白名单内，**不需要改 res/xml**；
 * - 大文件不读入内存：边下边写 `.part`，完成后原子 rename；
 * - 403 = 签名链接过期（30 分钟）→ 提示刷新后重试（UI 会先调 `findFreshFile` 换新链接）；
 * - 不向 DownloadManager 传 URL：drop 服务当前是 HTTP IP，系统下载进程的明文策略不受 App
 *   network_security_config 控制，自建 OkHttp 流式下载行为可预期。
 */
class OkHttpCloudSpaceDownloader(
    context: Context,
    private val client: OkHttpClient = OkHttpCloudSpaceTransport.downloadClient(),
) : CloudSpaceDownloader {

    private val appContext = context.applicationContext

    /** 下载目录（FileProvider 可映射；优先应用外部下载目录）。 */
    val downloadDir: File
        get() = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(appContext.cacheDir, "exports")

    override fun download(
        url: String,
        fileName: String,
        mimeType: String,
    ): Flow<CloudSpaceDownloadEvent> = flow {
        if (url.isBlank()) {
            emit(CloudSpaceDownloadEvent.Failed("文件不存在或已过期"))
            return@flow
        }
        val dir = downloadDir
        runCatching { dir.mkdirs() }
        val safeName = CloudSpaceLogic.safeName(fileName)
        val finalName = CloudSpaceLogic.uniqueName(existingNames(dir), safeName)
        val target = File(dir, finalName)
        val partial = File(dir, "$finalName.part")
        try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(
                        CloudSpaceDownloadEvent.Failed(
                            if (response.code == 403) {
                                "下载链接已过期，请刷新后重试"
                            } else {
                                "下载失败（HTTP ${response.code}）"
                            },
                        ),
                    )
                    return@flow
                }
                val body = response.body
                val totalBytes = body.contentLength()
                emit(CloudSpaceDownloadEvent.Progress(downloadedBytes = 0L, totalBytes = totalBytes))
                var downloaded = 0L
                var lastEmitted = 0L
                partial.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            if (downloaded - lastEmitted >= PROGRESS_STEP_BYTES) {
                                lastEmitted = downloaded
                                emit(
                                    CloudSpaceDownloadEvent.Progress(
                                        downloadedBytes = downloaded,
                                        totalBytes = totalBytes,
                                    ),
                                )
                            }
                        }
                    }
                }
                emit(
                    CloudSpaceDownloadEvent.Progress(
                        downloadedBytes = downloaded,
                        totalBytes = if (totalBytes > 0L) totalBytes else downloaded,
                    ),
                )
            }
            if (!partial.renameTo(target)) {
                // 个别文件系统 rename 失败时退化为复制（.part 仍会被清理）。
                runCatching { partial.copyTo(target, overwrite = true) }
                    .getOrElse {
                        runCatching { partial.delete() }
                        emit(CloudSpaceDownloadEvent.Failed("保存文件失败，请检查存储空间"))
                        return@flow
                    }
                runCatching { partial.delete() }
            }
            emit(
                CloudSpaceDownloadEvent.Done(
                    CloudSpaceDownloadTicket(
                        file = target,
                        fileName = finalName,
                        mimeType = mimeType.ifBlank { "application/octet-stream" },
                    ),
                ),
            )
        } catch (cancellation: CancellationException) {
            runCatching { partial.delete() }
            throw cancellation
        } catch (_: Throwable) {
            runCatching { partial.delete() }
            // 不拼接异常信息（可能含 URL），统一中文文案。
            emit(CloudSpaceDownloadEvent.Failed("下载失败：网络不可用，请稍后重试"))
        }
    }.flowOn(Dispatchers.IO)

    private fun existingNames(dir: File): Set<String> =
        runCatching { dir.list()?.toSet() }.getOrNull().orEmpty()

    private companion object {
        const val BUFFER_SIZE: Int = 256 * 1024
        const val PROGRESS_STEP_BYTES: Long = 512L * 1024L
    }
}
