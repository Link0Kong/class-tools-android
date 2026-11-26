package com.furina.classtools.data.cloudspace

import com.furina.classtools.BuildConfig

/**
 * 临时云空间（与网页/小程序同一 ECS drop 服务，契约 `web/drop/CONTRACT.md` v2.2）的数据模型。
 *
 * 语义（三端一致）：
 * - 同账号（同班级 token）自动共享同一云空间，不需要房间码/链接/配对；
 * - 每个文件自「上传完成」起保留 72 小时（3 天），逐文件过期；会话空闲 72h 且无未过期文件时回收；
 * - 上传任意大小：8MiB 原始字节分片（init → chunk × N → finish）。
 *
 * 红线：本包只处理业务数据；班级 token 只在请求头 `X-Device-Id` 中出现，**不落盘、不打日志**。
 */
object CloudSpaceDefaults {

    /**
     * drop 服务地址（2026-11-18 按 buildType 注入，见 deploy/migration/CLIENTS.md）：
     * - debug：http://121.196.47.2/drop（本机调试入口，debug 的 network_security_config 放行明文）；
     * - release：https://tool.furina.fun/drop（2026-11-23 备案已通过，正式域名全 https；不含明文 IP）。
     */
    val BASE_URL: String = BuildConfig.CLOUDSPACE_BASE_URL

    /** 与服务端/网页/小程序一致的固定分片：8 MiB。 */
    const val CHUNK_SIZE: Int = 8 * 1024 * 1024

    /** 文件 TTL：自上传完成起 72 小时。 */
    const val FILE_TTL_MS: Long = 72L * 60 * 60 * 1000

    /** 服务端单文件分片数上限（8MiB × 65536 ≈ 512GiB；仅用于本地超限提示）。 */
    const val MAX_UPLOAD_CHUNKS: Int = 65536

    /** 服务端单会话文件数上限（仅计未过期文件）。 */
    const val MAX_FILES: Int = 300

    /** 列表静默轮询间隔（与网页/小程序一致：5s）。 */
    const val POLL_INTERVAL_MS: Long = 5_000L

    /** 下载签名 URL 有效期（服务端 30 分钟；仅文案提示，不参与校验）。 */
    const val DOWNLOAD_URL_TTL_MINUTES: Int = 30
}

/** 云空间会话（`/mine`、`/open`、`/state` 的 session 节点；`code` 为历史兼容字段，UI 不展示）。 */
data class CloudSpaceSession(
    val id: String,
    val code: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
)

/**
 * 一个云空间文件（来自 `/state` 的 `file` 事件）。
 *
 * [downloadUrl] 由服务端**每次读取重新签发**（30 分钟有效），不要长期缓存：
 * 下载前先刷新一次列表，遇到 403「下载链接已过期」再刷新重试。
 */
data class CloudSpaceFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String = "",
    val downloadUrl: String = "",
    val expired: Boolean = false,
    /** 上传完成时刻（文件 TTL 起算点；毫秒）。 */
    val uploadedAt: Long = 0L,
    val conversion: DropConversion? = null,
) {
    val canConvert: Boolean get() = !expired && sizeBytes in 1..(64L * 1024 * 1024) &&
        name.substringAfterLast('.', "").lowercase() in setOf("ppt", "pptx")
    val sizeText: String get() = CloudSpaceLogic.fmtSize(sizeBytes)
    val timeText: String get() = CloudSpaceLogic.fmtTime(uploadedAt)
    fun ttlText(now: Long = System.currentTimeMillis()): String =
        CloudSpaceLogic.ttlText(uploadedAt, now)
}

/** 列表页渲染用的只读快照（同一时刻的文件集合 + 统计）。 */
data class CloudSpaceSnapshot(
    val session: CloudSpaceSession? = null,
    val files: List<CloudSpaceFile> = emptyList(),
    /** 已过期（上传超过 72h，服务端已删）的文件事件数；只用于提示。 */
    val expiredCount: Int = 0,
    /** 已合并的最大事件 seq（增量轮询用）。 */
    val cursor: Long = 0L,
    val pptConversion: Boolean = false,
) {
    val connected: Boolean get() = session != null
    val count: Int get() = files.size
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
    val totalBytesText: String get() = CloudSpaceLogic.fmtSize(totalBytes)
}

/** `/state` 解析后的事件（本页只关心 file / filedel；text / cmd 直接忽略）。 */
sealed interface CloudSpaceEvent {
    val seq: Long

    data class FileAdded(val file: CloudSpaceFile, override val seq: Long) : CloudSpaceEvent

    data class FileDeleted(val fileId: String, override val seq: Long) : CloudSpaceEvent
}

/** `/state` 一次响应（events 尚未合并进列表）。
 *
 *  v2.5：`files` = 服务端返回的**当前有效文件清单**（未过期全量，每次重签下载 URL）；
 *  事件表只保留最近 500 条、且「since 之后没有新事件」不等于没有文件，
 *  文件列表必须以 `files` 为准（`null` 表示旧服务端没有该字段，回退按事件合并）。
 */
data class CloudSpaceState(
    val session: CloudSpaceSession?,
    val events: List<CloudSpaceEvent>,
    val cursor: Long,
    val files: List<CloudSpaceFile>? = null,
    val expiredCount: Int = 0,
    val pptConversion: Boolean = false,
)

/** `/upload/init` 响应。 */
data class CloudSpaceUploadInit(
    val uploadId: String,
    val chunkSize: Int,
)

/** 上传进度回调（只在内存中传递，不落盘）。 */
data class CloudSpaceUploadProgress(
    val state: CloudSpaceUploadState,
    val sentBytes: Long,
    val totalBytes: Long,
    val message: String = "",
)

enum class CloudSpaceUploadState { WAITING, UPLOADING, DONE, FAILED, CANCELED }

/** 页面「上传进度」卡的一行（本机内存状态，不进云端同步）。 */
data class CloudSpaceUploadTask(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val sentBytes: Long = 0L,
    val state: CloudSpaceUploadState = CloudSpaceUploadState.WAITING,
    val message: String = "",
) {
    val percent: Int
        get() = if (state == CloudSpaceUploadState.DONE) {
            100
        } else {
            CloudSpaceLogic.uploadPercent(sentBytes, sizeBytes)
        }

    val sizeText: String get() = CloudSpaceLogic.fmtSize(sizeBytes)

    val stateText: String
        get() = when (state) {
            CloudSpaceUploadState.WAITING -> message.ifBlank { "准备上传…" }
            CloudSpaceUploadState.UPLOADING -> message.ifBlank { "上传中…" }
            CloudSpaceUploadState.DONE -> message.ifBlank { "已上传" }
            CloudSpaceUploadState.FAILED -> message.ifBlank { "上传失败" }
            CloudSpaceUploadState.CANCELED -> message.ifBlank { "已取消" }
        }
}


data class DropConversion(val status: String, val progress: Int, val error: String, val attempt: Int) {
    val label: String get() = when (status) {
        "queued" -> "等待转换"
        "processing" -> "转换中 $progress%"
        "ready" -> "查看转换结果"
        else -> if (error.isBlank()) "转换失败，重试" else "转换失败，重试 · $error"
    }
}

data class DropAsset(val key: String, val name: String, val mime: String, val url: String)
