package com.furina.classtools.data.todaycampus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「今日校园」CAS 自动登录纯逻辑单测。
 *
 * 覆盖：URL 解析 / allowlist、CAS 登录页与门户页识别、注入 JS 的字符串转义与脚本结构
 * （只返回状态 token、不打印账号密码、验证码不提交）、结果归一、密码可用性校验。
 *
 * 红线：只用假账号/假密码，不接触任何真实账号（AGENTS §0-7）。
 */
class TodayCampusLoginLogicTest {

    // ---- URL 解析与 allowlist ------------------------------------------------

    @Test
    fun `URL 解析工具处理非法输入`() {
        assertEquals("https", TodayCampusLoginLogic.schemeOf("https://e.fjtcm.edu.cn/index.html"))
        assertEquals("e.fjtcm.edu.cn", TodayCampusLoginLogic.hostOf("https://e.fjtcm.edu.cn:443/index.html"))
        assertEquals("/login2", TodayCampusLoginLogic.pathOf("https://e.fjtcm.edu.cn/login2?x=1#f"))
        assertNull(TodayCampusLoginLogic.hostOf(null))
        assertNull(TodayCampusLoginLogic.hostOf(""))
        assertNull(TodayCampusLoginLogic.pathOf("不是 URL"))
        assertEquals("u:p", TodayCampusLoginLogic.userInfoOf("https://u:p@e.fjtcm.edu.cn/"))
        assertNull(TodayCampusLoginLogic.userInfoOf("https://e.fjtcm.edu.cn/"))
    }

    @Test
    fun `校内域判定`() {
        assertTrue(TodayCampusLoginLogic.isSchoolHost("fjtcm.edu.cn"))
        assertTrue(TodayCampusLoginLogic.isSchoolHost("e.fjtcm.edu.cn"))
        assertTrue(TodayCampusLoginLogic.isSchoolHost("AUTHSERVER.FJTCM.EDU.CN"))
        assertTrue(TodayCampusLoginLogic.isSchoolHost("webvpn.fjtcm.edu.cn"))
        assertFalse(TodayCampusLoginLogic.isSchoolHost("example.com"))
        assertFalse(TodayCampusLoginLogic.isSchoolHost("fjtcm.edu.cn.evil.com"))
        assertFalse(TodayCampusLoginLogic.isSchoolHost("notfjtcm.edu.cn"))
        assertFalse(TodayCampusLoginLogic.isSchoolHost(null))
        assertFalse(TodayCampusLoginLogic.isSchoolHost(""))
    }

    @Test
    fun `仅 http 与校内域允许在 WebView 内加载`() {
        listOf(
            "https://e.fjtcm.edu.cn/",
            "https://e.fjtcm.edu.cn/index.html",
            "https://e.fjtcm.edu.cn/login2",
            "https://authserver.fjtcm.edu.cn/authserver/login?service=x",
            "http://e.fjtcm.edu.cn/login2",
            "https://fjtcm.edu.cn/",
            "https://webvpn.fjtcm.edu.cn:4433/login",
        ).forEach { url ->
            assertTrue("应允许：$url", TodayCampusLoginLogic.isAllowedNavigation(url))
        }
    }

    @Test
    fun `校外与非 http 跳转一律阻止`() {
        listOf(
            "https://example.com/",
            "https://fjtcm.edu.cn.evil.com/",
            "https://notfjtcm.edu.cn/",
            "https://u:p@e.fjtcm.edu.cn/",
            "file:///sdcard/a.html",
            "content://media/1",
            "intent://scan/#Intent;scheme=zxing;end",
            "market://details?id=x",
            "javascript:alert(1)",
            "data:text/html,<b>x</b>",
            "",
            "  ",
        ).forEach { url ->
            assertFalse("应阻止：$url", TodayCampusLoginLogic.isAllowedNavigation(url))
        }
        assertFalse(TodayCampusLoginLogic.isAllowedNavigation(null))
    }

    // ---- 页面识别 ------------------------------------------------------------

