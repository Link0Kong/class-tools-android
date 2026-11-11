package com.furina.classtools.features.roster

import com.furina.classtools.data.member.ClassMember
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterLogicTest {

    private fun members(): List<RosterMember> = listOf(
        RosterMember(1, "张三", "班长", "班委"),
        RosterMember(2, "李四", "团支书", "班委"),
        RosterMember(3, "王五", "", ""),
        RosterMember(4, "赵六", "", ""),
    )

    private fun json(text: String) = Json.parseToJsonElement(text)

    // ------------------------------------------------------------ 编解码

    @Test
    fun `null 或坏值解码为默认态`() {
        assertEquals(RosterState(), RosterCodec.fromJson(null))
        assertEquals(RosterState(), RosterCodec.fromJson(json("[]")))
        assertEquals(RosterState(), RosterCodec.fromJson(json("{}")))
    }

    @Test
    fun `脏数据归一 座号去重升序 状态越界丢弃 兼容中文到假`() {
        val state = RosterCodec.fromJson(
            json(
                """{"rosterSel":[0,1,1,99,999,"x"],
                   "rollSel":[3,2,2],
                   "rollSt":{"0":"到","1":"假","5":"in","x":"in","-1":"ab"},
                   "rollNames":"3 王五\n2 李四"}""",
            ),
        )
        assertEquals(listOf(1, 99), state.rosterSel)
        assertEquals(listOf(2, 3), state.rollSel)
        assertEquals(RollStatus.IN, state.rollStatus[0])
        assertEquals(RollStatus.AB, state.rollStatus[1])
        assertEquals(2, state.rollStatus.size)
        assertEquals("3 王五\n2 李四", state.rollNamesText)
    }

    @Test
    fun `编码往返一致且丢弃越界状态`() {
        val state = RosterState(
            rosterSel = listOf(1, 2, 3),
            rollSel = listOf(2, 3),
            rollStatus = mapOf(0 to RollStatus.IN, 1 to RollStatus.AB, 9 to RollStatus.IN),
            rollNamesText = "2 李四\n3 王五",
        )
        val encoded = RosterCodec.toJson(state)
        val rollSt = encoded["rollSt"] as JsonObject
        assertEquals(2, rollSt.size)
        assertFalse(rollSt.containsKey("9"))
        assertEquals(state.copy(rollStatus = mapOf(0 to RollStatus.IN, 1 to RollStatus.AB)), RosterCodec.fromJson(encoded))
    }

    @Test
    fun `空 section 编码为空数组与空状态`() {
        val encoded = RosterCodec.toJson(RosterState())
        assertEquals("[]", encoded["rosterSel"].toString())
        assertEquals("[]", encoded["rollSel"].toString())
        assertEquals("{}", encoded["rollSt"].toString())
        assertEquals("", (encoded["rollNames"] as kotlinx.serialization.json.JsonPrimitive).content)
    }

    // ------------------------------------------------------------ 名单归一/搜索

    @Test
    fun `班级名单归一 过滤非法座号与空姓名 去学号`() {
        val raw = listOf(
            ClassMember(seat = 2, sid = "s2", name = "李四", role = "团支书", badge = "班委"),
            ClassMember(seat = 0, sid = "s0", name = "无效"),
            ClassMember(seat = 1, sid = "s1", name = "张三", role = "班长", badge = "班委"),
            ClassMember(seat = 999, sid = "s999", name = "超界"),
            ClassMember(seat = 1, sid = "s1", name = "张三"),
            ClassMember(seat = 3, sid = "s3", name = "  ", role = ""),
        )
        val members = RosterLogic.membersOf(raw)
        assertEquals(listOf(1, 2), members.map { it.seat })
        assertEquals("张三", members.first().name)
        assertTrue(members.first().isCommittee)
        assertFalse(members.any { it.seat == 0 || it.seat == 999 })
    }

    @Test
    fun `座号归一`() {
        assertEquals(listOf(1, 3, 5), RosterLogic.normalizeSeats(listOf(5, 3, 1, 3, null, 0, 100)))
        assertEquals(emptyList<Int>(), RosterLogic.normalizeSeats(emptyList()))
    }

    @Test
    fun `搜索与筛选`() {
        val members = members()
        val state = RosterState(rollSel = listOf(1, 2), rosterSel = listOf(3))
        assertEquals(4, RosterLogic.filterRows(members, "", RosterFilter.ALL, state).size)
        assertEquals(1, RosterLogic.filterRows(members, "李四", RosterFilter.ALL, state).size)
        assertEquals(1, RosterLogic.filterRows(members, "2", RosterFilter.ALL, state).size)
        assertEquals(2, RosterLogic.filterRows(members, "", RosterFilter.COMMITTEE, state).size)
        assertEquals(2, RosterLogic.filterRows(members, "", RosterFilter.ROLL, state).size)
        assertEquals(1, RosterLogic.filterRows(members, "团支书", RosterFilter.ROLL, state).size)
        val row = RosterLogic.filterRows(members, "赵六", RosterFilter.ALL, state).single()
        assertFalse(row.inRoll)
        assertFalse(row.inRoster)
    }

    @Test
    fun `rollNames 生成与解析`() {
        val members = members()
        val text = RosterLogic.buildRollNames(listOf(2, 1), members)
        assertEquals("1 张三\n2 李四", text)
        assertEquals(mapOf(1 to "张三", 2 to "李四"), RosterLogic.namesFromText("1 张三\n2 李四"))
        assertEquals(mapOf(1 to "张三"), RosterLogic.namesFromText("张三"))
    }

    // ------------------------------------------------------------ 点名状态

    @Test
    fun `设置点名名单时按姓名保留状态`() {
        val members = members()
        val old = RosterState(
            rollSel = listOf(1, 2),
            rollStatus = mapOf(0 to RollStatus.IN, 1 to RollStatus.AB),
            rollNamesText = "1 张三\n2 李四",
        )
        val next = RosterLogic.withRollSel(old, listOf(2, 3), members)
        assertEquals(listOf(2, 3), next.rollSel)
        assertEquals(RollStatus.AB, next.rollStatus[0]) // 李四 原「假」
        assertNull(next.rollStatus[1])
        assertEquals("2 李四\n3 王五", next.rollNamesText)
    }

    @Test
    fun `到假切换 未处理到 到 假 未处理`() {
        var state = RosterState(rollSel = listOf(1, 2))
        state = RosterLogic.toggleStatus(state, 0)
        assertEquals(RollStatus.IN, state.rollStatus[0])
        state = RosterLogic.toggleStatus(state, 0)
        assertEquals(RollStatus.AB, state.rollStatus[0])
        state = RosterLogic.toggleStatus(state, 0)
        assertNull(state.rollStatus[0])
        // 越界索引不变
        assertEquals(state, RosterLogic.toggleStatus(state, 99))
    }

    @Test
    fun `直接设置状态 重置本轮 清空名单`() {
        var state = RosterState(
            rosterSel = listOf(1, 2),
            rollSel = listOf(1, 2, 3),
            rollStatus = mapOf(0 to RollStatus.IN, 1 to RollStatus.AB),
        )
        state = RosterLogic.setStatus(state, 2, RollStatus.IN)
        assertEquals(3, state.rollStatus.size)
        state = RosterLogic.setStatus(state, 2, null)
        assertEquals(2, state.rollStatus.size)
        state = RosterLogic.resetRoll(state)
        assertTrue(state.rollStatus.isEmpty())
        assertEquals(listOf(1, 2, 3), state.rollSel)

        state = RosterLogic.clearRoll(state)
        assertTrue(state.rollSel.isEmpty())
    }

    @Test
    fun `点击名单勾选加入或移出点名名单`() {
        var state = RosterState()
        val members = members()
        state = RosterLogic.toggleMemberInRoll(state, 3, members)
        assertEquals(listOf(3), state.rollSel)
        assertEquals("3 王五", state.rollNamesText)
        state = RosterLogic.toggleMemberInRoll(state, 3, members)
        assertTrue(state.rollSel.isEmpty())
        state = RosterLogic.selectAll(state, members)
        assertEquals(listOf(1, 2, 3, 4), state.rollSel)
    }

    @Test
    fun `点名统计`() {
        val state = RosterState(
            rollSel = listOf(1, 2, 3, 4),
            rollStatus = mapOf(0 to RollStatus.IN, 1 to RollStatus.IN, 2 to RollStatus.AB),
        )
        val stats = RosterLogic.stats(state)
        assertEquals(4, stats.total)
        assertEquals(3, stats.handled)
        assertEquals(2, stats.present)
        assertEquals(1, stats.absent)
        assertEquals(1, stats.pending)
        assertFalse(stats.completed)

        val done = RosterLogic.stats(
            state.copy(rollStatus = mapOf(0 to RollStatus.IN, 1 to RollStatus.IN, 2 to RollStatus.AB, 3 to RollStatus.IN)),
        )
        assertTrue(done.completed)
        assertEquals(0, done.pending)
    }

    @Test
    fun `点名网格 未处理在前 已处理沉底`() {
        val state = RosterState(
            rollSel = listOf(1, 2, 3),
            rollStatus = mapOf(1 to RollStatus.IN),
        )
        val items = RosterLogic.rollItems(state, members())
        assertEquals(listOf(1, 3, 2), items.map { it.seat })
        assertFalse(items[0].handled)
        assertTrue(items.last().handled)
        assertEquals("李四", items.last().name)

        val bySeat = RosterLogic.rollItems(state, members(), handledLast = false)
        assertEquals(listOf(1, 2, 3), bySeat.map { it.seat })
    }

    @Test
    fun `设置抽签名单`() {
        val state = RosterLogic.withRosterSel(RosterState(), listOf(4, 2, 2, 0))
        assertEquals(listOf(2, 4), state.rosterSel)
    }
}
