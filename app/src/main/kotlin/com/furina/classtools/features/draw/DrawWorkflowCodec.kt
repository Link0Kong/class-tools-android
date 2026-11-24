package com.furina.classtools.features.draw

import com.furina.classtools.data.network.NetJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * 抽签工作流 [DrawWorkflowState] 的 JSON 编解码（供 `rememberSaveable(stateSaver=...)` 使用）。
 *
 * 与 `DrawConfigCodec`（userdata `draw` section、跨端契约）区分：
 * - 本编解码只用于**本机 SavedState**，保存完整工作流快照：`step/config/candidates/result/log`；
 * - 兼容历史/未来：读缺失字段回落默认、非法值丢弃，`decode` 只在坏 JSON/非对象时返回 null；
 * - 编码键序固定，`encode(decode(text)) == encode(...)`（幂等），便于单测与调试。
 *
 * 键名：
 * ```jsonc
 * { "v":1, "step":"config|preview|result",
 *   "config":{...DrawConfigCodec 键 + "pool":"range|roster"},
 *   "candidates":[{"n":5,"nm":"张三"}],
 *   "result":{"entries":[…],"pool":[…],"config":{…},"log":[{"removed":{…},"added":{…}|null}]} }
 * ```
 */
object DrawWorkflowCodec {

    const val VERSION: Int = 1

    /** 防御性上限（SavedState Bundle 不宜过大；正常配置 ≤999 号码）。 */
    private const val MAX_ENTRIES: Int = 2000
    private const val MAX_LOG: Int = 500

    private val STEP_WIRES: Map<String, DrawStep> = mapOf(
        "config" to DrawStep.CONFIG,
        "preview" to DrawStep.PREVIEW,
        "result" to DrawStep.RESULT,
    )

    fun encode(state: DrawWorkflowState): String =
        NetJson.encodeToString(JsonObject.serializer(), toJson(state))

    fun decode(text: String?): DrawWorkflowState? {
        if (text.isNullOrBlank()) return null
        val element = runCatching { NetJson.parseToJsonElement(text) }.getOrNull() ?: return null
        val obj = element as? JsonObject ?: return null
        return fromJson(obj)
    }

    fun toJson(state: DrawWorkflowState): JsonObject = buildJsonObject {
        put("v", JsonPrimitive(VERSION))
        put("step", JsonPrimitive(stepWire(state.step)))
        put("config", configToJson(state.config))
        if (state.candidates.isNotEmpty()) {
            put("candidates", entriesToJson(state.candidates))
        }
        state.result?.let { put("result", resultToJson(it)) }
    }

    /** 容错解码：坏/缺字段回落默认；`result` 步骤缺结果时降级为 `preview`（有候选）或 `config`。 */
    fun fromJson(obj: JsonObject): DrawWorkflowState {
        val config = configFromJson(obj["config"] as? JsonObject)
        val candidates = entriesFromJson(obj["candidates"])
        val result = resultFromJson(obj["result"] as? JsonObject)
        var step = STEP_WIRES[(obj["step"] as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase()] ?: DrawStep.CONFIG
        if (step == DrawStep.RESULT && result == null) {
            step = if (candidates.isNotEmpty()) DrawStep.PREVIEW else DrawStep.CONFIG
        }
        return DrawWorkflowState(step = step, config = config, candidates = candidates, result = result)
    }

    // ------------------------------------------------------------ config

    fun configToJson(config: DrawConfig): JsonObject {
        val base = DrawConfigCodec.toJson(config)
        val out = LinkedHashMap<String, JsonElement>(base)
        out["pool"] = JsonPrimitive(poolWire(config.poolSource))
        return JsonObject(out)
    }

    fun configFromJson(obj: JsonObject?): DrawConfig {
        if (obj == null) return DrawConfig()
        val pool = if ((obj["pool"] as? JsonPrimitive)?.contentOrNull == "roster") {
            DrawPoolSource.ROSTER
        } else {
            DrawPoolSource.RANGE
        }
        return DrawConfigCodec.fromJson(obj).copy(poolSource = pool)
    }

    private fun poolWire(source: DrawPoolSource): String = when (source) {
        DrawPoolSource.RANGE -> "range"
        DrawPoolSource.ROSTER -> "roster"
    }

    private fun stepWire(step: DrawStep): String = when (step) {
        DrawStep.CONFIG -> "config"
        DrawStep.PREVIEW -> "preview"
        DrawStep.RESULT -> "result"
    }

    // ------------------------------------------------------------ entries / result

    fun entriesToJson(entries: List<DrawEntry>): JsonElement = buildJsonArray {
        entries.take(MAX_ENTRIES).forEach { entry -> add(entryToJson(entry)) }
    }

    fun entryToJson(entry: DrawEntry): JsonObject = buildJsonObject {
        put("n", JsonPrimitive(entry.number))
        val name = entry.name
        if (!name.isNullOrBlank()) put("nm", JsonPrimitive(name))
    }

    fun entriesFromJson(element: JsonElement?): List<DrawEntry> {
        val array = element as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return array.take(MAX_ENTRIES).mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val number = (obj["n"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            val name = (obj["nm"] as? JsonPrimitive)
                ?.takeUnless { it is JsonNull }
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
            DrawEntry(number = number, name = name)
        }
    }

    fun resultToJson(result: DrawResult): JsonObject = buildJsonObject {
        result.proof?.let { put("proof", kotlinx.serialization.json.Json.encodeToJsonElement(DrawRoundProof.serializer(), it)) }
        put("entries", entriesToJson(result.entries))
        put("pool", entriesToJson(result.pool))
        put("config", configToJson(result.config))
        if (result.log.isNotEmpty()) {
            put(
                "log",
                buildJsonArray {
                    result.log.take(MAX_LOG).forEach { entry ->
                        add(
                            buildJsonObject {
                                put("removed", entryToJson(entry.removed))
                                entry.added?.let { put("added", entryToJson(it)) }
                            },
                        )
                    }
                },
            )
        }
    }

    fun resultFromJson(obj: JsonObject?): DrawResult? {
        obj ?: return null
        val entries = entriesFromJson(obj["entries"])
        if (entries.isEmpty()) return null
        val pool = entriesFromJson(obj["pool"])
        val config = configFromJson(obj["config"] as? JsonObject)
        val log = (obj["log"] as? kotlinx.serialization.json.JsonArray)
            ?.take(MAX_LOG)
            ?.mapNotNull { item ->
                val logObj = item as? JsonObject ?: return@mapNotNull null
                val removed = entryFromJsonOrNull(logObj["removed"]) ?: return@mapNotNull null
                DrawRemoveLog(removed = removed, added = entryFromJsonOrNull(logObj["added"]))
            }
            .orEmpty()
        val proof = obj["proof"]?.let { runCatching {
            kotlinx.serialization.json.Json.decodeFromJsonElement(DrawRoundProof.serializer(), it)
        }.getOrNull() }?.takeIf { it.id.matches(Regex("d[0-9a-z]{8,31}")) && it.seed.matches(Regex("[0-9a-f]{16}")) }
        return DrawResult(entries = entries, pool = pool, config = config, log = log, proof = proof)
    }

    private fun entryFromJsonOrNull(element: JsonElement?): DrawEntry? {
        val obj = element as? JsonObject ?: return null
        val number = (obj["n"] as? JsonPrimitive)?.intOrNull ?: return null
        val name = (obj["nm"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
        return DrawEntry(number = number, name = name?.takeIf { it.isNotBlank() })
    }
}
