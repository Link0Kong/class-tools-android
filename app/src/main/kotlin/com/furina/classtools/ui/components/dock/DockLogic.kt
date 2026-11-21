package com.furina.classtools.ui.components.dock

import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.abs

/** 浮岛底栏的一个目的地（图标 + 文案 + 可选长按提示）。 */
data class CtDockDestination(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String = label,
)

/**
 * 浮岛底栏 / Tab 联动纯逻辑（无 Compose 依赖，可单测）。
 *
 * 设计口径：
 * - 底栏药丸「连续位置」= pager 当前页 + 页内偏移（跟手），范围 0..count-1；
 * - 松手落定后药丸用 spring 吸附到整页位置；
 * - 页面转场按页偏移计算 8% 位移 + 淡出 + 0.98 缩放。
 */
object CtDockLogic {

    /** 一次 Tab 页位移的转场规格（相对屏幕宽度比例，0 = 居中）。 */
    data class PageTransform(
        /** 水平位移（屏幕宽度比例；正 = 右移）。 */
        val translationXFraction: Float,
        val alpha: Float,
        val scale: Float,
    )

    const val PARALLAX_FRACTION: Float = 0.08f
    const val MIN_ALPHA: Float = 0.72f
    const val MIN_SCALE: Float = 0.98f
    const val ITEM_MIN_HEIGHT_DP: Int = 64
    /** M3 Expressive floating toolbar 官方高度 64dp。 */
    const val DOCK_HEIGHT_DP: Int = 80
    const val ITEM_MIN_TOUCH_DP: Int = 64
    /** 选中指示器 64 × 32dp（M3 navigation bar / toolbar）。 */
    const val INDICATOR_WIDTH_DP: Int = 64
    const val INDICATOR_HEIGHT_DP: Int = 32
    /** 指示器距底栏顶部 8dp（下方为 32dp 图标带 + 17dp 文案）。 */
    const val INDICATOR_TOP_DP: Int = 16

    /**
     * 底栏连续选中位置（可为小数）：`page + offsetFraction`，夹在 [0, count-1]。
     * `offsetFraction` 约定：向下一页拖动为正（与 PagerState.currentPageOffsetFraction 一致）。
     */
    fun continuousPosition(pageIndex: Int, offsetFraction: Float, count: Int): Float {
        if (count <= 0) return 0f
        return (pageIndex + offsetFraction).coerceIn(0f, (count - 1).toFloat())
    }

    /** 全等吸附：`offsetFraction >= 0.5` 视为已切到下一页。 */
    fun settlePage(pageIndex: Int, offsetFraction: Float, count: Int): Int {
        if (count <= 0) return 0
        val target = if (offsetFraction >= 0.5f) pageIndex + 1
        else if (offsetFraction <= -0.5f) pageIndex - 1
        else pageIndex
        return target.coerceIn(0, count - 1)
    }

    /** 底栏「跟手药丸」相对选中项的小数偏移（-0.5..0.5）。 */
    fun followOffset(selectedIndex: Int, pageIndex: Int, offsetFraction: Float): Float {
        val continuous = continuousPosition(pageIndex, offsetFraction, Int.MAX_VALUE)
        return (continuous - selectedIndex).coerceIn(-0.5f, 0.5f)
    }

    /** 药丸拉伸：拖动时水平拉伸最多 8%，垂直略压扁；松手回 1。 */
    fun pillScale(offsetFraction: Float): Pair<Float, Float> {
        val stretch = abs(offsetFraction).coerceIn(0f, 1f) * 0.08f
        return (1f + stretch) to (1f - stretch * 0.5f)
    }

    /** 按压反馈缩放（选中项按下略缩，未选中更明显）。 */
    fun pressScale(selected: Boolean, pressed: Boolean, dragging: Boolean = false): Float = when {
        dragging -> 0.94f
        !pressed -> 1f
        selected -> 0.96f
        else -> 0.90f
    }

    /** 第 [index] 个药丸中心 x（px）；itemWidth = 单 item 宽度。 */
    fun indicatorCenterX(index: Int, itemWidthPx: Float): Float =
        (index + 0.5f) * itemWidthPx

    /** 第 [index] 个页面在 [currentPage] + [offsetFraction] 时的转场参数。 */
    fun pageTransform(
        index: Int,
        currentPage: Int,
        offsetFraction: Float,
        parallax: Float = PARALLAX_FRACTION,
    ): PageTransform {
        // pageOffset：0 = 完全居中；负 = 位于当前页左侧；正 = 右侧（index 是页面下标）
        val pageOffset = (index - currentPage) - offsetFraction
        val dist = abs(pageOffset).coerceIn(0f, 1f)
        return PageTransform(
            translationXFraction = pageOffset * parallax,
            alpha = 1f - (1f - MIN_ALPHA) * dist,
            scale = 1f - (1f - MIN_SCALE) * dist,
        )
    }

    /** 底栏文案是否显示（compact 显示 icon+label；平板 rail 模式由 Lead 决定）。 */
    fun showsLabel(widthDp: Int): Boolean = widthDp < 840

    /** 无障碍序号播报：`工作台，第 1 个 Tab，共 3 个`。 */
    fun tabAnnouncement(label: String, index: Int, count: Int, selected: Boolean): String {
        val state = if (selected) "已选中" else "未选中"
        return "$label，第 ${index + 1} 个 Tab，共 $count 个，$state"
    }
}

