package com.furina.classtools.ui.preview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import com.furina.classtools.data.ppt.PptDeck
import com.furina.classtools.data.ppt.PptCompat
import com.furina.classtools.data.ppt.PptDeckDetail
import com.furina.classtools.data.ppt.PptFileInfo
import com.furina.classtools.data.ppt.PptFiles
import com.furina.classtools.data.ppt.PptHotspot
import com.furina.classtools.data.ppt.PptImageLoader
import com.furina.classtools.data.ppt.PptManifest
import com.furina.classtools.data.ppt.PptMediaDef
import com.furina.classtools.data.ppt.PptMediaFile
import com.furina.classtools.data.ppt.PptMediaKind
import com.furina.classtools.data.ppt.PptSlide
import com.furina.classtools.data.ppt.PptVideoChapter
import com.furina.classtools.data.ppt.PptVideoInfo
import com.furina.classtools.data.ppt.PptStep
import com.furina.classtools.features.ppt.PptPlayMode
import com.furina.classtools.features.ppt.PptResumeRecord
import com.furina.classtools.ui.screens.ppt.PptDownloadDetailContent
import com.furina.classtools.ui.screens.ppt.PptListContent
import com.furina.classtools.ui.screens.ppt.PptPlayerContent
import com.furina.classtools.ui.screens.ppt.PptUploadBar
import com.furina.classtools.ui.theme.ClassToolsTheme

// =============================================================================
// 演示（PPT）预览（供 `scripts/render_previews.ps1` 无设备验收）
//
// 全部走注入数据 + 合成位图（无网络、无 VideoView），保证离线可渲染。
// =============================================================================

private const val PREVIEW_NOW = 1_791_590_400_000L

/**
 * 预览用图片加载器：按 URL 生成合成「幻灯片」位图。
 * 只画几何块（不依赖字体），保证 preview render 稳定出图。
 */
private object PptPreviewImageLoader : PptImageLoader {
    private val cache = HashMap<String, ImageBitmap>()

    override suspend fun load(url: String, targetWidth: Int): ImageBitmap? {
        if (url.isBlank()) return null
        return cache.getOrPut("$targetWidth|$url") { syntheticSlide(url.hashCode()) }
    }

    override fun clearMemory() {
        cache.clear()
    }

    private fun syntheticSlide(seed: Int): ImageBitmap {
        val width = 640
        val height = 360
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = intArrayOf(
            AndroidColor.rgb(24, 44, 52),
            AndroidColor.rgb(35, 58, 66),
            AndroidColor.rgb(22, 38, 46),
        )
        val paint = Paint()
        paint.color = palette[Math.floorMod(seed, palette.size)]
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // 标题条
        paint.color = AndroidColor.argb(70, 255, 255, 255)
        canvas.drawRect(40f, 42f, 430f, 64f, paint)
        // 正文行
        paint.color = AndroidColor.argb(38, 255, 255, 255)
        canvas.drawRect(40f, 92f, 600f, 110f, paint)
        canvas.drawRect(40f, 124f, 560f, 142f, paint)
        canvas.drawRect(40f, 156f, 580f, 174f, paint)
        // 右下角品牌色块
        paint.color = AndroidColor.argb(120, 78, 144, 130)
        canvas.drawRect(500f, 280f, 600f, 320f, paint)
        return bitmap.asImageBitmap()
    }
}

private fun deck(
    id: String,
    title: String,
    status: String,
    ownerSid: String = "3260707024",
    ownerName: String = "陈子麒",
    slideCount: Int = 12,
    stepCount: Int = 37,
    progress: Int = 0,
    error: String = "",
    createdAt: Long = PREVIEW_NOW,
): PptDeck = PptDeck(
    id = id,
    title = title,
    ownerSid = ownerSid,
    ownerName = ownerName,
    status = status,
    progress = progress,
    error = error,
    slideCount = slideCount,
    stepCount = stepCount,
    coverUrl = "preview://cover/$id",
    sourceName = "$title.pptx",
    sourceSize = 12L * 1024 * 1024,
    createdAt = createdAt,
)

