package com.furina.classtools

import com.furina.classtools.data.tool.ToolCatalog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 统计工具只对管理员可见（不在普通成员「全部工具」/首页快捷中出现）。 */
class ToolCatalogStatsTest {

    @Test
    fun statsToolIsAdminOnly() {
        val stats = ToolCatalog.byId("stats")
        assertNotNull(stats)
        assertTrue(stats!!.adminOnly)
        assertTrue(ToolCatalog.visibleTools(isAdmin = true).any { it.id == "stats" })
        assertFalse(ToolCatalog.visibleTools(isAdmin = false).any { it.id == "stats" })
    }

    @Test
    fun statsCannotBePickedAsHomeQuickTool() {
        assertFalse(ToolCatalog.quickTools(listOf("stats")).any { it.id == "stats" })
    }
}
