package com.furina.classtools.data.ppt

import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.parseJsonOrNull
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * PPT 演示响应解析（契约 §4/§5）。**容错优先**：未知字段忽略、缺字段默认、坏行跳过。
 *
 * 说明：本文件只做「云端 JSON → 模型」；URL 解析与播放语义在 `features/ppt`。
 * 纯 Kotlin（无 android.*）→ JVM 单测覆盖正常/缺字段/陌生字段/坏 JSON。
 */
object PptParsers {

    /** notes 客户端上限（转换机截断 2000 字；这里再兜底防坏数据撑爆 UI）。 */
    const val MAX_NOTES_CHARS: Int = 8_000

    /** error 列上限（契约 §3.1 ≤300 字）。 */
    const val MAX_ERROR_CHARS: Int = 300

    /** 标题上限（契约 §3.1 ≤80 字）。 */
    const val MAX_TITLE_CHARS: Int = 80

    /** 兼容提示条数上限（防坏数据撑爆详情页）。 */
    const val MAX_WARNINGS: Int = 20

    /** 单条兼容提示字数上限。 */
    const val MAX_WARNING_CHARS: Int = 300

    /** v2.3 单独下载媒体条数上限（转换机 20，客户端防坏数据放宽到 50）。 */
    const val MAX_MEDIA_FILES: Int = 50

    /** v2.3 单个媒体关联页号数量上限。 */
    const val MAX_MEDIA_SLIDES: Int = 20

    /** v2.3 音频扩展名（kind 未知时按扩展名推断）。 */
    private val AUDIO_EXTENSIONS: Set<String> = setOf("m4a", "mp3", "aac", "wav", "ogg", "flac", "wma", "ac3")

    // ------------------------------------------------------------ 列表（§4.4）

