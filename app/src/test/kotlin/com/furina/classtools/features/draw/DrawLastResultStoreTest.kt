package com.furina.classtools.features.draw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「最近一次抽签结果」本机存储单测（契约 `web/group/CONTRACT.md` §3）：
 * 读写、跨实例（模拟进程重启）读取、脏数据容错、从 DrawResult 生成快照。
 */
class DrawLastResultStoreTest {

    private fun sample() = DrawLastResult(
        atMillis = 1_794_016_800_000L,
        result = listOf(7, 23),
        pool = listOf(5, 7, 23),
        config = DrawLastResultConfig(
            start = 1,
            end = 30,
            count = 2,
            allowDuplicates = false,
            sort = DrawSortMode.DESCENDING,
        ),
    )

    @Test
    fun deviceSnapshotRequiresAnOwnerAcrossLoginsAndProcessRestart() {
        val old = DrawLastResultCodec.encode(sample().copy(result = listOf(99)))
        val prefs = mutableMapOf("last_v1" to old)
        fun store(sid: String) = KeyValueDrawLastResultStore(drawResultKey(sid, false)!!,
            { prefs[it] }, { key, value -> prefs[key] = value })
        assertNull(drawResultKey(null, false))
        assertNull(drawResultKey("A", true))
        assertNull(store("A").load())
        store("A").save(sample())
        assertNull(store("B").load())
        val next = sample().copy(result = listOf(8))
        store("B").save(next)
        assertEquals(sample(), store("A").load())
        assertEquals(next, store("B").load())
        assertEquals(old, prefs["last_v1"])
    }

    @Test
    fun `codec round trip 保留序号 参数快照与时间`() {
        val value = sample()
        val encoded = DrawLastResultCodec.encode(value)
        val decoded = DrawLastResultCodec.decode(encoded)!!

        assertEquals(value, decoded)
        assertEquals(1_794_016_800_000L, decoded.atMillis)
        assertEquals(listOf(7, 23), decoded.result)
        assertEquals(listOf(5, 7, 23), decoded.pool)
        assertEquals(DrawSortMode.DESCENDING, decoded.config?.sort)
        assertTrue(encoded.contains("\"result\":[7,23]"))
        assertEquals(encoded, DrawLastResultCodec.encode(decoded)) // 幂等
    }

    @Test
    fun `键值存储跨实例读取 模拟进程重启后仍可读`() {
        val backing = mutableMapOf<String, String>()
        fun newStore() = KeyValueDrawLastResultStore(
            key = "last",
            read = { key -> backing[key] },
            write = { key, value -> backing[key] = value },
        )

        newStore().save(sample())
        // 新实例 = 进程重启后重新创建存储，数据仍在 SharedPreferences 等持久化层。
        val reloaded = newStore().load()

        assertEquals(sample(), reloaded)
        assertEquals(listOf(7, 23), reloaded?.result)
    }

    @Test
    fun `空值与坏 JSON 返回 null 不崩溃`() {
        assertNull(DrawLastResultCodec.decode(null))
        assertNull(DrawLastResultCodec.decode(""))
        assertNull(DrawLastResultCodec.decode("not-json"))
        assertNull(DrawLastResultCodec.decode("[]"))
        assertNull(DrawLastResultCodec.decode("""{"v":1,"result":[]}"""))
        assertNull(DrawLastResultCodec.decode("""{"v":1,"result":[0,1000]}"""))
    }

    @Test
    fun `脏序号被过滤且缺省 cfg 兼容为 null`() {
        val decoded = DrawLastResultCodec.decode(
            """{"v":1,"at":123,"result":[0,5,1000,5,7],"pool":[3,3],"unknown":"x"}""",
        )!!

        assertEquals(listOf(5, 7), decoded.result)
        assertEquals(listOf(3), decoded.pool)
        assertNull(decoded.config)
        assertEquals(123L, decoded.atMillis)
    }

    @Test
    fun `从 DrawResult 生成最近抽签快照`() {
        val config = DrawConfig(
            start = 2,
            end = 40,
            count = 3,
            allowDuplicates = true,
            sort = DrawSortMode.ASCENDING,
        )
        val result = DrawResult(
            entries = listOf(DrawEntry(9, "李四"), DrawEntry(3, null)),
            pool = listOf(DrawEntry(3), DrawEntry(9), DrawEntry(12)),
            config = config,
        )

        val last = DrawLastResult.from(result, nowMillis = 777L)

        assertEquals(777L, last.atMillis)
        assertEquals(listOf(9, 3), last.result)
        assertEquals(listOf(3, 9, 12), last.pool)
        assertEquals(2, last.config?.start)
        assertEquals(40, last.config?.end)
        assertEquals(3, last.config?.count)
        assertTrue(last.config?.allowDuplicates == true)
        assertEquals(DrawSortMode.ASCENDING, last.config?.sort)
        assertFalse(last.isEmpty)
    }
}
