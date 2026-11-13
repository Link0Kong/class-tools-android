package com.furina.classtools.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.timetable.TtPlan
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.ui.navigation.ctToolTransitionSource
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.glass.CtGlassCard
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

// =============================================================================
// 工作台 v5 · 表达性组件（只在本包内复用；不修改 ui/components 公共组件）
//
// 设计参照（只借鉴视觉语言，不复制素材/代码）：
// - xiaoying「我的」页：顶部渐变 hero + 重叠/半透明头像 + 四图标快捷行 + 2 列卡片；
// - HFUT-Schedule：卡片柔和分类色、图标气泡、大圆角与克制阴影。
// 现有品牌约束不变：暖白底 / 雾青 / 灰蓝 / 暖沙，卡片圆角 16–20dp，文字仍走
// MaterialTheme.colorScheme（深浅色对比度由主题保证）。
// =============================================================================

/** 工作台 v5 卡片圆角（比旧 16dp 更柔和，仍小于 hero 的 28dp）。 */
internal val HomeCardShape = RoundedCornerShape(20.dp)

/** 工作台 v5 hero 圆角（M3 extraLarge + 4dp，接近 xiaoying 渐变头图）。 */
internal val HomeHeroShape = RoundedCornerShape(28.dp)

/**
 * 顶部「今日」hero：日期/星期 + 问候 + 上课中/下一节 + 进度条。
 *
 * 数据来自 [HomeWorkbenchLogic.heroState]（纯逻辑可单测）；点击整卡打开课表。
 * 深浅色自适应：浅色用 primary→secondary 深渐变 + 白字；深色用容器色 + 浅色字。
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
    val zone = remember { ZoneId.systemDefault() }
    val hour = remember(nowMillis) { Instant.ofEpochMilli(nowMillis).atZone(zone).hour }
    val greeting = HomeWorkbenchLogic.greeting(hour)
    val hero = remember(plan, loading, nowMillis) {
        HomeWorkbenchLogic.heroState(plan, loading, nowMillis)
    }
    val dateText = remember(date) { HomeWorkbenchLogic.dateText(date) }
    val weekText = remember(plan?.week) { HomeWorkbenchLogic.weekText(plan?.week) }
    val roleText = session.role.trim()

    val dark = LocalCtDarkTheme.current
    val start = if (dark) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
    val end = if (dark) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.secondary
    val content = if (dark) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.992f else 1f,
        animationSpec = if (reduceMotion) snap() else CtMotion.spatialFast,
        label = "homeHeroPress",
    )

    Surface(
        onClick = onOpenTimetable,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        shape = HomeHeroShape,
        color = Color.Transparent,
        contentColor = content,
        border = BorderStroke(1.dp, content.copy(alpha = 0.14f)),
        interactionSource = interaction,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(colors = listOf(start, end))),
        ) {
            // 光场高光：右上角柔光 + 左下暗角（HFUT/ColorOS 风格的柔和层次，不使用模糊 API）。
            // 用 radialGradient + drawRect（与 ExpressiveBackground 同实现）而不是纯色圆形，
            // 避免出现生硬的「斜切色带」边缘，也规避 drawCircle(brush) 在预览渲染器里的异常。
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            content.copy(alpha = if (dark) 0.10f else 0.15f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.90f, -size.width * 0.06f),
                        radius = size.width * 0.72f,
                    ),
                    size = size,
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = if (dark) 0.0f else 0.06f),
                            Color.Transparent,
                        ),
                        center = Offset(-size.width * 0.05f, size.height * 1.06f),
                        radius = size.width * 0.55f,
                    ),
                    size = size,
                )
            }

            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeroChip(text = "今日", tint = content)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelLarge,
                        color = content.copy(alpha = 0.94f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    HomeAvatar(
                        initial = session.name.trim().take(1).ifBlank { "同" },
                        tint = content,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "$greeting，${session.name.trim().ifBlank { "同学" }}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subParts = listOf(weekText, roleText).filter { it.isNotBlank() }
                if (subParts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subParts.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = content.copy(alpha = 0.80f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                HeroCourseBlock(hero = hero, contentColor = content, reduceMotion = reduceMotion)
            }
        }
    }
}

/** hero 内课程区（上课中 / 下一节 / 空态 + 进度）。 */
@Composable
private fun HeroCourseBlock(hero: HomeHeroState, contentColor: Color, reduceMotion: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(contentColor.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroChip(text = hero.tag, tint = contentColor)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = hero.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (hero.detail.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = hero.detail,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.86f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val progress = hero.progress
        if (progress != null) {
            Spacer(modifier = Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = hero.progressText,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.90f),
                )
                Spacer(modifier = Modifier.weight(1f))
                if (hero.hint.isNotBlank()) {
                    Text(
                        text = hero.hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.90f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            HomeHeroProgressBar(progress = progress, color = contentColor, reduceMotion = reduceMotion)
        } else if (hero.hint.isNotBlank()) {
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = hero.hint,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.90f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** hero 进度条：细胶囊轨道 + 弹簧填充；reduceMotion 下直接切换（不做长动画）。 */
@Composable
private fun HomeHeroProgressBar(progress: Float, color: Color, reduceMotion: Boolean) {
    val clamped = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = if (reduceMotion) snap() else CtMotion.spatialFast,
        label = "homeHeroProgress",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.24f))
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(color.copy(alpha = 0.95f)),
        )
    }
}

/** hero 小胶囊（今日 / 周次 / 上课中…）。 */
@Composable
private fun HeroChip(text: String, tint: Color) {
    Surface(
        shape = CircleShape,
        color = tint.copy(alpha = 0.18f),
        contentColor = tint,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

/** 半透明头像（xiaoying 重叠头像的轻量版：首字 + 圆形玻璃底）。 */
@Composable
private fun HomeAvatar(initial: String, tint: Color) {
    Surface(
        shape = CircleShape,
        color = tint.copy(alpha = 0.20f),
        contentColor = tint,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.28f)),
    ) {
        Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

/**
 * 首次进入错峰淡入（Hero → 今日课程 → 电费 → 作业 → 通知 → 常用工具）。
 *
 * - 每块延迟 [index] × 55ms、淡入 300ms + 上移 18dp（spring）；
 * - [enabled] = false（系统「移除动画」/ 预览渲染）时直接呈现，无动画、无延迟；
 * - 仅首帧播放一次，数据刷新/重组不重播（remember 状态在组合内存活）。
 */
@Composable
internal fun HomeReveal(enabled: Boolean, index: Int, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtLeast(0) * 55L)
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = CtMotion.fadeIn,
        label = "homeRevealAlpha",
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 18f,
        animationSpec = CtMotion.spatial,
        label = "homeRevealOffset",
    )
    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            translationY = offsetY
        },
    ) {
        content()
    }
}

