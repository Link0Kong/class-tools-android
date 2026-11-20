package com.furina.classtools.data.visits

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiResult
import java.net.URLEncoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * 访问统计读取（仅管理员；服务端 RLS 还会再校验 token）。
 *
 * 查询与网页/小程序/Windows/鸿蒙同口径（契约 §2）：
 * `visits?select=slot,sid,name,ts,client,ver,dev,did&slot=gte.<from>&order=ts.desc&limit=5000`
 *
 * - `from` 固定「今天 - 6 天」的 `YYYY-MM-DD-00`，一次拉 7 天；页面切换今天/近 7 天不重新请求；
 * - `did` 只用于「访问设备数」去重，不展示；
 * - 测试账号/匿名行/范围外行由 [VisitStats] 过滤；
 * - 失败返回 [VisitsResult.Failure]（页面沿用既有失败态）。
 */
class VisitsRepository(private val api: ApiClient) {

    suspend fun pull(fromSlot: String): VisitsResult {
        val from = fromSlot.trim().ifEmpty { "1970-01-01-00" }
        val query = "visits?select=$SELECT" +
            "&slot=gte." + URLEncoder.encode(from, "UTF-8") +
            "&order=ts.desc&limit=5000"
        return when (val result = api.getJson(query)) {
            is ApiResult.Success -> parseRows(result.value)
            is ApiResult.Failure -> VisitsResult.Failure(result.error.displayMessage)
        }
    }

    private fun parseRows(root: kotlinx.serialization.json.JsonElement): VisitsResult {
        val array = root as? JsonArray ?: return VisitsResult.Success(emptyList())
        val rows = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val sid = obj.string("sid").trim()
            if (sid.isEmpty()) return@mapNotNull null
            VisitRow(
                slot = obj.string("slot"),
                sid = sid,
                name = obj.string("name"),
                ts = obj.long("ts"),
                client = obj.string("client"),
                ver = obj.string("ver"),
                dev = obj.string("dev"),
                did = obj.string("did"),
            )
        }
        return VisitsResult.Success(rows)
    }

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.long(key: String): Long {
        val primitive = this[key] as? JsonPrimitive ?: return 0
        return primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull() ?: 0
    }

    companion object {
        /** 契约 §2 统一查询字段（含新增 ver/dev/did）。 */
        const val SELECT: String = "slot,sid,name,ts,client,ver,dev,did"
    }
}
