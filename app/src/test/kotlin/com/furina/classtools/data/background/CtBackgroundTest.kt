package com.furina.classtools.data.background

import com.furina.classtools.ui.theme.CT_LIGHT_AA_BODY
import com.furina.classtools.ui.theme.CtPalette
import androidx.compose.ui.graphics.Color
import com.furina.classtools.ui.theme.CtLightCapability
import com.furina.classtools.ui.theme.ctLightContrastFloor
import com.furina.classtools.ui.theme.lucidContrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自定义背景（`data/background/CtBackground.kt`）纯逻辑单测。
 *
 * 覆盖：
 * 1) 持久化序列化/反序列化（含容错：非法值 / 缺 blur / 多余分隔符 / 路径穿越");
 * 2) 预设表完整性（9 个预设、每个都有浅/深两套、渐变标记一致）；
 * 3) 压缩目标尺寸 & 文件名合法性；
 * 4) 可读性 AA（预设 × 明暗，含卡片玻璃底合成对比度）；
 * 5) 模糊 σ 计算（设备能力降级）。
 */
class CtBackgroundTest {

    // ---------------------------------------------------------------------
    // 1) 持久化
    // ---------------------------------------------------------------------

    @Test
    fun `default selection serializes to warm_white`() {
        assertEquals("warm_white", CtBackgroundSelection.DEFAULT.storageValue)
        assertEquals(CtBackgroundSelection.DEFAULT, CtBackgroundSelection.fromStorage(null))
        assertEquals(CtBackgroundSelection.DEFAULT, CtBackgroundSelection.fromStorage(""))
        assertEquals(CtBackgroundSelection.DEFAULT, CtBackgroundSelection.fromStorage("   "))
    }

    @Test
    fun `every preset round trips`() {
        CtBackgroundPreset.entries.forEach { preset ->
            val s = CtBackgroundSelection(preset = preset)
            assertEquals(s, CtBackgroundSelection.fromStorage(s.storageValue))
        }
    }

    @Test
    fun `custom image selection round trips with blur`() {
        val s = CtBackgroundSelection(
            preset = CtBackgroundPreset.DEEP_SPACE,
            useCustomImage = true,
            customImageFile = "bg_1700000000000.jpg",
            customBlur = 24,
        )
        assertEquals("deep_space|custom:bg_1700000000000.jpg|blurv2:24", s.storageValue)
        assertEquals(s, CtBackgroundSelection.fromStorage(s.storageValue))
    }

    @Test
    fun `custom image without blur token falls back to default blur`() {
        val parsed = CtBackgroundSelection.fromStorage("slate_blue|custom:bg_1.jpg")
        assertTrue(parsed.hasImage)
        assertEquals(CtBackgroundPreset.SLATE_BLUE, parsed.preset)
        assertEquals(CtBackgroundSelection.DEFAULT_CUSTOM_BLUR, parsed.customBlur)
    }

    @Test
    fun `blur out of range is clamped`() {
        assertEquals(
            CtBackgroundSelection.MAX_CUSTOM_BLUR,
            CtBackgroundSelection.fromStorage("warm_white|custom:bg_1.jpg|blur:99").customBlur,
        )
        assertEquals(
            CtBackgroundSelection.MIN_CUSTOM_BLUR,
            CtBackgroundSelection.fromStorage("warm_white|custom:bg_1.jpg|blur:-3").customBlur,
        )
    }

    @Test
    fun `unknown preset falls back to default but keeps no image`() {
        val parsed = CtBackgroundSelection.fromStorage("not_a_preset")
        assertEquals(CtBackgroundPreset.DEFAULT, parsed.preset)
        assertFalse(parsed.hasImage)
    }

    @Test
    fun `garbage values never throw`() {
        listOf(
            "||||",
            "custom:",
            "blur:abc",
            "warm_white|custom:|blur:",
            "异形背景",
            "warm_white|blur:2|blur:1",
        ).forEach { raw ->
            val parsed = CtBackgroundSelection.fromStorage(raw)
            assertEquals(CtBackgroundPreset.DEFAULT, parsed.preset)
            assertFalse(parsed.hasImage)
        }
    }

