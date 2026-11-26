package com.furina.classtools.ui.screens.tasks

import com.furina.classtools.ui.components.light.CtLightAlertDialog
import com.furina.classtools.ui.components.CtToastBus
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.furina.classtools.data.tasks.*
import com.furina.classtools.ui.components.CtLightTopBar
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.theme.rememberReduceMotion
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder

internal val repeats = mapOf("" to "不重复", "daily" to "每天", "weekday" to "工作日", "weekly" to "每周", "monthly" to "每月", "yearly" to "每年")

private fun tasksForView(
    all: List<JsonObject>,
    view: String,
    query: String,
    sort: String,
    today: String,
): List<JsonObject> {
    return all.filter { t ->
        val matches = when (view) {
            "important" -> t.flag("starred")
            "myday" -> t.text("myDay") == today
            "planned" -> t.text("due").isNotEmpty()
            else -> !view.startsWith("group-") || t.text("list") == view.removePrefix("group-")
        }
        (view == "done") == t.flag("done") && matches &&
            listOf(t.text("title"), t.text("notes"), t.strings("tags").joinToString(" "), t.text("sourceTitle")).any { it.contains(query.trim(), true) }
    }.sortedWith(if (view == "done") compareByDescending { it.number("doneAt") }
        else when (sort) {
            "created" -> compareByDescending { it.number("createdAt") }
            "important" -> compareByDescending { it.flag("starred") }
            "due" -> compareBy { taskDeadline(it) ?: Long.MAX_VALUE }
            "priority" -> if (view == "all") taskPriorityOrder(today) else if (view == "planned") compareBy { taskDeadline(it) ?: Long.MAX_VALUE }
                else compareBy { it.text("order").toDoubleOrNull() ?: 0.0 }
            else -> compareBy { it.text("order").toDoubleOrNull() ?: 0.0 }
        })
}

