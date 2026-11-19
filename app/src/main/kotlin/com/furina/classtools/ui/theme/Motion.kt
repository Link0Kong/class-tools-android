package com.furina.classtools.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.furina.classtools.ui.theme.CtMotionTokens as Tokens

// =============================================================================
// 动效系统 · 弹簧与时长（M3 Expressive motion physics）
//
// 数值全部来自 [CtDesign.kt] 的 [com.furina.classtools.ui.theme.CtMotion]（唯一令牌源）。
//
// 三组弹簧：
//   spatial（位置/尺寸/形状）  0.8/380 默认、0.75/600 按压、0.9/200 页面；
//   effects（颜色/透明度）     临界阻尼 1.0/1600（不过冲）；
//   enter/exit                 tween(120/200/300/400) + fastOutSlowIn（系统「移除动画」时为 snap）。
//
// 「移除动画」：调用方通过 [rememberReduceMotion] 拿到布尔值，然后:
//   animateFloatAsState(target, if (reduce) CtMotion.reduceMotionSpec() else CtMotion.spatialDp)
// -----------------------------------------------------------------------------

object CtMotion {

    // ---- 空间弹簧（位置/尺寸/形状） ------------------------------------------

    /** 默认空间弹簧：0.8 / 380（页面内元素位移、列表增减、形状变形）。 */
    val spatial: SpringSpec<Float> = spring(
        dampingRatio = Tokens.springSpatialDamping,
        stiffness = Tokens.springSpatialStiffness,
    )

    /** 快速空间弹簧：0.75 / 600（按压回弹、指示器吸附、FAB 展开）。 */
    val spatialFast: SpringSpec<Float> = spring(
        dampingRatio = Tokens.springFastDamping,
        stiffness = Tokens.springFastStiffness,
    )

    /** 慢速空间弹簧：0.9 / 200（大面积 hero、弹层展开、页面容器变换）。 */
    val spatialSlow: SpringSpec<Float> = spring(
        dampingRatio = Tokens.springSlowDamping,
        stiffness = Tokens.springSlowStiffness,
    )

    // ---- 效果弹簧（颜色/透明度，临界阻尼不过冲） -----------------------------

    val effects: SpringSpec<Float> = spring(
        dampingRatio = 1f,
        stiffness = Tokens.springEffectsStiffness,
    )

    val effectsFast: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 3800f)

    val effectsSlow: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 800f)

    // ---- 时长 / 缓动 ---------------------------------------------------------

    /** 标准缓动 fastOutSlowIn cubic-bezier(.4,0,.2,1)（M3 Emphasized）。 */
    val ease: CubicBezierEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** 减速缓动（新内容出现）。 */
    val easeDecelerate: CubicBezierEasing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    /** 加速缓动（内容离开）。 */
    val easeAccelerate: CubicBezierEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /** 淡入 200ms。 */
    val fadeIn: FiniteAnimationSpec<Float> = tween(durationMillis = Tokens.shortMs, easing = easeDecelerate)

    /** 淡出 120ms（离开比进入快，让操作「即时」）。 */
    val fadeOut: FiniteAnimationSpec<Float> = tween(durationMillis = Tokens.fastMs, easing = easeAccelerate)

    /** 常规过渡 300ms（弹层/展开）。 */
    val standard: FiniteAnimationSpec<Float> = tween(durationMillis = Tokens.standardMs, easing = ease)

    /** 线性循环（加载指示器）。 */
    val linear: FiniteAnimationSpec<Float> = tween(durationMillis = 900, easing = LinearEasing)

    // ---- 非 Float 的预建 spec ------------------------------------------------

    val spatialDp: SpringSpec<Dp> = spring(
        dampingRatio = Tokens.springSpatialDamping,
        stiffness = Tokens.springSpatialStiffness,
    )
    val spatialFastDp: SpringSpec<Dp> = spring(
        dampingRatio = Tokens.springFastDamping,
        stiffness = Tokens.springFastStiffness,
    )
    val effectsDp: SpringSpec<Dp> = spring(dampingRatio = 1f, stiffness = Tokens.springEffectsStiffness)
    val spatialSize: SpringSpec<IntSize> = spring(
        dampingRatio = Tokens.springSpatialDamping,
        stiffness = Tokens.springSpatialStiffness,
    )
    val spatialFastSize: SpringSpec<IntSize> = spring(
        dampingRatio = Tokens.springFastDamping,
        stiffness = Tokens.springFastStiffness,
    )
    val spatialOffset: SpringSpec<IntOffset> = spring(
        dampingRatio = Tokens.springSpatialDamping,
        stiffness = Tokens.springSpatialStiffness,
    )

    /** 页面转场 tween（容器变换兜底/淡入淡出）。 */
    val pageTween: FiniteAnimationSpec<Float> = tween(
        durationMillis = Tokens.standardMs,
        easing = ease,
    )
    val pageTweenInt: FiniteAnimationSpec<IntOffset> = tween(
        durationMillis = Tokens.standardMs,
        easing = ease,
    )

    // ---- 泛型 spring ---------------------------------------------------------

    fun <T> spatialSpec(): SpringSpec<T> = spring(
        dampingRatio = Tokens.springSpatialDamping,
        stiffness = Tokens.springSpatialStiffness,
    )

    fun <T> fastSpatialSpec(): SpringSpec<T> = spring(
        dampingRatio = Tokens.springFastDamping,
        stiffness = Tokens.springFastStiffness,
    )

    fun <T> slowSpatialSpec(): SpringSpec<T> = spring(
        dampingRatio = Tokens.springSlowDamping,
        stiffness = Tokens.springSlowStiffness,
    )

    fun <T> effectsSpec(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = Tokens.springEffectsStiffness)

    /** 「移除动画」降级：所有 animatable 直接跳到终态。 */
    fun <T> reduceMotionSpec(): FiniteAnimationSpec<T> = snap()

    /** 列表错峰入场：第 [index] 项的动画（位移/透明度共用，[stepMs] 为每项递增延迟）。 */
    fun staggerDelayMs(index: Int): Long = (index.coerceAtLeast(0) * Tokens.staggerStepMs)

    /** 旧名保留：`CtMotion.fast` 等价 [spatialFast]。 */
    val fast: SpringSpec<Float> get() = spatialFast

    /** 旧名保留：`CtMotion.emphasized` 等价 [spatial]。 */
    val emphasized: SpringSpec<Float> get() = spatial

    /** 旧名保留：`CtMotion.scheme`（组件只依赖这一个对象读取 6 个 spec）。 */
    val scheme: CtMotionScheme = CtMotionScheme

    /** 旧常量：页面进入/退出时长（毫秒）。 */
    const val ENTER_MS = Tokens.standardMs
    const val EXIT_MS = Tokens.shortMs
}

/** 旧入口：MotionScheme 风格访问器。 */
object CtMotionScheme {
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = CtMotion.spatialSpec()
    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = CtMotion.fastSpatialSpec()
    fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = CtMotion.slowSpatialSpec()
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = CtMotion.effectsSpec()
    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)
    fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)
}
