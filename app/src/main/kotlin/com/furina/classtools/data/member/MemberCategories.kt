package com.furina.classtools.data.member

/**
 * 名单三分类（对齐网页/小程序「名单」工具三 tab：班委 / 团员 / 全班）。
 *
 * 展示模型 [MemberEntry] **不含学号**（只有座号/姓名/badge），满足隐私红线。
 */
enum class MemberCategory(val label: String) {
    COMMITTEE("班委"),
    LEAGUE("团员"),
    ALL("全班"),
}

/** 名单展示条目（无 sid）。 */
data class MemberEntry(
    val seat: Int,
    val name: String,
    /** 展示徽章：班委取 role/badge；团员可空。 */
    val badge: String = "",
    val membershipBadge: String = "",
) {
    fun matches(query: String): Boolean {
        val fields = listOf(name, badge, membershipBadge)
        return query.trim().split(Regex("\\s+")).all { term ->
            term.isEmpty() || term.toIntOrNull() == seat || fields.any { it.contains(term, ignoreCase = true) }
        }
    }
}

/** 三分类计数（UI tab 徽标）。 */
data class MemberCategoryCounts(
    val committee: Int = 0,
    val league: Int = 0,
    val all: Int = 0,
) {
    fun of(category: MemberCategory): Int = when (category) {
        MemberCategory.COMMITTEE -> committee
        MemberCategory.LEAGUE -> league
        MemberCategory.ALL -> all
    }
}

/** 名单分类纯逻辑（可单测；不联网、不含学号）。 */
object MemberCategoryBuilder {

    /**
     * 班委判定与服务端 `auth_is_committee()` 同口径：`role` 非空 **或** `badge` 含「班委」。
     * （测试身份已被服务端过滤，不在 `class_members` 里）
     */
    fun isCommittee(member: ClassMember): Boolean =
        member.role.isNotBlank() || member.badge.contains("班委")

    /** 班委名单（按座号升序；badge 优先 role，均无则「班委」）。 */
    fun committee(members: List<ClassMember>): List<MemberEntry> =
        members.asSequence()
            .filter { isCommittee(it) }
            .map { member ->
                MemberEntry(
                    seat = member.seat,
                    name = member.name.trim(),
                    badge = member.role.trim().ifBlank { member.badge.trim() }.ifBlank { "班委" },
                    membershipBadge = member.badge,
                )
            }
            .filter { it.seat > 0 && it.name.isNotEmpty() }
            .sortedBy { it.seat }
            .toList()

    /** 团员名单（来自 [ClassLeagueRepository]，无 badge）。 */
    fun league(members: List<LeagueMember>): List<MemberEntry> =
        members.asSequence()
            .filter { it.seat > 0 && it.name.isNotBlank() }
            .map { MemberEntry(seat = it.seat, name = it.name.trim()) }
            .distinctBy { it.seat }
            .sortedBy { it.seat }
            .toList()

    /** 全班名单（座号升序；badge 用 role/badge，无则不显示）。 */
    fun all(members: List<ClassMember>): List<MemberEntry> =
        members.asSequence()
            .filter { it.seat > 0 && it.name.isNotBlank() }
            .map { member ->
                MemberEntry(
                    seat = member.seat,
                    name = member.name.trim(),
                    badge = member.role.trim().ifBlank { member.badge.trim() },
                    membershipBadge = member.badge,
                )
            }
            .distinctBy { it.seat }
            .sortedBy { it.seat }
            .toList()

    /** 按分类取名单；数据不可用时返回空列表（由 UI 显示空态）。 */
    fun entries(
        category: MemberCategory,
        classMembers: List<ClassMember>,
        leagueMembers: List<LeagueMember>,
    ): List<MemberEntry> = when (category) {
        MemberCategory.COMMITTEE -> committee(classMembers)
        MemberCategory.LEAGUE -> league(leagueMembers)
        MemberCategory.ALL -> all(classMembers)
    }

    /** 三分类计数（用于 tab 徽标；不加载名单时传空列表）。 */
    fun counts(
        classMembers: List<ClassMember>,
        leagueMembers: List<LeagueMember>,
    ): MemberCategoryCounts = MemberCategoryCounts(
        committee = committee(classMembers).size,
        league = league(leagueMembers).size,
        all = all(classMembers).size,
    )

    /** 姓名搜索（大小写/空格不敏感；空查询返回全部）。 */
    fun search(entries: List<MemberEntry>, query: String): List<MemberEntry> {
        val q = query.trim()
        if (q.isEmpty()) return entries
        return entries.filter { it.matches(q) }
    }

    /** 方便 UI：按分类 + 搜索过滤。 */
    fun visibleEntries(
        category: MemberCategory,
        classMembers: List<ClassMember>,
        leagueMembers: List<LeagueMember>,
        query: String = "",
    ): List<MemberEntry> = search(entries(category, classMembers, leagueMembers), query)
}