/**
 * 信息卡统一外壳（v5）：玻璃底 + 分类色图标气泡 + 标题 + 可选徽标 + 尾部动作。
 * 内容区保持各卡自己的状态逻辑（loading/empty/error 行内展示）。
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
    CtGlassCard(
        modifier = modifier,
        onClick = onClick,
        shape = HomeCardShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HomeIconBadge(icon = icon, accent = accent)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!badge.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    CtPill(
                        text = badge,
                        containerColor = accent.copy(alpha = 0.14f),
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

/** 分类色图标气泡（40dp，柔和底 + 强调色图标）。 */
@Composable
internal fun HomeIconBadge(
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Int = 40,
    iconSize: Int = 20,
) {
    Surface(
        modifier = modifier.size(size.dp),
        shape = CtShapeTokens.iconTile,
        color = accent.copy(alpha = 0.14f),
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
 * 快捷工具行单格（xiaoying 四图标行）：图标气泡 + 名称，弹簧按压收缩 + 底色加深。
 * 点按整格打开工具；无选中概念（选中态在编辑网格里用勾选表达）。
 */
@Composable
internal fun QuickToolTile(
    tool: AppTool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = if (reduceMotion) snap() else CtMotion.spatialFast,
        label = "quickToolScale",
    )
    val tintAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.30f else 0.16f,
        animationSpec = if (reduceMotion) snap() else CtMotion.effectsFast,
        label = "quickToolTint",
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
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        interactionSource = interaction,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = tool.accent.copy(alpha = tintAlpha),
                contentColor = tool.accent,
            ) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        tint = tool.accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Text(
                text = tool.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 常用工具编辑模式单格：点击加入/移出（逻辑由调用方 [onClick] 保持原样）。
 * 选中 = 强调色描边 + 浅色底 + 右上角勾选；按压缩放走 spring。
 */
@Composable
internal fun EditableToolTile(
    tool: AppTool,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = if (reduceMotion) snap() else CtMotion.spatialFast,
        label = "editToolScale",
    )
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }
    Box(modifier = modifier.height(104.dp)) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            shape = RoundedCornerShape(18.dp),
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f)
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
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
                    shape = RoundedCornerShape(13.dp),
                    color = tool.accent.copy(alpha = 0.16f),
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
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "已选",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(18.dp),
            )
        }
    }
}
