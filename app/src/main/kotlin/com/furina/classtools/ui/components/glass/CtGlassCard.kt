package com.furina.classtools.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.HarmonyTokens
import com.furina.classtools.ui.theme.rememberHarmonyColors

/**
 * 鸿蒙风格白卡（2026-11-17 重做；名字保留 `CtGlassCard` 以免大面积改调用方）。
 *
 * 与鸿蒙 ToolTile/HomeScreen 卡片一致：
 * - 纯色卡片底（浅色 #FFFFFF / 深色中性 surface），**不再有玻璃半透明、描边高光、渐变**；
 * - 18dp 圆角 + 轻阴影（radius 18 / y6 / 低 alpha 墨色，Compose 用 2dp elevation 近似）；
 * - 无 1dp 描边（鸿蒙卡片靠底色+阴影分层）。
 *
 * 用法不变：`CtGlassCard(onClick = ...) { content() }`。
 */
@Composable
fun CtGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = CtShapeTokens.toolCard,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = rememberHarmonyColors()
    val cardModifier = modifier
        .fillMaxWidth()
        .shadow(
            elevation = HarmonyTokens.CardShadowElevation,
            shape = shape,
            clip = false,
            ambientColor = HarmonyTokens.CardShadowColor,
            spotColor = HarmonyTokens.CardShadowColor,
        )
        .clip(shape)
        .background(colors.card)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = cardModifier,
            shape = shape,
            color = colors.card,
            contentColor = colors.textPrimary,
            shadowElevation = 0.dp,
        ) {
            Box { content() }
        }
    } else {
        Surface(
            modifier = cardModifier,
            shape = shape,
            color = colors.card,
            contentColor = colors.textPrimary,
            shadowElevation = 0.dp,
        ) {
            Box { content() }
        }
    }
}

/** 兼容旧调用：圆角 18dp 的卡片形状。 */
val CtHarmonyCardShape: Shape = RoundedCornerShape(HarmonyTokens.RadiusCard)

/** 兼容 Material 主题色引用的占位（旧代码可能引用 glass 命名空间下的少量常量）。 */
@Composable
internal fun harmonyCardContentColor() = MaterialTheme.colorScheme.onSurface
