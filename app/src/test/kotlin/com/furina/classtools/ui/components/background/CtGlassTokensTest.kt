package com.furina.classtools.ui.components.background

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.furina.classtools.data.background.CtBackgroundPalette
import com.furina.classtools.data.background.CtBackgroundPreset
import com.furina.classtools.ui.theme.CtDarkColorScheme
import com.furina.classtools.ui.theme.CtDarkGlassRole
import com.furina.classtools.ui.theme.CtGlassTable
import com.furina.classtools.ui.theme.CtLightIntensity
import com.furina.classtools.ui.theme.CtLightLevel
import com.furina.classtools.ui.theme.CtLightSetting
import com.furina.classtools.ui.theme.CtPalette
import com.furina.classtools.ui.theme.ctLightContrastFloor
import com.furina.classtools.ui.theme.lucidContrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 覆盖 CtGlassSurface 的当前材质表和 ctFrostedCard 的兼容规格。 */
class CtGlassTokensTest {

    @Test
    fun `dark card glass is thicker than hero glass in every enabled setting`() {
        val level = CtLightLevel.THIN
        val boost = CtBackgroundPalette.dark(CtBackgroundPreset.DEFAULT).cardAlphaBoost
        val settings = CtLightSetting.choices.filter(CtLightSetting::enabled)
        val cardSpecs = settings.map { setting ->
            val hero = CtGlassTable.specOf(level, setting, dark = true, darkRole = CtDarkGlassRole.HERO, fillBoost = boost)
            val card = CtGlassTable.specOf(level, setting, dark = true, darkRole = CtDarkGlassRole.CARD, fillBoost = boost)
            assertTrue(card.fillBottom.alpha > hero.fillTop.alpha)
            assertTrue(card.fillTop.alpha < 1f)
            assertTrue(hero.specularAlpha > card.specularAlpha)
            assertEquals(0f, hero.edgeTopAlpha, 0f)
            assertEquals(0f, hero.edgeBottomAlpha, 0f)
            assertEquals(0f, card.edgeTopAlpha, 0f)
            assertEquals(0f, card.edgeBottomAlpha, 0f)
            card
        }
        assertTrue(cardSpecs.zipWithNext().all { (stronger, weaker) -> stronger.fillTop.alpha < weaker.fillTop.alpha })
    }

    @Test
    fun `dark glass text stays readable over a white photo and peak reflection`() {
        val scheme = CtDarkColorScheme
        val photo = scheme.background.copy(alpha = backdropVeilAlpha(dark = true, hasPhoto = true))
            .compositeOver(Color.White)
        val boost = CtBackgroundPalette.dark(CtBackgroundPreset.DEFAULT).cardAlphaBoost + 0.18f
        for (intensity in CtLightIntensity.entries) {
            for (role in listOf(CtDarkGlassRole.HERO, CtDarkGlassRole.CARD)) {
                val setting = CtLightSetting(intensity)
                val spec = CtGlassTable.specOf(role.level, setting, dark = true, darkRole = role, fillBoost = boost)
                assertTrue(spec.fillBottom.alpha > CtGlassTable.specOf(role.level, setting, dark = true, darkRole = role).fillBottom.alpha)
                val brightest = Color.White.copy(alpha = spec.specularAlpha).compositeOver(spec.fillTop.compositeOver(photo))
                for (text in listOf(scheme.onSurface, scheme.onSurfaceVariant, scheme.primary, CtPalette.HeroDarkLink)) {
                    val ratio = lucidContrastRatio(text, brightest)
                    assertTrue("$role $intensity $text contrast=$ratio", ratio >= 4.5)
                }
            }
        }
    }

    @Test
    fun `dark surface roles keep light and disabled specs intact`() {
        val level = CtLightLevel.THIN
        for (role in CtDarkGlassRole.entries) {
            assertEquals(
                CtGlassTable.specOf(level, CtLightSetting.Default, dark = false),
                CtGlassTable.specOf(level, CtLightSetting.Default, dark = false, darkRole = role),
            )
            assertEquals(
                CtGlassTable.specOf(level, CtLightSetting.Off, dark = true),
                CtGlassTable.specOf(level, CtLightSetting.Off, dark = true, darkRole = role),
            )
        }
    }

    @Test
    fun `light glass alpha stays in the frosted range`() {
        CtLightIntensity.entries.forEach { intensity ->
            val spec = CtGlassTokens.light(intensity, enabled = true)
            assertTrue("${intensity.name} baseAlpha=${spec.baseAlpha}", spec.baseAlpha in 0.55f..0.70f)
            assertTrue(spec.topHighlightAlpha > 0f)
            assertTrue(spec.edgeTopAlpha > 0f)
        }
    }

    @Test
    fun `dark glass alpha stays in the frosted range`() {
        CtLightIntensity.entries.forEach { intensity ->
            val spec = CtGlassTokens.dark(intensity, enabled = true)
            assertTrue("${intensity.name} baseAlpha=${spec.baseAlpha}", spec.baseAlpha in 0.78f..0.85f)
            assertTrue(spec.topHighlightAlpha > 0f)
            assertTrue("深色也要有高光边（不灰糊）", spec.edgeTopAlpha >= 0.18f)
        }
    }

    @Test
    fun `strong is more transparent than weak`() {
        assertTrue(
            CtGlassTokens.light(CtLightIntensity.STRONG, true).baseAlpha <
                CtGlassTokens.light(CtLightIntensity.WEAK, true).baseAlpha,
        )
        assertTrue(
            CtGlassTokens.dark(CtLightIntensity.STRONG, true).baseAlpha <
                CtGlassTokens.dark(CtLightIntensity.WEAK, true).baseAlpha,
        )
        // 高光：强 > 均衡 > 弱
        assertTrue(
            CtGlassTokens.light(CtLightIntensity.STRONG, true).topHighlightAlpha >
                CtGlassTokens.light(CtLightIntensity.WEAK, true).topHighlightAlpha,
        )
    }

