package com.furina.classtools.data.privacy

import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.sync.UserdataV6
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * 本机数据快照（隐私页「本机数据」8 计数用；**只数条数，不输出原文**，不联网）。
 *
 * 口径对齐小程序 `pages/data` + `utils/privacy.js countsOf`：
 * 已完成作业 / 已处理通知 / 置顶通知 / 体测记录 / 抽签名单 / 分组名单 / 点名名单 / 待补发。
 * 额外汇总（Android 增强，非 8 格）：同步 section 数 / 首页常用工具数 / 班级名单缓存人数。
 *
 * 数据来源：Lead 从现有仓库本机缓存聚合后传入（`MarksSnapshot`、`UserdataV6.sectionsOf(...)`、
 * 体测历史列表大小、班级名单列表大小、待补发条数）；本类不持有 Context/网络。
 */
data class LocalDataSnapshot(
    /** userdata v6 中与默认值不同的 section 数（0..11），对应「同步配置」。 */
    val userdataSections: Int = 0,
    /** 首页常用工具勾选数（0..4）。 */
    val homeTools: Int = 0,
    /** marks：已完成作业数。 */
    val hwDone: Int = 0,
    /** marks：已处理通知数（ntdone）。 */
    val ntDone: Int = 0,
    /** marks：置顶通知数（nt）。 */
    val ntPin: Int = 0,
    /** 体测历史条数（本机 fitrecs 列表大小）。 */
    val fitRecords: Int = 0,
    /** 抽签名单人数（userdata.roster.rosterSel 大小）。 */
    val rosterSel: Int = 0,
    /** 分组名单人数（userdata.group.sel 大小）。 */
    val groupSel: Int = 0,
    /** 点名名单人数（userdata.roster.rollSel 大小）。 */
    val rollSel: Int = 0,
    /** 待补发条数（marks 待同步 + userdata 待同步；由 Lead 传入，默认 0）。 */
    val pending: Int = 0,
    /** 班级名单缓存人数（本机缓存，仅本机）。 */
    val classMembers: Int = 0,
    /** 抽签 section 是否可用（不可用显示「—」）。 */
    val drawAvailable: Boolean = false,
    /** 分组 section 是否可用（不可用显示「—」）。 */
    val groupAvailable: Boolean = false,
)

/** 一项本机数据计数（可直接渲染）。 */
data class LocalDataStat(
    val key: String,
    val label: String,
    val value: String,
    /** false = 该项数据不可用，UI 显示「—」并降低强调。 */
    val available: Boolean = true,
)

/** 本机数据聚合与展示格式化（纯函数，可单测）。 */
object LocalDataStats {

    /** 8 计数主格（顺序与 mp 一致）。 */
    fun primaryStats(snapshot: LocalDataSnapshot): List<LocalDataStat> = listOf(
        LocalDataStat("hwDone", "已完成作业", formatCount(snapshot.hwDone)),
        LocalDataStat("ntDone", "已处理通知", formatCount(snapshot.ntDone)),
        LocalDataStat("ntPin", "置顶通知", formatCount(snapshot.ntPin)),
        LocalDataStat("fitRecs", "体测记录", formatCount(snapshot.fitRecords)),
        statOrUnavailable("rosterSel", "抽签名单", snapshot.rosterSel, snapshot.drawAvailable),
        statOrUnavailable("groupSel", "分组名单", snapshot.groupSel, snapshot.groupAvailable),
        LocalDataStat("rollSel", "点名名单", formatCount(snapshot.rollSel)),
        LocalDataStat("pending", "待补发", formatCount(snapshot.pending)),
    )

    /** 额外汇总行（Android 增强，非 8 格）：同步配置 / 首页常用工具 / 班级名单缓存。 */
    fun summaryStats(snapshot: LocalDataSnapshot): List<LocalDataStat> = listOf(
        LocalDataStat("sections", "同步配置", formatCount(snapshot.userdataSections)),
        LocalDataStat("homeTools", "首页常用工具", formatCount(snapshot.homeTools)),
        LocalDataStat("classMembers", "班级名单缓存", formatCount(snapshot.classMembers)),
    )

    /**
     * 从本机缓存构建快照（不联网）：
     * - `userdata` 只取 v6 的 11 个 section；`userdataSections` = 与默认值不同的 section 数；
     * - marks 只数 true；
     * - `drawAvailable/groupAvailable` 依据 section 是否存在。
     */
    fun from(
        userdata: JsonObject?,
        marks: MarksSnapshot,
        fitRecords: Int,
        classMembers: Int = 0,
        pending: Int = 0,
    ): LocalDataSnapshot {
        val sections = userdata ?: JsonObject(emptyMap())
        val present = UserdataV6.SECTION_KEYS.filter { sections.containsKey(it) }
        val nonDefault = present.count { key ->
            sections[key] != UserdataV6.defaultSection(key)
        }
        val homeTools = (sections["homeTools"] as? JsonArray)?.size ?: 0
        val roster = sections["roster"] as? JsonObject
        val rosterSel = (roster?.get("rosterSel") as? JsonArray)?.size ?: 0
        val rollSel = (roster?.get("rollSel") as? JsonArray)?.size ?: 0
        val group = sections["group"] as? JsonObject
        val groupSel = (group?.get("sel") as? JsonArray)?.size ?: 0
        return LocalDataSnapshot(
            userdataSections = nonDefault,
            homeTools = homeTools,
            hwDone = marks.hwDone.count { it.value },
            ntDone = marks.ntDone.count { it.value },
            ntPin = marks.ntPin.count { it.value },
            fitRecords = fitRecords.coerceAtLeast(0),
            rosterSel = rosterSel,
            groupSel = groupSel,
            rollSel = rollSel,
            pending = pending.coerceAtLeast(0),
            classMembers = classMembers.coerceAtLeast(0),
            drawAvailable = sections.containsKey("draw"),
            groupAvailable = sections.containsKey("group"),
        )
    }

    /** 数字展示：0..9999 原样，≥10000 显示 `9999+`（隐私页只作概览）。 */
    fun formatCount(value: Int): String = when {
        value <= 0 -> "0"
        value >= MAX_DISPLAY_COUNT -> "${MAX_DISPLAY_COUNT - 1}+"
        else -> value.toString()
    }

    /** 数据不可用（如抽签/分组 section 缺失）显示「—」。 */
    fun statOrUnavailable(key: String, label: String, value: Int, available: Boolean): LocalDataStat =
        if (available) {
            LocalDataStat(key, label, formatCount(value))
        } else {
            LocalDataStat(key, label, "—", available = false)
        }

    const val MAX_DISPLAY_COUNT: Int = 10_000
}
