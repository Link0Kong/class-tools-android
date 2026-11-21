package com.furina.classtools.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// =============================================================================
// 沉浸光感（Immersive Light）—— 令牌层
//
// 参考：华为 HarmonyOS「沉浸光感」设计语言（2026-11 UX 规范）与 ArkUI 沉浸光感开发指南：
//   - 界面层不是实色平面，而是「光学扩散 + 动态透光」的数字介质：材质滤镜 / 折射 / 高光 / 阴影
//     多层叠加，底层内容透过材质层自然透出（模糊 = 材质滤镜，边缘亮边 = 折射，顶部渐变 = 高光）；
//   - 材质提供 **5 档**：ULTRA_THIN（超薄，浮动工具栏）/ THIN（薄，搜索框）/ REGULAR（常规）/
//     THICK（厚，菜单）/ ULTRA_THICK（超厚，弹窗）；场景映射见 [ctLightSceneOf]；
//   - 用户可把整体光感设为 强 / 均衡 / 弱（官方：透明度、反色率、折射强度、环境交互程度的调控）。
//     开发者只需定义 5 档枚举，**强度矩阵在系统里映射** → 本文件 [CtLightMaterialTable] 就是那张
//     唯一映射表（level × intensity → [CtLightSpec]），纯函数，可直接 JVM 单测；
//   - 设备算力档位由系统定义（官方 `getGlobalMaterialLevel`）→ 本地等效为 [CtLightCapability]：
//     API 31+ 且有硬件加速 = FULL（真模糊）；API 26–30 = DEGRADED（半透明 + 发丝边 + 柔光晕）。
//
// 工程约束（task-80）：
//   1) 单一映射表 + 纯函数，可单测（[CtLightMaterialTable] / [ctLightSceneOf] / [ctLightCapabilityOf] /
//      [ctLightContrastFloor]）；
//   2) API 31+ 才用 RenderEffect 模糊，旧机降级（[CtLightCapability]）；
//   3) 单页模糊层数上限（[CtLightBudget]）+ 「正文不透明」红线（[CtLightSpec.textSafeSurfaceAlpha]）。
//
// 本文件**只放纯数据与纯函数**（不依赖 Android 框架类）→ 单元测试可直接在 JVM 跑；
// Compose 侧的 `LocalCtLight` / `rememberCtLightSpec` 在 `ui/components/light/CtLight.kt` 提供。
// =============================================================================

// -----------------------------------------------------------------------------
// 1. 档位 / 强度 / 场景 枚举
// -----------------------------------------------------------------------------

/**
 * 材质档（与鸿蒙沉浸光感 5 档一一对应）。
 *
 * 档位越厚 → 模糊越强、底色越不透明、透出的内容越「隐约」。
 */
enum class CtLightLevel(
    /** 持久化/测试契约名（与鸿蒙 `uiMaterial.ImmersiveStyle` 同名，禁止随意改名）。 */
    val id: String,
    /** 界面展示名。 */
    val label: String,
    /** 场景语义（文档/设置页副标题）。 */
    val scene: String,
) {
    /** 超薄：高度透明，浮动工具栏（顶栏、悬浮操作条）。 */
    ULTRA_THIN("ultra_thin", "超薄", "浮动工具栏"),

    /** 薄：较强透明，搜索框、底部浮岛。 */
    THIN("thin", "薄", "搜索框 · 底部浮岛"),

    /** 常规：通用场景（卡片式浮层、工具浮条）。 */
    MEDIUM("regular", "常规", "通用浮层"),

    /** 厚：模糊强，菜单 / 下拉 / Toast / Snackbar 等临时浮层。 */
    THICK("thick", "厚", "菜单 · 临时浮层"),

    /** 超厚：模糊很强，弹窗 / 半模态 / 大面积弹层。 */
    ULTRA_THICK("ultra_thick", "超厚", "弹窗 · 半模态"),
    ;

    companion object {
        fun fromStorage(raw: String?): CtLightLevel? =
            entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
    }
}

