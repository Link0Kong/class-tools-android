@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.ppt

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.ppt.PptDeckDetail
import com.furina.classtools.data.ppt.PptDetailResult
import com.furina.classtools.data.ppt.PptFileDownloader
import com.furina.classtools.data.ppt.PptDownloadEvent
import com.furina.classtools.data.ppt.PptImageLoader
import com.furina.classtools.data.ppt.PptMediaKind
import com.furina.classtools.data.ppt.PptRepository
import com.furina.classtools.features.ppt.PptDownloadLogic
import com.furina.classtools.features.ppt.PptMediaDownloadLogic
import com.furina.classtools.features.ppt.PptStatusTexts
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.theme.CtShapeTokens
import java.io.File
import kotlinx.coroutines.launch

/** 单行下载状态（详情页三行各自独立）。 */
sealed interface PptRowDownloadState {
    data object Idle : PptRowDownloadState
    data class Downloading(val percent: Int) : PptRowDownloadState
    data class Done(val file: File) : PptRowDownloadState
    data class Failed(val message: String) : PptRowDownloadState
}

/**
 * v2.0 下载详情页（契约 §12.4.1/§12.4.2）。
 *
 * - 拉单 deck manifest（[PptRepository.loadDownloadDetail]）→ 三行下载：兼容版 PPTX / PDF / 原始文件（显示大小）；
 * - 点下载 → DownloadManager 入队（系统下载通知），完成自动 `ACTION_VIEW`（FileProvider）打开，可再分享；
 * - `compat.warnings` 橙色「兼容提示」逐条；
 * - 旧 deck（无 files）：「兼容版未生成」+「在线预览」（旧播放器）+「重新处理」+ 原始文件下载；
 * - 状态非 ready：只显示状态与进度（完成前不显示下载）。
 */
