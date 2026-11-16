package com.furina.classtools.data.update

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * 已下载 APK 的 sha256 完整性校验（远端 latest.json 的 `sha256` 字段）。
 *
 * - DownloadManager 公共 Downloads 路径：优先用 ContentResolver 打开返回的 content URI 流式读取；
 *   不可读时退回本机路径；
 * - OkHttp 回退路径已在下载时校验（这里通常不需要重复）；
 * - 返回 null = 无法读取（跳过校验，仍允许安装），避免因系统 ROM 权限差异卡死更新。
 */
object UpdateApkVerifier {

    private const val BUFFER_SIZE = 64 * 1024

    /** true=校验通过；false=不匹配（应删除并重下）；null=无法读取/未提供校验值（跳过）。 */
    fun verify(context: Context, apk: DownloadedApk, expectedSha256: String): Boolean? {
        if (expectedSha256.isBlank()) return true
        val appContext: Context = context.applicationContext ?: context
        val input = openStream(appContext, apk) ?: return null
        return try {
            input.use { stream -> UpdateDigest.matches(expectedSha256, sha256Hex(stream)) }
        } catch (_: Throwable) {
            null
        }
    }

    private fun openStream(context: Context, apk: DownloadedApk): InputStream? {
        apk.contentUri?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(raw))
            }.getOrNull()?.let { return it }
        }
        val path = apk.filePath?.trim().orEmpty()
        if (path.isEmpty()) return null
        val file = File(path)
        if (!file.isFile) return null
        return runCatching { file.inputStream() }.getOrNull()
    }

    private fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
