package com.furina.classtools.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.furina.classtools.data.tool.AppTool

/**
 * 网页/小程序同款 Monet（莫奈）工具色板。
 *
 * 只用于工具图标块/工具卡图标底，不改变全局 surface：
 * - 网页 `tools/index.html` 的 --tint-blue / --tint-sand / --tint-rose / --tint-violet / --mint；
 * - 深色用同色相压暗版本，保证 content 对比度 ≥ 4.5。
 */
data class MonetTone(
    val container: Color,
    val content: Color,
    val border: Color,
)

object MonetPalette {

    private val light = mapOf(
        "mint" to MonetTone(Color(0xFFEAF2EC), Color(0xFF3F6B58), Color(0xFFD8E7DD)),
        "blue" to MonetTone(Color(0xFFEDF2F7), Color(0xFF4C6B85), Color(0xFFDCE6F0)),
        "sand" to MonetTone(Color(0xFFFAF3E6), Color(0xFF8A6A3E), Color(0xFFEFE3CB)),
        "rose" to MonetTone(Color(0xFFF7EDEB), Color(0xFF9A5F58), Color(0xFFEEDBD7)),
        "violet" to MonetTone(Color(0xFFF1EEF7), Color(0xFF6B6280), Color(0xFFE3DEEF)),
        "neutral" to MonetTone(Color(0xFFF3F1E9), Color(0xFF5C6860), Color(0xFFE7DED0)),
    )

    private val dark = mapOf(
        "mint" to MonetTone(Color(0xFF1E332B), Color(0xFFBFDCCB), Color(0xFF315044)),
        "blue" to MonetTone(Color(0xFF1C2A36), Color(0xFFC2D6E8), Color(0xFF2C4152)),
        "sand" to MonetTone(Color(0xFF332B1E), Color(0xFFE6D3B0), Color(0xFF4A3D29)),
        "rose" to MonetTone(Color(0xFF35211F), Color(0xFFE6C4BE), Color(0xFF4D302C)),
        "violet" to MonetTone(Color(0xFF292338), Color(0xFFD2C8E6), Color(0xFF3D3450)),
        "neutral" to MonetTone(Color(0xFF2A2B27), Color(0xFFD8D6CC), Color(0xFF3C3E37)),
    )

    /** 工具 → 色调：按 ToolCatalog.accentFamily 映射到网页的五组 Monet tint。 */
    fun toneOf(tool: AppTool, dark: Boolean): MonetTone {
        val key = when (tool.accentFamily) {
            "teal", "leaf" -> "mint"
            "blue", "water" -> "blue"
            "sand", "amber" -> "sand"
            "clay" -> "rose"
            "plum" -> "violet"
            else -> "neutral"
        }
        return if (dark) darkPalette.getValue(key) else lightPalette.getValue(key)
    }

    private val darkPalette: Map<String, MonetTone> = dark
    private val lightPalette: Map<String, MonetTone> = light
}

/** 当前主题下的 Monet 工具色调。 */
@Composable
@ReadOnlyComposable
fun monetToneOf(tool: AppTool): MonetTone = MonetPalette.toneOf(tool, LocalCtDarkTheme.current)
