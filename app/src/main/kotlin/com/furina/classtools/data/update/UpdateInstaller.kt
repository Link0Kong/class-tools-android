package com.furina.classtools.data.update

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** 拉起系统安装器的结果（UI 据此提示「去允许安装未知应用」/「无安装程序」）。 */
enum class UpdateInstallLaunch {
    /** 已打开系统安装器（用户还需在系统界面点「安装」确认，Android 不允许静默安装）。 */
    LAUNCHED,

    /** 需要用户先到系统设置允许「安装未知应用」（API 26+）。 */
    NEED_UNKNOWN_SOURCES_PERMISSION,

    /** 没有能处理安装 Intent 的应用（极少数 ROM）/ 安装包文件不存在。 */
    NO_HANDLER,

    /** 安装包不存在（下载结果丢失/被清理）。 */
    NO_APK,
}

/**
 * APK 安装（FileProvider + ACTION_VIEW / ACTION_INSTALL_PACKAGE）。
 *
 * 契约（Android 限制）：
 * - 不做静默安装：下载完成只拉起系统安装器，由用户在系统界面确认；
 * - targetSdk 26+ 必须声明 `REQUEST_INSTALL_PACKAGES`（Manifest 已加），且用户需在
 *   「安装未知应用」里授权本 App；未授权时返回 [UpdateInstallLaunch.NEED_UNKNOWN_SOURCES_PERMISSION]，
 *   UI 提供「去授权」按钮（[openUnknownSourcesSettings]）；
 * - Uri 解析顺序：DownloadManager content URI（系统下载提供者）→ FileProvider(本机路径)。
 *   对应 `res/xml/ct_file_paths.xml` 的 `update_public_downloads` / `update_cache` 路径。
 */
object UpdateInstaller {

    /** 系统安装器的 APK MIME。 */
    private const val MIME_APK: String = "application/vnd.android.package-archive"

    fun authority(context: Context): String = "${context.packageName}.fileprovider"

    /** 是否已允许安装未知应用（API 26+；读取失败按已允许处理，交给系统安装器再校验）。 */
    fun canRequestPackageInstalls(context: Context): Boolean =
        runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(true)

    /** 解析可交付给安装器的 Uri（contentUri 优先；本机路径经 FileProvider）。 */
    fun resolveApkUri(context: Context, apk: DownloadedApk): Uri? {
        apk.contentUri?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
            runCatching { Uri.parse(raw) }.getOrNull()?.let { return it }
        }
        val path = apk.filePath?.trim().orEmpty()
        if (path.isEmpty()) return null
        val file = File(path)
        if (!file.isFile) return null
        return runCatching {
            FileProvider.getUriForFile(context, authority(context), file)
        }.getOrNull()
    }

    /** 安装 Intent（ACTION_VIEW 优先，ACTION_INSTALL_PACKAGE 兜底；两者都带读授权）。 */
    fun buildInstallIntents(context: Context, apk: DownloadedApk): List<Intent> {
        val uri = resolveApkUri(context, apk) ?: return emptyList()
        return buildInstallIntents(uri)
    }

    private fun buildInstallIntents(uri: Uri): List<Intent> {
        val clip = ClipData.newRawUri("update.apk", uri)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            this.clipData = clip
        }
        val installPackage = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, MIME_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            this.clipData = clip
        }
        return listOf(view, installPackage)
    }

    /** 拉起系统安装器（不抛异常；所有失败都映射为 [UpdateInstallLaunch]）。 */
    fun launch(context: Context, apk: DownloadedApk): UpdateInstallLaunch {
        val appContext = context.applicationContext ?: context
        if (!canRequestPackageInstalls(appContext)) {
            return UpdateInstallLaunch.NEED_UNKNOWN_SOURCES_PERMISSION
        }
        val intents = buildInstallIntents(appContext, apk)
        if (intents.isEmpty()) return UpdateInstallLaunch.NO_APK
        for (intent in intents) {
            try {
                appContext.startActivity(intent)
                return UpdateInstallLaunch.LAUNCHED
            } catch (_: ActivityNotFoundException) {
                // 尝试下一个（ACTION_INSTALL_PACKAGE）。
            } catch (_: SecurityException) {
                return UpdateInstallLaunch.NEED_UNKNOWN_SOURCES_PERMISSION
            } catch (_: Throwable) {
                // 忽略，尝试下一个。
            }
        }
        return UpdateInstallLaunch.NO_HANDLER
    }

    /** 跳系统「安装未知应用」授权页；无对应页面时回退应用详情页（不崩溃）。 */
    fun openUnknownSourcesSettings(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        val packageUri = Uri.parse("package:${appContext.packageName}")
        val candidates = listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                appContext.startActivity(intent)
                return true
            } catch (_: Throwable) {
                // 下一个。
            }
        }
        return false
    }
}
