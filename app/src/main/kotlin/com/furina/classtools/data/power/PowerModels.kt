package com.furina.classtools.data.power

import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * 宿舍电费领域模型（契约：docs/BACKEND_CONTRACT.md §7.5、docs/api/ENDPOINTS.md E21–E23）。
 *
 * 口径必须与网页/小程序一致（web/AGENTS.md §4b-8/11/16/17）：
 * - 房间目录**不硬编码**：由云端 `power` 表读取（[PowerDirectoryRepository]），顺序按楼栋自然序 + 房间号数字序；
 * - 低电阈值默认 30，档位 [10,20,30,40,50,60,80,100]；
 * - 24h/7d/30d 区间；13/16 号楼「网关日更」慢频统计（SLOW_BUILDINGS）；
 * - 趋势禁止横向滚动、点击/拖动命中最近真实采样点、气泡固定右上角。
 */

/** 宿舍目录项；`rv` = roomverify（如 `4-23B---423`），`label` = 规范展示名 `23号楼B座423`。 */
data class RoomInfo(
    val rv: String,
    val label: String,
    val building: String,
    val room: String,
)

/**
 * roomverify 解析 + 目录排序（**不硬编码任何真实房号**，仓库可脱敏/公开）。
 *
 * - roomverify 是稳定主键：`{areaid}-{楼栋}---{房间}`，如 `1-13---324`、`4-23B---423`
 *   （areaid：13/16 号楼=1，23 号楼=4；与网页 `web/power/src/util.py::parse_roomverify` 同口径）；
 * - 规范展示名从 roomverify 派生：`{楼号}号楼{座}{房间号}`，因此已关注宿舍**不依赖目录**也能显示；
 * - 目录由云端 `power` 表提供（`select=id,label,building,room`），按楼栋自然序 + 房间号数字序排序，
 *   与网页 `PW_ROOMS` / 小程序 `utils/power.js ROOMS` 的展示顺序一致（13号楼 → 16号楼 → 23号楼A座 → 23号楼B座）。
 */
object PowerRooms {
    /** `areaid-楼栋---房间`（楼栋段不含 `-`；`-` 与 `---` 之间的分隔符允许 1+ 个 `-`）。 */
    private val RV_RE = Regex("""^(\d+)-([^-]+)-+([^-]+)$""")

    /** 楼栋段：纯数字（`13` → 13号楼）或数字+字母（`23A` → 23号楼A座）。 */
    private val BUILDING_RE = Regex("""^(\d+)([A-Za-z])?$""")

    /** 解析 roomverify → [RoomInfo]；解析不出返回 null（不猜测，页面按未选择处理）。 */
    fun catOf(rv: String?): RoomInfo? {
        val raw = rv?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val match = RV_RE.matchEntire(raw) ?: return null
        val token = match.groupValues[2]
        val room = match.groupValues[3].trim()
        if (room.isEmpty()) return null
        val buildingMatch = BUILDING_RE.matchEntire(token) ?: return null
        val number = buildingMatch.groupValues[1]
        val seat = buildingMatch.groupValues[2]
        val building = if (seat.isEmpty()) "${number}号楼" else "${number}号楼${seat.uppercase()}座"
        return RoomInfo(rv = raw, label = building + room, building = building, room = room)
    }

    /** 非 roomverify 形态 → ""（页面按未选择处理，避免展示脏数据）。 */
    fun normalizeSel(value: String?): String = catOf(value)?.rv.orEmpty()

    fun labelOf(rv: String?): String = catOf(rv)?.label.orEmpty()

    fun buildingOf(rv: String?): String = catOf(rv)?.building.orEmpty()

    fun roomOf(rv: String?): String = catOf(rv)?.room.orEmpty()

