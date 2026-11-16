package com.furina.classtools.ui.components.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furina.classtools.data.update.DownloadedApk
import com.furina.classtools.data.update.UpdateCheckStatus
import com.furina.classtools.data.update.UpdateDialogPolicy
import com.furina.classtools.data.update.UpdateDialogUi
import com.furina.classtools.data.update.UpdateDownloadProgress
import com.furina.classtools.data.update.UpdateDownloadState
import com.furina.classtools.data.update.UpdateFormat
import com.furina.classtools.data.update.UpdateInfo
import com.furina.classtools.data.update.UpdateManager
import com.furina.classtools.data.update.UpdateRuntime
import com.furina.classtools.data.update.UpdateTestVersion
import com.furina.classtools.data.update.UpdateVersion
import com.furina.classtools.ui.theme.ClassToolsTheme

/**
 * 更新弹窗（无状态渲染；状态由 [UpdateManager.dialog] 提供）。
 *
 * 标题固定带「测试版本」（[UpdateTestVersion.dialogTitle]）：
 * 「发现新版本 · 测试版本」。
 *
 * 按钮语义：
 * - Idle：「立即更新」开始下载；非强制可「稍后」；
 * - Downloading：进度条 + 「取消下载」（force 版本不可取消）；
 * - Ready：「立即安装」/「重新安装」（下载完成 Manager 已自动拉起系统安装器）；
 * - 未授权「安装未知应用」：显示说明 + 「去授权」；
 * - force=true：不可取消（无「稍后」、无「取消下载」）。
 */
