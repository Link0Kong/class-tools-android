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
import com.furina.classtools.data.homework.DefaultHomeworkRepository
import com.furina.classtools.data.homework.HomeworkLoadResult
import com.furina.classtools.data.marks.DefaultMarksRepository
import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.notices.DefaultNoticesRepository
import com.furina.classtools.data.notices.NoticesLoadResult
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.session.SessionState
import com.furina.classtools.data.session.SessionStore
import com.furina.classtools.ui.screens.home.HomeRotationItem
import com.furina.classtools.ui.screens.home.HomeWorkbenchLogic
import com.furina.classtools.ui.screens.notices.NoticeCardFilter
import com.furina.classtools.ui.screens.notices.buildUiItems
import com.furina.classtools.ui.screens.notices.viewContentOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

internal object WidgetFeedStore {
    private fun prefs(context: Context) = context.getSharedPreferences("desktop_widget_feed", Context.MODE_PRIVATE)

    suspend fun session(context: Context): Session? =
        (SessionStore(context.applicationContext).state.first() as? SessionState.SignedIn)
            ?.session?.takeIf { it.isLikelyValid() }

    fun read(context: Context, sid: String): WidgetFeed {
        val raw = prefs(context).all
        if (raw["owner"] != sid) return WidgetFeed()
        return (raw["feed"] as? String)?.let {
            runCatching { Json.decodeFromString(WidgetFeed.serializer(), it) }.getOrNull()
        } ?: WidgetFeed()
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
        val marks = DefaultMarksRepository(api, { session.sid }).refresh()
        if (marks !is ApiResult.Success) return@coroutineScope retryLater()
        val now = System.currentTimeMillis()
        val previous = WidgetFeedStore.read(context, session.sid)
        val needTasks = WidgetKind.TASKS in kinds || WidgetKind.WORKBENCH in kinds
        val needNotices = WidgetKind.NOTICES in kinds || WidgetKind.WORKBENCH in kinds
        val tasks = async {
            if (!needTasks) return@async null
            val result = DefaultHomeworkRepository(api).load() as? HomeworkLoadResult.Success ?: return@async null
            compact(HomeWorkbenchLogic.homeworkRotation(result.rows, marks.value, now), now)
        }
        val notices = async {
            if (!needNotices) return@async null
            val result = DefaultNoticesRepository(api).load() as? NoticesLoadResult.Success ?: return@async null
            val pending = compact(HomeWorkbenchLogic.noticeRotation(result.data.auto, marks.value, now), now)
            val latest = viewContentOf(buildUiItems(result.data, marks.value, now),
                cardFilter = NoticeCardFilter.LATEST, nowMillis = now).latest
            pending to WidgetList(latest.size, latest.take(6).map {
                WidgetLine(it.title.take(150), listOf(it.cat, it.postedText).filter(String::isNotBlank).joinToString(" · ").take(100))
            }, now)
        }
        val taskList = tasks.await()
        val noticeList = notices.await()
        if (session != WidgetFeedStore.session(context)) return@coroutineScope Result.success()
        if (taskList != null || noticeList != null) {
            val updated = WidgetFeed(taskList ?: previous.tasks, noticeList?.first ?: previous.notices,
                noticeList?.second ?: previous.latestNotices)
            if (!WidgetFeedStore.save(context, session.sid, updated)) {
                return@coroutineScope retryLater()
            }
            DesktopWidgets.refresh(context)
        }
        if ((needTasks && taskList == null) || (needNotices && noticeList == null)) retryLater() else Result.success()
    }

    private fun compact(items: List<HomeRotationItem>, now: Long) = WidgetList(items.size,
        items.take(6).map { WidgetLine(it.title.take(150), it.sub.take(100)) }, now)

    private fun retryLater() = if (runAttemptCount < 3) Result.retry() else Result.failure()
}
