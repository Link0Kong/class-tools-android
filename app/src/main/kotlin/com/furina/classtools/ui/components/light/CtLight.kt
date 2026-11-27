package com.furina.classtools.ui.components.light

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.CtLightCapability
import com.furina.classtools.ui.theme.CtLightLevel
import com.furina.classtools.ui.theme.CtLightMaterialTable
import com.furina.classtools.ui.theme.CtLightScene
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.CtLightSpec
import com.furina.classtools.ui.theme.CtLightContext
import com.furina.classtools.ui.theme.LocalCtLight
import com.furina.classtools.ui.theme.CtPalette
import com.furina.classtools.ui.theme.LocalCtColors
import com.furina.classtools.ui.theme.CtLightBudget
import com.furina.classtools.ui.theme.ctLightEffectiveAlpha

// =============================================================================
// 沉浸光感（Immersive Light）· Compose 材质层
//
// 令牌/映射表在 `ui/theme/ImmersiveLight.kt`（纯 Kotlin，可 JVM 单测）；本文件负责「怎么画、画在哪」：
//   1) LocalCtLight：运行期上下文（设置 + 设备能力档 + 状态栏/导航栏安全区）；
//   2) Modifier.ctLightMaterial(scene, shape, …)：自建模糊材质 =
//        · API 31+：graphicsLayer.renderEffect = BlurEffect（GPU 真模糊，官方「材质滤镜」）
//        · API 26–30：降级为「半透明底 + 发丝描边 + 顶部柔光晕」（无 GPU 模糊，保证可用）
//        · 关闭：不透明实色 + 发丝描边（既有规范）
//      + 顶部线性高光（highlightAlpha 0.55 → 0）+ 边缘 0.5dp 折射亮边（refractionAlpha）
//      + 顶部/底部渐变融合带（gradientFade，官方「渐变模糊延展」的等效）。
//   3) 模糊层记账 [CtLightLayerCounter]：单页模糊层数上限（性能红线）。
//
// 关于「背景采样」：Compose 没有公开的 backdrop-filter。这里用两种业界通行的等效：
//   · 结构性模糊：`graphicsLayer{renderEffect=BlurEffect}` 把「材质自身图层」柔化 —— 视觉上材质像一块
//     被光柔化的薄雾，背后的内容通过半透明底色隐约透出（官方文案即「内容透过浮层隐约可见」）；
//   · 滚动渐显：CtLightTopBar 首屏透明（内容直接沉浸）→ 滚动后材质+融合带渐显（见 CtLightScenarios.kt）。
// 不采用「把背景 View 树 draw 成 bitmap 再模糊」的做法：那样每帧重采样整页，在 WebView/SurfaceView
// 页面会空白（工程 2026-11-19 已因这个原因删除过一版快照方案），也不满足性能红线。
// =============================================================================

// 运行期上下文（CtLightContext / LocalCtLight）与其派生函数在令牌层 `ui/theme/ImmersiveLight.kt`，
// 这里只做 Compose 便捷入口（避免 Theme.kt 与组件层互相依赖）。

/** 当前光感是否支持真模糊（API 31+ 且开启）：页面可据此跳过模糊相关动效。 */
@Composable
fun rememberCtLightSupported(): Boolean = LocalCtLight.current.blurAvailable

/** 当前光感能力档（页面降级、诊断页展示用）。 */
@Composable
fun rememberCtLightCapability(): CtLightCapability = LocalCtLight.current.capability

/** 当前场景材质规格（场景语义版）。 */
@Composable
fun rememberCtLightSpec(scene: CtLightScene): CtLightSpec = LocalCtLight.current.spec(scene)

/** 当前材质规格（显式档位版）。 */
@Composable
fun rememberCtLightSpec(level: CtLightLevel): CtLightSpec = LocalCtLight.current.spec(level)

// -----------------------------------------------------------------------------
// 模糊层记账（单页模糊层数上限；Debug 断言，单测覆盖纯逻辑）
// -----------------------------------------------------------------------------

/**
 * 单页模糊层计数。
 *
 * 组件在「真正请求 GPU 模糊」时 [acquire]，离开组合时 [release]；[peak] 记录本页峰值，
 * 供 Debug 日志与 gfxinfo 对比排查「一个页面叠了太多模糊层」。
 */
class CtLightLayerCounter {
    private var active = 0
    private var peakValue = 0

    /** 当前活跃模糊层数。 */
    val current: Int get() = active

    /** 本页峰值。 */
    val peak: Int get() = peakValue

    fun acquire(): Int {
        active += 1
        if (active > peakValue) peakValue = active
        return active
    }

