package com.furina.classtools.ui.theme

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class CardReadabilityTest {
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