    @Test
    fun `CAS 登录页识别`() {
        assertTrue(
            TodayCampusLoginLogic.isCasLoginUrl(
                "https://authserver.fjtcm.edu.cn/authserver/login" +
                    "?service=https%3A%2F%2Fe.fjtcm.edu.cn%2Flogin2",
            ),
        )
        assertTrue(TodayCampusLoginLogic.isCasLoginUrl("http://authserver.fjtcm.edu.cn/authserver/login"))
        assertTrue(TodayCampusLoginLogic.isCasLoginUrl("https://authserver.fjtcm.edu.cn/authserver/login?x=1#f"))
        // 同一台服务器的门户主机路径也按登录页处理（学校 CAS 可能从该主机出页面）。
        assertTrue(TodayCampusLoginLogic.isCasLoginUrl("https://e.fjtcm.edu.cn/authserver/login?service=x"))
        // 非登录路径 / 校外 / 非 http(s) 都不算
        assertFalse(TodayCampusLoginLogic.isCasLoginUrl("https://authserver.fjtcm.edu.cn/"))
        assertFalse(TodayCampusLoginLogic.isCasLoginUrl("https://authserver.fjtcm.edu.cn/authserver/logout"))
        assertFalse(TodayCampusLoginLogic.isCasLoginUrl("https://example.com/authserver/login"))
        assertFalse(TodayCampusLoginLogic.isCasLoginUrl("file:///authserver/login"))
        assertFalse(TodayCampusLoginLogic.isCasLoginUrl(null))
        assertFalse(TodayCampusLoginLogic.isCasLoginUrl(""))
    }

    @Test
    fun `门户页识别与页面分类`() {
        assertTrue(TodayCampusLoginLogic.isPortalUrl("https://e.fjtcm.edu.cn/index.html"))
        assertTrue(TodayCampusLoginLogic.isPortalUrl("https://e.fjtcm.edu.cn/login2"))
        assertTrue(TodayCampusLoginLogic.isPortalUrl("https://e.fjtcm.edu.cn/"))
        assertFalse(TodayCampusLoginLogic.isPortalUrl("https://authserver.fjtcm.edu.cn/authserver/login"))
        assertFalse(TodayCampusLoginLogic.isPortalUrl("https://example.com/"))
        assertFalse(TodayCampusLoginLogic.isPortalUrl(null))

        assertEquals(
            TodayCampusLoginLogic.PageKind.AUTH_LOGIN,
            TodayCampusLoginLogic.pageKind("https://authserver.fjtcm.edu.cn/authserver/login?service=x"),
        )
        assertEquals(
            TodayCampusLoginLogic.PageKind.PORTAL,
            TodayCampusLoginLogic.pageKind("https://e.fjtcm.edu.cn/index.html"),
        )
        assertEquals(
            TodayCampusLoginLogic.PageKind.OTHER,
            TodayCampusLoginLogic.pageKind("https://example.com/"),
        )
        assertEquals(TodayCampusLoginLogic.PageKind.OTHER, TodayCampusLoginLogic.pageKind(null))
        assertEquals(TodayCampusLoginLogic.PageKind.OTHER, TodayCampusLoginLogic.pageKind("javascript:alert(1)"))
    }

    // ---- JS 字符串转义 -------------------------------------------------------

    @Test
    fun `JS 字符串字面量转义特殊字符`() {
        assertEquals("\"abc\"", TodayCampusLoginLogic.jsStringLiteral("abc"))
        assertEquals("\"中文学号\"", TodayCampusLoginLogic.jsStringLiteral("中文学号"))
        assertEquals("\"a\\\\b\"", TodayCampusLoginLogic.jsStringLiteral("a\\b"))
        assertEquals("\"a\\\"b\"", TodayCampusLoginLogic.jsStringLiteral("a\"b"))
        assertEquals("\"a\\'b\"", TodayCampusLoginLogic.jsStringLiteral("a'b"))
        assertEquals("\"a\\nb\"", TodayCampusLoginLogic.jsStringLiteral("a\nb"))
        assertEquals("\"a\\rb\"", TodayCampusLoginLogic.jsStringLiteral("a\rb"))
        assertEquals("\"a\\tb\"", TodayCampusLoginLogic.jsStringLiteral("a\tb"))
        assertEquals("\"\\u003c/script\\u003e\"", TodayCampusLoginLogic.jsStringLiteral("</script>"))
        assertEquals("\"\\u0026\"", TodayCampusLoginLogic.jsStringLiteral("&"))
        assertEquals("\"a\\u2028b\"", TodayCampusLoginLogic.jsStringLiteral("a\u2028b"))
        assertEquals("\"a\\u2029b\"", TodayCampusLoginLogic.jsStringLiteral("a\u2029b"))
    }

