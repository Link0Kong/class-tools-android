package com.furina.classtools.data.account

import android.app.Activity
import android.content.Intent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 荣耀账号配置/设备判定/文案（纯逻辑）。 */
class HonorAccountConfigTest {

    @Test
    fun `unconfigured client id hides both entries`() {
        val cfg = HonorAccountConfig.of("", null, null)
        assertFalse(cfg.isConfigured)
        val mgr = manager(cfg = cfg)
        assertFalse(mgr.quickLoginAvailable)
        assertFalse(mgr.bindingsAvailable)
    }

    @Test
    fun `scope always contains openid and keeps profile`() {
        assertEquals("openid", HonorAccountConfig.of("id", "openid", null).effectiveScope)
        assertEquals("openid profile", HonorAccountConfig.of("id", "profile", null).effectiveScope)
        assertEquals("openid email profile", HonorAccountConfig.of("id", "email profile", null).effectiveScope)
        assertEquals("openid", HonorAccountConfig.DEFAULT_SCOPE.split(" ")[0])
    }

    @Test
    fun `device support requires honor manufacturer or sdk flag`() {
        assertTrue(HonorAccountDeviceSupport.isSupported("HONOR", false))
        assertTrue(HonorAccountDeviceSupport.isSupported("Xiaomi", true))
        assertFalse(HonorAccountDeviceSupport.isSupported("Xiaomi", false))
        assertFalse(HonorAccountDeviceSupport.isSupported(null, false))
    }

    @Test
    fun `oauth error text keeps diagnosable code`() {
        assertTrue(HonorAccountText.oAuthErrorText(3002).contains("3002"))
        assertTrue(HonorAccountText.oAuthErrorText(34).contains("未安装"))
        assertTrue(HonorAccountText.oAuthErrorText(9999).contains("9999"))
    }

    @Test
    fun `auth request maps scopes`() {
        val request = HonorAuthRequest.of(HonorAccountConfig.of("cid", "profile openid", null))
        assertEquals(listOf("profile", "openid"), request.scopes().map { it.scopeUri })
        assertTrue(request.isValid)
        assertFalse(HonorAuthRequest("", "openid").isValid)
    }

    @Test
    fun `quick login rule requires consent`() {
        assertEquals(
            OppoQuickLoginClick.ShowConsentHint,
            HonorQuickLoginRule.onClick(consentAccepted = false, authorizing = false, verifying = false),
        )
        assertEquals(
            OppoQuickLoginClick.Trigger,
            HonorQuickLoginRule.onClick(consentAccepted = true, authorizing = false, verifying = false),
        )
        assertFalse(HonorQuickLoginRule.isButtonEnabled(authorizing = true, verifying = false))
    }

    @Test
    fun `honor api prefix labels messages`() = runTest {
        val transport = com.furina.classtools.FakeTransport {
            com.furina.classtools.data.network.ApiResponse(200, """{"ok":false,"code":"unbound"}""")
        }
        val api = EcsOppoAccountApi(
            transport = transport,
            baseUrl = "http://example.invalid/account",
            pathPrefix = "/honor",
            providerLabel = "荣耀",
        )
        val result = api.login("auth-code")
        assertEquals("http://example.invalid/account/honor/login", transport.lastRequest().url)
        assertTrue((result as OppoLoginResult.Unbound).message.contains("荣耀"))
    }

    @Test
    fun `honor API sends bindings token header on honor path`() = runTest {
        val transport = com.furina.classtools.FakeTransport {
            com.furina.classtools.data.network.ApiResponse(200, """{"ok":true,"max":3,"count":0,"items":[]}""")
        }
        val api = EcsOppoAccountApi(
            transport = transport,
            baseUrl = "http://example.invalid/account",
            pathPrefix = "/honor",
            providerLabel = "荣耀",
        )
        api.bindings("class-token")
        val request = transport.lastRequest()
        assertEquals("http://example.invalid/account/honor/bindings", request.url)
        assertEquals("class-token", request.headers["X-Class-Token"])
    }

    // ------------------------------------------------------------ 便捷构造

    private fun manager(
        cfg: HonorAccountConfig = HonorAccountConfig.of("cid", "openid profile", null),
        auth: FakeHonorAuth = FakeHonorAuth(),
        api: FakeHonorApi = FakeHonorApi(),
        deviceSupported: Boolean = true,
    ): HonorAccountManager = HonorAccountManager(
        config = cfg,
        api = api,
        auth = auth,
        deviceSupported = deviceSupported,
        now = { 1_700_000_000_000L },
    )
}

/** 假荣耀授权（不加载真 SDK）。 */
internal class FakeHonorAuth(
    var result: HonorAuthResult = HonorAuthResult.Success("auth-code-1"),
    var intent: Intent? = null,
) : HonorAuthSdk {
    var parses = 0

    override fun signInIntent(activity: Activity, request: HonorAuthRequest): Intent? = intent

    override suspend fun parseResult(requestCode: Int, data: Intent?): HonorAuthResult {
        parses += 1
        return result
    }

    override fun isHonorMachine(): Boolean = true
}

/** 假 ECS（荣耀路径）。 */
internal class FakeHonorApi(
    var loginResult: OppoLoginResult = OppoLoginResult.Failure("unset", "unset"),
    var bindResult: OppoBindResult = OppoBindResult.Failure("unset", "unset"),
    var bindingsResult: OppoBindingsResult = OppoBindingsResult.Success(OppoBindingsPage.EMPTY),
    var unbindResult: OppoUnbindResult = OppoUnbindResult.Success(0),
) : OppoAccountApi {
    val loginCodes = mutableListOf<String>()
    val bindCalls = mutableListOf<Pair<String, String>>()
    val unbindCalls = mutableListOf<Pair<Long, String>>()
    var bindingsCalls = 0

    override suspend fun login(authCode: String): OppoLoginResult {
        loginCodes += authCode
        return loginResult
    }

    override suspend fun bind(authCode: String, classToken: String): OppoBindResult {
        bindCalls += authCode to classToken
        return bindResult
    }

    override suspend fun bindings(classToken: String): OppoBindingsResult {
        bindingsCalls += 1
        return bindingsResult
    }

    override suspend fun unbind(bindingId: Long, classToken: String): OppoUnbindResult {
        unbindCalls += bindingId to classToken
        return unbindResult
    }

    override suspend fun health(): OppoHealthResult = OppoHealthResult(ok = true, configured = true)
}

