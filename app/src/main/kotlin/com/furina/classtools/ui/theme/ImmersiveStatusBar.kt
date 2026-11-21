package com.furina.classtools.ui.theme

import android.content.Context
import android.os.Build
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.background.CtBackgroundPreset
import com.furina.classtools.ui.components.background.drawLightField
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furina.classtools.data.background.CtBackgroundSelection
import com.furina.classtools.data.background.LocalBackgroundStore
import kotlinx.coroutines.delay
import com.furina.classtools.ui.components.background.asComposeImageBitmap
import com.furina.classtools.data.background.ctBackgroundBlurSigma
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip

/**
 * 全局沉浸式状态栏（2026-11-15 用户要求：app 使用时顶部通知栏隐藏）。
 *
 * 行为：
 * - [enabled] = true：用 [WindowInsetsControllerCompat] 隐藏 **statusBars**（导航栏保持原样，
 *   不影响 v4.54 修好的底部黑条方案），`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` 下滑临时显示；
 *   回前台（ON_RESUME）/ 其它页面把状态栏重新 show 出来时自动补隐藏；
 * - [enabled] = false：恢复显示状态栏，各页面 `statusBarsPadding()` 的行为完全不变；
 * - **insets 兜底**：状态栏隐藏后 `WindowInsets.statusBars` 会变成 0，页面 `statusBarsPadding()`
 *   也会归零。这里在内容顶部补一段等价安全区（系统 status_bar_height 与刘海取大者）**再加 10dp
 *   呼吸区**（v4.55 用户反馈「UI 不要直接贴顶」），保证身份页/工作台/工具页/WebView 页的内容与
 *   返回按钮都不贴屏幕顶边；页面自行进入全屏（导航栏也被隐藏，如沉浸式 PPT 播放器）时不补，保持真正全屏。
 *
 * 只负责状态栏；底部导航栏仍由 MainActivity 的 `enableEdgeToEdge` + 透明导航栏（v4.54 黑条修复）管理。
 */
