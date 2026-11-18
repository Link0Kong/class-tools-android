package com.furina.classtools.data.ppt

/**
 * PPT 演示数据层模型（契约唯一真源：`web/ppt/CONTRACT.md` §3/§4/§5）。
 *
 * 纯 Kotlin、无 `android.*` 依赖 → 可直接 JVM 单测构造/解析。
 * 容错原则（契约 §5 末条）：**忽略未知字段、缺字段用默认值、非法数值丢弃**，坏数据绝不抛异常白屏。
 */

/** 列表行（§4.4 轻字段；不含 manifest）。 */
data class PptDeck(
    val id: String,
    val title: String = "",
    val ownerSid: String = "",
    val ownerName: String = "",
    val status: String = STATUS_UPLOADING,
    /** 云端转换进度 0..100（客户端上传进度只在本机 UI，不写云端，见 §3.1）。 */
    val progress: Int = 0,
    val error: String = "",
    val slideCount: Int = 0,
    val stepCount: Int = 0,
    val aspect: String = "",
    val coverUrl: String = "",
    val sourceName: String = "",
    val sourceSize: Long = 0L,
    val createdAt: Long = 0L,
    val convertedAt: Long = 0L,
    val client: String = "",
    /** 仅本机上传中的实时进度（0..100；不来自云端，也不回写）。 */
    val localUploadPercent: Int? = null,
    /** v2.0 列表小标签：兼容处理包是否已生成（来自列表查询的 `manifest->files` 轻量 JSON 子路径）。 */
    val hasCompatFiles: Boolean = false,
    /** v2.0 列表小标签：PDF 兼容副本是否存在。 */
    val hasPdfFile: Boolean = false,
) {
    companion object {
        const val STATUS_UPLOADING = "uploading"
        const val STATUS_QUEUED = "queued"
        const val STATUS_PROCESSING = "processing"
        const val STATUS_READY = "ready"
        const val STATUS_FAILED = "failed"
    }
}

/** 媒体类型：`k:'v'` 视频 / `k:'a'` 音频（契约 §5；未知默认视频）。 */
enum class PptMediaKind { VIDEO, AUDIO }

/** 全局媒体表条目（manifest.media[]，按 `s` 引用）。 */
data class PptMediaDef(
    val src: String,
    val kind: PptMediaKind = PptMediaKind.VIDEO,
    val width: Int = 0,
    val height: Int = 0,
    val durationSec: Double = 0.0,
)

/** 「原样播放」高保真视频的一页章节：`index` = slides 下标（0 基），`startSec` = 该页内容开始秒数（单调递增）。 */
data class PptVideoChapter(
    val index: Int,
    val startSec: Double,
)

/**
 * manifest.video（契约 §11.1，可选字段）：
 * PowerPoint 直接导出的高保真视频（含动画/切换/内嵌视频声音）。
 * 缺省 = 只有分步浏览；旧客户端忽略该字段即可。
 */
data class PptVideoInfo(
    val src: String,
    /** 渲染分辨率（供 letterbox 用；0 = 未知，退回 manifest w/h）。 */
    val width: Int = 0,
    val height: Int = 0,
    /** 视频总时长（秒，ffprobe 实测；0 = 未知）。 */
    val durationSec: Double = 0.0,
    /** 按页章节（单调递增；可能缺页，播放器按最近页容错）。 */
    val chapters: List<PptVideoChapter> = emptyList(),
    /** 质量标签（展示用，如 720p）。 */
    val quality: String = "",
)

/**
 * 某一步上的媒体热点（manifest.slides[].steps[].media[]）。
 * 坐标均为相对整页的 0..1 归一化（与图片坐标系同一套）。
 */
data class PptHotspot(
    val src: String,
    val kind: PptMediaKind = PptMediaKind.VIDEO,
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 1f,
    val h: Float = 1f,
)