    fun release() {
        if (active > 0) active -= 1
    }

    /** 是否超出预算（Debug 日志 / 单测断言）。 */
    fun overBudget(allowed: kotlin.Int = CtLightBudget.persistentMax): Boolean = active > allowed

    /** 重置（页面切换/预览复用）。 */
    fun reset() {
        active = 0
        peakValue = 0
    }
}

/** 页面级模糊层记账器（`CtLightMaterialHost` 提供；未提供时材质组件自建独立计数）。 */
val LocalCtLightLayerCounter = staticCompositionLocalOf<CtLightLayerCounter?> { null }

/**
 * 页面级光感宿主：向子树提供**共享**的模糊层记账器（本身不产生布局/绘制节点）。
 *
 * 工具页/详情页骨架最外层包一层即可（`CtLightPage` 已内置）：
 * ```kotlin
 * CtLightMaterialHost { …页面… }
 * ```
 */
@Composable
fun CtLightMaterialHost(content: @Composable () -> Unit) {
    val counter = remember { CtLightLayerCounter() }
    CompositionLocalProvider(LocalCtLightLayerCounter provides counter) { content() }
}

// -----------------------------------------------------------------------------
// 材质绘制
// -----------------------------------------------------------------------------

/**
 * 材质「光」的基础色（**玻璃高光 / 折射亮边的唯一来源**）。
 *
 * 浅色模式下高光 = 白（提亮）；深色模式下高光仍用白（模拟环境光），暗边用黑（塑造厚度）。
 * 抽成令牌是为了让 `docs/design/grep_hardcode.ps1`（禁止 `CtLightInk.highlight/Black` 字面量）通过，
 * 同时保证「所有材质的亮度只有一个来源」——调一处即可全局统一。
 */
object CtLightInk {
    /** 高光（顶部线性高光 / 1px 亮边 / 折射亮边）。 */
    val highlight: Color = Color.White // 设计令牌-允许（玻璃高光唯一来源，见 docs/DESIGN_SYSTEM.md 玻璃章节）

    /** 暗边（底部 1px，塑造玻璃厚度；深色下更明显）。 */
    val shade: Color = Color.Black // 设计令牌-允许（玻璃暗边唯一来源，同上）
}

/** 材质默认形状（卡片 20dp；弹层传 `CtShape.sheet`，浮岛传 `RoundedCornerShape(CtRadius.hero)`）。 */
val CtLightMaterialShape: Shape = CtShape.card

/**
 * **沉浸光感材质**（唯一对外绘制入口）。
 *
 * @param scene 场景（决定档位：顶栏 ULTRA_THIN / 底栏 THIN / 菜单 THICK / 弹窗 ULTRA_THICK …）
 * @param shape 形状
 * @param fadeAtTop null = 按场景自动（顶栏/底栏用底部融合带，其余顶部融合带）；
 *                  true = 融合带在顶部；false = 融合带在底部
 * @param bodySafe true = 该材质上承载正文 → 底色自动抬到正文安全不透明度（AA 红线）
 * @param levelOverride 非 null 时用该档位替代场景档位（局部例外，文档需注明原因）
 */
