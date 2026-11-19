package com.furina.classtools.ui.components.expressive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.CtShapeTokens

/**
 * 统一底部弹层（**简化重载**：普通调用方无需 `@OptIn(ExperimentalMaterial3Api::class)`）。
 *
 * 内部自动创建 `sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)`；
 * 需要程序化控制展开/收起（例如先展开再执行动作）时改用 [CtBottomSheetWithState]。
 *
 * 统一规格（design/ANDROID17_DESIGN.md §6.6）：
 * - 顶部圆角 30dp（[CtShapeTokens.sheet]）、容器色 surfaceContainerLow；
 * - 默认显示拖拽把手；点击遮罩/下拉均可关闭（预测返回由系统处理）；
 * - 弹入/收起动效由 ModalBottomSheet 内置（material3 1.4.0 已按 MotionScheme 规格执行）。
 *
 * @param onDismissRequest 关闭回调
 * @param showDragHandle 是否显示拖拽把手
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CtBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    showDragHandle: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    CtBottomSheetWithState(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
        showDragHandle = showDragHandle,
        containerColor = containerColor,
        content = content,
    )
}

/**
 * 统一底部弹层（**高级重载**：外部持有 [SheetState]，可程序化展开/收起）。
 *
 * 调用方需要 `@OptIn(ExperimentalMaterial3Api::class)`（material3 1.4.0 的 SheetState 仍是实验 API）。
 * 普通页面请使用不暴露 SheetState 的 [CtBottomSheet] 重载。
 *
 * @param sheetState 外部弹层状态（可用 `rememberModalBottomSheetState` 创建）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CtBottomSheetWithState(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    showDragHandle: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = CtShapeTokens.sheet,
        containerColor = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        dragHandle = if (showDragHandle) {
            {
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        content = content,
    )
}

/**
 * 底部弹层通用标题行（标题 + 可选副标题 + 右侧动作）。
 *
 * 用法：
 * ```
 * CtBottomSheet(onDismissRequest = { ... }) {
 *     CtBottomSheetHeader(title = "选择名单", subtitle = "已选 12 / 70")
 *     // content
 *     CtBottomSheetFooter(primaryText = "确定", onPrimary = { ... })
 * }
 * ```
 */
@Composable
fun CtBottomSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        action?.invoke()
    }
}

/**
 * 底部弹层底部动作条：可选的取消文字按钮 + 主操作按钮（占满剩余宽度）。
 * 自动避让导航栏 insets 由调用方在 content 末尾加 `Modifier.navigationBarsPadding()`。
 */
@Composable
fun CtBottomSheetFooter(
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (secondaryText != null && onSecondary != null) {
            TextButton(onClick = onSecondary) {
                Text(text = secondaryText, style = MaterialTheme.typography.labelLarge)
            }
        }
        androidx.compose.material3.Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier.heightIn(min = 48.dp),
            shape = CtShapeTokens.pill,
        ) {
            Text(text = primaryText, style = MaterialTheme.typography.labelLarge)
        }
    }
}
