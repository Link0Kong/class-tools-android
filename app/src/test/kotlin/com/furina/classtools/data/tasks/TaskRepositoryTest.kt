package com.furina.classtools.data.tasks

import com.furina.classtools.FakeTransport
import com.furina.classtools.testApiClient
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TaskRepositoryTest {
    private class MemoryStorage : TaskStorage {
        val values = mutableMapOf<String, String>()
        override fun read(key: String) = values[key]
        override fun save(local: JsonObject, base: JsonObject?) {
            values["local"] = local.toString()
            if (base != null) values["base"] = base.toString()
        }
    }

    private val sid = "3260799999"
    private fun row(ts: Long, data: JsonObject) = buildJsonObject { put("id", sid); put("ts", ts); put("blob", data.toString()) }
    private fun doc(vararg items: JsonObject) = emptyTasks.withFields { put("items", JsonArray(items.toList())) }

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
