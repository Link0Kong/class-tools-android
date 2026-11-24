package com.furina.classtools.features.draw

/**
 * 抽签工具领域模型（纯 Kotlin，无 Android 依赖，可单测）。
 *
 * 对齐契约：`docs/api/USERDATA_V6.md` 的 `draw` section
 * `{ start, end, count, dup, excl, sort }`；`sort` 线格式 `draw|num|desc|rand`。
 */

/** 结果排序模式（web `A.cfg.sort` 的四种；wire 值必须与 userdata.draw.sort 对齐）。 */
enum class DrawSortMode(val wire: String, val label: String) {
    /** 按抽取顺序（默认）。 */
    DRAW_ORDER("draw", "按抽取顺序"),

    /** 从小到大。 */
    ASCENDING("num", "从小到大"),

    /** 从大到小。 */
    DESCENDING("desc", "从大到小"),

    /** 随机重排（重新洗牌，不改变抽取集合）。 */
    RANDOM("rand", "随机"),
    ;

    companion object {
        fun fromWire(wire: String?): DrawSortMode =
            entries.firstOrNull { it.wire == wire } ?: DRAW_ORDER
    }
}

/** 候选池来源：纯号码范围 / 班级名单（与范围取交集）。 */
enum class DrawPoolSource(val label: String) {
    RANGE("号码范围"),
    ROSTER("班级名单"),
}

/** 抽签参数（表单状态 + 持久化 sync 的唯一真源）。 */
data class DrawConfig(
    /** 范围起始（含）。 */
    val start: Int = DEFAULT_START,
    /** 范围结束（含）。 */
    val end: Int = DEFAULT_END,
    /** 抽取个数。 */
    val count: Int = DEFAULT_COUNT,
    /** 允许重复抽取。 */
    val allowDuplicates: Boolean = false,
    /** 排除序号原始文本（支持 `3,7,10-12`），持久化时原样保存。 */
    val excludedText: String = "",
    /** 结果排序模式。 */
    val sort: DrawSortMode = DrawSortMode.DRAW_ORDER,
    /** 候选池来源；RANGE 不进持久化（仅 UI 选择，跨端只有号码范围）。 */
    val poolSource: DrawPoolSource = DrawPoolSource.RANGE,
) {
    /** 规范化下界（min）。 */
    val lowerBound: Int get() = minOf(start, end)

    /** 规范化上界（max）。 */
    val upperBound: Int get() = maxOf(start, end)

    companion object {
        const val DEFAULT_START: Int = 1
        const val DEFAULT_END: Int = 70
        const val DEFAULT_COUNT: Int = 10

        /** 允许的号码绝对边界（防脏数据；与 web 的宽松范围相比收紧到可展示区间）。 */
        const val MIN_NUMBER: Int = 1
        const val MAX_NUMBER: Int = 999
        const val MIN_COUNT: Int = 1
        const val MAX_COUNT: Int = 999
    }
}

/** 候选/结果条目：序号 + 可选姓名（名单来源时带姓名）。 */
data class DrawEntry(
    val number: Int,
    val name: String? = null,
) {
    /** 展示文案：`07 张三` 或 `07`。 */
    val label: String
        get() = if (name.isNullOrBlank()) number.toString() else "$number $name"
}

/** 剔除重抽记录：被剔除的条目 + 补抽条目（未补抽为 null）。 */
data class DrawRemoveLog(
    val removed: DrawEntry,
    val added: DrawEntry? = null,
)

/** 一次抽签的完整结果（含候选池快照与参数快照，便于导出/重抽/最近抽签持久化）。 */
data class DrawResult(
    val entries: List<DrawEntry>,
    val pool: List<DrawEntry>,
    val config: DrawConfig,
    val log: List<DrawRemoveLog> = emptyList(),
    val proof: DrawRoundProof? = null,
) {
    val isEmpty: Boolean get() = entries.isEmpty()
}

/** 参数/操作错误分类（UI 按 code 决定提示方式，message 可直接展示）。 */
enum class DrawErrorCode {
    RANGE_INVALID,
    COUNT_INVALID,
    EXCLUSION_INVALID,
    POOL_EMPTY,
    COUNT_EXCEEDS_POOL,
    ROSTER_REQUIRED,
    INVALID_STEP,
    INVALID_INDEX,
}

/** 领域错误：code 用于逻辑分支，message 用于行内提示。 */
data class DrawError(
    val code: DrawErrorCode,
    val message: String,
)
