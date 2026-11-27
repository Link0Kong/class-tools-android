package com.furina.classtools.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 覆盖提示条避让顶栏的纵向定位规则（标题/返回/缓存等动作不被压住）。 */
class CtFeedbackLayoutTest {

    @Test
    fun `toast sits above the top bar when the safe band has room`() {
        // 隐藏状态栏时页面顶部留有一条安全带（状态栏高度 + 呼吸区），提示优先落在这里。
        assertEquals(38, feedbackTopOffset(safeTop = 0, topBarTop = 164, topBarBottom = 332, popupHeight = 105, gap = 21))
    }

    @Test
    fun `toast exactly fits when the band is as tall as the toast plus the gap`() {
        assertEquals(0, feedbackTopOffset(safeTop = 0, topBarTop = 126, topBarBottom = 294, popupHeight = 105, gap = 21))
    }

    @Test
    fun `toast drops below the top bar when the safe band is too short`() {
        // 状态栏可见时其下方就是顶栏，上方放不下 → 落到顶栏下方，仍不压标题。
        assertEquals(357, feedbackTopOffset(safeTop = 63, topBarTop = 63, topBarBottom = 336, popupHeight = 135, gap = 21))
    }

    @Test
    fun `toast keeps the safe area top when the page has no top bar`() {
        assertEquals(84, feedbackTopOffset(safeTop = 63, topBarTop = 0, topBarBottom = 0, popupHeight = 135, gap = 21))
    }

    @Test
    fun `stale half-registered top bar is ignored`() {
        assertEquals(84, feedbackTopOffset(safeTop = 63, topBarTop = 0, topBarBottom = 336, popupHeight = 135, gap = 21))
        assertEquals(84, feedbackTopOffset(safeTop = 63, topBarTop = 200, topBarBottom = 0, popupHeight = 135, gap = 21))
    }

    @Test
    fun `toast never starts above the window`() {
        val y = feedbackTopOffset(safeTop = 0, topBarTop = 10, topBarBottom = 178, popupHeight = 105, gap = 21)
        assertTrue("y=$y", y >= 0)
    }
}
