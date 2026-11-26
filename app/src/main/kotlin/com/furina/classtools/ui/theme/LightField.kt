package com.furina.classtools.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.background.CtBackgroundPreset

// =============================================================================
// 光场（Light Field）—— 沉浸光感的「底」
//
// [CtLightFields] 提供光场配色，CtAuroraField 负责绘制。应用窗口固定光斑位置，滚动时内容经过同一片背景。
// 强 / 均衡 / 弱调整光斑与流光的强度。关闭后保留预设底色和环境光。
// =============================================================================

/**
 * 单团柔光斑。
 *
 * @param x 锚点 x（0..1，相对屏幕宽度）
 * @param y 锚点 y（0..1，相对屏幕高度；顶部悬浮层下方为主，避开状态栏）
 * @param radius 半径系数（× 屏幕长边；>0.5 表示大面积铺开，<0.4 表示局部聚光）
 * @param color 光斑色（品牌色低饱和）
 * @param alpha 峰值不透明度（0 表示该光斑在该模式下不出现）
 * @param driftX 水平漂移幅度（dp，±）
 * @param driftY 垂直漂移幅度（dp，±）
 * @param periodMs 漂移周期（毫秒；越长越「静止」，推荐 24–48s）
 * @param phase 相位（0..1，错开周期，避免所有光斑同步呼吸）
 */
@Immutable
data class CtLightBloom(
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Color,
    val alpha: Float,
    val driftX: Dp = 12.dp,
    val driftY: Dp = 10.dp,
    val periodMs: Int = 32_000,
    val phase: Float = 0f,
)

/**
 * 一整块光场（底 + 光斑 + 流光 + 暗角）。
 *
 * @param base 底色（浅色近白、深色近墨）
 * @param topGlow 顶部环境光（状态栏方向的弱渐变，模拟「顶光」）
 * @param blooms 柔光斑（绘制顺序 = 列表顺序，后面的叠在前面）
 * @param sheen 斜向流光色（一道 18° 的极淡光带，制造「玻璃被光扫过」的错觉）
 * @param sheenAlpha 流光峰值不透明度
 * @param vignette 四角叠加的暗角色
 * @param vignetteAlpha 暗角峰值不透明度
 */
@Immutable
data class CtLightField(
    val base: Color,
    val topGlow: Color,
    val blooms: List<CtLightBloom>,
    val sheen: Color,
    val sheenAlpha: Float,
    val vignette: Color,
    val vignetteAlpha: Float,
)

/**
 * 光场配色表（**唯一来源**）。
 *
 * 浅色：近白冷调底（#F4F7F6）+ 4 团 20–32% 品牌柔光；整体观感是「有颜色的白」，
 *      卡片压上去能明显看到青色/蓝色从边缘渗出来。
 * 深色：启用玻璃化的默认预设使用深板岩与靠近工作台「今日」卡的一团弱雾青光。
 * 其余深色预设使用各自的底色偏移和品牌光斑。
 *
 * 预设只调整 emphasis：雾青 → 青色斑 +25%；灰蓝 → 蓝色斑 +25%；暖沙 → 暖色斑 +25%；
 * 深空 / 夜幕 → 换成深色板的冷色偏移。
 */
object CtLightFields {

    // ---- 光斑基础色（品牌三色 + 一点薄荷，全部低饱和） ----------------------
    private val bloomTeal = Color(0xFF5FC4AC)
    private val bloomMint = Color(0xFF9FDCC8)
    private val bloomPeri = Color(0xFF93B0E2)
    private val bloomSand = Color(0xFFEDC197)
    private val bloomRose = Color(0xFFE3B0B8)

    // 深色板：同一批色相，提亮 + 提高 alpha（深底上「发光」需要更高对比）
    private val darkTeal = Color(0xFF2E8C7B)
    private val darkMint = Color(0xFF2F6E68)
    private val darkPeri = Color(0xFF33477F)
    private val darkSand = Color(0xFF7A5426)
    private val darkRose = Color(0xFF6E3A46)

