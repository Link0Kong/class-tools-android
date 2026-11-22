package com.furina.classtools.ui.screens.all

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

/**
 * 「全部工具」拖动排序纯逻辑单测：
 * - toolOrder 归一/迁移（与 mp `normalizeToolOrder` 一致）；
 * - 2 列网格命中（行 + x 中线）→ 插入下标；
 * - prefs.toolOrder 编解码 + at.prefs 时间戳（仅用户修改时写、无基线不盲写）。
 */
class ToolOrderLogicTest {

    private val natural = listOf("draw", "group", "fit", "hw", "nt", "tt", "power", "water", "roster", "settings")

    // ---- 归一/迁移 ----------------------------------------------------------

    @Test
    fun normalize_filtersUnknownAndAppendsMissing() {
        val raw = listOf("hw", "unknown", "draw", "hw", "  nt  ")
        assertEquals(
            listOf("hw", "draw", "nt", "group", "fit", "tt", "power", "water", "roster", "settings"),
            ToolOrderLogic.normalize(raw, natural),
        )
    }

    @Test
    fun normalize_emptyFallsBackToNatural() {
        assertEquals(natural, ToolOrderLogic.normalize(emptyList(), natural))
        assertEquals(natural, ToolOrderLogic.normalize(listOf("zzz"), natural))
    }

    @Test
    fun migrateLegacyHomeOrder_onlyWhenLongerThanFour() {
        assertNull(ToolOrderLogic.migrateLegacyHomeOrder(listOf("draw", "hw", "nt", "ft"), natural))
        val migrated = ToolOrderLogic.migrateLegacyHomeOrder(
            listOf("hw", "nt", "draw", "tt", "water", "roster"),
            natural,
        )
        assertNotNull(migrated)
        assertEquals(
            listOf("hw", "nt", "draw", "tt", "water", "roster", "group", "fit", "power", "settings"),
            migrated!!,
        )
    }

    @Test
    fun isNaturalOrder_andShouldPersist() {
        assertTrue(ToolOrderLogic.isNaturalOrder(natural, natural))
        assertFalse(ToolOrderLogic.isNaturalOrder(natural.reversed(), natural))
        // 只看实际变化：无变化/空 → 不写
        assertFalse(ToolOrderLogic.shouldPersist(natural, natural))
        assertTrue(ToolOrderLogic.shouldPersist(natural, natural.reversed()))
        assertFalse(ToolOrderLogic.shouldPersist(emptyList(), emptyList()))
    }

    // ---- 移动/网格命中 -------------------------------------------------------

