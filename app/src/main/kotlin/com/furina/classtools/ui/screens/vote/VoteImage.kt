package com.furina.classtools.ui.screens.vote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.furina.classtools.data.vote.VoteLimits
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 投票选项配图：相册 URI → 压缩 `data:image/jpeg;base64,...`（契约 §1.3）。
 *
 * 规格：长边 ≤640px、JPEG；单张 data URL ≤150000 字符、单投票全部配图合计 ≤1.6MB。
 * 全部在 IO 线程执行；失败返回 null（调用方提示「图片处理失败，请换一张」）。
 */
object VoteImageCompressor {

    const val MAX_EDGE_PX: Int = 640
    const val JPEG_PREFIX: String = "data:image/jpeg;base64,"

    /** 质量从高到低尝试，直到 base64 字符数（含前缀）不超过 [VoteLimits.MAX_OPTION_IMAGE_CHARS]。 */
    private val QUALITY_STEPS: List<Int> = listOf(84, 76, 68, 60, 52, 44, 36, 30)

    /** 读取相册 URI 并压缩成 data URL；失败/取消返回 null。 */
    suspend fun compressToDataUrl(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_EDGE_PX * 2)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@runCatching null
            compressBitmap(decoded)
        }.getOrNull()
    }

    /** 位图 → data URL（长边 640、逐步降质量）。公开便于预览/测试注入。 */
    fun compressBitmap(source: Bitmap): String? {
        val scaled = scaleToMaxEdge(source, MAX_EDGE_PX)
        val bytes = ByteArrayOutputStream()
        var dataUrl: String
        for (quality in QUALITY_STEPS) {
            bytes.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, bytes)
            dataUrl = JPEG_PREFIX + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
            if (dataUrl.length <= VoteLimits.MAX_OPTION_IMAGE_CHARS) {
                if (scaled !== source) scaled.recycle()
                return dataUrl
            }
        }
        if (scaled !== source) scaled.recycle()
        return null
    }

    fun decodeDataUrl(dataUrl: String?): Bitmap? {
        val raw = dataUrl?.trim().orEmpty()
        if (!raw.startsWith("data:image/")) return null
        val comma = raw.indexOf(',')
        if (comma <= 0 || comma == raw.lastIndex) return null
        val payload = raw.substring(comma + 1)
        return runCatching {
            val bytes = Base64.decode(payload, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun sampleSizeFor(width: Int, height: Int, targetMax: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= targetMax && height / (sample * 2) >= targetMax) {
            sample *= 2
        }
        return sample
    }

    private fun scaleToMaxEdge(source: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = maxOf(source.width, source.height)
        if (longEdge <= maxEdge) return source
        val ratio = maxEdge.toFloat() / longEdge.toFloat()
        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        val height = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }
}

/** 显示 data URL 配图（IO 解码；失败显示占位）。 */
@Composable
fun VoteDataUrlImage(
    dataUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, dataUrl) {
        value = if (dataUrl.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                VoteImageCompressor.decodeDataUrl(dataUrl)?.asImageBitmap()
            }
        }
    }
    Box(
        modifier = modifier.background(MaterialThemeContainerColor(),),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun MaterialThemeContainerColor(): Color =
    androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
