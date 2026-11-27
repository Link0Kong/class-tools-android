package com.furina.classtools.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.furina.classtools.data.background.CtBackgroundPreset

/** One resolution result feeds Material, LocalCtColors and the legacy Harmony adapters. */
internal data class CtResolvedColors(val scheme: ColorScheme, val semantic: CtColor)

internal fun Color.layerOver(base: Color, opacity: Float): Color =
    copy(alpha = opacity).compositeOver(base)

/** Smallest passing adjustment, tested after sRGB quantization. The upper endpoint must be safe. */
internal inline fun minimumPassingOpacity(start: Float, passes: (Float) -> Boolean): Float {
    if (passes(start)) return start
    var low = start
    var high = 1f
    repeat(10) {
        val mid = (low + high) / 2f
        if (passes(mid)) high = mid else low = mid
    }
    return high
}

internal fun adjustInk(color: Color, toward: Color, passes: (Color) -> Boolean): Color =
    toward.layerOver(color, minimumPassingOpacity(0f) { passes(toward.layerOver(color, it)) })

private fun presetTones(preset: CtBackgroundPreset): CtColor {
    val teal = CtColorDark
    val sand = CtDarkSand
    val blue = CtDarkBlue
    return when (preset) {
        CtBackgroundPreset.WARM_WHITE -> teal
        CtBackgroundPreset.MIST_TEAL -> teal.tintSurfaces(CtPalette.MistTeal, 0.035f,
            CtPalette.MistTeal.layerOver(teal.primary, 0.12f))
        CtBackgroundPreset.SLATE_BLUE -> blue
        CtBackgroundPreset.WARM_SAND -> sand
        CtBackgroundPreset.DEEP_SPACE -> blue.tintSurfaces(Color.Black, 0.22f,
            blue.textSecondary.layerOver(blue.primary, 0.65f))
        CtBackgroundPreset.MORNING_MIST -> teal.tintSurfaces(blue.surface, 0.07f,
            blue.primary.layerOver(teal.primary, 0.32f))
        CtBackgroundPreset.SUNSET_ANGLE -> sand.tintSurfaces(CtPalette.WarmSand, 0.035f,
            sand.textPrimary.layerOver(sand.primary, 0.23f))
        CtBackgroundPreset.TEAL_INDIGO -> blue.tintSurfaces(teal.surface, 0.18f,
            teal.primary.layerOver(blue.primary, 0.48f))
        CtBackgroundPreset.NIGHTFALL -> blue.tintSurfaces(Color.Black, 0.12f,
            teal.primary.layerOver(blue.primary, 0.23f))
    }
}

private fun CtColor.tintSurfaces(tint: Color, amount: Float, accent: Color): CtColor = copy(
    background = tint.layerOver(background, amount), bar = tint.layerOver(bar, amount),
    surface = tint.layerOver(surface, amount), containerLow = tint.layerOver(containerLow, amount),
    container = tint.layerOver(container, amount), containerHigh = tint.layerOver(containerHigh, amount),
    containerHighest = tint.layerOver(containerHighest, amount), primary = accent,
)

private fun brandScheme(t: CtColor): ColorScheme {
    val secondary = DarkSecondary.layerOver(t.primary, 0.58f)
    val tertiary = DarkTertiary.layerOver(t.primary, 0.68f)
    return CtDarkColorScheme.copy(
        background = t.background, onBackground = t.textPrimary, surface = t.surface, onSurface = t.textPrimary,
        surfaceDim = t.background, surfaceContainerLowest = t.background, surfaceBright = t.containerHighest,
        surfaceContainerLow = t.containerLow, surfaceContainer = t.container, surfaceContainerHigh = t.containerHigh,
        surfaceContainerHighest = t.containerHighest, surfaceVariant = t.containerHigh,
        onSurfaceVariant = t.textSecondary, outlineVariant = t.divider, outline = t.outline,
        primary = t.primary, onPrimary = t.onPrimary, surfaceTint = t.primary,
        primaryContainer = t.primary.layerOver(t.container, 0.18f),
        onPrimaryContainer = t.textPrimary, inverseSurface = t.textPrimary, inverseOnSurface = t.surface,
        secondary = secondary, secondaryContainer = secondary.layerOver(t.container, 0.18f),
        onSecondaryContainer = t.textPrimary, tertiary = tertiary,
        tertiaryContainer = tertiary.layerOver(t.container, 0.18f), onTertiaryContainer = t.textPrimary,
        primaryFixed = t.textPrimary, primaryFixedDim = t.primary,
        onPrimaryFixed = t.onPrimary, onPrimaryFixedVariant = t.primary.layerOver(t.container, 0.18f),
    )
}

