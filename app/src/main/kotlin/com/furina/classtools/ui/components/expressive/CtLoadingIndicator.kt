package com.furina.classtools.ui.components.expressive

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.CtRadius
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 加载指示器尺寸档（容器直径 / 元素直径）。 */
enum class CtLoadingIndicatorSize(val container: Dp, val element: Dp) {
    /** 24dp：按钮内联、列表行内。 */
    Small(24.dp, 4.dp),

    /** 48dp：页面级默认（= material3 1.5 LoadingIndicator 容器规格）。 */
    Medium(48.dp, 6.dp),

    /** 64dp：hero 卡/大面积加载。 */
    Large(64.dp, 8.dp),
}

/**
 * 自建 expressive 加载指示器（stable 轨降级方案，对应 material3 1.5 的 LoadingIndicator）。
 *
 * 视觉：3 个圆点绕中心轨道旋转 + 依次放大/淡入淡出，形成「有生命感」的律动；
 * 数学相位错开 1/3 周期。无限动画使用 [rememberInfiniteTransition]（自动遵循系统 animator scale）。
 *
 * 无障碍：默认带 `contentDescription = "加载中"`，读屏会播报；纯装饰场景可传 null 关闭。
 *
 * @param size 尺寸档（默认 [CtLoadingIndicatorSize.Medium]）
 * @param color 指示器颜色（默认 primary）
 * @param contentDescription 无障碍描述；传 null 表示纯装饰
 */
@Composable
fun CtLoadingIndicator(
    modifier: Modifier = Modifier,
    size: CtLoadingIndicatorSize = CtLoadingIndicatorSize.Medium,
    color: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = "加载中",
) {
    val transition = rememberInfiniteTransition(label = "ctLoading")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ctLoadingProgress",
    )

    val semanticModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }

    Canvas(modifier = semanticModifier.size(size.container)) {
        val centerX = this.size.width / 2f
        val centerY = this.size.height / 2f
        val orbit = this.size.minDimension / 2f - size.element.toPx() / 2f
        repeat(3) { index ->
            // 相位错开：0 / 1/3 / 2/3 周期。
            val phase = (progress + index * (1f / 3f)) % 1f
            // 0→1→0 的呼吸曲线。
            val pulse = sin(phase * PI).toFloat().coerceIn(0f, 1f)
            val angle = phase * 2f * PI
            drawCircle(
                color = color.copy(alpha = 0.35f + 0.65f * pulse),
                radius = size.element.toPx() / 2f * (0.7f + 0.3f * pulse),
                center = Offset(
                    x = centerX + cos(angle).toFloat() * orbit,
                    y = centerY + sin(angle).toFloat() * orbit,
                ),
            )
        }
    }
}

/**
 * 带容器的加载指示器（对应 material3 1.5 的 ContainedLoadingIndicator）。
 *
 * 用于「整块区域加载」「导出图片生成中」等需要明确占位的场景；容器使用 surfaceContainerHigh、
 * 圆角 20dp（expressive 容器语义），内部是 [CtLoadingIndicator]。
 */
@Composable
fun CtContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    size: CtLoadingIndicatorSize = CtLoadingIndicatorSize.Medium,
    color: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentDescription: String? = "加载中",
) {
    Box(
        modifier = modifier
            .size(size.container + 16.dp)
            .background(color = containerColor, shape = RoundedCornerShape(CtRadius.card))
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        CtLoadingIndicator(
            size = size,
            color = color,
            contentDescription = contentDescription,
        )
    }
}
