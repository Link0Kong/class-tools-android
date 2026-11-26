package com.furina.classtools.data.sync

import com.furina.classtools.Fixtures
import com.furina.classtools.data.network.NetJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v6 合并算法对拍：金标准 = `mp-class-tools-tests/_sync_fixtures/`（merge_ref.js + expected_merge_*）。
 * 夹具只读拷贝在 `app/src/test/resources/sync_fixtures/`。
 */
class UserdataMergeTest {

    @Test
    fun frozenCrossPlatformVectors() {
        val cases = Fixtures.jsonObject("contract_v1.json").getValue("cases") as kotlinx.serialization.json.JsonArray
        assertEquals(6, cases.size)
        for (entry in cases) {
            val case = entry as JsonObject
            val id = case.getValue("id").toString()
            val result = UserdataMerge.merge(case["remote"] as JsonObject, case["local"] as JsonObject)
            fun value(path: String): JsonElement? =
                path.split('.').fold(result as JsonElement?) { obj, key -> (obj as? JsonObject)?.get(key) }
            for ((path, expected) in case["expect"] as JsonObject) {
                assertEquals("$id $path", expected, value(path))
            }
            val absent = case["absent"] as? kotlinx.serialization.json.JsonArray
            absent?.forEach { path -> assertEquals("$id $path", null, value((path as JsonPrimitive).content)) }
            if (case["legacyEmptyDraw"] == JsonPrimitive(true)) {
                assertTrue(id, result["draw"] in setOf(JsonObject(emptyMap()), buildJsonObject { put("names", "") }))
            }
        }
    }

    private fun localV5Fixture(): JsonObject = buildJsonObject {
        put("at", JsonObject(emptyMap()))
        put(
            "fit",
            buildJsonObject { put("sex", JsonPrimitive("m")) },
        )
    }

    @Test
    fun `merge(mp, web) 与 expected_merge_web_mp 逐字节一致`() {
        val remoteMp = Fixtures.jsonObject("userdata_v6_mp.json")
        val localWeb = Fixtures.jsonObject("userdata_v6_web.json")
        val expected = Fixtures.jsonObject("expected_merge_web_mp.json")

        val actual = UserdataMerge.merge(remoteMp, localWeb)

        assertEquals(expected, actual)
        // 顶层键顺序必须一致（逐字节依赖它）
        assertEquals(expected.keys.toList(), actual.keys.toList())
        assertEquals(UserdataV6.TOP_LEVEL_KEYS, actual.keys.toList())
        // 序列化后字节一致
        assertEquals(UserdataV6.encode(expected), UserdataV6.encode(actual))
    }

    @Test
    fun `merge(web, mp) 与 expected_merge_mp_web 逐字节一致`() {
        val remoteWeb = Fixtures.jsonObject("userdata_v6_web.json")
        val localMp = Fixtures.jsonObject("userdata_v6_mp.json")
        val expected = Fixtures.jsonObject("expected_merge_mp_web.json")

        val actual = UserdataMerge.merge(remoteWeb, localMp)

        assertEquals(expected, actual)
        assertEquals(expected.keys.toList(), actual.keys.toList())
        assertEquals(UserdataV6.encode(expected), UserdataV6.encode(actual))
    }

    @Test
    fun `v5 迁移与 expected_v5_migrate 一致`() {
        val legacy = Fixtures.jsonObject("legacy_v5.json")
        val expected = Fixtures.jsonObject("expected_v5_migrate.json")

        val actual = UserdataMerge.merge(legacy, localV5Fixture())

        assertEquals(expected, actual)
        assertEquals(6, (actual["v"] as JsonPrimitive).content.toInt())
        assertEquals(
            UserdataV6.numMs(legacy["homeToolsAt"]),
            UserdataV6.numMs(actual["homeToolsAt"]),
        )
        assertEquals(
            UserdataV6.numMs(actual["homeToolsAt"]),
            UserdataV6.atValue(actual, "homeTools"),
        )
    }

