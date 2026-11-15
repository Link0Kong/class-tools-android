package com.furina.classtools.ui.screens.guide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.furina.classtools.ui.theme.CtTokens
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import kotlin.math.hypot

/**
 * 使用说明卡片 hero 配图（Compose 自绘，**不下载图片、不新增依赖**）。
 *
 * 契约 §1.3 的 11 个场景（[GuideScene]）都用 `Canvas` / 圆角块 / Material 图标画示意，
 * 色调映射到本端主题色（契约 §1.4：mint / blue / sand / rose / violet / neutral）。
 * 配图只作示意，不承载需要读屏朗读的信息（无语义）。
 */

/** hero 配图调色板：soft = 容器底色，ink = 前景线条，accent = 强调色。 */
@Immutable
internal data class GuideSceneColors(
    val soft: Color,
    val ink: Color,
    val accent: Color,
) {
    /** accent 上的文字/图形色（用容器底色保证对比度）。 */
    val onAccent: Color get() = soft

    /** 次要线条。 */
    val line: Color get() = ink.copy(alpha = 0.55f)
}

/**
 * 按 tone 取本端主题色（不照搬 web 色值）。
 *
 * 深色模式：大面积底色统一改中性 tonal（surfaceContainerHigh + onSurface），
 * 品牌/语义色只保留在 [GuideSceneColors.accent]（场景图形线条）小面积使用，
 * 避免使用说明顶部 hero 在深色下出现大面积青绿。
 */
@Composable
internal fun guideSceneColors(tone: GuideTone): GuideSceneColors {
    val scheme = MaterialTheme.colorScheme
    val accent = when (tone) {
        GuideTone.MINT -> scheme.primary
        GuideTone.BLUE -> scheme.secondary
        GuideTone.SAND -> scheme.tertiary
        GuideTone.ROSE -> scheme.error
        GuideTone.VIOLET -> lerp(scheme.secondary, scheme.error, 0.55f)
        GuideTone.NEUTRAL -> scheme.onSurfaceVariant
    }
    if (LocalCtDarkTheme.current) {
        return GuideSceneColors(
            soft = scheme.surfaceContainerHigh,
            ink = scheme.onSurface,
            accent = accent,
        )
    }
    return when (tone) {
        GuideTone.MINT -> GuideSceneColors(
            soft = scheme.primaryContainer,
            ink = scheme.onPrimaryContainer,
            accent = scheme.primary,
        )

        GuideTone.BLUE -> GuideSceneColors(
            soft = scheme.secondaryContainer,
            ink = scheme.onSecondaryContainer,
            accent = scheme.secondary,
        )

        GuideTone.SAND -> GuideSceneColors(
            soft = scheme.tertiaryContainer,
            ink = scheme.onTertiaryContainer,
            accent = scheme.tertiary,
        )

        GuideTone.ROSE -> GuideSceneColors(
            soft = scheme.errorContainer,
            ink = scheme.onErrorContainer,
            accent = scheme.error,
        )

        GuideTone.VIOLET -> GuideSceneColors(
            soft = lerp(scheme.secondaryContainer, scheme.errorContainer, 0.5f),
            ink = lerp(scheme.onSecondaryContainer, scheme.onErrorContainer, 0.5f),
            accent = lerp(scheme.secondary, scheme.error, 0.55f),
        )

        GuideTone.NEUTRAL -> GuideSceneColors(
            soft = scheme.surfaceContainerHigh,
            ink = scheme.onSurface,
            accent = scheme.onSurfaceVariant,
        )
    }
}

/** 总览条目用的小场景图标（等比 hero 场景的语义，不重复画 Canvas）。 */
internal fun guideSceneIcon(scene: GuideScene): ImageVector = when (scene) {
    GuideScene.PICKS -> Icons.Rounded.Casino
    GuideScene.STEPS -> Icons.Rounded.Timeline
    GuideScene.GRID -> Icons.Rounded.GridView
    GuideScene.LIST -> Icons.AutoMirrored.Rounded.ViewList
    GuideScene.CHART -> Icons.Rounded.Insights
    GuideScene.PEOPLE -> Icons.Rounded.Groups
    GuideScene.TRANSFER -> Icons.Rounded.SyncAlt
    GuideScene.BALLOT -> Icons.Rounded.HowToVote
    GuideScene.DROPLET -> Icons.Rounded.WaterDrop
    GuideScene.SHIELD -> Icons.Rounded.Security
    GuideScene.SLIDES -> Icons.Rounded.Slideshow
}

