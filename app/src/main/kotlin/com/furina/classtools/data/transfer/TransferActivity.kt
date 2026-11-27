package com.furina.classtools.data.transfer

import kotlinx.serialization.Serializable

/** A local upload keeps its ID when the Drop service assigns a file ID. A deck ID is allocated before upload. */
@Serializable
data class TransferActivity(
    val owner: String,
    val kind: String,
    val id: String,
    val title: String,
    val createdAt: Long,
    val generation: String,
    val updatedAt: Long = createdAt,
    val stage: TransferStage = TransferStage.CREATING,
    val remoteId: String = "",
    val size: Long = 0L,
    val percent: Int? = null,
    val error: String = "",
    val hidden: Boolean = false,
    val attempt: Int = 0,
    val retryStatus: String = "",
    val retryRevision: Long = 0L,
    val uri: String = "",
    val sourceName: String = "",
    val replaceDeck: String = "",
    val uploadId: String = "",
    val uploadSession: String = "",
    val nextChunk: Int = 0,
    val sentBytes: Long = 0L,
    val polls: Int = 0,
) {
    val active get() = stage.active
    val label get() = if (active && error.isNotBlank()) "$stageLabel · $error" else stageLabel
    private val stageLabel get() = when (stage) {
        TransferStage.CREATING -> "准备上传"
        TransferStage.UPLOADING -> percent?.let { "已上传 $it%" } ?: "上传中"
        TransferStage.FINALIZING -> "上传完成，正在确认"
        TransferStage.SUBMITTING -> "正在提交转换"
        TransferStage.QUEUED -> "等待转换"
        TransferStage.PROCESSING -> percent?.let { "转换中 $it%" } ?: "转换中"
        TransferStage.UPLOADED -> "已上传，可下载"
        TransferStage.READY -> "转换完成，可下载"
        TransferStage.FAILED -> error.ifBlank { "任务失败" }
        TransferStage.UNCONFIRMED -> error.ifBlank { "请打开详情确认状态" }
        TransferStage.CANCELED -> "已取消"
        TransferStage.REMOVED -> "文件已删除或过期"
    }
}

@Serializable
enum class TransferStage(val active: Boolean) {
    CREATING(true), UPLOADING(true), FINALIZING(true), SUBMITTING(true), QUEUED(true), PROCESSING(true),
    UPLOADED(false), READY(false), FAILED(false), UNCONFIRMED(false), CANCELED(false), REMOVED(false)
}

internal fun TransferActivity.conversion(status: String, progress: Int?, message: String, serverAttempt: Int): TransferActivity {
    if (serverAttempt < attempt) return this
    val next = when (status) {
        "queued" -> TransferStage.QUEUED
        "processing" -> TransferStage.PROCESSING
        "ready" -> TransferStage.READY
        "failed" -> TransferStage.FAILED
        else -> return this
    }
    if (attempt == serverAttempt && stage == TransferStage.PROCESSING && next == TransferStage.QUEUED) return this
    return copy(stage = next, percent = progress?.coerceIn(0, 100)?.takeIf { next == TransferStage.PROCESSING },
        attempt = serverAttempt, error = message.take(200))
}
