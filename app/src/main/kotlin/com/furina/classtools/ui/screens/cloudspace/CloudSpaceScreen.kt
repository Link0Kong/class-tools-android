package com.furina.classtools.ui.screens.cloudspace

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import com.furina.classtools.ui.components.light.CtLightAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.furina.classtools.data.cloudspace.CloudSpaceByteSource
import com.furina.classtools.data.cloudspace.CloudSpaceDefaults
import com.furina.classtools.data.cloudspace.CloudSpaceDownloadEvent
import com.furina.classtools.data.cloudspace.CloudSpaceDownloadTicket
import com.furina.classtools.data.cloudspace.CloudSpaceFile
import com.furina.classtools.data.cloudspace.CloudSpaceLogic
import com.furina.classtools.data.cloudspace.CloudSpaceRepository
import com.furina.classtools.data.cloudspace.CloudSpaceResult
import com.furina.classtools.data.cloudspace.CloudSpaceSession
import com.furina.classtools.data.cloudspace.CloudSpaceSnapshot
import com.furina.classtools.data.cloudspace.CloudSpaceUploadState
import com.furina.classtools.data.cloudspace.CloudSpaceUploadTask
import com.furina.classtools.data.cloudspace.CloudSpaceUriSource
import com.furina.classtools.data.cloudspace.OkHttpCloudSpaceDownloader
import com.furina.classtools.ui.adaptive.rememberCtAdaptiveSpec
import com.furina.classtools.ui.components.CtIconAction
import com.furina.classtools.ui.components.ctTopBarRaw
import com.furina.classtools.ui.components.EmptyState
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.tool.CtToolCard
import com.furina.classtools.ui.components.tool.CtToolSectionHeader
import com.furina.classtools.ui.theme.ClassToolsTheme
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShapeTokens
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 「文件传输 · 临时云空间」页（与网页 `#s-file` / 小程序 `pages/cloudspace` 同语义）。
 *
 * - 同账号（同班级 token）跨设备自动共享同一空间，无需房间码/链接/配对；
 * - 上传走 SAF 多选（任意大小，8MiB 分片，逐片进度）；
 * - 列表：大小 / 上传时间 / 剩余 TTL / 下载并打开 / 删除；支持「清空空间」（二次确认）；
 * - 文件自上传完成起保留 72 小时（3 天），服务端到期自动删除；
 * - 空态 / 错误态 / 加载态齐全；5s 静默轮询（页面隐藏即停，回前台立即拉）。
 *
 * 依赖注入（Lead 接线）：
 * ```kotlin
 * val repository = CloudSpaceRepository(
 *     CloudSpaceApi(baseUrl = CloudSpaceDefaults.BASE_URL, tokenProvider = { activeSession?.token }),
 * )
 * CloudSpaceScreen(onBack = { popTool("file") }, repository = repository)
 * ```
 * 班级 token 只由 `CloudSpaceApi` 放进请求头 `X-Device-Id`，本页不接触、不展示、不落盘。
 */