/**
 * hero 配图：圆角容器 + 主题色渐变底 + 场景自绘。
 *
 * 浅色：tone 容器色 → 30% accent 的同色系渐变；深色：中性 tonal 底 → 6% ink 的轻微渐变，
 * 品牌/语义色只出现在场景线条（accent）里。
 *
 * @param modifier 由调用方决定高度（一般 `fillMaxWidth().height(heroHeight)`）
 */
@Composable
internal fun GuideSceneArt(
    scene: GuideScene,
    tone: GuideTone,
    modifier: Modifier = Modifier,
) {
    val dark = LocalCtDarkTheme.current
    val colors = guideSceneColors(tone)
    val measurer = rememberTextMeasurer()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CtTokens.Radius.largeIncreased))
            .background(
                Brush.linearGradient(
                    colors = if (dark) {
                        listOf(colors.soft, lerp(colors.soft, colors.ink, 0.06f))
                    } else {
                        listOf(colors.soft, lerp(colors.soft, colors.accent, 0.30f))
                    },
                ),
            ),
    ) {
        // 装饰：两枚低透明度圆，避免大片纯色
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = colors.accent.copy(alpha = 0.10f),
                radius = size.minDimension * 0.44f,
                center = Offset(size.width * 0.88f, size.height * 0.10f),
            )
            drawCircle(
                color = colors.ink.copy(alpha = 0.06f),
                radius = size.minDimension * 0.30f,
                center = Offset(size.width * 0.06f, size.height * 0.94f),
            )
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (scene) {
                GuideScene.PICKS -> drawPicksScene(colors, measurer)
                GuideScene.STEPS -> drawStepsScene(colors, measurer)
                GuideScene.GRID -> drawGridScene(colors)
                GuideScene.LIST -> drawListScene(colors)
                GuideScene.CHART -> drawChartScene(colors)
                GuideScene.PEOPLE -> drawPeopleScene(colors)
                GuideScene.TRANSFER -> drawTransferScene(colors)
                GuideScene.BALLOT -> drawBallotScene(colors)
                GuideScene.DROPLET -> drawDropletScene(colors)
                GuideScene.SHIELD -> drawShieldScene(colors)
                GuideScene.SLIDES -> drawSlidesScene(colors)
            }
        }
    }
}

// =============================================================================
// 11 个场景（DrawScope 扩展；相对尺寸，任意 hero 大小都成立）
// =============================================================================

/** picks：三颗号码球（02 / 07 / 13）+ 虚线洗牌箭头。 */
private fun DrawScope.drawPicksScene(c: GuideSceneColors, measurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    val s = size.minDimension
    val radius = s * 0.112f
    val centers = listOf(
        Offset(w * 0.27f, h * 0.64f),
        Offset(w * 0.50f, h * 0.57f),
        Offset(w * 0.73f, h * 0.67f),
    )
    // 虚线洗牌箭头（从左球上方绕到右球上方）
    val arcTop = centers[0].y - radius * 2.1f
    drawPath(
        path = Path().apply {
            moveTo(centers[0].x, centers[0].y - radius * 1.55f)
            cubicTo(
                centers[0].x + s * 0.10f, arcTop,
                centers[2].x - s * 0.10f, arcTop,
                centers[2].x, centers[2].y - radius * 1.55f,
            )
        },
        color = c.accent.copy(alpha = 0.75f),
        style = Stroke(
            width = s * 0.018f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.055f, s * 0.045f)),
        ),
    )
    val tip = Offset(centers[2].x, centers[2].y - radius * 1.55f)
    drawPath(
        path = Path().apply {
            moveTo(tip.x - s * 0.048f, tip.y - s * 0.032f)
            lineTo(tip.x, tip.y)
            lineTo(tip.x - s * 0.062f, tip.y + s * 0.028f)
        },
        color = c.accent,
        style = Stroke(width = s * 0.018f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    val labels = listOf("02", "07", "13")
    centers.forEachIndexed { index, center ->
        drawCircle(color = c.ink.copy(alpha = 0.10f), radius = radius, center = center + Offset(0f, s * 0.014f))
        drawCircle(color = c.accent, radius = radius, center = center)
        drawCircle(
            color = c.onAccent.copy(alpha = 0.55f),
            radius = radius,
            center = center,
            style = Stroke(width = s * 0.012f),
        )
        drawCenteredText(measurer, labels[index], center, radius * 0.82f, c.onAccent)
    }
}

/** steps：3 个编号圆点由折线连接 + 终点小旗。 */
private fun DrawScope.drawStepsScene(c: GuideSceneColors, measurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    val s = size.minDimension
    val radius = s * 0.075f
    val points = listOf(
        Offset(w * 0.20f, h * 0.70f),
        Offset(w * 0.47f, h * 0.42f),
        Offset(w * 0.71f, h * 0.64f),
    )
    drawPath(
        path = Path().apply {
            moveTo(points[0].x, points[0].y)
            lineTo(points[1].x, points[1].y)
            lineTo(points[2].x, points[2].y)
        },
        color = c.line,
        style = Stroke(
            width = s * 0.016f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.04f, s * 0.032f)),
        ),
    )
    points.forEachIndexed { index, point ->
        drawCircle(c.accent, radius, point)
        drawCenteredText(measurer, "${index + 1}", point, radius * 1.05f, c.onAccent)
    }
    // 终点小旗
    val poleX = points[2].x + radius * 1.25f
    val poleTop = points[2].y - radius * 2.1f
    val poleBottom = points[2].y + radius * 0.55f
    drawLine(
        color = c.ink,
        start = Offset(poleX, poleBottom),
        end = Offset(poleX, poleTop),
        strokeWidth = s * 0.018f,
        cap = StrokeCap.Round,
    )
    drawPath(
        path = Path().apply {
            moveTo(poleX, poleTop)
            lineTo(poleX + radius * 1.55f, poleTop + radius * 0.5f)
            lineTo(poleX, poleTop + radius * 1.0f)
            close()
        },
        color = c.accent,
    )
}

