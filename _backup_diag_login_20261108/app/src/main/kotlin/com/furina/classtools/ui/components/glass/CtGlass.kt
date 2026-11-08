package com.furina.classtools.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.LocalCtDarkTheme

/**
 * v3.0.0 ColorOS 17「液态玻璃（Liquid Glass）」组件层。
 *
 * 设计目标（见 `docs/design/V3_GLOBAL_STYLE.md`）：
 * - **可叠加外观层**：本层只画「半透明填充 + 高光描边 + 折射 specular + 内阴影」；
 *   真实背景模糊（Haze / RenderEffect）由 Lead 在页面层叠加（本组件不依赖第三方库）；
 * - **浅/深两套 token**：都由主题色板派生（雾青/暖沙点缀），深色用低亮度高饱和玻璃；
 * - **折射高光**：沿圆角内侧的顶部 specular 渐变 + 顶部 1px 亮边 + 左右侧细高光（模拟玻璃折射）；
 * - **底栏专用 spec**：`CtGlassDockSpec`（模糊半径/透明度/高光/折射边/圆角/阴影）供浮岛底栏使用。
 *
 * 用法：
 * ```kotlin
 * // 1) 玻璃卡片
 * CtGlassSurface(shape = CtShapeTokens.hero) { Text("玻璃卡片内容") }
 *
 * // 2) 页面层先垫 Haze 背景，再叠玻璃外观
 * Box {
 *     hazeBackground()                 // Lead 负责真实模糊
 *     CtGlassSurface { content() }     // 本组件负责高光/折射/描边
 * }
 *
 * // 3) 浮岛底栏玻璃
 * Box(Modifier.ctDockGlass(CtGlass.dockTokens())) { dockContent() }
 * ```
 */
@Immutable
data class CtGlassColorTokens(
    /** 玻璃填充渐变（上→下）。 */
    val fillStart: Color,
    val fillEnd: Color,
    /** 1dp 描边渐变（上亮下暗）。 */
    val borderStart: Color,
    val borderEnd: Color,
    /** 顶部折射高光 / specular 颜色。 */
    val specular: Color,
    /** 底部内阴影（增强玻璃厚度感）。 */
    val innerShadow: Color,
    /** 品牌薄涂层（雾青/暖沙，低透明）。 */
    val tint: Color,
    /** 投影高度（玻璃悬浮感；平板/大面积可调低）。 */
    val shadowElevation: Dp,
    /** 建议的模糊半径（Haze 层使用；本组件不执行模糊）。 */
    val blurRadius: Dp,
)

/** 玻璃 token 的 CompositionLocal（页面可覆盖为品牌玻璃/低功耗玻璃）。 */
val LocalCtGlassTokens = compositionLocalOf { CtGlass.light }

/** 玻璃体系入口：浅/深 token + 组件级 spec + Modifier 扩展。 */
object CtGlass {

    val light: CtGlassColorTokens = CtGlassColorTokens(
        fillStart = Color(0xCCFFFFFF), // 80% 白（暖白玻璃）
        fillEnd = Color(0x99F4F7F6),
        borderStart = Color(0xE6FFFFFF),
        borderEnd = Color(0x33FFFFFF),
        specular = Color(0x99FFFFFF),
        innerShadow = Color(0x1A1D2B28),
        tint = Color(0x144E9082), // 雾青薄涂层
        shadowElevation = 12.dp,
        blurRadius = 24.dp,
    )

    val dark: CtGlassColorTokens = CtGlassColorTokens(
        fillStart = Color(0xB31A1F1E), // 70% 深青灰
        fillEnd = Color(0x8C101514),
        borderStart = Color(0x66FFFFFF),
        borderEnd = Color(0x14FFFFFF),
        specular = Color(0x59FFFFFF),
        innerShadow = Color(0x66000000),
        tint = Color(0x1690D3C4), // 深色模式用亮雾青薄涂层
        shadowElevation = 10.dp,
        blurRadius = 20.dp,
    )

    /** 当前主题玻璃 token（跟随 `LocalCtDarkTheme`；预览/测试可直接显式传 light/dark）。 */
    @Composable
    fun tokens(): CtGlassColorTokens = if (LocalCtDarkTheme.current) dark else light

    /** 浮岛底栏玻璃 spec（浅/深）。 */
    @Composable
    fun dockTokens(): CtGlassDockSpec = if (LocalCtDarkTheme.current) CtGlassDockSpec.dark else CtGlassDockSpec.light

    /** 玻璃卡默认圆角（hero = 32dp，与浮岛/大卡一致）。 */
    val defaultShape: Shape = CtShapeTokens.hero
}

/**
 * 浮岛底栏玻璃规格（ColorOS 17 dock）：
 * - 模糊半径：浅色 24dp / 深色 20dp（实际模糊由 Lead 用 Haze 实现，本层提供数值）
 * - 填充透明度：78%（浅）/ 72%（深）
 * - 高光：顶部 1dp 亮边 + 内侧 specular 渐变（45% 强度）
 * - 折射边：左右细高光 + 底部内阴影（玻璃厚度感）
 * - 悬浮：圆角 32dp、阴影 12dp、离底 10dp（由调用方 padding 控制）
 */
