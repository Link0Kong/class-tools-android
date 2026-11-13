@file:OptIn(ExperimentalLayoutApi::class)

package com.furina.classtools.ui.screens.notices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.components.expressive.CtAnimatedCounter
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import kotlinx.coroutines.delay

// =============================================================================
// 通知页高级动效（task-43）
//
// 设计口径：
//  - 稳定优先：只用 Compose 公开 API（animateFloat/Color/Dp、animateItem、AnimatedVisibility、
//    Animatable），不依赖实验性 cross-window shared element；
//  - reduceMotion（系统「移除动画」）下全部退化为无动画/直接终态；静态 @Preview 也不播放动画；
//  - 「点开详情」的视觉延伸 = 被点卡片 scale/圆角/投影小幅放大（选中态）+ 详情弹层滑入，
//    避免跨窗口共享元素带来的不稳定；
//  - 动效参数集中在本文件，页面只消费。
// =============================================================================

/** 通知页动效 token（集中管理，便于调参与统一降级）。 */
internal object NoticeMotionTokens {

    /** 列表错峰入场：仅首屏前若干项参与（避免滚动到新卡片时出现延迟空白）。 */
    const val STAGGER_STEP_MS: Long = 28L
    const val STAGGER_ITEM_COUNT: Int = 6

    /** 错峰入场位移（dp，向上收拢）与最低透明度。 */
    const val ENTRANCE_TRANSLATE_DP: Float = 20f
    const val ENTRANCE_MIN_ALPHA: Float = 0.25f

    /** 列表项进出场淡入/淡出（ms）。 */
    const val FADE_IN_MS: Int = 180
    const val FADE_OUT_MS: Int = 120

    /** 按压：卡片缩放与圆角收紧。 */
    const val PRESS_SCALE: Float = 0.965f
    const val PRESS_RADIUS_DELTA_DP: Float = 3f
    const val PRESS_ELEVATION_DP: Float = 2f

    /** 点开详情的「延伸」：轻微放大 / 圆角变大 / 投影抬升。 */
    const val EXPAND_SCALE: Float = 1.016f
    const val EXPAND_RADIUS_DELTA_DP: Float = 8f
    const val EXPAND_ELEVATION_DP: Float = 5f

    /** 经典 spring（复用工程 CtMotion token，保证全局手感一致）。 */
    val pressSpring: FiniteAnimationSpec<Float> get() = CtMotion.spatialFast
    val expandSpring: FiniteAnimationSpec<Float> get() = CtMotion.spatial
    val crossFade: FiniteAnimationSpec<Float> get() = CtMotion.effects
    /** chips 弹性回弹：复用 CtMotion.spatialFast（0.6 / 800，带轻微 overshoot）。 */
    val chipSettleSpring: FiniteAnimationSpec<Float> get() = CtMotion.spatialFast

    /** 颜色过渡（Color 需要 AnimationSpec<Color>，不能用 Float 的 spring token）。 */
    val colorSpec: FiniteAnimationSpec<Color> = tween(durationMillis = 180)
    val entranceSpring: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.86f, stiffness = 420f)
}

// -----------------------------------------------------------------------------
// 列表：animateItem（位置/进出场）+ 错峰入场
// -----------------------------------------------------------------------------

/** LazyColumn 项：位置变化平滑 + 进出场淡入淡出（reduceMotion 时无动画）。 */
internal fun LazyItemScope.noticeAnimateItem(reduceMotion: Boolean): Modifier =
    if (reduceMotion) {
        Modifier
    } else {
        Modifier.animateItem(
            fadeInSpec = tween(NoticeMotionTokens.FADE_IN_MS),
            placementSpec = spring(dampingRatio = 0.84f, stiffness = 420f),
            fadeOutSpec = tween(NoticeMotionTokens.FADE_OUT_MS),
        )
    }

/** LazyVerticalGrid 项：同 [noticeAnimateItem]。 */
internal fun LazyGridItemScope.noticeAnimateItem(reduceMotion: Boolean): Modifier =
    if (reduceMotion) {
        Modifier
    } else {
        Modifier.animateItem(
            fadeInSpec = tween(NoticeMotionTokens.FADE_IN_MS),
            placementSpec = spring(dampingRatio = 0.84f, stiffness = 420f),
            fadeOutSpec = tween(NoticeMotionTokens.FADE_OUT_MS),
        )
    }

