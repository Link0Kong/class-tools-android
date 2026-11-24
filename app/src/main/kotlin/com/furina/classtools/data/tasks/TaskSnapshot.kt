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
    val knownGroups = (base.objects("lists") + remote.objects("lists")).mapTo(mutableSetOf()) { it.text("id") }
    val addedGroups = local.objects("lists").filter { it.text("id") !in knownGroups }
    return remote.withFields {
        if (addedGroups.isNotEmpty()) put("lists", JsonArray(remote.objects("lists") + addedGroups))
        put("items", JsonArray(merged.map { it.withFields { put("attachments", "") } }))
    }
}
