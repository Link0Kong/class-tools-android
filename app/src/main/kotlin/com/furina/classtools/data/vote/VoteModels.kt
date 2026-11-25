package com.furina.classtools.data.vote

/**
 * 投票领域模型（对齐 `web/vote/CONTRACT.md`，2026-11-11 版含可见人范围）。
 *
 * 隐私红线：**所有模型都不包含 sid/学号**；指定成员范围只用座号（[Poll.audienceSeats]）表示，
 * 投票人只返回姓名（匿名投票连姓名也不返回）。
 */

/** 投票模式（仅 UI 提示；deadline=0 时显示「不限时」）。 */
enum class PollMode(val wire: String, val label: String) {
    QUICK("quick", "限时"),
    LONG("long", "长时"),
    ;

    companion object {
        fun fromWire(raw: String?): PollMode =
            entries.firstOrNull { it.wire == raw?.trim() } ?: LONG
    }
}

/** 结果可见性（契约 §1.4）。 */
enum class PollResultVisibility(val wire: String, val label: String) {
    ALWAYS("always", "结果始终可见"),
    AFTER_VOTE("afterVote", "投票后可见"),
    AFTER_CLOSE("afterClose", "截止后可见"),
    ;

    companion object {
        fun fromWire(raw: String?): PollResultVisibility =
            entries.firstOrNull { it.wire == raw?.trim() } ?: AFTER_VOTE
    }
}

/** 可见人范围（契约 §1.6）。 */
enum class PollAudience(val wire: String, val label: String) {
    ALL("all", "全班"),
    COMMITTEE("committee", "班委"),
    LEAGUE("league", "团员"),
    CUSTOM("custom", "指定成员"),
    ;

    companion object {
        fun fromWire(raw: String?): PollAudience =
            entries.firstOrNull { it.wire == raw?.trim() } ?: ALL
    }
}

/** 投票规则（服务端归一后的 canonical settings）。 */
data class PollSettings(
    val multi: Boolean = false,
    /** 单选恒为 1；多选 2..20 且 ≤ 选项数。 */
    val maxChoices: Int = 1,
    val anonymous: Boolean = true,
    val allowChange: Boolean = true,
    val resultVisibility: PollResultVisibility = PollResultVisibility.AFTER_VOTE,
) {
    /** 客户端归一（服务端仍会复核）：单选 maxChoices=1；多选夹在 2..min(20, optionCount)。 */
    fun normalized(optionCount: Int): PollSettings {
        val count = optionCount.coerceIn(0, VoteLimits.MAX_OPTIONS)
        val max = if (!multi) {
            1
        } else {
            maxChoices.coerceIn(2, minOf(VoteLimits.MAX_CHOICES, count.coerceAtLeast(2)))
        }
        return copy(multi = multi && count >= 2, maxChoices = max)
    }

    /** 规则标签（列表/详情用；不含可见范围）。 */
    fun tags(hasVoted: Boolean = false, closed: Boolean = false): List<String> = buildList {
        add(if (multi) "多选" else "单选")
        if (multi) add("最多 $maxChoices 项")
        add(if (anonymous) "匿名" else "实名")
        if (allowChange && !closed) add("可改票")
        if (!VoteLogic.resultsVisible(this@PollSettings, closed, hasVoted)) {
            add(resultVisibility.label)
        }
    }
}

/** 一个选项（`img` 为可选 data URL；服务端校验 data:image/ 与大小）。 */
data class PollOption(
    val id: String = "",
    val text: String = "",
    val desc: String = "",
    val img: String = "",
)

/** 投票人（仅实名且结果可见时返回；无 sid）。 */
data class PollVoter(
    val name: String = "",
    val choices: List<String> = emptyList(),
)

/**
 * 投票完整信息（详情用；列表摘要见 [PollSummary]）。
 *
 * @param audienceSeats 指定成员**座号**（仅发起人/管理员可见；其他人恒空）
 */
