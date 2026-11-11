package com.furina.classtools.ui.components.dock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtShapeTokens
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * **ColorOS 17 浮岛式底栏**（task-20 / v2.0.0）。
 *
 * 设计要点（对齐 ColorOS 17 Floating Island + M3 Expressive floating toolbar）：
 * - 半透明胶囊条：surfaceContainer 半透明 + 1dp 高光描边 + 柔和阴影（不用 NavigationBar 默认样式）；
 * - **悬浮离底**：底部在导航栏 insets 之上再留 10dp，左右留 20dp（平板限宽 480dp 居中）；
 * - **弹性药丸**：选中项背后 secondaryContainer 药丸，位置用 `selectedIndex + pagerOffsetFraction`
 *   跟手；松手由 [CtMotion.spatialFast] spring 吸附；拖动中药丸横向拉伸 8%、纵向略压；
 * - **柔性反馈**：按压时图标/文字缩放（选中 0.96 / 未选中 0.90），切换有轻触感；
 * - 触控：单个 item 高 60dp、宽 ≥ 1/3 屏宽（远超 48dp），`selectable(role = Role.Tab)`；
 * - TalkBack：`selectableGroup` + 每项 `contentDescription = "工作台，第 1 个 Tab，共 3 个，已选中"`。
 *
 * 接线（Lead 在 MainNavHost 的 `Scaffold.bottomBar`）：
 * ```kotlin
 * val pagerState = rememberCtTabPagerState(pageCount = 3)
 * Scaffold(bottomBar = {
 *     CtFloatingDock(
 *         destinations = TopLevelDestination.entries.map {
 *             CtDockDestination(it.route, it.label, it.icon)
 *         },
 *         selectedIndex = pagerState.currentPage,
 *         pagerOffsetFraction = pagerState.currentPageOffsetFraction, // 跟手药丸
 *         onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
 *     )
 * }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { ... }
 * ```
 *
 * @param pagerOffsetFraction 当前页内滑动偏移（PagerState.currentPageOffsetFraction），用于药丸跟手
 * @param translucent 半透明玻璃层（关闭后为不透明 surfaceContainer，省电/对比度偏好）
 * @param showLabels 是否显示文字（窄屏必须 true；大屏 rail 场景可 false）
 */
@Composable
fun CtFloatingDock(
    destinations: List<CtDockDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pagerOffsetFraction: Float = 0f,
    translucent: Boolean = true,
    showLabels: Boolean = true,
    /** 真实玻璃效果（Haze 等绘制在 Surface 上的 Modifier；null = 仅半透明色层）。 */
    glassModifier: Modifier? = null,
) {
    if (destinations.isEmpty()) return
    val safeIndex = selectedIndex.coerceIn(0, destinations.lastIndex)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    // 松手吸附：仅当不在拖动中时用 spring 动画；拖动中直接跟手。
    val dragging = abs(pagerOffsetFraction) > 0.01f
    val snappedIndex by animateFloatAsState(
        targetValue = safeIndex.toFloat(),
        animationSpec = CtMotion.spatialFast,
        label = "dockSnappedIndex",
    )
    val rawPosition = CtDockLogic.continuousPosition(safeIndex, pagerOffsetFraction, destinations.size)
    val indicatorPosition = if (dragging) rawPosition else snappedIndex

    val (stretchX, stretchY) = CtDockLogic.pillScale(if (dragging) pagerOffsetFraction else 0f)
    val pillScaleX by animateFloatAsState(
        targetValue = stretchX,
        animationSpec = if (dragging) CtMotion.fadeIn else CtMotion.spatialFast,
        label = "dockPillScaleX",
    )
    val pillScaleY by animateFloatAsState(
        targetValue = stretchY,
        animationSpec = if (dragging) CtMotion.fadeIn else CtMotion.spatialFast,
        label = "dockPillScaleY",
    )

    val containerColor = if (translucent) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .then(glassModifier ?: Modifier),
            shape = RoundedCornerShape(32.dp),
            color = if (glassModifier != null) Color.Transparent else containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, borderColor),
        ) {
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CtDockLogic.DOCK_HEIGHT_DP.dp),
            ) {
                val itemWidth = maxWidth / destinations.size
                val pillWidth = itemWidth - 10.dp
                val pillWidthPx = with(density) { pillWidth.toPx() }
                val itemWidthPx = with(density) { itemWidth.toPx() }
                val pillOffsetPx =
                    CtDockLogic.indicatorCenterX(0, itemWidthPx) +
                        indicatorPosition * itemWidthPx -
                        pillWidthPx / 2f

                // 顶部镜面高光（液态玻璃折射感；置于内容之下）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .align(androidx.compose.ui.Alignment.TopCenter)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceBright.copy(alpha = if (translucent) 0.55f else 0.2f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )

                // 弹性药丸底（选中项背后）
                Surface(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                pillOffsetPx.roundToInt(),
                                ((CtDockLogic.DOCK_HEIGHT_DP.dp - 44.dp).toPx() / 2f).roundToInt(),
                            )
                        }
                        .width(pillWidth)
                        .height(44.dp)
                        .graphicsLayer {
                            scaleX = pillScaleX
                            scaleY = pillScaleY
                        },
                    shape = CtShapeTokens.pill,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.55f),
                    ),
                    shadowElevation = 6.dp,
                ) {}

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CtDockLogic.DOCK_HEIGHT_DP.dp)
                        .selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    destinations.forEachIndexed { index, destination ->
                        DockItem(
                            destination = destination,
                            selected = index == safeIndex,
                            index = index,
                            count = destinations.size,
                            showLabel = showLabels,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (index != safeIndex) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSelect(index)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockItem(
    destination: CtDockDestination,
    selected: Boolean,
    index: Int,
    count: Int,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = CtDockLogic.pressScale(selected = selected, pressed = pressed),
        animationSpec = CtMotion.spatialFast,
        label = "dockItemScale",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = CtMotion.effectsSpec(),
        label = "dockItemColor",
    )
    val announcement = CtDockLogic.tabAnnouncement(
        label = destination.label,
        index = index,
        count = count,
        selected = selected,
    )

    Column(
        modifier = modifier
            .height(CtDockLogic.DOCK_HEIGHT_DP.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            )
            .semantics { contentDescription = announcement }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null, // 语义由父节点 announcement 提供（避免 TalkBack 重复播报）
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        if (showLabel) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = destination.label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}


