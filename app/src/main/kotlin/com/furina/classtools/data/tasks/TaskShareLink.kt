package com.furina.classtools.data.tasks

import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate
import kotlinx.serialization.json.*

internal data class SharedTask(
    val title: String,
    val dueLabel: String,
    val groupLabel: String,
    val notes: String,
    val steps: List<String>,
) {
    fun draft(now: Long, order: Double): JsonObject = newTask(title, now, order).withFields {
        put("notes", notes)
        put("steps", JsonArray(steps.map { buildJsonObject { put("text", it); put("done", false) } }))
    }
}

internal object TaskShareLink {
    const val MAX_INPUT = 8192

    /** 旧分享格式：快照放在主域根路径的 hash 里（继续可读）。 */
    private const val LEGACY_PREFIX = "https://tool.furina.fun/#task-share="

    /** 统一内容链接（docs/app-links/CONTRACT.md §3）：路径固定 /l/task-share，快照只在片段里。 */
    private const val LINK_PREFIX = "https://tool.furina.fun/l/task-share#s="

    private fun String.clipped(limit: Int) = take(limit).dropLastWhile(Char::isHighSurrogate)

    fun create(task: JsonObject, groupName: String): String {
        val payload = buildJsonObject {
            put("t", task.text("title").clipped(60))
            put("d", taskDueLabel(task, LocalDate.now().toString()).clipped(32))
            put("l", groupName.ifBlank { "未分组" }.clipped(12))
            put("n", "")
            put("s", JsonArray(task.objects("steps").take(3).map { JsonPrimitive(it.text("text").clipped(40)) }))
        }
        val encoded = URLEncoder.encode(payload.toString(), "UTF-8").replace("+", "%20")
            .replace("%21", "!").replace("%27", "'").replace("%28", "(").replace("%29", ")").replace("%7E", "~")
        return LINK_PREFIX + encoded
    }

    fun parse(input: String): SharedTask? {
        if (input.length !in 1..MAX_INPUT) return null
        val text = input.trim()
        val value = when {
            text.startsWith(LINK_PREFIX) -> text.removePrefix(LINK_PREFIX)
            text.startsWith(LEGACY_PREFIX) -> text.removePrefix(LEGACY_PREFIX)
            text.startsWith("#s=") -> text.removePrefix("#s=")
            text.startsWith("#task-share=") -> text.removePrefix("#task-share=")
            text.startsWith("shared=") || text.startsWith("?shared=") -> text.substringAfter("shared=")
            text.removePrefix("/").startsWith("pages/task/task?") ->
                text.substringAfter('?').split('&').firstOrNull { it.startsWith("shared=") }?.substringAfter('=') ?: return null
            text.startsWith("{") || text.startsWith("%7B", ignoreCase = true) -> text
            else -> return null
        }
        return runCatching {
            val json = if (value.startsWith("{")) value else URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
            val payload = Json.parseToJsonElement(json).jsonObject
            fun field(key: String, limit: Int) = (payload[key] as? JsonPrimitive)
                ?.takeIf { it.isString }?.content?.trim()?.clipped(limit).orEmpty()
            val title = field("t", 100).ifBlank { return null }
            SharedTask(title, field("d", 32), field("l", 20), field("n", 500),
                (payload["s"] as? JsonArray).orEmpty().take(6).mapNotNull {
                    (it as? JsonPrimitive)?.takeIf { v -> v.isString }?.content?.trim()?.clipped(120)?.takeIf(String::isNotBlank)
                })
        }.getOrNull()
    }
}