    // ---- 填充脚本 ------------------------------------------------------------

    @Test
    fun `填充脚本结构与状态 token`() {
        val script = TodayCampusLoginLogic.buildFillAndSubmitScript("2026000001", "fake-password")
        // 只操作学校 CAS 页面已知 DOM（与教务自动登录同一套 Apereo CAS 页面）。
        // 账号密码登录区用作用域查询，避免页面同时存在手机动态码/FIDO 表单时填错。
        assertTrue(script.contains("#pwdLoginDiv #username"))
        assertTrue(script.contains("#pwdLoginDiv #password"))
        assertTrue(script.contains("getElementById('username')"))
        assertTrue(script.contains("getElementById('password')"))
        assertTrue(script.contains("pwdLoginDiv"))
        assertTrue(script.contains("captchaDiv"))
        assertTrue(script.contains("sliderCaptchaDiv"))
        assertTrue(script.contains("dispatchEvent"))
        assertTrue(script.contains("#pwdLoginDiv #login_submit"))
        // 账号/密码只作为 JS 字符串字面量出现，脚本不打印它们。
        assertTrue(script.contains(TodayCampusLoginLogic.jsStringLiteral("2026000001")))
        assertTrue(script.contains(TodayCampusLoginLogic.jsStringLiteral("fake-password")))
        assertFalse(script.contains("console."))
        assertFalse(script.contains("document.cookie"))
        // 只返回状态 token。
        listOf("'submitted'", "'captcha'", "'no-form'", "'no-submit'", "'error'").forEach { token ->
            assertTrue("脚本缺少 token：$token", script.contains(token))
        }
    }

    @Test
    fun `填充脚本对特殊字符密码安全转义`() {
        val password = "p@ss\"word\n</script>\\"
        val script = TodayCampusLoginLogic.buildFillAndSubmitScript("s'id", password)
        // 明文里的标签/换行必须被转义，避免提前闭合脚本文本或破坏字符串。
        assertFalse(script.contains("</script>"))
        assertFalse(script.contains("p@ss\"word"))
        assertTrue(script.contains(TodayCampusLoginLogic.jsStringLiteral(password)))
        assertEquals("\"s\\'id\"", TodayCampusLoginLogic.jsStringLiteral("s'id"))
    }

    @Test
    fun `填充结果归一`() {
        assertEquals(TodayCampusLoginLogic.FillOutcome.SUBMITTED, TodayCampusLoginLogic.fillOutcomeOf("submitted"))
        assertEquals(TodayCampusLoginLogic.FillOutcome.CAPTCHA, TodayCampusLoginLogic.fillOutcomeOf("captcha"))
        assertEquals(TodayCampusLoginLogic.FillOutcome.NEED_MANUAL, TodayCampusLoginLogic.fillOutcomeOf("no-form"))
        assertEquals(TodayCampusLoginLogic.FillOutcome.NEED_MANUAL, TodayCampusLoginLogic.fillOutcomeOf("no-submit"))
        assertEquals(TodayCampusLoginLogic.FillOutcome.NEED_MANUAL, TodayCampusLoginLogic.fillOutcomeOf("error"))
        assertEquals(TodayCampusLoginLogic.FillOutcome.NEED_MANUAL, TodayCampusLoginLogic.fillOutcomeOf(null))
    }

    // ---- 复核脚本 ------------------------------------------------------------

