package com.furina.classtools.data.notify

import android.content.Context
import android.os.SystemClock
import androidx.work.*
import com.furina.classtools.BuildConfig
import com.furina.classtools.data.reminder.reminderOwner
import com.furina.classtools.data.reminder.reminderSession
import com.furina.classtools.data.vote.*
import com.furina.classtools.data.network.ApiClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit

@Serializable
internal data class VoteWatch(val owner: String, val id: String, val title: String, val deadline: Long,
    val localDeadline: Long, val generation: String, val hidden: Boolean = false,
    val elapsedDeadline: Long? = null, val boot: Int? = null, val lastServerTime: Long = 0) {
    val tag get() = ActivityLinks.tag(owner, "vote", id)
}

internal object VoteWatches {
    private const val REFRESH = "ct_followed_votes_refresh"
    private fun boot(context: Context): Int? = runCatching {
        android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT)
    }.getOrNull()

    private fun VoteWatch.withClock(context: Context, detail: PollDetail): VoteWatch {
        val sample = detail.receivedAtElapsedMillis ?: return copy(boot = null, elapsedDeadline = null)
        if (detail.serverNow <= 0L) return copy(boot = null, elapsedDeadline = null)
        return copy(boot = boot(context), elapsedDeadline = sample + detail.poll.deadline - detail.serverNow,
            lastServerTime = detail.serverNow)
    }
    fun prefs(context: Context) = context.getSharedPreferences("ct_followed_votes", Context.MODE_PRIVATE)
    private fun decode(raw: String?) = raw?.let { runCatching { Json.decodeFromString<VoteWatch>(it) }.getOrNull() }
    fun all(context: Context) = prefs(context).all.values.mapNotNull { decode(it as? String) }
    fun get(context: Context, owner: String, id: String) =
        decode(prefs(context).getString(ActivityLinks.tag(owner, "vote", id), null))
    private fun save(context: Context, watch: VoteWatch) =
        prefs(context).edit().putString(watch.tag, Json.encodeToString(watch)).commit()

    @Synchronized
    fun follow(context: Context, owner: String, detail: PollDetail) {
        if (owner.isBlank() || detail.poll.id.isBlank() || detail.closed) return
        val end = detail.localCountdownDeadline(System.currentTimeMillis(), SystemClock.elapsedRealtime())
        if (end <= System.currentTimeMillis()) return
        val watch = VoteWatch(owner, detail.poll.id, detail.poll.title, detail.poll.deadline, end, UUID.randomUUID().toString())
            .withClock(context, detail)
        if (save(context, watch)) {
            scheduleExpiry(context, watch)
            scheduleRefresh(context)
            LiveUpdateNotifier.postVote(context, watch)
        }
    }

    @Synchronized
    fun refresh(context: Context, expected: VoteWatch, detail: PollDetail) {
        val current = get(context, expected.owner, expected.id) ?: return
        if (current.generation != expected.generation || detail.poll.id != current.id ||
            detail.serverNow > 0 && detail.serverNow < current.lastServerTime) return
        val end = detail.localCountdownDeadline(System.currentTimeMillis(), SystemClock.elapsedRealtime())
        if (detail.closed || detail.poll.isClosedStatus || end <= System.currentTimeMillis()) {
            remove(context, current)
            return
        }
        if (current.title == detail.poll.title && current.deadline == detail.poll.deadline &&
            kotlin.math.abs(current.localDeadline - end) < 1000) return
        val next = current.copy(title = detail.poll.title, deadline = detail.poll.deadline, localDeadline = end,
            generation = if (current.deadline == detail.poll.deadline) current.generation else UUID.randomUUID().toString())
        if (save(context, next.withClock(context, detail))) {
            scheduleExpiry(context, next)
            if (!next.hidden) LiveUpdateNotifier.postVote(context, next)
        }
    }

    @Synchronized
    fun hide(context: Context, owner: String, id: String, generation: String) {
        val watch = get(context, owner, id)?.takeIf { it.generation == generation } ?: return
        if (save(context, watch.copy(hidden = true))) LiveUpdateNotifier.cancelVote(context, watch.tag)
    }

    @Synchronized
    fun expire(context: Context, watch: VoteWatch): Boolean {
        val current = get(context, watch.owner, watch.id) ?: return true
        if (current.generation != watch.generation) return true
        if (current.localDeadline > System.currentTimeMillis()) return false
        remove(context, current)
        return true
    }

    @Synchronized
    fun remove(context: Context, watch: VoteWatch) {
        if (get(context, watch.owner, watch.id)?.generation != watch.generation) return
        prefs(context).edit().remove(watch.tag).commit()
        LiveUpdateNotifier.cancelVote(context, watch.tag)
        WorkManager.getInstance(context).cancelUniqueWork(watch.tag)
        if (all(context).isEmpty()) WorkManager.getInstance(context).cancelUniqueWork(REFRESH)
    }

    @Synchronized
    fun restore(context: Context, owner: String?) {
        LiveUpdateNotifier.cancelVote(context, null) // Remove the pre-upgrade singleton.
        val now = System.currentTimeMillis()
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        for (stored in all(context)) {
            val end = if (stored.boot != null && stored.boot == boot(context) && stored.elapsedDeadline != null)
                now + (stored.elapsedDeadline - SystemClock.elapsedRealtime()) else stored.deadline
            val watch = if (kotlin.math.abs(end - stored.localDeadline) < 1000) stored else stored.copy(localDeadline = end)
            if (watch.owner != owner || end <= now) remove(context, watch)
            else {
                if (watch != stored) save(context, watch)
                scheduleExpiry(context, watch)
                scheduleRefresh(context)
                if (!watch.hidden && (watch != stored || manager.activeNotifications.none { it.tag == watch.tag }))
                    LiveUpdateNotifier.postVote(context, watch)
            }
        }
    }

    private fun scheduleExpiry(context: Context, watch: VoteWatch) {
        val request = OneTimeWorkRequestBuilder<VoteWatchWorker>()
            .setInputData(workDataOf("owner" to watch.owner, "id" to watch.id, "generation" to watch.generation))
            .setInitialDelay((watch.localDeadline - System.currentTimeMillis()).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(watch.tag, ExistingWorkPolicy.REPLACE, request)
    }

    private fun scheduleRefresh(context: Context) {
        val request = PeriodicWorkRequestBuilder<VoteWatchWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(REFRESH, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class VoteWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val owner = inputData.getString("owner")
        if (owner != null) {
            val watch = VoteWatches.get(ctx, owner, inputData.getString("id").orEmpty())
            if (watch != null && watch.generation == inputData.getString("generation") && !VoteWatches.expire(ctx, watch))
                return Result.retry()
            return Result.success()
        }
        val session = reminderSession(ctx) ?: return Result.success()
        val api = ApiClient(BuildConfig.REST_BASE_URL, BuildConfig.PUBLISHABLE_KEY, tokenProvider = { session.token })
        val repo = DefaultVoteRepository(api, { session.sid })
        for (watch in VoteWatches.all(ctx).filter { it.owner == session.reminderOwner }) {
            if (VoteWatches.expire(ctx, watch)) continue
            if (!session.debug) {
                val result = repo.detail(watch.id, force = true)
                if (reminderSession(ctx) != session) return Result.success()
                when (result) {
                    is VoteResult.Success -> VoteWatches.refresh(ctx, watch, result.value)
                    is VoteResult.Failure -> if (result.error in setOf(VoteError.NotFound, VoteError.Closed, VoteError.OutOfAudience))
                        VoteWatches.remove(ctx, watch)
                }
            }
        }
        return Result.success()
    }
}