@Composable
fun CloudSpaceScreen(
    onBack: () -> Unit,
    repository: CloudSpaceRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val downloader = remember(context) { OkHttpCloudSpaceDownloader(context) }

    var snapshot by remember { mutableStateOf(repository.snapshot()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var uploads by remember { mutableStateOf<List<CloudSpaceUploadTask>>(emptyList()) }
    var downloadingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var downloadedFiles by remember { mutableStateOf<Map<String, CloudSpaceDownloadTicket>>(emptyMap()) }
    var deleteTarget by remember { mutableStateOf<CloudSpaceFile?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val uploadJobs = remember { mutableMapOf<String, Job>() }

    fun applyResult(result: CloudSpaceResult<CloudSpaceSnapshot>) {
        when (result) {
            is CloudSpaceResult.Success -> {
                snapshot = result.value
                errorMessage = null
            }

            is CloudSpaceResult.Failure -> errorMessage = result.error.displayMessage
        }
        loading = false
    }

    suspend fun load(force: Boolean) {
        // 首次进入（还没有会话）走 ensureSpace（/mine → /open → 全量列表）；之后按 force 刷新。
        val result = if (snapshot.session == null) {
            repository.ensureSpace()
        } else {
            repository.refresh(force = force)
        }
        applyResult(result)
    }

    // 可见轮询：进入页面立即拉一次，之后 5s 一轮；页面隐藏停止，回前台立即拉（与网页/小程序一致）。
    LaunchedEffect(repository) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                load(force = false)
                delay(CloudSpaceDefaults.POLL_INTERVAL_MS)
            }
        }
    }

    // 剩余 TTL 文案每 30s 更新一次（不额外发网络请求）。
    LaunchedEffect(Unit) {
        while (isActive) {
            nowMillis = System.currentTimeMillis()
            delay(30_000L)
        }
    }

    fun startUpload(source: CloudSpaceByteSource) {
        val taskId = "u" + System.nanoTime().toString(36)
        uploads = uploads + CloudSpaceUploadTask(
            id = taskId,
            name = CloudSpaceLogic.safeName(source.name),
            sizeBytes = source.size,
        )
        val job = scope.launch {
            val result = repository.upload(source) { progress ->
                uploads = uploads.map { task ->
                    if (task.id != taskId) {
                        task
                    } else {
                        task.copy(
                            sentBytes = progress.sentBytes,
                            state = progress.state,
                            message = progress.message,
                        )
                    }
                }
            }
            when (result) {
                is CloudSpaceResult.Success -> {
                    uploads = uploads.map { task ->
                        if (task.id == taskId) {
                            task.copy(
                                sentBytes = result.value.sizeBytes,
                                state = CloudSpaceUploadState.DONE,
                            )
                        } else {
                            task
                        }
                    }
                    snapshot = repository.snapshot()
                    toast(context, "已上传：${result.value.name}")
                }

                is CloudSpaceResult.Failure -> {
                    uploads = uploads.map { task ->
                        if (task.id == taskId && task.state != CloudSpaceUploadState.CANCELED) {
                            task.copy(
                                state = CloudSpaceUploadState.FAILED,
                                message = result.error.displayMessage,
                            )
                        } else {
                            task
                        }
                    }
                    toast(context, result.error.displayMessage)
                }
            }
            uploadJobs.remove(taskId)
        }
        uploadJobs[taskId] = job
    }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult // 用户取消
        scope.launch {
            var accepted = 0
            uris.forEach { uri ->
                val source = CloudSpaceUriSource.open(context, uri)
                if (source == null) {
                    toast(context, "无法读取所选文件，请重新选择")
                    return@forEach
                }
                startUpload(source)
                accepted += 1
            }
            if (accepted == 0) toast(context, "没有可上传的文件")
        }
    }

    fun manualRefresh() {
        scope.launch {
            applyResult(
                if (snapshot.session == null) repository.ensureSpace() else repository.refresh(force = true),
            )
        }
    }

    fun cancelUpload(taskId: String) {
        uploadJobs.remove(taskId)?.cancel()
        uploads = uploads.map { task ->
            if (task.id == taskId) {
                task.copy(state = CloudSpaceUploadState.CANCELED, message = "已取消")
            } else {
                task
            }
        }
    }

    fun openOrDownload(file: CloudSpaceFile) {
        val local = downloadedFiles[file.id]
        if (local != null && local.file.exists()) {
            val mime = CloudSpaceFileIntents.mimeTypeOf(local.mimeType, local.fileName)
            if (!CloudSpaceFileIntents.tryOpen(context, local.file, mime, local.fileName)) {
                toast(context, "已下载到本机，但未找到可打开该文件的应用")
            }
            return
        }
        if (file.id in downloadingIds) return
        downloadingIds = downloadingIds + file.id
        scope.launch {
            when (val fresh = repository.findFreshFile(file.id)) {
                is CloudSpaceResult.Failure -> {
                    toast(context, fresh.error.displayMessage)
                    applyResult(repository.refresh(force = true))
                }

                is CloudSpaceResult.Success -> {
                    val target = fresh.value
                    val mime = CloudSpaceFileIntents.mimeTypeOf(target.mimeType, target.name)
                    downloader.download(target.downloadUrl, target.name, mime).collect { event ->
                        when (event) {
                            is CloudSpaceDownloadEvent.Progress -> Unit
                            is CloudSpaceDownloadEvent.Done -> {
                                downloadedFiles = downloadedFiles + (file.id to event.ticket)
                                val opened = CloudSpaceFileIntents.tryOpen(
                                    context = context,
                                    file = event.ticket.file,
                                    mimeType = event.ticket.mimeType,
                                    title = event.ticket.fileName,
                                )
                                if (!opened) {
                                    toast(context, "已下载到本机，但未找到可打开该文件的应用")
                                }
                            }

                            is CloudSpaceDownloadEvent.Failed -> toast(context, event.message)
                        }
                    }
                    snapshot = repository.snapshot()
                }
            }
            downloadingIds = downloadingIds - file.id
        }
    }

    fun deleteFile(file: CloudSpaceFile) {
        scope.launch {
            when (val result = repository.delete(file.id)) {
                is CloudSpaceResult.Success -> {
                    snapshot = result.value
                    errorMessage = null
                    downloadedFiles = downloadedFiles - file.id
                    toast(context, "已删除")
                }

                is CloudSpaceResult.Failure -> toast(context, result.error.displayMessage)
            }
        }
    }

    fun clearSpace() {
        uploadJobs.values.forEach { it.cancel() }
        uploadJobs.clear()
        uploads = emptyList()
        scope.launch {
            when (val result = repository.clearSpace()) {
                is CloudSpaceResult.Success -> {
                    snapshot = result.value
                    errorMessage = null
                    downloadedFiles = emptyMap()
                    toast(context, "云空间已清空")
                }

                is CloudSpaceResult.Failure -> {
                    errorMessage = result.error.displayMessage
                    toast(context, result.error.displayMessage)
                }
            }
        }
    }

    CloudSpaceContent(
        snapshot = snapshot,
        loading = loading,
        errorMessage = errorMessage,
        uploads = uploads,
        downloadingIds = downloadingIds,
        downloadedIds = downloadedFiles.keys,
        nowMillis = nowMillis,
        onBack = onBack,
        onRefresh = ::manualRefresh,
        onUpload = { pickFiles.launch(arrayOf("*/*")) },
        onClear = { showClearConfirm = true },
        onDownload = ::openOrDownload,
        onDelete = { deleteTarget = it },
        onCancelUpload = ::cancelUpload,
        onRetry = ::manualRefresh,
        modifier = modifier,
    )

    deleteTarget?.let { file ->
        CtLightAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除文件？") },
            text = { Text("「${file.name}」将从云空间删除，且不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        deleteFile(file)
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    if (showClearConfirm) {
        CtLightAlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空云空间？") },
            text = { Text("将立即删除全部文件，且不可恢复。同账号其它设备也会同步清空。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        clearSpace()
                    },
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }
}

// =============================================================================
// 内容层（无副作用；@Preview 直接复用）
// =============================================================================

@Composable
fun CloudSpaceContent(
    snapshot: CloudSpaceSnapshot,
    loading: Boolean,
    errorMessage: String?,
    uploads: List<CloudSpaceUploadTask>,
    downloadingIds: Set<String>,
    downloadedIds: Set<String>,
    nowMillis: Long,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onUpload: () -> Unit,
    onClear: () -> Unit,
    onDownload: (CloudSpaceFile) -> Unit,
    onDelete: (CloudSpaceFile) -> Unit,
    onCancelUpload: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = rememberCtAdaptiveSpec()
    val statusText = when {
        loading -> "连接中…"
        !snapshot.connected -> "未连接"
        else -> "已连接 · 同账号共享"
    }
    // 列表为空时的错误用整块错误态（带重试）；已有缓存列表时只用顶部横幅提示。
    val blockingError = errorMessage.takeIf { snapshot.files.isEmpty() && !loading }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = spec.contentMaxWidth)
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                ctTopBarRaw(
                    subtitle = "同账号 · 跨设备 · 文件保留 3 天",
                    title = "临时云空间",
                    onBack = onBack,
                    actions = {
                        CtIconAction(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = "刷新云空间",
                            onClick = onRefresh,
                        )
                    },
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(
                        start = spec.horizontalPadding,
                        end = spec.horizontalPadding,
                        top = 6.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "space") {
                        CtToolCard(
                            title = "空间",
                            trailing = {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (snapshot.connected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            },
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatBlock(
                                    value = snapshot.count.toString(),
                                    label = "文件",
                                    modifier = Modifier.weight(1f),
                                )
                                StatBlock(
                                    value = snapshot.totalBytesText,
                                    label = "已占用",
                                    modifier = Modifier.weight(1f),
                                )
                                StatBlock(
                                    value = "3 天",
                                    label = "自动保留",
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = onUpload,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp),
                                    shape = CtShapeTokens.pill,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CloudUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "上传文件",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                TextButton(
                                    onClick = onClear,
                                    enabled = snapshot.count > 0,
                                    modifier = Modifier
                                        .heightIn(min = 52.dp)
                                        .padding(horizontal = 6.dp),
                                ) {
                                    Text(text = "清空空间", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    if (errorMessage != null && blockingError == null) {
                        item(key = "error") {
                            ErrorBanner(message = errorMessage, onRetry = onRetry)
                        }
                    }

                    if (uploads.isNotEmpty()) {
                        item(key = "uploads") {
                            UploadsCard(uploads = uploads, onCancelUpload = onCancelUpload)
                        }
                    }

                    item(key = "files-header") {
                        CtToolSectionHeader(
                            title = "文件",
                            subtitle = if (loading) {
                                "正在连接云空间…"
                            } else if (snapshot.count > 0) {
                                "共 ${snapshot.count} 个 · ${snapshot.totalBytesText}"
                            } else {
                                null
                            },
                        )
                    }

                    when {
                        loading && snapshot.files.isEmpty() -> item(key = "loading") {
                            CtSpaceLoading()
                        }

                        blockingError != null -> item(key = "error-state") {
                            ErrorStateContent(message = blockingError, onRetry = onRetry)
                        }

                        snapshot.files.isEmpty() -> item(key = "empty") {
                            EmptyState(
                                icon = Icons.Rounded.Cloud,
                                title = "还没有文件",
                                message = "点上方「上传文件」；同账号的电脑 / 手机 / 大屏可见同一批文件",
                            )
                        }

                        else -> items(items = snapshot.files, key = { it.id }) { file ->
                            FileRow(
                                file = file,
                                nowMillis = nowMillis,
                                downloading = file.id in downloadingIds,
                                downloaded = file.id in downloadedIds,
                                stackActions = spec.widthDp < 380,
                                onDownload = { onDownload(file) },
                                onDelete = { onDelete(file) },
                            )
                        }
                    }

                    if (snapshot.expiredCount > 0) {
                        item(key = "expired") {
                            Text(
                                text = CloudSpaceLogic.expiredHint(snapshot.expiredCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }

                    item(key = "note") {
                        CtToolCard(title = "说明") {
                            Text(
                                text = "文件自上传完成起保留 3 天（72 小时），到期自动删除；也可逐个删除或「清空空间」立即全部删除。" +
                                    "单文件不限大小，自动按 8 MiB 分片上传；下载链接每次刷新重新签发，${CloudSpaceDefaults.DOWNLOAD_URL_TTL_MINUTES} 分钟内有效。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CtSpaceLoading() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
        Text(
            text = "正在连接云空间…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorStateContent(
    message: String,
    onRetry: () -> Unit,
) {
    CtToolCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(34.dp),
            )
            Text(
                text = "云空间连接失败",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun StatBlock(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CtRadius.cardCompact))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CtRadius.cardCompact),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun UploadsCard(
    uploads: List<CloudSpaceUploadTask>,
    onCancelUpload: (String) -> Unit,
) {
    CtToolCard(
        title = "上传进度",
        trailing = {
            Text(
                text = "本次 ${uploads.size} 个",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            uploads.forEach { task ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = task.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${task.percent}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    UploadProgressBar(percent = task.percent)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${task.sizeText} · ${task.stateText}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (task.state == CloudSpaceUploadState.FAILED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val active = task.state == CloudSpaceUploadState.WAITING ||
                            task.state == CloudSpaceUploadState.UPLOADING
                        if (active) {
                            TextButton(
                                onClick = { onCancelUpload(task.id) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text("取消", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadProgressBar(
    percent: Int,
    modifier: Modifier = Modifier,
) {
    val fraction = (percent.coerceIn(0, 100)) / 100f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun FileRow(
    file: CloudSpaceFile,
    nowMillis: Long,
    downloading: Boolean,
    downloaded: Boolean,
    stackActions: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CtRadius.card),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(CtRadius.input))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = fileKindIcon(CloudSpaceLogic.fileKind(file.mimeType, file.name)),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${file.sizeText} · ${file.timeText} · ${file.ttlText(nowMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!stackActions) {
                    FileActions(
                        downloading = downloading,
                        downloaded = downloaded,
                        onDownload = onDownload,
                        onDelete = onDelete,
                    )
                }
            }
            if (stackActions) {
                FileActions(
                    downloading = downloading,
                    downloaded = downloaded,
                    onDownload = onDownload,
                    onDelete = onDelete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.End,
                )
            }
        }
    }
}

@Composable
private fun FileActions(
    downloading: Boolean,
    downloaded: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(4.dp),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onDownload,
            enabled = !downloading,
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            if (downloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = if (downloaded) "打开" else "下载",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        TextButton(
            onClick = onDelete,
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Text(
                text = "删除",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun fileKindIcon(kind: CloudSpaceLogic.FileKind): ImageVector = when (kind) {
    CloudSpaceLogic.FileKind.IMAGE -> Icons.Rounded.Image
    CloudSpaceLogic.FileKind.VIDEO -> Icons.Rounded.Movie
    CloudSpaceLogic.FileKind.AUDIO -> Icons.Rounded.MusicNote
    CloudSpaceLogic.FileKind.ARCHIVE -> Icons.Rounded.Folder
    CloudSpaceLogic.FileKind.PDF -> Icons.Rounded.PictureAsPdf
    CloudSpaceLogic.FileKind.DOCUMENT -> Icons.Rounded.Description
    CloudSpaceLogic.FileKind.OTHER -> Icons.Rounded.InsertDriveFile
}

private fun toast(context: Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
}

// =============================================================================
// Preview（固定时钟；深浅色各一张）
// =============================================================================

private const val CLOUDSPACE_PREVIEW_NOW: Long = 1_793_930_400_000L // 2026-11-06 10:00 +08

private fun previewSnapshot(): CloudSpaceSnapshot = CloudSpaceSnapshot(
    session = CloudSpaceSession(
        id = "preview-session",
        createdAt = CLOUDSPACE_PREVIEW_NOW - 3_600_000L,
        expiresAt = CLOUDSPACE_PREVIEW_NOW + 72L * 3_600_000L,
    ),
    files = listOf(
        CloudSpaceFile(
            id = "preview-1",
            name = "班会材料.pdf",
            sizeBytes = 2_411_724L,
            mimeType = "application/pdf",
            downloadUrl = "https://example.invalid/preview-1",
            uploadedAt = CLOUDSPACE_PREVIEW_NOW - 3L * 3_600_000L,
        ),
        CloudSpaceFile(
            id = "preview-2",
            name = "活动照片.jpg",
            sizeBytes = 8_912_345L,
            mimeType = "image/jpeg",
            downloadUrl = "https://example.invalid/preview-2",
            uploadedAt = CLOUDSPACE_PREVIEW_NOW - 30L * 3_600_000L,
        ),
        CloudSpaceFile(
            id = "preview-3",
            name = "复习资料汇总.zip",
            sizeBytes = 53_411_724L,
            mimeType = "application/zip",
            downloadUrl = "https://example.invalid/preview-3",
            uploadedAt = CLOUDSPACE_PREVIEW_NOW - 60L * 3_600_000L,
        ),
    ),
    expiredCount = 1,
)

private val previewUploads = listOf(
    CloudSpaceUploadTask(
        id = "u1",
        name = "实验报告.docx",
        sizeBytes = 1_248_576L,
        sentBytes = 786_432L,
        state = CloudSpaceUploadState.UPLOADING,
    ),
    CloudSpaceUploadTask(
        id = "u2",
        name = "班会记录.mp4",
        sizeBytes = 42_467_328L,
        sentBytes = 42_467_328L,
        state = CloudSpaceUploadState.DONE,
    ),
)

@Composable
private fun CloudSpacePreviewContent(darkTheme: Boolean, failed: Boolean) {
    ClassToolsTheme(darkTheme = darkTheme, dynamicColor = false) {
        CloudSpaceContent(
            snapshot = previewSnapshot(),
            loading = false,
            errorMessage = if (failed) "云空间服务不可达，请检查网络后重试" else null,
            uploads = previewUploads,
            downloadingIds = emptySet(),
            downloadedIds = emptySet(),
            nowMillis = CLOUDSPACE_PREVIEW_NOW,
            onBack = {},
            onRefresh = {},
            onUpload = {},
            onClear = {},
            onDownload = {},
            onDelete = {},
            onCancelUpload = {},
            onRetry = {},
        )
    }
}

@Preview(name = "云空间-浅色", showBackground = true, widthDp = 400, heightDp = 900)
@Composable
private fun CloudSpacePreviewLight() {
    CloudSpacePreviewContent(darkTheme = false, failed = false)
}

@Preview(name = "云空间-深色", showBackground = true, widthDp = 400, heightDp = 900)
@Composable
private fun CloudSpacePreviewDark() {
    CloudSpacePreviewContent(darkTheme = true, failed = true)
}
