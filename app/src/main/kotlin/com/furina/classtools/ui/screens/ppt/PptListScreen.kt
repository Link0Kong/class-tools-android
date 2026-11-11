@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.ppt

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.furina.classtools.data.ppt.DefaultPptRepository
import com.furina.classtools.data.ppt.PptByteSource
import com.furina.classtools.data.ppt.PptDeck
import com.furina.classtools.data.ppt.PptImageLoader
import com.furina.classtools.data.ppt.PptListResult
import com.furina.classtools.data.ppt.PptLocalStore
import com.furina.classtools.data.ppt.PptRepository
import com.furina.classtools.data.ppt.PptUploadInput
import com.furina.classtools.data.ppt.PptUploadPhase
import com.furina.classtools.data.ppt.PptUploadResult
import com.furina.classtools.data.ppt.PptUriSource
import com.furina.classtools.data.session.Session
import com.furina.classtools.features.ppt.PptDeckListLogic
import com.furina.classtools.features.ppt.PptDownloadLogic
import com.furina.classtools.features.ppt.PptLimits
import com.furina.classtools.features.ppt.PptResumeLogic
import com.furina.classtools.features.ppt.PptResumeRecord
import com.furina.classtools.features.ppt.PptStatusTexts
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.theme.CtShapeTokens
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 列表页可见轮询间隔（契约 §4.7：可见 10s，页面隐藏停止，回前台立即拉）。 */
private const val POLL_INTERVAL_MS = 10_000L

/** 上传中占位卡片状态（本地；契约 §3.1「上传进度用本地状态，不写云端」）。 */
data class PptUploadBar(
    val title: String,
    val percent: Int,
    val deckId: String?,
)

/**
 * 演示列表页（契约 §4.4/§4.7/§8.1；设计 web/ppt/DESIGN.md §4.1/§4.2）。
 *
 * - 卡片流：封面 + 标题 + 上传者/页数 + 状态；`ready` 点卡片直接进播放器；
 * - 顶部「上传 PPT」→ SAF 选择 .ppt/.pptx（读文件名/大小，≤64MiB）→ 可选改标题 → 分片上传（进度就地显示）；
 * - 本人卡片可删除（二次确认）；失败可重试（上传中断 = 重新选文件；转换失败 = 云端重新排队）；
 * - 可见 10s 轮询 + onResume 强拉；转换机离线提示；失败回退上次列表（不白屏）。
 */
