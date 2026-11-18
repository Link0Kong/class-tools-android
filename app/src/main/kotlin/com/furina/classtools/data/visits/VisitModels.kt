package com.furina.classtools.data.visits

/** 一条 `public.visits` 行（字段与网页/小程序/Windows 同契约）。 */
data class VisitRow(
    val slot: String,
    val sid: String,
    val name: String,
    val ts: Long,
    val client: String,
)

/** 统计页范围：今天 / 近 7 天（与网页、小程序、Windows 一致）。 */
enum class VisitRange { TODAY, WEEK }

/** 一个账号的聚合行（页面直接渲染）。 */
data class VisitAccount(
    val sid: String,
    val name: String,
    val count: Int,
    val lastTs: Long,
    val client: String,
    val clientText: String,
    val lastText: String,
    val activeDays: Int,
    val activeToday: Boolean,
    val detail: String,
)

/** 一次统计视图（摘要 + 账号行，已过滤测试账号/匿名行）。 */
data class VisitsSnapshot(
    val accounts: List<VisitAccount>,
    val total: Int,
    val activeToday: Int,
)

sealed interface VisitsResult {
    data class Success(val rows: List<VisitRow>) : VisitsResult
    data class Failure(val message: String) : VisitsResult
}
