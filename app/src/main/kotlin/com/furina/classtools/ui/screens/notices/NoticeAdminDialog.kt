package com.furina.classtools.ui.screens.notices

import com.furina.classtools.ui.components.ctOutlinedFieldColors
import com.furina.classtools.ui.components.light.CtLightAlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeStatusMachine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal enum class NoticeAdminAction { EDIT, VISIBILITY }

@Composable
internal fun NoticeAdminDialog(
    entry: NoticeEntry,
    action: NoticeAdminAction,
    onDismiss: () -> Unit,
    save: suspend (fields: JsonObject?, hidden: Boolean?) -> String?,
) {
    val group = entry.group
    val fields = remember(entry) { linkedMapOf(
        "title" to ("标题" to group.title),
        "cat" to ("分类" to group.cat),
        "summary" to ("摘要" to group.aiSummary.ifBlank { group.summary }),
        "deadline" to ("截止时间" to group.deadline),
        "eventAt" to ("活动时间" to group.eventAt),
        "timeLabel" to ("时间文案" to group.timeLabel),
    ) }
    val draft = remember(entry) { mutableStateMapOf<String, String>().apply {
        fields.forEach { (key, field) -> put(key, field.second) }
    } }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val edit = action == NoticeAdminAction.EDIT
    val title = if (edit) "编辑通知" else if (entry.hidden) "恢复通知" else "隐藏通知"
    CtLightAlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (edit) {
                    fields.forEach { (key, field) ->
                        OutlinedTextField(
                            value = draft[key].orEmpty(),
                            onValueChange = { draft[key] = it },
                            label = { Text(field.first) },
                            singleLine = key != "summary",
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ctOutlinedFieldColors(),
                        )
                    }
                } else {
                    Text(if (entry.hidden) "恢复后全班可查看《${entry.title}》。"
                        else "全班将看不到《${entry.title}》。可在「已隐藏」中恢复。")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                val changed = draft.filter { (key, value) -> value.trim() != fields[key]?.second }
                val invalidTime = listOf("deadline", "eventAt").firstOrNull { key ->
                    changed[key]?.let { it.isNotBlank() && NoticeStatusMachine.parseTs(it.trim()) == null } == true
                }
                if (edit && invalidTime != null) {
                    error = "${fields[invalidTime]?.first}格式无效，例如 2026-11-25T18:00:00+08:00"
                } else if (edit && changed.isEmpty()) {
                    onDismiss()
                } else {
                    val overrides = if (edit) changed.mapValues { JsonPrimitive(it.value.trim()) }.toMutableMap().apply {
                        get("summary")?.let { put("aiSummary", it) }
                    }.let(::JsonObject) else null
                    saving = true
                    scope.launch {
                        try { error = save(overrides, if (edit) null else !entry.hidden) }
                        finally { saving = false }
                    }
                }
            }) { Text(if (saving) "保存中…" else if (edit) "保存" else title) }
        },
    )
}