/** 一个播放步（一张完整画面图 + 该步上的媒体热点）。 */
data class PptStep(
    val img: String,
    val media: List<PptHotspot> = emptyList(),
)

/** 一页（含页内多个 step）。 */
data class PptSlide(
    /** 页切换动效原始值：none|fade|push|wipe|zoom|cover（未知由播放器按 fade 处理）。 */
    val tr: String = "fade",
    val trd: Int = 450,
    val notes: String = "",
    /** 缩略图引用（相对 base 或绝对地址；空则播放器退回第一步 img）。 */
    val thumb: String = "",
    val steps: List<PptStep> = emptyList(),
)

/**
 * manifest.files 中的一个下载文件（契约 §12.2）。
 *
 * @param src 相对 `manifest.base` 的文件名（也允许 http/https 绝对地址）
 * @param size 字节数（0 = 未知）
 * @param sha256 内容哈希（展示/校验用，可为空）
 * @param ext 扩展名（pptx/pdf/ppt…，不含点）
 * @param name 建议下载文件名（UTF-8，可为空 → 客户端按标题+扩展名兜底）
 * @param pages PDF 页数（仅 PDF 有意义）
 */
data class PptFileInfo(
    val src: String,
    val size: Long = 0L,
    val sha256: String = "",
    val ext: String = "",
    val name: String = "",
    val pages: Int = 0,
)

/** manifest.files：兼容版 PPTX / PDF / 原始文件（旧 deck 无该字段 → null）。 */
data class PptFiles(
    val compat: PptFileInfo? = null,
    val pdf: PptFileInfo? = null,
    val source: PptFileInfo? = null,
)

/** manifest.compat：兼容性处理结果与告警（契约 §12.2）。 */
data class PptCompat(
    /** 中文兼容提示（外链媒体未随文件携带、字体未嵌入等），客户端橙色逐条展示。 */
    val warnings: List<String> = emptyList(),
    /** 成功嵌入的字体数（null = 未提供）。 */
    val fontsEmbedded: Int? = null,
    /** 源文件是 .ppt 时记录 "ppt"（已标准化为 pptx），否则 null。 */
    val convertedFrom: String? = null,
)

/**
 * v2.3 §12.8「视频/音频单独下载」条目（来自 manifest.media_files）。
 *
 * @param src 相对 `manifest.base` 的路径（如 `media/xxx_视频1_第7页.mp4`）
 * @param name 建议下载文件名（转换机写；可为空 → 客户端按标题/序号/页号兜底）
 * @param size 字节数（0 = 未知）
 * @param sha256 内容哈希（展示/校验用，可空）
 * @param kind 视频 / 音频
 * @param durationSec 时长（秒，0 = 未知）
 * @param width/height 分辨率（音频为 0）
 * @param slides 该媒体在演示中的页号（1 基，按转换机写；可多页）
 * @param sourceName 原媒体文件名（compat 包内 `ppt/media/xxx.mp4` 名，可空）
 */
data class PptMediaFile(
    val src: String,
    val name: String = "",
    val size: Long = 0L,
    val sha256: String = "",
    val kind: PptMediaKind = PptMediaKind.VIDEO,
    val durationSec: Double = 0.0,
    val width: Int = 0,
    val height: Int = 0,
    val slides: List<Int> = emptyList(),
    val sourceName: String = "",
)

