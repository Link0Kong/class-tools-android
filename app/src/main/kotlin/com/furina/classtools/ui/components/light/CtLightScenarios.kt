package com.furina.classtools.ui.components.light

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.components.light.CtLightInk
import com.furina.classtools.ui.theme.CtLightIntensity
import com.furina.classtools.ui.theme.CtLightScene
import com.furina.classtools.ui.theme.CtPalette
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.CtSize
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.CtText
import com.furina.classtools.ui.theme.LocalCtLight
import com.furina.classtools.ui.theme.CtTopBarLight
import com.furina.classtools.ui.theme.rememberCtColors
import com.furina.classtools.ui.theme.rememberReduceMotion

// =============================================================================
// 沉浸光感 · 场景容器（页面只使用这些容器，不手写材质参数）
//
//   CtLightPage          页面骨架：光感背板 + 模糊层记账（工具/详情页统一用）
//   CtLightTopBar        顶部悬浮顶栏（ULTRA_THIN + 渐变模糊延展顶部 + 状态栏区域延展 + 滚动渐显）
//   CtLightBottomBar     底部悬浮外壳（THIN，浮岛 dock / 操作条）
//   CtLightBottomScrim   底部渐变颜色蒙层（官方「底部悬浮 = THIN + 渐变颜色蒙层延展底部」）
//   CtLightPanel         页面内浮层（菜单 THICK / 弹窗 ULTRA_THICK）
//   CtLightBackdrop      页面根光晕背板（品牌色柔光斑，强度随设置）
//
// 滚动 → 顶栏浮层的过渡（官方「顶部悬浮组件 + 渐变模糊延展顶部」）：
//   滚动 < 8dp       顶栏完全透明（内容与状态栏融为一体，沉浸）；
//   8dp → 48dp      浮层进度 0 → 1（材质淡入 + 底部渐变融合带渐显）。
// 进度公式是纯函数 [CtTopBarLight.overlayProgress]（单测锚点）。
// =============================================================================

/** 页面骨架：光感背板 + 模糊层记账宿主 + 全屏容器。 */
@Composable
fun CtLightPage(
    modifier: Modifier = Modifier,
    backdrop: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    CtLightMaterialHost {
        Box(modifier = modifier.fillMaxSize()) {
            if (backdrop) CtLightBackdrop(modifier = Modifier.fillMaxSize())
            content()
        }
    }
}

/**
 * 顶部悬浮顶栏（沉浸光感 ULTRA_THIN）。
 *
 * - 56dp 高内容 + **状态栏/刘海安全区向上延展**（材质延伸到屏幕顶边，内容不贴边）；
 * - [scrollPx]：页面滚动量（px）。0 = 完全透明（首屏沉浸），越过 8dp 后 40dp 内渐显浮层；
 * - 浮层显现时材质自带「向下渐变融合带」（官方：渐变模糊延展）；
 * - 返回 44dp 热区 / 标题 20 Bold / 右侧动作与既有 `CtTopBar` 一致（调用方零改动迁移）；
 * - 光感关闭或旧机降级时自动退化为「实色/半透明 + 发丝线」。
 */
@Composable
fun CtLightTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    scrollPx: Float = 0f,
) {
    val density = LocalDensity.current
    val target = CtTopBarLight.overlayProgress(scrollPx, density.density)
    val reduceMotion = rememberReduceMotion()
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduceMotion) snap() else tween(200),
        label = "ctTopBarOverlay",
    )
    CtLightTopBarSurface(overlayProgress = progress) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CtSize.topBarHeight)
                .padding(start = CtSpace.xs, end = CtSpace.sm),
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
                style = CtText.heading.copy(fontWeight = FontWeight.Bold),
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

/**
 * 顶栏材质外壳（不含标题内容）：状态栏延展 + 材质 + 底部渐变融合带。
 *
 * @param overlayProgress 浮层进度 0..1（0 = 完全透明，1 = 浮层完全显现）
 */
