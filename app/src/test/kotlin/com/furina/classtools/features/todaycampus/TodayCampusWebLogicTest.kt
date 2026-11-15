package com.furina.classtools.features.todaycampus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 今日校园 / 融合门户 WebView 纯逻辑单测（2026-11-16 用户反馈：CAS 登录后 WebView 白屏）。
 *
 * 覆盖：
 * - 登录成功后应显式加载门户首页（不停在 /login2 空壳）；
 * - 白屏检测阈值/脚本（只读 innerText 长度）/结果解析/重载决策（最多 1 次，之后错误条）；
 * - 门户 URL 判定（首页 / 登录空壳）与重载目标 URL；
 * - 文案（浏览器打开 / 融合门户）。
 *
 * 全部为纯逻辑断言，不联网、不使用任何真实账号。
 */
class TodayCampusWebLogicTest {

    @Test
    fun `门户首页与域名常量`() {
        assertEquals("https://e.fjtcm.edu.cn/index.html", TodayCampusWebLogic.PORTAL_HOME_URL)
        assertEquals("e.fjtcm.edu.cn", TodayCampusWebLogic.PORTAL_HOST)
        assertTrue(TodayCampusWebLogic.isPortalHost(TodayCampusWebLogic.PORTAL_HOME_URL))
        assertTrue(TodayCampusWebLogic.isPortalHost("https://e.fjtcm.edu.cn/login2"))
        assertFalse(TodayCampusWebLogic.isPortalHost("https://authserver.fjtcm.edu.cn/authserver/login"))
        assertFalse(TodayCampusWebLogic.isPortalHost("file:///e.fjtcm.edu.cn/index.html"))
        assertFalse(TodayCampusWebLogic.isPortalHost(null))
    }

    @Test
    fun `门户首页与登录空壳页识别`() {
        assertTrue(TodayCampusWebLogic.isPortalHome("https://e.fjtcm.edu.cn/index.html"))
        assertTrue(TodayCampusWebLogic.isPortalHome("https://e.fjtcm.edu.cn/"))
        assertFalse(TodayCampusWebLogic.isPortalHome("https://e.fjtcm.edu.cn/login2"))
        assertTrue(TodayCampusWebLogic.isPortalLoginShell("https://e.fjtcm.edu.cn/login2"))
        assertTrue(TodayCampusWebLogic.isPortalLoginShell("https://e.fjtcm.edu.cn/login"))
        assertFalse(TodayCampusWebLogic.isPortalLoginShell("https://e.fjtcm.edu.cn/index.html"))
        assertFalse(TodayCampusWebLogic.isPortalLoginShell("https://authserver.fjtcm.edu.cn/authserver/login"))
    }

    @Test
    fun `登录成功后应显式加载门户首页`() {
        // /login2 空壳（CAS 登录回报后常见）→ 加载 index.html
        assertTrue(
            TodayCampusWebLogic.shouldLoadPortalHomeAfterLogin(
                "https://e.fjtcm.edu.cn/login2",
                alreadyLoadedHome = false,
            ),
        )
        // 已是首页 / 已加载过 / 非门户域 / 空 URL → 不再跳
        assertFalse(
            TodayCampusWebLogic.shouldLoadPortalHomeAfterLogin(
                "https://e.fjtcm.edu.cn/index.html",
                alreadyLoadedHome = false,
            ),
        )
        assertFalse(
            TodayCampusWebLogic.shouldLoadPortalHomeAfterLogin(
                "https://e.fjtcm.edu.cn/login2",
                alreadyLoadedHome = true,
            ),
        )
        assertFalse(
            TodayCampusWebLogic.shouldLoadPortalHomeAfterLogin(
                "https://authserver.fjtcm.edu.cn/authserver/login",
                alreadyLoadedHome = false,
            ),
        )
        assertFalse(
            TodayCampusWebLogic.shouldLoadPortalHomeAfterLogin(
                "https://example.com/login2",
                alreadyLoadedHome = false,
            ),
        )
        assertFalse(TodayCampusWebLogic.shouldLoadPortalHomeAfterLogin(null, alreadyLoadedHome = false))
    }

