package com.furina.classtools.ui.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 全 App 统一的 M3 Snackbar 宿主。
 *
 * - 容器 inverseSurface / 文字 inverseOnSurface / 动作 inversePrimary（m3.material.io snackbar specs）；
 * - 形状 4dp，最大宽度 600dp，宽屏不拉成超长条；
 * - 只负责“长得对”，位置与底部避让仍由调用页面的 modifier 决定（浮岛/操作条上方）。
 *
 * 2026-11-21：替换各页直接使用 `SnackbarHost` 的默认样式，避免有的页面套玻璃底、
 * 有的用默认色，提示条观感不统一。
 */
@Composable
fun CtSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            modifier = Modifier.widthIn(max = 600.dp),
            shape = RoundedCornerShape(4.dp),
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inversePrimary,
            dismissActionContentColor = MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}
