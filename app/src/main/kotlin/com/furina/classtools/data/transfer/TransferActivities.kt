package com.furina.classtools.data.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.furina.classtools.data.cloudspace.CloudSpaceFile
import com.furina.classtools.data.notify.LiveUpdateNotifier
import com.furina.classtools.data.ppt.PptDeck
import com.furina.classtools.features.ppt.PptIds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit

class TransferActivities(private val context: Context) {
    private val prefs = context.getSharedPreferences("transfer_activities_v1", Context.MODE_PRIVATE)
    private val codec = Json { ignoreUnknownKeys = true }
    private val records = MutableStateFlow(prefs.all.values.mapNotNull { value ->
        (value as? String)?.let { runCatching { codec.decodeFromString<TransferActivity>(it) }.getOrNull() }
    })
    val tasks = records.asStateFlow()

    private fun key(owner: String, kind: String, id: String) = listOf(owner, kind, id).joinToString("/") { Uri.encode(it) }
    fun find(owner: String, kind: String, id: String): TransferActivity? =
        records.value.firstOrNull { it.owner == owner && it.kind == kind && (it.id == id || it.remoteId == id) }

    private fun workName(task: TransferActivity) = "transfer/" + key(task.owner, task.kind, task.id) + "/" + task.generation

    internal fun schedule(task: TransferActivity, delay: Long = 0, append: Boolean = false) {
        val work = OneTimeWorkRequestBuilder<TransferWorker>()
            .setInputData(workDataOf("owner" to task.owner, "kind" to task.kind, "id" to task.id, "version" to task.generation))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(task),
            if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP, work)
    }

    private fun persist(next: TransferActivity) {
        val previous = find(next.owner, next.kind, next.id)
        val saved = next.copy(updatedAt = System.currentTimeMillis())
        check(prefs.edit().putString(key(saved.owner, saved.kind, saved.id), codec.encodeToString(saved)).commit())
        records.value = records.value.filterNot { it.owner == saved.owner && it.kind == saved.kind && it.id == saved.id } + saved
        if (previous == null || previous.generation != saved.generation || previous.stage != saved.stage || previous.percent != saved.percent ||
            previous.hidden != saved.hidden || previous.error != saved.error)
            LiveUpdateNotifier.renderTransfer(context, saved)
        if (previous != null && previous.uri.isNotBlank() && saved.uri.isBlank()) release(previous.uri)
    }

    private fun release(uri: String) {
        if (records.value.any { it.uri == uri }) return
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    @Synchronized
    fun startUpload(owner: String, kind: String, uri: Uri, name: String, title: String, size: Long, replaceDeck: String = "") {
        if (owner.isBlank() || kind !in setOf("deck", "file")) return
        val now = System.currentTimeMillis()
        val id = if (kind == "deck") PptIds.newDeckId(now) else "upload-" + UUID.randomUUID()
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val task = TransferActivity(owner, kind, id, title, now, UUID.randomUUID().toString(),
            uri = uri.toString(), remoteId = if (kind == "deck") id else "", sourceName = name,
            size = size, replaceDeck = replaceDeck)
        persist(task)
        schedule(task)
    }

    @Synchronized
    fun convert(owner: String, file: CloudSpaceFile) {
        if (owner.isBlank() || !file.canConvert) return
        val previous = find(owner, "file", file.id)
        if (previous?.active == true) return
        val now = System.currentTimeMillis()
        val task = TransferActivity(owner, "file", previous?.id ?: file.id, file.name, now, UUID.randomUUID().toString(),
            remoteId = file.id, stage = TransferStage.SUBMITTING, size = file.sizeBytes, attempt = file.conversion?.attempt ?: 0)
        persist(task)
        schedule(task)
    }

    @Synchronized
    fun retryDeck(owner: String, deck: PptDeck) {
        if (owner.isBlank() || deck.status !in setOf(PptDeck.STATUS_FAILED, PptDeck.STATUS_READY) || find(owner, "deck", deck.id)?.active == true) return
        val task = TransferActivity(owner, "deck", deck.id, deck.title, System.currentTimeMillis(), UUID.randomUUID().toString(),
            remoteId = deck.id, size = deck.sourceSize, stage = TransferStage.SUBMITTING,
            retryStatus = deck.status, retryRevision = deck.updatedAt)
        persist(task)
        schedule(task)
    }

    @Synchronized
    internal fun update(task: TransferActivity, change: (TransferActivity) -> TransferActivity): TransferActivity? {
        val current = find(task.owner, task.kind, task.id) ?: return null
        if (!current.active || current.generation != task.generation) return null
        val changed = change(current)
        val next = if (changed.active && changed.stage in uploadStages) changed else changed.copy(uri = "")
        if (current != next) persist(next)
        return next
    }

    internal fun finish(task: TransferActivity, message: String, stage: TransferStage = TransferStage.FAILED) {
        update(task) { it.copy(stage = stage, error = message.take(200), percent = null) }
    }

    @Synchronized
    fun hide(owner: String, kind: String, id: String, generation: String) {
        find(owner, kind, id)?.takeIf { it.generation == generation && !it.hidden }?.let { persist(it.copy(hidden = true)) }
    }

    @Synchronized
    fun hide(owner: String, kind: String, id: String) {
        find(owner, kind, id)?.let { hide(owner, kind, id, it.generation) }
    }

    @Synchronized
    fun cancelUpload(owner: String, kind: String, id: String) {
        val task = find(owner, kind, id) ?: return
        if (task.stage !in cancellableStages) return
        persist(task.copy(stage = TransferStage.CANCELED, uri = "", percent = null))
        WorkManager.getInstance(context).cancelUniqueWork(workName(task))
        if (kind == "deck") {
            val work = OneTimeWorkRequestBuilder<TransferWorker>().setInputData(workDataOf("owner" to owner,
                "kind" to kind, "id" to id, "version" to task.generation, "cleanup" to true)).build()
            WorkManager.getInstance(context).enqueue(work)
        }
    }

    @Synchronized
    fun removed(owner: String, kind: String, id: String) {
        val task = find(owner, kind, id) ?: return
        persist(task.copy(stage = TransferStage.REMOVED, uri = "", percent = null))
        WorkManager.getInstance(context).cancelUniqueWork(workName(task))
    }

    @Synchronized
    internal fun discard(task: TransferActivity) {
        val current = find(task.owner, task.kind, task.id) ?: return
        if (current.generation != task.generation) return
        check(prefs.edit().remove(key(current.owner, current.kind, current.id)).commit())
        records.value = records.value - current
        LiveUpdateNotifier.clearTransfer(context, current)
        WorkManager.getInstance(context).cancelUniqueWork(workName(current))
        if (current.uri.isNotBlank()) release(current.uri)
    }

    @Synchronized
    fun sessionChanged(owner: String?) {
        val now = System.currentTimeMillis()
        records.value.filter { it.owner != owner || !it.active && now - it.updatedAt >= RETAIN_MS }.forEach(::discard)
        for (task in records.value.filter { it.active }) schedule(task)
        // Pre-upgrade versions used these untagged, shared IDs.
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.cancel(7401)
        manager.cancel(7411)
    }

    companion object {
        val cancellableStages = setOf(TransferStage.CREATING, TransferStage.UPLOADING)
        val uploadStages = cancellableStages + TransferStage.FINALIZING
        internal const val RETAIN_MS = 72 * 60 * 60 * 1000L
    }
}
