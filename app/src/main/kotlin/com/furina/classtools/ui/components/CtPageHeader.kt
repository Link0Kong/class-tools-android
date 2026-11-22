package com.furina.classtools.ui.components

import androidx.compose.foundation.layout.windowInsetsTopHeight

import androidx.compose.foundation.layout.WindowInsets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.components.glass.ctGlass
import com.furina.classtools.ui.components.glass.ctGlassPointerLight
import com.furina.classtools.ui.components.glass.rememberCtGlassPress
import com.furina.classtools.ui.theme.CtLightLevel
import com.furina.classtools.ui.theme.CtLightScene
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.CtTypeScale
import com.furina.classtools.ui.theme.ctLightSceneOf
import com.furina.classtools.ui.theme.rememberCtColors
import com.furina.classtools.ui.theme.rememberReduceMotion
import dev.chrisbanes.haze.HazeState

// =============================================================================
// 页面头部体系（沉浸光感的「顶部悬浮组件」）
//
// 官方口径（设计规范 §布局）：
//   · 顶部悬浮 = ULTRA_THIN + **渐变模糊**延展顶部内容空间（从完全透明渐变到模糊）；
//   · 标题栏用 STACK 形态，让内容延伸到标题栏下（内容可透出）；
//   · 悬浮组件与内容重叠时，强化 Z 轴空间感、提升屏占比。
//
// 因此每个一级页面都是「两层」结构：
//   1) [CtRootHeader]：随内容滚动的**大标题区**（40sp 主标题 + 副标题 + 右侧动作）；
//   2) [CtStickyHeaderBar]：常驻顶部的**悬浮玻璃条**，滚过阈值后由「完全透明」渐变到
//      「材质 + 模糊 + 底部融合带」，滚动归零再渐变回透明（双向）。
//
// 用法（页面里）：
// ```kotlin
// val haze = rememberHazeState()
// Box(Modifier.fillMaxSize()) {
//     Column(Modifier.fillMaxSize().hazeSource(haze).verticalScroll(scroll)) {
//         CtRootHeader(title = "工作台", subtitle = "11月20日 周五", actions = { … })
//         …
//     }
//     val progress = ctStickyHeaderProgress(scroll.value, density)
//     CtStickyHeaderBar(title = "工作台", progress = progress, hazeState = haze, actions = { … })
// }
// ```
// =============================================================================

/** 悬浮条开始显现的滚动阈值（px→dp）：滚过 12dp 后开始淡入。 */
private const val STICKY_START_DP = 88f

/** 悬浮条完全显现所需的额外滚动距离（dp）。 */
private const val STICKY_FADE_DP = 36f

/**
 * 计算悬浮顶栏进度（0 = 全透明，1 = 完全显现）。
 *
 * 纯函数（可单测）：滚动 < 12dp → 0；12 → 48dp 线性到 1；> 48dp 保持 1。
 */
fun ctStickyHeaderProgress(scrollPx: Float, density: Float): Float {
    if (density <= 0f) return if (scrollPx > 0f) 1f else 0f
    val dp = scrollPx / density
    val past = dp - STICKY_START_DP
    if (past <= 0f) return 0f
    return (past / STICKY_FADE_DP).coerceIn(0f, 1f)
}

/**
 * 大标题区（随内容滚动）。
 *
 * - 主标题 **34sp Bold**（官方标题栏 30，但首页大标题用更大的 Expressive 尺度才有层次）；
 * - 上方可选 **小标签行**（品牌名 / 页面类型），右侧常驻动作（通知 / 设置 / 头像）；
 * - 下方可选副标题（日期、说明、统计口径）。
 *
 * @param eyebrow 主标题上方的小号全大写标签（null = 不显示）
 * @param subtitle 主标题下方的说明行
 * @param actions 右侧动作（通常是若干 [CtGlassIconButton]）
 */
