package com.furina.classtools.ui.components.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.CtElevation
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import com.furina.classtools.ui.theme.rememberCtColors

// =============================================================================
// 材质层（Material）—— **不再提供玻璃 / 渐变 / 噪点 / 暗角**
//
// 2026-11-19 统一视觉语言重构：旧的 v3「液态玻璃」层（半透明渐变 + 折射高光 + 内阴影）
// 与 HarmonyOS「轻拟物」+ M3 Expressive「形体/层级」不符，已整体移除。本文件保留同名 API
// （`CtGlassSurface` / `ctGlassSurface` / `ctDockGlass`…）以便预览等旧调用点零改动，
// 但实现改为规范卡片：**纯色 containerLow 底 + 1dp outlineVariant 描边 + 极浅投影**。
//
// 需要新排版时请优先使用 `ui/components/CtLayout.kt` 里的 `CtCard` / `CtSection` / `CtListRow`。
// =============================================================================

/** 规范卡片材质参数（唯一材质来源；全部取自 [com.furina.classtools.ui.theme.CtDesign]）。 */
data class CtMaterial(
    /** 卡片底（surfaceContainerLow）。 */
    val fill: Color,
    /** 1dp 描边（outlineVariant）。 */
    val border: Color,
    /** 发丝线宽度（0.5dp）。 */
    val borderWidth: Dp,
    /** 极浅投影。 */
    val shadowElevation: Dp,
    /** 投影颜色（极低 alpha 墨色）。 */
    val shadowColor: Color,
)

/** 旧别名：玻璃 token（字段保留，语义改为规范卡片材质）。 */
typealias CtGlassColorTokens = CtMaterial

/** 旧别名：底栏材质。 */
data class CtGlassDockSpec(
    val fill: Color,
    val border: Color,
    val shadowElevation: Dp,
    val cornerRadius: Dp,
)

/** 材质入口（旧名 `CtGlass` 保留）。 */
object CtGlass {
    /** 浅色规范卡片材质。 */
    val light: CtMaterial = CtMaterial(
        fill = Color.Unspecified,
        border = Color.Unspecified,
        borderWidth = 1.dp,
        shadowElevation = CtElevation.card,
        shadowColor = Color.Unspecified,
    )

    /** 深色规范卡片材质。 */
    val dark: CtMaterial = light

    /** 当前主题材质（跟随 `LocalCtDarkTheme`）。 */
    @Composable
    fun material(): CtMaterial {
        val c = rememberCtColors()
        val dark = LocalCtDarkTheme.current
        return CtMaterial(
            fill = c.containerLow,
            border = c.divider,
            borderWidth = if (dark) 1.dp else 0.5.dp,
            shadowElevation = if (dark) CtElevation.none else CtElevation.card,
            shadowColor = c.cardShadow,
        )
    }

    /** 旧名：`tokens()`。 */
    @Composable
    fun tokens(): CtMaterial = material()

    /** 底栏材质（BAR_LIGHT + 发丝线；无黑影）。 */
    @Composable
    fun dockTokens(): CtGlassDockSpec {
        val c = rememberCtColors()
        return CtGlassDockSpec(
            fill = c.bar,
            border = c.divider,
            shadowElevation = CtElevation.none,
            cornerRadius = CtRadius.hero,
        )
    }

    /** 默认卡片圆角（hero 档 28dp）。 */
    val defaultShape: Shape = CtShape.hero
}

/** 旧 CompositionLocal。 */
val LocalCtGlassTokens = staticCompositionLocalOf<CtMaterial> { CtGlass.light }

/**
 * 卡片表面（旧名 `CtGlassSurface`）：纯色底 + 1dp 描边 + 极浅投影。
 */
@Composable
fun CtGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CtShape.card,
    tokens: CtMaterial = CtGlass.material(),
    shadow: Boolean = true,
    contentColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(modifier = modifier.ctGlassSurface(tokens = tokens, shape = shape, shadow = shadow)) {
            content()
        }
    }
}

/** 旧 Modifier 名：规范卡片外观。 */
fun Modifier.ctGlassSurface(
    tokens: CtMaterial,
    shape: Shape,
    shadow: Boolean = true,
): Modifier {
    var modifier = this
    if (shadow && tokens.shadowElevation > 0.dp) {
        modifier = modifier.shadow(
            elevation = tokens.shadowElevation,
            shape = shape,
            clip = false,
            ambientColor = tokens.shadowColor,
            spotColor = tokens.shadowColor,
        )
    }
    return modifier
        .clip(shape)
        .background(tokens.fill)
        .border(BorderStroke(tokens.borderWidth, tokens.border), shape)
}

/** 旧 Modifier 名：底栏材质（浮岛底栏使用）。 */
@Composable
fun Modifier.ctDockGlass(shape: Shape = RoundedCornerShape(CtRadius.hero)): Modifier {
    val spec = CtGlass.dockTokens()
    return this
        .clip(shape)
        .background(spec.fill)
        .border(BorderStroke(0.5.dp, spec.border), shape)
}

/** 旧 Modifier 名：折射高光 —— 规范下无折射，等价于 1dp 描边。 */
fun Modifier.ctGlassRefraction(tokens: CtMaterial, shape: Shape): Modifier =
    this.border(BorderStroke(tokens.borderWidth, tokens.border), shape)

/** 圆角路径描边（自定义 Canvas 常用；保留）。 */
fun Modifier.ctRoundedPathBorder(
    color: Color,
    cornerRadius: Dp,
    strokeWidth: Dp = 1.dp,
): Modifier = drawWithCache {
    val radiusPx = cornerRadius.toPx()
    val path = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                cornerRadius = CornerRadius(radiusPx, radiusPx),
            ),
        )
    }
    onDrawWithContent {
        drawContent()
        drawPath(path = path, color = color, style = Stroke(width = strokeWidth.toPx()))
    }
}
