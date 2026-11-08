package com.furina.classtools.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize

// =============================================================================
// 班级工具站 · 动效 token（Material 3 Expressive · official springs）
//
// 数值来源（已核实）：androidx.compose.material3.tokens.ExpressiveMotionTokens
//   spatial  : fast (d=0.6, k=800) / default (0.8, 380) / slow (0.8, 200)
//   effects  : fast (1.0, 3800) / default (1.0, 1600) / slow (1.0, 800)
//   (dampingRatio / stiffness，mass=1；effects 临界阻尼不 overshoot)
//
// 为什么不用 MaterialTheme.motionScheme？
//   material3 1.4.0 中 `MotionScheme` 接口与 `MaterialTheme.motionScheme` 都是 internal，
//   应用代码无法注入自定义 MotionScheme（1.5.0-alpha 才公开）。因此这里提供同值的
//   CtMotion + CtMotion.scheme（供组件统一读取），等升级 1.5.0 后再桥接即可。
// =============================================================================

object CtMotion {

    // ---- 空间（位置/尺寸/形状变化）-------------------------------------------

    /** Expressive default spatial：0.8 / 380 —— 页面/容器转场、列表增删、形状变形。 */
    val spatial: SpringSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Expressive fast spatial：0.6 / 800 —— 按压回弹、FAB 展开、指示器位移。 */
    val spatialFast: SpringSpec<Float> = spring(dampingRatio = 0.6f, stiffness = 800f)

    /** Expressive slow spatial：0.8 / 200 —— 底部弹层展开、大面积 hero 变化。 */
    val spatialSlow: SpringSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 200f)

    // ---- 效果（颜色/透明度）--------------------------------------------------

    /** Expressive default effects：1.0 / 1600 —— 常规颜色/alpha。 */
    val effects: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 1600f)

    /** Expressive fast effects：1.0 / 3800 —— 按下高亮/快速切换。 */
    val effectsFast: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 3800f)

    /** Expressive slow effects：1.0 / 800 —— 大底色渐变。 */
    val effectsSlow: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 800f)

    // ---- 兼容别名（旧代码在用，语义映射到新 token）---------------------------

    /** @deprecated 语义等价于 [spatialFast]；保留旧名避免破坏调用方。 */
    val fast: SpringSpec<Float> get() = spatialFast

    /** @deprecated 语义等价于 [spatial]（带轻微 overshoot 的强调动效）。 */
    val emphasized: SpringSpec<Float> get() = spatial

    // ---- 淡入淡出（非空间效果；tween 足够，且便于统一时长）-------------------

    /** 通用淡入：180ms + M3 emphasized 缓动。 */
    val fadeIn: FiniteAnimationSpec<Float> = tween(
        durationMillis = 180,
        easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    )

    /** 通用淡出：120ms。 */
    val fadeOut: FiniteAnimationSpec<Float> = tween(
        durationMillis = 120,
        easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    )

    /** 加载指示器旋转：线性无限循环用（配 infiniteRepeatable）。 */
    val linear: FiniteAnimationSpec<Float> = tween(durationMillis = 900, easing = LinearEasing)

    // ---- 非 Float 值的预建 spring（animateDpAsState/animateOffsetAsState 等）--

    /** Dp 版 spatial default（0.8 / 380）。 */
    val spatialDp: SpringSpec<Dp> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Dp 版 spatial fast（0.6 / 800）。 */
    val spatialFastDp: SpringSpec<Dp> = spring(dampingRatio = 0.6f, stiffness = 800f)

    /** Dp 版 effects default（1.0 / 1600）。 */
    val effectsDp: SpringSpec<Dp> = spring(dampingRatio = 1f, stiffness = 1600f)

    /** IntSize 版 spatial default（animateContentSize / animateIntSizeAsState 用）。 */
    val spatialSize: SpringSpec<IntSize> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** IntSize 版 spatial fast（快速内容尺寸变化）。 */
    val spatialFastSize: SpringSpec<IntSize> = spring(dampingRatio = 0.6f, stiffness = 800f)

    /** 页面转场时长（非 spring 场景，如 Crossfade/预览）。 */
    const val ENTER_MS = 320
    const val EXIT_MS = 220

    // ---- 泛型 spring（用于 Offset/Dp/Color 等非 Float 值）-------------------

    fun <T> spatialSpec(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> fastSpatialSpec(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> slowSpatialSpec(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)
    fun <T> effectsSpec(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)

    /** 全局统一的 MotionScheme 风格访问器（当前版本为自建对象；见文件头说明）。 */
    val scheme: CtMotionScheme = CtMotionScheme
}

/**
 * MotionScheme 风格 helper：组件可以只依赖这一个对象读取 6 个 spec，
 * 后续升级 material3 1.5.0 时只需把实现替换成 `MotionScheme.expressive()`。
 */
object CtMotionScheme {
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = CtMotion.spatialSpec()
    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = CtMotion.fastSpatialSpec()
    fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = CtMotion.slowSpatialSpec()
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = CtMotion.effectsSpec()
    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)
    fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)
}