/**
 * 列表项错峰入场（透明度 + 向上位移）。
 *
 * - 首次出现：按 index 延迟 [NoticeMotionTokens.STAGGER_STEP_MS] 依次入场；
 * - 滚动回来（key 已在 [appearedKeys] 中）：直接显示，不重复播放；
 * - reduceMotion / 静态预览 / 预览注入 progress：直接终态（预览可注入指定帧）。
 *
 * @param appearedKeys 页面级「已入场 key」集合（跨滚动/跨重组持久，避免滚动回来闪烁）
 * @param previewBaseProgress 预览注入的入场基准进度（0..1）；按 index 递减制造错峰帧
 */
@Composable
internal fun NoticeEntrance(
    itemKey: String,
    index: Int,
    reduceMotion: Boolean,
    appearedKeys: SnapshotStateMap<String, Boolean>,
    previewBaseProgress: Float?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val inspection = LocalInspectionMode.current
    // 只让首屏前若干项错峰入场；滚动后才出现的卡片直接显示（避免延迟空白），
    // 其出现/消失由 Lazy nimateItem 的淡入淡出负责。
    val staggerEligible = index < NoticeMotionTokens.STAGGER_ITEM_COUNT
    val instant = reduceMotion || inspection || previewBaseProgress != null || !staggerEligible
    val appeared = instant || appearedKeys[itemKey] == true

    if (!instant && appearedKeys[itemKey] != true) {
        LaunchedEffect(itemKey) {
            delay(index * NoticeMotionTokens.STAGGER_STEP_MS)
            appearedKeys[itemKey] = true
        }
    }

    val animated by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.entranceSpring,
        label = "noticeEntrance",
    )
    val progress = previewBaseProgress?.let { base ->
        (base - index * 0.18f).coerceIn(0f, 1f)
    } ?: if (instant) {
        1f
    } else {
        animated
    }

    Box(
        modifier = modifier.graphicsLayer {
            alpha = NoticeMotionTokens.ENTRANCE_MIN_ALPHA +
                (1f - NoticeMotionTokens.ENTRANCE_MIN_ALPHA) * progress
            translationY = (1f - progress) * NoticeMotionTokens.ENTRANCE_TRANSLATE_DP.dp.toPx()
        },
    ) {
        content()
    }
}

// -----------------------------------------------------------------------------
// 卡片按压 / 点开延伸
// -----------------------------------------------------------------------------

/**
 * 通知卡片可按压容器（替代 CtPressableCard 的通用版）：
 *
 * - 按下：scale 1.0 → 0.965（spring，跟手回弹）+ 圆角收紧 + 浮起 2dp + 顶部高光；
 * - 点开详情（[expanded]）：scale → 1.016、圆角变大、投影抬升 5dp，配合弹层滑入形成延伸感；
 * - reduceMotion：不缩放/无高光动画（[expanded] 直接终态），仍保留可点击与语义。
 */
