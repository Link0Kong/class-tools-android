package com.furina.classtools.data.draw

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiError
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asBooleanOrNull
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import com.furina.classtools.features.scan.DrawProofVerifier
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** 存证参数（服务端白名单归一后的 params；只取核验需要字段）。 */
data class DrawProofParams(
    val rangeStart: Int = 0,
    val rangeEnd: Int = 0,
    val count: Int = 0,
    val allowDuplicates: Boolean = false,
    val excluded: List<Int> = emptyList(),
    val rosterN: Int = 0,
    val namesN: Int = 0,
)

data class DrawProofLabel(val number: Int, val name: String)

/** 剔除/重抽事件（核验页只展示数量与概要）。 */
data class DrawProofEvent(
    val seq: Int,
    val kind: String,
    val removed: Int? = null,
    val added: Int? = null,
    val note: String = "",
)

/** 云端抽签存证（`rpc/draw_record_get`）。 */
data class DrawProofRecord(
    val id: String,
    val atMillis: Long,
    val day: String,
    val seqDay: Int,
    val todayCount: Int,
    val isMine: Boolean,
    val operatorName: String,
    val operatorSeat: Int?,
    val params: DrawProofParams,
    val pool: List<Int>,
    val result: List<Int>,
    val labels: List<DrawProofLabel>,
    val seed: String,
    val algo: String,
    val hash: String,
    val canon: String,
    val prevHash: String,
    val eventCount: Int,
    val events: List<DrawProofEvent>,
)

/** 手机端独立核验结果：哈希自检 + draw-v1 复算。 */
data class DrawProofVerified(
    val record: DrawProofRecord,
    val hashOk: Boolean,
    val replayOk: Boolean,
)

/**
 * 抽签存证核验仓库：`rpc/draw_record_get` + 本地 SHA-256 / draw-v1 复算。
 * 只读接口；不写云端、不落盘。
 */
class DrawProofRepository(private val api: ApiClient) {

    suspend fun verify(recordId: String): ApiResult<DrawProofVerified> {
        val id = recordId.trim().lowercase()
        if (!RECORD_RE.matches(id)) {
            return ApiResult.Failure(ApiError.Config("核验编号无效"))
        }
        val body = buildJsonObject { put("p_id", id) }.toString()
        return when (val result = api.post("rpc/draw_record_get", body)) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> DrawProofParser.parse(result.value)
        }
    }

    companion object {
        val RECORD_RE = Regex("^d[0-9a-z]{8,31}$")
    }
}

/** `draw_record_get` 响应解析 + 本地核验（纯函数，单测直接喂 JSON）。 */
object DrawProofParser {

    fun parse(raw: String): ApiResult<DrawProofVerified> {
        val root = parseJsonOrNull(NetJson, raw) as? JsonObject
            ?: return ApiResult.Failure(ApiError.Parse("存证服务返回了无法解析的内容"))
        if (root.bool("ok") != true) {
            val reason = root.str("reason").ifBlank { root.str("message") }
            return ApiResult.Failure(ApiError.Config(reason.ifBlank { "存证记录不存在或身份已失效" }))
        }
        val recordObj = root["record"] as? JsonObject
            ?: return ApiResult.Failure(ApiError.Parse("存证记录缺失"))
        val paramsObj = recordObj["params"] as? JsonObject ?: JsonObject(emptyMap())
        val range = paramsObj.arr("range").ints()
        val params = DrawProofParams(
            rangeStart = range.getOrNull(0) ?: 0,
            rangeEnd = range.getOrNull(1) ?: 0,
            count = paramsObj.int("count"),
            allowDuplicates = paramsObj.bool("dup"),
            excluded = paramsObj.arr("excl").ints(),
            rosterN = paramsObj.int("rosterN"),
            namesN = paramsObj.int("namesN"),
        )
        val labels = recordObj.arr("labels").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val number = obj.int("n")
            if (number <= 0) null else DrawProofLabel(number, obj.str("nm"))
        }
        val events = recordObj.arr("events").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            DrawProofEvent(
                seq = obj.int("seq"),
                kind = obj.str("kind"),
                removed = obj["n"].elementInt(),
                added = obj["added"].elementInt(),
                note = obj.str("note"),
            )
        }
        val record = DrawProofRecord(
            id = recordObj.str("id"),
            atMillis = recordObj.long("at"),
            day = recordObj.str("day"),
            seqDay = recordObj.int("seq_day"),
            todayCount = recordObj.int("today_count"),
            isMine = recordObj.bool("is_mine"),
            operatorName = recordObj.str("op_name"),
            operatorSeat = recordObj["op_seat"].elementInt(),
            params = params,
            pool = recordObj.arr("pool").ints(),
            result = recordObj.arr("result").ints(),
            labels = labels,
            seed = recordObj.str("seed"),
            algo = recordObj.str("algo"),
            hash = recordObj.str("hash"),
            canon = recordObj.str("canon"),
            prevHash = recordObj.str("prev_hash"),
            eventCount = recordObj.int("event_count"),
            events = events,
        )
        if (record.id.isBlank() || !DrawProofRepository.RECORD_RE.matches(record.id)) {
            return ApiResult.Failure(ApiError.Parse("存证编号格式异常"))
        }
        return ApiResult.Success(
            DrawProofVerified(
                record = record,
                hashOk = DrawProofVerifier.hashMatches(record.canon, record.hash),
                replayOk = DrawProofVerifier.replayMatches(
                    pool = record.pool,
                    count = params.count,
                    allowDuplicates = params.allowDuplicates,
                    seed = record.seed,
                    result = record.result,
                ),
            ),
        )
    }

    private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.asStringOrNull().orEmpty()

    private fun JsonObject.int(key: String): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: 0

    private fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.asBooleanOrNull() ?: false

    private fun JsonObject.arr(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())

    private fun JsonArray.ints(): List<Int> = mapNotNull { (it as? JsonPrimitive)?.intOrNull }

    private fun JsonElement?.elementInt(): Int? = (this as? JsonPrimitive)?.intOrNull
}
