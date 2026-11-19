package com.furina.classtools.ui.components.background

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.background.CtBackgroundCheck
import com.furina.classtools.data.background.CtBackgroundPalette
import com.furina.classtools.data.background.CtBackgroundPreset
import com.furina.classtools.data.background.ctBackgroundContrast
import com.furina.classtools.ui.components.light.CtLightInk
import com.furina.classtools.ui.theme.CtElevation
import com.furina.classtools.ui.theme.CtLightIntensity
import com.furina.classtools.ui.theme.CtPalette
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import com.furina.classtools.ui.theme.LocalCtLight
import com.furina.classtools.ui.theme.rememberCtColors

// =============================================================================
// 磨砂玻璃卡片材质（Frosted Glass Card）—— 所有卡片的**唯一材质来源**
//
// 视觉分层（自下而上）：
//   1) 半透明底：浅色 = 白 0.55–0.70、深色 = 深容器 0.45–0.60（**[CtGlassTokens] 唯一映射表**）；
//   2) 顶部线性高光（白 0.18 → 0，营造「玻璃被上方光照亮」的厚度感）；
//   3) **1px 高光边**（顶部更亮、底部极淡）—— 取代纯色描边，保证深色下「清晰不灰糊」；
//   4) 柔和外阴影（浅色 0.10 墨色 8dp；深色不投影，靠高光边分层）。
//
// 可读性红线（AA）：底不透明度随「背景花哨程度」自动抬高
//   - 纯色预设：[CtGlassTokens.baseAlpha]
//   - 渐变预设 / 自定义图片：+ [CtBackgroundColors.cardAlphaBoost]（最多 +0.30）
//   - 封闭区（列表行/输入框）不用玻璃，保持实色 container（见 §3.8）
// 单测 `CtGlassTokensTest` 锁定：任意预设 × 明暗下「正文压在玻璃上」的合成对比度 ≥ 4.5。
// =============================================================================

/**
 * 磨砂玻璃参数（唯一映射表的一行）。
 *
 * @param baseAlpha 半透明底不透明度（浅 0.55–0.70 / 深 0.45–0.60）
 * @param topHighlightAlpha 顶部线性高光起点不透明度
 * @param edgeTopAlpha 1px 高光边（顶部）不透明度
 * @param edgeBottomAlpha 1px 暗边（底部）不透明度
 * @param shadowElevation 柔和外阴影（深色为 0）
 */
data class CtGlassSpec(
    val baseAlpha: Float,
    val topHighlightAlpha: Float,
    val edgeTopAlpha: Float,
    val edgeBottomAlpha: Float,
    val shadowElevation: Dp,
)

/** 卡片玻璃的**唯一映射表**：明暗 × 沉浸光感强度。 */
object CtGlassTokens {

    /** 浅色玻璃（白底）。 */
    fun light(intensity: CtLightIntensity?, enabled: Boolean): CtGlassSpec = when {
        // 光感关闭：保留玻璃观感，但更实（接近实色卡片，最省电、最稳）
        !enabled -> CtGlassSpec(
            baseAlpha = 0.92f,
            topHighlightAlpha = 0.06f,
            edgeTopAlpha = 0.30f,
            edgeBottomAlpha = 0.06f,
            shadowElevation = CtElevation.card,
        )

        intensity == CtLightIntensity.STRONG -> CtGlassSpec(
            baseAlpha = 0.55f,
            topHighlightAlpha = 0.22f,
            edgeTopAlpha = 0.60f,
            edgeBottomAlpha = 0.05f,
            shadowElevation = CtElevation.card,
        )

        intensity == CtLightIntensity.WEAK -> CtGlassSpec(
            baseAlpha = 0.68f,
            topHighlightAlpha = 0.14f,
            edgeTopAlpha = 0.40f,
            edgeBottomAlpha = 0.06f,
            shadowElevation = CtElevation.card,
        )

        else -> CtGlassSpec(
            baseAlpha = 0.62f,
            topHighlightAlpha = 0.18f,
            edgeTopAlpha = 0.50f,
            edgeBottomAlpha = 0.05f,
            shadowElevation = CtElevation.card,
        )
    }

