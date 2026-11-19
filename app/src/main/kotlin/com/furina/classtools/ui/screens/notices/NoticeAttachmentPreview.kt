package com.furina.classtools.ui.screens.notices

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.parseJsonOrNull
import com.furina.classtools.data.notices.NoticeArchiveFile
import com.furina.classtools.data.notices.NoticeAttachmentFileResult
import com.furina.classtools.data.notices.NoticeAttachmentOpen
import com.furina.classtools.data.notices.NoticeAttachmentSource
import com.furina.classtools.data.notices.NoticeItemMeta
import com.furina.classtools.data.ppt.OkHttpPptImageLoader
import com.furina.classtools.data.ppt.PptImageLoader
import com.furina.classtools.ui.theme.CtPalette
import com.furina.classtools.ui.theme.CtRadius
import java.io.File
import java.net.URLEncoder
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 通知附件「按需预览」支持（`img` 附件缩略图 + 点击看大图）。
 *
 * 背景：`NoticeBodyParser` / `NoticesRepository` 在列表阶段会清掉 `row.body` / `row.attachments`
 * 与 items 里的 base64（避免 MB 级内存常驻）。因此详情预览必须**按需**重新拉取单行原始数据：
 *
 * 1. [NoticeRawAttachmentLoader]：行 id + 序号 → 原始 data URI（可注入假实现做预览/测试）；
 * 2. [ApiNoticeRawAttachmentLoader]：`GET notices?select=id,body,attachments&id=eq.<id>` 后解析；
 * 3. [NoticeAttachmentParser]：纯解析（自动通知 items 的图片 data URI / 人工通知 attachments），可单测；
 * 4. [NoticeImageDecoder]：data URI → Bitmap，**先 bounds 采样**再 decode（缩略 480px / 大图 1800px）；
 * 5. 解码失败/无数据 → 降级为「无法预览 · 名称」；未知 kind 显示「暂不支持预览」。
 */
fun interface NoticeRawAttachmentLoader {
    /**
     * 按需读取附件原始 data URI。
     *
     * @param rowId 通知行 id（`auto_<gid>` 或人工通知 id）
     * @param ordinal 自动通知 = 第 N 张图片（按 items 中 `img` 出现顺序，0-based）；
     *                人工通知 = `attachments` 数组下标
     * @param manual 是否人工通知附件
     * @return `data:image/...;base64,...` 或普通 URL；无内容返回 null
     */
    suspend fun load(rowId: String, ordinal: Int, manual: Boolean): String?
}

/** 网络实现：按需拉单行原始 body/attachments（不缓存，用完即弃）。 */
class ApiNoticeRawAttachmentLoader(
    private val api: ApiClient,
    private val json: Json = NetJson,
) : NoticeRawAttachmentLoader {

    override suspend fun load(rowId: String, ordinal: Int, manual: Boolean): String? {
        if (rowId.isBlank() || ordinal < 0) return null
        val encodedId = runCatching { URLEncoder.encode(rowId, "UTF-8") }.getOrElse { return null }
        val path = "notices?select=id,body,attachments&id=eq.$encodedId"
        val raw = when (val result = api.get(path)) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return null
        }
        val row = NoticeAttachmentParser.parseRow(raw, json) ?: return null
        return if (manual) {
            NoticeAttachmentParser.extractManualData(row.attachments, ordinal, json)
        } else {
            NoticeAttachmentParser.extractAutoImageData(row.body, ordinal, json)
        }
    }
}

/**
 * 附件原始数据纯解析（可单测；不依赖 Android Bitmap）。
 */
object NoticeAttachmentParser {

    const val BODY_PREFIX: String = "[[CLASS_NOTICE_V1]]"

    /** 单行原始数据（`body` 为自动通知组 JSON，`attachments` 为人工附件 JSON 字符串）。 */
    data class RawRow(
        val body: String?,
        val attachments: String?,
    )

    /** 解析 `notices?select=id,body,attachments&id=eq.x` 响应（数组/对象都兼容）。 */
    fun parseRow(raw: String?, json: Json = NetJson): RawRow? {
        if (raw.isNullOrBlank()) return null
        val root = parseJsonOrNull(json, raw) ?: return null
        val row: JsonObject = when (root) {
            is JsonArray -> root.firstOrNull() as? JsonObject ?: return null
            is JsonObject -> root
            else -> return null
        }
        return RawRow(
            body = row["body"].plainString(),
            attachments = row["attachments"].plainString(),
        )
    }

