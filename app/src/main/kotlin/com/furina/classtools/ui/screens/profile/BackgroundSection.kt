package com.furina.classtools.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.background.BackgroundStore
import com.furina.classtools.data.background.CtBackgroundImage
import com.furina.classtools.data.background.CtBackgroundPalette
import com.furina.classtools.data.background.CtBackgroundPreset
import com.furina.classtools.data.background.CtBackgroundSelection
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtSize
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.CtText
import com.furina.classtools.ui.theme.CtTypeScale
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import com.furina.classtools.ui.theme.rememberCtColors
import com.furina.classtools.ui.components.glass.CtGlassCard
import kotlinx.coroutines.launch

/**
 * 「我的 → 外观 → 背景」设置块。
 *
 * 三种入口：
 * 1) **预设**：9 个色块（暖白/雾青/灰蓝/暖沙/深空 + 4 组渐变），3 列网格，点按即生效；
 * 2) **自定义图片**：系统照片选择器（`PickVisualMedia`，Android 13+ 走系统照片选择器，
 *    低版本自动回退 `GetContent`），选图后由 [BackgroundStore] 压缩落盘；
 * 3) **模糊档**：不模糊 / 轻 / 标准 / 强（只作用在背景图片层，前景永远清晰）。
 *
 * 所有设置**只存本机**（SharedPreferences + App 私有目录），不上传、不进云同步。
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
        }
    }

    SectionTitle("背景")
    GroupCard {
        // ---- 预设色块（3 列网格） ----
        Column(modifier = Modifier.padding(CtSpace.md)) {
            Text(
                text = "预设",
                style = CtTypeScale.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(CtSpace.sm))
            CtBackgroundPreset.grid.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CtSpace.sm),
                ) {
                    row.forEach { preset ->
                        BackgroundSwatch(
                            preset = preset,
                            dark = dark,
                            selected = !selection.hasImage && selection.preset == preset,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                message = null
                                store?.selectPreset(preset)
                            },
                        )
                    }
                    // 补齐空位（9 个预设恰为 3×3；保留以兼容以后增删）
                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
                Spacer(Modifier.size(CtSpace.sm))
            }

            RowDivider()

            // ---- 自定义图片 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = CtSize.minTouch)
                    .clickable(enabled = !importing) {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CtSpace.sm),
            ) {
                Surface(
                    shape = RoundedCornerShape(CtRadius.tile),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Box(modifier = Modifier.size(CtSize.glyphBox), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(CtSize.icon),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (importing) "正在导入…" else "从相册选择图片",
                        style = CtText.body,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "只存本机 · 长边 ≤${CtBackgroundImage.MAX_LONG_EDGE} · JPEG ${CtBackgroundImage.JPEG_QUALITY}",
                        style = CtTypeScale.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selection.hasImage && store != null) {
                    IconTextButton(
                        icon = Icons.Rounded.Delete,
                        text = "删除",
                        tint = rememberCtColors().error,
                        onClick = {
                            scope.launch {
                                store.deleteImage()
                                message = "已恢复默认背景"
                            }
                        },
                    )
                }
            }

            // ---- 模糊档（仅自定义图片时可用） ----
            if (selection.hasImage) {
                RowDivider()
                Text(
                    text = "背景模糊（不模糊前景文字）",
                    style = CtTypeScale.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(CtSpace.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpace.sm)) {
                    (0..CtBackgroundSelection.MAX_CUSTOM_BLUR).forEach { level ->
                        val selected = selection.customBlur == level
                        Surface(
                            onClick = { store?.setCustomBlur(level) },
                            shape = CircleShape,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.heightIn(min = 40.dp),
                        ) {
                            Box(modifier = Modifier.heightIn(min = 40.dp).padding(horizontal = 16.dp)) {
                                Text(
                                    text = CtBackgroundImage.blurLabel(level),
                                    style = CtTypeScale.label,
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                        }
                    }
                }
            }

            message?.let { text ->
                Spacer(Modifier.size(CtSpace.sm))
                Text(
                    text = text,
                    style = CtTypeScale.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 预设色块（点按区 ≥48dp；选中 = 2dp 主色描边 + ✓）。 */
@Composable
private fun BackgroundSwatch(
    preset: CtBackgroundPreset,
    dark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 缩略图始终用浅色参数展示「这个预设是什么颜色」；实际应用时按当前明暗取 dark/light 参数。
    // 否则深色模式下所有浅色预设都显示为接近黑色，用户无法分辨。
    val colors = CtBackgroundPalette.of(preset, dark = false)
    val shape = RoundedCornerShape(CtRadius.tile)
    Column(
        modifier = modifier
            .heightIn(min = CtSize.minTouch)
            .clip(shape)
            .clickable(role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
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

/** 行内的图标+文字小按钮（删除背景）。 */
@Composable
private fun IconTextButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .heightIn(min = CtSize.minTouch)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpace.xs),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(CtSize.iconSmall))
        Text(text = text, style = CtTypeScale.label, color = tint)
    }
}
