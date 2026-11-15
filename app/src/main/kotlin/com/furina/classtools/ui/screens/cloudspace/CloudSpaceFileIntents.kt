package com.furina.classtools.ui.screens.cloudspace

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/**
 * 云空间下载完成后的系统动作：FileProvider Uri → ACTION_VIEW 打开。
 *
 * 文件保存在应用外部下载目录（`Android/data/<pkg>/files/Download/`，见
 * `data/cloudspace/OkHttpCloudSpaceDownloader`）；FileProvider 映射复用工程已有的
 * `res/xml/ct_file_paths.xml`（`external-files-path … path="Download/"` 与 `cache-path … exports/`），
 * **本功能不新增/修改 res 资源**。项目不申请存储读取权限，因此不写公共 Downloads（红线）。
 */
internal object CloudSpaceFileIntents {

    private fun authority(context: Context): String = "${context.packageName}.fileprovider"

    fun uriOf(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, authority(context), file)

    fun openIntent(context: Context, file: File, mimeType: String, title: String? = null): Intent {
        val uri = uriOf(context, file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "application/octet-stream" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = ClipData.newRawUri(title ?: file.name, uri)
        }
    }

    /** 尝试打开；无可用查看器/系统异常时返回 false（不崩溃、不抛异常）。 */
    fun tryOpen(context: Context, file: File, mimeType: String, title: String? = null): Boolean = try {
        val intent = openIntent(context, file, mimeType, title)
        context.startActivity(
            Intent.createChooser(intent, "打开方式").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: Throwable) {
        false
    }

    /**
     * 打开用的 MIME：优先服务端返回的 mime；`octet-stream`/空值按扩展名用系统 MimeTypeMap 兜底
     * （例如 .pptx/.zip/.mp4），都没有再退回 octet-stream（系统仍会按扩展名让用户选应用）。
     */
    fun mimeTypeOf(declaredMime: String, fileName: String): String {
        val declared = declaredMime.trim()
        if (declared.isNotEmpty() && declared != "application/octet-stream") return declared
        val extension = fileName.substringAfterLast('.', "").trim().lowercase()
        if (extension.isEmpty()) return "application/octet-stream"
        return runCatching {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
    }
}
