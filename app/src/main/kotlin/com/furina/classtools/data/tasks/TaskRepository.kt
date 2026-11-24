package com.furina.classtools.data.tasks

import android.content.SharedPreferences
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

interface TaskStorage {
    fun read(key: String): String?
    fun save(local: JsonObject, base: JsonObject? = null)
}

class TaskPreferences(private val prefs: SharedPreferences) : TaskStorage {
    override fun read(key: String): String? = prefs.getString(key, null)
    override fun save(local: JsonObject, base: JsonObject?) {
        val edit = prefs.edit().putString("local", local.toString())
        if (base != null) edit.putString("base", base.toString())
        edit.apply()
    }
}

class TaskRepository(
    private val sid: String,
    private val storage: TaskStorage,
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val onChanged: (JsonObject, JsonObject) -> Unit = { _, _ -> },
    private val canSync: () -> Boolean,
) {
    private fun read(key: String) = storage.read(key)?.let {
        runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull()
    } ?: emptyTasks

    private var base = read("base")
    private val current = MutableStateFlow(read("local"))
    val snapshot = current.asStateFlow()
    private val status = MutableStateFlow(if (current.value != base) "待同步" else "本机缓存")
    val syncStatus = status.asStateFlow()
    private val network = Mutex()

    fun edit(change: (List<JsonObject>, Long) -> List<JsonObject>) {
        update { old, now ->
            val before = old.objects("items")
            val after = change(before, now)
            if (before == after) old else old.withFields { put("items", JsonArray(after)) }
        }
    }

    fun addGroup(name: String): String? {
        var groupId: String? = null
        update { old, _ ->
            val groups = old.objects("lists")
            val label = name.trim().take(20)
            if (label.isEmpty() || groups.size >= 30) return@update old
            val id = "l" + java.util.UUID.randomUUID()
            groupId = id
            val group = buildJsonObject {
                put("id", id)
                put("name", label)
                put("color", "cy")
                put("order", (groups.maxOfOrNull { it.number("order") } ?: 0L) + 1)
            }
            old.withFields { put("lists", JsonArray(groups + group)) }
        }
        return groupId
    }

    @Synchronized
    private fun update(change: (JsonObject, Long) -> JsonObject) {
        val old = current.value
        val now = maxOf(System.currentTimeMillis(), old.number("updatedAt") + 1)
        val changed = change(old, now)
        if (old == changed) return
        val next = changed.withFields { put("updatedAt", now) }
        storage.save(next)
        current.value = next
        status.value = "待同步"
        onChanged(old, next)
        scope.launch {
            delay(450)
            if (current.value === next) sync()
        }
    }

    fun importHomework(row: HomeworkRow): Boolean {
        val title = listOf(row.subject, row.content.lineSequence().firstOrNull().orEmpty())
                .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "班级作业" }
        return importSource("sourceHwId", row.id, title) {
                put("notes", listOf(row.content, row.due).filter(String::isNotBlank).joinToString("\n").take(5000))
                put("sourceTitle", row.subject.ifBlank { title }.take(80))
                put("due", row.date)
                put("dueTime", row.time.takeIf { row.date.isNotEmpty() }.orEmpty())
                put("tags", buildJsonArray { add("班级作业") })
        }
    }

    fun importNotice(id: String, title: String, summary: String, deadline: Long?): Boolean =
        importSource("sourceNoticeId", id, title.ifBlank { "班级通知" }) {
            put("sourceTitle", title.take(80))
            put("notes", summary.take(5000))
            put("tags", buildJsonArray { add("通知") })
            deadline?.let { stamp ->
                val date = Instant.ofEpochMilli(stamp).atZone(ZoneId.systemDefault())
                put("due", date.toLocalDate().toString())
                if (date.hour != 0 || date.minute != 0) put("dueTime", date.toLocalTime().withSecond(0).withNano(0).toString())
            }
        }

    private fun importSource(source: String, id: String, title: String, fields: JsonObjectBuilder.() -> Unit): Boolean {
        var added = false
        edit { items, now ->
            if (id.isBlank() || items.any { it.text(source) == id }) return@edit items
            added = true
            items + newTask(title, now, firstOrder(items)).withFields { put(source, id); fields() }
        }
        return added
    }

    @Synchronized
    private fun accept(sent: JsonObject, confirmed: JsonObject) {
        val latest = current.value
        val next = if (sent == latest) confirmed else mergeTasks(sent, latest, confirmed).withFields {
            put("updatedAt", maxOf(latest.number("updatedAt"), confirmed.number("updatedAt") + 1))
        }
        storage.save(next, confirmed)
        base = confirmed
        current.value = next
        if (next != latest) onChanged(latest, next)
        status.value = if (next == confirmed) "已同步" else "待同步"
    }

    suspend fun sync() = network.withLock {
        if (!canSync()) { status.value = "仅本机"; return@withLock }
        status.value = "同步中…"
        val path = "taskdata?id=eq." + URLEncoder.encode(sid, "UTF-8")
        repeat(4) {
            val rows = (api.getJson(path + "&select=blob,ts", coalesce = false) as? ApiResult.Success)?.value as? JsonArray
            if (rows == null || !canSync()) { status.value = "仅本机"; return@withLock }
            val row = rows.firstOrNull() as? JsonObject
            val remote = if (row == null || row.text("blob").isBlank()) emptyTasks else
                runCatching { Json.parseToJsonElement(row.text("blob")).jsonObject }.getOrNull()
            if (remote == null || remote["items"] !is JsonArray) {
                status.value = "云端格式异常"; return@withLock
            }
            val (previous, local) = synchronized(this) { base to current.value }
            if (local == previous) {
                accept(local, remote)
            } else {
                val merged = mergeTasks(previous, local, remote).withFields {
                    put("updatedAt", maxOf(System.currentTimeMillis(), local.number("updatedAt"), (row?.number("ts") ?: 0L) + 1))
                }
                val body = buildJsonObject {
                    put("id", sid); put("blob", merged.toString()); put("ts", merged.number("updatedAt"))
                }.toString()
                val write = if (row == null) api.postJson("taskdata", body, "resolution=ignore-duplicates,return=representation")
                    else api.patchJson(path + "&ts=eq." + row.number("ts"), body, "return=representation")
                val saved = (write as? ApiResult.Success)?.value as? JsonArray
                if (saved == null || !canSync()) { status.value = "仅本机"; return@withLock }
                if (saved.isEmpty()) return@repeat
                accept(local, merged)
            }
            if (synchronized(this) { current.value == base }) return@withLock
        }
        status.value = "待同步"
    }
}
