package com.furina.classtools.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
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
import com.furina.classtools.ui.theme.CtText
import com.furina.classtools.ui.theme.rememberHarmonyColors

/**
 * 页面统一背景（2026-11-17 鸿蒙对齐）：**纯色底 `Tokens.BG #F7F8F8`（深色对应中性深底）**。
 *
 * 鸿蒙端没有渐变/玻璃光斑/噪点装饰；Android 之前 v6 的「顶光+径向色块+噪点」属于重装饰，已移除。
 * 该函数是唯一背景入口，所有页面自动变干净。
 */
@Composable
fun ExpressiveBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = rememberHarmonyColors()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        content()
    }
}

/**
 * 分组小标题（对齐鸿蒙 SectionHeader）：左侧标题 19sp Medium + 右侧可选文字操作（13sp 品牌雾青）。
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
            style = CtText.heading,
            color = colors.textPrimary,
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
    iconSize: Dp = 40.dp,
) {
    val colors = rememberHarmonyColors()
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
            tint = colors.textTertiary,
            modifier = Modifier.size(iconSize),
        )
        Text(
            text = title,
            style = CtText.bodyMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = CtText.caption,
            color = colors.textTertiary,
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
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = CtText.caption,
            color = colors.textTertiary,
        )
    }
}
