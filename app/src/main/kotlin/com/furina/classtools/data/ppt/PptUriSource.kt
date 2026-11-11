package com.furina.classtools.data.ppt

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileInputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android 端分片字节源：SAF Uri（`ActivityResultContracts.OpenDocument` → `.ppt/.pptx`）。
 *
 * 关键实现点（契约 §3.2 + Lead 提醒）：
 * - 文件名/大小用 `ContentResolver.query(OpenableColumns)` 读取；provider 不给大小时流式计数（上限 64MiB+1 提前停）；
 * - **分片读取不整文件进内存**：优先 `FileInputStream.channel.position(offset)` 定位后读 length 字节；
 *   provider 返回不可 seek 的管道时回退 `skip` 循环；
 * - 每次 [read] 打开一次 fd、读完即关（128 片以内开销可接受，避免长期占用文件句柄）。
 */
class PptUriSource private constructor(
    private val context: Context,
    private val uri: Uri,
    override val name: String,
    override val size: Long,
) : PptByteSource {

    override suspend fun read(offset: Long, length: Int): ByteArray = withContext(Dispatchers.IO) {
        if (length <= 0) return@withContext ByteArray(0)
        val resolver = context.contentResolver
        val pfd = resolver.openFileDescriptor(uri, "r")
            ?: throw IOException("无法打开所选文件")
        try {
            FileInputStream(pfd.fileDescriptor).use { stream ->
                val seekable = try {
                    stream.channel.position(offset)
                    true
                } catch (_: Throwable) {
                    false // 管道/FUSE 流可能不可 seek
                }
                if (!seekable) skipFully(stream, offset)
                val buffer = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = stream.read(buffer, read, length - read)
                    if (n < 0) break
                    read += n
                }
                if (read == length) buffer else buffer.copyOf(read)
            }
        } finally {
            runCatching { pfd.close() }
        }
    }

    private fun skipFully(stream: FileInputStream, offset: Long) {
        var remaining = offset
        val scratch = ByteArray(64 * 1024)
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
                continue
            }
            val n = stream.read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
            if (n < 0) throw IOException("文件在读取过程中被截断")
            remaining -= n
        }
    }

    companion object {
        /** 允许的 MIME（部分 provider 只报这些；扩展名仍是主判据）。 */
        private val ACCEPTED_MIME = setOf(
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/octet-stream",
            "application/zip",
        )

        /**
         * 打开选择结果并解析展示名/大小。
         *
         * @return null = 无法读取（provider 拒绝/文件消失），UI 提示重新选择。
         */
        suspend fun open(context: Context, uri: Uri): PptUriSource? = withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val queried = queryNameAndSize(resolver, uri)
            val mime = runCatching { resolver.getType(uri) }.getOrNull().orEmpty()
            val rawName = queried.first?.trim().orEmpty()
            val name = normalizeName(rawName, mime)
            var size = queried.second
            if (size <= 0L) {
                size = countSizeCap(context, uri)
            }
            if (name.isBlank() && size <= 0L) return@withContext null
            PptUriSource(context.applicationContext, uri, name, size)
        }

        /** 展示名补扩展名：provider 只给 MIME 时按 MIME 补 `.ppt/.pptx`。 */
        private fun normalizeName(raw: String, mime: String): String {
            if (raw.isNotBlank()) return raw
            return when (mime) {
                "application/vnd.ms-powerpoint" -> "演示.ppt"
                else -> "演示.pptx"
            }
        }

        private fun queryNameAndSize(resolver: ContentResolver, uri: Uri): Pair<String?, Long> {
            var cursor: Cursor? = null
            return try {
                cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                if (cursor == null || !cursor.moveToFirst()) return null to -1L
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
                name to size
            } catch (_: Throwable) {
                null to -1L
            } finally {
                runCatching { cursor?.close() }
            }
        }

        /** provider 不提供大小时流式计数；超过 64MiB 立即停止（返回 64MiB+1 让上层按「超限」拒绝）。 */
        private fun countSizeCap(context: Context, uri: Uri): Long {
            val cap = com.furina.classtools.features.ppt.PptLimits.MAX_SOURCE_BYTES
            return try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(256 * 1024)
                    var total = 0L
                    while (total <= cap) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                    }
                    if (total > cap) cap + 1 else total
                } ?: -1L
            } catch (_: Throwable) {
                -1L
            }
        }
    }
}
