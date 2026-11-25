package com.furina.classtools.data.tasks

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.json.*

internal fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.flag(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull == true
internal fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.longOrNull ?: 0L
internal fun JsonObject.objects(key: String) = (get(key) as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
internal fun JsonObject.strings(key: String) = (get(key) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
internal fun JsonObject.prefs() = get("prefs") as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonObject.withFields(fields: JsonObjectBuilder.() -> Unit) = JsonObject(this + buildJsonObject(fields))

internal val emptyTasks = buildJsonObject {
    put("v", 1)
    put("items", JsonArray(emptyList()))
    put("lists", JsonArray(emptyList()))
}

internal fun newTask(title: String, now: Long, order: Double): JsonObject = buildJsonObject {
    put("id", "t" + UUID.randomUUID().toString())
    put("title", title.trim().take(200))
    put("createdAt", now)
    put("updatedAt", now)
    put("order", order)
}

internal fun taskDeadline(task: JsonObject, zone: ZoneId = ZoneId.systemDefault()): Long? {
    val date = runCatching { LocalDate.parse(task.text("due")) }.getOrNull() ?: return null
    val time = runCatching { LocalTime.parse(task.text("dueTime")) }.getOrDefault(LocalTime.MAX)
    return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
}

internal fun reminderAt(task: JsonObject): Long {
    val mode = task.text("remindMode")
    if (mode == "custom") return task.number("remindCustom").takeIf { it > 0 } ?: task.number("remindAt")
    val minutes = if (mode == "due") 0L else mode.toLongOrNull() ?: return 0
    return taskDeadline(task)?.minus(minutes * 60000L)?.coerceAtLeast(0L) ?: 0
}

internal fun changeCompletion(items: List<JsonObject>, id: String, done: Boolean, now: Long): List<JsonObject> {
    val task = items.find { it.text("id") == id } ?: return items
    if (task.flag("done") == done) return items
    val changed = items.map {
        if (it != task) it else it.withFields {
            put("done", done)
            put("doneAt", if (done) now else 0L)
            put("updatedAt", now)
            put("lastNotifyAt", if (done) now else 0L)
            put("snoozeUntil", 0)
        }
    }
    val children = items.filter { it.text("fromId") == id && !it.flag("done") }
    if (!done) return changed - children.filter { it.number("createdAt") == it.number("updatedAt") }.toSet()
    if (children.isNotEmpty()) return changed
    val repeat = task.text("repeat")
    if (repeat !in listOf("daily", "weekday", "weekly", "monthly", "yearly")) return changed
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val base = runCatching { LocalDate.parse(task.text("due")) }.getOrDefault(today)
    var next = base
    if (repeat in listOf("daily", "weekday", "weekly")) {
        next = base.plusWeeks(((today.toEpochDay() - base.toEpochDay()) / 7 - 1).coerceAtLeast(0))
    }
    val time = runCatching { LocalTime.parse(task.text("dueTime")) }.getOrDefault(LocalTime.MAX)
    do {
        next = when (repeat) {
            "daily" -> next.plusDays(1)
            "weekday" -> generateSequence(next.plusDays(1)) { it.plusDays(1) }
                .first { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            "weekly" -> next.plusWeeks(1)
            "monthly" -> next.plusMonths(1)
            else -> next.plusYears(1)
        }
    } while (next.atTime(time).atZone(zone).toInstant().toEpochMilli() <= now)

    val nextTask = task.withFields {
        newTask(task.text("title"), now, firstOrder(items)).forEach { (k, v) -> put(k, v) }
        put("fromId", id)
        put("done", false)
        put("doneAt", 0)
        put("myDay", "")
        put("lastNotifyAt", 0)
        put("snoozeUntil", 0)
        put("due", next.toString())
        put("steps", JsonArray(task.objects("steps").map { it.withFields { put("done", false) } }))
        if (task.text("remindMode") == "custom") {
            val gap = next.atTime(time).atZone(zone).toInstant().toEpochMilli() -
                base.atTime(time).atZone(zone).toInstant().toEpochMilli()
            put("remindCustom", reminderAt(task) + gap)
        }
    }
    return changed + nextTask.withFields { put("remindAt", reminderAt(nextTask)) }
}

internal fun firstOrder(items: List<JsonObject>) =
    (items.minOfOrNull { it.text("order").toDoubleOrNull() ?: 0.0 } ?: 0.0) - 1.0

/** Rank actionable work the same way on the task screen and desktop. Stable order breaks ties. */
internal fun taskPriority(task: JsonObject, today: String): Int {
    val due = task.text("due")
    return when {
        due.isNotBlank() && due < today -> 0
        due == today -> 1
        task.flag("starred") -> 2
        task.text("myDay") == today -> 3
        due.isNotBlank() -> 4
        else -> 5
    }
}

internal fun taskPriorityOrder(today: String) = compareBy<JsonObject> { taskPriority(it, today) }
    .thenBy { taskDeadline(it) ?: Long.MAX_VALUE }
    .thenBy { it.text("order").toDoubleOrNull() ?: 0.0 }

internal fun taskDueLabel(task: JsonObject, today: String): String {
    val due = task.text("due")
    if (due.isBlank()) return ""
    val day = when {
        due < today -> "已逾期 " + due.takeLast(5)
        due == today -> "今天"
        else -> due.takeLast(5)
    }
    return listOf(day, task.text("dueTime")).filter(String::isNotBlank).joinToString(" ") + " 截止"
}

internal fun JsonObject.pendingTasks() = objects("items").filterNot { it.flag("done") }
    .sortedBy { it.text("order").toDoubleOrNull() ?: 0.0 }

// Replay only local changes against the latest snapshot. Fields owned by other clients survive.
internal fun mergeTasks(base: JsonObject, local: JsonObject, remote: JsonObject): JsonObject {
    fun byId(doc: JsonObject) = doc.objects("items").associateBy { it.text("id") }
    val before = byId(base)
    val after = byId(local)
    val result = byId(remote).toMutableMap()
    val remoteIds = result.keys.toSet()
    for (id in before.keys - after.keys) result.remove(id)
    for ((id, item) in after) {
        val old = before[id]
        if (old == item) continue
        val existing = result[id]
        result[id] = if (old == null || existing == null) item
            else JsonObject(existing + item.filter { (key, value) -> old[key] != value })
    }
    val seenSources = mutableSetOf<String>()
    val merged = result.values.filter { task ->
        val source = listOf("sourceHwId", "sourceNoticeId").firstNotNullOfOrNull { key ->
            task.text(key).takeIf(String::isNotEmpty)?.let { key + ":" + it }
        }.orEmpty()
        source.isBlank() || task.text("fromId").isNotBlank() || seenSources.add(source) ||
            before.containsKey(task.text("id")) || task.text("id") in remoteIds
    }
    val oldGroups = base.objects("lists").associateBy { it.text("id") }
    val localGroups = local.objects("lists").associateBy { it.text("id") }
    val groups = remote.objects("lists").associateBy { it.text("id") }.toMutableMap()
    for (id in oldGroups.keys - localGroups.keys) groups.remove(id)
    for ((id, group) in localGroups) {
        val old = oldGroups[id]
        if (group != old && (old == null || id in groups))
            groups[id] = JsonObject(groups[id].orEmpty() + group.filter { old?.get(it.key) != it.value })
    }
    val prefChanges = local.prefs().filter { (key, value) -> value != base.prefs()[key] &&
        (key !in setOf("sidebarOrder", "sidebarOrderAt") || local.prefs().number("sidebarOrderAt") >= remote.prefs().number("sidebarOrderAt")) }
    return remote.withFields {
        put("lists", JsonArray(groups.values.sortedBy { it.number("order") }))
        if (prefChanges.isNotEmpty()) put("prefs", JsonObject(remote.prefs() + prefChanges))
        put("items", JsonArray(merged.map { task ->
            if (task.text("list").isNotBlank() && task.text("list") !in groups) task.withFields { put("list", "") } else task
        }))
    }
}

internal fun JsonObject.taskCloudView() = withFields {
    put("items", JsonArray(objects("items").map { JsonObject(it - "localFiles").withFields { put("attachments", "") } }))
}

internal fun JsonObject.withLocalFiles(local: JsonObject): JsonObject {
    val files = local.objects("items").associate { it.text("id") to it.objects("localFiles") }
    return withFields {
        put("items", JsonArray(objects("items").map { task ->
            val owned = files[task.text("id")].orEmpty()
            if (owned.isEmpty()) JsonObject(task - "localFiles") else task.withFields { put("localFiles", JsonArray(owned)) }
        }))
    }
}