/**
 * 光感强度（用户设置 3 档 + 关闭）。
 *
 * 官方语义：强度 = 透明度 / 反色率 / 折射强度 / 环境交互程度的整体调控；
 * 本地把它映射为 [CtLightSpec] 的 4 个自由度（模糊半径 / 材质底色不透明度 / 折射亮边 / 顶部高光）+ 渐变融合长度。
 */
enum class CtLightIntensity(
    /** 持久化契约值（DataStore + 冷启动镜像，禁止改名）。 */
    val storageValue: String,
    /** 设置页展示文案。 */
    val label: String,
    /** 设置页副标题（一句话说明差异）。 */
    val hint: String,
) {
    /** 强：模糊更大、底色更透、高光/折射更亮（最「通透」，GPU 负载最高）。 */
    STRONG("strong", "强", "更通透 · 光感更明显"),

    /** 均衡（默认）：官方推荐值。 */
    BALANCED("balanced", "均衡", "推荐 · 性能与观感平衡"),

    /** 弱：模糊最小、底色更实、高光克制（最接近实色，最省电）。 */
    WEAK("weak", "弱", "更接近实色 · 最省电"),
    ;

    companion object {
        /** 默认强度（关闭 = 另外的 [CtLightMaterialTable.off]，不是 [CtLightIntensity] 的一员）。 */
        val DEFAULT: CtLightIntensity = BALANCED

        fun fromStorage(raw: String?): CtLightIntensity =
            entries.firstOrNull { it.storageValue == raw?.trim()?.lowercase() } ?: DEFAULT
    }
}

/**
 * 沉浸光感总开关（外观设置四选一：强 / 均衡 / 弱 / 关闭）。
 *
 * `enabled == false` 时材质退化为**既有实色规范**（surfaceContainer + 发丝描边），
 * 即不注入任何模糊层 —— 用于极致省电 / 用户不喜欢通透感的场景。
 */
@Immutable
data class CtLightSetting(
    val intensity: CtLightIntensity = CtLightIntensity.DEFAULT,
    val enabled: Boolean = true,
) {
    /** 持久化契约值："off" 或强度值。 */
    val storageValue: String get() = if (enabled) intensity.storageValue else OFF_STORAGE

    companion object {
        const val OFF_STORAGE: String = "off"

        /** 系统/旧版本缺省：开启 + 均衡。 */
        val Default: CtLightSetting = CtLightSetting()

        /** 关闭（实色规范）。 */
        val Off: CtLightSetting = CtLightSetting(enabled = false)

        /** 外观设置四选一。 */
        val choices: List<CtLightSetting> = listOf(
            CtLightSetting(CtLightIntensity.STRONG, true),
            CtLightSetting(CtLightIntensity.BALANCED, true),
            CtLightSetting(CtLightIntensity.WEAK, true),
            Off,
        )

        /** 解析持久化值：未知/缺失 → [Default]（不覆盖用户数据，只回退展示）。 */
        fun fromStorage(raw: String?): CtLightSetting {
            val v = raw?.trim()?.lowercase().orEmpty()
            if (v.isEmpty()) return Default
            if (v == OFF_STORAGE) return Off
            return CtLightSetting(intensity = CtLightIntensity.fromStorage(v), enabled = true)
        }

        /** 设置页展示文案：强 / 均衡 / 弱 / 关闭。 */
        fun labelOf(setting: CtLightSetting): String =
            if (setting.enabled) setting.intensity.label else "关闭"
    }
}

/**
 * 场景枚举（**单一场景 → 档位映射**；页面只表达「这是什么位置的浮层」，档位由这里决定）。
 *
 * 官方场景规范：
 *   - 顶部悬浮组件 = ULTRA_THIN + 渐变模糊延展顶部 → [TOP_BAR]；
 *   - 底部悬浮 = THIN + 渐变颜色蒙层延展底部 → [BOTTOM_DOCK]；
 *   - 非常驻任意位置弹出 = THICK → [MENU] / [SNACKBAR] / [TOAST]；
 *   - 半模态 / 弹出框 / 大面积内容 = ULTRA_THICK → [SHEET] / [DIALOG]；
 *   - 其余页面内「悬浮在内容之上」的元素 → [FLOATING] / [TOOL_STRIP] / [MEDIA_CHROME] / [CARD_GLASS]。
 */
