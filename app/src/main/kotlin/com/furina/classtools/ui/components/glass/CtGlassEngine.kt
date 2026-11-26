package com.furina.classtools.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import com.furina.classtools.ui.theme.CtDarkGlassRole
import com.furina.classtools.ui.theme.CtGlassSpec
import com.furina.classtools.ui.theme.CtGlassTable
import com.furina.classtools.ui.theme.CtLightIntensity
import com.furina.classtools.ui.theme.CtLightLevel
import com.furina.classtools.ui.theme.CtLightScene
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import com.furina.classtools.ui.theme.LocalCtLight
import com.furina.classtools.ui.theme.ctLightSceneOf
import com.furina.classtools.ui.theme.rememberCtColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

// =============================================================================
// 沉浸光感 · 材质引擎（Glass Engine）
//
// [Modifier.ctGlass] 绘制共享的玻璃材质，颜色、厚度和反光取自 [CtGlassTable]。
//
// 材质由 5 层叠加而成（对齐官方「材质滤镜 / 折射 / 高光 / 阴影」）：
//
//   ┌─ 外投影：柔和、下偏移、极低 alpha（浅色有、深色极淡）      → 层级（浮在内容之上）
//   ├─ 材质滤镜：Haze 采样背后内容做高斯模糊 + 颗粒               → 透光（可选，默认关）
//   ├─ 底部内阴影：向内压暗，塑造「玻璃有厚度」的实体感           → 厚度
//   ├─ 上亮下暗底色 + 极淡品牌赋色：白玻璃不会发灰、深玻璃不发脏  → 介质
//   ├─ 点光源高光：左上 / 手指位置的径向柔白                      → 高光（交互时跟随手指）
//   └─ 折射亮边：上沿 1.2dp 亮线 + 下沿 1dp 暗线（深色为亮线）    → 折射
//
// 关于「真模糊」：
//   Compose 没有 backdrop-filter。Haze 通过把 source 子树画进图层、再按各 child 的
//   区域做高斯采样来实现（API 31+ 用 RenderEffect）。它只能采样**同一窗口内、且不在
//   自身子树里**的内容 —— 所以：
//     · 卡片使用半透明底叠加窗口光场，滚动时无需采样背景。
//     · 同窗口内的悬浮层可传入 HazeState，在支持时采样滚动内容。
// =============================================================================

/**
 * 页面级 Haze 源（源 = 会从玻璃下面滚过的内容）。
 *
 * 由页面骨架（MainNavHost / CtLightPage）注入；为 null 时材质退化为「半透明 + 高光」，
 * 视觉仍然成立（只是背后的文字不会被糊开）。
 */
val LocalCtHazeState = staticCompositionLocalOf<HazeState?> { null }

/** 当前光感是否开启了真模糊能力（API 31+ 且用户未关闭光感）。 */
@Composable
fun rememberCtGlassBlurAvailable(): Boolean = LocalCtLight.current.blurAvailable

/**
 * 当前场景的材质规格（页面/组件需要读数值时用；直接绘制请用 [Modifier.ctGlass]）。
 */
@Composable
fun rememberCtGlassSpec(scene: CtLightScene, bodySafe: Boolean = true): CtGlassSpec {
    val light = LocalCtLight.current
    val dark = LocalCtDarkTheme.current
    return CtGlassTable.specOf(ctLightSceneOf(scene), light.setting, dark, bodySafe)
}

/** 显式档位的材质规格。 */
@Composable
fun rememberCtGlassSpec(level: CtLightLevel, bodySafe: Boolean = true): CtGlassSpec {
    val light = LocalCtLight.current
    val dark = LocalCtDarkTheme.current
    return CtGlassTable.specOf(level, light.setting, dark, bodySafe)
}

/**
 * **沉浸光感材质**（唯一对外绘制入口）。
 *
 * @param level 材质档（场景语义请先用 [ctLightSceneOf] 或直接传场景版重载）
 * @param shape 形状（材质、描边、投影都跟随它）
 * @param bodySafe 承载正文 → 底色不透明度抬到 AA 安全线
 * @param haze 传入页面级 HazeState 才有「真模糊」；null = 仅半透明（卡片默认）
 * @param pressState 按压点光源状态（[rememberCtGlassPress]）；null = 静态左上高光
 * @param intensityOverride 局部强度覆盖（极少用；播放器/媒体条等特殊场景）
 * @param glow 光晕强度（0..1；选中态/FAB 可以额外提亮）
 * @param darkRole 深色玻璃化开启时的材质用途
 */
