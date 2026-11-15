package com.furina.classtools.ui.screens.all

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「全部工具」拖动几何纯逻辑单测（task-58）。
 *
 * 覆盖：
 * - 视口 ↔ 网格内容坐标换算（**页面滚动后不能再叠加 scroll**：旧实现把滚动量算两次）；
 * - 长按开始指针（卡片内局部坐标 → 视口）与 2/3/4 列行列映射；
 * - 幽灵居中；
 * - 边缘自动滚动方向（上下触发带、滚动边界、内容不可滚）；
 * - 常量口径（350ms 长按 / 80ms 自动滚动节流，与小程序 `pages/all` 对齐）。
 */
class AllToolsDragGeometryTest {

    // 411dp 手机：卡宽 ≈ 443px、卡高 96dp = 252px、间距 10dp = 26px（density 2.625）。
    private val itemWidth = 443f
    private val itemHeight = 252f
    private val spacing = 26f
    private val gridLeft = 84f
    private val gridTopAtTop = 279f

    private fun rowStep() = itemHeight + spacing
    private fun colStep() = itemWidth + spacing

    // ---------------------------------------------------------------- 常量

    @Test
    fun constants_matchMpDragV2() {
        assertEquals(350L, AllToolsDragGeometry.LONG_PRESS_TIMEOUT_MS)
        assertEquals(350L, AllToolsDragGeometry.TAP_SUPPRESSION_MS)
        assertEquals(80L, AllToolsDragGeometry.AUTO_SCROLL_INTERVAL_MS)
        assertEquals(1_500L, AllToolsDragGeometry.STARTUP_RESTORE_WAIT_MS)
        assertTrue(AllToolsDragGeometry.AUTO_SCROLL_STEP_DP > 0f)
        assertTrue(AllToolsDragGeometry.AUTO_SCROLL_TOP_EDGE_DP > 0f)
        assertTrue(AllToolsDragGeometry.AUTO_SCROLL_BOTTOM_INSET_DP > 0f)
    }

    // ---------------------------------------------------------------- 视口 ↔ 内容坐标

    @Test
    fun pointerForItem_twoColumns_mapsRowAndColumn() {
        // 下标 3 = 第 2 行第 2 列；local 取卡片内 (10, 20)
        val (x, y) = AllToolsDragGeometry.pointerForItem(
            index = 3,
            localX = 10f,
            localY = 20f,
            columns = 2,
            itemWidth = itemWidth,
            itemHeight = itemHeight,
            spacing = spacing,
            gridLeft = gridLeft,
            gridTop = gridTopAtTop,
        )
        assertEquals(gridLeft + colStep() + 10f, x, 0.01f)
        assertEquals(gridTopAtTop + rowStep() + 20f, y, 0.01f)
    }

    @Test
    fun pointerForItem_threeAndFourColumns() {
        val cases = listOf(
            Triple(4, 3, 1), // index 4, columns 3 → row 1 col 1
            Triple(7, 4, 1), // index 7, columns 4 → row 1 col 3
            Triple(8, 4, 2), // index 8, columns 4 → row 2 col 0
        )
        for ((index, columns, expectedRow) in cases) {
            val (x, y) = AllToolsDragGeometry.pointerForItem(
                index = index,
                localX = 0f,
                localY = 0f,
                columns = columns,
                itemWidth = itemWidth,
                itemHeight = itemHeight,
                spacing = spacing,
                gridLeft = gridLeft,
                gridTop = gridTopAtTop,
            )
            val expectedColumn = index % columns
            assertEquals(
                "index=$index columns=$columns 列映射",
                gridLeft + expectedColumn * colStep(),
                x,
                0.01f,
            )
            assertEquals(
                "index=$index columns=$columns 行映射",
                gridTopAtTop + expectedRow * rowStep(),
                y,
                0.01f,
            )
        }
    }

    @Test
    fun contentPoint_roundTripsUnscrolledGeometry() {
        val (px, py) = AllToolsDragGeometry.pointerForItem(
            index = 5,
            localX = 100f,
            localY = 50f,
            columns = 2,
            itemWidth = itemWidth,
            itemHeight = itemHeight,
            spacing = spacing,
            gridLeft = gridLeft,
            gridTop = gridTopAtTop,
        )
        val (cx, cy) = AllToolsDragGeometry.contentPoint(px, py, gridLeft, gridTopAtTop)
        assertEquals(1 * colStep() + 100f, cx, 0.01f)
        assertEquals(2 * rowStep() + 50f, cy, 0.01f)
    }

