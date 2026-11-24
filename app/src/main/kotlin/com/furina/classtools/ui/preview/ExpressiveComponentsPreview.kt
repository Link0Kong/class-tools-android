package com.furina.classtools.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.components.expressive.CtAnimatedCounter
import com.furina.classtools.ui.components.expressive.CtBottomSheetFooter
import com.furina.classtools.ui.components.expressive.CtBottomSheetHeader
import com.furina.classtools.ui.components.expressive.CtChipGroup
import com.furina.classtools.ui.components.expressive.CtChipOption
import com.furina.classtools.ui.components.expressive.CtContainedLoadingIndicator
import com.furina.classtools.ui.components.expressive.CtFabAction
import com.furina.classtools.ui.components.expressive.CtFilterChipRow
import com.furina.classtools.ui.components.expressive.CtFloatingActionMenu
import com.furina.classtools.ui.components.expressive.CtLoadingIndicator
import com.furina.classtools.ui.components.expressive.CtLoadingIndicatorSize
import com.furina.classtools.ui.components.expressive.CtPressableCard
import com.furina.classtools.ui.components.expressive.CtSegmentOption
import com.furina.classtools.ui.components.expressive.CtSegmentedButtons
import com.furina.classtools.ui.components.expressive.CtTopAppBarLarge
import com.furina.classtools.ui.theme.ClassToolsTheme
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.ctEmphasized

// =============================================================================
// Expressive 组件库预览（浅色/深色各一组；不修改 ScreenPreviews.kt）
//
// 覆盖：CtSegmentedButtons / CtLoadingIndicator / CtContainedLoadingIndicator /
//       CtFloatingActionMenu / CtBottomSheetHeader+Footer / CtChipGroup /
//       CtFilterChipRow / CtPressableCard / CtTopAppBarLarge / CtAnimatedCounter
// =============================================================================

private val previewSegments = listOf(
    CtSegmentOption(key = "draw", label = "抽签"),
    CtSegmentOption(key = "group", label = "分组"),
    CtSegmentOption(key = "result", label = "结果"),
)

private val previewChips = listOf(
    CtChipOption(key = "all", label = "全部"),
    CtChipOption(key = "undo", label = "未处理"),
    CtChipOption(key = "done", label = "已处理"),
    CtChipOption(key = "pin", label = "置顶"),
)

@Preview(name = "E01 分段选择 · 浅色", showBackground = true, widthDp = 412, heightDp = 160)
@Composable
private fun SegmentedLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        PreviewSurface {
            CtSegmentedButtons(
                options = previewSegments,
                selectedKey = "draw",
                onSelect = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "E02 分段选择 · 深色", showBackground = true, widthDp = 412, heightDp = 160)
@Composable
private fun SegmentedDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        PreviewSurface {
            CtSegmentedButtons(
                options = previewSegments,
                selectedKey = "group",
                onSelect = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "E03 加载指示器 · 浅色", showBackground = true, widthDp = 412, heightDp = 260)
@Composable
private fun LoadingLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        PreviewSurface {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                CtLoadingIndicator(size = CtLoadingIndicatorSize.Small)
                CtLoadingIndicator(size = CtLoadingIndicatorSize.Medium)
                CtContainedLoadingIndicator(size = CtLoadingIndicatorSize.Medium)
                CtLoadingIndicator(size = CtLoadingIndicatorSize.Large)
            }
        }
    }
}

@Preview(name = "E04 加载指示器 · 深色", showBackground = true, widthDp = 412, heightDp = 260)
@Composable
private fun LoadingDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        PreviewSurface {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                CtLoadingIndicator(size = CtLoadingIndicatorSize.Large)
                CtContainedLoadingIndicator(size = CtLoadingIndicatorSize.Medium)
            }
        }
    }
}

