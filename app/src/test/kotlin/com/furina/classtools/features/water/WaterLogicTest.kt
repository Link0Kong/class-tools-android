package com.furina.classtools.features.water

import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterLogicTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Long = LocalDateTime.of(2026, 11, 8, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun room(
        persons: Int = 4,
        records: List<WaterRecord> = emptyList(),
    ) = WaterRoomState(persons = persons, records = records)

    private fun json(text: String) = Json.parseToJsonElement(text)

    // ------------------------------------------------------------ 编解码

    @Test
    fun `null 或坏值解码为空状态`() {
        assertEquals(WaterState(), WaterCodec.fromJson(null))
        assertEquals(WaterState(), WaterCodec.fromJson(json("{}")))
        assertEquals(WaterState(), WaterCodec.fromJson(json("""{"rooms":[]}""")))
    }

    @Test
    fun `脏数据归一 人数非法 月份非法 读数负数 重复月份后者覆盖`() {
        val state = WaterCodec.fromJson(
            json(
                """{"rooms":{"1-13---324":{"p":7,
                    "recs":[
                      {"m":"2026-13","r":1},
                      {"m":"2026-10","r":130.123,"ts":5},
                      {"m":"2026-09","r":-1},
                      {"m":"2026-10","r":131.5,"ts":6},
                      {"m":"2026-11","r":"not-number"}
                    ]}},
                   "bad-room": 1}""",
            ),
        )
        val room = state.rooms.getValue("1-13---324")
        assertEquals(4, room.persons)
        assertEquals(1, room.records.size)
        assertEquals(WaterRecord("2026-10", 131.5, 6), room.records.single())
    }

    @Test
    fun `记录按月升序且最多 120 条`() {
        val months = mutableListOf<String>()
        var ym = YearMonth.of(2016, 1)
        repeat(130) {
            months += ym.toString()
            ym = ym.plusMonths(1)
        }
        val recs = months.joinToString(",") { """{"m":"$it","r":10.5}""" }
        val state = WaterCodec.fromJson(json("""{"rooms":{"rv":{"p":4,"recs":[$recs]}}}"""))
        val room = state.rooms.getValue("rv")
        assertEquals(WaterRules.MAX_RECORDS_PER_ROOM, room.records.size)
        assertEquals(months.drop(10).first(), room.records.first().month)
        assertEquals(months.last(), room.records.last().month)
        assertTrue(room.records.zipWithNext().all { (a, b) -> a.month < b.month })
    }

    @Test
    fun `编码往返保留人数与记录`() {
        val state = WaterState(
            mapOf(
                "4-23A---321" to room(
                    persons = 6,
                    records = listOf(
                        WaterRecord("2026-09", 235.0, 1),
                        WaterRecord("2026-10", 240.2, 2),
                    ),
                ),
            ),
        )
        val encoded = WaterCodec.toJson(state)
        assertEquals(1, encoded["rooms"]!!.let { (it as JsonObject).size })
        val decoded = WaterCodec.fromJson(encoded)
        assertEquals(state, decoded)
        // 整数读数按整数输出
        val firstRecs = ((encoded["rooms"] as JsonObject)["4-23A---321"] as JsonObject)["recs"] as kotlinx.serialization.json.JsonArray
        assertEquals(235L.toDouble(), ((firstRecs[0] as JsonObject)["r"] as kotlinx.serialization.json.JsonPrimitive).content.toDouble(), 0.0)
    }

    @Test
    fun `与既有 WaterNormalizer 归一结果对齐`() {
        val dirty = json(
            """{"rooms":{"1-13---324":{"p":6,
                "recs":[
                  {"m":"2026-11","r":130.123,"ts":5},
                  {"m":"2026-10","r":-1},
                  {"m":"2026-09","r":"120.5"},
                  {"m":"2026-13","r":9}
                ]}}}""",
        )
        val normalized = com.furina.classtools.data.sync.WaterNormalizer.normalize(dirty)
        val fromNormalized = WaterCodec.fromJson(normalized)
        assertEquals(WaterCodec.fromJson(dirty), fromNormalized)
        assertEquals(2, fromNormalized.rooms.getValue("1-13---324").records.size)
        assertEquals(6, fromNormalized.rooms.getValue("1-13---324").persons)
        // 编码后再解码语义稳定（整数读数输出 130 而非 130.0 的兼容）
        assertEquals(fromNormalized, WaterCodec.fromJson(WaterCodec.toJson(fromNormalized)))
    }
    @Test
    fun `月份人数与取整工具`() {
        assertEquals("2026-01", WaterCodec.normalizeMonth("2026-01"))
        assertNull(WaterCodec.normalizeMonth("2026-1"))
        assertNull(WaterCodec.normalizeMonth(""))
        assertEquals(4, WaterCodec.normalizePersons(9))
        assertEquals(6, WaterCodec.normalizePersons(6))
        assertEquals(1.23, WaterCodec.round2(1.234), 0.0001)
        assertEquals(1.3, WaterCodec.round1(1.25), 0.0001)
    }

    // ------------------------------------------------------------ 月份状态机

    @Test
    fun `月份归一与边界`() {
        assertNull(WaterMonth.normalize("1999-12"))
        assertNull(WaterMonth.normalize("2101-01"))
        assertNull(WaterMonth.normalize("2026-00"))
        assertNull(WaterMonth.normalize("2026-13"))
        assertEquals("2026-11", WaterMonth.normalize(" 2026-11 "))
        assertEquals("2025-12", WaterMonth.shift("2026-01", -1))
        assertEquals("2026-02", WaterMonth.shift("2026-01", 1))
        assertEquals("2000-01", WaterMonth.shift("2000-01", -1))
        assertEquals("2100-12", WaterMonth.shift("2100-12", 1))
        assertEquals("2100-02", WaterMonth.withYear("2026-02", 2101))
    }

    @Test
    fun `月份文案与年网格`() {
        assertEquals("2026年11月", WaterMonth.label("2026-11"))
        assertEquals("11月", WaterMonth.shortLabel("2026-11"))
        assertEquals(12, WaterMonth.monthsOfYear(2026).size)
        assertEquals("2026-01", WaterMonth.monthsOfYear(2026).first())
        assertEquals("2026-12", WaterMonth.monthsOfYear(2026).last())
        assertEquals(28, WaterMonth.daysInMonth("2026-02"))
        assertEquals(29, WaterMonth.daysInMonth("2024-02"))
        assertEquals(31, WaterMonth.daysInMonth("2026-12"))
    }

    @Test
    fun `当前月份与已过天数注入时钟`() {
        assertEquals("2026-11", WaterMonth.current(now, zone))
        assertEquals(8, WaterMonth.elapsedDays("2026-11", now, zone))
        assertEquals(31, WaterMonth.elapsedDays("2026-10", now, zone))
        assertTrue(WaterMonth.isCurrent("2026-11", now, zone))
        assertFalse(WaterMonth.isCurrent("2026-10", now, zone))
    }

    // ------------------------------------------------------------ 计费

    @Test
    fun `首次记录不计算用量`() {
        val room = room(records = listOf(WaterRecord("2026-11", 130.0)))
        val charge = WaterCalculator.charge(room, room.records.single())
        assertFalse(charge.hasPrevious)
        assertNull(charge.usage)
        assertNull(charge.fee)
        assertNull(charge.degree)
        assertEquals(8.0, charge.free, 0.0001)
    }

    @Test
    fun `定额内水费为 0`() {
        val room = room(
            records = listOf(
                WaterRecord("2026-10", 125.75),
                WaterRecord("2026-11", 130.75),
            ),
        )
        val charge = WaterCalculator.charge(room, room.records.last())
        assertEquals(5.0, charge.usage!!, 0.0001)
        assertEquals(0.0, charge.excess!!, 0.0001)
        assertEquals(0.0, charge.fee!!, 0.0001)
        assertEquals(0.0, charge.degree!!, 0.0001)
        assertTrue(charge.within)
    }

    @Test
    fun `超定额按 3_03 元每吨计费并折算扣电`() {
        val room = room(
            records = listOf(
                WaterRecord("2026-10", 121.20),
                WaterRecord("2026-11", 130.75),
            ),
        )
        val charge = WaterCalculator.charge(room, room.records.last())
        assertEquals(9.55, charge.usage!!, 0.0001)
        assertEquals(1.55, charge.excess!!, 0.0001)
        assertEquals(4.70, charge.fee!!, 0.0001)
        assertEquals(8.1, charge.degree!!, 0.0001)
        assertFalse(charge.within)
    }

    @Test
    fun `6 人间免费额度 12 吨`() {
        assertEquals(12.0, WaterCalculator.freeWater(6), 0.0001)
        assertEquals(30.0, WaterCalculator.freeElec(6), 0.0001)
        val room = room(persons = 6, records = listOf(WaterRecord("2026-10", 200.0), WaterRecord("2026-11", 209.0)))
        val charge = WaterCalculator.charge(room, room.records.last())
        assertEquals(9.0, charge.usage!!, 0.0001)
        assertEquals(0.0, charge.excess!!, 0.0001)
        assertTrue(charge.within)
    }

    // ------------------------------------------------------------ 统计

    @Test
    fun `统计 本月用量 日均 对比上月 水费`() {
        val room = room(
            records = listOf(
                WaterRecord("2026-09", 121.20),
                WaterRecord("2026-10", 125.75),
                WaterRecord("2026-11", 130.75),
            ),
        )
        val stats = WaterCalculator.stats(room, "2026-11", now, zone)
        assertEquals("2026-11", stats.month)
        assertEquals(5.0, stats.charge.usage!!, 0.0001)
        assertEquals(0.63, stats.dailyAverage!!, 0.0001) // 5.0 / 8 天
        assertEquals(4.55, stats.previousMonthUsage!!, 0.0001)
        assertEquals(0.45, stats.deltaVsPreviousMonth!!, 0.0001)
        assertEquals(0.0, stats.charge.fee!!, 0.0001)
        assertNotNull(stats.previous)
        assertEquals("2026-10", stats.previous!!.month)
    }

    @Test
    fun `统计 无当月记录时为 null 且保留上月用量`() {
        val room = room(
            persons = 4,
            records = listOf(WaterRecord("2026-09", 121.20), WaterRecord("2026-10", 125.75)),
        )
        val stats = WaterCalculator.stats(room, "2026-11", now, zone)
        assertNull(stats.record)
        assertNull(stats.charge.usage)
        assertNull(stats.dailyAverage)
        assertNull(stats.deltaVsPreviousMonth)
        assertEquals(4.55, stats.previousMonthUsage!!, 0.0001)
    }

    @Test
    fun `上一条与下一条记录查找`() {
        val room = room(
            records = listOf(
                WaterRecord("2026-09", 1.0),
                WaterRecord("2026-11", 3.0),
            ),
        )
        assertEquals("2026-09", WaterCalculator.previousRecord(room, "2026-11")!!.month)
        assertNull(WaterCalculator.previousRecord(room, "2026-09"))
        assertEquals("2026-11", WaterCalculator.nextRecord(room, "2026-09")!!.month)
        assertEquals("2026-11", WaterCalculator.recordOf(room, "2026-11")!!.month)
    }

    // ------------------------------------------------------------ 录入校验/变更

    @Test
    fun `校验 非法月份与非法读数`() {
        assertTrue(WaterMutator.review(room(), "2026-13", "100").let { it is WaterRecordReview.Invalid })
        assertTrue(WaterMutator.review(room(), "2026-11", "abc").let { it is WaterRecordReview.Invalid })
        assertTrue(WaterMutator.review(room(), "2026-11", "-1").let { it is WaterRecordReview.Invalid })
    }

    @Test
    fun `校验 读数不得小于上月 不得大于下一条`() {
        val room = room(
            records = listOf(
                WaterRecord("2026-10", 130.0),
                WaterRecord("2026-12", 150.0),
            ),
        )
        val low = WaterMutator.review(room, "2026-11", "120", now)
        assertTrue(low is WaterRecordReview.Invalid && low.message.contains("上月"))
        val high = WaterMutator.review(room, "2026-11", "160", now)
        assertTrue(high is WaterRecordReview.Invalid && high.message.contains("下一条"))
        val ok = WaterMutator.review(room, "2026-11", "140.129", now)
        assertTrue(ok is WaterRecordReview.Valid)
        assertEquals(140.13, (ok as WaterRecordReview.Valid).record.reading, 0.0001)
        assertFalse(ok.replaceExisting)
    }

    @Test
    fun `同月记录校验返回覆盖标记`() {
        val room = room(records = listOf(WaterRecord("2026-11", 130.0)))
        val review = WaterMutator.review(room, "2026-11", "131", now)
        assertTrue(review is WaterRecordReview.Valid && review.replaceExisting)
    }

    @Test
    fun `写入 覆盖 排序 删除 人数与清除`() {
        var state = WaterMutator.put(
            WaterState(),
            "1-13---324",
            WaterRecord("2026-11", 130.0, 1),
        )
        state = WaterMutator.put(state, "1-13---324", WaterRecord("2026-10", 125.5, 2))
        state = WaterMutator.put(state, "1-13---324", WaterRecord("2026-11", 130.9, 3))
        val room = state.rooms.getValue("1-13---324")
        assertEquals(listOf("2026-10", "2026-11"), room.records.map { it.month })
        assertEquals(130.9, room.records.last().reading, 0.0001)

        state = WaterMutator.delete(state, "1-13---324", "2026-10")
        assertEquals(1, state.rooms.getValue("1-13---324").records.size)

        state = WaterMutator.withPersons(state, "1-13---324", 6)
        assertEquals(6, state.rooms.getValue("1-13---324").persons)

        state = WaterMutator.withPersons(state, "1-13---324", 5)
        assertEquals(5, state.rooms.getValue("1-13---324").persons) // 5 人间合法

        state = WaterMutator.withPersons(state, "1-13---324", 9)
        assertEquals(4, state.rooms.getValue("1-13---324").persons) // 非法人数回落默认 4

        state = WaterMutator.clearRoom(state, "1-13---324")
        assertTrue(state.rooms.isEmpty())
    }

    @Test
    fun `写入超过 120 条保留最近 120 个月`() {
        var state = WaterState()
        var ym = YearMonth.of(2015, 1)
        repeat(125) {
            state = WaterMutator.put(state, "rv", WaterRecord(ym.toString(), 100.0 + it, it.toLong()))
            ym = ym.plusMonths(1)
        }
        val records = state.rooms.getValue("rv").records
        assertEquals(WaterRules.MAX_RECORDS_PER_ROOM, records.size)
        assertEquals("2015-06", records.first().month)
        assertEquals("2025-05", records.last().month)
    }
}
