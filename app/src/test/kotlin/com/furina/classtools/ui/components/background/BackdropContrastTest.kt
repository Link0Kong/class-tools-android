package com.furina.classtools.ui.components.background

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.furina.classtools.data.background.CtBackgroundPreset
import com.furina.classtools.ui.theme.CtDarkColorScheme
import com.furina.classtools.ui.theme.CtLightColorScheme
import com.furina.classtools.ui.theme.CtLightFields
import com.furina.classtools.ui.theme.CtPalette
import com.furina.classtools.ui.theme.lucidContrastRatio
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropContrastTest {
    @Test
    fun photosKeepTextReadableAgainstLightAndDarkPixels() {
        val photoPixels = listOf(
            Color.Black, Color.White, Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta,
        )
        for (dark in listOf(false, true)) {
            assertContrast(dark, photoPixels, hasPhoto = true)
        }
    }

    @Test
    fun everyPresetKeepsTextReadableInBothThemes() {
        for (dark in listOf(false, true)) {
            for (preset in CtBackgroundPreset.entries) {
                val field = CtLightFields.of(preset, dark)
                val layers = listOf(field.base, field.topGlow, field.vignette) + field.blooms.map { it.color }
                assertContrast(dark, layers, hasPhoto = false)
            }
        }
    }

    private fun assertContrast(dark: Boolean, backgrounds: List<Color>, hasPhoto: Boolean) {
        val scheme = if (dark) CtDarkColorScheme else CtLightColorScheme
        val foregrounds = listOf(
            scheme.onBackground,
            scheme.onSurfaceVariant,
            scheme.primary,
            scheme.secondary,
            scheme.tertiary,
            scheme.error,
            if (dark) CtPalette.DarkTextTertiary else CtPalette.TextTertiary,
        )
        val veil = scheme.background.copy(alpha = backdropVeilAlpha(dark, hasPhoto))
        for (pixel in backgrounds) {
            val background = veil.compositeOver(pixel)
            for (foreground in foregrounds) {
                val contrast = lucidContrastRatio(foreground, background)
                assertTrue("dark=$dark photo=$hasPhoto foreground=$foreground background=$background ratio=$contrast", contrast >= 4.5)
            }
        }
    }
}