    @Test
    fun `同时间戳云端赢（tie-break）`() {
        val remote = buildJsonObject {
            put("v", JsonPrimitive(6))
            put("at", buildJsonObject { put("fit", JsonPrimitive(100L)) })
            put("fit", buildJsonObject { put("sex", JsonPrimitive("m")) })
        }
        val local = buildJsonObject {
            put("at", buildJsonObject { put("fit", JsonPrimitive(100L)) })
            put("fit", buildJsonObject { put("sex", JsonPrimitive("f")) })
        }

        val merged = UserdataMerge.merge(remote, local)

        assertEquals(buildJsonObject { put("sex", JsonPrimitive("m")) }, merged["fit"])
    }

    @Test
    fun `本地更新时本地赢`() {
        val remote = buildJsonObject {
            put("v", JsonPrimitive(6))
            put("at", buildJsonObject { put("nick", JsonPrimitive(100L)) })
            put("nick", JsonPrimitive("remote"))
        }
        val local = buildJsonObject {
            put("at", buildJsonObject { put("nick", JsonPrimitive(101L)) })
            put("nick", JsonPrimitive("local"))
        }

        val merged = UserdataMerge.merge(remote, local)

        assertEquals("local", (merged["nick"] as JsonPrimitive).content)
        assertEquals(101L, merged.atValueOrZero("nick"))
    }

    private fun JsonObject.atValueOrZero(key: String): Long = UserdataV6.atValue(this, key)

    @Test
    fun `双方缺失时输出默认值与 at 0`() {
        val remote = buildJsonObject { put("v", JsonPrimitive(6)) }
        val local = buildJsonObject { put("at", JsonObject(emptyMap())) }

        val merged = UserdataMerge.merge(remote, local)

        assertEquals(UserdataV6.defaultSection("homeTools"), merged["homeTools"])
        assertEquals(UserdataV6.defaultSection("power"), merged["power"])
        assertEquals(UserdataV6.defaultSection("water"), merged["water"])
        assertEquals(0L, UserdataV6.atValue(merged, "fit"))
        assertEquals(0L, UserdataV6.numMs(merged["homeToolsAt"]))
    }

    @Test
    fun `空 remote 与坏数据不覆盖本机`() {
        val local = buildJsonObject {
            put("at", buildJsonObject { put("nick", JsonPrimitive(5L)) })
            put("nick", JsonPrimitive("本机昵称"))
        }

        val merged = UserdataMerge.merge(null, local)

        assertEquals("本机昵称", (merged["nick"] as JsonPrimitive).content)
        assertEquals(5L, UserdataV6.atValue(merged, "nick"))
    }

    @Test
    fun `合并幂等`() {
        val blob = Fixtures.jsonObject("expected_merge_web_mp.json")
        assertEquals(blob, UserdataMerge.merge(blob, blob))
        assertEquals(blob, UserdataMerge.merge(blob, null))
    }

