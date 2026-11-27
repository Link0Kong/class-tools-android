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
import com.furina.classtools.data.timetable.SessionInfo
import com.furina.classtools.data.timetable.SessionState
import com.furina.classtools.data.timetable.TodayState
import com.furina.classtools.data.timetable.TtPlan
import java.time.LocalDate
import kotlin.math.roundToInt

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
 * 工作台 v5 顶部「今日」hero 的纯数据概览。
 *
 * @param kind 语义状态：`loading / ongoing / next / done / none / nodata`（UI 据此选强调色/图标）
 * @param tag 状态标签（`上课中 / 下一节 / 已结束 / 今日无课 / 未同步`）
 * @param title 主标题（上课中 = 课程名；下一节 = 课程名；其余 = 空态标题）
 * @param detail 副标题（时间 · 地点 / 下次课程）
 * @param hint 右下提示（`还剩 22 分钟` / `距上课 5 分钟` / `点此打开课表`）
 * @param progress 进度条 0..1；null = 不展示（未同步/无课/无数据）
 * @param progressText 进度说明（`已上 40%` / `今日 2/5 节`）
 */
data class HomeHeroState(
    val kind: String,
    val tag: String,
    val title: String,
    val detail: String,
    val hint: String,
    val progress: Float?,
    val progressText: String,
) {
    /** 是否处于「需要用户行动」的未同步空态（hero 显示打开课表 CTA）。 */
    val needsAction: Boolean get() = kind == "nodata"
}

