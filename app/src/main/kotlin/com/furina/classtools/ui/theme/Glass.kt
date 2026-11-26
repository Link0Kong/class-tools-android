package com.furina.classtools.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// =============================================================================
// 玻璃材质规格表（Glass Spec）—— 沉浸光感「材质层」的唯一数值来源
//
// 官方材质由**多层效果叠加**而成（材质滤镜 / 折射 / 高光 / 阴影），本表把这套语言
// 落成 7 个可绘制/可渲染的自由度：
//
//   1) [fillTop] / [fillBottom]  材质底色（自带上/下不透明度差 → 玻璃「厚度」的错觉）
//   2) [tint] / [tintAlpha]      材质赋色（极低饱和的品牌色，避免白玻璃发灰、黑玻璃发脏）
//   3) [specularAlpha] / [specularSize]  **点光源高光**：左上角一团柔白，按下时跟随手指
//   4) [edgeTopAlpha] / [edgeBottomAlpha] **折射亮边**：上沿亮、下沿暗的 1dp 双色边
//   5) [innerShadeAlpha]         内阴影：底部向内压暗，塑造厚度
//   6) [shadowAlpha] / [shadowBlur] / [shadowDy] 外投影（浅色必有、深色极淡）
//   7) [blurRadius] / [noiseFactor] **材质滤镜**（Haze 背景采样模糊 + 颗粒）
//
// 档位 × 强度 → 规格是**纯函数**（[CtGlassTable.specOf]），可 JVM 单测；
// 强度语义与官方一致：强 = 更透 + 模糊更大 + 高光更亮；弱 = 更实 + 模糊更小 + 高光收敛。
//
// 可读性红线：承载正文的材质不透明度不低于 [CtGlassTable.MIN_BODY_ALPHA]（AA）。
// 该下限与 `docs/DESIGN_SYSTEM.md` 的「正文不落在 <0.62 材质上」一致。
// =============================================================================

/**
 * 单档玻璃材质规格（全部绘制参数的唯一载体）。
 *
 * 颜色已含 alpha：渲染层直接 `background(Brush.verticalGradient(fillTop, fillBottom))`。
 * 这样「上亮下暗」的玻璃厚度感由色值本身表达，不再额外叠一层渐变。
 */
@Immutable
data class CtGlassSpec(
    /** 材质底色（顶部，通常更亮/更透）。 */
    val fillTop: Color,
    /** 材质底色（底部，通常更实）。 */
    val fillBottom: Color,
    /** 材质赋色（与底色混合的低饱和品牌色）。 */
    val tint: Color,
    /** 赋色强度（0..1）。 */
    val tintAlpha: Float,
    /** 点光源高光峰值不透明度（左上角柔白）。 */
    val specularAlpha: Float,
    /** 点光源高光半径系数（× 组件短边；>1 表示大面积泛光）。 */
    val specularSize: Float,
    /** 上沿折射亮边不透明度（1.2dp）。 */
    val edgeTopAlpha: Float,
    /** 下沿暗边/亮边不透明度（1dp；深色为亮边，浅色为暗边）。 */
    val edgeBottomAlpha: Float,
    /** 底部内阴影不透明度（塑造厚度）。 */
    val innerShadeAlpha: Float,
    /** 外投影不透明度（0 = 无投影）。 */
    val shadowAlpha: Float,
    /** 外投影模糊半径。 */
    val shadowBlur: Dp,
    /** 外投影纵向偏移（光从上方来 → 投影落在下方）。 */
    val shadowDy: Dp,
    /** 背景采样模糊半径（Haze；0 = 不做真模糊，仅半透明）。 */
    val blurRadius: Dp,
    /** 颗粒强度（0..0.05；抑制大面积极淡渐变在深色下的色带）。 */
    val noiseFactor: Float,
    /** 材质整体额外透明度系数（动画/进出场用；1 = 原样）。 */
    val alpha: Float = 1f,
)

/** 深色玻璃化开启时，按表面用途选用的材质。 */
enum class CtDarkGlassRole(val level: CtLightLevel, internal val fillAlpha: Float) {
    HERO(CtLightLevel.THIN, 0.72f),
    CARD(CtLightLevel.MEDIUM, 0.86f),
    DOCK(CtLightLevel.THIN, 0.92f),
}

/**
 * 玻璃材质唯一映射表：level × intensity × dark → [CtGlassSpec]。
 *
 * 数值来源：官方 5 档材质的场景语义 + 本机实测（1080×2400 / 420dpi 模拟器）逐档调参。
 * 其中浅色 ULTRA_THIN 的 `fillTop` 不透明度 0.46 是「能看出背后青光、正文仍可读」的临界值。
 */
object CtGlassTable {

    /** 承载正文时的最低底色不透明度（浅色板）：正文对比度仍 ≥ AA 的实测下限。 */
    const val MIN_BODY_ALPHA_LIGHT: Float = 0.38f

