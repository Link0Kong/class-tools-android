package com.furina.classtools.features.ppt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * 续播本机记录（契约 §11.3.4）：每个 deck 记住「模式 + 页/步 或 视频秒数 + 时间戳」。
 *
 * 本机存储（Android SharedPreferences 按 deckId）只存 JSON 字符串；编解码纯 Kotlin，可单测。
 */
data class PptResumeRecord(
    val mode: PptPlayMode,
    /** 分步模式：页下标（0 基）。 */
    val page: Int = 0,
    /** 分步模式：页内 step 下标（0 基）。 */
    val step: Int = 0,
    /** 原样播放模式：视频位置（秒）。 */
    val videoSec: Double = 0.0,
    /** 记录时间（unix ms）。 */
    val updatedAtMs: Long = 0L,
)

/** 续播有效窗口：7 天（契约 §11.3.4）。 */
const val PPT_RESUME_TTL_MS: Long = 7L * 24 * 3600 * 1000

/** 续播徽标/自动续播判定文案与阈值（纯函数）。 */
object PptResumeLogic {

    /** 视频至少看过 5s 才值得续播（契约 §11.3.4「位置 >5s」）。 */
    const val VIDEO_MIN_RESUME_SEC: Double = 5.0

    /** 是否在 7 天有效期内。 */
    fun isFresh(record: PptResumeRecord?, nowMs: Long): Boolean {
        if (record == null) return false
        if (record.updatedAtMs <= 0L) return false
        val age = nowMs - record.updatedAtMs
        return age in 0..PPT_RESUME_TTL_MS
    }

    /**
     * 列表卡片「继续」徽标：7 天内 + 有实际进度（视频 >5s / 分步不在第一页第一步）。
     * 列表没有每页步数，故用页/步是否 >0 粗判；末页精确判定在播放器内做。
     */
    fun badgeVisible(record: PptResumeRecord?, nowMs: Long): Boolean {
        if (!isFresh(record, nowMs)) return false
        val r = record ?: return false
        return when (r.mode) {
            PptPlayMode.VIDEO -> r.videoSec > VIDEO_MIN_RESUME_SEC
            PptPlayMode.STEPS -> r.page > 0 || r.step > 0
        }
    }

    /**
     * 打开播放器时是否自动续播（契约 §11.3.4）：
     * - 7 天内；
     * - 视频：>5s 且 <95%（未到末尾）；
     * - 分步：不在第一页第一步，且不在最后一页最后一步。
     *
     * @param stepCounts 每页 step 数（分步模式用；视频模式可传空）
     * @param videoDurationSec 视频总时长（0 = 未知，不做 95% 判定）
     */
    fun shouldResume(
        record: PptResumeRecord?,
        nowMs: Long,
        stepCounts: List<Int> = emptyList(),
        videoDurationSec: Double = 0.0,
    ): Boolean {
        if (!isFresh(record, nowMs)) return false
        val r = record ?: return false
        return when (r.mode) {
            PptPlayMode.VIDEO -> {
                if (r.videoSec <= VIDEO_MIN_RESUME_SEC) return false
                if (videoDurationSec > 0.0) {
                    !PptVideoLogic.isNearEnd(r.videoSec, videoDurationSec)
                } else {
                    true
                }
            }

            PptPlayMode.STEPS -> {
                if (stepCounts.isEmpty()) return false
                val pos = PptNav.normalize(stepCounts, PptNav.PptPosition(r.page, r.step))
                PptNav.globalIndex(stepCounts, pos) > 0 && !PptNav.isEnd(stepCounts, pos)
            }
        }
    }

    /** 续播提示文案：`续播 12:34`（视频）/ `续播 第 5 页`（分步）。 */
    fun resumeHint(record: PptResumeRecord): String = when (record.mode) {
        PptPlayMode.VIDEO -> "续播 ${PptVideoLogic.formatTime(record.videoSec)}"
        PptPlayMode.STEPS -> "续播 第 ${record.page + 1} 页"
    }
}

/** SharedPreferences 存 JSON 的编解码（字段短：m/p/s/t/at）。 */
object PptResumeCodec {

    private val json: Json = Json { ignoreUnknownKeys = true }

    fun encode(record: PptResumeRecord): String = buildJsonObject {
        put("m", if (record.mode == PptPlayMode.VIDEO) "v" else "s")
        put("p", record.page)
        put("st", record.step)
        put("t", record.videoSec)
        put("at", record.updatedAtMs)
    }.toString()

    fun decode(raw: String?): PptResumeRecord? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val obj = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: Throwable) {
            null
        } ?: return null
        val mode = when (str(obj, "m")) {
            "v" -> PptPlayMode.VIDEO
            "s" -> PptPlayMode.STEPS
            else -> return null
        }
        val updatedAt = long(obj, "at") ?: return null
        if (updatedAt <= 0L) return null
        return PptResumeRecord(
            mode = mode,
            page = (int(obj, "p") ?: 0).coerceAtLeast(0),
            step = (int(obj, "st") ?: 0).coerceAtLeast(0),
            videoSec = (double(obj, "t") ?: 0.0).coerceAtLeast(0.0),
            updatedAtMs = updatedAt,
        )
    }

    private fun str(obj: JsonObject, key: String): String? =
        (obj[key] as? JsonPrimitive)?.contentOrNull

    private fun int(obj: JsonObject, key: String): Int? =
        str(obj, key)?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }?.toInt()

    private fun long(obj: JsonObject, key: String): Long? =
        str(obj, key)?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }?.toLong()

    private fun double(obj: JsonObject, key: String): Double? =
        str(obj, key)?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }
}
