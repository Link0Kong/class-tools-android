package com.furina.classtools.ui.components.expressive

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtTokens

/**
 * 通用可按压卡片容器（expressive 按压 + 形状回弹）。
 *
 * 交互规格（design/ANDROID17_DESIGN.md §5.2 / §6.7）：
 * - 按下：scale 1.0 → 0.97，圆角 [cornerRadius] → [pressedCornerRadius]，阴影 0 → 2dp；
 * - 松手：全部用 [CtMotion.spatialFast]（d=0.6 / k=800）回弹；
 * - 常态无阴影（卡片用描边/色调分层），按下才浮起，形成「实体感」。
 *
 * 供工具卡、列表卡、设置项等复用；内部是 Material3 [Surface]（自带 ripple 与语义）。
 *
 * @param onClick 点击回调
 * @param cornerRadius 常态圆角（默认 16dp）
 * @param pressedCornerRadius 按下圆角（默认 12dp，形成形状收紧）
 * @param contentDescription 无障碍描述；null 时由内容自行提供
 */
@Composable
fun CtPressableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = CtTokens.Radius.card,
    pressedCornerRadius: Dp = CtTokens.Radius.cardPressed,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    contentDescription: String? = null,
    shadowElevation: Dp = 0.dp,
    pressedShadowElevation: Dp = 2.dp,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()

    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = CtMotion.spatialFast,
        label = "ctCardPress",
    )
    val scale = 1f - 0.03f * pressProgress
    val corner = lerp(cornerRadius, pressedCornerRadius, pressProgress)
    val elevation by animateDpAsState(
        targetValue = if (pressed) pressedShadowElevation else shadowElevation,
        animationSpec = CtMotion.spatialFastDp,
        label = "ctCardElevation",
    )

    val description = contentDescription
    val semanticsModifier = if (description != null) {
        Modifier.semantics {
            this.contentDescription = description
            role = Role.Button
        }
    } else {
        Modifier.semantics { role = Role.Button }
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(semanticsModifier),
        enabled = enabled,
        shape = RoundedCornerShape(corner),
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = elevation,
        interactionSource = source,
    ) {
        Box(content = content)
    }
}
