package com.furina.classtools.features.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * 导出卡片的保存/分享（Android 10+ 用 MediaStore，免存储权限；分享统一走 FileProvider）。
 * 隐私：只在用户主动操作时写文件；文件名不含学号/姓名等隐私字段。
 */
object ExportCardFiles {

    private const val ALBUM = "班级工具站"

    /** 保存到相册 `Pictures/班级工具站`。返回 content Uri；Android 9 及以下返回 null（降级为分享）。 */
    fun saveToGallery(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 10 以下写系统相册需要 WRITE_EXTERNAL_STORAGE；这里不申请存储权限，降级为分享。
            return null
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: return null
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (_: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    /** 写入缓存目录并生成分享 Intent（FileProvider）。 */
    fun shareIntent(context: Context, bitmap: Bitmap, fileName: String): Intent? {
        val file = writeCacheFile(context, bitmap, fileName) ?: return null
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** 写入应用缓存 `exports/`（如需自定义保存路径）。 */
    fun writeCacheFile(context: Context, bitmap: Bitmap, fileName: String): File? {
        return runCatching {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file
        }.getOrNull()
    }
}