/** 播放清单（v1 分步/视频；v2.0 兼容下载按契约 §12.2 扩展 files/compat）。 */
data class PptManifest(
    val v: Int = 1,
    val base: String = "",
    val src: String = "",
    val width: Int = 1600,
    val height: Int = 900,
    val slides: List<PptSlide> = emptyList(),
    /** 全局媒体表：`s` → 媒体定义。 */
    val media: Map<String, PptMediaDef> = emptyMap(),
    /** 「原样播放」高保真视频（契约 §11.1；null = 仅分步模式）。 */
    val video: PptVideoInfo? = null,
    /** v2.0 下载文件（兼容版/PDF/原始；旧 deck null）。 */
    val files: PptFiles? = null,
    /** v2.0 兼容处理信息与告警。 */
    val compat: PptCompat? = null,
    /** v2.3 §12.8 单独下载的视频/音频（旧 deck 缺失 → 空列表，不显示分组）。 */
    val mediaFiles: List<PptMediaFile> = emptyList(),
) {
    /** 是否可播放（至少一页且每页有 step；解析器已过滤空页）。 */
    val isPlayable: Boolean get() = slides.isNotEmpty()

    /** 每页 step 数（导航用）。 */
    val stepCounts: List<Int> get() = slides.map { it.steps.size }

    /** 总步数。 */
    val totalSteps: Int get() = stepCounts.sum()

    /** 是否有「原样播放」视频模式。 */
    val hasVideo: Boolean get() = video != null && video.src.isNotBlank()

    /** 原样播放视频地址（相对 base 解析；无 video → 空串）。 */
    val videoUrl: String get() = video?.let { resolveImage(it.src) }.orEmpty()

    /** v2.0：兼容版 PPTX 是否存在。 */
    val hasCompatFile: Boolean get() = files?.compat != null

    /** v2.0：PDF 兼容副本是否存在。 */
    val hasPdfFile: Boolean get() = files?.pdf != null

    /** 旧 deck（无 files 字段）判定：需提示「兼容版未生成」。 */
    val isLegacyDeck: Boolean get() = files == null

    /** 是否有可预览内容（旧版分步 slides 或原样视频）→「在线预览」入口。 */
    val hasPreview: Boolean get() = slides.isNotEmpty() || hasVideo

    /** 下载文件的绝对地址（相对 base 解析）。 */
    fun fileUrl(info: PptFileInfo): String = resolveImage(info.src)

    /** v2.3：是否有可单独下载的视频/音频（空/缺失 → 不显示分组）。 */
    val hasMediaFiles: Boolean get() = mediaFiles.isNotEmpty()

    /** 单独下载媒体的绝对地址（相对 base 解析）。 */
    fun mediaUrl(info: PptMediaFile): String = resolveImage(info.src)

    /**
     * 资源地址解析（img/thumb/src/视频/下载文件）：
     * 1) 相对引用按 `manifest.base` 拼绝对地址（[com.furina.classtools.features.ppt.PptUrl]）；
     * 2) OSS 香港桶 `/ppt/` 下媒体再映射到 ECS `/pptfiles` 缓存
     *    （[com.furina.classtools.features.ppt.PptEcsUrl]，2026-11-18 迁移优化；data:/其它域名原样）。
     */
    fun resolveImage(ref: String): String =
        com.furina.classtools.features.ppt.PptEcsUrl.map(
            com.furina.classtools.features.ppt.PptUrl.resolve(base, ref),
        )

    /** 缩略图地址：优先 thumb，缺省退回第一步 img。 */
    fun thumbOf(slide: PptSlide): String {
        val firstImg = slide.steps.firstOrNull()?.img.orEmpty()
        return resolveImage(slide.thumb.ifBlank { firstImg })
    }

    /** 当前步媒体热点的媒体定义（不在全局表时返回 null，播放器按热点自身的 src/kind 兜底）。 */
    fun mediaDefOf(hotspot: PptHotspot): PptMediaDef? = media[hotspot.src]
}

/** 转换机心跳（§3.4：行 `site_assets.id='ppt_agent'`）。 */
data class PptAgentStatus(
    val online: Boolean = false,
    val seenAt: Long = 0L,
    val queued: Int = 0,
    val version: String = "",
    val host: String = "",
) {
    /**
     * 是否在线：`now - seen_at > 180000` 视为离线（契约 §3.4）。
     * seenAt 缺失（<=0）时退回 body.online 标记。
     */
    fun isOnline(now: Long = System.currentTimeMillis(), offlineAfterMs: Long = OFFLINE_AFTER_MS): Boolean {
        if (!online) return false
        if (seenAt <= 0L) return true
        return now - seenAt <= offlineAfterMs
    }

    companion object {
        /** 心跳过期阈值：180 秒。 */
        const val OFFLINE_AFTER_MS: Long = 180_000L
    }
}

