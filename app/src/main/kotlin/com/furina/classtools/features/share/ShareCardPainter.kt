package com.furina.classtools.features.share

/**
 * 分享卡绘制面（抽象层）：
 * - Android 实现把逻辑坐标（500×400）按倍数放大画到 `android.graphics.Canvas`（见 [AndroidShareCardSurface]）；
 * - 单测用记录型实现收集文本，验证「投票卡不出现票数/百分比」等隐私口径。
 *
 * 逻辑坐标与小程序端 `utils/sharecard.js` 完全一致（500×400，5:4），保证两端卡片观感相同。
 */
interface ShareCardSurface {

    /** 逻辑宽（500）。 */
    val width: Float

    /** 逻辑高（400）。 */
    val height: Float

    /** 量一段文本的宽度（逻辑 px）。 */
    fun measure(text: String, size: Float, weight: Int): Float

    /** 画一行文本；[align] 取 [ALIGN_LEFT] / [ALIGN_CENTER] / [ALIGN_RIGHT]。 */
    fun text(text: String, x: Float, y: Float, size: Float, weight: Int, color: Int, align: Int = ALIGN_LEFT)

    /** 圆角矩形（[stroke] = true 时只描边，[strokeWidth] 逻辑 px）。 */
    fun roundRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        color: Int,
        stroke: Boolean = false,
        strokeWidth: Float = 1f,
    )

    /** 直线（分隔线/顶栏）。 */
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, strokeWidth: Float)

    /** 实心圆（品牌三点）。 */
    fun circle(cx: Float, cy: Float, radius: Float, color: Int)

    companion object {
        const val ALIGN_LEFT: Int = 0
        const val ALIGN_CENTER: Int = 1
        const val ALIGN_RIGHT: Int = 2

        /** 逻辑画布尺寸（与小程序端一致：5:4）。 */
        const val LOGICAL_WIDTH: Float = 500f
        const val LOGICAL_HEIGHT: Float = 400f
    }
}

/** 分享卡配色（与小程序端 `sharecard.js → COLOR` 一致）。 */
object ShareCardColors {
    const val BG: Int = 0xFFF7F8F8.toInt()
    const val CARD: Int = 0xFFFFFFFF.toInt()
    const val INK: Int = 0xFF23302E.toInt()
    const val INK2: Int = 0xFF526460.toInt()
    const val INK3: Int = 0xFF8A9A96.toInt()
    const val LINE: Int = 0xFFE1E7E5.toInt()
    const val TEAL: Int = 0xFF4E9082.toInt()
    const val TEAL_SOFT: Int = 0xFFE8F1EE.toInt()
    const val SAND: Int = 0xFFB98F63.toInt()
    const val BLUE: Int = 0xFF6E8CA8.toInt()
    const val WARN: Int = 0xFF9C5147.toInt()

    val BRAND_DOTS: IntArray = intArrayOf(TEAL, BLUE, SAND)
}

/**
 * 5:4 分享卡绘制（纯布局 + 文本，不依赖 Android）。
 *
 * 布局（逻辑坐标，y 为基线）：
 * - 顶栏 8px 品牌色 + 品牌三点 + 右上徽标；
 * - 标题最多 2 行（24px/700）；副标题 1 行（13px）；
 * - 状态胶囊（最多 2 个）；统计块最多 3 个（值 20px + 说明 10px）；
 * - 关键字段最多 2 条；信息行最多 6 行（标签 10px + 正文 13px）；
 * - 页脚分隔线 y=350，页脚文案 y=374；超出内容以「…」截断。
 */
object ShareCardPainter {

    private const val MARGIN = 28f
    private const val CONTENT_WIDTH = ShareCardSurface.LOGICAL_WIDTH - MARGIN * 2