    /**
     * 云端目录行 → [RoomInfo]：优先从 `id`（=roomverify）派生规范名；
     * 解析不出时才退回云端 `label/building/room`（避免旧/异构数据整条丢失）。
     */
    fun fromDirectoryRow(
        id: String?,
        label: String? = null,
        building: String? = null,
        room: String? = null,
    ): RoomInfo? {
        val rv = id?.trim().orEmpty()
        if (rv.isEmpty()) return null
        catOf(rv)?.let { return it }
        val buildingText = building?.trim().orEmpty()
        val roomText = room?.trim().orEmpty()
        if (buildingText.isEmpty() && roomText.isEmpty()) return null
        val labelText = label?.trim().orEmpty().ifBlank { buildingText + roomText }
        return RoomInfo(rv = rv, label = labelText, building = buildingText, room = roomText)
    }

    /** 解析 E21 目录响应：只保留可定义宿舍的行，按 [sortRooms] 排序；非法 JSON → 空列表。 */
    fun parseDirectory(json: Json, raw: String): List<RoomInfo> {
        val array = parseJsonOrNull(json, raw) as? JsonArray ?: return emptyList()
        val out = ArrayList<RoomInfo>(array.size)
        for (element in array) {
            val obj = element as? JsonObject ?: continue
            out.add(
                fromDirectoryRow(
                    id = obj["id"].asStringOrNull(),
                    label = obj["label"].asStringOrNull(),
                    building = obj["building"].asStringOrNull(),
                    room = obj["room"].asStringOrNull(),
                ) ?: continue,
            )
        }
        return sortRooms(out)
    }

    /** 目录排序：楼栋自然序（13号楼 < 16号楼 < 23号楼A座 < 23号楼B座）→ 房间号数字序。 */
    fun sortRooms(rooms: List<RoomInfo>): List<RoomInfo> = rooms
        .asSequence()
        .filter { it.rv.isNotBlank() }
        .distinctBy { it.rv }
        .sortedWith(
            compareBy(
                { naturalKey(it.building).first },
                { naturalKey(it.building).second },
                { naturalKey(it.room).first },
                { naturalKey(it.room).second },
            ),
        )
        .toList()

    /** 楼栋列表（按自然序；目录为空 → 空列表）。 */
    fun buildingsOf(rooms: List<RoomInfo>): List<String> =
        sortRooms(rooms).map { it.building }.filter { it.isNotBlank() }.distinct()

    /** 某楼栋下的房间（输入无需预排序）。 */
    fun roomsOfBuilding(rooms: List<RoomInfo>, building: String): List<RoomInfo> {
        val target = building.trim()
        if (target.isEmpty()) return emptyList()
        return sortRooms(rooms).filter { it.building == target }
    }

    /** 房间号搜索（房间号 / 楼栋 / 展示名，忽略大小写）。 */
    fun search(rooms: List<RoomInfo>, keyword: String): List<RoomInfo> {
        val q = keyword.trim()
        if (q.isEmpty()) return sortRooms(rooms)
        return sortRooms(rooms).filter {
            it.room.contains(q, ignoreCase = true) ||
                it.building.contains(q, ignoreCase = true) ||
                it.label.contains(q, ignoreCase = true)
        }
    }

    /** 当前关注宿舍是否在当前目录内（用于识别历史遗留的虚构/过期房号）。 */
    fun contains(rooms: List<RoomInfo>, rv: String?): Boolean {
        val target = rv?.trim().orEmpty()
        if (target.isEmpty()) return false
        return rooms.any { it.rv == target }
    }

    /**
     * 目录 ∪ 其它来源的房间（如本机水量记录里出现过的 roomverify）。
     *
     * 用途：目录加载失败/新宿舍尚未同步时，仍能切换到「本机已有记录的宿舍」；
     * 非法 key 自动忽略，结果按 [sortRooms] 排序。
     */
    fun merge(rooms: List<RoomInfo>, extraRoomverifies: Iterable<String>): List<RoomInfo> {
        val known = rooms.mapTo(HashSet()) { it.rv }
        val extra = extraRoomverifies.mapNotNull { catOf(it) }.filter { it.rv !in known }
        return sortRooms(rooms + extra)
    }

