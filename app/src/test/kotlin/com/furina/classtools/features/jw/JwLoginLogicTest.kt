package com.furina.classtools.features.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 教务「直进 + 自动登录」纯逻辑单测（契约增量 55 §5.1/§5.3）。
 *
 * 覆盖（全部纯 JVM，无 Android/Robolectric）：
 * - 注入 JS 的字符串转义与脚本结构（脚本只返回状态 token，不打印账号密码）；
 * - URL / 跳转 allowlist（页类型、仅 http(s)、仅校内域、拒绝内嵌凭据）；
 * - 密码校验 / 账号掩码 / 注释身份 sid 匹配；
 * - evaluateJavascript 回调解析、注入/复核结果归一、状态文案与延迟常量；
 * - 本机凭据的 Base64 编解码。
 *
 * 红线：测试只用**假账号/假密码**，不接触任何真实教务账号（AGENTS §0-7）。
 */
class JwLoginLogicTest {

    // ---- 入口 URL / 页面分类 ------------------------------------------------

    @Test
    fun `入口 URL 与契约逐字一致且保持 HTTP`() {
        assertEquals("http://210.34.74.77/sso.jsp", JwLoginLogic.ENTRY_URL)
        assertTrue(JwLoginLogic.ENTRY_URL.startsWith("http://"))
        assertEquals("210.34.74.77", JwLoginLogic.JW_HOST)
        assertEquals("authserver.fjtcm.edu.cn", JwLoginLogic.AUTH_HOST)
    }

    @Test
    fun `authserver 登录页识别`() {
        assertTrue(
            JwLoginLogic.isAuthServerLoginUrl(
                "https://authserver.fjtcm.edu.cn/authserver/login" +
                    "?service=http%3A%2F%2F210.34.74.77%2Fsso.jsp",
            ),
        )
        assertTrue(JwLoginLogic.isAuthServerLoginUrl("http://authserver.fjtcm.edu.cn/authserver/login"))
        assertTrue(JwLoginLogic.isAuthServerLoginUrl("https://authserver.fjtcm.edu.cn/authserver/login?x=1#frag"))
        // 非登录页 / 非 authserver / 非 http(s) 都不算
        assertFalse(JwLoginLogic.isAuthServerLoginUrl("https://authserver.fjtcm.edu.cn/"))
        assertFalse(JwLoginLogic.isAuthServerLoginUrl("https://example.com/authserver/login"))
        assertFalse(JwLoginLogic.isAuthServerLoginUrl("http://210.34.74.77/authserver/login"))
        assertFalse(JwLoginLogic.isAuthServerLoginUrl("file:///authserver/login"))
        assertFalse(JwLoginLogic.isAuthServerLoginUrl(null))
        assertFalse(JwLoginLogic.isAuthServerLoginUrl(""))
    }

    @Test
    fun `进入教务系统识别`() {
        // 登录成功后回到 210.34.74.77 的页面
        assertTrue(JwLoginLogic.isJwEntered("http://210.34.74.77/index.jsp"))
        assertTrue(JwLoginLogic.isJwEntered("http://210.34.74.77/"))
        assertTrue(JwLoginLogic.isJwEntered("http://210.34.74.77/jwgl/main"))
        // sso.jsp 只是跳板，还没登录成功
        assertFalse(JwLoginLogic.isJwEntered("http://210.34.74.77/sso.jsp"))
        // 其它域不算
        assertFalse(JwLoginLogic.isJwEntered("https://authserver.fjtcm.edu.cn/authserver/login"))
        assertFalse(JwLoginLogic.isJwEntered("https://example.com/index.jsp"))
        assertFalse(JwLoginLogic.isJwEntered(null))
    }

    @Test
    fun `页面分类覆盖登录全链路`() {
        assertEquals(
            JwLoginLogic.PageKind.AUTH_LOGIN,
            JwLoginLogic.pageKind("https://authserver.fjtcm.edu.cn/authserver/login?service=x"),
        )
        assertEquals(
            JwLoginLogic.PageKind.JW_ENTRY,
            JwLoginLogic.pageKind("http://210.34.74.77/sso.jsp"),
        )
        assertEquals(
            JwLoginLogic.PageKind.JW_ENTERED,
            JwLoginLogic.pageKind("http://210.34.74.77/index.jsp"),
        )
        assertEquals(JwLoginLogic.PageKind.OTHER, JwLoginLogic.pageKind("https://example.com/"))
        assertEquals(JwLoginLogic.PageKind.OTHER, JwLoginLogic.pageKind(null))
        assertEquals(JwLoginLogic.PageKind.OTHER, JwLoginLogic.pageKind("javascript:alert(1)"))
        assertEquals(JwLoginLogic.PageKind.OTHER, JwLoginLogic.pageKind("不是 URL"))
    }

