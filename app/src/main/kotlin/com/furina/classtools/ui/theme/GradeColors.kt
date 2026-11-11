package com.furina.classtools.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 体测评分的语义色（替代 FitScreen 里硬编码的浅色 pastel）。
 *
 * 浅色/深色各一套；一律成对（container + content），保证对比度 ≥4.5:1。
 * 用途：等级胶囊、评分卡强调色；新增其它「优秀/良好/及格/不及格」场景可复用。
 */
@Immutable
data class GradeTone(
    val container: Color,
    val content: Color,
)

@Immutable
data class GradePalette(
    val excellent: GradeTone,
    val good: GradeTone,
    val pass: GradeTone,
    val fail: GradeTone,
) {
    /** 按等级文案取 tone；未知等级按不及格处理。 */
    fun toneOf(grade: String): GradeTone = when (grade) {
        "优秀" -> excellent
        "良好" -> good
        "及格" -> pass
        else -> fail
    }
}

object GradeColors {

    val light: GradePalette = GradePalette(
        excellent = GradeTone(container = Color(0xFFD8EEE3), content = Color(0xFF17342D)),
        good = GradeTone(container = Color(0xFFE7F0DC), content = Color(0xFF2C3A1E)),
        pass = GradeTone(container = Color(0xFFF6ECD8), content = Color(0xFF4A3A1D)),
        fail = GradeTone(container = Color(0xFFF7E0DE), content = Color(0xFF5A2420)),
    )

    val dark: GradePalette = GradePalette(
        excellent = GradeTone(container = Color(0xFF1E3B31), content = Color(0xFFC9E8DB)),
        good = GradeTone(container = Color(0xFF2A3520), content = Color(0xFFD8E7C6)),
        pass = GradeTone(container = Color(0xFF3B3220), content = Color(0xFFEEDCB8)),
        fail = GradeTone(container = Color(0xFF452622), content = Color(0xFFF3CFC9)),
    )

    /** 跟随当前主题（LocalCtDarkTheme；预览可显式传 light/dark）。 */
    @Composable
    fun tokens(): GradePalette = if (LocalCtDarkTheme.current) dark else light
}
