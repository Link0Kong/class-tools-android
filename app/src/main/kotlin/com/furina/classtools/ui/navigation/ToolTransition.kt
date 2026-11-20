package com.furina.classtools.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtElevation
import com.furina.classtools.ui.theme.CtMotionTokens
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.CtSize
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.rememberCtColors
import com.furina.classtools.ui.theme.rememberReduceMotion
import com.furina.classtools.ui.theme.uiAccentOf

/**
 * 工具卡 → 工具页的**共享容器变换**（Shared Transition）。
 *
 * M3 Expressive 的容器变换（container transform）语义：
 *   起始容器（工具卡）与目标容器（工具页）之间做**位置 + 尺寸 + 圆角**的连续形变，
 *   由同一个弹簧驱动；内容在形变过程中淡出/淡入，因此用户看到的是「这张卡变成了这个页面」，
 *   而不是「两个页面互相推挤」。
 *
 * 实现（不引入实验性 shared-element API，行为稳定可控）：
 *  - 卡片 `onGloballyPositioned` 把工具 id → 根坐标 bounds 登记到 [ToolTransitionAnchors]；
 *  - 点击卡片：覆盖层从卡片 bounds **经弹簧**长成全屏圆角容器，同时 NavHost push 目标页
 *    （目标页自身 push 转场关闭，因此不会和覆盖层叠加出两重动画）；
 *  - 返回：工具页仍在前台时直接 pop，NavHost 的 pop 转场关闭；覆盖层以全屏开始、
 *    经**同一个弹簧**缩回卡片 bounds 并淡出 —— 全程只有一层动画，没有「快照遮底」。
 *  - 系统「移除动画」时直接跳转、无覆盖层。
 *
 * 与旧实现的差别（2026-11-19 重构）：**不再捕获界面快照 Bitmap**（旧做法把工具页
 * `View.draw()` 成图片再缩小，属于补丁式遮底，且在 WebView/SurfaceView 页会空白）。
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
 * 容器变换覆盖层：进入 = 卡片 bounds → 全屏；退出 = 全屏 → 卡片 bounds。
 *
 * @param reverse true = 返回方向（全屏 → 卡片）
 * @param onHalfway 进入方向：形变到 55% 时触发一次（此时导航 push 目标页，用户看不到空白）
 * @param onDone 动画结束（覆盖层撤除）
 */
@Composable
fun ToolTransitionOverlay(
    tool: AppTool?,
    startBounds: Rect?,
    onHalfway: () -> Unit,
    onDone: () -> Unit,
    reverse: Boolean = false,
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
    val colors = rememberCtColors()
    val accent = uiAccentOf(tool)
    val progress = remember(tool, reverse) { Animatable(0f) }
    LaunchedEffect(tool, reverse) {
        try {
            // 单一弹簧驱动整段形变（M3 Expressive spatial fast 0.75/600：≈300ms 成形，收得住、有弹性但不拖沓）
            progress.animateTo(targetValue = 1f, animationSpec = CtMotion.spatialFast)
        } finally {
            onDone()
        }
    }
    LaunchedEffect(tool, reverse) {
        if (!reverse) {
            // 进入：形变过半后开始 push 目标页，覆盖层在尾部淡出与之交接。
            kotlinx.coroutines.delay(CtMotionTokens.standardMs / 2L)
            onHalfway()
        }
    }

    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fullWidthPx = constraints.maxWidth.toFloat()
        val fullHeightPx = constraints.maxHeight.toFloat()
        // 进入：0→1（卡片长成全屏）；退出：1→0（全屏收回卡片）。
        val p = if (reverse) 1f - progress.value else progress.value
        val left = lerp(startBounds.left, 0f, p)
        val top = lerp(startBounds.top, 0f, p)
        val width = lerp(startBounds.width, fullWidthPx, p)
        val height = lerp(startBounds.height, fullHeightPx, p)
        // 圆角形变：卡片 20dp → 页面 0dp（M3 Expressive 形状变形）。
        // 弹簧可能短暂过冲（p>1），圆角必须夹在 [0, 卡片圆角] 内，否则 RoundedCornerShape 会抛异常
        val cornerDp = lerp(CtMotionTokens.containerMorphStartDp, 0f, p.coerceIn(0f, 1f))
        val cornerPx = cornerDp * density.density
        val shape: Shape = RoundedCornerShape(cornerPx)
        // 进入：最后 35% 淡出露出目标页；退出：全程不透明（底层是干净的根页）。
        val fade = when {
            reverse -> 1f
            // 提前到 45% 开始淡出、85% 完成交接：覆盖层只做「形体引导」，不长时间压住目标页，
            // 避免整屏色块在交接瞬间形成「闪一下」的观感（用户反馈的打开工具闪烁）。
            p <= 0.45f -> 1f
            else -> (1f - (p - 0.45f) / 0.40f).coerceIn(0f, 1f)
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
            // 2026-11-20 修复「打开工具闪白」：覆盖层原先是**不透明白板**，从玻璃卡片长成全屏时
            // 中途会形成整屏白闪。改成与页面同色系的半透明玻璃（+ 自上而下的微渐变），
            // 形变过程读作「卡片材质延展」，与目标页交接更自然。
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f),
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f),
                            ),
                        ),
                    ),
                shape = shape,
                color = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = colors.textPrimary,
                shadowElevation = 0.dp,
            ) {
                // 形变中的「容器内容」：卡片图标 + 名称（随形变淡出，目标页淡入接管）。
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier.graphicsLayer {
                            alpha = (1f - p * 1.4f).coerceIn(0f, 1f)
                        },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = CtShape.card,
                                color = androidx.compose.ui.graphics.Color.Transparent,
                                contentColor = accent.textRef,
                            ) {
                                Box(
                                    modifier = Modifier.size(CtSize.glyphBox),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = tool.icon,
                                        contentDescription = null,
                                        tint = accent.textRef,
                                        modifier = Modifier.size(CtSize.iconLarge),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(CtSpace.sm))
                            Text(
                                text = tool.name,
                                style = MaterialTheme.typography.titleMedium,
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

/** 形变起始圆角（dp；与 [CtRadius.card] 一致，供测试/预览引用）。 */
val CtToolMorphStartRadius = CtRadius.card
