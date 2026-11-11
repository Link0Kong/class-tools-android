package com.furina.classtools.features.group

import com.furina.classtools.data.network.asIntOrNull
import com.furina.classtools.data.network.asObjectOrNull
import com.furina.classtools.data.network.asStringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * `userdata.group` section 编解码（契约 docs/api/USERDATA_V6.md §1.2）：
 *
 * ```json
 * { "source": "prev", "mode": "groups", "n": 4, "shuffle": false, "sel": [1,3,5] }
 * ```
 *
 * 规则：
 * - `source` 兼容小程序本机词汇（`pick`/`paste` → `names`）；`mode` 兼容 `per` → `pergroup`；
 * - `sel` 只保留 1..99 的整数座号、去重升序（宽松归一，不因脏数据崩溃）；
 * - 未知/缺省字段用默认值（GroupConfig 默认）。
 */
object GroupConfigCodec {

    fun fromJson(element: JsonElement?): GroupConfig {
        val obj = element.asObjectOrNull() ?: return GroupConfig()
        val sourceWire = obj["source"].asStringOrNull()?.trim().orEmpty()
        val source = when (sourceWire) {
            "names", "pick", "paste", "roster" -> GroupSource.NAMES
            else -> GroupSource.PREV
        }
        val modeWire = obj["mode"].asStringOrNull()?.trim().orEmpty()
        val mode = when (modeWire) {
            "pergroup", "per", "size" -> GroupMode.PER_GROUP
            else -> GroupMode.GROUPS
        }
        val n = obj["n"].asIntOrNull() ?: GroupConfig.DEFAULT_N
        val shuffle = (obj["shuffle"] as? JsonPrimitive)?.content?.equals("true", ignoreCase = true) == true
        val seats = (obj["sel"] as? JsonArray)
            ?.mapNotNull { item -> (item as? JsonPrimitive)?.content?.toIntOrNull() }
            ?.filter { it in 1..99 }
            ?.distinct()
            ?.sorted()
            .orEmpty()
        return GroupConfig(
            source = source,
            mode = mode,
            n = n,
            shuffle = shuffle,
            selectedSeats = seats,
        )
    }

    /** 输出固定字段顺序（source/mode/n/shuffle/sel），与网页 localView 写入一致。 */
    fun toJson(config: GroupConfig): JsonObject = buildJsonObject {
        put("source", JsonPrimitive(config.source.wire))
        put("mode", JsonPrimitive(config.mode.wire))
        put("n", JsonPrimitive(config.n))
        put("shuffle", JsonPrimitive(config.shuffle))
        put(
            "sel",
            buildJsonArray {
                config.selectedSeats.distinct().sorted().forEach { add(JsonPrimitive(it)) }
            },
        )
    }

    /** 默认空 section（与 UserdataV6.defaultSection("group") 等价，便于单测/对比）。 */
    fun empty(): JsonObject = toJson(GroupConfig())

    fun isDefault(element: JsonElement?): Boolean {
        val config = fromJson(element)
        return config == GroupConfig()
    }

    @Suppress("unused")
    private fun isNull(element: JsonElement?): Boolean = element == null || element is JsonNull
}
