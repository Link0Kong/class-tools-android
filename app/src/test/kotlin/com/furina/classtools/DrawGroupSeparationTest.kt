package com.furina.classtools

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 抽签 / 分组独立性的源码级回归：
 * 抽签页不再有「去分组 / 下一步」与 onGroup 回调链，结果单入口删除；
 * 分组页文案改为「最近抽签」，且抽签成功会写本机最近抽签存储。
 *
 * 说明：Compose 函数签名在字节码里带 Composable 参数，直接反射不稳定；
 * 这里沿用本仓既有源码断言测试（如 OppoAccountConfigTest）的做法。
 */
class DrawGroupSeparationTest {

    private fun source(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = candidates.firstOrNull { it.isFile }
        assertNotNull("源文件未找到：$relative（工作目录=${File(".").absolutePath}）", file)
        return file!!.readText()
    }

    @Test
    fun `抽签页不再有 onGroup 与结果单入口`() {
        val drawScreen = source("src/main/kotlin/com/furina/classtools/ui/screens/draw/DrawScreen.kt")
        val drawSections = source("src/main/kotlin/com/furina/classtools/ui/screens/draw/DrawSections.kt")

        listOf("onGroup", "onExportSheet", "结果单", "去分组", "用此结果分组").forEach { forbidden ->
            assertFalse("DrawScreen 不应再包含 $forbidden", drawScreen.contains(forbidden))
            assertFalse("DrawSections 不应再包含 $forbidden", drawSections.contains(forbidden))
        }
        assertTrue("抽签应写入最近抽签存储", drawScreen.contains("DrawLastResult.from"))
        assertTrue("抽签应保留结果分享入口", drawSections.contains("""onClick = onExport"""))
        assertTrue("分享应从本轮结果生成导出内容", drawScreen.contains("onExport(DrawExportFactory.create(it))"))
    }

    @Test
    fun `导航不再从抽签页跳转分组`() {
        val nav = source("src/main/kotlin/com/furina/classtools/ui/navigation/MainNavHost.kt")

        assertFalse(nav.contains("drawGroupMembers"))
        assertFalse(nav.contains("onGroup ="))
        assertFalse("抽签页不应再 navigate 到分组", nav.contains("""Routes.tool("group")"""))
        assertTrue("分组页应接本机最近抽签存储", nav.contains("lastResultStore = container.drawLastResultStore"))
        assertTrue("分组页应接班级名单", nav.contains("rosterMembers = roster"))
    }

    @Test
    fun `分组页显示最近抽签与全班 结果摘要格式统一`() {
        val groupScreen = source("src/main/kotlin/com/furina/classtools/ui/screens/group/GroupScreen.kt")
        val groupModels = source("src/main/kotlin/com/furina/classtools/features/group/GroupModels.kt")

        assertTrue(groupScreen.contains("最近抽签"))
        assertTrue(groupScreen.contains("全班"))
        assertTrue("结果摘要应为「共 N 人 · M 组 · 每组 x–y 人」", groupScreen.contains("共 \${result.totalMembers} 人"))
        assertTrue(groupScreen.contains("每组 \$eachLabel 人"))
        assertTrue(groupModels.contains("""PREV("prev", "最近抽签")"""))
    }
}