@Composable
fun UpdateDialog(
    ui: UpdateDialogUi,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenInstallSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = ui.info
    val dismissible = UpdateDialogPolicy.isDismissible(info)
    AlertDialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        modifier = modifier,
        title = {
            Column {
                Text(
                    text = UpdateTestVersion.dialogTitle(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "v${UpdateVersion.normalize(info.versionName)} · 版本号 ${info.versionCode}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (info.notes.isNotBlank()) {
                    Text(
                        text = info.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (info.channel.isNotBlank() && info.channel != UpdateInfo.DEFAULT_CHANNEL) {
                    Text(
                        text = "渠道：${info.channel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DownloadStatus(ui = ui)
                if (ui.installPermissionBlocked) {
                    Text(
                        text = "需要允许「安装未知应用」：请在系统设置中为本应用打开开关，返回后点「立即安装」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (info.force && ui.download !is UpdateDownloadState.Ready) {
                    Text(
                        text = "此版本为强制更新，完成更新前请勿退出。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            when (val download = ui.download) {
                is UpdateDownloadState.Idle -> TextButton(onClick = onDownload) {
                    Text("立即更新")
                }

                is UpdateDownloadState.Downloading -> TextButton(onClick = {}, enabled = false) {
                    Text("下载中…")
                }

                is UpdateDownloadState.Ready -> TextButton(onClick = onInstall) {
                    Text(if (ui.installLaunched) "重新安装" else "立即安装")
                }

                is UpdateDownloadState.Failed -> TextButton(onClick = onDownload) {
                    Text("重试")
                }
            }
        },
        dismissButton = {
            if (ui.installPermissionBlocked) {
                TextButton(onClick = onOpenInstallSettings) {
                    Text("去授权")
                }
            } else if (dismissible) {
                if (ui.download is UpdateDownloadState.Downloading) {
                    TextButton(onClick = onCancelDownload) {
                        Text("取消下载")
                    }
                } else {
                    TextButton(onClick = onDismiss) {
                        Text("稍后")
                    }
                }
            }
        },
    )
}

/** 弹窗内的下载状态行（未下载 / 进度 / 已完成 / 失败）。 */
@Composable
private fun DownloadStatus(ui: UpdateDialogUi) {
    when (val download = ui.download) {
        is UpdateDownloadState.Idle -> {
            Text(
                text = if (ui.info.force) "需要下载新版本后继续使用。" else "点击「立即更新」下载安装包。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is UpdateDownloadState.Downloading -> {
            val progress = download.progress
            LinearProgressIndicator(
                progress = { progress.percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "正在下载安装包…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = progressText(progress),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is UpdateDownloadState.Ready -> {
            Text(
                text = if (ui.installLaunched) {
                    "已打开系统安装器，请在系统界面确认安装。"
                } else {
                    "下载完成，正在打开系统安装器…"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        is UpdateDownloadState.Failed -> {
            Text(
                text = download.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun progressText(progress: UpdateDownloadProgress): String {
    val downloaded = UpdateFormat.bytes(progress.downloadedBytes)
    return if (progress.totalBytes > 0L) {
        "${progress.percent}% · $downloaded / ${UpdateFormat.bytes(progress.totalBytes)}"
    } else {
        downloaded
    }
}

/**
 * 全局更新宿主（App 根挂载一次）：
 * - 登录后触发每天最多一次的静默检查（[UpdateManager.maybeCheckDaily]）；
 * - 渲染全局更新弹窗，并在挂载期间标记 [UpdateManager.attachHost]（「我的」页据此不再重复弹窗）。
 */
@Composable
fun UpdateHost(
    loggedIn: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val manager = remember(context) { UpdateRuntime.manager(context) }
    DisposableEffect(manager) {
        manager.attachHost()
        onDispose { manager.detachHost() }
    }
    LaunchedEffect(loggedIn) {
        // 「启动登录后」：已登录会话就绪（含冷启动恢复）时静默检查；退出再登录同一天不会重复请求。
        if (loggedIn) manager.maybeCheckDaily()
    }
    val dialog by manager.dialog.collectAsStateWithLifecycle()
    val ui = dialog ?: return
    UpdateDialog(
        ui = ui,
        onDownload = { manager.startDownload() },
        onCancelDownload = { manager.cancelDownload() },
        onInstall = { manager.install() },
        onOpenInstallSettings = { manager.openInstallPermissionSettings() },
        onDismiss = { manager.dismissDialog() },
        modifier = modifier,
    )
}

/**
 * 局部弹窗宿主：没有全局 [UpdateHost]（例如独立预览/单页调试）时由「我的」页兜底渲染，
 * 避免检测到新版本却看不到弹窗。全局宿主挂载后自动让位（[UpdateManager.hostAttached]）。
 */
@Composable
fun UpdateDialogHost(
    manager: UpdateManager,
    modifier: Modifier = Modifier,
) {
    val dialog by manager.dialog.collectAsStateWithLifecycle()
    val ui = dialog ?: return
    UpdateDialog(
        ui = ui,
        onDownload = { manager.startDownload() },
        onCancelDownload = { manager.cancelDownload() },
        onInstall = { manager.install() },
        onOpenInstallSettings = { manager.openInstallPermissionSettings() },
        onDismiss = { manager.dismissDialog() },
        modifier = modifier,
    )
}

/** 「我的 → 检查更新」行副标题文案（纯函数，便于预览/测试）。 */
fun updateCheckSubtitle(status: UpdateCheckStatus): String = when (status) {
    is UpdateCheckStatus.Idle -> "检查是否有新版本"
    is UpdateCheckStatus.Checking -> "正在检查…"
    is UpdateCheckStatus.UpToDate -> "已是最新版本"
    is UpdateCheckStatus.Available -> "发现新版本 v${UpdateVersion.normalize(status.info.versionName)}"
    is UpdateCheckStatus.Failed -> "检查失败，点击重试"
}

// =============================================================================
// 预览
// =============================================================================

private val previewInfo = UpdateInfo(
    versionCode = 79,
    versionName = "1.0.0-beta1",
    apkUrl = "http://121.196.47.2/android/classtools-1.0.0-beta1.apk",
    notes = "1. 新增检查更新与自动更新\n2. 测试版本 功能验证\n3. 修复若干已知问题",
    force = false,
    channel = "test",
    sha256 = "a".repeat(64),
)

private val previewForceInfo = previewInfo.copy(force = true, versionName = "4.58.0-test1")

@Preview(name = "301 更新弹窗 · 发现新版本", showBackground = true, widthDp = 412)
@Composable
private fun UpdateDialogAvailablePreview() {
    ClassToolsTheme(darkTheme = false) {
        UpdateDialog(
            ui = UpdateDialogUi(info = previewInfo),
            onDownload = {},
            onCancelDownload = {},
            onInstall = {},
            onOpenInstallSettings = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "302 更新弹窗 · 下载中", showBackground = true, widthDp = 412)
@Composable
private fun UpdateDialogDownloadingPreview() {
    ClassToolsTheme(darkTheme = false) {
        UpdateDialog(
            ui = UpdateDialogUi(
                info = previewInfo,
                download = UpdateDownloadState.Downloading(
                    UpdateDownloadProgress(8_388_608L, 24_117_248L, 35),
                ),
            ),
            onDownload = {},
            onCancelDownload = {},
            onInstall = {},
            onOpenInstallSettings = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "303 更新弹窗 · 强制更新深色", showBackground = true, widthDp = 412)
@Composable
private fun UpdateDialogForcePreview() {
    ClassToolsTheme(darkTheme = true) {
        UpdateDialog(
            ui = UpdateDialogUi(
                info = previewForceInfo,
                download = UpdateDownloadState.Ready(
                    DownloadedApk(fileName = "classtools-4.58.0-test1.apk"),
                ),
            ),
            onDownload = {},
            onCancelDownload = {},
            onInstall = {},
            onOpenInstallSettings = {},
            onDismiss = {},
        )
    }
}