    @Test
    fun `复核脚本只读提示是否为空并归一`() {
        val script = TodayCampusLoginLogic.buildResultCheckScript()
        assertTrue(script.contains("showErrorTip"))
        assertTrue(script.contains("showWarnTip"))
        assertTrue(script.contains("captchaDiv"))
        assertTrue(script.contains("sliderCaptchaDiv"))
        assertTrue(script.contains("'captcha'"))
        assertTrue(script.contains("'error'"))
        assertTrue(script.contains("'wait'"))
        // 只判断提示文本是否为空，不回传提示内容。
        assertFalse(script.contains("return tipText"))
        assertFalse(script.contains("console."))

        assertEquals(TodayCampusLoginLogic.VerifyOutcome.ERROR, TodayCampusLoginLogic.verifyOutcomeOf("error"))
        assertEquals(TodayCampusLoginLogic.VerifyOutcome.CAPTCHA, TodayCampusLoginLogic.verifyOutcomeOf("captcha"))
        assertEquals(TodayCampusLoginLogic.VerifyOutcome.WAIT, TodayCampusLoginLogic.verifyOutcomeOf("wait"))
        assertEquals(TodayCampusLoginLogic.VerifyOutcome.WAIT, TodayCampusLoginLogic.verifyOutcomeOf(null))
    }

    @Test
    fun `evaluateJavascript 回调解析`() {
        assertEquals("submitted", TodayCampusLoginLogic.parseJsStringResult("\"submitted\""))
        assertEquals("submitted", TodayCampusLoginLogic.parseJsStringResult("submitted"))
        assertEquals("中文", TodayCampusLoginLogic.parseJsStringResult("\"\\u4e2d\\u6587\""))
        assertEquals("a\nb", TodayCampusLoginLogic.parseJsStringResult("\"a\\nb\""))
        assertNull(TodayCampusLoginLogic.parseJsStringResult(null))
        assertNull(TodayCampusLoginLogic.parseJsStringResult(""))
        assertNull(TodayCampusLoginLogic.parseJsStringResult("null"))
    }

    // ---- 账号密码可用性 ------------------------------------------------------

    @Test
    fun `自动填充可用性校验`() {
        assertEquals(
            TodayCampusLoginLogic.MSG_NO_ACCOUNT,
            TodayCampusLoginLogic.validateAutomation("", "fake-password"),
        )
        assertEquals(
            TodayCampusLoginLogic.MSG_NO_ACCOUNT,
            TodayCampusLoginLogic.validateAutomation(null, "fake-password"),
        )
        assertEquals(
            TodayCampusLoginLogic.MSG_NO_PASSWORD,
            TodayCampusLoginLogic.validateAutomation("2026000001", null),
        )
        assertEquals(
            TodayCampusLoginLogic.MSG_NO_PASSWORD,
            TodayCampusLoginLogic.validateAutomation("2026000001", ""),
        )
        assertNull(TodayCampusLoginLogic.validateAutomation("2026000001", "fake-password"))
        // 超长视为异常输入，不自动提交（回退人工）。
        val tooLong = "p".repeat(TodayCampusLoginLogic.MAX_PASSWORD_LENGTH + 1)
        assertEquals(
            TodayCampusLoginLogic.MSG_NEED_MANUAL,
            TodayCampusLoginLogic.validateAutomation("2026000001", tooLong),
        )
        val maxLength = "p".repeat(TodayCampusLoginLogic.MAX_PASSWORD_LENGTH)
        assertNull(TodayCampusLoginLogic.validateAutomation("2026000001", maxLength))
    }

    @Test
    fun `自动登录策略常量与文案`() {
        // 只自动尝试 1 次，失败/验证码立即回退人工（避免反复错误提交触发 CAS 风控）。
        assertEquals(1, TodayCampusLoginLogic.MAX_INJECT_ATTEMPTS)
        assertTrue(TodayCampusLoginLogic.INJECT_DELAY_MS in 100L..1000L)
        assertTrue(TodayCampusLoginLogic.VERIFY_DELAY_MS in 300L..3000L)
        listOf(
            TodayCampusLoginLogic.MSG_OPENING,
            TodayCampusLoginLogic.MSG_AUTO_LOGIN,
            TodayCampusLoginLogic.MSG_ENTERED,
            TodayCampusLoginLogic.MSG_NEED_MANUAL,
            TodayCampusLoginLogic.MSG_CAPTCHA,
            TodayCampusLoginLogic.MSG_NO_PASSWORD,
            TodayCampusLoginLogic.MSG_NO_ACCOUNT,
            TodayCampusLoginLogic.MSG_LOAD_FAILED,
        ).forEach { message ->
            assertTrue("状态文案不应为空", message.isNotBlank())
            assertNotNull(message)
        }
    }
}
