package com.furina.classtools.data.member

import com.furina.classtools.data.network.ApiResult
import java.io.IOException

/** 提供当前账号有权查看的分类名单，UI 只接收去掉学号的条目。 */
fun interface RosterMemberSource {
    suspend fun load(category: MemberCategory): List<MemberEntry>
    suspend fun searchSeats(query: String): Set<Int> = emptySet()
}

class ClassRosterSource(
    private val members: ClassMembersRepository,
    private val league: ClassLeagueRepository,
) : RosterMemberSource {
    override suspend fun searchSeats(query: String): Set<Int> =
        (members.searchSeats(query) as? ApiResult.Success)?.value.orEmpty()

    override suspend fun load(category: MemberCategory): List<MemberEntry> {
        val roster = when (val result = members.load()) {
            is ClassMembersResult.Success -> result.members
            is ClassMembersResult.Failure -> throw IOException(result.message)
        }
        return when (category) {
            MemberCategory.ALL -> MemberCategoryBuilder.all(roster)
            MemberCategory.COMMITTEE -> MemberCategoryBuilder.committee(roster)
            MemberCategory.LEAGUE -> when (val result = league.load()) {
                is ClassLeagueResult.Success -> {
                    val seats = result.members.mapTo(hashSetOf()) { it.seat }
                    MemberCategoryBuilder.all(roster).filter { it.seat in seats }
                }
                is ClassLeagueResult.Failure -> throw IOException(result.message)
            }
        }
    }
}
