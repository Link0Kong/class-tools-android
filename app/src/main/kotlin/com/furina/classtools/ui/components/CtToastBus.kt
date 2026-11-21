package com.furina.classtools.ui.components

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 全 App 统一的应用内消息总线（替代系统 Toast）。
 *
 * 2026-11-22：Toast 在 M3 里没有对应组件，且会被底栏/键盘遮挡、无法统一主题。
 * 所有轻提示改为 M3 Snackbar：调用方只发文本，根级 [ClassToolsRoot] 的
 * CtSnackbarHost 负责展示（位于底栏上方）。
 */
object CtToastBus {
    private val _messages = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 根级收集的消息流。 */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun show(message: String) {
        if (message.isNotBlank()) _messages.tryEmit(message)
    }
}
