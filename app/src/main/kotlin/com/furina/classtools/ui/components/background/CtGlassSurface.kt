package com.furina.classtools.ui.components.background

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.furina.classtools.ui.components.glass.ctGlass
import com.furina.classtools.ui.components.glass.ctGlassPointerLight
import com.furina.classtools.ui.components.glass.rememberCtGlassPress
import com.furina.classtools.ui.components.light.CtLightInk
import com.furina.classtools.ui.theme.CtDarkGlassRole
import com.furina.classtools.ui.theme.CtElevation
import com.furina.classtools.ui.theme.CtLightIntensity
import com.furina.classtools.ui.theme.CtLightLevel
import com.furina.classtools.ui.theme.CtPalette
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import com.furina.classtools.ui.theme.LocalCtLight
import com.furina.classtools.ui.theme.rememberCtColors

// =============================================================================
// [CtGlassTokens] 保留旧绘制器 ctFrostedCard 的规格。
// CtGlassSurface 的深色底材由 DarkGlassMaterial 解析，浅色使用 ctGlass。
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

/** ctFrostedCard 的材质规格：明暗 × 沉浸光感强度。 */
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
            topHighlightAlpha = 0.03f,
            edgeTopAlpha = 0.14f,
            edgeBottomAlpha = 0.20f,
            shadowElevation = CtElevation.none,
        )

        intensity == CtLightIntensity.STRONG -> CtGlassSpec(
            baseAlpha = 0.8f,
            topHighlightAlpha = 0.09f,
            edgeTopAlpha = 0.26f,
            edgeBottomAlpha = 0.30f,
            shadowElevation = CtElevation.none,
        )

        intensity == CtLightIntensity.WEAK -> CtGlassSpec(
            baseAlpha = 0.83f,
            topHighlightAlpha = 0.06f,
            edgeTopAlpha = 0.18f,
            edgeBottomAlpha = 0.26f,
            shadowElevation = CtElevation.none,
        )

        else -> CtGlassSpec(
            baseAlpha = 0.82f,
            topHighlightAlpha = 0.07f,
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
 * 卡片表面。深色填充先校验合成对比度，有同窗口背景源时再模糊底图。内容和内边距由调用方定义。
 *
 * @param onClick 非 null 时使用可点击的 Material Surface。
 * @param boost 浅色装饰材质的底色不透明度补偿。
 * @param body 指定浅色正文表面。
 * @param frosted 使用半透明材质，或使用实色底。
 * @param opaque 玻璃化关闭时使用实色 Material 卡片。
 * @param darkRole Hero 默认采用高层容器，其他卡片采用低层容器。
 * @param containerColor 调用方指定的语义表面。
 * @param interactionSource 与缩放等按压反馈共享的交互源。
 */
@Composable
fun CtGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    onClick: (() -> Unit)? = null,
    boost: Float = LocalCtBackgroundCardBoost.current,
    body: Boolean = true,
    frosted: Boolean = true,
    opaque: Boolean = true,
    darkRole: CtDarkGlassRole? = null,
    containerColor: Color = Color.Unspecified,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val c = rememberCtColors()
    val lightEnabled = LocalCtLight.current.enabled
    // 玻璃化开关关：实色 M3；开：走下方磨砂玻璃材质（beta1 风格）。
    if (c.dark || body || (opaque && !lightEnabled)) {
        val scheme = MaterialTheme.colorScheme
        val base = when {
            containerColor != Color.Unspecified -> containerColor
            darkRole == CtDarkGlassRole.HERO -> scheme.surfaceContainerHigh
            else -> scheme.surfaceContainerLow
        }
        val material = if (c.dark && frosted) rememberDarkGlass(base) else DarkGlassMaterial(base, 0.dp)
        val container = material.fill
        val surfaceModifier = modifier.fillMaxWidth().darkGlassBlur(shape, material)
        val border = BorderStroke(1.dp, scheme.outlineVariant)
        if (onClick != null) {
            Surface(
                onClick = onClick,
                interactionSource = interactionSource,
                modifier = surfaceModifier,
                shape = shape,
                color = container,
                contentColor = scheme.onSurface,
                border = border,
                shadowElevation = if (c.dark) 0.dp else 1.dp,
            ) { content() }
        } else {
            Surface(
                modifier = surfaceModifier,
                shape = shape,
                color = container,
                contentColor = scheme.onSurface,
                border = border,
                shadowElevation = if (c.dark) 0.dp else 1.dp,
            ) { content() }
        }
        return
    }
    val material: Modifier = if (frosted) {
        val role = darkRole.takeIf { c.dark && lightEnabled }
        // 深色工作台卡的反光固定在卡面，点击反馈由 Surface 提供。
        val press = if (role == null) rememberCtGlassPress() else null
        val pointerLight = if (press == null) Modifier else Modifier.ctGlassPointerLight(press)
        pointerLight.ctGlass(
            level = if (body) CtLightLevel.THIN else CtLightLevel.ULTRA_THIN,
            shape = shape,
            bodySafe = body,
            pressState = press,
            fillBoost = boost,
            darkRole = role,
        )
    } else {
        Modifier
            .then(
                if (c.dark) {
                    Modifier
                } else {
                    Modifier.shadow(
                        CtElevation.card,
                        shape,
                        clip = false,
                        ambientColor = c.cardShadow,
                        spotColor = c.cardShadow,
                    )
                },
            )
            .clip(shape)
            .background(c.containerLow)
    }
    val contentColor = c.textPrimary
    if (onClick != null) {
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = modifier.fillMaxWidth().then(material),
            shape = shape,
            color = Color.Transparent,
            contentColor = contentColor,
            shadowElevation = 0.dp,
        ) { content() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth().then(material),
            shape = shape,
            color = Color.Transparent,
            contentColor = contentColor,
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
 * 窗口背景层 CtAppBackdrop 为前景卡片提供这个补偿。
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
