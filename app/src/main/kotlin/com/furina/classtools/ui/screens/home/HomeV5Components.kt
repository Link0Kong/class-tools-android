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
import com.furina.classtools.ui.theme.rememberCtHeroColors
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

/** 工作台 v5 hero 圆角（2026-11-16 改版：24dp，比旧 28dp 更轻）。 */
internal val HomeHeroShape = RoundedCornerShape(24.dp)

/**
 * 顶部「今日」hero（2026-11-16 轻量化改版）：日期/周次/角色一行 + 问候标题 + 今日课程摘要。
 *
 * 设计口径（用户反馈「最上面绿色卡片过于繁重」）：
 * - **降低高度**：单行 meta（去掉「今日」胶囊与多层装饰）、标题 22sp、课程块紧凑，整体约 150dp；
 * - **减少装饰**：只保留右上角一层极轻径向高光（删掉左下暗角/多余圆点），圆形头像缩到 34dp；
 * - **层级清楚**：日期/周次/角色（label）→ 问候 + 姓名（标题）→ 课程信息块（内容）；
 * - 颜色仍用品牌雾青（浅色 primary→secondary 深渐变 + 白字；深色中性 tonal），不引入外部资源；
 * - 深色模式、平板（父级限宽）沿用既有自适应。
 *
 * 数据来自 [HomeWorkbenchLogic.heroState]（纯逻辑可单测）；点击整卡打开课表。
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
    val metaText = remember(dateText, weekText, roleText) {
        listOf(dateText, weekText, roleText).filter { it.isNotBlank() }.joinToString(" · ")
    }

    val dark = LocalCtDarkTheme.current
    val heroColors = rememberCtHeroColors()
    val content = heroColors.content
    val accent = heroColors.accent

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
        border = BorderStroke(1.dp, heroColors.border),
        interactionSource = interaction,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(colors = listOf(heroColors.start, heroColors.end))),
        ) {
            // 单层极轻高光（右上）：保留品牌渐变的呼吸感，不再叠加左下暗角等装饰。
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            content.copy(alpha = if (dark) 0.06f else 0.10f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.92f, -size.height * 0.15f),
                        radius = size.width * 0.62f,
                    ),
                    size = size,
                )
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.labelMedium,
                        color = content.copy(alpha = 0.86f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    HomeAvatar(
                        initial = session.name.trim().take(1).ifBlank { "同" },
                        tint = accent,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$greeting，${session.name.trim().ifBlank { "同学" }}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(10.dp))
                HeroCourseBlock(
                    hero = hero,
                    contentColor = content,
                    chipTint = accent,
                    innerFill = heroColors.innerFill,
                    reduceMotion = reduceMotion,
                )
            }
        }
    }
}

/** hero 内课程区（上课中 / 下一节 / 空态 + 进度；紧凑版：padding/间距收紧、进度条 5dp）。 */
@Composable
private fun HeroCourseBlock(
    hero: HomeHeroState,
    contentColor: Color,
    chipTint: Color,
    innerFill: Color,
    reduceMotion: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(innerFill)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroChip(text = hero.tag, tint = chipTint)
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = hero.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (hero.detail.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
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
            Spacer(modifier = Modifier.height(6.dp))
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
            Spacer(modifier = Modifier.height(4.dp))
            HomeHeroProgressBar(progress = progress, color = chipTint, reduceMotion = reduceMotion)
        } else if (hero.hint.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
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
            .height(5.dp)
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

/** 半透明头像（轻量版：首字 + 圆形玻璃底；34dp，减少 hero 装饰体积）。 */
@Composable
private fun HomeAvatar(initial: String, tint: Color) {
    Surface(
        shape = CircleShape,
        color = tint.copy(alpha = 0.20f),
        contentColor = tint,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.28f)),
    ) {
        Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
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
