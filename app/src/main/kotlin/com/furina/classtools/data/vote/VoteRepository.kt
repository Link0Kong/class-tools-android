package com.furina.classtools.data.vote

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiError
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asBooleanOrNull
import com.furina.classtools.data.network.asIntOrNull
import com.furina.classtools.data.network.asLongOrNull
import com.furina.classtools.data.network.asObjectOrNull
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 投票仓库（契约：`web/vote/CONTRACT.md`，RPC：`poll_list/poll_detail/poll_create/poll_submit/poll_manage`）。
 *
 * - 所有请求 `POST {REST}/rpc/<name>`，body 为具名参数 JSON；服务端统一返回 `{ok:...}`（HTTP 200）；
 * - 响应**不含学号**；指定成员范围只用座号；
 * - 列表/详情带内存缓存 + TTL（默认 15s），网络失败降级上次数据并给出 `warning`；
 * - 错误映射为 [VoteError]（中文文案见各子类）。
 */
interface VoteRepository {
    suspend fun list(force: Boolean = false): VoteResult<PollList>

    suspend fun detail(pollId: String, force: Boolean = false): VoteResult<PollDetail>

    suspend fun create(draft: PollDraft): VoteResult<PollDetail>

    suspend fun submit(pollId: String, choices: List<String>): VoteResult<PollDetail>

    suspend fun manage(pollId: String, action: PollManageAction): VoteResult<PollManageResult>
}

/** 服务端 `{ok:false,reason}` → 明确错误（纯函数，可单测）。 */
object VoteErrors {

    private val MAX_CHOICES_REGEX = Regex("""最多选\s*(\d+)\s*项""")

    fun fromReason(reason: String?): VoteError {
        val text = reason?.trim().orEmpty()
        if (text.isEmpty()) return VoteError.Unknown()
        MAX_CHOICES_REGEX.find(text)?.let { match ->
            return VoteError.LimitExceeded(match.groupValues[1].toIntOrNull() ?: 0)
        }
        return when (text) {
            "投票不存在" -> VoteError.NotFound
            "身份已失效，请重新进入班级", "未登录", "身份无效" -> VoteError.IdentityInvalid
            "不在可见范围内" -> VoteError.OutOfAudience
            "投票已结束" -> VoteError.Closed
            "已投过票，不可改票" -> VoteError.AlreadyVoted
            "无权管理该投票" -> VoteError.NoPermission
            "选项不合法" -> VoteError.InvalidOptions
            "重复的选项" -> VoteError.DuplicateChoices
            "只能选 1 项" -> VoteError.SingleChoiceOnly
            "已有投票，不能修改内容" -> VoteError.EditLocked
            "投票编号不合法" -> VoteError.Validation("投票编号不合法")
            "发起投票失败，请重试", "投票失败，请重试", "操作失败，请重试" -> VoteError.Unknown(text)
            else -> VoteError.Validation(text)
        }
    }

    fun fromApiError(error: ApiError): VoteError = when (error) {
        is ApiError.Network -> VoteError.Network()
        is ApiError.TokenInvalid -> VoteError.IdentityInvalid
        is ApiError.Http -> when (error.status) {
            401, 403 -> VoteError.IdentityInvalid
            else -> VoteError.Network("网络不可用，请稍后重试")
        }

        is ApiError.Parse -> VoteError.Validation("投票数据格式异常")
        is ApiError.Config -> VoteError.Network("云端未配置，无法使用投票")
    }
}

/** RPC body 构造（纯函数，可单测）。 */
object VoteRpcBody {

    fun listBody(): String = "{}"

    fun detailBody(pollId: String): String = buildJsonObject {
        put("p_id", pollId)
    }.toString()

    fun submitBody(pollId: String, choices: List<String>): String = buildJsonObject {
        put("p_id", pollId)
        put("p_choices", buildJsonArray { choices.distinct().forEach { add(JsonPrimitive(it)) } })
    }.toString()

