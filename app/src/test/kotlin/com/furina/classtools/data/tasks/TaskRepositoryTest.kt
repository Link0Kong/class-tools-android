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
}
