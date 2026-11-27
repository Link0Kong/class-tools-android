package com.furina.classtools.ui.screens.tasks

import com.furina.classtools.ui.components.CtToastBus
import com.furina.classtools.ui.components.background.CtGlassSurface
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.furina.classtools.data.homework.HomeworkAttachmentMeta
import com.furina.classtools.data.tasks.*
import com.furina.classtools.ui.components.CtFeedbackWindow
import com.furina.classtools.ui.components.CtTopBar
import kotlinx.serialization.json.*

/** The list stays mounted so Back restores the category, query and scroll position. */
@Composable
internal fun TaskDetailPage(
    pageTitle: String,
    onBack: () -> Unit,
    inline: Boolean = false,
    actions: @Composable () -> Unit = {},
    bottom: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val page: @Composable () -> Unit = {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val insets = if (inline) Modifier else Modifier.safeDrawingPadding()
            Column(Modifier.fillMaxSize().then(insets), horizontalAlignment = Alignment.CenterHorizontally) {
                CtTopBar(title = pageTitle, onBack = if (inline) null else onBack, actions = {
                    actions()
                    if (inline) IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, "收起任务详情") }
                })
                Column(Modifier.weight(1f).widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
                HorizontalDivider()
                Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = bottom)
            }
        }
    }
    if (inline) page() else Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        CtFeedbackWindow()
        page()
    }
}

@Composable
internal fun DetailSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
internal fun DetailFact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.width(76.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun TaskDetail(task: JsonObject, repository: TaskRepository, groups: List<JsonObject>, onEdit: () -> Unit,
    onClose: () -> Unit, onSource: (JsonObject) -> Unit, onShare: () -> Unit, onReminderCalendar: ((JsonObject) -> Unit)?,
    inline: Boolean, onMove: (Int) -> Unit) {
    val context = LocalContext.current
    val id = task.text("id")
    val done = task.flag("done")
    val starred = task.flag("starred")
    val group = groups.find { it.text("id") == task.text("list") }?.text("name").orEmpty()
    val source = when {
        task.text("sourceHwId").isNotBlank() -> "班级作业"
        task.text("sourceNoticeId").isNotBlank() -> "班级通知"
        else -> ""
    }
    val steps = task.objects("steps")
    val files = task.objects("localFiles")
    val tags = task.strings("tags")
    val reminder = maxOf(task.number("remindAt"), task.number("snoozeUntil"))
    val deadline = taskDeadline(task)
    val overdue = !done && deadline != null && deadline < System.currentTimeMillis()
    var menu by remember { mutableStateOf(false) }
    fun shareLink() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, TaskShareLink.create(task, group))
        context.startActivity(Intent.createChooser(intent, "分享可保存的任务链接"))
    }
    TaskDetailPage("任务详情", onClose, inline = inline, actions = {
        IconToggleButton(checked = starred, onCheckedChange = { value ->
            repository.edit { items, now -> items.map {
                if (it.text("id") == id) it.withFields { put("starred", value); put("updatedAt", now) } else it
            } }
        }) { Icon(if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, "重要任务",
            tint = if (starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "更多任务操作") }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("分享可保存链接") }, onClick = { menu = false; shareLink() })
                DropdownMenuItem(text = { Text("分享任务图片") }, onClick = { menu = false; onShare() })
                if (!done) {
                    DropdownMenuItem(text = { Text("上移") }, onClick = { menu = false; onMove(-1) })
                    DropdownMenuItem(text = { Text("下移") }, onClick = { menu = false; onMove(1) })
                }
            }
        }
    }, bottom = {
        OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("编辑任务") }
        Button(onClick = { repository.edit { items, now -> changeCompletion(items, id, !done, now) } }, modifier = Modifier.weight(1f)) {
            Text(if (done) "标记未完成" else "标记完成")
        }
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (done) "已完成" else if (overdue) "已逾期" else "待办", style = MaterialTheme.typography.labelLarge,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text(task.text("title").ifBlank { "未命名任务" }, style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
        }
        CtGlassSurface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailFact("截止时间", task.text("due").takeIf { it.isNotBlank() }?.let {
                    it + " " + task.text("dueTime").ifBlank { "全天" }
                } ?: "未设置")
                DetailFact("提醒", when {
                    done -> "任务已完成"
                    reminder <= 0 -> "未设置"
                    task.number("lastNotifyAt") >= reminder -> "已提醒 · " + taskTimeText(reminder)
                    else -> taskTimeText(reminder) + if (task.number("snoozeUntil") >= reminder) " · 已推迟" else ""
                })
                if (onReminderCalendar != null && !done && reminder > System.currentTimeMillis() &&
                    reminder > task.number("lastNotifyAt")) {
                    TextButton(onClick = { onReminderCalendar(task) }) { Text("将提醒加入系统日历") }
                }
                if (task.text("repeat").isNotBlank()) repeats[task.text("repeat")]?.let { DetailFact("重复", it) }
                task.text("myDay").takeIf { it.isNotBlank() }?.let { DetailFact("我的一天", it) }
                if (group.isNotBlank()) DetailFact("分组", group)
                if (tags.isNotEmpty()) DetailFact("标签", tags.joinToString("  ") { "#$it" })
            }
        }
        val notes = task.text("notes").trim()
        if (notes.isNotEmpty() && notes != task.text("title").trim()) DetailSection("备注") { Text(notes) }
        if (steps.isNotEmpty()) DetailSection("步骤 · ${steps.count { it.flag("done") }}/${steps.size}") {
            steps.forEachIndexed { index, step ->
                val checked = step.flag("done")
                Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox) { value ->
                    repository.edit { items, now -> items.map { current ->
                        if (current.text("id") != id || current.objects("steps").getOrNull(index) != step) current
                        else current.withFields {
                            put("updatedAt", now)
                            put("steps", JsonArray(current.objects("steps").mapIndexed { i, s ->
                                if (i == index) s.withFields { put("done", value) } else s
                            }))
                        }
                    } }
                }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Checkbox(checked, onCheckedChange = null)
                    Column {
                        Text(step.text("text"), textDecoration = if (checked) TextDecoration.LineThrough else null)
                        step.text("note").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        if (files.isNotEmpty()) DetailSection("本机附件 · ${files.size}") {
            files.forEach { file ->
                Row(Modifier.fillMaxWidth().clickable {
                    runCatching { TaskFiles.open(context, file) }.onFailure { CtToastBus.show("无法打开附件") }
                }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(file.text("n").ifBlank { "附件" }, Modifier.weight(1f))
                    if (file.number("s") > 0) Text(HomeworkAttachmentMeta.formatSize(file.number("s")), style = MaterialTheme.typography.bodySmall)
                    Text("打开", Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (source.isNotEmpty()) DetailSection("来源") {
            OutlinedButton(onClick = { onSource(task) }) { Text("查看" + source +
                task.text("sourceTitle").takeIf { it.isNotBlank() && it != task.text("title") }?.let { " · $it" }.orEmpty()) }
        }
        if (task.number("createdAt") > 0 || task.number("updatedAt") > 0 || task.number("doneAt") > 0) {
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (task.number("createdAt") > 0) DetailFact("创建时间", taskTimeText(task.number("createdAt")))
                if (task.number("updatedAt") > task.number("createdAt")) DetailFact("更新时间", taskTimeText(task.number("updatedAt")))
                if (done && task.number("doneAt") > 0) DetailFact("完成时间", taskTimeText(task.number("doneAt")))
            }
        }
    }
}
