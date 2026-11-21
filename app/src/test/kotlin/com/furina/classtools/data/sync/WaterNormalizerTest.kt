package com.furina.classtools.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterNormalizerTest {

    private val json: Json = kotlinx.serialization.json.Json

    private fun parse(text: String) = json.parseToJsonElement(text)

    @Test
    fun `归一人数、丢弃坏房间`() {
        val section = parse(
            """
            {"rooms":{
              "4-23A---305":{"p":6,"recs":[{"m":"2026-10","r":100.456,"ts":1}]},
              "1-13---301":{"p":9,"recs":[]},
              "bad-room":"oops",
              "arr-room":[1,2],
              "no-room":{}
            }}
            """.trimIndent(),
        )

        val normalized = WaterNormalizer.normalize(section)
        val rooms = normalized["rooms"]!!.jsonObject

        // 与网页 normWater 一致：坏房间（非对象）丢弃；空对象房间保留并补默认 {p:4,recs:[]}。
        assertEquals(setOf("4-23A---305", "1-13---301", "no-room"), rooms.keys)
        assertEquals(6, rooms["4-23A---305"]!!.jsonObject["p"]!!.jsonPrimitive.int)
        val recs = rooms["4-23A---305"]!!.jsonObject["recs"]!! as JsonArray
        assertEquals(100.46, recs[0].jsonObject["r"]!!.jsonPrimitive.content.toDouble(), 1e-9)
        // p=9 → 默认 4
        assertEquals(4, rooms["1-13---301"]!!.jsonObject["p"]!!.jsonPrimitive.int)
        assertEquals(4, rooms["no-room"]!!.jsonObject["p"]!!.jsonPrimitive.int)
        assertTrue((rooms["no-room"]!!.jsonObject["recs"]!! as JsonArray).isEmpty())
    }

    @Test
    fun `记录按月唯一、升序、过滤非法`() {
        val records = WaterNormalizer.normalizeRecords(
            parse(
                """
                [
                  {"m":"2026-11","r":130.10,"ts":3},
                  {"m":"2026-09","r":80,"ts":1},
                  {"m":"2026-10","r":93.5,"ts":2},
                  {"m":"2026-13","r":99,"ts":0},
                  {"m":"2026-10","r":94.5,"ts":4},
                  {"m":"2026-08","r":-1,"ts":5},
                  {"m":"2026-07","r":"abc","ts":6}
                ]
                """.trimIndent(),
            ),
        )

        assertEquals(listOf("2026-09", "2026-10", "2026-11"), records.map {
            (it as JsonObject)["m"]!!.jsonPrimitive.content
        })
        // 同月后者覆盖
        assertEquals(94.5, (records[1] as JsonObject)["r"]!!.jsonPrimitive.content.toDouble(), 1e-9)
    }

    @Test
    fun `超过 120 条保留最新月份`() {
        val items = (1..130).joinToString(",") { i ->
            val month = 2020 + (i - 1) / 12
            val m = (i - 1) % 12 + 1
            """{"m":"${month}-${m.toString().padStart(2, '0')}","r":$i}"""
        }
        val records = WaterNormalizer.normalizeRecords(parse("[$items]"))

        assertEquals(120, records.size)
        assertEquals("2020-11", (records.first() as JsonObject)["m"]!!.jsonPrimitive.content)
        assertEquals("2030-10", (records.last() as JsonObject)["m"]!!.jsonPrimitive.content)
    }

    @Test
    fun `空或坏 section 归一为 rooms 空对象`() {
        val normalized = WaterNormalizer.normalize(null)
        assertTrue(normalized["rooms"]!!.jsonObject.isEmpty())

        val badShape = WaterNormalizer.normalize(JsonPrimitive("oops"))
        assertTrue(badShape["rooms"]!!.jsonObject.isEmpty())
    }
}
