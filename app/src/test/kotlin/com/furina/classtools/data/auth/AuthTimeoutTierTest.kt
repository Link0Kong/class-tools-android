package com.furina.classtools.data.auth

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.testApiClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-11-18 超时分级（安卓）：登录/身份验证使用 20s 档（默认读写档 60s），
 * 弱网下更早失败并提示重试，而不是让身份页长时间空转。
 */
class AuthTimeoutTierTest {

    @Test
    fun `class_verify 请求携带登录超时档 20s`() = runTest {
        val transport = FakeTransport {
            ApiResponse(200, """{"ok":true,"token":"tok","sid":"3260799999","name":"测试账号"}""")
        }
        val repository = ApiAuthRepository(api = testApiClient(transport))

        val result = repository.verify("3260799999", "测试账号")

        assertTrue(result is VerifyResult.Success)
        assertEquals(ApiAuthRepository.AUTH_TIMEOUT_MS, transport.lastRequest().timeoutMs)
        assertEquals(20_000L, ApiAuthRepository.AUTH_TIMEOUT_MS)
    }
}
