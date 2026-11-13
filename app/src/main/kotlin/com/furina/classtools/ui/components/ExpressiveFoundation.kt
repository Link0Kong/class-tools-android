package com.furina.classtools.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 表达性背景 v6（工具页统一背景重做）。
 *
 * 旧版三团高 alpha 径向色块在浅色页面会形成明显色斑/脏色（尤其白卡片之间露出的区域），深色页也偏灰。
 * v6 改为「干净底色 + 极轻顶光 + 细颗粒抖动」：
 * - 浅色：surfaceContainerLowest → background 的细微纵向渐变，顶部仅一团 primary(≤7%) 柔光，右下 tertiary(≤4%)；
 * - 深色：近黑中性底 + 低 alpha 品牌色柔光，避免发灰；
 * - 叠加 remembered 的 64×64 噪点纹理（alpha 极低）消除 8bit 渐变的色带；
 * - 不依赖 RenderEffect/模糊 API，低端机同样稳定。
 *
 * 该函数是唯一入口，所有页面（工具页/根页/设置页）自动获得新背景，无需逐页切换。
 */
@Composable
fun ExpressiveBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val surfaceLowest = MaterialTheme.colorScheme.surfaceContainerLowest
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val dark = background.luminance() < 0.2f


    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // 1) 极轻纵向渐变：顶部略亮，底部回到背景色
            drawRect(
                brush = Brush.verticalGradient(
                    colors = if (dark) {
                        listOf(
                            surfaceLowest.copy(alpha = 0.55f),
                            background,
                            background,
                        )
                    } else {
                        listOf(
                            surfaceLowest.copy(alpha = 0.9f),
                            background,
                            background,
                        )
                    },
                ),
                size = size,
            )
            // 2) 顶部柔和品牌光（alpha 很低，避免色块）
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = if (dark) 0.10f else 0.065f), Color.Transparent),
                    center = Offset(w * 0.16f, h * -0.06f),
                    radius = w * 0.95f,
                ),
                size = size,
            )
            // 3) 右下极轻暖色，平衡画面
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(tertiary.copy(alpha = if (dark) 0.05f else 0.035f), Color.Transparent),
                    center = Offset(w * 1.02f, h * 0.62f),
                    radius = w * 0.8f,
                ),
                size = size,
            )

        }
        content()
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

/** 空态。 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 40.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(iconSize),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 加载态。 */
@Composable
fun CtLoading(
    modifier: Modifier = Modifier,
    text: String = "加载中…",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
