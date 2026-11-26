package com.furina.classtools.ui.theme

import com.furina.classtools.data.background.CtBackgroundPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 光场「预设可辨识度」回归测试（2026-11-20）。
 *
 * 背景：v2 把预设背景从「实色/渐变底」换成「光场 + 品牌柔光斑」之后，深色模式下 9 个预设
 * 看起来几乎一模一样（用户反馈）。原因是旧实现只把一团光斑 alpha ×1.35，深色底近黑、
 * 四团光斑色相接近 → 无法分辨。
 *
 * 修法：每个预设现在有独立的「主次光斑配比 + 底色冷暖偏移」（[CtLightFields.of]）。
 * 本测试锁定该行为：任意两个预设在深色/浅色下都必须有肉眼可辨的色差。
 */
class CtLightFieldPresetTest {

    /** 取样点：画面上中下三个位置（都落在光斑覆盖范围内）。 */
    private val samplePoints = listOf(
        0.30f to 0.22f,
        0.65f to 0.55f,
        0.40f to 0.85f,
    )

    private fun fieldColors(dark: Boolean): Map<CtBackgroundPreset, List<IntArray>> =
        CtBackgroundPreset.grid.associateWith { preset ->
            val field = CtLightFields.forBackdrop(preset, dark, CtLightSetting(CtLightIntensity.STRONG))
            samplePoints.map { (x, y) ->
                val c = field.sampleAt(x, y, dark)
                intArrayOf(
                    (c.red * 255).toInt(),
                    (c.green * 255).toInt(),
                    (c.blue * 255).toInt(),
                )
            }
        }

    /** 两组取样的平均 RGB 曼哈顿距离（0..765）。 */
    private fun distance(a: List<IntArray>, b: List<IntArray>): Int {
        var sum = 0
        a.indices.forEach { i ->
            sum += abs(a[i][0] - b[i][0]) + abs(a[i][1] - b[i][1]) + abs(a[i][2] - b[i][2])
        }
        return sum / a.size
    }

    @Test
    fun `深色模式下任意两个预设都可区分`() {
        val colors = fieldColors(dark = true)
        val presets = CtBackgroundPreset.grid
        var minDistance = Int.MAX_VALUE
        var minPair = ""
        presets.forEachIndexed { i, first ->
            presets.drop(i + 1).forEach { second ->
                val distance = distance(colors.getValue(first), colors.getValue(second))
                if (distance < minDistance) {
                    minDistance = distance
                    minPair = "${first.id} vs ${second.id}"
                }
            }
        }
        // 阈值 10：低于 10 的 RGB 平均差在深色面上基本看不出区别（实测对比）。
        assertTrue(
            "深色模式预设几乎没区别：$minPair 平均色差仅 $minDistance（要求 ≥ 10）",
            minDistance >= 10,
        )
    }

    @Test
    fun `浅色模式下任意两个预设都可区分`() {
        val colors = fieldColors(dark = false)
        val presets = CtBackgroundPreset.grid
        var minDistance = Int.MAX_VALUE
        presets.forEachIndexed { i, first ->
            presets.drop(i + 1).forEach { second ->
                minDistance = minOf(
                    minDistance,
                    distance(colors.getValue(first), colors.getValue(second)),
                )
            }
        }
        assertTrue("浅色模式预设色差过小：$minDistance（要求 ≥ 8）", minDistance >= 8)
    }

    @Test
    fun `暖沙与雾青在深色下色相相反`() {
        val sand = CtLightFields.of(CtBackgroundPreset.WARM_SAND, dark = true)
            .sampleAt(0.70f, 0.85f, dark = true)
        val teal = CtLightFields.of(CtBackgroundPreset.MIST_TEAL, dark = true)
            .sampleAt(0.70f, 0.85f, dark = true)
        // 暖沙：红 > 蓝；雾青：绿/蓝 > 红（色相相反，肉眼一眼可辨）
        assertTrue("暖沙应偏暖（r>b）", sand.red > sand.blue)
        assertTrue("雾青应偏冷（g>b 或 b>r）", teal.green > teal.red || teal.blue > teal.red)
        assertNotEquals(sand.red, teal.red)
    }

    @Test
    fun `默认深色玻璃光场把雾青集中在上部并随强度变化`() {
        val preset = CtBackgroundPreset.DEFAULT
        val peaks = CtLightIntensity.entries.map { intensity ->
            val field = CtLightFields.forBackdrop(preset, dark = true, setting = CtLightSetting(intensity))
            val bloom = field.blooms.single()
            val upper = field.sampleAt(bloom.x, bloom.y, dark = true)
            assertTrue(upper.green - field.base.green > upper.red - field.base.red)
            assertEquals(field.base, field.sampleAt(0.15f, 0.85f, dark = true))
            bloom.alpha
        }
        assertTrue(peaks.zipWithNext().all { (stronger, weaker) -> stronger > weaker })
        assertEquals(
            CtLightFields.scaled(CtLightFields.of(preset, dark = true), 0f),
            CtLightFields.forBackdrop(preset, dark = true, setting = CtLightSetting.Off),
        )
        assertEquals(
            CtLightFields.scaled(CtLightFields.of(preset, dark = false), 0.78f),
            CtLightFields.forBackdrop(preset, dark = false, setting = CtLightSetting.Default),
        )
    }

    @Test
    fun `深空是全部预设里最暗的一组`() {
        fun brightness(preset: CtBackgroundPreset): Float {
            val field = CtLightFields.of(preset, dark = true)
            val c = field.sampleAt(0.5f, 0.5f, dark = true)
            return c.red + c.green + c.blue
        }
        val deepSpace = brightness(CtBackgroundPreset.DEEP_SPACE)
        CtBackgroundPreset.grid
            .filter { it != CtBackgroundPreset.DEEP_SPACE }
            .forEach { other ->
                assertTrue(
                    "深空应比 ${other.id} 更暗（$deepSpace vs ${brightness(other)}）",
                    deepSpace <= brightness(other),
                )
            }
    }
}
