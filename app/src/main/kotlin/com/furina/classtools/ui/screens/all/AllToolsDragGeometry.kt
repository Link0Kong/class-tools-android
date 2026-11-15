package com.furina.classtools.ui.screens.all

/**
 * 「全部工具」拖动几何纯逻辑（无 Compose / Android 依赖，可单测）。
 *
 * 坐标系约定（task-58 根因修复的关键）：
 * - **视口坐标**（viewport / container）：相对「全部工具」页容器左上角。指针、幽灵、自动滚动阈值都用它；
 * - **网格内容坐标**（content）：相对网格内容左上角（**不含**页面滚动）。[ToolOrderLogic.gridTarget] 用它；
 * - [gridLeft] / [gridTop] 是「网格内容原点」的**实时视口坐标**：页面滚动后由 `onGloballyPositioned`
 *   重新上报（滚动偏移已经包含在其中）。因此视口 ↔ 内容换算 **不能再叠加/减去 scroll**：
 *   旧实现 `pointerY = gridTop + contentY - scroll` 在页面已滚动时把滚动量算了两次，
 *   导致幽灵比手指高出一个滚动距离、跨行落位整体错一行（17 个工具后一屏放不下才暴露）。
 *
 * 拖动流程对应的函数：
 * 1. 长按开始：`pointerForItem(...)` 把「被拖卡片内局部坐标」换算成视口指针；
 * 2. 每次移动：视口指针累加 dragAmount，`contentPoint(...)` 换算回网格内容坐标 →
 *    [ToolOrderLogic.gridTarget] 得到落位下标；
 * 3. 幽灵：`ghostTopLeft(...)` 让 96dp 卡以指针为中心跟手；
 * 4. 边缘自动滚动：`autoScrollDirection(...)` 给出 -1/0/+1，按 [AUTO_SCROLL_INTERVAL_MS]
 *    节流连续滚动（不是「只在移动事件里滚 1 次」）。
 */
object AllToolsDragGeometry {

    /** 长按进入拖动的阈值（与微信小程序 `pages/all` 的 `LONG_PRESS_MS = 350` 对齐）。 */
    const val LONG_PRESS_TIMEOUT_MS: Long = 350L

    /** 拖动结束后抑制点击的时长（与小程序 `_supTapUntil = now + 350` 对齐）。 */
    const val TAP_SUPPRESSION_MS: Long = 350L

    /**
     * 启动恢复 `prefs.toolOrder` 时等待 userdata 本机恢复（ClassToolsRoot
     * `hydrateFromLocal`）的上限：两者都是启动首帧的 LaunchedEffect，
     * 本页可能先于 hydrate 读到空 sections；等首个非空快照，避免重启后顺序退回自然序。
     */
    const val STARTUP_RESTORE_WAIT_MS: Long = 1_500L

    /** 边缘自动滚动节流间隔（与小程序 `AUTO_MS = 80` 对齐）。 */
    const val AUTO_SCROLL_INTERVAL_MS: Long = 80L

    /** 边缘自动滚动每步距离（dp）。 */
    const val AUTO_SCROLL_STEP_DP: Float = 12f

    /** 距容器顶部多少 dp 内触发向上自动滚动（页头区域）。 */
    const val AUTO_SCROLL_TOP_EDGE_DP: Float = 96f

    /** 距容器底部多少 dp 内触发向下自动滚动（底部悬浮 dock 高度 + 余量）。 */
    const val AUTO_SCROLL_BOTTOM_INSET_DP: Float = 128f

    /**
     * 被拖卡片内局部坐标 → 视口指针坐标。
     *
     * @param index 当前顺序下的下标（0-based）
     * @param localX 指针在卡片内的局部 x
     * @param localY 指针在卡片内的局部 y
     * @param columns 当前列数
     * @param gridLeft 网格内容原点的实时视口 x
     * @param gridTop 网格内容原点的实时视口 y（已包含滚动偏移）
     */
    fun pointerForItem(
        index: Int,
        localX: Float,
        localY: Float,
        columns: Int,
        itemWidth: Float,
        itemHeight: Float,
        spacing: Float,
        gridLeft: Float,
        gridTop: Float,
    ): Pair<Float, Float> {
        val cols = columns.coerceAtLeast(1)
        val row = index.coerceAtLeast(0) / cols
        val column = index.coerceAtLeast(0) % cols
        return Pair(
            gridLeft + column * (itemWidth + spacing) + localX,
            gridTop + row * (itemHeight + spacing) + localY,
        )
    }

    /** 视口指针坐标 → 网格内容坐标（[gridLeft]/[gridTop] 为实时视口坐标，不要再加减 scroll）。 */
    fun contentPoint(
        pointerX: Float,
        pointerY: Float,
        gridLeft: Float,
        gridTop: Float,
    ): Pair<Float, Float> = Pair(pointerX - gridLeft, pointerY - gridTop)

    /** 幽灵左上角：让「卡宽 × 卡高」的幽灵以指针为中心（与小程序 `translate(-40%, -50%)` 同观感）。 */
    fun ghostTopLeft(
        pointerX: Float,
        pointerY: Float,
        itemWidth: Float,
        itemHeight: Float,
    ): Pair<Float, Float> = Pair(pointerX - itemWidth / 2f, pointerY - itemHeight / 2f)

    /**
     * 边缘自动滚动方向。
     *
     * @param pointerY 指针在容器内的 y（视口坐标）
     * @param containerHeight 容器高度（px）
     * @param topEdge 顶部触发带高度（px）
     * @param bottomInset 底部触发带高度（px，通常 = dock 高度 + 余量）
     * @param scrollValue 当前滚动值
     * @param maxScroll 最大滚动值
     * @return -1 = 向上滚，1 = 向下滚，0 = 不滚（含已到边界 / 内容不可滚）
     */
    fun autoScrollDirection(
        pointerY: Float,
        containerHeight: Float,
        topEdge: Float,
        bottomInset: Float,
        scrollValue: Int,
        maxScroll: Int,
    ): Int {
        if (maxScroll <= 0 || containerHeight <= 0f) return 0
        if (pointerY < topEdge) return if (scrollValue > 0) -1 else 0
        if (pointerY > containerHeight - bottomInset) return if (scrollValue < maxScroll) 1 else 0
        return 0
    }
}
