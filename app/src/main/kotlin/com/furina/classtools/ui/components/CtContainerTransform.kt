package com.furina.classtools.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 「我的 → 选项」点开时的一镜到底 container transform。
 *
 * 参考金标联盟 SharedViewDemo 的卡片展开语义（卡片 → 页面），但本项目是单 Activity
 * Compose，SharedViewKit 的公开 API 只接受 ActivityOptions/startActivity，无法直接套在
 * NavHost 内部路由上；这里用 Compose 实现同一套视觉：从被点行 bounds 扩展为整屏，
 * 圆角 12dp → 0，导航在遮罩下发生，再淡出露出目标页。
 *
 * 无 ROM 能力要求，所有设备可用；偏好降低动效时由调用方直接走普通导航。
 */
data class CtContainerTransformRequest(
    val bounds: Rect,
    val color: Color,
    val onNavigate: () -> Unit,
)

class CtContainerTransformState {
    internal var request by mutableStateOf<CtContainerTransformRequest?>(null)
        private set

    internal fun clear() {
        request = null
    }

    fun open(bounds: Rect, color: Color, onNavigate: () -> Unit) {
        if (request != null) return
        if (bounds.width <= 0f || bounds.height <= 0f) {
            onNavigate()
            return
        }
        request = CtContainerTransformRequest(bounds, color, onNavigate)
    }
}

val LocalCtContainerTransform = compositionLocalOf<CtContainerTransformState?> { null }

@Composable
fun CtContainerTransformHost(content: @Composable () -> Unit) {
    val state = remember { CtContainerTransformState() }
    CompositionLocalProvider(LocalCtContainerTransform provides state) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            CtContainerTransformOverlay(state)
        }
    }
}

@Composable
private fun CtContainerTransformOverlay(state: CtContainerTransformState) {
    val request = state.request ?: return
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val startLeft = with(density) { request.bounds.left.toDp() }
    val startTop = with(density) { request.bounds.top.toDp() }
    val startWidth = with(density) { request.bounds.width.toDp() }
    val startHeight = with(density) { request.bounds.height.toDp() }
    val progress = remember(request) { Animatable(0f) }
    val alpha = remember(request) { Animatable(1f) }

    LaunchedEffect(request) {
        // 先导航，让目标页在遮罩下方完成组合；下一帧后再展开，避免白屏。
        request.onNavigate()
        withFrameNanosCompat()
        progress.animateTo(1f, tween(durationMillis = 300, easing = FastOutSlowInEasing))
        delay(40)
        alpha.animateTo(0f, tween(durationMillis = 140, easing = FastOutSlowInEasing))
        state.clear()
    }

    val p = progress.value
    val left = lerpDp(startLeft, 0.dp, p)
    val top = lerpDp(startTop, 0.dp, p)
    val width = lerpDp(startWidth, screenWidth, p)
    val height = lerpDp(startHeight, screenHeight, p)
    val radius = lerpDp(12.dp, 0.dp, p)

    Box(
        modifier = Modifier
            .offset { IntOffset(left.roundToPx(), top.roundToPx()) }
            .size(width, height)
            .graphicsLayer { this.alpha = alpha.value }
            .clip(RoundedCornerShape(radius))
            .background(request.color),
    )
}

private fun lerpDp(start: androidx.compose.ui.unit.Dp, end: androidx.compose.ui.unit.Dp, fraction: Float) =
    start + (end - start) * fraction.coerceIn(0f, 1f)

private suspend fun withFrameNanosCompat() {
    androidx.compose.runtime.withFrameNanos { }
}





