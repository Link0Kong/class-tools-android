package com.furina.classtools.features.home

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 首页常用工具同步边界单测（2026-11-22）：
 * 写云端时本机 id → canonical（fit→ft、roster→committee），读云端时反向映射（含小程序 bz）。
 */
class HomeToolsEditorTest {

    private fun homeToolsOf(sections: JsonObject): List<String> =
        (sections["homeTools"] as JsonArray).map { (it as JsonPrimitive).content }

    @Test
    fun apply_writesCanonicalIdsAndCapsAtFour() {
        val next = HomeToolsEditor.apply(
            JsonObject(emptyMap()),
            listOf("fit", "roster", "hw", "nt", "draw"),
            now = 1234L,
        )
        assertEquals(listOf("ft", "committee", "hw", "nt"), homeToolsOf(next))
        assertEquals("1234", (next["homeToolsAt"] as JsonPrimitive).content)
        assertEquals("1234", ((next["at"] as JsonObject)["homeTools"] as JsonPrimitive).content)
    }

    @Test
    fun apply_preservesOtherSections() {
        val sections = buildJsonObject {
            put("v", JsonPrimitive(6))
            put("nick", JsonPrimitive("芙宁娜"))
            put("prefs", buildJsonObject { put("ntSub", JsonPrimitive(false)) })
        }
        val next = HomeToolsEditor.apply(sections, listOf("draw"), now = 9L)
        assertEquals("6", (next["v"] as JsonPrimitive).content)
        assertEquals("芙宁娜", (next["nick"] as JsonPrimitive).content)
        assertEquals(listOf("draw"), homeToolsOf(next))
    }

    @Test
    fun read_mapsSyncAliasesToLocalIds() {
        val sections = buildJsonObject {
            put(
                "homeTools",
                JsonArray(
                    listOf(
                        JsonPrimitive("ft"),
                        JsonPrimitive("committee"),
                        JsonPrimitive("bz"),
                        JsonPrimitive("group"),
                        JsonPrimitive("hw"),
                    ),
                ),
            )
        }
        assertEquals(listOf("fit", "roster", "group", "hw"), HomeToolsEditor.read(sections))
    }

    @Test
    fun read_missingReturnsEmpty() {
        assertEquals(emptyList<String>(), HomeToolsEditor.read(null))
        assertEquals(emptyList<String>(), HomeToolsEditor.read(JsonObject(emptyMap())))
    }
}
