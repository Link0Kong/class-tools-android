package com.furina.classtools.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * TalkBack 排序动作文案/可用性单测（纯逻辑，无 Compose 依赖）。
 */
class CtA11yFormatTest {

    @Test
    fun positionText_formatsOneBasedAndHandlesEmpty() {
        assertEquals("第 1 个，共 10 个", CtA11yFormat.positionText(0, 10))
        assertEquals("第 10 个，共 10 个", CtA11yFormat.positionText(9, 10))
        assertEquals("", CtA11yFormat.positionText(0, 0))
        assertEquals("", CtA11yFormat.positionText(-1, 5))
    }

    @Test
    fun draggingText_prependsState() {
        assertEquals("拖动中，第 2 个，共 10 个", CtA11yFormat.draggingText(1, 10))
        assertEquals("拖动中", CtA11yFormat.draggingText(0, 0))
    }

    @Test
    fun reorderActionLabels_firstItemHasNoMoveUp() {
        assertEquals(listOf("下移"), CtA11yFormat.reorderActionLabels(0, 3))
    }

    @Test
    fun reorderActionLabels_middleItemHasBoth() {
        assertEquals(listOf("上移", "下移"), CtA11yFormat.reorderActionLabels(1, 3))
    }

    @Test
    fun reorderActionLabels_lastItemHasNoMoveDown() {
        assertEquals(listOf("上移"), CtA11yFormat.reorderActionLabels(2, 3))
    }

    @Test
    fun reorderActionLabels_singleItemHasNoActions() {
        assertEquals(emptyList<String>(), CtA11yFormat.reorderActionLabels(0, 1))
        assertEquals(emptyList<String>(), CtA11yFormat.reorderActionLabels(0, 0))
    }

    @Test
    fun movedText_upAndDown() {
        assertEquals("已上移到第 1 个，共 10 个", CtA11yFormat.movedText(3, 0, 10))
        assertEquals("已下移到第 4 个，共 10 个", CtA11yFormat.movedText(0, 3, 10))
        assertNull(CtA11yFormat.movedText(2, 2, 10))
    }
}
