package com.furina.classtools.ui.screens.all

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 工具网格 3/4 列命中几何单测（task-25）：列数参数化后与 2 列旧行为一致。
 */
class ToolOrderGridColumnsTest {

    private val itemWidth = 100f
    private val itemHeight = 140f
    private val spacing = 10f

    @Test
    fun cellOf_twoColumns_legacyBehavior() {
        assertEquals(0 to 0, ToolOrderLogic.gridCellOf(10f, 10f, itemWidth, itemHeight, spacing, columns = 2))
        // 间隙中线（itemWidth + spacing/2 = 105）作为列分界
        assertEquals(0 to 0, ToolOrderLogic.gridCellOf(104f, 10f, itemWidth, itemHeight, spacing, columns = 2))
        assertEquals(1 to 0, ToolOrderLogic.gridCellOf(106f, 10f, itemWidth, itemHeight, spacing, columns = 2))
        assertEquals(1 to 1, ToolOrderLogic.gridCellOf(160f, 155f, itemWidth, itemHeight, spacing, columns = 2))
    }

    @Test
    fun cellOf_threeColumns() {
        val step = itemWidth + spacing // 110
        assertEquals(0 to 0, ToolOrderLogic.gridCellOf(step * 0.4f, 10f, itemWidth, itemHeight, spacing, columns = 3))
        assertEquals(1 to 0, ToolOrderLogic.gridCellOf(step * 1.4f, 10f, itemWidth, itemHeight, spacing, columns = 3))
        assertEquals(2 to 0, ToolOrderLogic.gridCellOf(step * 2.4f, 10f, itemWidth, itemHeight, spacing, columns = 3))
        // 第 2 行第 3 列
        assertEquals(2 to 1, ToolOrderLogic.gridCellOf(step * 2.4f, 155f, itemWidth, itemHeight, spacing, columns = 3))
    }

    @Test
    fun cellOf_fourColumns() {
        val step = itemWidth + spacing
        assertEquals(0 to 0, ToolOrderLogic.gridCellOf(step * 0.4f, 10f, itemWidth, itemHeight, spacing, columns = 4))
        assertEquals(1 to 0, ToolOrderLogic.gridCellOf(step * 1.4f, 10f, itemWidth, itemHeight, spacing, columns = 4))
        assertEquals(2 to 0, ToolOrderLogic.gridCellOf(step * 2.4f, 10f, itemWidth, itemHeight, spacing, columns = 4))
        assertEquals(3 to 0, ToolOrderLogic.gridCellOf(step * 3.4f, 10f, itemWidth, itemHeight, spacing, columns = 4))
        assertEquals(2 to 1, ToolOrderLogic.gridCellOf(step * 2.4f, 155f, itemWidth, itemHeight, spacing, columns = 4))
    }

    @Test
    fun cellOf_clampsOutsideGrid() {
        assertEquals(0 to 0, ToolOrderLogic.gridCellOf(-500f, -500f, itemWidth, itemHeight, spacing, columns = 3))
        // rowStep = 140+10 = 150 → floor(9999/150) = 66
        assertEquals(2 to 66, ToolOrderLogic.gridCellOf(9999f, 9999f, itemWidth, itemHeight, spacing, columns = 3))
        assertEquals(0 to 66, ToolOrderLogic.gridCellOf(1f, 9999f, itemWidth, itemHeight, spacing, columns = 1))
    }

    @Test
    fun gridTarget_threeColumns_mapsRowAndColumn() {
        val step = itemWidth + spacing
        // 6 项 3 列；拖第 0 项到第 2 行第 1 列（slot 3）
        assertEquals(
            3,
            ToolOrderLogic.gridTarget(
                pointerX = step * 0.4f,
                pointerY = 155f,
                itemWidth = itemWidth,
                itemHeight = itemHeight,
                spacing = spacing,
                itemCount = 6,
                draggedIndex = 0,
                columns = 3,
            ),
        )
        // 拖第 5 项到第 0 行第 0 列 → 0
        assertEquals(
            0,
            ToolOrderLogic.gridTarget(
                pointerX = 10f,
                pointerY = 10f,
                itemWidth = itemWidth,
                itemHeight = itemHeight,
                spacing = spacing,
                itemCount = 6,
                draggedIndex = 5,
                columns = 3,
            ),
        )
    }

    @Test
    fun gridTarget_fourColumns_lastRowClamp() {
        // 10 项 4 列；指针在最后一行右侧 → 最后一项（slot 9）
        assertEquals(
            9,
            ToolOrderLogic.gridTarget(
                pointerX = 9999f,
                pointerY = 9999f,
                itemWidth = itemWidth,
                itemHeight = itemHeight,
                spacing = spacing,
                itemCount = 10,
                draggedIndex = 0,
                columns = 4,
            ),
        )
    }

    @Test
    fun gridTarget_sameSlot_noop() {
        assertEquals(
            3,
            ToolOrderLogic.gridTarget(
                pointerX = 10f,
                pointerY = 155f,
                itemWidth = itemWidth,
                itemHeight = itemHeight,
                spacing = spacing,
                itemCount = 6,
                draggedIndex = 3,
                columns = 3,
            ),
        )
    }

    @Test
    fun gridRows_forColumns() {
        assertEquals(5, ToolOrderLogic.gridRows(10, 2))
        assertEquals(4, ToolOrderLogic.gridRows(10, 3))
        assertEquals(3, ToolOrderLogic.gridRows(10, 4))
        assertEquals(0, ToolOrderLogic.gridRows(0, 4))
        assertEquals(10, ToolOrderLogic.gridRows(10, 1))
    }

    @Test
    fun gridTarget_emptyAndSingleColumn() {
        assertEquals(0, ToolOrderLogic.gridTarget(0f, 0f, itemWidth, itemHeight, spacing, 0, 0, columns = 4))
        // 1 列：x 任意，y 决定 slot
        assertEquals(2, ToolOrderLogic.gridTarget(999f, 310f, itemWidth, itemHeight, spacing, 5, 0, columns = 1))
    }
}

