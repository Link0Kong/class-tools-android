package com.furina.classtools.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 品牌标记：圆角卡片 + 对勾。
 * 与启动图标 `ic_launcher_foreground.xml` 保持同一造型。
 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    container: Color = Color(0xFF4E9082),
    mark: Color = Color.White,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val radius = s * 0.24f
        val inset = s * 0.10f
        val card = RoundRect(
            left = inset,
            top = inset,
            right = s - inset,
            bottom = s - inset,
            cornerRadius = CornerRadius(radius, radius),
        )
        drawPath(
            path = Path().apply { addRoundRect(card) },
            color = container,
        )
        // 对勾：45° 下笔 + 上扬
        val check = Path().apply {
            moveTo(s * 0.36f, s * 0.52f)
            lineTo(s * 0.47f, s * 0.63f)
            lineTo(s * 0.67f, s * 0.39f)
        }
        drawPath(
            path = check,
            color = mark,
            style = Stroke(
                width = s * 0.075f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawCircle(
            color = mark.copy(alpha = 0.28f),
            radius = s * 0.035f,
            center = Offset(s * 0.70f, s * 0.70f),
        )
    }
}
