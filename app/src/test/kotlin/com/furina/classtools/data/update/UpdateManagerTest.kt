package com.furina.classtools.data.update

import android.content.Context
import android.content.ContextWrapper
import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UpdateManager 行为单测（用户 2026-11-17 晚要求）：
 * - 自动检查/手动检查发现新版本 → **弹窗并自动后台下载**；
 * - 下载完成（Ready）→ **不自动拉起安装器**，等用户点「立即安装」；
 * - 自动下载幂等；force 不可取消；
 * - 弹窗携带「当前版本 → 新版本」与可选包大小；
 * - 全假实现（FakeTransport/FakeDownloader/假安装器），不触碰系统服务。
 */
class UpdateManagerTest {

    // 假 OAuth 同样的做法：假下载器/假安装器不需要真实 Context（mockable android.jar 下构造为空实现）。
    private val context: Context = ContextWrapper(null)

    private val availableJson = """
        {"versionCode":86,"versionName":"1.0.0-beta3",
         "apkUrl":"http://121.196.47.2/android/classtools-1.0.0-beta3.apk",
         "notes":"修复 4 项反馈","force":false,"channel":"test","sizeBytes":6120000}
    """.trimIndent()

    private val upToDateJson = availableJson.replace("\"versionCode\":86", "\"versionCode\":85")

    private val forcedJson = availableJson.replace("\"force\":false", "\"force\":true")

    private class FakeStore(var dismissed: Int = 0) : UpdateCheckStore {
        override fun dismissedVersionCode(): Int = dismissed
        override fun markDismissed(versionCode: Int) {
            dismissed = versionCode
        }
    }

    private class FakeSession : UpdateDownloadSession {
        private val flow = MutableSharedFlow<UpdateDownloadEvent>(extraBufferCapacity = 16)
        override val events: Flow<UpdateDownloadEvent> = flow
        var cancelled = false
            private set

        override fun cancel() {
            cancelled = true
        }

        suspend fun emit(event: UpdateDownloadEvent) {
            flow.emit(event)
        }
    }

    private class FakeDownloader : UpdateApkDownloader {
        var calls = 0
            private set
        var lastFileName: String? = null
            private set
        var session: FakeSession = FakeSession()
        var failStart = false

        override fun enqueue(info: UpdateInfo, fileName: String): UpdateDownloadSession? {
            calls += 1
            lastFileName = fileName
            return if (failStart) null else session
        }
    }

    private data class Harness(
        val manager: UpdateManager,
        val downloader: FakeDownloader,
        val store: FakeStore,
        val installs: MutableList<DownloadedApk>,
    )

    private fun TestScope.harness(
        json: String = availableJson,
        downloader: FakeDownloader = FakeDownloader(),
        store: FakeStore = FakeStore(),
    ): Harness {
        val installs = mutableListOf<DownloadedApk>()
        // 独立 scope（非 runTest 前台子协程）：管理器内部有长驻下载收集协程，用独立 scheduler 协程避免
        // runTest 结束时报 UncompletedCoroutinesError；advanceUntilIdle 仍能驱动同一 scheduler。
        val managerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val manager = UpdateManager(
            context = context,
            store = store,
            checker = UpdateChecker(
                currentVersionCode = 85,
                currentVersionName = "1.0.0-beta2",
                transport = FakeTransport { ApiResponse(200, json) },
                endpoints = listOf("http://update.invalid/latest.json"),
            ),
            downloader = downloader,
            currentVersionName = "1.0.0-beta2",
            installLauncher = { _, apk ->
                installs += apk
                UpdateInstallLaunch.LAUNCHED
            },
            scope = managerScope,
        )
        return Harness(manager, downloader, store, installs)
    }

    // ------------------------------------------------------------ 检测只弹窗

    @Test
    fun `自动检查发现新版本 只弹窗等待用户选择`() = runTest {
        val h = harness()
        h.manager.checkOnStart()
        advanceUntilIdle()

        assertNotNull("checkStatus=${h.manager.checkStatus.value}", h.manager.dialog.value)
        assertEquals(0, h.downloader.calls)
        assertTrue(h.manager.checkStatus.value is UpdateCheckStatus.Available)
        val ui = h.manager.dialog.value!!
        assertEquals("1.0.0-beta2", ui.currentVersionName)
        assertEquals(86, ui.info.versionCode)
        assertEquals(6_120_000L, ui.info.sizeBytes)
        assertTrue(ui.download is UpdateDownloadState.Idle)
        assertTrue(h.installs.isEmpty())
    }

    @Test
    fun `手动检查发现新版本 同样只弹窗`() = runTest {
        val h = harness()
        val result = h.manager.checkNow()
        advanceUntilIdle()

        assertTrue(result is UpdateCheckResult.Available)
        assertNotNull(h.manager.dialog.value)
        assertEquals(0, h.downloader.calls)
        assertTrue(h.manager.dialog.value!!.download is UpdateDownloadState.Idle)
    }

    @Test
    fun `后台下载关闭弹窗 下载完成后重新提示安装`() = runTest {
        val h = harness()
        h.manager.checkOnStart()
        advanceUntilIdle()
        h.manager.startBackgroundDownload()
        runCurrent()
        assertNull(h.manager.dialog.value)
        assertEquals(1, h.downloader.calls)

        h.downloader.session.emit(
            UpdateDownloadEvent.Done(DownloadedApk(fileName = "classtools-1.0.0-beta3.apk")),
        )
        advanceUntilIdle()
        assertNotNull(h.manager.dialog.value)
        assertTrue(h.manager.dialog.value!!.download is UpdateDownloadState.Ready)
    }

