package com.furina.classtools

import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.furina.classtools.data.background.BackgroundStore
import com.furina.classtools.data.background.CtBackgroundPreset
import com.furina.classtools.ui.components.background.darkGlassMaterial
import com.furina.classtools.ui.theme.ctLightCapabilityOf
import com.furina.classtools.ui.theme.CtLightSetting
import com.furina.classtools.ui.theme.appColorScheme
import com.furina.classtools.ui.theme.layerOver
import com.furina.classtools.ui.theme.lucidContrastRatio
import com.furina.classtools.ui.theme.resolveThemeColors
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformDarkSchemeTest {
    @Test fun systemResourceColorsPassAfterResolutionAndCompositing() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val wallpaper = if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(ctx) else null
        val current = BackgroundStore.readSelection(ctx)
        assertEquals(resolveThemeColors(true, current.preset, wallpaper).scheme.primary,
            appColorScheme(ctx, true, monet = true).primary)
        for (preset in CtBackgroundPreset.entries) {
            val (s, c) = resolveThemeColors(true, preset, wallpaper)
            val high = s.surfaceContainerHighest
            assertTrue(lucidContrastRatio(c.textPrimary, high) >= 7.0)
            assertTrue(lucidContrastRatio(c.textSecondary, high) >= 6.0)
            assertTrue(lucidContrastRatio(c.textTertiary, high) >= 5.0)
            assertTrue(lucidContrastRatio(s.onPrimary, s.onPrimary.layerOver(s.primary, 0.16f)) >= 7.0)
            assertTrue(lucidContrastRatio(s.onSecondaryContainer, s.secondaryContainer) >= 4.5)
            assertTrue(lucidContrastRatio(s.onErrorContainer, s.errorContainer) >= 4.5)
            for (setting in CtLightSetting.choices) {
                val capability = ctLightCapabilityOf(Build.VERSION.SDK_INT, hardwareAccelerated = true, setting = setting)
                val material = darkGlassMaterial(s.surfaceContainerHigh, setting, capability, s, c)
                if (Build.VERSION.SDK_INT < 31 || !setting.enabled) {
                    assertEquals(1f, material.fill.alpha, 0f)
                    assertEquals(0f, material.blur.value, 0f)
                }
                val pixel = material.fill.compositeOver(Color.White)
                assertTrue(lucidContrastRatio(c.textTertiary, c.textPrimary.layerOver(pixel, 0.16f)) >= 4.5)
                assertTrue(lucidContrastRatio(s.outline, pixel) >= 3.0)
            }
        }
    }
}
