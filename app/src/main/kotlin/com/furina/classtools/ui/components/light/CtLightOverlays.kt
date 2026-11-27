package com.furina.classtools.ui.components.light

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.furina.classtools.ui.components.CtFeedbackWindow
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.CtLightScene
import com.furina.classtools.ui.theme.CtPalette
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.LocalCtLight
import com.furina.classtools.ui.theme.rememberCtColors

// =============================================================================
// 沉浸光感 · 弹层/对话框色的统一取色（供 CtBottomSheet / CtAlertDialog 使用）
//
// 官方规范：半模态、弹出框通常占据面积较大、内容丰富，推荐 ULTRA_THICK 档，
// 在「透出底层 + 正文可读」之间取平衡 → 本地用 [CtLightSpec.textSafeSurfaceAlpha] 保证正文 AA，
// 并在此基础上叠加一点材质 tint 让弹层看起来像「被光照亮的薄雾」而不是纯实色。
//
// 关闭光感 / 不支持的设备：退回既有实色（surfaceContainerLow / surfaceContainerHigh）。
// =============================================================================

/** 半模态/底部弹层的容器色（ULTRa_THICK 安全不透明度 + 材质 tint）。 */
@Composable
fun ctLightSheetContainerColor(): Color {
    val light = LocalCtLight.current
    val c = rememberCtColors()
    if (c.dark || !light.enabled) return c.containerLow
    val spec = light.spec(CtLightScene.SHEET)
    val base = c.container
    // 弹层必须不透明：0.94 的透明底会让下页文字透上来（实机截图确认「幽灵文字」）。
    return blendToward(base, c.primary, spec.tintAlpha * 0.5f)
}

/** 对话框容器色（与弹层同档，略更实一些以便承载表单）。 */
@Composable
fun ctLightDialogContainerColor(): Color {
    val light = LocalCtLight.current
    val c = rememberCtColors()
    if (c.dark || !light.enabled) return c.containerHigh
    val spec = light.spec(CtLightScene.DIALOG)
    val base = c.containerHigh
    // 对话框同样不透明，避免表单底下透出页面内容。
    return blendToward(base, c.primary, spec.tintAlpha * 0.5f)
}

/** 对话框/弹层统一形状（28dp 圆角）。 */
val CtLightDialogShape: Shape get() = RoundedCornerShape(CtRadius.dialog)

private fun blendToward(base: Color, tint: Color, amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(
        red = base.red * (1f - a) + tint.red * a,
        green = base.green * (1f - a) + tint.green * a,
        blue = base.blue * (1f - a) + tint.blue * a,
        alpha = 1f,
    )
}

/** 当前光感设置的一句话描述（设置页副标题 / 诊断页展示）。 */
@Composable
fun ctLightSummaryText(): String {
    val ctx = LocalCtLight.current
    return when {
        !ctx.enabled -> "关闭 · 实色材质（最省电）"
        else -> "${com.furina.classtools.ui.theme.CtLightSetting.labelOf(ctx.setting)} · 光感材质（顶部 / 浮岛 / 弹层）"
    }
}

/**
 * **沉浸光感版对话框**（ULTRA_THICK）：与 [AlertDialog] 参数完全一致，只把容器换成光感材质色与 28dp 圆角。
 *
 * 迁移方式：把 `AlertDialog(` 换成 `CtLightAlertDialog(` 即可（参数同名）。
 */
@Composable
fun CtLightAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = CtLightDialogShape,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    iconContentColor: Color = MaterialTheme.colorScheme.secondary,
    titleContentColor: Color = MaterialTheme.colorScheme.onSurface,
    textContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    tonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
    properties: androidx.compose.ui.window.DialogProperties = androidx.compose.ui.window.DialogProperties(),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            CtFeedbackWindow()
            confirmButton()
        },
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}

/** 便捷重载：标题/正文/确认/取消都是纯文本（覆盖 90% 的确认框）。 */
@Composable
fun CtLightAlertDialog(
    title: String,
    text: String? = null,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
) {
    CtLightAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = title, style = MaterialTheme.typography.headlineSmall) },
        text = text?.let { { Text(text = it, style = MaterialTheme.typography.bodyMedium) } },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText) }
        },
        dismissButton = dismissText?.let {
            {
                TextButton(onClick = { (onDismiss ?: onDismissRequest)() }) { Text(it) }
            }
        },
    )
}



