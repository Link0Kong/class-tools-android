package com.furina.classtools.data.diag

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiError
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.TimedResponse
import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/** 诊断数据加载结果（与 [ApiResult] 同语义：失败不抛异常）。 */
sealed interface DiagResult<out T> {
    data class Success<T>(val value: T) : DiagResult<T>
    data class Failure(val error: ApiError) : DiagResult<Nothing>
}

fun <T> DiagResult<T>.valueOrNull(): T? = (this as? DiagResult.Success)?.value

fun DiagResult<*>.displayMessage(): String = when (this) {
    is DiagResult.Failure -> error.displayMessage
    is DiagResult.Success -> ""
}

/**
 * 诊断 v2 数据仓库（契约 §1/§2 读接口 + client_diag 上报）。
 *
 * 所有请求走既有 [ApiClient]：`Authorization: Bearer <publishable>` + `X-Class-Token`，
 * 不新增外部域名。错误统一 [ApiError]（401/403 → TokenInvalid，由装配层清会话）。
 */
interface DiagRepository {
    /** `service_health?select=...&order=id.asc`（全班有效 token 可读）。 */
    suspend fun loadServiceHealth(): DiagResult<List<ServiceHealthRow>>

    /** 最近 20 条未忽略的诊断结论。 */
    suspend fun loadAnalyses(): DiagResult<List<DiagAnalysis>>

    /** `repair_tasks?select=*&order=created_at.desc&limit=20`。 */
    suspend fun loadRepairs(): DiagResult<List<RepairTask>>

    /** `POST rpc/diag_agent_info {}`。 */
    suspend fun agentInfo(): DiagResult<DiagAgentInfo>

    /** 计时读一个轻量 GET（网关延迟 + 服务器 Date 偏差）。 */
    suspend fun measureGateway(): DiagResult<GatewayProbe>

    /** `POST client_diag`（单行数组；sid 由 RLS 校验本人）。 */
    suspend fun reportClientDiag(row: JsonObject): ApiResult<Unit>

    /** `client_diag?select=*&order=ts.desc&limit=20&sid=eq.<自己>`；未登录 → Failure(Config)。 */
    suspend fun loadMyReports(): DiagResult<List<ClientDiagRow>>
}

class DefaultDiagRepository(
    private val api: ApiClient,
    private val currentSid: () -> String?,
    private val json: Json = NetJson,
) : DiagRepository {

    override suspend fun loadServiceHealth(): DiagResult<List<ServiceHealthRow>> =
        when (val result = api.get(SERVICE_HEALTH_PATH)) {
            is ApiResult.Failure -> DiagResult.Failure(result.error)
            is ApiResult.Success -> DiagResult.Success(DiagParser.parseServiceHealth(result.value, json))
        }

    override suspend fun loadAnalyses(): DiagResult<List<DiagAnalysis>> =
        when (val result = api.get(ANALYSIS_PATH)) {
            is ApiResult.Failure -> DiagResult.Failure(result.error)
            is ApiResult.Success -> DiagResult.Success(DiagParser.parseAnalyses(result.value, json))
        }

    override suspend fun loadRepairs(): DiagResult<List<RepairTask>> =
        when (val result = api.get(REPAIRS_PATH)) {
            is ApiResult.Failure -> DiagResult.Failure(result.error)
            is ApiResult.Success -> DiagResult.Success(DiagParser.parseRepairTasks(result.value, json))
        }

    override suspend fun agentInfo(): DiagResult<DiagAgentInfo> =
        when (val result = api.post(AGENT_INFO_PATH, "{}")) {
            is ApiResult.Failure -> DiagResult.Failure(result.error)
            is ApiResult.Success -> {
                val info = DiagParser.parseAgentInfo(result.value, json)
                if (info == null) {
                    DiagResult.Failure(ApiError.Parse("agent 状态响应结构异常"))
                } else {
                    DiagResult.Success(info)
                }
            }
        }

    override suspend fun measureGateway(): DiagResult<GatewayProbe> =
        when (val result = api.getTimed(GATEWAY_PROBE_PATH)) {
            is ApiResult.Failure -> DiagResult.Failure(result.error)
            is ApiResult.Success -> DiagResult.Success(timedToProbe(result.value))
        }

    override suspend fun reportClientDiag(row: JsonObject): ApiResult<Unit> {
        val body = buildJsonArray { add(row) }.toString()
        return when (val result = api.post(CLIENT_DIAG_PATH, body)) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(Unit)
        }
    }

    override suspend fun loadMyReports(): DiagResult<List<ClientDiagRow>> {
        val sid = currentSid()?.trim().orEmpty()
        if (sid.isEmpty()) return DiagResult.Failure(ApiError.Config("未登录"))
        val path = "client_diag?select=*&order=ts.desc&limit=20&sid=eq." +
            URLEncoder.encode(sid, "UTF-8")
        return when (val result = api.get(path)) {
            is ApiResult.Failure -> DiagResult.Failure(result.error)
            is ApiResult.Success -> DiagResult.Success(DiagParser.parseClientDiagRows(result.value, json))
        }
    }

    companion object {
        const val SERVICE_HEALTH_PATH: String =
            "service_health?select=id,name,kind,status,detail,meta,checked_at&order=id.asc"
        const val ANALYSIS_PATH: String = "diag_analysis?select=*&status=neq.ignored&order=ts.desc&limit=20"
        const val REPAIRS_PATH: String = "repair_tasks?select=*&order=created_at.desc&limit=20"
        const val AGENT_INFO_PATH: String = "rpc/diag_agent_info"
        const val CLIENT_DIAG_PATH: String = "client_diag"
        const val GATEWAY_PROBE_PATH: String = "service_health?select=id&limit=1"

        /** 计时结果 → 探测结果；Date 头缺失仍返回延迟（时钟偏差显示为「不可用」）。 */
        fun timedToProbe(response: TimedResponse): GatewayProbe =
            GatewayProbe(latencyMs = response.latencyMs, serverEpochMs = response.serverEpochMs)

        /** 上报行 → POST 数组 body（测试可直接断言契约结构）。 */
        fun reportBody(row: JsonObject): String = buildJsonArray { add(row) }.toString()

        /** 空服务健康时的占位（避免 UI 分不清「未加载」与「云端 0 行」）。 */
        fun emptyGroups(): List<ServiceHealthGroup> = emptyList()
    }
}
