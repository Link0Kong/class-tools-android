@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furina.classtools.data.permission.PermissionController
import com.furina.classtools.data.permission.PermissionState
import com.furina.classtools.data.permission.PermissionType
import com.furina.classtools.data.reminder.ReminderActions
import com.furina.classtools.data.reminder.ReminderDiagnostics
import com.furina.classtools.data.reminder.ReminderRuntime
import com.furina.classtools.data.reminder.ReminderSettings
import com.furina.classtools.data.reminder.ReminderSettingsStore
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.NotificationPermissionBanner
import com.furina.classtools.ui.components.glass.CtGlassCard
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtTokens
import kotlinx.coroutines.launch

/**
 * 提醒设置（v2.0.0）：
 * - 作业截止汇总/提前提醒、课表下一节提醒的开关与提前量；
 * - 本机通知（WorkManager，15 分钟周期；省电/Doze 下可能延迟）；
 * - 通知权限未开启时引导到「权限与提醒」页。
 */
@Composable
fun ReminderSettingsScreen(
    onBack: () -> Unit,
    settingsStore: ReminderSettingsStore,
    permissionController: PermissionController,
    modifier: Modifier = Modifier,
    onOpenPermissions: () -> Unit = {},
    onSettingsChanged: (ReminderSettings) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsStore.settings.collectAsStateWithLifecycle(
        initialValue = ReminderSettings.DEFAULT,
    )
    val notificationsGranted =
        permissionController.state(PermissionType.NOTIFICATIONS).isGranted
    var diagnostics by remember { mutableStateOf<ReminderDiagnostics?>(null) }

    fun update(transform: (ReminderSettings) -> ReminderSettings) {
        scope.launch {
            val updated = runCatching { settingsStore.update { transform(it).sanitized() } }.getOrNull()
            if (updated != null) onSettingsChanged(updated)
        }
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            CtTopBar(title = "提醒", subtitle = "本机通知 · 不联网", onBack = onBack)

            Column(
                modifier = Modifier.padding(
                    horizontal = CtTokens.Spacing.pageHorizontalCompact,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(CtTokens.Spacing.cardGapCompact),
            ) {
                NotificationPermissionBanner(
                    visible = !notificationsGranted,
                    onOpenPermissions = onOpenPermissions,
                )

                ReminderCard(
                    title = "提醒总开关",
                    description = "关闭后不再调度/发送任何本机提醒",
                    checked = settings.enabled,
                    onCheckedChange = { value -> update { it.copy(enabled = value) } },
                )

                ReminderCard(
                    title = "作业提醒",
                    description = "每日 ${settings.homeworkDigestHour.toString().padStart(2, '0')}:${
                        settings.homeworkDigestMinute.toString().padStart(2, '0')
                    } 汇总「明天/后天截止」，并在截止前提醒",
                    checked = settings.homeworkEnabled && settings.enabled,
                    enabled = settings.enabled,
                    onCheckedChange = { value -> update { it.copy(homeworkEnabled = value) } },
                    icon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                ) {
                    Text(
                        "截止前额外提醒",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReminderSettings.HOMEWORK_LEAD_DAY_OPTIONS.forEach { days ->
                            FilterChip(
                                selected = settings.homeworkLeadDays == days,
                                onClick = { update { it.copy(homeworkLeadDays = days) } },
                                label = { Text(if (days == 0) "当天" else "$days 天前") },
                            )
                        }
                    }
                }

                ReminderCard(
                    title = "上课提醒",
                    description = "下一节课开始前 ${settings.courseLeadMinutes} 分钟提醒",
                    checked = settings.courseEnabled && settings.enabled,
                    enabled = settings.enabled,
                    onCheckedChange = { value -> update { it.copy(courseEnabled = value) } },
                    icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                ) {
                    Text(
                        "提前时间",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReminderSettings.COURSE_LEAD_MINUTE_OPTIONS.forEach { minutes ->
                            FilterChip(
                                selected = settings.courseLeadMinutes == minutes,
                                onClick = { update { it.copy(courseLeadMinutes = minutes) } },
                                label = { Text("$minutes 分钟") },
                            )
                        }
                    }
                }

                ReminderCard(
                    title = "通知提醒",
                    description = "每日汇总「明天/后天」截止或开始的通知，并在临近 24 小时内提醒",
                    checked = settings.noticeEnabled && settings.enabled,
                    enabled = settings.enabled,
                    onCheckedChange = { value -> update { it.copy(noticeEnabled = value) } },
                    icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
                )

                ReminderCard(
                    title = "安静时段",
                    description = "${settings.quietStartHour}:00 – ${settings.quietEndHour}:00 不弹提醒（避免夜间打扰）",
                    checked = settings.quietHoursEnabled,
                    onCheckedChange = { value -> update { it.copy(quietHoursEnabled = value) } },
                )

                CtGlassCard(
                    shape = CtShapeTokens.hero,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "检查与诊断",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "立即检查本机通知链路，并查看缓存与上次运行摘要。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                ReminderRuntime.runNow(context)
                                scope.launch {
                                    diagnostics = runCatching {
                                        ReminderActions.diagnostics(context)
                                    }.getOrNull()
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("立即检查 + 诊断") }
                    }
                }

                CtGlassCard(
                    shape = CtShapeTokens.hero,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "说明",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "提醒由本机调度（Android WorkManager，约 15 分钟检查一次）；手机省电/息屏时可能延迟。" +
                                "提醒设置只保存在本机，不随账号同步；点击通知可直接打开作业/课表。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onOpenPermissions) { Text("管理权限") }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }

    diagnostics?.let { diag ->
        AlertDialog(
            onDismissRequest = { diagnostics = null },
            title = { Text("通知诊断") },
            text = {
                Text(
                    buildString {
                        appendLine("权限：${diag.permission.name}")
                        appendLine("设置：${diag.settingsSummary}")
                        appendLine(
                            "缓存：作业 ${diag.homeworkCacheCount} 条 / 通知 ${diag.noticeCacheCount} 条 / " +
                                "课表${if (diag.timetableReady) "可用" else "不可用"}",
                        )
                        appendLine(
                            "小图标：${if (diag.smallIconFallback) "系统占位（待加专用图标）" else "专用图标"}",
                        )
                        val lastRun = diag.lastRunAtMillis?.let {
                            java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(it))
                        } ?: "尚未运行"
                        appendLine("上次运行：$lastRun")
                        diag.lastRunSummary?.let { appendLine("摘要：$it") }
                        (diag.lastRunResult?.reason)?.takeIf { it.isNotBlank() }?.let {
                            appendLine("原因：$it")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = { diagnostics = null }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun ReminderCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = CtShapeTokens.hero,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Surface(
                        shape = CtShapeTokens.pill,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) { icon() }
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                )
            }
            if (extra != null && checked) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { extra() }
            }
        }
    }
}
