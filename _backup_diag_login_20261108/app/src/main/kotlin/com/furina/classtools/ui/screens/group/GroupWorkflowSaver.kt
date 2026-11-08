package com.furina.classtools.ui.screens.group

import androidx.compose.runtime.saveable.Saver
import com.furina.classtools.features.group.GroupWorkflowCodec
import com.furina.classtools.features.group.GroupWorkflowState

/**
 * 分组工作流 Saver：JSON 字符串 ↔ [GroupWorkflowState]。
 *
 * 用于 `rememberSaveable(stateSaver = GroupWorkflowSaver)`：
 * 从分组页导航去别处再返回 / 旋转 / 进程重建时恢复配置与结果（含每组座位+姓名）。
 * 仅本机 SavedState，不写云端；userdata `group` section 仍由页面防抖持久化。
 */
val GroupWorkflowSaver: Saver<GroupWorkflowState, String> = Saver(
    save = { state -> GroupWorkflowCodec.encode(state) },
    restore = { text -> GroupWorkflowCodec.decode(text) },
)
