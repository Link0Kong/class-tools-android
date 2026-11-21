package com.furina.classtools.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设计令牌锁定（2026-11-21 按 MD3 官方知识库回归）。
 *
 * 令牌源：`ui/theme/CtDesign.kt`（[CtPalette]/[CtRadius]/[CtSpace]/[CtType]/[CtSize]）。
 * 旧入口 `HarmonyTokens` / `CtText` / `harmonyColors()` 保留为同一令牌的别名，
 * 本测试同时锁定「旧名可用」与「新口径正确」：
 *
 * - MD3 layout：页面边距 compact 16 / medium+ 24；卡片间距 12；卡内边距 16。
 * - MD3 形状十档：卡片 12 / 紧凑卡 8 / hero 28 / 输入 12 / 图标块 12 / 弹层 28。
 * - MD3 字阶：页面标题 Headline Medium 28、hero Headline Small 24、分区 Title Large 22。
 */
class HarmonyTokensTest {

    @Test
    fun `间距与页面边距为 MD3 官方口径`() {
        // MD3 layout：compact 16dp，medium 及以上 24dp（不再用鸿蒙 20/24/32）
        assertEquals(16.dp, HarmonyTokens.PagePadding)
        assertEquals(24.dp, HarmonyTokens.PagePaddingFold)
        assertEquals(24.dp, HarmonyTokens.PagePaddingTablet)
        // 卡片间距 12（space150；MD3 卡片页建议 ≤8，2 列大卡取 12 属产品决策）
        assertEquals(12.dp, HarmonyTokens.Gap)
        assertEquals(16.dp, HarmonyTokens.CardPadding)
        assertEquals(12.dp, HarmonyTokens.TilePadding)   // ToolTile padding SPACE_12
        assertEquals(8.dp, HarmonyTokens.BreathTop)      // BREATH_TOP
        assertEquals(12.dp, HarmonyTokens.BreathTopWide) // BREATH_TOP_WIDE
        assertEquals(0.5.dp, HarmonyTokens.HairlineWidth)
    }

    @Test
    fun `圆角回到 MD3 十档（卡片 12 hero 28 输入 12 弹层 28）`() {
        assertEquals(12.dp, HarmonyTokens.RadiusCard)   // M3 Card 默认 medium
        assertEquals(28.dp, HarmonyTokens.RadiusHero)   // extra large
        assertEquals(12.dp, HarmonyTokens.RadiusInput)  // medium
        assertEquals(28.dp, HarmonyTokens.RadiusSheet)  // extra large
        assertEquals(12.dp, HarmonyTokens.RadiusTile)   // 磁贴图标块 12（medium）
        // 紧凑档 8（small）
        assertEquals(8.dp, CtRadius.cardCompact)
    }

    @Test
    fun `内容宽度与底部占位与鸿蒙 Breakpoints 同值`() {
        assertEquals(880.dp, HarmonyTokens.ContentMaxWidthPhone)
        assertEquals(1080.dp, HarmonyTokens.ContentMaxWidthWide)
        assertEquals(960.dp, HarmonyTokens.ToolMaxWide)
        assertEquals(96.dp, HarmonyTokens.FloatBarSpace)
        assertEquals(32.dp, HarmonyTokens.SideBarSpace)
        assertEquals(104.dp, HarmonyTokens.SideNavWidth)
    }

    @Test
    fun `顶栏与磁贴口径与鸿蒙 PageHeader、ToolTile 同值`() {
        assertEquals(56.dp, HarmonyTokens.PageHeaderHeight) // PageHeader headerBar height 56
        assertEquals(96.dp, HarmonyTokens.TileMinHeight)
        assertEquals(40.dp, HarmonyTokens.GlyphBox)
        assertEquals(24.dp, HarmonyTokens.TileGlyph)
        assertEquals(16.dp, HarmonyTokens.TileTextGap)
        assertEquals(4.dp, HarmonyTokens.TextGap)
        assertEquals(12.dp, HarmonyTokens.TextGapHorizontal)
        assertEquals(20.dp, HarmonyTokens.ElementGapLarge)
        assertEquals(12.dp, HarmonyTokens.ElementGap)
    }

