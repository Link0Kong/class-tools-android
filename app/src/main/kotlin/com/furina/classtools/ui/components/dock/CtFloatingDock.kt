package com.furina.classtools.ui.components.dock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furina.classtools.ui.components.light.ctLightMaterial
import com.furina.classtools.ui.components.light.CtLightInk
import com.furina.classtools.ui.theme.CtLightScene
import com.furina.classtools.ui.theme.CtPalette
import com.furina.classtools.ui.theme.LocalCtLight
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.CtType
import com.furina.classtools.ui.theme.LocalCtDarkTheme
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
    /** 真实玻璃效果（Haze 等绘制在 Surface 上的 Modifier；null = 仅半透明色层）。 */
    glassModifier: Modifier? = null,
) {
    if (destinations.isEmpty()) return
    val safeIndex = selectedIndex.coerceIn(0, destinations.lastIndex)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val dark = LocalCtDarkTheme.current
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
    val light = LocalCtLight.current
    val containerColor = if (dark) CtPalette.DarkBar else com.furina.classtools.ui.theme.HarmonyTokens.BarLight
    val dockShape = RoundedCornerShape(CtRadius.hero)
    val glass = if (dark) {
        // 深色：不用 CtLight 的 THICK 材质（其底色 = DarkContainer，和页面底撞色 → 灰糊），
        // 改用「深色玻璃」：更深底色 + 高光边 + 顶部高光（材质仍统一来自 CtGlassSurface）。
        Modifier
            .clip(dockShape)
            .background(containerColor.copy(alpha = DARK_DOCK_ALPHA))
            .drawWithCache {
                val highlight = androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to CtLightInk.highlight.copy(alpha = 0.10f),
                    0.5f to Color.Transparent,
                )
                onDrawBehind {
                    drawRect(brush = highlight)
                    // 1px 高光边（上）+ 极淡暗边（下），塑造玻璃厚度
                    drawRect(
                        color = CtLightInk.highlight.copy(alpha = 0.18f),
                        topLeft = Offset(0f, 0f),
                        size = androidx.compose.ui.geometry.Size(size.width, 1f),
                    )
                    drawRect(
                        color = CtLightInk.shade.copy(alpha = 0.35f),
                        topLeft = Offset(0f, size.height - 1f),
                        size = androidx.compose.ui.geometry.Size(size.width, 1f),
                    )
                }
            }
    } else if (light.enabled) {
        Modifier.ctLightMaterial(
            CtLightScene.BOTTOM_DOCK,
            shape = dockShape,
            fadeAtTop = true,
            bodySafe = true,
            // 浮岛文字/图标需要清楚：比官方 THIN 略厚一档，视觉仍是悬浮薄雾。
            levelOverride = com.furina.classtools.ui.theme.CtLightLevel.THICK,
        )
    } else {
        Modifier
            .clip(dockShape)
            .background(containerColor)
            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), dockShape)
    }
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val dockShadowElevation = 0.dp
    val pillShadowElevation = 0.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .then(glass),
        ) {
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CtDockLogic.DOCK_HEIGHT_DP.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull()
                                specularX = if (change != null && change.pressed) change.position.x else -1f
                            }
                        }
                    },
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

                // 光场：高光跟随触摸（ColorOS 光场设计；不消费事件，不影响点击）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CtDockLogic.DOCK_HEIGHT_DP.dp)
                        .drawBehind {
                            val x = if (specularX >= 0f) specularX else size.width * 0.5f
                            val alpha = if (specularX >= 0f) 0.22f else 0f
                            if (alpha > 0f) {
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            specularColor.copy(alpha = alpha),
                                            Color.Transparent,
                                        ),
                                        center = Offset(x, size.height * 0.18f),
                                        radius = size.width * 0.42f,
                                    ),
                                )
                            }
                        },
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
                    color = if (dark) {
                        // 深色：低饱和雾青药丸（primary 0.22）+ 1px 亮边，避免「灰药丸糊在灰底上」
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    } else {
                        com.furina.classtools.ui.theme.HarmonyTokens.TintTeal
                    },
                    border = if (dark) {
                        BorderStroke(1.dp, CtLightInk.highlight.copy(alpha = 0.14f))
                    } else {
                        BorderStroke(0.dp, Color.Transparent)
                    },
                    shadowElevation = pillShadowElevation,
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
            MaterialTheme.colorScheme.primary
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
            modifier = Modifier.size(20.dp),
        )
        if (showLabel) {
            Spacer(modifier = Modifier.height(CtSpace.xxs))
            Text(
                text = destination.label,
                // 鸿蒙 AppShell.tabItem：图标 20 + 文案 10 + 间距 2（安卓仅把文案色保留为可读的 primary）。
                fontSize = CtType.microSize,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}
