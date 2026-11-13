package com.furina.classtools.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import com.furina.classtools.data.diag.DiagAgentInfo
import com.furina.classtools.data.diag.DiagParser
import com.furina.classtools.data.diag.DiagPollConfig
import com.furina.classtools.data.ppt.PptDeck
import com.furina.classtools.data.ppt.PptDeckDetail
import com.furina.classtools.data.ppt.PptFileInfo
import com.furina.classtools.data.ppt.PptFiles
import com.furina.classtools.data.ppt.PptImageLoader
import com.furina.classtools.data.ppt.PptManifest
import com.furina.classtools.data.ppt.PptMediaFile
import com.furina.classtools.data.ppt.PptMediaKind
import com.furina.classtools.data.session.Session
import com.furina.classtools.ui.screens.diag.DiagScreen
import com.furina.classtools.ui.screens.diag.PreviewDiagRepository
import com.furina.classtools.ui.screens.diag.previewAnalyses
import com.furina.classtools.ui.screens.diag.previewChecks
import com.furina.classtools.ui.screens.diag.previewHealthRows
import com.furina.classtools.ui.screens.diag.previewReports
import com.furina.classtools.ui.screens.diag.previewRepairs
import com.furina.classtools.ui.screens.ppt.PptDownloadDetailContent
import com.furina.classtools.ui.screens.ppt.PptListContent
import com.furina.classtools.ui.screens.ppt.PptRowDownloadState
import com.furina.classtools.ui.theme.ClassToolsTheme

/**
 * 工具页 v6（演示 PPT / 诊断）预览：统一 CtToolCard / CtToolSectionHeader 后的浅色、深色、
 * 上传中、下载详情、折叠/展开、大字体与平板形态。全部为纯状态注入，不联网、不读写文件。
 */

private const val V6_NOW: Long = 1_793_930_400_000L

private val v6Session = Session(
    sid = "3260799999",
    name = "测试账号",
    token = "preview-token",
)

private object V6PreviewImageLoader : PptImageLoader {
    override suspend fun load(url: String, targetWidth: Int): ImageBitmap? = null

    override fun clearMemory() = Unit
}

private fun deck(
    id: String,
    title: String,
    status: String = PptDeck.STATUS_READY,
    slideCount: Int = 16,
    ownerName: String = "陈子麒",
    progress: Int = 100,
    error: String = "",
    hasCompat: Boolean = true,
    hasPdf: Boolean = true,
) = PptDeck(
    id = id,
    title = title,
    ownerName = ownerName,
    status = status,
    progress = progress,
    error = error,
    slideCount = slideCount,
    stepCount = slideCount * 12,
    sourceName = "班级活动策划.pptx",
    sourceSize = 18_345_678L,
    createdAt = V6_NOW - 3_600_000L,
    convertedAt = V6_NOW - 1_800_000L,
    hasCompatFiles = hasCompat,
    hasPdfFile = hasPdf,
)

private fun previewDecks(): List<PptDeck> = listOf(
    deck("d1", "班级活动策划 · 期末班会"),
    deck(
        id = "d2",
        title = "学风建设主题班会",
        status = PptDeck.STATUS_FAILED,
        slideCount = 0,
        progress = 0,
        error = "转换失败：文件损坏或包含不支持的媒体",
        hasCompat = false,
        hasPdf = false,
    ),
    deck(
        id = "d3",
        title = "大学生职业生涯规划",
        status = PptDeck.STATUS_PROCESSING,
        slideCount = 0,
        progress = 42,
        hasCompat = false,
        hasPdf = false,
    ),
)

