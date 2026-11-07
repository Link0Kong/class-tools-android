package com.furina.classtools.ui.screens.home

import com.furina.classtools.data.homework.HomeworkDueCalculator
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.marks.MarkKind
import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeStatusMachine
import com.furina.classtools.data.power.PowerRow
import com.furina.classtools.data.power.PowerState
import com.furina.classtools.data.power.PowerStats
import com.furina.classtools.data.timetable.TtPlan
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** 工作台轮换条目（作业待交 / 通知待处理卡内最新一条）。 */
data class HomeRotationItem(
    val id: String,
    val title: String,
    val sub: String,
)

/** 工作台电费卡（未关注时页面显示引导，不请求）。 */
data class HomePowerCard(
    val label: String,
    val qText: String,
    val unit: String,
    val stateText: String,
    /** low / warn / ok / idle（UI 映射色）。 */
    val stateKey: String,
    val updText: String,
    /** 云端读数是否已加载（false = 读取中）。 */
    val hasReading: Boolean,
)

/**
 * 工作台纯逻辑（可单测，无 Compose 依赖）。
 *
 * 对齐微信小程序 `pages/index/index`：
 * - 问候（按时段）+ 日期/周次/角色；
 * - 作业待交 = 未完成且未过期，按截止时间升序（最近截止优先）；
 * - 通知待处理 = 未 mark ntdone，按时间倒序；
 * - 电费卡 = 关注宿舍（userdata.power.sel）+ 读数/状态；
 * - 轮换：每 5s 下一条（页面不可见时暂停由 UI 层控制）。
 */
object HomeWorkbenchLogic {

    const val ROTATION_INTERVAL_MS: Long = 5_000L

    /** 时段问候（凌晨/早上/上午/中午/下午/晚上/夜深）。 */
    fun greeting(hourOfDay: Int): String = when (hourOfDay.coerceIn(0, 23)) {
        in 0..4 -> "夜深了"
        in 5..8 -> "早上好"
        in 9..11 -> "上午好"
        in 12..13 -> "中午好"
        in 14..17 -> "下午好"
        in 18..22 -> "晚上好"
        else -> "夜深了"
    }

    private val weekdayZh: Map<java.time.DayOfWeek, String> = mapOf(
        java.time.DayOfWeek.MONDAY to "周一",
        java.time.DayOfWeek.TUESDAY to "周二",
        java.time.DayOfWeek.WEDNESDAY to "周三",
        java.time.DayOfWeek.THURSDAY to "周四",
        java.time.DayOfWeek.FRIDAY to "周五",
        java.time.DayOfWeek.SATURDAY to "周六",
        java.time.DayOfWeek.SUNDAY to "周日",
    )

    /** `11月8日 周六`。 */
    fun dateText(date: LocalDate): String =
        "${date.monthValue}月${date.dayOfMonth}日 ${weekdayZh[date.dayOfWeek] ?: ""}".trim()

    /** `第 10 周`；周次未知返回空串（UI 不展示）。 */
    fun weekText(week: Int?): String = week?.let { "第 $it 周" } ?: ""

    /** `11月8日 周六 · 第 10 周 · 角色`（空项自动省略）。 */
    fun subtitle(date: LocalDate, week: Int?, role: String): String = buildList {
        add(dateText(date))
        weekText(week).takeIf { it.isNotEmpty() }?.let { add(it) }
        role.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
    }.joinToString(" · ")

    // ---- 作业 ---------------------------------------------------------------

    /** 待交作业：未完成、未过期；按截止时间升序（无截止排最后），再按发布时间倒序。 */
    fun openHomeworkRows(
        rows: List<HomeworkRow>,
        marks: MarksSnapshot,
        nowMillis: Long,
    ): List<HomeworkRow> = rows
        .filter { row -> !marks.isDone(MarkKind.HW, row.id) }
        .filter { row -> !HomeworkDueCalculator.isExpired(row, nowMillis) }
        .sortedWith(
            compareBy<HomeworkRow> { row ->
                HomeworkDueCalculator.evaluate(row, nowMillis)?.atMillis ?: Long.MAX_VALUE
            }.thenByDescending { it.ts },
        )

