package com.furina.classtools.features.jw

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 教务 WebVPN 入口纯逻辑单测（2026-11-15 校外先登录学校 WebVPN）。
 *
 * 调研证据（本机实测 2026-11-15）：
 * - `https://webvpn.fjtcm.edu.cn:4433/login` HTTP 200，标题「福建中医药大学WEBVPN」；
 * - 官方文档：<https://metc.fjtcm.edu.cn/2023/0517/c1095a65136/page.htm>；
 * - 登录页 DOM：`#user_name` / `#form input[name=password]` / `#login`（密码由页面自身加密）。
 *
 * 未验证项（红线）：真实账号登录后的门户/教务包装 URL 无法在测试中确认，因此实现不猜加密 URL，
 * 而在门户页注入只读 JS 点「本科教务」资源；找不到回退用户手动点。
 *
 * 全部为纯逻辑/假实现断言，不使用任何真实教务账号或密码。
 */
class JwWebVpnTest {

    // ---- 入口与路由决策 -----------------------------------------------------

    @Test
    fun `WebVPN 入口常量与官方域名端口一致`() {
        assertEquals("webvpn.fjtcm.edu.cn", JwWebVpn.HOST)
        assertEquals(4433, JwWebVpn.PORT)
        assertEquals("https://webvpn.fjtcm.edu.cn:4433", JwWebVpn.BASE_URL)
        assertEquals("https://webvpn.fjtcm.edu.cn:4433/login", JwWebVpn.LOGIN_URL)
        assertEquals("https://webvpn.fjtcm.edu.cn:4433/", JwWebVpn.HOME_URL)
        assertEquals(2500L, JwWebVpn.PROBE_TIMEOUT_MS)
        assertEquals(80, JwWebVpn.JW_PROBE_PORT)
    }

    @Test
    fun `自动探测结果决定入口 直连可达走直连 否则走 WebVPN`() {
        assertEquals(JwWebVpn.Route.DIRECT, JwWebVpn.routeFor(directReachable = true))
        assertEquals(JwWebVpn.Route.WEBVPN, JwWebVpn.routeFor(directReachable = false))
    }

    @Test
    fun `用户手动选择优先于自动探测`() {
        assertEquals(
            JwWebVpn.Route.WEBVPN,
            JwWebVpn.routeFor(directReachable = true, userOverride = JwWebVpn.Route.WEBVPN),
        )
        assertEquals(
            JwWebVpn.Route.DIRECT,
            JwWebVpn.routeFor(directReachable = false, userOverride = JwWebVpn.Route.DIRECT),
        )
    }

    @Test
    fun `直连探测使用短超时并回退为不可达`() = runBlocking {
        var seenHost = ""
        var seenPort = -1
        var seenTimeout = -1L
        val ok = JwNetworkDetector.directReachable { host, port, timeout ->
            seenHost = host
            seenPort = port
            seenTimeout = timeout
            true
        }
        assertTrue(ok)
        assertEquals("210.34.74.77", seenHost)
        assertEquals(80, seenPort)
        assertEquals(JwWebVpn.PROBE_TIMEOUT_MS, seenTimeout)

        val blocked = JwNetworkDetector.directReachable { _, _, _ -> throw IllegalStateException("timeout") }
        assertFalse(blocked)
        val refused = JwNetworkDetector.directReachable { _, _, _ -> false }
        assertFalse(refused)
    }

    // ---- URL 判定 -----------------------------------------------------------