    /** 深色玻璃（深容器底；比浅色更实一档，避免「灰糊」）。 */
    fun dark(intensity: CtLightIntensity?, enabled: Boolean): CtGlassSpec = when {
        !enabled -> CtGlassSpec(
            baseAlpha = 0.94f,
            topHighlightAlpha = 0.05f,
            edgeTopAlpha = 0.14f,
            edgeBottomAlpha = 0.20f,
            shadowElevation = CtElevation.none,
        )

        intensity == CtLightIntensity.STRONG -> CtGlassSpec(
            baseAlpha = 0.45f,
            topHighlightAlpha = 0.16f,
            edgeTopAlpha = 0.26f,
            edgeBottomAlpha = 0.30f,
            shadowElevation = CtElevation.none,
        )

        intensity == CtLightIntensity.WEAK -> CtGlassSpec(
            baseAlpha = 0.58f,
            topHighlightAlpha = 0.10f,
            edgeTopAlpha = 0.18f,
            edgeBottomAlpha = 0.26f,
            shadowElevation = CtElevation.none,
        )

        else -> CtGlassSpec(
            baseAlpha = 0.52f,
            topHighlightAlpha = 0.13f,
            edgeTopAlpha = 0.22f,
            edgeBottomAlpha = 0.28f,
            shadowElevation = CtElevation.none,
        )
    }

    /** 纯函数入口：明暗 × 光感设置。 */
    fun of(dark: Boolean, intensity: CtLightIntensity?, enabled: Boolean): CtGlassSpec =
        if (dark) dark(intensity, enabled) else light(intensity, enabled)

    /** 玻璃底色的**不透明度下限**（承载正文的卡片不允许更透；1px 高光边仍生效）。 */
    const val MIN_BODY_ALPHA: Float = 0.62f

    /** 玻璃底色的上限（再多就等于实色，失去磨砂感）。 */
    const val MAX_BODY_ALPHA: Float = 0.92f

    /**
     * 根据背景「花哨程度」计算最终底不透明度。
     *
     * @param spec 基础规格
     * @param boost 背景补偿（[com.furina.classtools.data.background.CtBackgroundColors.cardAlphaBoost]）
     * @param body 是否承载正文（true → 至少 [MIN_BODY_ALPHA]；列表行/输入框不走玻璃）
     */
    fun resolvedAlpha(spec: CtGlassSpec, boost: Float, body: Boolean = true): Float =
        (spec.baseAlpha + boost.coerceIn(MIN_BLOCK_BOOST, 0.30f))
            .coerceIn(MIN_BODY_ALPHA, MAX_BODY_ALPHA)

    /** 背景补偿的下限（负补偿最多 -0.30；实际不会低于 [MIN_BODY_ALPHA]）。 */
    const val MIN_BLOCK_BOOST: Float = -0.30f

    /** 玻璃底色（浅 = 白 / 深 = 深容器）。 */
    fun baseColor(dark: Boolean): Color = if (dark) CtPalette.DarkContainer else CtPalette.Surface

    /** 玻璃底的「深色」实际取色：与页面底同色相的 surfaceContainer（CtPalette.DarkContainer #1E2326）。 */
    fun darkBaseColor(): Color = CtPalette.DarkContainer

    /** 高光边颜色（白）。 */
    fun highlightColor(): Color = CtLightInk.highlight
}

/**
 * 当前磨砂玻璃规格（按主题明暗 + 沉浸光感强度 + 背景补偿解析一次）。
 */
@Composable
fun rememberCtGlassSpec(boost: Float = 0f): CtGlassSpec {
    val dark = LocalCtDarkTheme.current
    val light = LocalCtLight.current
    val base = remember(dark, light.setting, light.enabled) {
        if (light.enabled) {
            CtGlassTokens.of(dark, light.setting.intensity, true)
        } else {
            CtGlassTokens.of(dark, null, false)
        }
    }
    return if (boost <= 0f) base else base.copy(baseAlpha = (base.baseAlpha + boost).coerceAtMost(CtGlassTokens.MAX_BODY_ALPHA))
}

/**
 * 磨砂玻璃卡片容器（**所有卡片组件的底层实现**）。
 *
 * 只负责「画材质 + 给可点反馈」，不定义内边距/排版；调用方（CtCard / CtGlassCard / ToolCard …）
 * 继续负责内容布局，因此改造是「换材质」而非「换结构」。
 *
 * @param onClick 非 null 时可点（自定义 interactionSource/indication 由调用方保留）
 * @param boost 背景补偿（来自 [com.furina.classtools.data.background.CtBackgroundColors.cardAlphaBoost]）
 * @param body 承载正文（影响不透明度下限）
 * @param frosted true = 磨砂玻璃；false = 实色（列表行/输入框/媒体卡）
 */