    /** 作业轮换条目：标题 = 科目/首行；副标 = 截止文案。 */
    fun homeworkRotation(
        rows: List<HomeworkRow>,
        marks: MarksSnapshot,
        nowMillis: Long,
    ): List<HomeRotationItem> = openHomeworkRows(rows, marks, nowMillis).map { row ->
        HomeRotationItem(
            id = row.id,
            title = row.subject.ifBlank { row.content.lineSequence().firstOrNull().orEmpty() }
                .ifBlank { "作业" },
            sub = HomeworkDueCalculator.shortDueText(row).ifBlank { "未设置截止" },
        )
    }

    // ---- 通知 ---------------------------------------------------------------

    /** 待处理通知：未 mark ntdone；按时间倒序。 */
    fun noticePendingEntries(
        entries: List<NoticeEntry>,
        marks: MarksSnapshot,
        nowMillis: Long,
    ): List<NoticeEntry> = entries
        .filter { entry -> !marks.isDone(MarkKind.NT_DONE, entry.id) }
        .filter { entry -> !NoticeStatusMachine.statusOf(entry.group, nowMillis).expired }
        .sortedWith(compareByDescending { it.ts })

    /** 通知轮换条目：标题 = 通知标题；副标 = 分类 + 状态（如 `教务 · 今天截止`）。 */
    fun noticeRotation(
        entries: List<NoticeEntry>,
        marks: MarksSnapshot,
        nowMillis: Long,
    ): List<HomeRotationItem> = noticePendingEntries(entries, marks, nowMillis).map { entry ->
        val status = NoticeStatusMachine.statusOf(entry.group, nowMillis)
        HomeRotationItem(
            id = entry.id,
            title = entry.title,
            sub = listOf(entry.cat, status.label).filter { it.isNotBlank() }.joinToString(" · "),
        )
    }

    // ---- 电费 ---------------------------------------------------------------

    /**
     * 电费卡：未关注（sel 空）返回 null（页面显示引导、不请求）；
     * 关注但读数未到（row null）返回 hasReading=false 的「读取中」卡。
     */
    fun powerCard(sel: String?, row: PowerRow?, low: Int?): HomePowerCard? {
        val selected = sel?.trim().orEmpty()
        if (selected.isEmpty()) return null
        if (row == null) {
            return HomePowerCard(
                label = "关注的宿舍",
                qText = "—",
                unit = "度",
                stateText = "读取中",
                stateKey = "idle",
                updText = "",
                hasReading = false,
            )
        }
        val state = PowerStats.stateOf(row, low)
        val stateKey = when (state) {
            PowerState.LOW -> "low"
            PowerState.WARN -> "warn"
            PowerState.OK -> "ok"
            PowerState.IDLE -> "idle"
        }
        val label = listOf(row.building, row.room).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { row.label.ifBlank { "关注的宿舍" } }
        return HomePowerCard(
            label = label,
            qText = PowerStats.fmtMain(row.q),
            unit = row.unit.ifBlank { "度" },
            stateText = PowerStats.stateText(state),
            stateKey = stateKey,
            updText = PowerStats.updText(row.updatedAt),
            hasReading = row.q != null,
        )
    }

    // ---- 今日课程 -----------------------------------------------------------

    /** 今日课程（有数据才返回；无课/未同步由 UI 判断 plan/todayState）。 */
    fun todaySessions(plan: TtPlan?): List<com.furina.classtools.data.timetable.SessionInfo> =
        plan?.today.orEmpty()

    /** 是否存在「上课中」的课（卡片显示 pill）。 */
    fun hasOngoing(plan: TtPlan?): Boolean =
        plan?.today?.any {
            it.state == com.furina.classtools.data.timetable.SessionState.ONGOING
        } == true

    // ---- 轮换 ---------------------------------------------------------------

    /** 轮换下一条（size≤0 返回 0）。 */
    fun nextRotationIndex(current: Int, size: Int): Int {
        if (size <= 0) return 0
        return (current + 1).floorMod(size)
    }

    private fun Int.floorMod(modulus: Int): Int {
        val r = this % modulus
        return if (r < 0) r + modulus else r
    }

    /** 轮换是否可动（≥2 条且页面可见）。 */
    fun rotationEnabled(size: Int, pageVisible: Boolean): Boolean = pageVisible && size > 1
}
