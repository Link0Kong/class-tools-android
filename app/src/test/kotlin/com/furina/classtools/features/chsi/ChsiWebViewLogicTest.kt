package com.furina.classtools.features.chsi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学信网 WebView + Cookie 逻辑单测（2026-11-16，参考 HFUT-Schedule 的 WebView+Cookie 模式）。
 *
 * 覆盖：
 * - 默认 UA = 标准 Chrome UA（不是系统 WebView UA），桌面/手机 UA 切换；
 * - Cookie 过期指令（按学信网域名，只清 chsi.com.cn；含 domain / host-only 两种归属）；
 * - 清理入口在卡片页/登录页可见、全屏官方页不可见；
 * - 页面存储清理脚本只清 localStorage/sessionStorage（不用全局 WebStorage）；
 * - 相关文案（不宣称已登录、不影响其它工具）。
 *
 * 全部为纯逻辑断言，不联网、不接触任何真实学信网账号/Cookie。
 */
class ChsiWebViewLogicTest {

    // ---- UA -----------------------------------------------------------------

    @Test
    fun `默认 UA 是标准 Chrome UA 而不是系统 WebView UA`() {
        val ua = ChsiWebViewLogic.defaultUserAgent()
        assertEquals(ChsiCatalog.MOBILE_UA, ua)
        assertTrue("应含 Chrome/：$ua", ua.contains("Chrome/"))
        assertTrue("应为 Android 移动 Chrome UA：$ua", ua.contains("Android") && ua.contains("Mobile"))
        // 系统 WebView UA 特征：`; wv`、`Version/4.0`、应用包名 —— 默认 UA 必须不含这些。
        assertFalse("不应带 wv 标记：$ua", ua.contains("; wv"))
        assertFalse("不应带 Version/4.0：$ua", ua.contains("Version/4.0"))
        assertFalse(ua.contains("com.furina.classtools"))
        assertTrue(ChsiWebViewLogic.isStandardChromeUserAgent(ua))
    }

    @Test
    fun `UA 切换返回桌面版与标准移动版`() {
        assertEquals(ChsiCatalog.DESKTOP_UA, ChsiWebViewLogic.userAgentFor(desktop = true))
        assertEquals(ChsiCatalog.MOBILE_UA, ChsiWebViewLogic.userAgentFor(desktop = false))
        val desktop = ChsiWebViewLogic.userAgentFor(desktop = true)
        assertTrue(desktop.contains("Windows NT") && desktop.contains("Chrome/"))
        assertFalse(desktop.contains("Mobile"))
    }

    @Test
    fun `标准 Chrome UA 判定排除系统 WebView UA`() {
        val webViewUa = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP2A.240905.003; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/116.0.0.0 " +
            "Mobile Safari/537.36 com.furina.classtools.debug/1.0"
        assertFalse(ChsiWebViewLogic.isStandardChromeUserAgent(webViewUa))
        assertFalse(ChsiWebViewLogic.isStandardChromeUserAgent(""))
        assertFalse(ChsiWebViewLogic.isStandardChromeUserAgent(null))
        assertFalse(ChsiWebViewLogic.isStandardChromeUserAgent("Mozilla/5.0 Safari/537.36"))
    }

    // ---- Cookie 域名 --------------------------------------------------------

    @Test
    fun `Cookie 域名覆盖学信网各子系统且都是 https`() {
        val urls = ChsiWebViewLogic.COOKIE_URLS
        assertTrue(urls.size >= 5)
        urls.forEach { url ->
            assertTrue("应是学信网域名且 https：$url", ChsiWebViewLogic.isChsiCookieUrl(url))
            assertTrue(url.startsWith("https://"))
        }
        assertTrue(urls.any { it.contains("my.chsi.com.cn") })
        assertTrue(urls.any { it.contains("account.chsi.com.cn") })
        assertTrue(urls.any { it.contains("www.chsi.com.cn") })
    }

    @Test
    fun `非学信网或非 https 的地址不参与 Cookie 清理`() {
        assertFalse(ChsiWebViewLogic.isChsiCookieUrl("http://my.chsi.com.cn"))
        assertFalse(ChsiWebViewLogic.isChsiCookieUrl("https://not-chsi.com"))
        assertFalse(ChsiWebViewLogic.isChsiCookieUrl("https://chsi.com.cn.evil.example"))
        assertFalse(ChsiWebViewLogic.isChsiCookieUrl(null))
        assertFalse(ChsiWebViewLogic.isChsiCookieUrl(""))
    }

    // ---- Cookie 过期指令 ----------------------------------------------------