    @Test
    fun `withImage and withoutImage transitions`() {
        val base = CtBackgroundSelection.DEFAULT
        val withImg = CtBackgroundSelection.withImage(base, "bg_9.jpg", blur = 1)
        assertTrue(withImg.hasImage)
        assertEquals(1, withImg.customBlur)

        val backToPreset = CtBackgroundSelection.withPreset(withImg, CtBackgroundPreset.MIST_TEAL)
        assertFalse(backToPreset.hasImage)
        assertEquals(CtBackgroundPreset.MIST_TEAL, backToPreset.preset)
        // 图片文件名保留，便于用户切回
        assertEquals("bg_9.jpg", backToPreset.customImageFile)

        val removed = CtBackgroundSelection.withoutImage(withImg)
        assertFalse(removed.hasImage)
        assertNull(removed.customImageFile)
    }

    // ---------------------------------------------------------------------
    // 2) 预设表
    // ---------------------------------------------------------------------

    @Test
    fun `preset grid contains every preset exactly once`() {
        assertEquals(CtBackgroundPreset.entries.size, CtBackgroundPreset.grid.size)
        assertEquals(CtBackgroundPreset.entries.toSet(), CtBackgroundPreset.grid.toSet())
        assertEquals(9, CtBackgroundPreset.grid.size)
    }

    @Test
    fun `preset ids are unique and lowercase`() {
        val ids = CtBackgroundPreset.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { id -> assertEquals(id.lowercase(), id) }
    }

    @Test
    fun `gradient flag matches the four gradient presets`() {
        CtBackgroundPreset.entries.forEach { preset ->
            val paletteLight = CtBackgroundPalette.light(preset)
            assertEquals(preset.isGradient, paletteLight.gradient)
            if (preset.isGradient) {
                assertNotEquals(paletteLight.base, paletteLight.second)
            } else {
                // 纯色预设两色一致（不画渐变层；渐变由 gradient 标记 + 光感斑承担）
                assertEquals(paletteLight.base, paletteLight.second)
            }
        }
    }

    @Test
    fun `every preset has light and dark palettes with sane alpha`() {
        CtBackgroundPreset.entries.forEach { preset ->
            listOf(CtBackgroundPalette.light(preset), CtBackgroundPalette.dark(preset)).forEach { p ->
                assertTrue("scrimTop in range: ${preset.id}", p.scrimTop in 0f..1f)
                assertTrue("scrimBottom in range: ${preset.id}", p.scrimBottom in 0f..1f)
                assertTrue("boost in range: ${preset.id}", p.cardAlphaBoost in 0f..0.30f)
                assertTrue("bloom in range: ${preset.id}", p.bloomAlpha in 0f..1f)
                assertTrue("bottom scrim >= top: ${preset.id}", p.scrimBottom >= p.scrimTop)
            }
            // 深空/夜幕本身就是深色背景：浅色表与深色表可以同族，但必须「有明暗之分或都是深底」，
            // 这里只要求两张表都存在且合法（上方已校验 alpha 范围）。
        }
    }

    // ---------------------------------------------------------------------
    // 3) 压缩 / 文件名
    // ---------------------------------------------------------------------

    @Test
    fun `target size keeps aspect within max long edge`() {
        assertEquals(2048 to 1536, CtBackgroundImage.targetSize(4000, 3000))
        assertEquals(1536 to 2048, CtBackgroundImage.targetSize(3000, 4000))
        // 小于上限：不放大
        assertEquals(1200 to 800, CtBackgroundImage.targetSize(1200, 800))
        // 非法输入：返回 0,0（调用方回退失败）
        assertEquals(0 to 0, CtBackgroundImage.targetSize(0, 100))
        assertEquals(0 to 0, CtBackgroundImage.targetSize(100, -1))
        assertEquals(0 to 0, CtBackgroundImage.targetSize(100, 100, maxLongEdge = 0))
    }