    @Test
    fun `localView 脏 section 抢时间戳 max(now, base+1, remote+1)`() {
        val base = buildJsonObject {
            put("v", JsonPrimitive(6))
            put("at", buildJsonObject { put("homeTools", JsonPrimitive(100L)) })
            put("homeTools", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("hw"))))
        }
        val remote = buildJsonObject {
            put("at", buildJsonObject { put("homeTools", JsonPrimitive(500L)) })
        }
        val current = buildJsonObject {
            put("homeTools", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("tt"))))
        }

        val view = UserdataMerge.localView(base, current, remote, now = 1_000L)

        assertEquals(1_000L, UserdataV6.atValue(view, "homeTools")) // max(1000, 101, 501)
        assertEquals(current["homeTools"], view["homeTools"])
        assertEquals(
            UserdataV6.atValue(view, "homeTools"),
            UserdataV6.numMs(view["homeToolsAt"]),
        )
    }

    @Test
    fun `localView 无基线时不标脏（at 不抬高）`() {
        val current = buildJsonObject {
            put("homeTools", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("hw"))))
        }

        val view = UserdataMerge.localView(base = null, current = current, remote = null, now = 9_999L)

        assertEquals(0L, UserdataV6.atValue(view, "homeTools"))
        assertEquals(0L, UserdataV6.numMs(view["homeToolsAt"]))
        assertEquals(current["homeTools"], view["homeTools"])
    }

    @Test
    fun `localView forceKeys 强制脏`() {
        val current = buildJsonObject {
            put("homeTools", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("hw"))))
        }

        val view = UserdataMerge.localView(
            base = null,
            current = current,
            remote = buildJsonObject { put("at", buildJsonObject { put("homeTools", JsonPrimitive(700L)) }) },
            forceKeys = setOf("homeTools"),
            now = 1_000L,
        )

        assertEquals(1_000L, UserdataV6.atValue(view, "homeTools")) // max(1000, 0+1, 700+1)
    }

    @Test
    fun `localView 镜像 power 值内 at`() {
        val base = buildJsonObject {
            put("v", JsonPrimitive(6))
            put("at", buildJsonObject { put("power", JsonPrimitive(10L)) })
            put(
                "power",
                buildJsonObject {
                    put("sel", JsonPrimitive(""))
                    put("at", JsonPrimitive(10L))
                    put("low", JsonPrimitive(30))
                },
            )
        }
        val current = buildJsonObject {
            put(
                "power",
                buildJsonObject {
                    put("sel", JsonPrimitive("4-23A---305"))
                    put("at", JsonPrimitive(0L))
                    put("low", JsonPrimitive(30))
                },
            )
        }

        val view = UserdataMerge.localView(base, current, remote = null, now = 2_000L)
        val power = view["power"] as JsonObject
        val atPower = UserdataV6.atValue(view, "power")
        assertEquals(atPower, UserdataV6.numMs(power["at"]))
        assertEquals(2_000L, atPower)
        assertEquals("4-23A---305", (power["sel"] as JsonPrimitive).content)
    }

    @Test
    fun `localView 不拥有 section 时从基线原样带上`() {
        val base = buildJsonObject {
            put("v", JsonPrimitive(6))
            put("at", buildJsonObject { put("prefs", JsonPrimitive(50L)) })
            put(
                "prefs",
                buildJsonObject {
                    put("ntSub", JsonPrimitive(true))
                    put("ntSubAt", JsonPrimitive(50L))
                },
            )
        }
        val current = buildJsonObject {
            put(
                "prefs",
                buildJsonObject { put("ntSub", JsonPrimitive(false)) },
            )
        }

        val view = UserdataMerge.localView(
            base = base,
            current = current,
            remote = null,
            now = 1_000L,
            ownedSections = setOf("homeTools"),
        )

        assertEquals(base["prefs"], view["prefs"])
        assertEquals(50L, UserdataV6.atValue(view, "prefs"))
    }

    @Test
    fun `dirtySections 检测改动与缺键`() {
        val base = UserdataMerge.merge(null, null)
        val current = UserdataV6.sectionsOf(base).toMutableMap()
        current["nick"] = JsonPrimitive("新昵称")

        val dirty = UserdataMerge.dirtySections(base, JsonObject(current))

        assertEquals(setOf("nick"), dirty)
    }

    @Test
    fun `changedKeysVsDefaults 跳过 prefs 且识别真实改动`() {
        val current = LinkedHashMap<String, JsonElement>()
        UserdataV6.SECTION_KEYS.forEach { current[it] = UserdataV6.defaultSection(it) }
        current["fit"] = buildJsonObject { put("sex", JsonPrimitive("m")) }
        current["prefs"] = buildJsonObject { put("ntSub", JsonPrimitive(true)) }

        val changed = UserdataMerge.changedKeysVsDefaults(JsonObject(current))

        assertEquals(setOf("fit"), changed)
    }

    @Test
    fun `merge 结果与输入不同不相等（防御性）`() {
        val a = UserdataMerge.merge(null, null)
        val b = UserdataMerge.merge(
            buildJsonObject {
                put("v", JsonPrimitive(6))
                put("at", buildJsonObject { put("nick", JsonPrimitive(9L)) })
                put("nick", JsonPrimitive("x"))
            },
            null,
        )
        assertNotEquals(a, b)
        assertFalse(a == b)
    }
}
