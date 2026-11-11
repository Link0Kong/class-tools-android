package com.furina.classtools.ui.screens.ppt

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import com.furina.classtools.data.ppt.PptImageLoader

/**
 * 小图加载辅助：列表封面 / 缩略图抽屉（播放器主图有自己的预加载状态管理）。
 *
 * 图片加载失败 → [fallback]（不白屏、不阻塞点击）。
 */
@Composable
fun PptAsyncImage(
    url: String?,
    loader: PptImageLoader,
    modifier: Modifier = Modifier,
    targetWidth: Int = PptImageLoader.THUMB_TARGET_WIDTH,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit = {},
) {
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url, loader) {
        image = if (url.isNullOrBlank()) null else loader.load(url, targetWidth)
    }
    val current = image
    if (current != null) {
        Image(
            painter = remember(current) { BitmapPainter(current) },
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier) { fallback() }
    }
}

/** 列表封面占位（无封面/加载中/失败统一用同一种暖白卡片，避免跳动）。 */
@Composable
fun PptCoverFallback(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize())
}
