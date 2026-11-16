package com.furina.classtools.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 轻量顶栏 v6：圆形玻璃返回 + 标题（+ 可选副标题）+ 右侧动作胶囊。
 *
 * 不再铺满半透明底条：直接落在 [com.furina.classtools.ui.components.ExpressiveBackground] 的新背景上，
 * 与「我的」hero / 工作台 hero 的层级一致；滚动行为仍由各页面自己控制。
 *
 * [compact]（默认 true，工具页统一口径）：**单行 56dp 紧凑顶栏**——标题 22sp（titleLarge）、
 * 不显示副标题与多余装饰，把首屏垂直空间留给内容。需要大标题 + 副标题的页面显式传 `compact = false`。
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
    // 平板横屏（≥840dp）收紧左右/上下 padding（P2：大屏信息密度/留白）
    val widthDp = LocalConfiguration.current.screenWidthDp
    val tight = widthDp >= 840
    if (compact) {
        CompactTopBarRow(title = title, onBack = onBack, actions = actions, modifier = modifier)
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (tight) 56.dp else 60.dp)
            .padding(
                start = if (tight) 12.dp else 12.dp,
                end = if (tight) 12.dp else 12.dp,
                top = if (tight) 6.dp else 8.dp,
                bottom = if (tight) 4.dp else 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) {
            Surface(
                onClick = onBack,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 1.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        } else {
            Box(modifier = Modifier.size(4.dp))
        }

        androidx.compose.foundation.layout.Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // 读屏「按标题导航」可定位到页面标题。
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        actions()
    }
}

/**
 * 紧凑单行顶栏：44dp 圆形返回 + 22sp 单行标题 + 右侧动作，整体约 56dp。
 *
 * 不显示副标题（工具页首屏更轻）；标题过长省略，动作区保持原样（页面自己决定图标/文字按钮）。
 */
@Composable
private fun CompactTopBarRow(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 10.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            Surface(
                onClick = onBack,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 1.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        } else {
            Box(modifier = Modifier.size(4.dp))
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )

        actions()
    }
}

/** 圆形图标按钮（统一触控与视觉）。 */
@Composable
fun CtIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

/** 顶部品牌条：图标 + 站名 + 右侧动作。 */
@Composable
fun BrandBar(
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BrandMark(size = 34.dp)
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = "班级工具站",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        actions()
    }
}

/** 小胶囊标签。 */
@Composable
fun CtPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

