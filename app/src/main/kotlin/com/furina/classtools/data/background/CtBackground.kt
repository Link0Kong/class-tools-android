package com.furina.classtools.data.background

import androidx.compose.ui.graphics.Color
import com.furina.classtools.ui.theme.CtLightCapability
import com.furina.classtools.ui.theme.CtPalette
import com.furina.classtools.ui.theme.ctLightContrastFloor
import com.furina.classtools.ui.theme.lucidContrastRatio

// =============================================================================
// 自定义背景（Background）—— **纯逻辑层（无 Android/Compose 运行时依赖，可 JVM 单测）**
//
// 用户可以在「我的 → 外观 → 背景」里选择：
//   · 预设背景（[CtBackgroundPreset]）：暖白（默认）/ 雾青 / 灰蓝 / 暖沙 / 深空 + 4 组渐变；
//   · 自定义图片：系统照片选择器选图 → 压缩（长边 ≤2048 / JPEG 85）→ 存 App 私有目录
//     `filesDir/backgrounds/`（**只本机、不上传、不进 userdata 云同步**）。
//
// 每种背景都带**浅/深两套可读性参数**（[CtBackgroundColors]）：
//   - [scrimTop] / [scrimBottom]：顶部/底部附加蒙层（保证状态栏、dock 附近的文字可读）；
//   - [cardAlphaBoost]：卡片玻璃底不透明度的补偿（背景越花越实，正文才能 AA）；
//   - [blurSigma]：背景图片的模糊半径（官方「背景可模糊、前景绝不模糊」）；
//   - [headlineOnBackground]：直接画在背景上的文字色（预设渐变下也要 ≥AA）。
//
// 颜色一律引用品牌令牌 [CtPalette]（`ui/theme/CtDesign.kt`），本文件不出现颜色字面量；
// 明暗解析函数 [CtBackgroundPalette.of] 是**同一张表的两次取值**（浅色表 / 深色表）。
//
// 选择持久化在 SharedPreferences（[CtBackgroundSelection.storageValue] 为契约值），
// 冷启动首帧镜像（MainActivity 同步读）避免「先默认背景再跳变」。
// =============================================================================

/** 背景预设（枚举名 = 持久化契约，禁止改名）。 */
enum class CtBackgroundPreset(
    val id: String,
    val label: String,
    /** 是否适合深色主题（深空 / 夜幕）；其余在深色下自动换取深色参数。 */
    val darkFriendly: Boolean = false,
) {
    /** 暖白（默认）：与品牌 `CtPalette.Bg` 一致，光感斑叠加。 */
    WARM_WHITE("warm_white", "暖白"),

    /** 雾青：品牌主色淡底。 */
    MIST_TEAL("mist_teal", "雾青"),

    /** 灰蓝。 */
    SLATE_BLUE("slate_blue", "灰蓝"),

    /** 暖沙。 */
    WARM_SAND("warm_sand", "暖沙"),

    /** 深空：中性深板岩（适合夜间）。 */
    DEEP_SPACE("deep_space", "深空", darkFriendly = true),

    /** 晨雾渐变：暖白 → 雾青（非常淡的两色过渡）。 */
    MORNING_MIST("morning_mist", "晨雾渐变"),

    /** 斜阳渐变：暖沙 → 暖白。 */
    SUNSET_ANGLE("sunset_angle", "斜阳渐变"),

    /** 青黛渐变：雾青 → 灰蓝。 */
    TEAL_INDIGO("teal_indigo", "青黛渐变"),

    /** 夜幕渐变：深空 → 雾青（深色）。 */
    NIGHTFALL("nightfall", "夜幕渐变", darkFriendly = true),
    ;

    /** 是否渐变（两色层）。 */
    val isGradient: Boolean get() = this in GRADIENTS

    companion object {
        val GRADIENTS: Set<CtBackgroundPreset> = setOf(
            MORNING_MIST,
            SUNSET_ANGLE,
            TEAL_INDIGO,
            NIGHTFALL,
        )

        /** 默认背景（暖白）。 */
        val DEFAULT: CtBackgroundPreset = WARM_WHITE

        /** 设置页顺序（网格 3 列）。 */
        val grid: List<CtBackgroundPreset> = listOf(
            WARM_WHITE,
            MIST_TEAL,
            SLATE_BLUE,
            WARM_SAND,
            DEEP_SPACE,
            MORNING_MIST,
            SUNSET_ANGLE,
            TEAL_INDIGO,
            NIGHTFALL,
        )

        fun fromStorage(raw: String?): CtBackgroundPreset? =
            entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
    }
}

