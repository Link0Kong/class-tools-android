package com.furina.classtools.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.components.CtButton
import com.furina.classtools.ui.components.CtCard
import com.furina.classtools.ui.components.CtEmptyState
import com.furina.classtools.ui.components.CtIconBadge
import com.furina.classtools.ui.components.CtListRow
import com.furina.classtools.ui.components.CtPageTitle
import com.furina.classtools.ui.components.CtPressLayer
import com.furina.classtools.ui.components.CtRowDivider
import com.furina.classtools.ui.components.CtSection
import com.furina.classtools.ui.components.CtSectionTitle
import com.furina.classtools.ui.components.CtSheetContainer
import com.furina.classtools.ui.components.CtSkeletonPage
import com.furina.classtools.ui.components.CtTag
import com.furina.classtools.ui.components.CtTextField
import com.furina.classtools.ui.components.background.CtGlassSurface
import com.furina.classtools.ui.theme.CtAnimatedEntrance
import com.furina.classtools.ui.theme.CtColor
import com.furina.classtools.ui.theme.CtElevation
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtMotionTokens
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.CtSize
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.CtTypeScale
import com.furina.classtools.ui.theme.ctPressFeedback
import com.furina.classtools.ui.theme.harmonyTint
import com.furina.classtools.ui.theme.rememberCtColors
import com.furina.classtools.ui.theme.rememberReduceMotion

// =============================================================================
// 统一组件库（全局唯一视觉定义）
//
// 所有页面只允许用这里的组件来表达「卡片 / 区块标题 / 列表行 / 按钮 / 输入 / 空态 /
// 加载骨架 / 弹层 / 图标块 / 胶囊」，这样工作台、全部工具、我的、身份页、各工具页在
// 视觉上天然一致（同一圆角、描边、投影、字号、按压动效）。
//
// 设计参考：M3 Expressive（形体 + 层级 + 线性图标）+ HarmonyOS One Harmonious Universe
// （卡片式、轻拟物光影、以人为本）。
//
// 组件清单：
//   CtCard            卡片（20 圆角 + 1dp 描边 + 极浅投影；可点按 spring 0.97）
//   CtSection         区块（13 Medium 次级色标题 + 可选右侧动作 + 内容）
//   CtSectionTitle    区块标题
//   CtListRow         列表行（≥56dp；左 40dp 圆角图标块；标题 16 + 副标题 12 + 尾箭头）
//   CtButton          主按钮（Filled 56dp 胶囊）/ 三档变体（Filled/Tonal/Text）
//   CtTextField       输入（14 圆角 tonal 底、无边框；focus 描边动画）
//   CtEmptyState      空态（48dp 线性图标 + 标题 + 说明 + 主操作）
//   CtSkeleton*       加载骨架（三档灰 + 900ms 呼吸）
//   CtSheetContainer  弹层内容容器（大标题 + 说明 + 内容 + 底部动作）
//   CtPill / CtIconBadge / CtPressableOverlay 等小件
// =============================================================================

/**
 * 卡片容器（**磨砂玻璃材质**，2026-11-19）。
 *
 * 材质统一来自 `ui/components/background/CtGlassSurface.kt`：
 * 半透明底（浅 0.55–0.70 / 深 0.45–0.60）+ 顶部线性高光 + 1px 高光边 + 柔和外阴影；
 * 背景越花（渐变/自定义照片）底越实，正文对比度始终 ≥ AA。
 *
 * @param onClick 非 null 时可点按（按压 spring 0.97 + 8% tonal 按压层）
 * @param contentPadding 内边距（默认 16dp）
 * @param entranceIndex 非 null 时参与列表错峰入场
 * @param frosted false = 实色卡片（列表/表单等需要最稳可读性的场合）
 */
@Composable
fun CtCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = CtShape.card,
    contentPadding: Dp = CtSpace.cardPadding,
    entranceIndex: Int? = null,
    frosted: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val body: @Composable () -> Unit = {
        if (onClick != null) {
            val reduceMotion = rememberReduceMotion()
            val interaction = remember { MutableInteractionSource() }
            val press = ctPressFeedback(interaction, reduceMotion)
            CtGlassSurface(
                modifier = modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = press.scale
                        scaleY = press.scale
                    },
                shape = shape,
                onClick = onClick,
                frosted = frosted,
                body = true,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(contentPadding),
                    content = content,
                )
                CtPressOverlay(alpha = press.overlayAlpha, color = rememberCtColors())
            }
        } else {
            CtGlassSurface(
                modifier = modifier.fillMaxWidth(),
                shape = shape,
                frosted = frosted,
                body = true,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(contentPadding),
                    content = content,
                )
            }
        }
    }
    if (entranceIndex != null) {
        CtAnimatedEntrance(enabled = true, index = entranceIndex) { body() }
    } else {
        body()
    }
}