@Composable
fun PptDownloadDetailScreen(
    deckId: String,
    onBack: () -> Unit,
    onOpenPreview: (String) -> Unit,
    repository: PptRepository?,
    imageLoader: PptImageLoader,
    modifier: Modifier = Modifier,
    downloader: PptFileDownloader? = null,
    initialDetail: PptDeckDetail? = null,
    skipLoad: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember(deckId) { mutableStateOf(!skipLoad && initialDetail == null) }
    var detail by remember(deckId, initialDetail) { mutableStateOf(initialDetail) }
    var error by remember(deckId) { mutableStateOf<String?>(null) }
    var retryToken by remember(deckId) { mutableIntStateOf(0) }
    var reprocessing by remember(deckId) { mutableStateOf(false) }
    var rowStates by remember(deckId) {
        // key：文件行 "compat"/"pdf"/"source"；媒体行 "media:<video_1>"（v2.3 §12.8）
        mutableStateOf<Map<String, PptRowDownloadState>>(emptyMap())
    }

    LaunchedEffect(deckId, retryToken, skipLoad) {
        if (skipLoad) return@LaunchedEffect
        val repo = repository ?: return@LaunchedEffect
        loading = true
        error = null
        when (val result = repo.loadDownloadDetail(deckId)) {
            is PptDetailResult.Success -> detail = result.detail
            is PptDetailResult.Failure -> error = result.message
        }
        loading = false
    }

    fun update(key: String, state: PptRowDownloadState) {
        rowStates = rowStates + (key to state)
    }

    /** 通用下载（文件行与 v2.3 媒体行共用 DownloadManager 逻辑）。 */
    fun startFileDownload(key: String, url: String, fileName: String, mimeType: String) {
        val engine = downloader ?: return
        if (url.isBlank()) return
        val ticket = engine.enqueue(url, fileName, mimeType)
        if (ticket == null) {
            update(key, PptRowDownloadState.Failed("下载启动失败"))
            toast(context, "下载启动失败，请稍后重试")
            return
        }
        update(key, PptRowDownloadState.Downloading(0))
        scope.launch {
            engine.observe(ticket).collect { event ->
                when (event) {
                    is PptDownloadEvent.Progress ->
                        update(key, PptRowDownloadState.Downloading(event.percent))

                    is PptDownloadEvent.Done -> {
                        update(key, PptRowDownloadState.Done(event.ticket.file))
                        // 完成后用系统播放器/查看器打开（视频用 video/* → 系统播放器/WPS）
                        val opened = PptFileIntents.tryOpen(
                            context, event.ticket.file, mimeType, event.ticket.fileName,
                        )
                        toast(
                            context,
                            if (opened) "已下载：${event.ticket.fileName}" else "已下载，未找到可打开的应用",
                        )
                    }

                    is PptDownloadEvent.Failed -> {
                        update(key, PptRowDownloadState.Failed(event.reason))
                        toast(context, event.reason)
                    }
                }
            }
        }
    }

    fun shareState(key: String, mimeType: String, fileName: String) {
        val state = rowStates[key]
        if (state !is PptRowDownloadState.Done) {
            toast(context, "请先下载")
            return
        }
        val shared = PptFileIntents.tryShare(context, state.file, mimeType, fileName)
        if (!shared) toast(context, "没有可用的分享应用")
    }

    fun openState(key: String, url: String, fileName: String, mimeType: String) {
        val state = rowStates[key]
        if (state !is PptRowDownloadState.Done) {
            startFileDownload(key, url, fileName, mimeType)
            return
        }
        val opened = PptFileIntents.tryOpen(context, state.file, mimeType, fileName)
        if (!opened) toast(context, "没有可用的应用打开该文件")
    }

    fun startDownload(row: PptDownloadLogic.DownloadRow) {
        if (!row.available) return
        startFileDownload(row.kind.key, row.url, row.fileName, row.mimeType)
    }

    fun shareRow(row: PptDownloadLogic.DownloadRow) =
        shareState(row.kind.key, row.mimeType, row.fileName)

    fun openRow(row: PptDownloadLogic.DownloadRow) =
        openState(row.kind.key, row.url, row.fileName, row.mimeType)

    fun startMediaDownload(row: PptMediaDownloadLogic.MediaRow) =
        startFileDownload("media:" + row.key, row.url, row.fileName, row.mimeType)

    fun shareMedia(row: PptMediaDownloadLogic.MediaRow) =
        shareState("media:" + row.key, row.mimeType, row.fileName)

    fun openMedia(row: PptMediaDownloadLogic.MediaRow) =
        openState("media:" + row.key, row.url, row.fileName, row.mimeType)

    fun reprocess() {
        val repo = repository ?: return
        if (reprocessing) return
        reprocessing = true
        scope.launch {
            when (val result = repo.retryConversion(deckId)) {
                is com.furina.classtools.data.network.ApiResult.Success -> {
                    toast(context, "已提交重新处理")
                    retryToken += 1
                }

                is com.furina.classtools.data.network.ApiResult.Failure ->
                    toast(context, result.error.displayMessage)
            }
            reprocessing = false
        }
    }

    PptDownloadDetailContent(
        detail = detail,
        imageLoader = imageLoader,
        loading = loading,
        error = error,
        reprocessing = reprocessing,
        rowStates = rowStates,
        onBack = onBack,
        onRetryLoad = { retryToken += 1 },
        onDownload = ::startDownload,
        onOpenFile = ::openRow,
        onShareFile = ::shareRow,
        onDownloadMedia = ::startMediaDownload,
        onOpenMedia = ::openMedia,
        onShareMedia = ::shareMedia,
        onReprocess = ::reprocess,
        onOpenPreview = { onOpenPreview(deckId) },
        modifier = modifier,
    )
}

// =============================================================================
// 内容层（无网络/无下载副作用；@Preview 直接复用）
// =============================================================================