/** Android supplies the optional wallpaper scheme. Dynamic hue, container and error roles remain paired. */
internal fun resolveThemeColors(dark: Boolean, preset: CtBackgroundPreset, wallpaper: ColorScheme?): CtResolvedColors {
    if (!dark) {
        val scheme = wallpaper ?: CtLightColorScheme
        return CtResolvedColors(scheme, if (wallpaper == null) CtColorLight else scheme.semanticColors(false))
    }
    val tones = presetTones(preset)
    val input = wallpaper ?: brandScheme(tones)
    val highest = input.surfaceContainerHighest
    val body = adjustInk(input.onSurface, Color.White) { lucidContrastRatio(it, highest) >= 7.0 }
    val active = body.layerOver(highest, 0.16f)
    fun ink(color: Color, target: Double, activeTarget: Double = 4.5) = adjustInk(color, Color.White) {
        lucidContrastRatio(it, highest) >= target && lucidContrastRatio(it, active) >= activeTarget
    }
    fun onFill(color: Color, fill: Color, lightInk: Boolean, target: Double, rest: Double = target) =
        adjustInk(color, if (lightInk) Color.White else Color.Black) {
            lucidContrastRatio(it, fill) >= rest && lucidContrastRatio(it, it.layerOver(fill, 0.16f)) >= target
        }
    val tertiaryText = if (wallpaper == null) tones.textTertiary else highest.layerOver(input.onSurfaceVariant, 0.12f)
    val caption = ink(tertiaryText, 5.0)
    val secondaryText = ink(input.onSurfaceVariant, maxOf(6.0, lucidContrastRatio(caption, highest) * 1.08))
    val primary = adjustInk(ink(input.primary, 4.5), Color.White) {
        lucidContrastRatio(Color.Black, Color.Black.layerOver(it, 0.16f)) >= 7.0
    }
    val secondary = ink(input.secondary, 4.5)
    val tertiary = ink(input.tertiary, 4.5)
    val error = ink(input.error, 7.0)
    val scheme = input.copy(
        onBackground = body, onSurface = body, onSurfaceVariant = secondaryText,
        outline = ink(input.outline, 3.0, 3.0), primary = primary, surfaceTint = primary,
        onPrimary = onFill(input.onPrimary, primary, false, 7.0),
        onPrimaryContainer = onFill(input.onPrimaryContainer, input.primaryContainer, true, 4.5, rest = 7.0),
        secondary = secondary, onSecondary = onFill(input.onSecondary, secondary, false, 4.5),
        onSecondaryContainer = onFill(input.onSecondaryContainer, input.secondaryContainer, true, 4.5, rest = 7.0),
        tertiary = tertiary, onTertiary = onFill(input.onTertiary, tertiary, false, 4.5),
        onTertiaryContainer = onFill(input.onTertiaryContainer, input.tertiaryContainer, true, 4.5),
        error = error, onError = onFill(input.onError, error, false, 4.5),
        onErrorContainer = onFill(input.onErrorContainer, input.errorContainer, true, 4.5, rest = 7.0),
        inversePrimary = onFill(input.inversePrimary, input.inverseSurface, false, 4.5),
    )
    val semantic = scheme.semanticColors(true).copy(
        background = scheme.background, bar = if (wallpaper == null) tones.bar else scheme.surfaceContainerLow,
        textTertiary = caption, heroPaper = scheme.surfaceContainerHigh,
        heroLink = scheme.secondary, scrim = scheme.scrim,
    )
    return CtResolvedColors(scheme, semantic)
}
