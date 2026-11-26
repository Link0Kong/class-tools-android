package com.furina.classtools.ui.components

import android.os.Build
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 应用内一次性操作反馈，任何工具都通过前台提示层展示。 */
object CtToastBus {
    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    internal val messages = _messages.asSharedFlow()

    fun show(message: String) {
        if (message.isNotBlank()) _messages.tryEmit(message)
    }

    fun showCopied(message: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) show(message)
    }
}
