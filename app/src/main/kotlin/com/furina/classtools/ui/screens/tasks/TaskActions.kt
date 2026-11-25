package com.furina.classtools.ui.screens.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.tasks.*
import com.furina.classtools.features.export.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

internal val taskViews = linkedMapOf("myday" to "我的一天", "important" to "重要", "planned" to "计划中", "all" to "待办", "done" to "已完成")
internal val reminderOptions = listOf("none" to "不提醒", "due" to "到期时", "5" to "提前 5 分钟", "15" to "提前 15 分钟",
    "30" to "提前 30 分钟", "60" to "提前 1 小时", "1440" to "提前 1 天")
internal val groupColors = mapOf("cy" to "青绿", "bl" to "蓝色", "sd" to "沙金", "violet" to "紫色", "rose" to "玫瑰", "teal" to "蓝绿")

internal fun taskTimeText(stamp: Long): String = if (stamp <= 0) "" else
    Instant.ofEpochMilli(stamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

internal fun pickTaskDate(context: Context, value: String, apply: (String) -> Unit) {
    val initial = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
    DatePickerDialog(context, { _, year, month, day -> apply(LocalDate.of(year, month + 1, day).toString()) },
        initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}

internal fun pickTaskTime(context: Context, value: String, apply: (String) -> Unit) {
    val initial = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(18, 0))
    TimePickerDialog(context, { _, hour, minute -> apply(LocalTime.of(hour, minute).toString()) },
        initial.hour, initial.minute, true).show()
}

internal fun taskShareCard(items: List<JsonObject>, single: Boolean): ExportCardSpec {
    val title = if (single) items.first().text("title") else "我的任务"
    val lines = if (single) {
        val item = items.first()
        listOf(item.text("notes"), taskDueLabel(item, LocalDate.now().toString())) +
            item.objects("steps").map { (if (it.flag("done")) "☑ " else "☐ ") + it.text("text") }
    } else items.map { (if (it.flag("done")) "☑ " else "☐ ") + it.text("title") }
    return ExportCardSpec(title = title, file = ExportCardNaming.safeName(title) + ".png", rec = ExportCardNaming.recordId("TK"),
        sub = if (single) "个人任务" else "${items.count { !it.flag("done") }} 项未完成 · ${items.count { it.flag("done") }} 项已完成",
        blocks = listOf(TextBlock("任务", lines.filter(String::isNotBlank).map { TextLine.of(it) })), foot = ExportCardNaming.footText())
}

@Composable
internal fun TaskMenu(repository: TaskRepository, snapshot: JsonObject, onNew: () -> Unit, onManage: () -> Unit,
    onShare: () -> Unit, onReminderSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var clear by rememberSaveable { mutableStateOf(false) }
    fun feedback(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    fun write(uri: android.net.Uri?, content: () -> String) {
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(content()) }
            } }
            feedback(if (result.isSuccess) "已导出" else "文件保存失败")
        }
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        write(it) { TaskFiles.backup(context, snapshot).toString() }
    }
    val exportIcs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) {
        write(it) { taskCalendar(snapshot) }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val data = requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { it.readText() }
                TaskFiles.restore(context, repository, Json.parseToJsonElement(data).jsonObject)
            } }
            feedback(result.fold({ if (it == 0) "已合并任务备份" else "已合并任务，$it 个附件仅保存在原设备" },
                { "恢复失败，请选择有效的任务备份" }))
        }
    }
    Box {
        TextButton(onClick = { menu = true }) { Text("更多") }
        DropdownMenu(menu, { menu = false }) {
            val options: List<Pair<String, () -> Unit>> = listOf("新建任务" to onNew, "管理视图与分组" to onManage,
                "分享任务图片" to onShare, "提醒设置" to onReminderSettings,
                "导出日历" to { if (taskCalendar(snapshot).isBlank()) feedback("没有可导出的日程") else exportIcs.launch("个人任务.ics") },
                "备份任务" to { exportJson.launch("个人任务.json") },
                "恢复任务备份" to { importJson.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                "清除已完成" to { clear = true })
            options.forEach { (name, act) -> DropdownMenuItem(text = { Text(name) }, onClick = { menu = false; act() }) }
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("清除已完成的 ${snapshot.objects("items").count { it.flag("done") }} 项任务？") },
        confirmButton = { TextButton(onClick = { repository.edit { items, _ -> items.filterNot { it.flag("done") } }; clear = false }) { Text("清除") } },
        dismissButton = { TextButton(onClick = { clear = false }) { Text("取消") } })
}

