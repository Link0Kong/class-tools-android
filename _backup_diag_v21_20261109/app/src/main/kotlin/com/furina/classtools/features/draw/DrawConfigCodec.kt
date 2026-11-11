package com.furina.classtools.features.draw

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * `userdata` v6 `draw` section 编解码。
 *
 * 线格式（与网页 / 小程序一致，键序固定）：
 * ```json
 * {"start":1,"end":70,"count":10,"dup":false,"excl":"","sort":"draw"}
 * ```
 * 读取时对脏数据容错：数值越界 clamp 到 `1..999`、`count` clamp 到 `1..999`，缺失字段回落默认值。
 */
object DrawConfigCodec {

    /** 写入 userdata 的 `draw` section（键序与契约一致）。 */
    fun toJson(config: DrawConfig): JsonObject = buildJsonObject {
        put("start", JsonPrimitive(config.start))
        put("end", JsonPrimitive(config.end))
        put("count", JsonPrimitive(config.count))
        put("dup", JsonPrimitive(config.allowDuplicates))
        put("excl", JsonPrimitive(config.excludedText))
        put("sort", JsonPrimitive(config.sort.wire))
    }

    /** 从 `userdata.draw`（可能为 null/脏数据）解析；失败一律回落默认值。 */
    fun fromJson(element: JsonElement?, fallback: DrawConfig = DrawConfig()): DrawConfig {
        val obj = element as? JsonObject ?: return fallback
        return DrawConfig(
            start = intField(obj, "start", fallback.start, DrawConfig.MIN_NUMBER, DrawConfig.MAX_NUMBER),
            end = intField(obj, "end", fallback.end, DrawConfig.MIN_NUMBER, DrawConfig.MAX_NUMBER),
            count = intField(obj, "count", fallback.count, DrawConfig.MIN_COUNT, DrawConfig.MAX_COUNT),
            allowDuplicates = booleanField(obj, "dup", fallback.allowDuplicates),
            excludedText = stringField(obj, "excl", fallback.excludedText),
            sort = DrawSortMode.fromWire(stringField(obj, "sort", fallback.sort.wire)),
            // 候选池来源不属于跨端契约（仅本机 UI 选择），读取时恒为 RANGE。
            poolSource = DrawPoolSource.RANGE,
        )
    }

    /** 空 section 默认值（新建账号/无云端数据）。 */
    fun defaults(): DrawConfig = DrawConfig()

    private fun intField(obj: JsonObject, key: String, fallback: Int, min: Int, max: Int): Int {
        val raw = (obj[key] as? JsonPrimitive)?.takeUnless { it is JsonNull } ?: return fallback
        val value = raw.intOrNull ?: raw.contentOrNull?.trim()?.toDoubleOrNull()?.toInt() ?: return fallback
        return value.coerceIn(min, max)
    }

    private fun booleanField(obj: JsonObject, key: String, fallback: Boolean): Boolean {
        val raw = obj[key] as? JsonPrimitive ?: return fallback
        return raw.booleanOrNull ?: fallback
    }

    private fun stringField(obj: JsonObject, key: String, fallback: String): String {
        val raw = obj[key] as? JsonPrimitive ?: return fallback
        if (raw is JsonNull || !raw.isString) return fallback
        return raw.contentOrNull ?: fallback
    }
}
