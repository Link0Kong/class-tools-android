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
                    assertEquals(proof.id, body.getValue("p_id").jsonPrimitive.content)
                    assertEquals("draw", body.getValue("p_params").jsonObject.getValue("sort").jsonPrimitive.content)
                    assertEquals(listOf(9, 10, 4), body.getValue("p_result").jsonArray.map { it.jsonPrimitive.int })
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
        val receipt = publisher.publish(changed)
        assertNotNull(receipt)
        var displayed = changed
        for (sort in listOf(DrawSortMode.ASCENDING, DrawSortMode.DESCENDING, DrawSortMode.RANDOM)) {
            val state = DrawWorkflowState(DrawStep.RESULT, displayed.config, result = displayed)
            val next = (DrawWorkflow.changeSort(state, sort, proof.random()) as DrawTransition.Next).state.result!!
            displayed = next.copy(proof = displayed.proof!!.after(displayed, next))
            assertEquals(changed.proof, displayed.proof)
            assertEquals(receipt, publisher.publish(displayed))
        }
        assertEquals(receipt, publisher.publish(displayed))
        assertEquals(1, events.size)
        assertEquals("remove", events.single()["kind"]?.jsonPrimitive?.content)
        val restored = DrawWorkflowCodec.fromJson(DrawWorkflowCodec.toJson(DrawWorkflowState(DrawStep.RESULT, config, result = changed)))
        assertEquals(changed.proof, restored.result?.proof)

        // Older clients persisted display-sort events. Their exact position and note still verify.
        val legacyEvent = RoundEvent("sort", note = "asc")
        val historical = changed.copy(proof = changed.proof!!.copy(events = changed.proof.events + legacyEvent))
        events.add(buildJsonObject { put("kind", "sort"); put("note", "asc") })
        assertEquals(receipt, publisher.publish(historical))
        assertNull(publisher.publish(changed))
        assertEquals(2, events.size)
    }
}