enum class CtLightScene {
    /** 顶部悬浮组件（工具页顶栏、滚动渐显的页面标题栏）。 */
    TOP_BAR,

    /** 底部悬浮（浮岛 dock、底部操作条）。 */
    BOTTOM_DOCK,

    /** 半模态 / 底部弹层（CtBottomSheet、通知详情、课表详情、选宿舍…）。 */
    SHEET,

    /** 对话框（AlertDialog / 确认框 / 播放器退出确认）。 */
    DIALOG,

    /** 菜单 / 下拉（DropdownMenu、更多的操作面板、FAB 菜单）。 */
    MENU,

    /** Snackbar（底部提示条）。 */
    SNACKBAR,

    /** Toast / 轻提示（页面内自制气泡）。 */
    TOAST,

    /** 媒体播放器浮层（PPT 播放器控制条 / 顶栏标题 / 模式胶囊 / 缩略图抽屉）。 */
    MEDIA_CHROME,

    /** 工具页内浮条（筛选条、工具条、地图条、分段控件容器）。 */
    TOOL_STRIP,

    /** 页面内悬浮卡片（电费图表卡、统计卡、悬浮信息卡）。 */
    CARD_GLASS,

    /** 通用悬浮元素。 */
    FLOATING,
    ;

    /** 场景默认档位（= 官方场景规范）。 */
    val level: CtLightLevel
        get() = when (this) {
            TOP_BAR -> CtLightLevel.ULTRA_THIN
            BOTTOM_DOCK -> CtLightLevel.THIN
            SHEET, DIALOG -> CtLightLevel.ULTRA_THICK
            MENU, SNACKBAR, TOAST -> CtLightLevel.THICK
            // 播放器控制条在暗色媒体底上：THICK 才能压住画面，同时保留透光感。
            MEDIA_CHROME -> CtLightLevel.THICK
            TOOL_STRIP -> CtLightLevel.MEDIUM
            CARD_GLASS -> CtLightLevel.MEDIUM
            FLOATING -> CtLightLevel.MEDIUM
        }
}

/** 纯函数：场景 → 档位（单测锚点；页面/组件一律经此取值，不写死档位）。 */
fun ctLightSceneOf(scene: CtLightScene): CtLightLevel = scene.level

// -----------------------------------------------------------------------------
// 2. 强度矩阵（唯一映射表）
// -----------------------------------------------------------------------------

/**
 * 单档材质规格（一个 level × intensity 的全部可绘制参数）。
 *
 * 单位/语义（Compose 侧如何消费见 `ui/components/light/CtLight.kt`）：
 * - [blurSigma]：模糊 sigma（dp）。0 = 不模糊（关闭/旧机降级）；API31+ 换算 px 后进 RenderEffect；
 * - [surfaceAlpha]：材质底色（surface / 媒体遮罩）不透明度，0..1；
 * - [refractionAlpha]：边缘折射亮边不透明度（0.5dp 描边），0..1；
 * - [highlightAlpha]：顶部线性高光起点不透明度（沿竖直方向 linear 渐变到 0），0..1；
 * - [gradientFade]：顶部/底部「渐变融合」延展长度（dp）—— 材质边缘向下（或向上）淡出的过渡带；
 * - [tintAlpha]：中性 tint 叠加强度（材质赋色，官方「材质赋色」的低饱和等效）。
 *
 * 不透明度**下限**见 [textSafeSurfaceAlpha]：正文从不落在 < 该值的材质上（AA 红线）。
 */