    @Test
    fun `WebVPN 登录页与门户页识别`() {
        assertTrue(JwWebVpn.isWebVpnLoginUrl("https://webvpn.fjtcm.edu.cn:4433/login"))
        assertTrue(JwWebVpn.isWebVpnLoginUrl("https://webvpn.fjtcm.edu.cn/login?x=1"))
        assertTrue(JwWebVpn.isWebVpnLoginUrl("https://webvpn.fjtcm.edu.cn:4433/login/"))
        assertFalse(JwWebVpn.isWebVpnLoginUrl("https://webvpn.fjtcm.edu.cn:4433/"))
        assertFalse(JwWebVpn.isWebVpnLoginUrl("https://example.com/login"))
        assertFalse(JwWebVpn.isWebVpnLoginUrl("http://210.34.74.77/sso.jsp"))

        assertTrue(JwWebVpn.isWebVpnPortalUrl("https://webvpn.fjtcm.edu.cn:4433/"))
        assertTrue(JwWebVpn.isWebVpnPortalUrl("https://webvpn.fjtcm.edu.cn:4433/portal"))
        assertFalse(JwWebVpn.isWebVpnPortalUrl("https://webvpn.fjtcm.edu.cn:4433/login"))
        assertFalse(JwWebVpn.isWebVpnPortalUrl("https://example.com/"))
    }

    @Test
    fun `网关包装 URL 识别 支持 http https 与端口前缀`() {
        val wrappedAuth = "https://webvpn.fjtcm.edu.cn:4433/https/77726476706e69737468656265737421/authserver/login?service=x"
        val wrappedEntry = "https://webvpn.fjtcm.edu.cn:4433/http/77726476706e69737468656265737421/sso.jsp"
        val wrappedJw = "https://webvpn.fjtcm.edu.cn:4433/http-8080/77726476706e69737468656265737421/jwglxt/xtgl/index.html"
        assertTrue(JwWebVpn.isWrappedUrl(wrappedAuth))
        assertTrue(JwWebVpn.isWrappedUrl(wrappedEntry))
        assertTrue(JwWebVpn.isWrappedUrl(wrappedJw))
        assertTrue(JwWebVpn.isWrappedAuthServerLoginUrl(wrappedAuth))
        assertTrue(JwWebVpn.isWrappedJwEntryUrl(wrappedEntry))
        assertTrue(JwWebVpn.isWrappedJwEnteredUrl(wrappedJw))
        // 登录页/sso 跳板不算「已进入教务」。
        assertFalse(JwWebVpn.isWrappedJwEnteredUrl(wrappedAuth))
        assertFalse(JwWebVpn.isWrappedJwEnteredUrl(wrappedEntry))
        // 直连教务域不算包装 URL；非 http 不算。
        assertFalse(JwWebVpn.isWrappedUrl("http://210.34.74.77/jwglxt/xtgl/index.html"))
        assertFalse(JwWebVpn.isWrappedUrl("file:///https/x/authserver/login"))
        assertFalse(JwWebVpn.isWrappedUrl(null))
    }

    @Test
    fun `页面分类接入 WebVPN 全链路`() {
        assertEquals(
            JwLoginLogic.PageKind.WEBVPN_LOGIN,
            JwLoginLogic.pageKind("https://webvpn.fjtcm.edu.cn:4433/login"),
        )
        assertEquals(
            JwLoginLogic.PageKind.WEBVPN_PORTAL,
            JwLoginLogic.pageKind("https://webvpn.fjtcm.edu.cn:4433/"),
        )
        // 包装后的 CAS 登录页按 AUTH_LOGIN 处理（DOM 与直连一致，自动填充复用）。
        assertEquals(
            JwLoginLogic.PageKind.AUTH_LOGIN,
            JwLoginLogic.pageKind(
                "https://webvpn.fjtcm.edu.cn:4433/https/77726476706e69737468656265737421/authserver/login",
            ),
        )
        assertEquals(
            JwLoginLogic.PageKind.WEBVPN_ENTERED,
            JwLoginLogic.pageKind(
                "https://webvpn.fjtcm.edu.cn:4433/http/77726476706e69737468656265737421/jwglxt/xtgl/index.html",
            ),
        )
        // WebVPN 主机属于 fjtcm.edu.cn 子域，内置 WebView allowlist 直接放行（无需改 allowlist）。
        assertTrue(
            JwLoginLogic.isAllowedNavigation("https://webvpn.fjtcm.edu.cn:4433/login"),
        )
    }

    // ---- 注入脚本 -----------------------------------------------------------