    @Test
    fun `白屏判定阈值`() {
        assertEquals(20, TodayCampusWebLogic.BLANK_TEXT_THRESHOLD)
        assertTrue(TodayCampusWebLogic.isBlankPageText(null))
        assertTrue(TodayCampusWebLogic.isBlankPageText(""))
        assertTrue(TodayCampusWebLogic.isBlankPageText("   \n\t "))
        assertTrue(TodayCampusWebLogic.isBlankPageText("加载中"))
        assertFalse(TodayCampusWebLogic.isBlankPageText("福建中医药大学融合门户 · 统一身份认证登录"))
        assertTrue(TodayCampusWebLogic.isBlankTextLength(0))
        assertTrue(TodayCampusWebLogic.isBlankTextLength(19))
        assertFalse(TodayCampusWebLogic.isBlankTextLength(20))
        assertFalse(TodayCampusWebLogic.isBlankTextLength(500))
    }

    @Test
    fun `白屏检测脚本只读且返回长度`() {
        val script = TodayCampusWebLogic.buildBlankCheckScript()
        assertTrue(script.contains("document.body"))
        assertTrue(script.contains("innerText"))
        assertTrue(script.contains("trim().length"))
        // 只读：不读写存储/不发请求/不改页面
        listOf("localStorage", "sessionStorage", "document.cookie", "fetch(", "XMLHttpRequest")
            .forEach { forbidden -> assertFalse("脚本不应包含 $forbidden", script.contains(forbidden)) }
    }

    @Test
    fun `解析空白检测回调长度`() {
        assertEquals(123, TodayCampusWebLogic.parseBlankTextLength("\"123\""))
        assertEquals(0, TodayCampusWebLogic.parseBlankTextLength("0"))
        assertEquals(45, TodayCampusWebLogic.parseBlankTextLength("45"))
        assertEquals(0, TodayCampusWebLogic.parseBlankTextLength(null))
        assertEquals(0, TodayCampusWebLogic.parseBlankTextLength(""))
        assertEquals(0, TodayCampusWebLogic.parseBlankTextLength("null"))
        assertEquals(0, TodayCampusWebLogic.parseBlankTextLength("not-a-number"))
    }

    @Test
    fun `空白处理最多自动重载一次后转错误条`() {
        assertEquals(TodayCampusWebLogic.BlankAction.RELOAD, TodayCampusWebLogic.blankAction(0))
        assertEquals(TodayCampusWebLogic.BlankAction.SHOW_ERROR, TodayCampusWebLogic.blankAction(1))
        assertEquals(TodayCampusWebLogic.BlankAction.SHOW_ERROR, TodayCampusWebLogic.blankAction(2))
        assertEquals(1, TodayCampusWebLogic.MAX_BLANK_RELOADS)
        assertEquals(500L, TodayCampusWebLogic.BLANK_CHECK_DELAY_MS)
        assertEquals(800L, TodayCampusWebLogic.BLANK_RECHECK_DELAY_MS)
    }

    @Test
    fun `空白重载优先回门户首页`() {
        assertEquals(
            TodayCampusWebLogic.PORTAL_HOME_URL,
            TodayCampusWebLogic.reloadUrlForBlank("https://e.fjtcm.edu.cn/login2"),
        )
        assertEquals(
            TodayCampusWebLogic.PORTAL_HOME_URL,
            TodayCampusWebLogic.reloadUrlForBlank("https://e.fjtcm.edu.cn/index.html"),
        )
        // 非门户域保持原 URL
        assertEquals(
            "https://authserver.fjtcm.edu.cn/authserver/login",
            TodayCampusWebLogic.reloadUrlForBlank("https://authserver.fjtcm.edu.cn/authserver/login"),
        )
        assertEquals("", TodayCampusWebLogic.reloadUrlForBlank(null))
    }

    @Test
    fun `白屏文案与降级按钮`() {
        assertTrue(TodayCampusWebLogic.MSG_BLANK.contains("空白"))
        assertTrue(TodayCampusWebLogic.MSG_BLANK.contains("浏览器"))
        assertTrue(TodayCampusWebLogic.MSG_BLANK_RETRYING.contains("重新加载"))
        assertTrue(TodayCampusWebLogic.MSG_PORTAL_HOME_LOADING.contains("门户首页"))
        assertEquals("浏览器打开", TodayCampusWebLogic.BTN_OPEN_BROWSER)
        assertEquals("融合门户", TodayCampusWebLogic.BTN_OPEN_PORTAL)
    }
}
