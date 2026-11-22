package com.furina.classtools.features.group

import com.furina.classtools.features.draw.DrawLastResult
import com.furina.classtools.features.draw.DrawLastResultCodec
import com.furina.classtools.features.draw.KeyValueDrawLastResultStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分组「最近抽签」成员解析单测：
 * 本机存储 → 序号 → 成员（姓名优先班级名单）；无存储时友好降级，不崩溃。
 */
class GroupSourcesTest {

    private val roster = listOf(
        GroupMember(1, "张伟"),
        GroupMember(2, "李四"),
        GroupMember(5, "王五"),
    )

    private fun storedLast(): DrawLastResult = DrawLastResult(
        atMillis = 1_794_016_800_000L,
        result = listOf(5, 2),
    )

    @Test
    fun `PREV 来源从本机存储解析成员并补班级姓名`() {
        val backing = mutableMapOf<String, String>()
        val store = KeyValueDrawLastResultStore(
            key = "last",
            read = { backing[it] },
            write = { key, value -> backing[key] = value },
        )
        store.save(storedLast())

        // 新实例读取（模拟进程重启），再解析为分组 PREV 成员。
        val fromStore = KeyValueDrawLastResultStore(
            key = "last",
            read = { backing[it] },
            write = { key, value -> backing[key] = value },
        ).load()
        val members = GroupMemberResolver.fromLastDraw(fromStore, roster)

        assertEquals(listOf(5, 2), members.map { it.seat })
        assertEquals(listOf("王五", "李四"), members.map { it.name })
    }

    @Test
    fun `无本机最近抽签时 PREV 为空并给出友好提示`() {
        val members = GroupMemberResolver.fromLastDraw(null, roster)
        assertTrue(members.isEmpty())

        val validation = GroupValidator.validate(GroupConfig(source = GroupSource.PREV), members)
        assertTrue(validation is GroupValidation.Invalid)
        assertEquals(
            "还没有抽签结果，先抽签或换成员来源",
            (validation as GroupValidation.Invalid).error.message,
        )
    }

    @Test
    fun `内存兜底只在本机存储为空时使用`() {
        val fallback = listOf(GroupMember(9, "兜底同学"))
        assertEquals(fallback, GroupMemberResolver.fromLastDraw(null, roster, fallback))
        assertEquals(
            listOf(GroupMember(5, "王五"), GroupMember(2, "李四")),
            GroupMemberResolver.fromLastDraw(storedLast(), roster, fallback),
        )
    }

    @Test
    fun `全班座号升序去重且不含非法座号`() {
        val dirty = roster + listOf(GroupMember(2, "重复"), GroupMember(0, "无座号"))
        assertEquals(listOf(1, 2, 5), GroupMemberResolver.allClassSeats(dirty))
    }

    @Test
    fun `存储编码后仍能解析 PREV 成员`() {
        val encoded = DrawLastResultCodec.encode(storedLast())
        val decoded = DrawLastResultCodec.decode(encoded)!!
        val members = GroupMemberResolver.fromLastDraw(decoded, roster)
        assertEquals(listOf(5, 2), members.map { it.seat })
        assertEquals("王五", members.first().name)
    }
}
