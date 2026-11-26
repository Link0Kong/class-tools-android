package com.furina.classtools.data.tasks

import android.content.SharedPreferences
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiError
import com.furina.classtools.data.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

interface TaskStorage {
    fun read(key: String): String?
    fun save(local: JsonObject, base: JsonObject? = null, metadata: Map<String, String?> = emptyMap())
    fun checkpointAttempt(value: String?): Boolean
}

class TaskPreferences(private val prefs: SharedPreferences) : TaskStorage {
    override fun read(key: String): String? = prefs.getString(key, null)
    override fun checkpointAttempt(value: String?): Boolean =
        prefs.edit().putString("writeAttempt", value).commit()
    override fun save(local: JsonObject, base: JsonObject?, metadata: Map<String, String?>) {
        val edit = prefs.edit().putString("local", local.toString())
        if (base != null) edit.putString("base", base.toString())
        metadata.forEach { (key, value) -> if (value == null) edit.remove(key) else edit.putString(key, value) }
        edit.apply()
    }
}

class TaskRepository(
    private val sid: String,
    private val storage: TaskStorage,
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val onChanged: (JsonObject, JsonObject) -> Unit = { _, _ -> },
    private val canSync: suspend () -> Boolean,
) {
    private val invalidCache: Boolean
    private var knownBase: Boolean
    private var hasConfirmedRow: Boolean
    private var unconfirmed: TaskAttempt?
    private var legacyAnchor: JsonObject?
    private var base: JsonObject
    private val current: MutableStateFlow<JsonObject>
    init {
        val localText = storage.read("local")
        val baseText = storage.read("base")
        val savedLocal = localText?.let { TaskWire.decode(JsonPrimitive(it))?.snapshot }
        val savedBase = baseText?.let { TaskWire.decode(JsonPrimitive(it))?.snapshot }
        val checkpoint = storage.read("writeAttempt")
        val anchor = storage.read("legacyAnchor")
        unconfirmed = checkpoint?.let(TaskAttempt::read)
        legacyAnchor = anchor?.let { TaskWire.decode(JsonPrimitive(it))?.snapshot }
        invalidCache = (localText != null && savedLocal == null) || (baseText != null && savedBase == null) ||
            (checkpoint != null && unconfirmed == null) || (anchor != null && legacyAnchor == null)
        knownBase = savedBase != null || localText == null
        base = savedBase ?: emptyTasks
        hasConfirmedRow = storage.read("cloudRow") == "yes" || (savedBase != null && savedBase != emptyTasks)
        current = MutableStateFlow(savedLocal ?: base)
    }
    val snapshot = current.asStateFlow()
    private val status = MutableStateFlow(if (current.value.taskCloudView() != base.taskCloudView()) "待同步" else "本机缓存")
    val syncStatus = status.asStateFlow()
    private val network = Mutex()
    private val review = MutableStateFlow<TaskRecovery?>(null)
    val recovery = review.asStateFlow()
    fun deferRecovery() { review.value = null }

    fun edit(change: (List<JsonObject>, Long) -> List<JsonObject>) {
        update { old, now ->
            val before = old.objects("items")
            val after = change(before, now)
            if (before == after) old else old.withFields { put("items", JsonArray(after)) }
        }
    }

    fun addGroup(name: String, color: String = "cy"): String? {
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
                put("color", color)
                put("order", (groups.maxOfOrNull { it.number("order") } ?: 0L) + 1)
            }
            old.withFields { put("lists", JsonArray(groups + group)) }
        }
        return groupId
    }

    fun editGroup(id: String, name: String, color: String) = update { old, _ ->
        if (name.isBlank()) return@update old
        old.withFields {
            put("lists", JsonArray(old.objects("lists").map {
                if (it.text("id") == id) it.withFields { put("name", name.trim().take(20)); put("color", color) } else it
            }))
        }
    }

    fun removeGroup(id: String) = update { old, now ->
        old.withFields {
            put("lists", JsonArray(old.objects("lists").filterNot { it.text("id") == id }))
            put("items", JsonArray(old.objects("items").map {
                if (it.text("list") == id) it.withFields { put("list", ""); put("updatedAt", now) } else it
            }))
        }
    }

    fun moveGroup(id: String, direction: Int) = update { old, _ ->
        val groups = old.objects("lists").sortedBy { it.number("order") }.toMutableList()
        val index = groups.indexOfFirst { it.text("id") == id }
        if (index < 0 || index + direction !in groups.indices) return@update old
        java.util.Collections.swap(groups, index, index + direction)
        old.withFields { put("lists", JsonArray(groups.mapIndexed { i, group -> group.withFields { put("order", i) } })) }
    }

    fun savePreferences(values: JsonObject) = update { old, now ->
        old.withFields { put("prefs", JsonObject(old.prefs() + values).withFields {
            if ("sidebarOrder" in values) put("sidebarOrderAt", now)
        }) }
    }

    fun restore(backup: JsonObject, restoreFiles: (JsonObject) -> List<JsonObject> = { emptyList() }) =
        update { old, _ -> restoreTaskBackup(old, backup, restoreFiles) }

    @Synchronized
    private fun update(change: (JsonObject, Long) -> JsonObject) {
        if (invalidCache) { status.value = "本机缓存格式异常 · 已保留原数据"; return }
        val old = current.value
        val now = maxOf(System.currentTimeMillis(), old.number("updatedAt") + 1)
        val changed = change(old, now)
        if (old == changed) return
        val next = changed.withFields { put("updatedAt", now) }
        if (!knownBase && legacyAnchor == null) legacyAnchor = old
        val metadata = legacyAnchor?.let { mapOf("legacyAnchor" to it.toString()) }.orEmpty()
        storage.save(next, if (knownBase && storage.read("base") == null) base else null, metadata)
        current.value = next
        status.value = "待同步"
        onChanged(old, next)
        scope.launch {
            delay(450)
            if (current.value === next) sync()
        }
    }

    fun importHomework(row: HomeworkRow): Boolean {
        val subject = row.subject.trim()
        val title = listOf(subject, row.content.trim().lineSequence().firstOrNull().orEmpty())
            .filter(String::isNotBlank).joinToString(" · ").ifBlank { "班级作业" }
        return importSource("sourceHwId", row.id, title) {
            put("notes", listOfNotNull(row.content.trim().takeIf(String::isNotBlank),
                row.due.takeIf(String::isNotBlank)?.let { "截止说明：$it" }).joinToString("\n\n").take(5000))
            put("sourceTitle", subject.ifBlank { title }.take(80))
            put("due", row.date)
            put("dueTime", row.time.takeIf { row.date.isNotEmpty() }.orEmpty())
            put("tags", JsonArray(listOfNotNull("班级作业", subject.takeIf { it.isNotBlank() && it.length <= 16 }).distinct().map(::JsonPrimitive)))
        }
    }

    fun importNotice(id: String, title: String, summary: String, deadline: Long?, category: String = ""): Boolean =
        importSource("sourceNoticeId", id, title.ifBlank { "班级通知" }) {
            put("sourceTitle", title.take(80))
            put("notes", summary.take(5000))
            put("tags", JsonArray(listOf("通知", category.trim().take(16)).filter(String::isNotEmpty).distinct().map(::JsonPrimitive)))
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
            val clean = title.replace(Regex("\\s+"), " ").trim()
            val label = if (clean.length > 100) clean.take(98) + "…" else clean
            items + newTask(label, now, firstOrder(items)).withFields { put(source, id); fields() }
        }
        return added
    }

    @Synchronized
    private fun accept(sent: JsonObject, confirmed: JsonObject) {
        val latest = current.value
        val nextCloud = if (sent == latest) confirmed else mergeTasks(sent, latest, confirmed).withFields {
            put("updatedAt", maxOf(latest.number("updatedAt"), confirmed.number("updatedAt") + 1))
        }
        val next = nextCloud.withLocalFiles(latest)
        storage.save(next, confirmed, mapOf("writeAttempt" to null, "legacyAnchor" to null, "cloudRow" to "yes"))
        base = confirmed
        hasConfirmedRow = true
        knownBase = true
        legacyAnchor = null
        unconfirmed = null
        review.value = null
        current.value = next
        if (next != latest) onChanged(latest, next)
        status.value = if (next.taskCloudView() == confirmed.taskCloudView()) "已同步" else "待同步"
    }

    suspend fun sync() { syncSnapshot() }
    suspend fun resolveRecovery() { syncSnapshot(review.value) }

    private suspend fun saveAttempt(attempt: TaskAttempt?): Boolean {
        val saved = withContext(Dispatchers.IO) { canSync() && storage.checkpointAttempt(attempt?.encode()) }
        if (saved) unconfirmed = attempt else status.value = "同步记录未能保存 · 更改留在本机"
        return saved
    }

    /** Re-read before resolving. Keeping the archived input also preserves old local file references. */
    @Synchronized
    private fun reviewDraft(remote: JsonObject, anchor: JsonObject, legacy: Boolean, approved: TaskRecovery?, visible: Boolean): Boolean {
        val local = current.value
        val prompt = TaskRecovery(legacy, anchor)
        if (prompt != approved) {
            review.value = prompt
            status.value = "待核对草稿 · 更改留在本机"
            return false
        }
        val imported = if (legacy) addUnmatchedDraftEntries(anchor, remote) else remote
        val next = mergeTasks(anchor, local, imported).withLocalFiles(local)
        storage.save(next, remote, mapOf("recoveryArchive" to local.toString(), "writeAttempt" to null,
            "legacyAnchor" to null, "cloudRow" to if (visible) "yes" else null))
        hasConfirmedRow = visible
        knownBase = true
        base = remote
        current.value = next
        unconfirmed = null
        legacyAnchor = null
        review.value = null
        if (next != local) onChanged(local.taskCloudView(), next)
        return true
    }

    /** The merged document after a confirmed sync, or null when only local data is available. */
    internal suspend fun syncSnapshot(approved: TaskRecovery? = null): JsonObject? = network.withLock {
        if (!canSync()) { status.value = "仅本机"; return@withLock null }
        if (invalidCache) { status.value = "本机缓存格式异常 · 已保留原数据"; return@withLock null }
        status.value = "同步中…"
        val path = "taskdata?id=eq." + URLEncoder.encode(sid, "UTF-8")
        repeat(4) {
            val read = api.getJson(path + "&select=blob,ts", coalesce = false)
            if (!canSync()) return@withLock null
            val rows = responseRows(read) ?: return@withLock null
            val row = rows.singleOrNull() as? JsonObject
            val stamp = row?.get("ts").taskStamp()
            val wire = if (rows.isEmpty()) TaskWire.empty else TaskWire.decode(row?.get("blob"), stamp)
            val wrongOwner = row != null && "id" in row && row["id"] != JsonPrimitive(sid)
            if (wire == null || (rows.isNotEmpty() && stamp == null) || wrongOwner) {
                status.value = "云端格式异常 · 更改留在本机"; return@withLock null
            }
            val remote = wire.snapshot.taskCloudView()
            val (previous, local) = synchronized(this) { base to current.value }
            if (rows.isEmpty() && hasConfirmedRow) {
                status.value = "云端记录不可见 · 已保留本机"; return@withLock null
            }
            if (rows.isEmpty() && local.taskCloudView().sameTaskContents(previous.taskCloudView())) {
                status.value = "本机缓存"; return@withLock null
            }
            val pending = unconfirmed
            if (pending != null && stamp != null && stamp >= pending.stamp) {
                // Another writer may have changed the document after our response was lost. If all
                // intended changes are already present, accept the newer read without another PATCH.
                val replay = mergeTasks(previous, pending.sent, remote).taskCloudView()
                if (remote == pending.snapshot || replay.sameTaskContents(remote)) {
                    accept(pending.sent, remote)
                } else if (reviewDraft(remote, pending.sent, false, approved, true)) {
                    return@repeat
                } else return@withLock null
            } else if (!knownBase && !local.taskCloudView().sameTaskContents(remote)) {
                if (reviewDraft(remote, legacyAnchor ?: local, true, approved, row != null)) return@repeat
                return@withLock null
            } else if (pending == null && (local.taskCloudView() == previous.taskCloudView() ||
                    local.taskCloudView().sameTaskContents(remote))) {
                accept(local, remote)
            } else {
                val localStamp = local["updatedAt"]?.taskStamp()
                val nextStamp = maxOf(System.currentTimeMillis(), localStamp ?: 0L, (stamp ?: 0L) + 1)
                if (("updatedAt" in local && localStamp == null) || nextStamp !in 0..MAX_TASK_STAMP) {
                    status.value = "云端版本异常 · 更改留在本机"; return@withLock null
                }
                val merged = mergeTasks(previous, local, remote).taskCloudView().withFields {
                    put("updatedAt", nextStamp)
                }
                val attempt = TaskAttempt(local, merged, wire.encode(merged), nextStamp)
                if (!saveAttempt(attempt)) return@withLock null
                val body = buildJsonObject {
                    put("id", sid); put("blob", attempt.blob); put("ts", nextStamp)
                }.toString()
                if (!canSync()) return@withLock null
                val write = if (row == null) api.postJson("taskdata", body, "resolution=ignore-duplicates,return=representation")
                    else api.patchJson(path + "&ts=eq." + stamp, body, "return=representation")
                if (!canSync()) return@withLock null
                if (write is ApiResult.Failure && write.error !is ApiError.Parse) {
                    if (write.error is ApiError.TokenInvalid) saveAttempt(null)
                    responseRows(write); return@withLock null
                }
                val returned = (write as? ApiResult.Success)?.value as? JsonArray
                if (returned != null && returned.isEmpty()) {
                    if (!saveAttempt(null)) return@withLock null
                    return@repeat
                }
                val saved = returned?.singleOrNull() as? JsonObject
                if (saved == null || saved["id"] != JsonPrimitive(sid) || saved["ts"].taskStamp() != nextStamp ||
                    saved["blob"] != JsonPrimitive(attempt.blob)) return@repeat
                accept(local, merged)
            }
            if (synchronized(this) { current.value.taskCloudView() == base.taskCloudView() }) return@withLock snapshot.value
        }
        status.value = "待同步 · 已保存本机"
        null
    }

    private fun responseRows(result: ApiResult<JsonElement>): JsonArray? {
        if (result is ApiResult.Success && result.value is JsonArray) return result.value
        val error = (result as? ApiResult.Failure)?.error
        status.value = when (error) {
            is ApiError.TokenInvalid -> "登录失效或无权限 · 更改留在本机"
            is ApiError.Parse, null -> "云端格式异常 · 更改留在本机"
            else -> "待同步 · 已保存本机"
        }
        return null
    }
}
