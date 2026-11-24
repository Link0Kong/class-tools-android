package com.furina.classtools.data.draw

import com.furina.classtools.data.network.ApiError
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.features.scan.DrawProofVerifier
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawProofParserTest {

    private val canon = "v1\ndabc123456\n1700000000000\n2026-11-23\n3\ndraw-v1\n0123456789abcdef\n\n" +
        "{\"range\":[1,10],\"count\":5,\"dup\":false}\n[1,2,3,4,5,6,7,8,9,10]\n[9,10,4,6,7]\n[]"
    private val hash = DrawProofVerifier.sha256Hex(canon)
    private val canonJson = JsonPrimitive(canon).toString()

    private fun recordJson(hashValue: String = hash): String = """
        {"ok":true,"record":{
          "id":"dabc123456","at":1700000000000,"day":"2026-11-23","seq_day":3,"today_count":10,
          "is_mine":false,"op_name":"测试同学","op_seat":5,
          "params":{"range":[1,10],"count":5,"dup":false,"excl":[3],"rosterN":0,"namesN":0},
          "pool":[1,2,3,4,5,6,7,8,9,10],"result":[9,10,4,6,7],
          "labels":[{"n":9,"nm":"甲"}],
          "seed":"0123456789abcdef","algo":"draw-v1","hash":"$hashValue","canon":$canonJson,
          "prev_hash":"","event_count":2,
          "events":[{"seq":1,"kind":"remove","n":9,"added":10,"note":""}]
        }}
    """.trimIndent()

    @Test
    fun `parses record and verifies hash plus replay`() {
        val result = DrawProofParser.parse(recordJson())
        assertTrue(result is ApiResult.Success)
        val verified = (result as ApiResult.Success).value
        assertEquals("dabc123456", verified.record.id)
        assertEquals(3, verified.record.seqDay)
        assertEquals("测试同学", verified.record.operatorName)
        assertEquals(5, verified.record.operatorSeat)
        assertEquals(5, verified.record.params.count)
        assertEquals(listOf(3), verified.record.params.excluded)
        assertEquals(listOf(9, 10, 4, 6, 7), verified.record.result)
        assertEquals("甲", verified.record.labels.single().name)
        assertEquals(2, verified.record.eventCount)
        assertEquals(1, verified.record.events.size)
        assertTrue(verified.hashOk)
        assertTrue(verified.replayOk)
    }

    @Test
    fun `tampered hash and mismatched result fail verification`() {
        val tampered = recordJson(hashValue = "0".repeat(64))
            .replace("\"result\":[9,10,4,6,7]", "\"result\":[1,2,3,4,5]")
        val verified = (DrawProofParser.parse(tampered) as ApiResult.Success).value
        assertFalse(verified.hashOk)
        assertFalse(verified.replayOk)
    }

    @Test
    fun `server rejection maps to config error`() {
        val result = DrawProofParser.parse("""{"ok":false,"reason":"记录不存在"}""")
        assertTrue(result is ApiResult.Failure)
        val error = (result as ApiResult.Failure).error
        assertTrue(error is ApiError.Config)
        assertEquals("记录不存在", error.displayMessage)
    }

    @Test
    fun `bad json maps to parse error`() {
        val result = DrawProofParser.parse("not-json")
        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is ApiError.Parse)
    }
}
