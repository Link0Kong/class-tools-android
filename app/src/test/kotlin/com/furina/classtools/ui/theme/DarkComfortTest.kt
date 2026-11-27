package com.furina.classtools.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import com.furina.classtools.data.background.CtBackgroundPreset
import com.furina.classtools.ui.components.background.darkBackdropAlpha
import com.furina.classtools.ui.components.background.darkActionFill
import com.furina.classtools.ui.components.tagColors
import com.furina.classtools.ui.components.background.darkGlassMaterial
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkComfortTest {
    private val states = listOf(0f, 0.08f, 0.10f, 0.12f, 0.16f)
    private val presets = CtBackgroundPreset.entries

    private fun check(label: String, ink: Color, background: Color, target: Double) {
        val ratio = lucidContrastRatio(ink, background)
        assertTrue("$label $ink over $background = $ratio (target $target)", ratio >= target)
    }

    @Test fun wcagReferenceAndComposition() {
        assertEquals(21.0, lucidContrastRatio(Color.White, Color.Black), 0.000001)
        check("lighter side of AA boundary", Color(0xFF767676), Color.White, 4.5)
        assertTrue(lucidContrastRatio(Color(0xFF777777), Color.White) < 4.5)
        assertEquals(Color(0xFF808080), Color.White.copy(alpha = 0.5f).compositeOver(Color.Black))
    }

    @Test fun brandPresetsRemainDistinctAndKeepTheirIds() {
        assertEquals(listOf("warm_white", "mist_teal", "slate_blue", "warm_sand", "deep_space",
            "morning_mist", "sunset_angle", "teal_indigo", "nightfall"), presets.map { it.id })
        val colors = presets.map { resolveThemeColors(true, it, null) }
        assertEquals(9, colors.map { it.scheme.primary }.distinct().size)
        assertEquals(9, colors.map { it.scheme.background to it.scheme.surfaceContainerHighest }.distinct().size)
        val sand = colors[presets.indexOf(CtBackgroundPreset.WARM_SAND)].scheme.primary
        val blue = colors[presets.indexOf(CtBackgroundPreset.SLATE_BLUE)].scheme.primary
        assertTrue(sand.red > sand.blue && blue.blue > blue.red)
    }

    @Test fun surfacesControlsAndStatusLayersUseTheResolvedScheme() {
        val wallpaper = CtDarkColorScheme.copy(
            primary = Color(0xFFCCACDF), onPrimary = Color(0xFF514463), secondaryContainer = Color(0xFF493D52),
            onSurfaceVariant = Color(0xFF89838C), outline = Color(0xFF736F74),
            error = Color(0xFFA96D76), surfaceContainerHighest = Color(0xFF434047),
        )
        for (preset in presets) for (system in listOf(null, wallpaper)) {
            val (s, c) = resolveThemeColors(true, preset, system)
            val highest = s.surfaceContainerHighest
            val prefix = "${preset.id}/dynamic=${system != null}"
            assertEquals(s.onSurface, c.textPrimary)
            assertEquals(s.onSurfaceVariant, c.textSecondary)
            assertEquals(s.primary, c.primary)
            val surfaces = listOf(s.background, s.surface, s.surfaceContainerLow, s.surfaceContainer,
                s.surfaceContainerHigh, highest, c.bar)
            assertTrue(surfaces.all { it.alpha == 1f })
            check("$prefix body", c.textPrimary, highest, 7.0)
            check("$prefix secondary", c.textSecondary, highest, 6.0)
            check("$prefix tertiary", c.textTertiary, highest, 5.0)
            check("$prefix deadline/error", s.error, highest, 7.0)
            check("$prefix file status", s.onPrimaryContainer, s.primaryContainer, 7.0)
            check("$prefix error status", s.onErrorContainer, s.errorContainer, 7.0)
            assertTrue(relativeLuminance(c.textPrimary) > relativeLuminance(c.textSecondary))
            assertTrue(relativeLuminance(c.textSecondary) > relativeLuminance(c.textTertiary))
            for (surface in surfaces) for (alpha in states) {
                val active = s.onSurface.layerOver(surface, alpha)
                for (ink in listOf(c.textPrimary, c.textSecondary, c.textTertiary, s.primary, s.secondary, s.tertiary, s.error)) {
                    check("$prefix text/$alpha", ink, active, 4.5)
                }
                check("$prefix field boundary/$alpha", s.outline, active, 3.0)
                check("$prefix tab indicator", s.primary, active, 3.0)
            }
            for (alpha in states) {
                check("$prefix main button", s.onPrimary, s.onPrimary.layerOver(s.primary, alpha), 7.0)
                for ((ink, fill) in listOf(s.onPrimaryContainer to s.primaryContainer,
                    s.onSecondaryContainer to s.secondaryContainer, s.onSecondary to s.secondary,
                    s.onTertiaryContainer to s.tertiaryContainer, s.onTertiary to s.tertiary,
                    s.onErrorContainer to s.errorContainer, s.onError to s.error,
                    s.inverseOnSurface to s.inverseSurface, s.inversePrimary to s.inverseSurface)) {
                    check("$prefix selected/error/snackbar/$alpha", ink, ink.layerOver(fill, alpha), 4.5)
                }
            }
            for (tint in listOf(CtPalette.MistTeal, CtPalette.SlateBlue, CtPalette.WarmSand, s.primary, s.error)) {
                val (fill, ink) = tagColors(tint, c)
                check("status tag", ink, fill, 7.0)
            }
            if (system == null) report(preset, s, c)
        }
    }

    @Test fun glassAndPhotoUseTheCompositedPixelIncludingTheStateLayer() {
        for (preset in presets) for (wallpaper in listOf(null, CtDarkColorScheme.copy(
            primary = Color(0xFFC4A8DA), surfaceContainerHigh = Color(0xFF3C323E)))) {
            val (s, c) = resolveThemeColors(true, preset, wallpaper)
            for (setting in CtLightSetting.choices) for (capability in CtLightCapability.entries) {
                for (primary in listOf(true, false)) {
                    val ink = if (primary) s.onPrimary else s.onSecondaryContainer
                    val base = if (primary) s.primary else s.secondaryContainer
                    val fill = darkActionFill(base, ink, primary, setting, capability)
                    for (under in listOf(Color.White, Color.Black)) {
                        check("button/pressed/$setting", ink, ink.layerOver(fill.compositeOver(under), 0.16f),
                            if (primary) 7.0 else 4.5)
                    }
                }
                for (base in listOf(s.surfaceContainerLow, s.surfaceContainerHigh, c.bar)) {
                    val material = darkGlassMaterial(base, setting, capability, s, c)
                    if (!setting.enabled || capability != CtLightCapability.FULL) {
                        assertEquals(base, material.fill)
                        assertEquals(0f, material.blur.value)
                    }
                    for (pixel in listOf(Color.White, Color.Black, Color.Green, Color.Magenta)) {
                        val veil = darkBackdropAlpha(pixel, s, c)
                        val window = s.background.layerOver(pixel, veil)
                        check("photo title", c.textPrimary, window, 7.0)
                        check("photo caption", c.textTertiary, window, 5.0)
                        for (under in listOf(pixel, window)) {
                            val final = material.fill.compositeOver(under)
                            check("card body", c.textPrimary, final, 7.0)
                            check("card secondary", c.textSecondary, final, 6.0)
                            check("card tertiary", c.textTertiary, final, 5.0)
                            check("card error", s.error, final, 7.0)
                            val pressed = c.textPrimary.layerOver(final, 0.16f)
                            check("card pressed caption", c.textTertiary, pressed, 4.5)
                            check("card pressed button", s.primary, pressed, 4.5)
                            check("card pressed boundary", s.outline, pressed, 3.0)
                        }
                    }
                }
            }
        }
    }

    @Test fun materialCanIncreaseOpacityAndReturnSolid() {
        val (s, c) = resolveThemeColors(true, CtBackgroundPreset.DEFAULT, null)
        val strong = CtLightSetting.fromStorage("strong")
        val high = s.surfaceContainerHighest
        val result = darkGlassMaterial(high, strong, CtLightCapability.FULL, s, c)
        assertTrue(result.fill.alpha > 0.88f)
        val constrained = darkGlassMaterial(Color.White, strong, CtLightCapability.FULL, s, c)
        assertEquals(s.surfaceContainerLow, constrained.fill)
        check("opaque fallback", c.textPrimary, constrained.fill, 7.0)
        assertEquals(1f, constrained.fill.alpha, 0f)
        assertTrue(darkBackdropAlpha(Color.White, s, c) > darkBackdropAlpha(Color.Black, s, c))
    }

    private fun report(preset: CtBackgroundPreset, s: ColorScheme, c: CtColor) {
        val highest = s.surfaceContainerHighest
        if (preset == CtBackgroundPreset.DEFAULT) {
            val roles = mapOf(
                "primary" to s.primary, "onPrimary" to s.onPrimary, "primaryContainer" to s.primaryContainer,
                "onPrimaryContainer" to s.onPrimaryContainer, "inversePrimary" to s.inversePrimary, "secondary" to s.secondary,
                "onSecondary" to s.onSecondary, "secondaryContainer" to s.secondaryContainer, "onSecondaryContainer" to s.onSecondaryContainer,
                "tertiary" to s.tertiary, "onTertiary" to s.onTertiary, "tertiaryContainer" to s.tertiaryContainer,
                "onTertiaryContainer" to s.onTertiaryContainer, "error" to s.error, "onError" to s.onError,
                "errorContainer" to s.errorContainer, "onErrorContainer" to s.onErrorContainer, "background" to s.background,
                "onBackground" to s.onBackground, "surface" to s.surface, "onSurface" to s.onSurface,
                "surfaceVariant" to s.surfaceVariant, "onSurfaceVariant" to s.onSurfaceVariant, "surfaceTint" to s.surfaceTint,
                "inverseSurface" to s.inverseSurface, "inverseOnSurface" to s.inverseOnSurface, "outline" to s.outline,
                "outlineVariant" to s.outlineVariant, "scrim" to s.scrim, "surfaceDim" to s.surfaceDim,
                "surfaceBright" to s.surfaceBright, "surfaceContainerLowest" to s.surfaceContainerLowest, "surfaceContainerLow" to s.surfaceContainerLow,
                "surfaceContainer" to s.surfaceContainer, "surfaceContainerHigh" to s.surfaceContainerHigh, "surfaceContainerHighest" to s.surfaceContainerHighest,
                "primaryFixed" to s.primaryFixed, "primaryFixedDim" to s.primaryFixedDim, "onPrimaryFixed" to s.onPrimaryFixed,
                "onPrimaryFixedVariant" to s.onPrimaryFixedVariant, "secondaryFixed" to s.secondaryFixed, "secondaryFixedDim" to s.secondaryFixedDim,
                "onSecondaryFixed" to s.onSecondaryFixed, "onSecondaryFixedVariant" to s.onSecondaryFixedVariant, "tertiaryFixed" to s.tertiaryFixed,
                "tertiaryFixedDim" to s.tertiaryFixedDim, "onTertiaryFixed" to s.onTertiaryFixed, "onTertiaryFixedVariant" to s.onTertiaryFixedVariant,
            )
            roles.forEach { (role, color) ->
                println("DARK_MATERIAL|$role|" + String.format(Locale.ROOT, "#%06X", color.toArgb() and 0xFFFFFF))
            }
        }
        val veil = darkBackdropAlpha(Color.White, s, c)
        for (setting in CtLightSetting.choices) {
            val low = darkGlassMaterial(s.surfaceContainerLow, setting, CtLightCapability.FULL, s, c)
            val high = darkGlassMaterial(highest, setting, CtLightCapability.FULL, s, c)
            println("DARK_GLASS|${preset.id}|${setting.storageValue}|${low.fill.alpha}|${low.blur.value}|" +
                "${high.fill.alpha}|${high.blur.value}|$veil")
        }
        val tokens = listOf(s.background, s.surface, s.surfaceContainerLow, s.surfaceContainer, s.surfaceContainerHigh,
            highest, c.bar, s.onSurface, s.onSurfaceVariant, c.textTertiary, s.outlineVariant, s.outline,
            s.primary, s.onPrimary, s.primaryContainer, s.onPrimaryContainer, s.secondaryContainer,
            s.onSecondaryContainer, s.error, s.onError, s.errorContainer, s.onErrorContainer)
        val ratios = listOf(s.onSurface to highest, s.onSurfaceVariant to highest, c.textTertiary to highest,
            s.onPrimary to s.primary, s.onPrimary to s.onPrimary.layerOver(s.primary, 0.16f),
            s.outline to highest, s.onSecondaryContainer to s.secondaryContainer, s.error to highest)
        println("DARK_TOKENS|${preset.id}|" + tokens.joinToString("|") {
            String.format(Locale.ROOT, "#%06X", it.toArgb() and 0xFFFFFF)
        })
        println("DARK_RATIOS|${preset.id}|" + ratios.joinToString("|") { (ink, bg) ->
            lucidContrastRatio(ink, bg).toString()
        })
    }
}
