package com.furina.classtools.ui.theme

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * G22：系统「移除动画 / 动画时长缩放 0」检测。
 *
 * 命中时调用方应把长动画/无限动画降级（轮换暂停、计数器直接显示目标值等），
 * 但加载/进度指示器可保留（系统级 reduced-motion 通常允许进度指示）。
 */
fun isReduceMotion(context: Context): Boolean {
    val scale = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }.getOrDefault(1f)
    val animatorsEnabled = runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)
    return scale == 0f || !animatorsEnabled
}

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { isReduceMotion(context) }
}