    // ---- 跳转 allowlist -----------------------------------------------------

    @Test
    fun `仅 http(s) 校内域允许在 WebView 内加载`() {
        listOf(
            "http://210.34.74.77/sso.jsp",
            "HTTP://210.34.74.77/sso.jsp",
            "http://210.34.74.77/index.jsp",
            "https://authserver.fjtcm.edu.cn/authserver/login",
            "https://fjtcm.edu.cn/",
            "https://jw.fjtcm.edu.cn/x",
        ).forEach { url ->
            assertTrue("应允许：$url", JwLoginLogic.isAllowedNavigation(url))
        }
    }

    @Test
    fun `校外与非 http(s) 跳转一律阻止`() {
        listOf(
            "https://example.com/",
            "http://example.com/",
            "https://notfjtcm.edu.cn/",
            "https://fjtcm.edu.cn.evil.com/",
            "file:///sdcard/a.html",
            "content://media/1",
            "intent://scan/#Intent;scheme=zxing;end",
            "market://details?id=x",
            "javascript:alert(1)",
            "data:text/html,<b>x</b>",
            "",
            "  ",
        ).forEach { url ->
            assertFalse("应阻止：$url", JwLoginLogic.isAllowedNavigation(url))
        }
        assertFalse(JwLoginLogic.isAllowedNavigation(null))
        // 校内 URL 里内嵌 userinfo 也不允许（防钓鱼/凭据泄漏）
        assertFalse(JwLoginLogic.isAllowedNavigation("http://user:pass@210.34.74.77/sso.jsp"))
        assertEquals("user:pass", JwLoginLogic.userInfoOf("http://user:pass@210.34.74.77/sso.jsp"))
        assertNull(JwLoginLogic.userInfoOf("http://210.34.74.77/sso.jsp"))
    }

    @Test
    fun `外部 http(s) 与校内可区分（用于交给系统浏览器）`() {
        assertTrue(JwLoginLogic.isHttpUrl("https://example.com/x"))
        assertFalse(JwLoginLogic.isAllowedNavigation("https://example.com/x"))
        assertFalse(JwLoginLogic.isHttpUrl("intent://x"))
        assertFalse(JwLoginLogic.isHttpUrl(null))
    }

    // ---- 注入 JS：转义 ------------------------------------------------------

    @Test
    fun `JS 字符串字面量基础转义`() {
        assertEquals("\"abc\"", JwLoginLogic.jsStringLiteral("abc"))
        assertEquals("\"密码\"", JwLoginLogic.jsStringLiteral("密码"))
        assertEquals("\"a\\\"b\"", JwLoginLogic.jsStringLiteral("a\"b"))
        assertEquals("\"a\\\\b\"", JwLoginLogic.jsStringLiteral("a\\b"))
        assertEquals("\"a\\'b\"", JwLoginLogic.jsStringLiteral("a'b"))
        assertEquals("\"a\\nb\"", JwLoginLogic.jsStringLiteral("a\nb"))
        assertEquals("\"a\\rb\"", JwLoginLogic.jsStringLiteral("a\rb"))
        assertEquals("\"a\\tb\"", JwLoginLogic.jsStringLiteral("a\tb"))
        assertEquals("\"a\\bb\"", JwLoginLogic.jsStringLiteral("a\bb"))
        assertEquals("\"\"", JwLoginLogic.jsStringLiteral(""))
    }

    @Test
    fun `JS 字符串字面量转义 HTML 与行终止符`() {
        assertEquals("\"\\u003c/script\\u003e\"", JwLoginLogic.jsStringLiteral("</script>"))
        assertEquals("\"a\\u0026b\"", JwLoginLogic.jsStringLiteral("a&b"))
        assertTrue(JwLoginLogic.jsStringLiteral("\u2028").contains("\\u2028"))
        assertTrue(JwLoginLogic.jsStringLiteral("\u2029").contains("\\u2029"))
        // 控制字符一律 \u00XX，不产生裸控制字符
        assertEquals("\"\\u0001\"", JwLoginLogic.jsStringLiteral("\u0001"))
        assertEquals("\"\\u007f\"", JwLoginLogic.jsStringLiteral("\u007F"))
        assertFalse(JwLoginLogic.jsStringLiteral("\u0000\u0001").contains('\u0000'))
    }

