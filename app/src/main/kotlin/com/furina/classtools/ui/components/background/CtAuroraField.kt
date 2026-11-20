package com.furina.classtools.ui.components.background

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.furina.classtools.ui.theme.CtLightBloom
import com.furina.classtools.ui.theme.CtLightDrift
import com.furina.classtools.ui.theme.CtLightField
import com.furina.classtools.ui.theme.rememberReduceMotion

// =============================================================================
// 光场渲染（沉浸光感的「底」）—— 见 ui/theme/LightField.kt 的令牌与设计口径
//
// 绘制顺序（自下而上）：
//   1) 底色（近白 / 近墨）
//   2) 顶部环境光（状态栏方向的白/冷光渐变，让顶栏玻璃有「顶光」可采样）
//   3) 柔光斑 ×4：径向渐变圆，深浅色用不同混合模式（深色 Screen 叠加出「发光」）
//   4) 斜向流光：一道 18° 的极淡光带（官方「边缘流光」的静态等效，给大面积留白层次）
//   5) 暗角：四角极轻压暗，把视线收到中间
//
// 性能：
//   - 漂移用 8fps 的时间戳推进（30–46s 周期、12–24dp 幅度 → 每帧位移 < 0.2dp，肉眼完全平滑）；
//   - 时间戳只在 DrawScope 里被读取 → 只触发「重绘」，不触发重组；
//   - reduce-motion / 光感关闭 / 预览模式 → 静态绘制（timeMs 恒为 0）。
// =============================================================================

/** 漂移刷新间隔（毫秒）：8fps。 */
private const val AURORA_TICK_MS = 125L

/**
 * 光场背板。
 *
 * @param field 光场规格（[com.furina.classtools.ui.theme.CtLightFields] 生成）
 * @param dark 深色模式（决定混合模式）
 * @param animated 是否漂移（false = 静态；预览/reduce-motion/关闭光感时为 false）
 */
@Composable
fun CtAuroraField(
    field: CtLightField,
    dark: Boolean,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
) {
    val reduceMotion = rememberReduceMotion()
    val shouldAnimate = animated && !reduceMotion
    var timeMs by remember(field) { mutableLongStateOf(0L) }
    val latestField by rememberUpdatedState(field)

    if (shouldAnimate) {
        LaunchedEffect(field, shouldAnimate) {
            val start = android.os.SystemClock.uptimeMillis()
            while (isActive) {
                delay(AURORA_TICK_MS)
                timeMs = android.os.SystemClock.uptimeMillis() - start
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                onDrawBehind {
                    // 时间戳在 draw 阶段读取 → 只重绘、不重组。
                    drawLightField(
                        field = latestField,
                        dark = dark,
                        virtual = size,
                        timeMs = timeMs,
                    )
                }
            },
    )
}

/** 画一团柔光斑（含漂移与呼吸）。 */
private fun DrawScope.drawBloom(bloom: CtLightBloom, timeMs: Long, dark: Boolean, virtual: Size) {
    if (bloom.alpha <= 0.001f) return
    val breath = if (timeMs == 0L) 1f else CtLightDrift.breath(bloom.phase, bloom.periodMs, timeMs)
    val ox = if (timeMs == 0L) 0f else CtLightDrift.offset(bloom.phase, bloom.periodMs, timeMs)
    val oy = if (timeMs == 0L) 0f else CtLightDrift.offset(bloom.phase + 0.27f, bloom.periodMs, timeMs)
    val center = Offset(
        x = virtual.width * bloom.x + ox * bloom.driftX.toPx() * 2f,
        y = virtual.height * bloom.y + oy * bloom.driftY.toPx() * 2f,
    )
    val radius = virtual.maxDimension * bloom.radius * breath
    if (radius <= 1f) return
    val peak = bloom.color.copy(alpha = bloom.alpha)
    val mid = bloom.color.copy(alpha = bloom.alpha * 0.48f)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to peak,
                0.45f to mid,
                1f to Color.Transparent,
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
        alpha = 1f,
        blendMode = if (dark) BlendMode.Screen else BlendMode.SrcOver,
    )
}