/** 按压层（M3 state layer；不拦截点击）。 */
@Composable
private fun CtPressOverlay(alpha: Float, color: CtColor) {
    if (alpha <= 0.001f) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(color.stateLayer),
    )
}

/**
 * 区块：区块标题（13 Medium 次级色）+ 可选右侧文字动作 + 内容。
 *
 * @param spacing 标题与内容的间距（默认 8dp）
 */
@Composable
fun CtSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    spacing: Dp = CtSpace.sm,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            CtSectionTitle(title = title, action = action)
        }
        Spacer(modifier = Modifier.height(spacing))
        content()
    }
}

/**
 * 区块标题：**13sp Medium 次级色**（统一口径；所有页面一致）。
 *
 * @param action 右侧文字动作（"查看更多 ›" / "编辑"），调用方自己传 TextButton/Text
 */
@Composable
fun CtSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val c = rememberCtColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp)
            .padding(horizontal = CtSpace.xs, vertical = CtSpace.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = CtTypeScale.sectionTitle,
            color = c.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        action?.invoke()
    }
}

/**
 * 列表行：左 40dp 圆角图标块（tint 底）+ 标题 16 / 副标题 12 + 可选尾部（箭头/开关/值）。
 *
 * - 行高 ≥56dp、整行可点（[onClick] 非 null 时），点按区满足 48dp 无障碍硬线；
 * - [icon] / [accent] 为 null 时不显示图标块（纯文字行）；
 * - [trailing] 优先于默认箭头。
 */
@Composable
fun CtListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: androidx.compose.ui.graphics.Color? = null,
    icon: ImageVector? = null,
    accent: androidx.compose.ui.graphics.Color? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = rememberCtColors()
    val rowModifier = modifier
        .fillMaxWidth()
        .heightIn(min = CtSize.rowMinHeight)
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick, role = Role.Button)
            } else {
                Modifier
            },
        )
        .padding(horizontal = CtSpace.rowPaddingH, vertical = CtSpace.rowPaddingV)
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpace.md),
    ) {
        if (icon != null) {
            val iconColor = accent ?: c.textSecondary
            Surface(
                shape = CtShape.tile,
                color = harmonyTint(iconColor),
                contentColor = iconColor,
                modifier = Modifier.size(CtSize.glyphBox),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(CtSize.icon),
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CtSpace.xxs)) {
            Text(
                text = title,
                style = CtTypeScale.title16,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = CtTypeScale.caption,
                    color = subtitleColor ?: c.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = c.textTertiary,
                modifier = Modifier.size(CtSize.icon),
            )
        }
    }
}

/** 行间分割线（与卡片正文对齐：左侧内缩 16dp）。 */
@Composable
fun CtRowDivider(modifier: Modifier = Modifier, startInset: Dp = CtSpace.rowPaddingH) {
    val c = rememberCtColors()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startInset)
            .height(CtSize.dividerThickness)
            .background(c.divider),
    )
}

/** 按钮三档。 */
enum class CtButtonStyle {
    /** 主操作：filled 主色胶囊。 */
    Filled,

    /** 次操作：tonal 主色淡底胶囊。 */
    Tonal,

    /** 文字操作：只有文字，最小 48dp 触控。 */
    Text,
}

/**
 * 统一按钮：**Filled 56dp 胶囊**（主操作）/ Tonal / Text 三档；宽度默认撑满（传 modifier 可改）。
 *
 * @param enabled false 时禁用（主操作常见：未勾选协议/表单未填完）
 */
@Composable
fun CtButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CtButtonStyle = CtButtonStyle.Filled,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val c = rememberCtColors()
    val shape = CtShape.pill
    val content: @Composable RowScope.() -> Unit = {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(CtSize.iconSmall),
            )
            Spacer(modifier = Modifier.width(CtSpace.sm))
        }
        Text(text = text, style = CtTypeScale.label)
    }
    when (style) {
        CtButtonStyle.Filled -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = CtSize.buttonHeight),
            shape = shape,
            content = content,
        )

        CtButtonStyle.Tonal -> FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = CtSize.buttonHeight),
            shape = shape,
            content = content,
        )

        CtButtonStyle.Text -> TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = CtSize.minTouch),
            shape = shape,
            colors = ButtonDefaults.textButtonColors(contentColor = c.textPrimary),
            content = content,
        )
    }
}

