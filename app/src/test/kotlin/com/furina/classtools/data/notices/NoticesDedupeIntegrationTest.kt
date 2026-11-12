package com.furina.classtools.data.notices

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.testApiClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 仓库层去重集成：验证 buildData → NoticeDedupe 的组合（hidden/override/人工通知不误合）。 */
class NoticesDedupeIntegrationTest {

    private fun groupJson(
        id: String,
        title: String = "关于主题班会的通知",
        cat: String = "班会",
        deadline: String = "2026-11-15T16:00:00+08:00",
        mergedSources: List<String> = emptyList(),
        items: String = "[]",
    ): String {
        val sources = mergedSources.joinToString(",") { "\"$it\"" }
        return """{"id":"$id","title":"$title","cat":"$cat","source":"2026级信管1班",""" +
            """"senderName":"陈子麒","deadline":"$deadline","mergedSources":[$sources],""" +
            """"items":$items}"""
    }

    private fun item(text: String, follow: Boolean = false): String =
        """{"kind":"${if (follow) "follow" else "text"}","text":"$text","time":"11-10 10:00"}"""

    private fun autoRow(
        rowId: String,
        group: String,
        ts: Long,
        hidden: Boolean = false,
        overrideJson: String = "",
        editedAt: Long = 0L,
    ): String {
        val body = escape(NOTICE_BODY_PREFIX + group)
        val override = escape(overrideJson)
        return """{"id":"$rowId","title":"","body":"$body","ts":$ts,"hidden":$hidden,""" +
            """"override_json":"$override","edited_at":$editedAt,"edited_by":""}"""
    }

    private fun manualRow(id: String, title: String, body: String, ts: Long): String =
        """{"id":"$id","title":"$title","body":"$body","ts":$ts,"hidden":false}"""

    private fun escape(text: String): String =
        text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun lite(vararg rows: String): String =
        "[" + rows.joinToString(",") { row ->
            val id = Regex(""""id":"([^"]+)"""").find(row)?.groupValues?.get(1).orEmpty()
            val ts = Regex(""""ts":(\d+)"""").find(row)?.groupValues?.get(1).orEmpty()
            val hidden = Regex(""""hidden":(true|false)""").find(row)?.groupValues?.get(1).orEmpty()
            val editedAt = Regex(""""edited_at":(\d+)"""").find(row)?.groupValues?.get(1).orEmpty()
            """{"id":"$id","ts":${ts.ifBlank { "0" }},"hidden":${hidden.ifBlank { "false" }},"edited_at":${editedAt.ifBlank { "0" }}}"""
        } + "]"

    private fun repository(
        fullRows: List<String>,
        isAdmin: Boolean = false,
    ): NoticesRepository {
        val liteBody = lite(*fullRows.toTypedArray())
        val fullBody = "[" + fullRows.joinToString(",") + "]"
        val transport = FakeTransport { request ->
            if (request.url.contains("select=id,ts,hidden,edited_at")) {
                ApiResponse(200, liteBody)
            } else {
                ApiResponse(200, fullBody)
            }
        }
        return DefaultNoticesRepository(
            api = testApiClient(transport),
            isAdmin = { isAdmin },
            clock = { 1_700_000_000_000L },
        )
    }

    @Test
    fun `云端同一通知重复行在仓库层只显示一条`() = runTest {
        val rows = listOf(
            autoRow(
                rowId = "auto_g_old",
                group = groupJson("g_old", items = "[${item("原始消息")}]"),
                ts = 100L,
            ),
            autoRow(
                rowId = "auto_g_new",
                group = groupJson(
                    "g_new",
                    mergedSources = listOf("微信"),
                    items = "[${item("原始消息")},${item("补充：地点改到 01104", follow = true)}]",
                ),
                ts = 200L,
            ),
            manualRow("manual_1", "班费公示", "正文", ts = 50L),
        )
        val data = (repository(rows).load(force = true) as NoticesLoadResult.Success).data
        assertEquals(1, data.auto.size)
        assertEquals(1, data.manual.size)
        val merged = data.auto.single()
        assertEquals(200L, merged.ts)
        assertEquals(2, merged.items.size)
        assertTrue(merged.items.any { it is NoticeItemMeta.Text && it.isFollow })
        assertTrue(merged.group.mergedSources.contains("微信"))
        assertEquals("班费公示", data.manual.single().row.title)
    }

    @Test
    fun `不同通知在仓库层保留`() = runTest {
        val rows = listOf(
            autoRow("auto_a", groupJson("ga", title = "关于班会的通知", deadline = "2026-11-15T16:00:00+08:00"), 100L),
            autoRow("auto_b", groupJson("gb", title = "关于团课的通知", deadline = "2026-11-15T16:00:00+08:00"), 200L),
            autoRow("auto_c", groupJson("gc", title = "关于班会的通知", deadline = "2026-11-15T09:00:00+08:00"), 300L),
        )
        val data = (repository(rows).load(force = true) as NoticesLoadResult.Success).data
        assertEquals(3, data.auto.size)
    }

    @Test
    fun `管理员 hidden 与可见重复只保留可见且 hidden 计数按行`() = runTest {
        val rows = listOf(
            autoRow("auto_g1", groupJson("g1"), ts = 100L, hidden = true),
            autoRow("auto_g1_dup", groupJson("g1"), ts = 200L, hidden = false),
        )
        val adminData = (repository(rows, isAdmin = true).load(force = true) as NoticesLoadResult.Success).data
        assertEquals(1, adminData.auto.size)
        assertFalse(adminData.auto.single().hidden)
        assertEquals(1, adminData.hiddenCount)

        val userData = (repository(rows, isAdmin = false).load(force = true) as NoticesLoadResult.Success).data
        assertEquals(1, userData.auto.size)
        assertFalse(userData.auto.single().hidden)
        assertEquals(1, userData.hiddenCount)
    }

    @Test
    fun `override 在去重前应用并保留编辑标题`() = runTest {
        val rows = listOf(
            autoRow("auto_g1", groupJson("g1", title = "原标题"), ts = 300L),
            autoRow(
                rowId = "auto_g1_dup",
                group = groupJson("g1", title = "原标题"),
                ts = 200L,
                overrideJson = """{"title":"管理员改后的标题"}""",
                editedAt = 999L,
            ),
        )
        val data = (repository(rows, isAdmin = true).load(force = true) as NoticesLoadResult.Success).data
        assertEquals(1, data.auto.size)
        assertEquals("管理员改后的标题", data.auto.single().title)
        assertTrue(data.auto.single().edited)
    }

    @Test
    fun `人工通知与自动通知同标题不互相合并`() = runTest {
        val rows = listOf(
            autoRow("auto_g1", groupJson("g1", title = "班费公示"), ts = 100L),
            manualRow("manual_1", "班费公示", "正文", ts = 200L),
        )
        val data = (repository(rows).load(force = true) as NoticesLoadResult.Success).data
        assertEquals(1, data.auto.size)
        assertEquals(1, data.manual.size)
    }
}
