package com.furina.classtools.features.ppt

import com.furina.classtools.data.ppt.PptVideoChapter
import com.furina.classtools.data.ppt.PptVideoInfo

/**
 * 「原样播放」视频模式纯逻辑（契约 §11.3）：默认模式选择、章节 seek、进度/时间、倍速。
 *
 * 纯 Kotlin（无 android.*）→ 全部可 JVM 单测。
 */
object PptVideoLogic {

    /** 章节 seek 偏移：契约 §11.3.2「seek 到 chapters[i].t + 0.05」。 */
    const val SEEK_EPSILON_SEC: Double = 0.05

    /** 倍速档位（契约 §11.3.2：1× / 1.25× / 1.5× / 2× 循环）。 */
    val SPEED_OPTIONS: List<Float> = listOf(1f, 1.25f, 1.5f, 2f)

    /** 下一档倍速（循环；非法当前值按 1× 之后循环）。 */
    fun nextSpeed(current: Float): Float {
        val matched = SPEED_OPTIONS.indexOfFirst { kotlin.math.abs(it - current) < 0.001f }
        val index = if (matched < 0) 0 else matched
        return SPEED_OPTIONS[(index + 1).mod(SPEED_OPTIONS.size)]
    }

    /** 倍速展示文案：1× / 1.25× / 1.5× / 2×。 */
    fun speedLabel(speed: Float): String {
        val rounded = kotlin.math.round(speed * 100f) / 100f
        return if (rounded == rounded.toInt().toFloat()) "${rounded.toInt()}×" else "${rounded}×"
    }

    /** 视频展示宽高比（未知退回 manifest 比例；再退回 16:9）。 */
    fun aspectRatio(video: PptVideoInfo?, manifestWidth: Int = 0, manifestHeight: Int = 0): Float {
        val w = video?.width ?: 0
        val h = video?.height ?: 0
        if (w > 0 && h > 0) return w.toFloat() / h.toFloat()
        if (manifestWidth > 0 && manifestHeight > 0) return manifestWidth.toFloat() / manifestHeight.toFloat()
        return 16f / 9f
    }

    /** 某页的章节开始秒数（找不到 → null，调用方退回 0/最近页）。 */
    fun chapterStartOf(chapters: List<PptVideoChapter>, pageIndex: Int): Double? =
        chapters.firstOrNull { it.index == pageIndex }?.startSec

    /** 章节跳页的 seek 目标：`t + 0.05`；不支持该页 → null。 */
    fun seekTargetForPage(chapters: List<PptVideoChapter>, pageIndex: Int): Double? {
        val start = chapterStartOf(chapters, pageIndex) ?: return null
        return (start + SEEK_EPSILON_SEC).coerceAtLeast(0.0)
    }

    /** 当前播放位置对应的页（最大的 `chapter.t <= position`；无章节 → 0）。 */
    fun pageAt(chapters: List<PptVideoChapter>, positionSec: Double): Int {
        if (chapters.isEmpty()) return 0
        val position = if (positionSec.isFinite()) positionSec else 0.0
        var page = chapters.first().index
        for (chapter in chapters) {
            if (chapter.startSec <= position + 1e-6) page = chapter.index else break
        }
        return page
    }

    /** 上一页 seek 目标（当前页 -1，最小 0 页；无当前页章节时取第一个有章节的页）。 */
    fun previousSeekTarget(chapters: List<PptVideoChapter>, positionSec: Double): Double? {
        if (chapters.isEmpty()) return null
        val current = pageAt(chapters, positionSec)
        val previous = chapters.lastOrNull { it.index < current } ?: chapters.first()
        return seekTargetForPage(chapters, previous.index)
    }

    /** 下一页 seek 目标（当前页 +1，最大末章页；已在最后一页 → null = 不动作）。 */
    fun nextSeekTarget(chapters: List<PptVideoChapter>, positionSec: Double): Double? {
        if (chapters.isEmpty()) return null
        val current = pageAt(chapters, positionSec)
        val next = chapters.firstOrNull { it.index > current } ?: return null
        return seekTargetForPage(chapters, next.index)
    }

    /** 是否有上一页/下一页（控制条按钮可用性）。 */
    fun hasPrevious(chapters: List<PptVideoChapter>, positionSec: Double): Boolean {
        if (chapters.isEmpty()) return false
        val current = pageAt(chapters, positionSec)
        return chapters.any { it.index < current } || (positionSec > (chapters.firstOrNull()?.startSec ?: 0.0) + SEEK_EPSILON_SEC)
    }

    fun hasNext(chapters: List<PptVideoChapter>, positionSec: Double): Boolean {
        if (chapters.isEmpty()) return false
        val current = pageAt(chapters, positionSec)
        return chapters.any { it.index > current }
    }

    /** 进度比例（0..1）。 */
    fun progress(positionSec: Double, durationSec: Double): Float {
        if (!durationSec.isFinite() || durationSec <= 0.0) return 0f
        val position = if (positionSec.isFinite()) positionSec else 0.0
        return (position / durationSec).toFloat().coerceIn(0f, 1f)
    }

    /** 时间文案 `mm:ss`（总分钟制：超过 59 分钟显示 61:05；非法 → 00:00）。 */
    fun formatTime(seconds: Double): String {
        if (!seconds.isFinite() || seconds < 0.0) return "00:00"
        val total = seconds.toLong()
        val minutes = total / 60
        val secs = total % 60
        return String.format(java.util.Locale.US, "%02d:%02d", minutes, secs)
    }

    /**
     * 是否应自动开始播放（契约 §11.3.1：原样播放在用户点击进入后允许 autoplay；
     * 系统拒绝时由播放器显示大播放按钮，不报错）。
     */
    fun shouldAutoPlay(mode: PptPlayMode): Boolean = mode == PptPlayMode.VIDEO

    /** 视频看过的比例（用于「未到末尾」判断：<95% 视为未看完，契约 §11.3.4）。 */
    fun watchedFraction(positionSec: Double, durationSec: Double): Double =
        progress(positionSec, durationSec).toDouble()

    /** 是否已到末尾附近（>=95% 或距结束 <5s 视为看完）。 */
    fun isNearEnd(positionSec: Double, durationSec: Double): Boolean {
        if (!durationSec.isFinite() || durationSec <= 0.0) return false
        val remaining = durationSec - positionSec.coerceAtLeast(0.0)
        return remaining <= 5.0 || progress(positionSec, durationSec) >= 0.95f
    }
}
