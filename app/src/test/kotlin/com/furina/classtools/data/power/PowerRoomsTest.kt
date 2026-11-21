package com.furina.classtools.data.power

import com.furina.classtools.data.network.NetJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 宿舍 roomverify 解析 / 目录排序单测（2026-11-21 修复「部分宿舍无法显示」）。
 *
 * 背景：旧实现把 17 间宿舍目录硬编码在客户端；一次「公开仓库脱敏」把真实房号换成虚构值后，
 * 真实宿舍的 `normalizeSel` 全部返回空 → 已关注宿舍不显示、选择器里也没有真实宿舍。
 * 现在展示名一律从 roomverify 派生，目录由云端提供（[PowerDirectoryRepository]），
 * 因此这里用**虚构房号**（301/305/314 等）验证解析口径即可，不依赖真实宿舍清单。
 */
class PowerRoomsTest {

    @Test
    fun catOf_parsesCanonicalRoomverify() {
        val a = PowerRooms.catOf("1-13---324")!!
        assertEquals("1-13---324", a.rv)
        assertEquals("13号楼", a.building)
        assertEquals("324", a.room)
        assertEquals("13号楼324", a.label)

        val b = PowerRooms.catOf(" 4-23B---423 ")!!
        assertEquals("4-23B---423", b.rv)
        assertEquals("23号楼B座", b.building)
        assertEquals("423", b.room)
        assertEquals("23号楼B座423", b.label)

        val c = PowerRooms.catOf("1-16---305")!!
        assertEquals("16号楼", c.building)
        assertEquals("16号楼305", c.label)

        val d = PowerRooms.catOf("4-23A---321")!!
        assertEquals("23号楼A座", d.building)
        assertEquals("23号楼A座321", d.label)
    }

    @Test
    fun catOf_rejectsMalformedValues() {
        assertNull(PowerRooms.catOf(null))
        assertNull(PowerRooms.catOf(""))
        assertNull(PowerRooms.catOf("bad-room"))
        assertNull(PowerRooms.catOf("4-23A"))
        assertNull(PowerRooms.catOf("4-23A---"))
        assertNull(PowerRooms.catOf("x-13---324"))
        assertNull(PowerRooms.catOf("4-ABC---324"))
        assertEquals("", PowerRooms.normalizeSel("13号楼324"))
    }

    @Test
    fun normalizeSel_acceptsAnyWellFormedRoomverify() {
        // 回归：真实宿舍（未被硬编码进客户端）必须被识别，否则电费/水量页显示不出宿舍。
        assertEquals("4-23B---423", PowerRooms.normalizeSel("4-23B---423"))
        assertEquals("1-13---308", PowerRooms.normalizeSel(" 1-13---308 "))
        assertEquals("", PowerRooms.normalizeSel(""))
        assertEquals("", PowerRooms.normalizeSel("bad-room"))
    }

    @Test
    fun fromDirectoryRow_prefersRoomverifyThenFallsBackToServerFields() {
        // 规范：展示名从 roomverify 派生（云端 label 与 rv 不一致时以 rv 为准）。
        val canonical = PowerRooms.fromDirectoryRow(
            id = "1-13---324",
            label = "13号楼999",
            building = "错误楼栋",
            room = "999",
        )!!
        assertEquals("13号楼324", canonical.label)
        assertEquals("324", canonical.room)

        // roomverify 解析不出：退回云端字段，避免整条丢失。
        val fallback = PowerRooms.fromDirectoryRow(
            id = "legacy-room",
            label = "旧宿舍",
            building = "旧楼",
            room = "101",
        )!!
        assertEquals("legacy-room", fallback.rv)
        assertEquals("旧宿舍", fallback.label)
        assertEquals("旧楼", fallback.building)

        assertNull(PowerRooms.fromDirectoryRow(id = "", label = "x", building = "x", room = "1"))
        assertNull(PowerRooms.fromDirectoryRow(id = "legacy", label = "", building = "", room = ""))
    }

