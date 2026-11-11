package com.furina.classtools.data.notices

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 通知文件附件的「下载/解码 → 缓存文件 → 系统打开」数据层（三端附件外置迁移第 2 步，task-5）。
 *
 * 分工：
 * - [NoticeAttachmentFetcher] / [OkHttpNoticeAttachmentFetcher]：签名 URL 直出字节
 *   （契约：`GET https://<host>/att/<id>?exp=&sig=`，无鉴权头；代码只判断 `^https?://`，不写死域名）；
 * - [NoticeAttachmentSink]：字节落盘（生产 = `cacheDir/notice_attachments/`，磁盘缓存复用）；
 * - [NoticeAttachmentPreparer]：纯编排（URL 分支 + 旧数据 data URI 分支），**可 JVM 单测**；
 * - Android 侧 `NoticeAttachmentOpen`：CacheDir sink + FileProvider `ACTION_VIEW` 打开。
 *
 * 失败一律返回可读结果（不抛异常给 UI）：网络失败 / 超限 / 不可识别分别给不同结果。
 */
object NoticeAttachmentLimits {
    /** 外置 URL 下载上限（20MB；服务端附件最大 10MB 内联，外置后放宽）。 */
    const val MAX_URL_BYTES: Long = 20L * 1024 * 1024

    /** 旧数据内联 data URI 解码上限（与图片共用 10MB 口径）。 */
    const val MAX_INLINE_BYTES: Long = 10L * 1024 * 1024
}

/** 一次附件抓取结果（区分「网络失败」与「超限」以便 UI 提示）。 */
sealed interface NoticeAttachmentFetch {
    data class Bytes(val value: ByteArray) : NoticeAttachmentFetch

    /** 内容超过上限（Content-Length 或流式读取超限）。 */
    data object TooLarge : NoticeAttachmentFetch

    /** 网络/HTTP 失败。 */
    data object Failed : NoticeAttachmentFetch
}

/** 附件字节抓取（生产 = OkHttp；单测注入假实现，不联网）。 */
fun interface NoticeAttachmentFetcher {
    suspend fun fetch(url: String, maxBytes: Long): NoticeAttachmentFetch
}

/**
 * 附件落盘目标（生产 = `cacheDir/notice_attachments/<key>/<fileName>`）。
 *
 * 以 [key]（源内容 SHA-256 前 16 hex）分目录 + 展示名作文件名：
 * 同 key/name 已存在即磁盘缓存命中（重复点开不再下载，内容不可变故可安全复用）。
 */
interface NoticeAttachmentSink {
    /** 已缓存文件；不存在返回 null。 */
    fun cached(key: String, fileName: String): File?

    /** 写入缓存文件；失败返回 null。 */
    fun write(key: String, fileName: String, bytes: ByteArray): File?
}

/** 附件「准备」结果（UI 只展开 Ok / 其它给可读提示）。 */
sealed interface NoticeAttachmentFileResult {
    data class Ok(
        val file: File,
        val mime: String,
        val fromCache: Boolean,
    ) : NoticeAttachmentFileResult

    /** 超过大小上限。 */
    data class TooLarge(val limitBytes: Long) : NoticeAttachmentFileResult

    /** 下载/解码/落盘失败。 */
    data class Failed(val message: String) : NoticeAttachmentFileResult

    /** 内容形态不可识别（空 / 非 http-s / 非 data:）。 */
    data class Unsupported(val message: String) : NoticeAttachmentFileResult
}

/**
 * 附件准备器：签名 URL → 下载到缓存文件；data URI → 解码后写缓存（向后兼容旧数据）。
 *
 * @param maxUrlBytes 外置 URL 下载上限（默认 [NoticeAttachmentLimits.MAX_URL_BYTES]）
 * @param maxInlineBytes 内联 data URI 上限（默认 [NoticeAttachmentLimits.MAX_INLINE_BYTES]）
 */
