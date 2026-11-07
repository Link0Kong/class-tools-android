package com.furina.classtools.ui.screens.all

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * TalkBack「上移 / 下移」动作 → 排序结果映射单测（复用 ToolOrderLogic.moveBy）。
 */
class ToolOrderA11yTest {

    private val order = listOf("draw", "group", "fit", "hw")

    @Test
    fun moveBy_moveUp() {
        assertEquals(listOf("draw", "fit", "group", "hw"), ToolOrderLogic.moveBy(order, "fit", -1))
    }

    @Test
    fun moveBy_moveDown() {
        assertEquals(listOf("group", "draw", "fit", "hw"), ToolOrderLogic.moveBy(order, "draw", +1))
    }

    @Test
    fun moveBy_firstItemUp_noopReturnsSameInstance() {
        assertSame(order, ToolOrderLogic.moveBy(order, "draw", -1))
    }

    @Test
    fun moveBy_lastItemDown_noopReturnsSameInstance() {
        assertSame(order, ToolOrderLogic.moveBy(order, "hw", +1))
    }

    @Test
    fun moveBy_unknownId_noop() {
        assertSame(order, ToolOrderLogic.moveBy(order, "power", +1))
        assertSame(order, ToolOrderLogic.moveBy(order, "power", -1))
    }

    @Test
    fun moveBy_preservesAllIds() {
        val moved = ToolOrderLogic.moveBy(order, "group", +1)
        assertEquals(order.sorted(), moved.sorted())
        assertEquals(order.size, moved.size)
    }
}
