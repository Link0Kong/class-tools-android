package com.furina.classtools.data.draw

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.features.draw.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

class DrawRecordPublisher(private val api: ApiClient) {
    private val lock = Mutex()
    private suspend fun rpc(method: String, body: JsonObject): JsonObject? {
        val response = api.post("rpc/" + method, body.toString()) as? ApiResult.Success ?: return null
        return runCatching { Json.parseToJsonElement(response.value).jsonObject }.getOrNull()
            ?.takeIf { it["ok"]?.jsonPrimitive?.booleanOrNull == true }
    }

    suspend fun publish(result: DrawResult): DrawReceipt? = lock.withLock {
        val proof = result.proof ?: return null
        val config = result.config
        val pool = result.pool.map { it.number }
        val original = proof.original(pool, config)
        fun numbers(values: List<Int>) = JsonArray(values.map(::JsonPrimitive))
        val reply = rpc("draw_record_create", buildJsonObject {
            put("p_id", proof.id)
            put("p_seed", proof.seed)
            put("p_algo", "draw-v1")
            put("p_pool", numbers(pool))
            put("p_result", numbers(original))
            put("p_labels", JsonArray(emptyList()))
            put("p_params", buildJsonObject {
                put("range", numbers(listOf(config.lowerBound, config.upperBound)))
                put("count", config.count)
                put("dup", config.allowDuplicates)
                put("sort", proof.sort)
                put("excl", numbers(DrawValidator.parseExcluded(config.excludedText).numbers.sorted()))
                put("rosterN", if (config.poolSource == DrawPoolSource.ROSTER) pool.size else 0)
                put("namesN", 0)
            })
        }) ?: return null
        val record = reply["record"] as? JsonObject ?: return null
        fun field(name: String) = (record[name] as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (field("id") != proof.id || field("seed") != proof.seed ||
            record["result"] != numbers(original) || !com.furina.classtools.features.scan.DrawProofVerifier.hashMatches(field("canon"), field("hash"))) return null
        val existing = record["events"] as? JsonArray ?: return null
        if (existing.size > proof.events.size) return null
        for ((i, event) in proof.events.withIndex()) {
            val prior = existing.getOrNull(i) as? JsonObject
            if (prior != null) {
                if (prior["kind"]?.jsonPrimitive?.content != event.kind ||
                    prior["n"]?.jsonPrimitive?.intOrNull != event.n ||
                    prior["added"]?.jsonPrimitive?.intOrNull != event.added ||
                    prior["note"]?.jsonPrimitive?.contentOrNull.orEmpty() != event.note) return null
            } else if (rpc("draw_event_append", buildJsonObject {
                put("p_id", proof.id); put("p_kind", event.kind); put("p_note", event.note)
                put("p_n", event.n?.let(::JsonPrimitive) ?: JsonNull)
                put("p_added", event.added?.let(::JsonPrimitive) ?: JsonNull)
            }) == null) return null
        }
        DrawReceipt(proof.id, field("at").toLongOrNull() ?: return null, field("hash"),
            field("seq_day").toIntOrNull() ?: return null, field("today_count").toIntOrNull() ?: return null)
    }
}