@Composable
internal fun NoticePressableCard(
    onClick: () -> Unit,
    containerColor: Color,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    expanded: Boolean = false,
    previewPressed: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // 预览注入时优先展示注入态（即使系统 reduceMotion 打开）
    val pressTarget = if (previewPressed || pressed) 1f else 0f
    val press by animateFloatAsState(
        targetValue = if (reduceMotion && !previewPressed) 0f else pressTarget,
        animationSpec = if (reduceMotion && !previewPressed) snap() else NoticeMotionTokens.pressSpring,
        label = "noticeCardPress",
    )
    // reduceMotion 下不做装饰性延伸（避免弹层打开时卡片瞬间跳一下）
    val expandTarget = if (expanded && !reduceMotion) 1f else 0f
    val expand by animateFloatAsState(
        targetValue = expandTarget,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.expandSpring,
        label = "noticeCardExpand",
    )

    val radius by animateDpAsState(
        targetValue = 16.dp -
            (NoticeMotionTokens.PRESS_RADIUS_DELTA_DP * press).dp +
            (NoticeMotionTokens.EXPAND_RADIUS_DELTA_DP * expand).dp,
        animationSpec = if (reduceMotion) snap() else CtMotion.spatialFastDp,
        label = "noticeCardRadius",
    )
    val elevation by animateDpAsState(
        targetValue = (NoticeMotionTokens.PRESS_ELEVATION_DP * press).dp +
            (NoticeMotionTokens.EXPAND_ELEVATION_DP * expand).dp,
        animationSpec = if (reduceMotion) snap() else CtMotion.spatialFastDp,
        label = "noticeCardElevation",
    )
    val scale = 1f -
        (1f - NoticeMotionTokens.PRESS_SCALE) * press +
        (NoticeMotionTokens.EXPAND_SCALE - 1f) * expand

    // 高光：深色用白色微高光（surfaceBright 在深色下本身很暗），浅色用 surfaceBright
    val dark = LocalCtDarkTheme.current
    val highlight = if (dark) Color.White else MaterialTheme.colorScheme.surfaceBright
    val highlightAlpha = (if (dark) 0.12f else 0.16f) * press + (if (dark) 0.04f else 0.07f) * expand

    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Button
        }
    } else {
        Modifier.semantics { role = Role.Button }
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(semanticsModifier),
        shape = RoundedCornerShape(radius),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = elevation,
        interactionSource = interaction,
    ) {
        Box {
            content()
            if (highlightAlpha > 0.005f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(radius))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    highlight.copy(alpha = highlightAlpha),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 筛选 chips：弹性选中 + 勾选动画 + 计数动画
// -----------------------------------------------------------------------------

/** 通知筛选 chip（key/label/可选计数）。 */
internal data class NoticeChip(
    val key: String,
    val label: String,
    val count: Int? = null,
)

/**
 * 筛选 chips（FlowRow，可换行适配大字体/窄屏）：
 * 选中时弹性放大回弹（spring）、勾选图标缩放淡入、容器色渐变；计数用 [CtAnimatedCounter]。
 */
@Composable
internal fun NoticeFilterChips(
    chips: List<NoticeChip>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    previewBounceKey: String? = null,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            NoticeElasticChip(
                chip = chip,
                selected = chip.key == selectedKey,
                reduceMotion = reduceMotion,
                previewBounce = previewBounceKey == chip.key,
                onClick = { onSelect(chip.key) },
            )
        }
    }
}

@Composable
private fun NoticeElasticChip(
    chip: NoticeChip,
    selected: Boolean,
    reduceMotion: Boolean,
    previewBounce: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(selected, reduceMotion) {
        if (reduceMotion) {
            bounce.snapTo(1f)
            return@LaunchedEffect
        }
        if (selected) {
            bounce.snapTo(1f)
            bounce.animateTo(targetValue = 1.07f, animationSpec = tween(durationMillis = 90))
            bounce.animateTo(
                targetValue = 1f,
                animationSpec = NoticeMotionTokens.chipSettleSpring,
            )
        } else {
            bounce.animateTo(targetValue = 1f, animationSpec = NoticeMotionTokens.chipSettleSpring)
        }
    }
    val scale = if (previewBounce) 1.07f else bounce.value

    val container by animateColorAsState(
        targetValue = if (selected) scheme.secondaryContainer else Color.Transparent,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.colorSpec,
        label = "noticeChipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.colorSpec,
        label = "noticeChipContent",
    )
    val border by animateColorAsState(
        targetValue = if (selected) Color.Transparent else scheme.outlineVariant,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.colorSpec,
        label = "noticeChipBorder",
    )

    val checkEnter: EnterTransition =
        if (reduceMotion) EnterTransition.None else fadeIn(tween(90)) + scaleIn(initialScale = 0.4f, animationSpec = tween(140))
    val checkExit: ExitTransition =
        if (reduceMotion) ExitTransition.None else fadeOut(tween(70)) + scaleOut(targetScale = 0.4f, animationSpec = tween(100))

    Surface(
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                role = Role.Button
                this.selected = selected
                stateDescription = if (selected) "已选中" else "未选中"
            },
        shape = CtShapeTokens.chip,
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = selected,
                enter = checkEnter,
                exit = checkExit,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }
            Text(
                text = chip.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (chip.count != null) {
                Spacer(Modifier.width(5.dp))
                CtAnimatedCounter(
                    value = chip.count,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    animationSpec = NoticeMotionTokens.crossFade,
                    label = "noticeChipCount_${chip.key}",
                )
            }
        }
    }
}