data class Poll(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val mode: PollMode = PollMode.LONG,
    val ownerName: String = "",
    val settings: PollSettings = PollSettings(),
    val options: List<PollOption> = emptyList(),
    val deadline: Long = 0L,
    val status: String = "open",
    val closedAt: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val audience: PollAudience = PollAudience.ALL,
    /** custom 时的座号数（所有人可见）。 */
    val audienceCount: Int = 0,
    /** 座号数组（仅 canManage 可见，编辑回填；无学号）。 */
    val audienceSeats: List<Int> = emptyList(),
) {
    val isClosedStatus: Boolean get() = status.trim().lowercase() == "closed"
}

/** 列表页摘要（poll_list 返回；无 options/audienceSeats）。 */
data class PollSummary(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val mode: PollMode = PollMode.LONG,
    val ownerName: String = "",
    val settings: PollSettings = PollSettings(),
    val deadline: Long = 0L,
    val status: String = "open",
    val closedAt: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val audience: PollAudience = PollAudience.ALL,
    val audienceCount: Int = 0,
    /** 服务端按 deadline/status 计算后的「已结束」。 */
    val closed: Boolean = false,
    val total: Int = 0,
    /** 选项 id → 票数（结果隐藏时为空）。 */
    val counts: Map<String, Int> = emptyMap(),
    val hasVoted: Boolean = false,
    val myChoices: List<String> = emptyList(),
    val resultsHidden: Boolean = false,
    val mine: Boolean = false,
    val canManage: Boolean = false,
)

/** `poll_list` 结果。 */
data class PollList(
    val polls: List<PollSummary> = emptyList(),
    /** 服务端时间（倒计时基准用）。 */
    val serverNow: Long = 0L,
    /** true = 网络失败时返回本机缓存。 */
    val fromCache: Boolean = false,
    /** 降级提示（UI toast / 状态条）。 */
    val warning: String? = null,
) {
    val openCount: Int get() = polls.count { !it.closed }
    val closedCount: Int get() = polls.count { it.closed }
}

/** 详情 / 提交 / 管理（edit、close、extend）成功返回。 */
data class PollDetail(
    val poll: Poll = Poll(),
    val serverNow: Long = 0L,
    val closed: Boolean = false,
    val total: Int = 0,
    val counts: Map<String, Int> = emptyMap(),
    val hasVoted: Boolean = false,
    val myChoices: List<String> = emptyList(),
    val resultsHidden: Boolean = false,
    val mine: Boolean = false,
    val canManage: Boolean = false,
    val voters: List<PollVoter> = emptyList(),
    /** Elapsed realtime at receipt of serverNow. Cached reads keep the original sample. */
    val receivedAtElapsedMillis: Long? = null,
) {
    fun currentTime(wallNow: Long, elapsedNow: Long): Long {
        val received = receivedAtElapsedMillis ?: return wallNow
        return if (serverNow > 0L) serverNow + (elapsedNow - received).coerceAtLeast(0L) else wallNow
    }

    fun localCountdownDeadline(wallNow: Long, elapsedNow: Long): Long =
        wallNow + (poll.deadline - currentTime(wallNow, elapsedNow))
}

/** 发起/编辑草稿（客户端 → RPC；不含 sid）。 */
data class PollOptionDraft(
    val id: String = "",
    val text: String = "",
    val desc: String = "",
    val img: String = "",
)

data class PollDraft(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val mode: PollMode = PollMode.LONG,
    val options: List<PollOptionDraft> = emptyList(),
    val settings: PollSettings = PollSettings(),
    /** 0 = 不限时；>0 = 截止 epoch ms。 */
    val deadline: Long = 0L,
    val audience: PollAudience = PollAudience.ALL,
    /** custom 时的座号（去重升序，1..70；无学号）。 */
    val audienceSeats: List<Int> = emptyList(),
)

/** 管理动作（契约 §2.4；edit 仅 0 票可改）。 */
sealed interface PollManageAction {
    data object Close : PollManageAction

    data class Extend(val deadline: Long) : PollManageAction

    data object Delete : PollManageAction

