package com.furina.classtools.ui.theme

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MD3 字阶 / 间距令牌单测（2026-11-21 按官方知识库回归）。
 *
 * 锁定口径（来源：lwhhz/Android-MD3-Knowledge，核验 2026-11-16）：
 * - **MD3 spacing 18 档 token**：0/2/4/6/8/10/12/14/16/20/24/32/36/40/48/56/64/72；
 * - **页面 margin 16（compact）/ 24（medium 及以上）**；
 * - **字号取官方 role**：Headline Medium 28、Headline Small 24、Title Large 22、Title Medium 16、
 *   Body Medium 14、Label Large 14、Body Small 12、Label Small 11；
 * - **中文行高 ×1.07**：28→39、24→34、22→30、16→26、14→21、12→17、11→17；
 * - 常规层级只用 400 / 500，页面标题不再是 700。
 */
class CtSpacingTokensTest {

    private fun dpOf(v: androidx.compose.ui.unit.Dp): Float = v.value

    /** MD3 官方 18 个 system spacing token（dp）。 */
    private val md3SpacingTokens = setOf(
        0f, 2f, 4f, 6f, 8f, 10f, 12f, 14f, 16f, 20f, 24f, 32f, 36f, 40f, 48f, 56f, 64f, 72f,
    )

    @Test
    fun `spacing tokens 全部落在 MD3 官方 18 档内`() {
        val tokens = listOf(
            "xxs" to CtSpace.xxs,
            "xs" to CtSpace.xs,
            "sm" to CtSpace.sm,
            "md" to CtSpace.md,
            "lg" to CtSpace.lg,
            "xl" to CtSpace.xl,
            "xxl" to CtSpace.xxl,
            "xxxl" to CtSpace.xxxl,
            "huge" to CtSpace.huge,
            "pagePhone" to CtSpace.pagePhone,
            "pageFold" to CtSpace.pageFold,
            "pageTablet" to CtSpace.pageTablet,
            "cardPadding" to CtSpace.cardPadding,
            "rowPaddingH" to CtSpace.rowPaddingH,
            "rowPaddingV" to CtSpace.rowPaddingV,
            "tilePadding" to CtSpace.tilePadding,
            "cardGap" to CtSpace.cardGap,
            "sectionGap" to CtSpace.sectionGap,
            "groupGap" to CtSpace.groupGap,
            "controlGap" to CtSpace.controlGap,
            "controlGapMedium" to CtSpace.controlGapMedium,
            "controlGapLarge" to CtSpace.controlGapLarge,
        )
        tokens.forEach { (name, value) ->
            val v = dpOf(value)
            assertTrue("$name=$v 不在 MD3 spacing token 内（不允许 14/18/22 之类临时值）", v in md3SpacingTokens)
        }
    }

    @Test
    fun `page margins follow MD3 layout spec`() {
        assertEquals(16f, dpOf(CtSpace.pagePhone), 0.0001f)
        assertEquals(24f, dpOf(CtSpace.pageFold), 0.0001f)
        assertEquals(24f, dpOf(CtSpace.pageTablet), 0.0001f)
    }

    @Test
    fun `card padding is 16 card gap 12 and section gap 24`() {
        assertEquals(16f, dpOf(CtSpace.cardPadding), 0.0001f)
        assertEquals(12f, dpOf(CtSpace.cardGap), 0.0001f)
        assertEquals(24f, dpOf(CtSpace.sectionGap), 0.0001f)
    }

    @Test
    fun `grid unit is 8`() {
        assertEquals(8f, dpOf(CtSpace.gridUnit), 0.0001f)
    }

    @Test
    fun `control gap floor is 8vp and tiers are ascending`() {
        assertEquals(8f, dpOf(CtSpace.controlGap), 0.0001f)
        assertEquals(12f, dpOf(CtSpace.controlGapMedium), 0.0001f)
        assertEquals(16f, dpOf(CtSpace.controlGapLarge), 0.0001f)
        assertTrue(
            "控件间距三档递增：${CtSpace.controlGap} < ${CtSpace.controlGapMedium} < ${CtSpace.controlGapLarge}",
            dpOf(CtSpace.controlGap) < dpOf(CtSpace.controlGapMedium) &&
                dpOf(CtSpace.controlGapMedium) < dpOf(CtSpace.controlGapLarge),
        )
        assertTrue(
            "controlGap=${CtSpace.controlGap} 不应大于 cardGap=${CtSpace.cardGap}",
            dpOf(CtSpace.controlGap) <= dpOf(CtSpace.cardGap),
        )
    }

    @Test
    fun `type scale uses MD3 roles 28 24 22 16 14 14 12`() {
        assertEquals(28f, CtType.displayTitleSize.value, 0.0001f)
        assertEquals(24f, CtType.titleSize.value, 0.0001f)
        assertEquals(22f, CtType.headlineSize.value, 0.0001f)
        assertEquals(16f, CtType.title16Size.value, 0.0001f)
        assertEquals(14f, CtType.bodySize.value, 0.0001f)
        assertEquals(14f, CtType.labelSize.value, 0.0001f)
        assertEquals(12f, CtType.captionSize.value, 0.0001f)
        assertEquals(11f, CtType.microSize.value, 0.0001f)
    }

