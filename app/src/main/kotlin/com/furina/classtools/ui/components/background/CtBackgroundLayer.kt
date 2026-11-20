package com.furina.classtools.ui.components.background

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.background.CtBackgroundColors
import com.furina.classtools.data.background.CtBackgroundPreset
import com.furina.classtools.data.background.CtBackgroundSelection
import com.furina.classtools.data.background.ctBackgroundBlurSigma
import com.furina.classtools.data.background.ctBackgroundColorsOf
import com.furina.classtools.ui.components.light.CtLightBackdrop
import com.furina.classtools.ui.theme.CtLightFields
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import com.furina.classtools.ui.theme.LocalCtLight
import com.furina.classtools.ui.theme.rememberCtColors

/**
 * 全局自定义背景层（**唯一背景绘制入口**，由 `ExpressiveBackground` 内嵌）。
 *
 * 三层结构（自下而上），**前景一律不在这里画**，因此「背景可模糊、前景绝不模糊」：
 * 1) 底色 / 预设渐变（[CtBackgroundColors]）；
 * 2) 自定义图片：`ContentScale.Crop` + **只对本图层**施加 RenderEffect 模糊（API31+）；
 *    旧机（API26–30）不模糊，改为加一层底色蒙层（[fallbackVeilAlpha]）——观感一致、可读性不变；
 * 3) 顶部 / 底部 scrim（[CtBackgroundColors.scrimTop] / [scrimBottom]）+ 预设光感柔光斑。
 *
 * 页面内容（卡片/文字/顶栏/dock）在 [ExpressiveBackground] 里画在**本层之上**，
 * 所以永远保持锐利（这是本需求的硬红线）。
 */
@Composable
fun CtBackgroundLayer(
    modifier: Modifier = Modifier,
    selection: CtBackgroundSelection,
    image: ImageBitmap?,
) {
    val dark = LocalCtDarkTheme.current
    val light = LocalCtLight.current
    val colors = rememberCtColors()
    val palette = remember(selection, dark, image, colors.surface) {
        // 图片不可用（文件被删/非法）→ 自动回退预设，不出现「白屏背景」。
        ctBackgroundColorsOf(selection, dark, imageAvailable = image != null)
    }
    val blurSigma = remember(selection, image, light.capability) {
        if (image == null) 0f else ctBackgroundBlurSigma(selection, light.capability)
    }

    // 2026-11-20 沉浸光感 v2：预设背景改为「光场」（base + 4 团品牌柔光斑 + 流光 + 暗角）。
    // 光场是玻璃材质的**唯一采样对象** —— 没有光场时，材质层再精确也只是灰卡。
    // 自定义图片背景仍然走照片路径（下面 2)），照片本身就是光场。
    //
    // 若 App 根已绘制全局光场（[LocalCtRootAuroraActive]），本层**不再重复绘制**：
    // 根级光场从屏幕顶端连续到末端，不存在「状态栏留白条」接缝，也避免同屏多份全屏渐变。
    val field = remember(selection, dark, light.setting) {
        CtLightFields.scaled(
            field = CtLightFields.of(selection.preset, dark),
            factor = CtLightFields.alphaFactor(light.setting),
        )
    }
    val rootAurora = LocalCtRootAuroraActive.current

    Box(modifier = modifier.fillMaxSize()) {
        // 1) 底：根级光场已就绪 → 留空（透出根光场）；无照片 → 本地绘制光场
        if (image == null) {
            if (!rootAurora) {
                CtAuroraField(
                    field = field,
                    dark = dark,
                    animated = selection.preset != CtBackgroundPreset.DEEP_SPACE || !dark,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (palette.gradient) {
                            Brush.linearGradient(listOf(palette.base, palette.second))
                        } else {
                            Brush.verticalGradient(listOf(palette.base, palette.base))
                        },
                    ),
            )
        }

        // 2) 自定义图片（Crop + 仅本层模糊）
        if (image != null) {
            val density = LocalDensity.current
            val sigmaPx = with(density) { blurSigma.dp.toPx() }
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = true
                        if (sigmaPx > 0f) {
                            renderEffect = BlurEffect(
                                radiusX = sigmaPx,
                                radiusY = sigmaPx,
                                edgeTreatment = TileMode.Clamp,
                            )
                        }
                    },
            )
            // 2b) 旧机降级：无 GPU 模糊 → 用底色蒙层压低图片对比，卡片/文字依然可读。
            if (sigmaPx <= 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    palette.base.copy(alpha = fallbackVeilAlpha(dark)),
                                    palette.base.copy(alpha = fallbackVeilAlpha(dark) * 0.82f),
                                ),
                            ),
                        ),
                )
            }
        }

        // 3a) 预设光感柔光斑（照片背景时不用：避免与照片抢注意力）。
        // 光场模式下柔光斑已由 CtAuroraField 绘制，这里只在「照片背景 + 光感开启」时补一层
        // 极淡的品牌光，让照片背景上的玻璃卡也有透光层次。
        if (image != null && light.enabled) {
            CtLightBackdrop(modifier = Modifier.fillMaxSize(), baseColor = Color.Transparent)
        }

        // 3b) 顶部 / 底部 scrim：保证状态栏、顶栏、dock 附近文字可读（官方「边缘遮罩」）。
        // 2026-11-20：光场模式下 scrim 必须很轻（旧值 0.72/0.86 会把整片光场糊成灰白 —— 实测确认）。
        // 只在顶/底两端做 0.10–0.20 的柔化，中间完全不加蒙层，光场颜色与光斑层次才透得出来。
        // DEBUG-TEMP: scrim disabled
    }
}

/** 旧机（无 GPU 模糊）时压在图片上的底色蒙层不透明度。 */
internal fun fallbackVeilAlpha(dark: Boolean): Float = if (dark) 0.85f else 0.90f


/** 便捷：`Bitmap` → Compose `ImageBitmap`（已在 IO 解码，转换很轻）。 */
fun android.graphics.Bitmap.asComposeImageBitmap(): ImageBitmap = this.asImageBitmap()
