package com.furina.classtools.ui.screens.draw

import com.furina.classtools.ui.components.light.ctLightMaterial
import com.furina.classtools.ui.theme.CtLightScene
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.furina.classtools.ui.components.tool.CtToolCard
import com.furina.classtools.ui.components.tool.CtToolChip
import com.furina.classtools.ui.components.tool.CtToolField
import com.furina.classtools.ui.components.tool.CtToolInfoRow
import com.furina.classtools.ui.components.tool.CtToolPrimaryButton
import com.furina.classtools.ui.components.tool.CtToolSectionHeader
import com.furina.classtools.ui.components.tool.CtToolSegmented
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShapeTokens

// =============================================================================
// 抽签三步 UI 组件（无状态，只接收数据 + 回调，便于预览与复用）
// =============================================================================

/** v6 紧凑卡：标题 titleSmall + 14/12 内边距，信息密度对齐小程序（说明文案只留必要提示）。 */
@Composable
private fun DrawCompactCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    content: @Composable () -> Unit,
) {
    CtToolCard(modifier = modifier, contentPadding = contentPadding) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null || trailing != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

/** 卡片内紧凑信息行（替代 44dp 的 CtToolInfoRow）。 */
@Composable
private fun DrawCompactInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 步骤条：已完成/当前步骤可点击回退（只允许向前回退，不允许跳过）。 */
@Composable
fun DrawStepIndicator(
    current: DrawStep,
    onSelect: (DrawStep) -> Unit,
    modifier: Modifier = Modifier,
) {
    CtToolSegmented(
        options = DrawStep.entries.map { "${it.index} ${it.title}" },
        selectedIndex = current.index - 1,
        onSelect = { index ->
            DrawStep.entries.getOrNull(index)?.let { target ->
                if (target.index <= current.index) onSelect(target)
            }
        },
        modifier = modifier,
    )
}

/** STEP1 参数表单卡（实时校验：错误显示在对应输入框下或卡片底部）。 */
/** STEP1 参数表单卡（v6：CtToolCard + CtToolField + CtToolChip；实时校验）。 */
@OptIn(ExperimentalLayoutApi::class)
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

    DrawCompactCard(
        modifier = modifier,
        title = "抽签参数",
        trailing = { CtPill(text = "共 ${config.upperBound - config.lowerBound + 1} 个号") },
    ) {
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

        CtToolField(
            value = excludedText,
            onValueChange = { text ->
                excludedText = text
                onConfigChange(config.copy(excludedText = text))
            },
            label = "排除序号",
            placeholder = "如 3,7,10-12",
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error?.code == DrawErrorCode.EXCLUSION_INVALID) {
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

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
                modifier = Modifier.semantics { contentDescription = "允许重复" },
            )
        }

        Text("候选来源", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DrawPoolSource.entries.forEach { source ->
                val selectable = enabled && (source == DrawPoolSource.RANGE || rosterCount > 0)
                CtToolChip(
                    text = if (source == DrawPoolSource.ROSTER) {
                        "${source.label}($rosterCount)"
                    } else {
                        source.label
                    },
                    selected = config.poolSource == source,
                    onClick = { if (selectable) onConfigChange(config.copy(poolSource = source)) },
                )
            }
        }
        Text("结果排序", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DrawSortMode.entries.forEach { mode ->
                CtToolChip(
                    text = mode.label,
                    selected = config.sort == mode,
                    onClick = { if (enabled) onConfigChange(config.copy(sort = mode)) },
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

@Composable
private fun NumberField(
    value: String,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    CtToolField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() }.take(4)) },
        label = label,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
    DrawCompactCard(
        modifier = modifier,
        title = "候选池",
        trailing = { CtPill(text = if (numbers != null) "${numbers.size} 个" else "—") },
    ) {
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

/** STEP2 候选预览卡（v6）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawPreviewCard(
    candidates: List<DrawEntry>,
    modifier: Modifier = Modifier,
) {
    DrawCompactCard(
        modifier = modifier.animateContentSize(animationSpec = CtMotion.spatialSize),
        title = "候选名单",
        trailing = { CtPill(text = "${candidates.size} 人/号") },
    ) {
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

/** 结果卡（胶囊 + 剔除 + 排序 + 剔除记录 + 导出；v6 卡片/按钮）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawResultCard(
    result: DrawResult?,
    autoRedraw: Boolean,
    onToggleAutoRedraw: (Boolean) -> Unit,
    onRemove: (Int) -> Unit,
    onSortChange: (DrawSortMode) -> Unit,
    onReroll: () -> Unit,
    onEditConfig: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasEntries = result != null && result.entries.isNotEmpty()
    val trailing: (@Composable () -> Unit)? = if (hasEntries) {
        {
            CtPill(text = "${result!!.entries.size} 个 · 池 ${result.pool.size}")
        }
    } else {
        null
    }
    DrawCompactCard(
        modifier = modifier.animateContentSize(animationSpec = CtMotion.spatialSize),
        title = "抽签结果",
        trailing = trailing,
    ) {
        if (result == null || result.entries.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Inbox,
                title = "还没有抽签结果",
                message = "返回上一步设置参数后开始抽签",
            )
            return@DrawCompactCard
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            result.entries.forEachIndexed { index, entry ->
                DrawEntryChip(entry = entry, onRemove = { onRemove(index) })
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "剔除后自动重抽",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Switch(
                checked = autoRedraw,
                onCheckedChange = onToggleAutoRedraw,
                modifier = Modifier.semantics { contentDescription = "剔除后自动重抽" },
            )
        }

        Text("排序", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DrawSortMode.entries.forEach { mode ->
                CtToolChip(
                    text = mode.label,
                    selected = result.config.sort == mode,
                    onClick = { onSortChange(mode) },
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
            OutlinedButton(onClick = onReroll) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("重新抽签")
            }
        }

        OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("导出抽签图")
        }
    }
}

/** STEP2/3 顶部参数摘要（只读）。 */
/** STEP2/3 顶部参数摘要（只读，v6 信息行）。 */
@Composable
fun DrawConfigSummaryCard(
    config: DrawConfig,
    modifier: Modifier = Modifier,
) {
    DrawCompactCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        DrawCompactInfoRow(label = "范围", value = "${config.start}–${config.end}")
        DrawCompactInfoRow(label = "个数", value = config.count.toString())
        DrawCompactInfoRow(label = "重复", value = if (config.allowDuplicates) "允许" else "不重复")
        DrawCompactInfoRow(label = "排序", value = config.sort.label)
        DrawCompactInfoRow(label = "排除", value = config.excludedText.ifBlank { "无" })
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
        shape = RoundedCornerShape(CtRadius.tile),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = number?.toString() ?: name.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

/** 底部动作条（v6：主按钮统一 CtToolPrimaryButton；避让 gesture bar）。 */
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
        modifier = modifier
            .fillMaxWidth()
            .ctLightMaterial(
                CtLightScene.BOTTOM_DOCK,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                bodySafe = true,
            ),
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step != DrawStep.CONFIG) {
                OutlinedButton(
                    onClick = onBackStep,
                    modifier = Modifier.heightIn(min = 40.dp),
                    enabled = !isDrawing,
                ) { Text("上一步") }
            }
            when (step) {
                DrawStep.CONFIG -> CtToolPrimaryButton(
                    text = "下一步：预览候选",
                    onClick = onNext,
                    enabled = canContinue,
                    modifier = Modifier.weight(1f),
                )

                DrawStep.PREVIEW -> CtToolPrimaryButton(
                    text = if (isDrawing) "抽签中…" else "开始抽签",
                    onClick = onStartDraw,
                    enabled = !isDrawing,
                    modifier = Modifier.weight(1f),
                )

                DrawStep.RESULT -> CtToolPrimaryButton(
                    text = "完成",
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 全屏错误态（身份失效/恢复失败；v6 卡片 + 主按钮）。 */
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DrawCompactCard {
            EmptyState(
                icon = Icons.Rounded.Inbox,
                title = "暂时无法打开抽签",
                message = message,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("返回") }
            CtToolPrimaryButton(
                text = "重试",
                onClick = onRetry,
                modifier = Modifier.weight(1f),
            )
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

