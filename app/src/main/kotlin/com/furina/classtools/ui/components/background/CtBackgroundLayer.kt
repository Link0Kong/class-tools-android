package com.furina.classtools.ui.components.background

import android.app.ActivityManager
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furina.classtools.data.background.CtBackgroundPalette
import com.furina.classtools.data.background.CtBackgroundSelection
import com.furina.classtools.data.background.LocalBackgroundStore
import com.furina.classtools.data.background.ctBackgroundBlurSigma
import com.furina.classtools.ui.theme.CtLightFields
import com.furina.classtools.ui.components.glass.LocalCtHazeState
import com.furina.classtools.ui.theme.LocalCtColors
import com.furina.classtools.ui.theme.layerOver
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import com.furina.classtools.ui.theme.LocalCtLight
import com.furina.classtools.ui.theme.ctBackgroundBlurCapabilityOf

/** 每个窗口绘制一次背景，前景和系统栏共用同一套可读性处理。 */
@Composable
internal fun CtAppBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val store = LocalBackgroundStore.current
    val selection = store?.selection?.collectAsStateWithLifecycle()?.value ?: CtBackgroundSelection.DEFAULT
    val bitmap = store?.bitmap?.collectAsStateWithLifecycle()?.value
    val photo = remember(selection.hasImage, bitmap) {
        bitmap?.takeIf { selection.hasImage }?.asImageBitmap()
    }
    val dark = LocalCtDarkTheme.current
    val light = LocalCtLight.current
    val scheme = MaterialTheme.colorScheme
    val colors = LocalCtColors.current
    val background = scheme.background
    val palette = remember(selection.preset, dark) { CtBackgroundPalette.of(selection.preset, dark) }
    val boost = palette.cardAlphaBoost + if (photo != null) 0.18f else 0f
    val start = palette.base.layerOver(background, 0.25f)
    val end = palette.second.layerOver(background, 0.25f)
    val worstPhotoPixel = if (dark && photo != null && bitmap != null) key(bitmap) {
        val bound by produceState(Color.White, bitmap) {
            value = withContext(Dispatchers.Default) { bitmap.upperColorBound() }
        }
        bound
    } else null
    val veil = remember(dark, photo, scheme, colors, start, end, worstPhotoPixel) {
        if (dark) darkBackdropAlpha(worstPhotoPixel ?: upperColorBound(start, end), scheme, colors)
        else lightBackdropVeilAlpha(photo != null)
    }
    val haze = if (dark && photo != null && light.blurAvailable) remember { HazeState() } else null
    Box(modifier.fillMaxSize().background(background)) {
        Box(Modifier.fillMaxSize().then(if (haze != null) Modifier.hazeSource(haze) else Modifier)) {
            if (photo != null) {
                val view = LocalView.current
                val context = LocalContext.current
                val lowRam = remember(context) { context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true }
                val capability = ctBackgroundBlurCapabilityOf(Build.VERSION.SDK_INT, view.isHardwareAccelerated && !lowRam)
                val blur = with(LocalDensity.current) {
                    ctBackgroundBlurSigma(selection, capability).dp.toPx()
                }
                Image(
                    bitmap = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().then(
                        if (blur > 0f) Modifier.graphicsLayer {
                            clip = true
                            renderEffect = BlurEffect(blur, blur, TileMode.Clamp)
                        } else Modifier,
                    ),
                )
            } else if (dark) {
                if (palette.gradient) {
                    val brush = remember(start, end) { Brush.verticalGradient(listOf(start, end)) }
                    Box(Modifier.fillMaxSize().background(brush))
                } else Box(Modifier.fillMaxSize().background(start))
            } else {
                val field = remember(selection.preset, dark, light.setting) {
                    CtLightFields.forBackdrop(selection.preset, dark, light.setting)
                }
                CtAuroraField(field = field, dark = dark, animated = false)
            }
            // 遮罩全屏统一生效，模糊半径为零或设备不支持模糊时仍保持对比度。
            if (veil > 0f) Box(Modifier.fillMaxSize().background(background.copy(alpha = veil)))
        }
        CompositionLocalProvider(
            LocalCtRootAuroraActive provides true,
            LocalCtHazeState provides haze,
            LocalBackdropView provides LocalView.current,
            LocalCtBackgroundCardBoost provides boost,
            content = content,
        )
    }
}

internal fun lightBackdropVeilAlpha(hasPhoto: Boolean): Float = if (hasPhoto) 0.92f else 0.74f

/** Channel-wise bounds also hold after cropping, bilinear filtering and backdrop blur. */
private fun upperColorBound(a: Color, b: Color): Color =
    Color(maxOf(a.red, b.red), maxOf(a.green, b.green), maxOf(a.blue, b.blue))

private fun Bitmap.upperColorBound(): Color {
    val row = IntArray(width)
    var red = 0
    var green = 0
    var blue = 0
    for (y in 0 until height) {
        getPixels(row, 0, width, 0, y, width, 1)
        for (pixel in row) {
            red = maxOf(red, (pixel ushr 16) and 255)
            green = maxOf(green, (pixel ushr 8) and 255)
            blue = maxOf(blue, pixel and 255)
        }
        if (red == 255 && green == 255 && blue == 255) break
    }
    return Color(red, green, blue)
}
