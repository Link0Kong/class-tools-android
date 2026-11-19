package com.furina.classtools.data.network

import com.furina.classtools.FakeTransport
import com.furina.classtools.testApiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 网络层审计（2026-11-19）低风险修复的回归测试：
 *
 * 1. **协程取消不再被吞成 `ApiError.Network`**：以前 `catch (t: Throwable)` 会把
 *    `CancellationException` 转成失败结果返回，导致「离开页面 / 主动取消」看起来仍是一次网络失败；
 * 2. **在途合并的等待者不继承首个调用的取消**：以前 `completeExceptionally(t)` 会把 leader 的取消
 *    传染给其它页面发起的同一 GET，现在等待者拿到 `ApiResult.Failure`（可展示、可重试）。
 */
class ApiClientCancellationTest {

    @Test
    fun `协程取消不被吞成失败结果`() = runTest {
        val transport = FakeTransport {
            delay(10_000)
            ApiResponse(200, "[]")
        }
        val api = testApiClient(transport)

        var result: ApiResult<String>? = null
        val job = launch { result = api.get("homework?select=*") }
        // 让 leader 真正进入挂起（FakeTransport 的 delay）后再取消。
        delay(100)
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertNull("被取消的调用不应把 ApiResult.Failure 当成结果返回", result)
    }

    @Test
    fun `在途合并：首个调用取消后等待者拿到失败结果而不是异常`() = runTest {
        val transport = FakeTransport {
            delay(10_000)
            ApiResponse(200, """[{"id":"hw1"}]""")
        }
        val api = testApiClient(transport)

        val leader = launch { runCatching { api.get("homework?select=*") } }
        delay(100) // leader 已在途
        var follower: ApiResult<String>? = null
        val followerJob = launch { follower = api.get("homework?select=*") }
        delay(100) // follower 正在合并在途请求

        leader.cancel()
        leader.join()
        followerJob.join()

        assertTrue("等待者不应被连带取消", !followerJob.isCancelled)
        assertTrue("等待者应拿到可展示的失败结果", follower is ApiResult.Failure)
        // 两次调用（leader + follower 合并）只真正发出 1 个 HTTP 请求。
        assertTrue(transport.requests.size <= 1)
    }
}