/** 下载详情所需数据（§12.4.1：单 deck manifest + 轻量状态字段）。 */
data class PptDeckDetail(
    val title: String = "",
    val manifest: PptManifest = PptManifest(),
    val sourceName: String = "",
    val sourceKey: String = "",
    val aspect: String = "",
    val slideCount: Int = 0,
    val stepCount: Int = 0,
    val convertedAt: Long = 0L,
    /** 第一页缩略图（播放器首屏先铺 cover，主图到位后淡入替换；DESIGN §4.3）。 */
    val coverUrl: String = "",
    /** v2.0 下载详情：deck 状态（ready/processing/…）。 */
    val status: String = "",
    val progress: Int = 0,
    val error: String = "",
    val ownerName: String = "",
    val sourceSize: Long = 0L,
) {
    val isPlayable: Boolean get() = manifest.isPlayable

    /** 原件下载地址（manifest.src 优先；空 = 转换机未保留原件）。 */
    val sourceUrl: String get() = manifest.resolveImage(manifest.src).ifBlank { manifest.src }

    val isReady: Boolean get() = status == PptDeck.STATUS_READY
}

// ---------------------------------------------------------------- 上传相关

/**
 * 分片读取源（Android 端由 `PptUriSource` 用 SAF/FileChannel 实现；单测用内存假实现）。
 *
 * 约定：**不要整文件进内存**，按 [read] 的 offset/length 分段读取。
 */
interface PptByteSource {
    val name: String
    val size: Long

    /** 读取 [offset, offset+length) 原始字节；越界时返回实际可读的部分（上传器会校验长度）。 */
    suspend fun read(offset: Long, length: Int): ByteArray
}

/** 一次上传的输入。 */
data class PptUploadInput(
    val title: String,
    val source: PptByteSource,
)

// ---------------------------------------------------------------- 结果类型

/** 列表加载结果（失败时带回本机上次缓存，供「离线仍可看」）。 */
sealed interface PptListResult {
    data class Success(
        val decks: List<PptDeck>,
        val agent: PptAgentStatus? = null,
    ) : PptListResult

    data class Failure(
        val message: String,
        val cachedDecks: List<PptDeck>? = null,
        val cachedAgent: PptAgentStatus? = null,
        val tokenInvalid: Boolean = false,
    ) : PptListResult
}

/** 播放器清单加载结果。 */
sealed interface PptPlayerResult {
    data class Success(val detail: PptDeckDetail) : PptPlayerResult

    /** 记录存在但还没转换完成（manifest 空/{}）。 */
    data class NotReady(val message: String = "还没转换完成，请稍后再试") : PptPlayerResult

    data class Failure(
        val message: String,
        val tokenInvalid: Boolean = false,
    ) : PptPlayerResult
}

/** v2.0 下载详情加载结果（状态/进度/文件/manifest 一并返回）。 */
sealed interface PptDetailResult {
    data class Success(val detail: PptDeckDetail) : PptDetailResult

    data class Failure(
        val message: String,
        val tokenInvalid: Boolean = false,
    ) : PptDetailResult
}

/** 上传（建行 → 分片 → 提交转换）结果。 */
sealed interface PptUploadResult {
    data class Success(val deckId: String) : PptUploadResult

    data class Failure(
        val message: String,
        /** 已建行时的 deckId（取消/失败时可软删，避免残留 uploading 行）。 */
        val deckId: String? = null,
        val tokenInvalid: Boolean = false,
    ) : PptUploadResult
}
