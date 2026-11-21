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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furina.classtools.data.background.CtBackgroundSelection
import com.furina.classtools.data.background.CtBackgroundPalette
import com.furina.classtools.data.background.LocalBackgroundStore
import com.furina.classtools.ui.components.background.CtBackgroundLayer
import com.furina.classtools.ui.components.background.LocalCtBackgroundCardBoost
import com.furina.classtools.ui.components.background.asComposeImageBitmap
import com.furina.classtools.ui.theme.CtText
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import com.furina.classtools.ui.theme.rememberHarmonyColors

/**
 * 页面统一背景（2026-11-19 沉浸光感 + 自定义背景）：**全局唯一背景入口**。
 *
 * 三层：
 * 1) 自定义 / 预设背景（[CtBackgroundLayer]）：底色或渐变、或用户照片（背景可模糊、前景绝不模糊）；
 * 2) 光感柔光斑（预设背景时叠加，强度随「我的 → 外观 → 沉浸光感」）；
 * 3) 向子树注入「卡片玻璃补偿」[LocalCtBackgroundCardBoost]（背景越花 → 卡片底越实，正文才能 AA）。
 *
 * 所有页面（含工具页/详情/播放器外层）都经此渲染，因此自定义背景**全局生效**；
 * 预览（`LocalInspectionMode`）不读本机存储，固定用默认暖白，避免预览触碰磁盘。
 *
 * @param baseColorOverride 覆盖背景底色（媒体/播放器页强制深底时使用；null = 用全局背景）
 */
@Composable
fun ExpressiveBackground(
    modifier: Modifier = Modifier,
    baseColorOverride: androidx.compose.ui.graphics.Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = LocalCtDarkTheme.current
    val inspection = LocalInspectionMode.current
    val store = LocalBackgroundStore.current
    val selectionState = if (store != null && !inspection) {
        store.selection.collectAsStateWithLifecycle()
    } else {
        null
    }
    val imageState = if (store != null && !inspection) {
        store.bitmap.collectAsStateWithLifecycle()
    } else {
        null
    }
    val selection = selectionState?.value ?: CtBackgroundSelection.DEFAULT
    val palette = remember(selection, dark) { CtBackgroundPalette.of(selection.preset, dark) }
    val boost = if (selection.hasImage) palette.cardAlphaBoost + 0.18f else palette.cardAlphaBoost
    Box(modifier = modifier.fillMaxSize()) {
        if (baseColorOverride != null) {
            Box(modifier = Modifier.fillMaxSize().background(baseColorOverride))
        } else {
            // 只有「当前确实在用自定义图片」时才把位图交给背景层；
            // 否则切回预设后 bitmap 流仍持有旧照片，预设背景看起来"失效"（用户反馈的根因）。
            val rawBitmap = imageState?.value
            val image = if (selection.hasImage && rawBitmap != null) {
                remember(rawBitmap) { rawBitmap.asComposeImageBitmap() }
            } else {
                null
            }
            CtBackgroundLayer(
                modifier = Modifier.fillMaxSize(),
                selection = selection,
                image = image,
            )
        }
        CompositionLocalProvider(LocalCtBackgroundCardBoost provides boost) {
            // 背景层之上：前景内容（一律锐利，不受背景模糊影响）
            Box(modifier = Modifier.fillMaxSize()) { content() }
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