@Composable
fun Modifier.ctGlass(
    level: CtLightLevel,
    shape: Shape,
    bodySafe: Boolean = true,
    haze: HazeState? = null,
    pressState: CtGlassPressState? = null,
    intensityOverride: CtLightIntensity? = null,
    glow: Float = 0f,
    fillBoost: Float = 0f,
    darkRole: CtDarkGlassRole? = null,
): Modifier {
    val light = LocalCtLight.current
    val dark = LocalCtDarkTheme.current
    val c = rememberCtColors()
    val setting = if (intensityOverride == null) {
        light.setting
    } else {
        light.setting.copy(intensity = intensityOverride, enabled = light.setting.enabled)
    }
    val spec = CtGlassTable.specOf(level, setting, dark, bodySafe, darkRole, fillBoost)
    val pointer = pressState?.position
    val pressed = pressState?.pressed == true

    // 1) 外层投影（浅色=墨色柔影；深色=极淡黑影，主要靠亮边分层）
    var chain = this
    if (spec.shadowAlpha > 0.001f && !dark) {
        chain = chain.shadow(
            elevation = (spec.shadowBlur.value * 0.35f).coerceIn(2f, 16f).dp,
            shape = shape,
            clip = false,
            ambientColor = c.cardShadow.copy(alpha = spec.shadowAlpha),
            spotColor = c.cardShadow.copy(alpha = spec.shadowAlpha),
        )
    } else if (dark && spec.shadowAlpha > 0.001f) {
        chain = chain.shadow(
            elevation = 8.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = spec.shadowAlpha * 2.4f),
            spotColor = Color.Black.copy(alpha = spec.shadowAlpha * 2.4f),
        )
    }

    // 2) 形状裁剪（材质、模糊、描边全部在圆角内）
    chain = chain.clip(shape)

    // 深色材质在无模糊能力时直接用同一层半透明底色。
    val canBlur = darkRole == null || !dark || light.blurAvailable
    if (haze != null && canBlur && spec.blurRadius > 0.dp) {
        val hazeStyle = remember(spec, dark) {
            HazeStyle(
                backgroundColor = Color.Transparent,
                tint = HazeTint(
                    color = if (dark) Color(0x0DFFFFFF) else Color(0x14FFFFFF),
                ),
                blurRadius = spec.blurRadius,
                noiseFactor = spec.noiseFactor,
            )
        }
        chain = chain.hazeEffect(state = haze, style = hazeStyle) {
            this.blurEnabled = true
        }
    }

    // 4) 介质底色（上亮下暗）+ 极淡品牌赋色
    val fillBrush = remember(spec) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to spec.fillTop,
                0.55f to lerpGlassColor(spec.fillTop, spec.fillBottom, 0.55f),
                1f to spec.fillBottom,
            ),
        )
    }
    val tintColor = spec.tint.copy(alpha = spec.tintAlpha)
    chain = chain.background(fillBrush)

    // 5) 高光 / 折射 / 内阴影（一次绘制完成，滚动时不重建）
    return chain.drawWithCache {
        val tint = if (spec.tintAlpha > 0.001f) tintColor else Color.Transparent
        onDrawBehind {
            drawGlassOverlay(
                spec = spec,
                dark = dark,
                tint = tint,
                pointer = pointer,
                pressed = pressed,
                glow = glow,
            )
        }
    }
}

/** 场景版材质（页面里最常用：传 [CtLightScene] 即可，档位由场景决定）。 */
@Composable
fun Modifier.ctGlass(
    scene: CtLightScene,
    shape: Shape,
    bodySafe: Boolean = true,
    haze: HazeState? = null,
    pressState: CtGlassPressState? = null,
    glow: Float = 0f,
): Modifier = ctGlass(
    level = ctLightSceneOf(scene),
    shape = shape,
    bodySafe = bodySafe,
    haze = haze,
    pressState = pressState,
    glow = glow,
)

