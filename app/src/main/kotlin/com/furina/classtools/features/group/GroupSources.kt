package com.furina.classtools.features.group

import com.furina.classtools.features.draw.DrawLastResult

/**
 * 分组「最近抽签」成员解析（纯 Kotlin，可单测）。
 *
 * 数据流：抽签成功 → 本机 [com.furina.classtools.features.draw.DrawLastResultStore]
 * → 分组页 PREV 来源按序号数组还原成员。
 *
 * 名称优先级（契约 §3）：班级名单派生名 > 内存兜底姓名 > 空（安卓暂无名称映射）。
 * 存储里没有结果（老数据 `source=prev`、首次安装等）时返回 [fallback]（可为空），
 * 由 [GroupValidator] 给出「还没有抽签结果…」的友好提示，不崩溃。
 */
object GroupMemberResolver {

    /** 将本机最近抽签序号解析为分组可用的成员列表。 */
    fun fromLastDraw(
        last: DrawLastResult?,
        roster: List<GroupMember> = emptyList(),
        fallback: List<GroupMember> = emptyList(),
    ): List<GroupMember> {
        val seats = last?.result.orEmpty()
            .filter { it > 0 }
            .distinct()
        if (seats.isEmpty()) {
            return fallback.filter { it.seat > 0 }.distinctBy { it.seat }
        }
        val rosterBySeat = roster.associateBy { it.seat }
        val fallbackBySeat = fallback.associateBy { it.seat }
        return seats.map { seat ->
            val name = rosterBySeat[seat]?.name?.takeIf { it.isNotBlank() }
                ?: fallbackBySeat[seat]?.name.orEmpty()
            GroupMember(seat = seat, name = name)
        }
    }

    /** 全班座号（升序去重；「全班」= NAMES + 全选，不新增 source 值）。 */
    fun allClassSeats(roster: List<GroupMember>): List<Int> =
        roster.map { it.seat }.filter { it > 0 }.distinct().sorted()
}
