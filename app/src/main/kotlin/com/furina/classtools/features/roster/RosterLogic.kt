package com.furina.classtools.features.roster

import com.furina.classtools.data.member.ClassMember

/**
 * 名册 / 点名纯逻辑：
 * - 名单来源归一（去学号、按座号升序）；
 * - 搜索/筛选；
 * - 点名名单变更时**按姓名迁移状态**（对齐 mp `roster.js writeRoll`），并同步 `rollNames` 派生文本；
 * - 到/假切换（未处理 → 到 → 假 → 未处理）、重置本轮、统计、网格排序（未处理在前）。
 */
object RosterLogic {

    fun membersOf(members: List<ClassMember>): List<RosterMember> = members
        .map { RosterMember.from(it) }
        .filter { it.seat in 1..99 && it.name.isNotBlank() }
        .distinctBy { it.seat }
        .sortedBy { it.seat }

    fun normalizeSeats(input: Iterable<Int?>): List<Int> = RosterCodec.normalizeSeats(input)

    fun seatByName(members: List<RosterMember>): Map<Int, String> =
        members.associate { it.seat to it.name }

    fun nameMapOf(members: List<RosterMember>): Map<Int, String> =
        members.associate { it.seat to it.name }

    /** `座号 姓名` 多行文本（写回 `rollNames`；页面不展示学号）。 */
    fun buildRollNames(seats: List<Int>, members: List<RosterMember>): String {
        val nameBySeat = nameMapOf(members)
        return RosterCodec.normalizeSeats(seats).joinToString("\n") { seat ->
            val name = nameBySeat[seat].orEmpty()
            if (name.isBlank()) "$seat" else "$seat $name"
        }
    }

    /** 从旧 `rollNames` 文本解析「座号 → 姓名」（兜底：名单未加载时也能按姓名迁移）。 */
    fun namesFromText(text: String): Map<Int, String> {
        val out = LinkedHashMap<Int, String>()
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEachIndexed
            val space = line.indexOf(' ')
            if (space > 0) {
                val seat = line.substring(0, space).toIntOrNull()
                val name = line.substring(space + 1).trim()
                if (seat != null && seat in 1..99 && name.isNotEmpty()) {
                    out[seat] = name
                    return@forEachIndexed
                }
            }
            if (index in 0..98) out[index + 1] = line
        }
        return out
    }

    /** 旧状态按姓名索引（用于班级名单变化时迁移）。 */
    fun statusByName(state: RosterState, members: List<RosterMember>): Map<String, RollStatus> {
        val nameBySeat = nameMapOf(members)
        val parsed = namesFromText(state.rollNamesText)
        val out = LinkedHashMap<String, RollStatus>()
        for ((index, seat) in state.rollSel.withIndex()) {
            val status = state.rollStatus[index] ?: continue
            val name = nameBySeat[seat] ?: parsed[seat] ?: continue
            out[name] = status
        }
        return out
    }

    /** 设置点名名单（按姓名保留旧状态，重建 rollNames）。 */
    fun withRollSel(
        state: RosterState,
        seats: Iterable<Int?>,
        members: List<RosterMember>,
    ): RosterState {
        val normalized = normalizeSeats(seats)
        val byName = statusByName(state, members)
        val nameBySeat = nameMapOf(members)
        val parsed = namesFromText(state.rollNamesText)
        val statuses = LinkedHashMap<Int, RollStatus>()
        normalized.forEachIndexed { index, seat ->
            val name = nameBySeat[seat] ?: parsed[seat]
            if (name != null) {
                byName[name]?.let { statuses[index] = it }
            }
        }
        return state.copy(
            rollSel = normalized,
            rollStatus = statuses,
            rollNamesText = buildRollNames(normalized, members),
        )
    }

    fun withRosterSel(state: RosterState, seats: Iterable<Int?>): RosterState =
        state.copy(rosterSel = normalizeSeats(seats))

    /** 全选当前名册加入点名名单。 */
    fun selectAll(state: RosterState, members: List<RosterMember>): RosterState =
        withRollSel(state, members.map { it.seat }, members)

    fun clearRoll(state: RosterState): RosterState =
        state.copy(rollSel = emptyList(), rollStatus = emptyMap(), rollNamesText = "")

    /** 重置本轮：保留名单，清空到/假。 */
    fun resetRoll(state: RosterState): RosterState =
        state.copy(rollStatus = emptyMap())

    /** 到/假切换：未处理 → 到 → 假 → 未处理。 */
    fun toggleStatus(state: RosterState, index: Int): RosterState {
        if (index !in state.rollSel.indices) return state
        val current = state.rollStatus[index]
        val statuses = LinkedHashMap(state.rollStatus)
        when (current) {
            null -> statuses[index] = RollStatus.IN
            RollStatus.IN -> statuses[index] = RollStatus.AB
            RollStatus.AB -> statuses.remove(index)
        }
        return state.copy(rollStatus = statuses)
    }

    /** 直接设置状态（到/假/未处理）。 */
    fun setStatus(state: RosterState, index: Int, status: RollStatus?): RosterState {
        if (index !in state.rollSel.indices) return state
        val statuses = LinkedHashMap(state.rollStatus)
        if (status == null) statuses.remove(index) else statuses[index] = status
        return state.copy(rollStatus = statuses)
    }

    fun stats(state: RosterState): RollStats {
        val total = state.rollSel.size
        var present = 0
        var absent = 0
        for (index in state.rollSel.indices) {
            when (state.rollStatus[index]) {
                RollStatus.IN -> present += 1
                RollStatus.AB -> absent += 1
                null -> Unit
            }
        }
        val handled = present + absent
        return RollStats(
            total = total,
            handled = handled,
            present = present,
            absent = absent,
            pending = maxOf(0, total - handled),
        )
    }

    /** 点名网格：未处理在前，已处理沉底（各自按座号升序）；`handledLast=false` 时纯座号序。 */
    fun rollItems(
        state: RosterState,
        members: List<RosterMember>,
        handledLast: Boolean = true,
    ): List<RollItem> {
        val memberBySeat = members.associateBy { it.seat }
        val items = state.rollSel.mapIndexed { index, seat ->
            RollItem(
                index = index,
                seat = seat,
                member = memberBySeat[seat],
                status = state.rollStatus[index],
            )
        }
        return if (handledLast) {
            items.sortedWith(
                compareBy<RollItem> { it.handled }.thenBy { it.seat },
            )
        } else {
            items
        }
    }

    /** 名册搜索/筛选行。 */
    fun filterRows(
        members: List<RosterMember>,
        query: String,
        filter: RosterFilter,
        state: RosterState,
    ): List<RosterRow> {
        val q = query.trim().lowercase()
        val rollSet = state.rollSel.toSet()
        return members
            .asSequence()
            .filter { member ->
                when (filter) {
                    RosterFilter.ALL -> true
                    RosterFilter.COMMITTEE -> member.isCommittee
                    RosterFilter.ROLL -> member.seat in rollSet
                }
            }
            .filter { member ->
                member.asEntry().matches(q)
            }
            .map { member ->
                RosterRow(
                    member = member,
                    inRoster = member.seat in state.rosterSel.toSet(),
                    inRoll = member.seat in rollSet,
                )
            }
            .toList()
    }

    /** 从名册成员生成点名名单（名册页「加入点名」勾选用）。 */
    fun toggleMemberInRoll(
        state: RosterState,
        seat: Int,
        members: List<RosterMember>,
    ): RosterState {
        val seats = state.rollSel.toMutableList()
        if (seat in seats) seats.remove(seat) else seats.add(seat)
        return withRollSel(state, seats, members)
    }
}
