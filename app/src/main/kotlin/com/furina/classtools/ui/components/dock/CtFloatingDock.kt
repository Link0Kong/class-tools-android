package com.furina.classtools.ui.components.dock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.components.glass.ctGlass
import com.furina.classtools.ui.theme.CtLightLevel
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtSpace
import kotlin.math.abs

import kotlin.math.roundToInt

/** 深色浮岛底不透明度（比页面底更实、更深，轮廓清楚不灰糊）。 */
private const val DARK_DOCK_ALPHA: Float = 0.92f

/**
 * **ColorOS 17 浮岛式底栏**（task-20 / v2.0.0）。
 *
 * 设计要点（对齐 ColorOS 17 Floating Island + M3 Expressive floating toolbar）：
 * - 半透明胶囊条：surfaceContainer 半透明 + 1dp 高光描边（不用 NavigationBar 默认样式）；
 * - **浅色无黑色投影**（v4.55 用户反馈「浅色底部工具栏发黑」）：浅色下阴影 0dp、改用 1dp 细边框
 *   + 近白 tonal 底定义边缘；深色保留克制阴影（6dp）。避免 12dp 高度阴影在浅色页面上形成黑灰晕影；
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
    /** 真实玻璃效果（Haze 等绘制在 Surface 上的 Modifier；null = 仅半透明色层）。历史参数，保留兼容。 */
    glassModifier: Modifier? = null,
    /** 页面级 Haze 源（源 = 会从浮岛下面滚过的内容）；传入后材质做真模糊。 */
    hazeState: dev.chrisbanes.haze.HazeState? = null,
) {
    if (destinations.isEmpty()) return
    val safeIndex = selectedIndex.coerceIn(0, destinations.lastIndex)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val specularColor = MaterialTheme.colorScheme.surfaceBright
    var specularX by remember { androidx.compose.runtime.mutableFloatStateOf(-1f) }

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

    // 2026-11-19 沉浸光感 + 深色优化：浮岛 = THIN + 底部渐变颜色蒙层（官方「底部悬浮」规范）。
    // 深色专用（用户反馈「深色工具栏灰糊」）：
    //   - 底色不再用 surfaceContainerLow（与页面底几乎同色 → 灰糊），改为**更深的实感底**
    //     `DarkBar`(#171C1F) 叠加 0.92 不透明度：比内容层更暗、轮廓清楚；
    //   - 1px 高光边（白 0.18）+ 顶部线性高光（白 0.10），让浮岛从深底上「浮」起来；
    //   - 选中药丸改为**低饱和雾青**（primary 0.22）+ 1px 亮边，文字/图标用 primary（对深底 ≥7:1）。
    // 2026-11-20 沉浸光感 v2：浮岛改用统一材质引擎（THIN 档 + 真模糊 + 点光源高光 + 折射亮边），
    // 不再按明暗自建两套手绘材质 —— 深浅色差异全部由 CtGlassTable 表达（深色更实、亮边代替投影）。
    val dockShape = RoundedCornerShape(CtRadius.hero)
    val glass = com.furina.classtools.ui.theme.LocalCtLight.current.enabled
    // 玻璃浮动底栏：更薄（68dp）+ 左右 24dp / 底部 8dp；与实色 80dp 导航栏之间做收缩动画。
    val dockHeight by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (glass) 68.dp else 80.dp,
        animationSpec = androidx.compose.animation.core.tween(260),
        label = "dockHeight",
    )
    val horizontalInset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (glass) 24.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(260),
        label = "dockHorizontalInset",
    )
    val verticalInset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (glass) 8.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(260),
        label = "dockVerticalInset",
    )
    val dockCorner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (glass) 28.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(260),
        label = "dockCorner",
    )
    val contentInset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (glass) 8.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(260),
        label = "dockContentInset",
    )
    val animatedShape = RoundedCornerShape(dockCorner)
    // M3 NavigationBar：整宽、80dp、surfaceContainer，无圆角/无阴影。
    // 仍保留跟手指示器动画，但外观按标准导航栏而不是悬浮玻璃条。
    val pillShadowElevation = 0.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = horizontalInset, vertical = verticalInset),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (glass) {
                        Modifier.ctGlass(
                            level = com.furina.classtools.ui.theme.CtLightLevel.THIN,
                            shape = animatedShape,
                            bodySafe = false,
                            haze = hazeState,
                        )
                    } else {
                        Modifier
                    },
                ),
            color = if (glass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
            shape = animatedShape,
        ) {
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = contentInset)
                    // 200% 字体下允许底栏随文案长高。
                    .heightIn(min = dockHeight),
            ) {
                val itemWidth = maxWidth / destinations.size
                // M3 指示器固定 64 × 32dp（不随 item 宽度拉伸）；item 仍占满 1/N。
                val pillWidth = minOf(itemWidth - 16.dp, CtDockLogic.INDICATOR_WIDTH_DP.dp)
                val pillWidthPx = with(density) { pillWidth.toPx() }
                val itemWidthPx = with(density) { itemWidth.toPx() }
                val pillOffsetPx =
                    CtDockLogic.indicatorCenterX(0, itemWidthPx) +
                        indicatorPosition * itemWidthPx -
                        pillWidthPx / 2f

                // 选中指示器（M3：64 × 32dp，secondaryContainer；与图标带 32dp 对齐，不压文案）
                Surface(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                pillOffsetPx.roundToInt(),
                                CtDockLogic.INDICATOR_TOP_DP.dp.roundToPx(),
                            )
                        }
                        .width(pillWidth)
                        .height(CtDockLogic.INDICATOR_HEIGHT_DP.dp)
                        .graphicsLayer {
                            scaleX = pillScaleX
                            scaleY = pillScaleY
                        },
                    shape = CtShapeTokens.pill,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shadowElevation = pillShadowElevation,
                ) {}

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = dockHeight)
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
            // M3 navigation bar：选中图标 onSecondaryContainer（压在 secondaryContainer 指示器上）。
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = CtMotion.effectsSpec(),
        label = "dockItemColor",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = CtMotion.effectsSpec(),
        label = "dockLabelColor",
    )
    val announcement = CtDockLogic.tabAnnouncement(
        label = destination.label,
        index = index,
        count = count,
        selected = selected,
    )

    Column(
        modifier = modifier
            .heightIn(min = CtDockLogic.DOCK_HEIGHT_DP.dp)
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
        verticalArrangement = Arrangement.Top,
    ) {
        // 32dp 图标带：选中指示器（64×32）正好包住图标，不压到下方案案。
        Box(
            modifier = Modifier
                .padding(top = CtDockLogic.INDICATOR_TOP_DP.dp)
                .height(CtDockLogic.INDICATOR_HEIGHT_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = null, // 语义由父节点 announcement 提供（避免 TalkBack 重复播报）
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
        }
        if (showLabel) {
            Text(
                text = destination.label,
                // M3 navigation bar label = Label Medium（12sp / 500）；不再用 11sp 裸字号。
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(CtSpace.xxs))
        }
    }
}




