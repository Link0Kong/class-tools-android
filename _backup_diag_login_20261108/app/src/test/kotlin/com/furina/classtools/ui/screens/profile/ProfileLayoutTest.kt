package com.furina.classtools.ui.screens.profile

import com.furina.classtools.ui.adaptive.CtAdaptive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「我的」页自适应布局纯逻辑单测（task-25）：断点分栏/卡片覆盖/间距。
 */
class ProfileLayoutTest {

    @Test
    fun compact_singleColumnOrder() {
        val layout = ProfileLayoutLogic.layoutFor(CtAdaptive.specFor(412))
        assertFalse(layout.twoPane)
        assertEquals(
            listOf(
                ProfileCard.ACCOUNT,
                ProfileCard.SYNC,
                ProfileCard.DATA_PRIVACY,
                ProfileCard.PERMISSION,
                ProfileCard.APPEARANCE,
                ProfileCard.ABOUT,
                ProfileCard.SIGN_OUT,
            ),
            layout.single,
        )
        assertTrue(layout.left.isEmpty())
        assertTrue(layout.right.isEmpty())
    }

    @Test
    fun medium_stillSingleColumn() {
        val layout = ProfileLayoutLogic.layoutFor(CtAdaptive.specFor(700))
        assertFalse(layout.twoPane)
        assertEquals(24, layout.horizontalPaddingDp)
        assertEquals(840, layout.contentMaxWidthDp)
    }

    @Test
    fun expanded_twoPaneSplit() {
        val layout = ProfileLayoutLogic.layoutFor(CtAdaptive.specFor(1000))
        assertTrue(layout.twoPane)
        assertEquals(listOf(ProfileCard.ACCOUNT, ProfileCard.SYNC, ProfileCard.PERMISSION), layout.left)
        assertEquals(
            listOf(ProfileCard.DATA_PRIVACY, ProfileCard.APPEARANCE, ProfileCard.ABOUT, ProfileCard.SIGN_OUT),
            layout.right,
        )
        assertEquals(32, layout.horizontalPaddingDp)
        assertEquals(1200, layout.contentMaxWidthDp)
        assertEquals(0.55f, layout.mainPaneWeight, 0.001f)
    }

    @Test
    fun veryWide_mainPaneWeightGrows() {
        val layout = ProfileLayoutLogic.layoutFor(CtAdaptive.specFor(1280))
        assertEquals(0.58f, layout.mainPaneWeight, 0.001f)
    }

    @Test
    fun allCardsCovered_noLossNoDuplicate() {
        val single = ProfileLayoutLogic.layoutFor(CtAdaptive.specFor(412))
        val twoPane = ProfileLayoutLogic.layoutFor(CtAdaptive.specFor(1000))
        assertTrue(ProfileLayoutLogic.allCardsCovered(single))
        assertTrue(ProfileLayoutLogic.allCardsCovered(twoPane))
        assertEquals(ProfileCard.entries.size, twoPane.left.size + twoPane.right.size)
    }

    @Test
    fun tablet_densityTightened() {
        val compact = ProfileLayoutLogic.layoutFor(CtAdaptive.specFor(412))
        val tablet = ProfileLayoutLogic.layoutFor(CtAdaptive.specFor(1000))
        assertTrue(tablet.headerTopPaddingDp <= compact.headerTopPaddingDp)
        assertTrue(tablet.cardGapDp <= compact.cardGapDp)
    }
}
