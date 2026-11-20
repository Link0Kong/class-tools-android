package com.furina.classtools.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// =============================================================================
// 边缘渐隐（Edge Fade）—— 沉浸光感「内容让位给浮动控件」的核心手法
//
// 官方参考（沉浸光感设计规范配图）：
//   · 顶部悬浮区域：列表内容向上滑动时**在标题栏下方逐渐淡出**，而不是被标题栏硬盖住 ——
//     标题始终清晰可读，内容"融进"背景；
//   · 底部悬浮区域：内容靠近底部标签栏时同样逐渐淡出。
//
// 实现方式：真正的**逐像素 alpha 遮罩**（BlendMode.DstIn），而不是盖一层半透明色块：
//   1) 给内容加离屏合成层（CompositingStrategy.Offscreen）；
//   2) 在顶部/底部各画一条「透明 → 不透明」的竖直渐变，用 DstIn 与内容相乘；
//   3) 结果：靠近边缘的内容真的变透明，透出后面的光场/照片背景，而不是被糊上一层底色。
//
// 注意：本 Modifier 作用于**滚动容器整体**（遮罩固定在视口坐标），
// 因此滚动过程中内容会自然地"滑进遮罩"里渐隐，符合官方示例的观感。
// =============================================================================

/**
 * 内容在视口上下边缘渐隐。
 *
 * @param top 顶部渐隐带高度（建议 = 状态栏留白 + 悬浮条高度，例如 96dp）
 * @param bottom 底部渐隐带高度（建议 = 浮岛高度 + 导航栏安全区，例如 128dp）
 */
fun Modifier.ctFadeEdges(
    top: Dp = 0.dp,
    bottom: Dp = 0.dp,
): Modifier {
    if (top <= 0.dp && bottom <= 0.dp) return this
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val topPx = top.toPx().coerceIn(0f, size.height)
            val bottomPx = bottom.toPx().coerceIn(0f, size.height)
            if (topPx > 0.5f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black,
                        startY = 0f,
                        endY = topPx,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (bottomPx > 0.5f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black,
                        1f to Color.Transparent,
                        startY = size.height - bottomPx,
                        endY = size.height,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}

/**
 * 便捷：根页面（工作台 / 工具 / 我的）的滚动内容统一使用这套渐隐带。
 *
 * 顶部 96dp 覆盖「状态栏留白 + 悬浮玻璃条」，底部 128dp 覆盖「浮岛 + 导航栏」。
 */
@Composable
fun Modifier.ctRootPageEdgeFade(): Modifier = ctFadeEdges(top = 96.dp, bottom = 128.dp)

/** 滚动量 → 大标题折叠进度（0 = 全展开，1 = 完全收起）。纯函数，可单测。 */
fun ctHeaderCollapseProgress(scrollPx: Float, distancePx: Float = 320f): Float {
    if (distancePx <= 0f) return if (scrollPx > 0f) 1f else 0f
    return (scrollPx / distancePx).coerceIn(0f, 1f)
}
