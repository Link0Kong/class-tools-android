package com.furina.classtools.features.ppt

/**
 * 播放器手势 / 转场 / 热点几何（契约 §5 + §7 + §8.1），纯 Kotlin 可单测。
 */
object PptPlayerLogic {

    /** manifest `slides[].tr` → 页切换动效（未知按 fade，契约 §5/§7）。 */
    enum class TransitionKind(val defaultDurationMs: Int) {
        NONE(0),
        FADE(320),
        PUSH(360),
        WIPE(380),
        ZOOM(300),
        COVER(340),
    }

    /** 页内切步淡入时长（契约 §5「≤160ms」）。 */
    const val STEP_FADE_MS: Int = 150

    /** 控制条自动隐藏时长（课堂可用：点一下后 3s 收起）。 */
    const val CONTROLS_AUTO_HIDE_MS: Long = 3_000L

    /** 点击左 1/3 上一页，右 2/3 下一页。 */
    fun transitionOf(tr: String?): TransitionKind = when (tr?.trim()?.lowercase()) {
        "none" -> TransitionKind.NONE
        "fade" -> TransitionKind.FADE
        "push" -> TransitionKind.PUSH
        "wipe" -> TransitionKind.WIPE
        "zoom" -> TransitionKind.ZOOM
        "cover" -> TransitionKind.COVER
        else -> TransitionKind.FADE
    }

    /** 转场时长：manifest `trd` 合法则用（夹取 100..2000ms），否则用动效默认值。 */
    fun transitionDurationMs(tr: String?, trd: Int?): Int {
        val kind = transitionOf(tr)
        if (kind == TransitionKind.NONE) return 0
        val raw = trd ?: return kind.defaultDurationMs
        if (raw <= 0) return kind.defaultDurationMs
        return raw.coerceIn(100, 2_000)
    }

    /** 点击区域判定（xFraction = 触点 x / 舞台宽，0..1）。 */
    enum class TapTarget { PREV, NEXT }

    fun tapTarget(xFraction: Float): TapTarget =
        if (xFraction.isFinite() && xFraction < 1f / 3f) TapTarget.PREV else TapTarget.NEXT

    /** 横向滑动判定：|dx| 超过阈值才翻步；返回 0 = 不切换。 */
    enum class SwipeTarget { NONE, PREV, NEXT }

    fun swipeTarget(dxPx: Float, thresholdPx: Float): SwipeTarget {
        if (!dxPx.isFinite()) return SwipeTarget.NONE
        val threshold = thresholdPx.coerceAtLeast(1f)
        return when {
            dxPx <= -threshold -> SwipeTarget.NEXT
            dxPx >= threshold -> SwipeTarget.PREV
            else -> SwipeTarget.NONE
        }
    }

    /** 归一化热点矩形（全部夹取到 0..1，宽高 ≥0.01 且不越界）。 */
    data class HotspotRect(val x: Float, val y: Float, val w: Float, val h: Float)

    fun hotspotRect(x: Float?, y: Float?, w: Float?, h: Float?): HotspotRect {
        val anyGiven = x != null || y != null || w != null || h != null
        if (!anyGiven) {
            // 契约要求媒体热点总带坐标；缺失时退化为「居中播放键」，不遮挡整页点击区。
            return HotspotRect(CENTER_X, CENTER_Y, CENTER_W, CENTER_H)
        }
        val width = (w?.takeIf { it.isFinite() } ?: 1f).coerceIn(0.01f, 1f)
        val height = (h?.takeIf { it.isFinite() } ?: 1f).coerceIn(0.01f, 1f)
        val left = (x?.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, 1f - width)
        val top = (y?.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, 1f - height)
        return HotspotRect(left, top, width, height)
    }

    /** 横向滑动阈值：`max(40px, 屏宽 12%)`（DESIGN §4.9）。 */
    fun swipeThresholdPx(screenWidthPx: Float, minPx: Float = 40f): Float {
        val width = if (screenWidthPx.isFinite() && screenWidthPx > 0f) screenWidthPx else 0f
        return maxOf(minPx.coerceAtLeast(1f), width * 0.12f)
    }

    /** 后端媒体时长（秒）→ `0:12` / `1:05`（热点角标，DESIGN §4.7）。 */
    fun formatMediaDuration(seconds: Double): String {
        if (!seconds.isFinite() || seconds <= 0.0) return ""
        val total = seconds.toLong().coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.US, "%d:%02d", m, s)
    }

    /**
     * 低端机判定（DESIGN §4.11 降级开关）：内核 ≤2 或内存 ≤2GB → 页切换统一 fade 200ms、步内直切。
     */
    fun isLowEndDevice(cores: Int, memoryClassMb: Int): Boolean = cores <= 2 || memoryClassMb <= 2_048

    /** 页切换实际时长：低端机强制 fade 200ms（`none` 仍直切），否则按 manifest tr/trd。 */
    fun effectivePageDurationMs(tr: String?, trd: Int?, lowEnd: Boolean): Int {
        if (transitionOf(tr) == TransitionKind.NONE) return 0
        return if (lowEnd) 200 else transitionDurationMs(tr, trd)
    }

    /** 步内切换实际时长：低端机 0ms（DESIGN §4.11），否则 [STEP_FADE_MS]。 */
    fun effectiveStepDurationMs(lowEnd: Boolean): Int = if (lowEnd) 0 else STEP_FADE_MS

    /** 常量：无坐标时的居中热点。 */
    const val CENTER_X: Float = 0.36f
    const val CENTER_Y: Float = 0.42f
    const val CENTER_W: Float = 0.28f
    const val CENTER_H: Float = 0.16f

    /** 页面宽高比（manifest w/h；非法退回 16:9）。 */
    fun aspectRatio(width: Int, height: Int): Float {
        if (width <= 0 || height <= 0) return 16f / 9f
        return width.toFloat() / height.toFloat()
    }

    /** letterbox 显示区尺寸（给定可用区域，contain 居中）。 */
    fun fitSize(availableW: Float, availableH: Float, aspect: Float): Pair<Float, Float> {
        if (availableW <= 0f || availableH <= 0f) return 0f to 0f
        val safeAspect = if (aspect.isFinite() && aspect > 0f) aspect else 16f / 9f
        val w = availableW
        val h = w / safeAspect
        return if (h <= availableH) {
            w to h
        } else {
            availableH * safeAspect to availableH
        }
    }
}