@Immutable
data class CtLightSpec(
    val level: CtLightLevel,
    val intensity: CtLightIntensity?,
    val blurSigma: Float,
    val surfaceAlpha: Float,
    val refractionAlpha: Float,
    val highlightAlpha: Float,
    val gradientFade: Dp,
    val tintAlpha: Float,
    /** false = 关闭态：全部参数退化为实色规范（surfaceAlpha = 1，无模糊/高光/折射）。 */
    val enabled: Boolean,
) {
    /** 是否请求真模糊（已开启且 sigma > 0）；实际是否真的模糊还要看设备能力。 */
    val wantsBlur: Boolean get() = enabled && blurSigma > 0f

    /**
     * 「正文/数字安全」底色不透明度下限（AA 红线）。
     *
     * 正文类文字（正文 15 / 数字 / 表单输入）只允许落在 ≥ 该值的材质上：
     * 把它与「内容可能透出的最坏底色」按 alpha 合成后，文字对比度仍 ≥ 4.5。
     * 计算见 [ctLightContrastFloor]（单测锁定）。四档统一 0.78：
     *   浅色 textTertiary(#61696C) 压在最坏的深容器(#E6EAEC)上 → AA 仍成立；
     *   深色 textTertiary(#96A3A6) 压在最亮的浮层底上 → AA 仍成立。
     */
    val textSafeSurfaceAlpha: Float
        get() = if (!enabled) 1f else maxOf(surfaceAlpha, TEXT_SAFE_ALPHA)
}

/** 正文安全底色下限（见 [CtLightSpec.textSafeSurfaceAlpha]）。 */
const val TEXT_SAFE_ALPHA: Float = 0.78f

/**
 * **唯一映射表**：level × intensity → [CtLightSpec]。
 *
 * 设计原则（对齐官方「强度 = 透明度 / 反色率 / 折射强度 / 环境交互程度」）：
 *   1) 同一列（同一强度）内，档位越厚 → 模糊越大、底色越实、渐变融合带越长；
 *   2) 同一行（同一档位）内，强度强 → 模糊 ×1.35、底色更透、高光/折射更亮；弱 → ×0.6、底色更实、高光收敛；
 *   3) 渐变融合带（gradientFade）随档位增长：ULTRA_THIN 16dp（顶栏向下 16dp 内淡出）→ ULTRA_THICK 36dp；
 *   4) ULTRA_THIN / THIN 的 surfaceAlpha 可以低于正文安全线（它们是**导航/操作外壳**，文字是 20sp Bold
 *      标题或图标，按大字 3:1 判定），但 [CtLightSpec.textSafeSurfaceAlpha] 保证任何「承载正文」的用法
 *      自动抬到 0.78。
 *
 * 参考值来源：模糊 sigma 以鸿蒙「标题栏 8vp sigma」为 ULTRA_THIN/BALANCED 锚点，逐档 +6vp 增长；
 * 底色不透明度参照 iOS/M3 材质实测（0.42 → 0.92）。
 */
object CtLightMaterialTable {

    /** 关闭态（ID 不参与映射；level 仅用于展示/日志）。 */
    private val off = CtLightSpec(
        level = CtLightLevel.THIN,
        intensity = null,
        blurSigma = 0f,
        surfaceAlpha = 1f,
        refractionAlpha = 0f,
        highlightAlpha = 0f,
        gradientFade = 0.dp,
        tintAlpha = 0f,
        enabled = false,
    )

    /** 关闭态规格（实色规范）。 */
    val offSpec: CtLightSpec get() = off

    /** 档位排序索引（越厚越大）。 */
    private fun levelIndex(level: CtLightLevel): Int = when (level) {
        CtLightLevel.ULTRA_THIN -> 0
        CtLightLevel.THIN -> 1
        CtLightLevel.MEDIUM -> 2
        CtLightLevel.THICK -> 3
        CtLightLevel.ULTRA_THICK -> 4
    }

