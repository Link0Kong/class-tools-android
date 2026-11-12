package com.furina.classtools.ui.screens.draw

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.features.draw.DrawConfig
import com.furina.classtools.features.draw.DrawEntry
import com.furina.classtools.features.draw.DrawError
import com.furina.classtools.features.draw.DrawErrorCode
import com.furina.classtools.features.draw.DrawPoolSource
import com.furina.classtools.features.draw.DrawResult
import com.furina.classtools.features.draw.DrawSortMode
import com.furina.classtools.features.draw.DrawStep
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.EmptyState
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtShapeTokens

// =============================================================================
// 抽签三步 UI 组件（无状态，只接收数据 + 回调，便于预览与复用）
// =============================================================================

/** 步骤条：已完成/当前步骤可点击回退（只允许向前回退，不允许跳过）。 */
@Composable
fun DrawStepIndicator(
    current: DrawStep,
    onSelect: (DrawStep) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DrawStep.entries.forEach { step ->
            val isCurrent = step == current
            val isDone = step.index < current.index
            val clickable = isDone
            val background = when {
                isCurrent -> MaterialTheme.colorScheme.primary
                isDone -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
            val content = when {
                isCurrent -> MaterialTheme.colorScheme.onPrimary
                isDone -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(CtShapeTokens.pill)
                    .background(background)
                    .then(if (clickable) Modifier.clickable { onSelect(step) } else Modifier)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isDone) "✓ ${step.title}" else "${step.index} ${step.title}",
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** STEP1 参数表单卡（实时校验：错误显示在对应输入框下或卡片底部）。 */
@Composable
fun DrawConfigCard(
    config: DrawConfig,
    error: DrawError?,
    rosterCount: Int,
    enabled: Boolean,
    onConfigChange: (DrawConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var startText by remember(config.start) { mutableStateOf(config.start.toString()) }
    var endText by remember(config.end) { mutableStateOf(config.end.toString()) }
    var countText by remember(config.count) { mutableStateOf(config.count.toString()) }
    var excludedText by remember(config.excludedText) { mutableStateOf(config.excludedText) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "抽签参数",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                CtPill(text = "共 ${config.upperBound - config.lowerBound + 1} 个号")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField(
                    value = startText,
                    label = "起始",
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onValueChange = { text ->
                        startText = text
                        text.toIntOrNull()?.let { onConfigChange(config.copy(start = it)) }
                    },
                )
                NumberField(
                    value = endText,
                    label = "结束",
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onValueChange = { text ->
                        endText = text
                        text.toIntOrNull()?.let { onConfigChange(config.copy(end = it)) }
                    },
                )
            }

            NumberField(
                value = countText,
                label = "抽取个数",
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                onValueChange = { text ->
                    countText = text
                    text.toIntOrNull()?.let { onConfigChange(config.copy(count = it)) }
                },
            )

            OutlinedTextField(
                value = excludedText,
                onValueChange = { text ->
                    excludedText = text
                    onConfigChange(config.copy(excludedText = text))
                },
                label = { Text("排除序号") },
                placeholder = { Text("如 3,7,10-12") },
                singleLine = true,
                enabled = enabled,
                isError = error?.code == DrawErrorCode.EXCLUSION_INVALID,
                supportingText = {
                    if (error?.code == DrawErrorCode.EXCLUSION_INVALID) {
                        Text(error.message)
                    } else {
                        Text("格式示例：3,7,10-12")
                    }
                },
                shape = CtShapeTokens.input,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "允许重复",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "同一序号可被抽中多次",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = config.allowDuplicates,
                    onCheckedChange = { onConfigChange(config.copy(allowDuplicates = it)) },
                    enabled = enabled,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = "候选来源",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DrawPoolSource.entries.forEach { source ->
                    FilterChip(
                        selected = config.poolSource == source,
                        onClick = { onConfigChange(config.copy(poolSource = source)) },
                        enabled = enabled && (source == DrawPoolSource.RANGE || rosterCount > 0),
                        label = {
                            Text(
                                if (source == DrawPoolSource.ROSTER) {
                                    "${source.label}($rosterCount)"
                                } else {
                                    source.label
                                },
                            )
                        },
                    )
                }
            }
            if (rosterCount <= 0) {
                Text(
                    text = "班级名单未获取，暂只支持号码范围",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "结果排序",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DrawSortMode.entries.forEach { mode ->
                    FilterChip(
                        selected = config.sort == mode,
                        onClick = { onConfigChange(config.copy(sort = mode)) },
                        enabled = enabled,
                        label = { Text(mode.label) },
                    )
                }
            }

            val cardError = error?.takeIf { it.code !in setOf(DrawErrorCode.EXCLUSION_INVALID) }
            if (cardError != null) {
                Text(
                    text = cardError.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() }.take(4)) },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = CtShapeTokens.input,
        modifier = modifier,
    )
}

/** STEP1 右侧/下方：候选池预览（按当前校验结果显示数量与示例）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawPoolPreviewCard(
    config: DrawConfig,
    numbers: List<Int>?,
    error: DrawError?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "候选池",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                CtPill(text = if (numbers != null) "${numbers.size} 个" else "—")
            }
            when {
                error != null -> Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                numbers == null || numbers.isEmpty() -> Text(
                    text = "设置参数后显示候选",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    Text(
                        text = "范围 ${config.start}–${config.end}，排除 ${config.excludedText.ifBlank { "无" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        numbers.take(24).forEach { n -> DrawNumberChip(number = n, name = null) }
                        if (numbers.size > 24) {
                            DrawNumberChip(number = null, name = "+${numbers.size - 24}")
                        }
                    }
                }
            }
        }
    }
}

/** STEP2 候选预览卡。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawPreviewCard(
    candidates: List<DrawEntry>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(animationSpec = CtMotion.spatialSize),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "候选名单",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                CtPill(text = "${candidates.size} 人/号")
            }
            Text(
                text = "确认候选无误后点「开始抽签」；结果页可剔除并重抽。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                candidates.forEach { entry ->
                    DrawEntryChip(entry = entry, onRemove = null)
                }
            }
        }
    }
}

/** STEP3 结果卡（胶囊 + 剔除 + 排序 + 剔除记录）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawResultCard(
    result: DrawResult?,
    autoRedraw: Boolean,
    /** D-02：本结果已用于分组后显示徽标（由宿主传入，重新抽签/调整参数后重置）。 */
    grouped: Boolean = false,
    onToggleAutoRedraw: (Boolean) -> Unit,
    onRemove: (Int) -> Unit,
    onSortChange: (DrawSortMode) -> Unit,
    onReroll: () -> Unit,
    onEditConfig: () -> Unit,
    onExport: () -> Unit,
    onGroup: () -> Unit,
    onExportSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(animationSpec = CtMotion.spatialSize),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (result == null || result.entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Inbox,
                    title = "还没有抽签结果",
                    message = "返回上一步设置参数后开始抽签",
                )
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "抽签结果",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                CtPill(text = "${result.entries.size} 个 · 池 ${result.pool.size}")
                if (grouped) {
                    Spacer(Modifier.width(6.dp))
                    CtPill(
                        text = "已用于分组",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                result.entries.forEachIndexed { index, entry ->
                    DrawEntryChip(entry = entry, onRemove = { onRemove(index) })
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "剔除后自动重抽",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "剔除一项后立即从剩余池补抽",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = autoRedraw, onCheckedChange = onToggleAutoRedraw)
            }

            Text(
                text = "排序",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DrawSortMode.entries.forEach { mode ->
                    FilterChip(
                        selected = result.config.sort == mode,
                        onClick = { onSortChange(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }

            if (result.log.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "剔除重抽记录（${result.log.size}）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                result.log.forEachIndexed { i, entry ->
                    val added = entry.added?.label ?: "未补抽"
                    Text(
                        text = "#${i + 1} 剔除 ${entry.removed.label} → $added",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onEditConfig) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("调整参数")
                }
                FilledTonalButton(onClick = onReroll) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("重新抽签")
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onGroup) {
                    Icon(Icons.Rounded.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("用此结果分组")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onExport) { Text("导出抽签图") }
                Button(onClick = onExportSheet) {
                    Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("导出结果单")
                }
            }
        }
    }
}

/** STEP2/3 顶部参数摘要（只读）。 */
@Composable
fun DrawConfigSummaryCard(
    config: DrawConfig,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CtShapeTokens.listItem,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row {
                SummaryItem("范围", "${config.start}–${config.end}", Modifier.weight(1f))
                SummaryItem("个数", config.count.toString(), Modifier.weight(1f))
            }
            Row {
                SummaryItem("重复", if (config.allowDuplicates) "允许" else "不重复", Modifier.weight(1f))
                SummaryItem("排序", config.sort.label, Modifier.weight(1f))
            }
            SummaryItem("排除", config.excludedText.ifBlank { "无" }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 步骤指示上方的阶段标题。 */
@Composable
fun DrawStepHeadline(step: DrawStep, modifier: Modifier = Modifier) {
    val title = when (step) {
        DrawStep.CONFIG -> "设置抽签参数"
        DrawStep.PREVIEW -> "确认候选"
        DrawStep.RESULT -> "抽签结果"
    }
    val subtitle = when (step) {
        DrawStep.CONFIG -> "范围、个数、排除号与排序"
        DrawStep.PREVIEW -> "开始前可再检查一遍名单"
        DrawStep.RESULT -> "可剔除重抽、排序并导出"
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 结果/候选胶囊：序号 + 姓名（可带剔除 ×）。 */
@Composable
fun DrawEntryChip(
    entry: DrawEntry,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = if (onRemove != null) 48.dp else 40.dp),
        shape = CircleShape,
        color = if (entry.name.isNullOrBlank()) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (entry.name.isNullOrBlank()) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = if (onRemove != null) 4.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.number.toString().padStart(2, '0'),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (!entry.name.isNullOrBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = entry.name!!,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "剔除 ${entry.label}",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** 候选池里的数字胶囊（无姓名/占位）。 */
@Composable
fun DrawNumberChip(number: Int?, name: String?) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = number?.toString() ?: name.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

/** 底部动作条（避让 gesture bar）。 */
@Composable
fun DrawBottomBar(
    step: DrawStep,
    canContinue: Boolean,
    isDrawing: Boolean,
    onNext: () -> Unit,
    onStartDraw: () -> Unit,
    onBackStep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step != DrawStep.CONFIG) {
                OutlinedButton(
                    onClick = onBackStep,
                    modifier = Modifier.height(52.dp),
                    enabled = !isDrawing,
                ) { Text("上一步") }
            }
            when (step) {
                DrawStep.CONFIG -> Button(
                    onClick = onNext,
                    enabled = canContinue,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = CtShapeTokens.pill,
                ) { Text("下一步：预览候选") }
                DrawStep.PREVIEW -> Button(
                    onClick = onStartDraw,
                    enabled = !isDrawing,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = CtShapeTokens.pill,
                ) {
                    if (isDrawing) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("抽签中…")
                    } else {
                        Text("开始抽签")
                    }
                }
                DrawStep.RESULT -> Button(
                    onClick = onNext,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = CtShapeTokens.pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) { Text("完成") }
            }
        }
    }
}

/** 全屏错误态（身份失效/恢复失败）。 */
@Composable
fun DrawErrorState(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            icon = Icons.Rounded.Inbox,
            title = "暂时无法打开抽签",
            message = message,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack) { Text("返回") }
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

/** 提示文案（加载/空态辅助）。 */
@Composable
fun DrawHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}