class NoticeAttachmentPreparer(
    private val fetcher: NoticeAttachmentFetcher,
    private val sink: NoticeAttachmentSink,
    private val maxUrlBytes: Long = NoticeAttachmentLimits.MAX_URL_BYTES,
    private val maxInlineBytes: Long = NoticeAttachmentLimits.MAX_INLINE_BYTES,
) {

    suspend fun prepare(source: String?, displayName: String): NoticeAttachmentFileResult {
        val trimmed = source?.trim().orEmpty()
        val kind = NoticeAttachmentSource.kindOf(trimmed)
        if (kind == NoticeAttachmentSource.Kind.NONE) {
            return NoticeAttachmentFileResult.Unsupported("附件内容不可用（未外置或格式不支持）")
        }
        val fileName = fileNameFor(trimmed, displayName)
        val key = cacheKey(trimmed)
        // 磁盘缓存命中（IO 线程，避免主线程 stat 文件）
        withContext(Dispatchers.IO) { sink.cached(key, fileName) }?.let {
            return NoticeAttachmentFileResult.Ok(it, mimeFor(kind, trimmed, fileName), fromCache = true)
        }
        val limit = if (kind == NoticeAttachmentSource.Kind.URL) maxUrlBytes else maxInlineBytes
        val bytes = when (kind) {
            NoticeAttachmentSource.Kind.URL -> when (val fetched = fetcher.fetch(trimmed, maxUrlBytes)) {
                is NoticeAttachmentFetch.Bytes -> fetched.value
                NoticeAttachmentFetch.TooLarge -> return NoticeAttachmentFileResult.TooLarge(maxUrlBytes)
                NoticeAttachmentFetch.Failed -> return NoticeAttachmentFileResult.Failed("下载失败，请检查网络后重试")
            }

            NoticeAttachmentSource.Kind.INLINE -> when (val decoded = decodeInline(trimmed, maxInlineBytes)) {
                is InlineDecode.Ok -> decoded.bytes
                InlineDecode.TooLarge -> return NoticeAttachmentFileResult.TooLarge(maxInlineBytes)
                InlineDecode.Bad -> return NoticeAttachmentFileResult.Failed("附件数据损坏，无法打开")
            }

            NoticeAttachmentSource.Kind.NONE -> return NoticeAttachmentFileResult.Unsupported("附件内容不可用")
        }
        if (bytes.size.toLong() > limit) return NoticeAttachmentFileResult.TooLarge(limit)
        val file = withContext(Dispatchers.IO) { sink.write(key, fileName, bytes) }
            ?: return NoticeAttachmentFileResult.Failed("无法写入缓存文件，请稍后重试")
        return NoticeAttachmentFileResult.Ok(file, mimeFor(kind, trimmed, fileName), fromCache = false)
    }

    private sealed interface InlineDecode {
        data class Ok(val bytes: ByteArray) : InlineDecode
        data object TooLarge : InlineDecode
        data object Bad : InlineDecode
    }

    /** data URI → 字节（base64 或百分比编码；超限提前拒绝，避免先分配大数组）。 */
    private fun decodeInline(value: String, maxBytes: Long): InlineDecode {
        val comma = value.indexOf(',')
        if (comma < 0) return InlineDecode.Bad
        val meta = value.substring(0, comma)
        val payload = value.substring(comma + 1)
        if (payload.isEmpty()) return InlineDecode.Bad
        return try {
            if (meta.contains(";base64", ignoreCase = true)) {
                // base64 字符数 → 原始字节数 ≈ * 3/4（留 4 字符余量）
                if (payload.length / 4 * 3L > maxBytes + 3) return InlineDecode.TooLarge
                val bytes = Base64.getMimeDecoder().decode(payload)
                if (bytes.size.toLong() > maxBytes) {
                    InlineDecode.TooLarge
                } else {
                    InlineDecode.Ok(bytes)
                }
            } else {
                val bytes = URLDecoder.decode(payload, "UTF-8").toByteArray(Charsets.UTF_8)
                if (bytes.size.toLong() > maxBytes) InlineDecode.TooLarge else InlineDecode.Ok(bytes)
            }
        } catch (_: Throwable) {
            InlineDecode.Bad
        }
    }

    private fun mimeFor(kind: NoticeAttachmentSource.Kind, source: String, fileName: String): String {
        if (kind == NoticeAttachmentSource.Kind.INLINE) {
            return NoticeAttachmentSource.mimeOfDataUri(source) ?: mimeForFileName(fileName)
        }
        return mimeForFileName(fileName)
    }

    companion object {
        /** 缓存目录名（FileProvider `ct_file_paths.xml` 已声明 `notice_attachments/`）。 */
        const val CACHE_DIR: String = "notice_attachments"

        private const val MAX_FILE_NAME_LENGTH = 96
        private const val HEX = "0123456789abcdef"

        /** 附件源 → 缓存 key（SHA-256 前 16 hex；纯函数，可单测）。 */
        fun cacheKey(source: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(source.trim().toByteArray(Charsets.UTF_8))
            val builder = StringBuilder(digest.size * 2)
            for (byte in digest) {
                val value = byte.toInt() and 0xFF
                builder.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
            }
            return builder.substring(0, 16)
        }

        /**
         * 安全缓存文件名：保留展示名（打开后的标题更友好），去除路径分隔符/控制字符/`..`，
         * 空名用「附件」；无扩展名且 URL 路径带扩展名时补上；总长度截断保留扩展名。
         */
        fun fileNameFor(source: String, displayName: String): String {
            val cleanName = sanitizeFileName(displayName)
            val urlExt = extensionOfUrl(source)
            var base = when {
                cleanName.isEmpty() -> if (urlExt.isNotEmpty()) "附件.$urlExt" else "附件"
                urlExt.isNotEmpty() && !hasExtension(cleanName) -> "$cleanName.$urlExt"
                else -> cleanName
            }
            if (base.length > MAX_FILE_NAME_LENGTH) base = truncateKeepingExtension(base)
            return base.ifEmpty { "附件" }
        }

        /** MIME：按扩展名映射（未知 → octet-stream，交给系统选择器决定能否打开）。 */
        fun mimeForFileName(fileName: String): String = when (extensionOf(fileName)) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "zip" -> "application/zip"
            "rar" -> "application/vnd.rar"
            "7z" -> "application/x-7z-compressed"
            "txt" -> "text/plain"
            "md" -> "text/markdown"
            "csv" -> "text/csv"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            else -> "application/octet-stream"
        }

        private fun sanitizeFileName(raw: String): String {
            val cleaned = raw
                .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
                .replace(Regex("\\.{2,}"), "_")
                .trim()
                .trimStart('.')
                .trim()
            return if (cleaned.all { it == '_' || it.isWhitespace() }) "" else cleaned
        }

        private fun hasExtension(name: String): Boolean {
            val dot = name.lastIndexOf('.')
            return dot in 1 until name.length - 1
        }

        private fun extensionOf(name: String): String {
            val dot = name.lastIndexOf('.')
            if (dot !in 0 until name.length - 1) return ""
            val ext = name.substring(dot + 1).lowercase()
            return if (ext.matches(Regex("[a-z0-9]{1,8}"))) ext else ""
        }

        private fun extensionOfUrl(url: String): String {
            if (!NoticeAttachmentSource.isHttpUrl(url)) return ""
            return try {
                val path = java.net.URI(url).path.orEmpty()
                val segment = path.substringAfterLast('/')
                extensionOf(segment)
            } catch (_: Throwable) {
                ""
            }
        }

        private fun truncateKeepingExtension(name: String): String {
            val ext = extensionOf(name)
            if (ext.isEmpty()) return name.take(MAX_FILE_NAME_LENGTH)
            val suffix = ".$ext"
            if (suffix.length >= MAX_FILE_NAME_LENGTH / 2) return name.take(MAX_FILE_NAME_LENGTH)
            return name.take(MAX_FILE_NAME_LENGTH - suffix.length) + suffix
        }
    }
}

