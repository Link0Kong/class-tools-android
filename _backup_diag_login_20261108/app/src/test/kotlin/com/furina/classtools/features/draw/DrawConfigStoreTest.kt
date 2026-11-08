package com.furina.classtools.features.draw

import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.data.sync.UserdataPullResult
import com.furina.classtools.data.sync.UserdataPushResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawConfigStoreTest {

    private class FakeUserDataRepository(initial: JsonObject) : UserDataRepository {
        private val state = MutableStateFlow(initial)
        var lastUpdated: JsonObject? = null

        override val currentSections: StateFlow<JsonObject> get() = state

        override suspend fun pull(force: Boolean): UserdataPullResult =
            UserdataPullResult.Success(changed = false, pushed = false)

        override suspend fun push(force: Boolean, forceKeys: Set<String>): UserdataPushResult =
            UserdataPushResult.Success(pushed = false)

        override suspend fun updateCurrent(sections: JsonObject) {
            lastUpdated = sections
            state.value = sections
        }

        override fun touch() = Unit

        override suspend fun flush(): UserdataPushResult = UserdataPushResult.Skipped

        override suspend fun setSyncEnabled(enabled: Boolean) = Unit

        override suspend fun syncEnabled(): Boolean = true

        override suspend fun cachedBlob(): JsonObject? = state.value

        override suspend fun hydrateFromLocal() = Unit
    }

    @Test
    fun `内存实现可读写`() = runTest {
        val store = InMemoryDrawConfigStore(DrawConfig(count = 5))
        assertEquals(5, store.load().count)
        store.save(DrawConfig(count = 9, sort = DrawSortMode.ASCENDING))
        assertEquals(9, store.load().count)
        assertEquals(DrawSortMode.ASCENDING, store.load().sort)
    }

    @Test
    fun `userdata 实现读取 draw section`() = runTest {
        val repo = FakeUserDataRepository(
            buildJsonObject {
                put("fit", buildJsonObject { put("sex", JsonPrimitive("m")) })
                put(
                    "draw",
                    buildJsonObject {
                        put("start", JsonPrimitive(3))
                        put("end", JsonPrimitive(30))
                        put("count", JsonPrimitive(6))
                        put("dup", JsonPrimitive(true))
                        put("excl", JsonPrimitive("4"))
                        put("sort", JsonPrimitive("desc"))
                    },
                )
            },
        )
        val store = UserDataDrawConfigStore(repo)

        val config = store.load()

        assertEquals(3, config.start)
        assertEquals(30, config.end)
        assertEquals(6, config.count)
        assertTrue(config.allowDuplicates)
        assertEquals("4", config.excludedText)
        assertEquals(DrawSortMode.DESCENDING, config.sort)
    }

    @Test
    fun `userdata 实现保存时只替换 draw 保留其他 section`() = runTest {
        val original = buildJsonObject {
            put("fit", buildJsonObject { put("sex", JsonPrimitive("f")) })
            put("nick", JsonPrimitive("陈同学"))
        }
        val repo = FakeUserDataRepository(original)
        val store = UserDataDrawConfigStore(repo)

        store.save(DrawConfig(start = 2, end = 40, count = 7))

        val updated = repo.lastUpdated!!
        // 其他 section 原样保留
        assertEquals("f", updated["fit"]!!.jsonObject["sex"]!!.jsonPrimitive.content)
        assertEquals("陈同学", updated["nick"]!!.jsonPrimitive.content)
        // draw section 写入契约字段
        val draw = updated["draw"]!!.jsonObject
        assertEquals(2, draw["start"]!!.jsonPrimitive.int)
        assertEquals(40, draw["end"]!!.jsonPrimitive.int)
        assertEquals(7, draw["count"]!!.jsonPrimitive.int)
        assertEquals("draw", draw["sort"]!!.jsonPrimitive.content)
    }

    @Test
    fun `无 draw 数据时返回默认值`() = runTest {
        val repo = FakeUserDataRepository(JsonObject(emptyMap()))
        val store = UserDataDrawConfigStore(repo)
        assertEquals(DrawConfig(), store.load())
    }
}