    @Test
    fun `字号层级为 MD3 映射 28 24 22 16 14 14 12`() {
        assertEquals(28, CtType.displayTitleSize.value.toInt())
        assertEquals(24, CtType.titleSize.value.toInt())
        assertEquals(22, CtType.headlineSize.value.toInt())
        assertEquals(16, CtType.title16Size.value.toInt())
        assertEquals(14, CtType.bodySize.value.toInt())
        assertEquals(14, CtType.labelSize.value.toInt())
        assertEquals(12, CtType.captionSize.value.toInt())

        // 旧入口 CtText 指向同一套字号（页面零改动迁移）
        assertEquals(CtTypeScale.title.fontSize, CtText.title.fontSize)
        assertEquals(CtTypeScale.headline.fontSize, CtText.heading.fontSize)
        assertEquals(CtTypeScale.title16.fontSize, CtText.title16.fontSize)
        assertEquals(CtTypeScale.body.fontSize, CtText.body.fontSize)
        assertEquals(CtTypeScale.label.fontSize, CtText.label.fontSize)
        assertEquals(CtTypeScale.caption.fontSize, CtText.caption.fontSize)
    }

    @Test
    fun `动效时长统一为 120 200 300 400（旧名映射保留）`() {
        assertEquals(120, HarmonyMotion.FAST_MS)
        assertEquals(200, HarmonyMotion.NORMAL_MS)
        assertEquals(400, HarmonyMotion.SLOW_MS)
        // 页面转场位移（鸿蒙 Motion.pageEnter/Exit 的 28/24vp 与 0.985/0.99 缩放）
        assertEquals(28.dp, HarmonyMotion.PageEnterShift)
        assertEquals(24.dp, HarmonyMotion.PageExitShift)
        assertEquals(0.985f, HarmonyMotion.PageEnterScale)
        assertEquals(0.99f, HarmonyMotion.PageExitScale)
        assertEquals(0.97f, HarmonyMotion.PressScale)
        assertEquals(0.92f, HarmonyMotion.PressAlpha)
        assertEquals(0.9f, HarmonyMotion.PopInScale)
        assertEquals(10.dp, HarmonyMotion.ListItemRise)
        assertEquals(0.55f, HarmonyMotion.SkeletonDimAlpha)
        assertEquals(900, HarmonyMotion.SkeletonPulseMs)
    }

    @Test
    fun `骨架色取令牌（明暗各一套，深色更暗）`() {
        assertEquals(Color(0xFFE9EFF1), HarmonyTokens.SkeletonStrong)
        assertEquals(Color(0xFFEFF3F4), HarmonyTokens.SkeletonBase)
        assertEquals(Color(0xFFF5F7F8), HarmonyTokens.SkeletonSoft)
        assertEquals(Color(0x0F4E9082), HarmonyTokens.HeroShadowColor)
        assertEquals(1.dp, HarmonyTokens.HeroShadowElevation)
        assertNotEquals(HarmonyTokens.DarkSkeletonStrong, HarmonyTokens.SkeletonStrong)
        assertEquals(HarmonyTokens.CardShadowColor, CtPalette.CardShadow)
    }

    @Test
    fun `页面语义间距默认值与令牌一致`() {
        assertEquals(CtSpace.pagePhone, CtSpacing().screenHorizontal)
        assertEquals(CtSpace.cardGap, CtSpacing().cardGap)
        assertEquals(CtSpace.cardPadding, CtSpacing().cardPadding)
        assertEquals(CtSpace.sectionGap, CtSpacing().sectionGap)
        assertEquals(CtSpace.lg, CtSpacing().elementGapLarge)
        assertEquals(CtSpace.sm, CtSpacing().elementGap)
        assertEquals(CtSpace.xxs, CtSpacing().textGapVertical)
        assertEquals(CtSpace.sm, CtSpacing().textGapHorizontal)
        assertTrue(CtSpacing().minTouchTarget >= 48.dp)
    }

    @Test
    fun `明暗两套鸿蒙中性色都能取到卡片与骨架色`() {
        val light = harmonyColors(dark = false)
        val dark = harmonyColors(dark = true)
        assertEquals(CtPalette.Surface, light.card)
        assertEquals(CtPalette.DarkSurface, dark.card)
        assertEquals(CtPalette.Bg, light.background)
        assertEquals(CtPalette.DarkBg, dark.background)
        assertEquals(CtPalette.SkeletonBase, light.skeletonBase)
        assertEquals(CtPalette.DarkSkeletonBase, dark.skeletonBase)
        assertEquals(CtPalette.HeroPaper, light.heroPaper)
        assertTrue(dark.heroPaper != light.heroPaper)
    }
}
