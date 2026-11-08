package com.furina.classtools.features.draw

/** 排除序号解析结果。 */
data class ExcludedParse(
    val numbers: Set<Int>,
    /** 无法解析的片段（用于报错，最多保留前 3 个避免提示过长）。 */
    val invalidTokens: List<String>,
) {
    val isValid: Boolean get() = invalidTokens.isEmpty()
}

/** 参数校验结果：合法时直接给出候选号码（升序），非法时给出可展示的错误。 */
sealed interface DrawValidation {
    data class Valid(val numbers: List<Int>) : DrawValidation
    data class Invalid(val error: DrawError) : DrawValidation
}

/**
 * 抽签参数校验（纯函数）。
 *
 * 规则（与网页 `doDraw()` + 设计稿 §4.5 对齐，个别处按设计稿更严格）：
 * - 范围：`start` 与 `end` 都在 `1..999`，且 **start ≤ end**（网页会静默交换，Android 设计稿要求行内报错）；
 * - 个数：`1..999`；
 * - 排除序号：支持 `3,7,10-12`（逗号/顿号/空白分隔，区间 `-`/`~`/`—`）；格式错→报错；
 * - 候选池 = 范围 − 排除号；名单来源时再与班级名单座号取交集；
 * - 不重复时 个数 ≤ 池大小；允许重复则不限。
 */
object DrawValidator {

    private val RANGE_TOKEN = Regex("^(\\d+)\\s*[-~—]\\s*(\\d+)$")
    private val SINGLE_TOKEN = Regex("^\\d+$")

    /** 解析排除序号文本；空文本 = 无排除。 */
    fun parseExcluded(text: String): ExcludedParse {
        val numbers = LinkedHashSet<Int>()
        val invalid = ArrayList<String>()
        for (token in text.split(Regex("[,，、;；\\s]+"))) {
            val t = token.trim()
            if (t.isEmpty()) continue
            val range = RANGE_TOKEN.matchEntire(t)
            if (range != null) {
                val from = range.groupValues[1].toIntOrNull()
                val to = range.groupValues[2].toIntOrNull()
                if (from == null || to == null || from > to ||
                    from < DrawConfig.MIN_NUMBER || to > DrawConfig.MAX_NUMBER
                ) {
                    invalid.add(t)
                } else {
                    for (n in from..to) numbers.add(n)
                }
                continue
            }
            if (SINGLE_TOKEN.matches(t)) {
                val n = t.toIntOrNull()
                if (n == null || n < DrawConfig.MIN_NUMBER || n > DrawConfig.MAX_NUMBER) {
                    invalid.add(t)
                } else {
                    numbers.add(n)
                }
            } else {
                invalid.add(t)
            }
        }
        return ExcludedParse(numbers = numbers, invalidTokens = invalid.take(3))
    }

    /** 校验并给出候选号码（升序）。[rosterSeats] 为空表示未提供班级名单。 */
    fun validate(config: DrawConfig, rosterSeats: List<Int> = emptyList()): DrawValidation {
        if (config.start < DrawConfig.MIN_NUMBER || config.start > DrawConfig.MAX_NUMBER ||
            config.end < DrawConfig.MIN_NUMBER || config.end > DrawConfig.MAX_NUMBER
        ) {
            return DrawValidation.Invalid(
                DrawError(
                    DrawErrorCode.RANGE_INVALID,
                    "范围需在 ${DrawConfig.MIN_NUMBER}–${DrawConfig.MAX_NUMBER} 之间",
                ),
            )
        }
        if (config.start > config.end) {
            return DrawValidation.Invalid(
                DrawError(DrawErrorCode.RANGE_INVALID, "起始值不能大于结束值"),
            )
        }
        if (config.count < DrawConfig.MIN_COUNT || config.count > DrawConfig.MAX_COUNT) {
            return DrawValidation.Invalid(
                DrawError(
                    DrawErrorCode.COUNT_INVALID,
                    "抽取个数需在 ${DrawConfig.MIN_COUNT}–${DrawConfig.MAX_COUNT} 之间",
                ),
            )
        }
        val excluded = parseExcluded(config.excludedText)
        if (!excluded.isValid) {
            return DrawValidation.Invalid(
                DrawError(
                    DrawErrorCode.EXCLUSION_INVALID,
                    "排除格式示例：3,7,10-12",
                ),
            )
        }

        var numbers = (config.start..config.end).filter { it !in excluded.numbers }
        if (config.poolSource == DrawPoolSource.ROSTER) {
            val seats = rosterSeats.asSequence()
                .filter { it in DrawConfig.MIN_NUMBER..DrawConfig.MAX_NUMBER }
                .distinct()
                .toSet()
            if (seats.isEmpty()) {
                return DrawValidation.Invalid(
                    DrawError(DrawErrorCode.ROSTER_REQUIRED, "名单为空，请先选择班级名单"),
                )
            }
            numbers = numbers.filter { it in seats }
        }
        if (numbers.isEmpty()) {
            return DrawValidation.Invalid(
                DrawError(DrawErrorCode.POOL_EMPTY, "排除后没有可用序号，请调整范围或排除号"),
            )
        }
        if (!config.allowDuplicates && config.count > numbers.size) {
            return DrawValidation.Invalid(
                DrawError(
                    DrawErrorCode.COUNT_EXCEEDS_POOL,
                    "最多可抽 ${numbers.size} 个（可用池 ${numbers.size}）",
                ),
            )
        }
        return DrawValidation.Valid(numbers)
    }
}