    /** 自动通知：第 [ordinal] 张 `img` 的 data URI / URL；无则 null。 */
    fun extractAutoImageData(body: String?, ordinal: Int, json: Json = NetJson): String? {
        if (body.isNullOrBlank() || ordinal < 0) return null
        val text = body.trimStart().removePrefix(BODY_PREFIX).trim()
        if (text.isEmpty()) return null
        val group = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val items = group["items"] as? JsonArray ?: return null
        val images = items.mapNotNull { item -> item as? JsonObject }
            .filter { (it["kind"] as? JsonPrimitive)?.content == "img" }
        val item = images.getOrNull(ordinal) ?: return null
        return item["src"].plainString()?.takeIf { it.isNotBlank() }
            ?: item["url"].plainString()?.takeIf { it.isNotBlank() }
    }

    /** 人工通知：`attachments[index].d`（data URI / URL）；无则 null。 */
    fun extractManualData(attachments: String?, ordinal: Int, json: Json = NetJson): String? {
        if (attachments.isNullOrBlank() || ordinal < 0) return null
        val array = parseJsonOrNull(json, attachments) as? JsonArray ?: return null
        val item = array.getOrNull(ordinal) as? JsonObject ?: return null
        return item["d"].plainString()?.takeIf { it.isNotBlank() }
            ?: item["url"].plainString()?.takeIf { it.isNotBlank() }
    }

    /** 是否内联图片 data URI（`data:image/...;base64,`）。 */
    fun isImageDataUri(value: String?): Boolean {
        val text = value?.trim().orEmpty()
        if (!text.startsWith("data:", ignoreCase = true)) return false
        val mime = mimeOf(text) ?: return false
        return mime.startsWith("image/", ignoreCase = true) && text.contains(";base64,", ignoreCase = true)
    }

    /** data URI 的 MIME（`data:image/png;base64,...` → `image/png`）；非法返回 null。 */
    fun mimeOf(value: String?): String? {
        val text = value?.trim().orEmpty()
        if (!text.startsWith("data:", ignoreCase = true)) return null
        val headerEnd = text.indexOf(',')
        if (headerEnd <= 5) return null
        val header = text.substring(5, headerEnd)
        return header.substringBefore(';').takeIf { it.isNotBlank() }
    }

    /** 附件最大原始字节数（与小程序/契约一致：10MB 上限，防 OOM）。 */
    const val MAX_ATTACHMENT_BYTES: Int = 10 * 1024 * 1024

    /** data URI → 原始字节（base64 解码；测试可 mock 小图）。非 base64 data URI 返回 null。 */
    fun decodeBase64Payload(value: String?): ByteArray? {
        val text = value?.trim().orEmpty()
        if (!isImageDataUri(text)) return null
        val comma = text.indexOf(',')
        if (comma < 0) return null
        val payload = text.substring(comma + 1)
        if (payload.isEmpty()) return null
        // base64 字符数上限 ≈ 原始字节 * 4/3（提前拒绝，避免先分配大 ByteArray）
        if (payload.length > MAX_ATTACHMENT_BYTES / 3 * 4 + 4) return null
        val bytes = runCatching { Base64.getMimeDecoder().decode(payload) }.getOrNull() ?: return null
        if (bytes.size > MAX_ATTACHMENT_BYTES) return null
        return bytes
    }

    /** 采样倍数：`max(width,height)/sample <= maxDimension` 的最小 2 的幂。 */
    fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
        if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
        var sample = 1
        while (maxOf(width, height) / sample > maxDimension) {
            sample *= 2
        }
        return sample
    }

    private fun kotlinx.serialization.json.JsonElement?.plainString(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content
}

/** data URI → Bitmap（带采样；必须在 IO 线程调用）。 */
object NoticeImageDecoder {

    /** 列表缩略图最大边（px）。 */
    const val THUMB_MAX_DIMENSION: Int = 480

    /** 大图预览最大边（px）。 */
    const val FULL_MAX_DIMENSION: Int = 1800

