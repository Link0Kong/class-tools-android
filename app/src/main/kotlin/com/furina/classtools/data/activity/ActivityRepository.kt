package com.furina.classtools.data.activity

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.furina.classtools.data.network.ApiRequest
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpMethod
import com.furina.classtools.data.network.HttpTransport
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.OkHttpTransport
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

sealed interface ActivityResult<out T> {
    data class Ok<T>(val value: T) : ActivityResult<T>
    data class Err(val message: String) : ActivityResult<Nothing>
}

private val Context.activityDataStore by preferencesDataStore(name = "ct_activity")

/** 学校会话只保存在应用私有目录（DataStore），不上云、不打日志。 */
class ActivityStore(private val context: Context) {

    private object Keys {
        val token = stringPreferencesKey("token")
        val userId = stringPreferencesKey("user_id")
        val classId = stringPreferencesKey("class_id")
        val savedAt = longPreferencesKey("saved_at")
    }

    val state: Flow<ActivityToken?> = context.activityDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            val token = p[Keys.token].orEmpty()
            if (token.isBlank()) {
                null
            } else {
                ActivityToken(
                    token = token,
                    userId = p[Keys.userId].orEmpty(),
                    classId = p[Keys.classId].orEmpty(),
                    savedAt = p[Keys.savedAt] ?: 0L,
                )
            }
        }

    suspend fun save(value: ActivityToken) {
        context.activityDataStore.edit { p ->
            p[Keys.token] = value.token
            p[Keys.userId] = value.userId
            p[Keys.classId] = value.classId
            p[Keys.savedAt] = value.savedAt
        }
    }

    suspend fun clear() {
        context.activityDataStore.edit { it.clear() }
    }
}

/**
 * 第二课堂（rcm.fjtcm.edu.cn）本地仓库：签名 + 列表/详情/报名/取消。
 *
 * 与网页/助手共用同一套签名（[ActivitySigner]），token 只存在本机。
 */
class ActivityRepository(
    context: Context,
    private val transport: HttpTransport = OkHttpTransport(),
    private val baseUrl: String = "https://stmapi.fjtcm.edu.cn",
) {

    private val store = ActivityStore(context)

    val saved: Flow<ActivityToken?> = store.state

    suspend fun currentToken(): ActivityToken? = store.state.first()

    suspend fun saveToken(value: ActivityToken) = store.save(value)

    suspend fun clearToken() = store.clear()

    // ---------------------------------------------------------------- 请求

    private fun headers(url: String, token: String): Map<String, String> = mapOf(
        "Authorization" to ActivitySigner.header(url, token),
        "Accept" to "application/json, text/plain, */*",
        "Origin" to "https://rcm.fjtcm.edu.cn",
        "Referer" to "https://rcm.fjtcm.edu.cn/",
        "User-Agent" to
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36",
    )

    private suspend fun call(
        method: HttpMethod,
        path: String,
        token: String,
        query: String = "",
        body: String? = null,
    ): ApiResponse {
        val url = baseUrl + path
        val full = if (query.isBlank()) url else "$url?$query"
        return transport.execute(ApiRequest(method, full, headers(url, token), body))
    }

    /**
     * 可诊断错误文案：状态码 + 服务端原文 message（不含 token/账号等敏感信息）。
     *
     * 2026-11-15 用户报的「未找到对应编号的学校信息」就是学校 API 的 message 原文
     * （HTTP 500 `{"message":"未找到对应编号的学校信息"}`），原先只显示 body 前 120 字符，
     * 既可能被 JSON 外壳占满也不够可读；现在优先取 `message/msg/error` 字段并附操作名。
     */
    private fun errFor(status: Int, body: String = "", action: String = ""): String =
        activityErrorMessage(status, body, action)

    // ---------------------------------------------------------------- 业务

    suspend fun list(rows: Int = 30): ActivityResult<List<ScActivity>> {
        val t = currentToken() ?: return ActivityResult.Err("还没有登录学校账号")
        return try {
            val path = "/organizations/activity/page/for_sign/${t.userId}/class/${t.classId}"
            val resp = call(HttpMethod.POST, path, t.token, "orderBy=0&page=1&rows=$rows", """{"type":""}""")
            if (resp.status != 200) return ActivityResult.Err(errFor(resp.status, resp.body, "活动列表"))
            val root = NetJson.parseToJsonElement(resp.body).jsonObject
            val arr = (root["list"] as? JsonArray) ?: JsonArray(emptyList())
            ActivityResult.Ok(arr.mapNotNull { (it as? JsonObject)?.toActivity() })
        } catch (e: Exception) {
            ActivityResult.Err("网络错误：${e.message ?: e.javaClass.simpleName}")
        }
    }

    suspend fun detail(activityId: String): ActivityResult<ScDetail> {
        val t = currentToken() ?: return ActivityResult.Err("还没有登录学校账号")
        return try {
            val path = "/organizations/activity/all/$activityId"
            val resp = call(HttpMethod.GET, path, t.token, "studentUserId=${t.userId}")
            if (resp.status != 200) return ActivityResult.Err(errFor(resp.status, resp.body, "活动详情"))
            ActivityResult.Ok(NetJson.parseToJsonElement(resp.body).jsonObject.toDetail())
        } catch (e: Exception) {
            ActivityResult.Err("网络错误：${e.message ?: e.javaClass.simpleName}")
        }
    }

    suspend fun sign(activityId: String, roleId: String): ActivityResult<Unit> {
        val t = currentToken() ?: return ActivityResult.Err("还没有登录学校账号")
        return try {
            val path = "/organizations/sign_up/activity/$activityId/role/$roleId/user/${t.userId}"
            val resp = call(HttpMethod.POST, path, t.token)
            if (resp.status == 200) ActivityResult.Ok(Unit)
            else ActivityResult.Err(errFor(resp.status, resp.body, "报名"))
        } catch (e: Exception) {
            ActivityResult.Err("网络错误：${e.message ?: e.javaClass.simpleName}")
        }
    }

    suspend fun cancel(activityId: String): ActivityResult<Unit> {
        val t = currentToken() ?: return ActivityResult.Err("还没有登录学校账号")
        return try {
            val path = "/organizations/sign_up/cancel/$activityId/user/${t.userId}"
            val resp = call(HttpMethod.POST, path, t.token)
            if (resp.status == 200) ActivityResult.Ok(Unit)
            else ActivityResult.Err(errFor(resp.status, resp.body, "取消报名"))
        } catch (e: Exception) {
            ActivityResult.Err("网络错误：${e.message ?: e.javaClass.simpleName}")
        }
    }
}

