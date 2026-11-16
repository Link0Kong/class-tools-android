package com.furina.classtools.data.timetable

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 课表接入的**文件落盘 + 系统 Intent**（Android 依赖层；纯逻辑在 [TimetableIcs] / [WakeUpSchedule]）。
 *
 * - 分享/打开：写 `cacheDir/exports/` 后经 Manifest 已声明的 `FileProvider`
 *   （authority `${applicationId}.fileprovider`，`res/xml/ct_file_paths.xml` 的 `exports/`）授权给目标 App；
 * - 保存到下载：Android 10+ 走 `MediaStore.Downloads`（无需存储权限），供 WakeUp「备份与恢复 → 从文件导入」
 *   或系统文件管理器选取；Android 9 及以下返回 null（UI 提示用分享）。
 *
 * 文件命名固定（同一学期重复导出覆盖缓存同名文件，不累积垃圾）：
 * `课表_<开学日>.ics` / `课表_<开学日>.wakeup_schedule`。
 */
object TimetableLinkFiles {

    const val MIME_ICS: String = "text/calendar"
    const val MIME_WAKEUP: String = "application/octet-stream"

    private const val EXPORT_DIR = "exports"
    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** 一个待分享/保存的导出文件。 */
    data class ExportFile(
        val file: File,
        val displayName: String,
        val mime: String,
    )

    // =========================================================================
    // 写入缓存目录（FileProvider 可共享）
    // =========================================================================

    suspend fun writeIcs(
        context: Context,
        document: TimetableIcs.IcsDocument,
        termStart: LocalDate?,
    ): ExportFile = withContext(Dispatchers.IO) {
        writeTextFile(
            context = context,
            displayName = "课表_${dateTag(termStart)}.ics",
            mime = MIME_ICS,
            text = document.text,
        )
    }

    suspend fun writeWakeUp(
        context: Context,
        document: WakeUpSchedule.WakeUpDocument,
        termStart: LocalDate?,
    ): ExportFile = withContext(Dispatchers.IO) {
        writeTextFile(
            context = context,
            displayName = "课表_${dateTag(termStart)}.${WakeUpSchedule.EXTENSION}",
            mime = MIME_WAKEUP,
            text = document.text,
        )
    }

    private fun writeTextFile(
        context: Context,
        displayName: String,
        mime: String,
        text: String,
    ): ExportFile {
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        // 清理同类旧文件，避免缓存无限增长（文件管理器若正持有旧 Uri 不受影响）
        val suffix = displayName.substringAfterLast('.')
        dir.listFiles()?.forEach { old ->
            if (old.isFile && old.name.endsWith(".$suffix")) runCatching { old.delete() }
        }
        val file = File(dir, displayName)
        file.writeText(text, Charsets.UTF_8)
        return ExportFile(file = file, displayName = displayName, mime = mime)
    }

    private fun dateTag(termStart: LocalDate?): String = termStart?.format(DATE_FORMAT) ?: "我的课表"

    // =========================================================================
    // Intent
    // =========================================================================

    /** FileProvider Uri（authority 与 AndroidManifest 的 `${applicationId}.fileprovider` 对应）。 */
    fun fileUri(context: Context, export: ExportFile): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", export.file)

    /** 分享文件（系统分享面板；日历 App / 文件管理器 / 聊天工具均可接收）。 */
    fun shareIntent(context: Context, export: ExportFile, chooserTitle: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = export.mime
            putExtra(Intent.EXTRA_STREAM, fileUri(context, export))
            putExtra(Intent.EXTRA_TITLE, export.displayName)
            putExtra(Intent.EXTRA_SUBJECT, export.displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return Intent.createChooser(send, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** 用系统日历/其他 App 直接打开 .ics（ACTION_VIEW）。 */
    fun viewIcsIntent(context: Context, export: ExportFile): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(fileUri(context, export), MIME_ICS)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // =========================================================================
    // 保存到系统「下载」（Android 10+ 无需存储权限；WakeUp 导入最稳的路径）
    // =========================================================================

    /**
     * 保存到系统下载目录（`MediaStore.Downloads`）。
     *
     * @return 保存后的 Uri；Android 9 及以下（无存储权限）/ 写入失败返回 null（由 UI 提示改用分享）
     */
    suspend fun saveToDownloads(context: Context, export: ExportFile): Uri? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext null
        }
        runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, export.displayName)
                put(MediaStore.Downloads.MIME_TYPE, export.mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching null
            resolver.openOutputStream(uri)?.use { output ->
                output.write(export.file.readBytes())
            } ?: error("无法写入下载目录")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.getOrNull()
    }
}