    @Test
    fun moveToIndex_forwardAndBackward() {
        val order = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), ToolOrderLogic.moveToIndex(order, "a", 2))
        assertEquals(listOf("a", "c", "b", "d"), ToolOrderLogic.moveToIndex(order, "c", 1))
        assertEquals(listOf("c", "a", "b", "d"), ToolOrderLogic.moveToIndex(order, "c", 0))
        assertEquals(listOf("a", "b", "c", "d"), ToolOrderLogic.moveToIndex(order, "a", 0))
        assertEquals(listOf("b", "c", "d", "a"), ToolOrderLogic.moveToIndex(order, "a", 99))
        assertEquals(order, ToolOrderLogic.moveToIndex(order, "zzz", 1))
    }

    @Test
    fun gridTarget_columnByXMidline() {
        // 6 卡（3 行 2 列），卡宽 100、卡高 140、间距 10
        val count = 6
        // 第 0 行左列 → 目标 0
        assertEquals(0, ToolOrderLogic.gridTarget(10f, 10f, 100f, 140f, 10f, count, draggedIndex = 5))
        // 第 0 行右列（x > 100+5）→ 目标 1
        assertEquals(1, ToolOrderLogic.gridTarget(160f, 10f, 100f, 140f, 10f, count, draggedIndex = 5))
        // 第 1 行右列 → 目标 3
        assertEquals(3, ToolOrderLogic.gridTarget(160f, 200f, 100f, 140f, 10f, count, draggedIndex = 0))
    }

    @Test
    fun gridTarget_removesDraggedIndex() {
        // 拖第 1 张（当前下标 1）到第 3 行左列（下标 4）：remaining 中插到 3
        assertEquals(4, ToolOrderLogic.gridTarget(10f, 320f, 100f, 140f, 10f, 6, draggedIndex = 1))
        // 拖第 4 张（下标 4）到第 0 行左列：插到 0
        assertEquals(0, ToolOrderLogic.gridTarget(10f, 0f, 100f, 140f, 10f, 6, draggedIndex = 4))
    }

    @Test
    fun gridTarget_clampsOutsideGrid() {
        assertEquals(0, ToolOrderLogic.gridTarget(-50f, -80f, 100f, 140f, 10f, 4, draggedIndex = 2))
        // 指针在最后一行下方 → 落到最后一格（下标 3）
        assertEquals(3, ToolOrderLogic.gridTarget(60f, 9999f, 100f, 140f, 10f, 4, draggedIndex = 0))
        assertEquals(0, ToolOrderLogic.gridTarget(0f, 0f, 100f, 140f, 10f, 0, draggedIndex = 0))
    }

    // ---- prefs 编解码 -------------------------------------------------------

    @Test
    fun prefsRead_missingReturnsEmpty() {
        assertTrue(ToolOrderPrefs.read(null).isEmpty())
        assertTrue(ToolOrderPrefs.read(JsonObject(emptyMap())).isEmpty())
        assertTrue(ToolOrderPrefs.read(JsonObject(mapOf("prefs" to JsonPrimitive("x")))).isEmpty())
    }

    @Test
    fun prefsApply_writesToolOrderAndAtPrefs() {
        val sections = buildJsonObject {
            put("v", JsonPrimitive(6))
            put("nick", JsonPrimitive("芙宁娜"))
            put(
                "prefs",
                buildJsonObject {
                    put("hwRemind", JsonPrimitive(true))
                    put("ntSub", JsonPrimitive(false))
                },
            )
            put("at", buildJsonObject { put("prefs", JsonPrimitive(1000L)) })
        }
        val next = ToolOrderPrefs.apply(sections, listOf("hw", "draw"), now = 5000L)
        assertEquals(listOf("hw", "draw"), ToolOrderPrefs.read(next))
        assertEquals(5000L, ToolOrderPrefs.atOf(next))
        // 其它 section/prefs 字段原样保留
        assertEquals("6", (next["v"] as JsonPrimitive).content)
        assertEquals("芙宁娜", (next["nick"] as JsonPrimitive).content)
        val prefs = next["prefs"] as JsonObject
        assertEquals("true", (prefs["hwRemind"] as JsonPrimitive).content)
        // 不新增顶层键（仍为 v/nick/prefs/at）
        assertEquals(4, next.size)
    }

    @Test
    fun prefsApply_neverLowersAtPrefs() {
        val sections = buildJsonObject {
            put("at", buildJsonObject { put("prefs", JsonPrimitive(9000L)) })
        }
        val next = ToolOrderPrefs.apply(sections, listOf("draw"), now = 1000L)
        assertEquals(9000L, ToolOrderPrefs.atOf(next))
    }

    @Test
    fun prefsReadNormalized_usesNaturalWhenNoBaseline() {
        assertEquals(natural, ToolOrderPrefs.readNormalized(null, natural))
        val sections = ToolOrderPrefs.apply(JsonObject(emptyMap()), listOf("hw", "draw"))
        assertEquals(
            listOf("hw", "draw", "group", "fit", "nt", "tt", "power", "water", "roster", "settings"),
            ToolOrderPrefs.readNormalized(sections, natural),
        )
    }

    @Test
    fun prefsApply_mapsLocalIdsToCanonicalSyncIds() {
        // 2026-11-22：写入云端用跨端 canonical id（安卓 fit/roster → ft/committee），
        // 否则网页/小程序把体测、名单当未知项丢掉，顺序看起来「没同步」。
        val next = ToolOrderPrefs.apply(
            JsonObject(emptyMap()),
            listOf("draw", "fit", "roster", "group", "fit"),
            now = 1234L,
        )
        val raw = ((next["prefs"] as JsonObject)["toolOrder"] as JsonArray)
            .map { (it as JsonPrimitive).content }
        assertEquals(listOf("draw", "ft", "committee", "group"), raw)
        // 读回：canonical / 小程序 bz / 历史 fit 都归一成本机 id
        assertEquals(listOf("draw", "fit", "roster", "group"), ToolOrderPrefs.read(next))
    }

    @Test
    fun prefsRead_aliasesMapToLocalIds() {
        val sections = ToolOrderPrefs.parseSections(
            """{"prefs":{"toolOrder":["hw","ft","committee","bz","fit","roster","draw"]},"at":{"prefs":1}}""",
        )
        assertEquals(
            "网页 ft/committee、小程序 bz、历史 fit/roster 都要映射成本机 id 后再归一",
            listOf("hw", "fit", "roster", "draw", "group", "nt", "tt", "power", "water", "settings"),
            ToolOrderPrefs.readNormalized(sections, natural),
        )
    }

    @Test
    fun describeDrop_isNullWhenUnchanged() {
        assertNull(ToolOrderPrefs.describeDrop(listOf("a", "b"), listOf("a", "b"), "a"))
        assertEquals("已移动到第 2 位", ToolOrderPrefs.describeDrop(listOf("a", "b"), listOf("b", "a"), "a"))
    }

    @Test
    fun parseSections_roundTrip() {
        val raw = """{"prefs":{"toolOrder":["hw","draw"]},"at":{"prefs":123}}"""
        val sections = ToolOrderPrefs.parseSections(raw)
        assertNotNull(sections)
        assertEquals(listOf("hw", "draw"), ToolOrderPrefs.read(sections))
        assertEquals(123L, ToolOrderPrefs.atOf(sections))
    }
}