    @Test
    fun `WebVPN 填充脚本填写官方表单字段且只返回状态 token`() {
        val account = "9999000001"
        val password = "fake-p@ss\"word"
        val script = JwWebVpn.buildFillAndSubmitScript(account, password)
        assertTrue(script.contains("getElementById('user_name')"))
        assertTrue(script.contains("input[name=\"password\"]"))
        assertTrue(script.contains("getElementById('login')"))
        assertTrue(script.contains("submit.click()"))
        assertTrue(script.contains("'submitted'"))
        // 账号密码只作为 JS 字面量写入官方页面输入框；不打印、不存储、不发请求。
        listOf("console.", "localStorage", "sessionStorage", "document.cookie", "fetch(", "XMLHttpRequest")
            .forEach { forbidden -> assertFalse("脚本不应包含 $forbidden", script.contains(forbidden)) }
        // 双向转义后不提前闭合字符串（密码里的双引号被转义）。
        assertTrue(script.contains("fake-p@ss\\\"word"))
        assertEquals(JwWebVpn.LoginOutcome.SUBMITTED, JwWebVpn.loginOutcomeOf("submitted"))
        assertEquals(JwWebVpn.LoginOutcome.NEED_MANUAL, JwWebVpn.loginOutcomeOf("no-form"))
        assertEquals(JwWebVpn.LoginOutcome.NEED_MANUAL, JwWebVpn.loginOutcomeOf(null))
    }

    @Test
    fun `WebVPN 复核脚本识别二次验证与错误提示`() {
        val script = JwWebVpn.buildResultCheckScript()
        assertTrue(script.contains("second_login_layer"))
        assertTrue(script.contains("second_login_layer_totp"))
        assertTrue(script.contains("error-message"))
        assertTrue(script.contains("logged-in"))
        assertEquals(JwWebVpn.VerifyOutcome.TWO_STEP, JwWebVpn.verifyOutcomeOf("two-step"))
        assertEquals(JwWebVpn.VerifyOutcome.ERROR, JwWebVpn.verifyOutcomeOf("error"))
        assertEquals(JwWebVpn.VerifyOutcome.LOGGED_IN, JwWebVpn.verifyOutcomeOf("logged-in"))
        assertEquals(JwWebVpn.VerifyOutcome.WAIT, JwWebVpn.verifyOutcomeOf("wait"))
        assertEquals(JwWebVpn.VerifyOutcome.WAIT, JwWebVpn.verifyOutcomeOf(null))
    }

    @Test
    fun `门户跳转脚本多策略扫描教务资源且不做外部跳转`() {
        val script = JwWebVpn.buildPortalJumpScript()
        // 多选择器：a[href] + data-url/data-href/onclick/li/资源卡片容器
        listOf("a[href]", "[data-url]", "[data-href]", "[onclick]", ".resource-item", ".app-item")
            .forEach { selector -> assertTrue("应包含选择器 $selector", script.contains(selector)) }
        listOf("本科教务", "教务系统", "教务管理", "教学管理", "学生端", "210.34.74.77")
            .forEach { keyword -> assertTrue("应包含资源关键字 $keyword", script.contains(keyword)) }
        assertTrue(script.contains("target.click()"))
        assertTrue(script.contains("dispatchEvent"))
        // 只读页面结构：不读写 cookie/localStorage、不发请求、不 window.open。
        listOf("document.cookie", "localStorage", "sessionStorage", "fetch(", "XMLHttpRequest", "window.open")
            .forEach { forbidden -> assertFalse("脚本不应包含 $forbidden", script.contains(forbidden)) }
        assertEquals(JwWebVpn.PortalJumpOutcome.CLICKED, JwWebVpn.portalJumpOutcomeOf("clicked"))
        assertEquals(JwWebVpn.PortalJumpOutcome.NEED_MANUAL, JwWebVpn.portalJumpOutcomeOf("no-link"))
        assertEquals(JwWebVpn.PortalJumpOutcome.NEED_MANUAL, JwWebVpn.portalJumpOutcomeOf("error"))
        assertEquals(JwWebVpn.PortalJumpOutcome.NEED_MANUAL, JwWebVpn.portalJumpOutcomeOf(null))
    }

