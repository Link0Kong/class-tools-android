package com.furina.classtools.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.background.BackgroundStore
import com.furina.classtools.data.background.CtBackgroundImage
import com.furina.classtools.data.background.CtBackgroundPalette
import com.furina.classtools.data.background.CtBackgroundPreset
import com.furina.classtools.data.background.CtBackgroundSelection
import com.furina.classtools.ui.components.CtToastBus
import com.furina.classtools.ui.components.expressive.CtBottomSheet
import com.furina.classtools.ui.components.expressive.CtBottomSheetHeader
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtSize
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.CtText
import com.furina.classtools.ui.theme.CtTypeScale
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import com.furina.classtools.ui.theme.rememberCtColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// =============================================================================
// 「我的 → 外观 → 背景」—— 2026-11-20 简洁化重做
//
// 改造前：一张卡片里塞 9 个色块（3×3）+ 选图行 + 模糊四档 + 状态文案，
// 在「我的」页占了将近一屏，信息密度过高、每次进设置都要滚很久。
//
// 改造后：**一行摘要 + 底部弹层**。
//   · 摘要行：当前背景缩略图 + 名称 + 一句说明 + 「更换 ›」；
//   · 点任意位置拉起 [BackgroundSheet]：预设横向色卡（一屏 4–5 个，可横滑）、
//     从相册选择、模糊档（仅自定义图片时出现）、删除自定义图片；
//   · 选完即生效并自动收起，回到「我的」只有一行。
// =============================================================================

/** 当前背景的一句话描述（摘要行副标题）。 */
private fun backgroundSummary(selection: CtBackgroundSelection): String = when {
    selection.hasImage ->
        "自定义图片 · 模糊${CtBackgroundImage.blurLabel(selection.customBlur)}"

    selection.preset.isGradient -> "预设渐变 · ${selection.preset.label}"
    else -> "预设 · ${selection.preset.label}"
}

/**
 * 背景设置块（简洁版）。
 *
 * @param store 本机背景存储（预览为 null → 只读入参，不落盘）
 */
@Composable
internal fun BackgroundSection(
    store: BackgroundStore?,
    selection: CtBackgroundSelection,
) {
    val dark = LocalCtDarkTheme.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // 系统照片选择器：Android 13+ 原生照片选择器（无权限）；旧版本自动回退到系统文件选择器。
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null || store == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val ok = store.importImage(uri)
            importing = false
            message = if (ok) "已应用自定义背景" else (store.lastError.value ?: "导入失败")
            message?.let { CtToastBus.show(it) }
            if (ok) showSheet = false
        }
    }

    SectionTitle("背景")
    GroupCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(role = Role.Button) { showSheet = true }
                .padding(horizontal = CtSpace.md, vertical = CtSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CtSpace.md),
        ) {
            BackgroundThumb(selection = selection, dark = dark)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (importing) "正在导入…" else "当前背景",
                    style = CtText.body,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = backgroundSummary(selection) + " · 点击更换",
                    style = CtTypeScale.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "更换",
                style = CtTypeScale.label,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(CtSize.iconSmall),
            )
        }
        message?.let { text ->
            Text(
                text = text,
                style = CtTypeScale.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = CtSpace.md, end = CtSpace.md, bottom = CtSpace.sm),
            )
        }
    }

    if (showSheet) {
        BackgroundSheet(
            selection = selection,
            dark = dark,
            importing = importing,
            onDismiss = { showSheet = false },
            onPickPreset = { preset ->
                message = null
                store?.selectPreset(preset)
            },
            onPickImage = {
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onBlurChange = { level -> store?.setCustomBlur(level) },
            onBlurPreview = { level -> store?.previewCustomBlur(level) },
            onDeleteImage = {
                scope.launch {
                    store?.deleteImage()
                    message = "已恢复默认背景"
        CtToastBus.show("已恢复默认背景")
                    showSheet = false
                }
            },
        )
    }
}

/** 摘要行左侧缩略图（预设渐变 / 自定义图片图标）。 */
@Composable
private fun BackgroundThumb(
    selection: CtBackgroundSelection,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(CtRadius.tile)
    val colors = CtBackgroundPalette.of(selection.preset, dark = false)
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(shape)
            .background(
                if (colors.gradient) {
                    Brush.linearGradient(listOf(colors.base, colors.second))
                } else {
                    Brush.linearGradient(listOf(colors.base, colors.base))
                },
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (selection.hasImage) {
            Icon(
                imageVector = Icons.Rounded.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(CtSize.iconSmall),
            )
        }
    }
}

