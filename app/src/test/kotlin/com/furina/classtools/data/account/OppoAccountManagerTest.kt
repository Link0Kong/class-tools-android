package com.furina.classtools.data.account

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OppoAccountManager 编排单测（假 OAuth SDK + 假 ECS API）：
 * 未配置/非 OPPO 设备安全跳过、快捷登录成功/未绑定/失败、Session 构造、
 * 绑定/列表/解绑状态机与错误分支。不依赖真机与真实 AppKey。
 */
class OppoAccountManagerTest {

    // 单测里不需要真实 Context：假 OAuth SDK 不使用它（框架类在 mockable android.jar 下构造为空实现）。
    private val context: Context = ContextWrapper(null)

    private class FakeOAuth(
        var result: OppoAuthResult = OppoAuthResult.Success("auth-code-1"),
    ) : OppoOAuthSdk {
        val requests = mutableListOf<OppoOAuthRequest>()

        override suspend fun authorize(context: Context, request: OppoOAuthRequest): OppoAuthResult {
            requests += request
            return result
        }
    }

    private class FakeApi(
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

    private fun configuredConfig(): OppoAccountConfig =
        OppoAccountConfig.of("app-id", "app-key", "openid", "http://127.0.0.1:9/account")

    private fun manager(
        api: FakeApi = FakeApi(),
        oauth: FakeOAuth = FakeOAuth(),
        deviceSupported: Boolean = true,
        config: OppoAccountConfig = configuredConfig(),
        now: () -> Long = { 1_700_000_000_000L },
        state: String = "a".repeat(32),
        ttl: Long = OppoAccountManager.DEFAULT_SESSION_TTL_MS,
    ): OppoAccountManager = OppoAccountManager(
        config = config,
        api = api,
        oauth = oauth,
        deviceSupported = deviceSupported,
        now = now,
        stateFactory = { state },
        sessionTtlMs = ttl,
    )

    // ------------------------------------------------------------ 可用性

    @Test
    fun `unconfigured hides entries and skips sdk`() = runTest {
        val api = FakeApi()
        val oauth = FakeOAuth()
        val mgr = manager(api = api, oauth = oauth, config = OppoAccountConfig.of("", "", null, null))

        assertFalse(mgr.isConfigured)
        assertFalse(mgr.quickLoginAvailable)
        assertFalse(mgr.bindingsAvailable)
        assertEquals(
            OppoAccountErrorCode.NOT_AVAILABLE,
            (mgr.login(context) as OppoSessionLoginResult.Failure).code,
        )
        assertEquals(
            OppoAccountErrorCode.NOT_AVAILABLE,
            (mgr.bind(context, "token") as OppoBindResult.Failure).code,
        )
        assertTrue(oauth.requests.isEmpty())
        assertTrue(api.loginCodes.isEmpty())
        assertTrue(api.bindCalls.isEmpty())
    }

    @Test
    fun `non oppo device hides entries`() = runTest {
        val oauth = FakeOAuth()
        val mgr = manager(oauth = oauth, deviceSupported = false)
        assertFalse(mgr.quickLoginAvailable)
        assertFalse(mgr.bindingsAvailable)
        assertEquals(
            OppoAccountErrorCode.NOT_AVAILABLE,
            (mgr.login(context) as OppoSessionLoginResult.Failure).code,
        )
        assertTrue(oauth.requests.isEmpty())
    }

    @Test
    fun `configured oppo device enables both entries`() {
        val mgr = manager()
        assertTrue(mgr.isConfigured)
        assertTrue(mgr.quickLoginAvailable)
        assertTrue(mgr.bindingsAvailable)
    }

    // ------------------------------------------------------------ 快捷登录

    @Test
    fun `login success builds session and forwards auth code`() = runTest {
        val api = FakeApi(
            loginResult = OppoLoginResult.Success(
                token = " class-token ",
                sid = " 3260799999 ",
                name = " 测试账号 ",
                nickname = "nick",
            ),
        )
        val oauth = FakeOAuth(OppoAuthResult.Success("code-from-sdk"))
        val mgr = manager(api = api, oauth = oauth, now = { 1_000L }, ttl = 2_000L)

        val result = mgr.login(context)
        assertTrue(result is OppoSessionLoginResult.Success)
        val session = (result as OppoSessionLoginResult.Success).session
        assertEquals("3260799999", session.sid)
        assertEquals("测试账号", session.name)
        assertEquals("class-token", session.token)
        assertEquals(1_000L, session.issuedAt)
        assertEquals(3_000L, session.expiresAt)
        assertFalse(session.debug)
        assertEquals(listOf("code-from-sdk"), api.loginCodes)

        // OAuth 请求参数来自配置 + 注入的 state/scope。
        assertEquals(1, oauth.requests.size)
        val request = oauth.requests.single()
        assertEquals("app-id", request.appId)
        assertEquals("app-key", request.appKey)
        assertEquals("openid", request.scope)
        assertEquals("a".repeat(32), request.state)
        assertTrue(request.isValid)
    }

    @Test
    fun `login oauth failure skips api and returns friendly message`() = runTest {
        val api = FakeApi()
        val oauth = FakeOAuth(OppoAuthResult.Failure(-2015, "请先在系统设置登录 OPPO 账号"))
        val result = manager(api = api, oauth = oauth).login(context)

        assertEquals(
            OppoSessionLoginResult.Failure("-2015", "请先在系统设置登录 OPPO 账号"),
            result,
        )
        assertTrue(api.loginCodes.isEmpty())
    }

    @Test
    fun `login unbound message uses server text`() = runTest {
        val api = FakeApi(loginResult = OppoLoginResult.Unbound("未绑定提示"))
        val result = manager(api = api).login(context)
        assertEquals(OppoSessionLoginResult.Unbound("未绑定提示"), result)
    }

    @Test
    fun `login unbound empty message falls back`() = runTest {
        val api = FakeApi(loginResult = OppoLoginResult.Unbound(""))
        val result = manager(api = api).login(context)
        assertEquals(OppoSessionLoginResult.Unbound(OppoAccountText.UNBOUND_FALLBACK), result)
    }

    @Test
    fun `login server failure propagates`() = runTest {
        val api = FakeApi(loginResult = OppoLoginResult.Failure("server_error", "服务异常"))
        assertEquals(
            OppoSessionLoginResult.Failure("server_error", "服务异常"),
            manager(api = api).login(context),
        )
    }

    @Test
    fun `login success with blank token is rejected`() = runTest {
        val api = FakeApi(loginResult = OppoLoginResult.Success(token = " ", sid = "1", name = "n"))
        val result = manager(api = api).login(context) as OppoSessionLoginResult.Failure
        assertEquals(OppoAccountErrorCode.PARSE, result.code)
    }

    // ------------------------------------------------------------ 绑定

    @Test
    fun `bind success refreshes bindings list`() = runTest {
        val page = OppoBindingsPage(
            max = 3,
            count = 1,
            items = listOf(OppoBinding(id = 1, nickname = "小O", createdAt = "2026-11-17T00:00:00Z")),
        )
        val api = FakeApi(
            bindResult = OppoBindResult.Success(OppoBinding(id = 1, nickname = "小O"), 1),
            bindingsResult = OppoBindingsResult.Success(page),
        )
        val oauth = FakeOAuth(OppoAuthResult.Success("bind-code"))
        val mgr = manager(api = api, oauth = oauth)

        val result = mgr.bind(context, "class-token")
        assertTrue(result is OppoBindResult.Success)
        assertEquals(listOf("bind-code" to "class-token"), api.bindCalls)
        assertEquals(1, api.bindingsCalls)
        assertEquals(OppoBindingsUiState.Ready(page), mgr.bindingsState.value)
    }

    @Test
    fun `bind limit does not refresh`() = runTest {
        val api = FakeApi(bindResult = OppoBindResult.Limit("一个学号最多绑定3个OPPO账号"))
        assertEquals(
            OppoBindResult.Limit("一个学号最多绑定3个OPPO账号"),
            manager(api = api).bind(context, "token"),
        )
        assertEquals(0, api.bindingsCalls)
    }

    @Test
    fun `bind bound elsewhere does not refresh`() = runTest {
        val api = FakeApi(bindResult = OppoBindResult.BoundElsewhere("已绑其他学号"))
        assertEquals(OppoBindResult.BoundElsewhere("已绑其他学号"), manager(api = api).bind(context, "token"))
        assertEquals(0, api.bindingsCalls)
    }

    @Test
    fun `bind oauth failure skips api`() = runTest {
        val api = FakeApi()
        val oauth = FakeOAuth(OppoAuthResult.Failure(-2014, "OPPO 授权未完成，请重试"))
        val result = manager(api = api, oauth = oauth).bind(context, "token")
        assertEquals(OppoBindResult.Failure("oauth_-2014", "OPPO 授权未完成，请重试"), result)
        assertTrue(api.bindCalls.isEmpty())
    }

    @Test
    fun `bind blank class token rejected before oauth`() = runTest {
        val api = FakeApi()
        val oauth = FakeOAuth()
        val result = manager(api = api, oauth = oauth).bind(context, "   ")
        assertEquals(OppoAccountErrorCode.UNAUTHORIZED, (result as OppoBindResult.Failure).code)
        assertTrue(oauth.requests.isEmpty())
    }

    @Test
    fun `bind refresh failure still returns bind success`() = runTest {
        val api = FakeApi(
            bindResult = OppoBindResult.Success(OppoBinding(id = 2, nickname = "n"), 1),
            bindingsResult = OppoBindingsResult.Failure("network", "网络不可用"),
        )
        val result = manager(api = api).bind(context, "token")
        assertTrue(result is OppoBindResult.Success)
        assertTrue(api.bindingsCalls >= 1)
    }

    // ------------------------------------------------------------ 列表 / 解绑

    @Test
    fun `refresh bindings loading then ready`() = runTest {
        val page = OppoBindingsPage(max = 3, count = 2, items = emptyList())
        val api = FakeApi(bindingsResult = OppoBindingsResult.Success(page))
        val mgr = manager(api = api)

        assertEquals(OppoBindingsUiState.Idle, mgr.bindingsState.value)
        val result = mgr.refreshBindings("token")
        assertEquals(OppoBindingsResult.Success(page), result)
        assertEquals(OppoBindingsUiState.Ready(page), mgr.bindingsState.value)
        assertEquals(1, api.bindingsCalls)
    }

    @Test
    fun `refresh bindings failure sets error state`() = runTest {
        val api = FakeApi(bindingsResult = OppoBindingsResult.Failure("network", "网络不可用"))
        val mgr = manager(api = api)
        mgr.refreshBindings("token")
        assertEquals(OppoBindingsUiState.Error("网络不可用"), mgr.bindingsState.value)
    }

    @Test
    fun `unbind success refreshes list`() = runTest {
        val api = FakeApi(
            unbindResult = OppoUnbindResult.Success(0),
            bindingsResult = OppoBindingsResult.Success(OppoBindingsPage.EMPTY),
        )
        val mgr = manager(api = api)
        assertEquals(OppoUnbindResult.Success(0), mgr.unbind("token", 3))
        assertEquals(listOf(3L to "token"), api.unbindCalls)
        assertEquals(1, api.bindingsCalls)
    }

    @Test
    fun `unbind failure does not refresh`() = runTest {
        val api = FakeApi(unbindResult = OppoUnbindResult.Failure("not_found", "记录不存在"))
        val mgr = manager(api = api)
        assertEquals(OppoUnbindResult.Failure("not_found", "记录不存在"), mgr.unbind("token", 3))
        assertEquals(0, api.bindingsCalls)
    }

    @Test
    fun `unbind blank token rejected`() = runTest {
        val api = FakeApi()
        assertEquals(
            OppoAccountErrorCode.UNAUTHORIZED,
            (manager(api = api).unbind("", 1) as OppoUnbindResult.Failure).code,
        )
        assertTrue(api.unbindCalls.isEmpty())
    }

    @Test
    fun `clear bindings resets state`() = runTest {
        val page = OppoBindingsPage(max = 3, count = 1)
        val mgr = manager(api = FakeApi(bindingsResult = OppoBindingsResult.Success(page)))
        mgr.refreshBindings("token")
        assertNotNull((mgr.bindingsState.value as? OppoBindingsUiState.Ready))
        mgr.clearBindings()
        assertEquals(OppoBindingsUiState.Idle, mgr.bindingsState.value)
    }

    @Test
    fun `session ttl constant is 30 days`() {
        assertEquals(30L * 24 * 60 * 60 * 1000, OppoAccountManager.DEFAULT_SESSION_TTL_MS)
    }
}
