package com.furina.classtools.data.visits

/**
 * 一条 `public.visits` 行（字段与网页/小程序/Windows 同契约：
 * `web/统计多端_详细统计_契约_20261120.md` §1）。
 *
 * [ver]/[dev]/[did] 是 2026-11-20 新增列：
 * - 旧行 / 旧客户端可能为空串（页面显示「未记录」）；
 * - 构造参数给默认值，旧的「5 参构造」调用点与测试无需改动即可编译。
 *
 * 注意：[did] 只用于「访问设备数」去重，任何页面都不显示它。
 */
data class VisitRow(
    val slot: String,
    val sid: String,
    val name: String,
    val ts: Long,
    val client: String,
    val ver: String = "",
    val dev: String = "",
    val did: String = "",
)

/** 统计页范围：今天 / 近 7 天（与网页、小程序、Windows、鸿蒙一致）。 */
enum class VisitRange { TODAY, WEEK }

/**
 * 登录端分组（全局堆叠条 / 账号明细共用）。
 *
 * @param code 归一后的端代码（`web`/`mp`/`android`/`win`/`harmony`；空串 = 未记录）
 * @param percent 占当前范围总行数的整数百分比（最后一段补齐到 100）
 */
data class VisitClientStat(
    val code: String,
    val label: String,
    val count: Int,
    val percent: Int,
)

/**
 * 版本分组（空版本 = 「未记录」；超过 6 组时第 7 行为「其他」）。
 *
 * @param label 版本短标签或「未记录」/「其他」
 */
data class VisitVerStat(
    val label: String,
    val count: Int,
    val percent: Int,
)

/**
 * `client|dev` 设备分组（全局设备分布 / 账号设备明细共用）。
 *
 * @param client 归一后的端代码（「其他」聚合行为空串）
 * @param clientLabel 端中文标签（「其他」聚合行为空串，UI 不显示端标签）
 * @param label 设备短标签（空 dev 为「未记录」；聚合行为「其他」）
 * @param lastTs 组内最近一次访问毫秒（0 = 未知）
 * @param percent 占分母（全局总行数 / 账号行数）的整数百分比
 */
data class VisitDeviceStat(
    val client: String,
    val clientLabel: String,
    val label: String,
    val count: Int,
    val lastTs: Long,
    val percent: Int,
)

/**
 * 趋势柱（今天 24 根 / 近 7 天 7 根）。
 *
 * @param label 横轴标签；空串 = 该柱不显示标签（24 小时图只在 00/03/…/21/23 显示）
 * @param highlighted 是否高亮（近 7 天范围里的「今天」）
 */
data class VisitTrendPoint(
    val label: String,
    val count: Int,
    val highlighted: Boolean = false,
)

/**
 * 一个账号的聚合行（页面直接渲染；口径见契约 §3.2）。
 *
 * 不包含学号展示字段：`sid` 只作 LazyColumn 的稳定 key 与聚合键，UI 不渲染。
 *
 * @param client 最近一条访问的登录端代码
 * @param ver 最近一条访问的版本短标签（原始值，空串 = 未记录）
 * @param dev 最近一条访问的设备短标签（原始值，空串 = 未记录）
 * @param verText/devText 展示文案（空值已替换为「未记录」）
 * @param deviceCount 去重设备数（优先 did；无 did 的行按 client|dev 兜底）
 * @param detailText `活跃 N/7 天 · 首次 … · 最近 …`（今天范围省略活跃段）
 */
data class VisitAccount(
    val sid: String,
    val name: String,
    val count: Int,
    val firstTs: Long,
    val lastTs: Long,
    val firstText: String,
    val lastText: String,
    val client: String,
    val clientText: String,
    val ver: String,
    val verText: String,
    val dev: String,
    val devText: String,
    val deviceCount: Int,
    val activeDays: Int,
    val activeToday: Boolean,
    val clients: List<VisitClientStat>,
    val versions: List<VisitVerStat>,
    val devices: List<VisitDeviceStat>,
    val detailText: String,
)

/**
 * 一次统计视图（已过滤测试账号/匿名行/范围外行）。
 *
 * [devices] 是全局设备分布（最多 6 组 + 「其他」），[deviceTotal] 是摘要里的去重设备数。
 */
data class VisitsSnapshot(
    val accounts: List<VisitAccount>,
    val total: Int,
    val activeToday: Int,
    val deviceTotal: Int,
    val clients: List<VisitClientStat>,
    val versions: List<VisitVerStat>,
    val devices: List<VisitDeviceStat>,
    val trend: List<VisitTrendPoint>,
) {
    /** 摘要「访问账号」。 */
    val accountCount: Int get() = accounts.size

    /** 兼容命名：全局设备分布行（与 [devices] 是同一列表，便于页面/测试按 deviceRows 读取）。 */
    val deviceRows: List<VisitDeviceStat> get() = devices

    /** 当前范围是否有数据（空态用；无数据时不渲染空图表）。 */
    val hasData: Boolean get() = total > 0

    /** 账号次数条分母（至少 1，避免除零；空列表为 0）。 */
    val maxAccountCount: Int get() = accounts.maxOfOrNull { it.count } ?: 0

    /** 趋势柱高度分母（至少 1）。 */
    val trendMax: Int get() = trend.maxOfOrNull { it.count } ?: 0
}

/** 读取结果：成功（行已解析）/ 失败（页面展示既有失败态）。 */
sealed interface VisitsResult {
    data class Success(val rows: List<VisitRow>) : VisitsResult
    data class Failure(val message: String) : VisitsResult
}
