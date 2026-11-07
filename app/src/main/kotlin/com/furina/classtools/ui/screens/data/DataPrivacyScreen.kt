@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.data

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.furina.classtools.data.privacy.PrivacyClearReport
import com.furina.classtools.data.privacy.PrivacyExport
import com.furina.classtools.data.privacy.PrivacyExportResult
import com.furina.classtools.data.privacy.PrivacyRepository
import com.furina.classtools.data.privacy.PrivacySummary
import com.furina.classtools.data.session.Session
import com.furina.classtools.ui.components.CtLoading
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.EmptyState
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.screens.privacy.PrivacyPolicyDialog
import com.furina.classtools.ui.screens.privacy.UserAgreementDialog
import com.furina.classtools.ui.theme.CtShapeTokens
import java.io.File
import kotlinx.coroutines.launch

/** 页面弹层种类（预览可注入初始弹层）。 */
enum class DataPrivacyDialog {
    NONE,
    CLEAR_CLOUD,
    CLEAR_LOCAL,
    POLICY,
    AGREEMENT,
}

/**
 * 数据与隐私页（合规：导出我的数据 / 清除云端 / 清除本机 / 隐私政策回看）。
 *
 * 契约：`docs/PRIVACY_COMPLIANCE.md` 待办 #1/#2；`docs/api/ENDPOINTS.md`（userdata E13 / marks E11 / fitrecs E15/E16）。
 *
 * 隐私口径：
 * - 导出 JSON 不含 token、学号脱敏（`32***24`）、电费（power）不导出、名单原文/课表快照不导出；
 * - 清除云端为不可恢复操作，二次确认；网页/小程序同账号数据同样会被清空；
 * - 清除本机保留身份会话（重新同步可恢复可重建缓存）。
 *
 * 接线（Lead 在 AppContainer + Profile 入口 + 路由完成）：
 * ```kotlin
 * "dataPrivacy" -> DataPrivacyScreen(
 *     onBack = { navController.popBackStack() },
 *     session = session,
 *     repository = container.privacyRepository,
 * )
 * ```
 *
 * @param initialSummary 预览注入：概览数字
 * @param initialFeedback 预览注入：结果反馈文案
 * @param initialDialog 预览注入：初始弹层
 * @param skipLoad 预览/测试：跳过 loadSummary
 * @param forcedErrorMessage 预览注入：错误态
 */
