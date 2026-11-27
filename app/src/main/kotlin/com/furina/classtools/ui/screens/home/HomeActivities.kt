package com.furina.classtools.ui.screens.home

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furina.classtools.ClassToolsApp
import com.furina.classtools.data.notify.*
import com.furina.classtools.data.reminder.CourseActivities
import com.furina.classtools.data.reminder.reminderOwner
import com.furina.classtools.data.session.Session
import com.furina.classtools.ui.components.light.CtLightAlertDialog

private data class ActivityRow(val key: String, val title: String, val phase: String, val time: Long,
    val open: () -> Unit, val hide: () -> Unit)

@Composable
private fun preferenceRevision(prefs: SharedPreferences): Int {
    var revision by remember(prefs) { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return revision
}

@Composable
internal fun HomeActivities(session: Session, now: Long) {
    val context = LocalContext.current
    val app = context.applicationContext as? ClassToolsApp ?: return
    val owner = session.reminderOwner
    val changes by CourseActivities.changes.collectAsStateWithLifecycle()
    val transfers = app.container.transferActivities
    val tasks by transfers.tasks.collectAsStateWithLifecycle()
    val course = remember(owner, changes, now) { CourseActivities.current(context, owner) }
    val watches = remember(owner, preferenceRevision(VoteWatches.prefs(context))) {
        VoteWatches.all(context).filter { it.owner == owner && !it.hidden }
    }
    val signups = remember(owner, preferenceRevision(SignupCountdowns.prefs(context))) {
        SignupCountdowns.all(context).filter { it.owner == owner && !it.hidden }
    }
    fun open(kind: String, id: String) = context.startActivity(ActivityLinks.intent(context, owner, kind, id))
    val rows = buildList {
        course?.let { card ->
            add(ActivityRow(card.key, card.info.name, "即将上课", card.deadline,
                { val target = card.info.focus ?: com.furina.classtools.data.timetable.CourseFocus(
                    java.time.Instant.ofEpochMilli(card.deadline).atZone(
                        com.furina.classtools.data.timetable.TimetableLink.CHINA_ZONE).toLocalDate().toString())
                  open("course", target.encode()) },
                { CourseActivities.hide(context, owner, card.generation) }))
        }
        signups.filter { now < it.expiresAt }.forEach { watch ->
            add(ActivityRow("signup/" + watch.id, watch.title, if (now >= watch.start) "已到报名时间" else "报名倒计时",
                watch.start, { open("signup", watch.id) }, { SignupCountdowns.hide(context, owner, watch.id, watch.instance) }))
        }
        watches.filter { now < it.localDeadline }.forEach { watch ->
            add(ActivityRow("vote/" + watch.id, watch.title, "投票截止倒计时", watch.localDeadline,
                { open("vote", watch.id) }, { VoteWatches.hide(context, owner, watch.id, watch.generation) }))
        }
        tasks.filter { it.owner == owner && !it.hidden && (it.active ||
            it.stage == com.furina.classtools.data.transfer.TransferStage.UNCONFIRMED) }.forEach { task ->
            val priorityTime = if (task.active) Long.MAX_VALUE else task.updatedAt
            add(ActivityRow(task.kind + "/" + task.id, task.title, task.label, priorityTime,
                { open(task.kind, task.id) }, { transfers.hide(owner, task.kind, task.id, task.generation) }))
        }
    }.sortedBy { it.time }
    var expanded by rememberSaveable(owner) { mutableStateOf(false) }
    if (rows.isEmpty()) {
        LaunchedEffect(Unit) { expanded = false }
        return
    }
    val first = rows.first()
    OutlinedCard(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("进行中 · " + rows.size, style = MaterialTheme.typography.labelLarge)
            Text(first.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(first.phase, modifier = Modifier.widthIn(max = 108.dp), maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
        }
    }
    if (expanded) CtLightAlertDialog(onDismissRequest = { expanded = false }, title = { Text("进行中的活动") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                rows.forEach { row -> key(row.key) {
                    Text(row.title, style = MaterialTheme.typography.titleSmall)
                    Text(row.phase, style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { expanded = false; row.open() }) { Text("查看详情") }
                        TextButton(onClick = row.hide) { Text("隐藏本次展示") }
                    }
                } }
            }
        }, confirmButton = { TextButton(onClick = { expanded = false }) { Text("关闭") } })
}