@Composable
internal fun TaskManagement(repository: TaskRepository, snapshot: JsonObject, views: List<String>, onClose: () -> Unit) {
    val groups = snapshot.objects("lists").filterNot { it.text("id") == "inbox" }.sortedBy { it.number("order") }
    var form by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf("cy") }
    var remove by rememberSaveable { mutableStateOf(false) }
    fun form(group: JsonObject?) {
        form = group?.text("id").orEmpty(); name = group?.text("name").orEmpty(); color = group?.text("color") ?: "cy"
    }
    AlertDialog(onDismissRequest = onClose, title = { Text("任务管理") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Choice("默认提醒", snapshot.prefs().text("defaultRemind").ifBlank { "none" }, reminderOptions) {
                repository.savePreferences(buildJsonObject { put("defaultRemind", it) })
            }
            Text("视图顺序", style = MaterialTheme.typography.titleSmall)
            views.forEachIndexed { i, key ->
                MoveRow(taskViews[key].orEmpty(), i, views.size) { direction ->
                    val ordered = views.toMutableList()
                    java.util.Collections.swap(ordered, i, i + direction)
                    repository.savePreferences(buildJsonObject { put("sidebarOrder", JsonArray(ordered.map(::JsonPrimitive))) })
                }
            }
            HorizontalDivider()
            Text("分组", style = MaterialTheme.typography.titleSmall)
            groups.forEachIndexed { i, group ->
                MoveRow(group.text("name"), i, groups.size, { form(group) }) { repository.moveGroup(group.text("id"), it) }
            }
            if (groups.size < 30) TextButton(onClick = { form(null) }) { Text("新建分组") }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("完成") } })
    if (form != null) AlertDialog(onDismissRequest = { form = null }, title = { Text(if (form.isNullOrEmpty()) "新建分组" else "编辑分组") }, text = {
        Column {
            OutlinedTextField(name, { name = it.take(20) }, label = { Text("分组名称") }, singleLine = true)
            Choice("颜色", color, groupColors.toList()) { color = it }
            if (!form.isNullOrEmpty()) TextButton(onClick = { remove = true }) { Text("删除分组") }
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
        if (form.isNullOrEmpty()) repository.addGroup(name, color) else repository.editGroup(form.orEmpty(), name, color)
        form = null
    }) { Text("保存") } }, dismissButton = { TextButton(onClick = { form = null }) { Text("取消") } })
    if (remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text("删除分组「$name」？") },
        text = { Text("该分组的任务将移到未分组。") }, confirmButton = { TextButton(onClick = {
            repository.removeGroup(form.orEmpty()); remove = false; form = null
        }) { Text("删除") } }, dismissButton = { TextButton(onClick = { remove = false }) { Text("取消") } })
}

@Composable
private fun MoveRow(label: String, index: Int, count: Int, onEdit: (() -> Unit)? = null, onMove: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        if (onEdit != null) TextButton(onClick = onEdit) { Text("编辑") }
        TextButton(enabled = index > 0, onClick = { onMove(-1) }) { Text("上移") }
        TextButton(enabled = index + 1 < count, onClick = { onMove(1) }) { Text("下移") }
    }
}

@Composable
internal fun TaskDetail(task: JsonObject, repository: TaskRepository, groups: List<JsonObject>, onEdit: () -> Unit,
    onClose: () -> Unit, onSource: (JsonObject) -> Unit, onShare: () -> Unit, onMove: (Int) -> Unit) {
    val context = LocalContext.current
    val id = task.text("id")
    val due = taskDueLabel(task, LocalDate.now().toString())
    val detail = listOf(groups.find { it.text("id") == task.text("list") }?.text("name") ?: "未分组",
        if (task.flag("done")) "已完成" else "待办", due, repeats[task.text("repeat")].orEmpty(),
        taskTimeText(task.number("remindAt")).takeIf(String::isNotBlank)?.let { "提醒 $it" }.orEmpty(),
        task.strings("tags").joinToString(" ") { "#$it" }, task.text("notes"))
    AlertDialog(onDismissRequest = onClose, title = { Text(task.text("title")) }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            detail.filter(String::isNotBlank).forEach { Text(it) }
            FilterChip(task.flag("starred"), { repository.edit { items, now -> items.map {
                if (it.text("id") == id) it.withFields { put("starred", !it.flag("starred")); put("updatedAt", now) } else it
            } } }, { Text("重要") })
            task.objects("steps").forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(step.flag("done"), { done -> repository.edit { items, now -> items.map { current ->
                        if (current.text("id") != id || current.objects("steps").getOrNull(index) != step) current
                        else current.withFields { put("updatedAt", now); put("steps", JsonArray(current.objects("steps").mapIndexed { i, s ->
                            if (i == index) s.withFields { put("done", done) } else s
                        })) }
                    } } })
                    Column { Text(step.text("text")); if (step.text("note").isNotBlank()) Text(step.text("note"), style = MaterialTheme.typography.bodySmall) }
                }
            }
            task.objects("localFiles").forEach { attachment -> TextButton(onClick = {
                runCatching { TaskFiles.open(context, attachment) }.onFailure { Toast.makeText(context, "无法打开附件", Toast.LENGTH_SHORT).show() }
            }) { Text("附件 · " + attachment.text("n")) } }
            if (task.text("sourceHwId").isNotBlank() || task.text("sourceNoticeId").isNotBlank())
                TextButton(onClick = { onSource(task) }) { Text("查看来源 · " + task.text("sourceTitle")) }
            Text("创建 " + taskTimeText(task.number("createdAt")) + "\n更新 " + taskTimeText(task.number("updatedAt")),
                style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = onShare) { Text("分享") }
                if (!task.flag("done")) {
                    TextButton(onClick = { onMove(-1) }) { Text("上移") }
                    TextButton(onClick = { onMove(1) }) { Text("下移") }
                }
                TextButton(onClick = { repository.edit { items, now -> changeCompletion(items, id, !task.flag("done"), now) } }) {
                    Text(if (task.flag("done")) "标记未完成" else "标记完成")
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onEdit) { Text("编辑") } }, dismissButton = { TextButton(onClick = onClose) { Text("关闭") } })
}