/** 文字动作（区块标题右侧 / 卡片内链接）：13 Medium 主色，最小 48dp 触控区。 */
@Composable
fun CtTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: androidx.compose.ui.graphics.Color? = null,
) {
    val c = rememberCtColors()
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = CtSize.minTouch),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = CtSpace.sm),
    ) {
        Text(
            text = text,
            style = CtTypeScale.label,
            color = color ?: if (enabled) c.primary else c.textTertiary,
        )
    }
}

/**
 * 输入框：**14dp 圆角 tonal 底、无边框**（M3 Expressive filled text field 的轻量版）。
 *
 * 聚焦时底色调深一档（tonal → containerHigh）+ 1dp 主色描边淡入（200ms），
 * 不再用 OutlinedTextField 的双线边框，与卡片语言统一。
 *
 * @param label 占位/标签（非空且输入为空时显示）
 * @param trailing 尾部内容（清空按钮、单位、动作）
 * @param singleLine true 时单行
 * @param visualTransformation 密码遮罩等
 */
@Composable
fun CtTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    textStyle: TextStyle = CtTypeScale.body,
    trailing: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val c = rememberCtColors()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val container by animateColorAsState(
        targetValue = if (focused) c.containerHigh else c.container,
        animationSpec = CtMotion.effectsSpec(),
        label = "ctFieldContainer",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (focused || isError) 1.dp else 0.dp,
        animationSpec = CtMotion.spatialFastDp,
        label = "ctFieldBorder",
    )
    val borderColor = when {
        isError -> c.error
        focused -> c.primary
        else -> c.divider
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CtSize.buttonHeight)
            .clip(CtShape.input)
            .background(container)
            .border(borderWidth, borderColor, CtShape.input)
            .padding(horizontal = CtSpace.md, vertical = CtSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpace.sm),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = c.textTertiary,
                modifier = Modifier.size(CtSize.icon),
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = singleLine,
                maxLines = maxLines,
                textStyle = textStyle.copy(color = if (enabled) c.textPrimary else c.textTertiary),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                interactionSource = interaction,
            )
            if (value.isEmpty() && label.isNotEmpty()) {
                Text(
                    text = label,
                    style = textStyle,
                    color = c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * 空态：**48dp 线性图标 + 标题 + 说明 + 可选主操作**（M3 Expressive empty state 口径）。
 *
 * 图标放在 tonal 圆角块里（不画大插画），与卡片语言一致。
 */
@Composable
fun CtEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val c = rememberCtColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CtSpace.xxl, vertical = if (compact) CtSpace.lg else CtSpace.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CtSpace.sm),
    ) {
        Surface(
            shape = RoundedCornerShape(CtRadius.card),
            color = c.container,
            contentColor = c.textSecondary,
        ) {
            Box(modifier = Modifier.size(CtSpace.huge + CtSpace.sm), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = c.textSecondary,
                    modifier = Modifier.size(CtSize.emptyIcon),
                )
            }
        }
        Spacer(modifier = Modifier.height(CtSpace.xs))
        Text(
            text = title,
            style = CtTypeScale.title16,
            color = c.textPrimary,
            textAlign = TextAlign.Center,
        )
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                style = CtTypeScale.caption,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(CtSpace.xs))
            CtButton(text = actionText, onClick = onAction, style = CtButtonStyle.Tonal)
        }
    }
}

/**
 * 页面级加载骨架（整页/整块）：标题块 + 若干卡片块，统一 900ms 呼吸。
 *
 * @param rows 卡片块数量
 */
@Composable
fun CtSkeletonPage(
    modifier: Modifier = Modifier,
    rows: Int = 4,
    description: String? = null,
) {
    val alpha = com.furina.classtools.ui.components.rememberSkeletonAlpha()()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .padding(vertical = CtSpace.lg),
        verticalArrangement = Arrangement.spacedBy(CtSpace.md),
    ) {
        HarmonySkeletonBlock(widthFraction = 0.36f, height = CtSpace.xxl, radius = CtRadius.chip, strong = true)
        repeat(rows) {
            HarmonySkeletonBlock(
                widthFraction = 1f,
                height = CtSize.tileMinHeight,
                radius = CtRadius.card,
            )
        }
        if (!description.isNullOrBlank()) {
            HarmonySkeletonTextBlock(description = description)
        }
    }
}

