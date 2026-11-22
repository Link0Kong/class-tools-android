package com.furina.classtools.features.draw

import com.furina.classtools.data.network.NetJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 「最近一次抽签结果」的本机快照（不进入 userdata v6，不跨端同步）。
 *
 * 对齐 `web/group/CONTRACT.md` §3：`{at, result, pool, cfg:{range,count,dup,sort}}`；
 * 字段名安卓可自定，这里用 `at/result/pool/cfg`（序号数组，不含姓名）。
 * 分组页「最近抽签」只依赖本机存储，跨进程重启仍然可用；`DrawSession` 内存传递仅作兜底。
 */
data class DrawLastResultConfig(
    val start: Int,
    val end: Int,
    val count: Int,
    val allowDuplicates: Boolean,
    val sort: DrawSortMode,
) {
    companion object {
        fun from(config: DrawConfig): DrawLastResultConfig = DrawLastResultConfig(
            start = config.start,
            end = config.end,
            count = config.count,
            allowDuplicates = config.allowDuplicates,
            sort = config.sort,
        )
    }
}

/** 一次成功抽签的当前结果（含参数快照与时间）。 */
data class DrawLastResult(
    /** 抽签成功时间（epoch millis）。 */
    val atMillis: Long,
    /** 结果序号（保持抽签结果展示顺序，供分组按原顺序使用）。 */
    val result: List<Int>,
    /** 本次抽签的候选池序号（用于「最近抽签」说明；缺失时为空）。 */
    val pool: List<Int> = emptyList(),
    /** 参数快照（范围/个数/重复/排序）；缺失时兼容为 null。 */
    val config: DrawLastResultConfig? = null,
) {
    val isEmpty: Boolean get() = result.isEmpty()

    companion object {
        /** 从一次成功的 [DrawResult] 生成快照；[nowMillis] 可注入便于单测。 */
        fun from(result: DrawResult, nowMillis: Long = System.currentTimeMillis()): DrawLastResult =
            DrawLastResult(
                atMillis = nowMillis,
                result = result.entries.map { it.number }.filter { it in MIN_NUMBER..MAX_NUMBER },
                pool = result.pool.map { it.number }.filter { it in MIN_NUMBER..MAX_NUMBER },
                config = DrawLastResultConfig.from(result.config),
            )

        const val MIN_NUMBER: Int = 1
        const val MAX_NUMBER: Int = DrawConfig.MAX_NUMBER
    }
}

/**
 * JSON 编解码（纯 Kotlin，可单测）。
 *
 * 线格式（版本 1）：
 * ```jsonc
 * { "v":1, "at":1794016800000, "result":[7,23], "pool":[1,...,30],
 *   "cfg":{"start":1,"end":30,"count":2,"dup":false,"sort":"draw"} }
 * ```
 * 容错：坏 JSON / 非对象 / 空结果 → null；未知字段忽略；序号只保留 1..999。
 */
object DrawLastResultCodec {

    const val VERSION: Int = 1
    private const val MAX_NUMBERS: Int = 2000

    fun encode(value: DrawLastResult): String =
        NetJson.encodeToString(JsonObject.serializer(), toJson(value))

    fun decode(text: String?): DrawLastResult? {
        if (text.isNullOrBlank()) return null
        val element = runCatching { NetJson.parseToJsonElement(text) }.getOrNull() ?: return null
        val obj = element as? JsonObject ?: return null
        return fromJson(obj)
    }

    fun toJson(value: DrawLastResult): JsonObject = buildJsonObject {
        put("v", JsonPrimitive(VERSION))
        put("at", JsonPrimitive(value.atMillis))
        put("result", numbersToJson(value.result))
        if (value.pool.isNotEmpty()) put("pool", numbersToJson(value.pool))
        value.config?.let { config ->
            put(
                "cfg",
                buildJsonObject {
                    put("start", JsonPrimitive(config.start))
                    put("end", JsonPrimitive(config.end))
                    put("count", JsonPrimitive(config.count))
                    put("dup", JsonPrimitive(config.allowDuplicates))
                    put("sort", JsonPrimitive(config.sort.wire))
                },
            )
        }
    }

    fun fromJson(obj: JsonObject): DrawLastResult? {
        val result = numbersFromJson(obj["result"])
        if (result.isEmpty()) return null
        return DrawLastResult(
            atMillis = (obj["at"] as? JsonPrimitive)?.longOrNull ?: 0L,
            result = result,
            pool = numbersFromJson(obj["pool"]),
            config = configFromJson(obj["cfg"] as? JsonObject),
        )
    }

    private fun configFromJson(obj: JsonObject?): DrawLastResultConfig? {
        obj ?: return null
        val start = (obj["start"] as? JsonPrimitive)?.intOrNull ?: return null
        val end = (obj["end"] as? JsonPrimitive)?.intOrNull ?: return null
        val count = (obj["count"] as? JsonPrimitive)?.intOrNull ?: return null
        val dup = (obj["dup"] as? JsonPrimitive)?.contentOrNull?.equals("true", ignoreCase = true) == true
        val sort = (obj["sort"] as? JsonPrimitive)?.contentOrNull?.let(DrawSortMode::fromWire)
            ?: DrawSortMode.DRAW_ORDER
        return DrawLastResultConfig(
            start = start,
            end = end,
            count = count,
            allowDuplicates = dup,
            sort = sort,
        )
    }

    private fun numbersToJson(numbers: List<Int>): JsonArray = buildJsonArray {
        numbers.take(MAX_NUMBERS).forEach { number -> add(JsonPrimitive(number)) }
    }

    private fun numbersFromJson(element: JsonElement?): List<Int> =
        (element as? JsonArray)
            ?.take(MAX_NUMBERS)
            ?.mapNotNull { item -> (item as? JsonPrimitive)?.intOrNull }
            ?.filter { it in DrawLastResult.MIN_NUMBER..DrawLastResult.MAX_NUMBER }
            ?.distinct()
            .orEmpty()
}

/** 抽签成功后的「最近一次结果」读写接口（生产实现是 SharedPreferences；测试用内存/键值实现）。 */
interface DrawLastResultStore {
    fun load(): DrawLastResult?

    fun save(value: DrawLastResult)
}

/**
 * 键值存储实现（不依赖 Android，便于单测）：生产端包一层 SharedPreferences，
 * 测试端共享同一个 `MutableMap` 即可模拟「进程重启后再次读取」。
 */
class KeyValueDrawLastResultStore(
    private val key: String,
    private val read: (String) -> String?,
    private val write: (String, String) -> Unit,
) : DrawLastResultStore {

    override fun load(): DrawLastResult? = DrawLastResultCodec.decode(read(key))

    override fun save(value: DrawLastResult) {
        write(key, DrawLastResultCodec.encode(value))
    }
}

/** 仅用于预览/测试的内存实现。 */
class InMemoryDrawLastResultStore(initial: DrawLastResult? = null) : DrawLastResultStore {
    var stored: DrawLastResult? = initial
        private set

    override fun load(): DrawLastResult? = stored

    override fun save(value: DrawLastResult) {
        stored = value
    }
}
