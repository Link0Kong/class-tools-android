package com.furina.classtools.data.tasks

import com.furina.classtools.data.network.ApiRequest
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpMethod
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskAiClientTest {
    @Test
    fun sendsOnlySourceAndReferenceDateAndMapsDrafts() = runBlocking {
        var request: ApiRequest? = null
        val client = TaskAiClient(transport = {
            request = it
            ApiResponse(200, """{"drafts":[{"title":"提交报告","notes":"上传 PDF","due":"2026-11-27","dueTime":"","steps":[{"text":"整理资料","note":"保留原始数据","done":true}],"suggestions":["提交前检查文件名"]},{"title":"校园活动报名","due":"2026-02-30","dueTime":"18:00"}]}""")
        }) { "test-token" }

        val result = client.suggest("明天交报告，另外报名校园活动", LocalDate.of(2026, 11, 26)) as TaskAiResult.Success
        assertEquals(HttpMethod.POST, request?.method)
        assertEquals("https://tool.furina.fun/task-ai/suggest", request?.url)
        assertEquals(mapOf("X-Class-Token" to "test-token"), request?.headers)
        val payload = Json.parseToJsonElement(requireNotNull(request?.body)).jsonObject
        assertEquals(setOf("text", "localDate"), payload.keys)
        assertEquals("2026-11-26", payload.text("localDate"))
        assertEquals(listOf("提交报告", "校园活动报名"), result.drafts.map { it.title })
        val first = result.drafts[0].fill(newTask("", 10, 1.0))
        assertEquals("2026-11-27", first.text("due"))
        assertEquals("", first.text("dueTime"))
        assertEquals("上传 PDF\n\n完成建议\n• 提交前检查文件名", first.text("notes"))
        assertEquals("保留原始数据", first.objects("steps").single().text("note"))
        assertFalse(first.objects("steps").single().flag("done"))
        assertEquals("", result.drafts[1].due)
        assertEquals("", result.drafts[1].dueTime)
    }

    @Test
    fun handlesEmptyMalFormedAndUnavailableResponses() = runBlocking {
        val empty = TaskAiClient(transport = { ApiResponse(200, """{"drafts":[]}""") }) { "test-token" }
        assertEquals(TaskAiResult.Success(emptyList()), empty.suggest("你好"))
        val invalid = TaskAiClient(transport = { ApiResponse(200, """{"drafts":[{"title":17}]}""") }) { "test-token" }
        assertTrue(invalid.suggest("准备材料") is TaskAiResult.Failure)
        val unavailable = TaskAiClient(transport = { ApiResponse(503) }) { "test-token" }
        assertTrue(unavailable.suggest("准备材料") is TaskAiResult.Failure)
        val offline = TaskAiClient(transport = { throw IOException() }) { "test-token" }
        assertTrue(offline.suggest("准备材料") is TaskAiResult.Failure)
    }

    @Test
    fun rejectsEmptyOversizedAndUnauthenticatedRequestsLocally() = runBlocking {
        val rejecting = TaskAiClient(transport = { error("unexpected request") }) { "test-token" }
        val signedOut = TaskAiClient(transport = { error("unexpected request") }) { null }
        assertTrue(rejecting.suggest("   ") is TaskAiResult.Failure)
        assertTrue(rejecting.suggest("工".repeat(6001)) is TaskAiResult.Failure)
        assertTrue(signedOut.suggest("打印资料") is TaskAiResult.Failure)
    }
}
