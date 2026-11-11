package com.furina.classtools.data.timetable

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiError
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 课表登录 RPC 仓库（契约 §1：enqueue/status 是客户端唯一入口；`login_jobs` 表客户端无直接权限）。
 *
 * 安全：密码只出现在 [enqueue] 的 POST body 里，不落盘、不打印、不进入任何返回值；
 * 调用方（UI）在提交后立即清空输入框状态。
 */
interface TimetableLoginRepository {
    /** 入队一次教务登录（密码仅本次调用内联）。 */
    suspend fun enqueue(password: String): TtLoginEnqueueResult

    /** 查询登录任务状态。 */
    suspend fun status(jobId: String): TtLoginStatusResult
}

class DefaultTimetableLoginRepository(
    private val api: ApiClient,
    private val json: Json = NetJson,
) : TimetableLoginRepository {

    override suspend fun enqueue(password: String): TtLoginEnqueueResult {
        if (password.isBlank()) {
            return TtLoginEnqueueResult.Failure(TtLoginFailureReason.PWD)
        }
        return when (val result = api.post(ENQUEUE_PATH, enqueueBody(password))) {
            is ApiResult.Failure -> TtLoginEnqueueResult.Failure(mapError(result.error))
            is ApiResult.Success -> TtLoginParser.parseEnqueue(result.value, json)
        }
    }

    override suspend fun status(jobId: String): TtLoginStatusResult {
        if (jobId.isBlank()) {
            return TtLoginStatusResult.Failure(TtLoginFailureReason.PARSE)
        }
        return when (val result = api.post(STATUS_PATH, statusBody(jobId))) {
            is ApiResult.Failure -> TtLoginStatusResult.Failure(mapError(result.error))
            is ApiResult.Success -> TtLoginParser.parseStatus(result.value, json)
        }
    }

    companion object {
        const val ENQUEUE_PATH: String = "rpc/tt_login_enqueue"
        const val STATUS_PATH: String = "rpc/tt_login_status"
        const val PLATFORM_ANDROID: String = "android"

        /** 学号格式（与网页/小程序一致：10 位数字；仅用于前端校验，云端 sid 由 token 决定）。 */
        val STUDENT_ID_REGEX: Regex = Regex("^\\d{10}$")

        /** enqueue 请求体（契约固定字段：`p_pwd` + `p_platform:'android'`）。 */
        fun enqueueBody(password: String): String = buildJsonObject {
            put("p_pwd", JsonPrimitive(password))
            put("p_platform", JsonPrimitive(PLATFORM_ANDROID))
        }.toString()

        fun statusBody(jobId: String): String = buildJsonObject {
            put("p_id", JsonPrimitive(jobId))
        }.toString()

        /** RPC 失败 → 登录语义（401/403 → 身份失效；网络/5xx → 可重试）。 */
        fun mapError(error: ApiError): TtLoginFailureReason = when (error) {
            is ApiError.TokenInvalid -> TtLoginFailureReason.AUTH
            is ApiError.Network -> TtLoginFailureReason.NETWORK
            is ApiError.Parse -> TtLoginFailureReason.PARSE
            is ApiError.Config -> TtLoginFailureReason.AUTH
            is ApiError.Http -> if (error.status in 400..499) {
                TtLoginFailureReason.AUTH
            } else {
                TtLoginFailureReason.NETWORK
            }
        }
    }
}

/** 轮询节奏（契约：1.5s，≤120s）。 */
data class TtLoginPollConfig(
    val intervalMs: Long = 1_500L,
    val timeoutMs: Long = 120_000L,
) {
    init {
        require(intervalMs > 0) { "intervalMs must be > 0" }
        require(timeoutMs > 0) { "timeoutMs must be > 0" }
    }
}

sealed interface TtLoginPollOutcome {
    data class Done(val snapshot: TtSnapshot) : TtLoginPollOutcome

    data class Failed(
        val reason: TtLoginFailureReason,
        val message: String = reason.message,
    ) : TtLoginPollOutcome

    /** 超过 [TtLoginPollConfig.timeoutMs] 仍未拿到终态（停止轮询，不再发请求）。 */
    data object Timeout : TtLoginPollOutcome
}

/**
 * 登录轮询状态机（纯逻辑；`now` / `sleep` / `fetch` 全部可注入 → JVM 单测覆盖超时/停止）。
 *
 * 规则：
 * - 立即拉一次，之后每 [TtLoginPollConfig.intervalMs] 拉一次；
 * - `done`/`failed`/`expired` → 立即返回（不再 sleep/请求）；
 * - 网络/解析类失败可继续轮询；`auth`/`forbidden`/`not_found` 直接失败；
 * - 累计耗时达到 [TtLoginPollConfig.timeoutMs] → [TtLoginPollOutcome.Timeout]，**停止轮询**。
 */
object TtLoginPolling {

    suspend fun poll(
        jobId: String,
        fetch: suspend (String) -> TtLoginStatusResult,
        config: TtLoginPollConfig = TtLoginPollConfig(),
        now: () -> Long = System::currentTimeMillis,
        sleep: suspend (Long) -> Unit = { delay(it) },
    ): TtLoginPollOutcome {
        val startedAt = now()
        var polls = 0
        while (true) {
            val elapsed = now() - startedAt
            if (elapsed >= config.timeoutMs) return TtLoginPollOutcome.Timeout
            polls += 1
            val result = fetch(jobId)
            when (result) {
                is TtLoginStatusResult.Done -> return TtLoginPollOutcome.Done(result.snapshot)
                is TtLoginStatusResult.Failed -> return TtLoginPollOutcome.Failed(result.reason, result.message)
                is TtLoginStatusResult.Failure -> {
                    if (!result.reason.retriable) {
                        return TtLoginPollOutcome.Failed(result.reason, result.message)
                    }
                }
                is TtLoginStatusResult.Running -> Unit
            }
            val remaining = config.timeoutMs - (now() - startedAt)
            if (remaining <= 0) return TtLoginPollOutcome.Timeout
            sleep(minOf(config.intervalMs, remaining).coerceAtLeast(1L))
        }
    }
}
