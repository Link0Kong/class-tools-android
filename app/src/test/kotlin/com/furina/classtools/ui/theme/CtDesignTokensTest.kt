package com.furina.classtools.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// =============================================================================
// 统一设计令牌锁定测试（2026-11-19 系统性视觉/动效重构）
//
// 令牌源 = ui/theme/CtDesign.kt。这里锁定三类东西：
//  1) 任务规范要求的「刻度」（色板 5 级容器 / 圆角 / 间距 / 字号 / 时长 / 弹簧）；
//  2) 跨端一致性（品牌三色与鸿蒙 Tokens.ets 同值，布局数值按 Expressive 上移一档时有明确记录）；
//  3) 深色完整色板 AA：所有正文/次级文字与所在底色对比度 ≥4.5（逐对核算，不做抽样）。
// =============================================================================

class CtDesignTokensTest {

    // ---- WCAG 2.1 对比度（纯函数实现；不依赖 Android） ----

    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    // ---- 1. 色板 ----
    @Test
    fun `dark notice detail surface supports body text and outlined actions`() {
        val scheme = CtDarkColorScheme
        val panel = scheme.surfaceContainerHigh
        for (text in listOf(scheme.onSurface, scheme.onSurfaceVariant, scheme.primary)) {
            assertTrue(contrast(text, panel) >= 4.5)
        }
        assertTrue(contrast(scheme.outline, panel) >= 3.0)
        assertTrue(contrast(scheme.onPrimary, scheme.primary) >= 4.5)
    }


    @Test
    fun `品牌三色与网页主题同值（跨端一致）`() {
        assertEquals(Color(0xFF5E8F87), CtPalette.MistTeal)
        assertEquals(Color(0xFF7D9EBB), CtPalette.SlateBlue)
        assertEquals(Color(0xFFC6A475), CtPalette.WarmSand)
        assertEquals(CtPalette.MistTeal, BrandMistTeal)
        assertEquals(CtPalette.SlateBlue, BrandSlateBlue)
        assertEquals(CtPalette.WarmSand, BrandWarmSand)
    }

    @Test
    fun `浅色与深色都提供 5 级 surface 容器（M3 tonal 扩展色板）`() {
        val light = listOf(
            CtPalette.Surface,
            CtPalette.ContainerLow,
            CtPalette.Container,
            CtPalette.ContainerHigh,
            CtPalette.ContainerHighest,
        ).map { luminance(it) }
        // 浅色：容器越「高」越暗
        light.zipWithNext().forEach { (a, b) -> assertTrue("light container 应逐级变暗", a >= b) }

        val dark = listOf(
            CtPalette.DarkBg,
            CtPalette.DarkContainerLow,
            CtPalette.DarkContainer,
            CtPalette.DarkContainerHigh,
            CtPalette.DarkContainerHighest,
        ).map { luminance(it) }
        // 深色：容器越「高」越亮
        dark.zipWithNext().forEach { (a, b) -> assertTrue("dark container 应逐级变亮", a <= b) }
        assertEquals(Color(0xFF111614), CtPalette.DarkBg)
        assertEquals(Color(0xFF1A211E), CtPalette.DarkContainerLow)
        assertEquals(Color(0xFF29332D), CtPalette.DarkContainerHigh)
    }

    // ---- 2. 深色 / 浅色 AA ----

    @Test
    fun `浅色正文与次级文字对底色 AA（≥4_5）`() {
        listOf(CtPalette.Surface, CtPalette.Container, CtPalette.Bg).forEach { bg ->
            assertTrue("textPrimary/$bg", contrast(CtPalette.TextPrimary, bg) >= 4.5)
            assertTrue("textSecondary/$bg", contrast(CtPalette.TextSecondary, bg) >= 4.5)
            assertTrue("textTertiary/$bg", contrast(CtPalette.TextTertiary, bg) >= 4.5)
        }
    }

    @Test
    fun `深色正文与次级文字对底色 AA（≥4_5）`() {
        val darks = listOf(
            CtPalette.DarkBg,
            CtPalette.DarkSurface,
            CtPalette.DarkContainerHigh,
            CtPalette.DarkContainerHighest,
        )
        darks.forEach { bg ->
            assertTrue("darkTextPrimary/$bg", contrast(CtPalette.DarkTextPrimary, bg) >= 4.5)
            assertTrue("darkTextSecondary/$bg", contrast(CtPalette.DarkTextSecondary, bg) >= 4.5)
            assertTrue("darkTextTertiary/$bg", contrast(CtPalette.DarkTextTertiary, bg) >= 4.5)
        }
    }