@Composable
fun CtLightTopBarSurface(
    overlayProgress: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val light = LocalCtLight.current
    val c = rememberCtColors()
    val p = overlayProgress.coerceIn(0f, 1f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (p <= 0.001f) Modifier else Modifier.ctLightMaterial(CtLightScene.TOP_BAR, bodySafe = false))
            .graphicsLayer { if (p <= 0.001f) alpha = 1f },
    ) {
        // 底部渐变融合带：材质向下淡出到页面（官方「渐变模糊延展顶部」），随进度渐显。
        if (p > 0.001f && light.enabled) {
            val spec = light.spec(CtLightScene.TOP_BAR)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = p }
                    .height(spec.gradientFade)
                    .background(
                        Brush.verticalGradient(
                            listOf(c.background.copy(alpha = 0.94f * spec.surfaceAlpha), Color.Transparent),
                        ),
                    ),
            )
        }
        Column(modifier = Modifier.statusBarsPadding()) {
            Box(modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

/** 底部悬浮外壳（沉浸光感 THIN）：浮岛 dock / 底部操作条的材质容器。 */
@Composable
fun CtLightBottomBar(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(CtRadius.hero),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.ctLightMaterial(CtLightScene.BOTTOM_DOCK, shape = shape, bodySafe = false),
    ) {
        content()
    }
}

/**
 * 底部渐变颜色蒙层（官方「底部悬浮 = THIN + 渐变颜色蒙层延展底部」）。
 *
 * 2026-11-20 沉浸光感 v2：浮岛已改为**真模糊玻璃**（Haze 采样页面内容），蒙层若仍用
 * 0.62/0.92/1.0 的底色，会把浮岛背后的画面糊成一片白 —— 玻璃就失去透光对象（实测确认）。
 * 因此蒙层只保留极轻的「下沿收口」（0 → 0.20 → 0.34），把内容柔和收进浮岛，而不是盖住光场。
 *
 * @param height 蒙层高度（默认 108dp：dock 60 + 导航栏 + 上沿柔化）
 */
@Composable
fun CtLightBottomScrim(
    modifier: Modifier = Modifier,
    height: Dp = 108.dp,
    baseColor: Color? = null,
) {
    val c = rememberCtColors()
    val base = baseColor ?: c.background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to base.copy(alpha = 0.08f),
                    0.78f to base.copy(alpha = 0.16f),
                    1f to base.copy(alpha = 0.34f),
                ),
            ),
    )
}

/** 页面内浮层（菜单 / 弹窗 / 提示）：按场景自动取档（THICK / ULTRA_THICK），承载正文 → 安全不透明度。 */
@Composable
fun CtLightPanel(
    scene: CtLightScene,
    modifier: Modifier = Modifier,
    shape: Shape = CtShape.card,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.ctLightMaterial(scene, shape = shape, bodySafe = true)) {
        content()
    }
}

/**
 * 页面根光晕背板（可选「光感页面背景」）。
 *
 * - 品牌三色（雾青 / 灰蓝 / 暖沙）的低饱和柔光斑：右上 + 左下各一团，营造「光在空间里扩散」的介质感；
 * - 强度随设置：强 0.20 / 均衡 0.14 / 弱 0.08 / 关闭 0（纯色底，等于旧版观感）；
 * - 静态绘制（不逐帧动画），滚动时不参与重绘，避免给 GPU 添负担。
 */
@Composable
fun CtLightBackdrop(modifier: Modifier = Modifier, baseColor: Color? = null) {
    val light = LocalCtLight.current
    val c = rememberCtColors()
    // 根级光场（App 根 drawBehind 绘制）已铺满全屏时，这里不能再画不透明底色 ——
    // 否则会把光场整片盖住（2026-11-20 实测：只剩顶部 63dp 一条可见）。
    val rootAurora = com.furina.classtools.ui.components.background.LocalCtRootAuroraActive.current
    val base = if (rootAurora) Color.Transparent else (baseColor ?: c.background)
    val strength = when {
        !light.enabled -> 0f
        else -> when (light.setting.intensity) {
            CtLightIntensity.STRONG -> 0.20f
            CtLightIntensity.BALANCED -> 0.14f
            CtLightIntensity.WEAK -> 0.08f
        }
    }
    Box(modifier = modifier.background(base)) {
        if (strength > 0f) {
            GlowSpot(
                color = CtPalette.MistTeal,
                alpha = strength,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(360.dp)
                    .graphicsLayer {
                        translationX = 130f
                        translationY = -150f
                    },
            )
            GlowSpot(
                color = CtPalette.SlateBlue,
                alpha = strength * 0.7f,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(320.dp)
                    .graphicsLayer {
                        translationX = -110f
                        translationY = 90f
                    },
            )
        }
    }
}

/** 单团柔光斑（径向渐变圆，边缘完全透明）。 */
@Composable
private fun GlowSpot(color: Color, alpha: Float, modifier: Modifier = Modifier) {
    val ring = lerp(color, CtLightInk.highlight, 0.35f)
    Box(
        modifier = modifier.background(
            Brush.radialGradient(
                colors = listOf(
                    ring.copy(alpha = alpha),
                    color.copy(alpha = alpha * 0.55f),
                    Color.Transparent,
                ),
            ),
        ),
    )
}
