package com.furina.classtools.ui.screens.ppt

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 下载完成后的系统动作（契约 §12.4.2）：FileProvider Uri → ACTION_VIEW 打开 / ACTION_SEND 分享。
 *
 * 文件保存在应用外部下载目录（`Android/data/<pkg>/files/Download/`，见 [PptFileDownloader]），
 * FileProvider 映射见 `res/xml/ct_file_paths.xml` 的 `ppt_downloads`。
 * 本项目不申请存储读取权限，因此不走公共 Downloads（红线）。
 */
internal object PptFileIntents {

    private fun authority(context: Context): String = "${context.packageName}.fileprovider"

    fun uriOf(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, authority(context), file)

    /** 打开 Intent：MIME 由下载行决定（pptx/pdf/ppt/octet-stream），交给系统选 PowerPoint/WPS/PDF 查看器。 */
    fun openIntent(context: Context, file: File, mimeType: String, title: String? = null): Intent {
        val uri = uriOf(context, file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "application/octet-stream" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = ClipData.newRawUri(title ?: file.name, uri)
        }
    }

    /** 分享 Intent（可发到聊天/邮件/云盘）。 */
    fun shareIntent(context: Context, file: File, mimeType: String, title: String? = null): Intent {
        val uri = uriOf(context, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType.ifBlank { "application/octet-stream" }
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!title.isNullOrBlank()) putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = ClipData.newRawUri(title ?: file.name, uri)
        }
    }

    /** 是否有能处理的应用（无 → UI 提示「未安装可打开的应用」，不崩溃）。 */
    fun canOpen(context: Context, intent: Intent): Boolean =
        runCatching { intent.resolveActivity(context.packageManager) != null }.getOrDefault(false)

    /** 尝试打开；无查看器时返回 false（不抛异常）。 */
    fun tryOpen(context: Context, file: File, mimeType: String, title: String? = null): Boolean {
        val intent = openIntent(context, file, mimeType, title)
        return try {
            context.startActivity(Intent.createChooser(intent, "打开方式").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    /** 调起分享面板；无可用应用返回 false。 */
    fun tryShare(context: Context, file: File, mimeType: String, title: String? = null): Boolean {
        val intent = shareIntent(context, file, mimeType, title)
        return try {
            context.startActivity(Intent.createChooser(intent, "分享")
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            true
        } catch (_: Throwable) {
            false
        }
    }
}