private val previewDecks = listOf(
    deck("dmg2k4x9ab12c", "班会：期中总结与学习计划", "ready", createdAt = PREVIEW_NOW).copy(hasCompatFiles = true, hasPdfFile = true),
    deck(
        "dqueue000001",
        "秋季运动会分工安排",
        "queued",
        slideCount = 0,
        stepCount = 0,
        createdAt = PREVIEW_NOW - 90_000,
    ),
    deck(
        "dproc0000001",
        "英语单元展示",
        "processing",
        slideCount = 0,
        stepCount = 0,
        progress = 61,
        ownerName = "李同学",
        ownerSid = "3260707001",
        createdAt = PREVIEW_NOW - 200_000,
    ),
    deck(
        "dfail0000001",
        "实验报告模板",
        "failed",
        slideCount = 0,
        stepCount = 0,
        error = "转换超时：PowerPoint 渲染超过 15 分钟",
        ownerName = "王同学",
        ownerSid = "3260707002",
        createdAt = PREVIEW_NOW - 300_000,
    ),
)

private val previewManifest = PptManifest(
    v = 1,
    base = "https://preview.invalid/ppt/a/dmg2k4x9ab12c/b_preview/",
    src = "https://preview.invalid/ppt/src/dmg2k4x9ab12c/source.pptx",
    width = 1600,
    height = 900,
    slides = listOf(
        PptSlide(
            tr = "fade",
            trd = 450,
            notes = "开场：说明本次班会主题与流程。先回顾期中成绩，再讲下阶段学习计划。",
            thumb = "t1.webp",
            steps = listOf(
                PptStep("s1-1.webp"),
                PptStep(
                    "s1-2.webp",
                    media = listOf(
                        PptHotspot(
                            src = "m0.mp4",
                            kind = PptMediaKind.VIDEO,
                            x = 0.30f,
                            y = 0.28f,
                            w = 0.40f,
                            h = 0.40f,
                        ),
                    ),
                ),
            ),
        ),
        PptSlide(
            tr = "push",
            trd = 360,
            notes = "",
            thumb = "t2.webp",
            steps = listOf(PptStep("s2-1.webp")),
        ),
        PptSlide(
            tr = "fade",
            trd = 450,
            notes = "总结与答疑：下周三前提交学习计划表。",
            thumb = "t3.webp",
            steps = listOf(PptStep("s3-1.webp"), PptStep("s3-2.webp")),
        ),
    ),
    media = mapOf(
        "m0.mp4" to PptMediaDef(src = "m0.mp4", kind = PptMediaKind.VIDEO, width = 1280, height = 720, durationSec = 12.4),
    ),
)

private val previewManifestVideo = previewManifest.copy(
    video = PptVideoInfo(
        src = "deck.mp4",
        width = 1280,
        height = 720,
        durationSec = 342.5,
        chapters = listOf(
            PptVideoChapter(index = 0, startSec = 0.0),
            PptVideoChapter(index = 1, startSec = 8.7),
            PptVideoChapter(index = 2, startSec = 15.2),
        ),
        quality = "720p",
    ),
)

private val previewManifestHotspot = previewManifest.copy(
    slides = previewManifest.slides.mapIndexed { index, slide ->
        if (index != 0) {
            slide
        } else {
            slide.copy(
                steps = listOf(
                    PptStep(
                        "s1-1.webp",
                        media = listOf(
                            PptHotspot(
                                src = "m0.mp4",
                                kind = PptMediaKind.VIDEO,
                                x = 0.30f,
                                y = 0.28f,
                                w = 0.40f,
                                h = 0.40f,
                            ),
                        ),
                    ),
                    slide.steps.getOrNull(1) ?: PptStep("s1-2.webp"),
                ),
            )
        }
    },
)

private fun previewVideoDetail(): PptDeckDetail = playerDetail().copy(manifest = previewManifestVideo)

private fun playerDetail(aspectWidth: Int = 1600, aspectHeight: Int = 900): PptDeckDetail = PptDeckDetail(
    title = "班会：期中总结与学习计划",
    manifest = if (aspectWidth == 1600 && aspectHeight == 900) {
        previewManifest
    } else {
        previewManifest.copy(width = aspectWidth, height = aspectHeight)
    },
    sourceName = "班会：期中总结与学习计划.pptx",
    aspect = if (aspectWidth == 1600 && aspectHeight == 900) "16:9" else "4:3",
    slideCount = 3,
    stepCount = 6,
    coverUrl = "preview://cover/dmg2k4x9ab12c",
)