    /**
     * task-58 回归：页面已滚动 s 后，gridTop 实时值 = 顶部位置 - s（onGloballyPositioned 上报视口坐标）。
     * 视口 → 内容换算只减实时 gridTop，**不能再加 scroll**，否则 17 个工具一屏放不下时跨行落位整体错一行。
     */
    @Test
    fun contentPoint_scrolledPage_doesNotDoubleCountScroll() {
        val scroll = 212f
        val liveGridTop = gridTopAtTop - scroll

        // 用户长按第 6 张卡（下标 6 = 第 4 行第 1 列）的卡片中心，然后指针移到卡片中心。
        val (px, py) = AllToolsDragGeometry.pointerForItem(
            index = 6,
            localX = itemWidth / 2f,
            localY = itemHeight / 2f,
            columns = 2,
            itemWidth = itemWidth,
            itemHeight = itemHeight,
            spacing = spacing,
            gridLeft = gridLeft,
            gridTop = liveGridTop,
        )
        val (cx, cy) = AllToolsDragGeometry.contentPoint(px, py, gridLeft, liveGridTop)

        // 正确换算 = 该卡片在内容坐标中的中心（行 3、列 0）。
        assertEquals(itemWidth / 2f, cx, 0.01f)
        assertEquals(3 * rowStep() + itemHeight / 2f, cy, 0.01f)

        // 落位也应回到自己被拖的下标（同格 no-op），而不是被滚动量带到上一行。
        val target = ToolOrderLogic.gridTarget(
            pointerX = cx,
            pointerY = cy,
            itemWidth = itemWidth,
            itemHeight = itemHeight,
            spacing = spacing,
            itemCount = 17,
            draggedIndex = 6,
            columns = 2,
        )
        assertEquals(6, target)

        // 旧实现漏洞：视口指针 = gridTop + contentY - scroll（滚动算了两次），
        // 再把内容坐标按 −scroll 还原时会落到上一行（复现 root cause，防止回退）。
        val buggyPointerY = liveGridTop + (3 * rowStep() + itemHeight / 2f) - scroll
        val buggyContentY = buggyPointerY - liveGridTop
        val buggyTarget = ToolOrderLogic.gridTarget(
            pointerX = cx,
            pointerY = buggyContentY,
            itemWidth = itemWidth,
            itemHeight = itemHeight,
            spacing = spacing,
            itemCount = 17,
            draggedIndex = 6,
            columns = 2,
        )
        assertNotEquals(6, buggyTarget)
    }

    @Test
    fun dragAcrossRows_scrolledPage_landsOnExpectedSlot() {
        val scroll = 212f
        val liveGridTop = gridTopAtTop - scroll
        // 从下标 0（抽签）拖到下标的第 8 格（第 5 行第 1 列）中心。
        val (px, py) = AllToolsDragGeometry.pointerForItem(
            index = 8,
            localX = itemWidth / 2f,
            localY = itemHeight / 2f,
            columns = 2,
            itemWidth = itemWidth,
            itemHeight = itemHeight,
            spacing = spacing,
            gridLeft = gridLeft,
            gridTop = liveGridTop,
        )
        val (cx, cy) = AllToolsDragGeometry.contentPoint(px, py, gridLeft, liveGridTop)
        assertEquals(
            8,
            ToolOrderLogic.gridTarget(cx, cy, itemWidth, itemHeight, spacing, 17, draggedIndex = 0, columns = 2),
        )
        // 用 moveToIndex 落位后，被拖卡片确实在下标 8。
        val order = ToolCatalogOrder.sample17()
        val moved = ToolOrderLogic.moveToIndex(order, order[0], 8)
        assertEquals(order[0], moved[8])
    }

    @Test
    fun contentPoint_negativePointer_clampedByGridTarget() {
        // 拖到网格左上角之外：内容坐标为负，gridTarget 应 clamp 到 0 而不是崩溃/NaN。
        val (cx, cy) = AllToolsDragGeometry.contentPoint(-120f, -80f, gridLeft, gridTopAtTop)
        assertEquals(-204f, cx, 0.01f)
        assertEquals(-359f, cy, 0.01f)
        assertEquals(
            0,
            ToolOrderLogic.gridTarget(cx, cy, itemWidth, itemHeight, spacing, 17, draggedIndex = 5, columns = 2),
        )
    }

