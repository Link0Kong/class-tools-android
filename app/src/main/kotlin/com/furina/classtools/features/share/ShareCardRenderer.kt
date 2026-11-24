package com.furina.classtools.features.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.roundToInt

/**
 * 分享卡 5:4 位图渲染器（Android `Canvas`，非 Compose 截屏）。
 *
 * - 逻辑坐标 500×400，按 [scale]=2 输出 1000×800（与小程序端导出一致）；
 * - 绘制内容全部来自 [ShareCardPainter]（纯布局，可单测），本类只做「逻辑坐标 → 位图」适配；
 * - 线程：纯 CPU 绘制，可在后台线程调用；[Bitmap] 由调用方负责回收/持有。
 */
class ShareCardRenderer(private val scale: Float = DEFAULT_SCALE) {

    fun render(view: ShareCardView): Bitmap {
        view.fullExport?.let { return com.furina.classtools.features.export.ExportCardRenderer().render(it) }
        val pixelWidth = (ShareCardSurface.LOGICAL_WIDTH * scale).roundToInt().coerceAtLeast(1)
        val pixelHeight = (ShareCardSurface.LOGICAL_HEIGHT * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(pixelWidth, pixelHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ShareCardColors.BG)
        ShareCardPainter.paint(view, AndroidShareCardSurface(canvas, scale))
        return bitmap
    }

    companion object {
        const val DEFAULT_SCALE: Float = 2f
    }
}

/** [ShareCardSurface] 的 Android 实现：逻辑坐标 × [scale] 后画到 [Canvas]。 */
class AndroidShareCardSurface(
    private val canvas: Canvas,
    private val scale: Float,
) : ShareCardSurface {

    private val paints = HashMap<String, Paint>()

    override val width: Float get() = ShareCardSurface.LOGICAL_WIDTH
    override val height: Float get() = ShareCardSurface.LOGICAL_HEIGHT

    override fun measure(text: String, size: Float, weight: Int): Float =
        paintOf(size, weight, ShareCardColors.INK).measureText(text) / scale

    override fun text(text: String, x: Float, y: Float, size: Float, weight: Int, color: Int, align: Int) {
        val paint = paintOf(size, weight, color, align)
        canvas.drawText(text, x * scale, y * scale, paint)
    }

    override fun roundRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        color: Int,
        stroke: Boolean,
        strokeWidth: Float,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = if (stroke) Paint.Style.STROKE else Paint.Style.FILL
            if (stroke) this.strokeWidth = strokeWidth * scale
        }
        canvas.drawRoundRect(
            RectF(x * scale, y * scale, (x + width) * scale, (y + height) * scale),
            radius * scale,
            radius * scale,
            paint,
        )
    }

    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, strokeWidth: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.strokeWidth = strokeWidth * scale
        }
        canvas.drawLine(x1 * scale, y1 * scale, x2 * scale, y2 * scale, paint)
    }

    override fun circle(cx: Float, cy: Float, radius: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawCircle(cx * scale, cy * scale, radius * scale, paint)
    }

    private fun paintOf(
        size: Float,
        weight: Int,
        color: Int,
        align: Int = ShareCardSurface.ALIGN_LEFT,
    ): Paint {
        val key = "$size|$weight|$color|$align"
        return paints.getOrPut(key) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size * scale
                typeface = typeface(weight)
                this.color = color
                textAlign = when (align) {
                    ShareCardSurface.ALIGN_CENTER -> Paint.Align.CENTER
                    ShareCardSurface.ALIGN_RIGHT -> Paint.Align.RIGHT
                    else -> Paint.Align.LEFT
                }
            }
        }
    }

    private fun typeface(weight: Int): Typeface = when {
        weight >= 800 -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        weight >= 600 -> Typeface.create("sans-serif", Typeface.BOLD)
        weight >= 500 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        else -> Typeface.create("sans-serif", Typeface.NORMAL)
    }
}