    /** 编辑：draft 必须包含 title/options/settings/deadline；audience 不传则保持原范围（旧客户端兼容）。 */
    data class Edit(
        val draft: PollDraft,
        /** false = 不提交可见范围（保持原范围）。 */
        val updateAudience: Boolean = true,
    ) : PollManageAction
}

/** 管理结果（delete → Deleted；其它 → Updated(detail)）。 */
sealed interface PollManageResult {
    data class Updated(val detail: PollDetail) : PollManageResult
    data class Deleted(val pollId: String) : PollManageResult
}

/**
 * 投票错误（sealed + 中文文案）。
 *
 * `fromReason()` 把服务端 `{ok:false,reason}` 映射为明确类型；未知 reason 归入 [Validation]，
 * UI 直接展示 [message]。
 */
sealed interface VoteError {
    val message: String

    data object NotFound : VoteError {
        override val message: String = "投票不存在"
    }

    data object IdentityInvalid : VoteError {
        override val message: String = "身份已失效，请重新进入班级"
    }

    data object OutOfAudience : VoteError {
        override val message: String = "不在可见范围内"
    }

    data object Closed : VoteError {
        override val message: String = "投票已结束"
    }

    data object AlreadyVoted : VoteError {
        override val message: String = "已投过票，不可改票"
    }

    data object NoPermission : VoteError {
        override val message: String = "无权管理该投票"
    }

    data object InvalidOptions : VoteError {
        override val message: String = "选项不合法"
    }

    data class LimitExceeded(val max: Int) : VoteError {
        override val message: String = "最多选 $max 项"
    }

    data object SingleChoiceOnly : VoteError {
        override val message: String = "只能选 1 项"
    }

    data object DuplicateChoices : VoteError {
        override val message: String = "重复的选项"
    }

    data object EditLocked : VoteError {
        override val message: String = "已有投票，不能修改内容"
    }

    /** 服务端校验失败/未知业务原因（reason 原文）。 */
    data class Validation(override val message: String) : VoteError

    /** 网络不可用（可重试）。 */
    data class Network(override val message: String = "网络不可用，请稍后重试") : VoteError

    /** 客户端侧校验（提交前拦截）。 */
    data class Client(val code: String, override val message: String) : VoteError

    data class Unknown(override val message: String = "操作失败，请重试") : VoteError
}

/** 统一结果封装。 */
sealed interface VoteResult<out T> {
    data class Success<T>(val value: T) : VoteResult<T>
    data class Failure(val error: VoteError) : VoteResult<Nothing>
}

/** 校验错误码（UI 用于定位字段/展示）。 */
enum class VoteField(val label: String) {
    TITLE("标题"),
    DESCRIPTION("说明"),
    OPTIONS("选项"),
    OPTION_TEXT("选项文字"),
    OPTION_DESC("选项说明"),
    OPTION_IMAGE("选项配图"),
    SETTINGS("投票规则"),
    DEADLINE("截止时间"),
    AUDIENCE("可见范围"),
}

data class VoteValidationError(
    val field: VoteField,
    val code: String,
    val message: String,
)

/** 契约常量。 */
object VoteLimits {
    const val MAX_TITLE: Int = 80
    const val MAX_DESCRIPTION: Int = 500
    const val MIN_OPTIONS: Int = 2
    const val MAX_OPTIONS: Int = 20
    const val MAX_OPTION_TEXT: Int = 80
    const val MAX_OPTION_DESC: Int = 200
    const val MAX_OPTION_IMAGE_CHARS: Int = 150_000
    const val MAX_TOTAL_IMAGE_CHARS: Int = 1_600_000
    const val MIN_CHOICES: Int = 2
    const val MAX_CHOICES: Int = 20
    const val MAX_SEAT: Int = 70
    const val MAX_LIST: Int = 100

    /** 快捷限时选项（分钟；契约 §3.3）。 */
    val QUICK_MINUTES: List<Int> = listOf(5, 10, 30, 60, 120)
    const val DEFAULT_QUICK_MINUTES: Int = 30
}
