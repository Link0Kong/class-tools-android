package com.furina.classtools.data.cloudspace

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
 * 上传字节源抽象（任意大小；只要支持「按偏移读取一段」）。
 *
 * 生产实现 [CloudSpaceUriSource]（SAF Uri）；JVM 单测用内存假实现，不必读真实文件。
 */
interface CloudSpaceByteSource {
    val name: String
    val size: Long
    val mimeType: String

    /** 读取 `[offset, offset + length)` 原始字节；越界时返回实际可读的部分。 */
    suspend fun read(offset: Long, length: Int): ByteArray
}

/**
 * Android 端分片字节源：SAF 多选（`ActivityResultContracts.OpenMultipleDocuments`）返回的 Uri。
 *
 * 关键实现点（对齐 `data/ppt/PptUriSource`，但**不限制大小**）：
 * - 文件名/大小用 `ContentResolver.query(OpenableColumns)`；provider 不提供大小时流式计数；
 * - **分片读取不整文件进内存**：`FileInputStream.channel.position(offset)` 定位后只读本片；
 *   provider 返回不可 seek 的管道时回退 `skip` 循环；
 * - 每次 [read] 打开一次 fd、读完即关（避免大文件长期占用句柄）。
 */
class CloudSpaceUriSource private constructor(
    private val context: Context,
    private val uri: Uri,
    override val name: String,
    override val size: Long,
    override val mimeType: String,
) : CloudSpaceByteSource {

    override suspend fun read(offset: Long, length: Int): ByteArray = withContext(Dispatchers.IO) {
        if (length <= 0) return@withContext ByteArray(0)
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("无法打开所选文件")
        try {
            FileInputStream(descriptor.fileDescriptor).use { stream ->
                val seekable = try {
                    stream.channel.position(offset)
                    true
                } catch (_: Throwable) {
                    false // 管道 / FUSE 流可能不可 seek
                }
                if (!seekable) skipFully(stream, offset)
                val buffer = ByteArray(length)
                var read = 0
                while (read < length) {
                    val count = stream.read(buffer, read, length - read)
                    if (count < 0) break
                    read += count
                }
                if (read == length) buffer else buffer.copyOf(read)
            }
        } finally {
            runCatching { descriptor.close() }
        }
    }

    private fun skipFully(stream: FileInputStream, offset: Long) {
        var remaining = offset
        val scratch = ByteArray(64 * 1024)
        while (remaining > 0L) {
            val skipped = stream.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
                continue
            }
            val count = stream.read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
            if (count < 0) throw IOException("文件在读取过程中被截断")
            remaining -= count
        }
    }

    companion object {
        /**
         * 打开 SAF 选择结果并解析展示名/大小/MIME。
         *
         * @return null = 无法读取（provider 拒绝 / 文件消失 / 大小不可得），UI 提示重新选择。
         */
        suspend fun open(context: Context, uri: Uri): CloudSpaceUriSource? = withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val (queriedName, queriedSize) = queryNameAndSize(resolver, uri)
            val mimeType = runCatching { resolver.getType(uri) }.getOrNull()
                .orEmpty()
                .ifBlank { "application/octet-stream" }
            val name = CloudSpaceLogic.safeName(
                queriedName?.trim().orEmpty().ifBlank { "文件" },
            )
            val size = if (queriedSize > 0L) queriedSize else countSize(resolver, uri)
            if (size < 0L) return@withContext null
            CloudSpaceUriSource(context.applicationContext, uri, name, size, mimeType)
        }

        private fun queryNameAndSize(resolver: ContentResolver, uri: Uri): Pair<String?, Long> {
            var cursor: Cursor? = null
            return try {
                cursor = resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null,
                )
                if (cursor == null || !cursor.moveToFirst()) return null to -1L
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    cursor.getLong(sizeIndex)
                } else {
                    -1L
                }
                name to size
            } catch (_: Throwable) {
                null to -1L
            } finally {
                runCatching { cursor?.close() }
            }
        }

        /** provider 不提供大小时流式计数（任意大小，不设上限；失败返回 -1）。 */
        private fun countSize(resolver: ContentResolver, uri: Uri): Long = try {
            resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(256 * 1024)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                }
                total
            } ?: -1L
        } catch (_: Throwable) {
            -1L
        }
    }
}
