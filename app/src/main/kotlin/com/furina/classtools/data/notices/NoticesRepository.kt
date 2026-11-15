package com.furina.classtools.data.notices

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiError
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asObjectOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
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
 * 通知仓库（只读）：**两级 + 分页**拉取 + `[[CLASS_NOTICE_V1]]` 解析 + hidden 过滤 + override 白名单。
 *
 * 契约：`docs/api/NOTICES.md` §2 + `docs/api/ENDPOINTS.md` E7/E8/E9。
 *
 * 2026-11-13 网络修复（用户反馈「通知无法联网」根因）：
 * - CloudBase `notices?select=*` 全表响应约 **13.9MB**，旧实现每次拉整表 → App 10s 超时 → 只显示本机缓存；
 * - 现改为**按 `ts` 倒序分页**：首次只拉最新 [PAGE_SIZE] 条（~0.55MB，快速出首屏），
 *   旧数据通过 [loadMore] 每页 20 条追加；签名变化时自动重载第一页；
 *   **UI 由 [NoticesAutoLoader] 在后台自动循环 [loadMore] 直到 `hasMore=false`（用户无需点击「加载全部」）**；
 * - 每次请求加 30s 协程超时保护；超时/网络失败自动重试 1 次（ApiClient 对 GET 亦有一次网络重试）；
 * - 失败保留已加载缓存并在 UI 给出明确错误（「网络较慢或服务器无响应」）。
 *
 * 其他口径不变：
 * - 第一级：`select=id,ts,hidden,edited_at` 比签名；一致 → 直接用缓存（0 次正文请求）；
 * - hidden：非管理员过滤掉；管理员保留（hidden=true，UI 可标注）；
 * - override_json：按白名单应用到 group；
 * - 附件/条目：只保留元信息，不把大 base64 塞进 UI 模型（见 [NoticeBodyParser]）；
 * - 去重：跨页合并后仍走 [NoticeDedupe]（不得回退）。
 */
interface NoticesRepository {
    /** @param force true = 跳过 TTL（TTL 10min），但仍走签名校验。 */
    suspend fun load(force: Boolean = false): NoticesLoadResult

    /**
     * 加载下一页（按 ts 倒序的旧数据，每页 [DefaultNoticesRepository.PAGE_SIZE] 条）。
     *
     * - 追加到已加载内容并更新缓存；签名变化时内部改为重载第一页；
     * - 失败返回 [NoticesLoadResult.Failure]（调用方保留已加载内容并展示重试）。
     */
    suspend fun loadMore(): NoticesLoadResult

    suspend fun cached(): NoticesData?
}