/**
 * 在任意 DrawScope 上按「虚拟画布尺寸」绘制光场。
 *
 * 用途：状态栏留白条这类只有 30dp 高的窄条，需要画出「整屏光场在该位置的那一段」——
 * 直接在这个窄条里调用 [CtAuroraField] 会被父约束压成 30dp 高，光斑几何完全不同（会显色差）。
 * 这里显式传入 [virtual] 尺寸，绘制时按整屏坐标计算，再由 Canvas 自身边界裁剪。
 *
 * @param virtual 虚拟画布尺寸（px；通常 = 整屏宽高）
 * @param timeMs 动画时间戳（0 = 静态）
 */
fun DrawScope.drawLightField(
    field: CtLightField,
    dark: Boolean,
    virtual: Size,
    timeMs: Long = 0L,
) {
    drawRect(color = field.base)
    drawRect(
        brush = Brush.verticalGradient(
            0f to field.topGlow.copy(alpha = if (dark) 0.22f else 0.55f),
            0.34f to Color.Transparent,
            endY = virtual.height,
        ),
    )
    field.blooms.forEach { drawBloom(it, timeMs, dark, virtual) }
    // 斜向流光
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                field.sheen.copy(alpha = field.sheenAlpha * 0.10f),
                Color.Transparent,
            ),
            start = Offset(virtual.width * -0.15f, 0f),
            end = Offset(virtual.width * 1.15f, virtual.height * 0.55f),
        ),
    )
    // 暗角
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, field.vignette.copy(alpha = field.vignetteAlpha)),
            center = Offset(virtual.width * 0.5f, virtual.height * 0.42f),
            radius = virtual.maxDimension * 0.78f,
        ),
    )
}

/**
 * 光场之上的「页面蒙层」（保证正文可读的轻纱）。
 *
 * 浅色：白 0.10–0.22 的纵向渐变（顶部更实，顶栏区域文字更清晰）；
 * 深色：黑 0.18–0.34（压住发光斑，避免深色下底部文字发灰）。
 *
 * 与 [CtAuroraField] 分开，是因为「光场」属于背景、「蒙层」属于页面可读性策略，
 * 两者在设置页预览里需要独立开关。
 */
@Composable
fun ctAuroraVeilColor(dark: Boolean): Color = if (dark) {
    MaterialTheme.colorScheme.background.copy(alpha = 0.30f)
} else {
    Color.White.copy(alpha = 0.16f)
}

/** 便捷：整屏光场 + 顶部/底部 scrim（页面级背景用）。 */
@Composable
fun CtAuroraBackdrop(
    modifier: Modifier = Modifier,
    field: CtLightField,
    dark: Boolean,
    animated: Boolean = true,
    scrimTopAlpha: Float = 0f,
    scrimBottomAlpha: Float = 0f,
) {
    Box(modifier = modifier.fillMaxSize()) {
        CtAuroraField(field = field, dark = dark, animated = animated)
        if (scrimTopAlpha > 0f || scrimBottomAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val brush = Brush.verticalGradient(
                            0f to field.base.copy(alpha = scrimTopAlpha),
                            0.22f to Color.Transparent,
                            0.78f to Color.Transparent,
                            1f to field.base.copy(alpha = scrimBottomAlpha),
                        )
                        onDrawBehind { drawRect(brush = brush) }
                    },
            )
        }
    }
}

/** 光场尺寸工具（预览/单测用：给定视口算光斑直径，便于断言不会超出屏幕太多）。 */
internal fun bloomDiameterPx(size: Size, bloom: CtLightBloom): Float =
    size.maxDimension * bloom.radius * 2f

/**
 * 是否已由 **App 根**绘制了全局光场。
 *
 * 为 true 时，页面级的 [com.furina.classtools.ui.components.background.CtBackgroundLayer]
 * **不再重复绘制光场**（只保留照片背景 / scrim），这样：
 *   1) 光场从屏幕最顶端一直到最底端连续，不存在「状态栏留白条 → 页面」的接缝；
 *   2) 切换 tab 时背景不闪、不重绘（只有一份光场实例，滚动时也只重绘一次）；
 *   3) 多页叠加时不会 N 份全屏渐变互相叠加（省 GPU）。
 */
val LocalCtRootAuroraActive = androidx.compose.runtime.staticCompositionLocalOf { false }
