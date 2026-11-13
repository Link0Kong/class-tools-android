package com.furina.classtools.ui.screens.all

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.ui.navigation.ctToolTransitionSource
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.LocalCtDarkTheme

/**
 * 「全部工具」v5 卡片：分类色点缀 + 图标气泡 + 主/副标题 + 按压缩放。
 *
 * 视觉参照 HFUT-Schedule 的「查询中心」两列功能卡与 xiaoying 的浅色圆角卡：
 * - 底色用 surfaceContainerLow + 顶部类别色极淡渐变（不改变深色对比度）；
 * - 图标气泡 / 类别标签使用工具 accent（分类色点缀）；
 * - 按下 scale 0.95 + 圆角 20→16dp，松手 spring 回弹（CtMotion.spatialFast）。
 *
 * 语义：外层 [Surface] 为 clickable；[contentSemantics] 由调用方传入拖动排序语义
 * （`Modifier.reorderableSemantics`），合并进同一读屏焦点（与旧 ToolCard 行为一致）。
 */
@Composable
internal fun AllToolCard(
    tool: AppTool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentSemantics: Modifier = Modifier,
    pressable: Boolean = true,
    reduceMotion: Boolean = false,
) {
    val dark = LocalCtDarkTheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed && pressable) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else CtMotion.spatialFast,
        label = "allToolPress",
    )
    val scale = 1f - 0.05f * pressProgress
    val cornerDp = 20f + (16f - 20f) * pressProgress
    val shape = RoundedCornerShape(cornerDp.dp)

    val transitionSource = Modifier.ctToolTransitionSource(tool.id)
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxSize()
            .then(transitionSource)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        interactionSource = interaction,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(contentSemantics)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            tool.accent.copy(alpha = if (dark) 0.13f else 0.10f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AllToolIconBadge(tool = tool)
                    Spacer(modifier = Modifier.weight(1f))
                    ToolCategoryTag(tool = tool)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = tool.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = tool.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 工具图标气泡（44dp，分类色柔和底 + 图标）。 */
@Composable
private fun AllToolIconBadge(tool: AppTool) {
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = tool.accent.copy(alpha = 0.17f),
        contentColor = tool.accent,
    ) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                tint = tool.accent,
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

/** 类别点缀（小圆点 + 类别名，弱化处理，不与标题抢层级）。 */
@Composable
private fun ToolCategoryTag(tool: AppTool) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(tool.accent, CircleShape),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = tool.category.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