    @Test
    fun `检查已是最新 不弹窗不下载`() = runTest {
        val h = harness(json = upToDateJson)
        h.manager.checkOnStart()
        advanceUntilIdle()
        assertNull(h.manager.dialog.value)
        assertEquals(0, h.downloader.calls)
        assertTrue(h.manager.checkStatus.value is UpdateCheckStatus.UpToDate)
    }

    @Test
    fun `检查失败 不弹窗不下载`() = runTest {
        val h = harness(
            json = """{"versionCode":0}""",
        )
        h.manager.checkOnStart()
        advanceUntilIdle()
        assertNull(h.manager.dialog.value)
        assertEquals(0, h.downloader.calls)
        assertTrue(h.manager.checkStatus.value is UpdateCheckStatus.Failed)
    }

    @Test
    fun `自动检查同一进程只触发一次`() = runTest {
        val h = harness()
        h.manager.checkOnStart()
        advanceUntilIdle()
        val firstDialog = h.manager.dialog.value
        h.manager.cancelDownload()
        h.manager.dismissDialog()
        h.manager.checkOnStart()
        advanceUntilIdle()
        assertNull(h.manager.dialog.value)
        assertNotNull(firstDialog)
    }

    // ------------------------------------------------------------ 自动下载/幂等

    @Test
    fun `下载幂等 再次点击不重复 enqueue`() = runTest {
        val h = harness()
        h.manager.checkOnStart()
        advanceUntilIdle()
        assertEquals(0, h.downloader.calls)

        h.manager.startDownload()
        runCurrent()
        assertEquals(1, h.downloader.calls)

        h.manager.startDownload()
        runCurrent()
        assertEquals(1, h.downloader.calls)
    }

    @Test
    fun `下载开始失败 状态为 Failed 可重试`() = runTest {
        val h = harness(downloader = FakeDownloader().apply { failStart = true })
        h.manager.checkOnStart()
        advanceUntilIdle()
        h.manager.startDownload()
        runCurrent()
        assertTrue(h.manager.dialog.value!!.download is UpdateDownloadState.Failed)
    }

    @Test
    fun `下载完成 Ready 不自动拉起安装器`() = runTest {
        val h = harness()
        h.manager.checkOnStart()
        advanceUntilIdle()
        h.manager.startDownload()
        runCurrent()

        h.downloader.session.emit(
            UpdateDownloadEvent.Done(DownloadedApk(fileName = "classtools-1.0.0-beta3.apk")),
        )
        advanceUntilIdle()

        val ui = h.manager.dialog.value!!
        assertTrue(ui.download is UpdateDownloadState.Ready)
        assertTrue(h.installs.isEmpty()) // 没有自动安装
        assertFalse(ui.installLaunched)
    }

    @Test
    fun `用户点立即安装 才拉起安装器`() = runTest {
        val h = harness()
        h.manager.checkOnStart()
        advanceUntilIdle()
        h.manager.startDownload()
        runCurrent()
        h.downloader.session.emit(
            UpdateDownloadEvent.Done(DownloadedApk(fileName = "classtools-1.0.0-beta3.apk")),
        )
        advanceUntilIdle()
        assertTrue(h.installs.isEmpty())

        h.manager.install()
        assertEquals(1, h.installs.size)
        assertTrue(h.manager.dialog.value!!.installLaunched)
    }

    @Test
    fun `下载失败事件 状态为 Failed 不安装`() = runTest {
        val h = harness()
        h.manager.checkOnStart()
        advanceUntilIdle()
        h.manager.startDownload()
        runCurrent()
        h.downloader.session.emit(UpdateDownloadEvent.Failed("网络不可用"))
        advanceUntilIdle()

        assertEquals(
            UpdateDownloadState.Failed("网络不可用"),
            h.manager.dialog.value!!.download,
        )
        assertTrue(h.installs.isEmpty())
    }

    @Test
    fun `取消下载 回到 Idle 并取消会话`() = runTest {
        val h = harness()
        h.manager.checkOnStart()
        advanceUntilIdle()
        h.manager.startDownload()
        runCurrent()
        assertFalse(h.downloader.session.cancelled)

        h.manager.cancelDownload()
        advanceUntilIdle()

        assertTrue(h.downloader.session.cancelled)
        assertTrue(h.manager.dialog.value!!.download is UpdateDownloadState.Idle)
    }

    // ------------------------------------------------------------ force

    @Test
    fun `force 版本不能稍后也不能取消下载`() = runTest {
        val h = harness(json = forcedJson)
        h.manager.checkOnStart()
        advanceUntilIdle()
        val ui = h.manager.dialog.value!!
        assertTrue(ui.info.force)
        assertFalse(UpdateDialogPolicy.isDismissible(ui.info))

        h.manager.dismissDialog()
        assertNotNull(h.manager.dialog.value) // force 不关闭

        h.manager.startDownload()
        runCurrent()
        h.manager.cancelDownload()
        assertFalse(h.downloader.session.cancelled) // force 不允许取消
        assertTrue(h.manager.dialog.value!!.download is UpdateDownloadState.Downloading)
    }

    // ------------------------------------------------------------ 稍后

    @Test
    fun `非强制版本稍后关闭并记录版本`() = runTest {
        val h = harness()
        h.manager.checkOnStart()
        advanceUntilIdle()
        assertEquals(86, h.manager.dialog.value!!.info.versionCode)

        // 未下载时「稍后」直接关闭并记录版本，不会启动下载。
        h.manager.dismissDialog()
        assertNull(h.manager.dialog.value)
        assertEquals(86, h.store.dismissed)
        assertEquals(0, h.downloader.calls)
    }
}