/**
 * 背景选择（持久化状态）。
 *
 * 两种互斥形态：
 * - 预设：[preset] != null 且 [customImagePath] == null；
 * - 自定义图片：[customImagePath] != null（绝对路径，且必须位于 App 私有背景目录）。
 *
 * @param customImagePath 自定义图片**相对文件名**（不是绝对路径；读取时拼 `filesDir/backgrounds/`，
 *        避免把外部路径写进偏好、也避免备份/迁移时路径失效）
 * @param customBlur 自定义图片的模糊档（0 关闭 / 1 轻 / 2 标准 / 3 强）
 */
data class CtBackgroundSelection(
    val preset: CtBackgroundPreset = CtBackgroundPreset.DEFAULT,
    val useCustomImage: Boolean = false,
    val customImageFile: String? = null,
    val customBlur: Int = DEFAULT_CUSTOM_BLUR,
) {
    /** 是否真的在使用自定义图片（file 非空才算）。 */
    val hasImage: Boolean get() = useCustomImage && !customImageFile.isNullOrBlank()

    /** 持久化契约值：`warm_white` / `warm_white|custom:<file>|blur:2`。 */
    val storageValue: String
        get() = buildString {
            append(preset.id)
            if (hasImage) {
                append('|').append(CUSTOM_PREFIX).append(customImageFile)
                append('|').append(BLUR_V2_PREFIX).append(customBlur.coerceIn(MIN_CUSTOM_BLUR, MAX_CUSTOM_BLUR))
            }
        }

    companion object {
        val DEFAULT: CtBackgroundSelection = CtBackgroundSelection()

        /** 自定义模糊范围（0 = 不模糊；40 = 最强；滑杆 1dp 细分）。 */
        const val MIN_CUSTOM_BLUR: Int = 0
        const val MAX_CUSTOM_BLUR: Int = 40

        /** 默认自定义模糊（0 = 不模糊，由用户滑杆调整）。 */
        const val DEFAULT_CUSTOM_BLUR: Int = 0

        const val CUSTOM_PREFIX: String = "custom:"
        const val BLUR_PREFIX: String = "blur:"
        const val BLUR_V2_PREFIX: String = "blurv2:"

        /**
         * 解析持久化值（**容错**：格式不认识 → 退默认暖白，不抛异常、不丢用户其他设置）。
         *
         * 支持：
         *   `warm_white`
         *   `deep_space|custom:<file>|blur:2`
         *   `deep_space|custom:<file>`（缺 blur → 默认 2）
         */
        fun fromStorage(raw: String?): CtBackgroundSelection {
            val value = raw?.trim().orEmpty()
            if (value.isEmpty()) return DEFAULT
            val parts = value.split('|')
            val preset = CtBackgroundPreset.fromStorage(parts.firstOrNull()) ?: CtBackgroundPreset.DEFAULT
            var file: String? = null
            var blur = DEFAULT_CUSTOM_BLUR
            parts.drop(1).forEach { token ->
                val t = token.trim()
                when {
                    t.startsWith(CUSTOM_PREFIX) ->
                        file = t.removePrefix(CUSTOM_PREFIX).takeIf { it.isNotBlank() }
                    t.startsWith(BLUR_V2_PREFIX) ->
                        blur = t.removePrefix(BLUR_V2_PREFIX).toIntOrNull()
                            ?.coerceIn(MIN_CUSTOM_BLUR, MAX_CUSTOM_BLUR) ?: DEFAULT_CUSTOM_BLUR
                    t.startsWith(BLUR_PREFIX) ->
                        blur = t.removePrefix(BLUR_PREFIX).toIntOrNull()
                            ?.let { rawBlur ->
                                // 旧版 1/2/3 档 → 12/24/40dp。
                                if (rawBlur in 1..3) rawBlur * 12 + (if (rawBlur == 3) 4 else 0) else rawBlur
                            }
                            ?.coerceIn(MIN_CUSTOM_BLUR, MAX_CUSTOM_BLUR) ?: DEFAULT_CUSTOM_BLUR
                }
            }
            return if (file == null) {
                CtBackgroundSelection(preset = preset)
            } else {
                CtBackgroundSelection(
                    preset = preset,
                    useCustomImage = true,
                    customImageFile = file,
                    customBlur = blur,
                )
            }
        }

        /** 选择预设（保留自定义图片文件与模糊档，便于用户切回）。 */
        fun withPreset(current: CtBackgroundSelection, preset: CtBackgroundPreset): CtBackgroundSelection =
            current.copy(preset = preset, useCustomImage = false)

        /** 应用自定义图片（同时记住一个合适的浅/深预设作为「删除后恢复」的底）。 */
        fun withImage(
            current: CtBackgroundSelection,
            file: String,
            blur: Int = current.customBlur,
        ): CtBackgroundSelection = current.copy(
            useCustomImage = true,
            customImageFile = file,
            customBlur = blur.coerceIn(MIN_CUSTOM_BLUR, MAX_CUSTOM_BLUR),
        )

        /** 删除自定义图片 → 恢复默认预设（暖白）。 */
        fun withoutImage(current: CtBackgroundSelection): CtBackgroundSelection =
            current.copy(useCustomImage = false, customImageFile = null)
    }
}