    /** 浅色光场（未按预设加权）。 */
    private val lightField: CtLightField = CtLightField(
        base = Color(0xFFF1F6F4),
        topGlow = Color(0xFFFFFFFF),
        blooms = listOf(
            // 右上：雾青主光（最大、最亮，玻璃卡右边缘会明显泛青）
            CtLightBloom(
                x = 0.82f, y = 0.10f, radius = 0.86f, color = bloomTeal, alpha = 0.44f,
                driftX = 18.dp, driftY = 14.dp, periodMs = 36_000, phase = 0.00f,
            ),
            // 左中：灰蓝，给下半页压一层冷调，制造纵向层次
            CtLightBloom(
                x = 0.06f, y = 0.56f, radius = 0.88f, color = bloomPeri, alpha = 0.42f,
                driftX = 16.dp, driftY = 24.dp, periodMs = 44_000, phase = 0.34f,
            ),
            // 右下：暖沙，与青色形成互补，避免整屏发绿
            CtLightBloom(
                x = 0.84f, y = 0.94f, radius = 0.80f, color = bloomSand, alpha = 0.34f,
                driftX = 20.dp, driftY = 16.dp, periodMs = 40_000, phase = 0.62f,
            ),
            // 中上：一点薄荷提亮（让顶栏玻璃更通透）
            CtLightBloom(
                x = 0.38f, y = 0.30f, radius = 0.62f, color = bloomMint, alpha = 0.24f,
                driftX = 22.dp, driftY = 12.dp, periodMs = 28_000, phase = 0.18f,
            ),
        ),
        sheen = Color(0xFFFFFFFF),
        sheenAlpha = 0.55f,
        vignette = Color(0xFF8FA8A2),
        vignetteAlpha = 0.26f,
    )

    /** 深色光场（未按预设加权）。 */
    private val darkField: CtLightField = CtLightField(
        base = Color(0xFF080B0C),
        topGlow = Color(0xFF17232A),
        blooms = listOf(
            CtLightBloom(
                x = 0.84f, y = 0.10f, radius = 0.60f, color = darkTeal, alpha = 0.54f,
                driftX = 18.dp, driftY = 14.dp, periodMs = 38_000, phase = 0.10f,
            ),
            CtLightBloom(
                x = 0.02f, y = 0.54f, radius = 0.58f, color = darkPeri, alpha = 0.52f,
                driftX = 16.dp, driftY = 24.dp, periodMs = 46_000, phase = 0.42f,
            ),
            CtLightBloom(
                x = 0.86f, y = 0.94f, radius = 0.52f, color = darkSand, alpha = 0.34f,
                driftX = 20.dp, driftY = 16.dp, periodMs = 42_000, phase = 0.70f,
            ),
            CtLightBloom(
                x = 0.44f, y = 0.06f, radius = 0.34f, color = darkMint, alpha = 0.30f,
                driftX = 22.dp, driftY = 12.dp, periodMs = 30_000, phase = 0.24f,
            ),
        ),
        sheen = Color(0xFFBFE6FF),
        sheenAlpha = 0.10f,
        vignette = Color(0xFF000000),
        vignetteAlpha = 0.45f,
    )

    private val darkDefaultGlassField = CtLightField(
        base = Color(0xFF1F252A),
        topGlow = CtPalette.DarkBar,
        blooms = listOf(
            CtLightBloom(x = 0.82f, y = 0.19f, radius = 0.30f, color = Color(0xFF3B7166), alpha = 0.32f),
        ),
        sheen = darkField.sheen,
        sheenAlpha = 0.03f,
        vignette = darkField.vignette,
        vignetteAlpha = 0.16f,
    )

    /** 窗口光场，预设选色后再应用玻璃化开关和强度。 */
    fun forBackdrop(preset: CtBackgroundPreset, dark: Boolean, setting: CtLightSetting): CtLightField {
        val field = if (dark && setting.enabled && preset == CtBackgroundPreset.DEFAULT) {
            darkDefaultGlassField
        } else {
            of(preset, dark)
        }
        return scaled(field, alphaFactor(setting))
    }

    /**
     * 预设 → 光场（浅/深）。
     *
     * 只调「哪一团更亮」，不换语言：所有预设共享同一套光斑布局与漂移节奏，
     * 因此切换背景不会让 App 看起来像另一个产品。
     */
    fun of(preset: CtBackgroundPreset, dark: Boolean): CtLightField {
        val base = if (dark) darkField else lightField
        val e = emphasisOf(preset)
        val blooms = base.blooms.mapIndexed { index, bloom ->
            val f = e.factors.getOrElse(index) { 1f }
            bloom.copy(alpha = (bloom.alpha * f).coerceIn(0f, if (dark) 0.80f else 0.66f))
        }
        val tintedBase = e.baseTint?.let { tint ->
            blendColor(base.base, tint, if (dark) e.tintAmountDark else e.tintAmountLight)
        } ?: base.base
        // 深空：额外整体压暗（光斑 + 流光），确保它是 9 个预设里一眼可辨的「最暗中性」档。
        val dim = if (preset == CtBackgroundPreset.DEEP_SPACE) 0.45f else 1f
        return base.copy(
            base = tintedBase,
            blooms = blooms.map { it.copy(alpha = it.alpha * dim) },
            sheenAlpha = base.sheenAlpha * dim,
        )
    }

