package com.furina.classtools.features.feedback

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 反馈建议（对齐小程序 `pages/fb` / 契约 `docs/api/ENDPOINTS.md` E19/E20/E21）。
 *
 * 云端表 `public.feedback`：`{id, content, contact, acc, done, ts}`
 * - 普通用户 RLS 只能读自己的行；管理员可读全部、可 PATCH `done=true`；
 * - `contact` 列保留兼容旧数据，**新版两端提交固定空串（不收集联系方式）**；
 * - `acc` 存学号（服务端归属字段，页面不显示学号，管理员列表用姓名映射）。
 */
@Serializable
data class FeedbackRow(
    val id: String,
    val content: String = "",
    /** 兼容旧数据；新提交恒为空串，页面不展示。 */
    val contact: String = "",
    /** 提交人学号（服务端 RLS 归属字段；UI 不直接展示）。 */
    val acc: String = "",
    val done: Boolean = false,
    val ts: Long = 0L,
)

/** 反馈加载结果（失败时若有内存缓存则降级）。 */
sealed interface FeedbackLoadResult {
    data class Success(
        val rows: List<FeedbackRow>,
        val fromCache: Boolean = false,
    ) : FeedbackLoadResult

    data class Failure(val message: String) : FeedbackLoadResult
}

/** 提交结果。 */
sealed interface FeedbackSubmitResult {
    data class Success(val row: FeedbackRow) : FeedbackSubmitResult
    data class Failure(val message: String) : FeedbackSubmitResult
}

/** 管理员标记已处理结果。 */
sealed interface FeedbackDoneResult {
    data object Success : FeedbackDoneResult
    data class Failure(val message: String) : FeedbackDoneResult
}

/** 反馈列表筛选（与 mp 一致：普通用户只有「我的反馈」，管理员三档）。 */
enum class FeedbackFilter(val label: String) {
    MINE("我的反馈"),
    ALL("全部"),
    TODO("未处理"),
    DONE("已处理"),
    ;

    companion object {
        val adminFilters: List<FeedbackFilter> = listOf(ALL, TODO, DONE)
        val userFilters: List<FeedbackFilter> = listOf(MINE)
    }
}

/** 反馈纯逻辑：校验、ID 生成、筛选、作者名回退。 */
object FeedbackRules {

    /** 与 mp 一致：正文非空、最多 500 字（`textarea maxlength=500`）。 */
    const val MAX_CONTENT_LENGTH: Int = 500

    /** 校验提交内容；返回 null 表示合法，否则为可直接展示的错误文案。 */
    fun validateContent(raw: String): String? {
        val text = raw.trim()
        return when {
            text.isEmpty() -> "写点反馈内容"
            text.length > MAX_CONTENT_LENGTH -> "反馈内容最多 $MAX_CONTENT_LENGTH 字"
            else -> null
        }
    }

    /** 规范化正文（提交前）与 mp 一致：trim。 */
    fun normalizeContent(raw: String): String = raw.trim()

    /**
     * 生成反馈 id：与 mp `cbId()` 同风格（`base36(时间戳) + base36 随机`），
     * 便于跨端按 id 合并/标记；可注入 now/random 便于测试。
     */
    fun newId(nowMillis: Long, randomSuffix: String = randomBase36()): String =
        nowMillis.toString(36) + randomSuffix.take(6).ifBlank { randomBase36() }

    /** 6 位 base36 随机串（不用于安全场景，仅 id 去重）。 */
    fun randomBase36(): String {
        val alphabet = "0123456789abcdefghijklmnopqrstuvwxyz"
        val random = java.security.SecureRandom()
        return (1..6).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    /** 过滤：普通用户只看自己（`acc == sid`）；管理员按档位。 */
    fun filterRows(
        rows: List<FeedbackRow>,
        sid: String?,
        filter: FeedbackFilter,
        isAdmin: Boolean,
    ): List<FeedbackRow> {
        val mine = rows.filter { it.acc.trim() == sid?.trim().orEmpty() && it.acc.isNotBlank() }
        if (!isAdmin) return mine
        return when (filter) {
            FeedbackFilter.ALL -> rows
            FeedbackFilter.TODO -> rows.filterNot { it.done }
            FeedbackFilter.DONE -> rows.filter { it.done }
            FeedbackFilter.MINE -> mine
        }
    }

    /** 列表作者展示：普通用户自己的行显示「我」；管理员用姓名映射，查不到回退 mp 文案。 */
    fun displayAuthor(
        row: FeedbackRow,
        sid: String?,
        isAdmin: Boolean,
        nameOfSid: (String) -> String? = { null },
    ): String {
        val mine = row.acc.trim().isNotEmpty() && row.acc.trim() == sid?.trim().orEmpty()
        if (!isAdmin) return "我"
        if (mine) return nameOfSid(row.acc)?.takeIf { it.isNotBlank() } ?: "我"
        return nameOfSid(row.acc)?.takeIf { it.isNotBlank() } ?: "（未知成员）"
    }

    /** `yyyy-MM-dd HH:mm`（与 mp `fmt` 一致，本地时区）。 */
    fun formatTime(ts: Long): String {
        if (ts <= 0L) return ""
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
            .format(java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()))
    }

    /** 管理员副标题：`共 N 条 · 可标记已处理`；普通用户固定文案。 */
    fun subtitle(total: Int, isAdmin: Boolean): String =
        if (isAdmin) "共 $total 条 · 可标记已处理" else "把建议或问题发给管理员"
}
