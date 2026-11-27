package com.furina.classtools.ui.screens.notices

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.furina.classtools.data.notify.SignupClock
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notify.SignupCountdown
import com.furina.classtools.data.notify.SignupCountdowns
import com.furina.classtools.data.timetable.TimetableLink
import com.furina.classtools.ui.components.CtToastBus
import com.furina.classtools.ui.components.light.CtLightAlertDialog
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

internal fun pickReminderInstant(context: Context, zone: ZoneId, allowCurrentMinute: Boolean = false, onPicked: (Long) -> Unit) {
    val seed = ZonedDateTime.now(zone).plusMinutes(5)
    DatePickerDialog(context, { _, y, month, day ->
        TimePickerDialog(context, { _, h, m ->
            val at = LocalDate.of(y, month + 1, day).atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()
            val latest = if (allowCurrentMinute) at + 59_999L else at
            if (latest > System.currentTimeMillis()) onPicked(at) else CtToastBus.show("请选择未来的时间")
        }, seed.hour, seed.minute, true).show()
    }, seed.year, seed.monthValue - 1, seed.dayOfMonth).show()
}

@Composable
internal fun SignupCountdownControl(owner: String, item: NoticeUiItem) {
    val context = LocalContext.current
    val entry = item.auto
    val group = remember(item) {
        entry?.group?.copy(id = entry.id, title = entry.title)
            ?: NoticeGroupDto(id = item.manual?.id.orEmpty(), title = item.title, cat = item.cat)
    }
    val id = group.id
    if (id.isBlank()) return
    var saved by remember(owner, id) { mutableStateOf(SignupCountdowns.get(context, owner, id)) }
    DisposableEffect(context, owner, id) {
        val prefs = SignupCountdowns.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> saved = SignupCountdowns.get(context, owner, id) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        saved = SignupCountdowns.get(context, owner, id)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var configure by rememberSaveable(owner, id) { mutableStateOf(false) }
    var custom by rememberSaveable(owner, id) { mutableStateOf<Long?>(null) }
    var seconds by rememberSaveable(owner, id) { mutableStateOf("00.000") }
    var lead by rememberSaveable(owner, id) { mutableIntStateOf(5) }
    val now = System.currentTimeMillis()
    val active = saved?.takeIf { it.expiresAt > now }
    val offset = SignupClock.millisInMinute(seconds)
    val watch = if (custom != null && offset == null) null else
        SignupCountdown.from(owner, group, entry?.items.orEmpty(), now, custom?.plus(offset ?: 0L))
    if (!configure && active == null && (item.expired || item.hidden || (watch == null &&
            listOf("活动", "报名", "开抢").none { word ->
                item.cat.contains(word) || item.title.contains(word) || item.summary.contains(word) ||
                    group.aiSegments.any { it.type.contains(word) }
            }))) return
    OutlinedButton(onClick = {
        if (active != null) context.startActivity(SignupCountdowns.timerIntent(context, owner, id))
        else configure = true
    }) { Text(if (active == null) "设置报名倒计时" else "查看报名倒计时") }
    if (!configure) return
    val manager = context.getSystemService(android.app.NotificationManager::class.java)
    CtLightAlertDialog(onDismissRequest = { configure = false }, title = { Text("报名倒计时") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(group.title, style = MaterialTheme.typography.titleMedium)
                if (watch != null) Text("北京时间 ${SignupCountdown.timeText(watch.start)}")
                TextButton(onClick = { pickReminderInstant(context, TimetableLink.CHINA_ZONE, allowCurrentMinute = true) {
                    custom = it
                    seconds = "00.000"
                } }) {
                    Text(if (watch != null) "修改开抢时间" else "选择开抢时间")
                }
                if (custom != null) OutlinedTextField(value = seconds, onValueChange = { seconds = it.take(6) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, isError = offset == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text("开抢秒数（0–59.999）") })
                Text("提前显示实时活动", style = MaterialTheme.typography.labelLarge)
                for (minutes in listOf(1, 5, 15, 30)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).selectable(lead == minutes,
                        role = Role.RadioButton, onClick = { lead = minutes }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(lead == minutes, onClick = null)
                        Text("$minutes 分钟", modifier = Modifier.padding(start = 12.dp))
                    }
                }
                Text("点通知查看报名活动。通过“毫秒倒计时”可打开画中画。", style = MaterialTheme.typography.bodySmall)
                if (android.os.Build.VERSION.SDK_INT >= 36 && !manager.canPostPromotedNotifications()) {
                    TextButton(onClick = {
                        val settings = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        runCatching { context.startActivity(settings) }.onFailure { CtToastBus.show("请在系统通知设置中允许实时活动") }
                    }) { Text("允许状态栏显示实时活动") }
                }
            }
        }, confirmButton = {
            TextButton(enabled = watch != null, onClick = {
                if (watch == null) return@TextButton
                if (watch.start <= System.currentTimeMillis()) { CtToastBus.show("报名时间已到"); return@TextButton }
                if (SignupCountdowns.start(context, watch.copy(leadMinutes = lead))) {
                    configure = false
                    CtToastBus.show(if (!SignupCountdowns.available(context)) "已开启，可在工作台查看倒计时"
                        else if (watch.start - System.currentTimeMillis() <= lead * 60_000L) "报名倒计时已开启" else "已设置，开抢前 $lead 分钟显示")
                } else CtToastBus.show("未能设置倒计时，请重新选择时间")
            }) { Text("开启倒计时") }
        }, dismissButton = { TextButton(onClick = { configure = false }) { Text("取消") } })
}