@Composable
fun PptDownloadDetailContent(
    detail: PptDeckDetail?,
    imageLoader: PptImageLoader,
    loading: Boolean,
    error: String?,
    reprocessing: Boolean,
    /** key：文件行 "compat"/"pdf"/"source"；媒体行 "media:<video_1>"（v2.3 §12.8） */
    rowStates: Map<String, PptRowDownloadState>,
    onBack: () -> Unit,
    onRetryLoad: () -> Unit,
    onDownload: (PptDownloadLogic.DownloadRow) -> Unit,
    onOpenFile: (PptDownloadLogic.DownloadRow) -> Unit,
    onShareFile: (PptDownloadLogic.DownloadRow) -> Unit,
    onReprocess: () -> Unit,
    onOpenPreview: () -> Unit,
    onDownloadMedia: (PptMediaDownloadLogic.MediaRow) -> Unit = {},
    onOpenMedia: (PptMediaDownloadLogic.MediaRow) -> Unit = {},
    onShareMedia: (PptMediaDownloadLogic.MediaRow) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            CtTopBar(
                title = detail?.title?.ifBlank { "下载" } ?: "下载",
                subtitle = if (detail != null && detail.isReady) metaLine(detail) else null,
                onBack = onBack,
            )

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                }

                error != null || detail == null -> Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = error ?: "加载失败",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRetryLoad) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重试")
                    }
                }

                else -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DetailHeader(detail = detail, imageLoader = imageLoader)
                    if (!detail.isReady) {
                        NotReadyCard(
                            detail = detail,
                            reprocessing = reprocessing,
                            onReprocess = onReprocess,
                        )
                    } else {
                        val warnings = PptDownloadLogic.warnings(detail)
                        if (warnings.isNotEmpty()) {
                            CompatWarningsCard(warnings = warnings)
                        }
                        val rows = PptDownloadLogic.rows(detail)
                        if (PptDownloadLogic.isLegacyDeck(detail)) {
                            LegacyCard(
                                reprocessing = reprocessing,
                                canPreview = PptDownloadLogic.canPreview(detail),
                                onReprocess = onReprocess,
                                onOpenPreview = onOpenPreview,
                            )
                            rows.filter { it.kind == PptDownloadLogic.Kind.SOURCE && it.available }
                                .forEach { row ->
                                    DownloadRowCard(
                                        row = row,
                                        state = rowStates[row.kind.key] ?: PptRowDownloadState.Idle,
                                        onDownload = { onDownload(row) },
                                        onOpen = { onOpenFile(row) },
                                        onShare = { onShareFile(row) },
                                    )
                                }
                        } else {
                            rows.forEach { row ->
                                DownloadRowCard(
                                    row = row,
                                    state = rowStates[row.kind.key] ?: PptRowDownloadState.Idle,
                                    onDownload = { onDownload(row) },
                                    onOpen = { onOpenFile(row) },
                                    onShare = { onShareFile(row) },
                                )
                            }
                        }
                        // v2.3 §12.8：视频/音频单独下载（无 media_files 的旧 deck 不显示）
                        val mediaRows = remember(detail.manifest) {
                            PptMediaDownloadLogic.rows(detail.manifest, detail.title)
                        }
                        if (mediaRows.isNotEmpty()) {
                            MediaDownloadGroup(
                                rows = mediaRows,
                                rowStates = rowStates,
                                onDownload = onDownloadMedia,
                                onOpen = onOpenMedia,
                                onShare = onShareMedia,
                            )
                        }
                        if (!PptDownloadLogic.isLegacyDeck(detail) && PptDownloadLogic.canPreview(detail)) {
                            TextButton(onClick = onOpenPreview) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("在线预览")
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 区块

@Composable
private fun DetailHeader(detail: PptDeckDetail, imageLoader: PptImageLoader) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(128.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                PptAsyncImage(
                    url = detail.coverUrl,
                    loader = imageLoader,
                    modifier = Modifier.fillMaxSize(),
                    targetWidth = 400,
                    contentDescription = "",
                    fallback = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.title.ifBlank { "未命名演示" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metaLine(detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail.isReady) {
                    val tags = buildList {
                        if (detail.manifest.hasCompatFile) add("兼容版")
                        if (detail.manifest.hasPdfFile) add("PDF")
                    }
                    if (tags.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            tags.forEach { tag -> PptTag(tag) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PptTag(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun NotReadyCard(detail: PptDeckDetail, reprocessing: Boolean, onReprocess: () -> Unit) {
    val kind = PptStatusTexts.kindOf(detail.status)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (kind == PptStatusTexts.Kind.PROCESSING || kind == PptStatusTexts.Kind.QUEUED) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = PptStatusTexts.text(detail.status, detail.progress, agentOnline = null),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (kind == PptStatusTexts.Kind.PROCESSING || kind == PptStatusTexts.Kind.UPLOADING) {
                LinearProgressIndicator(
                    progress = { detail.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (detail.error.isNotBlank()) {
                Text(
                    text = PptStatusTexts.errorLine(detail.error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (kind == PptStatusTexts.Kind.FAILED || detail.status == "queued") {
                TextButton(onClick = onReprocess, enabled = !reprocessing) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (reprocessing) "处理中…" else "重新处理")
                }
            }
        }
    }
}

@Composable
private fun CompatWarningsCard(warnings: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = "兼容提示",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            warnings.forEach { warning ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LegacyCard(
    reprocessing: Boolean,
    canPreview: Boolean,
    onReprocess: () -> Unit,
    onOpenPreview: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = PptDownloadLogic.LEGACY_TITLE,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (canPreview) {
                    TextButton(onClick = onOpenPreview) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("在线预览")
                    }
                }
                TextButton(onClick = onReprocess, enabled = !reprocessing) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (reprocessing) "处理中…" else "重新处理")
                }
            }
        }
    }
}

@Composable
private fun DownloadRowCard(
    row: PptDownloadLogic.DownloadRow,
    state: PptRowDownloadState,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconOf(row.kind),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = row.kind.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (row.available) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = sublineOf(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                !row.available -> Text(
                    text = "未生成",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                state is PptRowDownloadState.Downloading -> Column(
                    modifier = Modifier.width(84.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { state.percent.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${state.percent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state is PptRowDownloadState.Done -> Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Text("打开")
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Rounded.Share,
                            contentDescription = "分享",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                state is PptRowDownloadState.Failed -> TextButton(onClick = onDownload) { Text("重试") }

                else -> Button(
                    onClick = onDownload,
                    modifier = Modifier.height(34.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("下载", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- v2.3 视频/音频单独下载（§12.8）

@Composable
private fun MediaDownloadGroup(
    rows: List<PptMediaDownloadLogic.MediaRow>,
    rowStates: Map<String, PptRowDownloadState>,
    onDownload: (PptMediaDownloadLogic.MediaRow) -> Unit,
    onOpen: (PptMediaDownloadLogic.MediaRow) -> Unit,
    onShare: (PptMediaDownloadLogic.MediaRow) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = PptMediaDownloadLogic.GROUP_TITLE,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = PptMediaDownloadLogic.summaryText(rows),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        rows.forEach { row ->
            MediaDownloadRowCard(
                row = row,
                state = rowStates["media:" + row.key] ?: PptRowDownloadState.Idle,
                onDownload = { onDownload(row) },
                onOpen = { onOpen(row) },
                onShare = { onShare(row) },
            )
        }
    }
}

/** 媒体行：`视频1 · 第 7 页` + 时长/分辨率 + 大小 + 下载/打开/分享（点按 ≥48dp，含无障碍描述）。 */
@Composable
private fun MediaDownloadRowCard(
    row: PptMediaDownloadLogic.MediaRow,
    state: PptRowDownloadState,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    val isAudio = row.kind == PptMediaKind.AUDIO
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isAudio) Icons.Rounded.MusicNote else Icons.Rounded.PlayCircle,
                        contentDescription = if (isAudio) "音频" else "视频",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = mediaSubline(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                state is PptRowDownloadState.Downloading -> Column(
                    modifier = Modifier.width(84.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { state.percent.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${state.percent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state is PptRowDownloadState.Done -> Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onOpen,
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    ) { Text("打开") }
                    IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Rounded.Share,
                            contentDescription = "分享 ${row.title}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                state is PptRowDownloadState.Failed -> TextButton(
                    onClick = onDownload,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("重试") }

                else -> Button(
                    onClick = onDownload,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("下载", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** 媒体行副标题：`02:30 · 1024×576 · 19.2 MB`（未知部分省略）。 */
private fun mediaSubline(row: PptMediaDownloadLogic.MediaRow): String {
    val parts = ArrayList<String>(3)
    if (row.detail.isNotBlank()) parts.add(row.detail)
    if (row.sizeText != "—") parts.add(row.sizeText)
    return if (parts.isEmpty()) "—" else parts.joinToString(" · ")
}
private fun iconOf(kind: PptDownloadLogic.Kind): ImageVector = when (kind) {
    PptDownloadLogic.Kind.COMPAT -> Icons.Rounded.Description
    PptDownloadLogic.Kind.PDF -> Icons.Rounded.PictureAsPdf
    PptDownloadLogic.Kind.SOURCE -> Icons.Rounded.Image
}

private fun sublineOf(row: PptDownloadLogic.DownloadRow): String {
    val parts = ArrayList<String>(2)
    if (row.sizeText != "—") parts.add(row.sizeText)
    if (row.subtitle.isNotBlank()) parts.add(row.subtitle)
    return if (parts.isEmpty()) "—" else parts.joinToString(" · ")
}

private fun metaLine(detail: PptDeckDetail): String {
    val parts = ArrayList<String>(2)
    if (detail.slideCount > 0) parts.add("${detail.slideCount} 页")
    if (detail.ownerName.isNotBlank()) parts.add(detail.ownerName)
    return if (parts.isEmpty()) "—" else parts.joinToString(" · ")
}

private fun toast(context: Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
}
