package com.furina.classtools.data.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 跨端同步 id 映射单测（2026-11-22）。
 *
 * userdata v6 的 homeTools / prefs.toolOrder 五端共用，但历史 id 词表不同：
 * 安卓 `fit`/`roster`、网页 `ft`/`committee`、小程序 `ft`/`bz`。
 * 映射只发生在同步边界，本机 UI/路由仍用本机 id。
 */
class ToolCatalogSyncIdTest {

    @Test
    fun retiredPptToolResolvesToOneFileShortcut() {
        assertEquals(0, ToolCatalog.tools.count { it.id == "ppt" })
        assertEquals("file", ToolCatalog.byId("ppt")?.id)
        val oldOrder = listOf("ppt", "hw", "file")
        val migrated = listOf("file", "hw")
        assertEquals(migrated, ToolCatalog.quickTools(oldOrder).map { it.id })
        assertEquals(migrated, ToolCatalog.fromSyncIds(oldOrder))
        assertEquals(migrated, ToolCatalog.toSyncIds(oldOrder))
    }

    @Test
    fun syncIdOf_mapsLocalIdsToCanonical() {
        assertEquals("ft", ToolCatalog.syncIdOf("fit"))
        assertEquals("committee", ToolCatalog.syncIdOf("roster"))
        assertEquals("draw", ToolCatalog.syncIdOf("draw"))
        assertEquals("group", ToolCatalog.syncIdOf("group"))
        assertEquals("jw", ToolCatalog.syncIdOf("jw"))
    }

    @Test
    fun localIdOf_mapsAllKnownAliases() {
        assertEquals("fit", ToolCatalog.localIdOf("ft"))
        assertEquals("fit", ToolCatalog.localIdOf("fit"))
        assertEquals("roster", ToolCatalog.localIdOf("committee"))
        assertEquals("roster", ToolCatalog.localIdOf("roster"))
        assertEquals("roster", ToolCatalog.localIdOf("bz"))
        assertEquals("draw", ToolCatalog.localIdOf("draw"))
    }

    @Test
    fun bySyncId_resolvesToolsAndRejectsUnknown() {
        assertEquals("fit", ToolCatalog.bySyncId("ft")?.id)
        assertEquals("roster", ToolCatalog.bySyncId("committee")?.id)
        assertEquals("roster", ToolCatalog.bySyncId("bz")?.id)
        assertNull(ToolCatalog.bySyncId("not-a-tool"))
    }

    @Test
    fun toAndFromSyncIds_preserveOrderAndDedupe() {
        assertEquals(
            listOf("ft", "committee", "hw", "group"),
            ToolCatalog.toSyncIds(listOf("fit", "roster", "hw", "group", "fit")),
        )
        assertEquals(
            listOf("fit", "roster", "hw", "group"),
            ToolCatalog.fromSyncIds(listOf("ft", "committee", "hw", "group", "bz")),
        )
    }
}