/** grid：4×3 宫格，一格高亮打勾。 */
private fun DrawScope.drawGridScene(c: GuideSceneColors) {
    val w = size.width
    val h = size.height
    val s = size.minDimension
    val cols = 4
    val rows = 3
    val gap = s * 0.035f
    val left = w * 0.07f
    val top = h * 0.10f
    val gridW = w * 0.86f
    val gridH = h * 0.80f
    val cellW = (gridW - gap * (cols - 1)) / cols
    val cellH = (gridH - gap * (rows - 1)) / rows
    val corner = CornerRadius(s * 0.032f)
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val x = left + col * (cellW + gap)
            val y = top + row * (cellH + gap)
            val highlight = row == 1 && col == 2
            drawRoundRect(
                color = if (highlight) c.accent else c.ink.copy(alpha = 0.12f),
                topLeft = Offset(x, y),
                size = Size(cellW, cellH),
                cornerRadius = corner,
            )
            when {
                highlight -> drawCheckMark(
                    color = c.onAccent,
                    center = Offset(x + cellW * 0.5f, y + cellH * 0.52f),
                    unit = cellW * 0.16f,
                    strokeWidth = s * 0.02f,
                )

                row == 0 && col == 1 -> drawCircle(
                    color = c.ink.copy(alpha = 0.35f),
                    radius = cellW * 0.13f,
                    center = Offset(x + cellW * 0.5f, y + cellH * 0.5f),
                )
            }
        }
    }
}

/** list：3 行列表（圆点 + 横线），一行带勾。 */
private fun DrawScope.drawListScene(c: GuideSceneColors) {
    val w = size.width
    val h = size.height
    val s = size.minDimension
    val dotRadius = s * 0.042f
    val lineHeight = s * 0.042f
    val startX = w * 0.14f
    val rowYs = listOf(h * 0.24f, h * 0.50f, h * 0.76f)
    rowYs.forEachIndexed { index, y ->
        val done = index == 2
        drawCircle(
            color = if (done) c.accent else c.ink.copy(alpha = 0.30f),
            radius = dotRadius,
            center = Offset(startX, y),
        )
        val lineW = if (index == 0) w * 0.46f else w * 0.50f
        drawRoundRect(
            color = c.ink.copy(alpha = if (done) 0.22f else 0.14f),
            topLeft = Offset(startX + dotRadius * 2.2f, y - lineHeight / 2f),
            size = Size(lineW, lineHeight),
            cornerRadius = CornerRadius(lineHeight / 2f),
        )
        if (done) {
            val checkCenter = Offset(startX + dotRadius * 2.2f + lineW + s * 0.155f, y)
            drawCircle(color = c.accent, radius = s * 0.082f, center = checkCenter)
            drawCheckMark(c.onAccent, checkCenter, s * 0.034f, s * 0.02f)
        }
    }
}