class DefaultNoticesRepository(
    private val api: ApiClient,
    private val isAdmin: () -> Boolean = { false },
    private val clock: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val json: Json = NetJson,
    /** 每页条数（最新 20 条约 0.55MB；分页是本次网络修复的核心）。 */
    private val pageSize: Int = PAGE_SIZE,
    /** 单次请求的协程超时保护（毫秒）。 */
    private val requestTimeoutMs: Long = DEFAULT_REQUEST_TIMEOUT_MS,
    /** 超时后的重试次数（网络失败重试由 ApiClient 负责，避免重复放大等待）。 */
    private val timeoutRetries: Int = 1,
    private val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
) : NoticesRepository {

    private val mutex = Mutex()
    private var cache: NoticesData? = null
    private var cachedSignature: String? = null
    private var lastFetchAt = 0L
    private var loadedOnce = false

    // 分页状态（按 ts 倒序已加载；跨页合并后仍走 NoticeDedupe）
    private var totalRawCount = 0
    private var loadedRawCount = 0
    private val autoAccum = ArrayList<NoticeEntry>()
    private val manualAccum = ArrayList<ManualNotice>()
    private var hiddenCountAccum = 0

    override suspend fun load(force: Boolean): NoticesLoadResult = mutex.withLock { loadLocked(force) }

    override suspend fun loadMore(): NoticesLoadResult = mutex.withLock { loadMoreLocked() }

    override suspend fun cached(): NoticesData? = mutex.withLock { cache }

    // ------------------------------------------------------------ 内部：首屏 / 加载更多

    private suspend fun loadLocked(force: Boolean): NoticesLoadResult {
        val now = clock()
        if (!force && loadedOnce && cache != null && now - lastFetchAt < ttlMs) {
            return NoticesLoadResult.Success(cache!!.copy(fromCache = true))
        }

        // 第一级：轻量签名（约 2KB；全表行签名，不拉正文）
        val liteRows = when (val lite = fetchString(LITE_PATH)) {
            is ApiResult.Failure -> return fallbackOrFailure(networkMessage(lite.error))
            is ApiResult.Success -> parseSigRows(json, lite.value)
                ?: return fallbackOrFailure("通知签名数据格式异常")
        }
        val signature = NoticeSignature.ofRows(liteRows)
        if (cache != null && signature == cachedSignature) {
            lastFetchAt = now
            loadedOnce = true
            return NoticesLoadResult.Success(cache!!.copy(fromCache = true, fetchedAt = now))
        }

        // 第二级：只拉最新一页（ts 倒序 limit=20）→ 首屏快速可见
        resetPages()
        cachedSignature = signature
        totalRawCount = liteRows.size
        val firstPage = fetchPage(offset = 0)
            ?: return fallbackOrFailure(lastPageError ?: PAGE_PARSE_MESSAGE)
        appendPage(firstPage)
        return publish(now = clock(), fromCache = false)
    }

    private suspend fun loadMoreLocked(): NoticesLoadResult {
        val current = cache
        if (current == null || cachedSignature == null) return loadLocked(force = true)
        if (!current.hasMore) {
            return NoticesLoadResult.Success(current.copy(fromCache = true, fetchedAt = clock()))
        }
        val now = clock()

        // 追加页码前先复核签名（2KB）：数据变了就重载第一页，避免拼接旧页
        when (val lite = fetchString(LITE_PATH)) {
            is ApiResult.Failure -> return NoticesLoadResult.Failure(networkMessage(lite.error))
            is ApiResult.Success -> {
                val rows = parseSigRows(json, lite.value)
                    ?: return NoticesLoadResult.Failure("通知签名数据格式异常")
                val signature = NoticeSignature.ofRows(rows)
                if (signature != cachedSignature) {
                    return loadLocked(force = true)
                }
                totalRawCount = rows.size
            }
        }

        // 旧页体积差异极大（部分页 3–4MB）；超时时自动缩小 limit 重试，保证「全部加载」可继续推进。
        val page = fetchPageAdaptive(offset = loadedRawCount)
            ?: return NoticesLoadResult.Failure(lastPageError ?: PAGE_PARSE_MESSAGE)
        if (page.isEmpty()) {
            totalRawCount = loadedRawCount
            return publish(now = now, fromCache = false)
        }
        appendPage(page)
        if (page.size < pageSize) totalRawCount = loadedRawCount
        return publish(now = now, fromCache = false)
    }

    private fun resetPages() {
        autoAccum.clear()
        manualAccum.clear()
        hiddenCountAccum = 0
        totalRawCount = 0
        loadedRawCount = 0
    }

    private fun appendPage(rows: List<NoticeRow>) {
        val chunk = parseChunk(rows, admin = isAdmin())
        // 跨页去重：旧页与新页合并后统一去重（不拆散已合并条目）
        val dedupedAuto = NoticeDedupe.dedupeAuto(autoAccum + chunk.auto).entries
        val dedupedManual = NoticeDedupe.dedupeManual(manualAccum + chunk.manual).entries
        autoAccum.clear()
        autoAccum.addAll(dedupedAuto.sortedByDescending { it.ts })
        manualAccum.clear()
        manualAccum.addAll(dedupedManual.sortedByDescending { it.ts })
        hiddenCountAccum += chunk.hiddenCount
        loadedRawCount += rows.size
    }

    private fun publish(now: Long, fromCache: Boolean): NoticesLoadResult {
        val remaining = (totalRawCount - loadedRawCount).coerceAtLeast(0)
        val data = NoticesData(
            auto = autoAccum.toList(),
            manual = manualAccum.toList(),
            hiddenCount = hiddenCountAccum,
            fetchedAt = now,
            fromCache = fromCache,
            hasMore = remaining > 0,
            remainingCount = remaining,
            loadedRawCount = loadedRawCount,
        )
        cache = data
        lastFetchAt = now
        loadedOnce = true
        return NoticesLoadResult.Success(data)
    }

    private fun fallbackOrFailure(message: String): NoticesLoadResult {
        val cachedData = cache
        return if (cachedData != null) {
            NoticesLoadResult.Success(cachedData.copy(fromCache = true))
        } else {
            NoticesLoadResult.Failure(message)
        }
    }

    /**
     * 拉一页正文；返回 null = 失败（具体错误已转成 UI 文案由调用方兜底）。
     * @param limit 本页条数（首屏 20；加载更多自适应 5/3/1）
     */
    private suspend fun fetchPage(offset: Int, limit: Int = pageSize): List<NoticeRow>? {
        val path = pagePath(offset, limit)
        return when (val page = fetchString(path)) {
            is ApiResult.Failure -> {
                lastPageError = networkMessage(page.error)
                null
            }

            is ApiResult.Success -> parseRows(json, page.value) ?: run {
                lastPageError = PAGE_PARSE_MESSAGE
                null
            }
        }
    }

    /**
     * 「加载更多」自适应分页：先试 5 条；若超时（旧通知正文单页可达 3–4MB），
     * 依次降到 3 条、1 条重试，保证慢网/大响应下也能继续把全部通知加载完。
     */
    private suspend fun fetchPageAdaptive(offset: Int): List<NoticeRow>? {
        // 测试/自定义 pageSize≤5 时保持原页大小（生产 20 时走 5/3/1 自适应）
        val limits = if (pageSize <= 5) listOf(pageSize) else MORE_PAGE_LIMITS
        for (limit in limits) {
            val page = fetchPage(offset, limit)
            if (page != null) return page
            if (lastPageError != TIMEOUT_MESSAGE) return null
        }
        return null
    }

    /** 最近一次分页失败文案（UI 用它展示「加载更多失败」）。 */
    var lastPageError: String? = null
        private set

    private fun pagePath(offset: Int, limit: Int = pageSize): String =
        "notices?select=*&order=ts.desc,id.desc&archived=eq.false&limit=$limit&offset=$offset"

    private fun networkMessage(error: ApiError): String = when (error) {
        is ApiError.Network -> when (error.cause) {
            // withTimeout 取消 / OkHttp 读超时：给用户明确「服务器无响应」而不是笼统网络错误
            is kotlinx.coroutines.CancellationException,
            is java.util.concurrent.TimeoutException,
            is java.net.SocketTimeoutException -> TIMEOUT_MESSAGE

            else -> error.message.ifBlank { DEFAULT_ERROR_MESSAGE }
        }

        else -> error.displayMessage
    }

    // ------------------------------------------------------------ 单请求：30s 超时 + 超时重试 1 次

    private suspend fun fetchString(path: String): ApiResult<String> {
        var attempt = 0
        while (true) {
            val result = try {
                withTimeout(requestTimeoutMs) { api.get(path) }
            } catch (timeout: TimeoutCancellationException) {
                ApiResult.Failure(ApiError.Network(cause = timeout, message = TIMEOUT_MESSAGE))
            }
            if (result is ApiResult.Success) return result
            val error = (result as ApiResult.Failure).error
            // 超时（withTimeout 取消 / 传输层读超时）才在仓库层重试；普通网络失败由 ApiClient 重试
            val timedOut = error is ApiError.Network &&
                (
                    error.cause is kotlinx.coroutines.CancellationException ||
                        error.cause is java.util.concurrent.TimeoutException ||
                        error.cause is java.net.SocketTimeoutException
                    )
            if (timedOut && attempt < timeoutRetries) {
                attempt += 1
                delay(retryDelayMs)
                continue
            }
            return result
        }
    }

    companion object {
        const val LITE_PATH: String =
            "notices?select=id,ts,hidden,edited_at&order=ts.asc,id.asc&archived=eq.false"

        /** 首屏每页条数：最新 20 条约 0.55MB（全表 13.9MB 会触发超时）。 */
        const val PAGE_SIZE: Int = 20

        /** 「加载更多」自适应页大小：旧通知单页可达 3–4MB，超时后逐级缩小。 */
        val MORE_PAGE_LIMITS: List<Int> = listOf(5, 3, 1)

        /** 单次请求超时保护（大于传输层 10s 读超时；仅兜底卡死场景）。 */
        const val DEFAULT_REQUEST_TIMEOUT_MS: Long = 40_000L

        const val DEFAULT_RETRY_DELAY_MS: Long = 350L
        const val DEFAULT_TTL_MS: Long = 10L * 60 * 1000

        /** 超时/网络失败文案（UI 明确提示，而不是笼统「网络不可用」）。 */
        const val TIMEOUT_MESSAGE: String = "网络较慢或服务器无响应，请重试"
        const val DEFAULT_ERROR_MESSAGE: String = "网络不可用，请稍后重试"
        const val PAGE_PARSE_MESSAGE: String = "通知数据格式异常"

        /** 首屏正文路径（测试/诊断用；offset 由分页状态决定）。 */
        fun pagePathFor(offset: Int, pageSize: Int = PAGE_SIZE): String =
            "notices?select=*&order=ts.desc,id.desc&archived=eq.false&limit=$pageSize&offset=$offset"

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

        /** 解析一页正文；不是数组 → null。坏行跳过。 */
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

        /** 行 → 条目（自动/人工分流、hidden 过滤、override 应用、items 元信息解析）；不做去重。 */
        fun parseChunk(rows: List<NoticeRow>, admin: Boolean): NoticeChunk {
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
            return NoticeChunk(auto = auto, manual = manual, hiddenCount = hiddenCount)
        }

        /** 一页（或整表）行 → 渲染数据：解析 + 跨条目去重（保留旧 API 供预览/测试使用）。 */
        fun buildData(rows: List<NoticeRow>, admin: Boolean, now: Long): NoticesData {
            val chunk = parseChunk(rows, admin)
            val dedupedAuto = NoticeDedupe.dedupeAuto(chunk.auto).entries.sortedByDescending { it.ts }
            val dedupedManual = NoticeDedupe.dedupeManual(chunk.manual).entries.sortedByDescending { it.ts }
            return NoticesData(
                auto = dedupedAuto,
                manual = dedupedManual,
                hiddenCount = chunk.hiddenCount,
                fetchedAt = now,
                fromCache = false,
            )
        }
    }
}

/** 单页解析结果（尚未去重/排序）。 */
data class NoticeChunk(
    val auto: List<NoticeEntry>,
    val manual: List<ManualNotice>,
    val hiddenCount: Int,
)
