package com.furina.classtools.data.todaycampus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「今日校园」入口目录纯逻辑单测。
 *
 * 覆盖：入口数量/唯一性/文案、官方地址常量（已核实）、URL allowlist、未知子页手动提示口径。
 * 红线：只用公开常量与假数据，不接触任何真实账号（AGENTS §0-7）。
 */
class TodayCampusCatalogTest {

    @Test
    fun `入口数量在需求区间 10 到 14`() {
        assertTrue("入口数应在 10–14：${TodayCampusCatalog.entries.size}", TodayCampusCatalog.entries.size in 10..14)
        assertEquals(12, TodayCampusCatalog.entries.size)
    }

    @Test
    fun `需求列出的服务全部存在`() {
        val names = TodayCampusCatalog.entries.map { it.name }.toSet()
        listOf(
            "学工服务",
            "奖助学金",
            "勤工助学",
            "请假申请",
            "困难认定",
            "宿舍服务",
            "心理健康",
            "一卡通",
            "课表成绩",
            "水电查询",
            "报修/失物",
            "融合门户",
        ).forEach { name -> assertTrue("缺少入口：$name", names.contains(name)) }
    }

    @Test
    fun `入口 id 名称唯一且文案非空`() {
        assertEquals(
            TodayCampusCatalog.entries.size,
            TodayCampusCatalog.entries.map { it.id }.distinct().size,
        )
        assertEquals(
            TodayCampusCatalog.entries.size,
            TodayCampusCatalog.entries.map { it.name }.distinct().size,
        )
        TodayCampusCatalog.entries.forEach { entry ->
            assertTrue("id 不应为空", entry.id.isNotBlank())
            assertTrue("名称不应为空", entry.name.isNotBlank())
            assertTrue("说明不应为空", entry.desc.isNotBlank())
            assertTrue("说明不应过长：${entry.desc}", entry.desc.length <= 16)
            assertTrue("关键词不应含空白：${entry.name}", entry.keywords.none { it.isBlank() })
        }
    }

    @Test
    fun `每个入口图标与颜色有效且不重复`() {
        assertEquals(
            TodayCampusCatalog.entries.size,
            TodayCampusCatalog.entries.map { it.glyph }.distinct().size,
        )
        assertEquals(
            TodayCampusCatalog.entries.size,
            TodayCampusCatalog.entries.map { it.colorArgb }.distinct().size,
        )
        TodayCampusCatalog.entries.forEach { entry ->
            // 0xFFRRGGBB：必须是不透明色（浅/深色都能直接当图标 tint）。
            assertTrue("颜色应为不透明 ARGB：${entry.id}", entry.colorArgb ushr 24 == 0xFFL)
            assertTrue("颜色应在 32 位范围内：${entry.id}", entry.colorArgb in 0xFF000000L..0xFFFFFFFFL)
        }
    }

    @Test
    fun `官方地址常量与已核实结论一致`() {
        assertEquals("e.fjtcm.edu.cn", TodayCampusCatalog.PORTAL_HOST)
        assertEquals("https://e.fjtcm.edu.cn", TodayCampusCatalog.PORTAL_BASE_URL)
        assertEquals("https://e.fjtcm.edu.cn/index.html", TodayCampusCatalog.PORTAL_HOME_URL)
        assertEquals("https://e.fjtcm.edu.cn/login2", TodayCampusCatalog.PORTAL_ENTRY_URL)
        assertEquals("authserver.fjtcm.edu.cn", TodayCampusCatalog.CAS_HOST)
        assertEquals("/authserver/login", TodayCampusCatalog.CAS_LOGIN_PATH)
        assertEquals("fjtcm.edu.cn", TodayCampusCatalog.SCHOOL_DOMAIN)
        assertEquals("com.wisedu.cpdaily", TodayCampusCatalog.APP_PACKAGE)
        // 门户登录入口是 https（不允许明文 http）。
        assertTrue(TodayCampusCatalog.PORTAL_ENTRY_URL.startsWith("https://"))
        assertTrue(TodayCampusCatalog.PORTAL_HOME_URL.startsWith("https://"))
    }

    @Test
    fun `所有入口打开地址都在 allowlist 内且不伪造业务 URL`() {
        // 未核实到门户子页：所有入口统一打开已核实的门户登录入口（不拼接/猜测业务 URL）。
        TodayCampusCatalog.entries.forEach { entry ->
            val url = TodayCampusCatalog.openUrlOf(entry)
            assertEquals("入口 ${entry.id} 不应打开其它 URL", TodayCampusCatalog.PORTAL_ENTRY_URL, url)
            assertTrue("入口 ${entry.id} 的 URL 不在 allowlist：$url", TodayCampusLoginLogic.isAllowedNavigation(url))
        }
        // 门户首页是已核实的公开页面（本工具不直接落地到 SPA 空壳，只作为已知常量/页面识别）。
        assertEquals("https://e.fjtcm.edu.cn/index.html", TodayCampusCatalog.PORTAL_HOME_URL)
        assertTrue(TodayCampusLoginLogic.isAllowedNavigation(TodayCampusCatalog.PORTAL_HOME_URL))
        assertTrue(TodayCampusLoginLogic.isPortalUrl(TodayCampusCatalog.PORTAL_HOME_URL))
    }

    @Test
    fun `未知子页入口给出一句话手动提示`() {
        TodayCampusCatalog.entries.forEach { entry ->
            val hint = TodayCampusCatalog.manualHintOf(entry)
            if (entry.id == TodayCampusCatalog.PORTAL_ENTRY_ID) {
                // 融合门户本身就是门户，不需要「手动打开」提示。
                assertNull("融合门户不应有手动提示", hint)
            } else {
                assertNotNull("入口 ${entry.id} 应有手动提示", hint)
                assertTrue("提示应包含服务名：$hint", hint!!.contains(entry.name))
                assertTrue("提示不写长段落：$hint", hint.length <= 30)
            }
        }
        assertNull(TodayCampusCatalog.manualHintOf(TodayCampusCatalog.portalEntry))
    }

    @Test
    fun `按 id 查找入口`() {
        val entry = TodayCampusCatalog.byId("dorm")
        assertNotNull(entry)
        assertEquals("宿舍服务", entry!!.name)
        assertNull(TodayCampusCatalog.byId("not-exist"))
        assertSame(TodayCampusCatalog.portalEntry, TodayCampusCatalog.byId(TodayCampusCatalog.PORTAL_ENTRY_ID))
    }

    @Test
    fun `页面文案非空且为短句`() {
        listOf(
            TodayCampusCatalog.PAGE_TITLE,
            TodayCampusCatalog.SUBTITLE,
            TodayCampusCatalog.SEARCH_PLACEHOLDER,
            TodayCampusCatalog.BTN_OPEN_APP,
            TodayCampusCatalog.BTN_PORTAL,
            TodayCampusCatalog.BTN_REFRESH,
            TodayCampusCatalog.BTN_RETRY,
            TodayCampusCatalog.BTN_KNOW,
            TodayCampusCatalog.BTN_CLEAR_SEARCH,
            TodayCampusCatalog.MSG_NO_MATCH_TITLE,
            TodayCampusCatalog.MSG_NO_MATCH_DESC,
        ).forEach { text ->
            assertTrue("文案不应为空", text.isNotBlank())
            assertTrue("文案应为短句：$text", text.length <= 24)
        }
        assertEquals("今日校园", TodayCampusCatalog.PAGE_TITLE)
    }
}
