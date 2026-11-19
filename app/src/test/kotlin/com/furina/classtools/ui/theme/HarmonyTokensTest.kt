package com.furina.classtools.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设计令牌锁定（原 `HarmonyTokensTest`，2026-11-19 随「统一视觉语言」重构更新）。
 *
 * 令牌源：`ui/theme/CtDesign.kt`（[CtPalette]/[CtRadius]/[CtSpace]/[CtType]/[CtSize]）。
 * 旧入口 `HarmonyTokens` / `CtText` / `harmonyColors()` 保留为同一令牌的别名，
 * 本测试同时锁定「旧名可用」与「新口径正确」：
 *
 * - 跨端一致（与鸿蒙 `Tokens.ets` 同值）：品牌三色、页面边距 16/24/32、GAP 12、
 *   顶栏 56、磁贴 96/40/24/12、内容宽 880/1080/960、骨架色。
 * - 安卓按 M3 Expressive **上移一档**：卡片圆角 18→**20**、hero 24→**28**、页面主标题 26→**28**；
 *   动效时长按任务统一为 120/200/300/400。
 */
class HarmonyTokensTest {

    @Test
    fun `间距与页面边距与鸿蒙 Tokens 同值`() {
        // 2026-11-19 鸿蒙间隔规范：PAGE_PADDING 由 16 → 20（屏幕边缘 20vp）
        assertEquals(20.dp, HarmonyTokens.PagePadding)
        assertEquals(24.dp, HarmonyTokens.PagePaddingFold)
        assertEquals(32.dp, HarmonyTokens.PagePaddingTablet)
        // 2026-11-19 8vp 网格：GAP 由 12 → 16（卡片间距 16vp）
        assertEquals(16.dp, HarmonyTokens.Gap)
        assertEquals(16.dp, HarmonyTokens.CardPadding)
        assertEquals(12.dp, HarmonyTokens.TilePadding)   // ToolTile padding SPACE_12
        assertEquals(8.dp, HarmonyTokens.BreathTop)      // BREATH_TOP
        assertEquals(12.dp, HarmonyTokens.BreathTopWide) // BREATH_TOP_WIDE
        assertEquals(0.5.dp, HarmonyTokens.HairlineWidth)
    }

    @Test
    fun `圆角按 M3 Expressive 上移一档（卡片 20 hero 28 输入 14 弹层 28）`() {
        assertEquals(20.dp, HarmonyTokens.RadiusCard)   // 鸿蒙 18 → 安卓 20（Expressive）
        assertEquals(28.dp, HarmonyTokens.RadiusHero)   // 鸿蒙 24 → 安卓 28
        assertEquals(14.dp, HarmonyTokens.RadiusInput)  // 鸿蒙 14（不变）
        assertEquals(28.dp, HarmonyTokens.RadiusSheet)  // 弹层 28
        assertEquals(12.dp, HarmonyTokens.RadiusTile)   // 磁贴图标块 12（不变）
        // 紧凑档保留鸿蒙原值，供小磁贴/编辑格使用
        assertEquals(18.dp, CtRadius.cardCompact)
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
    fun `字号层级为新阶梯 28 24 20 16 14 14 12`() {
        assertEquals(28, CtType.displayTitleSize.value.toInt())
        assertEquals(24, CtType.titleSize.value.toInt())
        assertEquals(20, CtType.headlineSize.value.toInt())
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
        assertEquals(3.dp, HarmonyTokens.HeroShadowElevation)
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