    /**
     * 预设的风格化参数（**唯一映射表**）。
     *
     * 光斑顺序：0 = 雾青（右上）· 1 = 灰蓝（左中）· 2 = 暖沙（右下）· 3 = 薄荷（中上）。
     *
     * @param factors 四团光斑的 alpha 系数（>1 强调、<1 收敛、0 关闭）
     * @param baseTint 底色偏移色（null = 不改底色）
     */
    private data class PresetEmphasis(
        val factors: FloatArray,
        val baseTint: Color? = null,
        val tintAmountDark: Float = 0.34f,
        val tintAmountLight: Float = 0.30f,
    )

    private fun emphasisOf(preset: CtBackgroundPreset): PresetEmphasis = when (preset) {
        // 暖白：浅色下就是默认光场（不加底色偏移）；深色下底色偏暖，避免和「深空」混为一谈。
        CtBackgroundPreset.WARM_WHITE -> PresetEmphasis(
            floatArrayOf(1f, 1f, 1f, 1f),
            baseTint = Color(0xFF2A2118),
            tintAmountDark = 0.38f,
            tintAmountLight = 0f,
        )
        CtBackgroundPreset.MIST_TEAL -> PresetEmphasis(
            floatArrayOf(1.95f, 0.35f, 0.10f, 0.95f),
            baseTint = Color(0xFF0B5C46),
            tintAmountDark = 0.34f,
        )
        CtBackgroundPreset.SLATE_BLUE -> PresetEmphasis(
            floatArrayOf(0.45f, 2.05f, 0.12f, 0.45f),
            baseTint = Color(0xFF1A2E66),
            tintAmountDark = 0.34f,
        )
        CtBackgroundPreset.WARM_SAND -> PresetEmphasis(
            floatArrayOf(0.25f, 0.35f, 2.10f, 0.40f),
            baseTint = Color(0xFF6B4318),
            tintAmountDark = 0.38f,
        )
        CtBackgroundPreset.DEEP_SPACE -> PresetEmphasis(
            floatArrayOf(0.25f, 0.45f, 0.08f, 0.12f),
            baseTint = Color(0xFF04070E),
            tintAmountDark = 0.30f,
            tintAmountLight = 0.30f,
        )
        CtBackgroundPreset.MORNING_MIST -> PresetEmphasis(
            floatArrayOf(0.85f, 1.05f, 0.12f, 2.10f),
            baseTint = Color(0xFF1A5A6E),
            tintAmountDark = 0.36f,
        )
        CtBackgroundPreset.SUNSET_ANGLE -> PresetEmphasis(
            floatArrayOf(0.12f, 0.20f, 2.20f, 0.25f),
            baseTint = Color(0xFF7A3A12),
            tintAmountDark = 0.40f,
        )
        CtBackgroundPreset.TEAL_INDIGO -> PresetEmphasis(
            floatArrayOf(1.60f, 1.60f, 0.10f, 0.50f),
            baseTint = Color(0xFF0F2F55),
            tintAmountDark = 0.32f,
        )
        CtBackgroundPreset.NIGHTFALL -> PresetEmphasis(
            floatArrayOf(0.35f, 1.95f, 0.18f, 0.20f),
            baseTint = Color(0xFF0A1030),
            tintAmountDark = 0.38f,
        )
    }

    /** 强度 → 光斑整体 alpha 系数（强 1 / 均衡 0.78 / 弱 0.5 / 关闭 0）。 */
    fun alphaFactor(setting: CtLightSetting): Float = when {
        !setting.enabled -> 0f
        setting.intensity == CtLightIntensity.STRONG -> 1f
        setting.intensity == CtLightIntensity.WEAK -> 0.5f
        else -> 0.78f
    }

    /** 按强度缩放光斑、流光与暗角。 */
    fun scaled(field: CtLightField, factor: Float): CtLightField {
        if (factor >= 0.999f) return field
        val f = factor.coerceIn(0f, 1f)
        return field.copy(
            blooms = field.blooms.map { it.copy(alpha = it.alpha * f) },
            sheenAlpha = field.sheenAlpha * f,
            vignetteAlpha = field.vignetteAlpha * (0.6f + 0.4f * f),
        )
    }
}

/** 两色线性混合（t = 0 取 [a]，1 取 [b]）；保持 a 的 alpha。 */
private fun blendColor(a: Color, b: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * f,
        green = a.green + (b.green - a.green) * f,
        blue = a.blue + (b.blue - a.blue) * f,
        alpha = a.alpha,
    )
}

/** 便捷：当前背景预设 + 明暗 → 未缩放的原始光场。 */
fun ctLightFieldOf(preset: CtBackgroundPreset, dark: Boolean): CtLightField =
    CtLightFields.of(preset, dark)