// ---------------------------------------------------------------- 错误文案（可单测）

/**
 * 学校 API 错误 → 可诊断文案（状态码 + 服务端原文 message + 操作名；不含敏感信息）。
 *
 * 例：`activityErrorMessage(500, """{"message":"未找到对应编号的学校信息"}""", "活动列表")`
 * → `服务器返回 500（活动列表）：未找到对应编号的学校信息 · 请重新登录学校账号（登录入口学校编号异常）`。
 */
internal fun activityErrorMessage(status: Int, body: String = "", action: String = ""): String = when (status) {
    401 -> "登录已过期，请重新登录学校账号"
    408 -> "请求被拒绝（登录可能已失效）"
    else -> buildString {
        append("服务器返回 ").append(status)
        if (action.isNotBlank()) append("（").append(action).append("）")
        val message = activityServerMessageOf(body)
        if (message.isNotBlank()) {
            append("：").append(message)
        } else if (body.isNotBlank()) {
            append("：").append(body.replace(Regex("\\s+"), " ").trim().take(160))
        }
        if (message.contains("学校信息") || message.contains("学校编号")) {
            append(" · 请重新登录学校账号（登录入口学校编号异常）")
        }
    }
}

/** 从学校 API 错误响应中提取可读 message（JSON 的 message/msg/error 优先；无则原文压缩截断）。 */
internal fun activityServerMessageOf(body: String): String {
    val text = body.trim()
    if (text.isEmpty()) return ""
    val fromJson = runCatching {
        val root = NetJson.parseToJsonElement(text)
        val obj = root as? JsonObject
        listOf("message", "msg", "error", "errorMessage")
            .firstNotNullOfOrNull { key ->
                obj?.get(key)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            }
    }.getOrNull()
    if (!fromJson.isNullOrBlank()) return fromJson.take(160)
    // 非 JSON（如 nginx 错误页）：压缩空白后截断，仍保留原文特征。
    return text.replace(Regex("\\s+"), " ").trim().take(160)
}

// ---------------------------------------------------------------- JSON 映射

private fun JsonObject.str(key: String): String =
    (this[key]?.jsonPrimitive?.contentOrNull).orEmpty()

private fun JsonObject.intOr(key: String, fallback: Int = 0): Int =
    this[key]?.jsonPrimitive?.intOrNull ?: fallback

private fun JsonObject.longOrNull(key: String): Long? =
    this[key]?.jsonPrimitive?.longOrNull

internal fun JsonObject.toActivity(): ScActivity = ScActivity(
    id = str("id"),
    name = str("name"),
    state = intOr("state"),
    actBeginTime = longOrNull("actBeginTime"),
    actEndTime = longOrNull("actEndTime"),
    supBeginTime = longOrNull("supBeginTime"),
    supEndTime = longOrNull("supEndTime"),
)

internal fun JsonObject.toDetail(): ScDetail {
    val roles = (this["activityRoleConfigs"] as? JsonArray) ?: JsonArray(emptyList())
    return ScDetail(
        id = str("id"),
        name = str("name"),
        state = intOr("state"),
        isSignUp = intOr("isSignUp"),
        roles = roles.mapNotNull { raw ->
            val o = raw as? JsonObject ?: return@mapNotNull null
            val role = o["signUpRole"] as? JsonObject
            ScRole(
                supRoleId = o.str("supRoleId"),
                name = role?.str("name").orEmpty().ifBlank { "角色" },
                numLimit = o["numLimit"]?.jsonPrimitive?.intOrNull,
                signUpNum = o.intOr("signUpNum"),
            )
        },
    )
}