/** chart：柱状 + 折线 + 采样点。 */
private fun DrawScope.drawChartScene(c: GuideSceneColors) {
    val w = size.width
    val h = size.height
    val s = size.minDimension
    val left = w * 0.10f
    val right = w * 0.92f
    val top = h * 0.16f
    val bottom = h * 0.84f
    drawLine(
        color = c.ink.copy(alpha = 0.22f),
        start = Offset(left, bottom),
        end = Offset(right, bottom),
        strokeWidth = s * 0.014f,
        cap = StrokeCap.Round,
    )
    val barCount = 4
    val slot = (right - left) / barCount
    val barWidth = slot * 0.42f
    val heights = listOf(0.34f, 0.56f, 0.44f, 0.74f)
    heights.forEachIndexed { index, fraction ->
        val blockHeight = (bottom - top) * fraction
        drawRoundRect(
            color = if (index == heights.lastIndex) c.accent else c.ink.copy(alpha = 0.13f),
            topLeft = Offset(left + slot * index + (slot - barWidth) / 2f, bottom - blockHeight),
            size = Size(barWidth, blockHeight),
            cornerRadius = CornerRadius(barWidth * 0.32f),
        )
    }
    val points = listOf(
        Offset(left + slot * 0.5f, bottom - (bottom - top) * 0.46f),
        Offset(left + slot * 1.5f, bottom - (bottom - top) * 0.64f),
        Offset(left + slot * 2.5f, bottom - (bottom - top) * 0.52f),
        Offset(left + slot * 3.5f, bottom - (bottom - top) * 0.82f),
    )
    drawPath(
        path = Path().apply {
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        },
        color = c.accent.copy(alpha = 0.85f),
        style = Stroke(width = s * 0.02f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    points.forEach { point ->
        drawCircle(color = c.soft, radius = s * 0.045f, center = point)
        drawCircle(color = c.accent, radius = s * 0.03f, center = point)
    }
}

/** people：4 个圆形头像，两个高亮。 */
private fun DrawScope.drawPeopleScene(c: GuideSceneColors) {
    val w = size.width
    val h = size.height
    val s = size.minDimension
    val radius = s * 0.082f
    val centerY = h * 0.52f
    val xs = listOf(w * 0.20f, w * 0.40f, w * 0.60f, w * 0.80f)
    xs.forEachIndexed { index, x ->
        val highlighted = index == 1 || index == 2
        drawCircle(
            color = if (highlighted) c.accent else c.ink.copy(alpha = 0.18f),
            radius = radius,
            center = Offset(x, centerY - radius * 1.05f),
        )
        drawRoundRect(
            color = if (highlighted) c.ink.copy(alpha = 0.55f) else c.ink.copy(alpha = 0.12f),
            topLeft = Offset(x - radius * 1.15f, centerY + radius * 0.3f),
            size = Size(radius * 2.3f, radius * 1.25f),
            cornerRadius = CornerRadius(radius * 0.62f),
        )
    }
}

/** transfer：手机 ↔ 云 ↔ 电脑，双向箭头。 */
private fun DrawScope.drawTransferScene(c: GuideSceneColors) {
    val w = size.width
    val h = size.height
    val s = size.minDimension
    // 手机
    val phoneWidth = s * 0.15f
    val phoneHeight = s * 0.32f
    val phoneLeft = w * 0.08f
    val phoneTop = h * 0.44f
    drawRoundRect(
        color = c.ink.copy(alpha = 0.12f),
        topLeft = Offset(phoneLeft, phoneTop),
        size = Size(phoneWidth, phoneHeight),
        cornerRadius = CornerRadius(s * 0.026f),
    )
    drawRoundRect(
        color = c.ink.copy(alpha = 0.7f),
        topLeft = Offset(phoneLeft, phoneTop),
        size = Size(phoneWidth, phoneHeight),
        cornerRadius = CornerRadius(s * 0.026f),
        style = Stroke(width = s * 0.015f),
    )
    drawCircle(
        color = c.accent,
        radius = phoneWidth * 0.11f,
        center = Offset(phoneLeft + phoneWidth / 2f, phoneTop + phoneHeight * 0.87f),
    )
    // 笔记本
    val lapWidth = s * 0.25f
    val lapHeight = s * 0.16f
    val lapLeft = w * 0.67f
    val lapTop = h * 0.47f
    drawRoundRect(
        color = c.ink.copy(alpha = 0.12f),
        topLeft = Offset(lapLeft, lapTop),
        size = Size(lapWidth, lapHeight),
        cornerRadius = CornerRadius(s * 0.018f),
    )
    drawRoundRect(
        color = c.ink.copy(alpha = 0.7f),
        topLeft = Offset(lapLeft, lapTop),
        size = Size(lapWidth, lapHeight),
        cornerRadius = CornerRadius(s * 0.018f),
        style = Stroke(width = s * 0.015f),
    )
    drawRoundRect(
        color = c.ink.copy(alpha = 0.5f),
        topLeft = Offset(lapLeft - s * 0.02f, lapTop + lapHeight),
        size = Size(lapWidth + s * 0.04f, s * 0.02f),
        cornerRadius = CornerRadius(s * 0.01f),
    )
    // 云
    val cloudCx = w * 0.50f
    val cloudCy = h * 0.25f
    val cloudColor = c.ink.copy(alpha = 0.55f)
    drawCircle(cloudColor, s * 0.058f, Offset(cloudCx - s * 0.062f, cloudCy + s * 0.012f))
    drawCircle(cloudColor, s * 0.074f, Offset(cloudCx, cloudCy - s * 0.012f))
    drawCircle(cloudColor, s * 0.058f, Offset(cloudCx + s * 0.064f, cloudCy + s * 0.012f))
    drawRoundRect(
        color = cloudColor,
        topLeft = Offset(cloudCx - s * 0.112f, cloudCy + s * 0.012f),
        size = Size(s * 0.224f, s * 0.052f),
        cornerRadius = CornerRadius(s * 0.026f),
    )
    // 双向虚线箭头
    drawBidirectionalArrow(
        c = c,
        start = Offset(phoneLeft + phoneWidth + s * 0.025f, phoneTop + phoneHeight * 0.3f),
        end = Offset(cloudCx - s * 0.13f, cloudCy + s * 0.075f),
    )
    drawBidirectionalArrow(
        c = c,
        start = Offset(cloudCx + s * 0.13f, cloudCy + s * 0.075f),
        end = Offset(lapLeft - s * 0.015f, lapTop + lapHeight * 0.4f),
    )
}

/** ballot：3 条不同长度的选项条 + 勾选圆。 */
private fun DrawScope.drawBallotScene(c: GuideSceneColors) {
    val w = size.width
    val h = size.height
    val s = size.minDimension
    val left = w * 0.10f
    val barHeight = s * 0.125f
    val gap = s * 0.075f
    val widths = listOf(0.66f, 0.50f, 0.74f)
    val totalHeight = barHeight * 3 + gap * 2
    var y = (h - totalHeight) / 2f
    widths.forEachIndexed { index, fraction ->
        val checked = index == 0
        val checkboxCenter = Offset(left + s * 0.055f, y + barHeight / 2f)
        drawCircle(
            color = if (checked) c.accent else c.ink.copy(alpha = 0.20f),
            radius = s * 0.055f,
            center = checkboxCenter,
        )
        if (checked) drawCheckMark(c.onAccent, checkboxCenter, s * 0.03f, s * 0.018f)
        drawRoundRect(
            color = if (checked) c.accent.copy(alpha = 0.35f) else c.ink.copy(alpha = 0.14f),
            topLeft = Offset(left + s * 0.16f, y),
            size = Size(w * 0.72f * fraction, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f),
        )
        y += barHeight + gap
    }
}

/** droplet：大水滴 + 用量刻度条。 */
private fun DrawScope.drawDropletScene(c: GuideSceneColors) {
    val w = size.width
    val h = size.height
    val s = size.minDimension
    val cx = w * 0.5f
    val top = h * 0.08f
    val bottom = h * 0.64f
    val halfWidth = s * 0.165f
    val bodyHeight = bottom - top
    val drop = Path().apply {
        moveTo(cx, top)
        cubicTo(
            cx + halfWidth * 1.15f, top + bodyHeight * 0.42f,
            cx + halfWidth, bottom - bodyHeight * 0.06f,
            cx, bottom,
        )
        cubicTo(
            cx - halfWidth, bottom - bodyHeight * 0.06f,
            cx - halfWidth * 1.15f, top + bodyHeight * 0.42f,
            cx, top,
        )
        close()
    }
    drawPath(path = drop, color = c.accent)
    drawCircle(
        color = c.onAccent.copy(alpha = 0.55f),
        radius = s * 0.032f,
        center = Offset(cx - halfWidth * 0.45f, top + bodyHeight * 0.42f),
    )
    // 用量刻度条
    val trackY = h * 0.86f
    val trackLeft = w * 0.14f
    val trackRight = w * 0.86f
    val trackHeight = s * 0.042f
    drawRoundRect(
        color = c.ink.copy(alpha = 0.14f),
        topLeft = Offset(trackLeft, trackY - trackHeight / 2f),
        size = Size(trackRight - trackLeft, trackHeight),
        cornerRadius = CornerRadius(trackHeight / 2f),
    )
    drawRoundRect(
        color = c.accent,
        topLeft = Offset(trackLeft, trackY - trackHeight / 2f),
        size = Size((trackRight - trackLeft) * 0.62f, trackHeight),
        cornerRadius = CornerRadius(trackHeight / 2f),
    )
    for (index in 1..3) {
        val x = trackLeft + (trackRight - trackLeft) * index / 4f
        drawLine(
            color = c.ink.copy(alpha = 0.35f),
            start = Offset(x, trackY - trackHeight),
            end = Offset(x, trackY - trackHeight - s * 0.025f),
            strokeWidth = s * 0.012f,
            cap = StrokeCap.Round,
        )
    }
}

/** shield：盾牌 + 对勾 + 锁点。 */
private fun DrawScope.drawShieldScene(c: GuideSceneColors) {
    val w = size.width
    val h = size.height
    val s = size.minDimension
    val cx = w * 0.5f
    val top = h * 0.10f
    val bottom = h * 0.88f
    val halfWidth = s * 0.195f
    val bodyHeight = bottom - top
    val shield = Path().apply {
        moveTo(cx, top)
        lineTo(cx + halfWidth, top + bodyHeight * 0.24f)
        lineTo(cx + halfWidth, top + bodyHeight * 0.56f)
        cubicTo(
            cx + halfWidth, bottom - bodyHeight * 0.06f,
            cx + halfWidth * 0.3f, bottom - bodyHeight * 0.02f,
            cx, bottom,
        )
        cubicTo(
            cx - halfWidth * 0.3f, bottom - bodyHeight * 0.02f,
            cx - halfWidth, bottom - bodyHeight * 0.06f,
            cx - halfWidth, top + bodyHeight * 0.56f,
        )
        lineTo(cx - halfWidth, top + bodyHeight * 0.24f)
        close()
    }
    drawPath(path = shield, color = c.ink.copy(alpha = 0.12f))
    drawPath(
        path = shield,
        color = c.ink.copy(alpha = 0.7f),
        style = Stroke(width = s * 0.018f, join = StrokeJoin.Round),
    )
    drawCheckMark(c.accent, Offset(cx, top + bodyHeight * 0.44f), s * 0.072f, s * 0.026f)
    // 锁点
    val lockCenter = Offset(cx, top + bodyHeight * 0.69f)
    drawRoundRect(
        color = c.accent,
        topLeft = Offset(lockCenter.x - s * 0.042f, lockCenter.y - s * 0.018f),
        size = Size(s * 0.084f, s * 0.062f),
        cornerRadius = CornerRadius(s * 0.013f),
    )
    drawArc(
        color = c.accent,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(lockCenter.x - s * 0.028f, lockCenter.y - s * 0.07f),
        size = Size(s * 0.056f, s * 0.056f),
        style = Stroke(width = s * 0.014f, cap = StrokeCap.Round),
    )
}

/** slides：层叠幻灯片 + 播放三角。 */
private fun DrawScope.drawSlidesScene(c: GuideSceneColors) {
    val w = size.width
    val h = size.height
    val s = size.minDimension
    val slideWidth = s * 0.50f
    val slideHeight = s * 0.32f
    val cx = w * 0.52f
    val cy = h * 0.52f
    rotate(degrees = -9f, pivot = Offset(cx, cy)) {
        drawRoundRect(
            color = c.ink.copy(alpha = 0.10f),
            topLeft = Offset(cx - slideWidth / 2f - s * 0.035f, cy - slideHeight / 2f - s * 0.02f),
            size = Size(slideWidth, slideHeight),
            cornerRadius = CornerRadius(s * 0.032f),
        )
    }
    rotate(degrees = -4f, pivot = Offset(cx, cy)) {
        drawRoundRect(
            color = c.ink.copy(alpha = 0.15f),
            topLeft = Offset(cx - slideWidth / 2f - s * 0.014f, cy - slideHeight / 2f - s * 0.008f),
            size = Size(slideWidth, slideHeight),
            cornerRadius = CornerRadius(s * 0.032f),
        )
    }
    val frontLeft = cx - slideWidth / 2f
    val frontTop = cy - slideHeight / 2f
    drawRoundRect(
        color = c.ink.copy(alpha = 0.10f),
        topLeft = Offset(frontLeft, frontTop),
        size = Size(slideWidth, slideHeight),
        cornerRadius = CornerRadius(s * 0.032f),
    )
    drawRoundRect(
        color = c.ink.copy(alpha = 0.65f),
        topLeft = Offset(frontLeft, frontTop),
        size = Size(slideWidth, slideHeight),
        cornerRadius = CornerRadius(s * 0.032f),
        style = Stroke(width = s * 0.015f),
    )
    for (index in 0..2) {
        drawRoundRect(
            color = c.ink.copy(alpha = 0.32f),
            topLeft = Offset(
                frontLeft + slideWidth * 0.10f,
                frontTop + slideHeight * (0.26f + index * 0.16f),
            ),
            size = Size(slideWidth * (0.44f - index * 0.08f), slideHeight * 0.06f),
            cornerRadius = CornerRadius(s * 0.012f),
        )
    }
    val playRadius = s * 0.078f
    val playCenter = Offset(frontLeft + slideWidth * 0.74f, frontTop + slideHeight * 0.5f)
    drawCircle(color = c.accent, radius = playRadius, center = playCenter)
    drawPath(
        path = Path().apply {
            moveTo(playCenter.x - playRadius * 0.30f, playCenter.y - playRadius * 0.44f)
            lineTo(playCenter.x + playRadius * 0.48f, playCenter.y)
            lineTo(playCenter.x - playRadius * 0.30f, playCenter.y + playRadius * 0.44f)
            close()
        },
        color = c.onAccent,
    )
}

// =============================================================================
// 绘制小工具
// =============================================================================

/** 双向虚线箭头（手机 ↔ 云 ↔ 电脑）。 */
private fun DrawScope.drawBidirectionalArrow(c: GuideSceneColors, start: Offset, end: Offset) {
    val s = size.minDimension
    drawLine(
        color = c.accent.copy(alpha = 0.8f),
        start = start,
        end = end,
        strokeWidth = s * 0.015f,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.035f, s * 0.028f)),
    )
    drawArrowHead(c.accent, tip = start, toward = end, headSize = s * 0.045f)
    drawArrowHead(c.accent, tip = end, toward = start, headSize = s * 0.045f)
}

