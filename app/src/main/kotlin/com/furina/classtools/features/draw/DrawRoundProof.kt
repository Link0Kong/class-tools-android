package com.furina.classtools.features.draw

import com.furina.classtools.features.scan.DrawProofVerifier
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class RoundEvent(val kind: String, val n: Int? = null, val added: Int? = null, val note: String = "")

@Serializable
data class DrawRoundProof(val id: String, val seed: String, val sort: String, val events: List<RoundEvent> = emptyList()) {
    companion object {
        fun start(sort: DrawSortMode): DrawRoundProof {
            val uuid = UUID.randomUUID().toString().replace("-", "")
            return DrawRoundProof("d" + uuid.take(28), UUID.randomUUID().toString().replace("-", "").take(16), sort.wire)
        }
    }

    fun random() = DrawProofVerifier.random(seed)

    fun original(pool: List<Int>, config: DrawConfig) =
        DrawProofVerifier.replay(pool, config.count, config.allowDuplicates, seed)

    fun after(previous: DrawResult, next: DrawResult): DrawRoundProof {
        val additions = buildList {
            if (previous.config.sort != next.config.sort) add(RoundEvent("sort", note = next.config.sort.wire))
            next.log.forEachIndexed { index, item ->
                val prior = previous.log.getOrNull(index)
                if (prior == null) add(RoundEvent("remove", item.removed.number, item.added?.number))
                else if (item.added != null && prior.added == null) add(RoundEvent("refill", item.removed.number, item.added.number))
            }
        }
        return if (additions.isEmpty()) this else copy(events = events + additions)
    }
}

data class DrawReceipt(val id: String, val at: Long, val hash: String, val seqDay: Int, val todayCount: Int)
