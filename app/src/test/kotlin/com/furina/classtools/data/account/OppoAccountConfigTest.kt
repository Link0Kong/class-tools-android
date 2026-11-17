package com.furina.classtools.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OPPO 账号服务纯逻辑单测：配置解析、设备支持、state/authCode 校验、绑定文案、
 * 授权结果解析。全部 JVM 单测，不依赖真机/真实 AppID/AppKey/ColorOS。
 */
class OppoAccountConfigTest {

    // ------------------------------------------------------------ 配置

    @Test
    fun `config of trims and defaults`() {
        val config = OppoAccountConfig.of("  appid  ", "  appkey ", " ", " ")
        assertEquals("appid", config.appId)
        assertEquals("appkey", config.appKey)
        assertEquals(OppoAccountConfig.DEFAULT_SCOPE, config.scope)
        assertEquals(OppoAccountConfig.DEFAULT_BASE_URL, config.baseUrl)
        assertTrue(config.isConfigured)
        assertEquals("openid", config.effectiveScope)
        assertEquals("http://121.196.47.2/account", config.normalizedBaseUrl)
    }

    @Test
    fun `config blank appId or appKey means not configured`() {
        assertFalse(OppoAccountConfig.of("", "k", null, null).isConfigured)
        assertFalse(OppoAccountConfig.of("id", "", null, null).isConfigured)
        assertFalse(OppoAccountConfig.of(null, null, null, null).isConfigured)
    }

    @Test
    fun `config normalizes baseUrl trailing slash`() {
        val config = OppoAccountConfig.of("a", "b", "openid profile", "http://1.2.3.4/account/")
        assertEquals("http://1.2.3.4/account", config.normalizedBaseUrl)
        assertEquals("openid profile", config.effectiveScope)
    }

    @Test
    fun `config default baseUrl constant matches contract`() {
        assertEquals("http://121.196.47.2/account", OppoAccountConfig.DEFAULT_BASE_URL)
    }

    // ------------------------------------------------------------ 设备支持

    @Test
    fun `device support 只认 ColorOS ROM 属性`() {
        assertTrue(OppoAccountDeviceSupport.isSupported(hasColorOsRom = true))
        assertFalse(OppoAccountDeviceSupport.isSupported(hasColorOsRom = false))
    }

    @Test
    fun `device support OPPO 品牌但无 ColorOS 属性 不支持`() {
        // 用户 2026-11-17 晚反馈：入口只应在 ColorOS 手机出现，不能仅凭品牌名判断。
        assertFalse(OppoAccountDeviceSupport.isSupported(hasColorOsRom = false))
        assertTrue(OppoAccountDeviceSupport.COLOR_OS_ROM_PROPERTIES.isNotEmpty())
        assertTrue(OppoAccountDeviceSupport.COLOR_OS_ROM_PROPERTIES.any { it == "ro.build.version.oplusrom" })
        assertTrue(OppoAccountDeviceSupport.COLOR_OS_ROM_PROPERTIES.any { it == "ro.build.version.opporom" })
        assertTrue(OppoAccountDeviceSupport.COLOR_OS_ROM_PROPERTIES.any { it == "ro.build.version.oplusos" })
    }

    @Test
    fun `device support 非 ColorOS 一律不支持`() {
        // 非 ColorOS（华为/荣耀/其他）与模拟器都按 false 处理（严格 ColorOS 口径）。
        assertFalse(OppoAccountDeviceSupport.isSupported(hasColorOsRom = false))
    }

    // ------------------------------------------------------------ OAuth state / authCode

    @Test
    fun `oauth state is 32 hex chars and valid`() {
        val state = OppoOAuthState.random()
        assertEquals(32, state.length)
        assertTrue(state.all { it in "0123456789abcdef" })
        assertTrue(OppoOAuthState.isValid(state))
        assertNotNull(OppoOAuthState.random().takeIf { it != state })
    }

    @Test
    fun `oauth state validation`() {
        assertFalse(OppoOAuthState.isValid(null))
        assertFalse(OppoOAuthState.isValid(""))
        assertFalse(OppoOAuthState.isValid("ABC"))
        assertFalse(OppoOAuthState.isValid("z".repeat(32)))
        assertTrue(OppoOAuthState.isValid("a".repeat(32)))
        assertFalse(OppoOAuthState.isValid("a".repeat(15)))
    }

    @Test
    fun `auth code normalize trims and rejects bad input`() {
        assertEquals("abc-123", OppoAuthCode.normalize("  abc-123  "))
        assertNull(OppoAuthCode.normalize(null))
        assertNull(OppoAuthCode.normalize("   "))
        assertNull(OppoAuthCode.normalize("abc def"))
        assertNull(OppoAuthCode.normalize("abc\tdef"))
        assertNull(OppoAuthCode.normalize("a".repeat(2049)))
        assertNotNull(OppoAuthCode.normalize("a".repeat(2048)))
    }

