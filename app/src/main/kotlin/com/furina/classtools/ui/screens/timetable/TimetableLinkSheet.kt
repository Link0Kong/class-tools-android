package com.furina.classtools.ui.screens.timetable

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.timetable.CalendarSyncOutcome
import com.furina.classtools.data.timetable.TimetableCalendarSync
import com.furina.classtools.data.timetable.TimetableIcs
import com.furina.classtools.data.timetable.TimetableLink
import com.furina.classtools.data.timetable.TimetableLinkFiles
import com.furina.classtools.data.timetable.TtSnapshot
import com.furina.classtools.data.timetable.WakeUpSchedule
import com.furina.classtools.data.timetable.WritableCalendarInfo
import com.furina.classtools.ui.components.expressive.CtBottomSheet
import com.furina.classtools.ui.components.expressive.CtBottomSheetHeader
import com.furina.classtools.ui.components.tool.CtToolPrimaryButton
import com.furina.classtools.ui.components.tool.CtToolSegmented
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtSpace
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * 「课表接入」入口（悬浮按钮）：生成 .ics / WakeUp 备份，或原生写入系统日历并提醒。
 *
 * 放在课表页右下角（不占顶栏，避免与顶栏/紧凑化改动冲突）。
 */
@Composable
fun TimetableLinkFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = CtShapeTokens.pill,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        icon = {
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        text = {
            Text(
                text = "课表接入",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
    )
}

/**
 * 课表接入弹层（纯增量功能，自包含权限申请/文件导出/日历写入）。
 *
 * 结构（简洁、不堆说明文字）：
 * - 系统日历：权限 → 选择可写日历 → 写入本学期（从当前周起）/清除已写入；
 * - 文件导出：`.ics`（系统日历/WakeUp 导入，含提前提醒）与 `.wakeup_schedule`（WakeUp 备份导入）。
 *
 * @param termStart 开学日（用于生成 RRULE/事件日期与文件名）
 * @param onMessage 结果提示（页面 Snackbar）
 */
@Composable
fun TimetableLinkSheet(
    snapshot: TtSnapshot,
    termStart: LocalDate?,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sync = remember(context) { TimetableCalendarSync(context) }
    val zone = remember { ZoneId.of("Asia/Shanghai") }
    val maxWeek = remember(snapshot) { TimetableLink.semesterWeeks(snapshot.c) }
    val nowMillis = remember { System.currentTimeMillis() }
    val currentWeek = remember(snapshot, nowMillis, maxWeek) {
        TimetableLink.currentWeek(snapshot, nowMillis, zone = zone, maxWeek = maxWeek) ?: 1
    }
    val plannedCount = remember(snapshot, maxWeek, currentWeek) {
        TimetableLink.planSessions(
            snapshot = snapshot,
            zone = zone,
            fromWeek = currentWeek,
            toWeek = maxWeek,
            limit = TimetableLink.MAX_CALENDAR_EVENTS,
        ).size
    }
    val reminderOptions = TimetableLink.REMINDER_MINUTES_OPTIONS
    val defaultReminderIndex = reminderOptions.indexOf(TimetableLink.DEFAULT_REMINDER_MINUTES).coerceAtLeast(0)

    var reminderChoice by remember { mutableIntStateOf(defaultReminderIndex) }
    val reminderMinutes: Int = reminderOptions[reminderChoice]
    // .ics 导出范围：0=整学期（RRULE 系列）；1=仅当前教学周（一次性事件）
    var icsWeekScope by remember { mutableIntStateOf(0) }
    var hasAccess by remember { mutableStateOf(sync.hasCalendarAccess()) }
    var permissionRequested by remember { mutableStateOf(false) }
    var calendars by remember { mutableStateOf<List<WritableCalendarInfo>>(emptyList()) }
    var selectedCalendarId by remember { mutableStateOf<Long?>(null) }
    var loadingCalendars by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var lastRecord by remember { mutableStateOf(sync.lastRecord()) }

    suspend fun reloadCalendars() {
        loadingCalendars = true
        calendars = sync.writableCalendars()
        selectedCalendarId = calendars.firstOrNull()?.id
        loadingCalendars = false
    }

    LaunchedEffect(hasAccess) {
        if (hasAccess) reloadCalendars()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionRequested = true
        hasAccess = sync.hasCalendarAccess()
        if (!hasAccess) onMessage("未授予日历权限，无法写入系统日历")
    }

    fun requestPermission() {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
        )
    }

    fun startIntent(intent: android.content.Intent, failure: String) {
        runCatching { context.startActivity(intent) }.onFailure { onMessage(failure) }
    }

    fun shareFile(export: TimetableLinkFiles.ExportFile, chooserTitle: String) {
        startIntent(TimetableLinkFiles.shareIntent(context, export, chooserTitle), "没有可分享的应用")
    }

    suspend fun saveFile(export: TimetableLinkFiles.ExportFile) {
        val uri = TimetableLinkFiles.saveToDownloads(context, export)
        onMessage(
            if (uri != null) {
                "已保存到下载：${export.displayName}"
            } else {
                "保存失败（系统版本过低或权限限制），请用「分享」发送文件"
            },
        )
    }

    fun exportIcs(open: Boolean) {
        if (busy) return
        val document = TimetableIcs.build(
            snapshot = snapshot,
            reminderMinutes = reminderMinutes,
            maxWeeks = maxWeek,
            onlyWeek = if (icsWeekScope == 1) currentWeek else null,
        )
        if (document == null) {
            onMessage("开学日未知或无课程，暂时无法生成日历文件")
            return
        }
        scope.launch {
            busy = true
            val export = TimetableLinkFiles.writeIcs(context, document, termStart)
            if (open) {
                // 没有 ACTION_VIEW 日历应用时回退到分享面板（系统包可见性限制下不预判 resolve）
                val started = runCatching {
                    context.startActivity(TimetableLinkFiles.viewIcsIntent(context, export))
                    true
                }.getOrDefault(false)
                if (!started) shareFile(export, "分享课表日历")
            } else {
                shareFile(export, "分享课表日历")
            }
            busy = false
        }
    }

    fun downloadIcs() {
        if (busy) return
        val document = TimetableIcs.build(
            snapshot = snapshot,
            reminderMinutes = reminderMinutes,
            maxWeeks = maxWeek,
            onlyWeek = if (icsWeekScope == 1) currentWeek else null,
        )
        if (document == null) {
            onMessage("开学日未知或无课程，暂时无法生成日历文件")
            return
        }
        scope.launch {
            busy = true
            saveFile(TimetableLinkFiles.writeIcs(context, document, termStart))
            busy = false
        }
    }

    fun shareWakeUp() {
        if (busy) return
        val document = WakeUpSchedule.build(snapshot = snapshot, maxWeeks = maxWeek)
        if (document == null) {
            onMessage("开学日未知或无课程，暂时无法生成 WakeUp 文件")
            return
        }
        scope.launch {
            busy = true
            shareFile(TimetableLinkFiles.writeWakeUp(context, document, termStart), "导入 WakeUp 课程表")
            busy = false
        }
    }

    fun downloadWakeUp() {
        if (busy) return
        val document = WakeUpSchedule.build(snapshot = snapshot, maxWeeks = maxWeek)
        if (document == null) {
            onMessage("开学日未知或无课程，暂时无法生成 WakeUp 文件")
            return
        }
        scope.launch {
            busy = true
            saveFile(TimetableLinkFiles.writeWakeUp(context, document, termStart))
            busy = false
        }
    }

    fun syncToCalendar() {
        val calendarId = selectedCalendarId ?: return
        if (busy) return
        scope.launch {
            busy = true
            when (val outcome = sync.sync(snapshot, calendarId, reminderMinutes)) {
                is CalendarSyncOutcome.Success -> {
                    lastRecord = sync.lastRecord()
                    val trimmedText = if (outcome.trimmed) "（超出上限，写入 ${outcome.inserted}/${outcome.total}）" else ""
                    onMessage("已写入 ${outcome.inserted} 个日程到「${outcome.calendarName}」$trimmedText")
                }

                CalendarSyncOutcome.NeedsPermission -> {
                    hasAccess = false
                    onMessage("需要日历权限")
                }

                CalendarSyncOutcome.NoWritableCalendar -> onMessage("本机没有可写日历")
                CalendarSyncOutcome.NoEvents -> onMessage("没有可写入的课程")
                is CalendarSyncOutcome.Failed -> onMessage(outcome.message)
            }
            busy = false
        }
    }

    fun clearCalendar() {
        if (busy) return
        scope.launch {
            busy = true
            val deleted = sync.clearAll()
            lastRecord = sync.lastRecord()
            onMessage(if (deleted > 0) "已清除 $deleted 个课程日程" else "已清除写入记录")
            busy = false
        }
    }

    CtBottomSheet(onDismissRequest = onDismiss) {
        CtBottomSheetHeader(
            title = "课表接入",
            subtitle = "${snapshot.c.size} 门课 · 共 $maxWeek 周",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---- 系统日历 -----------------------------------------------------
            LinkSectionTitle(text = "系统日历")
            if (!hasAccess) {
                LinkText("写入系统日历需要「日历」权限（选择可写日历 + 添加日程与提醒）")
                CtToolPrimaryButton(
                    text = if (permissionRequested) "重新授权日历权限" else "授予日历权限",
                    onClick = { requestPermission() },
                    enabled = !busy,
                )
                if (permissionRequested) {
                    TextButton(
                        onClick = { startIntent(sync.appSettingsIntent(), "无法打开系统设置") },
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) {
                        Text("打开系统设置授权")
                    }
                }
            } else {
                ReminderChoice(selectedIndex = reminderChoice, onSelect = { reminderChoice = it })
                when {
                    loadingCalendars -> LinkText("正在读取本机日历…")

                    calendars.isEmpty() -> {
                        LinkText("本机没有可写日历，请先在系统日历里登录账号")
                        TextButton(
                            onClick = { scope.launch { reloadCalendars() } },
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) {
                            Text("重新检查")
                        }
                    }

                    else -> calendars.forEach { calendar ->
                        CalendarChoiceRow(
                            calendar = calendar,
                            selected = calendar.id == selectedCalendarId,
                            onClick = { selectedCalendarId = calendar.id },
                        )
                    }
                }
                if (calendars.isNotEmpty()) {
                    CtToolPrimaryButton(
                        text = if (busy) "正在写入…" else "写入系统日历（$plannedCount 个日程）",
                        onClick = { syncToCalendar() },
                        enabled = !busy && plannedCount > 0 && selectedCalendarId != null,
                    )
                }
                lastRecord?.let { record ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LinkText(
                            text = "已写入 ${record.eventCount} 个日程到「${record.calendarName}」",
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { clearCalendar() },
                            modifier = Modifier.heightIn(min = 44.dp),
                            enabled = !busy,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("清除")
                        }
                    }
                }
            }

            // ---- 文件导出 -----------------------------------------------------
            LinkSectionTitle(text = "日历文件")
            CtToolSegmented(
                options = listOf("本学期", "当前周"),
                selectedIndex = icsWeekScope,
                onSelect = { icsWeekScope = it },
            )
            LinkFileRow(
                icon = Icons.Rounded.CalendarMonth,
                title = ".ics 日历文件",
                subtitle = if (icsWeekScope == 1) {
                    "系统日历导入 · 第 $currentWeek 周 · 提前 $reminderMinutes 分钟提醒"
                } else {
                    "系统日历导入 · 整学期 · 提前 $reminderMinutes 分钟提醒"
                },
                actions = {
                    TextButton(
                        onClick = { exportIcs(open = true) },
                        modifier = Modifier.heightIn(min = 44.dp),
                        enabled = !busy,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("打开")
                    }
                    TextButton(
                        onClick = { exportIcs(open = false) },
                        modifier = Modifier.heightIn(min = 44.dp),
                        enabled = !busy,
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("分享")
                    }
                    TextButton(
                        onClick = { downloadIcs() },
                        modifier = Modifier.heightIn(min = 44.dp),
                        enabled = !busy,
                    ) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("下载")
                    }
                },
            )
            LinkFileRow(
                icon = Icons.Rounded.PhoneAndroid,
                title = "WakeUp 备份（.wakeup_schedule）",
                subtitle = "WakeUp：备份与恢复 → 从文件导入",
                actions = {
                    TextButton(
                        onClick = { shareWakeUp() },
                        modifier = Modifier.heightIn(min = 44.dp),
                        enabled = !busy,
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("分享")
                    }
                    TextButton(
                        onClick = { downloadWakeUp() },
                        modifier = Modifier.heightIn(min = 44.dp),
                        enabled = !busy,
                    ) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("下载")
                    }
                },
            )
            LinkText("时区 Asia/Shanghai · 由本机课表快照生成，不联网")
            Spacer(modifier = Modifier.height(CtSpace.xl))
        }
    }
}

// =============================================================================
// 小组件（文件内私有）
// =============================================================================

@Composable
private fun LinkSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun LinkText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun ReminderChoice(selectedIndex: Int, onSelect: (Int) -> Unit) {
    CtToolSegmented(
        options = TimetableLink.REMINDER_MINUTES_OPTIONS.map { "提前 $it 分钟" },
        selectedIndex = selectedIndex,
        onSelect = onSelect,
    )
}

@Composable
private fun CalendarChoiceRow(
    calendar: WritableCalendarInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(CtRadius.cardCompact),
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = calendar.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (calendar.accountName.isNotBlank()) {
                    Text(
                        text = calendar.accountName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            content.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkFileRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(CtRadius.cardCompact))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            actions()
        }
    }
}
