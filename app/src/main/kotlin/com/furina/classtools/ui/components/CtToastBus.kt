package com.furina.classtools.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 全 App 统一的应用内消息总线（替代系统 Toast）。
 *
 * 2026-11-22：Toast 在 M3 里没有对应组件，且会被底栏/键盘遮挡、无法统一主题。
 * 调用方发送提示文本，由当前前台窗口中的 Snackbar 展示。
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

@Composable
internal fun CtToastHost(modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            CtToastBus.messages.collect { host.showSnackbar(it) }
        }
    }
    CtSnackbarHost(hostState = host, modifier = modifier)
}