    @Test
    fun `oauth request build from config carries appId appKey scope and state`() {
        val config = OppoAccountConfig.of("aid", "akey", "openid profile", null)
        val request = OppoOAuthRequest.of(config, "a".repeat(32))
        assertEquals("aid", request.appId)
        assertEquals("akey", request.appKey)
        assertEquals("openid profile", request.scope)
        assertEquals("a".repeat(32), request.state)
        assertTrue(request.isValid)
    }

    @Test
    fun `oauth request invalid when any required field blank`() {
        val valid = OppoOAuthRequest("aid", "akey", "openid", "a".repeat(32))
        assertTrue(valid.isValid)
        assertFalse(valid.copy(appId = "").isValid)
        assertFalse(valid.copy(appKey = "").isValid)
        assertFalse(valid.copy(scope = "").isValid)
        assertFalse(valid.copy(state = "").isValid)
        assertFalse(valid.copy(state = "XYZ").isValid)
    }

    // ------------------------------------------------------------ 授权回调解析

    @Test
    fun `oauth callback parse success returns normalized auth code`() {
        val result = OppoOAuthResultParser.parse(
            success = true,
            code = 0,
            message = null,
            authCode = "  auth-code-xyz  ",
        )
        assertEquals(OppoAuthResult.Success("auth-code-xyz"), result)
    }

    @Test
    fun `oauth callback parse success without auth code is failure`() {
        val result = OppoOAuthResultParser.parse(success = true, code = 0, message = null, authCode = "   ")
        assertTrue(result is OppoAuthResult.Failure)
        assertEquals(OppoOAuthResultParser.CODE_BAD_AUTH_CODE, (result as OppoAuthResult.Failure).code)
    }

    @Test
    fun `oauth callback parse failure maps friendly message and keeps code`() {
        val result = OppoOAuthResultParser.parse(success = false, code = -2015, message = "raw", authCode = null)
        assertTrue(result is OppoAuthResult.Failure)
        result as OppoAuthResult.Failure
        assertEquals(-2015, result.code)
        assertTrue(result.message.contains("OPPO 账号"))
        assertFalse(result.message.contains("raw"))
    }

    @Test
    fun `oauth error text mapping 保留可诊断错误码`() {
        // 用户 2026-11-17 晚反馈：授权失败文案要能诊断（显示 SDK code）。
        assertTrue(OppoAccountText.oAuthErrorText(-2015).contains("登录 OPPO 账号"))
        assertTrue(OppoAccountText.oAuthErrorText(-2015).contains("code=-2015"))
        assertTrue(OppoAccountText.oAuthErrorText(-2011).contains("code=-2011"))
        assertTrue(OppoAccountText.oAuthErrorText(-2014).contains("code=-2014"))
        assertTrue(OppoAccountText.oAuthErrorText(1117001).contains("code=1117001"))
        assertEquals("OPPO 授权失败（code=12345）", OppoAccountText.oAuthErrorText(12345))
    }

    @Test
    fun `exchange failed 提示账号服务未开通或凭证不匹配`() {
        assertEquals(
            "OPPO 账号服务未开通或凭证不匹配（invalid_client），请联系管理员",
            OppoAccountText.exchangeFailedMessage("invalid_client"),
        )
        assertEquals(
            "OPPO 账号服务未开通或凭证不匹配，请联系管理员",
            OppoAccountText.exchangeFailedMessage("  "),
        )
        assertTrue(
            OppoAccountText.exchangeFailedMessage("invalid_client").contains("invalid_client"),
        )
        assertEquals(
            OppoAccountErrorCode.EXCHANGE_FAILED,
            "oppo_exchange_failed",
        )
    }

    // ------------------------------------------------------------ 快捷登录按钮规则

    @Test
    fun `快捷登录按钮只依赖协议勾选与授权中状态 与表单无关`() {
        // 用户 2026-11-17 晚反馈：未填学号/姓名也能用快捷登录。
        assertTrue(OppoQuickLoginRule.isButtonEnabled(authorizing = false, verifying = false))
        assertFalse(OppoQuickLoginRule.isButtonEnabled(authorizing = true, verifying = false))
        assertFalse(OppoQuickLoginRule.isButtonEnabled(authorizing = false, verifying = true))

        // 字段为空（规则根本没有表单参数）+ 已勾选协议 → 可直接拉起授权。
        assertEquals(
            OppoQuickLoginClick.Trigger,
            OppoQuickLoginRule.onClick(consentAccepted = true, authorizing = false, verifying = false),
        )
        // 未勾选协议 → 简短提示（不静默禁用）。
        assertEquals(
            OppoQuickLoginClick.ShowConsentHint,
            OppoQuickLoginRule.onClick(consentAccepted = false, authorizing = false, verifying = false),
        )
        assertEquals(
            OppoQuickLoginClick.Ignore,
            OppoQuickLoginRule.onClick(consentAccepted = true, authorizing = true, verifying = false),
        )
        assertTrue(OppoQuickLoginRule.CONSENT_HINT.contains("勾选"))
    }

