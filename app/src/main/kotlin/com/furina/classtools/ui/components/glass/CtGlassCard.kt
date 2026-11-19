package com.furina.classtools.ui.components.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.CtElevation
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.rememberCtColors

/**
 * 规范卡片（旧名 `CtGlassCard` 保留）：**surfaceContainerLow 底 + 1dp outlineVariant 描边 +
 * 20dp 圆角 + 极浅投影**。全局「卡片」只有一个视觉定义，所有页面/组件都经此渲染。
 *
 * 用法不变：`CtGlassCard(onClick = …) { content() }` 或 `CtGlassCard { content() }`。
 */
@Composable
fun CtGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = CtShape.card,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = rememberCtColors()
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = c.containerLow,
            contentColor = c.textPrimary,
            shadowElevation = CtElevation.card,
        ) {
            CardBorder(c.dark) { content() }
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = c.containerLow,
            contentColor = c.textPrimary,
            shadowElevation = if (c.dark) CtElevation.none else CtElevation.card,
        ) {
            CardBorder(c.dark) { content() }
        }
    }
}

/** 卡片内描边（发丝线；深色 1dp）。 */
@Composable
private fun CardBorder(dark: Boolean, content: @Composable () -> Unit) {
    val c = rememberCtColors()
    val width: Dp = if (dark) 1.dp else 0.5.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .ctGlassSurface(
                tokens = CtMaterial(
                    fill = androidx.compose.ui.graphics.Color.Transparent,
                    border = c.divider,
                    borderWidth = width,
                    shadowElevation = CtElevation.none,
                    shadowColor = c.cardShadow,
                ),
                shape = CtShape.card,
                shadow = false,
            ),
    ) {
        content()
    }
}

/** 兼容旧调用：圆角 20dp 的卡片形状。 */
@Composable
fun CtHarmonyCardShape(): Shape = CtShape.card
