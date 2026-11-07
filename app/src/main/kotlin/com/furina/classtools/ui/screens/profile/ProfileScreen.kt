package com.furina.classtools.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.BuildConfig
import com.furina.classtools.data.session.Session
import com.furina.classtools.ui.components.BrandMark
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.screens.privacy.PrivacyPolicyDialog
import com.furina.classtools.ui.screens.privacy.UserAgreementDialog
import com.furina.classtools.ui.theme.CtShapeTokens

/**
 * 我的：账号信息、同步、数据与隐私、关于、退出。
 * 隐私口径：学号只在本机脱敏展示，不写日志、不外发。
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
    dynamicColorEnabled: Boolean = false,
    onDynamicColorChange: (Boolean) -> Unit = {},
) {
    var showPrivacy by remember { mutableStateOf(false) }
    var showAgreement by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.padding(top = 10.dp),
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

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = CtShapeTokens.hero,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
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

            SettingCard(title = "同步") {
                SettingSwitchRow(
                    label = "云同步",
                    checked = syncEnabled,
                    onCheckedChange = onSyncEnabledChange,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(label = "用户数据", value = "userdata v6")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(label = "通知已处理 / 作业完成", value = "marks 同步")
            }

            SettingCard(title = "数据与隐私") {
                SettingActionRow(
                    label = "数据与隐私",
                    actionText = "管理",
                    onAction = onOpenDataPrivacy,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(label = "本机缓存", value = "随账号隔离")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingActionRow(
                    label = "清除本机缓存",
                    actionText = "清除",
                    onAction = onClearLocalCache,
                )
            }

            SettingCard(title = "外观") {
                SettingSwitchRow(
                    label = "壁纸取色（Material You）",
                    checked = dynamicColorEnabled,
                    onCheckedChange = onDynamicColorChange,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(label = "深浅色", value = "跟随系统")
            }

            SettingCard(title = "关于") {
                SettingRow(label = "版本", value = "v${BuildConfig.VERSION_NAME}")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(label = "设计语言", value = "Android 17 · M3 Expressive")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(label = "备案信息", value = "上线前填写")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingActionRow(label = "用户协议", actionText = "查看", onAction = { showAgreement = true })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingActionRow(label = "隐私政策", actionText = "查看", onAction = { showPrivacy = true })
            }

            Spacer(modifier = Modifier.height(6.dp))
            FilledTonalButton(
                onClick = { showSignOutConfirm = true },
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
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showPrivacy) PrivacyPolicyDialog(onDismiss = { showPrivacy = false })
    if (showAgreement) UserAgreementDialog(onDismiss = { showAgreement = false })
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
private fun SettingCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.hero,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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