@Immutable
data class CtGlassDockSpec(
    val fillAlpha: Float,
    val highlightAlpha: Float,
    val refractionAlpha: Float,
    val borderAlpha: Float,
    val blurRadius: Dp,
    val cornerRadius: Dp,
    val shadowElevation: Dp,
    val tint: Color,
    val specular: Color,
    val fillBase: Color,
    val fillBaseBottom: Color,
) {
    companion object {
        val light = CtGlassDockSpec(
            fillAlpha = 0.78f,
            highlightAlpha = 0.55f,
            refractionAlpha = 0.30f,
            borderAlpha = 0.55f,
            blurRadius = 24.dp,
            cornerRadius = 32.dp,
            shadowElevation = 12.dp,
            tint = Color(0x144E9082),
            specular = Color(0x8CFFFFFF),
            fillBase = Color(0xFFF7F8F8),
            fillBaseBottom = Color(0xFFEDF1F0),
        )

        val dark = CtGlassDockSpec(
            fillAlpha = 0.72f,
            highlightAlpha = 0.42f,
            refractionAlpha = 0.24f,
            borderAlpha = 0.40f,
            blurRadius = 20.dp,
            cornerRadius = 32.dp,
            shadowElevation = 10.dp,
            tint = Color(0x1890D3C4),
            specular = Color(0x59FFFFFF),
            fillBase = Color(0xFF1A1F1E),
            fillBaseBottom = Color(0xFF111514),
        )
    }

    /** 转成通用玻璃 token（供 [ctDockGlass] 复用）。 */
    fun toTokens(): CtGlassColorTokens = CtGlassColorTokens(
        fillStart = fillBase.copy(alpha = fillAlpha),
        fillEnd = fillBaseBottom.copy(alpha = fillAlpha * 0.92f),
        borderStart = specular.copy(alpha = borderAlpha),
        borderEnd = specular.copy(alpha = borderAlpha * 0.25f),
        specular = specular.copy(alpha = highlightAlpha * 0.5f),
        innerShadow = Color.Black.copy(alpha = 0.18f),
        tint = tint,
        shadowElevation = shadowElevation,
        blurRadius = blurRadius,
    )
}

/**
 * 玻璃表面（半透明填充 + 描边 + 折射高光 + 内阴影）。
 *
 * 注意：**不执行背景模糊**（避免依赖 Haze）；需要真实模糊时由页面在下方垫模糊层，
 * 本组件半透明即可透出。`tokens` 默认跟随主题（浅/深）。
 */
@Composable
fun CtGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CtGlass.defaultShape,
    tokens: CtGlassColorTokens = CtGlass.tokens(),
    shadow: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier.ctGlassSurface(tokens = tokens, shape = shape, shadow = shadow),
        ) {
            content()
        }
    }
}

/**
 * 玻璃外观 Modifier（可叠加在任何容器上；含投影/填充/描边/折射高光）。
 *
 * @param shadow 是否加投影（列表内密集玻璃建议关闭以省性能）
 */
fun Modifier.ctGlassSurface(
    tokens: CtGlassColorTokens,
    shape: Shape,
    shadow: Boolean = true,
): Modifier {
    var modifier = this
    if (shadow) {
        modifier = modifier.shadow(elevation = tokens.shadowElevation, shape = shape, clip = false)
    }
    return modifier
        .clip(shape)
        .background(
            Brush.verticalGradient(listOf(tokens.fillStart, tokens.fillEnd)),
        )
        .border(
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(tokens.borderStart, tokens.borderEnd)),
            ),
            shape = shape,
        )
        .ctGlassRefraction(tokens = tokens, shape = shape)
}

/** 浮岛底栏玻璃（内部用 [CtGlassDockSpec] 转 token；浅/深跟随主题）。 */
@Composable
fun Modifier.ctDockGlass(shape: Shape = RoundedCornerShape(32.dp)): Modifier {
    val spec = CtGlass.dockTokens()
    return ctGlassSurface(tokens = spec.toTokens(), shape = shape, shadow = true)
}

/**
 * 折射高光绘制（玻璃质感核心）：
 * 1. 顶部约 40% 高度：白→透 specular 渐变（模拟光从上方折射）；
 * 2. 底部 25%：内阴影渐变（玻璃厚度/底部反射）；
 * 3. 顶部 1px 亮边 + 左右边缘细高光（沿圆角路径 stroke）。
 */
fun Modifier.ctGlassRefraction(
    tokens: CtGlassColorTokens,
    shape: Shape,
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val topShine = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to tokens.specular.copy(alpha = tokens.specular.alpha.coerceIn(0f, 1f)),
            0.14f to tokens.specular.copy(alpha = tokens.specular.alpha * 0.45f),
            0.42f to Color.Transparent,
            1f to Color.Transparent,
        ),
        endY = size.height,
    )
    val bottomShade = Brush.verticalGradient(
        colorStops = arrayOf(
            0.68f to Color.Transparent,
            1f to tokens.innerShadow,
        ),
        endY = size.height,
    )
    val edgeShine = Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to tokens.specular.copy(alpha = tokens.specular.alpha * 0.6f),
            0.12f to Color.Transparent,
            0.88f to Color.Transparent,
            1f to tokens.specular.copy(alpha = tokens.specular.alpha * 0.6f),
        ),
    )
    val tintBrush = Brush.verticalGradient(
        colors = listOf(tokens.tint, Color.Transparent),
        endY = size.height * 0.6f,
    )
    onDrawWithContent {
        drawContent()
        clipPath(path) {
            drawRect(topShine)
            drawRect(bottomShade)
            drawRect(tintBrush)
        }
        // 顶部 1px 亮边
        drawPath(
            path = path,
            brush = edgeShine,
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

/** 圆角路径 helper（供自定义 Canvas 使用）。 */
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
