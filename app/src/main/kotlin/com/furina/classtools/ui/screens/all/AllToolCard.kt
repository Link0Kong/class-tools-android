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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.ui.navigation.ctToolTransitionSource
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.rememberReduceMotion

/**
 * 「全部工具」卡片 v7：横向结构（图标 chip + 名称/副标题）不变，副标题最多 **2 行**，
 * 网格卡高由调用方的 `itemHeight`（96dp）固定；2 列 48.6% 宽。
 *
 * task-58：副标单行会被截断成「公平随机 · 可导…」，改为 2 行后常见描述可完整显示
 * （超长仍 ellipsis）；按压缩放（0.96）/16dp 圆角/转场锚点/读屏语义保持不变。
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed && pressable) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else CtMotion.spatialFast,
        label = "allToolPress",
    )
    val scale = 1f - 0.04f * pressProgress
    val cornerDp = 16f + (14f - 16f) * pressProgress
    val effectiveReduceMotion = reduceMotion || rememberReduceMotion()
    val shape = RoundedCornerShape(cornerDp.dp)
    val transitionSource = Modifier.ctToolTransitionSource(tool.id)

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            // 网格由调用方以固定 96dp 测量；此处 min 只兜底预览/独立使用，避免两行副标被压扁。
            .heightIn(min = 88.dp)
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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .then(contentSemantics)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 与 mp 一致的 40dp 彩色圆角 chip（mp：72rpx ≈ 39dp）。
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = tool.accent.copy(alpha = if (effectiveReduceMotion) 0.17f else 0.17f),
                contentColor = tool.accent,
            ) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        tint = tool.accent,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tool.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // task-58：常见描述（如「公平随机 · 可导出」）2 行内不折叠；超长仍省略。
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
