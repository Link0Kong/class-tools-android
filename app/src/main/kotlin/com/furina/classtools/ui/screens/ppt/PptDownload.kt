package com.furina.classtools.ui.screens.ppt

import com.furina.classtools.ui.components.CtToastBus

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment

/**
 * 系统下载（契约 §11.3.4）：DownloadManager 保存到系统「下载」目录；
 * 失败退回浏览器打开。不申请存储权限（DownloadManager 自管）。
 *
 * 注：OSS 默认域名会带 `Content-Disposition: attachment`（force-download），
 * 对系统下载无影响；对在线播放的影响见 `docs/PPT_VIDEO_20261110.md` 已知限制。
 */
internal fun downloadToSystemDownloads(context: Context, url: String, fileName: String, mimeType: String? = null) {
    if (url.isBlank()) {
        CtToastBus.show("该演示没有可下载的文件")
        return
    }
    val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "演示文件" }
    try {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(safeName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
        if (!mimeType.isNullOrBlank()) request.setMimeType(mimeType)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        CtToastBus.show("开始下载：$safeName")
    } catch (_: Throwable) {
        val opened = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.isSuccess
        CtToastBus.show(if (opened) "已用浏览器打开" else "下载失败，请稍后重试")
    }
}


