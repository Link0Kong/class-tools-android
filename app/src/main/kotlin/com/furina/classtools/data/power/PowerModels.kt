package com.furina.classtools.data.power

/**
 * 宿舍电费领域模型（契约：docs/BACKEND_CONTRACT.md §7.5、docs/api/ENDPOINTS.md E22/E23）。
 *
 * 口径必须与网页/小程序一致（web/AGENTS.md §4b-8/11/16/17）：
 * - 房间目录 17 间（顺序即 UI 顺序）；
 * - 低电阈值默认 30，档位 [10,20,30,40,50,60,80,100]；
 * - 24h/7d/30d 区间；13/16 号楼「网关日更」慢频统计（SLOW_BUILDINGS）；
 * - 趋势禁止横向滚动、点击/拖动命中最近真实采样点、气泡固定右上角。
 */

/** 宿舍目录项；`rv` = roomverify（如 `4-23A---321`）。 */
data class RoomInfo(
    val rv: String,
    val label: String,
    val building: String,
    val room: String,
)

/**
 * 17 间宿舍目录（与网页 `PW_ROOMS` / 小程序 `utils/power.js ROOMS` **逐项一致**；增删宿舍必须两端同改）。
 */
object PowerRooms {
    val ROOMS: List<RoomInfo> = listOf(
        RoomInfo("1-13---324", "13号楼324", "13号楼", "324"),
        RoomInfo("1-13---308", "13号楼308", "13号楼", "308"),
        RoomInfo("1-16---305", "16号楼305", "16号楼", "305"),
        RoomInfo("1-16---306", "16号楼306", "16号楼", "306"),
        RoomInfo("4-23A---321", "23号楼A座321", "23号楼A座", "321"),
        RoomInfo("4-23A---323", "23号楼A座323", "23号楼A座", "323"),
        RoomInfo("4-23A---324", "23号楼A座324", "23号楼A座", "324"),
        RoomInfo("4-23A---325", "23号楼A座325", "23号楼A座", "325"),
        RoomInfo("4-23A---326", "23号楼A座326", "23号楼A座", "326"),
        RoomInfo("4-23A---327", "23号楼A座327", "23号楼A座", "327"),
        RoomInfo("4-23A---328", "23号楼A座328", "23号楼A座", "328"),
        RoomInfo("4-23A---329", "23号楼A座329", "23号楼A座", "329"),
        RoomInfo("4-23A---330", "23号楼A座330", "23号楼A座", "330"),
        RoomInfo("4-23B---422", "23号楼B座422", "23号楼B座", "422"),
        RoomInfo("4-23B---423", "23号楼B座423", "23号楼B座", "423"),
        RoomInfo("4-23B---424", "23号楼B座424", "23号楼B座", "424"),
        RoomInfo("4-23B---425", "23号楼B座425", "23号楼B座", "425"),
    )

    /** 楼栋顺序（选择弹层展示用）。 */
    val BUILDINGS: List<String> = listOf("13号楼", "16号楼", "23号楼A座", "23号楼B座")

    private val byRv: Map<String, RoomInfo> = ROOMS.associateBy { it.rv }

    fun catOf(rv: String?): RoomInfo? = byRv[rv?.trim().orEmpty()]

    /** 非法/未知 roomverify → ""（页面按未选择处理，避免展示脏数据）。 */
    fun normalizeSel(value: String?): String {
        val rv = value?.trim().orEmpty()
        return if (byRv.containsKey(rv)) rv else ""
    }

    fun labelOf(rv: String?): String = catOf(rv)?.label.orEmpty()

    fun buildingOf(rv: String?): String = catOf(rv)?.building.orEmpty()

    fun roomsOfBuilding(building: String): List<RoomInfo> =
        ROOMS.filter { it.building == building.trim() }

    fun rvOf(building: String, room: String): String =
        roomsOfBuilding(building).firstOrNull { it.room == room.trim() }?.rv.orEmpty()
}

/** 云端 `power` 当前值行（quantity 非法 → q=null，界面显示「—」）。 */
data class PowerRow(
    val id: String,
    val label: String = "",
    val building: String = "",
    val room: String = "",
    val q: Double?,
    val unit: String = "度",
    /** 云端 low 标记（仅参考；状态判定以用户阈值 q<low 为准）。 */
    val low: Boolean = false,
    /** ISO timestamptz。 */
    val updatedAt: String = "",
)

/** 一个采样点：q=剩余电量，t=epoch ms。 */
data class PowerPoint(
    val q: Double,
    val t: Long,
)

/** 充值/上升事件。 */
data class RechargePoint(
    val t: Long,
    val reset: Double,
    val delta: Double = 0.0,
)

/** 异常扣减（水费从电费账户扣 / 充值后短时大跌）统计。 */
data class DeductInfo(
    val n: Int = 0,
    val deg: Double = 0.0,
    val yuan: Double = 0.0,
    val waterN: Int = 0,
    val thr: Double = 0.0,
    val u: Double = 0.0,
)

