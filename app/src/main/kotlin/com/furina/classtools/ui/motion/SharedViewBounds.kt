package com.furina.classtools.ui.motion

import androidx.compose.ui.geometry.Rect
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * SharedView Kit 的 bounds 坐标约定（纯逻辑，JVM 单测覆盖）。
 *
 * 设备通过 `getViewAnimationExtras()[EXTRA_BOUNDS]` 的第二段声明坐标系：
 * `rect|WINDOW|...` → 窗口坐标；`null` / `rect` / `rect|SCREEN|...` → 屏幕坐标。
 * 应用不得按机型/系统版本硬编码（官方 README §Bounds 坐标系）。
 */
object SharedViewBounds {
    const val SCREEN = "SCREEN"
    const val WINDOW = "WINDOW"

    /** 与官方示例 `parseBoundsCoordinateToken` 同规则：第二段优先，`contains("WINDOW")` 仅作旧描述兜底。 */
    fun parseCoordinateToken(value: String?): String {
        if (value.isNullOrBlank()) return SCREEN
        val parts = value.split("|", limit = 3)
        if (parts.size >= 2) {
            val token = parts[1].trim().uppercase(Locale.ROOT)
            if (token == SCREEN || token == WINDOW) return token
        }
        return if (value.uppercase(Locale.ROOT).contains(WINDOW)) WINDOW else SCREEN
    }

    /**
     * 把 Compose 根坐标 bounds 转为 Kit 需要的屏幕/窗口坐标。
     *
     * @param originX rootView 的屏幕/窗口 x（按 [token] 选择 getLocationOnScreen / getLocationInWindow）
     */
    fun toKitBounds(bounds: Rect, originX: Int, originY: Int): PixelBounds {
        val left = floor(bounds.left + originX).toInt()
        val top = floor(bounds.top + originY).toInt()
        val right = ceil(bounds.right + originX).toInt()
        val bottom = ceil(bounds.bottom + originY).toInt()
        return PixelBounds(
            left = left,
            top = top,
            right = max(left + 1, right),
            bottom = max(top + 1, bottom),
        )
    }

    /** 半遮卡片不启动 Kit（Bitmap 源无法表达裁切）；返回 Compose 覆盖层处理更稳。 */
    fun isFullyVisible(bounds: Rect, containerWidth: Int, containerHeight: Int, tolerance: Float = 1f): Boolean =
        bounds.width > 0f &&
            bounds.height > 0f &&
            bounds.left >= -tolerance &&
            bounds.top >= -tolerance &&
            bounds.right <= containerWidth + tolerance &&
            bounds.bottom <= containerHeight + tolerance

    /** 返回落点是否仍可用：Activity 未销毁且屏幕尺寸未变（旋屏/分屏时不硬套旧 bounds）。 */
    fun canReuseReturnBounds(
        sourceAlive: Boolean,
        sourceWidth: Int,
        sourceHeight: Int,
        recordedWidth: Int,
        recordedHeight: Int,
    ): Boolean = sourceAlive && sourceWidth == recordedWidth && sourceHeight == recordedHeight
}

/**
 * 纯 Kotlin 的像素矩形，便于 JVM 单测；Android 层再转 `android.graphics.Rect`。
 * 不直接在纯逻辑里用 android.graphics.Rect，避免 JVM 单测拿 stub 全 0。
 */
data class PixelBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun toAndroidRect(): android.graphics.Rect = android.graphics.Rect(left, top, right, bottom)
}
