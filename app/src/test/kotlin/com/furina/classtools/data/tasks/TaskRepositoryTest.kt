package com.furina.classtools.data.tasks

import com.furina.classtools.FakeTransport
import com.furina.classtools.testApiClient
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.io.IOException
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TaskRepositoryTest {
    private class MemoryStorage : TaskStorage {
        val values = mutableMapOf<String, String>()
        override fun read(key: String) = values[key]
        override fun checkpointAttempt(value: String?): Boolean {
            if (value == null) values.remove("writeAttempt") else values["writeAttempt"] = value
            return true
        }
        override fun save(local: JsonObject, base: JsonObject?, metadata: Map<String, String?>) {
            values["local"] = local.toString()
            if (base != null) values["base"] = base.toString()
            metadata.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
        }
    }

    private val sid = "3260799999"
    private fun row(ts: Long, data: JsonObject) = buildJsonObject { put("id", sid); put("ts", ts); put("blob", data.toString()) }
    private fun doc(vararg items: JsonObject) = emptyTasks.withFields { put("items", JsonArray(items.toList())) }

    @Test fun cancelledRefreshSettlesAndAConfirmedEmptyAccountCanBeRetried() = runTest {
        val started = CompletableDeferred<Unit>()
        val transport = FakeTransport {
            started.complete(Unit)
            awaitCancellation()
        }
        val repository = TaskRepository(sid, MemoryStorage(), testApiClient(transport), backgroundScope) { true }
        val sync = launch { repository.sync() }
        started.await()
        assertEquals("同步中…", repository.syncStatus.value)

        sync.cancelAndJoin()
        assertEquals("本机缓存", repository.syncStatus.value)

        transport.responder = { ApiResponse(200, "[]") }
        assertEquals(emptyTasks, repository.syncSnapshot())
        assertEquals("已同步", repository.syncStatus.value)
    }

    @Test fun wrappedTextAndAdapterObjectsKeepThePathAndOtherFieldsOnConditionalWrite() = runTest {
        for (adapterObject in listOf(false, true)) {
            val leaf = doc(newTask("旧任务", 1, 0.0)).withFields { put("futureLeaf", 88) }
            val wrapped = buildJsonObject {
                put("futureRoot", "keep"); put("task", buildJsonObject {
                    put("data", buildJsonObject { put("v", leaf); put("side", "keep") })
                })
            }
            var remote = row(17, leaf).withFields { put("blob", if (adapterObject) wrapped else JsonPrimitive(wrapped.toString())) }
            val transport = FakeTransport { request ->
                if (request.method == HttpMethod.PATCH) {
                    assertTrue(request.url.contains("ts=eq.17"))
                    remote = Json.parseToJsonElement(request.body!!).jsonObject
                }
                ApiResponse(200, JsonArray(listOf(remote)).toString())
            }
            val repo = TaskRepository(sid, MemoryStorage(), testApiClient(transport), backgroundScope) { true }
            repo.sync()
            assertEquals("旧任务", repo.snapshot.value.objects("items").single().text("title"))
            repo.importNotice("notice", "来源", "摘要", null)
            repo.sync()
            assertEquals("已同步", repo.syncStatus.value)
            val outer = Json.parseToJsonElement(remote.text("blob")).jsonObject
            assertEquals("keep", outer.text("futureRoot"))
            val envelope = outer.getValue("task").jsonObject.getValue("data").jsonObject
            assertEquals("keep", envelope.text("side"))
            val saved = envelope.getValue("v").jsonObject
            assertEquals(88, saved.number("futureLeaf").toInt())
            assertEquals(setOf("旧任务", "来源"), saved.objects("items").map { it.text("title") }.toSet())
        }
    }

    @Test fun malformedRowsNeverOverwriteRemoteOrDiscardLocalEdits() = runTest {
        val invalid = listOf(
            """[{"blob":"","ts":19}]""", """[{"blob":null,"ts":0}]""", """[{"blob":"{","ts":1}]""",
            """[{"ts":1}]""", """[{"blob":{"items":[]},"ts":-1}]""", """[{"blob":{"items":[]}}]""",
            """[{"blob":{"items":[]},"ts":9007199254740991}]""", """[{"blob":{"items":[]},"ts":9007199254740992}]""",
            """[{"blob":{"items":[{"id":"x"},{"id":"x"}]},"ts":1}]""",
            """[{"blob":{"items":[],"lists":[{}]},"ts":1}]""", """[{},{}]"""
        )
        for (body in invalid) {
            val transport = FakeTransport { ApiResponse(200, body) }
            val storage = MemoryStorage()
            val repo = TaskRepository(sid, storage, testApiClient(transport), backgroundScope) { true }
            repo.importNotice("n", "本机草稿", "", null)
            repo.sync()
            assertEquals(body, 1, transport.requests.size)
            assertTrue(body, repo.syncStatus.value.contains("异常"))
            assertEquals("本机草稿", repo.snapshot.value.objects("items").single().text("title"))
        }
        val emptyStub = TaskWire.decode(JsonPrimitive(""), 0)
        assertEquals(emptyTasks, emptyStub?.snapshot)
        assertNull(TaskWire.decode(JsonPrimitive(" "), 0))
    }

    @Test fun confirmedDeletionWinsAndRemoteOccurrenceIsReusedForSameSeriesAndDate() {
        val old = buildJsonObject { put("id", "gone"); put("title", "旧任务") }
        val remoteRepeat = buildJsonObject { put("id", "web"); put("fromId", "series"); put("due", "2030-01-02") }
        val mobileRepeat = remoteRepeat.withFields { put("id", "phone") }
        val following = mobileRepeat.withFields { put("id", "next"); put("due", "2030-01-03") }
        val result = mergeTasks(doc(old), doc(old.withFields { put("title", "离线编辑") }, mobileRepeat, following), doc(remoteRepeat))
        assertEquals(listOf("web", "next"), result.objects("items").map { it.text("id") })
    }

    @Test fun hiddenReadAfterConfirmedRowIncludingEmptyPreservesDraftWithoutWritingOrClaimingSync() = runTest {
        val item = newTask("已确认任务", 1, 0.0)
        for (known in listOf(doc(item), emptyTasks)) {
            val storage = MemoryStorage()
            var visible = true
            val transport = FakeTransport {
                ApiResponse(200, if (visible) JsonArray(listOf(row(8, known))).toString() else "[]")
            }
            fun open() = TaskRepository(sid, storage, testApiClient(transport), backgroundScope) { true }
            val first = open()
            first.sync()
            val previous = storage.read("base")
            if (known.objects("items").isEmpty()) first.importNotice("note", "离线新建", "", null)
            else first.edit { tasks, _ -> tasks.map { it.withFields { put("title", "离线已编辑") } } }
            val draft = storage.read("local")
            visible = false
            val reopened = open()
            assertNull(reopened.syncSnapshot())
            assertTrue(reopened.syncStatus.value.contains("不可见"))
            assertEquals(draft, storage.read("local"))
            assertEquals(previous, storage.read("base"))
            assertTrue(transport.requests.all { it.method == HttpMethod.GET })
        }
    }

    @Test fun firstInsertRaceAndLostPatchReceiptReconcileByReadingConfirmedDocument() = runTest {
        var remote: JsonObject? = null
        val other = newTask("另一设备", 10, 0.0)
        val transport = FakeTransport { request ->
            when (request.method) {
                HttpMethod.GET -> ApiResponse(200, JsonArray(listOfNotNull(remote)).toString())
                HttpMethod.POST -> {
                    assertTrue(request.headers["Prefer"].orEmpty().contains("ignore-duplicates"))
                    remote = row(19, doc(other))
                    ApiResponse(200, "[]")
                }
                HttpMethod.PATCH -> {
                    assertTrue(request.url.contains("ts=eq.19"))
                    remote = Json.parseToJsonElement(request.body!!).jsonObject
                    throw IOException("fixture response lost")
                }
                else -> error("unexpected")
            }
        }
        val repo = TaskRepository(sid, MemoryStorage(), testApiClient(transport), backgroundScope) { true }
        repo.importNotice("n", "本机", "", null)
        repo.sync()
        assertTrue(repo.syncStatus.value.startsWith("待同步"))
        repo.sync()
        assertEquals("已同步", repo.syncStatus.value)
        assertEquals(setOf("本机","另一设备"), repo.snapshot.value.objects("items").map { it.text("title") }.toSet())
        assertEquals(1, transport.requests.count { it.method == HttpMethod.PATCH })
    }

    @Test fun unexpectedReceiptsExhaustFourAttemptsAndForbiddenKeepsDraft() = runTest {
        var attempts = 0
        var denied = false
        val baseline = row(2, doc())
        val transport = FakeTransport { request ->
            if (request.method == HttpMethod.GET) ApiResponse(200, JsonArray(listOf(baseline)).toString())
            else if (denied) ApiResponse(403, "{}")
            else {
                val sent = Json.parseToJsonElement(request.body!!).jsonObject
                val rows = when (++attempts) {
                    1 -> listOf(sent.withFields { put("id", "other") })
                    2 -> listOf(sent.withFields { put("ts", 1) })
                    3 -> listOf(sent.withFields { put("blob", "{}") })
                    else -> listOf(sent, sent)
                }
                ApiResponse(200, JsonArray(rows).toString())
            }
        }
        val store = MemoryStorage()
        val repo = TaskRepository(sid, store, testApiClient(transport), backgroundScope) { true }
        repo.sync()
        val original = store.read("base")
        repo.importHomework(HomeworkRow("h", content = "本机"))
        repo.sync()
        assertTrue(repo.syncStatus.value.startsWith("待同步"))
        assertEquals(4, attempts)
        denied = true
        repo.sync()
        assertTrue(repo.syncStatus.value.contains("无权限"))
        assertEquals(original, store.read("base"))
        assertEquals(setOf("h"), repo.snapshot.value.importedHomeworkIds())
    }

    @Test fun sameSidRenewalDropsInFlightWriteAndPreservesFreshStorageAndUnknownLegacyBase() = runTest {
        var active = true
        val wait = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val transport = FakeTransport { request ->
            if (request.method == HttpMethod.GET) ApiResponse(200, "[]")
            else {
                started.complete(Unit); wait.await()
                ApiResponse(200, "[" + request.body + "]")
            }
        }
        val store = MemoryStorage()
        val old = TaskRepository(sid, store, testApiClient(transport), backgroundScope) { active }
        old.importNotice("old", "之前", "", null)
        val send = launch { old.sync() }
        started.await()
        active = false
        val fresh = doc(newTask("新会话", 1, 0.0))
        store.save(fresh, fresh)
        wait.complete(Unit)
        send.join()
        assertEquals(fresh.toString(), store.read("base"))
        assertEquals(fresh.toString(), store.read("local"))
        store.values.remove("base")
        val newRepo = TaskRepository(sid, store, testApiClient(transport), backgroundScope) { true }
        newRepo.sync()
        assertTrue(newRepo.syncStatus.value.contains("核对"))
        assertEquals(fresh.toString(), store.read("local"))
        assertEquals(1, transport.requests.count { it.method == HttpMethod.POST })
    }

    @Test fun missingBaselineCanBeReviewedWithoutDeletingCloudRowsAndPreservesExplicitNewEdits() = runTest {
        fun task(id: String, title: String) = buildJsonObject { put("id", id); put("title", title) }
        val legacy = doc(task("same", "旧稿"), task("draft", "仅本机"))
        val storage = MemoryStorage().apply { save(legacy) }
        var remote = row(21, doc(task("same", "已核实的云端标题"), task("cloud", "仅云端")))
        val transport = FakeTransport { request ->
            if (request.method == HttpMethod.PATCH) remote = Json.parseToJsonElement(request.body!!).jsonObject
            ApiResponse(200, JsonArray(listOf(remote)).toString())
        }
        fun open() = TaskRepository(sid, storage, testApiClient(transport), backgroundScope) { true }
        val first = open()
        first.sync()
        assertTrue(first.recovery.value!!.legacy)
        first.deferRecovery()
        first.edit { items, _ -> items.map { if (it.text("id") == "same") it.withFields { put("notes", "刚输入") } else it } }
        val next = open()
        next.sync()
        assertEquals(0, transport.requests.count { it.method == HttpMethod.PATCH })
        next.resolveRecovery()
        val items = next.snapshot.value.objects("items").associateBy { it.text("id") }
        assertEquals(setOf("same","cloud","draft"), items.keys)
        assertEquals("已核实的云端标题", items["same"]!!.text("title"))
        assertEquals("刚输入", items["same"]!!.text("notes"))
        assertEquals("已同步", next.syncStatus.value)
        assertTrue(storage.read("recoveryArchive")!!.contains("旧稿"))
        assertEquals(1, transport.requests.count { it.method == HttpMethod.PATCH })
    }

    @Test fun lostReceiptSurvivesRestartAndLaterEditsNeverSilentlyOverwriteConfirmedChanges() = runTest {
        for (sameField in listOf(false,true)) {
            val item = newTask("初始", 1, 0.0)
            val store = MemoryStorage()
            var remote = row(3, doc(item))
            var writes = 0
            val api = testApiClient(FakeTransport { request ->
                if (request.method == HttpMethod.PATCH) {
                    writes++
                    remote = Json.parseToJsonElement(request.body!!).jsonObject
                    if (writes == 1) throw IOException("receipt lost")
                }
                ApiResponse(200, JsonArray(listOf(remote)).toString())
            })
            fun open() = TaskRepository(sid, store, api, backgroundScope) { true }
            val first = open()
            first.sync()
            first.edit { items, _ -> items.map { it.withFields { put("title", "本机已发送") } } }
            first.sync()
            assertNotNull(store.read("writeAttempt"))
            val confirmed = Json.parseToJsonElement(remote.text("blob")).jsonObject
            val cloudItem = confirmed.objects("items").single().withFields {
                put("notes", "后来远端编辑")
                if (sameField) put("title", "远端新标题")
            }
            val stamp = remote.number("ts") + 100
            remote = row(stamp, confirmed.withFields { put("items", JsonArray(listOf(cloudItem))); put("updatedAt", stamp) })
            val reopened = open()
            reopened.sync()
            assertEquals(1, writes)
            if (sameField) {
                assertFalse(reopened.recovery.value!!.legacy)
                assertEquals("本机已发送", reopened.snapshot.value.objects("items").single().text("title"))
                reopened.resolveRecovery()
                assertEquals("远端新标题", reopened.snapshot.value.objects("items").single().text("title"))
            } else assertNull(reopened.recovery.value)
            assertEquals(1, writes)
            assertEquals("后来远端编辑", reopened.snapshot.value.objects("items").single().text("notes"))
            assertEquals("已同步", reopened.syncStatus.value)
            assertNull(store.read("writeAttempt"))
        }
    }

    @Test fun homeworkVisibilityTracksLocalRestartCompletionAndRemoteRemoval() = runTest {
        val store = MemoryStorage()
        val homework = HomeworkRow("hw1", subject = "实验报告")
        var remote = doc(newTask("实验报告", 1, 0.0).withFields { put("sourceNoticeId", homework.id) })
        var version = 1L
        val api = testApiClient(FakeTransport { request ->
            if (request.method == HttpMethod.PATCH) {
                val body = Json.parseToJsonElement(request.body!!).jsonObject
                remote = Json.parseToJsonElement(body.text("blob")).jsonObject
                version = body.number("ts")
            }
            ApiResponse(200, JsonArray(listOf(row(version, remote))).toString())
        })
        val offline = TaskRepository(sid, store, api, backgroundScope) { false }
        assertTrue(offline.importHomework(homework))
        assertEquals(setOf(homework.id), offline.snapshot.value.importedHomeworkIds())
        offline.edit { tasks, now -> changeCompletion(tasks, tasks.single().text("id"), true, now) }
        assertEquals(setOf(homework.id), offline.snapshot.value.importedHomeworkIds())
        val online = TaskRepository(sid, store, api, backgroundScope) { true }
        assertEquals(setOf(homework.id), online.snapshot.value.importedHomeworkIds())
        online.sync()
        assertEquals(2, online.snapshot.value.objects("items").size)
        assertEquals(setOf(homework.id), online.snapshot.value.importedHomeworkIds())
        remote = doc(*remote.objects("items").filterNot { it.text("sourceHwId") == homework.id }.toTypedArray())
        version++
        online.sync()
        assertTrue(online.snapshot.value.importedHomeworkIds().isEmpty())
        assertTrue(online.importHomework(homework))
        online.edit { tasks, _ -> tasks.filterNot { it.text("sourceHwId") == homework.id } }
        assertTrue(online.snapshot.value.importedHomeworkIds().isEmpty())
    }

    @Test fun offlineImportSurvivesRestartAndMergesWithRemote() = runTest {
        val store = MemoryStorage()
        var existing = row(7, doc(newTask("另一台设备", 5, 0.0)).withFields { put("prefs", buildJsonObject { put("sound", false) }) })
        val transport = FakeTransport { request ->
            if (request.method == HttpMethod.PATCH) {
                assertTrue(request.url.contains("ts=eq.7"))
                existing = Json.parseToJsonElement(request.body!!).jsonObject
            }
            ApiResponse(200, JsonArray(listOf(existing)).toString())
        }
        val api = testApiClient(transport)
        val offline = TaskRepository(sid, store, api, backgroundScope) { false }
        val homework = HomeworkRow("hw-class", subject = "实验", content = "报告", date = "2026-11-25")
        assertTrue(offline.importHomework(homework))
        val group = offline.addGroup("实验组")
        assertNotNull(group)
        assertFalse(offline.importHomework(homework))
        offline.sync()
        assertTrue(transport.requests.isEmpty())
        val online = TaskRepository(sid, store, api, backgroundScope) { true }
        online.sync()
        val snapshot = online.snapshot.value
        assertEquals(2, snapshot.objects("items").size)
        assertEquals(group, snapshot.objects("lists").single().text("id"))
        assertEquals("hw-class", snapshot.objects("items").last().text("sourceHwId"))
        assertEquals(false, snapshot["prefs"]!!.jsonObject.flag("sound"))
        assertEquals("已同步", online.syncStatus.value)
        online.sync()
        assertEquals(1, transport.requests.count { it.method == HttpMethod.PATCH })
    }

    @Test fun concurrentRemoteChangeRetriesWithoutLosingOtherFields() = runTest {
        val item = newTask("作业", 1, 0.0)
        var existing = row(1, doc(item))
        var attempts = 0
        val transport = FakeTransport { request ->
            when (request.method) {
                HttpMethod.GET -> ApiResponse(200, JsonArray(listOf(existing)).toString())
                HttpMethod.PATCH -> if (++attempts == 1) {
                    existing = row(9, doc(item.withFields { put("notes", "网页编辑"); put("extension", 42) }))
                    ApiResponse(200, "[]")
                } else {
                    assertTrue(request.url.contains("ts=eq.9"))
                    existing = Json.parseToJsonElement(request.body!!).jsonObject
                    ApiResponse(200, JsonArray(listOf(existing)).toString())
                }
                else -> error("unexpected request")
            }
        }
        val repository = TaskRepository(sid, MemoryStorage(), testApiClient(transport), backgroundScope) { true }
        repository.sync()
        repository.edit { list, now -> changeCompletion(list, item.text("id"), true, now) }
        repository.sync()
        val result = repository.snapshot.value.objects("items").single()
        assertTrue(result.flag("done"))
        assertEquals("网页编辑", result.text("notes"))
        assertEquals(42L, result.number("extension"))
        assertEquals(2, attempts)
        repository.edit { _, _ -> emptyList() }
        repository.sync()
        assertTrue(repository.snapshot.value.objects("items").isEmpty())
    }

    @Test fun recurrencePreservesNotesAndUndoProtectsEditedNextTask() {
        val now = System.currentTimeMillis()
        val parent = newTask("每周", 1L, 0.0).withFields {
            put("repeat", "weekly"); put("due", "2020-01-01")
            put("steps", buildJsonArray { add(buildJsonObject { put("text", "复习"); put("note", "先看第三章"); put("done", true) }) })
        }
        val done = changeCompletion(listOf(parent), parent.text("id"), true, now)
        val next = done.last()
        assertEquals("先看第三章", next.objects("steps").single().text("note"))
        assertFalse(next.objects("steps").single().flag("done"))
        assertTrue(taskDeadline(next)!! > now)
        assertEquals(1, changeCompletion(done, parent.text("id"), false, now + 1).size)
        assertEquals(2, changeCompletion(listOf(done.first(), next.withFields { put("updatedAt", now + 1) }), parent.text("id"), false, now + 2).size)
    }

    @Test fun recentlySyncedTaskRemindersCatchUpOnceTheyAreDiscovered() {
        val now = 1_800_000_000_000L
        assertEquals(120_000L, taskReminderDelay(now + 120_000, now))
        assertEquals(0L, taskReminderDelay(now - 15 * 60_000, now))
        assertEquals(null, taskReminderDelay(now - 24 * 60 * 60_000L, now))
        assertEquals(null, taskReminderDelay(0, now))
    }

    @Test fun reminderRespectsSnoozeCompletionAndPriorDelivery() {
        val task = buildJsonObject { put("remindAt", 1000); put("snoozeUntil", 2000); put("lastNotifyAt", 1000) }
        assertEquals(2000L, taskReminderTime(task))
        assertEquals(0L, taskReminderTime(task.withFields { put("done", true) }))
        assertEquals(0L, taskReminderTime(task.withFields { put("snoozeUntil", 0) }))
    }

    @Test fun taskFilesStayLocalAcrossPushPullAndConcurrentWebEdit() = runTest {
        val base = newTask("有附件的任务", 1, 0.0)
        var remote = row(2, doc(base))
        val api = testApiClient(FakeTransport { request ->
            if (request.method == HttpMethod.PATCH) {
                assertFalse(request.body!!.contains("private-file-token"))
                remote = Json.parseToJsonElement(request.body!!).jsonObject
            }
            ApiResponse(200, JsonArray(listOf(remote)).toString())
        })
        val repository = TaskRepository(sid, MemoryStorage(), api, backgroundScope) { true }
        repository.sync()
        val files = buildJsonArray { add(buildJsonObject { put("id", "private-file-token"); put("n", "记录.pdf") }) }
        repository.edit { items, now -> items.map { it.withFields { put("localFiles", files); put("updatedAt", now) } } }
        remote = row(3, doc(base.withFields { put("notes", "来自网页") }))
        repository.sync()
        repository.sync()
        val task = repository.snapshot.value.objects("items").single()
        assertEquals(files, task["localFiles"])
        assertEquals("来自网页", task.text("notes"))
        assertEquals("已同步", repository.syncStatus.value)
    }

    @Test fun changesToGroupsAndSidebarMergeWithOtherClient() {
        val group = buildJsonObject { put("id", "g1"); put("name", "实验"); put("color", "cy"); put("order", 0) }
        val base = doc().withFields { put("lists", JsonArray(listOf(group))) }
        val local = base.withFields {
            put("lists", JsonArray(listOf(group.withFields { put("name", "作业") })))
            put("prefs", buildJsonObject { put("sidebarOrder", buildJsonArray { add("all") }); put("sidebarOrderAt", 3); put("defaultRemind", "5") })
        }
        val remote = base.withFields {
            put("lists", JsonArray(listOf(group.withFields { put("color", "rose") })))
            put("prefs", buildJsonObject { put("sidebarOrder", buildJsonArray { add("myday") }); put("sidebarOrderAt", 5) })
        }
        val merged = mergeTasks(base, local, remote)
        assertEquals("作业", merged.objects("lists").single().text("name"))
        assertEquals("rose", merged.objects("lists").single().text("color"))
        assertEquals(listOf("myday"), merged.prefs().strings("sidebarOrder"))
        assertEquals("5", merged.prefs().text("defaultRemind"))
        assertTrue(mergeTasks(base, local.withFields { put("lists", JsonArray(emptyList())) }, remote).objects("lists").isEmpty())
    }

    @Test fun webBackupRestoresIdempotentlyAndCalendarIncludesAllDayEndAndAlarm() {
        val task = newTask("报告,第一稿", 1, 0.0).withFields {
            put("due", "2026-11-26"); put("remindAt", 3_600_000); put("notes", "准备\n提交")
            put("localFiles", buildJsonArray { add(buildJsonObject { put("id", "untrusted-file") }) })
        }
        val restored = restoreTaskBackup(emptyTasks, doc(task))
        assertNull(restored.objects("items").single()["localFiles"])
        assertEquals(restored, restoreTaskBackup(restored, taskBackup(restored)))
        val ics = taskCalendar(restored)
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261126\r\nDTEND;VALUE=DATE:20261127"))
        assertTrue(ics.contains("BEGIN:VALARM"))
        assertTrue(ics.contains("SUMMARY:报告\\,第一稿"))
    }
}
