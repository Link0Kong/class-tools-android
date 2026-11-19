package com.furina.classtools.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.timetable.TtPlan
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.navigation.ctToolTransitionSource
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtText
import com.furina.classtools.ui.theme.HarmonyTokens
import com.furina.classtools.ui.theme.harmonyTint
import com.furina.classtools.ui.theme.rememberHarmonyColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// =============================================================================
// 工作台组件（2026-11-17 按鸿蒙 HomeScreen.ets 重做）
//
// - hero：「莫奈纸面」浅色卡（#F1F6F2 / 1px #DCE7E2 / 24 圆角 / 顶部三色 3px 条 /
//   「今日」#FFFCF6 胶囊 / 问候 24 Bold #303A35 / 副行 12 #5C6860 / 摘要分割线 #D8E4DE）；
// - 信息卡：白 18 圆角 + 浅阴影 + 40dp tint 图标块 + 15 标题 + 12 说明；
// - 常用工具：白卡 + 4 列 tint 图标块（46dp）+ 12 名称；
// - 深色模式自动映射到中性深底（见 harmonyColors）。
// =============================================================================

/** 工作台卡片圆角（鸿蒙 18）。 */
internal val HomeCardShape = RoundedCornerShape(HarmonyTokens.RadiusCard)

/** 工作台 hero 圆角（鸿蒙 24）。 */
internal val HomeHeroShape = RoundedCornerShape(HarmonyTokens.RadiusHero)

/**
 * 工作台 hero（鸿蒙 HomeScreen.heroCard 同款「莫奈纸面」浅色卡）。
 *
 * 结构：三色 3px 顶条 → 「今日」奶白胶囊 + 周次 → 问候 24 Bold → 副行 12 →
 * 摘要行（上分割线）：今日课程摘要 + 「课表 ›」；整卡点击打开课表。
 */
@Composable
internal fun HomeHeroCard(
    session: Session,
    date: LocalDate,
    plan: TtPlan?,
    loading: Boolean,
    nowMillis: Long,
    reduceMotion: Boolean,
    onOpenTimetable: () -> Unit,
) {
    val colors = rememberHarmonyColors()
    val zone = remember { ZoneId.systemDefault() }
    val hour = remember(nowMillis) { Instant.ofEpochMilli(nowMillis).atZone(zone).hour }
    val greeting = HomeWorkbenchLogic.greeting(hour)
    val hero = remember(plan, loading, nowMillis) {
        HomeWorkbenchLogic.heroState(plan, loading, nowMillis)
    }
    val dateText = remember(date) { HomeWorkbenchLogic.dateText(date) }
    val weekText = remember(plan?.week) {
        HomeWorkbenchLogic.weekText(plan?.week).ifBlank { "课表未同步" }
    }
    val roleText = session.role.trim()
    val subLine = remember(dateText, roleText, plan?.hasData) {
        buildList {
            add(dateText)
            if (roleText.isNotBlank()) add(roleText)
            if (plan?.hasData != true) add("课表未同步")
        }.joinToString(" · ")
    }
    val summary = remember(hero, plan) {
        when {
            plan == null || !plan.hasData -> "打开课表即可拉取本周课程"
            plan.today.isEmpty() -> "今天没课" + (plan.next?.let { " · 下次 ${it.label} ${it.name}" } ?: "")
            else -> buildString {
                if (hero.tag.isNotBlank()) append(hero.tag).append(" · ")
                append(hero.title)
                if (hero.detail.isNotBlank()) append(" · ").append(hero.detail)
            }
        }
    }
    val int = remember { MutableInteractionSource() }
    val pressed by int.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.99f else 1f,
        animationSpec = if (reduceMotion) snap() else CtMotion.spatialFast,
        label = "homeHeroPress",
    )

    Surface(
        onClick = onOpenTimetable,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = HarmonyTokens.Gap)
            .shadow(
                elevation = HarmonyTokens.HeroShadowElevation,
                shape = HomeHeroShape,
                clip = false,
                ambientColor = HarmonyTokens.HeroShadowColor,
                spotColor = HarmonyTokens.HeroShadowColor,
            )
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        shape = HomeHeroShape,
        color = colors.heroPaper,
        contentColor = colors.heroTitle,
        border = BorderStroke(HarmonyTokens.HairlineWidth, colors.heroBorder),
        interactionSource = int,
    ) {
        Column(modifier = Modifier.padding(HarmonyTokens.PagePadding)) {
            // 顶部青/蓝/沙三色 3px 条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxSize().background(HarmonyTokens.MistTeal))
                Box(modifier = Modifier.weight(1f).fillMaxSize().background(HarmonyTokens.SlateBlue))
                Box(modifier = Modifier.weight(1f).fillMaxSize().background(HarmonyTokens.WarmSand))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = colors.heroPill,
                    contentColor = colors.heroAccent,
                ) {
                    Text(
                        text = "今日",
                        style = CtText.caption,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = weekText,
                    style = CtText.caption,
                    color = colors.heroSub,
                )
            }
            Text(
                text = "$greeting，${session.name.trim().ifBlank { "同学" }}",
                style = CtText.title.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
                color = colors.heroTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = subLine,
                style = CtText.caption,
                color = colors.heroSub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            // 摘要分割线（#D8E4DE；鸿蒙 heroCard 摘要行上边框）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(0.5.dp)
                    .background(colors.heroDivider),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary,
                        style = CtText.label,
                        color = colors.heroTitle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "课表 ›",
                    style = CtText.caption,
                    color = colors.heroLink,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable { onOpenTimetable() },
                )
            }
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
    val colors = rememberHarmonyColors()
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = HarmonyTokens.CardShadowElevation,
                shape = HomeCardShape,
                clip = false,
                ambientColor = HarmonyTokens.CardShadowColor,
                spotColor = HarmonyTokens.CardShadowColor,
            ),
        shape = HomeCardShape,
        color = colors.card,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HarmonyTokens.CardPadding, vertical = HarmonyTokens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(HarmonyTokens.ElementGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HomeIconBadge(icon = icon, accent = accent)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = CtText.labelMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!badge.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    CtPill(
                        text = badge,
                        containerColor = harmonyTint(accent),
                        contentColor = accent,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (trailing != null) trailing()
            }
            content()
        }
    }
}

/** 分类色图标块（鸿蒙 `HomeScreen.iconBadge`：30dp tint 底 9dp 圆角 + 17dp 字形）。 */
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

/** 常用工具单格（鸿蒙：46dp tint 块 + 12 名称；弹簧按压缩放）。 */
@Composable
internal fun QuickToolTile(
    tool: AppTool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val colors = rememberHarmonyColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = if (reduceMotion) snap() else CtMotion.spatialFast,
        label = "quickToolScale",
    )
    val transitionSource = Modifier.ctToolTransitionSource(tool.id)
    Surface(
        onClick = onClick,
        modifier = modifier
            .then(transitionSource)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(HarmonyTokens.RadiusCard),
        color = Color.Transparent,
        contentColor = colors.textPrimary,
        interactionSource = interaction,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(HarmonyTokens.RadiusInput),
                color = harmonyTint(tool.accent),
                contentColor = tool.accent,
            ) {
                Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        tint = tool.accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Text(
                text = tool.name,
                style = CtText.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    Box(modifier = modifier.height(96.dp)) {
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
                Surface(
                    shape = RoundedCornerShape(HarmonyTokens.RadiusTile),
                    color = harmonyTint(tool.accent),
                    contentColor = tool.accent,
                ) {
                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = tool.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = tool.name,
                    style = CtText.label,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
    val progress = androidx.compose.animation.core.Animatable(0f)
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
