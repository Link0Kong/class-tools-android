package com.furina.classtools.ui.screens.home

import com.furina.classtools.data.timetable.CourseFocus
import com.furina.classtools.data.timetable.DefaultTimetableRepository
import com.furina.classtools.data.timetable.SessionInfo
import com.furina.classtools.data.timetable.SessionState
import com.furina.classtools.data.timetable.TodayState
import com.furina.classtools.data.timetable.TtPeriods
import com.furina.classtools.data.timetable.TtPlan
import com.furina.classtools.data.timetable.TtSnapshot
import com.furina.classtools.data.timetable.TtSource
import com.furina.classtools.features.home.HomeTopic
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal data class HomeTimetableSnapshot(
    val snapshot: TtSnapshot,
    val source: TtSource? = null,
    val fetchedAt: Long = 0,
) {
    fun isFresh(now: Long): Boolean = source == TtSource.PERSONAL ||
        source != null && fetchedAt > 0 && now - fetchedAt in 0 until DefaultTimetableRepository.DEFAULT_TTL_MS
}

internal data class HomeCourseContext(
    val status: String?,
    val topic: HomeTopic? = null,
    val current: SessionInfo? = null,
    val next: SessionInfo? = null,
    val action: String? = null,
    val focus: CourseFocus? = null,
    val reloading: Boolean = false,
    val cached: Boolean = false,
) {
    val occurrence: Long? get() = (current ?: next)?.startAt
}

internal fun homeCourseContext(
    plan: TtPlan?,
    state: HomeLoadState<HomeTimetableSnapshot>?,
    now: Long,
    date: LocalDate,
    zone: ZoneId,
): HomeCourseContext {
    val today = CourseFocus(date.toString())
    if (state?.loading == true && state.data?.isFresh(now) != true) {
        return HomeCourseContext("正在读取课表", reloading = true)
    }
    if (state?.error != null || state?.data != null && !state.data.isFresh(now)) {
        return HomeCourseContext(
            status = if (plan?.hasData == true) "课表待更新 · 缓存参考" else "课表读取失败",
            next = plan?.next?.takeIf { it.startAt > now },
            action = "打开课表",
            focus = today,
            cached = true,
        )
    }
    if (plan?.hasData != true) {
        return HomeCourseContext(
            if (state?.data?.snapshot?.c?.isNotEmpty() == true) "课表缺少有效的开学日期" else "尚未配置课表",
            action = "打开课表", focus = today,
        )
    }

    val current = plan.today.firstOrNull { it.state == SessionState.ONGOING && now in it.startAt until it.endAt }
    val next = plan.next?.takeIf { it.startAt > now }
    val todayNext = next?.takeIf { it.dayOffset == 0 }
    val near = todayNext != null && HomeWorkbenchLogic.minutesUntil(todayNext.startAt, now) in 1..10
    val isBreak = current == null && near && todayNext.afterShortBreak &&
        inShortBreak(todayNext, date, zone, now)
    val topic = when {
        current != null -> HomeTopic.ONGOING
        isBreak -> HomeTopic.BREAK
        near -> HomeTopic.UPCOMING
        plan.todayState == TodayState.DONE -> HomeTopic.ENDED
        plan.todayState == TodayState.NONE && plan.week != null -> HomeTopic.NO_CLASS
        else -> null
    }
    val status = when (topic) {
        HomeTopic.ENDED -> "今日课程已结束"
        HomeTopic.NO_CLASS -> "今日无课"
        HomeTopic.BREAK -> "课间"
        HomeTopic.UPCOMING -> "即将上课"
        else -> "学期尚未开始".takeIf { plan.week == null && plan.today.isEmpty() }
    }
    val aim = current ?: next
    return when {
        current == null && next?.dayOffset == 1 && topic in setOf(HomeTopic.ENDED, HomeTopic.NO_CLASS) ->
            HomeCourseContext(status, topic, next = next, action = "查看明日课表",
                focus = CourseFocus(date.plusDays(1).toString()))
        aim != null -> HomeCourseContext(status, topic, current, next, "查看课程", CourseFocus.of(aim, zone))
        else -> HomeCourseContext(status, topic, action = "打开课表", focus = today)
    }
}

private fun inShortBreak(next: SessionInfo, date: LocalDate, zone: ZoneId, now: Long): Boolean {
    val period = TtPeriods.PERIODS.indexOfFirst { it.first == next.startText }
    val previousEnd = TtPeriods.PERIODS.getOrNull(period - 1)?.second ?: return false
    val boundary = date.atTime(LocalTime.parse(previousEnd)).atZone(zone).toInstant().toEpochMilli()
    return now in boundary until next.startAt
}
