package com.furina.classtools.features.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** 单张导出位图上限（像素面积 / 高度）：极限参数（999 号码、70 组）曾在网页端生成 40MB+ 位图。 */
private const val MAX_EXPORT_PIXEL_AREA: Long = 12_000_000L
private const val MAX_EXPORT_HEIGHT_PX: Int = 8192

/**
 * 导出卡片 v2 原生渲染器（android.graphics.Canvas，非 Compose 截屏）。
 *
 * 规格来源：`web/tools/tests/export_card_v2_spec.md` + `docs/api/EXPORT_CARD_V2.md`。
 * 逻辑坐标：宽 760（逻辑 px），输出放大 [scale]=2 倍 → 1520px 宽；高度自适应。
 * 绘制顺序：渐变底 → 主卡（阴影）→ 头部 → 参数面板 → 内容块 → 页脚 → 卡内水印。
 *
 * 线程：纯 CPU/Canvas 绘制，可在后台线程调用；Bitmap 仅由调用方持有。
 */
class ExportCardRenderer(private val scale: Float = 2f) {

    // ------------------------------------------------------------------ 规格常量（逻辑 px）
    private val W = 760f
    private val M = 22f
    private val X = 50f
    private val RX = W - X // 710
    private val CW = RX - X // 660
    private val CARD_R = 22f
    private val PANEL_R = 14f
    private val PAD = 14f

    // ------------------------------------------------------------------ 色板（网页版，Android 沿用）
    private val bgTop = Color.parseColor("#EFF4F2")
    private val bgBottom = Color.parseColor("#F7F8F8")
    private val cardColor = Color.parseColor("#FFFFFF")
    private val borderColor = Color.parseColor("#E6E9E8")
    private val ink = Color.parseColor("#23302E")
    private val ink2 = Color.parseColor("#5A6A67")
    private val ink3 = Color.parseColor("#8C9A97")
    private val primary = Color.parseColor("#4E9082")
    private val primary2 = Color.parseColor("#6E8CA8")
    private val sand = Color.parseColor("#B98F63")
    private val panelColor = Color.parseColor("#F4F8F7")
    private val panelBorder = Color.parseColor("#E8EEEC")
    private val chipBg = Color.parseColor("#FFFFFF")
    private val chipBorder = Color.parseColor("#E3E9E6")
    private val groupBg = Color.parseColor("#FAFCFB")
    private val shadowColor = Color.parseColor("#2123302E") // rgba(35,48,46,0.13)

    private val paints = HashMap<String, Paint>()

    private fun typeface(weight: Int): Typeface = when {
        weight >= 800 -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        weight >= 600 -> Typeface.create("sans-serif", Typeface.BOLD)
        weight >= 500 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        else -> Typeface.create("sans-serif", Typeface.NORMAL)
    }

