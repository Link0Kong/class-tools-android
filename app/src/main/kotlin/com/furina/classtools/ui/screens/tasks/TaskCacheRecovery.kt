package com.furina.classtools.ui.screens.tasks

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.furina.classtools.data.tasks.TaskFiles
import com.furina.classtools.data.tasks.TaskRepository
import com.furina.classtools.ui.components.CtToastBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun TaskCacheRecovery(repository: TaskRepository) {
    val scope = rememberCoroutineScope()
    val review by repository.recovery.collectAsState()
    val draft = review ?: return
    val context = LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching {
                val backup = TaskFiles.backup(context, repository.snapshot.value)
                requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(backup.toString()) }
            } }
            CtToastBus.show(if (saved.isSuccess) "草稿备份已保存" else "保存失败，草稿仍在本机")
        }
    }
    AlertDialog(
        onDismissRequest = repository::deferRecovery,
        title = { Text(if (draft.legacy) "核对本机旧任务" else "其他设备已继续编辑") },
        text = { Column {
            Text(if (draft.legacy) "旧缓存缺少上次同步记录。合并会保留云端现值，并加入本机独有的任务和分组。核对期间你新做的编辑会继续同步。"
                else "上次保存的回执未确认。继续会采用当前云端任务，并同步你在那次保存后新做的编辑。可以先备份本机草稿。")
            TextButton(onClick = { export.launch("个人任务草稿.json") }) { Text("导出本机草稿") }
        } },
        confirmButton = { TextButton(onClick = { scope.launch { repository.resolveRecovery() } }) {
            Text(if (draft.legacy) "合并草稿" else "采用云端并继续")
        } },
        dismissButton = { TextButton(onClick = repository::deferRecovery) { Text("稍后核对") } },
    )
}
