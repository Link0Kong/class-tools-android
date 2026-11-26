package com.furina.classtools.data.tasks

import com.furina.classtools.data.network.ApiRequest
import com.furina.classtools.data.network.HttpMethod
import com.furina.classtools.data.network.HttpTransport
import com.furina.classtools.data.network.OkHttpTransport
import com.furina.classtools.data.network.parseJsonOrNull
import java.io.IOException
import java.io.InterruptedIOException
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val TASK_AI_INPUT_LIMIT = 6000
internal const val TASK_AI_DRAFT_LIMIT = 8

internal data class TaskAiDraft(
    val title: String,
    val notes: String,
    val due: String,
    val dueTime: String,
    val steps: List<JsonObject>,
) {
    fun fill(task: JsonObject): JsonObject = task.withFields {
        put("title", title)
        put("notes", notes)
        put("due", due)
        put("dueTime", dueTime)
        put("steps", JsonArray(steps))
    }
}

internal sealed interface TaskAiResult {
    data class Success(val drafts: List<TaskAiDraft>) : TaskAiResult
    data class Failure(val message: String) : TaskAiResult
}

class TaskAiClient(
    private val transport: HttpTransport = OkHttpTransport(
        OkHttpTransport.defaultClient().newBuilder().followRedirects(false).followSslRedirects(false).build(),
    ),
    private val tokenProvider: () -> String?,
) {
    internal suspend fun suggest(text: String, localDate: LocalDate = LocalDate.now()): TaskAiResult {
        val source = text.trim()
        if (source.isEmpty() || source.length > TASK_AI_INPUT_LIMIT) return TaskAiResult.Failure("请输入 1 至 6000 字的任务文字")
        val token = tokenProvider()?.takeIf(String::isNotBlank) ?: return TaskAiResult.Failure("请核验班级身份后再使用")
        val body = buildJsonObject {
            put("text", source)
            put("localDate", localDate.toString())
        }
        val response = try {
            transport.execute(ApiRequest(HttpMethod.POST, "https://tool.furina.fun/task-ai/suggest",
                mapOf("X-Class-Token" to token), body.toString(), timeoutMs = 30_000))
        } catch (_: InterruptedIOException) {
            return TaskAiResult.Failure("整理超时，请重试")
        } catch (_: IOException) {
            return TaskAiResult.Failure("网络连接失败，请检查网络后重试")
        }
        if (response.status == 200) return parseDrafts(response.body)
        return TaskAiResult.Failure(when (response.status) {
            400 -> "文字格式有误，请检查后重试"
            401, 403 -> "班级身份已失效，请重新登录后重试"
            429 -> "整理次数过多，请稍后重试"
            504 -> "整理超时，请重试"
            else -> "云端 AI 暂不可用，请稍后重试"
        })
    }

    private fun parseDrafts(body: String): TaskAiResult {
        val obj = parseJsonOrNull(Json, body) as? JsonObject
        val entries = obj?.get("drafts") as? JsonArray
            ?: return TaskAiResult.Failure("云端返回的草稿格式有误，请重试")
        val drafts = entries.take(TASK_AI_DRAFT_LIMIT).mapNotNull { entry ->
            val draft = entry as? JsonObject ?: return@mapNotNull null
            val title = draft.string("title").take(200).trim()
            if (title.isBlank()) return@mapNotNull null
            val due = draft.string("due").let { value ->
                value.takeIf { it.length == 10 && runCatching { LocalDate.parse(it) }.isSuccess }.orEmpty()
            }
            val time = draft.string("dueTime").takeIf {
                due.isNotEmpty() && it.length == 5 && runCatching { LocalTime.parse(it) }.isSuccess
            }.orEmpty()
            val steps = draft.objects("steps").take(12).mapNotNull { step ->
                val label = step.string("text").take(120).trim()
                if (label.isBlank()) null else buildJsonObject {
                    put("text", label)
                    put("note", step.string("note").take(500))
                    put("done", false)
                }
            }
            val suggestions = (draft["suggestions"] as? JsonArray).orEmpty().mapNotNull {
                (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content?.trim()?.take(300)?.takeIf(String::isNotBlank)
            }.take(3)
            val advice = if (suggestions.isEmpty()) "" else suggestions.joinToString("\n", "完成建议\n") { "• $it" }
            val notes = listOf(draft.string("notes").take(5000 - advice.length - 2), advice)
                .filter(String::isNotBlank).joinToString("\n\n")
            TaskAiDraft(title, notes, due, time, steps)
        }
        return if (entries.isNotEmpty() && drafts.isEmpty()) {
            TaskAiResult.Failure("云端返回的草稿格式有误，请重试")
        } else TaskAiResult.Success(drafts)
    }

    private fun JsonObject.string(key: String): String = (get(key) as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)?.content?.trim().orEmpty()
}
