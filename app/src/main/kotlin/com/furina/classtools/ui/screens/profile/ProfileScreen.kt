package com.furina.classtools.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.BuildConfig
import com.furina.classtools.data.session.Session
import com.furina.classtools.ui.adaptive.CtAdaptiveSpec
import com.furina.classtools.ui.adaptive.rememberCtAdaptiveSpec
import com.furina.classtools.ui.components.BrandMark
import com.furina.classtools.ui.screens.me.MeStats
import com.furina.classtools.ui.screens.me.MeStatsSection
import com.furina.classtools.ui.screens.me.ReminderStatus
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.glass.CtGlassCard
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.NotificationPermissionBanner
import com.furina.classtools.ui.screens.privacy.PrivacyPolicyDialog
import com.furina.classtools.ui.screens.privacy.UserAgreementDialog
import com.furina.classtools.ui.theme.CtShapeTokens
import kotlinx.coroutines.launch

/**
 * 「我的」：账号信息、同步、权限与提醒、数据与隐私、外观、关于、退出。
 *
 * v2.0.0 自适应（task-25）：
 * - compact/medium：单栏（内容限宽居中）；
 * - expanded（≥840dp）：**双栏**（左：账号 + 同步 + 权限与提醒；右：数据与隐私 + 外观 + 关于 + 退出），
 *   卡片间距/顶部留白收紧，内容限宽 1200dp 居中；
 * - 所有既有入口与回调签名保持不变（权限管理/提醒设置/数据与隐私由 Lead 接线）。
 *
 * @param adaptiveSpec 预览/测试注入窗口规格（默认按当前窗口读取；不影响 Lead 接线）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    session: Session,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    syncEnabled: Boolean = true,
    onSyncEnabledChange: (Boolean) -> Unit = {},
    onClearLocalCache: () -> Unit = {},
    onOpenDataPrivacy: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    showNotificationPermissionBanner: Boolean = false,
    onOpenReminderSettings: () -> Unit = {},
    dynamicColorEnabled: Boolean = false,
    onDynamicColorChange: (Boolean) -> Unit = {},
    adaptiveSpec: CtAdaptiveSpec? = null,
    meStats: MeStats = MeStats(),
    reminderStatus: ReminderStatus = ReminderStatus(),
    onOpenFeedback: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
) {
    var showPrivacy by remember { mutableStateOf(false) }
    var showAgreement by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val spec = adaptiveSpec ?: rememberCtAdaptiveSpec()
    val layout = remember(spec) { ProfileLayoutLogic.layoutFor(spec) }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val baseModifier = Modifier
                .widthIn(max = spec.contentMaxWidth)
                .fillMaxWidth()
                .fillMaxHeight()
                .statusBarsPadding()
                .padding(horizontal = spec.horizontalPadding)
            if (layout.twoPane) {
                Row(
                    modifier = baseModifier,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(layout.mainPaneWeight)
                            .verticalScroll(rememberScrollState())
                            .padding(top = layout.headerTopPaddingDp.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(layout.cardGapDp.dp),
                    ) {
                        ProfileHeader()
                        MeStatsSection(
                            stats = meStats,
                            reminder = reminderStatus,
                            onOpenReminder = onOpenReminderSettings,
                        )
                        NotificationPermissionBanner(
                            visible = showNotificationPermissionBanner,
                            onOpenPermissions = onOpenPermissions,
                        )
                        layout.left.forEach { card ->
                            ProfileCardContent(
                                card = card,
                                session = session,
                                syncEnabled = syncEnabled,
                                onSyncEnabledChange = onSyncEnabledChange,
                                onClearLocalCache = { showClearCacheConfirm = true },
                                onOpenDataPrivacy = onOpenDataPrivacy,
                                onOpenPermissions = onOpenPermissions,
                                onOpenReminderSettings = onOpenReminderSettings,
                                dynamicColorEnabled = dynamicColorEnabled,
                                onDynamicColorChange = onDynamicColorChange,
                                onShowPrivacy = { showPrivacy = true },
                                onShowAgreement = { showAgreement = true },
                                onSignOutClick = { showSignOutConfirm = true },
                                onOpenFeedback = onOpenFeedback,
                                onOpenGuide = onOpenGuide,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f - layout.mainPaneWeight)
                            .verticalScroll(rememberScrollState())
                            .padding(top = layout.headerTopPaddingDp.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(layout.cardGapDp.dp),
                    ) {
                        layout.right.forEach { card ->
                            ProfileCardContent(
                                card = card,
                                session = session,
                                syncEnabled = syncEnabled,
                                onSyncEnabledChange = onSyncEnabledChange,
                                onClearLocalCache = { showClearCacheConfirm = true },
                                onOpenDataPrivacy = onOpenDataPrivacy,
                                onOpenPermissions = onOpenPermissions,
                                onOpenReminderSettings = onOpenReminderSettings,
                                dynamicColorEnabled = dynamicColorEnabled,
                                onDynamicColorChange = onDynamicColorChange,
                                onShowPrivacy = { showPrivacy = true },
                                onShowAgreement = { showAgreement = true },
                                onSignOutClick = { showSignOutConfirm = true },
                                onOpenFeedback = onOpenFeedback,
                                onOpenGuide = onOpenGuide,
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = baseModifier
                        .verticalScroll(rememberScrollState())
                        .padding(top = layout.headerTopPaddingDp.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(layout.cardGapDp.dp),
                ) {
                    ProfileHeader()
                    MeStatsSection(
                        stats = meStats,
                        reminder = reminderStatus,
                        onOpenReminder = onOpenReminderSettings,
                    )
                    NotificationPermissionBanner(
                        visible = showNotificationPermissionBanner,
                        onOpenPermissions = onOpenPermissions,
                    )
                    layout.single.forEach { card ->
                        ProfileCardContent(
                            card = card,
                            session = session,
                            syncEnabled = syncEnabled,
                            onSyncEnabledChange = onSyncEnabledChange,
                            onClearLocalCache = { showClearCacheConfirm = true },
                            onOpenDataPrivacy = onOpenDataPrivacy,
                            onOpenPermissions = onOpenPermissions,
                            onOpenReminderSettings = onOpenReminderSettings,
                            dynamicColorEnabled = dynamicColorEnabled,
                            onDynamicColorChange = onDynamicColorChange,
                            onShowPrivacy = { showPrivacy = true },
                            onShowAgreement = { showAgreement = true },
                            onSignOutClick = { showSignOutConfirm = true },
                            onOpenFeedback = onOpenFeedback,
                            onOpenGuide = onOpenGuide,
                        )
                    }
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

    if (showPrivacy) PrivacyPolicyDialog(onDismiss = { showPrivacy = false })
    if (showAgreement) UserAgreementDialog(onDismiss = { showAgreement = false })
    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("清除本机缓存？") },
            text = {
                Text(
                    "将清除本机缓存的名单/课表/演示等数据，不影响云端数据与登录状态；下次打开会重新同步。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheConfirm = false
                        onClearLocalCache()
                        scope.launch { snackbar.showSnackbar("已清除本机缓存") }
                    },
                ) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("退出登录？") },
            text = {
                Text(
                    "将清除本机会话与名单缓存；云端数据（作业完成、通知已处理、体测历史等）不受影响，重新核验后恢复。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirm = false
                        onSignOut()
                    },
                ) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ProfileHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BrandMark(size = 34.dp)
        Text(
            text = "我的",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ProfileCardContent(
    card: ProfileCard,
    session: Session,
    syncEnabled: Boolean,
    onSyncEnabledChange: (Boolean) -> Unit,
    onClearLocalCache: () -> Unit,
    onOpenDataPrivacy: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenReminderSettings: () -> Unit,
    dynamicColorEnabled: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    onShowPrivacy: () -> Unit,
    onShowAgreement: () -> Unit,
    onSignOutClick: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    when (card) {
        ProfileCard.ACCOUNT -> AccountCard(session)
        ProfileCard.SYNC -> SettingCard(title = "同步") {
            SettingSwitchRow(
                label = "云同步",
                checked = syncEnabled,
                onCheckedChange = onSyncEnabledChange,
            )
            SettingDivider()
            SettingRow(label = "用户数据", value = "userdata v6")
            SettingDivider()
            SettingRow(label = "通知已处理 / 作业完成", value = "marks 同步")
        }

        ProfileCard.PERMISSION -> SettingCard(title = "权限与提醒") {
            SettingActionRow(
                label = "权限管理",
                actionText = "管理",
                onAction = onOpenPermissions,
            )
            SettingDivider()
            SettingActionRow(label = "提醒设置", actionText = "设置", onAction = onOpenReminderSettings)
            SettingDivider()
            SettingRow(label = "通知 / 日历", value = "本地功能 · 不上传")
        }

        ProfileCard.DATA_PRIVACY -> SettingCard(title = "数据与隐私") {
            SettingActionRow(
                label = "数据与隐私",
                actionText = "管理",
                onAction = onOpenDataPrivacy,
            )
            SettingDivider()
            SettingRow(label = "本机缓存", value = "随账号隔离")
            SettingDivider()
            SettingActionRow(
                label = "清除本机缓存",
                actionText = "清除",
                onAction = onClearLocalCache,
            )
        }

        ProfileCard.APPEARANCE -> SettingCard(title = "外观") {
            SettingSwitchRow(
                label = "壁纸取色（Material You）",
                checked = dynamicColorEnabled,
                onCheckedChange = onDynamicColorChange,
            )
            SettingDivider()
            SettingRow(label = "深浅色", value = "跟随系统")
        }

        ProfileCard.ABOUT -> SettingCard(title = "关于") {
            SettingRow(label = "版本", value = "v${BuildConfig.VERSION_NAME}")
            SettingDivider()
            SettingRow(label = "设计语言", value = "Android 17 · M3 Expressive")
            SettingDivider()
            SettingRow(label = "备案信息", value = "上线前填写")
            SettingDivider()
            SettingActionRow(label = "用户协议", actionText = "查看", onAction = onShowAgreement)
            SettingDivider()
            SettingActionRow(label = "隐私政策", actionText = "查看", onAction = onShowPrivacy)
            SettingDivider()
            SettingActionRow(label = "反馈建议", actionText = "反馈", onAction = onOpenFeedback)
            SettingDivider()
            SettingActionRow(label = "使用说明", actionText = "查看", onAction = onOpenGuide)
        }

        ProfileCard.SIGN_OUT -> Column {
            Spacer(modifier = Modifier.height(2.dp))
            FilledTonalButton(
                onClick = onSignOutClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = CtShapeTokens.pill,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text("退出登录并清除本机会话")
            }
        }
    }
}

@Composable
private fun AccountCard(session: Session) {
    CtGlassCard(
        shape = CtShapeTokens.hero,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = session.name.ifBlank { "班级成员" },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            // 大字体/长角色名时自动换行（避免横向截断）
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CtPill(text = session.maskedSid)
                if (session.role.isNotBlank()) CtPill(text = session.role)
                if (session.isAdmin) CtPill(text = "管理员")
            }
        }
    }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SettingCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    CtGlassCard(
        shape = CtShapeTokens.hero,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun SettingActionRow(
    label: String,
    actionText: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        TextButton(onClick = onAction) { Text(actionText) }
    }
}
