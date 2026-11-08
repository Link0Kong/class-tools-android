package com.furina.classtools.data.timetable

import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asBooleanOrNull
import com.furina.classtools.data.network.asObjectOrNull
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 课表登录（云收单 + 本机代理）RPC 契约：`_diag_task/CONTRACT.md` §1/§4。
 *
 * - `POST rpc/tt_login_enqueue {p_pwd, p_platform:'android'}` → `{ok:true,job_id[,reused]}`
 *   或 `{ok:false,reason:"auth|pwd|agent_offline|rate|encrypt"}`；
 * - `POST rpc/tt_login_status {p_id}` → `{ok:true,status,finished,error,result}`；
 *   `status ∈ pending|claimed|done|failed|expired`；`result={c,t,meta}`（done 时）。
 *
 * **密码只在请求体里出现一次**：模型/结果/日志均不保留密码字段（[TtLoginFailureReason] 文案也不含密码）。
 */

/** 登录失败原因（enqueue reason / status failed error 映射为可直接展示的中文）。 */
enum class TtLoginFailureReason(val wire: String, val message: String) {
    AUTH("auth", "身份已失效，请重新验证"),
    PWD("pwd", "教务密码不正确或为空"),
    AGENT_OFFLINE("agent_offline", "本机登录代理不在线，请稍后再试"),
    RATE("rate", "尝试次数过多（每学号每小时 3 次），请稍后再试"),
    ENCRYPT("encrypt", "密码加密失败，请稍后重试"),
    NOT_FOUND("not_found", "登录任务不存在或已过期"),
    FORBIDDEN("forbidden", "只能查询本人的登录任务"),
    EXPIRED("expired", "登录任务已过期，请重试"),
    FAILED("failed", "教务登录失败，请检查学号与密码"),
    TIMEOUT("timeout", "登录超时，请稍后重试"),
    NETWORK("network", "网络不可用，请稍后重试"),
    PARSE("parse", "登录服务返回异常"),
    UNKNOWN("unknown", "登录失败，请稍后重试"),
    ;

    /** 该类失败是否值得继续轮询（网络抖动/解析失败可重试；身份/密码/限频直接终止）。 */
    val retriable: Boolean
        get() = this == NETWORK || this == PARSE || this == UNKNOWN

    companion object {
        fun fromWire(raw: String?): TtLoginFailureReason {
            val wire = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.wire == wire } ?: UNKNOWN
        }
    }
}

/** enqueue 结果。 */
sealed interface TtLoginEnqueueResult {
    data class Success(val jobId: String, val reused: Boolean = false) : TtLoginEnqueueResult

    data class Failure(
        val reason: TtLoginFailureReason,
        val message: String = reason.message,
    ) : TtLoginEnqueueResult
}

/** tt_login_status 一次轮询的结果。 */
sealed interface TtLoginStatusResult {
    /** pending / claimed / running（任务进行中，继续轮询）。 */
    data class Running(val status: String) : TtLoginStatusResult

    /** done：拿到个人课表快照。 */
    data class Done(val snapshot: TtSnapshot) : TtLoginStatusResult

    /** failed / expired：本机代理返回失败原因或任务过期。 */
    data class Failed(
        val reason: TtLoginFailureReason,
        val message: String = reason.message,
    ) : TtLoginStatusResult

    /** 网络/HTTP/结构错误（[reason] 为 NETWORK/PARSE/AUTH 等；NETWORK/PARSE 可继续轮询）。 */
    data class Failure(
        val reason: TtLoginFailureReason,
        val message: String = reason.message,
    ) : TtLoginStatusResult
}

object TtLoginParser {

    /** 解析 enqueue 响应（对象或单元素数组）。 */
    fun parseEnqueue(raw: String?, json: Json = NetJson): TtLoginEnqueueResult {
        val body = responseObject(raw, json)
            ?: return TtLoginEnqueueResult.Failure(TtLoginFailureReason.PARSE)
        val ok = body["ok"].asBooleanOrNull()
        val jobId = body["job_id"].asStringOrNull()?.trim().orEmpty()
        if (ok == false) {
            val reason = TtLoginFailureReason.fromWire(body["reason"].asStringOrNull())
            return TtLoginEnqueueResult.Failure(reason)
        }
        if (jobId.isEmpty()) {
            return TtLoginEnqueueResult.Failure(TtLoginFailureReason.PARSE)
        }
        return TtLoginEnqueueResult.Success(
            jobId = jobId,
            reused = body["reused"].asBooleanOrNull() ?: false,
        )
    }

    /** 解析 status 响应（对象或单元素数组）。 */
    fun parseStatus(raw: String?, json: Json = NetJson): TtLoginStatusResult {
        val body = responseObject(raw, json)
            ?: return TtLoginStatusResult.Failure(TtLoginFailureReason.PARSE)
        val ok = body["ok"].asBooleanOrNull()
        if (ok == false) {
            val reason = TtLoginFailureReason.fromWire(body["reason"].asStringOrNull())
            return TtLoginStatusResult.Failure(reason)
        }
        val status = body["status"].asStringOrNull()?.trim()?.lowercase().orEmpty()
        return when (status) {
            "pending", "claimed", "running" -> TtLoginStatusResult.Running(status.ifBlank { "pending" })
            "done" -> {
                val resultElement = body["result"]
                val snapshot = TtLocal.parse(resultElement, json)
                if (snapshot == null) {
                    TtLoginStatusResult.Failure(TtLoginFailureReason.PARSE, "登录成功但课表数据异常")
                } else {
                    TtLoginStatusResult.Done(snapshot)
                }
            }
            "failed" -> {
                val error = body["error"].asStringOrNull()?.trim().orEmpty()
                val reason = mapFailedError(error)
                TtLoginStatusResult.Failed(
                    reason = reason,
                    message = if (reason == TtLoginFailureReason.FAILED && error.isNotBlank()) {
                        sanitizeErrorText(error)
                    } else {
                        reason.message
                    },
                )
            }
            "expired" -> TtLoginStatusResult.Failed(TtLoginFailureReason.EXPIRED)
            else -> TtLoginStatusResult.Running(status.ifBlank { "pending" })
        }
    }

    /** 本机代理回写的 error 文本（pwd/auth/failed/... 或自由文本）→ 枚举。 */
    fun mapFailedError(error: String?): TtLoginFailureReason {
        val text = error?.trim().orEmpty()
        if (text.isEmpty()) return TtLoginFailureReason.FAILED
        val matched = TtLoginFailureReason.entries.firstOrNull { it.wire == text.lowercase() }
        return matched ?: TtLoginFailureReason.FAILED
    }

    /** 失败文案脱敏（不展示内网地址/路径/凭据等；长度截断）。 */
    fun sanitizeErrorText(text: String): String {
        val sanitized = com.furina.classtools.data.diag.DiagSanitizer.sanitizeText(text, 120)
        return sanitized.ifBlank { TtLoginFailureReason.FAILED.message }
    }

    private fun responseObject(raw: String?, json: Json): JsonObject? {
        val element = parseJsonOrNull(json, raw?.trim()) ?: return null
        return when (element) {
            is JsonObject -> element
            is JsonArray -> element.firstOrNull().asObjectOrNull()
            is JsonPrimitive -> {
                val text = element.content
                if (text.isBlank()) null else parseJsonOrNull(json, text)?.asObjectOrNull()
            }
            else -> null
        }
    }
}
