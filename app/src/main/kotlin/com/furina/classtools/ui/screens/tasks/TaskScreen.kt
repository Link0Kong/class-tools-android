package com.furina.classtools.ui.screens.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.tasks.*
import com.furina.classtools.ui.components.CtLightTopBar
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.theme.rememberReduceMotion
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

private val repeats = mapOf("" to "不重复", "daily" to "每天", "weekday" to "工作日", "weekly" to "每周", "monthly" to "每月", "yearly" to "每年")

@Composable
fun TaskScreen(
    repository: TaskRepository,
    onBack: () -> Unit,
    onHomework: () -> Unit,
    homeworkOverview: (@Composable () -> Unit)? = null,
    onRefreshHomework: () -> Unit = {},
) {
    val snapshot by repository.snapshot.collectAsState()
    val status by repository.syncStatus.collectAsState()
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val all = remember(snapshot) { snapshot.objects("items").distinctBy { it.text("id") } }
    val groups = remember(snapshot) { snapshot.objects("lists").filter { it.text("id") != "inbox" } }
    val filters = listOf("all" to "待办", "important" to "重要", "myday" to "我的一天", "planned" to "有截止日期", "done" to "已完成") +
        groups.map { "group-" + it.text("id") to it.text("name") }
    var view by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var quick by rememberSaveable { mutableStateOf("") }
    var quickDue by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var addGroup by rememberSaveable { mutableStateOf(false) }
    var groupName by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val today = LocalDate.now().toString()
    val visible = remember(all, view, query, today) {
        all.filter { t ->
            val matches = when (view) {
                "important" -> t.flag("starred")
                "myday" -> t.text("myDay") == today
                "planned" -> t.text("due").isNotEmpty()
                else -> !view.startsWith("group-") || t.text("list") == view.removePrefix("group-")
            }
            (view == "done") == t.flag("done") && matches &&
                listOf(t.text("title"), t.text("notes"), t["tags"].toString()).any { it.contains(query.trim(), true) }
        }.sortedWith(if (view == "done") compareByDescending { it.number("doneAt") }
            else if (view == "planned") compareBy { taskDeadline(it) ?: Long.MAX_VALUE }
            else if (view == "all") taskPriorityOrder(today) else compareBy { it.text("order").toDoubleOrNull() ?: 0.0 })
    }
    fun create() {
        val title = quick.trim()
        if (title.isEmpty()) return
        repository.edit { list, time ->
            list + newTask(title, time, firstOrder(list)).withFields {
                if (view.startsWith("group-")) put("list", view.removePrefix("group-"))
                if (view == "important") put("starred", true)
                if (view == "myday") put("myDay", today)
                if (quickDue.isNotBlank()) put("due", quickDue) else if (view == "planned") put("due", today)
            }
        }
        quick = ""
        quickDue = ""
        query = ""
        if (view == "done") view = "all"
    }
    LaunchedEffect(repository) { repository.sync() }
    LaunchedEffect(groups) { if (filters.none { it.first == view }) view = "all" }
    ExpressiveBackground(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            CtLightTopBar(title = "任务", scrollPx = 0f, subtitle = if (status.contains("离线") || status.contains("失败") || status.contains("同步中")) status else "${all.count { !it.flag("done") }} 项待办 · ${all.count { !it.flag("done") && it.text("due") == today }} 项今天截止", onBack = onBack, actions = {
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                    Icon(Icons.Rounded.Search, "搜索任务")
                }
                if (homeworkOverview == null || searching || view != "all") TextButton(onClick = onHomework) { Text("班级作业") }
                IconButton(onClick = { onRefreshHomework(); scope.launch { repository.sync() } }, enabled = status != "同步中…") {
                    Icon(Icons.Rounded.Refresh, "同步任务")
                }
            })
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (homeworkOverview != null && view == "all" && !searching) {
                    item("class-homework") { homeworkOverview() }
                    item("personal-heading") { Text("个人任务", style = MaterialTheme.typography.titleMedium) }
                }
                item("controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            filters.forEach { (key, name) -> FilterChip(view == key, { view = key }, { Text(name) }) }
                            if (groups.size < 30) TextButton(onClick = { groupName = ""; addGroup = true }) { Text("新建分组") }
                        }
                        if (searching) OutlinedTextField(query, { query = it },
                            Modifier.fillMaxWidth(), singleLine = true, label = { Text("搜索标题、备注或标签") })
                    }
                }
                if (visible.isEmpty()) item("empty") {
                    Text(if (query.isNotBlank()) "没有匹配的任务" else if (view == "done") "暂无已完成任务" else "暂无待办任务",
                        Modifier.padding(vertical = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(visible, key = { "item-" + it.text("id") }) { task ->
                    val id = task.text("id")
                    val done = task.flag("done")
                    Card(modifier = if (reduceMotion) Modifier else Modifier.animateItem()) {
                        Row(Modifier.fillMaxWidth().clickable { editing = id }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(done, { value -> repository.edit { list, time -> changeCompletion(list, id, value, time) } },
                                modifier = Modifier.semantics { contentDescription = if (done) "标记未完成" else "标记完成" })
                            Column(Modifier.weight(1f).padding(vertical = 5.dp)) {
                                Text(task.text("title"), style = MaterialTheme.typography.titleMedium, textDecoration = if (done) TextDecoration.LineThrough else null)
                                val group = groups.find { it.text("id") == task.text("list") }
                                val meta = listOf(taskDueLabel(task, today), if (task.text("myDay") == today) "我的一天" else "",
                                    group?.text("name").orEmpty(),
                                    if (task.text("sourceHwId").isNotBlank()) "班级作业" else "",
                                    if (task.text("sourceNoticeId").isNotBlank()) "班级通知" else "").filter(String::isNotBlank)
                                if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                    color = if (!done && taskPriority(task, today) == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconToggleButton(task.flag("starred"), { value -> repository.edit { list, time ->
                                list.map { if (it.text("id") == id) it.withFields { put("starred", value); put("updatedAt", time) } else it }
                            } }) { Text(if (task.flag("starred")) "★" else "☆", modifier = Modifier.semantics { contentDescription = "重要任务" }) }
                        }
                        val steps = task.objects("steps")
                        if (steps.isNotEmpty()) {
                            TextButton(onClick = { expanded = if (expanded == id) null else id }) {
                                Text((if (expanded == id) "收起步骤" else "展开步骤") + "  " + steps.count { it.flag("done") } + "/" + steps.size)
                            }
                            AnimatedVisibility(expanded == id,
                                enter = if (reduceMotion) EnterTransition.None else expandVertically(),
                                exit = if (reduceMotion) ExitTransition.None else shrinkVertically()) {
                                Column(Modifier.padding(start = 12.dp, bottom = 6.dp)) {
                                    steps.forEachIndexed { index, step ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(step.flag("done"), { value -> repository.edit { list, now ->
                                                list.map { old ->
                                                    if (old.text("id") != id || old.objects("steps").getOrNull(index) != step) old
                                                    else old.withFields {
                                                        put("updatedAt", now)
                                                        put("steps", JsonArray(old.objects("steps").mapIndexed { i, s ->
                                                            if (i == index) s.withFields { put("done", value) } else s
                                                        }))
                                                    }
                                                }
                                            } }, modifier = Modifier.semantics { contentDescription = "步骤 · " + step.text("text") })
                                            Column { Text(step.text("text")); if (step.text("note").isNotBlank()) Text(step.text("note"), style = MaterialTheme.typography.bodySmall) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    OutlinedTextField(quick, { quick = it.take(200) }, Modifier.fillMaxWidth(),
                        label = { Text("添加任务") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { create() }),
                        trailingIcon = { IconButton(onClick = ::create, enabled = quick.isNotBlank()) {
                            Icon(Icons.Rounded.Add, "保存任务")
                        } })
                    if (quick.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(today to "今天截止", LocalDate.now().plusDays(1).toString() to "明天截止").forEach { (date, label) ->
                            FilterChip(quickDue == date, { quickDue = if (quickDue == date) "" else date }, { Text(label) })
                        }
                    }
                }
            }
        }
    }
    val selected = all.find { it.text("id") == editing }
    if (addGroup) AlertDialog(onDismissRequest = { addGroup = false }, title = { Text("新建分组") },
        text = { OutlinedTextField(groupName, { groupName = it.take(20) }, label = { Text("分组名称") }, singleLine = true) },
        confirmButton = { TextButton(enabled = groupName.isNotBlank(), onClick = {
            repository.addGroup(groupName)?.let { view = "group-" + it }
            addGroup = false
        }) { Text("创建") } }, dismissButton = { TextButton(onClick = { addGroup = false }) { Text("取消") } })
    if (selected != null) key(selected.text("id")) {
        TaskEditor(selected, groups, onClose = { editing = null }, onDelete = { deleting = editing }, onSave = { patch ->
            repository.edit { list, now ->
                list.map { original ->
                    if (original.text("id") != selected.text("id") || patch.isEmpty()) original else JsonObject(original + patch).let { edited ->
                        edited.withFields {
                            put("updatedAt", now)
                            val alarm = reminderAt(edited)
                            put("remindAt", alarm)
                            if (alarm != original.number("remindAt")) {
                                put("snoozeUntil", 0)
                                put("lastNotifyAt", 0)
                            }
                        }
                    }
                }
            }
            editing = null
        })
    }
    if (deleting != null) AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除这项任务？") },
        text = { Text(selected?.text("title").orEmpty()) },
        confirmButton = { TextButton(onClick = {
            val id = deleting
            repository.edit { list, _ -> list.filterNot { it.text("id") == id } }
            deleting = null; editing = null
        }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } })
}

@Composable
private fun TaskEditor(task: JsonObject, groups: List<JsonObject>, onClose: () -> Unit, onDelete: () -> Unit, onSave: (JsonObject) -> Unit) {
    val opened = rememberSaveable { task.toString() }
    val initial = remember(opened) { Json.parseToJsonElement(opened).jsonObject }
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf(task.text("title")) }
    var notes by rememberSaveable { mutableStateOf(task.text("notes")) }
    var date by rememberSaveable { mutableStateOf(task.text("due")) }
    var time by rememberSaveable { mutableStateOf(task.text("dueTime")) }
    var group by rememberSaveable { mutableStateOf(task.text("list")) }
    var repeat by rememberSaveable { mutableStateOf(task.text("repeat")) }
    var remindMode by rememberSaveable { mutableStateOf(task.text("remindMode").ifBlank { "none" }) }
    var day by rememberSaveable { mutableStateOf(task.text("myDay")) }
    val today = LocalDate.now().toString()
    val steps = task.objects("steps")
    var stepData by rememberSaveable { mutableStateOf(JsonArray(steps).toString()) }
    val draftSteps = remember(stepData) { Json.parseToJsonElement(stepData).jsonArray.map { it.jsonObject } }
    var stepTitle by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("编辑任务") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(title, { title = it.take(200) }, label = { Text("标题") }, singleLine = true)
            OutlinedTextField(notes, { notes = it.take(5000) }, label = { Text("备注") }, maxLines = 5)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = {
                    val d = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
                    DatePickerDialog(context, { _, y, m, n -> date = LocalDate.of(y, m + 1, n).toString() },
                        d.year, d.monthValue - 1, d.dayOfMonth).show()
                }) { Text(date.ifEmpty { "截止日期" }) }
                TextButton(onClick = {
                    val t = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(18, 0))
                    TimePickerDialog(context, { _, h, m -> time = LocalTime.of(h, m).toString() }, t.hour, t.minute, true).show()
                }, enabled = date.isNotBlank()) { Text(time.ifEmpty { "全天" }) }
                if (date.isNotBlank()) TextButton(onClick = { date = ""; time = "" }) { Text("清除") }
            }
            Choice("重复", repeat, repeats.entries.map { it.key to it.value }) { repeat = it }
            if (date.isNotEmpty() || remindMode == "custom") {
                val reminders = listOf("none" to "不提醒", "due" to "到期时", "5" to "提前 5 分钟", "15" to "提前 15 分钟",
                    "30" to "提前 30 分钟", "60" to "提前 1 小时", "1440" to "提前 1 天") +
                    (if (task.text("remindMode") == "custom") listOf("custom" to "指定时间") else emptyList())
                Choice("提醒", remindMode, reminders) { remindMode = it }
            }
            if (groups.isNotEmpty()) Choice("分组", group, listOf("" to "未分组") + groups.map { it.text("id") to it.text("name") }) { group = it }
            FilterChip(day == today, { day = if (day == today) "" else today }, { Text("我的一天") })
            draftSteps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(step.flag("done"), { value -> stepData = JsonArray(draftSteps.mapIndexed { i, s ->
                        if (i == index) s.withFields { put("done", value) } else s
                    }).toString() })
                    Column {
                        Text(step.text("text"))
                        if (step.text("note").isNotBlank()) Text(step.text("note"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (draftSteps.size < 50) OutlinedTextField(stepTitle, { stepTitle = it.take(120) }, label = { Text("添加步骤") },
                singleLine = true, trailingIcon = { IconButton(onClick = {
                    stepData = JsonArray(draftSteps + buildJsonObject { put("text", stepTitle.trim()); put("done", false) }).toString()
                    stepTitle = ""
                }, enabled = stepTitle.isNotBlank()) { Icon(Icons.Rounded.Add, "添加步骤") } })
            TextButton(onClick = onDelete) { Text("删除任务", color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = {
        val edited = buildJsonObject {
            put("title", title.trim()); put("notes", notes); put("due", date); put("dueTime", time)
            put("list", group); put("repeat", repeat); put("myDay", day)
            val saved = if (stepTitle.isBlank() || draftSteps.size >= 50) draftSteps else draftSteps +
                buildJsonObject { put("text", stepTitle.trim()); put("done", false) }
            put("steps", JsonArray(saved))
            put("remindMode", if (date.isEmpty() && remindMode != "custom") "none" else remindMode)
        }
        onSave(JsonObject(edited.filter { (key, value) ->
            when (key) {
                "steps" -> value != JsonArray(initial.objects(key))
                "remindMode" -> (value as JsonPrimitive).content != initial.text(key).ifBlank { "none" }
                else -> (value as JsonPrimitive).content != initial.text(key)
            }
        }))
    }) { Text("保存") } }, dismissButton = { TextButton(onClick = onClose) { Text("取消") } })
}

@Composable
private fun Choice(label: String, selected: String, values: List<Pair<String, String>>, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(label + " · " + values.find { it.first == selected }?.second.orEmpty()) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            values.forEach { (key, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { select(key); expanded = false }) }
        }
    }
}
