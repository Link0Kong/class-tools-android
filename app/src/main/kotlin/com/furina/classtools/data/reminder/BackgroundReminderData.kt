package com.furina.classtools.data.reminder

import android.content.Context
import com.furina.classtools.data.homework.DefaultHomeworkRepository
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.marks.DefaultMarksRepository
import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.notices.DefaultNoticesRepository
import com.furina.classtools.data.notices.NoticesData
import com.furina.classtools.data.notices.NoticesLoadResult
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.sync.FileUserDataLocalStore
import com.furina.classtools.data.timetable.TtCalendar
import com.furina.classtools.data.timetable.deviceTimetable
import com.furina.classtools.data.vote.DefaultVoteRepository
import com.furina.classtools.data.vote.PollSummary
import com.furina.classtools.data.vote.VoteResult
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** A failed read is distinct from a successful empty list, which can establish a new baseline. */
internal data class ReminderFeeds(
    val homework: List<HomeworkRow>? = null,
    val notices: NoticesData? = null,
    val polls: List<PollSummary>? = null,
    val marks: MarksSnapshot? = null,
) {
    val available: Set<String> get() = buildSet {
        if (homework != null && marks != null) add("hw")
        if (notices != null && marks != null) add("nt")
        if (polls != null) add("vote")
    }
}

internal fun ReminderSettings.requestedFeeds(): Set<String> = buildSet {
    if (!enabled) return@buildSet
    if (homeworkNewEnabled || homeworkDueEnabled || digestEnabled) add("hw")
    if (noticeNewEnabled || noticeDeadlineEnabled || noticeSignupEnabled || digestEnabled) add("nt")
    if (voteNewEnabled || voteResultEnabled) add("vote")
}

internal suspend fun loadReminderFeeds(
    api: ApiClient, sid: String, requested: Set<String>, noticeRepository: DefaultNoticesRepository = DefaultNoticesRepository(api),
): ReminderFeeds = coroutineScope {
    val marks = async {
        if ("hw" !in requested && "nt" !in requested) null
        else (DefaultMarksRepository(api, { sid }).refresh() as? ApiResult.Success)?.value
    }
    val homework = async {
        if ("hw" !in requested) null else {
            val result = api.get("homework?select=id,subject,content,due,date,time,ts&order=ts.asc&archived=eq.false")
            (result as? ApiResult.Success)?.let { DefaultHomeworkRepository.parseRows(NetJson, it.value) }
        }
    }
    val notices = async {
        if ("nt" !in requested) null else (noticeRepository.load() as? NoticesLoadResult.Success)?.data
    }
    val polls = async {
        if ("vote" !in requested) null
        else (DefaultVoteRepository(api, { sid }).list() as? VoteResult.Success)?.value?.polls
    }
    ReminderFeeds(homework.await(), notices.await(), polls.await(), marks.await())
}

internal class BackgroundReminderData(
    private val context: Context,
    private val session: Session,
    private val feeds: ReminderFeeds,
) : ReminderDataProvider {
    override val availableFeeds = feeds.available
    override fun canReplay(event: ReminderEvent): Boolean {
        val id = event.key.substringAfter(':')
        return when (event.toolId) {
            "nt" -> "nt" in availableFeeds && (event.kind == ReminderKind.NOTICE_DIGEST ||
                (feeds.marks?.ntDone?.get(id) != true && feeds.notices?.let { notices ->
                    notices.auto.any { it.id == id && !it.hidden } ||
                        notices.manual.any { it.id == id && !it.row.hidden && !it.row.archived }
                } == true))
            "hw" -> "hw" in availableFeeds && (event.kind == ReminderKind.HOMEWORK_DIGEST ||
                (feeds.marks?.hwDone?.get(id) != true && feeds.homework.orEmpty().any { it.id == id }))
            "vote" -> feeds.polls.orEmpty().any { it.id == id }
            else -> true
        }
    }
    override suspend fun homeworkRows() = if (feeds.marks != null) feeds.homework.orEmpty() else emptyList()
    override suspend fun homeworkDoneIds() = feeds.marks?.hwDone?.filterValues { it }?.keys.orEmpty()
    override suspend fun noticesData() = feeds.notices.takeIf { feeds.marks != null }
    override suspend fun handledNoticeIds() = feeds.marks?.ntDone?.filterValues { it }?.keys.orEmpty()
    override suspend fun votePolls() = feeds.polls.orEmpty()
    override suspend fun timetablePlan(nowMillis: Long, zone: ZoneId) =
        deviceTimetable(context, session.sid)?.let { TtCalendar.plan(it, nowMillis, zone) }

    override suspend fun watchedWaterRoom(): WaterWatch? {
        val store = FileUserDataLocalStore(File(context.filesDir, "userdata"), { session.sid })
        val current = store.readCurrent()
        return waterWatchFrom(current.takeIf { it.isNotEmpty() } ?: store.readCache())
    }
}
