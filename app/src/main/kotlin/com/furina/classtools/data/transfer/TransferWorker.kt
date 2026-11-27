package com.furina.classtools.data.transfer

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.furina.classtools.BuildConfig
import com.furina.classtools.ClassToolsApp
import com.furina.classtools.data.cloudspace.*
import com.furina.classtools.data.network.ApiError
import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.ppt.*
import com.furina.classtools.data.reminder.reminderOwner
import com.furina.classtools.data.reminder.reminderSession
import kotlinx.coroutines.CancellationException

class TransferWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val activities get() = (applicationContext as ClassToolsApp).container.transferActivities

    override suspend fun doWork(): Result {
        val owner = inputData.getString("owner") ?: return Result.success()
        val kind = inputData.getString("kind") ?: return Result.success()
        val id = inputData.getString("id") ?: return Result.success()
        val task = activities.find(owner, kind, id) ?: return Result.success()
        if (task.generation != inputData.getString("version")) return Result.success()
        val session = reminderSession(applicationContext)
        if (session == null || session.reminderOwner != owner || session.debug) {
            activities.discard(task)
            return Result.success()
        }
        val cleanup = inputData.getBoolean("cleanup", false)
        val current = {
            activities.find(owner, kind, id)?.let { it.generation == task.generation &&
                (it.active || cleanup && it.stage == TransferStage.CANCELED) } == true
        }
        val api = ApiClient(BuildConfig.REST_BASE_URL, BuildConfig.PUBLISHABLE_KEY,
            tokenProvider = { session.token.takeIf { current() } })
        val decks = DefaultPptRepository(api, currentSession = { session.takeIf { current() } })
        if (cleanup) {
            if (task.stage == TransferStage.CANCELED) {
                val deleted = decks.softDelete(task.id)
                if (deleted is ApiResult.Failure && runAttemptCount < 4) return Result.retry()
            }
            return Result.success()
        }
        if (!task.active) return Result.success()
        val age = System.currentTimeMillis() - task.createdAt
        if (age >= (if (task.stage in TransferActivities.uploadStages) 24 else 8) * 3_600_000L) {
            activities.finish(task, "状态跟踪超时，请在详情中刷新确认", TransferStage.UNCONFIRMED)
            return Result.success()
        }
        val drop = CloudSpaceRepository(CloudSpaceApi(baseUrl = CloudSpaceDefaults.BASE_URL,
            tokenProvider = { session.token }, isCurrent = current))
        val execution = TransferExecution(applicationContext, activities)
        try {
            if (kind == "file") execution.runFile(task, drop) else execution.runDeck(task, decks)
        } catch (c: CancellationException) {
            throw c
        } catch (_: SecurityException) {
            activities.finish(task, "文件读取权限已失效，请重新选择文件")
        } catch (_: Exception) {
            if (runAttemptCount < 4) return Result.retry()
            activities.finish(task, "暂时无法确认任务状态，请打开详情刷新", TransferStage.UNCONFIRMED)
        }
        return Result.success()
    }


}

internal class TransferExecution(private val applicationContext: Context, private val activities: TransferActivities) {

    private fun later(task: TransferActivity, offline: Boolean = false) {
        val next = activities.update(task) { it.copy(polls = (it.polls + 1).coerceAtMost(1000),
            error = if (offline) "暂未确认，稍后重试" else "") } ?: return
        val delay = when {
            offline -> (15_000L shl next.polls.coerceAtMost(5)).coerceAtMost(300_000L)
            next.polls <= 10 -> 10_000L
            next.polls <= 40 -> 30_000L
            else -> 120_000L
        }
        activities.schedule(next, delay, append = true)
    }

    private fun conversion(task: TransferActivity, status: String, percent: Int?, error: String, attempt: Int) {
        val next = activities.update(task) { it.conversion(status, percent, error, attempt) } ?: return
        if (next.active) later(next)
    }

