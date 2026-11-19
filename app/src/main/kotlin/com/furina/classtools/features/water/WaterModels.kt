package com.furina.classtools.features.water

/**
 * 宿舍水量领域模型（纯 Kotlin，无 Android 依赖）。
 *
 * 契约：`docs/api/USERDATA_V6.md` 的 `water` section + `web/tools/tests/_water_contract.md` §0/§1：
 * ```jsonc
 * water = { "rooms": { "<roomverify>": { "p": 4, "recs": [ {"m":"2026-11","r":130.10,"ts":1699100000000} ] } } }
 * ```
 * 约束：`p ∈ {4,6}`；`m` = `YYYY-MM`；`r` 有限且 ≥0、2 位小数；按月唯一、升序、≤120 条；**整段 LWW**。
 */

/** 计费规则常量（web/mp 同值；页面唯一来源）。 */
object WaterRules {
    /** 电 0.58 元/度（福建中医药大学后勤基建处 2025-09-05）。 */
    const val ELEC_PRICE: Double = 0.58

    /** 水 3.03 元/吨（同上；无阶梯、无额外加价）。 */
    const val WATER_PRICE: Double = 3.03

    /** 免费水量 2 吨/人/月。 */
    const val WATER_FREE_PER_PERSON: Double = 2.0

    /** 免费电量 5 度/人/月（展示用；水量页只算水费与折合扣电）。 */
    const val ELEC_FREE_PER_PERSON: Double = 5.0

    /** 默认 4 人间。 */
    const val PERSONS_DEFAULT: Int = 4

    /** 可选人数档位：1–6 人间自选（2026-11-19 用户要求）。 */
    val PERSONS_OPTIONS: List<Int> = (1..6).toList()

    /** 每学年按 10 个月划拨（规则卡文案）。 */
    const val CYCLE_MONTHS: Int = 10

    /** 每月每房间最多保留记录数（契约上限）。 */
    const val MAX_RECORDS_PER_ROOM: Int = 120

    /** 1 吨水折合扣电（≈5.2241 度）。 */
    val TON_TO_DEGREE: Double get() = WATER_PRICE / ELEC_PRICE
}

/** 一条抄表记录。 */
data class WaterRecord(
    val month: String,
    val reading: Double,
    val ts: Long = 0L,
)

/** 一间宿舍的水量状态。 */
data class WaterRoomState(
    val persons: Int = WaterRules.PERSONS_DEFAULT,
    /** 按月份升序、按月唯一（WaterCodec/WaterMutator 保证）。 */
    val records: List<WaterRecord> = emptyList(),
) {
    val latest: WaterRecord? get() = records.lastOrNull()
}

/** 整个 water section。 */
data class WaterState(
    val rooms: Map<String, WaterRoomState> = emptyMap(),
) {
    fun room(roomVerify: String): WaterRoomState? = rooms[roomVerify?.trim().orEmpty()]

    /** 带默认值的读取（未录入过的房间返回 p=默认、records=空）。 */
    fun roomOrDefault(roomVerify: String): WaterRoomState =
        room(roomVerify) ?: WaterRoomState()

    /** 某个月份的用量/费用（无记录或无上一条 → 对应字段 null）。 */
    fun recordOf(roomVerify: String, month: String): WaterRecord? =
        room(roomVerify)?.records?.firstOrNull { it.month == month }
}

/** 一次抄表读数/水费计算。 */
data class WaterCharge(
    /** 本期用量（吨）；无上一条记录 → null。 */
    val usage: Double?,
    /** 免费额度（吨）。 */
    val free: Double,
    /** 超出定额部分（吨）；无用量 → null。 */
    val excess: Double?,
    /** 本期水费（元）；无用量 → null。 */
    val fee: Double?,
    /** 折合扣电（度，1 位）；无用量 → null。 */
    val degree: Double?,
    /** 是否在定额内（无用量时 false）。 */
    val within: Boolean,
    /** 是否有上一条记录。 */
    val hasPrevious: Boolean,
)

/** 某个月份的统计（页面统计卡）。 */
data class WaterMonthStats(
    val month: String,
    val persons: Int,
    val record: WaterRecord?,
    val previous: WaterRecord?,
    val charge: WaterCharge,
    /** 日均用量（吨/天）；无用量 → null。 */
    val dailyAverage: Double?,
    /** 上月用量（吨）；无上月记录或无上上条 → null。 */
    val previousMonthUsage: Double?,
    /** 相对上月用量差（吨，正=多用）；任一为空 → null。 */
    val deltaVsPreviousMonth: Double?,
) {
    val hasRecord: Boolean get() = record != null
}

/** 抄表校验结果（UI 行内错误/覆盖确认）。 */
sealed interface WaterRecordReview {
    /** 可保存；`replaceExisting=true` 表示同月已有记录将覆盖。 */
    data class Valid(val record: WaterRecord, val replaceExisting: Boolean) : WaterRecordReview

    data class Invalid(val message: String) : WaterRecordReview
}

/** 录入/删除/人数切换等纯函数结果（不可变状态）。 */
data class WaterMutation(
    val state: WaterState,
    val message: String? = null,
)
