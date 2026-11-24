package com.furina.classtools.ui.screens.draw

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.furina.classtools.features.draw.DrawConfig
import com.furina.classtools.features.draw.DrawEntry
import com.furina.classtools.features.draw.DrawErrorCode
import com.furina.classtools.features.draw.DrawPoolSource
import com.furina.classtools.features.draw.DrawRemoveLog
import com.furina.classtools.features.draw.DrawResult
import com.furina.classtools.features.draw.DrawSortMode
import com.furina.classtools.features.draw.DrawValidation
import com.furina.classtools.ui.components.tool.CtToolField
import com.furina.classtools.ui.theme.CtSpace

@Composable
internal fun DrawConfiguration(
    config: DrawConfig,
    validation: DrawValidation,
    showOptions: Boolean,
    onToggleOptions: () -> Unit,
    autoRedraw: Boolean,
    onAutoRedrawChange: (Boolean) -> Unit,
    rosterCount: Int,
    rosterLoading: Boolean,
    onPickRoster: () -> Unit,
    onConfigChange: (DrawConfig) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val error = (validation as? DrawValidation.Invalid)?.error
    Card(colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CtSpace.md),
            verticalArrangement = Arrangement.spacedBy(CtSpace.sm),
        ) {
            Text("抽签参数", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text("范围", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            BoxWithConstraints {
                val start: @Composable (Modifier) -> Unit = { modifier ->
                    DrawNumberField(config.start, "起始", modifier) { onConfigChange(config.copy(start = it)) }
                }
                val end: @Composable (Modifier) -> Unit = { modifier ->
                    DrawNumberField(config.end, "结束", modifier) { onConfigChange(config.copy(end = it)) }
                }
                if (maxWidth < 160.dp * LocalDensity.current.fontScale) {
                    Column(verticalArrangement = Arrangement.spacedBy(CtSpace.sm)) {
                        start(Modifier.fillMaxWidth())
                        end(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(CtSpace.sm)) {
                        start(Modifier.weight(1f))
                        end(Modifier.weight(1f))
                    }
                }
            }
            Text("抽取个数", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            Row(
                horizontalArrangement = Arrangement.spacedBy(CtSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DrawNumberField(config.count, "个数", Modifier.weight(1f)) { onConfigChange(config.copy(count = it)) }
                FilledTonalIconButton(
                    enabled = config.count > DrawConfig.MIN_COUNT,
                    onClick = { onConfigChange(config.copy(count = config.count - 1)) },
                ) { Icon(Icons.Rounded.Remove, contentDescription = "少抽一个") }
                FilledTonalIconButton(
                    enabled = config.count < DrawConfig.MAX_COUNT,
                    onClick = { onConfigChange(config.copy(count = config.count + 1)) },
                ) { Icon(Icons.Rounded.Add, contentDescription = "多抽一个") }
            }

            val poolSize = (validation as? DrawValidation.Valid)?.numbers?.size
            if (poolSize != null) {
                Text("可抽 $poolSize 个序号", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            } else if (error != null) {
                val text = when (error.code) {
                    DrawErrorCode.RANGE_INVALID -> if (config.start == 0 || config.end == 0) "请填写起始和结束序号" else error.message
                    DrawErrorCode.ROSTER_REQUIRED -> if (rosterLoading) "正在读取班级名单…" else "请在更多设置中选择参与名单"
                    else -> error.message
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (rosterLoading && error.code == DrawErrorCode.ROSTER_REQUIRED) scheme.onSurfaceVariant else scheme.error,
                )
            }

            HorizontalDivider()
            TextButton(onClick = onToggleOptions, contentPadding = PaddingValues(horizontal = CtSpace.xs)) {
                Icon(if (showOptions) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                Text(if (showOptions) "收起更多设置" else "更多设置")
            }
            AnimatedVisibility(showOptions) {
                Column(verticalArrangement = Arrangement.spacedBy(CtSpace.sm)) {
                    CtToolField(
                        value = config.excludedText,
                        onValueChange = { onConfigChange(config.copy(excludedText = it)) },
                        label = "排除序号",
                        placeholder = "如 3,7,10-12",
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DrawToggle("允许重复", config.allowDuplicates) {
                        onConfigChange(config.copy(allowDuplicates = it))
                    }
                    DrawToggle("剔除后自动补抽", autoRedraw, onAutoRedrawChange)
                    HorizontalDivider()
                    Text("参与名单", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = when {
                            config.poolSource == DrawPoolSource.ROSTER && rosterCount > 0 -> "已选 $rosterCount 人，在号码范围内抽取"
                            rosterLoading -> "正在读取班级名单…"
                            rosterCount == 0 -> "班级名单暂不可用"
                            else -> "当前抽取号码，可选择班级名单"
                        },
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpace.xs)) {
                        TextButton(onClick = onPickRoster, enabled = !rosterLoading) {
                            Text(if (rosterCount == 0 && !rosterLoading) "重试读取名单" else "选择名单")
                        }
                        if (config.poolSource == DrawPoolSource.ROSTER) {
                            TextButton(onClick = { onConfigChange(config.copy(poolSource = DrawPoolSource.RANGE)) }) {
                                Text("改抽号码")
                            }
                        }
                    }
                    Text("结果排序", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpace.xs)) {
                        DrawSortMode.entries.forEach { mode ->
                            FilterChip(
                                selected = config.sort == mode,
                                onClick = { onConfigChange(config.copy(sort = mode)) },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawNumberField(value: Int, label: String, modifier: Modifier, onChange: (Int) -> Unit) {
    CtToolField(
        value = if (value == 0) "" else value.toString(),
        onValueChange = { text -> onChange(text.filter { it in '0'..'9' }.take(4).toIntOrNull() ?: 0) },
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun DrawToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = CtSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpace.sm),
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun DrawResultHeader(
    result: DrawResult?,
    exporting: Boolean = false,
    onExport: () -> Unit,
    onRefill: () -> Unit,
    onEdit: () -> Unit,
    onClear: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CtSpace.md),
            verticalArrangement = Arrangement.spacedBy(CtSpace.xs),
        ) {
            Text("抽签结果", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(
                text = if (result == null) "开始抽签后，结果会显示在这里"
                else "已抽 ${result.entries.size} / ${result.config.count} 个 · 范围 ${result.config.start}–${result.config.end}",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            if (result != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpace.xs)) {
                    if (result.entries.size < result.config.count) {
                        TextButton(onClick = onRefill) { Text("补抽") }
                    }
                    if (result.entries.isNotEmpty()) {
                        TextButton(onClick = onExport, enabled = !exporting) { Text(if (exporting) "正在确认存证…" else "分享结果") }
                    }
                    TextButton(onClick = onEdit) { Text("调整参数") }
                    TextButton(onClick = onClear) { Text("清空结果") }
                }
            }
        }
    }
}

@Composable
internal fun DrawResultEntry(entry: DrawEntry, onRemove: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = scheme.surfaceContainerLow) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = CtSpace.md, end = CtSpace.xs, top = CtSpace.xs, bottom = CtSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CtSpace.md),
        ) {
            Surface(shape = MaterialTheme.shapes.small, color = scheme.primaryContainer) {
                Text(
                    text = entry.number.toString(),
                    modifier = Modifier.padding(horizontal = CtSpace.sm, vertical = CtSpace.xs),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onPrimaryContainer,
                )
            }
            Text(
                text = entry.name.orEmpty(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = "剔除 ${entry.label}")
            }
        }
    }
}

@Composable
internal fun DrawLogToggle(count: Int, expanded: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
        Text("剔除记录（$count）")
    }
}

@Composable
internal fun DrawLogEntry(record: DrawRemoveLog) {
    val added = record.added
    Text(
        text = if (added == null) "剔除 ${record.removed.label}"
        else "剔除 ${record.removed.label} · 补抽 ${added.label}",
        modifier = Modifier.padding(horizontal = CtSpace.md),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
