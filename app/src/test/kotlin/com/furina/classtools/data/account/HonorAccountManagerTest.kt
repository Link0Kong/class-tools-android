package com.furina.classtools.data.account

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** HonorAccountManager 编排单测（假授权 SDK + 假 ECS）：不依赖荣耀真机/真实 Client ID。 */
class HonorAccountManagerTest {

    private fun manager(
        api: FakeHonorApi = FakeHonorApi(),
        auth: FakeHonorAuth = FakeHonorAuth(),
        deviceSupported: Boolean = true,
        config: HonorAccountConfig = HonorAccountConfig.of("cid", "openid profile", null),
    ): HonorAccountManager = HonorAccountManager(
        config = config,
        api = api,
        auth = auth,
        deviceSupported = deviceSupported,
        now = { 1_700_000_000_000L },
    )

    @Test
    fun `non honor device hides entries and skips sdk`() = runTest {
        val api = FakeHonorApi()
        val auth = FakeHonorAuth()
        val mgr = manager(api = api, auth = auth, deviceSupported = false)

        assertFalse(mgr.quickLoginAvailable)
        assertFalse(mgr.bindingsAvailable)
        assertEquals(
            OppoAccountErrorCode.NOT_AVAILABLE,
            (mgr.login(0, null) as OppoSessionLoginResult.Failure).code,
        )
        assertEquals(0, auth.parses)
        assertTrue(api.loginCodes.isEmpty())
    }

    @Test
    fun `login success builds session with server identity`() = runTest {
        val api = FakeHonorApi(
            loginResult = OppoLoginResult.Success(
                token = "t", sid = "3260799999", name = "测试账号", nickname = "荣耀用户",
            ),
        )
        val result = manager(api = api).login(0, null) as OppoSessionLoginResult.Success

        assertEquals("3260799999", result.session.sid)
        assertEquals("测试账号", result.session.name)
        assertEquals("t", result.session.token)
        assertEquals(1_700_000_000_000L, result.session.issuedAt)
        assertEquals(listOf("auth-code-1"), api.loginCodes)
    }

    @Test
    fun `login unbound maps to unbound result`() = runTest {
        val api = FakeHonorApi(loginResult = OppoLoginResult.Unbound("未绑定"))
        val result = manager(api = api).login(0, null)
        assertEquals("未绑定", (result as OppoSessionLoginResult.Unbound).message)
    }

    @Test
    fun `login auth failure does not call server`() = runTest {
        val api = FakeHonorApi()
        val auth = FakeHonorAuth(result = HonorAuthResult.Failure(3002, "已取消"))
        val result = manager(api = api, auth = auth).login(0, null)
        assertEquals("3002", (result as OppoSessionLoginResult.Failure).code)
        assertTrue(api.loginCodes.isEmpty())
    }

    @Test
    fun `login empty token maps to parse failure`() = runTest {
        val api = FakeHonorApi(loginResult = OppoLoginResult.Success("", "sid", "n", ""))
        val result = manager(api = api).login(0, null)
        assertEquals(OppoAccountErrorCode.PARSE, (result as OppoSessionLoginResult.Failure).code)
    }

    @Test
    fun `bind success refreshes bindings`() = runTest {
        val api = FakeHonorApi(
            bindResult = OppoBindResult.Success(OppoBinding(1, "n", ""), 1),
        )
        val mgr = manager(api = api)
        val result = mgr.bind(0, null, "class-token")

        assertTrue(result is OppoBindResult.Success)
        assertEquals(listOf("auth-code-1" to "class-token"), api.bindCalls)
        assertEquals(1, api.bindingsCalls)
    }

    @Test
    fun `bind requires login token`() = runTest {
        val api = FakeHonorApi()
        val result = manager(api = api).bind(0, null, "")
        assertEquals(OppoAccountErrorCode.UNAUTHORIZED, (result as OppoBindResult.Failure).code)
        assertTrue(api.bindCalls.isEmpty())
    }

    @Test
    fun `bind limit and bound elsewhere are surfaced`() = runTest {
        val limitApi = FakeHonorApi(bindResult = OppoBindResult.Limit("上限"))
        assertEquals("上限", (manager(api = limitApi).bind(0, null, "t") as OppoBindResult.Limit).message)

        val elseApi = FakeHonorApi(bindResult = OppoBindResult.BoundElsewhere("已绑"))
        assertTrue(manager(api = elseApi).bind(0, null, "t") is OppoBindResult.BoundElsewhere)
    }

    @Test
    fun `bind auth failure becomes oauth failure`() = runTest {
        val api = FakeHonorApi()
        val auth = FakeHonorAuth(result = HonorAuthResult.Failure(3002, "已取消"))
        val result = manager(api = api, auth = auth).bind(0, null, "t")
        assertEquals("oauth_3002", (result as OppoBindResult.Failure).code)
        assertTrue(api.bindCalls.isEmpty())
    }

    @Test
    fun `refresh bindings exposes loading then ready`() = runTest {
        val page = OppoBindingsPage(max = 3, count = 1, items = listOf(OppoBinding(7, "昵称", "2026-11-20")))
        val mgr = manager(api = FakeHonorApi(bindingsResult = OppoBindingsResult.Success(page)))
        val result = mgr.refreshBindings("t")

        assertTrue(result is OppoBindingsResult.Success)
        val state = mgr.bindingsState.value as OppoBindingsUiState.Ready
        assertEquals(1, state.page.count)
        assertEquals("昵称", state.page.items.single().nickname)
    }

    @Test
    fun `refresh failure sets error and unbind refreshes`() = runTest {
        val api = FakeHonorApi(bindingsResult = OppoBindingsResult.Failure("bad", "读取失败"))
        val mgr = manager(api = api)
        mgr.refreshBindings("t")
        assertTrue(mgr.bindingsState.value is OppoBindingsUiState.Error)

        api.bindingsResult = OppoBindingsResult.Success(OppoBindingsPage.EMPTY)
        api.unbindResult = OppoUnbindResult.Success(0)
        val result = mgr.unbind("t", 7)
        assertTrue(result is OppoUnbindResult.Success)
        assertEquals(listOf(7L to "t"), api.unbindCalls)
        assertEquals(2, api.bindingsCalls)
    }

    @Test
    fun `clear bindings resets state`() = runTest {
        val api = FakeHonorApi(bindingsResult = OppoBindingsResult.Success(OppoBindingsPage.EMPTY))
        val mgr = manager(api = api)
        mgr.refreshBindings("t")
        mgr.clearBindings()
        assertEquals(OppoBindingsUiState.Idle, mgr.bindingsState.value)
    }
}

