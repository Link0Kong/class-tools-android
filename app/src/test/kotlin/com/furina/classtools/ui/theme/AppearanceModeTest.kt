package com.furina.classtools.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 外观模式（我的 → 外观 三选一）纯逻辑单测：
 * 持久化契约（storageValue / DataStore key 不可随意改）+ 强制浅/深解析 + 隐藏状态栏默认值。
 */
class AppearanceModeTest {

    @Test
    fun `storage values are the persisted contract`() {
        assertEquals("system", AppearanceMode.SYSTEM.storageValue)
        assertEquals("light", AppearanceMode.LIGHT.storageValue)
        assertEquals("dark", AppearanceMode.DARK.storageValue)
    }

    @Test
    fun `fromStorage round trips every mode`() {
        AppearanceMode.entries.forEach { mode ->
            assertEquals(mode, AppearanceMode.fromStorage(mode.storageValue))
        }
    }

    @Test
    fun `fromStorage tolerates case and surrounding whitespace`() {
        assertEquals(AppearanceMode.DARK, AppearanceMode.fromStorage(" DARK "))
        assertEquals(AppearanceMode.LIGHT, AppearanceMode.fromStorage("Light"))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStorage("\tsystem\n"))
    }

    @Test
    fun `fromStorage falls back to system for missing or unknown values`() {
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStorage(null))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStorage(""))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStorage("   "))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStorage("auto"))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStorage("night"))
    }

    @Test
    fun `resolve dark follows system only in system mode`() {
        assertTrue(AppearanceMode.SYSTEM.resolveDark(systemDark = true))
        assertFalse(AppearanceMode.SYSTEM.resolveDark(systemDark = false))

        assertFalse(AppearanceMode.LIGHT.resolveDark(systemDark = true))
        assertFalse(AppearanceMode.LIGHT.resolveDark(systemDark = false))

        assertTrue(AppearanceMode.DARK.resolveDark(systemDark = true))
        assertTrue(AppearanceMode.DARK.resolveDark(systemDark = false))
    }

    @Test
    fun `default appearance mode is follow system`() {
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.DEFAULT)
    }

    @Test
    fun `three options are presented in system light dark order`() {
        assertEquals(listOf("跟随系统", "浅色", "深色"), AppearanceMode.entries.map { it.label })
    }

    @Test
    fun `datastore keys are stable`() {
        assertEquals("appearance_mode", AppearanceSettings.KEY_APPEARANCE_MODE)
        assertEquals("hide_status_bar", AppearanceSettings.KEY_HIDE_STATUS_BAR)
    }

    @Test
    fun `hide status bar defaults to enabled only when unset`() {
        assertTrue(AppearanceSettings.DEFAULT_HIDE_STATUS_BAR)
        assertTrue(AppearanceSettings.hideStatusBarOrDefault(null))
        assertTrue(AppearanceSettings.hideStatusBarOrDefault(true))
        assertFalse(AppearanceSettings.hideStatusBarOrDefault(false))
    }
}
