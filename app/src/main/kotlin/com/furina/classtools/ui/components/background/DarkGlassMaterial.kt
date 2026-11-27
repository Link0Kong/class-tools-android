package com.furina.classtools.ui.components.background

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import android.view.View
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.components.glass.LocalCtHazeState
import com.furina.classtools.ui.theme.CtColor
import com.furina.classtools.ui.theme.CtLightCapability
import com.furina.classtools.ui.theme.CtLightIntensity
import com.furina.classtools.ui.theme.CtLightSetting
import com.furina.classtools.ui.theme.LocalCtColors
import com.furina.classtools.ui.theme.LocalCtLight
import com.furina.classtools.ui.theme.layerOver
import com.furina.classtools.ui.theme.lucidContrastRatio
import com.furina.classtools.ui.theme.minimumPassingOpacity
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

internal val LocalBackdropView = staticCompositionLocalOf<View?> { null }

/** A fill and an independent background blur. The border and foreground stay opaque. */
internal data class DarkGlassMaterial(val fill: Color, val blur: Dp)

private fun glassGrade(intensity: CtLightIntensity): Pair<Float, Dp> = when (intensity) {
    CtLightIntensity.WEAK -> 0.96f to 12.dp
    CtLightIntensity.BALANCED -> 0.92f to 24.dp
    CtLightIntensity.STRONG -> 0.88f to 32.dp
}

/** Text button layouts keep their transparent fill. For paired button colors, also check the darkest pixel. */
internal fun darkActionFill(base: Color, ink: Color, primary: Boolean, setting: CtLightSetting, capability: CtLightCapability): Color {
    if (!setting.enabled || capability != CtLightCapability.FULL) return base
    val target = if (primary) 7.0 else 4.5
    val alpha = minimumPassingOpacity(glassGrade(setting.intensity).first) {
        val darkest = base.layerOver(Color.Black, it)
        val brightest = base.layerOver(Color.White, it)
        lucidContrastRatio(ink, ink.layerOver(darkest, 0.16f)) >= target &&
            lucidContrastRatio(ink, ink.layerOver(brightest, 0.16f)) >= target
    }
    return if (alpha >= 0.99f) base else base.copy(alpha = alpha)
}

@Composable
internal fun actionFill(base: Color, ink: Color, primary: Boolean): Color {
    if (!LocalCtColors.current.dark) return base
    val light = LocalCtLight.current
    return remember(base, ink, primary, light.setting, light.capability) {
        darkActionFill(base, ink, primary, light.setting, light.capability)
    }
}

private fun readableSurface(pixel: Color, scheme: ColorScheme, colors: CtColor): Boolean {
    val pressed = scheme.onSurface.layerOver(pixel, 0.16f)
    return lucidContrastRatio(colors.textPrimary, pixel) >= 7.0 &&
        lucidContrastRatio(colors.textSecondary, pixel) >= 6.0 &&
        lucidContrastRatio(colors.textTertiary, pixel) >= 5.0 &&
        lucidContrastRatio(colors.textTertiary, pressed) >= 4.5 &&
        lucidContrastRatio(colors.textSecondary, pressed) >= 4.5 &&
        lucidContrastRatio(colors.textPrimary, pressed) >= 4.5 &&
        lucidContrastRatio(scheme.primary, pressed) >= 4.5 &&
        lucidContrastRatio(scheme.secondary, pressed) >= 4.5 &&
        lucidContrastRatio(scheme.tertiary, pressed) >= 4.5 &&
        lucidContrastRatio(scheme.error, pressed) >= 4.5 &&
        lucidContrastRatio(scheme.error, pixel) >= 7.0 &&
        lucidContrastRatio(scheme.outline, pressed) >= 3.0
}

/** A white pixel bounds any photo or content scrolling below a floating surface. */
internal fun darkGlassMaterial(
    base: Color, setting: CtLightSetting, capability: CtLightCapability, scheme: ColorScheme, colors: CtColor,
): DarkGlassMaterial {
    if (!setting.enabled || capability != CtLightCapability.FULL) return DarkGlassMaterial(base, 0.dp)
    if (!readableSurface(base, scheme, colors)) return DarkGlassMaterial(scheme.surfaceContainerLow, 0.dp)
    val (opacity, blur) = glassGrade(setting.intensity)
    val alpha = minimumPassingOpacity(opacity) { readableSurface(base.layerOver(Color.White, it), scheme, colors) }
    return if (alpha >= 0.99f) DarkGlassMaterial(base, 0.dp) else DarkGlassMaterial(base.copy(alpha = alpha), blur)
}

/** The same bound protects text drawn directly on the window before its cards are composited. */
internal fun darkBackdropAlpha(worstPixel: Color, scheme: ColorScheme, colors: CtColor): Float =
    minimumPassingOpacity(0f) { readableSurface(scheme.background.layerOver(worstPixel, it), scheme, colors) }

@Composable
internal fun rememberDarkGlass(base: Color): DarkGlassMaterial {
    val light = LocalCtLight.current
    val scheme = MaterialTheme.colorScheme
    val colors = LocalCtColors.current
    return remember(base, light.setting, light.capability, scheme, colors) {
        darkGlassMaterial(base, light.setting, light.capability, scheme, colors)
    }
}

@Composable
internal fun Modifier.darkGlassBlur(shape: Shape, material: DarkGlassMaterial, source: HazeState? = LocalCtHazeState.current): Modifier {
    if (source == null || material.blur == 0.dp || LocalBackdropView.current !== LocalView.current) return this
    val background = MaterialTheme.colorScheme.background
    val style = remember(background, material.blur) {
        HazeStyle(backgroundColor = background, tint = HazeTint(Color.Transparent), blurRadius = material.blur, noiseFactor = 0f)
    }
    return clip(shape).hazeEffect(state = source, style = style) { blurEnabled = true }
}