    /** 档位锚点（BALANCED 列）：超薄 → 超厚。 */
    private val balancedBlur = floatArrayOf(8f, 12f, 18f, 24f, 32f)
    private val balancedSurface = floatArrayOf(0.42f, 0.56f, 0.70f, 0.84f, 0.92f)
    private val balancedRefraction = floatArrayOf(0.26f, 0.22f, 0.18f, 0.14f, 0.12f)
    private val balancedHighlight = floatArrayOf(0.55f, 0.50f, 0.42f, 0.32f, 0.24f)
    private val balancedFade = floatArrayOf(16f, 20f, 24f, 30f, 36f)
    private val balancedTint = floatArrayOf(0.06f, 0.08f, 0.10f, 0.12f, 0.14f)

    /** 强度因子：[blur, surfaceAlpha, refraction, highlight, tint] 的乘/加权重。 */
    private fun factors(intensity: CtLightIntensity): FloatArray = when (intensity) {
        // 强：模糊更大、底色更透、高光/折射更亮（更「通透」）
        CtLightIntensity.STRONG -> floatArrayOf(1.35f, 0.86f, 1.35f, 1.30f, 1.25f)
        CtLightIntensity.BALANCED -> floatArrayOf(1f, 1f, 1f, 1f, 1f)
        // 弱：模糊更小、底色更实、高光收敛（更接近实色）
        CtLightIntensity.WEAK -> floatArrayOf(0.60f, 1.16f, 0.60f, 0.55f, 0.50f)
    }

    /**
     * 查表（唯一入口）：level × intensity → 规格。
     *
     * alpha 一律夹到 [0,1]；模糊下限 4dp（再小就退化成「磨砂」看不见），避免强/弱档位换算出现 0。
     */
    fun specOf(level: CtLightLevel, intensity: CtLightIntensity): CtLightSpec {
        val i = levelIndex(level)
        val f = factors(intensity)
        val blur = (balancedBlur[i] * f[0]).coerceAtLeast(4f)
        val surface = (balancedSurface[i] * f[1]).coerceIn(0f, 1f)
        val refraction = (balancedRefraction[i] * f[2]).coerceIn(0f, 1f)
        val highlight = (balancedHighlight[i] * f[3]).coerceIn(0f, 1f)
        val tint = (balancedTint[i] * f[4]).coerceIn(0f, 1f)
        return CtLightSpec(
            level = level,
            intensity = intensity,
            blurSigma = blur,
            surfaceAlpha = surface,
            refractionAlpha = refraction,
            highlightAlpha = highlight,
            gradientFade = balancedFade[i].dp,
            tintAlpha = tint,
            enabled = true,
        )
    }

    /** 查表（设置版）：关闭 → [offSpec]（保持场景档位用于阴影/描边的几何，但无光感效果）。 */
    fun specOf(level: CtLightLevel, setting: CtLightSetting): CtLightSpec =
        if (!setting.enabled) off else specOf(level, setting.intensity)

    /** 场景 × 设置 → 规格（页面最常用入口）。 */
    fun specOf(scene: CtLightScene, setting: CtLightSetting): CtLightSpec =
        specOf(ctLightSceneOf(scene), setting)

    /** 全表导出（单测/文档表生成用；顺序 level 外层、intensity 内层）。 */
    fun all(): List<CtLightSpec> = CtLightLevel.entries.flatMap { level ->
        CtLightIntensity.entries.map { intensity -> specOf(level, intensity) }
    }
}

// -----------------------------------------------------------------------------
// 3. 设备能力 & 模糊层预算
// -----------------------------------------------------------------------------

/**
 * 设备光感能力档（官方「算力档位由设备定义」的本地等效）。
 *
 * - [FULL]：API 31+（RenderEffect/BlurEffect）→ 真模糊材质；
 * - [DEGRADED]：API 26–30 → 半透明底 + 发丝描边 + 柔和外发光（无 GPU 模糊），保证可用；
 * - [OFF]：不渲染任何光感层（关闭设置 / 系统「移除动画」+ 低端兜底时由调用方选择）。
 */
enum class CtLightCapability(val blurAvailable: Boolean) {
    FULL(true),
    DEGRADED(false),
    OFF(false),
    ;
}

