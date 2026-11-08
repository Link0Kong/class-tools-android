package com.furina.classtools.data.privacy

import com.furina.classtools.data.sync.UserdataV6
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncScopeCatalogTest {

    @Test
    fun `覆盖全部 11 个 userdata section 且顺序与契约一致`() {
        assertTrue(SyncScopeCatalog.coversAllSections())
        assertEquals(
            UserdataV6.SECTION_KEYS,
            SyncScopeCatalog.sectionItems.mapNotNull { it.sectionKey },
        )
    }

    @Test
    fun `marks 三类均在同步清单`() {
        val marks = SyncScopeCatalog.marksItems
        assertEquals(3, marks.size)
        assertEquals(
            listOf("marks.hw", "marks.nt", "marks.ntdone"),
            marks.map { it.key },
        )
        assertTrue(marks.all { it.mode == SyncScopeMode.SYNCED })
    }

    @Test
    fun `仅本机与设备偏好项包含身份 token 名单缓存 提醒 日历`() {
        val local = SyncScopeCatalog.localItems
        assertEquals(4, local.size)
        val keys = local.map { it.key }.toSet()
        assertTrue(keys.containsAll(setOf("identity", "members", "reminder", "calendar")))
        assertEquals(
            listOf("身份 token", "班级名单缓存", "提醒与安静时段", "日历写入结果"),
            local.map { it.title },
        )
        assertTrue(local.first { it.key == "identity" }.mode == SyncScopeMode.LOCAL_ONLY)
        assertTrue(local.first { it.key == "reminder" }.mode == SyncScopeMode.DEVICE_ONLY)
    }

    @Test
    fun `条目 key 与标题唯一且文案非空`() {
        val items = SyncScopeCatalog.items
        assertEquals(items.size, items.map { it.key }.distinct().size)
        assertEquals(items.size, items.map { it.title }.distinct().size)
        items.forEach { item ->
            assertTrue("empty key", item.key.isNotBlank())
            assertTrue("empty title: ${item.key}", item.title.isNotBlank())
            assertTrue("empty detail: ${item.key}", item.detail.isNotBlank())
        }
    }

    @Test
    fun `按 section 查找与不存在的查找`() {
        val homeTools = SyncScopeCatalog.bySection("homeTools")
        assertNotNull(homeTools)
        assertEquals("首页常用工具", homeTools!!.title)
        assertNull(SyncScopeCatalog.bySection("missing"))
        assertNull(SyncScopeCatalog.bySection("marks.hw"))
    }

    @Test
    fun `清单包含 web 红线中的关键声明`() {
        val titles = SyncScopeCatalog.items.map { it.title }
        assertTrue(titles.contains("作业完成"))
        assertTrue(titles.contains("通知已处理"))
        assertTrue(titles.contains("关注宿舍（电费）"))
        assertTrue(titles.contains("抄表记录（水量）"))
        assertTrue(SyncScopeCatalog.FOOTER.contains("本机"))
        assertTrue(SyncScopeCatalog.FOOTER.contains("同一账号"))
        assertFalse(SyncScopeCatalog.FOOTER.contains("token 明文"))
        assertTrue(SyncScopeCatalog.items.none { it.key == "token" })
    }

    @Test
    fun `模式文案符合隐私页口径`() {
        assertEquals("随账号同步", SyncScopeMode.SYNCED.label)
        assertEquals("仅本机", SyncScopeMode.LOCAL_ONLY.label)
        assertEquals("设备偏好", SyncScopeMode.DEVICE_ONLY.label)
        assertTrue(SyncScopeCatalog.items.all { it.mode in SyncScopeMode.entries })
    }
}
