package com.furina.classtools.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 沉浸光感（Immersive Light）令牌层锁定。
 *
 * 对应华为「沉浸光感」设计语言：5 档材质（ULTRA_THIN…ULTRA_THICK）× 3 档强度（强/均衡/弱），
 * 以及官方场景规范（顶部 ULTRA_THIN / 底部 THIN / 弹窗 ULTRA_THICK）、AA 可读性红线。
 * 这些是 UI 的系统级约束，改动映射表会直接影响全应用观感与可读性，必须有测试兜底。
 */
class ImmersiveLightTest {

    private val levels = CtLightLevel.entries
    private val intensities = CtLightIntensity.entries

    @Test
    fun `档位越厚 底色越实 高光越弱 融合带越长`() {
        intensities.forEach { intensity ->
            val specs = levels.map { CtLightMaterialTable.specOf(it, intensity) }
            specs.zipWithNext().forEach { (thin, thick) ->
                assertTrue("$intensity: 厚档 surfaceAlpha 应更大", thick.surfaceAlpha >= thin.surfaceAlpha)
                assertTrue("$intensity: 厚档 高光应更克制", thick.highlightAlpha <= thin.highlightAlpha)
                assertTrue("$intensity: 厚档 融合带应更长", thick.gradientFade >= thin.gradientFade)
            }
        }
    }

    @Test
    fun `强度越强 底色越透 高光越亮`() {
        levels.forEach { level ->
            val strong = CtLightMaterialTable.specOf(level, CtLightIntensity.STRONG)
            val balanced = CtLightMaterialTable.specOf(level, CtLightIntensity.BALANCED)
            val weak = CtLightMaterialTable.specOf(level, CtLightIntensity.WEAK)
            assertTrue("$level: 强档应比均衡更透", strong.surfaceAlpha <= balanced.surfaceAlpha)
            assertTrue("$level: 弱档应比均衡更实", weak.surfaceAlpha >= balanced.surfaceAlpha)
            assertTrue("$level: 强档高光应更亮", strong.highlightAlpha >= balanced.highlightAlpha)
        }
    }

    @Test
    fun `场景映射遵循官方规范`() {
        assertEquals(CtLightLevel.ULTRA_THIN, ctLightSceneOf(CtLightScene.TOP_BAR))
        assertEquals(CtLightLevel.THIN, ctLightSceneOf(CtLightScene.BOTTOM_DOCK))
        assertEquals(CtLightLevel.ULTRA_THICK, ctLightSceneOf(CtLightScene.SHEET))
        assertEquals(CtLightLevel.ULTRA_THICK, ctLightSceneOf(CtLightScene.DIALOG))
        assertEquals(CtLightLevel.THICK, ctLightSceneOf(CtLightScene.MENU))
        assertEquals(CtLightLevel.THICK, ctLightSceneOf(CtLightScene.SNACKBAR))
        assertEquals(CtLightLevel.THICK, ctLightSceneOf(CtLightScene.TOAST))
        assertEquals(CtLightLevel.MEDIUM, ctLightSceneOf(CtLightScene.TOOL_STRIP))
    }

    @Test
    fun `承载正文的材质自动抬到 AA 安全不透明度`() {
        listOf(CtLightScene.SHEET, CtLightScene.DIALOG, CtLightScene.MENU, CtLightScene.CARD_GLASS).forEach { scene ->
            val spec = CtLightMaterialTable.specOf(scene, CtLightSetting.Default)
            val alpha = ctLightEffectiveAlpha(scene, spec)
            assertTrue("$scene 正文材质 alpha=$alpha 应 ≥ $TEXT_SAFE_ALPHA", alpha >= TEXT_SAFE_ALPHA - 1e-4f)
        }
        // 导航外壳（顶栏/底栏）按大字 3:1，不强制 AA 正文下限，但必须仍然可见（> 0）。
        listOf(CtLightScene.TOP_BAR, CtLightScene.BOTTOM_DOCK).forEach { scene ->
            val spec = CtLightMaterialTable.specOf(scene, CtLightSetting.Default)
            assertTrue("$scene alpha 应 > 0", spec.surfaceAlpha > 0f)
        }
    }

