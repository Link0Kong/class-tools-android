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
 * 查询与网页/小程序/Windows 同口径：
 * `visits?select=slot,sid,name,ts,client&slot=gte.<from>&order=ts.desc&limit=5000`
 * 页面按最近登录排序、显示分钟时间和登录端；测试账号/匿名行由 [VisitStats] 过滤。
 */
class VisitsRepository(private val api: ApiClient) {

    suspend fun pull(fromSlot: String): VisitsResult {
        val from = fromSlot.trim().ifEmpty { "1970-01-01-00" }
        val query = "visits?select=slot,sid,name,ts,client&slot=gte." +
            URLEncoder.encode(from, "UTF-8") +
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
}
