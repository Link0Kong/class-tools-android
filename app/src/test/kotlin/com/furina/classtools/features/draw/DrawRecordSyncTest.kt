package com.furina.classtools.features.draw

import com.furina.classtools.data.draw.DrawRecordPublisher
import com.furina.classtools.data.network.*
import com.furina.classtools.features.scan.DrawProofVerifier
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DrawRecordSyncTest {
    @Test fun sameRoundIsReproducibleAndRetryDoesNotAppendAnAdjustmentTwice() = runBlocking {
        val proof = DrawRoundProof("dtest3260799999", "0123456789abcdef", "draw")
        val config = DrawConfig(end = 10, count = 3)
        val drawn = (DrawEngine.draw(config, emptyList(), { null }, proof.random()) as DrawOutcome.Success).result.copy(proof = proof)
        assertEquals(listOf(9, 10, 4), drawn.entries.map { it.number })
        val adjusted = (DrawEngine.removeAndRedraw(drawn, 0, true, proof.random()) as DrawOutcome.Success).result
        val changed = adjusted.copy(proof = proof.after(drawn, adjusted))
        val events = mutableListOf<JsonObject>()
        val fake = HttpTransport { request ->
            val body = Json.parseToJsonElement(request.body!!).jsonObject
            val response = buildJsonObject {
                put("ok", true)
                if (request.url.endsWith("draw_record_create")) {
                    assertEquals(JsonArray(emptyList()), body["p_labels"])
                    put("record", buildJsonObject {
                        put("id", proof.id); put("seed", proof.seed); put("canon", "测试记录")
                        put("hash", DrawProofVerifier.sha256Hex("测试记录")); put("result", body.getValue("p_result"))
                        put("events", JsonArray(events)); put("at", 100L); put("seq_day", 1); put("today_count", 1)
                    })
                } else {
                    events.add(JsonObject(body.filterKeys { it != "p_id" }.mapKeys { it.key.removePrefix("p_") }))
                }
            }
            ApiResponse(200, response.toString())
        }
        val publisher = DrawRecordPublisher(ApiClient("https://example.test/", "test", { "test" }, fake))
        assertNotNull(publisher.publish(changed))
        assertNotNull(publisher.publish(changed))
        assertEquals(1, events.size)
        assertEquals("remove", events.single()["kind"]?.jsonPrimitive?.content)
        val restored = DrawWorkflowCodec.fromJson(DrawWorkflowCodec.toJson(DrawWorkflowState(DrawStep.RESULT, config, result = changed)))
        assertEquals(changed.proof, restored.result?.proof)
    }
}
