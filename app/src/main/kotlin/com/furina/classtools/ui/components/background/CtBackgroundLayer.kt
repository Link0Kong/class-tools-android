package com.furina.classtools.ui.components.background

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furina.classtools.data.background.CtBackgroundPalette
import com.furina.classtools.data.background.CtBackgroundSelection
import com.furina.classtools.data.background.LocalBackgroundStore
import com.furina.classtools.data.background.ctBackgroundBlurSigma
import com.furina.classtools.ui.theme.CtLightFields
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
    val background = MaterialTheme.colorScheme.background
    val boost = CtBackgroundPalette.of(selection.preset, dark).cardAlphaBoost +
        if (photo != null) 0.18f else 0f

    Box(modifier.fillMaxSize().background(background)) {
        if (photo != null) {
            val view = LocalView.current
            val capability = remember(view) {
                ctBackgroundBlurCapabilityOf(Build.VERSION.SDK_INT, view.isHardwareAccelerated)
            }
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
        } else {
            val field = remember(selection.preset, dark, light.setting) {
                CtLightFields.forBackdrop(selection.preset, dark, light.setting)
            }
            CtAuroraField(field = field, dark = dark, animated = false)
        }
        // 遮罩全屏统一生效，模糊半径为零或设备不支持模糊时仍保持对比度。
        Box(Modifier.fillMaxSize().background(background.copy(alpha = backdropVeilAlpha(dark, photo != null))))
        CompositionLocalProvider(
            LocalCtRootAuroraActive provides true,
            LocalCtBackgroundCardBoost provides boost,
            content = content,
        )
    }
}

internal fun backdropVeilAlpha(dark: Boolean, hasPhoto: Boolean): Float = when {
    hasPhoto -> if (dark) 0.88f else 0.92f
    dark -> 0.64f
    else -> 0.74f
}

