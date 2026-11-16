package com.furina.classtools.data.update

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下载协调器单测：优先 DownloadManager，不可用（返回 null / 抛异常）时回退 OkHttp。
 * 用假实现，不触碰 Android 系统服务。
 */
class UpdateDownloadCoordinatorTest {

    private val info = UpdateInfo(
        versionCode = 79,
        versionName = "4.57.0-test1",
        apkUrl = "http://121.196.47.2/android/classtools-4.57.0-test1.apk",
    )

    private class FakeDownloader(
        private val result: () -> UpdateDownloadSession?,
    ) : UpdateApkDownloader {
        var calls = 0
            private set
        var lastFileName: String? = null
            private set

        override fun enqueue(info: UpdateInfo, fileName: String): UpdateDownloadSession? {
            calls += 1
            lastFileName = fileName
            return result()
        }
    }

    private fun session(): UpdateDownloadSession = object : UpdateDownloadSession {
        override val events: Flow<UpdateDownloadEvent> = flowOf(
            UpdateDownloadEvent.Done(DownloadedApk(fileName = "a.apk")),
        )

        override fun cancel() = Unit
    }

    @Test
    fun `首选可用时不回退`() {
        val preferredSession = session()
        val preferred = FakeDownloader { preferredSession }
        val fallback = FakeDownloader { session() }
        val coordinator = UpdateDownloadCoordinator(preferred, fallback)

        val result = coordinator.enqueue(info, "classtools-4.57.0-test1.apk")

        assertSame(preferredSession, result)
        assertEquals(1, preferred.calls)
        assertEquals(0, fallback.calls)
        assertEquals("classtools-4.57.0-test1.apk", preferred.lastFileName)
    }

    @Test
    fun `首选返回 null 时回退`() {
        val fallbackSession = session()
        val preferred = FakeDownloader { null }
        val fallback = FakeDownloader { fallbackSession }
        val coordinator = UpdateDownloadCoordinator(preferred, fallback)

        val result = coordinator.enqueue(info, "a.apk")

        assertSame(fallbackSession, result)
        assertEquals(1, preferred.calls)
        assertEquals(1, fallback.calls)
    }

    @Test
    fun `首选抛异常时回退`() {
        val fallbackSession = session()
        val preferred = FakeDownloader { throw IllegalStateException("DownloadManager 不可用") }
        val fallback = FakeDownloader { fallbackSession }
        val coordinator = UpdateDownloadCoordinator(preferred, fallback)

        val result = coordinator.enqueue(info, "a.apk")

        assertSame(fallbackSession, result)
    }

    @Test
    fun `两者都不可用返回 null`() {
        val coordinator = UpdateDownloadCoordinator(FakeDownloader { null }, FakeDownloader { null })
        assertNull(coordinator.enqueue(info, "a.apk"))
    }

    @Test
    fun `协调器透传文件名`() {
        val fallback = FakeDownloader { session() }
        val coordinator = UpdateDownloadCoordinator(FakeDownloader { null }, fallback)
        coordinator.enqueue(info, "classtools-4.58.0.apk")
        assertTrue(fallback.lastFileName == "classtools-4.58.0.apk")
    }
}