    suspend fun runFile(task: TransferActivity, drop: CloudSpaceRepository) {
        if (task.stage in TransferActivities.uploadStages) {
            val parsed = Uri.parse(task.uri)
            val source = CloudSpaceUriSource.open(applicationContext, parsed)
            if (source == null || source.size != task.size || source.name != task.sourceName) {
                activities.finish(task, "源文件已变更或无法读取，请重新选择文件")
                return
            }
            val resume = task.uploadId.takeIf { it.isNotBlank() }?.let { UploadCheckpoint(task.uploadSession, it, task.nextChunk) }
            val result = drop.upload(source, resume, checkpoint = { checkpoint ->
                activities.update(task) { it.copy(uploadId = checkpoint.uploadId, uploadSession = checkpoint.sessionId,
                    nextChunk = checkpoint.nextChunk) } ?: throw CancellationException("inactive upload")
            }) { progress ->
                when (progress.state) {
                    CloudSpaceUploadState.UPLOADING -> activities.update(task) {
                        val percent = CloudSpaceLogic.uploadPercent(progress.sentBytes, progress.totalBytes)
                        it.copy(stage = if (progress.message.isNotEmpty()) TransferStage.FINALIZING else TransferStage.UPLOADING,
                            percent = maxOf(it.percent ?: 0, percent), error = "", sentBytes = maxOf(it.sentBytes, progress.sentBytes))
                    }
                    else -> Unit
                }
            }
            when (result) {
                is CloudSpaceResult.Success -> activities.update(task) { it.copy(stage = TransferStage.UPLOADED,
                    remoteId = result.value.id, percent = null, error = "") }
                is CloudSpaceResult.Failure -> {
                    val state = activities.find(task.owner, task.kind, task.id) ?: return
                    // finish may have committed before the response was lost. A new upload would duplicate the file.
                    val uncertain = state.stage == TransferStage.FINALIZING &&
                        (result.error.retryable || result.error is CloudSpaceError.Parse || (result.error as? CloudSpaceError.Http)?.status == 404)
                    val transient = result.error.retryable
                    if (!uncertain && transient) {
                        later(task, offline = true)
                        return
                    }
                    activities.finish(task, if (uncertain) "上传结果待确认，请查看文件列表" else result.error.displayMessage,
                        if (uncertain) TransferStage.UNCONFIRMED else TransferStage.FAILED)
                }
            }
            return
        }

        val list = drop.existingFiles()
        if (list is CloudSpaceResult.Failure) {
            if (list.error.retryable) later(task, offline = true)
            else activities.finish(task, list.error.displayMessage, if ((list.error as? CloudSpaceError.Http)?.status == 404)
                TransferStage.REMOVED else TransferStage.FAILED)
            return
        }
        val file = (list as CloudSpaceResult.Success).value.files.firstOrNull { it.id == task.remoteId && !it.expired }
        if (file == null) {
            activities.finish(task, "", TransferStage.REMOVED)
            return
        }
        val seen = file.conversion
        if (task.stage == TransferStage.SUBMITTING && (seen == null || seen.status == "failed" && seen.attempt == task.attempt)) {
            when (val result = drop.submitConversion(file)) {
                is CloudSpaceResult.Failure -> {
                    if (result.error.retryable) later(task, offline = true) else activities.finish(task, result.error.displayMessage)
                }
                is CloudSpaceResult.Success -> {
                    val accepted = result.value.files.firstOrNull { it.id == task.remoteId }?.conversion
                    if (accepted == null) later(task, offline = true)
                    else conversion(task, accepted.status, accepted.progress, accepted.error, accepted.attempt)
                }
            }
        } else if (seen == null) later(task, offline = true)
        else conversion(task, seen.status, seen.progress, seen.error, seen.attempt)
    }

    suspend fun runDeck(task: TransferActivity, decks: DefaultPptRepository) {
        val status = decks.transferStatus(task.id)
        if (status is ApiResult.Failure) {
            if (status.error is ApiError.Network || (status.error as? ApiError.Http)?.status in transientHttpCodes)
                later(task, offline = true)
            else activities.finish(task, status.error.displayMessage)
            return
        }
        val deck = (status as ApiResult.Success).value
        if (task.stage == TransferStage.SUBMITTING && deck != null && deck.status == task.retryStatus &&
            (task.retryRevision == 0L || deck.updatedAt == task.retryRevision)) {
            when (val result = decks.retryConversion(task.id, deck.status, task.retryRevision)) {
                is ApiResult.Failure -> activities.finish(task, result.error.displayMessage)
                is ApiResult.Success -> {
                    val next = activities.update(task) { it.copy(stage = TransferStage.QUEUED, error = "") }
                    next?.let { later(it) }
                }
            }
            return
        }
        if (deck != null && deck.status != PptDeck.STATUS_UPLOADING) {
            if (task.replaceDeck.isNotBlank() && deck.status != PptDeck.STATUS_FAILED) decks.softDelete(task.replaceDeck)
            // The class list defaults a missing percentage to zero. An absent measurement is displayed as a stage.
            conversion(task, deck.status, deck.progress.takeIf { it > 0 }, deck.error, task.attempt)
            return
        }
        if (task.stage !in TransferActivities.uploadStages || (deck == null && task.stage != TransferStage.CREATING)) {
            activities.finish(task, "", TransferStage.REMOVED)
            return
        }
        val source = PptUriSource.open(applicationContext, Uri.parse(task.uri))
        if (source == null || source.size != task.size || source.name != task.sourceName) {
            activities.finish(task, "源文件已变更或无法读取，请重新选择文件")
            return
        }
        when (val result = decks.upload(PptUploadInput(task.title, source, task.id, resumeExisting = deck != null) { progress ->
            when (progress.phase) {
                PptUploadPhase.UPLOADING -> activities.update(task) {
                    it.copy(stage = TransferStage.UPLOADING, percent = maxOf(it.percent ?: 0, progress.percent), error = "")
                }
                PptUploadPhase.SUBMITTING -> activities.update(task) { it.copy(stage = TransferStage.FINALIZING, percent = null) }
                else -> Unit
            }
        })) {
            is PptUploadResult.Failure -> if (result.retryable) later(task, offline = true) else activities.finish(task, result.message)
            is PptUploadResult.Success -> {
                if (task.replaceDeck.isNotBlank()) decks.softDelete(task.replaceDeck)
                val queued = activities.update(task) { it.copy(stage = TransferStage.QUEUED, replaceDeck = "", percent = null) }
                queued?.let { later(it) }
            }
        }
    }
}


private val transientHttpCodes = setOf(408, 425, 429, 500, 502, 503, 504)
private val CloudSpaceError.retryable: Boolean
    get() = this is CloudSpaceError.Network || this is CloudSpaceError.Http && status in transientHttpCodes