    @Test
    fun `target size never produces zero dimension`() {
        val (w, h) = CtBackgroundImage.targetSize(100_000, 1)
        assertTrue(w > 0)
        assertTrue(h >= 1)
        assertEquals(CtBackgroundImage.MAX_LONG_EDGE, w)
    }

    @Test
    fun `file name validation rejects traversal and foreign names`() {
        assertTrue(CtBackgroundImage.isValidFileName("bg_1700000000000.jpg"))
        assertTrue(CtBackgroundImage.isValidFileName("bg_1_2.jpg"))
        assertFalse(CtBackgroundImage.isValidFileName(null))
        assertFalse(CtBackgroundImage.isValidFileName(""))
        assertFalse(CtBackgroundImage.isValidFileName("../bg_1.jpg"))
        assertFalse(CtBackgroundImage.isValidFileName("bg_1.jpg/../x"))
        assertFalse(CtBackgroundImage.isValidFileName("evil_1.jpg"))
        assertFalse(CtBackgroundImage.isValidFileName("bg_1.png"))
        assertFalse(CtBackgroundImage.isValidFileName("bg_.jpg"))
        assertFalse(CtBackgroundImage.isValidFileName("bg_1 bad.jpg"))
        assertFalse(CtBackgroundImage.isValidFileName("bg_1.jpg.bak"))
    }

    @Test
    fun `new file name is valid and unique by timestamp`() {
        val a = CtBackgroundImage.newFileName(1_700_000_000_000)
        val b = CtBackgroundImage.newFileName(1_700_000_000_001)
        assertTrue(CtBackgroundImage.isValidFileName(a))
        assertTrue(CtBackgroundImage.isValidFileName(b))
        assertNotEquals(a, b)
        assertEquals("bg_1700000000000.jpg", a)
    }

    // ---------------------------------------------------------------------
    // 4) 可读性 AA
    // ---------------------------------------------------------------------

    @Test
    fun `every preset meets AA for headline on background and body on card`() {
        CtBackgroundPreset.entries.forEach { preset ->
            listOf(false, true).forEach { dark ->
                val check = ctBackgroundContrast(preset, dark)
                assertTrue(
                    "headline AA: ${preset.id} dark=$dark -> ${check.headlineOnBackground}",
                    check.headlineOnBackground >= 4.5,
                )
                assertTrue(
                    "body AA: ${preset.id} dark=$dark -> ${check.bodyOnCard}",
                    check.bodyOnCard >= 4.5,
                )
                assertTrue(check.passesAA)
            }
        }
    }

    @Test
    fun `custom image background keeps AA via boosted scrims and card alpha`() {
        val selection = CtBackgroundSelection(
            useCustomImage = true,
            customImageFile = "bg_1.jpg",
        )
        listOf(false, true).forEach { dark ->
            val colors = ctBackgroundColorsOf(selection, dark, imageAvailable = true)
            val preset = CtBackgroundColorsProbe.presetColors(selection.preset, dark)
            assertTrue("boost raised: dark=$dark", colors.cardAlphaBoost > preset.cardAlphaBoost)
            // 任意图片的最坏情况：浅色模式图片纯黑 / 深色模式图片纯白。
            val worstImage = if (dark) Color.White else Color.Black
            val text = if (dark) CtPalette.DarkTextPrimary else CtPalette.TextPrimary
            // 顶部标题直接画在 scrim 上：按 scrimTop 合成后必须 AA。
            val composed = Color(
                red = colors.base.red * colors.scrimTop + worstImage.red * (1f - colors.scrimTop),
                green = colors.base.green * colors.scrimTop + worstImage.green * (1f - colors.scrimTop),
                blue = colors.base.blue * colors.scrimTop + worstImage.blue * (1f - colors.scrimTop),
            )
            val ratio = lucidContrastRatio(text, composed)
            assertTrue("headline AA: dark=$dark ratio=$ratio", ratio >= CT_LIGHT_AA_BODY)
        }
    }