    /** 逻辑字号 [size]（px），[weight] 100–900。 */
    private fun paintOf(
        size: Float,
        weight: Int,
        color: Int,
        align: Paint.Align = Paint.Align.LEFT,
        mono: Boolean = false,
    ): Paint {
        val key = "$size|$weight|$color|${align.ordinal}|$mono"
        return paints.getOrPut(key) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size * scale
                typeface = if (mono) Typeface.MONOSPACE else typeface(weight)
                this.color = color
                textAlign = align
            }
        }
    }

    // ------------------------------------------------------------------ 状态
    private lateinit var spec: ExportCardSpec
    private var drawing = false
    private var canvas: Canvas? = null
    private var cursor = 0f
    private var contentBottom = 0f
    private val cardPath = Path()
    private val tmpRect = RectF()

    /** 渲染为 Bitmap（高 = 逻辑高 * scale）。 */
    fun render(spec: ExportCardSpec): Bitmap {
        this.spec = spec
        paints.clear()

        // 第一遍：只量高（canvas = null）
        drawing = false
        canvas = null
        cursor = 0f
        layoutCard()
        val logicalHeight = contentBottom + 22f

        val pixelWidth = (W * scale).roundToInt().coerceAtLeast(1)
        val pixelHeight = (logicalHeight * scale).roundToInt().coerceAtLeast(1)
        // 预检：超大卡片（999 号码/70 组等）直接给可读错误，避免 OOM 崩溃。
        if (
            pixelHeight > MAX_EXPORT_HEIGHT_PX ||
            pixelWidth.toLong() * pixelHeight.toLong() > MAX_EXPORT_PIXEL_AREA
        ) {
            throw IllegalStateException("导出内容过大（${pixelWidth}×${pixelHeight}px），请减少人数/组数后重试")
        }

        val bitmap = Bitmap.createBitmap(
            pixelWidth,
            pixelHeight,
            Bitmap.Config.ARGB_8888,
        )
        val c = Canvas(bitmap)
        c.drawColor(bgBottom)

        // 渐变底
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, logicalHeight * scale,
                bgTop, bgBottom, Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(0f, 0f, W * scale, logicalHeight * scale, bgPaint)

        // 第二遍：真正绘制
        drawing = true
        canvas = c
        cursor = 0f
        layoutCard()
        drawWatermark(c, logicalHeight)
        return bitmap
    }

    // ------------------------------------------------------------------ 主布局（两遍共用）
    private fun layoutCard() {
        val cardX = M
        val cardY = M
        val cardW = W - 2 * M
        // 第一遍测量时 contentBottom 尚未确定；绘制遍里 contentBottom 已等于「卡底」，故卡高 = 卡底 - cardY。
        val cardH = contentBottom - cardY

        // 卡片（阴影 + 白底 + 圆角）
        if (drawing) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = cardColor
                setShadowLayer(26f * scale, 0f, 8f * scale, shadowColor)
            }
            tmpRect.set(cardX * scale, cardY * scale, (cardX + cardW) * scale, (cardY + cardH) * scale)
            canvas!!.drawRoundRect(tmpRect, CARD_R * scale, CARD_R * scale, p)
            // 描边
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = borderColor
                style = Paint.Style.STROKE
                strokeWidth = 1f * scale
            }
            canvas!!.drawRoundRect(tmpRect, CARD_R * scale, CARD_R * scale, stroke)
            cardPath.reset()
            cardPath.addRoundRect(tmpRect, CARD_R * scale, CARD_R * scale, Path.Direction.CW)
        }

        // ---------------- 头部
        drawDecorBar(cardX + 14f, cardY + 28f)

        val brandBase = cardY + 34f
        drawDots(X, brandBase - 4f, 3f, 10f)
        drawText("tool.furina.fun", X + 34f, brandBase, paintOf(11f, 800, ink3))

        val titlePaint = paintOf(30f, 800, ink)
        val titleLines = wrapText(spec.title, titlePaint, CW)
        val titleBase = cardY + 70f
        titleLines.forEachIndexed { index, line ->
            drawText(line, X, titleBase + 36f * index, titlePaint)
        }

        val subBase = titleBase + 36f * (titleLines.size - 1) + 24f
        var dividerY = subBase + 24f
        if (spec.sub.isNotBlank()) {
            val subPaint = paintOf(13f, 500, ink3)
            val subLines = wrapText(spec.sub, subPaint, CW)
            subLines.forEachIndexed { index, line ->
                drawText(line, X, subBase + 19f * index, subPaint)
            }
            dividerY += 19f * (subLines.size - 1)
        }
        drawDivider(X, RX, dividerY)

        // ---------------- 参数配置面板
        var y = dividerY
        if (spec.config.isNotEmpty()) {
            val titleBaseline = dividerY + 26f
            drawBlockTitle("参数配置", titleBaseline)
            val panelTop = titleBaseline + 12f
            val panelH = drawConfigPanel(cardX + 28f, panelTop)
            y = panelTop + panelH
        }

        // ---------------- 内容块
        spec.blocks.forEachIndexed { index, block ->
            val titleBaseline = if (index == 0) y + 30f else y + 26f
            drawBlockTitle(block.label, titleBaseline)
            val contentTop = titleBaseline + 14f
            y = when (block) {
                is TextBlock -> drawTextBlock(block, contentTop)
                is ChipsBlock -> drawChipsBlock(block, contentTop)
                is GroupsBlock -> drawGroupsBlock(block, contentTop)
            }
        }

        // ---------------- 页脚
        val footerDividerY = y + 26f
        drawDivider(X, RX, footerDividerY)

        val footBase = footerDividerY + 24f
        if (spec.foot.isNotBlank()) {
            drawText(spec.foot, X, footBase, paintOf(11.5f, 500, ink3))
        }
        drawText("编号 ${spec.rec}", X, footerDividerY + 44f, paintOf(11f, 500, ink3))

        // 右侧品牌点 + 文案（右对齐到 RX）
        val brandPaint = paintOf(11.5f, 800, primary, Paint.Align.RIGHT)
        val brandText = "tool.furina.fun"
        val brandW = measure(brandText, brandPaint)
        drawText(brandText, RX, footBase, brandPaint)
        drawDots(RX - brandW - 8f - 3f * 2 - 8f * 2, footBase - 4f, 2.5f, 8f)

        contentBottom = footerDividerY + 70f
    }

    // ------------------------------------------------------------------ 头部装饰
    private fun drawDecorBar(x: Float, y: Float) {
        if (!drawing) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                x * scale, y * scale, x * scale, (y + 40f) * scale,
                primary, primary2, Shader.TileMode.CLAMP,
            )
        }
        tmpRect.set(x * scale, y * scale, (x + 4f) * scale, (y + 40f) * scale)
        canvas!!.drawRoundRect(tmpRect, 2f * scale, 2f * scale, p)
    }

    private fun drawDots(startX: Float, cy: Float, radius: Float, gap: Float) {
        if (!drawing) return
        val colors = intArrayOf(primary, primary2, sand)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        colors.forEachIndexed { i, c ->
            p.color = c
            canvas!!.drawCircle((startX + i * gap) * scale, cy * scale, radius * scale, p)
        }
    }

    // ------------------------------------------------------------------ 参数面板
    private fun drawConfigPanel(panelX: Float, panelTop: Float): Float {
        val labelPaint = paintOf(12.5f, 500, ink3)
        val valuePaint = paintOf(14f, 600, ink2)

        val maxLabelW = spec.config.maxOf { measure(it.first, labelPaint) }
        val maxValueW = spec.config.maxOf { measure(it.second, valuePaint) }
        val twoColumns = maxLabelW <= 104f && maxValueW <= 190f
        val innerW = CW - PAD * 2 // 632
        val colW = (innerW - 20f) / 2f // 306

        var height = PAD
        if (twoColumns) {
            var i = 0
            while (i < spec.config.size) {
                val a = spec.config[i]
                val b = spec.config.getOrNull(i + 1)
                val linesA = wrapCount(a.second, valuePaint, colW - measure(a.first, labelPaint) - 6f)
                val linesB = if (b != null) {
                    wrapCount(b.second, valuePaint, colW - measure(b.first, labelPaint) - 6f)
                } else {
                    1
                }
                height += max(linesA, linesB) * 21f + 3f
                i += 2
            }
        } else {
            val labelColW = minOf(186f, maxOf(110f, maxLabelW + 6f))
            val valueColW = innerW - labelColW - 6f
            spec.config.forEach { (_, v) ->
                height += wrapCount(v, valuePaint, valueColW) * 21f + 3f
            }
        }
        height += PAD - 3f

        if (drawing) {
            val c = canvas!!
            tmpRect.set(panelX * scale, panelTop * scale, (panelX + CW) * scale, (panelTop + height) * scale)
            c.drawRoundRect(tmpRect, PANEL_R * scale, PANEL_R * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = panelColor })
            c.drawRoundRect(tmpRect, PANEL_R * scale, PANEL_R * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = panelBorder
                style = Paint.Style.STROKE
                strokeWidth = 1f * scale
            })
        }

        var rowTop = panelTop + PAD
        if (twoColumns) {
            var i = 0
            while (i < spec.config.size) {
                val a = spec.config[i]
                val b = spec.config.getOrNull(i + 1)
                val labelAW = measure(a.first, labelPaint)
                val colBW = colW - 20f
                val linesA = wrapText(a.second, valuePaint, colW - labelAW - 6f)
                val linesB = if (b != null) {
                    val labelBW = measure(b.first, labelPaint)
                    val maxW = colBW - labelBW - 6f
                    wrapText(b.second, valuePaint, maxW)
                } else {
                    emptyList()
                }
                val rows = max(linesA.size, linesB.size).coerceAtLeast(1)

                drawText(a.first, panelX + PAD, rowTop + 17f, labelPaint)
                linesA.forEachIndexed { j, line ->
                    drawText(line, panelX + PAD + labelAW + 6f, rowTop + 17f + j * 21f, valuePaint)
                }
                if (b != null) {
                    val colBX = panelX + PAD + colW + 20f
                    val labelBW = measure(b.first, labelPaint)
                    drawText(b.first, colBX, rowTop + 17f, labelPaint)
                    linesB.forEachIndexed { j, line ->
                        drawText(line, colBX + labelBW + 6f, rowTop + 17f + j * 21f, valuePaint)
                    }
                }
                rowTop += rows * 21f + 3f
                i += 2
            }
        } else {
            val labelColW = minOf(186f, maxOf(110f, maxLabelW + 6f))
            val valueColW = innerW - labelColW - 6f
            spec.config.forEach { (k, v) ->
                val lines = wrapText(v, valuePaint, valueColW)
                drawText(k, panelX + PAD, rowTop + 17f, labelPaint)
                lines.forEachIndexed { j, line ->
                    drawText(line, panelX + PAD + labelColW + 6f, rowTop + 17f + j * 21f, valuePaint)
                }
                rowTop += lines.size * 21f + 3f
            }
        }
        return height
    }

    // ------------------------------------------------------------------ 内容块
    private fun drawBlockTitle(label: String, baseline: Float) {
        if (!drawing) return
        val c = canvas!!
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primary }
        tmpRect.set(X * scale, (baseline - 11f) * scale, (X + 5f) * scale, (baseline - 6f) * scale)
        c.drawRoundRect(tmpRect, 1.5f * scale, 1.5f * scale, p)
        drawText(label, X + 13f, baseline, paintOf(12.5f, 800, ink))
    }

    private fun drawTextBlock(block: TextBlock, top: Float): Float {
        val lineH = if (block.mono) 30f else 24f
        var baseline = top + 15f
        block.lines.forEach { line ->
            if (drawing && !block.mono && line.runs.isNotEmpty()) {
                var x = X
                line.runs.forEach { run ->
                    val p = when {
                        run.dim -> paintOf(11.5f, 600, ink3)
                        run.strong -> paintOf(14f, 800, ink)
                        else -> paintOf(14f, 400, ink2)
                    }
                    drawText(run.t, x, baseline, p)
                    x += measure(run.t, p)
                }
            } else {
                val text = line.plain ?: line.runs.joinToString("") { it.t }
                val p = if (block.mono) paintOf(20f, 800, primary) else paintOf(14f, 400, ink2)
                if (block.mono) {
                    drawText(text, X + (CW - measure(text, p)) / 2f, baseline, p)
                } else {
                    drawText(text, X, baseline, p)
                }
            }
            baseline += lineH
        }
        return top + block.lines.size * lineH
    }

    private fun drawChipsBlock(block: ChipsBlock, top: Float): Float {
        val numPaint = paintOf(13.5f, 800, primary)
        val nmPaint = paintOf(12f, 500, ink2)
        var rowTop = top
        var x = X
        var rowHeight = 30f
        block.chips.forEach { chip ->
            val number = chip.n.toString()
            val numberWidth = measure(number, numPaint)
            val nameOffset = 16f + numberWidth
            val nameLines = if (chip.nm.isBlank()) emptyList() else wrapText(chip.nm, nmPaint, CW - nameOffset - 11f)
            val chipW = if (nameLines.isEmpty()) {
                22f + numberWidth
            } else {
                nameOffset + nameLines.maxOf { measure(it, nmPaint) } + 11f
            }
            val chipHeight = 30f + 17f * (nameLines.size - 1).coerceAtLeast(0)
            if (x + chipW > RX && x > X) {
                x = X
                rowTop += rowHeight + 7f
                rowHeight = 30f
            }
            drawChipBackground(x, rowTop, chipW, chipHeight)
            val baseline = rowTop + 20f
            drawText(number, x + 11f, baseline, numPaint)
            nameLines.forEachIndexed { index, line ->
                drawText(line, x + nameOffset, baseline + 17f * index, nmPaint)
            }
            x += chipW + 7f
            rowHeight = maxOf(rowHeight, chipHeight)
        }
        return rowTop + rowHeight
    }

    private fun drawChipBackground(x: Float, top: Float, width: Float, height: Float) {
        if (!drawing) return
        val c = canvas!!
        tmpRect.set(x * scale, top * scale, (x + width) * scale, (top + height) * scale)
        c.drawRoundRect(tmpRect, 15f * scale, 15f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = chipBg })
        c.drawRoundRect(tmpRect, 15f * scale, 15f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = chipBorder
            style = Paint.Style.STROKE
            strokeWidth = 1f * scale
        })
    }

    private fun drawGroupsBlock(block: GroupsBlock, top: Float): Float {
        val gap = 14f
        val colW = (CW - gap) / 2f // 323
        var rowTop = top
        var index = 0
        while (index < block.groups.size) {
            val g1 = block.groups[index]
            val g2 = block.groups.getOrNull(index + 1)
            val h1 = groupHeight(g1)
            val h2 = if (g2 != null) groupHeight(g2) else 0f
            val rowH = max(h1, h2)
            drawGroupCard(g1, X, rowTop, colW, index, block.groupLabel)
            if (g2 != null) {
                drawGroupCard(g2, X + colW + gap, rowTop, colW, index + 1, block.groupLabel)
            }
            rowTop += rowH + gap
            index += 2
        }
        return if (block.groups.isEmpty()) top else rowTop - gap
    }

    private fun groupHeight(group: List<ChipItem>): Float {
        val panelW = (CW - 14f) / 2f
        val memberLines = memberLines(group, panelW - 24f)
        return 34f + memberLines * 21f + 10f
    }

    private fun memberLines(group: List<ChipItem>, maxWidth: Float): Int {
        val numPaint = paintOf(11.5f, 600, ink3)
        val nmPaint = paintOf(13.5f, 700, ink)
        var lines = 1
        var x = 0f
        group.forEach { m ->
            val w = measure(m.n.toString(), numPaint) + if (m.nm.isNotBlank()) 3f + measure(m.nm, nmPaint) else 0f
            if (x + w > maxWidth && x > 0f) {
                lines++
                x = 0f
            }
            x += w + 10f
        }
        return lines
    }

    private fun drawGroupCard(
        group: List<ChipItem>,
        x: Float,
        top: Float,
        width: Float,
        index: Int,
        groupLabel: String?,
    ) {
        if (!drawing) return
        val c = canvas!!
        val height = groupHeight(group)
        tmpRect.set(x * scale, top * scale, (x + width) * scale, (top + height) * scale)
        c.drawRoundRect(tmpRect, PANEL_R * scale, PANEL_R * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = groupBg })
        c.drawRoundRect(tmpRect, PANEL_R * scale, PANEL_R * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = panelBorder
            style = Paint.Style.STROKE
            strokeWidth = 1f * scale
        })

        val label = if (groupLabel != null) "$groupLabel ${index + 1}" else "第 ${index + 1} 组"
        drawText(label, x + 12f, top + 20f, paintOf(12.5f, 800, ink))
        val countPaint = Paint(paintOf(10.5f, 500, ink3)).apply { textAlign = Paint.Align.RIGHT }
        drawText("${group.size} 人", x + width - 12f, top + 20f, countPaint)

        val numPaint = paintOf(11.5f, 600, ink3)
        val nmPaint = paintOf(13.5f, 700, ink)
        var cx = x + 12f
        var cy = top + 34f + 15f
        group.forEach { m ->
            val nw = measure(m.n.toString(), numPaint)
            val nmw = if (m.nm.isNotBlank()) 3f + measure(m.nm, nmPaint) else 0f
            if (cx + nw + nmw > x + width - 12f && cx > x + 12f) {
                cx = x + 12f
                cy += 21f
            }
            drawText(m.n.toString(), cx, cy, numPaint)
            if (m.nm.isNotBlank()) {
                drawText(m.nm, cx + nw + 3f, cy, nmPaint)
            }
            cx += nw + nmw + 10f
        }
    }

    // ------------------------------------------------------------------ 页脚 / 水印
    private fun drawDivider(from: Float, to: Float, y: Float) {
        if (!drawing) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            strokeWidth = 1f * scale
        }
        canvas!!.drawLine(from * scale, y * scale, to * scale, y * scale, p)
    }

    private fun drawWatermark(c: Canvas, logicalHeight: Float) {
        c.save()
        c.clipPath(cardPath)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            alpha = (0.03f * 255f).roundToInt()
            textSize = 15f * scale
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        c.rotate(-20f, W * scale / 2f, logicalHeight * scale / 2f)
        val text = "tool.furina.fun"
        var y = -logicalHeight * scale
        while (y < logicalHeight * scale * 2f) {
            var x = -W * scale
            while (x < W * scale * 2f) {
                c.drawText(text, x, y, p)
                x += 300f * scale
            }
            y += 140f * scale
        }
        c.restore()
    }

    // ------------------------------------------------------------------ 文本工具
    private fun measure(text: String, paint: Paint): Float = paint.measureText(text) / scale

    private fun drawText(text: String, x: Float, baseline: Float, paint: Paint) {
        canvas?.drawText(text, x * scale, baseline * scale, paint)
    }

    private fun wrapCount(text: String, paint: Paint, maxWidth: Float): Int =
        wrapText(text, paint, maxWidth).size

    /** 按字符换行（中文无空格，逐字测量；英文单词尽量不拆）。 */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val lines = ArrayList<String>()
        var start = 0
        var lastSpace = -1
        var i = 0
        var currentWidth = 0f
        while (i < text.length) {
            val ch = text[i]
            val w = paint.measureText(ch.toString()) / scale
            if (ch == ' ') lastSpace = i
            if (currentWidth + w > maxWidth && i > start) {
                val breakAt = if (lastSpace > start) lastSpace else i
                lines.add(text.substring(start, breakAt).trimEnd())
                start = if (lastSpace > start) breakAt + 1 else breakAt
                lastSpace = -1
                currentWidth = 0f
                i = start
                continue
            }
            currentWidth += w
            i++
        }
        if (start < text.length) lines.add(text.substring(start).trimEnd())
        return if (lines.isEmpty()) listOf("") else lines
    }

    // ------------------------------------------------------------------ 模拟布局高度（供预览/测试）
    /** 仅计算逻辑高度（不创建 Bitmap，供 UI 预估）。 */
    fun logicalHeight(spec: ExportCardSpec): Float {
        this.spec = spec
        paints.clear()
        drawing = false
        canvas = null
        cursor = 0f
        layoutCard()
        return ceil(contentBottom + 22f)
    }

    private fun unusedCursor() = cursor
}
