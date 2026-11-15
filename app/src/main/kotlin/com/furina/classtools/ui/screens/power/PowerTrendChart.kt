package com.furina.classtools.ui.screens.power

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.power.PowerRange
import com.furina.classtools.data.power.PowerStats
import com.furina.classtools.data.power.TrendLine
import com.furina.classtools.data.power.TrendPoint
import com.furina.classtools.ui.theme.LocalCtBrandColors

/**
 * 电费趋势图（Compose Canvas；口径与网页/小程序一致）。
 *
 * 交互（web/AGENTS.md §4b-11/16）：
 * - 点击/左右拖动 → 按 x 比例命中**最近真实采样点** → 竖线 + 圆点 + 右上角固定气泡（气泡在绘图区外，由 [PowerTrendSection] 渲染）；
 * - **禁止横向滚动**：图不包 scroll 容器；横向手势由本组件消费，纵向手势留给页面滚动；
 * - 空态（<2 采样点）不画图，只显示「数据积累中 / 未选择宿舍」；
 * - 慢频房（13/16 号楼）画阶梯线 + 真实变化点；
 * - 区间内有充值跳变（`Δq>0.5`）时，折线 y 已是**折算剩余**（连续下降），并按 [TrendLine.rechargeMarks]
 *   画充值竖虚线（暖沙色虚线，对齐网页 `.pw-rline`）；真实剩余仍在图例「当前」与游标文案中。
 *
 * @param cursorIndex 当前游标命中的采样点下标（null=未查看）
 * @param onCursor 游标变化（点在同一采样点再次点击 = 取消，传 null）
 * @param yLabelHi / yLabelMid / yLabelLo 纵轴刻度文案（折算范围，由调用方按电量格式计算）
 * @param legendExtras 图例补充行（如「充值 +N 度（已折算）」/「扣减 N 度」）
 */
