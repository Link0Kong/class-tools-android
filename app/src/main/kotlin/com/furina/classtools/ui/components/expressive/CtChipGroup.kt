package com.furina.classtools.ui.components.expressive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtShapeTokens

/**
 * Chip 组的一个选项。
 *
 * @param key 稳定标识
 * @param label 展示文案
 * @param leadingIcon 可选前导图标
 * @param enabled 单选项禁用
 */
@Immutable
data class CtChipOption(
    val key: String,
    val label: String,
    val leadingIcon: ImageVector? = null,
    val enabled: Boolean = true,
)

/**
 * 可换行的多选/单选 Chip 组（material3 1.4.0 [FilterChip] 封装）。
 *
 * - 选中项用 [CtMotion.spatialFast] 做轻微回弹（0.96 → 1.0），配合 M3 默认的容器色/对勾过渡；
 * - 触控目标由 [FilterChipDefaults] 保证 ≥48dp；
 * - 换行布局使用 [FlowRow]（项目内工具数量有限，无性能问题）。
 *
 * 典型用途：抽签「排除序号」快捷标签、体测性别/年级、通知筛选、名单分组。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CtChipGroup(
    options: List<CtChipOption>,
    selectedKeys: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    multiSelect: Boolean = true,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
    ) {
        options.forEach { option ->
            val selected = option.key in selectedKeys
            val scale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.96f,
                animationSpec = CtMotion.spatialFast,
                label = "chipScale",
            )
            FilterChip(
                selected = selected,
                onClick = { onToggle(option.key) },
                enabled = option.enabled,
                label = {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                leadingIcon = option.leadingIcon?.let { icon ->
                    {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                },
                shape = CtShapeTokens.chip,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            )
        }
    }
}

/**
 * 单选 Chip 行（横向滚动；用于「周/日」「区间」等短列表）。
 *
 * 与 [CtChipGroup] 的区别：单选、单行、可横向滚动，适合放在卡片头部与趋势图上方。
 */
@Composable
fun CtFilterChipRow(
    options: List<CtChipOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 0.dp),
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = options, key = { it.key }) { option ->
            val selected = option.key == selectedKey
            val scale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.96f,
                animationSpec = CtMotion.spatialFast,
                label = "filterChipScale",
            )
            Box(modifier = Modifier.padding(vertical = 2.dp)) {
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(option.key) },
                    enabled = option.enabled,
                    label = {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    leadingIcon = option.leadingIcon?.let { icon ->
                        {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    },
                    shape = CtShapeTokens.chip,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                )
            }
        }
    }
}