@Composable
fun CtImmersiveStatusBar(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = remember(view) { context.findActivity()?.window }
    val density = LocalDensity.current

    // 读取 insets：状态栏隐藏后 top = 0；被其它全屏页面 show 回来时组件会重组，下面会补隐藏。
    val statusBarTop = WindowInsets.statusBars.getTop(density)
    val navigationBarBottom = WindowInsets.navigationBars.getBottom(density)
    val cutoutTop = WindowInsets.displayCutout.getTop(density)

    // 首帧/开关切换立即应用；之后状态栏若重新可见（如退出沉浸式播放器）延迟补隐藏，
    // 避免和「下滑临时显示」抢时序（临时显示时系统不派发 insets，正常不会触发这里）。
    var appliedOnce by remember(window) { mutableStateOf(false) }
    LaunchedEffect(window, enabled, statusBarTop) {
        if (window == null) return@LaunchedEffect
        when {
            !enabled -> {
                window.setStatusBarsHidden(false)
                appliedOnce = false
            }

            !appliedOnce -> {
                window.setStatusBarsHidden(true)
                appliedOnce = true
            }

            statusBarTop > 0 -> {
                delay(900)
                window.setStatusBarsHidden(true)
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        window?.setStatusBarsHidden(enabled)
    }

    // 状态栏隐藏时的顶部安全区（见类注释）：status_bar_height 与刘海高度取大；24dp 兜底；
    // 再额外加 [CtTopBreathing] 呼吸区，避免页面内容/顶栏返回按钮贴屏幕顶边。
    val statusBarHeightPx = remember(context) { statusBarHeightPx(context) }
    val reserveTop = enabled && statusBarTop == 0 && navigationBarBottom > 0
    val extraTop = if (reserveTop) {
        with(density) { maxOf(statusBarHeightPx, cutoutTop).toDp() } + CtTopBreathing
    } else {
        0.dp
    }

    // 全局光场在 App 根绘制（**挂在根 Box 自身的 drawBehind 上**）：
    // 子节点方案会被布局约束/层叠顺序干扰（实测顶部 63dp 之外会被根底色盖住），
    // 而 drawBehind 直接使用根 Box 的绘制尺寸，保证从屏幕最顶端到最底端连续。
    val dark = LocalCtDarkTheme.current
    val light = LocalCtLight.current
    val store = LocalBackgroundStore.current
    val selection = if (store != null) {
        store.selection.collectAsStateWithLifecycle().value
    } else {
        CtBackgroundSelection.DEFAULT
    }
    val field = remember(selection.preset, dark, light.setting) {
        CtLightFields.scaled(
            field = CtLightFields.of(selection.preset, dark),
            factor = CtLightFields.alphaFactor(light.setting),
        )
    }

    // 自定义图片背景：**必须在 App 根画一次**，否则只有「页面内容区」有图、状态栏留白条仍是光场，
    // 观感就是「只有工作台背景是全屏的」（用户反馈的根因）。
    val bgBitmap = if (store != null) {
        store.bitmap.collectAsStateWithLifecycle().value
    } else {
        null
    }
    val imageBitmap = if (selection.hasImage && bgBitmap != null) {
        remember(bgBitmap) { bgBitmap.asComposeImageBitmap() }
    } else {
        null
    }
    // 背景模糊只看设备能力，不再受「沉浸光感」开关影响（否则固定 Off 后模糊永久失效）。
    val backgroundBlurCapability = remember(view) {
        ctBackgroundBlurCapabilityOf(
            sdkInt = Build.VERSION.SDK_INT,
            hardwareAccelerated = runCatching { view.isHardwareAccelerated }.getOrDefault(true),
        )
    }
    val blurSigmaDp = remember(selection, backgroundBlurCapability, imageBitmap) {
        if (imageBitmap == null) 0f else ctBackgroundBlurSigma(selection, backgroundBlurCapability)
    }
    val veilAlpha = remember(dark) { if (dark) 0.66f else 0.46f }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                if (imageBitmap == null) {
                    drawLightField(
                        field = field,
                        dark = dark,
                        virtual = size,
                        timeMs = 0L,
                    )
                }
            },
    ) {
        if (imageBitmap != null) {
            // 图片层：整屏 Crop + 仅本层模糊（默认不模糊）+ 可读性蒙层；前景永远清晰。
            val sigmaPx = with(density) { blurSigmaDp.dp.toPx() }
            androidx.compose.foundation.Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = true
                        if (sigmaPx > 0f) {
                            renderEffect = androidx.compose.ui.graphics.BlurEffect(
                                radiusX = sigmaPx,
                                radiusY = sigmaPx,
                                edgeTreatment = androidx.compose.ui.graphics.TileMode.Clamp,
                            )
                        }
                    },
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to MaterialTheme.colorScheme.background.copy(alpha = veilAlpha * 0.90f),
                            0.40f to MaterialTheme.colorScheme.background.copy(alpha = veilAlpha * 0.55f),
                            1f to MaterialTheme.colorScheme.background.copy(alpha = veilAlpha),
                        ),
                    ),
            )
        }
        androidx.compose.runtime.CompositionLocalProvider(
            com.furina.classtools.ui.components.background.LocalCtRootAuroraActive provides true,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = extraTop),
            ) {
                content()
            }
        }
    }
}

/** 隐藏/恢复状态栏（保留导航栏；图标深浅仍由 [ClassToolsTheme] 的 isAppearanceLightStatusBars 管）。 */
private fun Window.setStatusBarsHidden(hide: Boolean) {
    val controller = WindowCompat.getInsetsController(this, decorView)
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (hide) {
        controller.hide(WindowInsetsCompat.Type.statusBars())
    } else {
        controller.show(WindowInsetsCompat.Type.statusBars())
    }
}

/** 状态栏标准高度（px）：优先系统 dimen，缺失时按 24dp 兜底。 */
private fun statusBarHeightPx(context: Context): Int {
    val res = context.resources
    val id = res.getIdentifier("status_bar_height", "dimen", "android")
    val fromRes = if (id > 0) runCatching { res.getDimensionPixelSize(id) }.getOrDefault(0) else 0
    val fallback = (24f * res.displayMetrics.density).toInt()
    return maxOf(fromRes, fallback)
}

/**
 * 状态栏隐藏时，在安全区之外再留的顶部呼吸区（用户要求约 8–12dp，取 10dp）。
 *
 * 既避免页面内容/顶栏返回按钮贴屏幕顶边，也不至于明显压缩首屏。
 */
private val CtTopBreathing = 10.dp