    @Test
    fun `body range is 14 to 16 and caption is 12`() {
        assertTrue(CtType.bodySize.value in 14f..16f)
        assertTrue(CtType.labelSize.value in 14f..16f)
        assertEquals(12f, CtType.captionSize.value, 0.0001f)
    }

    @Test
    fun `headline range is 22 to 28`() {
        listOf(CtType.headlineSize, CtType.titleSize, CtType.displayTitleSize).forEach {
            assertTrue("${it.value} in 22..28", it.value in 22f..28f)
        }
    }

    @Test
    fun `chinese line heights are official times 1_07`() {
        assertEquals(39f, CtType.displayTitleLine.toFloat(), 0.0001f)
        assertEquals(34f, CtType.titleLine.toFloat(), 0.0001f)
        assertEquals(30f, CtType.headlineLine.toFloat(), 0.0001f)
        assertEquals(26f, CtType.title16Line.toFloat(), 0.0001f)
        assertEquals(21f, CtType.bodyLine.toFloat(), 0.0001f)
        assertEquals(21f, CtType.labelLine.toFloat(), 0.0001f)
        assertEquals(17f, CtType.captionLine.toFloat(), 0.0001f)
        assertEquals(17f, CtType.microLine.toFloat(), 0.0001f)
    }

    @Test
    fun `declared line ratios match the token table`() {
        assertEquals(CtType.displayTitleLine / 28f, CtType.lineRatioDisplay, 0.0001f)
        assertEquals(CtType.titleLine / 24f, CtType.lineRatioTitle, 0.0001f)
        assertEquals(CtType.headlineLine / 22f, CtType.lineRatioHeadline, 0.0001f)
        assertEquals(CtType.bodyLine / 14f, CtType.lineRatioBody, 0.0001f)
        assertEquals(CtType.captionLine / 12f, CtType.lineRatioCaption, 0.0001f)
    }

    @Test
    fun `font weights are regular medium and bold constants`() {
        assertEquals(400, CtType.weightNormal)
        assertEquals(500, CtType.weightMedium)
        assertEquals(700, CtType.weightBold)
        assertEquals(500, CtType.weightSemiBold)
    }

    @Test
    fun `regular hierarchy avoids bold 700`() {
        listOf("display" to CtTypeScale.display, "title" to CtTypeScale.title, "headline" to CtTypeScale.headline)
            .forEach { (name, style) ->
                assertNotEquals("$name 不应使用 700", FontWeight.Bold, style.fontWeight)
            }
        assertEquals(FontWeight.Normal, CtTypeScale.display.fontWeight)
        assertEquals(FontWeight.Normal, CtTypeScale.title.fontWeight)
        assertEquals(FontWeight.Normal, CtTypeScale.headline.fontWeight)
        assertEquals(FontWeight.Normal, CtTypeScale.body.fontWeight)
    }

    @Test
    fun `type scale line heights match tokens`() {
        assertEquals(CtType.displayTitleLine.toFloat(), CtTypeScale.display.lineHeight.value, 0.0001f)
        assertEquals(CtType.titleLine.toFloat(), CtTypeScale.title.lineHeight.value, 0.0001f)
        assertEquals(CtType.headlineLine.toFloat(), CtTypeScale.headline.lineHeight.value, 0.0001f)
        assertEquals(CtType.bodyLine.toFloat(), CtTypeScale.body.lineHeight.value, 0.0001f)
        assertEquals(CtType.labelLine.toFloat(), CtTypeScale.label.lineHeight.value, 0.0001f)
        assertEquals(CtType.captionLine.toFloat(), CtTypeScale.caption.lineHeight.value, 0.0001f)
    }

    @Test
    fun `type scale sizes match tokens`() {
        assertEquals(CtType.displayTitleSize, CtTypeScale.display.fontSize)
        assertEquals(CtType.titleSize, CtTypeScale.title.fontSize)
        assertEquals(CtType.headlineSize, CtTypeScale.headline.fontSize)
        assertEquals(CtType.title16Size, CtTypeScale.title16.fontSize)
        assertEquals(CtType.bodySize, CtTypeScale.body.fontSize)
        assertEquals(CtType.labelSize, CtTypeScale.label.fontSize)
        assertEquals(CtType.captionSize, CtTypeScale.caption.fontSize)
    }

    @Test
    fun `semantic styles point at the matching M3 typography roles`() {
        assertEquals(CtTypography.headlineMedium, CtTypeScale.display)
        assertEquals(CtTypography.headlineSmall, CtTypeScale.title)
        assertEquals(CtTypography.titleLarge, CtTypeScale.headline)
        assertEquals(CtTypography.titleMedium, CtTypeScale.title16)
        assertEquals(CtTypography.bodyMedium, CtTypeScale.body)
        assertEquals(CtTypography.labelLarge, CtTypeScale.label)
        assertEquals(CtTypography.bodySmall, CtTypeScale.caption)
        assertEquals(CtTypography.labelSmall, CtTypeScale.micro)
    }
}