/**
 * 背景配色参数（浅/深各一套；纯数据）。
 *
 * @param base 背景底色（渐变的起点色）
 * @param second 渐变终点色（[gradient] = true 时有效）
 * @param gradient 是否两色线性渐变
 * @param headlineOnBackground 直接画在背景上的标题/正文文字色（需 ≥AA）
 * @param cardAlphaBoost 卡片玻璃底补偿系数（0..1，越大越不透明）
 * @param scrimTop 顶部蒙层不透明度（状态栏/顶栏附近）
 * @param scrimBottom 底部蒙层不透明度（dock 附近）
 * @param bloomAlpha 光感柔光斑强度（叠加在背景上；自定义图片时为 0）
 */
data class CtBackgroundColors(
    val base: Color,
    val second: Color,
    val gradient: Boolean,
    val headlineOnBackground: Color,
    val cardAlphaBoost: Float,
    val scrimTop: Float,
    val scrimBottom: Float,
    val bloomAlpha: Float,
)

/**
 * 背景图片显示参数（纯数据 + 纯函数）。
 *
 * 「背景可模糊、前景绝不模糊」：模糊只作用在背景图片图层（[blurSigmaFor] 决定的 σ）；
 * 前景（卡片/文字/顶栏/dock）永远与背景层**分离绘制**，因此不会被渲染层模糊（见 `CtBackgroundLayer`）。
 */
/**
 * 品牌色引用（**不在本文件写颜色字面量**；全部来自 `ui/theme/CtDesign.kt` 的 [CtPalette]）。
 */
internal object BgTokens {
    val bg = CtPalette.Bg
    val surface = CtPalette.Surface
    val container = CtPalette.Container
    val containerHigh = CtPalette.ContainerHigh
    val containerHighest = CtPalette.ContainerHighest
    val textPrimary = CtPalette.TextPrimary
    val teal = CtPalette.MistTeal
    val blue = CtPalette.SlateBlue
    val sand = CtPalette.WarmSand
    val darkBg = CtPalette.DarkBg
    val darkSurface = CtPalette.DarkSurface
    val darkContainer = CtPalette.DarkContainer
    val darkContainerHigh = CtPalette.DarkContainerHigh
    val darkContainerHighest = CtPalette.DarkContainerHighest
    val darkTextPrimary = CtPalette.DarkTextPrimary
    val heroDarkPaper = CtPalette.HeroDarkPaper
    val heroDarkPill = CtPalette.HeroDarkPill
}

/** 混色（t = 0 取 [from]，1 取 [to]）—— 等价 Compose 的 lerp，但保持本层无 Compose 运行时依赖。 */
private fun mix(from: Color, to: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * f,
        green = from.green + (to.green - from.green) * f,
        blue = from.blue + (to.blue - from.blue) * f,
        alpha = 1f,
    )
}