    /** 把一张分享卡画到 [surface]（返回实际绘制的内容高度，便于单测/调试）。 */
    fun paint(view: ShareCardView, surface: ShareCardSurface): Float {
        val w = ShareCardSurface.LOGICAL_WIDTH
        val h = ShareCardSurface.LOGICAL_HEIGHT

        // 背景 + 顶栏
        surface.roundRect(0f, 0f, w, h, 0f, ShareCardColors.BG)
        surface.roundRect(0f, 0f, w, 8f, 0f, ShareCardColors.TEAL)

        // 品牌行
        ShareCardColors.BRAND_DOTS.forEachIndexed { index, color ->
            surface.circle(MARGIN + index * 13f, 30f, 4f, color)
        }
        surface.text("班级工具", MARGIN + 42f, 34f, 12f, 600, ShareCardColors.INK2)
        if (view.badge.isNotBlank()) {
            surface.text(
                view.badge,
                w - MARGIN,
                34f,
                12f,
                600,
                ShareCardColors.INK3,
                ShareCardSurface.ALIGN_RIGHT,
            )
        }

        var y = 74f

        // 标题（最多 2 行）
        val titleLines = wrap(surface, view.title.ifBlank { "班级工具" }, CONTENT_WIDTH, 26f, 700, 2)
        titleLines.forEach { line ->
            surface.text(line, MARGIN, y, 26f, 700, ShareCardColors.INK)
            y += 32f
        }
        if (titleLines.isEmpty()) y += 8f

        // 副标题
        if (view.subtitle.isNotBlank()) {
            surface.text(clip(surface, view.subtitle, CONTENT_WIDTH, 13f, 400), MARGIN, y + 2f, 13f, 400, ShareCardColors.INK2)
            y += 21f
        }

        // 状态胶囊（最多 2 个，第二个放不下就省略）
        if (view.pills.isNotEmpty()) {
            var x = MARGIN
            view.pills.filter { it.isNotBlank() }.take(2).forEach { pill ->
                val text = clip(surface, pill, CONTENT_WIDTH - 24f, 12f, 600)
                val pillWidth = surface.measure(text, 12f, 600) + 20f
                if (x + pillWidth > w - MARGIN) return@forEach
                surface.roundRect(x, y, pillWidth, 24f, 12f, ShareCardColors.TEAL_SOFT)
                surface.text(text, x + 10f, y + 16f, 12f, 600, ShareCardColors.TEAL)
                x += pillWidth + 8f
            }
            y += 30f
        }

        // 统计块（最多 3 个）
        val stats = view.stats.filter { it.value.isNotBlank() }.take(ShareCardView.MAX_STATS)
        if (stats.isNotEmpty()) {
            val gap = 8f
            val cellWidth = (CONTENT_WIDTH - gap * (stats.size - 1)) / stats.size
            val cellHeight = 46f
            stats.forEachIndexed { index, stat ->
                val x = MARGIN + index * (cellWidth + gap)
                surface.roundRect(x, y, cellWidth, cellHeight, 10f, ShareCardColors.TEAL_SOFT)
                surface.text(
                    clip(surface, stat.value, cellWidth - 16f, 20f, 700),
                    x + 8f,
                    y + 24f,
                    20f,
                    700,
                    ShareCardColors.TEAL,
                )
                surface.text(
                    clip(surface, stat.label, cellWidth - 16f, 10f, 400),
                    x + 8f,
                    y + 38f,
                    10f,
                    400,
                    ShareCardColors.INK2,
                )
            }
            y += cellHeight + 10f
        }

        // 关键字段（最多 2 条：标签 + 值）
        view.fields.filter { it.text.isNotBlank() }.take(ShareCardView.MAX_FIELDS).forEach { field ->
            surface.text(clip(surface, field.label, 40f, 10f, 600), MARGIN, y + 12f, 10f, 600, ShareCardColors.TEAL)
            surface.text(
                clip(surface, field.text, CONTENT_WIDTH - 44f, 12f, 400),
                MARGIN + 44f,
                y + 12f,
                12f,
                400,
                ShareCardColors.INK,
            )
            y += 19f
        }
        if (view.fields.isNotEmpty()) y += 4f

        // 信息行（最多 6 行，且不越过页脚）
        val maxRows = ((338f - y) / 26f).toInt().coerceIn(0, ShareCardView.MAX_LINES)
        view.lines.filter { it.text.isNotBlank() }.take(maxRows).forEach { line ->
            if (line.label.isNotBlank()) {
                surface.text(clip(surface, line.label, 84f, 10f, 600), MARGIN, y + 15f, 10f, 600, ShareCardColors.TEAL)
                surface.text(
                    clip(surface, line.text, CONTENT_WIDTH - 90f, 13f, 400),
                    MARGIN + 90f,
                    y + 15f,
                    13f,
                    400,
                    ShareCardColors.INK,
                )
            } else {
                surface.text(
                    clip(surface, line.text, CONTENT_WIDTH, 13f, 400),
                    MARGIN,
                    y + 15f,
                    13f,
                    400,
                    ShareCardColors.INK,
                )
            }
            y += 26f
        }

        // 备注
        val note = view.note.ifBlank { "打开班级工具查看完整内容" }
        surface.text(
            clip(surface, note, CONTENT_WIDTH, 10.5f, 400),
            MARGIN,
            minOf(338f, y + 14f),
            10.5f,
            400,
            ShareCardColors.INK3,
        )

        // 页脚
        surface.line(MARGIN, 350f, w - MARGIN, 350f, ShareCardColors.LINE, 1f)
        val left = view.footerLeft.ifBlank { "班级工具 · ${view.kind.badge}" }
        surface.text(clip(surface, left, 260f, 11f, 400), MARGIN, 374f, 11f, 400, ShareCardColors.INK3)
        val right = clip(surface, view.footerRight, 160f, 11f, 400)
        if (right.isNotBlank()) {
            surface.text(
                right,
                w - MARGIN,
                374f,
                11f,
                400,
                ShareCardColors.INK3,
                ShareCardSurface.ALIGN_RIGHT,
            )
        }
        return minOf(y, 338f)
    }

    /** 按字符换行（中文无空格逐字量宽；英文尽量按空格断行）。 */
    fun wrap(
        surface: ShareCardSurface,
        text: String,
        maxWidth: Float,
        size: Float,
        weight: Int,
        maxLines: Int,
    ): List<String> {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return emptyList()
        val lines = ArrayList<String>()
        var current = StringBuilder()
        var lastSpace = -1
        normalized.forEach { ch ->
            if (ch == ' ') lastSpace = current.length
            if (current.isNotEmpty() && surface.measure(current.toString() + ch, size, weight) > maxWidth) {
                val text = current.toString()
                val cut = if (lastSpace > 0) lastSpace else text.length
                lines.add(text.substring(0, cut).trimEnd())
                current = StringBuilder(text.substring(if (lastSpace > 0) cut + 1 else cut))
                lastSpace = -1
                if (lines.size >= maxLines) return lines
            }
            current.append(ch)
        }
        if (current.isNotEmpty() && lines.size < maxLines) lines.add(current.toString().trimEnd())
        return lines
    }

    /** 单行截断（超宽补省略号）。 */
    fun clip(surface: ShareCardSurface, text: String, maxWidth: Float, size: Float, weight: Int): String {
        if (surface.measure(text, size, weight) <= maxWidth) return text
        var end = text.length
        while (end > 1 && surface.measure(text.substring(0, end) + "…", size, weight) > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}
