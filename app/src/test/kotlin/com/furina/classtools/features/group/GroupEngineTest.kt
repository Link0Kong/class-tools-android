package com.furina.classtools.features.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分组引擎/校验/状态机单测（与 web/mp `doGroup` 同算法）。
 */
class GroupEngineTest {

    private fun members(count: Int): List<GroupMember> =
        (1..count).map { seat -> GroupMember(seat = seat, name = "同学$seat") }

    // ---- groupCount ---------------------------------------------------------

    @Test
    fun groupCount_groupsMode_clampsToMemberCount() {
        val base = GroupConfig(mode = GroupMode.GROUPS, n = 10)
        assertEquals(3, GroupEngine.groupCount(3, base))
        assertEquals(10, GroupEngine.groupCount(20, base))
        // n<2 clamp 到 2
        assertEquals(2, GroupEngine.groupCount(5, base.copy(n = 0)))
        assertEquals(0, GroupEngine.groupCount(0, base))
    }

    @Test
    fun groupCount_perGroupMode_ceils() {
        val base = GroupConfig(mode = GroupMode.PER_GROUP, n = 4)
        assertEquals(3, GroupEngine.groupCount(10, base)) // ceil(10/4)
        assertEquals(1, GroupEngine.groupCount(4, base))
        assertEquals(3, GroupEngine.groupCount(9, base))
    }

    // ---- 均衡 / 顺序 / 打乱 ---------------------------------------------------

    @Test
    fun group_roundRobin_balancedSizes() {
        val result = GroupEngine.group(members(10), GroupConfig(mode = GroupMode.GROUPS, n = 3), SeededGroupRandom(1))
        assertEquals(3, result.groups.size)
        val sizes = result.groups.map { it.size }
        assertEquals(4, sizes.maxOrNull())
        assertEquals(3, sizes.minOrNull())
        assertEquals(10, result.totalMembers)
    }

    @Test
    fun group_noShuffle_assignsInOrderRoundRobin() {
        val result = GroupEngine.group(members(7), GroupConfig(mode = GroupMode.GROUPS, n = 3), SeededGroupRandom(42))
        // 轮流转：g1=m1,m4,m7; g2=m2,m5; g3=m3,m6
        assertEquals(listOf(1, 4, 7), result.groups[0].members.map { it.seat })
        assertEquals(listOf(2, 5), result.groups[1].members.map { it.seat })
        assertEquals(listOf(3, 6), result.groups[2].members.map { it.seat })
    }

    @Test
    fun group_perGroupMode_sizesWithinPerGroup() {
        val config = GroupConfig(mode = GroupMode.PER_GROUP, n = 4)
        val result = GroupEngine.group(members(10), config, SeededGroupRandom(7))
        assertEquals(3, result.groups.size)
        result.groups.forEach { group -> assertTrue(group.size <= 4) }
        assertEquals(10, result.totalMembers)
    }

    @Test
    fun group_shuffle_sameSeedReproducible() {
        val config = GroupConfig(mode = GroupMode.GROUPS, n = 3, shuffle = true)
        val a = GroupEngine.group(members(10), config, SeededGroupRandom(2026))
        val b = GroupEngine.group(members(10), config, SeededGroupRandom(2026))
        assertEquals(a.groups.map { it.members.map { m -> m.seat } }, b.groups.map { it.members.map { m -> m.seat } })
    }

    @Test
    fun group_shuffle_preservesMembership() {
        val config = GroupConfig(mode = GroupMode.GROUPS, n = 4, shuffle = true)
        val result = GroupEngine.group(members(17), config, SeededGroupRandom(9))
        val seats = result.groups.flatMap { group -> group.members.map { it.seat } }.sorted()
        assertEquals((1..17).toList(), seats)
    }

    @Test
    fun group_differentSeedsUsuallyDiffer() {
        val config = GroupConfig(mode = GroupMode.GROUPS, n = 3, shuffle = true)
        val a = GroupEngine.group(members(12), config, SeededGroupRandom(1))
        val b = GroupEngine.group(members(12), config, SeededGroupRandom(2))
        assertFalse(
            a.groups.map { it.members.map { m -> m.seat } } == b.groups.map { it.members.map { m -> m.seat } },
        )
    }

    @Test
    fun shuffle_doesNotMutateInput() {
        val input = members(6)
        val copy = input.map { it.copy() }
        GroupEngine.shuffle(input, SeededGroupRandom(3))
        assertEquals(copy, input)
    }

    @Test
    fun group_emptyMembers_returnsEmpty() {
        val result = GroupEngine.group(emptyList(), GroupConfig(), SeededGroupRandom(1))
        assertTrue(result.groups.isEmpty())
        assertEquals(0, result.snapshot.groupCount)
    }

    // ---- 快照 ---------------------------------------------------------------

    @Test
    fun snapshot_labelsMatchWebVocabulary() {
        val result = GroupEngine.group(
            members(9),
            GroupConfig(mode = GroupMode.GROUPS, n = 3, shuffle = true),
            SeededGroupRandom(1),
        )
        assertEquals("上一步抽签序号", result.snapshot.sourceLabel)
        assertEquals("按组数(3 组)", result.snapshot.modeLabel)
        assertEquals("打乱后随机分配", result.snapshot.innerLabel)
        assertEquals(9, result.snapshot.total)

        val names = GroupEngine.group(
            members(5),
            GroupConfig(source = GroupSource.NAMES, mode = GroupMode.PER_GROUP, n = 2),
            SeededGroupRandom(1),
        )
        assertEquals("班级名单（5 人）", names.snapshot.sourceLabel)
        assertEquals("按每组人数(2 人)", names.snapshot.modeLabel)
        assertEquals("按原顺序轮流分配", names.snapshot.innerLabel)
    }