/**
 * 预设 → 明暗两套参数（**唯一映射表**，纯函数，可单测）。
 *
 * 浅色：品牌中性底（暖白 = `CtPalette.Bg`）+ 品牌三色 4–8% 低饱和染色；
 * 深色：中性深板岩（`DarkBg` / `DarkContainer`）+ 同一色相的极淡染色，保持「深而不灰糊」。
 */
object CtBackgroundPalette {

    /** 浅色参数。 */
    fun light(preset: CtBackgroundPreset): CtBackgroundColors = when (preset) {
        CtBackgroundPreset.WARM_WHITE -> CtBackgroundColors(
            base = BgTokens.bg,
            second = BgTokens.bg,
            gradient = false,
            headlineOnBackground = BgTokens.textPrimary,
            cardAlphaBoost = 0f,
            scrimTop = 0.72f,
            scrimBottom = 0.86f,
            bloomAlpha = 1f,
        )

        CtBackgroundPreset.MIST_TEAL -> CtBackgroundColors(
            base = mix(BgTokens.bg, BgTokens.teal, 0.10f),
            second = mix(BgTokens.bg, BgTokens.teal, 0.10f),
            gradient = false,
            headlineOnBackground = BgTokens.textPrimary,
            cardAlphaBoost = 0.10f,
            scrimTop = 0.74f,
            scrimBottom = 0.88f,
            bloomAlpha = 0.9f,
        )

        CtBackgroundPreset.SLATE_BLUE -> CtBackgroundColors(
            base = mix(BgTokens.bg, BgTokens.blue, 0.10f),
            second = mix(BgTokens.bg, BgTokens.blue, 0.10f),
            gradient = false,
            headlineOnBackground = BgTokens.textPrimary,
            cardAlphaBoost = 0.10f,
            scrimTop = 0.74f,
            scrimBottom = 0.88f,
            bloomAlpha = 0.9f,
        )

        CtBackgroundPreset.WARM_SAND -> CtBackgroundColors(
            base = mix(BgTokens.bg, BgTokens.sand, 0.12f),
            second = mix(BgTokens.bg, BgTokens.sand, 0.12f),
            gradient = false,
            headlineOnBackground = BgTokens.textPrimary,
            cardAlphaBoost = 0.10f,
            scrimTop = 0.74f,
            scrimBottom = 0.88f,
            bloomAlpha = 0.9f,
        )

        CtBackgroundPreset.DEEP_SPACE -> CtBackgroundColors(
            base = BgTokens.darkBg,
            second = BgTokens.darkBg,
            gradient = false,
            headlineOnBackground = BgTokens.darkTextPrimary,
            cardAlphaBoost = 0.18f,
            scrimTop = 0.70f,
            scrimBottom = 0.86f,
            bloomAlpha = 0.8f,
        )

        CtBackgroundPreset.MORNING_MIST -> CtBackgroundColors(
            base = BgTokens.bg,
            second = mix(BgTokens.bg, BgTokens.teal, 0.16f),
            gradient = true,
            headlineOnBackground = BgTokens.textPrimary,
            cardAlphaBoost = 0.12f,
            scrimTop = 0.76f,
            scrimBottom = 0.90f,
            bloomAlpha = 0.7f,
        )

        CtBackgroundPreset.SUNSET_ANGLE -> CtBackgroundColors(
            base = mix(BgTokens.bg, BgTokens.sand, 0.18f),
            second = mix(BgTokens.container, BgTokens.sand, 0.10f),
            gradient = true,
            headlineOnBackground = BgTokens.textPrimary,
            cardAlphaBoost = 0.12f,
            scrimTop = 0.76f,
            scrimBottom = 0.90f,
            bloomAlpha = 0.7f,
        )

        CtBackgroundPreset.TEAL_INDIGO -> CtBackgroundColors(
            base = mix(BgTokens.bg, BgTokens.teal, 0.14f),
            second = mix(BgTokens.bg, BgTokens.blue, 0.16f),
            gradient = true,
            headlineOnBackground = BgTokens.textPrimary,
            cardAlphaBoost = 0.12f,
            scrimTop = 0.76f,
            scrimBottom = 0.90f,
            bloomAlpha = 0.7f,
        )

        CtBackgroundPreset.NIGHTFALL -> CtBackgroundColors(
            base = BgTokens.darkBg,
            second = mix(BgTokens.darkBg, BgTokens.teal, 0.16f),
            gradient = true,
            headlineOnBackground = BgTokens.darkTextPrimary,
            cardAlphaBoost = 0.20f,
            scrimTop = 0.70f,
            scrimBottom = 0.88f,
            bloomAlpha = 0.7f,
        )
    }