    fun decodeSampled(value: String?, maxDimension: Int = THUMB_MAX_DIMENSION): ImageBitmap? {
        val bytes = NoticeAttachmentParser.decodeBase64Payload(value) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = NoticeAttachmentParser.sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap: Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return bitmap.asImageBitmap()
    }
}

/**
 * 网络图片加载器兜底单例（调用方未注入 `container.pptImageLoader` 时使用，如 IDE 预览）。
 * 复用 `data/ppt` 的 OkHttp + BitmapFactory + LRU 实现，不引入 Coil 等新依赖。
 */
private object NoticeNetworkImageLoader {
    val shared: PptImageLoader by lazy { OkHttpPptImageLoader() }
}

private sealed interface ThumbState {
    data object Loading : ThumbState
    data object Failed : ThumbState

    /** @param source 原始内容（data URI 或签名 URL；点击大图时按原形态再加载） */
    data class Ready(val image: ImageBitmap, val source: String) : ThumbState
}

/**
 * 附件缩略图（详情内嵌）：
 * - [url] 为外置签名 URL（新数据）时走 [imageLoader] 网络加载（内存 LRU 复用，失败占位不崩）；
 * - 否则按需通过 [loader] 拉原始 data URI（旧数据，行为不变）；
 * - [loader] 为 null / 无数据 / 解码失败 → 元信息行 +「暂不支持预览」（不崩、不联网图库）；
 * - 成功 → 缩略图 + 说明；点击打开全屏大图（URL 走同一加载器，data URI 最大边 1800px）。
 */
@Composable
fun NoticeAttachmentThumb(
    loader: NoticeRawAttachmentLoader?,
    rowId: String,
    ordinal: Int,
    manual: Boolean,
    title: String,
    caption: String = "",
    modifier: Modifier = Modifier,
    /** 外置签名 URL（空 = 旧数据 data URI 分支）。 */
    url: String = "",
    /** 网络图片加载器（生产传 `container.pptImageLoader`；null 用内置单例兜底）。 */
    imageLoader: PptImageLoader? = null,
) {
    var state by remember(rowId, ordinal, manual, loader, url, imageLoader) {
        mutableStateOf<ThumbState>(if (loader == null && url.isBlank()) ThumbState.Failed else ThumbState.Loading)
    }
    var showFull by remember { mutableStateOf(false) }
    if (loader != null || url.isNotBlank()) {
        LaunchedEffect(loader, rowId, ordinal, manual, url, imageLoader) {
            state = ThumbState.Loading
            if (url.isNotBlank()) {
                val network = imageLoader ?: NoticeNetworkImageLoader.shared
                val remote = runCatching {
                    network.load(url, PptImageLoader.THUMB_TARGET_WIDTH)
                }.getOrNull()
                state = if (remote == null) ThumbState.Failed else ThumbState.Ready(remote, url)
                return@LaunchedEffect
            }
            val value = runCatching { loader?.load(rowId, ordinal, manual) }.getOrNull()
            if (!NoticeAttachmentParser.isImageDataUri(value)) {
                state = ThumbState.Failed
                return@LaunchedEffect
            }
            val image = withContext(Dispatchers.IO) {
                NoticeImageDecoder.decodeSampled(value, NoticeImageDecoder.THUMB_MAX_DIMENSION)
            }
            state = if (image == null) ThumbState.Failed else ThumbState.Ready(image, value.orEmpty())
        }
    }

    when (val current = state) {
        ThumbState.Loading -> Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CtRadius.tile),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = "图片加载中…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ThumbState.Failed -> AttachmentFallback(
            title = title,
            detail = listOf(caption, "图片加载失败").filter { it.isNotBlank() }.joinToString(" · "),
            modifier = modifier,
        )

        is ThumbState.Ready -> Column(modifier = modifier.fillMaxWidth()) {
            Image(
                bitmap = current.image,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .clip(RoundedCornerShape(CtRadius.tile))
                    .clickable { showFull = true },
            )
            Text(
                text = listOf(caption.ifBlank { title }, "点击查看大图").filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (showFull) {
                NoticeImageFullDialog(
                    source = current.source,
                    title = title,
                    imageLoader = imageLoader,
                    onDismiss = { showFull = false },
                )
            }
        }
    }
}

