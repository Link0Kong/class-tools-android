package com.furina.classtools.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtText
import com.furina.classtools.ui.theme.HarmonyTokens
import com.furina.classtools.ui.theme.harmonyTint
import com.furina.classtools.ui.theme.rememberHarmonyColors

/**
 * 全局通知权限提示（审计 S-01）：
 * - 工作台 / 我的 / 提醒设置顶部显示；
 * - 一次性可关闭：本会话内任一处关闭后全部隐藏（[NotificationPermissionBannerState]）；
 * - 点击「去开启」跳权限页（Lead 接线 `onOpenPermissions`）。
 *
 * 视觉（2026-11-17 鸿蒙对齐）：白卡 18 圆角 + 浅阴影；40dp tint 图标块；标题 15 medium、
 * 说明 12；右侧「去开启」13 雾青文字操作；关闭按钮 44dp 热区。不再玻璃/大面积色块。
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
    val colors = rememberHarmonyColors()
    val accent = MaterialTheme.colorScheme.tertiary
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = colors.card,
        contentColor = colors.textPrimary,
        shadowElevation = HarmonyTokens.CardShadowElevation,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(HarmonyTokens.RadiusTile),
                color = harmonyTint(accent),
                contentColor = accent,
            ) {
                Box(
                    modifier = Modifier.size(HarmonyTokens.GlyphBox),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "通知权限未开启",
                    style = CtText.bodyMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = "开启后才能收到作业/上课提醒",
                    style = CtText.caption,
                    color = colors.textSecondary,
                )
            }
            Text(
                text = "去开启",
                style = CtText.labelMedium,
                color = colors.heroAccent,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onOpenPermissions)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(role = Role.Button) { NotificationPermissionBannerState.dismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭通知权限提示",
                    tint = colors.textTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
