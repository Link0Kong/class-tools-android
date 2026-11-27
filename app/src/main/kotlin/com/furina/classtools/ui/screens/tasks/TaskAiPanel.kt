package com.furina.classtools.ui.screens.tasks

import com.furina.classtools.ui.components.ctOutlinedFieldColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.tasks.TASK_AI_INPUT_LIMIT
import com.furina.classtools.data.tasks.TaskAiClient
import com.furina.classtools.data.tasks.TaskAiDraft
import com.furina.classtools.data.tasks.TaskAiResult
import com.furina.classtools.ui.components.light.CtLightAlertDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun TaskAiPanel(
    client: TaskAiClient,
    source: String,
    onSourceChange: (String) -> Unit,
    drafts: List<TaskAiDraft>,
    reachedLimit: Boolean,
    onDrafts: (List<TaskAiDraft>) -> Unit,
    onChoose: (Int) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var replacing by remember { mutableStateOf(false) }
    var request by remember { mutableStateOf<Job?>(null) }
    fun close() { request?.cancel(); onClose() }
    val hasDrafts = drafts.isNotEmpty()
    if (replacing) {
        CtLightAlertDialog(title = "整理新文字？", text = "这会清除本次剩余的草稿。", confirmText = "开始新的整理",
            onConfirm = { replacing = false; onReset() }, onDismissRequest = { replacing = false }, dismissText = "取消")
        return
    }
    CtLightAlertDialog(
        onDismissRequest = ::close,
        title = { Text(if (hasDrafts) "选择任务草稿" else "AI 整理文字") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasDrafts) {
                    if (reachedLimit) Text("本次最多整理 8 项，其他内容可分段整理。")
                    drafts.forEachIndexed { index, draft ->
                        OutlinedButton(onClick = { onChoose(index) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(draft.title, style = MaterialTheme.typography.titleSmall)
                                if (draft.due.isNotEmpty()) Text(listOf(draft.due, draft.dueTime).filter(String::isNotEmpty).joinToString(" "),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else {
                    Text("点击发送后，DeepSeek 将收到本次文字和本地日期，用于整理任务。")
                    OutlinedTextField(source, {
                        if (it.length <= TASK_AI_INPUT_LIMIT) { onSourceChange(it); message = "" }
                        else message = "一次最多 6000 字，请分段粘贴"
                    },
                        modifier = Modifier.fillMaxWidth(), label = { Text("粘贴或输入任务文字") },
                        minLines = 3, maxLines = 6, readOnly = busy,
                        supportingText = { Text("${source.length} / $TASK_AI_INPUT_LIMIT") }, colors = ctOutlinedFieldColors())
                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("正在整理…", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    } else if (message.isNotEmpty()) {
                        Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = ::close) { Text(if (hasDrafts) "收起" else "关闭") } },
        confirmButton = {
            if (hasDrafts) {
                TextButton(onClick = { replacing = true }) { Text("整理新文字") }
            } else {
                TextButton(enabled = source.isNotBlank() && !busy, onClick = {
                    if (busy || source.isBlank()) return@TextButton
                    busy = true
                    message = ""
                    request = scope.launch {
                        when (val result = client.suggest(source)) {
                            is TaskAiResult.Success -> if (result.drafts.isNotEmpty()) onDrafts(result.drafts)
                                else message = "未识别到待办事项，请补充要完成的事后重试"
                            is TaskAiResult.Failure -> message = result.message
                        }
                        busy = false
                    }
                }) { Text("发送给云端 AI") }
            }
        },
    )
}
