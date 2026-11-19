package com.furina.classtools.ui.components

/**
 * 无障碍播报文本（纯 Kotlin，无 Compose/Android 依赖，可单测）。
 *
 * TalkBack 排序语义用到的文案集中在这里，避免 UI 内联字符串漂移。
 */
object CtA11yFormat {

    const val ACTION_MOVE_UP: String = "上移"
    const val ACTION_MOVE_DOWN: String = "下移"

    /** `第 2 个，共 10 个`（1-based；total ≤0 时返回空串，不播报无意义位置）。 */
    fun positionText(index: Int, total: Int): String =
        if (total <= 0 || index < 0) "" else "第 ${index + 1} 个，共 $total 个"

    /** 拖动中状态播报：`拖动中，第 2 个，共 10 个`。 */
    fun draggingText(index: Int, total: Int): String {
        val position = positionText(index, total)
        return if (position.isEmpty()) "拖动中" else "拖动中，$position"
    }

    /** 当前可用的排序动作标签（首项无「上移」、末项无「下移」）。 */
    fun reorderActionLabels(index: Int, total: Int): List<String> = buildList {
        if (index > 0) add(ACTION_MOVE_UP)
        if (total > 0 && index < total - 1) add(ACTION_MOVE_DOWN)
    }

    /** 排序成功后的播报文案：`已上移到第 1 个，共 10 个`；未移动返回 null。 */
    fun movedText(fromIndex: Int, toIndex: Int, total: Int): String? {
        if (fromIndex == toIndex) return null
        val direction = if (toIndex < fromIndex) "上移" else "下移"
        return "已${direction}到第 ${toIndex + 1} 个，共 $total 个"
    }
}
