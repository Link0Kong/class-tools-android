package com.furina.classtools.data.visits

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 统计页纯逻辑（唯一口径：`web/统计多端_最新登录_契约_20261118.md` §2）。
 *
 * - 按每个账号 `max(ts)` 倒序（次数只做副指标）；
 * - 时间显示 `MM/dd HH:mm`；旧行 `ts=0` 回退 `slot` 整点；
 * - 账号行显示最近登录端（web/mp/android/win/harmony）；
 * - 过滤官方测试身份 `3260799999`、9999 段 10 位 QA 身份与匿名行；
 * - 范围今天 / 近 7 天。
 *
 * 不依赖 Android/Compose，便于 JVM 单测。
 */
object VisitStats {

    const val OFFICIAL_TEST_SID = "3260799999"
    const val UNKNOWN_NAME = "班级成员"

    private val SLOT_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu-MM-dd-HH", Locale.ROOT)
    private val TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.ROOT)

    fun isTestAccount(sid: String?): Boolean {
        val s = sid?.trim().orEmpty()
        return s == OFFICIAL_TEST_SID || (s.length == 10 && s.startsWith("9999"))
    }

    /** 毫秒时间；旧行 ts=0 时回退 slot 的整点。 */
    fun effectiveTs(row: VisitRow, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        if (row.ts > 0) return row.ts
        return slotToMillis(row.slot, zoneId)
    }

    fun slotToMillis(slot: String?, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val s = slot?.trim().orEmpty()
        if (s.length < 13) return 0
        return runCatching {
            LocalDateTime.parse(s, SLOT_FORMAT)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0)
    }

    fun formatTime(ts: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        if (ts <= 0) return ""
        return runCatching {
            Instant.ofEpochMilli(ts).atZone(zoneId).format(TIME_FORMAT)
        }.getOrDefault("")
    }

    fun clientLabel(client: String?): String = when (client?.trim()?.lowercase(Locale.ROOT)) {
        "web" -> "网页"
        "mp" -> "小程序"
        "android" -> "安卓"
        "win" -> "Windows"
        "harmony" -> "鸿蒙"
        else -> "未记录"
    }

    /**
     * 构建统计视图。
     *
     * @param rows 原始 visits 行（可包含匿名/测试行，内部过滤）
     * @param range 今天 / 近 7 天
     * @param nowMillis 当前时间（测试可注入，默认取调用时）
     * @param nameOfSid 名单姓名兜底（visits 行没有姓名时用）
     */
    fun build(
        rows: List<VisitRow>,
        range: VisitRange,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        nameOfSid: (String) -> String? = { null },
    ): VisitsSnapshot {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val firstDay = if (range == VisitRange.WEEK) today.minusDays(6) else today

        val filtered = rows.filter { row ->
            val sid = row.sid.trim()
            sid.isNotEmpty() && !isTestAccount(sid) &&
                dayOf(effectiveTs(row, zoneId), zoneId).let { !it.isBefore(firstDay) && !it.isAfter(today) }
        }

        val accounts = filtered.groupBy { it.sid.trim() }.mapNotNull { (sid, list) ->
            val latest = list.maxByOrNull { effectiveTs(it, zoneId) } ?: return@mapNotNull null
            val lastTs = effectiveTs(latest, zoneId)
            if (lastTs <= 0) return@mapNotNull null
            val days = list.map { dayOf(effectiveTs(it, zoneId), zoneId) }.toSet()
            val firstName = list.asSequence().map { it.name.trim() }.firstOrNull { it.isNotEmpty() }
            val name = firstName
                ?: nameOfSid(sid)?.trim()?.takeIf { it.isNotEmpty() }
                ?: UNKNOWN_NAME
            val detail = buildDetail(range, days.size, lastTs, latest.client, zoneId)
            VisitAccount(
                sid = sid,
                name = name,
                count = list.size,
                lastTs = lastTs,
                client = latest.client,
                clientText = clientLabel(latest.client),
                lastText = formatTime(lastTs, zoneId),
                activeDays = days.size,
                activeToday = days.contains(today),
                detail = detail,
            )
        }.sortedWith(
            compareByDescending<VisitAccount> { it.lastTs }
                .thenByDescending { it.count }
                .thenBy { it.name },
        )

        return VisitsSnapshot(
            accounts = accounts,
            total = accounts.sumOf { it.count },
            activeToday = accounts.count { it.activeToday },
        )
    }

    fun buildDetail(
        range: VisitRange,
        activeDays: Int,
        lastTs: Long,
        client: String?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val parts = mutableListOf<String>()
        if (range == VisitRange.WEEK) parts.add("活跃 $activeDays/7 天")
        formatTime(lastTs, zoneId).takeIf { it.isNotEmpty() }?.let { parts.add("最近 $it") }
        parts.add(clientLabel(client))
        return parts.joinToString(" · ")
    }

    private fun dayOf(ts: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(ts).atZone(zoneId).toLocalDate()
}