@Composable
fun DataPrivacyScreen(
    onBack: () -> Unit,
    session: Session?,
    repository: PrivacyRepository?,
    modifier: Modifier = Modifier,
    skipLoad: Boolean = false,
    initialSummary: PrivacySummary? = null,
    initialFeedback: String? = null,
    initialDialog: DataPrivacyDialog = DataPrivacyDialog.NONE,
    forcedErrorMessage: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var summary by remember { mutableStateOf(initialSummary) }
    var feedback by remember { mutableStateOf(initialFeedback) }
    var feedbackIsError by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(initialDialog) }
    var working by remember { mutableStateOf<Working?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var phase by remember {
        mutableStateOf(
            when {
                forcedErrorMessage != null -> PrivacyPhase.ERROR
                session == null && !skipLoad -> PrivacyPhase.EMPTY
                skipLoad || repository == null || initialSummary != null -> PrivacyPhase.READY
                else -> PrivacyPhase.LOADING
            },
        )
    }
    var errorMessage by remember { mutableStateOf(forcedErrorMessage) }

    fun notify(message: String, isError: Boolean = false) {
        feedback = message
        feedbackIsError = isError
        scope.launch { snackbar.showSnackbar(message) }
    }

    LaunchedEffect(reloadToken) {
        if (skipLoad || forcedErrorMessage != null) {
            phase = PrivacyPhase.READY
            return@LaunchedEffect
        }
        if (session == null) {
            phase = PrivacyPhase.EMPTY
            return@LaunchedEffect
        }
        val repo = repository
        if (repo == null) {
            phase = PrivacyPhase.READY
            return@LaunchedEffect
        }
        if (summary == null) phase = PrivacyPhase.LOADING
        runCatching { repo.loadSummary() }
            .onSuccess {
                summary = it
                errorMessage = null
                phase = PrivacyPhase.READY
            }
            .onFailure {
                errorMessage = it.message ?: "数据概览加载失败"
                phase = if (summary == null) PrivacyPhase.ERROR else PrivacyPhase.READY
            }
    }

    fun exportData() {
        val repo = repository
        if (repo == null) {
            notify("功能尚未接线", isError = true)
            return
        }
        scope.launch {
            working = Working.EXPORT
            try {
                when (val result = repo.buildExport()) {
                    is PrivacyExportResult.Success -> {
                        val error = shareExport(context, result.export)
                        if (error == null) {
                            notify("已生成导出文件：${result.export.fileName}")
                        } else {
                            notify(error, isError = true)
                        }
                    }

                    is PrivacyExportResult.Failure -> notify(result.message, isError = true)
                }
            } finally {
                working = null
            }
        }
    }

    fun showClearReport(report: PrivacyClearReport) {
        notify(report.message, isError = !report.allOk)
        reloadToken += 1
    }
    fun clearCloud() {
        val repo = repository
        dialog = DataPrivacyDialog.NONE
        if (repo == null) {
            notify("功能尚未接线", isError = true)
            return
        }
        scope.launch {
            working = Working.CLEAR_CLOUD
            try {
                when (val result = repo.clearCloudData()) {
                    is com.furina.classtools.data.privacy.PrivacyClearCloudResult.Done ->
                        showClearReport(result.report)
                    is com.furina.classtools.data.privacy.PrivacyClearCloudResult.Failure ->
                        notify(result.message, isError = true)
                }
            } finally {
                working = null
            }
        }
    }


    fun clearLocal() {
        val repo = repository
        dialog = DataPrivacyDialog.NONE
        if (repo == null) {
            notify("功能尚未接线", isError = true)
            return
        }
        scope.launch {
            working = Working.CLEAR_LOCAL
            try {
                val result = repo.clearLocalData()
                notify(result.message, isError = !result.ok)
            } finally {
                working = null
            }
        }
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                CtTopBar(
                    title = "数据与隐私",
                    subtitle = session?.let { "账号 ${it.maskedSid}" } ?: "班级工具站",
                    onBack = onBack,
                    actions = {
                        IconButtonRefresh(enabled = working == null) { reloadToken += 1 }
                    },
                )

                Box(modifier = Modifier.weight(1f)) {
                    when (phase) {
                        PrivacyPhase.LOADING -> CtLoading(
                            modifier = Modifier.align(Alignment.Center),
                            text = "加载数据概览…",
                        )

                        PrivacyPhase.EMPTY -> EmptyState(
                            icon = Icons.Rounded.Shield,
                            title = "请先完成身份验证",
                            message = "数据与隐私功能需要登录后使用",
                        )

                        PrivacyPhase.ERROR -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            EmptyState(
                                icon = Icons.Rounded.ErrorOutline,
                                title = "数据概览加载失败",
                                message = errorMessage ?: "请稍后重试",
                            )
                            OutlinedButton(onClick = { reloadToken += 1 }) { Text("重试") }
                        }

                        PrivacyPhase.READY -> PrivacyContent(
                            summary = summary,
                            working = working,
                            feedback = feedback,
                            feedbackIsError = feedbackIsError,
                            onExport = { exportData() },
                            onClearCloud = { dialog = DataPrivacyDialog.CLEAR_CLOUD },
                            onClearLocal = { dialog = DataPrivacyDialog.CLEAR_LOCAL },
                            onPolicy = { dialog = DataPrivacyDialog.POLICY },
                            onAgreement = { dialog = DataPrivacyDialog.AGREEMENT },
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            )
        }
    }

    when (dialog) {
        DataPrivacyDialog.NONE -> Unit
        DataPrivacyDialog.CLEAR_CLOUD -> AlertDialog(
            onDismissRequest = { dialog = DataPrivacyDialog.NONE },
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
            title = { Text("清除云端数据？") },
            text = {
                Text(
                    "将清空本人在云端的 userdata、作业/通知标记与体测历史。\n" +
                        "此操作**不可恢复**；网页端/微信小程序同账号的数据也会被清空。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { clearCloud() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("确认清除") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = DataPrivacyDialog.NONE }) { Text("取消") }
            },
        )

        DataPrivacyDialog.CLEAR_LOCAL -> AlertDialog(
            onDismissRequest = { dialog = DataPrivacyDialog.NONE },
            icon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null) },
            title = { Text("清除本机数据？") },
            text = {
                Text(
                    "将清除本机缓存的班级名单、数据快照等可重建数据；保留身份会话，" +
                        "下次打开会重新从云端同步。",
                )
            },
            confirmButton = {
                TextButton(onClick = { clearLocal() }) { Text("确认清除") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = DataPrivacyDialog.NONE }) { Text("取消") }
            },
        )

        DataPrivacyDialog.POLICY -> PrivacyPolicyDialog(onDismiss = { dialog = DataPrivacyDialog.NONE })
        DataPrivacyDialog.AGREEMENT -> UserAgreementDialog(onDismiss = { dialog = DataPrivacyDialog.NONE })
    }
}