@Composable
fun TaskScreen(
    repository: TaskRepository,
    onBack: () -> Unit,
    onHomework: () -> Unit,
    homeworkOverview: (@Composable () -> Unit)? = null,
    onRefreshHomework: () -> Unit = {},
    onSource: (JsonObject) -> Unit = { onHomework() },
    onShare: (com.furina.classtools.features.export.ExportCardSpec) -> Unit = {},
    onReminderSettings: () -> Unit = {},
    onReminderCalendar: ((JsonObject) -> Unit)? = null,
    openTaskId: String? = null,
    onTaskOpened: () -> Unit = {},
    onCloseFocused: (() -> Unit)? = null,
    sessionSid: String? = null,
    createFromWidget: Boolean = false,
) {
    val context = LocalContext.current
    val snapshot by repository.snapshot.collectAsState()
    val status by repository.syncStatus.collectAsState()
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val all = remember(snapshot) { snapshot.objects("items").distinctBy { it.text("id") } }
    val groups = remember(snapshot) { snapshot.objects("lists").filter { it.text("id") != "inbox" }.sortedBy { it.number("order") } }
    val viewOrder = (snapshot.prefs().strings("sidebarOrder").filter { it in taskViews } + taskViews.keys).distinct()
    val filters = viewOrder.map { it to taskViews.getValue(it) } +
        groups.map { "group-" + it.text("id") to it.text("name") }
    val pageKeys = filters.map { it.first }
    val tabs = com.furina.classtools.ui.components.rememberListTabs(sessionSid)
    var savedView by rememberSaveable(sessionSid) { mutableStateOf(tabs?.getString("task", "all") ?: "all") }
    val allPage = pageKeys.indexOf("all")
    val pager = rememberPagerState(initialPage = pageKeys.indexOf(savedView).takeIf { it >= 0 } ?: allPage) { pageKeys.size }
    val view = pageKeys.getOrNull(pager.currentPage) ?: "all"
    val tabStrip = rememberLazyListState()
    fun selectPage(index: Int) {
        scope.launch {
            if (reduceMotion) pager.scrollToPage(index) else pager.animateScrollToPage(index)
        }
    }
    LaunchedEffect(pageKeys) {
        val target = pageKeys.indexOf(savedView).takeIf { it >= 0 } ?: allPage
        pager.scrollToPage(target)
        snapshotFlow { pager.settledPage }.collect { index -> pageKeys.getOrNull(index)?.let { savedView = it; tabs?.edit()?.putString("task", it)?.apply() } }
    }
    LaunchedEffect(view, pageKeys, reduceMotion) {
        val index = pageKeys.indexOf(view)
        val layout = tabStrip.layoutInfo
        val tab = layout.visibleItemsInfo.find { it.index == index }
        if (tab == null || tab.offset < layout.viewportStartOffset || tab.offset + tab.size > layout.viewportEndOffset) {
            if (reduceMotion) tabStrip.scrollToItem(index) else tabStrip.animateScrollToItem(index)
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var quick by rememberSaveable { mutableStateOf("") }
    var quickDue by rememberSaveable { mutableStateOf("") }
    var quickTime by rememberSaveable { mutableStateOf("") }
    var quickGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var quickStar by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf("priority") }
    var manage by rememberSaveable { mutableStateOf(false) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var newDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var importLink by rememberSaveable { mutableStateOf(false) }
    var sharedContext by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var addGroup by rememberSaveable { mutableStateOf(false) }
    var groupName by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var firstSyncComplete by remember(repository) { mutableStateOf(false) }
    var openedFromCard by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(openTaskId, all, firstSyncComplete) {
        if (openTaskId != null && all.any { it.text("id") == openTaskId }) {
            detail = openTaskId
            openedFromCard = openTaskId
            onTaskOpened()
        } else if (openTaskId != null && firstSyncComplete) {
            CtToastBus.show("当前任务列表中找不到这项任务")
            onTaskOpened()
        }
    }
    val today = LocalDate.now().toString()
    fun draft(title: String, now: Long, list: List<JsonObject>): JsonObject {
        val date = quickDue.ifEmpty { if (view == "planned") today else "" }
        val task = newTask(title, now, firstOrder(list)).withFields {
            put("list", quickGroup ?: view.removePrefix("group-").takeIf { view.startsWith("group-") }.orEmpty())
            put("due", date); put("dueTime", if (date.isBlank()) "" else quickTime)
            put("starred", quickStar || view == "important"); put("myDay", if (view == "myday") today else "")
            put("remindMode", if (date.isBlank()) "none" else snapshot.prefs().text("defaultRemind").ifBlank { "none" })
        }
        return task.withFields { put("remindAt", reminderAt(task)) }
    }
    var createConsumed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(createFromWidget) {
        if (createFromWidget && !createConsumed) {
            newDraft = draft("", System.currentTimeMillis(), all).toString()
            createConsumed = true
        }
    }
    fun resetQuick() {
        quick = ""; quickDue = ""; quickTime = ""; quickStar = false; quickGroup = null; query = ""
        if (view == "done") selectPage(allPage)
    }
    fun create() {
        val title = quick.trim()
        if (title.isEmpty()) return
        repository.edit { list, time -> list + draft(title, time, list) }
        resetQuick()
    }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, repository) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            launch { while (true) { repository.sync(); firstSyncComplete = true; delay(60_000L) } }
            while (true) { clock = System.currentTimeMillis(); delay(15_000L) }
        }
    }
    val dueReminder = all.filter { taskReminderTime(it) in 1..clock }.minByOrNull(::taskReminderTime)
    ExpressiveBackground(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            CtLightTopBar(title = "任务", scrollPx = 0f, subtitle = if (status.contains("离线") || status.contains("失败") || status.contains("同步中")) status else "${all.count { !it.flag("done") }} 项个人待办 · ${all.count { !it.flag("done") && it.text("due") == today }} 项今天截止", onBack = onBack, actions = {
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                    Icon(Icons.Rounded.Search, "搜索任务")
                }
                if (homeworkOverview == null || searching || view != "all") TextButton(onClick = onHomework) { Text("班级作业") }
                IconButton(onClick = { onRefreshHomework(); scope.launch { repository.sync() } }, enabled = status != "同步中…") {
                    Icon(Icons.Rounded.Refresh, "同步任务")
                }
            })
            if (dueReminder != null) Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("任务提醒 · " + dueReminder.text("title"), style = MaterialTheme.typography.titleSmall)
                    Row {
                        val id = dueReminder.text("id")
                        TextButton(onClick = { repository.edit { list, now -> changeCompletion(list, id, true, now) } }) { Text("完成任务") }
                        TextButton(onClick = { repository.edit { list, now -> list.map {
                            if (it.text("id") != id) it else it.withFields { put("snoozeUntil", now + 600_000L); put("lastNotifyAt", 0); put("updatedAt", now) }
                        } } }) { Text("10 分钟后提醒") }
                        TextButton(onClick = { repository.edit { list, now -> list.map {
                            if (it.text("id") != id) it else it.withFields { put("lastNotifyAt", now); put("updatedAt", now) }
                        } } }) { Text("关闭") }
                    }
                }
            }
            LazyRow(
                state = tabStrip,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(filters, key = { _, item -> item.first }) { index, item ->
                    FilterChip(view == item.first, { selectPage(index) }, { Text(item.second) })
                }
                if (groups.size < 30) item {
                    TextButton(onClick = { groupName = ""; addGroup = true }) { Text("新建分组") }
                }
            }
            if (searching) OutlinedTextField(query, { query = it },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true,
                label = { Text("搜索标题、备注或标签") })
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f),
                key = { pageKeys[it] },
                verticalAlignment = Alignment.Top,
            ) { page ->
                val pageView = pageKeys[page]
                val visible = remember(all, pageView, query, sort, today) {
                    tasksForView(all, pageView, query, sort, today)
                }
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (homeworkOverview != null && pageView == "all" && !searching) {
                        item("class-homework") { homeworkOverview() }
                        item("personal-heading") { Text("个人任务", style = MaterialTheme.typography.titleMedium) }
                    }
                    item("controls") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Choice("排序", sort, listOf("priority" to "智能", "manual" to "手动", "due" to "截止时间",
                                    "created" to "创建时间", "important" to "重要性")) { sort = it }
                                TaskMenu(repository, snapshot, { newDraft = draft(quick, System.currentTimeMillis(), all).toString() },
                                    { manage = true }, { onShare(taskShareCard(visible, false)) }, onReminderSettings, { importLink = true })
                            }
                        }
                    }
                    if (visible.isEmpty()) item("empty") {
                        Text(if (query.isNotBlank()) "没有匹配的任务" else if (pageView == "done") "暂无已完成任务" else "暂无待办任务",
                            Modifier.padding(vertical = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(visible, key = { "item-" + it.text("id") }) { task ->
                        val id = task.text("id")
                        val done = task.flag("done")
                        val colors = MaterialTheme.colorScheme
                        Card(
                            modifier = if (reduceMotion) Modifier else Modifier.animateItem(),
                            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clickable { detail = id }.padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(done, { value -> repository.edit { list, time -> changeCompletion(list, id, value, time) } },
                                    modifier = Modifier.semantics { contentDescription = if (done) "标记未完成" else "标记完成" })
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        task.text("title"), style = MaterialTheme.typography.titleMedium,
                                        color = if (done) colors.onSurfaceVariant else colors.onSurface,
                                        textDecoration = if (done) TextDecoration.LineThrough else null,
                                    )
                                    val due = taskDueLabel(task, today)
                                    if (due.isNotBlank()) Text(
                                        due, style = MaterialTheme.typography.labelMedium,
                                        color = if (!done && taskPriority(task, today) == 0) colors.error else colors.onSurfaceVariant,
                                    )
                                    val meta = buildList {
                                        if (task.text("myDay") == today) add("我的一天")
                                        groups.find { it.text("id") == task.text("list") }?.let { add(it.text("name")) }
                                        if (task.text("sourceHwId").isNotBlank()) add("班级作业")
                                        if (task.text("sourceNoticeId").isNotBlank()) add("班级通知")
                                        task.strings("tags").filterNot { it in this }.forEach { add("#$it") }
                                        if (task.number("remindAt") > 0) add("有提醒")
                                        repeats[task.text("repeat").ifBlank { "-" }]?.let { add(it) }
                                        val files = task.objects("localFiles")
                                        if (files.isNotEmpty()) add("附件 ${files.size}")
                                    }.filter(String::isNotBlank).distinct()
                                    if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant)
                                }
                                val starred = task.flag("starred")
                                IconToggleButton(starred, { value -> repository.edit { list, time ->
                                    list.map { if (it.text("id") == id) it.withFields { put("starred", value); put("updatedAt", time) } else it }
                                } }) {
                                    Icon(if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, "重要任务",
                                        tint = if (starred) colors.primary else colors.onSurfaceVariant)
                                }
                                IconButton(onClick = { editing = id }) { Icon(Icons.Rounded.Edit, "编辑", tint = colors.onSurfaceVariant) }
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
                    if (quick.isNotBlank()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(today to "今天截止", LocalDate.now().plusDays(1).toString() to "明天截止").forEach { (date, label) ->
                            FilterChip(quickDue == date, { quickDue = if (quickDue == date) "" else date; if (quickDue.isBlank()) quickTime = "" }, { Text(label) })
                        }
                        TextButton(onClick = { pickTaskDate(context, quickDue) { quickDue = it } }) { Text(quickDue.ifBlank { "其他日期" }) }
                        if (quickDue.isNotBlank()) TextButton(onClick = { pickTaskTime(context, quickTime) { quickTime = it } }) { Text(quickTime.ifBlank { "全天" }) }
                        FilterChip(quickStar, { quickStar = !quickStar }, { Text("重要") })
                        Choice("分组", quickGroup ?: view.removePrefix("group-").takeIf { view.startsWith("group-") }.orEmpty(),
                            listOf("" to "未分组") + groups.map { it.text("id") to it.text("name") }) { quickGroup = it }
                        TextButton(onClick = { newDraft = draft(quick, System.currentTimeMillis(), all).toString() }) { Text("详细编辑") }
                    }
                }
            }
        }
    }
    val selected = newDraft?.let { Json.parseToJsonElement(it).jsonObject } ?: all.find { it.text("id") == editing }
    if (importLink) ImportSharedTask({ importLink = false }) { shared ->
        importLink = false
        sharedContext = listOf(shared.groupLabel, shared.dueLabel).filter(String::isNotBlank).joinToString(" · ")
        newDraft = shared.draft(System.currentTimeMillis(), firstOrder(all)).toString()
    }
    if (manage) TaskManagement(repository, snapshot, viewOrder) { manage = false }
    all.find { it.text("id") == detail && editing == null }?.let { task ->
        TaskDetail(task, repository, groups, { editing = detail }, {
            if (openedFromCard == detail) onCloseFocused?.invoke()
            openedFromCard = null
            detail = null
        },
            onSource, { onShare(taskShareCard(listOf(task), true)) }, onReminderCalendar) { direction ->
            repository.edit { list, now ->
                val ordered = list.filterNot { it.flag("done") }.sortedBy { it.text("order").toDoubleOrNull() ?: 0.0 }.toMutableList()
                val i = ordered.indexOfFirst { it.text("id") == detail }
                if (i < 0 || i + direction !in ordered.indices) list else {
                    java.util.Collections.swap(ordered, i, i + direction)
                    val indices = ordered.mapIndexed { index, t -> t.text("id") to index }.toMap()
                    list.map { t -> indices[t.text("id")]?.let { t.withFields { put("order", it); put("updatedAt", now) } } ?: t }
                }
            }
            sort = "manual"
        }
    }
    if (addGroup) CtLightAlertDialog(onDismissRequest = { addGroup = false }, title = { Text("新建分组") },
        text = { OutlinedTextField(groupName, { groupName = it.take(20) }, label = { Text("分组名称") }, singleLine = true) },
        confirmButton = { TextButton(enabled = groupName.isNotBlank(), onClick = {
            repository.addGroup(groupName)?.let { savedView = "group-" + it }
            addGroup = false
        }) { Text("创建") } }, dismissButton = { TextButton(onClick = { addGroup = false }) { Text("取消") } })
    if (selected != null) key(selected.text("id")) {
        TaskEditor(selected, groups, isNew = newDraft != null, sharedContext = sharedContext,
            onClose = { editing = null; newDraft = null; sharedContext = null }, onDelete = {
            repository.edit { list, _ -> list.filterNot { it.text("id") == selected.text("id") } }
            if (detail == editing) detail = null
            editing = null
        }, onSave = { patch ->
            repository.edit { list, now ->
                val creating = newDraft != null
                val updated = (if (creating) list + selected else list).map { original ->
                    if (original.text("id") != selected.text("id") || patch.isEmpty()) original else JsonObject(original + (patch - "done")).let { edited ->
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
                if ("done" in patch) changeCompletion(updated, selected.text("id"), patch.flag("done"), now) else updated
            }
            if (newDraft != null) resetQuick()
            newDraft = null
            sharedContext = null
            editing = null
        })
    }
}

@Composable
private fun TaskEditor(task: JsonObject, groups: List<JsonObject>, isNew: Boolean, sharedContext: String?,
    onClose: () -> Unit, onDelete: () -> Unit, onSave: (JsonObject) -> Unit) {
    val opened = rememberSaveable { task.toString() }
    val initial = remember(opened) { Json.parseToJsonElement(opened).jsonObject }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf(task.text("title")) }
    var notes by rememberSaveable { mutableStateOf(task.text("notes")) }
    var date by rememberSaveable { mutableStateOf(task.text("due")) }
    var time by rememberSaveable { mutableStateOf(task.text("dueTime")) }
    var group by rememberSaveable { mutableStateOf(task.text("list")) }
    var repeat by rememberSaveable { mutableStateOf(task.text("repeat")) }
    var remindMode by rememberSaveable { mutableStateOf(task.text("remindMode").ifBlank { "none" }) }
    var day by rememberSaveable { mutableStateOf(task.text("myDay")) }
    var starred by rememberSaveable { mutableStateOf(task.flag("starred")) }
    var done by rememberSaveable { mutableStateOf(task.flag("done")) }
    var tags by rememberSaveable { mutableStateOf(task.strings("tags").joinToString("，")) }
    val customStart = taskTimeText(task.number("remindCustom").takeIf { it > 0 } ?: task.number("remindAt"))
    var customDate by rememberSaveable { mutableStateOf(customStart.substringBefore(' ').ifBlank { LocalDate.now().toString() }) }
    var customTime by rememberSaveable { mutableStateOf(customStart.substringAfter(' ', "09:00")) }
    var fileData by rememberSaveable { mutableStateOf(JsonArray(task.objects("localFiles")).toString()) }
    val files = remember(fileData) { Json.parseToJsonElement(fileData).jsonArray.map { it.jsonObject } }
    var loadingFile by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            loadingFile = true
            try {
                val attachment = withContext(Dispatchers.IO) { TaskFiles.copy(context, uri) }
                fileData = JsonArray(files + attachment).toString()
            } catch (_: java.io.IOException) {
                CtToastBus.show("文件读取失败，请重新选择")
            } catch (_: SecurityException) {
                CtToastBus.show("没有读取此文件的权限")
            } catch (_: IllegalArgumentException) {
                CtToastBus.show("无法读取此文件")
            } finally { loadingFile = false }
        }
    }
    fun close() {
        if (loadingFile) return
        TaskFiles.discard(context, files - initial.objects("localFiles").toSet())
        onClose()
    }
    val today = LocalDate.now().toString()
    val steps = task.objects("steps")
    var stepData by rememberSaveable { mutableStateOf(JsonArray(steps).toString()) }
    val draftSteps = remember(stepData) { Json.parseToJsonElement(stepData).jsonArray.map { it.jsonObject } }
    var stepTitle by rememberSaveable { mutableStateOf("") }
    fun updateStep(index: Int, field: String, value: String) {
        stepData = JsonArray(draftSteps.mapIndexed { i, s -> if (i == index) s.withFields { put(field, value) } else s }).toString()
    }
    CtLightAlertDialog(onDismissRequest = ::close, title = { Text(if (sharedContext != null) "保存为我的任务" else if (isNew) "新建任务" else "编辑任务") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!sharedContext.isNullOrEmpty()) Text("原任务 · $sharedContext", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(title, { title = it.take(200) }, label = { Text("标题") }, singleLine = true)
            OutlinedTextField(notes, { notes = it.take(5000) }, label = { Text("备注") }, maxLines = 5)
            OutlinedTextField(tags, { tags = it.take(120) }, label = { Text("标签（逗号分隔）") }, singleLine = true)
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
            if (date.isNotBlank() && time.isNotBlank()) TextButton(onClick = { time = "" }) { Text("设为全天") }
            Choice("重复", repeat, repeats.entries.map { it.key to it.value }) { repeat = it }
            Choice("提醒", remindMode, (if (date.isNotBlank()) reminderOptions else reminderOptions.take(1)) + ("custom" to "指定时间")) { remindMode = it }
            if (remindMode == "custom") Row {
                TextButton(onClick = { pickTaskDate(context, customDate) { customDate = it } }) { Text(customDate) }
                TextButton(onClick = { pickTaskTime(context, customTime) { customTime = it } }) { Text(customTime) }
            }
            if (groups.isNotEmpty()) Choice("分组", group, listOf("" to "未分组") + groups.map { it.text("id") to it.text("name") }) { group = it }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(day == today, { day = if (day == today) "" else today }, { Text("我的一天") })
                FilterChip(starred, { starred = !starred }, { Text("重要") })
                FilterChip(done, { done = !done }, { Text("已完成") })
            }
            draftSteps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(step.flag("done"), { value -> stepData = JsonArray(draftSteps.mapIndexed { i, s ->
                        if (i == index) s.withFields { put("done", value) } else s
                    }).toString() })
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(step.text("text"), { updateStep(index, "text", it.take(120)) }, singleLine = true, label = { Text("步骤 ${index + 1}") })
                        OutlinedTextField(step.text("note"), { updateStep(index, "note", it.take(500)) }, maxLines = 3, label = { Text("步骤备注") })
                    }
                    TextButton(onClick = { stepData = JsonArray(draftSteps.filterIndexed { i, _ -> i != index }).toString() }) { Text("删除") }
                }
            }
            if (draftSteps.size < 50) OutlinedTextField(stepTitle, { stepTitle = it.take(120) }, label = { Text("添加步骤") },
                singleLine = true, trailingIcon = { IconButton(onClick = {
                    stepData = JsonArray(draftSteps + buildJsonObject { put("text", stepTitle.trim()); put("done", false) }).toString()
                    stepTitle = ""
                }, enabled = stepTitle.isNotBlank()) { Icon(Icons.Rounded.Add, "添加步骤") } })
            files.forEach { attachment -> Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(modifier = Modifier.weight(1f), onClick = {
                    runCatching { TaskFiles.open(context, attachment) }.onFailure { CtToastBus.show("无法打开附件") }
                }) { Text(attachment.text("n")) }
                TextButton(onClick = {
                    if (attachment !in initial.objects("localFiles")) TaskFiles.discard(context, listOf(attachment))
                    fileData = JsonArray(files - attachment).toString()
                }) { Text("移除") }
            } }
            if (files.size < 2) TextButton(onClick = { pickFile.launch(arrayOf("*/*")) }, enabled = !loadingFile) {
                Text(if (loadingFile) "正在保存附件…" else "添加附件（仅本机，最多 2 个）")
            }
            if (!isNew) TextButton(onClick = { confirmDelete = true }, enabled = !loadingFile) { Text("删除任务", color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = title.isNotBlank() && !loadingFile, onClick = {
        val edited = buildJsonObject {
            put("title", title.trim()); put("notes", notes); put("due", date); put("dueTime", time)
            put("list", group); put("repeat", repeat); put("myDay", day)
            put("starred", starred); put("done", done)
            put("localFiles", JsonArray(files))
            put("tags", JsonArray(tags.split(Regex("[,，;；#]+")).map { it.trim().take(16) }.filter(String::isNotBlank).distinct().take(6).map(::JsonPrimitive)))
            if (remindMode == "custom") put("remindCustom", LocalDate.parse(customDate).atTime(LocalTime.parse(customTime)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            val saved = if (stepTitle.isBlank() || draftSteps.size >= 50) draftSteps else draftSteps +
                buildJsonObject { put("text", stepTitle.trim()); put("done", false) }
            put("steps", JsonArray(saved.filter { it.text("text").isNotBlank() }))
            put("remindMode", if (date.isEmpty() && remindMode != "custom") "none" else remindMode)
        }
        onSave(JsonObject(edited.filter { (key, value) ->
            when (key) {
                "steps", "localFiles" -> value != JsonArray(initial.objects(key))
                "tags" -> value != JsonArray(initial.strings(key).map(::JsonPrimitive))
                "done", "starred" -> value != JsonPrimitive(initial.flag(key))
                "remindMode" -> (value as JsonPrimitive).content != initial.text(key).ifBlank { "none" }
                else -> (value as JsonPrimitive).content != initial.text(key)
            }
        }))
    }) { Text("保存") } }, dismissButton = { TextButton(onClick = ::close, enabled = !loadingFile) { Text("取消") } })
    if (confirmDelete) CtLightAlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("删除这项任务？") },
        text = { Text(title) }, confirmButton = { TextButton(onClick = {
            TaskFiles.discard(context, files - initial.objects("localFiles").toSet())
            confirmDelete = false; onDelete()
        }) { Text("删除") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
}

@Composable
internal fun Choice(label: String, selected: String, values: List<Pair<String, String>>, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(label + " · " + values.find { it.first == selected }?.second.orEmpty()) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            values.forEach { (key, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { select(key); expanded = false }) }
        }
    }
}
