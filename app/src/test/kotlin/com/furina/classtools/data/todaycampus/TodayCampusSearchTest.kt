package com.furina.classtools.data.todaycampus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「今日校园」服务入口本地搜索过滤纯逻辑单测。 */
class TodayCampusSearchTest {

    private val entries: List<TodayCampusEntry> = TodayCampusCatalog.entries

    @Test
    fun `空查询返回全部入口并保持顺序`() {
        assertSame(entries, TodayCampusSearch.filter(entries, ""))
        assertSame(entries, TodayCampusSearch.filter(entries, "   "))
        assertSame(entries, TodayCampusSearch.filter(entries, null))
        assertEquals(entries.size, TodayCampusSearch.filter(entries, "").size)
    }

    @Test
    fun `按名称过滤`() {
        // 「宿舍」同时出现在宿舍服务（名称）与水电查询（说明）里
        assertEquals(listOf("宿舍服务", "水电查询"), TodayCampusSearch.filter(entries, "宿舍").map { it.name })
        assertEquals(listOf("心理健康"), TodayCampusSearch.filter(entries, "心理").map { it.name })
        assertEquals(listOf("融合门户"), TodayCampusSearch.filter(entries, "门户").map { it.name })
        // 名称子串可命中多个入口
        val services = TodayCampusSearch.filter(entries, "服务").map { it.name }
        assertTrue(services.contains("学工服务"))
        assertTrue(services.contains("宿舍服务"))
    }

    @Test
    fun `按说明与关键词过滤`() {
        // 「校园卡」「充值」只出现在一卡通的说明/关键词里
        assertEquals(listOf("一卡通"), TodayCampusSearch.filter(entries, "校园卡").map { it.name })
        assertEquals(listOf("一卡通"), TodayCampusSearch.filter(entries, "充值").map { it.name })
        // 「教务」是课表成绩的关键词
        assertEquals(listOf("课表成绩"), TodayCampusSearch.filter(entries, "教务").map { it.name })
        // 「勤工」同时是名称与关键词前缀
        assertEquals(listOf("勤工助学"), TodayCampusSearch.filter(entries, "勤工").map { it.name })
    }

    @Test
    fun `忽略大小写与内部空白`() {
        assertEquals(listOf("学工服务"), TodayCampusSearch.filter(entries, " 学 工 ").map { it.name })
        assertEquals(listOf("学工服务"), TodayCampusSearch.filter(entries, "XG").map { it.name })
        assertEquals("xg", TodayCampusSearch.normalize("  X G  "))
        assertEquals("", TodayCampusSearch.normalize(null))
    }

    @Test
    fun `无匹配返回空列表`() {
        assertTrue(TodayCampusSearch.filter(entries, "不存在的服务").isEmpty())
        assertTrue(TodayCampusSearch.filter(entries, "zzzz").isEmpty())
        assertTrue(TodayCampusSearch.filter(entries, "   ").isNotEmpty())
    }

    @Test
    fun `单个入口命中判定`() {
        val dorm = TodayCampusCatalog.byId("dorm")!!
        assertTrue(TodayCampusSearch.matches(dorm, TodayCampusSearch.normalize("住宿")))
        assertTrue(TodayCampusSearch.matches(dorm, ""))
        assertFalse(TodayCampusSearch.matches(dorm, TodayCampusSearch.normalize("奖助学金")))
    }

    @Test
    fun `搜索词长度上限合理`() {
        assertTrue(TodayCampusSearch.MAX_QUERY_LENGTH in 8..64)
    }
}
