package com.furina.classtools.ui.screens.home

import androidx.compose.material3.MaterialTheme


import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.timetable.TtPlan
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.ui.components.CtIconBadge
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.CtPressLayer
import com.furina.classtools.ui.components.CtTag
import com.furina.classtools.ui.components.background.CtGlassSurface
import com.furina.classtools.ui.navigation.ctToolTransitionSource
import com.furina.classtools.ui.theme.CtDarkGlassRole
import com.furina.classtools.ui.theme.CtElevation
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.CtSize
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.CtText
import com.furina.classtools.ui.theme.CtTypeScale
import com.furina.classtools.ui.theme.HarmonyTokens
import com.furina.classtools.ui.theme.ctPressFeedback
import com.furina.classtools.ui.theme.harmonyTint
import com.furina.classtools.ui.theme.rememberCtColors
import com.furina.classtools.ui.theme.rememberHarmonyColors
import com.furina.classtools.ui.theme.uiAccentOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// =============================================================================
// 工作台首卡和信息卡使用 CtGlassSurface，内容排版及常用工具由页面组件定义。
// =============================================================================

/** 工作台卡片圆角（鸿蒙 18）。 */
internal val HomeCardShape = RoundedCornerShape(HarmonyTokens.RadiusCard)

/** 工作台 hero 圆角（鸿蒙 24）。 */
internal val HomeHeroShape = RoundedCornerShape(HarmonyTokens.RadiusHero)

/**
 * 工作台「今日」卡，展示日期、周次与课程摘要。整卡点击打开课表。
 */
@Composable
internal fun HomeHeroCard(
    date: LocalDate,
    plan: TtPlan?,
    loading: Boolean,
    nowMillis: Long,
    onOpenTimetable: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val course = plan?.today?.firstOrNull { it.state == com.furina.classtools.data.timetable.SessionState.ONGOING } ?: plan?.next
    val ongoing = course?.state == com.furina.classtools.data.timetable.SessionState.ONGOING
    val empty = HomeWorkbenchLogic.heroState(plan, loading, nowMillis)
    val day = course?.let { Instant.ofEpochMilli(it.startAt).atZone(ZoneId.systemDefault()).toLocalDate() } ?: date
    CtGlassSurface(onClick = onOpenTimetable, shape = CtShape.card, darkRole = CtDarkGlassRole.HERO) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(listOf(if (course == null) empty.tag else if (ongoing) "正在上课" else "下一节课",
                HomeWorkbenchLogic.dateText(day)).joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            Text(course?.name ?: empty.title, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
            Text(if (course == null) empty.detail else listOf(
                if (ongoing) "${course.endText} 下课" else "${course.startText} 开始",
                course.fullRoom, course.span).filter(String::isNotBlank).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        }
    }
}

/**
 * 信息卡统一外壳（鸿蒙白卡）：tint 图标块 30dp + 标题 13 Medium 次级色 + 可选徽标 + 尾部动作 + 内容。
 *
 * 2026-11-19 对照鸿蒙 `HomeScreen.cardTitleRow`：`iconBadge(symbol, accent, 30, 17)` +
 * 标题 `F_LABEL(13) Medium TEXT_SECONDARY` + 间距 `SPACE_8`。
 */
@Composable
internal fun HomeInfoCard(
    icon: ImageVector,
    title: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = rememberCtColors()
    CtGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = CtShape.card,
        onClick = onClick,
        darkRole = CtDarkGlassRole.CARD,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CtSpace.cardPadding),
            verticalArrangement = Arrangement.spacedBy(CtSpace.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CtIconBadge(
                    icon = icon,
                    color = accent,
                    box = CtSize.badgeBox,
                    glyph = CtSize.badgeGlyph,
                )
                Spacer(modifier = Modifier.width(CtSpace.sm))
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                )
                if (!badge.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(CtSpace.xs))
                    CtTag(text = badge, color = accent)
                }
                if (trailing != null) Spacer(modifier = Modifier.width(CtSpace.sm))
                if (trailing != null) trailing()
            }
            content()
        }
    }
}

@Composable
internal fun HomeIconBadge(
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Int = 30,
    iconSize: Int = 17,
) {
    Surface(
        modifier = modifier.size(size.dp),
        shape = RoundedCornerShape((size * 0.3f).dp),
        color = harmonyTint(accent),
        contentColor = accent,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(iconSize.dp),
            )
        }
    }
}

/**
 * 常用工具单格（统一规范 §3.2「磁贴」）：44dp 淡色圆角图标块 + 12 名称，占满格宽；
 * 按压 scale 0.97（spring 0.75/600）+ 8% 按压层（M3 Expressive 实体感）。
 */
@Composable
internal fun QuickToolTile(
    tool: AppTool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val c = rememberCtColors()
    val interaction = remember { MutableInteractionSource() }
    val press = ctPressFeedback(interaction, reduceMotion)
    val transitionSource = Modifier.ctToolTransitionSource(tool.id, CtRadius.input)
    Surface(
        onClick = onClick,
        modifier = modifier
            .then(transitionSource)
            .graphicsLayer {
                scaleX = press.scale
                scaleY = press.scale
            },
        shape = CtShape.input,
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = c.textPrimary,
        interactionSource = interaction,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CtSpace.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CtSpace.xs),
            ) {
                val scheme = MaterialTheme.colorScheme
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                ) {
                    Box(modifier = Modifier.size(CtSpace.huge + CtSpace.xs), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier.size(CtSize.icon),
                        )
                    }
                }
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            CtPressLayer(alpha = press.overlayAlpha, modifier = Modifier.clip(CtShape.input))
        }
    }
}

/** 常用工具编辑模式单格（白卡 + 选中描边 + 右上角 ✓）。 */
@Composable
internal fun EditableToolTile(
    tool: AppTool,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val colors = rememberHarmonyColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = if (reduceMotion) snap() else CtMotion.spatialFast,
        label = "editToolScale",
    )
    Box(modifier = modifier.heightIn(min = 96.dp)) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            shape = HomeCardShape,
            color = colors.card,
            contentColor = colors.textPrimary,
            border = BorderStroke(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) {
                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                } else {
                    colors.divider
                },
            ),
            interactionSource = interaction,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val monet = com.furina.classtools.ui.theme.monetToneOf(tool)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = monet.container,
                    contentColor = monet.content,
                ) {
                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = monet.content,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = tool.name,
                    style = CtText.label,
                    color = colors.textPrimary,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "已选",
                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(18.dp),
            )
        }
    }
}

/** 首屏错峰入场（保留原能力；reduceMotion/预览时直接终态）。 */
@Composable
internal fun HomeReveal(
    enabled: Boolean,
    index: Int,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    // 必须 remember：否则每次重组都会新建 Animatable 并从 0 重新计时；
    // 自定义背景/刷新等导致父级重组时，入场动画会一直停在中途（整页/整卡发灰）。
    val progress = androidx.compose.runtime.remember(index) {
        androidx.compose.animation.core.Animatable(0f)
    }
    androidx.compose.runtime.LaunchedEffect(index) {
        kotlinx.coroutines.delay(index * 40L)
        progress.animateTo(1f, androidx.compose.animation.core.tween(durationMillis = 260))
    }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.graphicsLayer {
            alpha = 0.3f + 0.7f * progress.value
            translationY = (1f - progress.value) * 20f
        },
    ) {
        content()
    }
}






