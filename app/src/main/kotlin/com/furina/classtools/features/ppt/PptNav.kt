package com.furina.classtools.features.ppt

/**
 * 播放器 step 导航（契约 §5：「每个『点击』推进一个 step」）。
 *
 * 术语：`slide` = 页（0 基），`step` = 页内动画步（0 基），`global` = 全篇步序号（0 基）。
 * 纯函数、无状态，播放器只持有 [PptPosition]；跨页上一/下一步由这里统一换算。
 */
object PptNav {

    /** 播放位置（0 基）。 */
    data class PptPosition(val slide: Int, val step: Int) {
        override fun toString(): String = "p$slide.s$step"
    }

    /** 每页步数列表（来自 manifest；空页已过滤，因此每项 ≥1）。 */
    typealias StepCounts = List<Int>

    fun ofManifest(stepCounts: List<Int>): StepCounts =
        stepCounts.map { it.coerceAtLeast(1) }

    /** 总步数（全篇）。 */
    fun totalSteps(counts: StepCounts): Int = counts.sumOf { it.coerceAtLeast(1) }

    /** 总页数。 */
    fun pageCount(counts: StepCounts): Int = counts.size

    /** 首页第一步。 */
    fun first(): PptPosition = PptPosition(0, 0)

    /** 末页最后一步；空列表返回首页（调用方应保证 manifest 非空）。 */
    fun last(counts: StepCounts): PptPosition {
        if (counts.isEmpty()) return first()
        val slide = counts.lastIndex
        return PptPosition(slide, counts[slide].coerceAtLeast(1) - 1)
    }

    /** 全篇步序号（越界夹取）。 */
    fun globalIndex(counts: StepCounts, position: PptPosition): Int {
        if (counts.isEmpty()) return 0
        val slide = position.slide.coerceIn(0, counts.lastIndex)
        var n = 0
        for (i in 0 until slide) n += counts[i].coerceAtLeast(1)
        return n + position.step.coerceIn(0, counts[slide].coerceAtLeast(1) - 1)
    }

    /** 由全篇步序号反查位置（越界夹取）。 */
    fun positionAt(counts: StepCounts, global: Int): PptPosition {
        if (counts.isEmpty()) return first()
        var remaining = global.coerceIn(0, totalSteps(counts) - 1)
        for (i in counts.indices) {
            val size = counts[i].coerceAtLeast(1)
            if (remaining < size) return PptPosition(i, remaining)
            remaining -= size
        }
        return last(counts)
    }

    /** 位置夹取到合法范围。 */
    fun normalize(counts: StepCounts, position: PptPosition): PptPosition {
        if (counts.isEmpty()) return first()
        val slide = position.slide.coerceIn(0, counts.lastIndex)
        val step = position.step.coerceIn(0, counts[slide].coerceAtLeast(1) - 1)
        return PptPosition(slide, step)
    }

    /** 下一步；已是最后一步 → null（调用方可提示「已是最后一页」）。 */
    fun next(counts: StepCounts, position: PptPosition): PptPosition? {
        if (counts.isEmpty()) return null
        val current = globalIndex(counts, position)
        val total = totalSteps(counts)
        if (current >= total - 1) return null
        return positionAt(counts, current + 1)
    }

    /** 上一步；已是第一步 → null。 */
    fun prev(counts: StepCounts, position: PptPosition): PptPosition? {
        if (counts.isEmpty()) return null
        val current = globalIndex(counts, position)
        if (current <= 0) return null
        return positionAt(counts, current - 1)
    }

    /** 跳到某页第一步（缩略图点击）。 */
    fun jumpToSlide(counts: StepCounts, slide: Int): PptPosition {
        if (counts.isEmpty()) return first()
        val target = slide.coerceIn(0, counts.lastIndex)
        return PptPosition(target, 0)
    }

    /** 是否最后一页的最后一步。 */
    fun isEnd(counts: StepCounts, position: PptPosition): Boolean {
        if (counts.isEmpty()) return true
        return globalIndex(counts, position) >= totalSteps(counts) - 1
    }

    /** 当前页步数。 */
    fun stepsOfSlide(counts: StepCounts, slide: Int): Int =
        counts.getOrElse(slide) { 0 }.coerceAtLeast(1)

    /** 顶部进度条比例（0..1，含当前步）。 */
    fun progress(counts: StepCounts, position: PptPosition): Float {
        val total = totalSteps(counts)
        if (total <= 0) return 0f
        val done = globalIndex(counts, position) + 1
        return (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    /** 控制条文案：`页/总页 · 步/本页步`（契约 §8.1 的 `3/12 · 2/3`）。 */
    fun label(counts: StepCounts, position: PptPosition): String {
        if (counts.isEmpty()) return "0/0 · 0/0"
        val pos = normalize(counts, position)
        return "${pos.slide + 1}/${counts.size} · ${pos.step + 1}/${stepsOfSlide(counts, pos.slide)}"
    }

    /** 页文案（备注抽屉标题/缩略图标签）。 */
    fun pageLabel(counts: StepCounts, slide: Int): String =
        if (counts.isEmpty()) "0/0" else "${slide.coerceIn(0, counts.lastIndex) + 1}/${counts.size}"

    /** 无障碍/长文案：`第 3 页 · 第 2 步`。 */
    fun spokenLabel(counts: StepCounts, position: PptPosition): String {
        if (counts.isEmpty()) return "空演示"
        val pos = normalize(counts, position)
        return "第 ${pos.slide + 1} 页 · 第 ${pos.step + 1} 步"
    }
}