    /** 解析列表响应（数组；单对象也兼容）。坏行跳过，不抛异常。 */
    fun parseDeckList(json: Json = NetJson, raw: String?): List<PptDeck> {
        val root = parseJsonOrNull(json, raw) ?: return emptyList()
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> JsonArray(listOf(root))
            else -> return emptyList()
        }
        return array.mapNotNull { item -> (item as? JsonObject)?.let { parseDeckRow(it) } }
    }

    /** 解析单行（id 为空视为坏行跳过）。 */
    fun parseDeckRow(row: JsonObject): PptDeck? {
        val id = stringOf(row["id"]).trim()
        if (id.isEmpty()) return null
        val fileFlags = parseFileFlags(row)
        return PptDeck(
            id = id,
            title = stringOf(row["title"]).trim().take(MAX_TITLE_CHARS),
            ownerSid = stringOf(row["owner_sid"]).trim(),
            ownerName = stringOf(row["owner_name"]).trim(),
            status = stringOf(row["status"]).trim().lowercase().ifBlank { PptDeck.STATUS_UPLOADING },
            progress = intOf(row["progress"])?.coerceIn(0, 100) ?: 0,
            error = stringOf(row["error"]).trim().take(MAX_ERROR_CHARS),
            slideCount = intOf(row["slide_count"])?.coerceAtLeast(0) ?: 0,
            stepCount = intOf(row["step_count"])?.coerceAtLeast(0) ?: 0,
            aspect = stringOf(row["aspect"]).trim(),
            coverUrl = stringOf(row["cover_url"]).trim(),
            sourceName = stringOf(row["source_name"]).trim(),
            sourceSize = longOf(row["source_size"])?.coerceAtLeast(0) ?: 0L,
            createdAt = longOf(row["created_at"]) ?: 0L,
            convertedAt = longOf(row["converted_at"]) ?: 0L,
            client = stringOf(row["client"]).trim(),
            hasCompatFiles = fileFlags.first,
            hasPdfFile = fileFlags.second,
        )
    }

    /**
     * 列表小标签数据源（§12.4.1）：列表查询用 jsonb 路径只取 `manifest->files` 轻量字段。
     * 兼容直接返回完整 manifest 的网络响应（POST 建行/缓存）。
     */
    private fun parseFileFlags(row: JsonObject): Pair<Boolean, Boolean> {
        val files = (row["files"] as? JsonObject)
            ?: ((row["manifest"] as? JsonObject)?.get("files") as? JsonObject)
            ?: return false to false
        val compat = files["compat"] as? JsonObject
        val pdf = files["pdf"] as? JsonObject
        return (compat != null && stringOf(compat["s"]).isNotBlank()) to
            (pdf != null && stringOf(pdf["s"]).isNotBlank())
    }

    /** 建行响应（§4.1，201 + 行数组）→ 首行。 */
    fun parseCreatedDeck(json: Json = NetJson, raw: String?): PptDeck? = parseDeckList(json, raw).firstOrNull()

    /** 上传/更新响应只关心成败；此处仅判断响应体是否合法数组/对象（坏 JSON 不视为失败）。 */
    fun isAcceptedWriteResponse(raw: String?): Boolean = true

    // ------------------------------------------------------------ 播放详情（§4.5）

    /**
     * 解析播放详情（数组首行；`manifest` 允许对象或 JSON 字符串，均容错）。
     * 结构完全非法 → null（调用方展示「加载失败」）；记录存在但 manifest 空 → [PptDeckDetail] 且 manifest 不可播。
     */
    fun parseDeckDetail(json: Json = NetJson, raw: String?): PptDeckDetail? {
        val root = parseJsonOrNull(json, raw) ?: return null
        val row = when (root) {
            is JsonArray -> root.firstOrNull() as? JsonObject ?: return null
            is JsonObject -> root
            else -> return null
        }
        val manifest = parseManifestElement(json, row["manifest"])
        return PptDeckDetail(
            title = stringOf(row["title"]).trim().take(MAX_TITLE_CHARS),
            manifest = manifest,
            sourceName = stringOf(row["source_name"]).trim(),
            sourceKey = stringOf(row["source_key"]).trim(),
            aspect = stringOf(row["aspect"]).trim(),
            slideCount = intOf(row["slide_count"])?.coerceAtLeast(0) ?: manifest.slides.size,
            stepCount = intOf(row["step_count"])?.coerceAtLeast(0) ?: manifest.totalSteps,
            convertedAt = longOf(row["converted_at"]) ?: 0L,
            coverUrl = stringOf(row["cover_url"]).trim(),
            status = stringOf(row["status"]).trim().lowercase(),
            progress = intOf(row["progress"])?.coerceIn(0, 100) ?: 0,
            error = stringOf(row["error"]).trim().take(MAX_ERROR_CHARS),
            ownerName = stringOf(row["owner_name"]).trim(),
            sourceSize = longOf(row["source_size"])?.coerceAtLeast(0) ?: 0L,
        )
    }

    // ------------------------------------------------------------ manifest（§5）

    /**
     * 解析 manifest 元素：`{}` / 缺失 / 非法 → 空 manifest（`isPlayable=false`，不报错）。
     * 支持直接传 manifest 对象，或传数据库行（自动取 `manifest` 字段）。
     */
    fun parseManifest(json: Json = NetJson, raw: String?): PptManifest {
        val root = parseJsonOrNull(json, raw) ?: return PptManifest()
        return when (root) {
            is JsonObject -> {
                val inner = (root["manifest"] as? JsonObject)
                    ?: (root["manifest"] as? JsonPrimitive)?.let { parseJsonOrNull(json, it.contentOrNull) as? JsonObject }
                parseManifestObject(json, inner ?: root)
            }
            is JsonArray -> {
                val row = root.firstOrNull() as? JsonObject ?: return PptManifest()
                parseManifestElement(json, row["manifest"])
            }
            else -> PptManifest()
        }
    }

    /** 解析任意 manifest 元素（对象 / JSON 字符串 / null）。 */
    fun parseManifestElement(json: Json, element: JsonElement?): PptManifest {
        val obj = when (element) {
            null -> return PptManifest()
            is JsonObject -> element
            is JsonPrimitive -> parseJsonOrNull(json, element.contentOrNull) as? JsonObject ?: return PptManifest()
            else -> return PptManifest()
        }
        return parseManifestObject(json, obj)
    }

    private fun parseManifestObject(json: Json, obj: JsonObject): PptManifest {
        val width = intOf(obj["w"])?.takeIf { it in 1..20_000 } ?: 1600
        val height = intOf(obj["h"])?.takeIf { it in 1..20_000 } ?: 900
        val slides = ArrayList<PptSlide>()
        val rawSlides = obj["slides"] as? JsonArray
        if (rawSlides != null) {
            for (slideEl in rawSlides) {
                val slideObj = slideEl as? JsonObject ?: continue
                val steps = ArrayList<PptStep>()
                val rawSteps = slideObj["steps"] as? JsonArray
                if (rawSteps != null) {
                    for (stepEl in rawSteps) {
                        val stepObj = stepEl as? JsonObject ?: continue
                        val img = stringOf(stepObj["img"]).trim()
                        if (img.isEmpty()) continue // 契约：img 必填；坏步跳过
                        steps.add(PptStep(img = img, media = parseHotspots(stepObj["media"])))
                    }
                }
                if (steps.isEmpty()) continue // 无有效 step 的页无法播放 → 跳过
                slides.add(
                    PptSlide(
                        tr = stringOf(slideObj["tr"]).trim().lowercase().ifBlank { "fade" },
                        trd = intOf(slideObj["trd"]) ?: 450,
                        notes = stringOf(slideObj["notes"]).take(MAX_NOTES_CHARS),
                        thumb = stringOf(slideObj["thumb"]).trim(),
                        steps = steps,
                    ),
                )
            }
        }
        return PptManifest(
            v = intOf(obj["v"]) ?: 1,
            base = stringOf(obj["base"]).trim(),
            src = stringOf(obj["src"]).trim(),
            width = width,
            height = height,
            slides = slides,
            media = parseMediaTable(obj["media"]),
            video = parseVideo(obj["video"], slides.size),
            files = parseFiles(obj["files"]),
            compat = parseCompat(obj["compat"]),
            mediaFiles = parseMediaFiles(json, obj["media_files"]),
        )
    }

    // ------------------------------------------------------------ v2.3 视频/音频单独下载（§12.8）

    /**
     * 解析 `manifest.media_files`（视频/音频单独下载，§12.8）。容错：
     * - 缺失/非数组 → 空列表（旧 deck 不显示分组，不报错）；
     * - `s` 为空的坏项跳过；`kind` 未知时按文件名/扩展名推断（m4a/mp3/wav → 音频，其它 → 视频）；
     * - size/dur/w/h 非法 → 0；slides 只保留 0..10000 的整数、去重升序、上限 [MAX_MEDIA_SLIDES]；
     * - 最多 [MAX_MEDIA_FILES] 条（转换机上限 20，这里放宽但防坏数据撑爆）。
     */
    fun parseMediaFiles(json: Json = NetJson, element: JsonElement?): List<PptMediaFile> {
        val array = element as? JsonArray ?: return emptyList()
        val out = ArrayList<PptMediaFile>(array.size)
        for (item in array) {
            if (out.size >= MAX_MEDIA_FILES) break
            val obj = item as? JsonObject ?: continue
            val src = stringOf(obj["s"]).trim()
            if (src.isEmpty()) continue
            val rawName = stringOf(obj["name"]).trim()
            val rawSource = stringOf(obj["src"]).trim()
            val kind = mediaKindOf(stringOf(obj["kind"]).trim(), rawName, src, rawSource)
            val slides = (obj["slides"] as? JsonArray)
                ?.mapNotNull { slide -> intOf(slide)?.takeIf { it in 0..10_000 } }
                ?.distinct()
                ?.sorted()
                ?.take(MAX_MEDIA_SLIDES)
                .orEmpty()
            out.add(
                PptMediaFile(
                    src = src,
                    name = rawName,
                    size = longOf(obj["size"])?.coerceAtLeast(0L) ?: 0L,
                    sha256 = stringOf(obj["sha256"]).trim(),
                    kind = kind,
                    durationSec = doubleOf(obj["dur"])?.takeIf { it > 0.0 && it.isFinite() } ?: 0.0,
                    width = intOf(obj["w"])?.coerceAtLeast(0) ?: 0,
                    height = intOf(obj["h"])?.coerceAtLeast(0) ?: 0,
                    slides = slides,
                    sourceName = rawSource,
                ),
            )
        }
        return out
    }

    private fun mediaKindOf(rawKind: String, name: String, src: String, sourceName: String): PptMediaKind =
        when (rawKind.lowercase()) {
            "video", "v" -> PptMediaKind.VIDEO
            "audio", "a" -> PptMediaKind.AUDIO
            else -> {
                val ext = extensionOfAny(name, src, sourceName)
                if (ext in AUDIO_EXTENSIONS) PptMediaKind.AUDIO else PptMediaKind.VIDEO
            }
        }

    private fun extensionOfAny(vararg names: String): String {
        for (raw in names) {
            val name = raw.trim()
            val dot = name.lastIndexOf('.')
            if (dot > 0 && dot < name.lastIndex) return name.substring(dot + 1).lowercase()
        }
        return ""
    }

    // ------------------------------------------------------------ v2.0 下载文件（§12.2）

    /** 解析 `manifest.files`（旧 deck 缺失/非对象 → null）。 */
    fun parseFiles(element: JsonElement?): PptFiles? {
        val obj = element as? JsonObject ?: return null
        val files = PptFiles(
            compat = parseFileInfo(obj["compat"]),
            pdf = parseFileInfo(obj["pdf"]),
            source = parseFileInfo(obj["source"]),
        )
        return if (files.compat == null && files.pdf == null && files.source == null) null else files
    }

    /** 解析单个文件项；`s` 为空 → null。 */
    fun parseFileInfo(element: JsonElement?): PptFileInfo? {
        val obj = element as? JsonObject ?: return null
        val src = stringOf(obj["s"]).trim()
        if (src.isEmpty()) return null
        return PptFileInfo(
            src = src,
            size = longOf(obj["size"])?.coerceAtLeast(0L) ?: 0L,
            sha256 = stringOf(obj["sha256"]).trim(),
            ext = stringOf(obj["ext"]).trim().lowercase().removePrefix("."),
            name = stringOf(obj["name"]).trim(),
            pages = intOf(obj["pages"])?.coerceAtLeast(0) ?: 0,
        )
    }

    /** 解析 `manifest.compat`（缺失/非对象 → null）。 */
    fun parseCompat(element: JsonElement?): PptCompat? {
        val obj = element as? JsonObject ?: return null
        val warnings = ArrayList<String>()
        (obj["warnings"] as? JsonArray)?.forEach { item ->
            val text = stringOf(item).trim()
            val cleaned = if (text.length <= MAX_WARNING_CHARS) text else text.take(MAX_WARNING_CHARS) + "…"
            if (cleaned.isNotEmpty() && cleaned !in warnings && warnings.size < MAX_WARNINGS) {
                warnings.add(cleaned)
            }
        }
        val converted = stringOf(obj["converted_from"]).trim()
        val fonts = intOf(obj["fonts_embedded"])?.coerceAtLeast(0)
        if (warnings.isEmpty() && fonts == null && converted.isEmpty()) return null
        return PptCompat(
            warnings = warnings,
            fontsEmbedded = fonts,
            convertedFrom = converted.ifBlank { null },
        )
    }

    /**
     * 解析 manifest.video（契约 §11.1）。容错：
     * - 缺失 / 非对象 / `s` 为空 → null（旧 manifest 不报错，走分步）；
     * - 章节：忽略非法项、按页去重、按页排序、时间单调不减（后项 max(前项)）、越界页丢弃；
     * - w/h/dur 非法 → 0（播放器按 manifest 比例/时长自行兜底）。
     */
    fun parseVideo(element: JsonElement?, slideCount: Int): PptVideoInfo? {
        val obj = element as? JsonObject ?: return null
        val src = stringOf(obj["s"]).trim()
        if (src.isEmpty()) return null
        val chapters = ArrayList<PptVideoChapter>()
        val raw = obj["chapters"] as? JsonArray
        if (raw != null) {
            val byIndex = LinkedHashMap<Int, Double>()
            for (item in raw) {
                val chapter = item as? JsonObject ?: continue
                val index = intOf(chapter["i"]) ?: continue
                val start = doubleOf(chapter["t"]) ?: continue
                if (index < 0 || start < 0.0) continue
                if (slideCount > 0 && index > slideCount - 1) continue
                // 同页重复：保留最早时间
                val old = byIndex[index]
                byIndex[index] = if (old == null) start else minOf(old, start)
            }
            var previous = 0.0
            byIndex.entries.sortedBy { it.key }.forEach { (index, start) ->
                val monotonic = maxOf(start, previous)
                previous = monotonic
                chapters.add(PptVideoChapter(index = index, startSec = monotonic))
            }
        }
        return PptVideoInfo(
            src = src,
            width = intOf(obj["w"])?.coerceAtLeast(0) ?: 0,
            height = intOf(obj["h"])?.coerceAtLeast(0) ?: 0,
            durationSec = doubleOf(obj["dur"])?.takeIf { it > 0.0 } ?: 0.0,
            chapters = chapters,
            quality = stringOf(obj["q"]).trim(),
        )
    }

    private fun parseHotspots(element: JsonElement?): List<PptHotspot> {
        val array = element as? JsonArray ?: return emptyList()
        val out = ArrayList<PptHotspot>(array.size)
        for (item in array) {
            val obj = item as? JsonObject ?: continue
            val src = stringOf(obj["s"]).trim()
            if (src.isEmpty()) continue
            val kind = if (stringOf(obj["k"]).trim().lowercase() == "a") PptMediaKind.AUDIO else PptMediaKind.VIDEO
            val rect = hotspotRect(
                x = doubleOf(obj["x"]),
                y = doubleOf(obj["y"]),
                w = doubleOf(obj["w"]),
                h = doubleOf(obj["h"]),
            )
            out.add(PptHotspot(src, kind, rect.x, rect.y, rect.w, rect.h))
        }
        return out
    }

    private data class Rect4(val x: Float, val y: Float, val w: Float, val h: Float)

    /** 热点矩形夹取：全缺 → 居中播放键（不遮整页）；部分缺按 0/1 填充；保证不越界。 */
    private fun hotspotRect(x: Double?, y: Double?, w: Double?, h: Double?): Rect4 {
        if (x == null && y == null && w == null && h == null) {
            return Rect4(0.36f, 0.42f, 0.28f, 0.16f)
        }
        val width = (w ?: 1.0).toFloat().let { if (it.isFinite()) it else 1f }.coerceIn(0.01f, 1f)
        val height = (h ?: 1.0).toFloat().let { if (it.isFinite()) it else 1f }.coerceIn(0.01f, 1f)
        val left = (x ?: 0.0).toFloat().let { if (it.isFinite()) it else 0f }.coerceIn(0f, 1f - width)
        val top = (y ?: 0.0).toFloat().let { if (it.isFinite()) it else 0f }.coerceIn(0f, 1f - height)
        return Rect4(left, top, width, height)
    }

    private fun parseMediaTable(element: JsonElement?): Map<String, PptMediaDef> {
        val array = element as? JsonArray ?: return emptyMap()
        val out = LinkedHashMap<String, PptMediaDef>(array.size)
        for (item in array) {
            val obj = item as? JsonObject ?: continue
            val src = stringOf(obj["s"]).trim()
            if (src.isEmpty()) continue
            val kind = if (stringOf(obj["k"]).trim().lowercase() == "a") PptMediaKind.AUDIO else PptMediaKind.VIDEO
            out[src] = PptMediaDef(
                src = src,
                kind = kind,
                width = intOf(obj["w"])?.coerceAtLeast(0) ?: 0,
                height = intOf(obj["h"])?.coerceAtLeast(0) ?: 0,
                durationSec = doubleOf(obj["dur"])?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0,
            )
        }
        return out
    }

    // ------------------------------------------------------------ 转换机心跳（§3.4）

    /** 解析 `site_assets?id=eq.ppt_agent` 响应首行；body 缺失/坏 JSON → null（UI 不显示离线提示）。 */
    fun parseAgentStatus(json: Json = NetJson, raw: String?): PptAgentStatus? {
        val root = parseJsonOrNull(json, raw) ?: return null
        val row = when (root) {
            is JsonArray -> root.firstOrNull() as? JsonObject ?: return null
            is JsonObject -> root
            else -> return null
        }
        val body = when (val bodyEl = row["body"]) {
            is JsonObject -> bodyEl
            is JsonPrimitive -> parseJsonOrNull(json, bodyEl.contentOrNull) as? JsonObject
            else -> null
        } ?: return null
        val seenAt = longOf(body["seen_at"])
            ?: longOf(body["seenAt"])
            ?: parseIsoMillis(stringOf(row["updated_at"]))
            ?: 0L
        return PptAgentStatus(
            online = boolOf(body["online"]) ?: (seenAt > 0L),
            seenAt = seenAt,
            queued = intOf(body["queued"])?.coerceAtLeast(0) ?: 0,
            version = stringOf(body["version"]).trim(),
            host = stringOf(body["host"]).trim(),
        )
    }

    // ------------------------------------------------------------ JSON 小工具（容错）

    fun stringOf(element: JsonElement?): String {
        val primitive = element as? JsonPrimitive ?: return ""
        return primitive.contentOrNull.orEmpty()
    }

    fun intOf(element: JsonElement?): Int? {
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive.contentOrNull == null) return null
        return primitive.contentOrNull?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }?.toInt()
    }

    fun longOf(element: JsonElement?): Long? {
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive.contentOrNull == null) return null
        return primitive.contentOrNull?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }?.toLong()
    }

    fun doubleOf(element: JsonElement?): Double? {
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive.contentOrNull == null) return null
        return primitive.contentOrNull?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    fun boolOf(element: JsonElement?): Boolean? = when (element) {
        is JsonPrimitive -> element.contentOrNull?.trim()?.lowercase()?.let {
            when (it) {
                "true", "1" -> true
                "false", "0" -> false
                else -> null
            }
        }
        else -> null
    }

    /** ISO-8601 / 数字时间戳 → epoch ms；无法解析 → null。 */
    fun parseIsoMillis(raw: String?): Long? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        value.toLongOrNull()?.let { return if (it > 0) it else null }
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: Throwable) {
            null
        }
    }
}
