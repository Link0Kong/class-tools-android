package com.furina.classtools.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.CtText
import com.furina.classtools.ui.theme.HarmonyTokens
import com.furina.classtools.ui.theme.rememberHarmonyColors

/**
 * 工具页统一顶栏（2026-11-19 逐项对齐鸿蒙 `PageHeader.ets` headerBar）：
 *
 * - 高度 **56dp**（鸿蒙 headerBar height 56）、标题 **19sp Bold**（鸿蒙 `F_HEADING` + Bold）、
 *   返回 **44dp 热区**（鸿蒙 38vp 视觉，按安卓无障碍硬线抬到 44）、右侧动作保持调用方；
 * - 背景透明（页面纯色底），副标题参数保留兼容但不再渲染（鸿蒙 PageHeader 只有标题+动作）。
 *
 * 旧调用方无需改写签名；需要「右侧文字操作」的页面在 `actions` 里传 TextButton/Text 即可。
 */
@Composable
fun CtTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    compact: Boolean = true,
) {
    val colors = rememberHarmonyColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HarmonyTokens.PageHeaderHeight)
            .padding(start = 4.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Surface(
                onClick = onBack,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = Color.Transparent,
                contentColor = colors.textPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.width(44.dp))
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = title,
            style = CtText.heading.copy(fontWeight = FontWeight.Bold),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            actions()
        }
    }
}

/** 圆形图标按钮（保留旧 API；鸿蒙风为无底色 44dp 图标按钮）。 */
@Composable
fun CtIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberHarmonyColors()
    Surface(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = colors.textSecondary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
        }
    }
}

/** 顶部品牌条（身份页/启动页用）：图标 + 站名 + 右侧动作。 */
@Composable
fun BrandBar(
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val colors = rememberHarmonyColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BrandMark(size = 28.dp)
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = "班级工具站",
                style = CtText.bodyMedium,
                color = colors.textPrimary,
            )
        }
        actions()
    }
}

/** 小胶囊标签（鸿蒙 pill：品牌雾青 12% 底 + 雾青文字）。 */
@Composable
fun CtPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = harmonyTintLocal(),
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            style = CtText.caption,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun harmonyTintLocal(): Color {
    val colors = rememberHarmonyColors()
    return if (colors.dark) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        com.furina.classtools.ui.theme.HarmonyTokens.TintTeal
    }
}
