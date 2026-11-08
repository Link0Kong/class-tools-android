package com.furina.classtools.ui.screens.notices

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.parseJsonOrNull
import java.net.URLEncoder
import java.util.Base64
import kotlinx.coroutines.Dispatchers
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

private sealed interface ThumbState {
    data object Loading : ThumbState
    data object Failed : ThumbState
    data class Ready(val image: ImageBitmap, val dataUri: String) : ThumbState
}

/**
 * 附件缩略图（详情内嵌）：
 * - [loader] 为 null / 无数据 / 解码失败 → 元信息行 +「暂不支持预览」（不崩、不联网图库）；
 * - 成功 → 缩略图 + 说明；点击打开全屏大图（按需解码，最大边 1800px）。
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
) {
    var state by remember(rowId, ordinal, manual, loader) {
        mutableStateOf<ThumbState>(if (loader == null) ThumbState.Failed else ThumbState.Loading)
    }
    var showFull by remember { mutableStateOf(false) }
    if (loader != null) {
        LaunchedEffect(loader, rowId, ordinal, manual) {
            state = ThumbState.Loading
            val value = runCatching { loader.load(rowId, ordinal, manual) }.getOrNull()
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
            shape = RoundedCornerShape(12.dp),
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
            detail = listOf(caption, "无法预览 · 暂不支持预览").filter { it.isNotBlank() }.joinToString(" · "),
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
                    .clip(RoundedCornerShape(12.dp))
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
                    dataUri = current.dataUri,
                    title = title,
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
 * 全屏图片预览（按需解码；失败降级文案，不阻塞页面）。
 * 使用黑色背景 + 自适应 Fit，点击右上角关闭/点背景关闭。
 */
@Composable
fun NoticeImageFullDialog(
    dataUri: String?,
    title: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xEE000000),
                contentColor = Color.White,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    var image by remember(dataUri) { mutableStateOf<ImageBitmap?>(null) }
                    var failed by remember(dataUri) { mutableStateOf(false) }
                    LaunchedEffect(dataUri) {
                        val value = dataUri
                        if (value == null) {
                            failed = true
                            return@LaunchedEffect
                        }
                        val decoded = withContext(Dispatchers.IO) {
                            NoticeImageDecoder.decodeSampled(value, NoticeImageDecoder.FULL_MAX_DIMENSION)
                        }
                        image = decoded
                        failed = decoded == null
                    }
                    when {
                        image != null -> Image(
                            bitmap = image!!,
                            contentDescription = title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                        )

                        failed -> Text(
                            text = "无法预览 · $title",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                        )

                        else -> CircularProgressIndicator(color = Color.White)
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭大图", tint = Color.White)
                    }
                }
            }
        }
    }
}




