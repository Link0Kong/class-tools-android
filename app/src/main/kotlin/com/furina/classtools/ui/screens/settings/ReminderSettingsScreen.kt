@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furina.classtools.data.permission.PermissionController
import com.furina.classtools.data.permission.PermissionType
import com.furina.classtools.data.push.PushProviderStatus
import com.furina.classtools.data.push.PushStatusDiag
import com.furina.classtools.data.push.PushStatusSnapshot
import com.furina.classtools.data.reminder.ReminderActions
import com.furina.classtools.data.reminder.ReminderDiagnostics
import com.furina.classtools.data.reminder.ReminderRuntime
import com.furina.classtools.data.reminder.ReminderSettings
import com.furina.classtools.data.reminder.ReminderSettingsStore
import com.furina.classtools.data.reminder.ReminderTestNotificationResult
import com.furina.classtools.ui.components.CtLightTopBar
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.NotificationPermissionBanner
import com.furina.classtools.ui.components.glass.CtGlassCard
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.CtTokens
import kotlinx.coroutines.launch

/**
 * 通知设置（v4：与鸿蒙端「通知设置」面板对齐，2026-11-21）。
 *
 * 结构（分组 + 内容对齐 `MeScreen.ets → notifyPanel`）：
 * 1. 允许通知（总开关，附系统权限状态）；
 * 2. 作业提醒：新作业通知 / 作业即将截止 / 每日汇总（安卓保留）；
 * 3. 上课提醒 + 提前时间（5/10/15/30 分钟）；
 * 4. 通知提醒：新通知 / 活动截止 / 活动开抢；
 * 5. 投票与水费：新投票 / 投票结果 / 水费扣除；
 * 6. 提醒时间：截止类提醒统一提前（10 分钟 / 30 分钟 / 1 小时 / 2 小时 / 1 天）；
 * 7. 确认提醒效果：发送测试通知（本地立即发一条，检查权限与样式）；
 * 8. 检查与诊断：立即检查 + 诊断摘要。
 *
 * 全部为本机通知（WorkManager，约 15 分钟检查一次），设置只存本机、不随账号同步。
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
    var testBusy by remember { mutableStateOf(false) }
    var testHint by remember { mutableStateOf("") }
    // 沉浸光感顶栏「滚动 → 浮层」：顶栏固定、内容独立滚动。
    val ctTopBarScroll = rememberScrollState()

    fun update(transform: (ReminderSettings) -> ReminderSettings) {
        scope.launch {
            val updated = runCatching { settingsStore.update { transform(it).sanitized() } }.getOrNull()
            if (updated != null) {
                testHint = ""
                onSettingsChanged(updated)
            }
        }
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            CtLightTopBar(
                scrollPx = ctTopBarScroll.value.toFloat(),
                title = "通知",
                subtitle = "本机通知 · 不联网",
                onBack = onBack,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(ctTopBarScroll)
                    .padding(
                        horizontal = CtTokens.Spacing.pageHorizontalCompact,
                        vertical = 8.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(CtTokens.Spacing.cardGapCompact),
            ) {
                NotificationPermissionBanner(
                    visible = !notificationsGranted,
                    onOpenPermissions = onOpenPermissions,
                )

                MasterCard(
                    checked = settings.enabled,
                    granted = notificationsGranted,
                    moduleCount = settings.enabledCount(),
                    onCheckedChange = { value -> update { it.copy(enabled = value) } },
                    onOpenPermissions = onOpenPermissions,
                )

                // 系统推送状态（应用关闭后能否收到）：OPPO / vivo 厂商通道的真实注册状态 + 错误码。
                // 之前「收不到推送」时无法自查，这里把状态直接展示出来；只读本机状态、不上传 token。
                NotifySection("系统推送（应用关闭后）")
                NotifyGroup {
                    PushStatusRow(snapshot = PushStatusDiag.snapshot(context))
                }

                NotifySection("作业提醒")
                NotifyGroup {
                    NotifySwitchRow(
                        title = "新作业通知",
                        description = "有新的作业发布时提醒",
                        checked = settings.homeworkNewEnabled,
                        enabled = settings.enabled,
                        onCheckedChange = { value -> update { it.copy(homeworkNewEnabled = value) } },
                    )
                    NotifyDivider()
                    NotifySwitchRow(
                        title = "作业即将截止",
                        description = "按下方提前时间提醒（当前 ${ReminderSettings.leadLabel(settings.leadMinutes)}）",
                        checked = settings.homeworkDueEnabled,
                        enabled = settings.enabled,
                        onCheckedChange = { value -> update { it.copy(homeworkDueEnabled = value) } },
                    )
                    NotifyDivider()
                    NotifySwitchRow(
                        title = "每日汇总",
                        description = "每天 ${settings.homeworkDigestHour.toString().padStart(2, '0')}:${
                            settings.homeworkDigestMinute.toString().padStart(2, '0')
                        } 汇总未来 48 小时截止的作业/通知（可选）",
                        checked = settings.digestEnabled,
                        enabled = settings.enabled,
                        onCheckedChange = { value -> update { it.copy(digestEnabled = value) } },
                    )
                }

                NotifySection("上课提醒")
                NotifyGroup {
                    NotifySwitchRow(
                        title = "上课提醒",
                        description = "按课表在课前 ${settings.classLeadMinutes} 分钟提醒",
                        checked = settings.classRemindEnabled,
                        enabled = settings.enabled,
                        icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                        onCheckedChange = { value -> update { it.copy(classRemindEnabled = value) } },
                    )
                    NotifyDivider()
                    LeadChipsRow(
                        label = "提前时间",
                        options = ReminderSettings.CLASS_LEAD_OPTIONS,
                        selected = settings.classLeadMinutes,
                        enabled = settings.enabled && settings.classRemindEnabled,
                        onSelect = { minutes -> update { it.copy(classLeadMinutes = minutes) } },
                    )
                }

                NotifySection("通知提醒")
                NotifyGroup {
                    NotifySwitchRow(
                        title = "新通知提醒",
                        description = "班委发布新通知时提醒",
                        checked = settings.noticeNewEnabled,
                        enabled = settings.enabled,
                        icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
                        onCheckedChange = { value -> update { it.copy(noticeNewEnabled = value) } },
                    )
                    NotifyDivider()
                    NotifySwitchRow(
                        title = "活动截止提醒",
                        description = "活动/报名截止前提醒",
                        checked = settings.noticeDeadlineEnabled,
                        enabled = settings.enabled,
                        onCheckedChange = { value -> update { it.copy(noticeDeadlineEnabled = value) } },
                    )
                    NotifyDivider()
                    NotifySwitchRow(
                        title = "活动开抢提醒",
                        description = "报名开始时提前 5 分钟提醒",
                        checked = settings.noticeSignupEnabled,
                        enabled = settings.enabled,
                        onCheckedChange = { value -> update { it.copy(noticeSignupEnabled = value) } },
                    )
                }

                NotifySection("投票与水费")
                NotifyGroup {
                    NotifySwitchRow(
                        title = "新投票提醒",
                        description = "发起新投票时提醒",
                        checked = settings.voteNewEnabled,
                        enabled = settings.enabled,
                        onCheckedChange = { value -> update { it.copy(voteNewEnabled = value) } },
                    )
                    NotifyDivider()
                    NotifySwitchRow(
                        title = "投票结果可查看",
                        description = "投票结束后提醒查看结果",
                        checked = settings.voteResultEnabled,
                        enabled = settings.enabled,
                        onCheckedChange = { value -> update { it.copy(voteResultEnabled = value) } },
                    )
                    NotifyDivider()
                    NotifySwitchRow(
                        title = "水费扣除提醒",
                        description = "抄表产生用量/水费时提醒（只在本机计算）",
                        checked = settings.waterDeductEnabled,
                        enabled = settings.enabled,
                        icon = { Icon(Icons.Rounded.WaterDrop, contentDescription = null) },
                        onCheckedChange = { value -> update { it.copy(waterDeductEnabled = value) } },
                    )
                }

                NotifySection("提醒时间")
                NotifyGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "截止类提醒统一提前",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "对新作业截止、活动截止生效",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        LeadChips(
                            options = ReminderSettings.LEAD_OPTIONS,
                            selected = settings.leadMinutes,
                            enabled = settings.enabled,
                            onSelect = { minutes -> update { it.copy(leadMinutes = minutes) } },
                            labelOf = { minutes -> ReminderSettings.leadLabel(minutes) },
                        )
                    }
                }

                NotifySection("确认提醒效果")
                NotifyGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = {
                                if (testBusy) return@Button
                                testBusy = true
                                testHint = ""
                                scope.launch {
                                    val result = runCatching {
                                        ReminderActions.testNotification(context)
                                    }.getOrNull()
                                    testBusy = false
                                    testHint = when (result) {
                                        ReminderTestNotificationResult.Sent ->
                                            "测试通知已发送，请下拉通知中心查看"
                                        ReminderTestNotificationResult.PermissionMissing ->
                                            "请先在系统设置中允许通知，再发送测试"
                                        is ReminderTestNotificationResult.Failed -> result.message
                                        null -> "发送失败，请稍后重试"
                                    }
                                }
                            },
                            enabled = !testBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            Text(if (testBusy) "正在发送…" else "发送测试通知")
                        }
                        if (testHint.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                testHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (testHint.startsWith("测试通知已发送")) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "立即发出一条本地通知，用于检查系统权限和通知样式。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                CtGlassCard(shape = CtShapeTokens.hero) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "检查与诊断",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(CtSpace.xs))
                        Text(
                            "立即检查本机通知链路，并查看缓存、权限与上次运行摘要（不含通知正文）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(CtSpace.sm))
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

                CtGlassCard(shape = CtShapeTokens.hero) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "说明",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(CtSpace.xs))
                        Text(
                            "通知由本机调度（Android WorkManager，约 15 分钟检查一次）；手机省电/息屏时可能延迟。" +
                                "通知设置只保存在本机，不随账号同步；点击通知可直达作业/课表/通知/投票/水量。" +
                                "系统通知权限只会在你首次开启通知或发送测试时请求。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onOpenPermissions) { Text("管理权限") }
                    }
                }

                Spacer(Modifier.height(CtSpace.md))
            }
        }
    }

    diagnostics?.let { diag ->
        com.furina.classtools.ui.components.light.CtLightAlertDialog(
            onDismissRequest = { diagnostics = null },
            title = { Text("通知诊断") },
            text = {
                Text(
                    buildString {
                        appendLine("权限：${diag.permission.name}")
                        appendLine("设置：${diag.settingsSummary}")
                        appendLine("缓存：${diag.cacheSummary}")
                        appendLine(
                            "小图标：${if (diag.smallIconFallback) "系统占位（待加专用图标）" else "专用图标"}",
                        )
                        val lastRun = diag.lastRunAtMillis?.let {
                            java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(it))
                        } ?: "尚未运行"
                        appendLine("上次运行：$lastRun")
                        diag.lastRunSummary?.let { appendLine("摘要：$it") }
                        diag.lastRunResult?.reason?.takeIf { it.isNotBlank() }?.let {
                            appendLine("原因：$it")
                        }
                        appendLine("补发回看：${diag.catchUpLookbackMillis / 60_000} 分钟")
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

// ------------------------------------------------------------------ 组件

/** 分组小标题（与鸿蒙 `notifySection` 一致）。 */
@Composable
private fun NotifySection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