@Composable
fun PowerTrendChart(
    trend: TrendLine,
    range: PowerRange,
    slow: Boolean,
    changeDots: List<TrendPoint>,
    cursorIndex: Int?,
    onCursor: (Int?) -> Unit,
    yLabelHi: String,
    yLabelMid: String,
    yLabelLo: String,
    legendStart: String,
    legendCurrent: String,
    legendDrop: String,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    legendExtras: List<String> = emptyList(),
) {
    if (trend.empty) {
        EmptyTrend(note = trend.note, modifier = modifier)
        return
    }

    val plotHeight = 188.dp
    val leftAxis = 46.dp
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val rechargeColor = LocalCtBrandColors.current.powerWarn
    var plotSize by remember { mutableStateOf(IntSize.Zero) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 纵轴刻度（固定，不随游标移动）
            Column(
                modifier = Modifier
                    .width(leftAxis)
                    .height(plotHeight)
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                AxisLabel(yLabelHi)
                AxisLabel(yLabelMid)
                AxisLabel(yLabelLo)
            }

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(plotHeight)
                    .semantics {
                        // 自定义 Canvas 趋势图读屏描述（无障碍：图形信息不缺失）。
                        contentDescription = buildString {
                            append(range.title)
                            if (trend.foldedRecharge > 0.0) append("，已折算充值")
                            append("，共 ${trend.points.size} 个采样点")
                        }
                    }
                    .onSizeChanged { plotSize = it }
                    .pointerInput(trend.points, range) {
                        detectTapGestures { offset ->
                            val hit = hitAt(trend.points, offset.x, plotSize.width)
                            if (hit == null) return@detectTapGestures
                            onCursor(if (hit == cursorIndex) null else hit)
                        }
                    }
                    .pointerInput(trend.points, range) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                hitAt(trend.points, offset.x, plotSize.width)?.let(onCursor)
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                hitAt(trend.points, change.position.x, plotSize.width)?.let(onCursor)
                            },
                        )
                    },
            ) {
                val w = size.width
                val h = size.height
                // 横向网格线（顶部 / 中间 / 底部）
                val grid = lineColor.copy(alpha = 0.16f)
                listOf(0.02f, 0.5f, 0.98f).forEach { fy ->
                    drawLine(
                        color = grid,
                        start = Offset(0f, h * fy),
                        end = Offset(w, h * fy),
                        strokeWidth = 1f,
                    )
                }

                // 充值位置竖虚线（只做标记，不改变折算后的用电曲线；对齐网页 .pw-rline 暖沙虚线）
                if (trend.rechargeMarks.isNotEmpty()) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
                    trend.rechargeMarks.forEach { mark ->
                        val x = mark.x.coerceIn(0f, 1f) * w
                        drawLine(
                            color = rechargeColor.copy(alpha = 0.55f),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f,
                            pathEffect = dash,
                        )
                    }
                }

                val points = trend.points
                fun px(point: TrendPoint) = Offset(
                    x = point.x.coerceIn(0f, 1f) * w,
                    y = point.y.coerceIn(0f, 1f) * h,
                )

                // 折线（慢频房用阶梯线；普通房用平滑折线）
                val path = Path()
                val drawPoints: List<Offset> = if (slow) {
                    PowerStats.stepSegments(points).map { px(it) }
                } else {
                    points.map { px(it) }
                }
                if (drawPoints.size >= 2) {
                    path.moveTo(drawPoints.first().x, drawPoints.first().y)
                    for (i in 1 until drawPoints.size) {
                        path.lineTo(drawPoints[i].x, drawPoints[i].y)
                    }
                    // 折线下方淡渐变（视觉层级，不遮网格）
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(drawPoints.last().x, h)
                        lineTo(drawPoints.first().x, h)
                        close()
                    }
                    drawPath(
                        path = fill,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                lineColor.copy(alpha = 0.18f),
                                lineColor.copy(alpha = 0.02f),
                            ),
                        ),
                    )
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 2.2f, cap = StrokeCap.Round),
                    )
                }

                // 首尾小点
                points.firstOrNull()?.let { p ->
                    drawCircle(color = lineColor, radius = 3f, center = px(p))
                }
                points.lastOrNull()?.let { p ->
                    drawCircle(color = lineColor, radius = 3f, center = px(p))
                }

                // 慢频房：真实变化点
                changeDots.forEach { p ->
                    drawCircle(
                        color = lineColor.copy(alpha = 0.55f),
                        radius = 2.4f,
                        center = px(p),
                    )
                }

                // 游标：竖线 + 采样点圆点
                val cursor = cursorIndex?.let { idx -> points.getOrNull(idx) }
                if (cursor != null) {
                    val c = px(cursor)
                    drawLine(
                        color = onSurfaceVariantColor.copy(alpha = 0.8f),
                        start = Offset(c.x, 0f),
                        end = Offset(c.x, h),
                        strokeWidth = 1.2f,
                    )
                    drawCircle(
                        color = surfaceColor,
                        radius = 6f,
                        center = c,
                    )
                    drawCircle(color = lineColor, radius = 4.2f, center = c)
                }
            }
        }

        // x 轴刻度（按真实采样点比例定位）
        val ticks = remember(trend, range) { PowerStats.xTicks(trend.points, range) }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .padding(start = leftAxis),
        ) {
            ticks.forEach { tick ->
                Text(
                    text = tick.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.offset(x = maxWidth * tick.frac - 18.dp),
                )
            }
        }

        // 图例（网页 .pw-legend：可换行；补充行展示充值折算/异常扣减，不挤占主图例）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LegendItem("起点", legendStart)
                LegendItem("当前", legendCurrent)
                LegendItem(range.dropLabel, legendDrop)
            }
            if (legendExtras.isNotEmpty()) {
                Text(
                    text = legendExtras.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (trend.foldedRecharge > 0.0) {
                        rechargeColor
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "$value 度",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AxisLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        maxLines = 1,
    )
}

@Composable
private fun EmptyTrend(note: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = note.ifBlank { "数据积累中" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 点击/拖动 x → 最近真实采样点下标（null=无有效点）。 */
private fun hitAt(points: List<TrendPoint>, offsetX: Float, widthPx: Int): Int? {
    if (widthPx <= 0 || points.size < 2) return null
    val frac = (offsetX / widthPx.toFloat()).coerceIn(0f, 1f)
    return PowerStats.nearestPoint(points, frac)?.index
}

