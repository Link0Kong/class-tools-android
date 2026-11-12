package com.furina.classtools.data.notices

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asObjectOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/**
 * 通知签名（两级拉取的第一级，约 2KB）：
 * `sig = rows.joinToString("|") { "id:ts:hidden:edited_at" }`（服务端已按 `ts.asc,id.asc` 排序）。
 *
 * 必须包含 `hidden` 与 `edited_at`：管理员编辑/隐藏**不改 ts**，否则永远命中旧缓存。
 */
object NoticeSignature {
    fun ofRows(rows: List<NoticeSigRow>): String =
        rows.joinToString("|") { "${it.id}:${it.ts}:${it.hidden}:${it.editedAt}" }
}

/**
 * 通知仓库（只读）：两级拉取 + `[[CLASS_NOTICE_V1]]` 解析 + hidden 过滤 + override 白名单。
 *
 * 契约：`docs/api/NOTICES.md` §2 + `docs/api/ENDPOINTS.md` E7/E8/E9。
 * - 第一级：`select=id,ts,hidden,edited_at` 比签名；一致 → 直接用缓存（0 次大请求）；
 * - 第二级：签名变化/首次 → `select=*` 整表；
 * - hidden：非管理员过滤掉；管理员保留（hidden=true，UI 可标注）；
 * - override_json：按白名单应用到 group（title/cat/summary/aiSummary/deadline/eventAt/expireAt/timeLabel）；
 * - 附件/条目：只保留元信息，不把大 base64 塞进 UI 模型（见 [NoticeBodyParser]）。
 */
interface NoticesRepository {
    /** @param force true = 跳过 TTL（TTL 10min），但仍走签名校验。 */
    suspend fun load(force: Boolean = false): NoticesLoadResult

    suspend fun cached(): NoticesData?
}

