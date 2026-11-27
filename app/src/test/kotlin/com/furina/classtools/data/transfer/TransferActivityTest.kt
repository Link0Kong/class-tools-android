package com.furina.classtools.data.transfer

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.net.Uri
import androidx.work.Configuration
import androidx.work.WorkManager
import com.furina.classtools.data.cloudspace.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, instrumentedPackages = ["com.vivo.push.sdk"])
class TransferActivityTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val owner = "3260799999"
    private val notifications get() = app.getSystemService(NotificationManager::class.java).activeNotifications

    @Before fun prepare() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        if (runCatching { WorkManager.getInstance(app) }.isFailure)
            WorkManager.initialize(app, Configuration.Builder().setTaskExecutor { }.build())
    }

    private fun upload(store: TransferActivities, contents: String): TransferActivity {
        val file = File.createTempFile("transfer-qa", ".txt", app.cacheDir).apply { writeText(contents) }
        store.startUpload(owner, "file", Uri.fromFile(file), "文件", contents, file.length())
        return store.tasks.value.single { it.title == contents }
    }

    @Test fun concurrentUploadsSurviveLeavingPageAndDropRetriesOnlyTheUnacknowledgedChunk() = runBlocking {
        val store = TransferActivities(app)
        val first = upload(store, "ABCDEF")
        val second = upload(store, "second")
        val chunkStarted = CompletableDeferred<Unit>()
        val finishChunk = CompletableDeferred<Unit>()
        val firstTransport = FakeCloudSpaceTransport { req ->
            when {
                req.url.endsWith("/mine") -> CloudSpaceResponse(200, """{"ok":true,"session":{"id":"s"}}""")
                req.url.endsWith("/upload/init") -> CloudSpaceResponse(200, """{"ok":true,"uploadId":"one","chunkSize":3}""")
                req.url.contains("index=0") -> { chunkStarted.complete(Unit); finishChunk.await(); CloudSpaceResponse(200, """{"ok":true}""") }
                req.url.contains("index=1") -> throw IOException("test offline")
                else -> error(req.url)
            }
        }
        fun transport(id: String) = FakeCloudSpaceTransport { req ->
            when {
                req.url.endsWith("/mine") -> CloudSpaceResponse(200, """{"ok":true,"session":{"id":"s"}}""")
                req.url.endsWith("/upload/init") -> CloudSpaceResponse(200, """{"ok":true,"uploadId":"$id","chunkSize":3}""")
                req.url.contains("/upload/chunk") -> CloudSpaceResponse(200, """{"ok":true}""")
                req.url.endsWith("/upload/finish") -> CloudSpaceResponse(200,
                    """{"ok":true,"file":{"id":"file-$id","name":"文件","size":6,"downloadUrl":"https://example.invalid/file"}}""")
                else -> error(req.url)
            }
        }
        fun repo(transport: FakeCloudSpaceTransport) = CloudSpaceRepository(testCloudSpaceApi(transport), chunkSize = 3)
        val execution = TransferExecution(app, store)
        val pending = async { execution.runFile(first, repo(firstTransport)) }
        kotlinx.coroutines.withTimeout(15_000L) { chunkStarted.await() }
        execution.runFile(second, repo(transport("two")))
        assertEquals(TransferStage.UPLOADED, store.find(owner, "file", second.id)!!.stage)
        finishChunk.complete(Unit)
        pending.await()
        val reopened = TransferActivities(app)
        val resumed = reopened.find(owner, "file", first.id)!!
        assertEquals(1, resumed.nextChunk)
        assertEquals(50, resumed.percent)
        val afterRestart = transport("one")
        TransferExecution(app, reopened).runFile(resumed, repo(afterRestart))
        assertTrue(afterRestart.paths().none { it.contains("/upload/init") || it.contains("index=0") })
        assertTrue(afterRestart.paths().any { it.contains("uploadId=one&index=1") })
        assertEquals("file-one", reopened.find(owner, "file", first.id)!!.remoteId)
        assertEquals(2, notifications.count { it.id == 7411 })
        assertFalse(notifications.any { it.id == 7401 })
    }

    @Test fun conversionRechecksServerAndHonorsHiddenGenerationAcrossRetries() = runBlocking {
        val store = TransferActivities(app)
        val file = CloudSpaceFile("f1", "课件.pptx", 600, downloadUrl = "https://example.invalid/file")
        var status = ""
        var attempt = 0
        var submits = 0
        val transport = FakeCloudSpaceTransport { req ->
            val conversion = if (status.isEmpty()) "" else ""","conversion":{"status":"$status","attempt":$attempt,"error":"${if (status == "failed") "测试失败" else ""}"}"""
            when {
                req.url.endsWith("/mine") -> CloudSpaceResponse(200, """{"ok":true,"session":{"id":"s"}}""")
                req.url.contains("/state?") -> CloudSpaceResponse(200,
                    """{"ok":true,"session":{"id":"s"},"pptConversion":true,"events":[],"files":[{"id":"f1","name":"课件.pptx","size":600,"downloadUrl":"https://example.invalid/f"$conversion}]}""")
                req.url.endsWith("/ppt/f1") -> {
                    if (submits > 0) assertTrue(req.body!!.decodeToString().contains("\"retryAttempt\":1"))
                    status = "queued"; attempt++; submits++
                    CloudSpaceResponse(200, """{"ok":true}""")
                }
                req.url.contains("/ppt/f1?") -> CloudSpaceResponse(200,
                    """{"ok":true,"conversion":{"status":"ready","attempt":2},"manifest":{"files":{"pdf":{"s":"/drop/ppt-result/s/f1/pdf?sig=test","name":"结果.pdf"}}}}""")
                else -> error(req.url)
            }
        }
        val repo = CloudSpaceRepository(testCloudSpaceApi(transport))
        val execution = TransferExecution(app, store)
        fun task() = store.find(owner, "file", "f1")!!
        store.convert(owner, file)
        val initial = task()
        execution.runFile(initial, repo)
        assertEquals(TransferStage.QUEUED, task().stage)
        store.hide(owner, "file", initial.id, initial.generation)
        status = "processing"
        execution.runFile(task(), repo)
        assertEquals("转换中", task().label)
        assertFalse(notifications.any { it.id == 7401 })
        status = "failed"
        execution.runFile(task(), repo)
        assertEquals("测试失败", task().label)
        store.convert(owner, file.copy(conversion = DropConversion("failed", null, "测试失败", attempt)))
        store.hide(owner, "file", initial.id, initial.generation)
        assertFalse(task().hidden)
        execution.runFile(task(), repo)
        assertEquals(2, submits)
        status = "ready"
        execution.runFile(task(), repo)
        assertEquals(TransferStage.READY, task().stage)
        assertFalse(notifications.any { it.id == 7401 })
        assertTrue(notifications.any { it.id == 7411 })
        assertEquals("pdf", (repo.assets("f1") as CloudSpaceResult.Success).value.single().key)
        store.sessionChanged("9999000002")
        assertTrue(store.tasks.value.isEmpty())
        assertTrue(notifications.isEmpty())
    }

    @Test fun anExplicitRefusalEndsTheUploadAndAnUncertainFinishNeverCreatesAnotherFile() = runBlocking {
        val store = TransferActivities(app)
        val execution = TransferExecution(app, store)
        val refused = upload(store, "refused")
        val unknown = upload(store, "pending")
        fun repo(denied: Boolean): CloudSpaceRepository {
            val transport = FakeCloudSpaceTransport { req ->
                when {
                    req.url.endsWith("/mine") -> CloudSpaceResponse(200, """{"ok":true,"session":{"id":"s"}}""")
                    req.url.endsWith("/upload/init") -> CloudSpaceResponse(200, """{"ok":true,"uploadId":"u1","chunkSize":3}""")
                    req.url.contains("/upload/chunk") -> if (denied) CloudSpaceResponse(507, """{"ok":false,"reason":"存储不足"}""")
                        else CloudSpaceResponse(200, """{"ok":true}""")
                    req.url.endsWith("/upload/finish") -> throw IOException("lost acknowledgement")
                    else -> error(req.url)
                }
            }
            return CloudSpaceRepository(testCloudSpaceApi(transport), chunkSize = 3)
        }
        execution.runFile(refused, repo(true))
        assertEquals(TransferStage.FAILED, store.find(owner, "file", refused.id)!!.stage)
        assertEquals("存储不足", store.find(owner, "file", refused.id)!!.label)
        execution.runFile(unknown, repo(false))
        val pending = store.find(owner, "file", unknown.id)!!
        assertEquals(TransferStage.UNCONFIRMED, pending.stage)
        assertTrue(pending.uri.isEmpty())
        assertFalse(pending.active)
    }

    @Test fun queuedClassDeckSurvivesOfflineAndReadyResultOpensItsOwnObject() = runBlocking {
        val store = TransferActivities(app)
        val deck = com.furina.classtools.data.ppt.PptDeck("dqa123456", title = "课件", status = "failed", updatedAt = 123)
        store.retryDeck(owner, deck)
        var status = "failed"
        var offline = true
        var patches = 0
        val transport = com.furina.classtools.FakeTransport { request ->
            if (offline) throw IOException("offline")
            when (request.method) {
                com.furina.classtools.data.network.HttpMethod.GET -> com.furina.classtools.data.network.ApiResponse(200,
                    """[{"id":"dqa123456","status":"$status","updated_at":123}]""")
                com.furina.classtools.data.network.HttpMethod.PATCH -> {
                    assertTrue(request.url.contains("status=eq.failed&updated_at=eq.123"))
                    patches++
                    status = "queued"
                    com.furina.classtools.data.network.ApiResponse(200, "[]")
                }
                else -> error(request.url)
            }
        }
        val api = com.furina.classtools.data.network.ApiClient("https://example.invalid", "public", { "qa" },
            transport, maxRetries = 0)
        val repo = com.furina.classtools.data.ppt.DefaultPptRepository(api)
        val execution = TransferExecution(app, store)
        fun current() = store.find(owner, "deck", deck.id)!!
        execution.runDeck(current(), repo)
        assertTrue(current().active)
        offline = false
        execution.runDeck(current(), repo)
        assertEquals(1, patches)
        assertEquals(TransferStage.QUEUED, current().stage)
        status = "processing"
        execution.runDeck(current(), repo)
        assertEquals("转换中", current().label)
        status = "ready"
        execution.runDeck(current(), repo)
        assertEquals(TransferStage.READY, current().stage)
        val click = shadowOf(notifications.single().notification.contentIntent).savedIntent
        assertEquals(listOf(owner, deck.id), click.data!!.pathSegments)
        assertEquals("deck", click.data!!.authority)
    }

    @Test fun deniedNotificationsKeepTaskAndCancelledUploadsIgnoreLateEvents() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val store = TransferActivities(app)
        val task = upload(store, "content")
        assertTrue(task.active)
        assertTrue(notifications.isEmpty())
        store.cancelUpload(owner, "file", task.id)
        assertNull(store.update(task) { it.copy(stage = TransferStage.UPLOADED) })
        assertEquals(TransferStage.CANCELED, TransferActivities(app).find(owner, "file", task.id)!!.stage)
    }
}