    @Test
    fun `JS 字符串转义不会被引号或换行打断`() {
        val tricky = "p\"w\\d\n</script>'"
        val literal = JwLoginLogic.jsStringLiteral(tricky)
        assertTrue(literal.startsWith("\""))
        assertTrue(literal.endsWith("\""))
        // 字面量内部的引号都已转义：除首尾外没有未转义的 "
        val inner = literal.substring(1, literal.length - 1)
        var i = 0
        var unescapedQuotes = 0
        while (i < inner.length) {
            if (inner[i] == '\\') {
                i += 2
                continue
            }
            if (inner[i] == '"') unescapedQuotes += 1
            i += 1
        }
        assertEquals(0, unescapedQuotes)
        assertFalse(inner.contains('\n'))
    }

    // ---- 注入 JS：脚本结构 --------------------------------------------------

    @Test
    fun `填充提交脚本结构正确且不打印账号密码`() {
        // 只用假账号/假密码
        val account = "9999000001"
        val password = "p\"w\\d</script>"
        val script = JwLoginLogic.buildFillAndSubmitScript(account, password)

        // 必需 DOM id（契约 §5.1：账号/可见密码/登录区/提交按钮）
        assertTrue(script.contains("'username'"))
        assertTrue(script.contains("'password'"))
        assertTrue(script.contains("'pwdLoginDiv'"))
        assertTrue(script.contains("'login_submit'"))
        // 填值 + 派发事件 + 点击提交
        assertTrue(script.contains("dispatchEvent"))
        assertTrue(script.contains("new Event('input'"))
        assertTrue(script.contains("new Event('change'"))
        assertTrue(script.contains("submit.click()"))
        assertTrue(script.contains("return 'submitted';"))
        // 账号/密码只以转义后的 JS 字面量出现
        assertTrue(script.contains(JwLoginLogic.jsStringLiteral(account)))
        assertTrue(script.contains(JwLoginLogic.jsStringLiteral(password)))
        assertFalse("脚本不得含密码原文", script.contains(password))
        // 不打印任何内容、不弹窗、不外发
        assertFalse(script.contains("console."))
        assertFalse(script.contains("alert("))
        assertFalse(script.contains("XMLHttpRequest"))
        assertFalse(script.contains("fetch("))
        assertFalse(script.contains("localStorage"))
        assertFalse(script.contains("document.cookie"))
    }

    @Test
    fun `复核脚本识别错误提示与验证码且不携带密码`() {
        val script = JwLoginLogic.buildResultCheckScript()
        assertTrue(script.contains("'showErrorTip'"))
        assertTrue(script.contains("'captchaDiv'"))
        assertTrue(script.contains("return 'error';"))
        assertTrue(script.contains("return 'captcha';"))
        assertTrue(script.contains("return 'wait';"))
        assertFalse(script.contains("console."))
        assertFalse(script.contains("password"))
    }

    // ---- 密码校验 / 账号掩码 -----------------------------------------------

    @Test
    fun `密码校验必填与长度上限`() {
        assertEquals(JwLoginLogic.MSG_PASSWORD_REQUIRED, JwLoginLogic.validatePassword(null))
        assertEquals(JwLoginLogic.MSG_PASSWORD_REQUIRED, JwLoginLogic.validatePassword(""))
        assertNull(JwLoginLogic.validatePassword("a"))
        assertNull(JwLoginLogic.validatePassword("a".repeat(JwLoginLogic.MAX_PASSWORD_LENGTH)))
        assertEquals(
            JwLoginLogic.MSG_PASSWORD_TOO_LONG,
            JwLoginLogic.validatePassword("a".repeat(JwLoginLogic.MAX_PASSWORD_LENGTH + 1)),
        )
        // 不裁剪：首尾空格是密码的一部分
        assertNull(JwLoginLogic.validatePassword(" a "))
    }

    @Test
    fun `账号掩码保留末四位且不暴露完整学号`() {
        assertEquals("••••••9999", JwLoginLogic.maskAccount("3260799999"))
        // 短账号全掩码，不保留可识别片段
        assertEquals("••••", JwLoginLogic.maskAccount("9999"))
        assertEquals("••", JwLoginLogic.maskAccount("12"))
        assertEquals("", JwLoginLogic.maskAccount(""))
        assertEquals("", JwLoginLogic.maskAccount(null))
        val masked = JwLoginLogic.maskAccount("3260799999")
        assertFalse(masked.contains("326079"))
    }

    @Test
    fun `凭据与当前身份 sid 绑定`() {
        assertTrue(JwLoginLogic.isCredentialCompatible("3260799999", "3260799999"))
        assertFalse(JwLoginLogic.isCredentialCompatible("3260799999", "3260799998"))
        assertFalse(JwLoginLogic.isCredentialCompatible(null, "3260799999"))
        assertFalse(JwLoginLogic.isCredentialCompatible("", ""))
        assertFalse(JwLoginLogic.isCredentialCompatible("3260799999", null))
    }

