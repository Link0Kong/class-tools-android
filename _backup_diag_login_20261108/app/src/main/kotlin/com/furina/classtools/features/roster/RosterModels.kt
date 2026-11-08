package com.furina.classtools.features.roster

import com.furina.classtools.data.member.ClassMember

/**
 * 名册 / 点名领域模型（纯 Kotlin）。
 *
 * 契约：`docs/api/USERDATA_V6.md` §1.2 的 `roster` section（网页/小程序 v6）：
 * ```jsonc
 * roster = { "rosterSel":[1,3], "rollSel":[1,2], "rollSt":{"0":"in","1":"ab"}, "rollNames":"1 张三\n2 李四" }
 * ```
 * - `rosterSel` = 抽签参与名单座号；`rollSel` = 点名名单座号（升序去重、1..99）；
 * - `rollSt` = 点名状态，key = **rollSel 排序后的下标（0-based 字符串）**，value = `in`/`ab`；
 * - 名单变化时按**姓名**迁移状态（对齐 mp `roster.js writeRoll`），避免座号重排错位；
 * - 名单只展示姓名/座号/职务/徽章，**不显示学号**；不导出名单。
 */

/** 名册成员（来自 `class_members`，已去掉学号）。 */
data class RosterMember(
    val seat: Int,
    val name: String,
    val role: String = "",
    val badge: String = "",
    val isAdmin: Boolean = false,
) {
    val isCommittee: Boolean get() = role.isNotBlank()

    companion object {
        fun from(member: ClassMember): RosterMember = RosterMember(
            seat = member.seat,
            name = member.name,
            role = member.role,
            badge = member.badge,
            isAdmin = member.isAdmin,
        )
    }
}

/** 点名状态（云端 wire = `in`/`ab`；兼容中文「到/假」）。 */
enum class RollStatus(val wire: String, val label: String) {
    IN("in", "到"),
    AB("ab", "假"),
    ;

    companion object {
        fun fromWire(raw: String?): RollStatus? = when (raw?.trim()?.lowercase()) {
            "in", "到", "present", "1", "true" -> IN
            "ab", "假", "absent", "0", "false" -> AB
            else -> null
        }
    }
}

/** 名册筛选。 */
enum class RosterFilter(val label: String) {
    ALL("全部"),
    COMMITTEE("班委"),
    ROLL("点名名单"),
    ;

    companion object {
        val all: List<RosterFilter> get() = entries
    }
}

/** 名册页签。 */
enum class RosterTab(val label: String) {
    ROSTER("名册"),
    ROLL("点名"),
}

/**
 * userdata.roster 的内存态。
 *
 * @param rollStatus key = rollSel 排序后下标；非法 key 由 [RosterCodec] 丢弃。
 * @param rollNamesText 云端派生的「座号 姓名」文本（写回时重新生成）。
 */
data class RosterState(
    val rosterSel: List<Int> = emptyList(),
    val rollSel: List<Int> = emptyList(),
    val rollStatus: Map<Int, RollStatus> = emptyMap(),
    val rollNamesText: String = "",
)

/** 点名统计。 */
data class RollStats(
    val total: Int = 0,
    val handled: Int = 0,
    val present: Int = 0,
    val absent: Int = 0,
    val pending: Int = 0,
) {
    val completed: Boolean get() = total > 0 && pending == 0
}

/** 点名网格一项。 */
data class RollItem(
    val index: Int,
    val seat: Int,
    val member: RosterMember?,
    val status: RollStatus?,
) {
    val name: String get() = member?.name.orEmpty().ifBlank { "#$seat" }
    val handled: Boolean get() = status != null
}

/** 名册搜索/筛选结果条目。 */
data class RosterRow(
    val member: RosterMember,
    val inRoster: Boolean,
    val inRoll: Boolean,
)