    @Test
    fun parseDirectory_sortsDedupesAndSkipsInvalidRows() {
        val raw = """
            [
              {"id":"4-23B---423","label":"23号楼B座423","building":"23号楼B座","room":"423"},
              {"id":"1-13---324","label":"13号楼324","building":"13号楼","room":"324"},
              {"id":"4-23B---423","label":"重复行","building":"23号楼B座","room":"423"},
              {"id":"1-16---305","label":"16号楼305","building":"16号楼","room":"305"},
              {"id":"","label":"空 id","building":"x","room":"1"},
              {"id":"4-23A---321","label":"23号楼A座321","building":"23号楼A座","room":"321"}
            ]
        """.trimIndent()
        val rooms = PowerRooms.parseDirectory(NetJson, raw)
        assertEquals(
            listOf("1-13---324", "1-16---305", "4-23A---321", "4-23B---423"),
            rooms.map { it.rv },
        )
    }

    @Test
    fun parseDirectory_invalidJsonOrNonArrayReturnsEmpty() {
        assertTrue(PowerRooms.parseDirectory(NetJson, "oops").isEmpty())
        assertTrue(PowerRooms.parseDirectory(NetJson, "{\"id\":\"1-13---324\"}").isEmpty())
        assertTrue(PowerRooms.parseDirectory(NetJson, "[]").isEmpty())
    }

    @Test
    fun sortRooms_buildingNaturalOrderThenRoomNumber() {
        val rooms = listOf(
            room("4-23B---422"),
            room("1-16---305"),
            room("1-13---324"),
            room("4-23A---321"),
            room("1-13---308"),
        )
        val sorted = PowerRooms.sortRooms(rooms)
        assertEquals(
            listOf("13号楼308", "13号楼324", "16号楼305", "23号楼A座321", "23号楼B座422"),
            sorted.map { it.label },
        )
    }

    @Test
    fun buildingsOf_returnsNaturalOrder() {
        val rooms = listOf(room("4-23B---422"), room("1-13---324"), room("4-23A---321"), room("1-16---305"))
        assertEquals(
            listOf("13号楼", "16号楼", "23号楼A座", "23号楼B座"),
            PowerRooms.buildingsOf(rooms),
        )
        assertTrue(PowerRooms.buildingsOf(emptyList()).isEmpty())
    }

    @Test
    fun roomsOfBuilding_filtersAndSorts() {
        val rooms = listOf(room("4-23A---323"), room("1-13---324"), room("4-23A---321"))
        assertEquals(
            listOf("321", "323"),
            PowerRooms.roomsOfBuilding(rooms, "23号楼A座").map { it.room },
        )
        assertTrue(PowerRooms.roomsOfBuilding(rooms, "23号楼B座").isEmpty())
        assertTrue(PowerRooms.roomsOfBuilding(rooms, " ").isEmpty())
    }

    @Test
    fun search_matchesRoomBuildingAndLabel() {
        val rooms = listOf(room("1-13---324"), room("4-23A---321"), room("4-23B---422"))
        assertEquals(listOf("4-23A---321"), PowerRooms.search(rooms, "321").map { it.rv })
        assertEquals(2, PowerRooms.search(rooms, "23号楼").size)
        assertEquals(listOf("4-23B---422"), PowerRooms.search(rooms, "B座").map { it.rv })
        assertEquals(3, PowerRooms.search(rooms, "  ").size)
        assertTrue(PowerRooms.search(rooms, "999").isEmpty())
    }

    @Test
    fun contains_detectsStaleSelection() {
        val rooms = listOf(room("1-13---324"), room("4-23A---321"))
        assertTrue(PowerRooms.contains(rooms, "4-23A---321"))
        assertTrue(PowerRooms.contains(rooms, " 1-13---324 "))
        // 历史版本写入的虚构房号不在目录里 → 页面回到「未选择」，但不回写云端。
        assertTrue(!PowerRooms.contains(rooms, "4-23A---305"))
        assertTrue(!PowerRooms.contains(rooms, ""))
        assertTrue(!PowerRooms.contains(emptyList(), "4-23A---321"))
    }

    @Test
    fun merge_addsRecordedRoomsAndSorts() {
        val rooms = listOf(room("1-13---324"))
        val merged = PowerRooms.merge(
            rooms,
            listOf("1-13---324", "4-23B---422", "bad-room", "1-16---305"),
        )
        assertEquals(
            listOf("1-13---324", "1-16---305", "4-23B---422"),
            merged.map { it.rv },
        )
        // 目录为空时仍能兜底展示有记录的宿舍
        assertEquals(listOf("4-23B---422"), PowerRooms.merge(emptyList(), listOf("4-23B---422")).map { it.rv })
    }

    private fun room(rv: String): RoomInfo = PowerRooms.catOf(rv)!!
}