    // ---- evaluateJavascript 回调 / 结果归一 --------------------------------

    @Test
    fun `解析 evaluateJavascript 回调字符串`() {
        assertEquals("submitted", JwLoginLogic.parseJsStringResult("\"submitted\""))
        assertEquals("captcha", JwLoginLogic.parseJsStringResult("\"captcha\""))
        assertEquals("", JwLoginLogic.parseJsStringResult("\"\""))
        assertNull(JwLoginLogic.parseJsStringResult("null"))
        assertNull(JwLoginLogic.parseJsStringResult(null))
        assertNull(JwLoginLogic.parseJsStringResult("  "))
        assertEquals("a\"b", JwLoginLogic.parseJsStringResult("\"a\\\"b\""))
        assertEquals("a\\b", JwLoginLogic.parseJsStringResult("\"a\\\\b\""))
        assertEquals("中", JwLoginLogic.parseJsStringResult("\"\\u4e2d\""))
        assertEquals("a\nb", JwLoginLogic.parseJsStringResult("\"a\\nb\""))
        // 非 JSON 字符串原样返回（容错）
        assertEquals("submitted", JwLoginLogic.parseJsStringResult("submitted"))
    }

    @Test
    fun `注入与复核结果归一`() {
        assertEquals(JwLoginLogic.LoginOutcome.SUBMITTED, JwLoginLogic.loginOutcomeOf("submitted"))
        listOf(null, "", "no-form", "no-submit", "error", "hidden").forEach { token ->
            assertEquals(
                "token=$token",
                JwLoginLogic.LoginOutcome.NEED_MANUAL,
                JwLoginLogic.loginOutcomeOf(token),
            )
        }
        assertEquals(JwLoginLogic.VerifyOutcome.ERROR, JwLoginLogic.verifyOutcomeOf("error"))
        assertEquals(JwLoginLogic.VerifyOutcome.CAPTCHA, JwLoginLogic.verifyOutcomeOf("captcha"))
        assertEquals(JwLoginLogic.VerifyOutcome.WAIT, JwLoginLogic.verifyOutcomeOf("wait"))
        assertEquals(JwLoginLogic.VerifyOutcome.WAIT, JwLoginLogic.verifyOutcomeOf(null))
    }

    // ---- 文案 / 常量 --------------------------------------------------------

    @Test
    fun `状态与按钮文案符合契约口径`() {
        assertEquals("教务", JwLoginLogic.PAGE_TITLE)
        assertEquals("登录教务", JwLoginLogic.PROMPT_TITLE)
        assertEquals("清除登录", JwLoginLogic.BTN_CLEAR)
        assertTrue(JwLoginLogic.BTN_SAVE_LOGIN.contains("保存"))
        assertTrue(JwLoginLogic.BTN_OPEN_ONLY.contains("不保存"))
        assertTrue(JwLoginLogic.MSG_AUTO_LOGIN.isNotBlank())
        // 失败回退提示必须提到「验证码」「手动」（契约 §5.3-4）
        assertTrue(JwLoginLogic.MSG_NEED_MANUAL.contains("验证码"))
        assertTrue(JwLoginLogic.MSG_NEED_MANUAL.contains("手动"))
        // 保存提示必须明确仅本机 + 不上传
        assertTrue(JwLoginLogic.HINT_PASSWORD_LOCAL.contains("本机"))
        assertTrue(JwLoginLogic.HINT_PASSWORD_LOCAL.contains("上传"))
        assertTrue(JwLoginLogic.MSG_CLEARED.contains("清除"))
    }

    @Test
    fun `注入延迟与重试上限符合契约`() {
        assertEquals(300L, JwLoginLogic.INJECT_DELAY_MS)
        assertEquals(800L, JwLoginLogic.VERIFY_DELAY_MS)
        assertTrue(JwLoginLogic.MAX_INJECT_ATTEMPTS in 1..3)
        assertEquals(128, JwLoginLogic.MAX_PASSWORD_LENGTH)
    }

    // ---- 本机凭据 Base64 ------------------------------------------------

    @Test
    fun `Base64 编解码可往返且坏数据返回 null`() {
        val iv = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val encoded = JwLoginLogic.encodeBase64(iv)
        assertEquals("AQIDBAUGBwgJCgsM", encoded)
        assertTrue(JwLoginLogic.decodeBase64(encoded!!)!!.contentEquals(iv))
        assertEquals("", JwLoginLogic.encodeBase64(ByteArray(0)))
        assertNull(JwLoginLogic.decodeBase64("不是 base64!!!"))
        // 空串合法 Base64 → 空字节数组（不是 null）
        assertTrue(JwLoginLogic.decodeBase64("")!!.isEmpty())
    }
}