    @Test
    fun `快捷登录入口位于学号姓名表单之前 且不依赖表单状态（静态断言）`() {
        // 用户 2026-11-17 晚反馈：未输入学号/姓名也能用 OPPO 快捷登录 → 入口必须在表单上方且与表单解耦。
        val candidates = listOf(
            java.io.File("src/main/kotlin/com/furina/classtools/ui/screens/identity/IdentityScreen.kt"),
            java.io.File("app/src/main/kotlin/com/furina/classtools/ui/screens/identity/IdentityScreen.kt"),
        )
        val source = candidates.firstOrNull { it.isFile }?.readText()
        assertNotNull("IdentityScreen.kt 未找到（测试工作目录=${java.io.File(".").absolutePath}）", source)
        source!!
        val quickLoginIndex = source.indexOf("OPPO 账号快捷登录")
        val studentIdFieldIndex = source.indexOf("label = { Text(\"学号\") }")
        assertTrue("未找到快捷登录按钮", quickLoginIndex >= 0)
        assertTrue("未找到学号输入框", studentIdFieldIndex >= 0)
        assertTrue("OPPO 快捷登录入口应在学号/姓名表单之前", quickLoginIndex < studentIdFieldIndex)
        // 按钮/点击必须走纯规则（与表单字段无关），不能沿用旧的“勾选才可点”静默禁用写法。
        assertTrue(source.contains("OppoQuickLoginRule.isButtonEnabled("))
        assertTrue(source.contains("OppoQuickLoginRule.onClick("))
        assertFalse(source.contains("enabled = consentAccepted && !oppoLoginInProgress"))
    }

    // ------------------------------------------------------------ 绑定展示

    @Test
    fun `bindings label clamps count and max`() {
        assertEquals("已绑定 0/3", OppoAccountText.bindingsLabel(0, 3))
        assertEquals("已绑定 1/3", OppoAccountText.bindingsLabel(1, 3))
        assertEquals("已绑定 3/3", OppoAccountText.bindingsLabel(3, 3))
        assertEquals("已绑定 3/3", OppoAccountText.bindingsLabel(9, 3))
        assertEquals("已绑定 2/3", OppoAccountText.bindingsLabel(2, 0))
    }

    @Test
    fun `created at formatting from ISO variants`() {
        assertEquals("2026-11-17", OppoAccountText.formatCreatedAt("2026-11-17T12:00:00+08:00"))
        assertEquals("2026-11-17", OppoAccountText.formatCreatedAt("2026-11-17T04:00:00Z"))
        assertEquals("2026-11-17", OppoAccountText.formatCreatedAt("2026-11-17 12:00:00"))
        assertEquals("2026-11-17", OppoAccountText.formatCreatedAt("2026-11-17"))
        assertEquals("", OppoAccountText.formatCreatedAt(null))
        assertEquals("", OppoAccountText.formatCreatedAt("  "))
        assertTrue(OppoAccountText.formatCreatedAt("not-a-date").isNotBlank())
    }

    @Test
    fun `bindings page isFull and empty defaults`() {
        assertTrue(OppoBindingsPage(max = 3, count = 3).isFull)
        assertFalse(OppoBindingsPage(max = 3, count = 2).isFull)
        assertTrue(OppoBindingsPage.EMPTY.items.isEmpty())
        assertEquals(3, OppoAccountText.DEFAULT_MAX_BINDINGS)
    }

    @Test
    fun `binding created date text uses formatter`() {
        val binding = OppoBinding(id = 1, nickname = "nick", createdAt = "2026-11-17T12:00:00+08:00")
        assertEquals("2026-11-17", binding.createdDateText)
        assertEquals("", OppoBinding(id = 1, nickname = "nick").createdDateText)
    }

    @Test
    fun `unbound fallback mentions binding path`() {
        assertTrue(OppoAccountText.UNBOUND_FALLBACK.contains("学号"))
        assertTrue(OppoAccountText.UNBOUND_FALLBACK.contains("绑定"))
    }
}