    /** 承载正文时的最低底色不透明度（深色板）：深色底更「吃光」，需要更实一档。 */
    const val MIN_BODY_ALPHA_DARK: Float = 0.54f

    /** 兼容旧引用（浅色板下限）。 */
    const val MIN_BODY_ALPHA: Float = MIN_BODY_ALPHA_LIGHT

    /** 关掉光感后的「实色」不透明度（等价旧规范，最稳）。 */
    const val OFF_ALPHA: Float = 1f

    /** 档位序号（越厚越大）。 */
    fun levelIndex(level: CtLightLevel): Int = when (level) {
        CtLightLevel.ULTRA_THIN -> 0
        CtLightLevel.THIN -> 1
        CtLightLevel.MEDIUM -> 2
        CtLightLevel.THICK -> 3
        CtLightLevel.ULTRA_THICK -> 4
    }

    // 锚点（BALANCED）：从超薄到超厚
    private val fillAlphaTop = floatArrayOf(0.30f, 0.38f, 0.46f, 0.54f, 0.64f)
    private val fillAlphaBottom = floatArrayOf(0.17f, 0.24f, 0.33f, 0.42f, 0.54f)
    private val blurRadius = floatArrayOf(10f, 16f, 22f, 30f, 38f)
    private val edgeTop = floatArrayOf(0.62f, 0.52f, 0.42f, 0.32f, 0.24f)
    private val edgeBottom = floatArrayOf(0.10f, 0.12f, 0.14f, 0.16f, 0.18f)
    private val specular = floatArrayOf(0.30f, 0.24f, 0.18f, 0.14f, 0.10f)
    private val innerShade = floatArrayOf(0.04f, 0.05f, 0.06f, 0.07f, 0.08f)
    private val shadow = floatArrayOf(0.05f, 0.07f, 0.09f, 0.11f, 0.13f)
    private val shadowBlur = floatArrayOf(20f, 24f, 30f, 36f, 44f)

    /** 强度因子：[fill, blur, edge, specular, shadow] 的乘数。 */
    private fun factors(intensity: CtLightIntensity?): FloatArray = when (intensity) {
        CtLightIntensity.STRONG -> floatArrayOf(0.86f, 1.30f, 1.18f, 1.25f, 1.10f)
        CtLightIntensity.WEAK -> floatArrayOf(1.14f, 0.62f, 0.78f, 0.62f, 0.85f)
        else -> floatArrayOf(1f, 1f, 1f, 1f, 1f)
    }

    /**
     * 查表（唯一入口）。
     *
     * @param level 材质档（场景决定）
     * @param setting 用户光感设置（关闭 → 实色规格）
     * @param dark 是否深色模式（深色板：更实、亮边代替投影、赋色更冷）
     * @param bodySafe 承载正文时抬高通用材质的底色不透明度。
     * @param darkRole 深色玻璃化开启时采用的表面用途。
     * @param fillBoost 预设或自定义图片给底色提供的不透明度补偿。
     */
    fun specOf(
        level: CtLightLevel,
        setting: CtLightSetting,
        dark: Boolean,
        bodySafe: Boolean = true,
        darkRole: CtDarkGlassRole? = null,
        fillBoost: Float = 0f,
    ): CtGlassSpec {
        if (!setting.enabled) {
            val solid = if (dark) CtPalette.DarkContainerLow else CtPalette.Surface
            return CtGlassSpec(
                fillTop = solid,
                fillBottom = solid,
                tint = Color.Transparent,
                tintAlpha = 0f,
                specularAlpha = 0f,
                specularSize = 1f,
                edgeTopAlpha = if (dark) 0.10f else 0.35f,
                edgeBottomAlpha = if (dark) 0.14f else 0.05f,
                innerShadeAlpha = 0f,
                shadowAlpha = if (dark) 0f else 0.05f,
                shadowBlur = 20.dp,
                shadowDy = 6.dp,
                blurRadius = 0.dp,
                noiseFactor = 0f,
            )
        }
        val role = darkRole.takeIf { dark }
        val i = levelIndex(role?.level ?: level)
        val f = factors(setting.intensity)
        val fillBaseTop = (fillAlphaTop[i] * f[0]).coerceIn(0.14f, 1f)
        val fillBaseBottom = (fillAlphaBottom[i] * f[0]).coerceIn(0.10f, 1f)
        // 承载正文 → 抬到安全线（正文可读优先于通透感）。
        val safeFloor = if (dark) MIN_BODY_ALPHA_DARK else MIN_BODY_ALPHA_LIGHT
        val safeTop = if (bodySafe) maxOf(fillBaseTop, safeFloor) else fillBaseTop
        val safeBottom = if (bodySafe) maxOf(fillBaseBottom, safeFloor - 0.06f) else fillBaseBottom

        // 浅色玻璃 = 白（含极淡品牌色）；深色玻璃 = 深青灰（比页面底亮半档，才能「浮」起来）
        val topColor = if (dark) Color(0xFF223032) else Color(0xFFFFFFFF)
        val bottomColor = if (dark) Color(0xFF151E20) else Color(0xFFF7FAFA)
        val tintColor = if (dark) CtPalette.MistTeal else CtPalette.SlateBlue

        val base = CtGlassSpec(
            fillTop = topColor.copy(alpha = safeTop),
            fillBottom = bottomColor.copy(alpha = safeBottom),
            tint = tintColor,
            tintAlpha = if (dark) 0.10f * f[0] else 0.035f * f[0],
            specularAlpha = specular[i] * f[3] * (if (dark) 0.55f else 1f),
            specularSize = 1.35f,
            edgeTopAlpha = edgeTop[i] * f[2] * (if (dark) 0.42f else 1f),
            edgeBottomAlpha = edgeBottom[i] * (if (dark) 1.6f else 1f),
            innerShadeAlpha = innerShade[i] * (if (dark) 1.8f else 1f),
            shadowAlpha = shadow[i] * f[4] * (if (dark) 0.35f else 1f),
            shadowBlur = (shadowBlur[i] * f[1] * 0.6f).dp,
            shadowDy = (shadowBlur[i] * 0.26f).dp,
            blurRadius = (blurRadius[i] * f[1]).dp,
            noiseFactor = if (dark) 0.045f else 0.022f,
        )
        val surface = if (role == null) base else base.forDarkRole(role, f[0])
        val boost = fillBoost.coerceIn(0f, 0.34f)
        return if (boost <= 0.001f) surface else surface.copy(
            fillTop = surface.fillTop.copy(alpha = (surface.fillTop.alpha + boost).coerceAtMost(1f)),
            fillBottom = surface.fillBottom.copy(alpha = (surface.fillBottom.alpha + boost).coerceAtMost(1f)),
        )
    }

