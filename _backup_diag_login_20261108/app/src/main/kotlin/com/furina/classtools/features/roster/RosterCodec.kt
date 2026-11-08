package com.furina.classtools.features.roster

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * `userdata.roster` section 编解码（契约：`docs/api/USERDATA_V6.md` §1.2）。
 *
 * 输出键序固定：`rosterSel`、`rollSel`、`rollSt`、`rollNames`（与网页/小程序 localView 写入一致）。
 * 容错：座号只收 1..99 去重升序；状态 key 越界丢弃；状态值兼容 `in`/`ab`/`到`/`假`；坏 JSON → 默认态。
 */
object RosterCodec {

    fun fromJson(element: JsonElement?): RosterState {
        val obj = element as? JsonObject ?: return RosterState()
        val rosterSel = normalizeSeats((obj["rosterSel"] as? JsonArray).orEmptySeats())
        val rollSel = normalizeSeats((obj["rollSel"] as? JsonArray).orEmptySeats())
        val statusObj = obj["rollSt"] as? JsonObject
        val statuses = LinkedHashMap<Int, RollStatus>()
        if (statusObj != null) {
            for ((key, value) in statusObj) {
                val index = key.trim().toIntOrNull() ?: continue
                if (index !in rollSel.indices) continue
                val raw = (value as? JsonPrimitive)?.contentOrNull
                val status = RollStatus.fromWire(raw) ?: continue
                statuses[index] = status
            }
        }
        val names = (obj["rollNames"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return RosterState(
            rosterSel = rosterSel,
            rollSel = rollSel,
            rollStatus = statuses,
            rollNamesText = names,
        )
    }

    fun toJson(state: RosterState): JsonObject = buildJsonObject {
        put(
            "rosterSel",
            JsonArray(normalizeSeats(state.rosterSel).map { JsonPrimitive(it) }),
        )
        val rollSel = normalizeSeats(state.rollSel)
        put("rollSel", JsonArray(rollSel.map { JsonPrimitive(it) }))
        put(
            "rollSt",
            buildJsonObject {
                state.rollStatus.toSortedMap().forEach { (index, status) ->
                    if (index in rollSel.indices) put(index.toString(), JsonPrimitive(status.wire))
                }
            },
        )
        put("rollNames", JsonPrimitive(state.rollNamesText))
    }

    fun empty(): RosterState = RosterState()

    /** 座号归一：丢 null/非 1..99、去重、升序。 */
    fun normalizeSeats(input: Iterable<Int?>): List<Int> = input
        .mapNotNull { it }
        .filter { it in 1..99 }
        .distinct()
        .sorted()

    private fun JsonArray?.orEmptySeats(): Iterable<Int?> =
        this?.map { item -> (item as? JsonPrimitive)?.intOrNull } ?: emptyList()

    @Suppress("unused")
    private fun JsonArray?.orEmptyStrings(): List<String> =
        this?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
}
