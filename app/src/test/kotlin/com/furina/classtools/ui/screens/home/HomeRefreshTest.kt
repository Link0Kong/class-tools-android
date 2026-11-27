package com.furina.classtools.ui.screens.home

import com.furina.classtools.data.notices.NoticesData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeRefreshTest {
    @Test
    fun pullWaitsForActualResultsAndKeepsSuccessfulContentWhenOtherModulesFail() = runTest {
        val refresh = HomeRefresh(backgroundScope, timeoutMillis = 4_000L)
        val timetable = HomeLoad(initial = "旧课表") {
            delay(2_000)
            HomeLoadResult("新课表")
        }
        val notices = HomeLoad(cached = { "旧通知" }) {
            delay(250)
            HomeLoadResult(error = "网络不可用")
        }
        val power = HomeLoad(initial = "上次读数") { awaitCancellation() }

        refresh.refresh(timetable, notices, power)
        advanceTimeBy(1_100)
        assertTrue(refresh.isRefreshing)
        assertEquals("旧课表", timetable.state.data)
        assertEquals("旧通知", notices.state.data)
        assertEquals("网络不可用", notices.state.error)
        assertFalse(notices.state.loading)

        advanceTimeBy(1_000)
        assertTrue(refresh.isRefreshing)
        assertEquals("新课表", timetable.state.data)
        assertNull(timetable.state.error)
        assertTrue(power.state.loading)

        advanceTimeBy(2_000)
        assertFalse(refresh.isRefreshing)
        assertFalse(power.state.loading)
        assertEquals("上次读数", power.state.data)
        assertEquals("更新超时，请重试", power.state.error)
    }

    @Test
    fun noticeRetryTouchesOnlyItsDependenciesAndCoalescesWithAPull() = runTest {
        val calls = mutableListOf<String>()
        val done = CompletableDeferred<Unit>()
        fun module(name: String) = HomeLoad<Unit> { force ->
            assertTrue(force)
            calls += name
            done.await()
            HomeLoadResult(Unit)
        }
        val notices = HomeLoad<NoticesData> { force ->
            assertTrue(force)
            calls += "notices"
            done.await()
            HomeLoadResult(NoticesData(emptyList(), emptyList(), 0, 1L, false))
        }
        val refresh = HomeRefresh(backgroundScope)
        val loads = HomeDataLoads(refresh, null, module("tasks"), null, notices, module("marks"), null)

        loads.retryNotices()
        loads.retryNotices()
        runCurrent()
        assertEquals(setOf("marks", "notices"), calls.toSet())
        assertEquals(2, calls.size)
        assertFalse(refresh.isRefreshing)

        loads.refresh()
        loads.refresh()
        runCurrent()
        assertEquals(setOf("tasks", "marks", "notices"), calls.toSet())
        assertEquals(3, calls.size)
        assertTrue(refresh.isRefreshing)

        done.complete(Unit)
        runCurrent()
        assertFalse(refresh.isRefreshing)
    }

    @Test
    fun backgroundRefreshKeepsThePreviousErrorVisibleUntilItHasAResult() = runTest {
        val response = CompletableDeferred<HomeLoadResult<Unit>>()
        val refresh = HomeRefresh(backgroundScope)
        val tasks = HomeLoad(initial = Unit, keepErrorWhileLoading = true) { force ->
            if (force) response.await() else HomeLoadResult(error = "网络不可用")
        }

        refresh.prefetch(tasks)
        runCurrent()
        assertEquals("网络不可用", tasks.state.error)

        refresh.retry(tasks)
        runCurrent()
        assertTrue(tasks.state.loading)
        assertEquals("网络不可用", tasks.state.error)
        assertEquals(Unit, tasks.state.data)

        response.complete(HomeLoadResult(Unit))
        runCurrent()
        assertFalse(tasks.state.loading)
        assertNull(tasks.state.error)
    }

    @Test
    fun supersededPrefetchCannotPublishAnOlderResult() = runTest {
        val refresh = HomeRefresh(backgroundScope)
        val finishOld = CompletableDeferred<Unit>()
        val load = HomeLoad(initial = "缓存") { force ->
            if (force) HomeLoadResult("新数据") else {
                withContext(NonCancellable) { finishOld.await() }
                HomeLoadResult("旧数据")
            }
        }

        refresh.prefetch(load)
        runCurrent()
        refresh.refresh(load)
        runCurrent()
        assertEquals("新数据", load.state.data)
        assertFalse(refresh.isRefreshing)

        finishOld.complete(Unit)
        runCurrent()
        assertEquals("新数据", load.state.data)
        assertFalse(load.state.loading)
    }
}