// -----------------------------------------------------------------------------
// 光场取样（纯函数；给「状态栏留白条」「边缘融合带」这类需要延续光场颜色的地方用）
// -----------------------------------------------------------------------------

/**
 * 在归一化坐标 (x, y) 处采样光场颜色。
 *
 * 与 `CtAuroraField` 的绘制口径一致：底 → 顶部环境光 → 每团光斑按径向衰减叠加
 * （浅色 SrcOver / 深色 Screen）。漂移取 t=0 的静态值（留白条不需要逐帧变化）。
 *
 * @param x 0..1（相对宽度）
 * @param y 0..1（相对高度）
 */
fun CtLightField.sampleAt(x: Float, y: Float, dark: Boolean): Color {
    var out = base
    // 顶部环境光：0 → 0.34 线性淡出（与渲染层一致）
    val glowAlpha = if (dark) 0.22f else 0.55f
    if (y < 0.34f) {
        val t = (1f - y / 0.34f) * glowAlpha
        out = out.compositeOver(topGlow.copy(alpha = t), dark)
    }
    blooms.forEach { bloom ->
        if (bloom.alpha <= 0.001f) return@forEach
        // 光斑用「相对屏幕长边」的半径；取样时以 1:1 正方形近似（长宽比在 0.4–0.6 之间影响很小）。
        val dx = (x - bloom.x) * 1f
        val dy = (y - bloom.y) * 1.8f // 纵向压缩：模拟长边半径在竖屏上的分布
        val d = kotlin.math.sqrt(dx * dx + dy * dy)
        val r = bloom.radius
        if (d >= r) return@forEach
        // 与渲染层一致的径向衰减：0 → peak，0.45 → 0.48×peak，1 → 0
        val falloff = when {
            d <= r * 0.45f -> 1f
            else -> 1f - ((d - r * 0.45f) / (r * 0.55f)).coerceIn(0f, 1f)
        }
        out = out.compositeOver(bloom.color.copy(alpha = bloom.alpha * falloff), dark)
    }
    return out
}

/** 光场合成（浅色 SrcOver / 深色 Screen —— 与绘制层的混合模式对齐）。 */
private fun Color.compositeOver(src: Color, dark: Boolean): Color {
    val a = src.alpha.coerceIn(0f, 1f)
    return if (dark) {
        // Screen：1-(1-dst)(1-src*a)
        fun screen(dst: Float, s: Float): Float {
            val sv = s * a
            return 1f - (1f - dst) * (1f - sv)
        }
        Color(
            red = screen(red, src.red),
            green = screen(green, src.green),
            blue = screen(blue, src.blue),
            alpha = 1f,
        )
    } else {
        Color(
            red = red * (1f - a) + src.red * a,
            green = green * (1f - a) + src.green * a,
            blue = blue * (1f - a) + src.blue * a,
            alpha = 1f,
        )
    }
}

/**
 * 光场顶部横条渐变（给状态栏留白 / 顶部融合带用）。
 *
 * 采样 y = [y] 处若干个横坐标点，构造横向渐变（`endX` 取默认值 = 绘制区域宽度，
 * 因此不需要外部传宽度 —— 传错宽度会把整条压成单色，2026-11-20 实测踩过这个坑）。
 */
fun CtLightField.topBandBrush(
    y: Float,
    dark: Boolean,
    stopCount: Int = 5,
): androidx.compose.ui.graphics.Brush {
    val stops = (0 until stopCount).map { i ->
        val t = i.toFloat() / (stopCount - 1).coerceAtLeast(1)
        t to sampleAt(t, y, dark)
    }.toTypedArray()
    return androidx.compose.ui.graphics.Brush.horizontalGradient(colorStops = stops)
}

// -----------------------------------------------------------------------------
// 漂移轨迹（纯函数，单测锚点）
// -----------------------------------------------------------------------------

/** 光场漂移数学（纯函数；渲染层只负责把结果写进 graphicsLayer / Canvas）。 */
object CtLightDrift {

    /** 单个光斑在 t（毫秒）时刻的归一化偏移（-1..1，正弦；不同相位错开）。 */
    fun offset(phase: Float, periodMs: Int, timeMs: Long): Float {
        if (periodMs <= 0) return 0f
        val t = (timeMs % periodMs).toFloat() / periodMs
        val angle = (t + phase) * 2f * Math.PI.toFloat()
        return kotlin.math.sin(angle)
    }

    /**
     * 呼吸系数（0.94..1.06）：光斑半径与亮度随时间极轻微起伏，
     * 让「静态截图」也有层次，同时幅度足够小、不会让文字背景闪烁。
     */
    fun breath(phase: Float, periodMs: Int, timeMs: Long): Float {
        val o = offset(phase, periodMs, timeMs)
        return 1f + o * 0.06f
    }
}
