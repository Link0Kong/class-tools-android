@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furina.classtools.data.permission.PermissionController
import com.furina.classtools.data.permission.PermissionState
import com.furina.classtools.data.permission.PermissionTexts
import com.furina.classtools.data.permission.PermissionType
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.glass.CtGlassCard
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtTokens

/**
 * 权限与提醒设置（v2.0.0）。
 *
 * - 通知：Android 13+ 运行时权限；拒绝时引导系统设置；说明「只用于作业/上课本机提醒」。
 * - 日历：读写日历权限；拒绝时写日程走系统 ACTION_INSERT 兜底（由业务页处理）。
 * 权限只服务本地功能，不上传任何数据。
 */
@Composable
fun PermissionSettingsScreen(
    onBack: () -> Unit,
    controller: PermissionController,
    modifier: Modifier = Modifier,
    onOpenReminderSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }
    var pendingType by remember { mutableStateOf<PermissionType?>(null) }
    var showRationale by remember { mutableStateOf(false) }
    var showSettingsGuide by remember { mutableStateOf(false) }

    // refreshTick 参与读取，授权返回后强制重新检查。
    val snapshot = remember(refreshTick) { controller.snapshot() }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshTick++
    }

    fun request(type: PermissionType) {
        pendingType = type
        when (controller.state(type)) {
            PermissionState.GRANTED -> Unit
            PermissionState.REQUESTABLE, PermissionState.DENIED -> showRationale = true
            PermissionState.PERMANENTLY_DENIED -> showSettingsGuide = true
        }
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            CtTopBar(title = "权限与提醒", subtitle = "通知 · 日历", onBack = onBack)

            Column(
                modifier = Modifier.padding(
                    horizontal = CtTokens.Spacing.pageHorizontalCompact,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(CtTokens.Spacing.cardGapCompact),
            ) {
                CtGlassCard(
                    shape = CtShapeTokens.hero,
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            "权限只用于本地功能",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "通知用于作业截止与上课提醒；日历用于把课表/通知写成日程。数据均在本机处理，不上传，可随时关闭。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                PermissionCard(
                    type = PermissionType.NOTIFICATIONS,
                    state = snapshot.notifications,
                    icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
                    onAction = { request(PermissionType.NOTIFICATIONS) },
                )

                PermissionCard(
                    type = PermissionType.CALENDAR,
                    state = snapshot.calendar,
                    icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                    onAction = { request(PermissionType.CALENDAR) },
                )

                if (onOpenReminderSettings != null) {
                    CtGlassCard(
                        shape = CtShapeTokens.hero,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "提醒设置",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "作业截止与上课提醒的开关和提前时间",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = onOpenReminderSettings) { Text("设置") }
                        }
                    }
                }

                CtGlassCard(
                    shape = CtShapeTokens.hero,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "在系统设置中管理",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { context.startActivity(controller.settingsIntent()) },
                        ) { Text("打开") }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showRationale) {
        val type = pendingType
        val copy = type?.let { PermissionTexts.of(it) }
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(copy?.title ?: "需要权限") },
            text = { Text(copy?.rationale ?: "") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRationale = false
                        type?.let {
                            controller.markRequested(it)
                            val permissions = controller.permissionsToRequest(it)
                            if (permissions.isEmpty()) {
                                refreshTick++
                            } else {
                                launcher.launch(permissions)
                            }
                        }
                    },
                ) { Text(copy?.confirmLabel ?: "去开启") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text(copy?.cancelLabel ?: "暂不") }
            },
        )
    }

    if (showSettingsGuide) {
        val copy = pendingType?.let { PermissionTexts.of(it) }
        AlertDialog(
            onDismissRequest = { showSettingsGuide = false },
            title = { Text(copy?.settingsTitle ?: "权限已被关闭") },
            text = { Text(copy?.settingsMessage ?: "") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSettingsGuide = false
                        context.startActivity(controller.settingsIntent())
                    },
                ) { Text("打开系统设置") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsGuide = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun PermissionCard(
    type: PermissionType,
    state: PermissionState,
    icon: @Composable () -> Unit,
    onAction: () -> Unit,
) {
    val copy = remember(type) { PermissionTexts.of(type) }
    CtGlassCard(
        shape = CtShapeTokens.hero,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CtShapeTokens.pill,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) { icon() }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        copy.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        type.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CtPill(
                    text = stateLabel(state),
                    containerColor = when (state) {
                        PermissionState.GRANTED -> MaterialTheme.colorScheme.secondaryContainer
                        PermissionState.REQUESTABLE, PermissionState.DENIED ->
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        PermissionState.PERMANENTLY_DENIED -> MaterialTheme.colorScheme.errorContainer
                    },
                    contentColor = when (state) {
                        PermissionState.PERMANENTLY_DENIED -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                copy.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.isGranted) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "已开启，可继续使用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick = onAction) {
                    Text(
                        if (state.needsSettings) "去系统设置开启" else "开启权限",
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

private fun stateLabel(state: PermissionState): String = when (state) {
    PermissionState.GRANTED -> "已开启"
    PermissionState.REQUESTABLE -> "未开启"
    PermissionState.DENIED -> "未开启"
    PermissionState.PERMANENTLY_DENIED -> "需系统设置"
}
