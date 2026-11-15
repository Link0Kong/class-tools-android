package com.furina.classtools.data.todaycampus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「打开今日校园 App」Intent 判定 / 未安装回退判定纯逻辑单测。 */
class TodayCampusAppLinkTest {

    @Test
    fun `包名与 Intent 常量与平台约定一致`() {
        assertEquals("com.wisedu.cpdaily", TodayCampusAppLink.APP_PACKAGE)
        // 与 android.content.Intent.ACTION_MAIN / CATEGORY_LAUNCHER 的官方字符串值一致（纯 JVM 可测）。
        assertEquals("android.intent.action.MAIN", TodayCampusAppLink.ACTION_MAIN)
        assertEquals("android.intent.category.LAUNCHER", TodayCampusAppLink.CATEGORY_LAUNCHER)
        assertEquals("com.wisedu.cpdaily", TodayCampusCatalog.APP_PACKAGE)
    }

    @Test
    fun `能拿到启动 Intent 时直接拉起 App`() {
        assertEquals(
            TodayCampusAppLink.LaunchTarget.APP,
            TodayCampusAppLink.targetFor(appLaunchable = true),
        )
    }

    @Test
    fun `拿不到启动 Intent 时走门户回退路径`() {
        assertEquals(
            TodayCampusAppLink.LaunchTarget.PORTAL_FALLBACK,
            TodayCampusAppLink.targetFor(appLaunchable = false),
        )
    }

    @Test
    fun `回退提示文案非空且为短句`() {
        assertTrue(TodayCampusAppLink.MSG_NOT_INSTALLED.isNotBlank())
        assertTrue(TodayCampusAppLink.MSG_NOT_INSTALLED.length <= 24)
        assertTrue(TodayCampusAppLink.MSG_NOT_INSTALLED.contains("融合门户"))
    }

    @Test
    fun `包名合法性校验`() {
        assertTrue(TodayCampusAppLink.isLaunchablePackage("com.wisedu.cpdaily"))
        assertTrue(TodayCampusAppLink.isLaunchablePackage("com.a.b"))
        assertFalse(TodayCampusAppLink.isLaunchablePackage(null))
        assertFalse(TodayCampusAppLink.isLaunchablePackage(""))
        assertFalse(TodayCampusAppLink.isLaunchablePackage("   "))
        assertFalse(TodayCampusAppLink.isLaunchablePackage("cpdaily"))
        assertFalse(TodayCampusAppLink.isLaunchablePackage("com.wisedu cpdaily"))
        assertFalse(TodayCampusAppLink.isLaunchablePackage("com.wisedu\ncpdaily"))
    }
}