    fun createBody(draft: PollDraft): String = buildJsonObject {
        put("p_id", draft.id)
        put("p_title", draft.title)
        put("p_description", draft.description)
        put("p_mode", draft.mode.wire)
        put("p_options", optionsJson(draft.options))
        put("p_settings", settingsJson(draft.settings, draft.options.size))
        put("p_deadline", draft.deadline)
        put("p_audience", draft.audience.wire)
        put(
            "p_audience_seats",
            buildJsonArray { draft.audienceSeats.forEach { add(JsonPrimitive(it)) } },
        )
    }.toString()

    fun manageBody(pollId: String, action: PollManageAction): String = buildJsonObject {
        put("p_id", pollId)
        when (action) {
            is PollManageAction.Close -> put("p_action", "close")
            is PollManageAction.Delete -> put("p_action", "delete")
            is PollManageAction.Extend -> {
                put("p_action", "extend")
                put("p_deadline", action.deadline.coerceAtLeast(0L))
            }

            is PollManageAction.Edit -> {
                val draft = VoteLogic.normalizeDraft(action.draft)
                put("p_action", "edit")
                put("p_title", draft.title)
                put("p_description", draft.description)
                put("p_options", optionsJson(draft.options))
                put("p_settings", settingsJson(draft.settings, draft.options.size))
                put("p_deadline", draft.deadline)
                if (action.updateAudience) {
                    put("p_audience", draft.audience.wire)
                    put(
                        "p_audience_seats",
                        buildJsonArray { draft.audienceSeats.forEach { add(JsonPrimitive(it)) } },
                    )
                }
            }
        }
    }.toString()

    fun optionsJson(options: List<PollOptionDraft>): JsonArray = buildJsonArray {
        options.forEach { option ->
            add(
                buildJsonObject {
                    put("id", option.id)
                    put("text", option.text)
                    if (option.desc.isNotBlank()) put("desc", option.desc)
                    if (option.img.isNotBlank()) put("img", option.img)
                },
            )
        }
    }

    fun settingsJson(settings: PollSettings, optionCount: Int): JsonObject {
        val normalized = settings.normalized(optionCount)
        return buildJsonObject {
            put("multi", normalized.multi)
            put("maxChoices", normalized.maxChoices)
            put("anonymous", normalized.anonymous)
            put("allowChange", normalized.allowChange)
            put("resultVisibility", normalized.resultVisibility.wire)
        }
    }
}

/** 响应解析（纯函数；失败返回 null，由仓库映射错误）。 */
object VoteParser {

    sealed interface Envelope {
        data class Ok(val body: JsonObject) : Envelope
        data class Err(val reason: String) : Envelope
        data object Malformed : Envelope
    }

    fun envelope(raw: String, json: Json = NetJson): Envelope {
        val element = parseJsonOrNull(json, raw) ?: return Envelope.Malformed
        val obj = element.asObjectOrNull() ?: return Envelope.Malformed
        val ok = obj["ok"].asBooleanOrNull() ?: return Envelope.Malformed
        if (!ok) return Envelope.Err(obj["reason"].asStringOrNull().orEmpty())
        return Envelope.Ok(obj)
    }

    fun parseList(body: JsonObject): PollList {
        val polls = (body["polls"] as? JsonArray)?.mapNotNull { parseSummary(it.asObjectOrNull()) }.orEmpty()
        return PollList(
            polls = polls,
            serverNow = body["now"].asLongOrNull() ?: 0L,
        )
    }

    fun parseDetail(body: JsonObject): PollDetail {
        val poll = parsePoll(body["poll"].asObjectOrNull())
        val voters = (body["voters"] as? JsonArray)?.mapNotNull { element ->
            val obj = element.asObjectOrNull() ?: return@mapNotNull null
            PollVoter(
                name = obj["name"].asStringOrNull().orEmpty(),
                choices = (obj["choices"] as? JsonArray)?.mapNotNull { it.asStringOrNull() }.orEmpty(),
            )
        }.orEmpty()
        return PollDetail(
            poll = poll,
            serverNow = body["now"].asLongOrNull() ?: 0L,
            closed = body["closed"].asBooleanOrNull() ?: false,
            total = body["total"].asIntOrNull() ?: 0,
            counts = parseCounts(body["counts"]),
            hasVoted = body["has_voted"].asBooleanOrNull() ?: false,
            myChoices = (body["my_choices"] as? JsonArray)?.mapNotNull { it.asStringOrNull() }.orEmpty(),
            resultsHidden = body["results_hidden"].asBooleanOrNull() ?: false,
            mine = body["mine"].asBooleanOrNull() ?: false,
            canManage = body["can_manage"].asBooleanOrNull() ?: false,
            voters = voters,
        )
    }

