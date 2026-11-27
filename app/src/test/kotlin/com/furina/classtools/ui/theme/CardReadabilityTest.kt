package com.furina.classtools.ui.theme

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class CardReadabilityTest {
    @Test
    fun darkCardsRetainPresetAndContrastWhenPlatformChangesHue() {
        val platform = CtDarkColorScheme.copy(primary = androidx.compose.ui.graphics.Color.Magenta,
            surfaceContainer = androidx.compose.ui.graphics.Color.Yellow)
        val cards = mutableSetOf<androidx.compose.ui.graphics.Color>()
        fun ratio(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Float {
            val x = a.luminance()
            val y = b.luminance()
            return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
        }
        for (preset in com.furina.classtools.data.background.CtBackgroundPreset.entries) {
            val scheme = darkBrandCards(platform, preset)
            val project = darkBrandCards(CtDarkColorScheme, preset)
            org.junit.Assert.assertEquals(project.surfaceContainer, scheme.surfaceContainer)
            org.junit.Assert.assertEquals(platform.primary, scheme.primary)
            for (surface in listOf(scheme.surfaceContainerLow, scheme.surfaceContainer, scheme.surfaceContainerHighest)) {
                org.junit.Assert.assertTrue(ratio(surface, scheme.onSurface) >= 4.5f)
                org.junit.Assert.assertTrue(ratio(surface, scheme.onSurfaceVariant) >= 4.5f)
                org.junit.Assert.assertTrue(ratio(surface, scheme.outline) >= 3f)
            }
            cards.add(scheme.surfaceContainer)
        }
        org.junit.Assert.assertTrue(cards.size > 1)
    }

    @Test fun bodyAndActionsMeetContrastOnTheirPaintedSurfaces() {
        for (scheme in listOf(CtLightColorScheme, CtDarkColorScheme)) {
            val surfaces = listOf(scheme.surface, scheme.surfaceContainerLow, scheme.surfaceContainerHigh)
            val pairs = surfaces.flatMap { bg ->
                listOf(scheme.onSurface, scheme.onSurfaceVariant, scheme.primary, scheme.error).map { fg -> fg to bg }
            } + listOf(scheme.onPrimary to scheme.primary, scheme.onPrimaryContainer to scheme.primaryContainer)
            for ((ink, background) in pairs) {
                val a = ink.luminance()
                val b = background.luminance()
                val ratio = (maxOf(a,b) + 0.05f) / (minOf(a,b) + 0.05f)
                assertTrue("$ink on $background = $ratio", ratio >= 4.5f)
            }
        }
    }
}