@Composable
fun PptListScreen(
    onBack: () -> Unit,
    onOpenDetail: (PptDeck) -> Unit,
    repository: PptRepository,
    imageLoader: PptImageLoader,
    session: Session?,
    modifier: Modifier = Modifier,
    localStore: PptLocalStore? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val mySid = session?.sid

    var decks by remember { mutableStateOf<List<PptDeck>>(emptyList()) }
    var resumeRecords by remember { mutableStateOf<Map<String, PptResumeRecord>>(emptyMap()) }
    var agentOnline by remember { mutableStateOf<Boolean?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var offlineHint by remember { mutableStateOf(false) }
    var uploadFailedMessage by remember { mutableStateOf<String?>(null) }
    var pendingSource by remember { mutableStateOf<PptUriSource?>(null) }
    var titleDraft by remember { mutableStateOf("") }
    var replaceDeckId by remember { mutableStateOf<String?>(null) }
    var uploadJob by remember { mutableStateOf<Job?>(null) }
    var uploadNonce by remember { mutableStateOf(0) }
    var uploadTitle by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<PptDeck?>(null) }
    val uploadProgress by repository.uploadProgress.collectAsStateWithLifecycle()

    suspend fun refresh() {
        when (val result = repository.loadList()) {
            is PptListResult.Success -> {
                decks = result.decks
                agentOnline = result.agent?.isOnline()
                errorMessage = null
                offlineHint = false
            }

            is PptListResult.Failure -> {
                errorMessage = result.message
                val cached = result.cachedDecks
                if (cached != null) {
                    decks = cached
                    offlineHint = true
                }
                result.cachedAgent?.let { agentOnline = it.isOnline() }
            }
        }
        // v1.2 续播：刷新「继续」徽标（7 天内 + 有进度）；顺带清理过期记录。
        resumeRecords = localStore?.resumeRecords(decks.map { it.id }) ?: emptyMap()
        loading = false
    }

    // 可见轮询：repeatOnLifecycle(RESUMED) 每次回到前台立即拉一次，之后 10s 一轮（契约 §4.7）。
    LaunchedEffect(repository, mySid) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            replaceDeckId = null
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val source = PptUriSource.open(context, uri)
            if (source == null) {
                toast(context, "无法读取所选文件")
                replaceDeckId = null
                return@launch
            }
            val reason = PptLimits.rejectReason(source.size, source.name)
            if (reason != null) {
                toast(context, reason)
                replaceDeckId = null
                return@launch
            }
            pendingSource = source
            titleDraft = PptLimits.titleFromFileName(source.name)
        }
    }

    fun pickFile() {
        // OpenDocument + ppt/pptx MIME；部分文件管理器把 pptx 报成 octet-stream，扩展名仍会再校验。
        pickLauncher.launch(
            arrayOf(
                MIME_PPT,
                MIME_PPTX,
                "application/octet-stream",
            ),
        )
    }

    fun startUpload(title: String, source: PptByteSource) {
        pendingSource = null
        uploadFailedMessage = null
        uploadTitle = PptLimits.sanitizeTitle(title)
        val replaceId = replaceDeckId
        replaceDeckId = null
        uploadJob?.cancel()
        val nonce = uploadNonce + 1
        uploadNonce = nonce
        uploadJob = scope.launch {
            try {
                when (val result = repository.upload(PptUploadInput(title = uploadTitle, source = source))) {
                    is PptUploadResult.Success -> {
                        if (replaceId != null) runCatching { repository.softDelete(replaceId) }
                        toast(context, "已上传，排队转换中")
                        refresh()
                    }

                    is PptUploadResult.Failure -> {
                        uploadFailedMessage = result.message
                        toast(context, result.message)
                        refresh()
                    }
                }
            } finally {
                // 仅当前这次上传结束时才清空（避免旧任务覆盖新任务的进度条）。
                if (uploadNonce == nonce) uploadJob = null
            }
        }
    }

    fun deleteDeck(deck: PptDeck) {
        scope.launch {
            when (val result = repository.softDelete(deck.id)) {
                is com.furina.classtools.data.network.ApiResult.Success -> {
                    toast(context, "已删除")
                    refresh()
                }

                is com.furina.classtools.data.network.ApiResult.Failure -> {
                    toast(context, result.error.displayMessage)
                }
            }
        }
    }

    fun retryDeck(deck: PptDeck) {
        // 上传中断：源文件不在本机，必须重新选文件（契约 §4.2「重新走 §4.2 全部片再 §4.3」）。
        if (deck.error.contains("上传中断")) {
            replaceDeckId = deck.id
            pickFile()
            return
        }
        scope.launch {
            when (val result = repository.retryConversion(deck.id)) {
                is com.furina.classtools.data.network.ApiResult.Success -> {
                    toast(context, "已重新排队")
                    refresh()
                }

                is com.furina.classtools.data.network.ApiResult.Failure -> {
                    toast(context, result.error.displayMessage)
                }
            }
        }
    }

    val uploading = uploadJob?.isActive == true
    val uploadBar = if (uploading) {
        PptUploadBar(title = uploadTitle, percent = uploadProgress.percent, deckId = uploadProgress.deckId)
    } else {
        null
    }

    PptListContent(
        decks = decks,
        agentOnline = agentOnline,
        mySid = mySid,
        imageLoader = imageLoader,
        loading = loading,
        errorMessage = errorMessage,
        offlineHint = offlineHint,
        uploadBar = uploadBar,
        uploadFailedMessage = uploadFailedMessage,
        resumeRecords = resumeRecords,
        onBack = onBack,
        onUpload = ::pickFile,
        onOpenDetail = onOpenDetail,
        onDelete = { deck -> deleteTarget = deck },
        onRetry = ::retryDeck,
        onCancelUpload = {
            uploadJob?.cancel()
            toast(context, "已取消上传")
        },
        onDismissUploadError = { uploadFailedMessage = null },
        onRefresh = { scope.launch { refresh() } },
        modifier = modifier,
    )

    // 可选改标题（契约 §8.1 上传 3 步中的中间步）。
    val source = pendingSource
    if (source != null) {
        PptTitleDialog(
            fileName = source.name,
            sizeBytes = source.size,
            title = titleDraft,
            onTitleChange = { titleDraft = it },
            onDismiss = {
                pendingSource = null
                replaceDeckId = null
            },
            onConfirm = { startUpload(titleDraft, source) },
        )
    }

    // 删除二次确认。
    deleteTarget?.let { deck ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除演示？") },
            text = { Text("「${deck.title.ifBlank { "未命名演示" }}」将从列表移除（软删除）。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    deleteDeck(deck)
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

// =============================================================================
// 内容层（无副作用；@Preview 直接复用）
// =============================================================================

@Composable
fun PptListContent(
    decks: List<PptDeck>,
    agentOnline: Boolean?,
    mySid: String?,
    imageLoader: PptImageLoader,
    loading: Boolean,
    errorMessage: String?,
    offlineHint: Boolean,
    uploadBar: PptUploadBar?,
    uploadFailedMessage: String?,
    resumeRecords: Map<String, PptResumeRecord> = emptyMap(),
    onBack: () -> Unit,
    onUpload: () -> Unit,
    onOpenDetail: (PptDeck) -> Unit,
    onDelete: (PptDeck) -> Unit,
    onRetry: (PptDeck) -> Unit,
    onCancelUpload: () -> Unit,
    onDismissUploadError: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleDecks = remember(decks, mySid) { PptDeckListLogic.visible(decks, mySid) }
    val showAgentOffline = PptStatusTexts.shouldShowAgentOfflineBanner(visibleDecks, agentOnline)
    val nowMs = remember { System.currentTimeMillis() }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            CtTopBar(
                title = "演示",
                onBack = onBack,
                actions = {
                    Button(
                        onClick = onUpload,
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("上传 PPT", style = MaterialTheme.typography.labelLarge)
                    }
                },
            )

            if (showAgentOffline) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WifiOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = "转换机离线 · 任务排队中",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }

            uploadFailedMessage?.let { message ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = onUpload) { Text("重试") }
                        IconButton(onClick = onDismissUploadError) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "关闭提示",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    loading && visibleDecks.isEmpty() && uploadBar == null -> PptListSkeleton()
                    errorMessage != null && visibleDecks.isEmpty() && uploadBar == null -> PptListError(
                        message = errorMessage,
                        onRetry = onRefresh,
                    )
                    visibleDecks.isEmpty() && uploadBar == null -> PptListEmpty(onUpload = onUpload)
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        uploadBar?.let { bar ->
                            item(key = "local-upload") {
                                PptDeckCard(
                                    deck = PptDeck(
                                        id = bar.deckId ?: "local-upload",
                                        title = bar.title,
                                        status = PptDeck.STATUS_UPLOADING,
                                        localUploadPercent = bar.percent,
                                    ),
                                    mySid = mySid,
                                    agentOnline = agentOnline,
                                    imageLoader = imageLoader,
                                    uploadPercent = bar.percent,
                                    onOpen = {},
                                    onDelete = null,
                                    onRetry = null,
                                    onCancelUpload = onCancelUpload,
                                )
                            }
                        }
                        items(visibleDecks, key = { it.id }) { deck ->
                            PptDeckCard(
                                deck = deck,
                                mySid = mySid,
                                agentOnline = agentOnline,
                                imageLoader = imageLoader,
                                showResume = PptResumeLogic.badgeVisible(resumeRecords[deck.id], nowMs),
                                onOpen = { onOpenDetail(deck) },
                                onDelete = if (PptDeckListLogic.canDelete(deck, mySid)) {
                                    { onDelete(deck) }
                                } else {
                                    null
                                },
                                onRetry = if (PptDeckListLogic.canRetry(deck, mySid)) {
                                    { onRetry(deck) }
                                } else {
                                    null
                                },
                            )
                        }
                        if (offlineHint) {
                            item(key = "offline-hint") {
                                Text(
                                    text = "离线：显示上次列表",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 卡片

@Composable
private fun PptDeckCard(
    deck: PptDeck,
    mySid: String?,
    agentOnline: Boolean?,
    imageLoader: PptImageLoader,
    uploadPercent: Int? = null,
    showResume: Boolean = false,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)?,
    onRetry: (() -> Unit)?,
    onCancelUpload: (() -> Unit)? = null,
) {
    val statusKind = PptStatusTexts.kindOf(deck.status)
    val uploading = statusKind == PptStatusTexts.Kind.UPLOADING && uploadPercent != null
    val percent = uploadPercent ?: deck.localUploadPercent ?: 0
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clip(CtShapeTokens.toolCard)
            .clickable { onOpen() },
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
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
                    url = deck.coverUrl,
                    loader = imageLoader,
                    modifier = Modifier.fillMaxSize(),
                    targetWidth = 400,
                    contentDescription = "",
                    fallback = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Slideshow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    },
                )
                if (uploading) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.Black.copy(alpha = 0.18f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((percent.coerceIn(0, 100) / 100f))
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = deck.title.ifBlank { "未命名演示" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // v1.2 续播徽标（契约 §11.3.4：7 天内 + 有进度）
                    if (showResume) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Text(
                                text = "继续",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                val meta = metaLine(deck)
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // v2.0 列表小标签（§12.4.1：「兼容版」/「PDF」，不写长句）
                val fileTags = PptDownloadLogic.listTags(deck)
                if (fileTags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        fileTags.forEach { tag -> PptListTag(tag) }
                    }
                }
                when {
                    statusKind == PptStatusTexts.Kind.READY -> Unit // 就绪不显示状态胶囊（DESIGN §4.1）
                    uploading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "上传中 $percent%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "取消",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onCancelUpload?.invoke() }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }

                    else -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PptStatusPill(
                            text = PptStatusTexts.text(deck.status, deck.progress, agentOnline),
                            failed = statusKind == PptStatusTexts.Kind.FAILED,
                        )
                        if (onRetry != null) {
                            Text(
                                text = "重试",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onRetry() }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                if (statusKind == PptStatusTexts.Kind.FAILED && deck.error.isNotBlank()) {
                    Text(
                        text = PptStatusTexts.errorLine(deck.error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (onDelete != null) {
                var menuOpen by remember(deck.id) { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "更多操作",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("删除") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PptStatusPill(text: String, failed: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (failed) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (failed) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        modifier = Modifier.height(22.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/** 下载中心列表小标签（兼容版 / PDF）。 */
@Composable
private fun PptListTag(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** 卡片元信息：就绪「12 页 · 陈子麒」（DESIGN §4.1）；上传占位没有上传者/页数 → 不显示。 */
private fun metaLine(deck: PptDeck): String {
    val owner = PptDeckListLogic.ownerLabel(deck).takeUnless { it == "未知同学" }.orEmpty()
    val pages = deck.slideCount
    return when {
        pages > 0 && owner.isNotEmpty() -> "$pages 页 · $owner"
        pages > 0 -> "$pages 页"
        else -> owner
    }
}

// ---------------------------------------------------------------- 状态视图

@Composable
private fun PptListSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(3) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(96.dp),
                shape = CtShapeTokens.toolCard,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .width(128.dp)
                            .height(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .width(180.dp)
                                .height(14.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                        Box(
                            Modifier
                                .width(120.dp)
                                .height(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PptListEmpty(onUpload: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Slideshow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = PptStatusTexts.EMPTY_TITLE,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = PptStatusTexts.EMPTY_HINT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = onUpload, shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("上传 PPT")
        }
    }
}

@Composable
private fun PptListError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("重试")
        }
    }
}

// ---------------------------------------------------------------- 标题编辑弹层

@Composable
private fun PptTitleDialog(
    fileName: String,
    sizeBytes: Long,
    title: String,
    onTitleChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("上传演示") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "$fileName · ${PptLimits.formatBytes(sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { onTitleChange(it.take(PptLimits.MAX_TITLE_CHARS)) },
                    label = { Text("标题（≤80 字）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }) { Text("开始上传") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// ---------------------------------------------------------------- 小工具

private fun toast(context: Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
}

private const val MIME_PPT = "application/vnd.ms-powerpoint"
private const val MIME_PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"

/** 供 MainNavHost 判断失败错误文案（上传中断 → 需重新选文件）。 */
internal val PPT_UPLOAD_INTERRUPTED = DefaultPptRepository.FAIL_UPLOAD_MESSAGE