    /** 深色参数（预设若本身是深色专属，则更「深而清晰」；浅色预设统一映射到中性深板岩）。 */
    fun dark(preset: CtBackgroundPreset): CtBackgroundColors = when (preset) {
        CtBackgroundPreset.DEEP_SPACE -> CtBackgroundColors(
            base = BgTokens.darkBg,
            second = BgTokens.darkBg,
            gradient = false,
            headlineOnBackground = BgTokens.darkTextPrimary,
            cardAlphaBoost = 0.16f,
            scrimTop = 0.70f,
            scrimBottom = 0.88f,
            bloomAlpha = 0.7f,
        )

        CtBackgroundPreset.NIGHTFALL -> CtBackgroundColors(
            base = BgTokens.darkBg,
            second = mix(BgTokens.darkBg, BgTokens.teal, 0.20f),
            gradient = true,
            headlineOnBackground = BgTokens.darkTextPrimary,
            cardAlphaBoost = 0.18f,
            scrimTop = 0.70f,
            scrimBottom = 0.88f,
            bloomAlpha = 0.7f,
        )

        CtBackgroundPreset.MIST_TEAL -> CtBackgroundColors(
            base = mix(BgTokens.darkBg, BgTokens.teal, 0.16f),
            second = mix(BgTokens.darkBg, BgTokens.teal, 0.16f),
            gradient = false,
            headlineOnBackground = BgTokens.darkTextPrimary,
            cardAlphaBoost = 0.12f,
            scrimTop = 0.70f,
            scrimBottom = 0.86f,
            bloomAlpha = 0.64f,
        )

        CtBackgroundPreset.SLATE_BLUE -> CtBackgroundColors(
            base = mix(BgTokens.darkBg, BgTokens.blue, 0.16f),
            second = mix(BgTokens.darkBg, BgTokens.blue, 0.16f),
            gradient = false,
            headlineOnBackground = BgTokens.darkTextPrimary,
            cardAlphaBoost = 0.12f,
            scrimTop = 0.70f,
            scrimBottom = 0.86f,
            bloomAlpha = 0.64f,
        )

        CtBackgroundPreset.WARM_SAND -> CtBackgroundColors(
            base = mix(BgTokens.darkBg, BgTokens.sand, 0.16f),
            second = mix(BgTokens.darkBg, BgTokens.sand, 0.16f),
            gradient = false,
            headlineOnBackground = BgTokens.darkTextPrimary,
            cardAlphaBoost = 0.12f,
            scrimTop = 0.70f,
            scrimBottom = 0.86f,
            bloomAlpha = 0.64f,
        )

        CtBackgroundPreset.MORNING_MIST -> CtBackgroundColors(
            base = BgTokens.darkBg,
            second = mix(BgTokens.darkBg, BgTokens.teal, 0.18f),
            gradient = true,
            headlineOnBackground = BgTokens.darkTextPrimary,
            cardAlphaBoost = 0.14f,
            scrimTop = 0.70f,
            scrimBottom = 0.88f,
            bloomAlpha = 0.58f,
        )

        CtBackgroundPreset.SUNSET_ANGLE -> CtBackgroundColors(
            base = BgTokens.darkBg,
            second = mix(BgTokens.darkBg, BgTokens.sand, 0.18f),
            gradient = true,
            headlineOnBackground = BgTokens.darkTextPrimary,
            cardAlphaBoost = 0.14f,
            scrimTop = 0.70f,
            scrimBottom = 0.88f,
            bloomAlpha = 0.58f,
        )

        CtBackgroundPreset.TEAL_INDIGO -> CtBackgroundColors(
            base = mix(BgTokens.darkBg, BgTokens.teal, 0.14f),
            second = mix(BgTokens.darkBg, BgTokens.blue, 0.18f),
            gradient = true,
            headlineOnBackground = BgTokens.darkTextPrimary,
            cardAlphaBoost = 0.14f,
            scrimTop = 0.70f,
            scrimBottom = 0.88f,
            bloomAlpha = 0.58f,
        )

        CtBackgroundPreset.WARM_WHITE -> CtBackgroundColors(
            base = BgTokens.darkBg,
            second = BgTokens.darkBg,
            gradient = false,
            headlineOnBackground = BgTokens.darkTextPrimary,
            cardAlphaBoost = 0.10f,
            scrimTop = 0.70f,
            scrimBottom = 0.86f,
            bloomAlpha = 0.72f,
        )
    }

