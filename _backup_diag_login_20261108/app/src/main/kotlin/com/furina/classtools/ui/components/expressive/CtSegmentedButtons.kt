package com.furina.classtools.ui.components.expressive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.CtMotion

/**
 * 分段选择的一个选项。
 *
 * @param key 稳定标识（不要用展示文案当 key，便于 i18n 与持久化）
 * @param label 展示文案（短，建议 ≤4 个汉字）
 * @param icon 可选前导图标（Material Symbols/rounded 图标）
 */
@Immutable
data class CtSegmentOption(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
)

/**
 * 自建 expressive 分段选择按钮组（stable 轨降级方案，对应 material3 1.5 的 ButtonGroup/ToggleButton 场景）。
 *
 * 特性：
 * - 基于 material3 1.4.0 公开 API [SingleChoiceSegmentedButtonRow] + [SegmentedButton]；
 * - 选中项使用 [CtMotion.spatialFast] 做轻微弹性缩放（1.0 vs 0.985），配合 M3 自带的形状/颜色过渡；
 * - 高度 40dp（M3 small 档），触控目标由 M3 默认 48dp 保证；语义 role 由 SegmentedButton 提供。
 *
 * 典型用途：抽签 STEP 切换、课表「周/日」、电费趋势「24h/7天/30天」、统计「今天/近 7 天」。
 *
 * @param options 选项（≥1；1 个时退化为单段按钮样式）
 * @param selectedKey 当前选中的 key
 * @param onSelect 选中回调（点击已选中项也会回调，调用方自行幂等）
 * @param showCheckIcon 无自定义图标时，选中是否展示 M3 对勾图标
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CtSegmentedButtons(
    options: List<CtSegmentOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showCheckIcon: Boolean = true,
) {
    if (options.isEmpty()) return
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            val selected = option.key == selectedKey
            // 选中回弹：只做非常轻微的缩放，避免布局抖动。
            val selectedScale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.985f,
                animationSpec = CtMotion.spatialFast,
                label = "segmentScale",
            )
            SegmentedButton(
                selected = selected,
                onClick = { onSelect(option.key) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                enabled = enabled,
                modifier = Modifier.graphicsLayer {
                    scaleX = selectedScale
                    scaleY = selectedScale
                },
                icon = when {
                    option.icon != null -> {
                        {
                            Icon(
                                imageVector = option.icon!!,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    showCheckIcon -> {
                        { SegmentedButtonDefaults.Icon(active = selected) }
                    }

                    else -> {
                        {}
                    }
                },
                label = {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}
