package com.furina.classtools.data.tasks

import com.furina.classtools.data.timetable.TimetableIcs
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.serialization.json.*

internal fun taskBackup(data: JsonObject) = data.taskCloudView().withFields {
    put("kind", "task-backup")
    put("app", "tool.furina.fun")
    put("exportedAt", Instant.now().toString())
}

internal fun restoreTaskBackup(data: JsonObject, input: JsonObject, restoreFiles: (JsonObject) -> List<JsonObject> = { emptyList() }): JsonObject {
    val backup = input["task"] as? JsonObject ?: input
    require(backup["items"] is JsonArray && backup["lists"] is JsonArray) { "文件不含任务备份" }
    val validId = Regex("[A-Za-z0-9_-]{1,48}")
    val groups = data.objects("lists").toMutableList()
    val groupIds = mutableMapOf<String, String>()
    for (group in backup.objects("lists").take(30)) {
        val id = group.text("id")
        val name = group.text("name").trim().take(20)
        if (id == "inbox" || name.isEmpty()) continue
        val shared = groups.find { it.text("id") == id || it.text("name") == name }
        if (shared != null) groupIds[id] = shared.text("id")
        else if (groups.size < 30) {
            val newId = id.takeIf(validId::matches) ?: "l" + UUID.randomUUID()
            groups += group.withFields { put("id", newId); put("name", name); put("order", groups.size) }
            groupIds[id] = newId
        }
    }
    val tasks = data.objects("items").associateBy { it.text("id") }.toMutableMap()
    for (item in backup.objects("items")) {
        val title = item.text("title").trim().take(200)
        if (title.isBlank()) continue
        var id = item.text("id")
        val existing = tasks[id]
        if (existing != null && existing.text("title") == title) {
            if (existing.objects("localFiles").isEmpty()) {
                val files = restoreFiles(item)
                if (files.isNotEmpty()) tasks[id] = existing.withFields { put("localFiles", JsonArray(files)) }
            }
            continue
        }
        require(tasks.size < 2000) { "恢复后任务超过 2000 项" }
        if (id in tasks || !validId.matches(id)) id = "t" + UUID.randomUUID()
        tasks[id] = JsonObject(item - "localFiles").withFields {
            put("id", id); put("title", title); put("list", groupIds[item.text("list")].orEmpty())
            put("attachments", "")
            val files = restoreFiles(item)
            if (files.isNotEmpty()) put("localFiles", JsonArray(files))
        }
    }
    return data.withFields { put("lists", JsonArray(groups)); put("items", JsonArray(tasks.values.toList())) }
}

internal fun taskCalendar(data: JsonObject): String {
    val entries = mutableListOf<String>()
    for (item in data.pendingTasks()) {
        val day = runCatching { LocalDate.parse(item.text("due")) }.getOrNull()
        val start = taskDeadline(item) ?: item.number("remindAt").takeIf { it > 0 } ?: continue
        val utc = { stamp: Long -> TimetableIcs.formatUtc(Instant.ofEpochMilli(stamp)) }
        val date = DateTimeFormatter.BASIC_ISO_DATE
        entries += listOf("BEGIN:VEVENT", "UID:" + TimetableIcs.escapeText(item.text("id")) + "@tool.furina.fun",
            "DTSTAMP:" + utc(System.currentTimeMillis()), "SUMMARY:" + TimetableIcs.escapeText(item.text("title")))
        if (day != null && item.text("dueTime").isBlank()) {
            entries += listOf("DTSTART;VALUE=DATE:" + day.format(date), "DTEND;VALUE=DATE:" + day.plusDays(1).format(date))
        } else entries += listOf("DTSTART:" + utc(start), "DTEND:" + utc(start + 3_600_000))
        val description = listOf(item.text("notes")) + item.objects("steps").map {
            (if (it.flag("done")) "[x] " else "[ ] ") + it.text("text")
        }
        entries += "DESCRIPTION:" + TimetableIcs.escapeText(description.filter(String::isNotBlank).joinToString("\n"))
        if (item.flag("starred")) entries += "PRIORITY:1"
        if (item.number("remindAt") > 0) entries += listOf("BEGIN:VALARM", "ACTION:DISPLAY",
            "DESCRIPTION:" + TimetableIcs.escapeText(item.text("title")), "TRIGGER;VALUE=DATE-TIME:" + utc(item.number("remindAt")), "END:VALARM")
        entries += "END:VEVENT"
    }
    if (entries.isEmpty()) return ""
    return (listOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//ClassTools//Tasks//CN", "CALSCALE:GREGORIAN") +
        entries + "END:VCALENDAR").joinToString("\r\n", postfix = "\r\n", transform = TimetableIcs::foldLine)
}
