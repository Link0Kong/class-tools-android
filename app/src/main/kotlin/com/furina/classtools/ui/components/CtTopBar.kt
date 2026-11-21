package com.furina.classtools.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.components.light.ctLightMaterial
import com.furina.classtools.ui.components.light.CtLightInk
import com.furina.classtools.ui.theme.CtLightLevel
import com.furina.classtools.ui.theme.CtLightScene
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtSize
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.CtText
import com.furina.classtools.ui.theme.CtTypeScale
import com.furina.classtools.ui.theme.LocalCtColors
import com.furina.classtools.ui.theme.LocalCtLight
import com.furina.classtools.ui.theme.CtTopBarLight
import com.furina.classtools.ui.theme.CT_TOP_BAR_FULL_SCROLL_PX
import com.furina.classtools.ui.theme.HarmonyTokens
import com.furina.classtools.ui.theme.rememberHarmonyColors
import com.furina.classtools.ui.theme.rememberReduceMotion

/** 工具页顶栏首屏的基础材质强度（滚动后继续加深到 1）。 */
private const val CT_TOP_BAR_MIN_MATERIAL = 0.65f

/**
 * 工具页统一顶栏（**沉浸光感 ULTRA_THIN**，2026-11-19 重构）。
 *
 * 尺寸/层级沿用鸿蒙 `PageHeader` 口径：高度 **56dp**、标题 **20sp Bold**、返回 **44dp 热区**（图标 22dp）。
 *
 * 沉浸光感行为（官方「顶部悬浮组件 = ULTRA_THIN + 渐变模糊延展顶部」）：
 * - 默认 `scrollPx = 0f`：静态透明顶栏，页面内容直接沉浸到屏幕顶边（状态栏区域由材质向上延展，内容不贴边）；
 * - [scrollPx] 由调用方提供滚动量（`LazyListState.firstVisibleItemScrollOffset` 等）：
 *   滚动 ≤ 8dp 仍透明；8 → 48dp 内浮层进度 0 → 1（材质淡入 + 底部渐变融合带渐显）；
 * - API 31+ 走 RenderEffect 模糊；API 26–30 自动降级为「半透明 + 发丝亮边 + 柔光晕」；
 * - 「关闭光感」时退化为「实色顶栏 + 发丝线」（旧版观感），滚动量被忽略。
 *
 * 旧调用方（不传 [scrollPx]）也拿到同样的浮层材质，只是不随滚动变化 —— 既有页面零改动即适配。
 *
 * @param subtitle 保留参数（不渲染；鸿蒙 PageHeader 只有标题 + 动作）
 * @param actions 右侧动作
 * @param compact 保留参数（顶栏高度固定 56dp）
 * @param scrollPx 页面滚动量（px）；负数按 0 处理
 */
@Composable
fun CtTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    compact: Boolean = true,
    scrollPx: Float = CT_TOP_BAR_FULL_SCROLL_PX,
) {
    // M3 small app bar：整宽、64dp、surface 容器（不再悬浮药丸/方形渐变）。
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    Surface(
                        onClick = onBack,
                        modifier = Modifier.size(CtSize.backHit),
                        shape = CircleShape,
                        color = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回",
                                modifier = Modifier.size(CtSize.icon),
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(CtSize.backHit))
                }
                Spacer(modifier = Modifier.width(CtSpace.xs))
                Text(
                    text = title,
                    style = CtText.heading,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CtSpace.xxs),
                ) {
                    actions()
                }
            }
        }
    }
}

/**
 * **沉浸光感版顶栏**（页面首选入口）：在原 [CtTopBar] 之上加一行副标题（滚动时随浮层一起渐显淡出）。
 *
 * - [scrollPx] 由页面提供滚动量（0 = 首屏透明；8dp 起渐显浮层；见 `CtTopBarLight`）；
 * - [subtitle] 不挤进 56dp 主行：作为浮层底部的「次级信息行」，浮层进度 > 0.5 时淡出，避免滚动时与内容打架；
 * - 自带 `statusBarsPadding()`，页面不再需要自己加安全区；
 * - 语义与大标题一致（`heading()`），TalkBack 读「返回 / 标题」。
 *
 * 旧 `CtTopBar` 保留为薄别名（见文件末尾），既有预览/测试零改动。
 */
@Composable
fun CtLightTopBar(
    title: String,
    scrollPx: Float,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        CtTopBar(
            title = title,
            onBack = onBack,
            actions = actions,
            scrollPx = scrollPx,
        )
        if (!subtitle.isNullOrBlank()) {
            val light = LocalCtLight.current
            val reduceMotion = rememberReduceMotion()
            val density = LocalDensity.current
            val target = if (light.enabled) {
                CtTopBarLight.overlayProgress(scrollPx, density.density)
            } else {
                0f
            }
            val progress by animateFloatAsState(
                targetValue = target,
                animationSpec = if (reduceMotion) snap() else tween(200),
                label = "ctTopBarLightSubtitle",
            )
            val c = LocalCtColors.current
            Text(
                text = subtitle,
                style = CtTypeScale.caption,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = CtSize.backHit + CtSpace.sm, end = CtSpace.rowPaddingH)
                    .graphicsLayer { alpha = (1f - progress * 1.6f).coerceIn(0f, 1f) },
            )
        }
    }
}

/**
 * 旧名义项：[CtTopBar] 的别名（自定义 `subtitle` 行语义）。
 *
 * 需要副标题的页面用 [CtLightTopBar]；这里仅保留给不关心滚动的零散调用。
 */
@Composable
fun ctTopBarRaw(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        CtTopBar(title = title, onBack = onBack, actions = actions)
        if (!subtitle.isNullOrBlank()) {
            val c = LocalCtColors.current
            Text(
                text = subtitle,
                style = CtTypeScale.caption,
                color = c.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = CtSize.backHit + CtSpace.sm, end = CtSpace.rowPaddingH),
            )
        }
    }
}

/** 圆形图标按钮（保留旧 API；鸿蒙风为无底色 44dp 图标按钮）。 */
@Composable
fun CtIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberHarmonyColors()
    Surface(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = colors.textSecondary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
        }
    }
}

/** 顶部品牌条（身份页/启动页用）：图标 + 站名 + 右侧动作。 */
@Composable
fun BrandBar(
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val colors = rememberHarmonyColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BrandMark(size = 28.dp)
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = "班级工具站",
                style = CtText.bodyMedium,
                color = colors.textPrimary,
            )
        }
        actions()
    }
}

/** 小胶囊标签（鸿蒙 pill：品牌雾青 12% 底 + 雾青文字）。 */
@Composable
fun CtPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = harmonyTintLocal(),
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            style = CtText.caption,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun harmonyTintLocal(): Color {
    val colors = rememberHarmonyColors()
    return if (colors.dark) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        HarmonyTokens.TintTeal
    }
}




