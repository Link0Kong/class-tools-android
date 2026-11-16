package com.furina.classtools.ui.navigation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.ui.theme.rememberReduceMotion

/**
 * 工具卡 → 工具页的「一镜到底」容器转场（参考 HFUT-Schedule 的转场连续性原则，自行实现）。
 *
 * 思路（不引入 Navigation shared-element 实验 API，稳定可控）：
 * - 卡片 `onGloballyPositioned` 把工具 id → 根坐标 bounds 登记到 [ToolTransitionAnchors]；
 * - 点卡片时，在 NavHost 上覆盖一层 [ToolTransitionOverlay]：从卡片 bounds 长成全屏圆角容器，
 *   同时背后的 NavHost 照常 push；覆盖层在最后 30% 淡出，形成「卡片展开成页面」的连续感；
 * - 目标页仍使用既有 spring push/pop 转场；系统「移除动画」时直接跳转、无覆盖层。
 */
class ToolTransitionAnchors {
    private val anchors = mutableStateMapOf<String, Rect>()

    fun put(id: String, bounds: Rect) {
        if (id.isBlank()) return
        if (bounds.width <= 0f || bounds.height <= 0f) return
        anchors[id] = bounds
    }

    fun get(id: String): Rect? = anchors[id]
}

val LocalToolTransitionAnchors = staticCompositionLocalOf<ToolTransitionAnchors?> { null }

/** 工具卡上登记转场起点（preview / 无覆盖层时是 no-op）。 */
@Composable
fun Modifier.ctToolTransitionSource(toolId: String): Modifier {
    val anchors = LocalToolTransitionAnchors.current ?: return this
    return this.onGloballyPositioned { coordinates ->
        anchors.put(toolId, coordinates.boundsInRoot())
    }
}

private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

/**
 * 捕获当前工具页的真实界面快照（主线程同步 `View.draw(Canvas)`；调用时必须仍未开始转场，保证拍到工具页）。
 *
 * - 返回 null（尺寸异常 / draw 抛异常 / OOM）时调用方降级为原图标卡覆盖层，不影响返回；
 * - 仅用于「页面收回卡片」的退出动画（进入动画仍用图标卡，目标页很快会由 NavHost 渲染）；
 * - WebView/SurfaceView 等硬件层可能不在软件 Canvas 中完整呈现，最坏情况是快照局部空白，
 *   但仍优于旧实现「只画图标卡」。
 */
internal fun captureToolSnapshot(view: View?): Bitmap? {
    val root = view ?: return null
    if (root.width <= 0 || root.height <= 0) return null
    return runCatching {
        Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { bitmap ->
            root.draw(Canvas(bitmap))
        }
    }.getOrNull()
}

/**
 * 覆盖层：进入 = 卡片 bounds → 全屏圆角容器；退出 = **全屏真实快照** → 卡片 bounds。
 *
 * 进入时覆盖层从卡片长成全屏、最后 30% 淡出露出目标页；退出时先由调用方 pop 回根页，
 * 再让 [snapshot]（工具页真实界面）从全屏缩回卡片并淡出——动画期间/结束后都不会露出工具内部界面。
 * [snapshot] 为 null（拍摄失败/进入方向）时退回旧图标卡视觉。
 * 若系统开启移除动画，则本 composable 不绘制任何覆盖层（调用方直接跳转）。
 */
@Composable
fun ToolTransitionOverlay(
    tool: AppTool?,
    startBounds: Rect?,
    onHalfway: () -> Unit,
    onDone: () -> Unit,
    reverse: Boolean = false,
    snapshot: Bitmap? = null,
) {
    if (tool == null || startBounds == null) return
    val reduceMotion = rememberReduceMotion()
    if (reduceMotion) {
        LaunchedEffect(tool, reverse) {
            onHalfway()
            onDone()
        }
        return
    }
    val progress = remember(tool, reverse) { Animatable(0f) }
    LaunchedEffect(tool, reverse) {
        try {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = if (reverse) 280 else 340,
                    easing = FastOutSlowInEasing,
                ),
            )
        } finally {
            onDone()
        }
    }
    LaunchedEffect(tool, reverse) {
        // 进入：覆盖层先「铺开」一点再导航；退出：调用方已在开始动画前 pop（避免底层露出工具界面），
        // 这里不再调用 onHalfway。
        if (!reverse) {
            kotlinx.coroutines.delay(120)
            onHalfway()
        }
    }

    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 退出兜底：先铺一层不透明根页底色，再画缩小的快照。
        // 这样即使 NavHost 的 pop 在极端时序下晚一帧，用户也绝不会看到「工具页内部」，
        // 只会看到卡片在干净背景上收回；root 出现后再撤层。
        if (reverse) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {}
        }
        val fullWidthPx = constraints.maxWidth.toFloat()
        val fullHeightPx = constraints.maxHeight.toFloat()
        val progressValue = progress.value
        // 进入：0→1（卡片长成全屏）；退出：1→0（全屏收回卡片）。
        val p = if (reverse) 1f - progressValue else progressValue
        val left = lerp(startBounds.left, 0f, p)
        val top = lerp(startBounds.top, 0f, p)
        val width = lerp(startBounds.width, fullWidthPx, p)
        val height = lerp(startBounds.height, fullHeightPx, p)
        val corner = (20f * (1f - p)).coerceAtLeast(0f)
        val fade = if (reverse) {
            // 退出：全程不透明；根页 pop 转场已关闭，收回结束才撤层，避免露出工具页。
            1f
        } else {
            // 进入：最后 30% 淡出，露出已经完成 push 的目标页。
            if (p <= 0.7f) 1f else (1f - (p - 0.7f) / 0.3f).coerceIn(0f, 1f)
        }
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(left.toInt().coerceAtLeast(0), top.toInt().coerceAtLeast(0))
                }
                .size(
                    width = with(density) { width.toDp().coerceAtLeast(0.dp) },
                    height = with(density) { height.toDp().coerceAtLeast(0.dp) },
                )
                .graphicsLayer { alpha = fade },
        ) {
            if (reverse && snapshot != null) {
                // 退出：真实工具页快照从全屏缩回卡片（圆角随进度收拢），最后 18% 淡出与底层卡片交接。
                Image(
                    bitmap = snapshot.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(corner.dp)),
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(corner.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 2.dp,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = tool.accent.copy(alpha = 0.18f),
                            ) {
                                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = tool.icon,
                                        contentDescription = null,
                                        tint = tool.accent,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                            }
                            androidx.compose.foundation.layout.Spacer(
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = tool.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
