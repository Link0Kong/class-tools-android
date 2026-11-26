package com.furina.classtools.ui.components.dock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.components.glass.ctGlass
import com.furina.classtools.ui.theme.CtDarkGlassRole
import com.furina.classtools.ui.theme.CtLightLevel
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import com.furina.classtools.ui.theme.LocalCtLight
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 底部导航。玻璃化时使用 THIN 材质，关闭时使用实色导航栏。
 *
 * @param pagerOffsetFraction 当前页内滑动偏移，供选中指示器跟随分页滑动。
 * @param translucent 兼容旧调用。浮岛材质跟随外观设置。
 * @param showLabels 是否显示导航项文字。
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
    val scheme = MaterialTheme.colorScheme
    val glass = LocalCtLight.current.enabled
    val darkGlass = glass && LocalCtDarkTheme.current
    val indicatorColor = if (darkGlass) {
        lerp(scheme.primary, scheme.surfaceContainerHigh, 0.16f)
    } else {
        scheme.secondaryContainer
    }
    val selectedIconColor = if (darkGlass) scheme.onPrimary else scheme.onSecondaryContainer

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
                            level = CtLightLevel.THIN,
                            shape = animatedShape,
                            bodySafe = false,
                            haze = hazeState,
                            darkRole = CtDarkGlassRole.DOCK,
                        )
                    } else {
                        Modifier
                    },
                ),
            color = if (glass) Color.Transparent else scheme.surfaceContainer,
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

                // 选中指示器（64 × 32dp），与图标带对齐。
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
                    color = indicatorColor,
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
                            selectedIconColor = selectedIconColor,
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
    selectedIconColor: Color,
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
            selectedIconColor
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
            )
            Spacer(modifier = Modifier.height(CtSpace.xxs))
        }
    }
}




