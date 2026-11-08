package com.furina.classtools.features.feedback

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asObjectOrNull
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import java.net.URLEncoder
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 反馈建议仓库（对齐小程序 `pages/fb` + `utils/cloud.js fbPull/fbSend/fbDone`）。
 *
 * 契约：`docs/api/ENDPOINTS.md`
 * - 读：`GET feedback?select=*&order=ts.desc`（RLS：普通用户只返回自己的行；管理员返回全部）；
 * - 提交：`POST feedback` 数组 `{id, content, contact:"", acc:<sid>, done:false, ts}`；
 * - 管理员标记：`PATCH feedback?id=eq.<id>` `{"done":true}`；PATCH 失败时回退 POST upsert（mp 同款兜底）；
 * - 隐私：不收集联系方式（contact 恒空串、页面不展示）；页面不显示学号（管理员列表用姓名映射由 UI 注入）；
 * - 本机内存缓存 + TTL，失败降级缓存（离线可看自己的历史反馈）。
 */
interface FeedbackRepository {
    suspend fun load(force: Boolean = false): FeedbackLoadResult

    suspend fun submit(content: String): FeedbackSubmitResult

    suspend fun markDone(id: String): FeedbackDoneResult

    suspend fun cached(): List<FeedbackRow>
}

class DefaultFeedbackRepository(
    private val api: ApiClient,
    private val currentSid: () -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val json: Json = NetJson,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val randomSuffix: () -> String = { FeedbackRules.randomBase36() },
) : FeedbackRepository {

    private val mutex = Mutex()
    private var cache: List<FeedbackRow> = emptyList()
    private var loadedOnce = false
    private var lastFetchAt = 0L

    override suspend fun load(force: Boolean): FeedbackLoadResult {
        mutex.lock()
        try {
            if (!force && loadedOnce && clock() - lastFetchAt < ttlMs) {
                return FeedbackLoadResult.Success(cache, fromCache = true)
            }
            val sid = sidOrNull()
                ?: return if (cache.isNotEmpty()) {
                    FeedbackLoadResult.Success(cache, fromCache = true)
                } else {
                    FeedbackLoadResult.Failure("请先完成身份验证")
                }
            return when (val result = api.get(FEEDBACK_SELECT_PATH)) {
                is ApiResult.Failure -> {
                    if (cache.isNotEmpty()) {
                        FeedbackLoadResult.Success(cache, fromCache = true)
                    } else {
                        FeedbackLoadResult.Failure(result.error.displayMessage)
                    }
                }

                is ApiResult.Success -> {
                    val rows = parseRows(json, result.value)
                    if (rows == null) {
                        if (cache.isNotEmpty()) {
                            FeedbackLoadResult.Success(cache, fromCache = true)
                        } else {
                            FeedbackLoadResult.Failure("反馈数据格式异常")
                        }
                    } else {
                        // 防御：普通用户 RLS 已限制，客户端再按 acc 兜底过滤，避免管理员页误用同一仓库时泄露
                        cache = rows.filter { it.acc.isBlank() || it.acc == sid }
                        loadedOnce = true
                        lastFetchAt = clock()
                        FeedbackLoadResult.Success(cache, fromCache = false)
                    }
                }
            }
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun submit(content: String): FeedbackSubmitResult {
        mutex.lock()
        try {
            FeedbackRules.validateContent(content)?.let { return FeedbackSubmitResult.Failure(it) }
            val sid = sidOrNull() ?: return FeedbackSubmitResult.Failure("请先完成身份验证")
            val now = clock()
            val row = FeedbackRow(
                id = FeedbackRules.newId(now, randomSuffix()),
                content = FeedbackRules.normalizeContent(content),
                contact = "",
                acc = sid,
                done = false,
                ts = now,
            )
            val body = buildSubmitBody(row)
            return when (val result = api.post(FEEDBACK_PATH, body)) {
                is ApiResult.Failure -> FeedbackSubmitResult.Failure(
                    result.error.displayMessage.ifBlank { "网络不可用，请稍后重试" },
                )

                is ApiResult.Success -> {
                    cache = listOf(row) + cache.filterNot { it.id == row.id }
                    loadedOnce = true
                    lastFetchAt = now
                    FeedbackSubmitResult.Success(row)
                }
            }
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun markDone(id: String): FeedbackDoneResult {
        mutex.lock()
        try {
            val rowId = id.trim()
            if (rowId.isEmpty()) return FeedbackDoneResult.Failure("反馈 id 无效")
            if (sidOrNull() == null) return FeedbackDoneResult.Failure("请先完成身份验证")
            val patchPath = "feedback?id=eq." + URLEncoder.encode(rowId, "UTF-8")
            val patched = api.patch(patchPath, DONE_BODY)
            if (patched is ApiResult.Success) {
                updateCacheDone(rowId)
                return FeedbackDoneResult.Success
            }
            // 与 mp 一致：PATCH 失败回退 POST upsert（需要完整行；缓存里没有则失败）
            val current = cache.firstOrNull { it.id == rowId }
                ?: return FeedbackDoneResult.Failure("标记失败，请重试")
            val upsertBody = buildSubmitBody(current.copy(done = true))
            return when (api.post(FEEDBACK_PATH, upsertBody)) {
                is ApiResult.Success -> {
                    updateCacheDone(rowId)
                    FeedbackDoneResult.Success
                }

                is ApiResult.Failure -> FeedbackDoneResult.Failure("标记失败，请重试")
            }
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun cached(): List<FeedbackRow> {
        mutex.lock()
        try {
            return cache
        } finally {
            mutex.unlock()
        }
    }

    private fun updateCacheDone(id: String) {
        cache = cache.map { if (it.id == id) it.copy(done = true) else it }
    }

    private fun sidOrNull(): String? = currentSid()?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        const val FEEDBACK_PATH: String = "feedback"
        const val FEEDBACK_SELECT_PATH: String = "feedback?select=*&order=ts.desc"
        const val DONE_BODY: String = """{"done":true}"""
        const val DEFAULT_TTL_MS: Long = 30L * 1000

        /** 提交 body：PostgREST 批量语义，数组。 */
        fun buildSubmitBody(row: FeedbackRow): String = buildJsonArray {
            add(
                buildJsonObject {
                    put("id", JsonPrimitive(row.id))
                    put("content", JsonPrimitive(row.content))
                    put("contact", JsonPrimitive(""))   // 不收集联系方式（与 mp 2026-11-05 起口径一致）
                    put("acc", JsonPrimitive(row.acc))
                    put("done", JsonPrimitive(row.done))
                    put("ts", JsonPrimitive(row.ts))
                },
            )
        }.toString()

        /** 解析反馈数组（容错坏行）；非数组 → null。 */
        fun parseRows(json: Json, raw: String): List<FeedbackRow>? {
            val element = parseJsonOrNull(json, raw) ?: return null
            val array = element as? JsonArray ?: return null
            val rows = ArrayList<FeedbackRow>(array.size)
            for (item in array) {
                val obj = item.asObjectOrNull() ?: continue
                val id = obj["id"].asStringOrNull()?.trim().orEmpty()
                if (id.isEmpty()) continue
                rows.add(
                    FeedbackRow(
                        id = id,
                        content = obj["content"].asStringOrNull().orEmpty(),
                        contact = obj["contact"].asStringOrNull().orEmpty(),
                        acc = obj["acc"].asStringOrNull().orEmpty(),
                        done = (obj["done"] as? JsonPrimitive)?.booleanOrNull ?: false,
                        ts = (obj["ts"] as? JsonPrimitive)?.longOrNull ?: 0L,
                    ),
                )
            }
            return rows
        }
    }
}