    fun parseSummary(obj: JsonObject?): PollSummary? {
        if (obj == null) return null
        val id = obj["id"].asStringOrNull()?.trim().orEmpty()
        if (id.isEmpty()) return null
        return PollSummary(
            id = id,
            title = obj["title"].asStringOrNull().orEmpty(),
            description = obj["description"].asStringOrNull().orEmpty(),
            mode = PollMode.fromWire(obj["mode"].asStringOrNull()),
            ownerName = obj["owner_name"].asStringOrNull().orEmpty(),
            settings = parseSettings(obj["settings"].asObjectOrNull()),
            deadline = obj["deadline"].asLongOrNull() ?: 0L,
            status = obj["status"].asStringOrNull().orEmpty().ifBlank { "open" },
            closedAt = obj["closed_at"].asLongOrNull() ?: 0L,
            createdAt = obj["created_at"].asLongOrNull() ?: 0L,
            updatedAt = obj["updated_at"].asLongOrNull() ?: 0L,
            audience = PollAudience.fromWire(obj["audience"].asStringOrNull()),
            audienceCount = obj["audience_count"].asIntOrNull() ?: 0,
            closed = obj["closed"].asBooleanOrNull() ?: false,
            total = obj["total"].asIntOrNull() ?: 0,
            counts = parseCounts(obj["counts"]),
            hasVoted = obj["has_voted"].asBooleanOrNull() ?: false,
            myChoices = (obj["my_choices"] as? JsonArray)?.mapNotNull { it.asStringOrNull() }.orEmpty(),
            resultsHidden = obj["results_hidden"].asBooleanOrNull() ?: false,
            mine = obj["mine"].asBooleanOrNull() ?: false,
            canManage = obj["can_manage"].asBooleanOrNull() ?: false,
        )
    }