    @Test
    fun `missing image falls back to preset colors`() {
        val selection = CtBackgroundSelection(
            preset = CtBackgroundPreset.MIST_TEAL,
            useCustomImage = true,
            customImageFile = "bg_missing.jpg",
        )
        listOf(false, true).forEach { dark ->
            assertEquals(
                CtBackgroundPalette.of(CtBackgroundPreset.MIST_TEAL, dark),
                ctBackgroundColorsOf(selection, dark, imageAvailable = false),
            )
        }
    }

    // ---------------------------------------------------------------------
    // 5) 模糊 σ / 降级
    // ---------------------------------------------------------------------

    @Test
    fun `dark presets have visibly distinct bases`() {
        val pairs = CtBackgroundPreset.entries.map {
            CtBackgroundPalette.dark(it).base to CtBackgroundPalette.dark(it).second
        }
        assertTrue("深色预设底色过于接近: $pairs", pairs.distinct().size >= 7)
        assertTrue(
            CtBackgroundPalette.dark(CtBackgroundPreset.MIST_TEAL).base !=
                CtBackgroundPalette.dark(CtBackgroundPreset.SLATE_BLUE).base,
        )
    }

    @Test
    fun `blur sigma mapping is continuous dp`() {
        assertEquals(0f, CtBackgroundImage.blurSigmaFor(0))
        assertEquals(1f, CtBackgroundImage.blurSigmaFor(1))
        assertEquals(24f, CtBackgroundImage.blurSigmaFor(24))
        assertEquals(40f, CtBackgroundImage.blurSigmaFor(40))
        // 越界夹到边界
        assertEquals(0f, CtBackgroundImage.blurSigmaFor(-5))
        assertEquals(40f, CtBackgroundImage.blurSigmaFor(99))
    }

    @Test
    fun `blur is disabled on devices without GPU blur`() {
        val selection = CtBackgroundSelection(
            useCustomImage = true,
            customImageFile = "bg_1.jpg",
            customBlur = 2,
        )
        assertEquals(0f, ctBackgroundBlurSigma(selection, CtLightCapability.DEGRADED))
        assertEquals(0f, ctBackgroundBlurSigma(selection, CtLightCapability.OFF))
        assertEquals(
            CtBackgroundImage.blurSigmaFor(2),
            ctBackgroundBlurSigma(selection, CtLightCapability.FULL),
        )
    }

    @Test
    fun `blur label shows continuous dp`() {
        assertEquals("不模糊", CtBackgroundImage.blurLabel(0))
        assertEquals("1 dp", CtBackgroundImage.blurLabel(1))
        assertEquals("24 dp", CtBackgroundImage.blurLabel(24))
        assertEquals("40 dp", CtBackgroundImage.blurLabel(40))
        // 越界夹到边界
        assertEquals("40 dp", CtBackgroundImage.blurLabel(99))
    }

    @Test
    fun `compression constants match the spec`() {
        assertEquals(2048, CtBackgroundImage.MAX_LONG_EDGE)
        assertEquals(85, CtBackgroundImage.JPEG_QUALITY)
        assertEquals("backgrounds", CtBackgroundImage.DIR_NAME)
    }

    @Test
    fun `contrast helper is symmetric and bounded`() {
        val a = androidx.compose.ui.graphics.Color(0xFF000000)
        val b = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
        assertEquals(21.0, lucidContrastRatio(a, b), 0.01)
        assertEquals(lucidContrastRatio(a, b), lucidContrastRatio(b, a), 0.0001)
        // 完全不透明时 = 实色对比度
        assertEquals(
            lucidContrastRatio(a, b),
            ctLightContrastFloor(a, b, a, 1f),
            0.0001,
        )
    }

    /** 测试探针：直接读预设表（避免测试里重复写映射）。 */
    private object CtBackgroundColorsProbe {
        fun presetColors(preset: CtBackgroundPreset, dark: Boolean) = CtBackgroundPalette.of(preset, dark)
    }
}


