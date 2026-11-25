package com.furina.classtools.ui.screens.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.homework.*
import com.furina.classtools.data.marks.MarkKind
import com.furina.classtools.data.marks.MarksRepository
import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.network.ApiResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive

internal data class HomeworkBrief(
    val loading: Boolean = false,
    val failed: Boolean = false,
    val cached: Boolean = false,
    val unfinished: Int = 0,
    val overdue: Int = 0,
    val dueSoon: Int = 0,
    val highlights: List<HomeworkItem> = emptyList(),
    val pending: List<HomeworkRow> = emptyList(),
)

internal fun homeworkBrief(rows: List<HomeworkRow>, marks: MarksSnapshot, now: Long): HomeworkBrief {
    val pending = rows.asSequence().filterNot { it.archived || marks.isDone(MarkKind.HW, it.id) }
        .map { HomeworkItem(it, false, HomeworkDueCalculator.evaluate(it, now), emptyList()) }
        .filterNot { it.due?.prunable == true }.toList()
    val ordered = HomeworkDueCalculator.sort(pending)
    val late = ordered.filter { it.due?.expired == true }
    val upcoming = ordered.filterNot { it.due?.expired == true }
    // Keep the nearest actionable deadline visible even when more than one assignment is overdue.
    val highlights = if (late.isNotEmpty() && upcoming.firstOrNull()?.due != null)
        listOf(late.first(), upcoming.first()) else ordered.take(2)
    return HomeworkBrief(unfinished = ordered.size, overdue = late.size,
        dueSoon = upcoming.count { it.due?.state == HomeworkDueState.HOURS_LEFT }, highlights = highlights, pending = ordered.map { it.row })
}

@Composable
internal fun rememberHomeworkBrief(
    homework: HomeworkRepository, markRepository: MarksRepository, account: String, revision: Int,
): HomeworkBrief {
    var result by remember(account, homework) { mutableStateOf<HomeworkLoadResult?>(null) }
    var marksCached by remember(account) { mutableStateOf(false) }
    val marks by markRepository.marks.collectAsState()
    LaunchedEffect(account, homework, markRepository, revision) {
        val assignments = async { homework.load(force = revision > 0) }
        marksCached = markRepository.refresh(force = revision > 0) is ApiResult.Failure
        result = assignments.await()
    }
    val now by produceState(System.currentTimeMillis(), account) {
        while (isActive) {
            value = System.currentTimeMillis()
            delay(60_000L)
        }
    }
    return remember(result, marks, now, marksCached) {
        when (val feed = result) {
            null -> HomeworkBrief(loading = true)
            is HomeworkLoadResult.Failure -> HomeworkBrief(failed = true)
            is HomeworkLoadResult.Success -> homeworkBrief(feed.rows, marks, now).copy(cached = feed.fromCache || marksCached)
        }
    }
}

@Composable
internal fun ClassHomeworkOverview(brief: HomeworkBrief, onOpen: (String?) -> Unit,
    onRemind: (HomeworkRow) -> Unit, onSettings: () -> Unit, onRetry: () -> Unit, onImport: (List<HomeworkRow>) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("班级作业", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onSettings) { Text("提醒设置") }
                TextButton(onClick = { onOpen(null) }) { Text("全部作业") }
            }
            val summary = when {
                brief.loading -> "正在读取班级作业…"
                brief.failed -> "暂时无法读取班级作业"
                brief.unfinished == 0 -> "当前没有未完成的班级作业"
                else -> listOfNotNull(
                    "${brief.unfinished} 项未完成",
                    "${brief.overdue} 项逾期".takeIf { brief.overdue > 0 },
                    "${brief.dueSoon} 项 24 小时内截止".takeIf { brief.dueSoon > 0 },
                ).joinToString(" · ")
            }
            Text(summary, style = MaterialTheme.typography.bodyMedium, color =
                if (brief.overdue > 0 || brief.dueSoon > 0) colors.error else colors.onSurfaceVariant)
            if (brief.failed) TextButton(onClick = onRetry) { Text("重试") }
            if (brief.pending.isNotEmpty()) TextButton(onClick = { onImport(brief.pending) }) { Text("全部加入个人任务") }
            if (brief.cached) Text("上次同步", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            brief.highlights.forEach { item ->
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { onOpen(item.id) }.padding(vertical = 8.dp)) {
                        val title = item.row.briefTitle()
                        Text(title, style = MaterialTheme.typography.titleSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val deadline = item.due?.let {
                            HomeworkDueCalculator.shortDueText(item.row) + " · " + it.label
                        } ?: item.row.due.ifBlank { "无截止日期" }
                        val subject = item.row.subject.trim().takeIf { it.isNotEmpty() && it != title }
                        Text(listOfNotNull(subject, deadline).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color =
                            if (item.due?.expired == true) colors.error else colors.onSurfaceVariant)
                    }
                    if (item.due != null && !item.due.expired) {
                        TextButton(onClick = { onRemind(item.row) }) { Text("提醒我") }
                    }
                    TextButton(onClick = { onImport(listOf(item.row)) }) { Text("加入任务") }
                }
            }
        }
    }
}
