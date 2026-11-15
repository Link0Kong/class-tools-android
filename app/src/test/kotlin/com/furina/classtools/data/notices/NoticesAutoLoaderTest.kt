package com.furina.classtools.data.notices

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知「自动全部加载」控制器单测（task-56 B）：
 * 自动循环到无 hasMore、失败自动重试一次、仍失败保留已加载页、签名变化重置基线（见 NoticesPaginationTest）。
 */
class NoticesAutoLoaderTest {

    /** 可编排结果的假仓库（不联网；只关心 loadMore 序列与缓存数据）。 */
    private class FakeRepository(initial: NoticesData?) : NoticesRepository {
        var current: NoticesData? = initial
        var loadMoreCalls = 0
        val scripted = ArrayDeque<NoticesLoadResult>()

        override suspend fun load(force: Boolean): NoticesLoadResult =
            current?.let { NoticesLoadResult.Success(it) } ?: NoticesLoadResult.Failure("no cache")

        override suspend fun loadMore(): NoticesLoadResult {
            loadMoreCalls += 1
            val result = scripted.removeFirstOrNull() ?: NoticesLoadResult.Failure("no scripted result")
            if (result is NoticesLoadResult.Success) current = result.data
            return result
        }

        override suspend fun cached(): NoticesData? = current
    }

    /** 构造一页数据：manual 条数 = [loaded]，总行数 [total]（hasMore/remaining 由二者推导）。 */
    private fun page(loaded: Int, total: Int, prefix: String = "m"): NoticesData {
        val manual = (1..loaded).map { i ->
            ManualNotice(
                row = NoticeRow(id = "$prefix$i", title = "$prefix$i", ts = i.toLong()),
                attachments = emptyList(),
            )
        }
        return NoticesData(
            auto = emptyList(),
            manual = manual,
            hiddenCount = 0,
            fetchedAt = 0L,
            fromCache = false,
            hasMore = total > loaded,
            remainingCount = (total - loaded).coerceAtLeast(0),
            loadedRawCount = loaded,
        )
    }

    @Test
    fun `自动循环直到没有更多 且每页回调`() = runTest {
        val repo = FakeRepository(page(loaded = 2, total = 6))
        repo.scripted.addLast(NoticesLoadResult.Success(page(4, 6)))
        repo.scripted.addLast(NoticesLoadResult.Success(page(6, 6)))
        val pages = mutableListOf<Int>()
        val sleeps = mutableListOf<Long>()
        val loader = NoticesAutoLoader(repo, retryDelayMs = 123L, maxSleep = { sleeps.add(it) })

        val result = loader.loadAll { pages.add(it.manual.size) }

        assertTrue(result is NoticesAutoLoadResult.Completed)
        assertEquals(listOf(4, 6), pages)
        assertEquals(2, repo.loadMoreCalls)
        assertTrue(sleeps.isEmpty())
        assertFalse((result as NoticesAutoLoadResult.Completed).data!!.hasMore)
        assertEquals(6, repo.current!!.manual.size)
    }

    @Test
    fun `失败自动重试一次后继续加载`() = runTest {
        val repo = FakeRepository(page(loaded = 2, total = 4))
        repo.scripted.addLast(NoticesLoadResult.Failure("网络较慢或服务器无响应，请重试"))
        repo.scripted.addLast(NoticesLoadResult.Success(page(4, 4)))
        val sleeps = mutableListOf<Long>()
        val loader = NoticesAutoLoader(repo, retryDelayMs = 456L, maxSleep = { sleeps.add(it) })

        val result = loader.loadAll { }

        assertTrue(result is NoticesAutoLoadResult.Completed)
        assertEquals(2, repo.loadMoreCalls) // 失败 + 重试成功
        assertEquals(listOf(456L), sleeps)
        assertEquals(4, repo.current!!.manual.size)
    }

    @Test
    fun `重试仍失败时保留已加载页并返回失败`() = runTest {
        val repo = FakeRepository(page(loaded = 2, total = 8))
        repo.scripted.addLast(NoticesLoadResult.Failure("第一次失败"))
        repo.scripted.addLast(NoticesLoadResult.Failure("第二次失败"))
        val sleeps = mutableListOf<Long>()
        val loader = NoticesAutoLoader(repo, retryDelayMs = 1L, maxSleep = { sleeps.add(it) })

        val result = loader.loadAll { }

        assertTrue(result is NoticesAutoLoadResult.Failed)
        assertEquals("第二次失败", (result as NoticesAutoLoadResult.Failed).message)
        assertEquals(2, repo.loadMoreCalls) // 失败 + 自动重试一次
        assertEquals(1, sleeps.size)
        // 已加载页保留（首屏 2 页未被清空）
        assertEquals(2, result.data!!.manual.size)
        assertTrue(result.data!!.hasMore)
    }

    @Test
    fun `没有更多时不发 loadMore`() = runTest {
        val repo = FakeRepository(page(loaded = 2, total = 2))
        val loader = NoticesAutoLoader(repo)

        val result = loader.loadAll { }

        assertTrue(result is NoticesAutoLoadResult.Completed)
        assertEquals(0, repo.loadMoreCalls)
    }

    @Test
    fun `无缓存直接完成`() = runTest {
        val repo = FakeRepository(initial = null)
        val loader = NoticesAutoLoader(repo)

        val result = loader.loadAll { }

        assertTrue(result is NoticesAutoLoadResult.Completed)
        assertNull((result as NoticesAutoLoadResult.Completed).data)
        assertEquals(0, repo.loadMoreCalls)
    }

    @Test
    fun `maxPages 保护避免死循环`() = runTest {
        val repo = FakeRepository(page(loaded = 0, total = 100))
        repeat(5) { index ->
            repo.scripted.addLast(NoticesLoadResult.Success(page(loaded = index + 1, total = 100)))
        }
        val loader = NoticesAutoLoader(repo, maxPages = 2, maxSleep = { })

        val result = loader.loadAll { }

        assertTrue(result is NoticesAutoLoadResult.Failed)
        assertEquals(NoticesAutoLoader.STOPPED_MESSAGE, (result as NoticesAutoLoadResult.Failed).message)
        assertEquals(2, repo.loadMoreCalls)
    }
}
