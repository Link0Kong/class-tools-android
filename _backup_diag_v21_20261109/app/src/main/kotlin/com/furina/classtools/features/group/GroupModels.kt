package com.furina.classtools.features.group

/**
 * 分组工具领域模型（纯 Kotlin，无 Android 依赖，可单测）。
 *
 * 对齐契约：
 * - `docs/BACKEND_CONTRACT.md` §7.2：成员来源 prev/names、模式 groups/pergroup、可选打乱、轮流分配；
 * - `docs/api/USERDATA_V6.md`：`userdata.group = { source, mode, n, shuffle, sel }`
 *   （blob 里统一用网页词汇：`source: prev|names`、`mode: groups|pergroup`、`sel: 座号数组`）。
 */

/** 成员来源。 */
enum class GroupSource(val wire: String, val label: String) {
    /** 上一步抽签结果（带序号/可选姓名）。 */
    PREV("prev", "上一步抽签序号"),

    /** 班级名单（按座号选择）。 */
    NAMES("names", "班级名单"),
    ;

    companion object {
        fun fromWire(wire: String?): GroupSource =
            entries.firstOrNull { it.wire == wire?.trim() } ?: PREV
    }
}

/** 分组方式。 */
enum class GroupMode(val wire: String, val label: String) {
    /** 按组数（clamp 2..成员数）。 */
    GROUPS("groups", "按组数"),

    /** 按每组人数（组数 = ceil(成员数/每组人数)）。 */
    PER_GROUP("pergroup", "按每组人数"),
    ;

    companion object {
        fun fromWire(wire: String?): GroupMode =
            entries.firstOrNull { it.wire == wire?.trim() } ?: GROUPS
    }
}

/** 一个可分组的学生：座号 + 可选姓名。 */
data class GroupMember(
    val seat: Int,
    val name: String = "",
) {
    /** 展示文案：`07 张伟` 或 `07`。 */
    val label: String
        get() = if (name.isBlank()) seat.toString() else "$seat $name"
}

/** 分组参数（表单状态 + userdata.group 持久化唯一真源）。 */
data class GroupConfig(
    val source: GroupSource = GroupSource.PREV,
    val mode: GroupMode = GroupMode.GROUPS,
    /** 组数（GROUPS）或每组人数（PER_GROUP）。 */
    val n: Int = DEFAULT_N,
    /** 是否打乱成员再分组（Fisher-Yates）。 */
    val shuffle: Boolean = false,
    /** 班级名单来源时已选座号（升序去重；blob 原样保存）。 */
    val selectedSeats: List<Int> = emptyList(),
) {
    /** 规范化 n：GROUPS 夹到 [2, 成员数]，PER_GROUP 夹到 [1, 99]（与网页 doGroup 的 clamp 语义一致）。 */
    fun normalizedN(memberCount: Int): Int = when (mode) {
        GroupMode.GROUPS -> n.coerceIn(MIN_GROUPS, maxOf(MIN_GROUPS, memberCount))
        GroupMode.PER_GROUP -> n.coerceIn(MIN_PER_GROUP, MAX_PER_GROUP)
    }

    companion object {
        const val DEFAULT_N: Int = 4
        const val MIN_GROUPS: Int = 2
        const val MIN_PER_GROUP: Int = 1
        const val MAX_PER_GROUP: Int = 99
    }
}

/** 一组结果（index 从 1 开始，用于「第 N 组」展示与导出）。 */
data class MemberGroup(
    val index: Int,
    val members: List<GroupMember>,
) {
    val size: Int get() = members.size

    /** 组头文案：`第 3 组`。 */
    val label: String get() = "第 $index 组"
}

/**
 * 分组参数快照（导出卡「参数配置」区 + 结果页摘要用）。
 *
 * 文案与网页/小程序 `cfg` 对齐：
 * `source=上一步抽签序号|班级名单（N 人）`、`mode=按组数(N 组)|按每组人数(N 人)`、
 * `inner=打乱后随机分配|按原顺序轮流分配`。
 */
data class GroupConfigSnapshot(
    val sourceLabel: String,
    val modeLabel: String,
    val total: Int,
    val groupCount: Int,
    val innerLabel: String,
)

/** 一次分组结果。 */
data class GroupResult(
    val groups: List<MemberGroup>,
    val config: GroupConfig,
    val snapshot: GroupConfigSnapshot,
) {
    val isEmpty: Boolean get() = groups.isEmpty()
    val totalMembers: Int get() = groups.sumOf { it.size }
}

/** 分组向导步骤（配置 → 分组结果；分组本身是抽签三步的第 2 步）。 */
enum class GroupStep {
    CONFIG,
    RESULT,
}

/** 向导状态。 */
data class GroupWorkflowState(
    val step: GroupStep = GroupStep.CONFIG,
    val config: GroupConfig = GroupConfig(),
    val result: GroupResult? = null,
)

/** 分组错误分类。 */
enum class GroupErrorCode {
    /** 来源没有成员（未抽签 / 未选名单）。 */
    NO_MEMBERS,

    /** 成员不足 2 人。 */
    MEMBERS_TOO_FEW,

    /** 组数/每组人数非法。 */
    COUNT_INVALID,

    /** 班级名单未选择。 */
    ROSTER_EMPTY,

    /** 非法步骤切换。 */
    INVALID_STEP,
}

/** 领域错误：code 用于逻辑分支，message 可直接展示。 */
data class GroupError(
    val code: GroupErrorCode,
    val message: String,
)

/** 校验结果。 */
sealed interface GroupValidation {
    data class Valid(val members: List<GroupMember>) : GroupValidation
    data class Invalid(val error: GroupError) : GroupValidation
}

/** 状态迁移结果。 */
sealed interface GroupTransition {
    data class Next(val state: GroupWorkflowState) : GroupTransition
    data class Rejected(val error: GroupError) : GroupTransition
}
