package com.furina.classtools.data.timetable

import kotlinx.serialization.Serializable

/**
 * 课表领域模型（契约：docs/BACKEND_CONTRACT.md §7.4、docs/api/ENDPOINTS.md E24）。
 *
 * 数据源：CloudBase `public.site_assets` 行 `id='tt'` 的 `body`（jsonb 对象或 JSON 字符串），
 * 结构 `{c:[course], t:"YYYY-MM-DD"(开学日), meta:{...}}`。周次/单双周/下一节为客户端纯计算。
 */

/** 一门课（字段与网页/小程序完全一致：day 0=周一，start/end 为节次 1..14）。 */
@Serializable
data class Course(
    val name: String = "",
    val teacher: String = "",
    val room: String = "",
    val day: Int = 0,
    val start: Int = 1,
    val end: Int = 1,
    /** 周次表达式，如 "1-16,18"；空 = 全学期。 */
    val weeks: String = "",
    /** all | odd | even（单双周）。 */
    val parity: String = "all",
)

/** 快照元信息（教务同步产物；缺省字段容忍缺失）。 */
@Serializable
data class TtMeta(
    val source: String = "",
    val fetchedAt: Long = 0L,
    val count: Int = 0,
    val termStart: String = "",
    val syncedAt: String = "",
)

/** 课表快照：`c` 课程数组 + `t` 开学日 + 可选 `meta`。 */
@Serializable
data class TtSnapshot(
    val c: List<Course> = emptyList(),
    val t: String = "",
    val meta: TtMeta? = null,
) {
    /** 开学日解析优先级：与网页端一致，课程数据 `t` 优先，meta.termStart 仅兜底。 */
    val termStart: String
        get() = t.takeIf { it.isNotBlank() } ?: meta?.termStart.orEmpty()
}

/** 某节课相对「现在」的状态。 */
enum class SessionState {
    /** 尚未开始。 */
    NEXT,

    /** 进行中。 */
    ONGOING,

    /** 已结束。 */
    DONE,
}

/** 今日课程整体状态（与小程序 plan().todayState 对齐）。 */
enum class TodayState {
    /** 没有课表数据。 */
    NODATA,

    /** 今天没课。 */
    NONE,

    /** 今天有课且还有未结束的。 */
    HAS,

    /** 今天的课都结束了。 */
    DONE,
}

/**
 * 一次课程占用（用于今日课程/下一节/日视图）。
 *
 * @param startAt 开始时刻 epoch ms（本地时区计算）
 * @param endAt 结束时刻 epoch ms
 * @param dayOffset 相对今天的天数（仅「下一节」填充；0=今天，1=明天）
 */
data class SessionInfo(
    val name: String,
    val room: String,
    val start: Int,
    val end: Int,
    val startText: String,
    val endText: String,
    val span: String,
    val startAt: Long,
    val endAt: Long,
    val state: SessionState,
    val dayOffset: Int = 0,
    val dayText: String = "",
    val dateText: String = "",
    val label: String = "",
)

/** 工作台/课表页共用计算产物。 */
data class TtPlan(
    val hasData: Boolean,
    val week: Int?,
    val today: List<SessionInfo>,
    val todayState: TodayState,
    val next: SessionInfo?,
)

/** 课表加载失败的语义分类（UI 据此给出不同文案）。 */
enum class TtFailureKind {
    /** 网络不可用 / 服务端 5xx。 */
    NETWORK,

    /** 未登录、token 失效或云端未配置。 */
    NO_TOKEN,

    /** 请求成功但没有课表数据（空响应 / c 为空 / body 缺失）。 */
    EMPTY,

    /** 响应结构不符合契约（坏 JSON / body 非对象 / c 非数组等）。 */
    FORMAT,
}

/** 课表数据来源（个人教务登录 ttLocal 优先于公共 site_assets 快照；契约 §4）。 */
enum class TtSource {
    /** 个人课表（userdata v6 `tt.ttLocal`，来自「登录教务」）。 */
    PERSONAL,

    /** 公共课表（CloudBase `site_assets?id=eq.tt` 教务快照）。 */
    PUBLIC,

    /** 本机落盘缓存（网络失败兜底）。 */
    CACHE,
}

/** 课表加载结果（失败时 UI 显示错误态；有本机缓存则 fromCache=true）。 */
sealed interface TtLoadResult {
    data class Success(
        val snapshot: TtSnapshot,
        val fromCache: Boolean,
        val fetchedAt: Long,
        /** 数据来源；默认 PUBLIC 保持既有调用方/预览兼容。 */
        val source: TtSource = TtSource.PUBLIC,
    ) : TtLoadResult

    /**
     * 失败：`message` 可直接展示；`kind` 决定标题/重试提示（见 TtFailureKind）。
     * 默认 FORMAT 保持既有调用方兼容。
     */
    data class Failure(
        val message: String,
        val kind: TtFailureKind = TtFailureKind.FORMAT,
    ) : TtLoadResult
}

/**
 * 节次时间表（与小程序 `utils/ttplan.js` PERIODS 逐项一致；12 节固定）。
 * 下标 0 = 第 1 节。
 */
object TtPeriods {
    val PERIODS: List<Pair<String, String>> = listOf(
        "08:10" to "08:55",
        "08:55" to "09:40",
        "09:50" to "10:30",
        "10:40" to "11:25",
        "11:25" to "12:10",
        "14:30" to "15:15",
        "15:15" to "16:00",
        "16:10" to "16:55",
        "16:55" to "17:40",
        "19:00" to "19:45",
        "19:45" to "20:30",
        "20:30" to "21:20",
    )

    const val COUNT: Int = 12

    val DAY_LABELS: List<String> = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    /** 第 1 节开始时间文案（网格左侧标签用）。 */
    fun startText(period: Int): String = PERIODS.getOrNull(period - 1)?.first.orEmpty()

    /** 第 n 节结束时间文案。 */
    fun endText(period: Int): String = PERIODS.getOrNull(period - 1)?.second.orEmpty()
}