/** 区间统计（deriveRange 结果；与 web `deriveRange` 同字段语义）。 */
data class RangeStats(
    val n: Int = 0,
    val start: Double? = null,
    val end: Double? = null,
    val drop: Double? = null,
    val hours: Double = 0.0,
    val perDay: Double? = null,
    val days: Double? = null,
    val interval: Double? = null,
    val recharge: Boolean = false,
    val lastUp: RechargePoint? = null,
    val valid: Boolean = false,
    val deduct: DeductInfo = DeductInfo(),
)

/** 单档速率统计（rateFrom 结果）。 */
data class RateStats(
    val rate: Double? = null,
    val used: Double = 0.0,
    val hours: Double = 0.0,
    val span: Double = 0.0,
    val n: Int = 0,
    val recharge: Boolean = false,
    val deductN: Int = 0,
)

/** 估算可用天数（实时口径：0.65*r24+0.35*r7；慢频房由 slowStats 覆盖）。 */
data class PowerEstimate(
    val valid: Boolean = false,
    val rate: Double? = null,
    val days: Double? = null,
    val q: Double? = null,
    val slow: Boolean = false,
    val ref: Boolean = false,
    val nSeg: Int = 0,
    val changes: Int = 0,
    val lastChange: Long? = null,
    val lastRecharge: RechargePoint? = null,
    val v24: Boolean = false,
    val v7: Boolean = false,
    val r24: RateStats? = null,
    val r7: RateStats? = null,
)

/** 慢更新提示（24h 窗口相邻 |Δq|>0.005 的次数 ≤2 视为慢）。 */
data class SlowUpdate(
    val changes: Int = 0,
    val slow: Boolean = false,
    val lastAt: Long? = null,
    val text: String = "",
    val lastChange: Long? = null,
    val lastRecharge: RechargePoint? = null,
)

/** 13/16 号楼慢频（日更）统计（slowStats 结果；字段与 web/mp 同名同义）。 */
data class SlowStats(
    val valid: Boolean = false,
    val rate: Double? = null,
    val days: Double? = null,
    val ref: Boolean = false,
    val nSeg: Int = 0,
    val events: Int = 0,
    val changes: Int = 0,
    val lastChange: Long? = null,
    val lastRecharge: RechargePoint? = null,
)

/** 趋势折线点（x/y ∈ [0,1]；y=0 顶部、1 底部，与 web 一致）。 */
data class TrendPoint(
    val x: Float,
    val y: Float,
    val q: Double,
    val t: Long,
)

/** 趋势折线数据（canvas 绘制用纯数据）。 */
data class TrendLine(
    val empty: Boolean = true,
    val note: String = "",
    val count: Int = 0,
    val points: List<TrendPoint> = emptyList(),
    val hi: Double? = null,
    val mid: Double? = null,
    val lo: Double? = null,
    val min: Double? = null,
    val max: Double? = null,
    val flat: Boolean = false,
)

/** 点击/拖动命中最近真实采样点。 */
data class CursorHit(
    val index: Int,
    val point: TrendPoint,
)

/** x 轴刻度（frac 为真实采样点 x 比例；label 已格式化）。 */
data class XTick(
    val frac: Float,
    val label: String,
)

/** 指标 tile（估算可用 / 区间用电 / 低电阈值 / 上次充值）。 */
data class PowerTile(
    val key: String,
    val label: String,
    val value: String,
    val unit: String = "",
    val sub: String = "",
    val highlight: Boolean = false,
    /** 估算 tile 的「参考」标记。 */
    val ref: Boolean = false,
    /** 是否可点（低电阈值 tile）。 */
    val clickable: Boolean = false,
)

/** 计费规则条（与 web `#pwBill` 同文案口径）。 */
data class BillInfo(
    val persons: Int = 4,
    val elecText: String = "",
    val feeText: String = "—",
    val freeText: String = "",
    val waterText: String = "",
    val hasDeduct: Boolean = false,
    val deductText: String = "",
)

/** 低电状态。 */
enum class PowerState { IDLE, LOW, WARN, OK }

/** 趋势区间（顺序即 UI 顺序；与 web `PW_RANGES` 一致）。 */
enum class PowerRange(
    val key: String,
    val label: String,
    val windowMs: Long,
    val title: String,
    val tileLabel: String,
    val dropLabel: String,
    val xTickCount: Int,
) {
    H24("24h", "24h", 24L * 3600 * 1000, "近 24h 趋势", "近 24h 用电", "近 24h 下降", 3),
    D7("7d", "7天", 7L * 24 * 3600 * 1000, "近 7 天趋势", "近 7 天用电", "区间下降", 4),
    D30("30d", "30天", 30L * 24 * 3600 * 1000, "近 30 天趋势", "近 30 天用电", "区间下降", 5);

    companion object {
        fun fromKey(key: String): PowerRange = entries.firstOrNull { it.key == key } ?: H24
    }
}

/** 电费行加载结果。 */
sealed interface PowerRowResult {
    data class Success(val row: PowerRow, val fromCache: Boolean) : PowerRowResult
    data class Failure(val message: String) : PowerRowResult
}

/** 电费历史加载结果。 */
sealed interface PowerHistoryResult {
    data class Success(
        val points: List<PowerPoint>,
        val range: PowerRange,
        val fromCache: Boolean,
    ) : PowerHistoryResult

    data class Failure(val message: String) : PowerHistoryResult
}