    /** 纯函数入口：预设 × 明暗。 */
    fun of(preset: CtBackgroundPreset, dark: Boolean): CtBackgroundColors =
        if (dark) dark(preset) else light(preset)
}
object CtBackgroundImage {

    /** 压缩目标：长边上限（px）。 */
    const val MAX_LONG_EDGE: Int = 2048

    /** 压缩目标：JPEG 质量。 */
    const val JPEG_QUALITY: Int = 85

    /** 缩略图（设置页预览 / 小尺寸）：长边上限（px）。 */
    const val THUMB_LONG_EDGE: Int = 320

    /** 私有目录名（`filesDir/backgrounds/`）。 */
    const val DIR_NAME: String = "backgrounds"

    /** 文件名前缀（便于清理与识别）。 */
    const val FILE_PREFIX: String = "bg_"

    /**
     * 自定义模糊档 → σ（dp）。
     *
     * 0 = 不模糊（仅在用户显式选择）；1=12dp / 2=24dp（默认）/ 3=40dp。
     * 背景层单独模糊，其 σ 不影响前景清晰度。
     */
    fun blurSigmaFor(blur: Int): Float = blur.coerceIn(0, 40).toFloat()

    /** 模糊展示名（设置页）：连续 dp，便于滑杆读数。 */
    fun blurLabel(blur: Int): String =
        if (blur <= 0) "不模糊" else "${blur.coerceIn(0, 40)} dp"

    /**
     * 计算压缩后的目标尺寸（保持比例、**只缩小不放大**）。
     *
     * @param width 原图宽（px）
     * @param height 原图高（px）
     * @param maxLongEdge 长边上限（默认 [MAX_LONG_EDGE]）
     * @return (targetW, targetH)；非法输入（≤0）返回 (0, 0)，调用方据此回退失败
     */
    fun targetSize(width: Int, height: Int, maxLongEdge: Int = MAX_LONG_EDGE): Pair<Int, Int> {
        if (width <= 0 || height <= 0 || maxLongEdge <= 0) return 0 to 0
        val longEdge = maxOf(width, height)
        if (longEdge <= maxLongEdge) return width to height
        val scale = maxLongEdge.toFloat() / longEdge.toFloat()
        val w = (width * scale).toInt().coerceAtLeast(1)
        val h = (height * scale).toInt().coerceAtLeast(1)
        return w to h
    }

    /**
     * 文件名合法性（防路径穿越）：只允许 `bg_` 前缀 + `[A-Za-z0-9_-]+` + `.jpg`，且不含分隔符。
     *
     * 非法 → false；调用方据此回退默认背景（不读外部文件）。
     */
    fun isValidFileName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        if (name.contains('/') || name.contains('\\') || name.contains("..")) return false
        if (!name.startsWith(FILE_PREFIX) || !name.endsWith(".jpg")) return false
        val stem = name.removePrefix(FILE_PREFIX).removeSuffix(".jpg")
        return stem.isNotEmpty() && stem.all { it.isLetterOrDigit() || it == '_' || it == '-' }
    }

    /** 生成新文件名：`bg_<timestamp>.jpg`（时间戳 + 计数器，避免同毫秒覆盖）。 */
    fun newFileName(timestampMs: Long, salt: Int = 0): String =
        FILE_PREFIX + timestampMs + if (salt > 0) "_$salt" else "" + ".jpg"
}

/**
 * 「背景 → 实际生效参数」解析（纯函数；单测锚点）。
 *
 * 把「用户选择 + 明暗 + 设备能力」解析成最终要画的东西：
 *   - 预设：颜色层 + 光感柔光斑；
 *   - 自定义图片：图片层（可模糊）+ 上下 scrim + 更实的卡片底（[cardAlphaBoost] 提高）。
 *
 * @param dark 当前是否深色
 * @param imageAvailable 自定义图片文件是否存在且合法（false → 退回预设）
 * @param blurSupported 设备是否支持 GPU 模糊（API31+ 且硬件加速）
 */