    @Test
    fun `语义色包 CtColorLight 与 CtColorDark 所有文字对 AA`() {
        val light = ctColorOf(dark = false)
        listOf(
            light.textPrimary,
            light.textSecondary,
            light.textTertiary,
        ).forEach { fg ->
            assertTrue("light fg on surface", contrast(fg, light.surface) >= 4.5)
            assertTrue("light fg on background", contrast(fg, light.background) >= 4.5)
        }
        assertTrue("onPrimary/primary", contrast(light.onPrimary, light.primary) >= 4.5)
        assertTrue("onError/error", contrast(light.onError, light.error) >= 4.5)
        assertTrue("heroTitle/heroPaper", contrast(light.heroTitle, light.heroPaper) >= 4.5)
        assertTrue("heroSub/heroPaper", contrast(light.heroSub, light.heroPaper) >= 4.5)

        val dark = ctColorOf(dark = true)
        listOf(
            dark.textPrimary,
            dark.textSecondary,
            dark.textTertiary,
        ).forEach { fg ->
            assertTrue("dark fg on surface", contrast(fg, dark.surface) >= 4.5)
            assertTrue("dark fg on containerHigh", contrast(fg, dark.containerHigh) >= 4.5)
        }
        assertTrue("dark onPrimary/primary", contrast(dark.onPrimary, dark.primary) >= 4.5)
        assertTrue("dark onError/error", contrast(dark.onError, dark.error) >= 4.5)
        assertTrue("dark heroTitle/heroPaper", contrast(dark.heroTitle, dark.heroPaper) >= 4.5)
        assertTrue("dark heroSub/heroPaper", contrast(dark.heroSub, dark.heroPaper) >= 4.5)
    }

    @Test
    fun `工具强调色的文字档对白底 AA（蓝沙绿梅陶琥珀水）`() {
        val accents = ctAccentsOf(dark = false)
        listOf(
            "teal" to accents.teal,
            "blue" to accents.blue,
            "sand" to accents.sand,
            "leaf" to accents.leaf,
            "plum" to accents.plum,
            "clay" to accents.clay,
            "amber" to accents.amber,
            "water" to accents.water,
        ).forEach { (name, ref) ->
            assertTrue("$name textRef on surface", contrast(ref.textRef, CtPalette.Surface) >= 4.5)
        }
        val dark = ctAccentsOf(dark = true)
        listOf(dark.teal, dark.blue, dark.sand, dark.leaf, dark.plum, dark.clay, dark.amber, dark.water)
            .forEach { ref ->
                assertTrue("dark accent on surface", contrast(ref.textRef, CtPalette.DarkSurface) >= 4.5)
            }
    }
    // ---- 3. 形状 / 间距 / 字号 ----

    @Test
    fun `圆角全部落在 MD3 十档（card 12 输入 12 hero 28 弹层 28 · 2026-11-21）`() {
        assertEquals(12.dp, CtRadius.card)
        assertEquals(12.dp, CtRadius.input)
        assertEquals(28.dp, CtRadius.hero)
        assertEquals(28.dp, CtRadius.sheetTop)
        assertEquals(28.dp, CtRadius.dialog)
        assertEquals(999.dp, CtRadius.pill)
        assertEquals(8.dp, CtRadius.cardCompact)
        assertEquals(8.dp, CtRadius.chip)
        assertEquals(8.dp, CtRadius.cardPressed)
        // M3 十档：0 / 4 / 8 / 12 / 16 / 20 / 28 / 32 / 48 / Full
        val steps = setOf(0, 4, 8, 12, 16, 20, 28, 32, 48)
        listOf(
            CtRadius.card,
            CtRadius.cardCompact,
            CtRadius.hero,
            CtRadius.input,
            CtRadius.sheetTop,
            CtRadius.dialog,
            CtRadius.tile,
            CtRadius.chip,
            CtRadius.cardPressed,
            CtRadius.largeIncreased,
            CtRadius.extraLargeIncreased,
        ).forEach { radius ->
            assertTrue("${radius.value}dp 不在 MD3 十档内", radius.value.toInt() in steps)
        }
    }

    @Test
    fun `间距 token 与 MD3 页面边距（compact 16 medium 24）`() {
        // 2026-11-21 MD3 layout：页面 margin 只有 16 / 24 两档
        assertEquals(16.dp, CtSpace.pagePhone)
        assertEquals(24.dp, CtSpace.pageFold)
        assertEquals(24.dp, CtSpace.pageTablet)
        assertEquals(12.dp, CtSpace.cardGap)
        assertEquals(16.dp, CtSpace.cardPadding)
        assertEquals(24.dp, CtSpace.sectionGap)
        // 页面边距三档 = HarmonyTokens 别名（跨端不再要求一致，别名仍指向同一值）
        assertEquals(HarmonyTokens.PagePadding, CtSpace.pagePhone)
        assertEquals(HarmonyTokens.PagePaddingFold, CtSpace.pageFold)
        assertEquals(HarmonyTokens.PagePaddingTablet, CtSpace.pageTablet)
    }

