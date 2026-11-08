package com.furina.classtools.features.draw

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawConfigCodecTest {

    @Test
    fun `写出的 draw section 键序与线格式正确`() {
        val json = DrawConfigCodec.toJson(
            DrawConfig(
                start = 2,
                end = 50,
                count = 8,
                allowDuplicates = true,
                excludedText = "3,7",
                sort = DrawSortMode.DESCENDING,
            ),
        )

        assertEquals(listOf("start", "end", "count", "dup", "excl", "sort"), json.keys.toList())
        assertEquals(2, json["start"]!!.jsonPrimitive.int)
        assertEquals(50, json["end"]!!.jsonPrimitive.int)
        assertEquals(8, json["count"]!!.jsonPrimitive.int)
        assertTrue(json["dup"]!!.jsonPrimitive.boolean)
        assertEquals("3,7", json["excl"]!!.jsonPrimitive.content)
        assertEquals("desc", json["sort"]!!.jsonPrimitive.content)
    }

    @Test
    fun `空或坏数据回落默认值`() {
        assertEquals(DrawConfig(), DrawConfigCodec.fromJson(null))
        assertEquals(DrawConfig(), DrawConfigCodec.fromJson(JsonObject(emptyMap())))

        val bad = kotlinx.serialization.json.buildJsonObject {
            put("start", kotlinx.serialization.json.JsonPrimitive("oops"))
            put("count", kotlinx.serialization.json.JsonNull)
        }
        assertEquals(DrawConfig.DEFAULT_START, DrawConfigCodec.fromJson(bad).start)
        assertEquals(DrawConfig.DEFAULT_COUNT, DrawConfigCodec.fromJson(bad).count)
    }

    @Test
    fun `数值越界被 clamp`() {
        val json = kotlinx.serialization.json.buildJsonObject {
            put("start", kotlinx.serialization.json.JsonPrimitive(0))
            put("end", kotlinx.serialization.json.JsonPrimitive(5000))
            put("count", kotlinx.serialization.json.JsonPrimitive(-3))
        }
        val config = DrawConfigCodec.fromJson(json)
        assertEquals(DrawConfig.MIN_NUMBER, config.start)
        assertEquals(DrawConfig.MAX_NUMBER, config.end)
        assertEquals(DrawConfig.MIN_COUNT, config.count)
    }

    @Test
    fun `未知 sort 回落为按抽取顺序`() {
        val json = kotlinx.serialization.json.buildJsonObject {
            put("sort", kotlinx.serialization.json.JsonPrimitive("weird"))
        }
        assertEquals(DrawSortMode.DRAW_ORDER, DrawConfigCodec.fromJson(json).sort)
    }

    @Test
    fun `非字符串排除号回落为空`() {
        val json = kotlinx.serialization.json.buildJsonObject {
            put("excl", kotlinx.serialization.json.JsonPrimitive(123))
        }
        assertEquals("", DrawConfigCodec.fromJson(json).excludedText)
    }

    @Test
    fun `非布尔 dup 回落默认值`() {
        val json = kotlinx.serialization.json.buildJsonObject {
            put("dup", kotlinx.serialization.json.JsonPrimitive("not-bool"))
        }
        assertFalse(DrawConfigCodec.fromJson(json).allowDuplicates)
    }

    @Test
    fun `roundtrip 保持参数`() {
        val config = DrawConfig(
            start = 5,
            end = 60,
            count = 12,
            allowDuplicates = false,
            excludedText = "9, 15-17",
            sort = DrawSortMode.RANDOM,
            poolSource = DrawPoolSource.ROSTER,
        )
        val decoded = DrawConfigCodec.fromJson(DrawConfigCodec.toJson(config))
        assertEquals(config.copy(poolSource = DrawPoolSource.RANGE), decoded)
        // 候选来源不跨端（每次读取恒为 RANGE），其余字段原样恢复
        assertEquals(DrawPoolSource.RANGE, decoded.poolSource)
    }

    @Test
    fun `默认参数与 userdata 契约一致`() {
        val json = DrawConfigCodec.toJson(DrawConfigCodec.defaults())
        assertEquals(1, json["start"]!!.jsonPrimitive.int)
        assertEquals(70, json["end"]!!.jsonPrimitive.int)
        assertEquals(10, json["count"]!!.jsonPrimitive.int)
        assertFalse(json["dup"]!!.jsonPrimitive.boolean)
        assertEquals("", json["excl"]!!.jsonPrimitive.content)
        assertEquals("draw", json["sort"]!!.jsonPrimitive.content)
    }
}