    fun parsePoll(obj: JsonObject?): Poll {
        if (obj == null) return Poll()
        return Poll(
            id = obj["id"].asStringOrNull().orEmpty(),
            title = obj["title"].asStringOrNull().orEmpty(),
            description = obj["description"].asStringOrNull().orEmpty(),
            mode = PollMode.fromWire(obj["mode"].asStringOrNull()),
            ownerName = obj["owner_name"].asStringOrNull().orEmpty(),
            settings = parseSettings(obj["settings"].asObjectOrNull()),
            options = (obj["options"] as? JsonArray)?.mapNotNull { element ->
                val option = element.asObjectOrNull() ?: return@mapNotNull null
                val optionId = option["id"].asStringOrNull()?.trim().orEmpty()
                if (optionId.isEmpty()) return@mapNotNull null
                PollOption(
                    id = optionId,
                    text = option["text"].asStringOrNull().orEmpty(),
                    desc = option["desc"].asStringOrNull().orEmpty(),
                    img = option["img"].asStringOrNull().orEmpty(),
                )
            }.orEmpty(),
            deadline = obj["deadline"].asLongOrNull() ?: 0L,
            status = obj["status"].asStringOrNull().orEmpty().ifBlank { "open" },
            closedAt = obj["closed_at"].asLongOrNull() ?: 0L,
            createdAt = obj["created_at"].asLongOrNull() ?: 0L,
            updatedAt = obj["updated_at"].asLongOrNull() ?: 0L,
            audience = PollAudience.fromWire(obj["audience"].asStringOrNull()),
            audienceCount = obj["audience_count"].asIntOrNull() ?: 0,
            audienceSeats = (obj["audience_seats"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }
                .orEmpty(),
        )
    }

    fun parseSettings(obj: JsonObject?): PollSettings {
        if (obj == null) return PollSettings()
        return PollSettings(
            multi = obj["multi"].asBooleanOrNull() ?: false,
            maxChoices = obj["maxChoices"].asIntOrNull() ?: 1,
            anonymous = obj["anonymous"].asBooleanOrNull() ?: true,
            allowChange = obj["allowChange"].asBooleanOrNull() ?: true,
            resultVisibility = PollResultVisibility.fromWire(obj["resultVisibility"].asStringOrNull()),
        )
    }

    fun parseCounts(element: JsonElement?): Map<String, Int> {
        val obj = element.asObjectOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, Int>()
        obj.forEach { (key, value) ->
            val count = (value as? JsonPrimitive)?.content?.toIntOrNull() ?: return@forEach
            out[key] = count
        }
        return out
    }

    fun isDeleted(body: JsonObject): Boolean = body["deleted"].asBooleanOrNull() == true
}

/**
 * 默认实现。
 *
 * @param currentSid 当前身份；为空时所有请求直接返回 [VoteError.IdentityInvalid]（避免无 token 请求）
 */
class DefaultVoteRepository(
    private val api: ApiClient,
    private val currentSid: () -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val json: Json = NetJson,
    private val listTtlMs: Long = DEFAULT_TTL_MS,
) : VoteRepository {

    private var cacheSid: String? = null
    private var cachedList: PollList? = null
    private var listFetchedAt: Long = 0L
    private val detailCache = LinkedHashMap<String, Pair<PollDetail, Long>>()

    override suspend fun list(force: Boolean): VoteResult<PollList> {
        val sid = sidOrNull() ?: return VoteResult.Failure(VoteError.IdentityInvalid)
        syncSid(sid)
        val now = clock()
        val cached = cachedList
        if (!force && cached != null && now - listFetchedAt < listTtlMs) {
            return VoteResult.Success(cached.copy(fromCache = true))
        }
        return when (val result = post(LIST_PATH, VoteRpcBody.listBody())) {
            is VoteResult.Failure -> fallbackList(result.error)
            is VoteResult.Success -> {
                val parsed = VoteParser.parseList(result.value) ?: return fallbackList(VoteError.Validation("投票数据格式异常"))
                cachedList = parsed
                listFetchedAt = now
                VoteResult.Success(parsed)
            }
        }
    }

    override suspend fun detail(pollId: String, force: Boolean): VoteResult<PollDetail> {
        val sid = sidOrNull() ?: return VoteResult.Failure(VoteError.IdentityInvalid)
        syncSid(sid)
        val id = pollId.trim()
        if (id.isEmpty()) return VoteResult.Failure(VoteError.NotFound)
        val now = clock()
        if (!force) {
            detailCache[id]?.takeIf { now - it.second < listTtlMs }?.let {
                return VoteResult.Success(it.first)
            }
        }
        return when (val result = post(DETAIL_PATH, VoteRpcBody.detailBody(id))) {
            is VoteResult.Failure -> fallbackDetail(id, result.error)
            is VoteResult.Success -> {
                val parsed = VoteParser.parseDetail(result.value)
                detailCache[id] = parsed to now
                VoteResult.Success(parsed)
            }
        }
    }

    override suspend fun create(draft: PollDraft): VoteResult<PollDetail> {
        val sid = sidOrNull() ?: return VoteResult.Failure(VoteError.IdentityInvalid)
        syncSid(sid)
        val errors = VoteLogic.validateDraft(draft, clock())
        if (errors.isNotEmpty()) {
            return VoteResult.Failure(VoteError.Client(errors.first().code, errors.first().message))
        }
        val now = clock()
        val prepared = VoteLogic.prepareDraft(draft, now)
        return when (val result = post(CREATE_PATH, VoteRpcBody.createBody(prepared))) {
            is VoteResult.Failure -> VoteResult.Failure(result.error)
            is VoteResult.Success -> {
                val parsed = VoteParser.parseDetail(result.value)
                cacheDetail(parsed)
                VoteResult.Success(parsed)
            }
        }
    }

    override suspend fun submit(pollId: String, choices: List<String>): VoteResult<PollDetail> {
        val sid = sidOrNull() ?: return VoteResult.Failure(VoteError.IdentityInvalid)
        syncSid(sid)
        val id = pollId.trim()
        if (id.isEmpty()) return VoteResult.Failure(VoteError.NotFound)
        if (choices.isEmpty()) {
            return VoteResult.Failure(VoteError.Client("EMPTY", "请选择至少 1 项"))
        }
        if (choices.size != choices.distinct().size) {
            return VoteResult.Failure(VoteError.DuplicateChoices)
        }
        return when (val result = post(SUBMIT_PATH, VoteRpcBody.submitBody(id, choices))) {
            is VoteResult.Failure -> VoteResult.Failure(result.error)
            is VoteResult.Success -> {
                val parsed = VoteParser.parseDetail(result.value)
                cacheDetail(parsed)
                VoteResult.Success(parsed)
            }
        }
    }

    override suspend fun manage(pollId: String, action: PollManageAction): VoteResult<PollManageResult> {
        val sid = sidOrNull() ?: return VoteResult.Failure(VoteError.IdentityInvalid)
        syncSid(sid)
        val id = pollId.trim()
        if (id.isEmpty()) return VoteResult.Failure(VoteError.NotFound)
        return when (val result = post(MANAGE_PATH, VoteRpcBody.manageBody(id, action))) {
            is VoteResult.Failure -> VoteResult.Failure(result.error)
            is VoteResult.Success -> {
                if (VoteParser.isDeleted(result.value)) {
                    detailCache.remove(id)
                    VoteResult.Success(PollManageResult.Deleted(id))
                } else {
                    val parsed = VoteParser.parseDetail(result.value)
                    cacheDetail(parsed)
                    VoteResult.Success(PollManageResult.Updated(parsed))
                }
            }
        }
    }

    // ------------------------------------------------------------ 内部

    private fun sidOrNull(): String? = currentSid()?.trim()?.takeIf { it.isNotEmpty() }

    private fun syncSid(sid: String) {
        if (cacheSid != sid) {
            cacheSid = sid
            cachedList = null
            listFetchedAt = 0L
            detailCache.clear()
        }
    }

    private fun cacheDetail(detail: PollDetail) {
        if (detail.poll.id.isNotBlank()) {
            detailCache[detail.poll.id] = detail to clock()
        }
    }

    private fun fallbackList(error: VoteError): VoteResult<PollList> {
        val cached = cachedList
        return if (cached != null) {
            VoteResult.Success(cached.copy(fromCache = true, warning = error.message))
        } else {
            VoteResult.Failure(error)
        }
    }

    private fun fallbackDetail(id: String, error: VoteError): VoteResult<PollDetail> {
        val cached = detailCache[id]?.first
        return if (cached != null) {
            VoteResult.Success(cached)
        } else {
            VoteResult.Failure(error)
        }
    }

    private suspend fun post(path: String, body: String): VoteResult<JsonObject> {
        return when (val result = api.post(path, body)) {
            is ApiResult.Failure -> VoteResult.Failure(VoteErrors.fromApiError(result.error))
            is ApiResult.Success -> when (val envelope = VoteParser.envelope(result.value, json)) {
                is VoteParser.Envelope.Ok -> VoteResult.Success(envelope.body)
                is VoteParser.Envelope.Err -> VoteResult.Failure(VoteErrors.fromReason(envelope.reason))
                VoteParser.Envelope.Malformed -> VoteResult.Failure(VoteError.Validation("投票数据格式异常"))
            }
        }
    }

    companion object {
        const val LIST_PATH: String = "rpc/poll_list"
        const val DETAIL_PATH: String = "rpc/poll_detail"
        const val CREATE_PATH: String = "rpc/poll_create"
        const val SUBMIT_PATH: String = "rpc/poll_submit"
        const val MANAGE_PATH: String = "rpc/poll_manage"

        const val DEFAULT_TTL_MS: Long = 15_000L
    }
}