/** RenderEffect / BlurEffect 可用的最低 API（Android 12）。 */
const val CT_LIGHT_BLUR_MIN_SDK: Int = 31

/**
 * 纯函数：由 SDK_INT + 硬件加速 + 设置 决定能力档（单测锚点）。
 *
 * @param sdkInt `Build.VERSION.SDK_INT`
 * @param hardwareAccelerated View 树是否硬件加速（软件层无法 RenderEffect）
 * @param setting 用户光感设置（关闭 → [CtLightCapability.OFF]）
 */
fun ctLightCapabilityOf(
    sdkInt: Int,
    hardwareAccelerated: Boolean = true,
    setting: CtLightSetting = CtLightSetting.Default,
): CtLightCapability = when {
    !setting.enabled -> CtLightCapability.OFF
    sdkInt < 26 -> CtLightCapability.OFF
    sdkInt >= CT_LIGHT_BLUR_MIN_SDK && hardwareAccelerated -> CtLightCapability.FULL
    else -> CtLightCapability.DEGRADED
}

/**
 * 背景图片模糊能力：只由「设备是否支持 GPU 模糊」决定，**不受沉浸光感开关影响**。
 *
 * 2026-11-21：移除沉浸光感选择后主题固定 Off，若继续复用 [ctLightCapabilityOf]，
 * 背景模糊会被误判为不可用（用户反馈「模糊选择不起作用」）。
 */
fun ctBackgroundBlurCapabilityOf(
    sdkInt: Int,
    hardwareAccelerated: Boolean = true,
): CtLightCapability = when {
    sdkInt < 26 -> CtLightCapability.OFF
    sdkInt >= CT_LIGHT_BLUR_MIN_SDK && hardwareAccelerated -> CtLightCapability.FULL
    else -> CtLightCapability.DEGRADED
}

/**
 * 模糊层预算（性能红线）：单屏同一时刻最多允许多少个「大面积模糊层」。
 *
 * 官方约束「避免大面积使用材质，多层叠加会显著增加 GPU 负载与功耗」；
 * 本地工程口径：**常驻层（顶栏 + 底栏/dock）最多 2 个**，弹层出现时最多再 +1（即 3），
 * 页面内卡片级别的「玻璃卡」只使用半透明 + 描边（不额外开模糊），见 [CtLightBudget.cardGlassBlur]。
 */
object CtLightBudget {

    /** 常驻悬浮层（顶部 + 底部）最多 2 个。 */
    const val persistentMax: Int = 2

    /** 叠一个弹层后最多 3 个（弹层是当前焦点，允许 +1）。 */
    const val withOverlayMax: Int = 3

    /** 页面内卡片级「玻璃卡」是否开模糊：否（只用半透明 + 发丝边 + 高光，避免滚动列表时逐帧模糊）。 */
    const val cardGlassBlur: Boolean = false

    /**
     * 计算允许的模糊层数：`overlays` = 0（无常驻例外时的默认）、1（有弹层）。
     *
     * 注意：关闭光感 / 旧机降级时统一返回 0（不渲染模糊层）。→ 由调用方结合 [CtLightCapability] 使用。
     */
    fun allowedLayers(capability: CtLightCapability, overlays: Int = 0): Int = when (capability) {
        CtLightCapability.OFF, CtLightCapability.DEGRADED -> 0
        CtLightCapability.FULL -> if (overlays > 0) withOverlayMax else persistentMax
    }

    /**
     * 预算分配是否合法：常驻层 ≤ [persistentMax]；含弹层时总量 ≤ [withOverlayMax]。
     * 纯函数，供运行期断言/单测使用。
     */
    fun isWithinBudget(persistent: Int, overlays: Int): Boolean =
        persistent in 0..persistentMax && overlays >= 0 && persistent + overlays <= withOverlayMax
}

// -----------------------------------------------------------------------------
// 4. 顶部栏滚动态（透明 → 浮层渐变）
// -----------------------------------------------------------------------------

