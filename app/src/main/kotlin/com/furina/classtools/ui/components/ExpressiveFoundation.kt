package com.furina.classtools.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.components.background.CtAppBackdrop
import com.furina.classtools.ui.components.background.LocalCtRootAuroraActive
import com.furina.classtools.ui.theme.CtText
import com.furina.classtools.ui.theme.rememberHarmonyColors

/** 使用窗口的背景，媒体页可指定自己的实色底。 */
@Composable
fun ExpressiveBackground(
    modifier: Modifier = Modifier,
    baseColorOverride: androidx.compose.ui.graphics.Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    if (baseColorOverride != null || LocalCtRootAuroraActive.current) {
        val background = if (baseColorOverride == null) Modifier else Modifier.background(baseColorOverride)
        Box(modifier.fillMaxSize().then(background), content = content)
    } else {
        CtAppBackdrop(modifier) {
            Box(Modifier.fillMaxSize(), content = content)
        }
    }
}

/**
 * 分组小标题：Title Large（22sp / 400，MD3 分区标题）+ 右侧可选文字操作（Label Large）。
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = rememberHarmonyColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

/** 空态（鸿蒙卡片语言的居中空态：图标 40、标题 15、说明 12）。 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 48.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
            color = MaterialTheme.colorScheme.onSurface,
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

/** 加载态（鸿蒙卡片语言：小号进度环 + 12sp 说明，居中）。 */
@Composable
fun CtLoading(
    modifier: Modifier = Modifier,
    text: String = "加载中…",
) {
    val colors = rememberHarmonyColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 2026-11-20：改用 M3 Expressive 形变指示器
        // （7 形状连续形变 + 每次 90° 自转 + 4666ms 匀速自转；官方 LoadingIndicator 的等价实现）。
        com.furina.classtools.ui.components.expressive.CtLoadingIndicator(
            size = com.furina.classtools.ui.components.expressive.CtLoadingIndicatorSize.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = CtText.caption,
            color = colors.textTertiary,
        )
    }
}