private fun DrawScope.drawArrowHead(color: Color, tip: Offset, toward: Offset, headSize: Float) {
    val dx = tip.x - toward.x
    val dy = tip.y - toward.y
    val length = hypot(dx, dy)
    if (length <= 0f) return
    val ux = dx / length
    val uy = dy / length
    val base = Offset(tip.x - ux * headSize, tip.y - uy * headSize)
    val perpX = -uy * headSize * 0.45f
    val perpY = ux * headSize * 0.45f
    drawPath(
        path = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(base.x + perpX, base.y + perpY)
            lineTo(base.x - perpX, base.y - perpY)
            close()
        },
        color = color,
    )
}

/** 对勾（grid / list / ballot / shield 共用）。 */
private fun DrawScope.drawCheckMark(
    color: Color,
    center: Offset,
    unit: Float,
    strokeWidth: Float,
) {
    drawPath(
        path = Path().apply {
            moveTo(center.x - unit * 0.95f, center.y + unit * 0.02f)
            lineTo(center.x - unit * 0.22f, center.y + unit * 0.68f)
            lineTo(center.x + unit * 1.0f, center.y - unit * 0.72f)
        },
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** Canvas 里居中画一行文字（序号 / 号码球数字）。 */
private fun DrawScope.drawCenteredText(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    sizePx: Float,
    color: Color,
) {
    val layout = measurer.measure(
        text = AnnotatedString(text),
        style = TextStyle(
            color = color,
            fontSize = sizePx.toSp(),
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            center.x - layout.size.width / 2f,
            center.y - layout.size.height / 2f,
        ),
    )
}