/**
 * 顶部栏滚动态：滚动 < [ctTopBarOverlayThresholdDp] 时顶栏透明（页面内容直接沉浸到状态栏下），
 * 越过阈值后渐变出浮层材质 + 向下淡出遮罩（官方「顶部悬浮组件 + 渐变模糊延展顶部」）。
 */
object CtTopBarLight {

    /** 触发浮层的最小滚动量（dp）。 */
    const val OVERLAY_THRESHOLD_DP: Float = 8f

    /**
     * 浮层进度（0 = 完全透明，1 = 完全浮层）：滚动 8dp 开始，40dp 内线性到位。
     * 纯函数（单测锚点；Compose 侧用 [rememberCtTopBarOverlay] 平滑化）。
     */
    fun overlayProgress(scrollPx: Float, density: Float): Float {
        if (density <= 0f) return if (scrollPx > 0f) 1f else 0f
        val dp = scrollPx / density
        val past = dp - OVERLAY_THRESHOLD_DP
        if (past <= 0f) return 0f
        return (past / (OVERLAY_FADE_DP)).coerceIn(0f, 1f)
    }

    /** 线性到位所需的滚动距离（dp）：8 → 48dp 之间完成淡入。 */
    const val OVERLAY_FADE_DP: Float = 40f
}

/** 顶部栏浮层阈值（dp，供测试/文档引用）。 */
val ctTopBarOverlayThresholdDp: Dp = CtTopBarLight.OVERLAY_THRESHOLD_DP.dp

/**
 * 「已越过顶栏阈值」的哨兵滚动量（px 语义的极大值）。
 *
 * Lazy 列表滚过首项后我们只知道「已经不是首项」，把滚动量给这个值即可让顶栏浮层进度夹到 1，
 * 避免在大偏移时来回切换亚像素值造成材质闪烁。纯常量，供页面与单测共用。
 */
const val CT_TOP_BAR_FULL_SCROLL_PX: Float = 100_000f

// -----------------------------------------------------------------------------
// 5. 可读性（AA）校验：正文落在材质上的最坏对比度
// -----------------------------------------------------------------------------

/**
 * 计算「正文落在材质上」的最坏情况对比度（WCAG 2.1）。
 *
 * 最坏情况：材质底不透明度 = [alpha]，其下透出的是与文字**对比度最低**的容器色（内容可能透出的最坏底色）。
 * 合成公式：`mixed = base * alpha + (worstUnder * (1 - alpha))`。
 *
 * @param text 正文/次级文字色
 * @param base 材质自身底色（surface）
 * @param worstUnder 可能透出的最坏底层色（浅色取最深容器、深色取最亮浮层）
 * @param alpha 材质底不透明度
 * @return 合成后的 WCAG 对比度（1..21）
 */
fun ctLightContrastFloor(
    text: Color,
    base: Color,
    worstUnder: Color,
    alpha: Float,
): Double {
    val a = alpha.coerceIn(0f, 1f)
    val mixed = Color(
        red = base.red * a + worstUnder.red * (1f - a),
        green = base.green * a + worstUnder.green * (1f - a),
        blue = base.blue * a + worstUnder.blue * (1f - a),
    )
    return lucidContrastRatio(text, mixed)
}