/** 材质内部绘制：内阴影 + 点光源高光 + 折射亮边 + 可选光晕。 */
private fun DrawScope.drawGlassOverlay(
    spec: CtGlassSpec,
    dark: Boolean,
    tint: Color,
    pointer: Offset?,
    pressed: Boolean,
    glow: Float,
) {
    val hasEdgeHighlight = spec.edgeTopAlpha > 0f || spec.edgeBottomAlpha > 0f

    // 1) 品牌赋色（整块极淡）
    if (tint.alpha > 0.001f) drawRect(color = tint)

    // 2) 底部内阴影：从上到下 0 → innerShadeAlpha，塑造「下缘有厚度」
    if (spec.innerShadeAlpha > 0.001f) {
        val shade = if (dark) Color.Black else Color(0xFF6E7F7C)
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.62f to Color.Transparent,
                1f to shade.copy(alpha = spec.innerShadeAlpha),
            ),
        )
    }

    // 3) 点光源高光：按下时跟随手指，否则固定在左上（光从左上打来）
    if (spec.specularAlpha > 0.001f) {
        val anchor = pointer ?: Offset(size.width * 0.26f, -size.height * 0.18f)
        val radius = size.minDimension * spec.specularSize * (if (pressed) 1.25f else 1f)
        val boost = if (pressed) 1.35f else 1f
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.White.copy(alpha = spec.specularAlpha * boost),
                    0.55f to Color.White.copy(alpha = spec.specularAlpha * 0.32f * boost),
                    1f to Color.Transparent,
                ),
                center = anchor,
                radius = radius.coerceAtLeast(1f),
            ),
            radius = radius.coerceAtLeast(1f),
            center = anchor,
        )
        if (hasEdgeHighlight) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = spec.specularAlpha * 0.45f),
                    0.18f to Color.Transparent,
                ),
            )
        }
    }

    if (hasEdgeHighlight) drawGlassEdgeHighlights(spec, dark)

    // 5) 光晕（选中/FAB）：在材质内侧描一圈品牌色柔光
    if (glow > 0.001f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.10f * glow),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.5f, size.height * 0.5f),
                radius = size.maxDimension * 0.8f,
            ),
            radius = size.maxDimension * 0.8f,
            center = Offset(size.width * 0.5f, size.height * 0.5f),
        )
    }
    if (hasEdgeHighlight) {
        drawRoundRect(
            color = Color.White.copy(alpha = if (dark) 0.06f else 0.20f),
            style = Stroke(width = 1f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.12f),
        )
    }
}

private fun DrawScope.drawGlassEdgeHighlights(spec: CtGlassSpec, dark: Boolean) {
    val edgeTop = Color.White.copy(alpha = spec.edgeTopAlpha)
    val edgeBottom = if (dark) {
        Color.White.copy(alpha = spec.edgeBottomAlpha)
    } else {
        Color(0xFF4E5C59).copy(alpha = spec.edgeBottomAlpha)
    }
    val hair = 1.2.dp.toPx()
    drawRect(color = edgeTop, topLeft = Offset(0f, 0f), size = Size(size.width, hair))
    drawRect(
        color = edgeBottom,
        topLeft = Offset(0f, size.height - hair),
        size = Size(size.width, hair),
    )
    val sideAlpha = spec.edgeTopAlpha * 0.35f
    if (sideAlpha > 0.02f) {
        drawRect(
            color = Color.White.copy(alpha = sideAlpha),
            topLeft = Offset(0f, 0f),
            size = Size(hair, size.height),
        )
        drawRect(
            color = Color.White.copy(alpha = sideAlpha * 0.6f),
            topLeft = Offset(size.width - hair, 0f),
            size = Size(hair, size.height),
        )
    }
}

/** 颜色线性混合（不引入 lerp 的重载歧义）。 */
private fun lerpGlassColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

// -----------------------------------------------------------------------------
// 按压点光源（交互形变的输入）
// -----------------------------------------------------------------------------

/**
 * 按压点光源状态：记录手指位置（px，相对组件左上角）与是否按下。
 *
 * 用 [Modifier.ctGlassPointerLight] 挂到可点容器上；不消费手势，因此与
 * `clickable` / `Surface(onClick)` 共存。
 */
class CtGlassPressState {
    /** 手指位置（null = 未按下）。 */
    var position: Offset? by mutableStateOf(null)

    /** 是否处于按下态（用于材质提亮与 scale）。 */
    var pressed: Boolean by mutableStateOf(false)

    internal fun update(offset: Offset?, down: Boolean) {
        position = offset
        pressed = down
    }
}

/** 记住一个按压点光源状态。 */
@Composable
fun rememberCtGlassPress(): CtGlassPressState = remember { CtGlassPressState() }

/**
 * 观察按下位置（**不消费事件**，与现有点击手势共存）。
 *
 * 只在材质需要「点光源跟随手指」时挂载；纯静态玻璃不需要。
 */
fun Modifier.ctGlassPointerLight(state: CtGlassPressState): Modifier = this.pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        state.update(down.position, true)
        val up = waitForUpOrCancellation()
        state.update(up?.position, false)
    }
}
