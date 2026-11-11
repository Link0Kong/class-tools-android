package com.furina.classtools.data.homework

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 作业截止时间计算（纯函数，可单测）。
 *
 * 对齐网页 `hwDue()/dueInfo()/hwExpired()/pruneHW()`：
 * - `date` = `YYYY-MM-DD`；`time` 可选 `HH:MM`（为空按当天 00:00）；
 * - 展示：<24h「还剩 N 小时」，≥24h「还剩 N 天」，过期「已过期 N 小时/N 天」；
 * - 过期超过 **3 天** 从列表内存过滤（不写云、不 DELETE）。
 */
object HomeworkDueCalculator {

    private const val PRUNE_AFTER_DAYS = 3L
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA)
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val fullFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)

    /** 解析 `date`(+`time`) → epoch ms；无法解析返回 null。 */
    fun parseDue(date: String, time: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val d = date.trim()
        if (d.isEmpty()) return null
        val localDate = try {
            LocalDate.parse(d)
        } catch (_: Throwable) {
            return null
        }
        val localTime = if (time.isBlank()) {
            LocalTime.MIDNIGHT
        } else {
            try {
                LocalTime.parse(time.trim())
            } catch (_: Throwable) {
                LocalTime.MIDNIGHT
            }
        }
        return LocalDateTime.of(localDate, localTime).atZone(zone).toInstant().toEpochMilli()
    }

    /** 截止信息；无日期返回 null。 */
    fun evaluate(row: HomeworkRow, nowMillis: Long = System.currentTimeMillis()): HomeworkDue? {
        val dueAt = parseDue(row.date, row.time) ?: return null
        val diff = dueAt - nowMillis
        val prunable = nowMillis > dueAt + PRUNE_AFTER_DAYS * 24 * 60 * 60 * 1000
        return when {
            diff < 0 -> {
                val overdue = -diff
                if (overdue < 24 * 60 * 60 * 1000) {
                    val hours = maxOf(1, (overdue / (60 * 60 * 1000)).toInt())
                    HomeworkDue(
                        atMillis = dueAt,
                        label = "已过期 $hours 小时",
                        state = HomeworkDueState.OVERDUE_HOURS,
                        tone = HomeworkDueTone.OVERDUE,
                        expired = true,
                        prunable = prunable,
                    )
                } else {
                    val days = (overdue / (24 * 60 * 60 * 1000)).toInt()
                    HomeworkDue(
                        atMillis = dueAt,
                        label = "已过期 $days 天",
                        state = HomeworkDueState.OVERDUE_DAYS,
                        tone = HomeworkDueTone.OVERDUE,
                        expired = true,
                        prunable = prunable,
                    )
                }
            }
            diff < 24 * 60 * 60 * 1000 -> {
                val hours = maxOf(1, (diff / (60 * 60 * 1000)).toInt())
                HomeworkDue(
                    atMillis = dueAt,
                    label = "还剩 $hours 小时",
                    state = HomeworkDueState.HOURS_LEFT,
                    tone = HomeworkDueTone.URGENT,
                    expired = false,
                    prunable = false,
                )
            }
            else -> {
                val days = ((diff + 24 * 60 * 60 * 1000 - 1) / (24 * 60 * 60 * 1000)).toInt()
                HomeworkDue(
                    atMillis = dueAt,
                    label = "还剩 $days 天",
                    state = HomeworkDueState.DAYS_LEFT,
                    tone = if (days <= 3) HomeworkDueTone.WARN else HomeworkDueTone.SAFE,
                    expired = false,
                    prunable = false,
                )
            }
        }
    }

    /** 是否过期（有截止且 now 超过截止）。 */
    fun isExpired(row: HomeworkRow, nowMillis: Long = System.currentTimeMillis()): Boolean =
        parseDue(row.date, row.time)?.let { nowMillis > it } ?: false

    /** 行 → UI 条目（附件元信息 + 完成状态由 marks 决定，入参传入）。 */
    fun toItem(
        row: HomeworkRow,
        done: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): HomeworkItem = HomeworkItem(
        row = row,
        done = done,
        due = evaluate(row, nowMillis),
        attachments = HomeworkAttachments.parse(row.attachments),
    )

    /** 过滤：过期 >3 天的丢弃（内存过滤，契约要求）。 */
    fun pruneExpired(items: List<HomeworkItem>): List<HomeworkItem> =
        items.filterNot { it.due?.prunable == true }

    /** 筛选（全部/未完成/已完成）。 */
    fun filter(items: List<HomeworkItem>, filter: HomeworkFilter): List<HomeworkItem> = when (filter) {
        HomeworkFilter.ALL -> items
        HomeworkFilter.UNFINISHED -> items.filterNot { it.done }
        HomeworkFilter.DONE -> items.filter { it.done }
    }

    /**
     * 排序：未完成在前；未完成按截止升序（无截止排最后）；已完成沉底（按截止降序）。
     * 与网页 `renderHW` 的排序语义一致（完成项沉底）。
     */
    fun sort(items: List<HomeworkItem>): List<HomeworkItem> = items.sortedWith(
        compareBy<HomeworkItem> { it.done }
            .thenBy { if (it.done) Long.MAX_VALUE - (it.due?.atMillis ?: 0L) else (it.due?.atMillis ?: Long.MAX_VALUE) }
            .thenBy { it.row.ts },
    )

    /** 分组标题：今天 / 明天 / 已过期 / MM-dd 周X / 无截止日期。 */
    fun groupLabel(item: HomeworkItem, nowMillis: Long = System.currentTimeMillis()): String {
        val due = item.due ?: return "无截止日期"
        if (due.expired) return "已过期"
        val zone = ZoneId.systemDefault()
        val nowDate = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val dueDate = java.time.Instant.ofEpochMilli(due.atMillis).atZone(zone).toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(nowDate, dueDate)
        return when (days) {
            0L -> "今天 · ${dateFormatter.format(dueDate)}"
            1L -> "明天 · ${dateFormatter.format(dueDate)}"
            else -> "${dateFormatter.format(dueDate)} ${weekdayZh(dueDate)}"
        }
    }

    /** 截止时间完整文本（详情用）：`MM-dd HH:mm`。 */
    fun dueText(row: HomeworkRow): String {
        val at = parseDue(row.date, row.time) ?: return row.due.ifBlank { "无截止日期" }
        return fullFormatter.format(java.time.Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))
    }

    /** 发布/更新时间文本（详情用）：`MM-dd HH:mm`。 */
    fun tsText(ts: Long): String {
        if (ts <= 0) return ""
        return fullFormatter.format(java.time.Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()))
    }

    /** 行内的时间文本（列表 meta 用）。 */
    fun shortDueText(row: HomeworkRow): String {
        val at = parseDue(row.date, row.time) ?: return ""
        val zoned = java.time.Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        return "${dateFormatter.format(zoned)} ${timeFormatter.format(zoned)}"
    }

    private fun weekdayZh(date: LocalDate): String = when (date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }
}
