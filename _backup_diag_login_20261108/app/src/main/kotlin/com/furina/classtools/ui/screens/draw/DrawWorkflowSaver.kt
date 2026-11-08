package com.furina.classtools.ui.screens.draw

import androidx.compose.runtime.saveable.Saver
import com.furina.classtools.features.draw.DrawWorkflowCodec
import com.furina.classtools.features.draw.DrawWorkflowState

/**
 * 抽签工作流 Saver：JSON 字符串 ↔ [DrawWorkflowState]。
 *
 * 用于 `rememberSaveable(stateSaver = DrawWorkflowSaver)`：
 * - 导航到分组页再返回（本 destination 仍在返回栈，SavedStateRegistry 保留）→ 回到 STEP3 结果页；
 * - 旋转/分屏/进程重建（SavedStateHandle）同样恢复；
 * - 仅本机 SavedState，不写云端；与 userdata `draw` section 的持久化互不影响。
 */
val DrawWorkflowSaver: Saver<DrawWorkflowState, String> = Saver(
    save = { state -> DrawWorkflowCodec.encode(state) },
    restore = { text -> DrawWorkflowCodec.decode(text) },
)