@Composable
fun Modifier.ctLightMaterial(
    scene: CtLightScene,
    shape: Shape = CtLightMaterialShape,
    fadeAtTop: Boolean? = null,
    bodySafe: Boolean = true,
    levelOverride: CtLightLevel? = null,
): Modifier {
    val light = LocalCtLight.current
    val c = LocalCtColors.current
    if (c.dark) {
        val base = when (scene) {
            CtLightScene.TOP_BAR, CtLightScene.BOTTOM_DOCK, CtLightScene.TOOL_STRIP -> c.bar
            CtLightScene.DIALOG, CtLightScene.MENU, CtLightScene.SNACKBAR, CtLightScene.TOAST -> c.containerHigh
            else -> c.containerLow
        }
        return clip(shape).background(base)
    }
    if (!light.enabled) {
        // 关闭 / 不支持：不透明实色（既有规范），不参与模糊预算。
        return this.clip(shape).background(c.containerLow)
    }
    val baseSpec = if (levelOverride == null) light.spec(scene) else light.spec(levelOverride)
    // 承载正文 → 抬到安全不透明度；导航外壳（顶栏/底栏）用原值（大字 3:1）。
    val alpha = if (bodySafe) ctLightEffectiveAlpha(scene, baseSpec) else baseSpec.surfaceAlpha
    val spec = baseSpec.copy(surfaceAlpha = alpha)
    val fadeTop = fadeAtTop ?: ctLightSurfaceFadeAtTop(scene)
    // 重要：Compose 的 RenderEffect 会模糊「本图层及其子节点」——若挂在整个容器上，
    // 顶栏标题/图标、底部 dock 的图标文字都会一起被糊掉（2026-11-19 实测截图发现）。
    // Compose 没有公开的 backdrop-filter，因此这里不做「对背后内容采样模糊」，
    // 改用「半透明底 + 顶部高光 + 边缘折射 + 渐变融合带」模拟被光柔化的介质（真机不糊字）。
    val blurring = false
    return this
        .clip(shape)
        .drawWithCache {
            // 缓存画笔与渐变（只在尺寸/主题/规格变化时重建；滚动时不重建）。
            val highlight = Brush.verticalGradient(
                0f to CtLightInk.highlight.copy(alpha = spec.highlightAlpha * 0.55f),
                0.55f to Color.Transparent,
            )
            val fade = spec.gradientFade.toPx()
            val base = if (c.dark) CtPalette.DarkContainer else CtPalette.Container
            val fadeBrush = when {
                fade <= 0f -> null
                fadeTop -> Brush.verticalGradient(
                    colors = listOf(base.copy(alpha = 0f), base.copy(alpha = spec.surfaceAlpha * 0.9f)),
                    startY = 0f,
                    endY = fade,
                )
                else -> Brush.verticalGradient(
                    colors = listOf(base.copy(alpha = spec.surfaceAlpha * 0.9f), base.copy(alpha = 0f)),
                    startY = size.height - fade,
                    endY = size.height,
                )
            }
            onDrawBehind {
                specPaint(spec, bloom = !blurring, dark = c.dark, c = c)
                fadeBrush?.let { drawRect(brush = it) }
                if (spec.highlightAlpha > 0f) drawRect(brush = highlight)
                drawEdgeRefraction(spec, c.dark)
            }
        }
}

/**
 * 材质绘制分派（drawWithCache 内外共用；[bloom] = 无 GPU 模糊时点亮柔光晕）。
 *
 * 视觉分层（自下而上）：半透明底色 → 柔光晕（仅降级） → 渐变融合带 → 顶部线性高光 → 边缘折射亮边。
 */
private fun DrawScope.specPaint(spec: CtLightSpec, bloom: Boolean, dark: Boolean, c: com.furina.classtools.ui.theme.CtColor) {
    val base = if (dark) CtPalette.DarkContainer else CtPalette.Container
    // 1) 底色：关闭/无模糊时更实（alpha + (1-alpha)*0.55），弱化「看不清」的风险。
    val alpha = if (bloom) (spec.surfaceAlpha + (1f - spec.surfaceAlpha) * 0.55f).coerceIn(0f, 1f) else spec.surfaceAlpha
    drawRect(color = base.copy(alpha = alpha))
    // 2) 降级柔光晕：顶部一层极淡白光，模拟「被光柔化」的材质，替代 GPU 模糊。
    if (bloom) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to CtLightInk.highlight.copy(alpha = spec.highlightAlpha * 0.22f),
                1f to Color.Transparent,
            ),
        )
    }
    // 3) 中性 tint 叠加（材质赋色：非常轻微，避免把淡色底染出品牌味）
    if (spec.tintAlpha > 0f) {
        drawRect(color = c.primary.copy(alpha = spec.tintAlpha * 0.35f))
    }
}

/** 边缘折射亮边：顶部 0.5dp 亮线（官方「折射」）+ 底部极淡暗线（塑造玻璃厚度）。 */
private fun DrawScope.drawEdgeRefraction(spec: CtLightSpec, dark: Boolean) {
    val hair = 1f
    drawRect(
        color = CtLightInk.highlight.copy(alpha = spec.refractionAlpha.coerceAtLeast(0.10f)),
        topLeft = Offset(0f, 0f),
        size = Size(size.width, hair),
    )
    drawRect(
        color = (if (dark) CtLightInk.highlight else CtLightInk.shade).copy(alpha = if (dark) spec.refractionAlpha * 0.6f else 0.06f),
        topLeft = Offset(0f, size.height - hair),
        size = Size(size.width, hair),
    )
}

/**
 * 材质的「渐变融合带」在哪一侧：顶部悬浮（TOP_BAR）融合带在**底部**（内容向下淡出到页面），
 * 底部悬浮（BOTTOM_DOCK）融合带在**顶部**（内容向上淡出）；其余浮层（菜单/弹窗）在顶部。
 */
fun ctLightSurfaceFadeAtTop(scene: CtLightScene): Boolean = when (scene) {
    CtLightScene.TOP_BAR -> false
    else -> true
}
