package com.furina.classtools.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.CtShapeTokens

/**
 * 全局通知权限提示（审计 S-01）：
 * - 工作台 / 我的 / 提醒设置顶部显示；
 * - 一次性可关闭：本会话内任一处关闭后全部隐藏（[NotificationPermissionBannerState]）；
 * - 点击「去开启」跳权限页（Lead 接线 `onOpenPermissions`）。
 *
 * 只在 `visible = true`（通知权限关闭 **且** 用户开启过提醒）时由页面传入。
 */
object NotificationPermissionBannerState {
    /** 本会话内用户主动关闭（进程重启恢复）。 */
    var dismissedThisSession: Boolean by mutableStateOf(false)
        private set

    fun dismiss() {
        dismissedThisSession = true
    }

    /** 预览/测试用。 */
    fun reset() {
        dismissedThisSession = false
    }
}

@Composable
fun NotificationPermissionBanner(
    visible: Boolean,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible || NotificationPermissionBannerState.dismissedThisSession) return
    val scheme = MaterialTheme.colorScheme
    // 2026-11-16 视觉统一：去大面积 tertiaryContainer 色块，改「浅色玻璃底 + 小面积警示色图标」，
    // 与卡片体系一致（1dp 软描边、16dp 圆角、56dp 行高）；文案与交互保持不变。
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = scheme.surfaceContainerLow,
        contentColor = scheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = scheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                color = scheme.tertiary.copy(alpha = 0.14f),
                contentColor = scheme.tertiary,
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "通知权限未开启",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "开启后才能收到作业/上课提醒",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpenPermissions) { Text("去开启") }
            IconButton(onClick = { NotificationPermissionBannerState.dismiss() }) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭通知权限提示",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