    @Test
    fun `字体层级为 M3 映射 28 24 22 16 14 14 12 与中文行高`() {
        assertEquals(28.sp, CtType.displayTitleSize)
        assertEquals(24.sp, CtType.titleSize)
        assertEquals(22.sp, CtType.headlineSize)
        assertEquals(16.sp, CtType.title16Size)
        assertEquals(14.sp, CtType.bodySize)
        assertEquals(14.sp, CtType.labelSize)
        assertEquals(12.sp, CtType.captionSize)

        assertEquals(28.sp, CtTypeScale.display.fontSize)
        assertEquals(24.sp, CtTypeScale.title.fontSize)
        assertEquals(22.sp, CtTypeScale.headline.fontSize)
        assertEquals(16.sp, CtTypeScale.title16.fontSize)
        assertEquals(14.sp, CtTypeScale.body.fontSize)
        assertEquals(14.sp, CtTypeScale.label.fontSize)
        assertEquals(12.sp, CtTypeScale.caption.fontSize)

        // 中文行高 = 官方行高 × 1.07，比例落在 1.29–1.65
        listOf(
            CtTypeScale.display,
            CtTypeScale.title,
            CtTypeScale.headline,
            CtTypeScale.title16,
            CtTypeScale.body,
            CtTypeScale.label,
            CtTypeScale.caption,
        ).forEach { style ->
            val ratio = style.lineHeight.value / style.fontSize.value
            assertTrue("lineRatio=$ratio", ratio in 1.28f..1.65f)
        }
        // 旧入口 CtText 指向同一套字号（页面零改动迁移）
        assertEquals(CtTypeScale.title, CtText.title)
        assertEquals(CtTypeScale.headline, CtText.heading)
        assertEquals(CtTypeScale.body, CtText.body)
        assertEquals(CtTypeScale.caption, CtText.caption)
    }

    // ---- 4. 动效 ----

    @Test
    fun `时长 120 200 300 400 与弹簧参数（damping 0_75 到 0_9 stiffness 380）`() {
        assertEquals(120, CtMotionTokens.fastMs)
        assertEquals(200, CtMotionTokens.shortMs)
        assertEquals(300, CtMotionTokens.standardMs)
        assertEquals(400, CtMotionTokens.slowMs)

        assertTrue(CtMotionTokens.springFastDamping in 0.75f..0.9f)
        assertTrue(CtMotionTokens.springSpatialDamping in 0.75f..0.9f)
        // 容器变换用的快档弹簧与按压弹簧同为 0.75（M3 Expressive spatial fast），慢档为大面积专用的 0.9
        assertTrue(CtMotionTokens.springFastDamping in 0.75f..0.9f)
        assertEquals(380f, CtMotionTokens.springSpatialStiffness)

        // CtMotion 的弹簧对象由令牌构造
        assertEquals(0.8f, CtMotion.spatial.dampingRatio)
        assertEquals(380f, CtMotion.spatial.stiffness)
        assertEquals(0.75f, CtMotion.spatialFast.dampingRatio)
        assertEquals(1f, CtMotion.effects.dampingRatio)
        assertEquals(0.97f, CtMotionTokens.pressScale)
        assertEquals(40L, CtMotionTokens.staggerStepMs)
        assertEquals(8f, CtMotionTokens.itemEnterRise)
    }

    @Test
    fun `旧名 HarmonyMotion 映射到新时长（页面转场 28 与 24vp 位移保留）`() {
        assertEquals(CtMotionTokens.fastMs, HarmonyMotion.FAST_MS)
        assertEquals(CtMotionTokens.shortMs, HarmonyMotion.NORMAL_MS)
        assertEquals(CtMotionTokens.slowMs, HarmonyMotion.SLOW_MS)
        assertEquals(28.dp, HarmonyMotion.PageEnterShift)
        assertEquals(24.dp, HarmonyMotion.PageExitShift)
        assertEquals(0.985f, HarmonyMotion.PageEnterScale)
        assertEquals(0.99f, HarmonyMotion.PageExitScale)
        assertEquals(10.dp, HarmonyMotion.ListItemRise)
    }

    // ---- 5. 明暗语义色包 ----

    @Test
    fun `明暗两套语义色字段完整且深色为中性深板岩`() {
        val light = ctColorOf(dark = false)
        val dark = ctColorOf(dark = true)
        assertNotEquals(light.background, dark.background)
        assertEquals(CtPalette.DarkBg, dark.background)
        assertEquals(CtPalette.DarkSurface, dark.surface)
        assertTrue(dark.dark)
        assertTrue(!light.dark)
        // hero 纸面：浅色为鸿蒙实测值，深色为中性（不是大面积品牌绿）
        assertEquals(CtPalette.HeroPaper, light.heroPaper)
        assertNotEquals(light.heroPaper, dark.heroPaper)
        // 骨架：明暗各一套
        assertNotEquals(light.skeletonBase, dark.skeletonBase)
    }

    @Test
    fun `颜色方案由令牌构字（卡片底等于 surface 令牌）`() {
        val (lightScheme, darkScheme) = CtThemeColorSchemes
        assertEquals(CtPalette.Surface, lightScheme.surface)
        assertEquals(CtPalette.Bg, lightScheme.background)
        assertEquals(CtPalette.DarkSurface, darkScheme.surface)
        assertEquals(CtPalette.DarkBg, darkScheme.background)
        assertEquals(CtPalette.TextPrimary, lightScheme.onSurface)
        assertEquals(CtPalette.DarkTextPrimary, darkScheme.onSurface)
        // 深色 error 用 M3 深色档（#FFB4AB，对深底 ≥6:1）
        assertEquals(Color(0xFFFFB4AB), darkScheme.error)
    }
}
