package com.furina.classtools.data.visits

import java.util.Locale

/**
 * 登录端固定标签与颜色（唯一口径：`web/统计多端_详细统计_契约_20261120.md` §4 视觉表）。
 *
 * 五端同一套颜色；暗色模式只换底槽与文字，**不换端颜色**。
 * 颜色以 ARGB `Long` 常量放在数据层，Compose 侧用 `Color(VisitClientStyle.COLOR_WEB)` 即可，
 * 避免纯逻辑单测依赖 Compose。
 */
object VisitClientStyle {

    const val CODE_WEB = "web"
    const val CODE_MP = "mp"
    const val CODE_ANDROID = "android"
    const val CODE_WIN = "win"
    const val CODE_HARMONY = "harmony"

    /** 空/未知端的归一代码（图表分组键）。 */
    const val CODE_UNKNOWN = ""

    const val LABEL_WEB = "网页"
    const val LABEL_MP = "小程序"
    const val LABEL_ANDROID = "安卓"
    const val LABEL_WIN = "Windows"
    const val LABEL_HARMONY = "鸿蒙"

    /** 契约 §1/§4：空 client 与未知取值都显示「未记录」。 */
    const val LABEL_UNKNOWN = "未记录"

    /** 契约 §4 固定颜色（ARGB，不含 alpha 的 0x 补全为不透明）。 */
    const val COLOR_WEB: Long = 0xFF4E9082
    const val COLOR_MP: Long = 0xFF6E8CA8
    const val COLOR_ANDROID: Long = 0xFF5F8F5B
    const val COLOR_WIN: Long = 0xFFB98F63
    const val COLOR_HARMONY: Long = 0xFF8A6A92
    const val COLOR_UNKNOWN: Long = 0xFF9AA5A1

    /** 固定展示顺序（仅用于需要稳定顺序的兜底；图表按次数排序）。 */
    val ORDER: List<String> = listOf(
        CODE_WEB,
        CODE_MP,
        CODE_ANDROID,
        CODE_WIN,
        CODE_HARMONY,
        CODE_UNKNOWN,
    )

    /** 归一化端代码：去空白 + 小写；未知取值归为「未记录」（空串）。 */
    fun normalize(client: String?): String {
        val code = client?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return if (code in ORDER) code else CODE_UNKNOWN
    }

    /** 端中文标签；空/未知为「未记录」。 */
    fun labelOf(client: String?): String = when (normalize(client)) {
        CODE_WEB -> LABEL_WEB
        CODE_MP -> LABEL_MP
        CODE_ANDROID -> LABEL_ANDROID
        CODE_WIN -> LABEL_WIN
        CODE_HARMONY -> LABEL_HARMONY
        else -> LABEL_UNKNOWN
    }

    /** 端固定颜色（ARGB Long）；空/未知为「未记录」灰。 */
    fun colorOf(client: String?): Long = when (normalize(client)) {
        CODE_WEB -> COLOR_WEB
        CODE_MP -> COLOR_MP
        CODE_ANDROID -> COLOR_ANDROID
        CODE_WIN -> COLOR_WIN
        CODE_HARMONY -> COLOR_HARMONY
        else -> COLOR_UNKNOWN
    }

    /** 原始 client 取值（未归一）是否属于五端之一（供写库侧校验，可选）。 */
    fun isKnown(client: String?): Boolean = normalize(client).isNotEmpty()
}