    // ---- 校验 / 状态机 -------------------------------------------------------

    @Test
    fun validate_rejectsEmptyAndTooFew() {
        val invalidEmpty = GroupValidator.validate(GroupConfig(), emptyList())
        assertFalse(invalidEmpty is GroupValidation.Valid)
        assertEquals(
            GroupErrorCode.NO_MEMBERS,
            (invalidEmpty as GroupValidation.Invalid).error.code,
        )

        val invalidFew = GroupValidator.validate(GroupConfig(), members(1))
        assertEquals(
            GroupErrorCode.MEMBERS_TOO_FEW,
            (invalidFew as GroupValidation.Invalid).error.code,
        )

        val invalidRoster = GroupValidator.validate(
            GroupConfig(source = GroupSource.NAMES),
            members(3),
            rosterSelected = false,
        )
        assertEquals(
            GroupErrorCode.ROSTER_EMPTY,
            (invalidRoster as GroupValidation.Invalid).error.code,
        )
    }

    @Test
    fun validate_countBounds() {
        val badGroups = GroupValidator.validate(GroupConfig(mode = GroupMode.GROUPS, n = 1), members(5))
        assertEquals(
            GroupErrorCode.COUNT_INVALID,
            (badGroups as GroupValidation.Invalid).error.code,
        )
        val badPer = GroupValidator.validate(GroupConfig(mode = GroupMode.PER_GROUP, n = 0), members(5))
        assertEquals(
            GroupErrorCode.COUNT_INVALID,
            (badPer as GroupValidation.Invalid).error.code,
        )
        // GROUPS 模式 n 大于成员数：允许（clamp）
        assertTrue(GroupValidator.validate(GroupConfig(mode = GroupMode.GROUPS, n = 9), members(5)) is GroupValidation.Valid)
    }

    @Test
    fun workflow_start_movesToResult() {
        val start = GroupWorkflowState()
        val transition = GroupWorkflow.start(start, members(7), SeededGroupRandom(1))
        assertTrue(transition is GroupTransition.Next)
        val next = (transition as GroupTransition.Next).state
        assertEquals(GroupStep.RESULT, next.step)
        assertNotNull(next.result)
    }

    @Test
    fun workflow_start_rejectedLeavesConfigStep() {
        val start = GroupWorkflowState()
        val transition = GroupWorkflow.start(start, emptyList(), SeededGroupRandom(1))
        assertTrue(transition is GroupTransition.Rejected)
        assertEquals(1, 1)
    }

    @Test
    fun workflow_updateConfig_returnsToConfigAndKeepsResult() {
        val configured = GroupWorkflowState(config = GroupConfig(n = 3))
        val next = (GroupWorkflow.start(configured, members(9), SeededGroupRandom(1)) as GroupTransition.Next).state
        val edited = GroupWorkflow.updateConfig(next, next.config.copy(shuffle = true))
        assertEquals(GroupStep.CONFIG, edited.step)
        assertNotNull(edited.result)
    }

    @Test
    fun workflow_backAndOpenResult() {
        val configured = GroupWorkflowState(config = GroupConfig(n = 3))
        val result = (GroupWorkflow.start(configured, members(9), SeededGroupRandom(1)) as GroupTransition.Next).state
        val back = GroupWorkflow.back(result)
        assertEquals(GroupStep.CONFIG, back.step)
        val reopened = GroupWorkflow.openResult(back)
        assertTrue(reopened is GroupTransition.Next)

        val rejected = GroupWorkflow.openResult(GroupWorkflowState())
        assertTrue(rejected is GroupTransition.Rejected)
        assertEquals("还没有分组结果", (rejected as GroupTransition.Rejected).error.message)
    }

    @Test
    fun workflow_regroup_noShuffleKeepsSameResult() {
        val configured = GroupWorkflowState(config = GroupConfig(mode = GroupMode.GROUPS, n = 3))
        val first = (GroupWorkflow.start(configured, members(6), SeededGroupRandom(1)) as GroupTransition.Next).state
        val second = (GroupWorkflow.regroup(first, members(6), SeededGroupRandom(99)) as GroupTransition.Next).state
        assertEquals(
            first.result!!.groups.map { it.members.map { m -> m.seat } },
            second.result!!.groups.map { it.members.map { m -> m.seat } },
        )
    }

    @Test
    fun workflow_regroup_withShuffleVariesWithSeed() {
        val configured = GroupWorkflowState(config = GroupConfig(mode = GroupMode.GROUPS, n = 3, shuffle = true))
        val first = (GroupWorkflow.start(configured, members(10), SeededGroupRandom(1)) as GroupTransition.Next).state
        val second = (GroupWorkflow.regroup(first, members(10), SeededGroupRandom(2)) as GroupTransition.Next).state
        assertFalse(
            first.result!!.groups.map { it.members.map { m -> m.seat } } ==
                second.result!!.groups.map { it.members.map { m -> m.seat } },
        )
    }

    @Test
    fun member_labelFormats() {
        assertEquals("7 张伟", GroupMember(7, "张伟").label)
        assertEquals("7", GroupMember(7).label)
        assertEquals("第 2 组", MemberGroup(2, emptyList()).label)
    }
}

