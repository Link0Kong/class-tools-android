package com.furina.classtools.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.furina.classtools.data.reminder.*
import com.furina.classtools.ui.components.CtToastBus
import com.furina.classtools.ui.components.light.CtLightAlertDialog
import com.furina.classtools.ui.screens.notices.pickReminderInstant
import java.time.ZoneId
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val reminderDate = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private fun timeText(at: Long) = reminderDate.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))

@Composable
internal fun ExactAlarmAccess() {
    if (Build.VERSION.SDK_INT < 31) return
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(UserReminderAlarms.exactAllowed(context)) }
    LifecycleResumeEffect(context) {
        allowed = UserReminderAlarms.exactAllowed(context)
        onPauseOrDispose { }
    }
    if (!allowed) TextButton(onClick = {
        if (!UserReminderAlarms.openSettings(context)) CtToastBus.show("请在系统设置中打开「闹钟和提醒」")
    }) { Text("允许准时提醒 · 当前可能延迟") }
}

@Composable
internal fun UserReminderDialog(
    kind: OneShotTargetKind,
    id: String,
    title: String,
    options: List<OneShotPlannedTime>,
    scheduler: ReminderOneShotScheduler?,
    onDismiss: () -> Unit,
    onPermissions: () -> Unit,
    onChange: () -> Unit = {},
    onCalendar: (OneShotKey, Long, String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var calendar by rememberSaveable(kind, id) { mutableStateOf(false) }
    var at by rememberSaveable(kind, id) { mutableLongStateOf(0L) }
    var custom by rememberSaveable(kind, id) { mutableLongStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val pending by produceState(emptyList(), kind, id, revision) {
        value = scheduler?.listPending().orEmpty().filter { it.key.targetKind == kind && it.key.targetId == id }
    }
    val choices = (options + listOfNotNull(custom.takeIf { it > 0 }?.let {
        OneShotPlannedTime(OneShotReminderOption.CUSTOM, "自定义提醒", it, it)
    })).distinctBy { it.fireAtMillis }
    val selected = choices.find { it.fireAtMillis == at }
    CtLightAlertDialog(onDismissRequest = onDismiss, title = { Text("提醒我") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            for (option in choices) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(at == option.fireAtMillis,
                    role = Role.RadioButton, onClick = { at = option.fireAtMillis }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(at == option.fireAtMillis, onClick = null)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(option.option.label)
                        Text(timeText(option.fireAtMillis), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            TextButton(onClick = { pickReminderInstant(context, ZoneId.systemDefault()) { custom = it; at = it } }) {
                Text("自定义提醒时间")
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(calendar, role = Role.Checkbox,
                onValueChange = { calendar = it }), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(calendar, onCheckedChange = null)
                Text("同时加入系统日历", Modifier.padding(start = 8.dp))
            }
            ExactAlarmAccess()
            if (pending.isNotEmpty()) {
                HorizontalDivider()
                Text("已设置的提醒", style = MaterialTheme.typography.labelLarge)
                for (item in pending) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(timeText(item.fireAtMillis), Modifier.weight(1f))
                        TextButton(onClick = { onCalendar(item.key, item.fireAtMillis, item.label) }) { Text("加入日历") }
                        TextButton(onClick = { scope.launch { scheduler?.cancel(item.key); revision++; onChange() } }) { Text("撤销") }
                    }
                }
            }
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        confirmButton = {
            TextButton(enabled = selected != null && !busy, onClick = {
                val picked = selected ?: return@TextButton
                if (scheduler == null) { CtToastBus.show("提醒服务不可用"); return@TextButton }
                busy = true
                scope.launch {
                    val result = when (kind) {
                        OneShotTargetKind.NOTICE -> scheduler.scheduleNoticeReminder(id, at, title, picked.targetAtMillis)
                        OneShotTargetKind.HOMEWORK -> scheduler.scheduleHomeworkReminder(id, at, title, picked.targetAtMillis)
                    }
                    busy = false
                    when (result) {
                        is OneShotScheduleResult.Scheduled -> {
                            onChange()
                            onDismiss()
                            CtToastBus.show("提醒已设在 ${timeText(at)}")
                            if (calendar) onCalendar(result.key, at, picked.label)
                        }
                        OneShotScheduleResult.PermissionMissing -> {
                            if (calendar) onCalendar(ReminderOneShot.key(kind, id, at), at, picked.label)
                            CtToastBus.show("应用提醒需要通知权限")
                            if (!calendar) { onDismiss(); onPermissions() }
                        }
                        is OneShotScheduleResult.TooLate -> CtToastBus.show("提醒时间已过，请重新选择")
                        is OneShotScheduleResult.LimitReached -> CtToastBus.show("最多可设置 ${result.limit} 条同类提醒")
                        OneShotScheduleResult.EmptyTarget -> CtToastBus.show("找不到这项内容")
                        is OneShotScheduleResult.Failed -> CtToastBus.show(result.message)
                    }
                }
            }) { Text("保存提醒") }
        })
}