    @Test
    fun `disabled light falls back to near solid`() {
        val light = CtGlassTokens.light(null, enabled = false)
        val dark = CtGlassTokens.dark(null, enabled = false)
        assertTrue(light.baseAlpha >= 0.90f)
        assertTrue(dark.baseAlpha >= 0.90f)
    }

    @Test
    fun `background boost is clamped to glass range`() {
        CtBackgroundPreset.entries.forEach { preset ->
            listOf(false, true).forEach { dark ->
                val palette = CtBackgroundPalette.of(preset, dark)
                val base = CtGlassTokens.of(dark, CtLightIntensity.BALANCED, true)
                val alpha = CtGlassTokens.resolvedAlpha(base, palette.cardAlphaBoost)
                assertTrue(
                    "alpha in range: ${preset.id} dark=$dark -> $alpha",
                    alpha in CtGlassTokens.MIN_BODY_ALPHA..CtGlassTokens.MAX_BODY_ALPHA,
                )
                assertTrue(
                    "boost never lowers alpha: ${preset.id}",
                    alpha >= CtGlassTokens.resolvedAlpha(base, 0f),
                )
            }
        }
    }

    @Test
    fun `extreme boost is capped`() {
        val spec = CtGlassTokens.light(CtLightIntensity.STRONG, true)
        // 强光感 + 巨大正补偿 → 夹到上限
        val strongest = CtGlassTokens.light(CtLightIntensity.WEAK, true)
        assertEquals(CtGlassTokens.MAX_BODY_ALPHA, CtGlassTokens.resolvedAlpha(strongest, 5f), 0.0001f)
        // 任意基础 + 巨大负补偿 → 夹到下限（正文可读性不被背景补偿破坏）
        assertEquals(CtGlassTokens.MIN_BODY_ALPHA, CtGlassTokens.resolvedAlpha(spec, -5f), 0.0001f)
        assertTrue(CtGlassTokens.resolvedAlpha(spec, 0.30f) <= CtGlassTokens.MAX_BODY_ALPHA)
    }

    @Test
    fun `base colors come from brand tokens`() {
        assertEquals(CtPalette.Surface, CtGlassTokens.baseColor(dark = false))
        assertEquals(CtPalette.DarkContainer, CtGlassTokens.baseColor(dark = true))
    }

    /**
     * **AA 红线**：任意预设 × 明暗下，正文色压在玻璃底上（最坏底层 = 预设底色）合成对比度 ≥ 4.5。
     */
    @Test
    fun `body text on glass meets AA for every preset and theme`() {
        CtBackgroundPreset.entries.forEach { preset ->
            listOf(false, true).forEach { dark ->
                CtLightIntensity.entries.forEach { intensity ->
                    val palette = CtBackgroundPalette.of(preset, dark)
                    val spec = CtGlassTokens.of(dark, intensity, enabled = true)
                    val alpha = CtGlassTokens.resolvedAlpha(spec, palette.cardAlphaBoost)
                    val cardSurface = CtGlassTokens.baseColor(dark)
                    val bodyText = if (dark) CtPalette.DarkTextPrimary else CtPalette.TextPrimary
                    val ratio = ctLightContrastFloor(bodyText, cardSurface, palette.base, alpha)
                    assertTrue(
                        "AA body: ${preset.id} dark=$dark ${intensity.name} alpha=$alpha -> $ratio",
                        ratio >= 4.5,
                    )
                }
            }
        }
    }

    /**
     * 次级文字（说明 12sp / 次级色）在玻璃上同样需要 AA —— 这是最容易踩的坑（半透明底 + 次级色）。
     */
    @Test
    fun `secondary text on glass meets AA for every preset and theme`() {
        CtBackgroundPreset.entries.forEach { preset ->
            listOf(false, true).forEach { dark ->
                val palette = CtBackgroundPalette.of(preset, dark)
                val spec = CtGlassTokens.of(dark, CtLightIntensity.BALANCED, enabled = true)
                val alpha = CtGlassTokens.resolvedAlpha(spec, palette.cardAlphaBoost)
                val cardSurface = CtGlassTokens.baseColor(dark)
                val secondary = if (dark) CtPalette.DarkTextSecondary else CtPalette.TextSecondary
                val ratio = ctLightContrastFloor(secondary, cardSurface, palette.base, alpha)
                assertTrue(
                    "AA secondary: ${preset.id} dark=$dark alpha=$alpha -> $ratio",
                    ratio >= 4.5,
                )
            }
        }
    }

    @Test
    fun `glass on default warm white is strictly better than raw surface`() {
        // 默认暖白 + 浅色：玻璃底合成后与「纯白卡片」对比度差不超过 1.0（视觉一致）
        val palette = CtBackgroundPalette.of(CtBackgroundPreset.WARM_WHITE, dark = false)
        val spec = CtGlassTokens.of(false, CtLightIntensity.BALANCED, true)
        val alpha = CtGlassTokens.resolvedAlpha(spec, palette.cardAlphaBoost)
        val ratio = ctLightContrastFloor(CtPalette.TextPrimary, CtPalette.Surface, palette.base, alpha)
        val solid = lucidContrastRatio(CtPalette.TextPrimary, CtPalette.Surface)
        assertTrue("glass=$ratio solid=$solid", ratio >= 4.5)
        assertTrue("glass ratio within 1.0 of solid", solid - ratio <= 1.0)
    }
}