/** 分组容器（圆角白底卡片 + 行间分割线）。 */
@Composable
private fun NotifyGroup(content: @Composable () -> Unit) {
    Surface(
        shape = CtShapeTokens.hero,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun NotifyDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/** 总开关（允许通知）：状态徽标 + 系统权限说明。 */
@Composable
private fun MasterCard(
    checked: Boolean,
    granted: Boolean,
    moduleCount: Int,
    onCheckedChange: (Boolean) -> Unit,
    onOpenPermissions: () -> Unit,
) {
    Surface(
        shape = CtShapeTokens.hero,
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CtShapeTokens.pill,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(36.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) { Icon(Icons.Rounded.Schedule, contentDescription = null) }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "允许通知",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = CtShapeTokens.pill,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            if (checked) "已开启" else "已关闭",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    when {
                        !granted -> "系统通知未授权，开启开关将请求授权"
                        checked -> "已开启 $moduleCount 项 · 新通知/作业到达时由系统提醒"
                        else -> "关闭后不再调度/发送任何本机通知"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!granted) {
                    TextButton(onClick = onOpenPermissions) { Text("去开启权限") }
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/** 一行开关（标题 + 说明 + 右侧 Switch）。 */
@Composable
private fun NotifySwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Surface(
                shape = CtShapeTokens.pill,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(32.dp),
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
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** 提前时间一行（标题 + 胶囊选项）。 */
@Composable
private fun LeadChipsRow(
    label: String,
    options: List<Int>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        LeadChips(options = options, selected = selected, enabled = enabled, onSelect = onSelect)
    }
}

@Composable
private fun LeadChips(
    options: List<Int>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    labelOf: (Int) -> String = { minutes -> "$minutes 分钟" },
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { minutes ->
            FilterChip(
                selected = selected == minutes,
                enabled = enabled,
                onClick = { onSelect(minutes) },
                label = { Text(labelOf(minutes)) },
            )
        }
    }
}

/**
 * 系统推送状态行（OPPO / vivo 厂商通道）：
 * - 显示上次注册状态与错误码（如「注册失败（错误码 33）」），用于排查应用关闭后收不到推送；
 * - 只读本机 SharedPreferences，不含 token/学号；点「刷新状态」重新读取；
 * - 非 ColorOS / vivo 设备显示「本机不支持」，这类机型应用被杀后收不到系统推送，只能靠本机提醒。
 */
@Composable
private fun PushStatusRow(snapshot: PushStatusSnapshot) {
    val pushContext = androidx.compose.ui.platform.LocalContext.current
    var current by remember(snapshot) { mutableStateOf(snapshot) }
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            "应用关闭后的通知依赖厂商系统推送通道。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        PushStatusLine("OPPO 推送", current.oppo)
        Spacer(Modifier.height(4.dp))
        PushStatusLine("vivo 推送", current.vivo)
        Spacer(Modifier.height(8.dp))
        Text(
            "仅 ColorOS / vivo 设备支持应用关闭后接收；其他机型只有本机提醒（被系统清理后可能收不到）。" +
                "重开 App 会自动重新注册。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = { current = PushStatusDiag.snapshot(pushContext) },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("刷新状态") }
    }
}

@Composable
private fun PushStatusLine(name: String, status: PushProviderStatus) {
    val bad = status.state == "FAILED" || status.state == "UNSUPPORTED"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(96.dp),
        )
        Text(
            status.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