    // ---------------------------------------------------------------- 幽灵

    @Test
    fun ghostTopLeft_isCenteredOnPointer() {
        val (x, y) = AllToolsDragGeometry.ghostTopLeft(
            pointerX = 500f,
            pointerY = 700f,
            itemWidth = itemWidth,
            itemHeight = itemHeight,
        )
        assertEquals(500f - itemWidth / 2f, x, 0.01f)
        assertEquals(700f - itemHeight / 2f, y, 0.01f)
        // 幽灵中心 = 指针
        assertEquals(500f, x + itemWidth / 2f, 0.01f)
        assertEquals(700f, y + itemHeight / 2f, 0.01f)
    }

    // ---------------------------------------------------------------- 边缘自动滚动

    @Test
    fun autoScrollDirection_topBand() {
        // 顶部触发带内、可向上滚 → -1
        assertEquals(
            -1,
            AllToolsDragGeometry.autoScrollDirection(
                pointerY = 100f,
                containerHeight = 2400f,
                topEdge = 252f,
                bottomInset = 336f,
                scrollValue = 120,
                maxScroll = 212,
            ),
        )
        // 已在顶部 → 0（不产生滚动）
        assertEquals(
            0,
            AllToolsDragGeometry.autoScrollDirection(
                pointerY = 100f,
                containerHeight = 2400f,
                topEdge = 252f,
                bottomInset = 336f,
                scrollValue = 0,
                maxScroll = 212,
            ),
        )
    }

    @Test
    fun autoScrollDirection_bottomBand() {
        // 底部触发带内、还能向下滚 → 1
        assertEquals(
            1,
            AllToolsDragGeometry.autoScrollDirection(
                pointerY = 2200f,
                containerHeight = 2400f,
                topEdge = 252f,
                bottomInset = 336f,
                scrollValue = 0,
                maxScroll = 212,
            ),
        )
        // 已滚到底 → 0
        assertEquals(
            0,
            AllToolsDragGeometry.autoScrollDirection(
                pointerY = 2200f,
                containerHeight = 2400f,
                topEdge = 252f,
                bottomInset = 336f,
                scrollValue = 212,
                maxScroll = 212,
            ),
        )
    }

    @Test
    fun autoScrollDirection_middleAndNoScrollable() {
        // 中间区域 → 0
        assertEquals(
            0,
            AllToolsDragGeometry.autoScrollDirection(
                pointerY = 1200f,
                containerHeight = 2400f,
                topEdge = 252f,
                bottomInset = 336f,
                scrollValue = 100,
                maxScroll = 212,
            ),
        )
        // 内容不足一屏（maxScroll = 0）→ 任何位置都不滚
        assertEquals(
            0,
            AllToolsDragGeometry.autoScrollDirection(
                pointerY = 100f,
                containerHeight = 2400f,
                topEdge = 252f,
                bottomInset = 336f,
                scrollValue = 0,
                maxScroll = 0,
            ),
        )
        // 容器未测量 → 0
        assertEquals(
            0,
            AllToolsDragGeometry.autoScrollDirection(
                pointerY = 100f,
                containerHeight = 0f,
                topEdge = 252f,
                bottomInset = 336f,
                scrollValue = 100,
                maxScroll = 212,
            ),
        )
    }

    @Test
    fun autoScrollDirection_boundariesAreExclusive() {
        // pointerY == topEdge 不进顶部带；== containerHeight - bottomInset 不进底部带。
        assertEquals(
            0,
            AllToolsDragGeometry.autoScrollDirection(
                pointerY = 252f,
                containerHeight = 2400f,
                topEdge = 252f,
                bottomInset = 336f,
                scrollValue = 100,
                maxScroll = 212,
            ),
        )
        assertEquals(
            0,
            AllToolsDragGeometry.autoScrollDirection(
                pointerY = 2064f,
                containerHeight = 2400f,
                topEdge = 252f,
                bottomInset = 336f,
                scrollValue = 100,
                maxScroll = 212,
            ),
        )
    }
}

/** 17 个工具 id 的样例顺序（避免测试依赖 ToolCatalog 的 Android/Compose 依赖）。 */
private object ToolCatalogOrder {
    fun sample17(): List<String> = listOf(
        "draw", "group", "fit", "hw", "nt", "vote", "activity", "tt", "power",
        "map", "ppt", "water", "roster", "roll", "diag", "jw", "chsi",
    )
}

