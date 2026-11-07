package com.furina.classtools.features.draw

/** 抽签执行结果。 */
sealed interface DrawOutcome {
    data class Success(val result: DrawResult) : DrawOutcome
    data class Failure(val error: DrawError) : DrawOutcome
}

/**
 * 抽签引擎（纯函数 + 注入随机源，可复现）。
 *
 * 算法对齐网页 `doDraw()`：
 * - 池 = 范围 − 排除号（名单来源再∩名单）；
 * - 不重复：Fisher-Yates 洗牌后取前 count 个；允许重复：count 次独立随机；
 * - 排序：按抽取顺序（默认）/ 从小到大 / 从大到小 / 随机重排；
 * - 剔除重抽：移除指定项后，从「池 − 当前结果」补抽一个（允许重复时从全池补），追加 log。
 */
object DrawEngine {

    /** 候选号码 → 带姓名条目。 */
    fun entriesFor(numbers: List<Int>, nameOf: (Int) -> String?): List<DrawEntry> =
        numbers.map { number -> DrawEntry(number = number, name = nameOf(number)?.takeIf { it.isNotBlank() }) }

    /** 校验 + 抽签（首抽入口）。 */
    fun draw(
        config: DrawConfig,
        rosterSeats: List<Int> = emptyList(),
        nameOf: (Int) -> String? = { null },
        random: DrawRandom,
    ): DrawOutcome = when (val validation = DrawValidator.validate(config, rosterSeats)) {
        is DrawValidation.Invalid -> DrawOutcome.Failure(validation.error)
        is DrawValidation.Valid -> {
            val candidates = entriesFor(validation.numbers, nameOf)
            drawFromCandidates(config, candidates, random)
        }
    }

    /** 从已确定的候选池抽签（STEP2 预览后调用，保证候选与预览一致）。 */
    fun drawFromCandidates(
        config: DrawConfig,
        candidates: List<DrawEntry>,
        random: DrawRandom,
    ): DrawOutcome {
        if (candidates.isEmpty()) {
            return DrawOutcome.Failure(DrawError(DrawErrorCode.POOL_EMPTY, "候选为空，请返回上一步调整参数"))
        }
        if (config.count < DrawConfig.MIN_COUNT || config.count > DrawConfig.MAX_COUNT) {
            return DrawOutcome.Failure(
                DrawError(
                    DrawErrorCode.COUNT_INVALID,
                    "抽取个数需在 ${DrawConfig.MIN_COUNT}–${DrawConfig.MAX_COUNT} 之间",
                ),
            )
        }
        if (!config.allowDuplicates && config.count > candidates.size) {
            return DrawOutcome.Failure(
                DrawError(
                    DrawErrorCode.COUNT_EXCEEDS_POOL,
                    "最多可抽 ${candidates.size} 个（可用池 ${candidates.size}）",
                ),
            )
        }
        val picked = pick(config, candidates, random)
        val ordered = applySort(picked, config.sort, random)
        return DrawOutcome.Success(
            DrawResult(
                entries = ordered,
                pool = candidates.toList(),
                config = config,
            ),
        )
    }

    /** 剔除指定下标并（可选）自动补抽；返回新结果（不可变）。 */
    fun removeAndRedraw(
        result: DrawResult,
        index: Int,
        autoRedraw: Boolean,
        random: DrawRandom,
    ): DrawOutcome {
        if (index !in result.entries.indices) {
            return DrawOutcome.Failure(DrawError(DrawErrorCode.INVALID_INDEX, "要剔除的项不存在"))
        }
        val removed = result.entries[index]
        val remaining = result.entries.toMutableList().apply { removeAt(index) }
        if (!autoRedraw) {
            return DrawOutcome.Success(
                result.copy(
                    entries = remaining,
                    log = result.log + DrawRemoveLog(removed = removed, added = null),
                ),
            )
        }
        val usedNumbers = if (result.config.allowDuplicates) emptySet() else remaining.map { it.number }.toSet()
        val available = result.pool.filter { it.number !in usedNumbers }
        val added = if (available.isEmpty()) null else available[random.nextInt(available.size)]
        val newEntries = if (added != null) remaining + added else remaining
        return DrawOutcome.Success(
            result.copy(
                entries = newEntries,
                log = result.log + DrawRemoveLog(removed = removed, added = added),
            ),
        )
    }

    /** 同参数重新抽一次（结果集合会变，保留原参数）。 */
    fun reroll(result: DrawResult, random: DrawRandom): DrawOutcome =
        drawFromCandidates(result.config, result.pool, random)

    /** 对已有结果应用排序模式（返回新列表，不改变集合）。 */
    fun sortEntries(entries: List<DrawEntry>, mode: DrawSortMode, random: DrawRandom): List<DrawEntry> =
        when (mode) {
            DrawSortMode.DRAW_ORDER -> entries
            DrawSortMode.ASCENDING -> entries.sortedBy { it.number }
            DrawSortMode.DESCENDING -> entries.sortedByDescending { it.number }
            DrawSortMode.RANDOM -> random.shuffled(entries)
        }

    // ------------------------------------------------------------------ 内部

    private fun pick(config: DrawConfig, candidates: List<DrawEntry>, random: DrawRandom): List<DrawEntry> {
        if (config.allowDuplicates) {
            return List(config.count) { candidates[random.nextInt(candidates.size)] }
        }
        val order = random.shuffledList(candidates.size)
        return order.take(config.count).map { candidates[it] }
    }

    private fun applySort(entries: List<DrawEntry>, mode: DrawSortMode, random: DrawRandom): List<DrawEntry> =
        sortEntries(entries, mode, random)
}