    @Test
    fun `登录成功探针识别门户 登录表单 二次验证 错误`() {
        val script = JwWebVpn.buildPortalProbeScript()
        assertTrue(script.contains("second_login_layer"))
        assertTrue(script.contains("user_name"))
        assertTrue(script.contains("error-message"))
        listOf("资源", "最近访问", "我的收藏", "退出").forEach { marker ->
            assertTrue("探针应包含门户特征 $marker", script.contains(marker))
        }
        // 只读：不读写存储/不发请求。
        listOf("document.cookie", "localStorage", "fetch(", "XMLHttpRequest")
            .forEach { forbidden -> assertFalse("探针不应包含 $forbidden", script.contains(forbidden)) }
        assertEquals(JwWebVpn.PortalProbeOutcome.PORTAL, JwWebVpn.portalProbeOutcomeOf("portal"))
        assertEquals(JwWebVpn.PortalProbeOutcome.LOGIN_FORM, JwWebVpn.portalProbeOutcomeOf("login-form"))
        assertEquals(JwWebVpn.PortalProbeOutcome.TWO_STEP, JwWebVpn.portalProbeOutcomeOf("two-step"))
        assertEquals(JwWebVpn.PortalProbeOutcome.ERROR, JwWebVpn.portalProbeOutcomeOf("error"))
        assertEquals(JwWebVpn.PortalProbeOutcome.UNKNOWN, JwWebVpn.portalProbeOutcomeOf("unknown"))
        assertEquals(JwWebVpn.PortalProbeOutcome.UNKNOWN, JwWebVpn.portalProbeOutcomeOf(null))
    }

    @Test
    fun `手动门户入口文案`() {
        assertEquals("打开 WebVPN 门户", JwWebVpn.BTN_OPEN_PORTAL)
        assertTrue(JwWebVpn.MSG_PORTAL_MANUAL.contains("本科教务"))
        assertTrue(JwWebVpn.MSG_PORTAL_MANUAL.contains("手动"))
    }

    @Test
    fun `WebVPN 填写与提交拆成两步脚本`() {
        val fill = JwWebVpn.buildFillScript("2024000001", "fake-pwd")
        assertTrue(fill.contains("getElementById('user_name')"))
        assertTrue(fill.contains("input[name=\\\"password\\\"]") || fill.contains("input[name=\"password\"]"))
        assertTrue(fill.contains("'filled'"))
        assertFalse("填写脚本不应提交", fill.contains("submit.click"))
        assertEquals(JwWebVpn.FillOutcome.FILLED, JwWebVpn.fillOutcomeOf("filled"))
        assertEquals(JwWebVpn.FillOutcome.NEED_MANUAL, JwWebVpn.fillOutcomeOf(null))

        val submit = JwWebVpn.buildSubmitScript()
        assertTrue(submit.contains("getElementById('login')"))
        assertTrue(submit.contains("'submitted'"))
        assertFalse(submit.contains("fake-pwd"))
    }

    // ---- 文案 ---------------------------------------------------------------

    @Test
    fun `校外 WebVPN 状态文案齐全`() {
        assertTrue(JwLoginLogic.MSG_NETWORK_PROBING.contains("WebVPN") || JwLoginLogic.MSG_NETWORK_PROBING.contains("检测"))
        assertTrue(JwLoginLogic.MSG_DIRECT_UNAVAILABLE.contains("WebVPN"))
        assertTrue(JwLoginLogic.MSG_WEBVPN_NEED_MANUAL.contains("手动"))
        assertTrue(JwLoginLogic.MSG_WEBVPN_TWO_STEP.contains("二次验证"))
        assertTrue(JwLoginLogic.MSG_WEBVPN_CLICK_MANUAL.contains("教务"))
        assertTrue(JwLoginLogic.MSG_WEBVPN_CARDS_UNAVAILABLE.contains("校园网"))
        assertEquals("直接打开教务", JwLoginLogic.BTN_DIRECT)
        assertEquals("走 WebVPN", JwLoginLogic.BTN_WEBVPN)
    }
}