/** 背景选择弹层（预设横滑 + 相册 + 模糊 + 删除）。 */
@Composable
private fun BackgroundSheet(
    selection: CtBackgroundSelection,
    dark: Boolean,
    importing: Boolean,
    onDismiss: () -> Unit,
    onPickPreset: (CtBackgroundPreset) -> Unit,
    onPickImage: () -> Unit,
    onBlurChange: (Int) -> Unit,
    onBlurPreview: (Int) -> Unit,
    onDeleteImage: () -> Unit,
) {
    val c = rememberCtColors()
    CtBottomSheet(
        onDismissRequest = onDismiss,
        // 不透明容器：半透明会让下方「外观」文字透上来（实测背景模块弹层显脏）。
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        CtBottomSheetHeader(
            title = "背景",
            subtitle = "只存本机 · 不上传、不进云同步",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CtSpace.lg)
                .padding(bottom = CtSpace.lg),
        ) {
            // ---- 预设：横向色卡（一屏 4–5 个，可横滑） ----
            Text(
                text = "预设",
                style = CtTypeScale.caption,
                color = c.textSecondary,
            )
            Spacer(Modifier.size(CtSpace.xs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(CtSpace.sm),
            ) {
                CtBackgroundPreset.grid.forEach { preset ->
                    PresetChip(
                        preset = preset,
                        selected = !selection.hasImage && selection.preset == preset,
                        onClick = { onPickPreset(preset) },
                    )
                }
            }

            Spacer(Modifier.size(CtSpace.lg))

            // ---- 相册 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = CtSize.minTouch)
                    .clip(RoundedCornerShape(CtRadius.cardCompact))
                    .background(c.container)
                    .clickable(enabled = !importing, role = Role.Button, onClick = onPickImage)
                    .padding(horizontal = CtSpace.md, vertical = CtSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CtSpace.sm),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(CtSize.icon),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (importing) "正在导入…" else "从相册选择图片",
                        style = CtText.body,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "长边 ≤${CtBackgroundImage.MAX_LONG_EDGE} · JPEG ${CtBackgroundImage.JPEG_QUALITY}",
                        style = CtTypeScale.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selection.hasImage) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "删除自定义背景",
                        tint = c.error,
                        modifier = Modifier
                            .size(CtSize.minTouch)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onDeleteImage)
                            .padding(CtSpace.sm),
                    )
                }
            }

            // ---- 模糊（仅自定义图片时出现；0–40dp 连续滑杆） ----
            if (selection.hasImage) {
                Spacer(Modifier.size(CtSpace.md))
                var blurDraft by remember {
                    mutableFloatStateOf(selection.customBlur.toFloat())
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "图片模糊（不影响前景文字）",
                        style = CtTypeScale.caption,
                        color = c.textSecondary,
                    )
                    Text(
                        text = CtBackgroundImage.blurLabel(blurDraft.roundToInt()),
                        style = CtTypeScale.caption,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = blurDraft,
                    onValueChange = {
                        blurDraft = it
                        onBlurPreview(it.roundToInt())
                    },
                    onValueChangeFinished = { onBlurChange(blurDraft.roundToInt()) },
                    valueRange = CtBackgroundSelection.MIN_CUSTOM_BLUR.toFloat()..
                        CtBackgroundSelection.MAX_CUSTOM_BLUR.toFloat(),
                    // M3 连续滑杆：不画 39 个刻点，拖动时实时预览、松手持久化。
                    steps = 0,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            stateDescription = CtBackgroundImage.blurLabel(blurDraft.roundToInt())
                        },
                )
            }
        }
    }
}

/** 预设色卡（竖向：色块 + 名称；横滑用）。 */
@Composable
private fun PresetChip(
    preset: CtBackgroundPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // 缩略图始终用浅色参数展示「这个预设是什么颜色」，避免深色模式下所有浅色预设都发黑。
    val colors = CtBackgroundPalette.of(preset, dark = false)
    val shape = RoundedCornerShape(CtRadius.tile)
    Column(
        modifier = Modifier
            .width(84.dp)
            .clip(shape)
            .clickable(role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(shape)
                .background(
                    if (colors.gradient) {
                        Brush.linearGradient(listOf(colors.base, colors.second))
                    } else {
                        Brush.linearGradient(listOf(colors.base, colors.base))
                    },
                )
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(CtSize.iconSmall),
                    )
                }
            }
        }
        Spacer(Modifier.size(CtSpace.xxs))
        Text(
            text = preset.label,
            style = CtTypeScale.caption,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}



