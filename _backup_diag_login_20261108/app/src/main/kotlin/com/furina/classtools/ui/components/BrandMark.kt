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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 品牌标记：暖白圆角卡 + 三圆点（雾青居中、灰蓝左上、暖沙右下）。
 *
 * 规格与网页 favicon（`web/tools/index.html` 内联 SVG）及微信小程序
 * `mp-class-tools/images/app-icon-1024.png` 完全一致：
 * 64 视口内 rect(64,rx14,#F7F8F8)、circle(32,32,r9,#4E9082)、
 * circle(15,15,r5,#6E8CA8)、circle(49,49,r5,#B98F63)。
 * 启动图标 `ic_launcher_foreground.xml` 使用同一几何（映射到自适应安全区）。
 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    container: Color = Color(0xFFF7F8F8),
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val radius = s * (14f / 64f)
        drawPath(
            path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = s,
                        bottom = s,
                        cornerRadius = CornerRadius(radius, radius),
                    ),
                )
            },
            color = container,
        )
        // 中心点（雾青）
        drawCircle(
            color = Color(0xFF4E9082),
            radius = s * (9f / 64f),
            center = Offset(s * 0.5f, s * 0.5f),
        )
        // 左上点（灰蓝）
        drawCircle(
            color = Color(0xFF6E8CA8),
            radius = s * (5f / 64f),
            center = Offset(s * (15f / 64f), s * (15f / 64f)),
        )
        // 右下点（暖沙）
        drawCircle(
            color = Color(0xFFB98F63),
            radius = s * (5f / 64f),
            center = Offset(s * (49f / 64f), s * (49f / 64f)),
        )
    }
}