/** 解码失败/无内容时的元信息占位（与列表 MetaRow 风格一致）。 */
@Composable
fun AttachmentFallback(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.AttachFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 全屏图片预览（按需加载；失败降级文案，不阻塞页面）。
 *
 * @param source data URI（旧数据）或 http(s) 签名 URL（新数据）
 * @param imageLoader 网络图片加载器（URL 形态用；null 用内置单例兜底）
 */
@Composable
fun NoticeImageFullDialog(
    source: String?,
    title: String,
    onDismiss: () -> Unit,
    imageLoader: PptImageLoader? = null,
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = CtPalette.MediaScrimWeak,
                contentColor = CtPalette.MediaOnScrim,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    var image by remember(source, imageLoader) { mutableStateOf<ImageBitmap?>(null) }
                    var failed by remember(source, imageLoader) { mutableStateOf(false) }
                    // N-08：全屏大图支持双指缩放/拖动/双击复位；单击背景关闭。
                    var scale by remember(source) { mutableFloatStateOf(1f) }
                    var offset by remember(source) { mutableStateOf(Offset.Zero) }
                    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                        scale = (scale * zoomChange).coerceIn(1f, 4f)
                        offset = if (scale > 1.01f) offset + panChange else Offset.Zero
                    }
                    LaunchedEffect(source, imageLoader) {
                        val value = source
                        if (value.isNullOrBlank()) {
                            failed = true
                            return@LaunchedEffect
                        }
                        val loaded = if (NoticeAttachmentSource.isHttpUrl(value)) {
                            runCatching {
                                (imageLoader ?: NoticeNetworkImageLoader.shared)
                                    .load(value, NoticeImageDecoder.FULL_MAX_DIMENSION)
                            }.getOrNull()
                        } else {
                            withContext(Dispatchers.IO) {
                                NoticeImageDecoder.decodeSampled(value, NoticeImageDecoder.FULL_MAX_DIMENSION)
                            }
                        }
                        image = loaded
                        failed = loaded == null
                    }
                    when {
                        image != null -> Image(
                            bitmap = image!!,
                            contentDescription = title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                                .transformable(transformState)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (scale > 1.01f) {
                                                scale = 1f
                                                offset = Offset.Zero
                                            } else {
                                                scale = 2.5f
                                            }
                                        },
                                    )
                                },
                        )

                        failed -> Text(
                            text = "无法预览 · $title",
                            style = MaterialTheme.typography.bodyLarge,
                            color = CtPalette.MediaOnScrim,
                        )

                        else -> CircularProgressIndicator(color = CtPalette.MediaOnScrim)
                    }
                    if (image != null) {
                        Text(
                            text = "双指缩放 · 双击复位",
                            style = MaterialTheme.typography.labelSmall,
                            color = CtPalette.MediaOnScrim.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 28.dp),
                        )
                    }
                    // 外置签名 URL 的图片可分享链接（data URI 旧数据不分享）。
                    val shareUrl = source?.takeIf { NoticeAttachmentSource.isHttpUrl(it) }
                    if (shareUrl != null) {
                        IconButton(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                                }
                                context.startActivity(Intent.createChooser(send, "分享图片链接"))
                            },
                            modifier = Modifier.align(Alignment.TopStart),
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = "分享图片链接", tint = CtPalette.MediaOnScrim)
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭大图", tint = CtPalette.MediaOnScrim)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 文件附件：外置签名 URL（新数据）/ data URI（旧数据）→ 缓存文件 → 系统打开
// ---------------------------------------------------------------------------

/** 单个附件的打开进度（每个文件行独立状态，互不影响）。 */
private sealed interface AttachmentOpenState {
    data object Idle : AttachmentOpenState
    data object Preparing : AttachmentOpenState
    data class Opened(val file: File) : AttachmentOpenState
    data class Error(val message: String) : AttachmentOpenState
}

/**
 * 对外通用「可打开附件行」（人工通知附件 `attachments[].url` 等非 items 场景复用）。
 *
 * @param source http(s) 签名 URL（外置）或 data URI（旧数据）
 */
@Composable
fun NoticeUrlAttachmentRow(
    title: String,
    detail: String,
    source: String,
    fileName: String,
    modifier: Modifier = Modifier,
) {
    AttachmentOpenRow(
        title = title,
        detail = detail,
        source = source,
        fileName = fileName,
        modifier = modifier,
    )
}

