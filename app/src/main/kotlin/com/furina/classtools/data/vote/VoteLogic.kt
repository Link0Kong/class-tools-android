package com.furina.classtools.data.vote

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 倒计时状态（UI 直接渲染）。 */
sealed interface PollCountdown {
    /** `deadline=0` 且未提前结束：不限时。 */
    data object Unlimited : PollCountdown

    data object Closed : PollCountdown

    data class Remaining(val millis: Long, val text: String) : PollCountdown
}

/**
 * 投票纯逻辑（无 Android/网络依赖，可单测）。
 *
 * 口径来源：`web/vote/CONTRACT.md`（§1.4/§1.5/§1.6/§2/§3）。
 * 注：最终校验以服务端为准；本类用于客户端预校验、展示与交互状态。
 */
object VoteLogic {

    const val POLL_ID_PATTERN: String = "^p[a-z0-9]{4,32}$"

    private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)
    private val HH_MM_SS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.CHINA)

    // ------------------------------------------------------------ 时间 / 截止

    /**
     * 是否已结束：优先使用服务端 `closed` 结果（`poll_list`/`poll_detail` 的 `closed`），
     * 否则按 `status=closed` 或 `deadline>0 && now>=deadline` 本地判定。
     */
    fun effectiveClosed(
        status: String?,
        deadline: Long,
        nowMillis: Long,
        serverClosed: Boolean? = null,
    ): Boolean = serverClosed ?: (status?.trim()?.lowercase() == "closed" || (deadline > 0 && nowMillis >= deadline))

    /** 倒计时（以本地时钟显示；提交时以服务端为准）。 */
    fun countdown(
        closed: Boolean,
        deadline: Long,
        nowMillis: Long,
    ): PollCountdown = when {
        closed -> PollCountdown.Closed
        deadline <= 0L -> PollCountdown.Unlimited
        else -> {
            val remain = deadline - nowMillis
            if (remain <= 0L) {
                PollCountdown.Closed
            } else {
                PollCountdown.Remaining(remain, formatRemaining(remain))
            }
        }
    }

    /** `剩 hh:mm:ss` / `剩 mm:ss`（契约 §3.4）。 */
    fun formatRemaining(millis: Long): String {
        if (millis <= 0L) return "已截止"
        val totalSeconds = (millis + 999) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "剩 ${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "剩 ${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        }
    }

    /** 列表/详情的时间标签：不限时 / 已截止 / 剩 xx / 截止 MM-dd HH:mm。 */
    fun deadlineLabel(
        closed: Boolean,
        deadline: Long,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = when {
        closed -> "已结束"
        deadline <= 0L -> "不限时"
        deadline - nowMillis <= 0L -> "已结束"
        deadline - nowMillis <= 24L * 60 * 60 * 1000 -> formatRemaining(deadline - nowMillis)
        else -> "截止 " + HH_MM.format(Instant.ofEpochMilli(deadline).atZone(zone))
    }

    /** 快捷限时：`now + minutes`。 */
    fun quickDeadline(nowMillis: Long, minutes: Int): Long =
        nowMillis + minutes.coerceIn(1, 7 * 24 * 60).toLong() * 60_000L

    // ------------------------------------------------------------ 可见性 / 权限

    /** 结果是否可见（契约 §1.4：always / 投票后 / 截止后）。 */
    fun resultsVisible(settings: PollSettings, closed: Boolean, hasVoted: Boolean): Boolean =
        when (settings.resultVisibility) {
            PollResultVisibility.ALWAYS -> true
            PollResultVisibility.AFTER_VOTE -> closed || hasVoted
            PollResultVisibility.AFTER_CLOSE -> closed
        }

    fun canManage(mine: Boolean, isAdmin: Boolean): Boolean = mine || isAdmin

    /**
     * 客户端「能否投票/改票」判定（返回 null 表示可投；否则为提示文案）。
     *
     * @param inAudience 是否在可见范围内（客户端只能近似：服务端范围外会直接隐藏投票；
     *   管理员不在范围时服务端返回「不在可见范围内」）。
     */
    fun voteBlockReason(
        closed: Boolean,
        settings: PollSettings,
        hasVoted: Boolean,
        inAudience: Boolean = true,
    ): String? = when {
        !inAudience -> "不在可见范围内"
        closed -> "投票已结束"
        hasVoted && !settings.allowChange -> "已投过票，不可改票"
        else -> null
    }

    fun canChangeVote(settings: PollSettings, closed: Boolean, hasVoted: Boolean): Boolean =
        hasVoted && settings.allowChange && !closed

    // ------------------------------------------------------------ 选项校验

    /**
     * 提交选项校验（客户端预校验）：与契约 §2.3 的 reason 顺序尽量一致。
     * @return null = 合法；否则为明确错误
     */
    fun validateChoices(
        settings: PollSettings,
        choices: List<String>,
        optionIds: Collection<String>,
    ): VoteError? {
        val validIds = optionIds.toSet()
        if (choices.isEmpty()) return VoteError.Client("EMPTY", "请选择至少 1 项")
        if (choices.size != choices.distinct().size) return VoteError.DuplicateChoices
        if (choices.any { it !in validIds }) return VoteError.InvalidOptions
        if (!settings.multi && choices.size > 1) return VoteError.SingleChoiceOnly
        if (settings.multi && choices.size > settings.maxChoices) {
            return VoteError.LimitExceeded(settings.maxChoices)
        }
        return null
    }

    // ------------------------------------------------------------ 草稿校验 / 归一

    fun isPollIdValid(id: String): Boolean =
        id.isNotBlank() && id.length in 5..33 && Regex(POLL_ID_PATTERN).matches(id)

    /** 发起/编辑草稿校验；返回空列表 = 可提交。 */
    fun validateDraft(draft: PollDraft, nowMillis: Long): List<VoteValidationError> {
        val errors = mutableListOf<VoteValidationError>()
        val title = draft.title.trim()
        if (title.length !in 1..VoteLimits.MAX_TITLE) {
            errors += VoteValidationError(VoteField.TITLE, "TITLE_LENGTH", "标题需 1–80 字")
        }
        if (draft.description.trim().length > VoteLimits.MAX_DESCRIPTION) {
            errors += VoteValidationError(
                VoteField.DESCRIPTION,
                "DESCRIPTION_LENGTH",
                "说明不能超过 500 字",
            )
        }
        if (draft.id.isNotBlank() && !isPollIdValid(draft.id)) {
            errors += VoteValidationError(VoteField.TITLE, "POLL_ID", "投票编号不合法")
        }
        val options = draft.options
        if (options.size !in VoteLimits.MIN_OPTIONS..VoteLimits.MAX_OPTIONS) {
            errors += VoteValidationError(VoteField.OPTIONS, "OPTIONS_COUNT", "选项需 2–20 个")
        }
        var totalImg = 0
        options.forEachIndexed { index, option ->
            val text = option.text.trim()
            if (text.length !in 1..VoteLimits.MAX_OPTION_TEXT) {
                errors += VoteValidationError(
                    VoteField.OPTION_TEXT,
                    "OPTION_TEXT_${index}",
                    "第 ${index + 1} 个选项文字需 1–80 字",
                )
            }
            if (option.desc.trim().length > VoteLimits.MAX_OPTION_DESC) {
                errors += VoteValidationError(
                    VoteField.OPTION_DESC,
                    "OPTION_DESC_$index",
                    "第 ${index + 1} 个选项说明不能超过 200 字",
                )
            }
            val img = option.img.trim()
            if (img.isNotEmpty()) {
                if (!img.startsWith("data:image/")) {
                    errors += VoteValidationError(
                        VoteField.OPTION_IMAGE,
                        "OPTION_IMAGE_$index",
                        "第 ${index + 1} 个选项图片格式不合法",
                    )
                } else if (img.length > VoteLimits.MAX_OPTION_IMAGE_CHARS) {
                    errors += VoteValidationError(
                        VoteField.OPTION_IMAGE,
                        "OPTION_IMAGE_SIZE_$index",
                        "第 ${index + 1} 个选项图片过大",
                    )
                }
                totalImg += img.length
            }
        }
        if (totalImg > VoteLimits.MAX_TOTAL_IMAGE_CHARS) {
            errors += VoteValidationError(VoteField.OPTION_IMAGE, "OPTION_IMAGE_TOTAL", "选项图片总大小过大")
        }
        val settings = draft.settings.normalized(options.size)
        if (settings.multi) {
            if (draft.settings.maxChoices < VoteLimits.MIN_CHOICES || draft.settings.maxChoices > VoteLimits.MAX_CHOICES) {
                errors += VoteValidationError(VoteField.SETTINGS, "MAX_CHOICES", "多选需允许至少选 2 项")
            }
            if (draft.settings.maxChoices > options.size) {
                errors += VoteValidationError(VoteField.SETTINGS, "MAX_CHOICES_OVER", "最多选择数不能超过选项数")
            }
        }
        if (draft.deadline < 0L) {
            errors += VoteValidationError(VoteField.DEADLINE, "DEADLINE_NEGATIVE", "截止时间不合法")
        } else if (draft.deadline > 0L && draft.deadline <= nowMillis) {
            errors += VoteValidationError(VoteField.DEADLINE, "DEADLINE_PAST", "截止时间需晚于当前时间")
        }
        when (draft.audience) {
            PollAudience.CUSTOM -> {
                val seats = draft.audienceSeats
                if (seats.isEmpty()) {
                    errors += VoteValidationError(VoteField.AUDIENCE, "AUDIENCE_EMPTY", "请选择可见成员")
                }
                if (seats.any { it !in 1..VoteLimits.MAX_SEAT }) {
                    errors += VoteValidationError(VoteField.AUDIENCE, "AUDIENCE_SEAT", "可见成员座号不合法")
                }
                if (seats.size != seats.distinct().size) {
                    errors += VoteValidationError(VoteField.AUDIENCE, "AUDIENCE_DUP", "可见成员重复")
                }
            }

            else -> {
                if (draft.audienceSeats.isNotEmpty()) {
                    errors += VoteValidationError(VoteField.AUDIENCE, "AUDIENCE_SEATS", "仅指定成员需要座号")
                }
            }
        }
        return errors
    }

    /**
     * 为一次「发起」准备稳定的草稿：补全投票 id 与选项 id。
     *
     * 契约 `poll_create` 按 `p_id` 幂等——网络失败后用户重试时，应复用同一份 prepared draft
     * （否则每次重试生成新 id，可能产生重复投票）。UI 建议：打开发起弹层时 `prepareDraft` 一次，
     * 提交失败重试直接复用；成功后清空。
     */
    fun prepareDraft(
        draft: PollDraft,
        nowMillis: Long = System.currentTimeMillis(),
    ): PollDraft {
        val normalized = normalizeDraft(draft)
        return normalized.copy(
            id = normalized.id.takeIf { isPollIdValid(it) } ?: newPollId(nowMillis),
            options = normalized.options.mapIndexed { index, option ->
                option.copy(id = option.id.takeIf { it.isNotBlank() } ?: newOptionId(nowMillis, index))
            },
        )
    }
    /** 客户端归一（服务端仍复核）：trim、选项 id 补全、settings/座号归一。 */
    fun normalizeDraft(draft: PollDraft): PollDraft {
        val options = draft.options.mapIndexed { index, option ->
            PollOptionDraft(
                id = option.id.trim().ifBlank { "o${index + 1}" },
                text = option.text.trim(),
                desc = option.desc.trim(),
                img = option.img.trim(),
            )
        }
        return draft.copy(
            title = draft.title.trim(),
            description = draft.description.trim(),
            options = options,
            settings = draft.settings.normalized(options.size),
            deadline = draft.deadline.coerceAtLeast(0L),
            audienceSeats = if (draft.audience == PollAudience.CUSTOM) {
                draft.audienceSeats.filter { it in 1..VoteLimits.MAX_SEAT }.distinct().sorted()
            } else {
                emptyList()
            },
        )
    }

    // ------------------------------------------------------------ 列表排序 / 标签

    /** 进行中：按截止时间升序（不限时排最后），同截止按创建时间倒序。 */
    fun sortOpen(polls: List<PollSummary>): List<PollSummary> = polls
        .filterNot { it.closed }
        .sortedWith(
            compareBy<PollSummary> { if (it.deadline > 0L) 0 else 1 }
                .thenBy { if (it.deadline > 0L) it.deadline else Long.MAX_VALUE }
                .thenByDescending { it.createdAt },
        )

    /** 已结束：按创建时间倒序。 */
    fun sortClosed(polls: List<PollSummary>): List<PollSummary> = polls
        .filter { it.closed }
        .sortedByDescending { it.createdAt }

    fun filterForTab(polls: List<PollSummary>, showClosed: Boolean): List<PollSummary> =
        if (showClosed) sortClosed(polls) else sortOpen(polls)

    /** 可见范围标签（all 不显示；custom 用座号数）。 */
    fun audienceTag(audience: PollAudience, audienceCount: Int = 0): String? = when (audience) {
        PollAudience.ALL -> null
        PollAudience.COMMITTEE -> "班委可见"
        PollAudience.LEAGUE -> "团员可见"
        PollAudience.CUSTOM -> "指定 ${audienceCount.coerceAtLeast(0)} 人"
    }

    /** 结果可见标签（结果可见时不显示）。 */
    fun resultHiddenTag(settings: PollSettings): String? =
        if (settings.resultVisibility == PollResultVisibility.ALWAYS) null else settings.resultVisibility.label

    fun percent(count: Int, total: Int): Double =
        if (total <= 0) 0.0 else count.toDouble() / total.toDouble() * 100.0

    fun formatPercent(value: Double): String =
        if (!value.isFinite()) "0%" else String.format(Locale.CHINA, "%.1f%%", value)

    /** 相对上一周期的百分比文案（如 `+12.3%` / `-4.5%` / `0%`）。 */
    fun formatDeltaPercent(value: Double): String {
        if (!value.isFinite()) return "0%"
        val rounded = String.format(Locale.CHINA, "%.1f", value)
        val prefix = if (value > 0.05) "+" else ""
        return "$prefix$rounded%"
    }

    // ------------------------------------------------------------ ID

    /** 投票 id：`p` + base36(时间) + 随机后缀（服务端要求 `[a-z0-9]{4,32}`）。 */
    fun newPollId(nowMillis: Long, randomSuffix: String = randomBase36()): String {
        val base = "p" + nowMillis.toString(36) + randomSuffix.lowercase().filter { it.isLetterOrDigit() }.take(6)
        return base.take(33)
    }

    /** 选项 id：`o` + base36(时间) + 序号 + 随机后缀（≤24）。 */
    fun newOptionId(nowMillis: Long, index: Int, randomSuffix: String = randomBase36()): String {
        val tail = randomSuffix.lowercase().filter { it.isLetterOrDigit() }.take(4)
        return ("o" + nowMillis.toString(36) + (index + 1) + tail).take(24)
    }

    fun randomBase36(): String {
        val alphabet = "0123456789abcdefghijklmnopqrstuvwxyz"
        val random = java.security.SecureRandom()
        return (1..6).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    /** 供 UI 展示的时间（`MM-dd HH:mm`）。 */
    fun formatAt(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        if (millis <= 0L) "" else HH_MM.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** 供 UI 展示的日期时间（`MM-dd HH:mm:ss`，诊断/详情用）。 */
    fun formatAtSeconds(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        HH_MM_SS.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** 服务端时间缺失时用本机时钟兜底。 */
    fun bestNow(serverNow: Long, localNow: Long = System.currentTimeMillis()): Long =
        if (serverNow > 0L) serverNow else localNow

    /** 本地时区当天 0 点（供 UI 计算「今日限时」等）。 */
    fun startOfDay(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    @Suppress("unused")
    private val unusedLocalDateTime = LocalDateTime.MIN
}