@Preview(name = "E05 浮动动作菜单 · 浅色", showBackground = true, widthDp = 412, heightDp = 520)
@Composable
private fun FloatingActionMenuPreview() {
    ClassToolsTheme(darkTheme = false) {
        Box(modifier = Modifier.fillMaxSize()) {
            CtFloatingActionMenu(
                expanded = true,
                onExpandedChange = {},
                actions = listOf(
                    CtFabAction(label = "导出图片", icon = Icons.Rounded.Download, onClick = {}),
                    CtFabAction(label = "分享结果", icon = Icons.Rounded.Share, onClick = {}),
                    CtFabAction(label = "复制文本", icon = Icons.Rounded.Edit, onClick = {}),
                ),
            )
        }
    }
}

@Preview(name = "E06 底部弹层头部/底部 · 浅色", showBackground = true, widthDp = 412, heightDp = 300)
@Composable
private fun BottomSheetChromePreview() {
    ClassToolsTheme(darkTheme = false) {
        PreviewSurface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = CtShapeTokens.sheet,
                    ),
            ) {
                CtBottomSheetHeader(title = "选择名单", subtitle = "已选 12 / 70")
                Spacer(modifier = Modifier.height(12.dp))
                CtBottomSheetFooter(
                    primaryText = "确定",
                    onPrimary = {},
                    secondaryText = "取消",
                    onSecondary = {},
                )
            }
        }
    }
}

@Preview(name = "E07 Chip 组 · 浅色", showBackground = true, widthDp = 412, heightDp = 300)
@Composable
private fun ChipGroupLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        PreviewSurface {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CtChipGroup(
                    options = previewChips,
                    selectedKeys = setOf("all", "undo"),
                    onToggle = {},
                )
                CtFilterChipRow(
                    options = previewChips,
                    selectedKey = "done",
                    onSelect = {},
                )
            }
        }
    }
}

@Preview(name = "E08 Chip 组 · 深色", showBackground = true, widthDp = 412, heightDp = 300)
@Composable
private fun ChipGroupDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        PreviewSurface {
            CtChipGroup(
                options = previewChips,
                selectedKeys = setOf("pin"),
                onToggle = {},
            )
        }
    }
}

@Preview(name = "E09 可按压卡片 · 浅色", showBackground = true, widthDp = 412, heightDp = 200)
@Composable
private fun PressableCardPreview() {
    ClassToolsTheme(darkTheme = false) {
        PreviewSurface {
            CtPressableCard(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                contentDescription = "抽签工具卡",
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(text = "抽签", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "公平随机 · 可导出",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview(name = "E10 大标题顶栏 · 浅色", showBackground = true, widthDp = 412, heightDp = 420)
@Composable
private fun TopAppBarLargePreview() {
    ClassToolsTheme(darkTheme = false) {
        Column(modifier = Modifier.fillMaxSize()) {
            CtTopAppBarLarge(
                title = "抽签",
                subtitle = "STEP 1 抽签",
                onBack = {},
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = MaterialTheme.colorScheme.surface,
            ) {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(8) {
                        Text(
                            text = "内容行 $it",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "E10b 大标题顶栏 · 深色", showBackground = true, widthDp = 412, heightDp = 420)
@Composable
private fun TopAppBarLargeDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        Column(modifier = Modifier.fillMaxSize()) {
            CtTopAppBarLarge(title = "枫丹工具箱", subtitle = "第 12 周 · 11/3-11/9")
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = MaterialTheme.colorScheme.surface,
            ) {}
        }
    }
}

@Preview(name = "E11 数字滚动 · 浅色", showBackground = true, widthDp = 412, heightDp = 240)
@Composable
private fun AnimatedCounterPreview() {
    ClassToolsTheme(darkTheme = false) {
        PreviewSurface {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "剩余电量",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    CtAnimatedCounter(
                        value = 86,
                        style = MaterialTheme.ctEmphasized.displayLargeEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = " 度",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CtAnimatedCounter(
                    value = 12,
                    style = MaterialTheme.ctEmphasized.displaySmallEmphasized,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            )
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        content()
    }
}
