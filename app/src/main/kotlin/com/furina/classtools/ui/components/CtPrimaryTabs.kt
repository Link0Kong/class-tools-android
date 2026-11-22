package com.furina.classtools.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow

/**
 * M3 PrimaryTabRow（页面级筛选/分类切换）。
 *
 * 替代此前的「白药丸 FilterChip 行」：选中态是 M3 下划线指示器 + primary 文本，
 * 高度 48dp，跟手指示器由 Material3 处理；调用方仍负责 pager 跟手。
 */
@Composable
fun CtPrimaryTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryTabRow(
        selectedTabIndex = selectedIndex.coerceIn(0, (labels.size - 1).coerceAtLeast(0)),
        modifier = modifier,
        containerColor = Color.Transparent,
        divider = {},
    ) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                text = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}
