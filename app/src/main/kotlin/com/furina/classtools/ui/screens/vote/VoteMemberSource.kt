package com.furina.classtools.ui.screens.vote

import com.furina.classtools.data.member.MemberCategory
import com.furina.classtools.data.member.MemberEntry

/**
 * 投票「指定成员」可见范围的数据源（UI 只依赖该接口；Lead 在接线时用
 * `ClassMembersRepository` + `ClassLeagueRepository` + `MemberCategoryBuilder` 实现）。
 *
 * 实现建议（Lead 接线，不在本文件夹）：
 * ```kotlin
 * val voteMemberSource = object : VoteMemberSource {
 *     override suspend fun load(category: MemberCategory): List<MemberEntry> = when (category) {
 *         MemberCategory.COMMITTEE -> when (val r = classMembersRepository.load()) {
 *             is ClassMembersResult.Success -> MemberCategoryBuilder.committee(r.members)
 *             is ClassMembersResult.Failure -> emptyList()
 *         }
 *         MemberCategory.LEAGUE -> when (val r = classLeagueRepository.load()) {
 *             is ClassLeagueResult.Success -> MemberCategoryBuilder.league(r.members)
 *             is ClassLeagueResult.Failure -> emptyList()
 *         }
 *         MemberCategory.ALL -> when (val r = classMembersRepository.load()) {
 *             is ClassMembersResult.Success -> MemberCategoryBuilder.all(r.members)
 *             is ClassMembersResult.Failure -> emptyList()
 *         }
 *     }
 * }
 * ```
 *
 * 隐私红线：只返回 `MemberEntry`（座号/姓名/badge），**不含学号**。
 */
interface VoteMemberSource {
    suspend fun load(category: MemberCategory): List<MemberEntry>
}
