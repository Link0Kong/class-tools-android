package com.furina.classtools.ui.components.expressive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShapeTokens

/**
 * 浮动动作菜单中的一项（标签 + 图标 + 回调）。
 *
 * @param label 展示文案（如「导出图片」「复制文本」）
 * @param icon 前导图标
 * @param onClick 点击回调（菜单会自动收起）
 */
@Immutable
data class CtFabAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val containerColor: Color? = null,
    val contentColor: Color? = null,
)

/**
 * 自建表达性浮动动作菜单（stable 轨降级方案，对应 material3 1.5 的 FloatingActionButtonMenu）。
 *
 * 交互：
 * - 主 FAB 点击切换展开；展开时图标旋转 0° → 45°（[CtMotion.spatialFast]）；
 * - 动作项由下往上错峰出现（scale 0.8→1 + fade，间隔 20ms），收起反序淡出；
 * - 展开时背后有半透明遮罩，点击遮罩收起（无障碍：遮罩无点击语义，FAB 有描述）。
 * - 每个动作行高 48dp，满足最小触控目标；动作文案可见（读屏自读）。
 *
 * @param expanded 是否展开（受控）
 * @param onExpandedChange 展开态变化回调
 * @param actions 动作列表（建议 2–5 个）
 * @param mainIcon 主 FAB 图标（默认加号）
 * @param mainContentDescription 主 FAB 无障碍描述
 */
@Composable
fun CtFloatingActionMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: List<CtFabAction>,
    modifier: Modifier = Modifier,
    mainIcon: ImageVector = Icons.Rounded.Add,
    mainContentDescription: String = "更多操作",
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = CtMotion.spatialFast,
        label = "ctFabMenuRotation",
    )

    Box(modifier = modifier.fillMaxSize()) {
        // 遮罩：展开时淡入，点击收起。
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(durationMillis = 120)),
            exit = fadeOut(animationSpec = tween(durationMillis = 90)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onExpandedChange(false) },
                    ),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            actions.forEachIndexed { index, action ->
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(animationSpec = tween(80, delayMillis = index * 20)) +
                        scaleIn(
                            animationSpec = CtMotion.spatialFast,
                            initialScale = 0.8f,
                        ),
                    exit = fadeOut(animationSpec = tween(60)) +
                        scaleOut(
                            animationSpec = CtMotion.spatialFast,
                            targetScale = 0.8f,
                        ),
                ) {
                    CtFabActionRow(
                        action = action,
                        onClick = {
                            onExpandedChange(false)
                            action.onClick()
                        },
                    )
                }
            }

            FloatingActionButton(
                onClick = { onExpandedChange(!expanded) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = mainIcon,
                    contentDescription = mainContentDescription,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(rotation),
                )
            }
        }
    }
}

@Composable
private fun CtFabActionRow(
    action: CtFabAction,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CtRadius.cardCompact),
        color = action.containerColor ?: MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = action.contentColor ?: MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
