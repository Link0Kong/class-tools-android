package com.furina.classtools.data.notices

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * 通知附件落盘/打开（Android 侧实现；数据编排见 [NoticeAttachmentPreparer]）。
 *
 * - sink：`cacheDir/notice_attachments/<key>/<展示名>`（FileProvider `ct_file_paths.xml` 已声明该目录）；
 * - 打开：FileProvider content Uri + `ACTION_VIEW`（系统预览器）；无处理器时退回 `ACTION_SEND` 分享；
 * - 旧数据 `data:` 内联分支同样可用（解码后写缓存），与新签名 URL 共用打开逻辑。
 */
object NoticeAttachmentOpen {

    /** 下载器单例（OkHttp 连接池复用；不含用户凭据）。 */
    private val fetcher: NoticeAttachmentFetcher by lazy { OkHttpNoticeAttachmentFetcher() }

    /** 按需准备：签名 URL 下载 / data URI 解码 → 缓存文件。 */
    suspend fun prepare(
        context: Context,
        source: String?,
        displayName: String,
    ): NoticeAttachmentFileResult = NoticeAttachmentPreparer(
        fetcher = fetcher,
        sink = CacheDirAttachmentSink(context.applicationContext),
    ).prepare(source, displayName)

    /**
     * 用系统应用打开缓存文件。
     *
     * @return false = 没有可处理的应用（UI 提示「没有可打开该附件的应用」），不抛异常
     */
    fun open(context: Context, file: File, mime: String): Boolean {
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return false
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (tryStart(context, view)) return true
        val share = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return tryStart(context, share)
    }

    private fun tryStart(context: Context, intent: Intent): Boolean {
        if (runCatching { context.startActivity(intent) }.isSuccess) return true
        // 极少数调用方给的是 Application context（没有 Activity task）→ 补 NEW_TASK 再试一次
        val retry = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(retry) }.isSuccess
    }
}

/**
 * `cacheDir/notice_attachments/<key>/<fileName>` 落盘实现（同 key/name 命中即磁盘缓存）。
 *
 * 缓存上限策略：写入前按需清理 7 天未访问的旧附件（最多每 10 分钟扫一次），
 * 避免长期使用后 cache 目录无界增长。
 */
internal class CacheDirAttachmentSink(context: Context) : NoticeAttachmentSink {

    private val root: File = File(context.cacheDir, NoticeAttachmentPreparer.CACHE_DIR)

    @Volatile
    private var lastPruneAt: Long = 0L

    override fun cached(key: String, fileName: String): File? = runCatching {
        if (key.isBlank() || fileName.isBlank()) return@runCatching null
        File(File(root, key), fileName).takeIf { it.isFile && it.length() > 0 }
    }.getOrNull()

    override fun write(key: String, fileName: String, bytes: ByteArray): File? = runCatching {
        if (key.isBlank() || fileName.isBlank()) return@runCatching null
        pruneIfNeeded()
        val dir = File(root, key)
        if (!dir.exists() && !dir.mkdirs()) return@runCatching null
        val target = File(dir, fileName)
        val temp = File(dir, "$fileName.tmp")
        temp.outputStream().use { out -> out.write(bytes) }
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        target.takeIf { it.isFile && it.length() > 0 }
    }.getOrNull()

    private fun pruneIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastPruneAt < PRUNE_INTERVAL_MS) return
        lastPruneAt = now
        runCatching {
            val cutoff = now - MAX_CACHE_AGE_MS
            root.listFiles()?.forEach { dir ->
                if (!dir.isDirectory) return@forEach
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.lastModified() < cutoff) file.delete()
                }
                if (dir.listFiles()?.isEmpty() == true) dir.delete()
            }
        }
    }

    private companion object {
        /** 附件缓存保留期（7 天）。 */
        const val MAX_CACHE_AGE_MS: Long = 7L * 24 * 60 * 60 * 1000

        /** 清理扫描最短间隔（10 分钟）。 */
        const val PRUNE_INTERVAL_MS: Long = 10L * 60 * 1000
    }
}