private fun previewDetail(): PptDeckDetail = PptDeckDetail(
    title = "班级活动策划 · 期末班会",
    manifest = PptManifest(
        base = "https://example.invalid/ppt/a/d1/build1/",
        width = 1600,
        height = 900,
        files = PptFiles(
            compat = PptFileInfo(
                src = "班级活动策划_兼容版.pptx",
                size = 39_063_744L,
                ext = "pptx",
                name = "班级活动策划_兼容版.pptx",
                pages = 16,
            ),
            pdf = PptFileInfo(
                src = "班级活动策划_预览.pdf",
                size = 18_670_000L,
                ext = "pdf",
                name = "班级活动策划_预览.pdf",
                pages = 16,
            ),
            source = PptFileInfo(
                src = "班级活动策划_原始文件.pptx",
                size = 38_016_533L,
                ext = "pptx",
                name = "班级活动策划_原始文件.pptx",
            ),
        ),
        mediaFiles = listOf(
            PptMediaFile(
                src = "media/视频1_第7页.mp4",
                name = "班级活动策划_视频1_第7页.mp4",
                size = 20_158_796L,
                kind = PptMediaKind.VIDEO,
                durationSec = 150.92,
                width = 1280,
                height = 720,
                slides = listOf(7),
            ),
            PptMediaFile(
                src = "media/音频1_第12页.m4a",
                name = "班级活动策划_音频1_第12页.m4a",
                size = 2_300_000L,
                kind = PptMediaKind.AUDIO,
                durationSec = 42.0,
                slides = listOf(12),
            ),
        ),
        compat = com.furina.classtools.data.ppt.PptCompat(
            warnings = listOf("外链媒体未随文件携带，请班级电脑联网播放", "字体未嵌入（许可限制）"),
            fontsEmbedded = 2,
        ),
    ),
    sourceName = "班级活动策划.pptx",
    sourceKey = "a/d1/build1/source.pptx",
    aspect = "16:9",
    slideCount = 16,
    stepCount = 192,
    convertedAt = V6_NOW - 1_800_000L,
    coverUrl = "",
    status = PptDeck.STATUS_READY,
    progress = 100,
    ownerName = "陈子麒",
)

@Composable
private fun PptListPreview(dark: Boolean) {
    ClassToolsTheme(darkTheme = dark) {
        PptListContent(
            decks = previewDecks(),
            agentOnline = true,
            mySid = v6Session.sid,
            imageLoader = V6PreviewImageLoader,
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

@Preview(name = "230 演示 v6 · 列表（浅色）", showBackground = true, widthDp = 412, heightDp = 920)
@Composable
private fun PptListLightPreview() {
    PptListPreview(dark = false)
}

@Preview(name = "231 演示 v6 · 列表（深色）", showBackground = true, widthDp = 412, heightDp = 920)
@Composable
private fun PptListDarkPreview() {
    PptListPreview(dark = true)
}


@Preview(name = "232 演示 v6 · 下载详情（文件 + 媒体）", showBackground = true, widthDp = 412, heightDp = 1300)
@Composable
private fun PptDownloadDetailPreview() {
    ClassToolsTheme(darkTheme = false) {
        PptDownloadDetailContent(
            detail = previewDetail(),
            imageLoader = V6PreviewImageLoader,
            loading = false,
            error = null,
            reprocessing = false,
            rowStates = mapOf(
                "compat" to PptRowDownloadState.Done(File("preview/compat.pptx")),
                "pdf" to PptRowDownloadState.Downloading(percent = 42),
                "source" to PptRowDownloadState.Idle,
                "media:video_1" to PptRowDownloadState.Downloading(percent = 18),
            ),
            onBack = {},
            onRetryLoad = {},
            onDownload = {},
            onOpenFile = {},
            onShareFile = {},
            onReprocess = {},
            onOpenPreview = {},
        )
    }
}

private fun diagGroups() = DiagParser.groupServiceHealth(previewHealthRows(), V6_NOW)

@Composable
private fun DiagV6Preview(
    dark: Boolean = false,
) {
    ClassToolsTheme(darkTheme = dark) {
        DiagScreen(
            onBack = {},
            session = v6Session,
            repository = PreviewDiagRepository,
            skipLoad = true,
            initialGroups = diagGroups(),
            initialAnalyses = previewAnalyses(),
            initialRepairs = previewRepairs(),
            initialAgent = DiagAgentInfo(
                online = true,
                seenAt = V6_NOW - 60_000L,
                host = "local-windows",
                version = "1.1.0",
            ),
            initialChecks = previewChecks(),
            initialMyReports = previewReports(),
            initialExpandedIds = emptySet(),
            initialOthersExpanded = false,
            nowMillis = { V6_NOW },
            pollConfig = DiagPollConfig(),
        )
    }
}

@Preview(name = "233 诊断 v6 · 折叠态（浅色）", showBackground = true, widthDp = 412, heightDp = 2200)
@Composable
private fun DiagV6FoldedPreview() {
    DiagV6Preview()
}


@Preview(name = "234 诊断 v6 · 深色", showBackground = true, widthDp = 412, heightDp = 2200)
@Composable
private fun DiagV6DarkPreview() {
    DiagV6Preview(dark = true)
}

@Preview(name = "235 诊断 v6 · 平板 800dp", showBackground = true, widthDp = 800, heightDp = 1100)
@Composable
private fun DiagV6TabletPreview() {
    DiagV6Preview(dark = false)
}