@Composable
fun CtRootHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    /** 折叠进度 0..1：向上滑动时大标题**轻微缩小 + 上移 + 淡出**（官方参考的顶部悬浮行为）。 */
    collapse: Float = 0f,
    actions: @Composable () -> Unit = {},
) {
    val c = rememberCtColors()
    val p = collapse.coerceIn(0f, 1f)
    val headerAlpha = (1f - p * 1.15f).coerceIn(0f, 1f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                // 稍微缩小 + 上移：0 → 0.94 倍、向上 10dp，收得克制（官方示例幅度很小）
                val s = 1f - 0.06f * p
                scaleX = s
                scaleY = s
                translationY = -10.dp.toPx() * p
                alpha = headerAlpha
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            }
            .padding(top = CtSpace.sm, bottom = CtSpace.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (eyebrow != null) {
                    Text(
                        text = eyebrow,
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textTertiary,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.height(CtSpace.xxs))
                }
                // M3 large app bar 标题 = Display Small（36sp / 400）。之前是 34sp Bold 的就地覆盖。
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(modifier = Modifier.width(CtSpace.sm))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CtSpace.xs),
            ) {
                actions()
            }
        }
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(CtSpace.xxs))
            Text(
                text = subtitle,
                style = CtTypeScale.body,
                color = c.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 常驻顶部悬浮玻璃条（渐变模糊）。
 *
 * @param progress 0..1（[ctStickyHeaderProgress] 计算；0 时完全不可见、不拦截点击）
 * @param hazeState 页面级 Haze 源（源 = 滚动内容），传入后材质做真模糊
 * @param contentIsDark 顶部内容是否为深色（智能反色：深色内容上自动用浅色前景）
 */
@Composable
fun CtStickyHeaderBar(
    title: String,
    progress: Float,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    actions: @Composable () -> Unit = {},
    contentIsDark: Boolean = false,
) {
    if (progress <= 0.001f) return
    val reduceMotion = rememberReduceMotion()
    val p by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (reduceMotion) snap() else tween(160),
        label = "ctStickyHeader",
    )
    val foreground = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            contentColor = foreground,
        ) {
            Column {
                // 覆盖状态栏区域：滚动后顶栏必须是一条完整通栏（含状态栏），不能露出下方内容。
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .graphicsLayer { alpha = p }
                        .padding(start = 16.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        // M3 small app bar 标题 = Title Large（22sp / 400）。
                        style = MaterialTheme.typography.titleLarge,
                        color = foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
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
}

/**
 * 圆形玻璃图标按钮（头部动作 / 顶栏动作 / 列表尾部动作的统一样式）。
 *
 * - 44dp 热区（≥48dp 触摸目标由外层 padding 补足）；
 * - ULTRA_THIN 材质 + 按压点光源 + 弹性形变；
 * - 可选小圆点徽标（未读数）。
 */
@Composable
fun CtGlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    badge: Boolean = false,
    size: Int = 48,
) {
    val c = rememberCtColors()
    val press = rememberCtGlassPress()
    val scale by animateFloatAsState(
        targetValue = if (press.pressed) 0.92f else 1f,
        animationSpec = CtMotion.spatialFast,
        label = "glassIconScale",
    )
    Box(
        modifier = modifier
            .size(size.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Box(
            modifier = Modifier
                .size(size.dp)
                // M3：实色 container 圆形图标按钮，不再用玻璃材质。
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onClick, role = Role.Button)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint ?: c.textPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
        if (badge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 9.dp, end = 9.dp)
                    .size(7.dp)
                    .background(c.error, CircleShape),
            )
        }
    }
}

/** 屏内小标题（区块标题的玻璃版：字重更重、无下划线）。 */
@Composable
fun CtGlassSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = rememberCtColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CtSpace.xxs, vertical = CtSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = CtTypeScale.title16,
            color = c.textPrimary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** 顶部渐变融合带（悬浮条下方：材质向页面淡出，官方「渐变模糊延展顶部」）。 */
@Composable
fun CtTopFadeScrim(height: Int = 56, modifier: Modifier = Modifier) {
    val c = rememberCtColors()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        c.background.copy(alpha = 0.42f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}





