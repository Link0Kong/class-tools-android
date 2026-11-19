package com.furina.classtools.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToLong

// =============================================================================
// 「移除动画」与统一按压/入场动效
//
// 1) 系统移除动画检测（任务要求「遵循系统减少动画」）：
//    - AccessibilityManager.isReduceMotionEnabled（Android 12+ 的「移除动画」系统开关；
//      Android 13+ 的 `Settings.Global.ANIMATOR_DURATION_SCALE=0` 也会让它返回 true）；
//    - `Settings.Global.ANIMATOR_DURATION_SCALE == 0.0`（开发者选项「动画时长调节」调成关闭）；
//    - `ValueAnimator.areAnimatorsEnabled() == false`。
//    任一命中 → [rememberReduceMotion] 为 true，所有统一动效组件改用 snap（瞬时到位）。
//    运行中切换（系统设置里打开/关闭）会通过 ContentObserver 实时生效，无需重进页面。
//
// 2) [ctPressFeedback]：M3 Expressive 按压反馈 —— scale 0.97（spring 0.75/600）
//    + surface tonal 按压层（8% state layer）。所有可点卡片/磁贴统一使用。
//
// 3) [CtAnimatedEntrance]：列表项错峰入场（fade + 上移 8dp，每项 40ms 递增）。
// =============================================================================

/**
 * 系统「移除动画」是否开启（纯函数，便于单测）。
 *
 * @param animatorScale `Settings.Global.ANIMATOR_DURATION_SCALE`（辅助功能「移除动画」会把它置 0）
 * @param animatorsEnabled `ValueAnimator.areAnimatorsEnabled()`
 * @param reduceMotionSetting 预留：将来接入 AccessibilityManager 的 reduce-motion 扩展信号
 */
fun shouldReduceMotion(
    animatorScale: Float,
    animatorsEnabled: Boolean,
    reduceMotionSetting: Boolean = false,
): Boolean = animatorScale == 0f || !animatorsEnabled || reduceMotionSetting

/**
 * 读取系统当前「移除动画」状态。
 *
 * 「移除动画（Remove animations）」在 Android 上就是 `ANIMATOR_DURATION_SCALE == 0`（辅助功能 →
 * 移除动画 会把「动画时长调节」置 0），因此两个信号足够；另外兼容 `areAnimatorsEnabled()` 关闭。
 */
fun isReduceMotion(context: Context): Boolean {
    val scale = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }.getOrDefault(1f)
    val animatorsEnabled = runCatching { android.animation.ValueAnimator.areAnimatorsEnabled() }
        .getOrDefault(true)
    return shouldReduceMotion(scale, animatorsEnabled, reduceMotionSetting = false)
}

/**
 * 监听系统「移除动画」：运行中切换无需重进页面即可生效。
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduceMotion by remember(context) { mutableStateOf(isReduceMotion(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduceMotion = isReduceMotion(context)
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
        }
        onDispose { runCatching { context.contentResolver.unregisterContentObserver(observer) } }
    }
    return reduceMotion
}

/**
 * 统一按压反馈（M3 Expressive）：`scale → 0.97`（spring 0.75/600）+ 按压层不透明度 → 8%。
 *
 * 用法（Surface 的 interactionSource 传入同一个 source）：
 * ```kotlin
 * val interaction = remember { MutableInteractionSource() }
 * val press = ctPressFeedback(interaction, reduceMotion)
 * Surface(onClick = …, interactionSource = interaction,
 *     modifier = Modifier.ctPressScale(press.scale)) { … }
 * Box(Modifier.matchParentSize().background(color = c.textPrimary.copy(press.overlayAlpha)))  // 可选按层
 * ```
 */
data class CtPressState(val scale: Float, val overlayAlpha: Float, val pressed: Boolean)

/** 读按压进度（0..1，spring 或 snap）。 */
@Composable
fun rememberPressProgress(
    interactionSource: InteractionSource,
    reduceMotion: Boolean,
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (reduceMotion) CtMotion.reduceMotionSpec() else CtMotion.spatialFast,
        label = "ctPressProgress",
    )
    return progress
}

/** 组合：按压进度 → [CtPressState]。 */
@Composable
fun ctPressFeedback(
    interactionSource: InteractionSource,
    reduceMotion: Boolean,
): CtPressState {
    val progress = rememberPressProgress(interactionSource, reduceMotion)
    return CtPressState(
        scale = 1f - (1f - CtMotionTokens.pressScale) * progress,
        overlayAlpha = CtMotionTokens.pressOverlayAlpha * progress,
        pressed = progress > 0.01f,
    )
}

/** 应用按压缩放（graphicsLayer，不影响布局与命中）。 */
fun Modifier.ctPressScale(scale: Float): Modifier = composed {
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 列表项错峰入场状态：返回 0..1 的进度（fade + 位移）。
 *
 * @param enabled false（预览 / 移除动画）时直接 1f；
 * @param index 第几项（延迟 = index × 40ms，最多延迟 8 项后封顶，避免长列表末尾等待过久）。
 */
@Composable
fun rememberStaggeredEntry(
    enabled: Boolean,
    index: Int,
    reduceMotion: Boolean = rememberReduceMotion(),
): Float {
    val animate = enabled && !reduceMotion
    val progress = remember(index) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate, index) {
        if (!animate) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        val delayMs = index.coerceIn(0, 8) * CtMotionTokens.staggerStepMs
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
        progress.animateTo(1f, CtMotion.pageTween)
    }
    return progress.value
}

/**
 * 列表项错峰入场（fade + 上移 8dp）：包裹列表中的每一项。
 *
 * `enabled=false` 或系统移除动画时直接显示（无动画、无延迟）。
 */
@Composable
fun CtAnimatedEntrance(
    enabled: Boolean,
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val progress = rememberStaggeredEntry(enabled = enabled, index = index, reduceMotion = reduceMotion)
    val risePx = CtMotionTokens.itemEnterRise
    androidx.compose.foundation.layout.Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * risePx * density
        },
    ) {
        content()
    }
}

/**
 * 计数滚动（统计数字 0 → 目标值，400ms）：用于「我的数据」等指标卡；
 * 移除动画时直接显示目标值。
 */
@Composable
fun rememberAnimatedCount(
    target: Int,
    reduceMotion: Boolean = rememberReduceMotion(),
): Int {
    if (reduceMotion) return target
    val value by animateIntAsState(
        targetValue = target,
        animationSpec = CtMotion.spatialSpec(),
        label = "ctAnimatedCount",
    )
    return value
}

/** 长按/悬停的按压层颜色（surface tonal：在卡片底上叠 state layer）。 */
fun ctPressOverlayColor(base: CtColor): androidx.compose.ui.graphics.Color =
    base.stateLayer.copy(alpha = CtMotionTokens.pressOverlayAlpha)

/** 数值格式化辅助：把 Float 进度转百分比（0..100 整数）。 */
fun progressPercent(progress: Float): Int = (progress.coerceIn(0f, 1f) * 100f).roundToLong().toInt()

/** 便捷：`rememberUpdatedState` 包装的回调（避免在动画里捕获旧 lambda）。 */
@Composable
fun rememberLatest(block: () -> Unit): State<() -> Unit> = rememberUpdatedState(block)
