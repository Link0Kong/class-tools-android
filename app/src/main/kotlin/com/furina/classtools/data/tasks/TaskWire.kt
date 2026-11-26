package com.furina.classtools.data.tasks

import kotlinx.serialization.json.*

internal const val MAX_TASK_STAMP = 9_007_199_254_740_991L

internal fun JsonElement?.taskStamp(): Long? {
    val number = this as? JsonPrimitive ?: return null
    return number.longOrNull?.takeIf { it in 0..MAX_TASK_STAMP && number.content == it.toString() }
}

data class TaskRecovery internal constructor(val legacy: Boolean, internal val anchor: JsonObject)

internal data class TaskAttempt(val sent: JsonObject, val snapshot: JsonObject, val blob: String, val stamp: Long) {
    fun encode() = buildJsonObject { put("sent", sent); put("blob", blob); put("ts", stamp) }.toString()
    companion object {
        fun read(value: String): TaskAttempt? {
            val attempt = runCatching { Json.parseToJsonElement(value).jsonObject }.getOrNull() ?: return null
            val sent = TaskWire.decode(attempt["sent"])?.snapshot ?: return null
            val stamp = attempt["ts"].taskStamp() ?: return null
            val blob = attempt.text("blob")
            val snapshot = TaskWire.decode(attempt["blob"], stamp)?.snapshot ?: return null
            return TaskAttempt(sent, snapshot, blob, stamp)
        }
    }
}

internal fun JsonObject.sameTaskContents(other: JsonObject): Boolean {
    fun contents(doc: JsonObject) = JsonObject(doc - "updatedAt").withFields { put("lists", JsonArray(doc.objects("lists"))) }
    return contents(this) == contents(other)
}

internal fun addUnmatchedDraftEntries(draft: JsonObject, remote: JsonObject): JsonObject {
    val additions = draft.withFields {
        for (key in listOf("items", "lists")) {
            val confirmed = remote.objects(key).mapTo(mutableSetOf()) { it.text("id") }
            put(key, JsonArray(draft.objects(key).filter { it.text("id") !in confirmed }))
        }
        put("prefs", JsonObject(draft.prefs() - remote.prefs().keys))
    }
    return mergeTasks(emptyTasks, additions, remote)
}

/** Older adapters wrapped the task document; the REST column itself is text. */
internal class TaskWire private constructor(
    private val root: JsonObject,
    private val path: List<String>,
    val snapshot: JsonObject,
) {
    fun encode(snapshot: JsonObject): String {
        fun replace(node: JsonObject, depth: Int): JsonObject {
            if (depth == path.size) return snapshot
            val key = path[depth]
            return node.withFields { put(key, replace(node.getValue(key).jsonObject, depth + 1)) }
        }
        return replace(root, 0).toString()
    }

    companion object {
        val empty = TaskWire(emptyTasks, emptyList(), emptyTasks)

        fun decode(blob: JsonElement?, stamp: Long? = null): TaskWire? {
            val root = when {
                blob is JsonObject -> blob
                blob is JsonPrimitive && blob.isString -> {
                    if (blob.content.isEmpty() && stamp == 0L) return empty
                    runCatching { Json.parseToJsonElement(blob.content) as? JsonObject }.getOrNull()
                }
                else -> null
            } ?: return null
            val path = mutableListOf<String>()
            var node = root
            while (true) {
                if (node["items"] is JsonArray) {
                    if (!validIds(node["items"]) || ("lists" in node && !validIds(node["lists"]))) return null
                    return TaskWire(root, path, node)
                }
                if (path.size == 3) return null
                val key = listOf("task", "data", "v").find { node[it] is JsonObject } ?: return null
                path += key
                node = node.getValue(key).jsonObject
            }
        }

        private fun validIds(value: JsonElement?): Boolean {
            val rows = value as? JsonArray ?: return false
            val ids = mutableSetOf<String>()
            return rows.all { row ->
                val id = (row as? JsonObject)?.get("id") as? JsonPrimitive
                id != null && id.isString && id.content.isNotBlank() && ids.add(id.content)
            }
        }
    }
}
