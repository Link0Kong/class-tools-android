package com.furina.classtools.ui.components.dock

import org.junit.Assert.assertEquals
import org.junit.Test

/** 根页被工具页覆盖时必须首帧隐藏，否则会从半透明工具页下透出工作台内容。 */
class CtPageTransitionsTest {

    @Test
    fun `root is hidden on the first frame when covered by a tool page`() {
        assertEquals(0, CtPageTransitions.ROOT_COVERED_EXIT_MS)
    }

    @Test
    fun `tool page itself keeps its own enter and pop animation durations`() {
        assert(CtPageTransitions.HARMONY_PAGE_MS > 0)
        assert(CtPageTransitions.HARMONY_FADE_MS > 0)
    }
}
