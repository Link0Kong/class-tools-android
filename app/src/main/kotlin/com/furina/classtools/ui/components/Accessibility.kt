package com.furina.classtools.ui.components

import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics

/**
 * 无障碍辅助（TalkBack 自定义操作 / 位置播报 / 拖动排序语义）。
 *
 * 用法（列表项 / 可排序卡片）：
 * ```kotlin
 * Modifier.reorderableSemantics(
 *     index = index,
 *     total = total,
 *     itemLabel = tool.name,
 *     dragging = draggingId == tool.id,
 *     onMoveUp = { moveBy(tool.id, -1) },
 *     onMoveDown = { moveBy(tool.id, +1) },
 * )
 * ```
 * 关键点：
 * - 自定义操作「上移 / 下移」不依赖手势，TalkBack 聚焦卡片后通过「操作」菜单触发；
 * - 首/末项不提供越界动作（TalkBack 不会出现无效「上移/下移」）；
 * - `stateDescription` 播报当前位置（拖动中额外标注「拖动中」），避免读屏用户失去位置感；
 * - **放在 clickable 子节点内部**（如 ToolCard 的 contentSemantics）才能合并进同一读屏焦点，
 *   否则会多出一个只含自定义操作的焦点节点。
 */
object CtA11y {

    /** `第 2 个，共 10 个`（1-based；total ≤0 时只播报序号）。 */
    fun positionText(index: Int, total: Int): String = CtA11yFormat.positionText(index, total)

    /** 拖动中状态播报：`拖动中，第 2 个，共 10 个`。 */
    fun draggingText(index: Int, total: Int): String = CtA11yFormat.draggingText(index, total)

    /** 当前可用的排序动作标签（首项无「上移」、末项无「下移」）。 */
    fun reorderActionLabels(index: Int, total: Int): List<String> =
        CtA11yFormat.reorderActionLabels(index, total)

    /** 构造 Compose 自定义无障碍操作（顺序与 [reorderActionLabels] 一致）。 */
    fun reorderActions(
        index: Int,
        total: Int,
        onMoveUp: () -> Unit,
        onMoveDown: () -> Unit,
    ): List<CustomAccessibilityAction> = buildList {
        if (index > 0) {
            add(CustomAccessibilityAction(ACTION_MOVE_UP) { onMoveUp(); true })
        }
        if (total > 0 && index < total - 1) {
            add(CustomAccessibilityAction(ACTION_MOVE_DOWN) { onMoveDown(); true })
        }
    }

    const val ACTION_MOVE_UP: String = CtA11yFormat.ACTION_MOVE_UP
    const val ACTION_MOVE_DOWN: String = CtA11yFormat.ACTION_MOVE_DOWN
}

/**
 * 可排序卡片语义：合并进父 clickable 焦点（由 ToolCard.contentSemantics 承载）。
 *
 * `mergeDescendants = false`：只添加属性，不额外产生焦点；外层 clickable 已 merge 子节点。
 */
fun Modifier.reorderableSemantics(
    index: Int,
    total: Int,
    itemLabel: String,
    dragging: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
): Modifier = semantics {
    val position = if (dragging) {
        CtA11y.draggingText(index, total)
    } else {
        CtA11y.positionText(index, total)
    }
    if (itemLabel.isNotBlank()) contentDescription = itemLabel
    if (position.isNotEmpty()) stateDescription = position
    customActions = CtA11y.reorderActions(index, total, onMoveUp, onMoveDown)
}

/** 供组件内部使用：把额外语义放到内容节点（clickable 合并时保留 customActions）。 */
fun Modifier.contentSemantics(block: SemanticsPropertyReceiver.() -> Unit): Modifier =
    semantics { block() }