@Composable
fun CtGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    onClick: (() -> Unit)? = null,
    boost: Float = 0f,
    body: Boolean = true,
    frosted: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = LocalCtDarkTheme.current
    val c = rememberCtColors()
    val spec = if (frosted) rememberCtGlassSpec(boost) else null
    val alpha = spec?.let { CtGlassTokens.resolvedAlpha(it, boost, body) } ?: 1f
    // 半透明底：浅色用白（CtGlassTokens.baseColor），深色用 surfaceContainer(#1E2326)。
    // 注意：深色不能用 DarkContainer 再叠图片（会透出中灰 → 卡片/文字发灰）；surfaceContainer 与页面底同色相，
    // 透出来的是「同色相的磨砂」而不是灰雾。
    val baseFill = if (dark) MaterialTheme.colorScheme.surfaceContainer else CtGlassTokens.baseColor(false)
    val fill = if (frosted && spec != null) {
        baseFill.copy(alpha = alpha)
    } else {
        c.containerLow
    }
    val material: Modifier = if (frosted && spec != null) {
        Modifier
            .then(
                if (!dark && spec.shadowElevation > 0.dp) {
                    Modifier.shadow(
                        elevation = spec.shadowElevation,
                        shape = shape,
                        clip = false,
                        ambientColor = c.cardShadow,
                        spotColor = c.cardShadow,
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(fill)
            .frostedEdge(shape, spec)
    } else {
        Modifier
            .then(
                if (!dark) {
                    Modifier.shadow(CtElevation.card, shape, clip = false, ambientColor = c.cardShadow, spotColor = c.cardShadow)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(c.containerLow)
            .clip(shape)
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().then(material),
            shape = shape,
            color = Color.Transparent,
            contentColor = c.textPrimary,
            shadowElevation = 0.dp,
        ) { content() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth().then(material),
            shape = shape,
            color = Color.Transparent,
            contentColor = c.textPrimary,
            shadowElevation = 0.dp,
        ) { content() }
    }
}

/**
 * 玻璃的边缘与高光：顶部线性高光（0 → [CtGlassSpec.topHighlightAlpha]）+ 1px 高光边。
 *
 * 用 `drawWithCache` 缓存渐变，滚动/重组时不重建。
 */
private fun Modifier.frostedEdge(shape: Shape, spec: CtGlassSpec): Modifier = drawWithCache {
    val highlight = Brush.verticalGradient(
        0f to CtLightInk.highlight.copy(alpha = spec.topHighlightAlpha),
        0.42f to Color.Transparent,
    )
    val edgeTop = CtLightInk.highlight.copy(alpha = spec.edgeTopAlpha)
    val edgeBottom = (if (spec.edgeBottomAlpha > 0.2f) CtLightInk.highlight else CtLightInk.shade)
        .copy(alpha = spec.edgeBottomAlpha)
    onDrawBehind {
        // 顶部线性高光
        drawRect(brush = highlight)
        // 1px 高光边（上亮下淡，形成玻璃厚度）
        val hair = 1f
        drawRect(color = edgeTop, topLeft = Offset(0f, 0f), size = Size(size.width, hair))
        drawRect(
            color = edgeBottom,
            topLeft = Offset(0f, size.height - hair),
            size = Size(size.width, hair),
        )
    }
}

/**
 * 背景补偿系数（当前预设/图片 → [com.furina.classtools.data.background.CtBackgroundColors.cardAlphaBoost]）。
 *
 * 卡片组件通过 CompositionLocal 读到它（由 `ExpressiveBackground` 每页注入），
 * 这样「背景越花 → 卡片越实」全局自动生效，页面代码零改动。
 */
val LocalCtBackgroundCardBoost = androidx.compose.runtime.staticCompositionLocalOf { 0f }

/** 便捷：读当前背景补偿。 */
@Composable
fun rememberCtBackgroundBoost(): Float = LocalCtBackgroundCardBoost.current

/** 背景可读性自检（诊断/预览用）：当前明暗 × 预设的 AA 对比度。 */
fun ctGlassContrastCheck(preset: CtBackgroundPreset, dark: Boolean): CtBackgroundCheck =
    ctBackgroundContrast(preset, dark)

/**
 * 兼容旧名：`Modifier.ctGlassSurface(tokens = CtMaterial(…))`。
 *
 * 旧代码（如 `CtGlassCard` 的内描边）会传 `CtMaterial`；这里统一转成磨砂玻璃材质，
 * 保证「所有卡片一个材质来源」。
 */
fun Modifier.ctFrostedCard(shape: Shape, spec: CtGlassSpec, fill: Color): Modifier =
    this
        .clip(shape)
        .background(fill.copy(alpha = spec.baseAlpha))
        .frostedEdge(shape, spec)

/** 内部：把 [CtBackgroundPalette] 的补偿直接映射到玻璃（设置页预览卡片用）。 */
internal fun previewBoost(preset: CtBackgroundPreset, dark: Boolean): Float =
    CtBackgroundPalette.of(preset, dark).cardAlphaBoost
