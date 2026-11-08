package com.furina.classtools.ui.components.expressive

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.ctEmphasized
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 数字滚动计数器（表达性「数据有生命感」的最小实现）。
 *
 * 场景：抽签结果数字、电费剩余电量、水量读数、体测总分、统计计数。
 * 动画用 effects spring（临界阻尼，不 overshoot），默认 [CtMotion.effects]。
 *
 * 无障碍：动画期间 Text 会多次重组；读屏播报频繁的场景，建议调用方在容器上
 * 覆盖 `Modifier.semantics { contentDescription = 最终值 }`，或把本组件放在
 * `clearAndSetSemantics` 内由外部提供描述。
 *
 * @param value 目标值
 * @param formatter 展示格式化（默认整数）
 * @param style 文本样式（默认 expressive displayMediumEmphasized）
 */
@Composable
fun CtAnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    formatter: (Int) -> String = { it.toString() },
    style: TextStyle = MaterialTheme.ctEmphasized.displayMediumEmphasized,
    color: Color = MaterialTheme.colorScheme.onSurface,
    textAlign: TextAlign = TextAlign.Start,
    animationSpec: AnimationSpec<Float> = CtMotion.effects,
    label: String = "ctCounter",
) {
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = animationSpec,
        label = label,
    )
    Text(
        text = formatter(animated.roundToInt()),
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign,
    )
}

/**
 * 浮点数版本（电量 86.42、水量 1.68、折算比率 0.58 等）。
 *
 * @param decimals 小数位（0–2）
 */
@Composable
fun CtAnimatedDecimalCounter(
    value: Double,
    modifier: Modifier = Modifier,
    decimals: Int = 2,
    style: TextStyle = MaterialTheme.ctEmphasized.displayMediumEmphasized,
    color: Color = MaterialTheme.colorScheme.onSurface,
    textAlign: TextAlign = TextAlign.Start,
    animationSpec: AnimationSpec<Float> = CtMotion.effects,
    label: String = "ctDecimalCounter",
) {
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = animationSpec,
        label = label,
    )
    val digits = decimals.coerceIn(0, 2)
    Text(
        text = String.format(Locale.CHINA, "%.${digits}f", animated),
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign,
    )
}
