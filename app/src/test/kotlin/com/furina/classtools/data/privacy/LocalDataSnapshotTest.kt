package com.furina.classtools.data.privacy

import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.sync.UserdataV6
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDataSnapshotTest {

    private fun userdata(): JsonObject = buildJsonObject {
        UserdataV6.SECTION_KEYS.forEach { put(it, UserdataV6.defaultSection(it)) }
        put(
            "homeTools",
            JsonArray(listOf(JsonPrimitive("hw"), JsonPrimitive("nt"), JsonPrimitive("draw"))),
        )
        put(
            "draw",
            buildJsonObject {
                put("n", JsonPrimitive(10))
                put("sel", JsonArray(emptyList()))
            },
        )
        put(
            "group",
            buildJsonObject {
                put("n", JsonPrimitive(5))
                put("sel", JsonArray((1..6).map { JsonPrimitive(it) }))
                put("mode", JsonPrimitive("count"))
            },
        )
        put(
            "roster",
            buildJsonObject {
                put("rosterSel", JsonArray((1..8).map { JsonPrimitive(it) }))
                put("rollSel", JsonArray((1..3).map { JsonPrimitive(it) }))
                put("rollSt", JsonObject(emptyMap()))
                put("rollNames", JsonPrimitive("1,2,3"))
            },
        )
    }

    // ------------------------------------------------------------ 聚合

    @Test
    fun `从本机缓存聚合 8 计数基础项`() {
        val marks = MarksSnapshot(
            hwDone = mapOf("a" to true, "b" to true, "c" to false),
            ntDone = mapOf("n1" to true),
            ntPin = mapOf("p1" to true, "p2" to true),
        )
        val snapshot = LocalDataStats.from(
            userdata = userdata(),
            marks = marks,
            fitRecords = 2,
            classMembers = 68,
            pending = 4,
        )
        assertEquals(2, snapshot.hwDone)
        assertEquals(1, snapshot.ntDone)
        assertEquals(2, snapshot.ntPin)
        assertEquals(2, snapshot.fitRecords)
        assertEquals(8, snapshot.rosterSel)
        assertEquals(6, snapshot.groupSel)
        assertEquals(3, snapshot.rollSel)
        assertEquals(4, snapshot.pending)
        assertEquals(68, snapshot.classMembers)
        assertEquals(3, snapshot.homeTools)
        assertTrue(snapshot.drawAvailable)
        assertTrue(snapshot.groupAvailable)
    }

    @Test
    fun `userdataSections 只统计与默认值不同的 section`() {
        val snapshot = LocalDataStats.from(userdata(), MarksSnapshot(), fitRecords = 0)
        // 非默认：homeTools / draw / group / roster = 4
        assertEquals(4, snapshot.userdataSections)
    }

    @Test
    fun `全空 userdata 与空 marks 归零且抽签分组不可用`() {
        val snapshot = LocalDataStats.from(
            userdata = JsonObject(emptyMap()),
            marks = MarksSnapshot(),
            fitRecords = 0,
        )
        assertEquals(0, snapshot.userdataSections)
        assertEquals(0, snapshot.homeTools)
        assertEquals(0, snapshot.rosterSel)
        assertEquals(0, snapshot.rollSel)
        assertEquals(0, snapshot.groupSel)
        assertFalse(snapshot.drawAvailable)
        assertFalse(snapshot.groupAvailable)
    }

    @Test
    fun `负重与超大数据安全钳制`() {
        val snapshot = LocalDataStats.from(
            userdata = null,
            marks = MarksSnapshot(),
            fitRecords = -5,
            classMembers = -2,
            pending = -9,
        )
        assertEquals(0, snapshot.fitRecords)
        assertEquals(0, snapshot.classMembers)
        assertEquals(0, snapshot.pending)
    }

    // ------------------------------------------------------------ 展示

    @Test
    fun `8 计数顺序与标签与 mp 一致`() {
        val stats = LocalDataStats.primaryStats(LocalDataSnapshot())
        assertEquals(8, stats.size)
        assertEquals(
            listOf("已完成作业", "已处理通知", "置顶通知", "体测记录", "抽签名单", "分组名单", "点名名单", "待补发"),
            stats.map { it.label },
        )
        assertEquals("hwDone", stats[0].key)
        assertEquals("pending", stats[7].key)
    }

    @Test
    fun `抽签分组不可用时显示破折号且标记不可用`() {
        val stats = LocalDataStats.primaryStats(LocalDataSnapshot(drawAvailable = false, groupAvailable = false))
        val draw = stats.first { it.key == "rosterSel" }
        assertEquals("—", draw.value)
        assertFalse(draw.available)
        val group = stats.first { it.key == "groupSel" }
        assertEquals("—", group.value)
        assertFalse(group.available)
    }

    @Test
    fun `数字格式化上限与大数折叠`() {
        assertEquals("0", LocalDataStats.formatCount(0))
        assertEquals("9", LocalDataStats.formatCount(9))
        assertEquals("9999", LocalDataStats.formatCount(9999))
        assertEquals("9999+", LocalDataStats.formatCount(10_000))
        assertEquals("9999+", LocalDataStats.formatCount(12_345))
    }

    @Test
    fun `额外汇总展示同步配置 常用工具 名单缓存`() {
        val stats = LocalDataStats.summaryStats(
            LocalDataSnapshot(userdataSections = 5, homeTools = 3, classMembers = 68),
        )
        assertEquals(listOf("同步配置", "首页常用工具", "班级名单缓存"), stats.map { it.label })
        assertEquals("5", stats[0].value)
        assertEquals("3", stats[1].value)
        assertEquals("68", stats[2].value)
    }

    @Test
    fun `部分可用场景 有 userdata 无 marks 时仍展示 0 与可用项`() {
        val snapshot = LocalDataStats.from(userdata(), MarksSnapshot(), fitRecords = 0)
        val stats = LocalDataStats.primaryStats(snapshot)
        assertEquals("0", stats[0].value)
        assertTrue(stats[0].available)
        assertEquals("8", stats.first { it.key == "rosterSel" }.value)
        assertEquals("6", stats.first { it.key == "groupSel" }.value)
    }
}