/** 骨架「显示/内容」交叉淡入（加载完成时内容淡入，避免跳变）。 */
@Composable
fun CtLoadingSwitch(
    loading: Boolean,
    modifier: Modifier = Modifier,
    loadingContent: @Composable () -> Unit = { CtSkeletonPage(modifier = Modifier) },
    content: @Composable () -> Unit,
) {
    Crossfade(
        targetState = loading,
        animationSpec = CtMotion.standard,
        label = "ctLoadingSwitch",
        modifier = modifier,
    ) { isLoading ->
        if (isLoading) loadingContent() else content()
    }
}

/**
 * 弹层内容容器：大标题（20 Bold）+ 说明（12 次级）+ 内容 + 底部动作条。
 *
 * 弹层本身（ModalBottomSheet）由 [com.furina.classtools.ui.components.expressive.CtBottomSheet] 提供；
 * 这里统一「弹层里长什么样」。
 */
@Composable
fun CtSheetContainer(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = rememberCtColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CtSpace.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = CtTypeScale.headline, color = c.textPrimary)
                if (!subtitle.isNullOrBlank()) {
                    Text(text = subtitle, style = CtTypeScale.caption, color = c.textSecondary)
                }
            }
        }
        Spacer(modifier = Modifier.height(CtSpace.lg))
        Column(modifier = Modifier.weight(1f, fill = false), content = content)
        if (footer != null) {
            Spacer(modifier = Modifier.height(CtSpace.md))
            footer()
        }
    }
}

/** 小胶囊标签（tonal 底 + 13 或 12 字）。 */
@Composable
fun CtTag(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color? = null,
) {
    val c = rememberCtColors()
    val tint = color ?: c.primary
    Surface(modifier = modifier, shape = CtShape.pill, color = harmonyTint(tint), contentColor = tint) {
        Text(
            text = text,
            style = CtTypeScale.caption,
            modifier = Modifier.padding(horizontal = CtSpace.sm, vertical = CtSpace.xxs),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 图标块（40dp 默认；30dp 小号）：淡色 tint 底 + 圆角图标。 */
@Composable
fun CtIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color? = null,
    box: Dp = CtSize.glyphBox,
    glyph: Dp = CtSize.iconLarge,
) {
    val c = rememberCtColors()
    val tint = color ?: c.textSecondary
    Surface(
        modifier = modifier.size(box),
        shape = RoundedCornerShape(box * 0.3f),
        color = harmonyTint(tint),
        contentColor = tint,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(glyph),
            )
        }
    }
}

/** 统一文字（默认 15/22 正文；提供语义字号快捷入口，避免页面写 fontSize）。 */
@Composable
fun CtBodyText(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color? = null,
    style: TextStyle? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val c = rememberCtColors()
    Text(
        text = text,
        modifier = modifier,
        style = style ?: CtTypeScale.body,
        color = color ?: c.textPrimary,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 便捷：正文中的次级文字（12 说明）。 */
@Composable
fun CtCaptionText(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val c = rememberCtColors()
    Text(
        text = text,
        modifier = modifier,
        style = CtTypeScale.caption,
        color = color ?: c.textSecondary,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 页码/统计数字（24 Bold 等宽）。 */
@Composable
fun CtMetricText(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color? = null,
) {
    val c = rememberCtColors()
    Text(
        text = text,
        modifier = modifier,
        style = CtTypeScale.metric,
        color = color ?: c.textPrimary,
        maxLines = 1,
    )
}

/** 页面标题（28 Bold；工作台/全部/我的 页首统一）。 */
@Composable
fun CtPageTitle(
    text: String,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = rememberCtColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = CtTypeScale.display,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        actions?.invoke(this)
    }
}

/** 局部约束：供页面统一使用「默认文本样式」（避免 BasicTextField 丢字号）。 */
@Composable
fun CtTextStyleProvider(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalTextStyle provides CtTypeScale.body,
        content = content,
    )
}

/**
 * 按压态层（M3 state layer）：在卡片内容之上叠一层 8% 的文字色，
 * 让「按下」既有 scale 也有 tonal 变化（Expressive 的「实体感」）。
 */
@Composable
fun CtPressLayer(alpha: Float, modifier: Modifier = Modifier) {
    if (alpha <= 0.001f) return
    val c = rememberCtColors()
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(c.stateLayer),
    )
}