package com.furina.classtools.ui.theme

import android.animation.ValueAnimator
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/**
 * 监听系统动画缩放变化：运行中切换「移除动画」无需重进页面即可生效。
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduceMotion by remember(context) {
        mutableStateOf(isReduceMotion(context))
    }
    DisposableEffect(context) {
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduceMotion = isReduceMotion(context)
            }
        }
        runCatching { context.contentResolver.registerContentObserver(uri, false, observer) }
        onDispose {
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }
    return reduceMotion
}