    @Test
    fun `正文在材质最坏叠底下仍满足 WCAG AA`() {
        val alpha = TEXT_SAFE_ALPHA
        // 浅色：底色 Container，最坏透出 ContainerHighest；正文 Primary / Secondary / Tertiary
        listOf(CtPalette.TextPrimary, CtPalette.TextSecondary, CtPalette.TextTertiary).forEach { text ->
            val ratio = ctLightContrastFloor(text, CtPalette.Container, CtPalette.ContainerHighest, alpha)
            assertTrue("浅色正文 $text 对比度 $ratio 应 ≥ 4.5", ratio >= CT_LIGHT_AA_BODY)
        }
        // 深色：底色 DarkContainer，最坏透出 DarkContainerHighest（更亮 → 对比度更低）
        listOf(CtPalette.DarkTextPrimary, CtPalette.DarkTextSecondary).forEach { text ->
            val ratio = ctLightContrastFloor(text, CtPalette.DarkContainer, CtPalette.DarkContainerHighest, alpha)
            assertTrue("深色正文 $text 对比度 $ratio 应 ≥ 4.5", ratio >= CT_LIGHT_AA_BODY)
        }
    }

    @Test
    fun `顶栏滚动 8dp 开始渐显 48dp 内到位`() {
        // density=1 时 1px = 1dp
        assertEquals(0f, CtTopBarLight.overlayProgress(0f, 1f), 1e-4f)
        assertEquals(0f, CtTopBarLight.overlayProgress(8f, 1f), 1e-4f)
        assertEquals(0.5f, CtTopBarLight.overlayProgress(28f, 1f), 1e-4f)
        assertEquals(1f, CtTopBarLight.overlayProgress(48f, 1f), 1e-4f)
        assertEquals(1f, CtTopBarLight.overlayProgress(1_000f, 1f), 1e-4f)
    }

    @Test
    fun `设备能力档按 SDK 与设置降级`() {
        assertEquals(CtLightCapability.FULL, ctLightCapabilityOf(35, true, CtLightSetting.Default))
        assertEquals(CtLightCapability.DEGRADED, ctLightCapabilityOf(30, true, CtLightSetting.Default))
        assertEquals(CtLightCapability.DEGRADED, ctLightCapabilityOf(31, false, CtLightSetting.Default))
        assertEquals(CtLightCapability.OFF, ctLightCapabilityOf(25, true, CtLightSetting.Default))
        assertEquals(CtLightCapability.OFF, ctLightCapabilityOf(35, true, CtLightSetting.Off))
    }

    @Test
    fun `模糊层预算常驻 2 层 含弹层最多 3 层`() {
        assertEquals(2, CtLightBudget.allowedLayers(CtLightCapability.FULL, overlays = 0))
        assertEquals(3, CtLightBudget.allowedLayers(CtLightCapability.FULL, overlays = 1))
        assertTrue(CtLightBudget.isWithinBudget(2, 1))
        assertTrue(!CtLightBudget.isWithinBudget(3, 1))
    }

    @Test
    fun `设置持久化值往返与未知值回退`() {
        listOf(
            CtLightSetting(CtLightIntensity.STRONG, true),
            CtLightSetting(CtLightIntensity.BALANCED, true),
            CtLightSetting(CtLightIntensity.WEAK, true),
            CtLightSetting.Off,
        ).forEach { setting ->
            assertEquals(setting, CtLightSetting.fromStorage(setting.storageValue))
        }
        assertEquals(CtLightSetting.Default, CtLightSetting.fromStorage(null))
        assertEquals(CtLightSetting.Default, CtLightSetting.fromStorage("nonsense"))
        assertEquals("强", CtLightSetting.labelOf(CtLightSetting(CtLightIntensity.STRONG, true)))
        assertEquals("关闭", CtLightSetting.labelOf(CtLightSetting.Off))
    }
}