    /** 自然排序键：数字前缀按数值、其余按字符串（`13号楼` < `16号楼` < `23号楼A座`）。 */
    private fun naturalKey(text: String): Pair<Int, String> {
        val value = text.trim()
        val match = Regex("""^(\d+)(.*)$""").matchEntire(value)
        val number = match?.groupValues?.get(1)?.toIntOrNull()
        return if (number != null) number to match.groupValues[2] else Int.MAX_VALUE to value
    }
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

/** 充值/上升事件：[reset]=充值后余额，[delta]=本次充值量（度）。 */
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

/**
 * 趋势折线点（x/y ∈ [0,1]；y=0 顶部、1 底部，与 web 一致）。
 *
 * [y] 由**绘图值**（重置/充值后为折算剩余）计算；[q] 保留**真实剩余**（游标/图例展示用）。
 */
data class TrendPoint(
    val x: Float,
    val y: Float,
    val q: Double,
    val t: Long,
)

/** 趋势充值标记（竖虚线；[x] 为真实时间比例 0..1，[delta] 为该次充值跳变度数）。 */
data class TrendRechargeMark(
    val t: Long,
    val x: Float,
    val delta: Double,
)

/**
 * 趋势「已折算充值」结果（对齐网页 `POWER.renderTrend` 2026-11-12 优化）：
 *
 * 区间内相邻采样 `Δq > [PowerStats.RECHARGE_JUMP]`（0.5 度）= 充值跳变；
 * 绘图值改为 `真实剩余 - 累计充值`，使曲线保持连续下降；真实剩余仍保留在 [PowerPoint.q]。
 */
data class RechargeFold(
    /** 与输入采样一一对应的绘图值（扣除累计充值后的折算剩余）。 */
    val values: List<Double> = emptyList(),
    /** 充值事件（每笔 +0.5 度以上跳变一个；用于画竖虚线）。 */
    val marks: List<TrendRechargeMark> = emptyList(),
    /** 区间内累计充值度数（>0 时标题/图例标「已折算充值」）。 */
    val total: Double = 0.0,
    /** 充值事件笔数。 */
    val count: Int = 0,
)

/** 趋势折线数据（canvas 绘制用纯数据）。 */
data class TrendLine(
    val empty: Boolean = true,
    val note: String = "",
    val count: Int = 0,
    val points: List<TrendPoint> = emptyList(),
    /** y 轴上/中/下刻度值：**折算范围**（有充值时 ≠ 真实剩余范围）。 */
    val hi: Double? = null,
    val mid: Double? = null,
    val lo: Double? = null,
    val min: Double? = null,
    val max: Double? = null,
    val flat: Boolean = false,
    /** 区间内累计充值（>0 → 标题「已折算充值」+ 图例「充值 +N 度（已折算）」）。 */
    val foldedRecharge: Double = 0.0,
    /** 充值位置（竖虚线）。 */
    val rechargeMarks: List<TrendRechargeMark> = emptyList(),
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

/**
 * 比较取数的隐藏窗口（对齐网页 `PW_RANGE_MAP["14d"/"70d"]`，**不出现在区间按钮**）。
 *
 * - 7 天比较需要「上周同一时段」→ 14d；
 * - 30 天比较需要「上月同期」→ 70d；
 * - 24h 比较直接用已加载的 7d（无需额外窗口）。
 */
enum class PowerCompareRange(val key: String, val windowMs: Long) {
    D14("14d", 14L * 24 * 3600 * 1000),
    D70("70d", 70L * 24 * 3600 * 1000),
    ;

    companion object {
        fun fromKey(key: String?): PowerCompareRange? = entries.firstOrNull { it.key == key?.trim() }
    }
}

/** 隐藏窗口历史加载结果（比较取数专用；不参与区间档位/趋势渲染）。 */
sealed interface PowerHistoryWindowResult {
    data class Success(
        val points: List<PowerPoint>,
        val range: PowerCompareRange,
        val fromCache: Boolean,
    ) : PowerHistoryWindowResult

    data class Failure(val message: String) : PowerHistoryWindowResult
}