/** WCAG 2.1 相对亮度（sRGB；sRGB 线性化按官方公式）。 */
fun relativeLuminance(color: Color): Double {
    fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

/** WCAG 2.1 对比度（(L1+0.05)/(L2+0.05)，L1 为较亮者）。 */
fun lucidContrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** AA 正文阈值（11–18pt 常规字重）。 */
const val CT_LIGHT_AA_BODY: Double = 4.5

/** AA 大字/图形阈值（≥18pt 或 14pt Bold）。 */
const val CT_LIGHT_AA_LARGE: Double = 3.0

/**
 * 场景是否「承载正文」：决定使用 [CtLightSpec.textSafeSurfaceAlpha] 还是原值。
 *
 * 导航外壳（顶栏/底栏/工具条）上的文字是大字/图标 → 走 3:1；弹层/菜单/卡片承载正文 → 走 4.5:1。
 */
fun ctLightCarriesBodyText(scene: CtLightScene): Boolean = when (scene) {
    CtLightScene.TOP_BAR, CtLightScene.BOTTOM_DOCK, CtLightScene.TOOL_STRIP, CtLightScene.MEDIA_CHROME -> false
    CtLightScene.SHEET,
    CtLightScene.DIALOG,
    CtLightScene.MENU,
    CtLightScene.SNACKBAR,
    CtLightScene.TOAST,
    CtLightScene.CARD_GLASS,
    CtLightScene.FLOATING,
    -> true
}

/** 材质底色的最小可用 alpha（承载正文 → [TEXT_SAFE_ALPHA]；外壳 → 原值）。 */
fun ctLightEffectiveAlpha(scene: CtLightScene, spec: CtLightSpec): Float =
    if (ctLightCarriesBodyText(scene)) spec.textSafeSurfaceAlpha else spec.surfaceAlpha

// -----------------------------------------------------------------------------
// 6. CompositionLocal（主题注入；Compose 运行期在 ui/components/light/CtLight.kt 提供实现）
// -----------------------------------------------------------------------------

/** 当前 App 级光感设置（由 ClassToolsTheme 注入；预览默认「均衡」）。 */
val LocalCtLightSetting = staticCompositionLocalOf { CtLightSetting.Default }

/** 当前设备能力档（由 SDK_INT + View 硬件加速推导，ClassToolsTheme 注入；预览默认 FULL）。 */
val LocalCtLightCapability =
    staticCompositionLocalOf<CtLightCapability> { CtLightCapability.FULL }

/**
 * 运行期光感上下文（设置 + 能力档 + 安全区），**承载类放在令牌层**，避免
 * `LocalCtLight` / `LocalCtLightSetting` 分居两处造成循环依赖；Compose 侧（`ui/components/light/CtLight.kt`）
 * 直接消费本类。
 *
 * @param statusBarHeight 状态栏/刘海安全区高度（顶部悬浮材质向上延展到屏幕顶边用）
 * @param navigationBarHeight 导航栏安全区高度（底部悬浮蒙层延展用）
 */
@Immutable
class CtLightContext(
    val setting: CtLightSetting,
    val capability: CtLightCapability,
    val statusBarHeight: Dp = 0.dp,
    val navigationBarHeight: Dp = 0.dp,
) {
    /** 光感是否开启（能力档 OFF = 关闭 / 不支持的极端环境）。 */
    val enabled: Boolean get() = capability != CtLightCapability.OFF

    /** API31+ 真模糊是否可用。 */
    val blurAvailable: Boolean get() = capability == CtLightCapability.FULL

    /** 场景 → 规格（能力档 OFF 时返回关闭态规格）。 */
    fun spec(scene: CtLightScene): CtLightSpec =
        if (!enabled) CtLightMaterialTable.offSpec else CtLightMaterialTable.specOf(scene, setting)

    /** 显式档位 → 规格（需要脱离场景语义时用；如播放器强制 THICK）。 */
    fun spec(level: CtLightLevel): CtLightSpec =
        if (!enabled) CtLightMaterialTable.offSpec else CtLightMaterialTable.specOf(level, setting)

    /** 显式强度 → 规格（局部例外：如高对比度场景强制弱材质）。 */
    fun spec(scene: CtLightScene, intensity: CtLightIntensity): CtLightSpec =
        if (!enabled) {
            CtLightMaterialTable.offSpec
        } else {
            CtLightMaterialTable.specOf(scene, CtLightSetting(intensity, true))
        }
}

/** 当前光感上下文（ClassToolsTheme 注入；预览/孤立组件默认「均衡 + FULL + 0 安全区」）。 */
val LocalCtLight = staticCompositionLocalOf {
    CtLightContext(CtLightSetting.Default, CtLightCapability.FULL)
}

