package com.furina.classtools.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.furina.classtools.BuildConfig
import com.furina.classtools.data.tasks.*
import com.furina.classtools.data.homework.*
import com.furina.classtools.data.marks.MarkKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import com.furina.classtools.data.marks.DefaultMarksRepository
import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.notices.DefaultNoticesRepository
import com.furina.classtools.data.notices.NoticesLoadResult
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.session.SessionState
import com.furina.classtools.data.session.SessionStore
import com.furina.classtools.ui.screens.notices.NoticeUiItem
import com.furina.classtools.ui.screens.home.HomeWorkbenchLogic
import com.furina.classtools.ui.screens.notices.NoticeCardFilter
import com.furina.classtools.ui.screens.notices.buildUiItems
import com.furina.classtools.ui.screens.notices.noticePostedDescComparator
import com.furina.classtools.ui.screens.notices.viewContentOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private fun taskList(doc: JsonObject, version: Long, now: Long): WidgetList {
    val today = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    val items = doc.pendingTasks().sortedWith(taskPriorityOrder(today))
    val rows = items.map { task ->
        val steps = task.objects("steps")
        val detail = listOfNotNull(
            taskDueLabel(task, today).takeIf(String::isNotBlank),
            "重要".takeIf { task.flag("starred") },
            steps.takeIf { it.isNotEmpty() }?.let { "步骤 ${it.count { step -> step.flag("done") }}/${it.size}" },
        ).joinToString(" · ")
        WidgetLine(task.text("title").take(150), detail, priority = taskPriority(task, today), homeworkId = task.text("sourceHwId"), focus = task.text("id"), deadlineAt = taskDeadline(task), starred = task.flag("starred"))
    }
    return WidgetList(items.size, rows.take(8), now, version, doc.importedHomeworkIds(), quadrantLeads(rows, now))
}

internal object WidgetFeedStore {
    private fun prefs(context: Context) = context.getSharedPreferences("desktop_widget_feed", Context.MODE_PRIVATE)

    suspend fun session(context: Context): Session? =
        (SessionStore(context.applicationContext).state.first() as? SessionState.SignedIn)
            ?.session?.takeIf { it.isLikelyValid() }

    fun read(context: Context, sid: String): WidgetFeed {
        val raw = prefs(context).all
        val feed = (raw["feed"] as? String)?.takeIf { raw["owner"] == sid }?.let {
            runCatching { Json.decodeFromString(WidgetFeed.serializer(), it) }.getOrNull()
        } ?: WidgetFeed()
        val tasks = TaskPreferences(context.getSharedPreferences("tasks_" + sid, Context.MODE_PRIVATE))
        val localText = tasks.read("local")
        val local = localText?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val cloud = feed.tasks?.takeIf { it.taskVersion != null }
        if (local == null) return feed.copy(tasks = cloud)
        val version = local.number("updatedAt")
        val list = if (cloud == null || version >= (cloud.taskVersion ?: 0) || tasks.read("base") != localText)
            taskList(local, version, System.currentTimeMillis()) else cloud
        return feed.copy(tasks = list)
    }

    fun save(context: Context, sid: String, feed: WidgetFeed) =
        prefs(context).edit().putString("owner", sid)
            .putString("feed", Json.encodeToString(WidgetFeed.serializer(), feed)).commit()

    fun request(context: Context, sid: String, replace: Boolean = false) {
        if (DesktopWidgets.feedKinds(context).isEmpty()) return
        val work = OneTimeWorkRequestBuilder<WidgetFeedWorker>().setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        ).build()
        WorkManager.getInstance(context).enqueueUniqueWork("desktop_widget_feed_${sid.hashCode()}",
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, work)
    }
}

class WidgetFeedWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = coroutineScope {
        val context = applicationContext
        val kinds = DesktopWidgets.feedKinds(context)
        if (kinds.isEmpty()) return@coroutineScope Result.success()
        val session = WidgetFeedStore.session(context)?.takeUnless { it.debug } ?: return@coroutineScope Result.success()
        // The worker can run without an Activity and while the foreground account changes.
        val api = ApiClient(BuildConfig.REST_BASE_URL, BuildConfig.PUBLISHABLE_KEY, tokenProvider = { session.token })
        val now = System.currentTimeMillis()
        val previous = WidgetFeedStore.read(context, session.sid)
        val needTasks = WidgetKind.TASKS in kinds || WidgetKind.WORKBENCH in kinds
        val needNotices = WidgetKind.NOTICES in kinds || WidgetKind.WORKBENCH in kinds
        val tasks = async {
            if (!needTasks) return@async null
            val rows = (api.getJson("taskdata?id=eq." + android.net.Uri.encode(session.sid) + "&select=blob,ts") as? ApiResult.Success)?.value as? JsonArray ?: return@async null
            val row = rows.firstOrNull() as? JsonObject
            val doc = if (row == null || row.text("blob").isBlank()) emptyTasks else
                runCatching { Json.parseToJsonElement(row.text("blob")).jsonObject }.getOrNull() ?: return@async null
            taskList(doc, row?.number("ts") ?: 0L, now)
        }
        val marks = async { DefaultMarksRepository(api, { session.sid }).refresh() }
        val homework = async {
            if (!needTasks) return@async null
            val snapshot = (marks.await() as? ApiResult.Success)?.value ?: return@async null
            val path = "homework?select=id,subject,content,due,date,time,ts&archived=eq.false&order=ts.asc"
            val response = (api.get(path) as? ApiResult.Success)?.value ?: return@async null
            val rows = DefaultHomeworkRepository.parseRows(com.furina.classtools.data.network.NetJson, response) ?: return@async null
            val pending = rows.filterNot { snapshot.isDone(MarkKind.HW, it.id) }.map { row ->
                HomeworkRow(id = row.id, subject = row.subject.take(80), content = row.briefTitle().take(150),
                    date = row.date, time = row.time, due = row.due.take(60), ts = row.ts)
            }
            WidgetHomework(pending, now)
        }
        val notices = async {
            if (!needNotices) return@async null
            val snapshot = (marks.await() as? ApiResult.Success)?.value ?: return@async null
            val result = DefaultNoticesRepository(api).load() as? NoticesLoadResult.Success ?: return@async null
            val items = buildUiItems(result.data, snapshot, now)
            val latest = items.filterNot { it.hidden }.sortedWith(noticePostedDescComparator)
            val urgent = viewContentOf(items, cardFilter = NoticeCardFilter.URGENT, nowMillis = now).primary
                .mapNotNull { it.auto?.id }.toSet()
            fun rank(item: NoticeUiItem) = when {
                item.done || item.expired -> 8
                item.auto?.id in urgent -> 1
                item.pinned -> 2
                else -> 5
            }
            val pending = HomeWorkbenchLogic.noticeRotation(result.data.auto, snapshot, now)
            val pendingRows = pending.map { WidgetLine(it.title.take(150), it.sub.take(100),
                priority = if (it.id in urgent) 1 else 5, focus = it.id) }.sortedBy { it.priority }
            val rows = latest.take(8).map {
                val detail = it.postedText.ifBlank { "发布时间未知" }
                WidgetLine(it.title.take(150), detail, priority = rank(it), focus = it.occurrenceKey ?: it.auto?.id ?: it.manual?.id.orEmpty())
            }
            WidgetList(pending.size, pendingRows.take(8), now) to WidgetList(latest.size, rows, now)
        }
        val taskList = tasks.await()
        val noticeList = notices.await()
        val assignments = homework.await()
        if (session != WidgetFeedStore.session(context)) return@coroutineScope Result.success()
        if (taskList != null || noticeList != null || assignments != null) {
            val updated = WidgetFeed(taskList ?: previous.tasks, noticeList?.first ?: previous.notices,
                noticeList?.second ?: previous.latestNotices, assignments ?: previous.homework)
            if (!WidgetFeedStore.save(context, session.sid, updated)) {
                return@coroutineScope retryLater()
            }
            DesktopWidgets.refresh(context)
        }
        if ((needTasks && (taskList == null || assignments == null)) || (needNotices && noticeList == null)) retryLater() else Result.success()
    }

    private fun retryLater() = if (runAttemptCount < 3) Result.retry() else Result.failure()
}