// -----------------------------------------------------------------------------
// 内容
// -----------------------------------------------------------------------------

@Composable
private fun PrivacyContent(
    summary: PrivacySummary?,
    working: Working?,
    feedback: String?,
    feedbackIsError: Boolean,
    onExport: () -> Unit,
    onClearCloud: () -> Unit,
    onClearLocal: () -> Unit,
    onPolicy: () -> Unit,
    onAgreement: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CtShapeTokens.toolCard,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("隐私说明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                PrivacyBullet("不显示手机号；学号仅脱敏展示（如 32***24）")
                PrivacyBullet("页面与导出图不含宿舍号；电费数据不导出")
                PrivacyBullet("导出文件只保存在本机，由你主动分享")
                PrivacyBullet("云同步可关闭；退出登录会清除本机会话")
            }
        }

        if (feedback != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = CtShapeTokens.listItem,
                color = if (feedbackIsError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (feedbackIsError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = feedback,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (feedbackIsError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CtShapeTokens.toolCard,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("我的数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    SummaryItem("同步配置", "${summary?.userdataSections ?: 0} 项")
                    SummaryItem("作业/通知标记", "${summary?.marksCount ?: 0} 条")
                    SummaryItem("体测历史", "${summary?.fitRecsCount ?: 0} 条")
                }
                HorizontalDivider()
                Text(
                    text = "数据来源：班级云服务（腾讯云 CloudBase）；本人可读写，仅用于班级工具功能。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CtShapeTokens.toolCard,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("数据管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                Button(
                    onClick = onExport,
                    enabled = working == null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = CtShapeTokens.pill,
                ) {
                    if (working == Working.EXPORT) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("导出我的数据（JSON）")
                }
                Text(
                    text = "不含 token 与学号明文；电费数据不导出；生成文件：`我的数据_YYYYMMDD-HHmm.json`",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedButton(
                    onClick = onClearCloud,
                    enabled = working == null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = CtShapeTokens.pill,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    if (working == Working.CLEAR_CLOUD) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("清除云端数据")
                }
                Text(
                    text = "清空云端 userdata、作业/通知标记与体测历史；不可恢复，网页/小程序同账号同步清空。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedButton(
                    onClick = onClearLocal,
                    enabled = working == null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = CtShapeTokens.pill,
                ) {
                    if (working == Working.CLEAR_LOCAL) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("清除本机数据")
                }
                Text(
                    text = "只清本机可重建缓存，保留身份会话，下次打开重新同步。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CtShapeTokens.toolCard,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .clickable(onClick = onPolicy),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Policy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("隐私政策", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        text = "查看",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .clickable(onClick = onAgreement),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("用户协议", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        text = "查看",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Text(
            text = "账号注销/数据删除请求：请联系班级管理员人工处理（班级群公告）。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Spacer(Modifier.navigationBarsPadding().height(24.dp))
    }
}

@Composable
private fun PrivacyBullet(text: String) {
    Row {
        Text("• ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IconButtonRefresh(enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick, enabled = enabled) {
        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
    }
}



// -----------------------------------------------------------------------------
// 导出文件分享
// -----------------------------------------------------------------------------

/** 写入 cache/exports 并用 FileProvider 分享 JSON；成功返回 null，失败返回可读错误。 */
private fun shareExport(context: Context, export: PrivacyExport): String? = runCatching {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, export.fileName)
    file.writeText(export.json, Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享我的数据"))
    null
}.getOrElse { error -> "导出失败：${error.message ?: "无法写入分享文件"}" }

/** 页面操作中的状态（按钮禁用/进度）。 */
private enum class Working { EXPORT, CLEAR_CLOUD, CLEAR_LOCAL }

/** 页面内部阶段（四态中的加载/错误/空态由它承载）。 */
private enum class PrivacyPhase { LOADING, READY, EMPTY, ERROR }
