package com.furina.classtools.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.ui.theme.CtAnimatedEntrance
import com.furina.classtools.ui.theme.CtElevation
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.CtSize
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.CtTypeScale
import com.furina.classtools.ui.theme.ctPressFeedback
import com.furina.classtools.ui.theme.harmonyTint
import com.furina.classtools.ui.theme.rememberCtColors
import com.furina.classtools.ui.theme.rememberReduceMotion
import com.furina.classtools.ui.theme.uiAccentOf

/**
 * 通用工具卡片（纵向大卡：图标块 + 名称 + 副标题）。
 *
 * 视觉（统一设计规范 §3.2「卡片」）：
 *   surfaceContainerLow 底 + 1dp outlineVariant 描边 + 20dp 圆角 + 极浅投影；
 *   40dp 淡色图标块（12dp 圆角） + 名称 16 Medium + 副标题 12 次级色。
 * 动效：按压 scale 0.97（spring 0.75/600）+ 8% tonal 按压层；
 *   可选 [entranceIndex] 列表错峰入场（fade + 上移 8dp，每项 40ms）。
 *
 * @param entranceIndex 非 null 时按 index 播放错峰入场
 */
@Composable
fun ToolCard(
    tool: AppTool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    entranceIndex: Int? = null,
    contentSemantics: Modifier = Modifier,
) {
    val c = rememberCtColors()
    val accent = uiAccentOf(tool)
    val reduceMotion = rememberReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val press = ctPressFeedback(interaction, reduceMotion)
    val body: @Composable () -> Unit = {
        Surface(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = press.scale
                    scaleY = press.scale
                },
            shape = CtShape.card,
            color = c.containerLow,
            contentColor = c.textPrimary,
            border = BorderStroke(CtSize.dividerThickness, c.divider),
            shadowElevation = if (c.dark) CtElevation.none else CtElevation.card,
            interactionSource = interaction,
        ) {
            Box(modifier = Modifier.fillMaxSize().then(contentSemantics)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(CtSpace.cardPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CtSpace.md),
                ) {
                    Surface(
                        shape = CtShape.tile,
                        color = harmonyTint(accent.tintRef),
                        contentColor = accent.textRef,
                    ) {
                        Box(
                            modifier = Modifier.size(CtSize.glyphBox),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = null,
                                tint = accent.tintRef,
                                modifier = Modifier.size(CtSize.iconLarge),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(CtSpace.xxs),
                    ) {
                        Text(
                            text = tool.name,
                            style = CtTypeScale.title16,
                            color = c.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = tool.subtitle,
                            style = CtTypeScale.caption,
                            color = c.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // 按压层（M3 state layer）：盖在内容上但不拦截点击。
                if (press.overlayAlpha > 0.001f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = press.overlayAlpha },
                    )
                }
            }
        }
    }
    if (entranceIndex != null) {
        CtAnimatedEntrance(enabled = true, index = entranceIndex) { body() }
    } else {
        body()
    }
}

/** 常用工具的小横条（胶囊：图标 + 名称）。 */
@Composable
fun ToolRowItem(
    tool: AppTool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = rememberCtColors()
    val accent = uiAccentOf(tool)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CtShape.pill,
        color = c.container,
        contentColor = c.textPrimary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CtSpace.md, vertical = CtSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CtSpace.sm),
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                tint = accent.textRef,
                modifier = Modifier.size(CtSize.iconSmall),
            )
            Text(text = tool.name, style = CtTypeScale.label, color = c.textPrimary)
        }
    }
}
