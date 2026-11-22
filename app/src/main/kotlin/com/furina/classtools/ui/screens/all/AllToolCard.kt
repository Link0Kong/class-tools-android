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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.ui.navigation.ctToolTransitionSource
import com.furina.classtools.ui.components.background.CtGlassSurface
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.components.background.CtGlassSurface
import com.furina.classtools.ui.theme.CtText
import com.furina.classtools.ui.components.background.CtGlassSurface
import com.furina.classtools.ui.theme.HarmonyMotion
import com.furina.classtools.ui.components.background.CtGlassSurface
import com.furina.classtools.ui.theme.HarmonyTokens
import com.furina.classtools.ui.components.background.CtGlassSurface
import com.furina.classtools.ui.theme.harmonyTint
import com.furina.classtools.ui.components.background.CtGlassSurface
import com.furina.classtools.ui.theme.uiAccentOf
import com.furina.classtools.ui.components.background.CtGlassSurface
import com.furina.classtools.ui.theme.rememberHarmonyColors
import com.furina.classtools.ui.components.background.CtGlassSurface
import com.furina.classtools.ui.theme.rememberReduceMotion

/**
 * 「全部工具」磁贴（2026-11-19 逐项对齐鸿蒙 `ToolTile.ets`）：
 *
 * - 白色（深色 surface）18dp 圆角 + 轻阴影，最小高度 **96dp**、内边距 **12dp**；
 * - 左侧 **40dp 淡色 tint 图标块（12dp 圆角）**：图标 **24dp**（鸿蒙 fontSize 24）；
 * - 图标块与文字间距 **12dp**（鸿蒙 `margin({left: GAP})`）、主次文本间距 **2dp**（鸿蒙 `TEXT_GAP`）；
 * - 名称 **15sp Medium**、副标题 **12sp 最多 2 行（行高 17）**；
 * - 按压 **scale 0.97 + opacity 0.92**（鸿蒙 `stateStyles.pressed`），快 160ms；
 * - 编辑首页模式：右上角 22dp 雾青 ✓ 角标（选中首页快捷时显示）。
 *
 * 转场锚点 / 读屏语义 / 拖动能力保持不变（参数向后兼容，预览无需改调用）。
 */
@Composable
internal fun AllToolCard(
    tool: AppTool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentSemantics: Modifier = Modifier,
    pressable: Boolean = true,
    reduceMotion: Boolean = false,
    editing: Boolean = false,
    selected: Boolean = false,
) {
    val colors = rememberHarmonyColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed && pressable) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else CtMotion.spatialFast,
        label = "allToolPress",
    )
    val scale = 1f - (1f - HarmonyMotion.PressScale) * pressProgress
    val alpha = 1f - (1f - HarmonyMotion.PressAlpha) * pressProgress
    val transitionSource = Modifier.ctToolTransitionSource(tool.id, HarmonyTokens.RadiusCard)
    val shape = RoundedCornerShape(HarmonyTokens.RadiusCard)

    Box {
        // 磨砂玻璃卡片（统一材质来源 CtGlassSurface）：半透明底 + 顶部高光 + 1px 高光边。
        CtGlassSurface(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = HarmonyTokens.TileMinHeight)
                .then(transitionSource)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
            shape = shape,
            onClick = onClick,
            frosted = true,
            body = true,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .then(contentSemantics)
                    .padding(HarmonyTokens.TilePadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 40dp Monet 淡色底图标块（网页同款 tint）
                val monet = com.furina.classtools.ui.theme.monetToneOf(tool)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = monet.container,
                    contentColor = monet.content,
                ) {
                    Box(modifier = Modifier.size(HarmonyTokens.GlyphBox), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = monet.content,
                            modifier = Modifier.size(HarmonyTokens.TileGlyph),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(HarmonyTokens.TileTextGap))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(HarmonyTokens.TextGap),
                ) {
                    Text(
                        text = tool.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = tool.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (editing && selected) {
            Surface(
                shape = CircleShape,
                color = MaterialThemePrimary(),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "已加入首页快捷",
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MaterialThemePrimary() = androidx.compose.material3.MaterialTheme.colorScheme.primary


