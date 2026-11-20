package com.furina.classtools.ui.platform

import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * 全透明系统栏（MainActivity 与 ToolActivity 共用）。
 *
 * 避免部分 ROM（ColorOS 等）在底部工具栏下画一条黑色导航栏色带：
 * - navigationBarStyle 使用 auto 仅控制图标深/浅，颜色始终透明；
 * - 再关闭系统对比度蒙层。
 *
 * 必须在 `super.onCreate()` 之前调用，保证首帧就是边到边。
 */
fun ComponentActivity.enableClassToolsEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
    @Suppress("DEPRECATION")
    window.navigationBarColor = Color.TRANSPARENT
}