// ---------------------------------------------------------------- 列表页

@Preview(name = "60 演示列表 · 全部状态", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun PptListStatesPreview() {
    ClassToolsTheme {
        PptListContent(
            decks = previewDecks,
            agentOnline = false,
            mySid = "3260707024",
            imageLoader = PptPreviewImageLoader,
            loading = false,
            errorMessage = null,
            offlineHint = false,
            uploadBar = PptUploadBar(title = "上传中的演示", percent = 34, deckId = "dupload00001"),
            uploadFailedMessage = null,
            resumeRecords = mapOf(
                "dmg2k4x9ab12c" to PptResumeRecord(
                    mode = PptPlayMode.VIDEO,
                    videoSec = 754.0,
                    updatedAtMs = System.currentTimeMillis() - 1_800_000L,
                ),
            ),
            onBack = {},
            onUpload = {},
            onOpenDetail = {},
            onDelete = {},
            onRetry = {},
            onCancelUpload = {},
            onDismissUploadError = {},
            onRefresh = {},
        )
    }
}

@Preview(
    name = "60b 演示列表 · 大字体 1.3",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
    fontScale = 1.3f,
)
@Composable
private fun PptListLargeFontPreview() {
    ClassToolsTheme {
        PptListContent(
            decks = previewDecks,
            agentOnline = false,
            mySid = "3260707024",
            imageLoader = PptPreviewImageLoader,
            loading = false,
            errorMessage = null,
            offlineHint = false,
            uploadBar = null,
            uploadFailedMessage = null,
            onBack = {},
            onUpload = {},
            onOpenDetail = {},
            onDelete = {},
            onRetry = {},
            onCancelUpload = {},
            onDismissUploadError = {},
            onRefresh = {},
        )
    }
}

@Preview(name = "61 演示列表 · 空态", showBackground = true, widthDp = 412, heightDp = 760)
@Composable
private fun PptListEmptyPreview() {
    ClassToolsTheme {
        PptListContent(
            decks = emptyList(),
            agentOnline = null,
            mySid = "3260707024",
            imageLoader = PptPreviewImageLoader,
            loading = false,
            errorMessage = null,
            offlineHint = false,
            uploadBar = null,
            uploadFailedMessage = null,
            onBack = {},
            onUpload = {},
            onOpenDetail = {},
            onDelete = {},
            onRetry = {},
            onCancelUpload = {},
            onDismissUploadError = {},
            onRefresh = {},
        )
    }
}

// ---------------------------------------------------------------- 播放器

@Preview(name = "62 演示播放器 · 16:9 舞台", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun PptPlayer169Preview() {
    ClassToolsTheme {
        PptPlayerContent(
            detail = playerDetail(),
            imageLoader = PptPreviewImageLoader,
            onClose = {},
        )
    }
}

@Preview(name = "63 演示播放器 · 4:3 舞台（平板宽）", showBackground = true, widthDp = 900, heightDp = 600)
@Composable
private fun PptPlayer43Preview() {
    ClassToolsTheme {
        PptPlayerContent(
            detail = playerDetail(aspectWidth = 1600, aspectHeight = 1200),
            imageLoader = PptPreviewImageLoader,
            onClose = {},
        )
    }
}

@Preview(name = "64 演示播放器 · 深色", showBackground = true, widthDp = 412, heightDp = 760, uiMode = 32)
@Composable
private fun PptPlayerDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        PptPlayerContent(
            detail = playerDetail(),
            imageLoader = PptPreviewImageLoader,
            onClose = {},
        )
    }
}
@Preview(name = "65 演示播放器 · 原样播放控制条", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun PptVideoModePreview() {
    ClassToolsTheme {
        PptPlayerContent(
            detail = previewVideoDetail(),
            imageLoader = PptPreviewImageLoader,
            onClose = {},
            initialMode = PptPlayMode.VIDEO,
            initialVideoSec = 42.0,
            videoPlaybackEnabled = false,
        )
    }
}

@Preview(name = "66 演示播放器 · 分步热点（64dp 脉冲）", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun PptStepsHotspotPreview() {
    ClassToolsTheme {
        PptPlayerContent(
            detail = playerDetail().copy(manifest = previewManifestHotspot),
            imageLoader = PptPreviewImageLoader,
            onClose = {},
            initialMode = PptPlayMode.STEPS,
        )
    }
}
// ---------------------------------------------------------------- v2.0 下载详情

private fun downloadFilesManifest(warnings: List<String> = emptyList()): PptManifest = previewManifest.copy(
    files = PptFiles(
        compat = PptFileInfo(
            src = "compat.pptx",
            size = 18_432_000L,
            sha256 = "abc123",
            ext = "pptx",
            name = "班会：期中总结与学习计划-兼容版.pptx",
        ),
        pdf = PptFileInfo(
            src = "preview.pdf",
            size = 6_291_456L,
            ext = "pdf",
            name = "班会：期中总结与学习计划.pdf",
            pages = 16,
        ),
        source = PptFileInfo(
            src = "original.pptx",
            size = 17_825_792L,
            ext = "pptx",
            name = "班会：期中总结与学习计划.pptx",
        ),
    ),
    compat = PptCompat(warnings = warnings, fontsEmbedded = 2, convertedFrom = "ppt"),
    mediaFiles = listOf(
        PptMediaFile(
            src = "media/班会_视频1_第7页.mp4",
            name = "班会_视频1_第7页.mp4",
            size = 20_158_796L,
            sha256 = "m1",
            kind = PptMediaKind.VIDEO,
            durationSec = 150.92,
            width = 1024,
            height = 576,
            slides = listOf(7),
            sourceName = "media1.mp4",
        ),
        PptMediaFile(
            src = "media/班会_视频2_第12页.mp4",
            name = "",
            size = 13_668_151L,
            sha256 = "m2",
            kind = PptMediaKind.VIDEO,
            durationSec = 225.582,
            width = 1280,
            height = 720,
            slides = listOf(12),
            sourceName = "media2.mp4",
        ),
        PptMediaFile(
            src = "media/班会_音频1_第3页.m4a",
            name = "",
            size = 1_048_576L,
            sha256 = "m3",
            kind = PptMediaKind.AUDIO,
            durationSec = 45.5,
            slides = listOf(3),
            sourceName = "media3.m4a",
        ),
    ),
)

private fun downloadDetail(warnings: List<String> = emptyList()): PptDeckDetail = playerDetail().copy(
    manifest = downloadFilesManifest(warnings),
    status = "ready",
    ownerName = "陈子麒",
    sourceSize = 17_825_792L,
    coverUrl = "preview://cover/dmg2k4x9ab12c",
)

private fun legacyDetail(): PptDeckDetail = playerDetail().copy(
    status = "ready",
    ownerName = "陈子麒",
    sourceSize = 17_825_792L,
    coverUrl = "preview://cover/dmg2k4x9ab12c",
)

@Composable
private fun PptDownloadDetailPreviewContent(
    detail: PptDeckDetail,
) {
    PptDownloadDetailContent(
        detail = detail,
        imageLoader = PptPreviewImageLoader,
        loading = false,
        error = null,
        reprocessing = false,
        rowStates = emptyMap(),
        onBack = {},
        onRetryLoad = {},
        onDownload = {},
        onOpenFile = {},
        onShareFile = {},
        onReprocess = {},
        onOpenPreview = {},
    )
}

@Preview(name = "67 演示下载详情 · 三行+大小", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun PptDownloadDetailPreview() {
    ClassToolsTheme {
        PptDownloadDetailPreviewContent(detail = downloadDetail())
    }
}

@Preview(name = "68 演示下载详情 · 兼容提示", showBackground = true, widthDp = 412, heightDp = 1050)
@Composable
private fun PptDownloadDetailWarningsPreview() {
    ClassToolsTheme {
        PptDownloadDetailPreviewContent(
            detail = downloadDetail(
                warnings = listOf(
                    "外链视频 1 处未随文件携带（第 7 页，路径尾 …/v.mp4）",
                    "2 个字体未嵌入（许可受限），请在装有该字体的电脑上编辑",
                ),
            ),
        )
    }
}

@Preview(name = "69 演示下载详情 · 旧 deck", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun PptDownloadDetailLegacyPreview() {
    ClassToolsTheme {
        PptDownloadDetailPreviewContent(detail = legacyDetail())
    }
}