class DefaultNoticesRepository(
    private val api: ApiClient,
    private val isAdmin: () -> Boolean = { false },
    private val clock: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val json: Json = NetJson,
) : NoticesRepository {

    private val mutex = Mutex()
    private var cache: NoticesData? = null
    private var cachedSignature: String? = null
    private var lastFetchAt: Long = 0L
    private var loadedOnce = false

    override suspend fun load(force: Boolean): NoticesLoadResult = mutex.withLock {
        val now = clock()
        if (!force && loadedOnce && cache != null && now - lastFetchAt < ttlMs) {
            return@withLock NoticesLoadResult.Success(cache!!.copy(fromCache = true))
        }

        // 第一级：轻量签名（约 2KB）
        val liteRows = when (val lite = api.get(LITE_PATH)) {
            is ApiResult.Failure -> return@withLock fallbackOrFailure(lite.error.displayMessage)
            is ApiResult.Success -> parseSigRows(json, lite.value)
                ?: return@withLock fallbackOrFailure("通知签名数据格式异常")
        }
        val signature = NoticeSignature.ofRows(liteRows)
        if (cache != null && signature == cachedSignature) {
            lastFetchAt = now
            loadedOnce = true
            return@withLock NoticesLoadResult.Success(cache!!.copy(fromCache = true, fetchedAt = now))
        }

        // 第二级：整表
        val rows = when (val full = api.get(FULL_PATH)) {
            is ApiResult.Failure -> return@withLock fallbackOrFailure(full.error.displayMessage)
            is ApiResult.Success -> parseRows(json, full.value)
                ?: return@withLock fallbackOrFailure("通知数据格式异常")
        }
        val data = buildData(rows, admin = isAdmin(), now = now)
        cache = data
        cachedSignature = signature
        lastFetchAt = now
        loadedOnce = true
        NoticesLoadResult.Success(data)
    }

    override suspend fun cached(): NoticesData? = mutex.withLock { cache }

    private fun fallbackOrFailure(message: String): NoticesLoadResult {
        val cachedData = cache
        return if (cachedData != null) {
            NoticesLoadResult.Success(cachedData.copy(fromCache = true))
        } else {
            NoticesLoadResult.Failure(message)
        }
    }

    companion object {
        const val LITE_PATH: String =
            "notices?select=id,ts,hidden,edited_at&order=ts.asc,id.asc&archived=eq.false"
        const val FULL_PATH: String =
            "notices?select=*&order=ts.asc,id.asc&archived=eq.false"
        const val DEFAULT_TTL_MS: Long = 10L * 60 * 1000

        /** 解析签名数组；不是数组 → null。坏行跳过。 */
        fun parseSigRows(json: Json, raw: String): List<NoticeSigRow>? {
            val element = parseJsonOrNull(json, raw) ?: return null
            val array = element as? JsonArray ?: return null
            val rows = ArrayList<NoticeSigRow>(array.size)
            for (item in array) {
                val obj = item.asObjectOrNull() ?: continue
                val decoded = try {
                    json.decodeFromJsonElement(NoticeSigRow.serializer(), obj)
                } catch (_: Throwable) {
                    continue
                }
                if (decoded.id.isNotBlank()) rows.add(decoded)
            }
            return rows
        }

        /** 解析整表；不是数组 → null。坏行跳过。 */
        fun parseRows(json: Json, raw: String): List<NoticeRow>? {
            val element = parseJsonOrNull(json, raw) ?: return null
            val array = element as? JsonArray ?: return null
            val rows = ArrayList<NoticeRow>(array.size)
            for (item in array) {
                val obj = item.asObjectOrNull() ?: continue
                val decoded = try {
                    json.decodeFromJsonElement(NoticeRow.serializer(), obj)
                } catch (_: Throwable) {
                    continue
                }
                if (decoded.id.isNotBlank()) rows.add(decoded)
            }
            return rows
        }

        /** 行 → 渲染数据：自动/人工分流、hidden 过滤、override 应用、items 元信息解析。 */
        fun buildData(rows: List<NoticeRow>, admin: Boolean, now: Long): NoticesData {
            val auto = ArrayList<NoticeEntry>()
            val manual = ArrayList<ManualNotice>()
            var hiddenCount = 0
            for (row in rows) {
                if (row.archived) continue
                if (NoticeBodyParser.isAuto(row.body)) {
                    val group = NoticeBodyParser.parseGroup(row.body) ?: continue
                    if (row.hidden) {
                        if (!admin) {
                            hiddenCount += 1
                            continue
                        }
                        hiddenCount += 1
                    }
                    val override = NoticeOverride.parse(row.overrideJson)
                    val applied = NoticeOverride.apply(group, override)
                    val items = NoticeBodyParser.parseItems(group.items)
                    auto.add(
                        NoticeEntry(
                            // 清掉 body / items 原文，避免大 base64 常驻（渲染只用 items 元信息）
                            row = row.copy(body = ""),
                            group = applied.copy(items = emptyList()),
                            items = items,
                            hidden = row.hidden,
                            edited = row.editedAt > 0,
                        ),
                    )
                } else {
                    val attachments = NoticeBodyParser.parseManualAttachments(row.attachments)
                    manual.add(
                        ManualNotice(
                            row = row.copy(attachments = ""),
                            attachments = attachments,
                        ),
                    )
                }
            }
            // 数据层去重：云端可能对同一逻辑通知保留多行（旧行未归档/重新采集/合并来源），
            // 去重后 UI 只看到一条；hidden/override 已在上面应用，保留 supplements/mergedSources 语义。
            val dedupedAuto = NoticeDedupe.dedupeAuto(auto).entries.sortedByDescending { it.ts }
            val dedupedManual = NoticeDedupe.dedupeManual(manual).entries.sortedByDescending { it.ts }
            return NoticesData(
                auto = dedupedAuto,
                manual = dedupedManual,
                hiddenCount = hiddenCount,
                fetchedAt = now,
                fromCache = false,
            )
        }
    }
}
