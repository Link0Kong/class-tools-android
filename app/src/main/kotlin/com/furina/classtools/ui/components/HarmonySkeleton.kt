package com.furina.classtools.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.HarmonyMotion
import com.furina.classtools.ui.theme.HarmonyTokens
import com.furina.classtools.ui.theme.rememberHarmonyColors
import com.furina.classtools.ui.theme.rememberReduceMotion

/**
 * 加载骨架（2026-11-19 对照鸿蒙 `HomeScreen.ets skeletonPage`）。
 *
 * 鸿蒙实现：骨架用三档灰块（#E6ECEE / #E9EEEF / #EDF1F2），并由一个 900ms 计时器在
 * `opacity 1 ↔ 0.55` 之间呼吸（源码 `this.shimmer ? 0.55 : 1`）。Compose 用
 * `rememberInfiniteTransition` 的 900ms 线性往返得到同一节奏；系统「移除动画」时固定为 1。
 *
 * 组成：[rememberSkeletonAlpha] 呼吸值 + [HarmonySkeletonBlock] 单块 +
 * [HarmonySkeletonTextBlock] 两行文本块 + [HarmonySkeletonRow] 图标行（列表/工具卡加载态）。
 */
@Composable
fun rememberSkeletonAlpha(): () -> Float {
    val reduceMotion = rememberReduceMotion()
    if (reduceMotion) return { 1f }
    val transition = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = HarmonyMotion.SkeletonDimAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = HarmonyMotion.SkeletonPulseMs, easing = LinearEasing),
        ),
        label = "skeletonAlpha",
    )
    return { alpha }
}

/** 单块骨架（宽度比例 + 高度 + 圆角；鸿蒙 12/10/4/6dp 圆角档）。 */
@Composable
fun HarmonySkeletonBlock(
    widthFraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    radius: Dp = 4.dp,
    strong: Boolean = false,
    soft: Boolean = false,
) {
    val colors = rememberHarmonyColors()
    val color = when {
        strong -> colors.skeletonStrong
        soft -> colors.skeletonSoft
        else -> colors.skeletonBase
    }
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction.coerceIn(0.02f, 1f))
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(color),
    )
}

/** 骨架的读屏说明（骨架本身没有文字，仍要让 TalkBack 播报「读取课表…」）。 */
private fun Modifier.skeletonSemantics(description: String?): Modifier =
    if (description.isNullOrBlank()) this else this.semantics { contentDescription = description }

/** 两行文本骨架（等效鸿蒙 skeletonPage 的「标题 40% + 说明 65%」块）。 */
@Composable
fun HarmonySkeletonTextBlock(
    modifier: Modifier = Modifier,
    description: String? = null,
    alphaProvider: () -> Float = rememberSkeletonAlpha(),
) {
    val alpha = alphaProvider()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .skeletonSemantics(description),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HarmonySkeletonBlock(widthFraction = 0.42f, height = 12.dp, radius = 4.dp, strong = true)
        HarmonySkeletonBlock(widthFraction = 0.66f, height = 10.dp, radius = 4.dp, soft = true)
    }
}

/** 图标行骨架（40dp 图标块 + 两行文本；鸿蒙 skeletonPage 的卡片行）。 */
@Composable
fun HarmonySkeletonRow(
    modifier: Modifier = Modifier,
    description: String? = null,
    alphaProvider: () -> Float = rememberSkeletonAlpha(),
) {
    val colors = rememberHarmonyColors()
    val alpha = alphaProvider()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .skeletonSemantics(description),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HarmonyTokens.TileTextGap),
    ) {
        Box(
            modifier = Modifier
                .size(HarmonyTokens.GlyphBox)
                .clip(RoundedCornerShape(HarmonyTokens.RadiusTile))
                .background(colors.skeletonStrong),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HarmonySkeletonBlock(widthFraction = 0.40f, height = 12.dp, strong = true)
            HarmonySkeletonBlock(widthFraction = 0.65f, height = 10.dp, soft = true)
        }
    }
}