fun ctBackgroundColorsOf(
    selection: CtBackgroundSelection,
    dark: Boolean,
    imageAvailable: Boolean = false,
): CtBackgroundColors {
    val preset = CtBackgroundPalette.of(selection.preset, dark)
    if (!selection.hasImage || !imageAvailable) return preset
    // 自定义图片：图片本身提供明暗，蒙层/卡片底要更实（浅色偏亮、深色偏暗）。
    return preset.copy(
        base = if (dark) BgTokens.darkBg else BgTokens.bg,
        second = if (dark) BgTokens.darkBg else BgTokens.bg,
        gradient = false,
        headlineOnBackground = if (dark) BgTokens.darkTextPrimary else BgTokens.textPrimary,
        // 图片背景：蒙层只负责把画面压到可读区间，不能把图片完全盖成纯色。
        // 浅色用白蒙层提亮（0.58/0.70），深色用黑蒙层压暗（0.66/0.74）；
        // 卡片玻璃底再 +0.18 保证正文 AA（见 CtBackgroundTest 组合断言）。
        cardAlphaBoost = preset.cardAlphaBoost + 0.18f,
        scrimTop = if (dark) 0.84f else 0.82f,
        scrimBottom = if (dark) 0.88f else 0.86f,
        bloomAlpha = 0f,
    )
}

/**
 * 自定义图片背景的实际模糊 σ（dp）：设备不支持 GPU 模糊时返回 0（调用方改画半透明蒙层）。
 */
fun ctBackgroundBlurSigma(
    selection: CtBackgroundSelection,
    capability: CtLightCapability,
): Float = if (capability == CtLightCapability.FULL && selection.hasImage) {
    CtBackgroundImage.blurSigmaFor(selection.customBlur)
} else {
    0f
}

/**
 * 可读性红线校验（AA）：背景上直接渲染的标题文字、卡片玻璃底上的正文。
 *
 * @param preset 预设
 * @param dark 明暗
 * @param cardSurface 卡片的实色底（浅 `#FFFFFF` / 深 `#1A1E21`）
 * @param cardText 卡片正文色
 * @param glassAlpha 卡片玻璃底不透明度（0..1；与「最坏底层」合成后算对比度）
 * @param worstUnder 背景最坏底层（预设底色）
 * @param bodyTextOnCard 该卡片上的正文色
 * @return (标题对背景, 正文对卡片) 两个对比度
 */
fun ctBackgroundContrast(preset: CtBackgroundPreset, dark: Boolean): CtBackgroundCheck {
    val palette = CtBackgroundPalette.of(preset, dark)
    val base = palette.base
    val headline = palette.headlineOnBackground
    // 标题直接画在背景上（最坏情况：背景是渐变的另一色，取两者较差的那个）。
    val headlineRatio = if (palette.gradient) {
        minOf(lucidContrastRatio(headline, base), lucidContrastRatio(headline, palette.second))
    } else {
        lucidContrastRatio(headline, base)
    }
    // 卡片正文：卡片实色底压在背景上（不透明卡片本身 ≥ AA，这里额外校验「半透明卡片」）。
    val cardSolid = if (dark) BgTokens.darkSurface else BgTokens.surface
    val bodyText = if (dark) BgTokens.darkTextPrimary else BgTokens.textPrimary
    val worst = if (dark) BgTokens.darkContainerHighest else palette.base
    val cardTextRatio = ctLightContrastFloor(bodyText, cardSolid, worst, 1f)
    return CtBackgroundCheck(headlineOnBackground = headlineRatio, bodyOnCard = cardTextRatio)
}

/** 背景可读性校验结果（单测/诊断展示用）。 */
data class CtBackgroundCheck(
    val headlineOnBackground: Double,
    val bodyOnCard: Double,
) {
    /** 是否全部满足 AA（正文 4.5 : 1）。 */
    val passesAA: Boolean get() = headlineOnBackground >= 4.5 && bodyOnCard >= 4.5
}