/**
 * 工作台纯逻辑（可单测，无 Compose 依赖）。
 *
 * 对齐微信小程序 `pages/index/index`：
 * - 问候（按时段）+ 日期/周次/角色；
 * - 作业待交 = 未完成且未过期，按截止时间升序（最近截止优先）；
 * - 通知待处理 = 未 mark ntdone，按时间倒序；
 * - 电费卡 = 关注宿舍（userdata.power.sel）+ 读数/状态；
 * - 轮换：每 5s 下一条（页面不可见时暂停由 UI 层控制）；
 * - v5：`heroState()` 汇总「今日 hero」（上课中/下一节/已结束/无课/未同步 + 进度条）。
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

    /** 待处理通知（§2.4 口径：普通通知需有时间锚点或发布时间；长期安排仅期次窗口内且该期未处理；资料/无时间兜底不计）。 */
    fun noticePendingEntries(
        entries: List<NoticeEntry>,
        marks: MarksSnapshot,
        nowMillis: Long,
        hiddenIds: Set<String> = emptySet(),
    ): List<NoticeEntry> = entries
        .filter { entry ->
            !entry.hidden && entry.id !in hiddenIds && entry.rowId !in hiddenIds &&
                NoticeStatusMachine.isPending(
                group = entry.group,
                nowMillis = nowMillis,
                done = marks.isDone(MarkKind.NT_DONE, entry.id),
                doneRef = { ref -> marks.isDone(MarkKind.NT_DONE, ref) },
                publishedTs = NoticeStatusMachine.publishedTsOf(entry.group, entry.items, nowMillis) ?: 0L,
            )
        }
        .sortedWith(compareByDescending { it.ts })

    /** 通知轮换条目：标题 = 通知标题；副标 = 分类 + 状态（期次显示「本次 …」，长期定义显示「长期安排」）。 */
    fun noticeRotation(
        entries: List<NoticeEntry>,
        marks: MarksSnapshot,
        nowMillis: Long,
        hiddenIds: Set<String> = emptySet(),
    ): List<HomeRotationItem> = noticePendingEntries(entries, marks, nowMillis, hiddenIds).map { entry ->
        val occurrence = if (NoticeStatusMachine.isLongterm(entry.group)) {
            NoticeStatusMachine.occurrenceOf(entry.group, nowMillis)
        } else {
            null
        }
        val statusText = occurrence?.let { NoticeStatusMachine.occurrenceLabelOf(it, nowMillis) }
            ?: NoticeStatusMachine.statusOf(
                entry.group,
                nowMillis,
                publishedTs = NoticeStatusMachine.publishedTsOf(entry.group, entry.items, nowMillis),
            ).label
        val published = com.furina.classtools.ui.screens.notices.publishedAtOf(entry)
        val posted = com.furina.classtools.ui.screens.notices.formatPosted(published, nowMillis)
        HomeRotationItem(
            id = occurrence?.key ?: entry.id,
            title = entry.title,
            sub = listOf(entry.cat, posted, statusText).distinct().filter(String::isNotBlank).joinToString(" · "),
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
            it.state == SessionState.ONGOING
        } == true

    // ---- 今日 hero ----------------------------------------------------------

    /** 课程进度：`(now - start) / (end - start)`，钳制 0..1（时间异常时返回 0）。 */
    fun lessonProgress(startAt: Long, endAt: Long, nowMillis: Long): Float {
        val total = endAt - startAt
        if (total <= 0L) return 0f
        val elapsed = (nowMillis - startAt).coerceIn(0L, total)
        return (elapsed.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun courseStatus(session: SessionInfo, nowMillis: Long): String {
        if (session.state == SessionState.ONGOING) return "距下课 ${minutesUntil(session.endAt, nowMillis)} 分钟"
        val minutes = minutesUntil(session.startAt, nowMillis)
        return if (minutes in 1..10) "距上课 $minutes 分钟" else "下一节"
    }

    /** 距离 [atMillis] 的分钟数（向上取整；已过返回 0）。 */
    fun minutesUntil(atMillis: Long, nowMillis: Long): Int {
        val delta = atMillis - nowMillis
        if (delta <= 0L) return 0
        return ((delta + 59_999L) / 60_000L).toInt()
    }

    /**
     * 工作台顶部「今日」hero 状态。
     *
     * 优先级：上课中 > 今日下一节 > 今日已结束 > 今日无课 > 未同步。
     * 进度条口径：
     * - 上课中 = 本节课已上比例；
     * - 下一节 / 已结束 = 今日已完成节数比例（无今日课程时不展示）；
     * - 未同步 / 无课 = 不展示。
     */
    fun heroState(plan: TtPlan?, loading: Boolean, nowMillis: Long): HomeHeroState {
        if (plan == null || !plan.hasData) {
            return if (loading) {
                HomeHeroState(
                    kind = "loading",
                    tag = "同步中",
                    title = "正在读取今日课表",
                    detail = "稍后自动更新",
                    hint = "",
                    progress = null,
                    progressText = "",
                )
            } else {
                HomeHeroState(
                    kind = "nodata",
                    tag = "未同步",
                    title = "课表未同步",
                    detail = "打开课表拉取本周课程",
                    hint = "点此打开课表",
                    progress = null,
                    progressText = "",
                )
            }
        }

        val ongoing = plan.today.firstOrNull { it.state == SessionState.ONGOING }
        if (ongoing != null) {
            val progress = lessonProgress(ongoing.startAt, ongoing.endAt, nowMillis)
            val remainMin = ((ongoing.endAt - nowMillis).coerceAtLeast(0L) / 60_000L).toInt()
            return HomeHeroState(
                kind = "ongoing",
                tag = "上课中",
                title = ongoing.name,
                detail = courseDetail(ongoing.startText, ongoing.endText, ongoing.room),
                hint = if (remainMin > 0) "还剩 $remainMin 分钟" else "即将下课",
                progress = progress,
                progressText = "已上 ${(progress * 100f).roundToInt()}%",
            )
        }

        val totalToday = plan.today.size
        val doneToday = plan.today.count { it.state == SessionState.DONE }
        val nextToday = plan.today.firstOrNull { it.state == SessionState.NEXT }
        if (nextToday != null) {
            val minutes = minutesUntil(nextToday.startAt, nowMillis)
            return HomeHeroState(
                kind = "next",
                tag = "下一节",
                title = nextToday.name,
                detail = courseDetail(nextToday.startText, nextToday.endText, nextToday.room),
                hint = if (minutes in 1..10) courseStatus(nextToday, nowMillis) else "",
                progress = if (totalToday > 0) doneToday.toFloat() / totalToday.toFloat() else null,
                progressText = "今日 $doneToday/$totalToday 节",
            )
        }

        val next = plan.next
        if (plan.todayState == TodayState.DONE && totalToday > 0) {
            return HomeHeroState(
                kind = "done",
                tag = "已结束",
                title = "今日课程已结束",
                detail = next?.let { "下次 ${it.dayText} ${it.span} ${it.name}" } ?: "近期暂无课程",
                hint = "",
                progress = 1f,
                progressText = "今日 $totalToday/$totalToday 节",
            )
        }

        return HomeHeroState(
            kind = "none",
            tag = "今日无课",
            title = "今天没课",
            detail = next?.let { "下次 ${it.dayText} ${it.span} ${it.name}" } ?: "近期暂无课程",
            hint = "",
            progress = null,
            progressText = "",
        )
    }

    /** `08:10–09:40 · 01104`（空项自动省略；时间相同或结束为空时只显示开始时间）。 */
    private fun courseDetail(startText: String, endText: String, room: String): String = buildList {
        val start = startText.trim()
        val end = endText.trim()
        when {
            start.isNotEmpty() && end.isNotEmpty() && start != end -> add("$start–$end")
            start.isNotEmpty() -> add(start)
            end.isNotEmpty() -> add(end)
        }
        room.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
    }.joinToString(" · ")

    /** 供 hero 展示的今日课程列表（含进行中/已结束；空表 = 无数据）。 */
    fun heroSessions(plan: TtPlan?): List<SessionInfo> = plan?.today.orEmpty()

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