    @Test
    fun `Cookie 头解析为按域名过期指令`() {
        val directives = ChsiWebViewLogic.expiredCookieDirectives(
            "JSESSIONID=abc123; CHSI_SESSION=zzz; other=1",
        )
        assertTrue("至少为 3 个 Cookie 各生成指令", directives.size >= 9)
        listOf("JSESSIONID", "CHSI_SESSION", "other").forEach { name ->
            assertTrue(
                "缺少 $name 的 domain 过期指令：$directives",
                directives.contains("$name=; Max-Age=0; Path=/; Domain=chsi.com.cn"),
            )
            // 前导点写法（历史 domain Cookie）也要覆盖
            assertTrue(
                "缺少 $name 的 .chsi.com.cn 过期指令：$directives",
                directives.contains("$name=; Max-Age=0; Path=/; Domain=.chsi.com.cn"),
            )
            // host-only Cookie
            assertTrue(directives.contains("$name=; Max-Age=0; Path=/"))
        }
    }

    @Test
    fun `Cookie 过期指令去重且忽略非法项`() {
        val directives = ChsiWebViewLogic.expiredCookieDirectives(
            "a=1; a=2; =bad; ; b=3; bad name=4; \$Version=1",
        )
        // 合法名 a / b（各 3 条 domain 变体），重复的 a 只处理一次、不产生 6 条。
        assertEquals(6, directives.size)
        assertEquals(3, directives.count { it.startsWith("a=") })
        assertFalse(directives.any { it.startsWith("bad name") })
        assertFalse(directives.any { it.startsWith("\$Version") })
    }

    @Test
    fun `空 Cookie 头不产生指令`() {
        assertTrue(ChsiWebViewLogic.expiredCookieDirectives(null).isEmpty())
        assertTrue(ChsiWebViewLogic.expiredCookieDirectives("").isEmpty())
        assertTrue(ChsiWebViewLogic.expiredCookieDirectives("   ").isEmpty())
        assertTrue(ChsiWebViewLogic.expiredCookieDirectives("; ; ").isEmpty())
    }

    @Test
    fun `过期指令域名参数会去掉前导点并回退默认值`() {
        val dot = ChsiWebViewLogic.expiredCookieDirectives("a=1", ".chsi.com.cn")
        assertTrue(dot.any { it.endsWith("Domain=chsi.com.cn") })
        assertTrue(dot.any { it.endsWith("Domain=.chsi.com.cn") })
        assertFalse("不应出现双点域名：$dot", dot.any { it.contains("Domain=..") })
        val blank = ChsiWebViewLogic.expiredCookieDirectives("a=1", "   ")
        assertTrue(blank.any { it.endsWith("Domain=chsi.com.cn") })
    }

    @Test
    fun `过期指令只包含学信网域不会误伤其它站点`() {
        val directives = ChsiWebViewLogic.expiredCookieDirectives("token=abc")
        directives.forEach { directive ->
            assertFalse("不应出现其它域名：$directive", directive.contains("fjtcm"))
            assertFalse("不应出现其它域名：$directive", directive.contains("jw."))
        }
        assertEquals(ChsiWebViewLogic.COOKIE_DOMAIN, "chsi.com.cn")
    }

    // ---- 清理入口可见性 / 存储清理 -----------------------------------------

    @Test
    fun `登录页与卡片页提供清除 Cookie 重登而全屏官方页不提供`() {
        assertTrue(ChsiWebViewLogic.shouldShowClearCookies(ChsiNavigationLogic.Mode.LOGIN))
        assertTrue(ChsiWebViewLogic.shouldShowClearCookies(ChsiNavigationLogic.Mode.CARDS))
        assertFalse(ChsiWebViewLogic.shouldShowClearCookies(ChsiNavigationLogic.Mode.WEB))
    }

    @Test
    fun `页面存储清理脚本只清当前源不使用全局 WebStorage`() {
        val js = ChsiWebViewLogic.CLEAR_PAGE_STORAGE_JS
        assertTrue(js.contains("localStorage.clear()"))
        assertTrue(js.contains("sessionStorage.clear()"))
        // 不触碰 Cookie、不发请求、不清理全局 WebStorage。
        assertFalse(js.contains("cookie"))
        assertFalse(js.contains("fetch("))
        assertFalse(js.contains("XMLHttpRequest"))
    }

    // ---- 文案 --------------------------------------------------------------

    @Test
    fun `清理 Cookie 文案明确只影响学信网`() {
        assertTrue(ChsiTexts.BTN_CLEAR_COOKIE_RELOGIN.contains("Cookie"))
        assertTrue(ChsiTexts.HINT_CLEAR_COOKIE_RELOGIN.contains("不影响其它工具"))
        assertTrue(ChsiTexts.CLEAR_COOKIE_CONFIRM_TEXT.contains("chsi.com.cn"))
        assertTrue(ChsiTexts.CLEAR_COOKIE_CONFIRM_TEXT.contains("不影响其它工具"))
        assertTrue(ChsiTexts.LOGOUT_CONFIRM_TEXT.contains("chsi.com.cn"))
        assertTrue(ChsiTexts.LOGOUT_CONFIRM_TEXT.contains("不影响"))
        // 提示文案不能宣称「已登录」。
        assertFalse(ChsiTexts.HINT_CLEAR_COOKIE_RELOGIN.contains("已登录"))
        assertTrue(ChsiTexts.MSG_COOKIE_CLEARED_RELOGIN.contains("重新"))
    }
}
