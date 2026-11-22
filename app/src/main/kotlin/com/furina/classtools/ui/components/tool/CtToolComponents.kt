package com.furina.classtools.ui.components.tool

import androidx.compose.foundation.layout.widthIn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.components.EmptyState
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.CtText
import com.furina.classtools.ui.theme.HarmonyTokens
import com.furina.classtools.ui.theme.harmonyTint
import com.furina.classtools.ui.theme.rememberHarmonyColors

// =============================================================================
// 工具页统一组件（2026-11-17 按鸿蒙端重做：白卡 18 圆角浅阴影 + 19/15/13/12 字号层级）
//
// 只改视觉，不改任何业务逻辑；各工具页继续按原 API 调用即可获得鸿蒙观感。
// =============================================================================

/**
 * 工具页内容卡：纯白（深色 surface）18dp 圆角 + 轻阴影；无玻璃/渐变/重描边。
 * 可选标题行（标题 15 Medium + 说明 12）。
 */
@Composable
fun CtToolCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = HarmonyTokens.CardPadding,
        vertical = HarmonyTokens.CardPadding,
    ),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = rememberHarmonyColors()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = HarmonyTokens.CardShadowElevation,
                shape = RoundedCornerShape(HarmonyTokens.RadiusCard),
                clip = false,
                ambientColor = HarmonyTokens.CardShadowColor,
                spotColor = HarmonyTokens.CardShadowColor,
            ),
        shape = RoundedCornerShape(HarmonyTokens.RadiusCard),
        color = colors.card,
        contentColor = colors.textPrimary,
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            if (title != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = CtText.bodyMedium,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = CtText.caption,
                                color = colors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    trailing?.invoke()
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            content()
        }
    }
}

/** 分组小标题：Title Large（22sp / 400，MD3 分区标题）+ 可选右侧文字操作。 */
@Composable
fun CtToolSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = rememberHarmonyColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = CtText.heading,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = CtText.caption,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** 工具页主操作按钮：M3 filled button，视觉高 40dp + 48dp 触控目标。 */
@Composable
fun CtToolPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        enabled = enabled,
        shape = CtShapeTokens.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 工具页分段选择：直接使用 M3 SegmentedButton（40dp，secondaryContainer 选中态）。 */
@Composable
fun CtToolSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    com.furina.classtools.ui.components.expressive.CtSegmentedButtons(
        options = options.mapIndexed { index, label ->
            com.furina.classtools.ui.components.expressive.CtSegmentOption(
                key = index.toString(),
                label = label,
            )
        },
        selectedKey = selectedIndex.toString(),
        onSelect = { key -> key.toIntOrNull()?.let(onSelect) },
        modifier = modifier.fillMaxWidth(),
        showCheckIcon = false,
    )
}

/** 工具页信息行：左侧标签 13 + 右侧数值 15 Medium，最小高度 44dp。 */
@Composable
fun CtToolInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = rememberHarmonyColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leading?.invoke()
        Text(
            text = label,
            style = CtText.label,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = CtText.bodyMedium,
            color = if (valueColor == Color.Unspecified) colors.textPrimary else valueColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 小统计块：大数字 26 Bold + 标签 12（鸿蒙 hero/电费数字层级）。 */
@Composable
fun CtToolStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
) {
    val colors = rememberHarmonyColors()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(HarmonyTokens.RadiusInput))
            .background(if (colors.dark) colors.bar else colors.background)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = value,
            style = CtText.title,
            color = if (valueColor == Color.Unspecified) colors.textPrimary else valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = CtText.caption,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 填充式输入框：14dp 圆角、BG 底、无硬描边；标题 13。 */
@Composable
fun CtToolField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    // M3 filled text field：4dp 顶圆角、surfaceContainerHighest、底部指示线；标签由 M3 浮动。
    androidx.compose.material3.TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.widthIn(min = 0.dp).fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        label = { Text(label) },
        placeholder = placeholder?.let { p ->
            { Text(text = p, style = MaterialTheme.typography.bodyLarge) }
        },
        trailingIcon = trailing,
        keyboardOptions = keyboardOptions,
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}

/** 工具页筛选 Chip：标准 M3 FilterChip（32dp，8dp 圆角，选中 secondaryContainer）。 */
@Composable
fun CtToolChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
    )
}

/**
 * 工具页统一错误态：卡片 + 图标 + 用户可读说明 + 全宽主操作「重试」。
 *
 * 2026-11-21 统一：此前各工具页有的裸空态、有的满宽按钮、有的文字链接，
 * 且会把数据库/SQL 原文直接展示给用户。以后错误态一律用这个组件。
 */
@Composable
fun CtToolErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "加载失败",
    retryText: String = "重试",
) {
    CtToolCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
    ) {
        EmptyState(
            icon = Icons.Rounded.ErrorOutline,
            title = title,
            message = message,
        )
        Spacer(Modifier.height(CtSpace.sm))
        CtToolPrimaryButton(text = retryText, onClick = onRetry, modifier = Modifier.fillMaxWidth())
    }
}








