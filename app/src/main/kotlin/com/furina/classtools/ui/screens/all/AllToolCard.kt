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
 * 「全部工具」卡片 v6：与微信小程序 `pages/all` 的 `.tool-cell__in` 同尺寸/同结构
 * （横向：图标 chip + 名称/副标题两行，min-height ≈ 72dp，2 列 48.6% 宽）。
 *
 * 只做视觉尺寸对齐；点击/长按拖动/转场锚点/读屏语义由调用方保持原逻辑。
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
            .heightIn(min = 72.dp)
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
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