/**
 * OkHttp 实现：签名 URL 直出字节；只接受 http(s)（不写死域名）。
 *
 * - 下载走独立客户端：连接 15s / 读 30s / 整体 120s（20MB 上限 + 移动网络余量），
 *   与云 API 的 10s 客户端分开，避免大附件下载被 API 超时拖死；
 * - 先看 `Content-Length`，再流式读取计数，超过上限立即中止（不把 20MB+ 读进内存）。
 */
class OkHttpNoticeAttachmentFetcher(
    private val client: OkHttpClient = defaultDownloadClient(),
) : NoticeAttachmentFetcher {

    override suspend fun fetch(url: String, maxBytes: Long): NoticeAttachmentFetch =
        withContext(Dispatchers.IO) {
            if (!NoticeAttachmentSource.isHttpUrl(url)) return@withContext NoticeAttachmentFetch.Failed
            val request = try {
                Request.Builder().url(url.trim()).get().build()
            } catch (_: Throwable) {
                return@withContext NoticeAttachmentFetch.Failed
            }
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext NoticeAttachmentFetch.Failed
                    val body = response.body
                    val contentLength = body.contentLength()
                    if (contentLength > maxBytes) return@withContext NoticeAttachmentFetch.TooLarge
                    val expected = if (contentLength in 1..maxBytes) contentLength.toInt() else 8 * 1024
                    val output = ByteArrayOutputStream(expected)
                    val source = body.source()
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        total += read
                        if (total > maxBytes) return@withContext NoticeAttachmentFetch.TooLarge
                        output.write(buffer, 0, read)
                    }
                    NoticeAttachmentFetch.Bytes(output.toByteArray())
                }
            } catch (_: Throwable) {
                NoticeAttachmentFetch.Failed
            }
        }

    companion object {
        /** 64KiB 读缓冲（大文件少系统调用）。 */
        private const val DOWNLOAD_BUFFER_BYTES = 64 * 1024

        /** 下载专用客户端（不复用云 API 的短超时；重试交给 OkHttp 连接层）。 */
        fun defaultDownloadClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofSeconds(30))
            .writeTimeout(Duration.ofSeconds(30))
            .callTimeout(Duration.ofSeconds(120))
            .retryOnConnectionFailure(true)
            .build()
    }
}