    private fun CtGlassSpec.forDarkRole(role: CtDarkGlassRole, fillFactor: Float): CtGlassSpec {
        val alpha = role.fillAlpha + (fillFactor - 1f) * 0.20f
        val top = when (role) {
            CtDarkGlassRole.HERO -> CtPalette.DarkContainer
            CtDarkGlassRole.CARD -> lerp(CtPalette.DarkContainerLow, CtPalette.DarkContainer, 0.55f)
            CtDarkGlassRole.DOCK -> CtPalette.DarkContainerLow
        }
        val bottom = if (role == CtDarkGlassRole.DOCK) CtPalette.DarkBar else CtPalette.DarkContainerLow
        val reflection = when (role) {
            CtDarkGlassRole.HERO -> 0.65f
            CtDarkGlassRole.CARD -> 0f
            CtDarkGlassRole.DOCK -> 0.18f
        }
        return copy(
            fillTop = top.copy(alpha = alpha),
            fillBottom = bottom.copy(alpha = alpha - 0.03f),
            tintAlpha = 0f,
            specularAlpha = specularAlpha * reflection,
            specularSize = 0.88f,
            edgeTopAlpha = 0f,
            edgeBottomAlpha = 0f,
            innerShadeAlpha = 0f,
            shadowAlpha = 0f,
        )
    }

    /** 场景版（页面最常用）：场景 → 档位 → 规格。 */
    fun specOf(
        scene: CtLightScene,
        setting: CtLightSetting,
        dark: Boolean,
        bodySafe: Boolean = ctLightCarriesBodyText(scene),
    ): CtGlassSpec = specOf(ctLightSceneOf(scene), setting, dark, bodySafe)

    /** 全表导出（单测 / 文档生成用）。 */
    fun all(dark: Boolean): List<CtGlassSpec> = CtLightLevel.entries.flatMap { level ->
        CtLightIntensity.entries.map { intensity -> specOf(level, CtLightSetting(intensity, true), dark) }
    }
}

// -----------------------------------------------------------------------------
// 交互形变（按下 / 悬停 / 选中的几何与光感变化）
// -----------------------------------------------------------------------------

/**
 * 交互形变参数（官方「交互形变 + 点光源」）。
 *
 * - 按下：整体 scale [pressScale]，材质提亮 [pressBrighten]，点光源跟着手指；
 * - 选中：圆角 [selectedRadiusDelta] 收紧 / 外扩，形成「形体响应」；
 * - 松手：弹簧回弹（[CtMotionTokens.springFastDamping]/[CtMotionTokens.springFastStiffness]）。
 */
object CtGlassInteraction {
    /** 按压缩放（M3 Expressive 0.96；玻璃材质比实色卡更能接受形变）。 */
    const val PRESS_SCALE: Float = 0.965f

    /** 按下时材质提亮的额外不透明度（叠加在 fill 上）。 */
    const val PRESS_BRIGHTEN: Float = 0.06f

    /** 按下时点光源半径放大系数。 */
    const val PRESS_SPECULAR_BOOST: Float = 1.25f

    /** 选中态圆角（dp）：卡片 20 → 24（更「圆润」，呼应 Expressive 形状）。 */
    val SELECTED_RADIUS: Dp = 24.dp
}
