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
 * - 补抽沿用本轮候选池，将剔除号码排除。不重复时，结果中已有的号码也排除。
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
        val updated = result.copy(
            entries = result.entries.toMutableList().apply { removeAt(index) },
            log = result.log + DrawRemoveLog(removed = removed),
        )
        return DrawOutcome.Success(if (autoRedraw) refill(updated, random, limit = 1) else updated)
    }

    /** 按本轮目标数量补齐缺额。候选用尽时保留已有结果。 */
    fun refill(result: DrawResult, random: DrawRandom, limit: Int = result.config.count): DrawResult {
        val missing = minOf(result.config.count - result.entries.size, limit)
        if (missing <= 0) return result
        val blocked = result.log.mapTo(mutableSetOf()) { it.removed.number }
        if (!result.config.allowDuplicates) result.entries.mapTo(blocked) { it.number }
        val available = result.pool.filter { it.number !in blocked }
        if (available.isEmpty()) return result
        val count = if (result.config.allowDuplicates) missing else minOf(missing, available.size)
        val added = pick(result.config.copy(count = count), available, random)
        val replacements = added.iterator()
        val log = result.log.map { record ->
            if (record.added == null && replacements.hasNext()) record.copy(added = replacements.next()) else record
        }
        val entries = applySort(result.entries + added, result.config.sort, random)
        return result.copy(entries = entries, log = log)
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
