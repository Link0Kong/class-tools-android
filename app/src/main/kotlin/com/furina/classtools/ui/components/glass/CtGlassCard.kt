package com.furina.classtools.ui.components.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.LocalCtDarkTheme

/**
 * v3.1 页面级玻璃卡（Lead 接线层，较 [CtGlassSurface] 更轻量、视觉更稳定）：
 *
 * - 单层半透明 surface 填充（浅 80% / 深 74%），避免「玻璃套玻璃」发灰；
 * - 顶部 specular 高光（约 30% 高度渐隐）+ 1dp 亮描边 + 柔和悬浮阴影；
 * - 浅/深模式自动跟随，文字仍使用 M3 onSurface/onSurfaceVariant（对比度不变）。
 *
 * 用法：
 * ```kotlin
 * CtGlassCard(onClick = onOpen) { content() }
 * CtGlassCard(shape = CtShapeTokens.hero) { content() }   // 无点击
 * ```
 */
@Composable
fun CtGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = CtShapeTokens.toolCard,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val dark = LocalCtDarkTheme.current
    val fill = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (dark) 0.86f else 0.96f)
    val highlight = MaterialTheme.colorScheme.surfaceBright.copy(
        alpha = if (dark) 0.16f else 0.65f,
    )
    val border = MaterialTheme.colorScheme.outlineVariant.copy(
        alpha = if (dark) 0.30f else 0.35f,
    )
    val glassModifier = modifier.fillMaxWidth()
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = glassModifier,
            shape = shape,
            color = fill,
            border = BorderStroke(1.dp, border),
            shadowElevation = 0.dp,
        ) {
            Box { content() }
        }
    } else {
        Surface(
            modifier = glassModifier,
            shape = shape,
            color = fill,
            border = BorderStroke(1.dp, border),
            shadowElevation = 0.dp,
        ) {
            Box { content() }
        }
    }
}