/**
 * 文件附件行（`NoticeItemMeta.File`）：
 * - `url` 为 http(s) 签名 URL → 点击下载到 `cacheDir/notice_attachments/` 再用系统应用打开（失败可重试）；
 * - 旧数据内联 data URI / 「待获取」→ 保持元信息展示（行为不变）；
 * - `archive.files[]` 有各自 URL 时逐项可打开，无 URL 时只展示名称/类型/大小。
 */
@Composable
fun NoticeFileAttachmentRow(
    meta: NoticeItemMeta.File,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (meta.url.isNotBlank()) {
            AttachmentOpenRow(
                title = meta.name,
                detail = fileDetailText(meta),
                source = meta.url,
                fileName = meta.name,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            AttachmentFallback(
                title = meta.name,
                detail = fileDetailText(meta),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (meta.archiveFiles.isNotEmpty()) {
            Text(
                text = listOf(
                    "压缩包内容 · ${meta.archiveCount.ifZero(meta.archiveFiles.size)} 个文件",
                    meta.archiveSummary.take(60),
                ).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
            meta.archiveFiles.forEach { file ->
                if (file.url.isNotBlank()) {
                    AttachmentOpenRow(
                        title = file.name,
                        detail = archiveFileDetailText(file),
                        source = file.url,
                        fileName = file.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp),
                    )
                } else {
                    AttachmentFallback(
                        title = file.name,
                        detail = archiveFileDetailText(file),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

/**
 * 「点击 → 准备（下载/解码到缓存）→ 系统打开」的附件行。
 *
 * @param source http(s) 签名 URL（外置）或 data URI（旧数据）
 */
@Composable
private fun AttachmentOpenRow(
    title: String,
    detail: String,
    source: String,
    fileName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember(source) { mutableStateOf<AttachmentOpenState>(AttachmentOpenState.Idle) }
    val preparing = state is AttachmentOpenState.Preparing

    fun startOpen() {
        if (preparing) return
        state = AttachmentOpenState.Preparing
        scope.launch {
            val result = NoticeAttachmentOpen.prepare(context, source, fileName)
            state = when (result) {
                is NoticeAttachmentFileResult.Ok -> {
                    if (NoticeAttachmentOpen.open(context, result.file, result.mime)) {
                        AttachmentOpenState.Opened(result.file)
                    } else {
                        AttachmentOpenState.Error("没有可打开该附件的应用")
                    }
                }

                is NoticeAttachmentFileResult.TooLarge -> AttachmentOpenState.Error(
                    "附件超过 ${NoticeAttachmentSource.formatSize(result.limitBytes)}，暂不支持打开",
                )

                is NoticeAttachmentFileResult.Failed -> AttachmentOpenState.Error(result.message)
                is NoticeAttachmentFileResult.Unsupported -> AttachmentOpenState.Error(result.message)
            }
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(CtRadius.tile))
            .clickable(enabled = !preparing) { startOpen() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.AttachFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val stateText = when (val current = state) {
                AttachmentOpenState.Idle -> ""
                AttachmentOpenState.Preparing -> "下载中…"
                is AttachmentOpenState.Opened -> "已打开"
                is AttachmentOpenState.Error -> current.message
            }
            val line = listOf(detail, stateText).filter { it.isNotBlank() }.joinToString(" · ")
            if (line.isNotBlank()) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state is AttachmentOpenState.Error) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = when (state) {
                AttachmentOpenState.Preparing -> "下载中…"
                is AttachmentOpenState.Opened -> "重新打开"
                else -> "打开"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

private fun fileDetailText(meta: NoticeItemMeta.File): String = buildList {
    if (meta.ext.isNotBlank()) add(meta.ext.uppercase())
    add(meta.sizeText)
    if (meta.pending) add("待获取")
    if (meta.note.isNotBlank()) add(meta.note)
    if (meta.url.isBlank() && meta.archiveFiles.isEmpty()) add("暂不支持预览")
}.filter { it.isNotBlank() }.joinToString(" · ")

private fun archiveFileDetailText(file: NoticeArchiveFile): String = listOf(
    file.kind,
    file.sizeText,
    if (file.hasData) "内联附件" else "",
).filter { it.isNotBlank() }.joinToString(" · ")

private fun Int.ifZero(fallback: Int): Int = if (this > 0) this else fallback
