package com.furina.classtools.data.visits

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 统计页纯逻辑（唯一口径：`web/统计多端_详细统计_契约_20261120.md` §3）。
 *
 * - 过滤：空 sid（匿名行）、官方测试身份 `3260799999`、9999 段 10 位 QA 身份、范围外/未来行；
 * - 时间：`effectiveTs = ts > 0 ? ts : slot 整点`，按本地时区取日期；显示 `MM/dd HH:mm`；
 * - 范围：今天 = 本地今天；近 7 天 = 今天及之前 6 天（共 7 个自然日）；
 * - 账号聚合：count / firstTs / lastTs / 最近端·版本·设备 / activeDays / 去重设备数 /
 *   登录端·版本·设备分组；
 * - 全局聚合：摘要 4 项、趋势（今天 24 小时 / 近 7 天 7 天）、登录端、版本、设备；
 * - 排序：账号 `count` 降序 → `lastTs` 降序 → `name` 升序；
 *   登录端 `count` 降序 → 标签升序；版本 `count` 降序 → 版本号降序（空版本最后）；
 *   设备 `count` 降序 → 最近时间降序；
 * - 版本/设备分组最多 6 组 + 「其他」；百分比分母 = 当前范围过滤后的总行数，
 *   最后一段补齐到 100%。
 *
 * 不依赖 Android/Compose，便于 JVM 单测。
 */
object VisitStats {

    const val OFFICIAL_TEST_SID = "3260799999"

    /** 行里没有姓名且名单也查不到时的兜底（契约 §3.2）。 */
    const val UNKNOWN_NAME = "班级成员"

    /** 空 client / 空 ver / 空 dev 的展示文案（契约 §1/§3）。 */
    const val UNKNOWN_LABEL = VisitClientStyle.LABEL_UNKNOWN

    /** 超过 6 组后的聚合行文案。 */
    const val OTHER_LABEL = "其他"

    /** 版本/设备分组最多展示的真实组数（再加一行「其他」）。 */
    const val MAX_DISTRIBUTION_ROWS = 6

    /** 今天趋势柱数量。 */
    const val TREND_HOURS = 24

    /** 近 7 天趋势柱数量。 */
    const val TREND_DAYS = 7

    private const val OTHER_KEY = "\u0000other"

    private val SLOT_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu-MM-dd-HH", Locale.ROOT)
    private val TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.ROOT)
    private val DAY_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MM/dd", Locale.ROOT)

    /** 带原始输入下标的行：同时间「取输入顺序靠后者」需要 index 参与比较。 */
    private data class TimedRow(val row: VisitRow, val ts: Long, val index: Int)

    /** 版本分组中间态（key 为空串 = 未记录；OTHER_KEY = 其他）。 */
    private data class VersionGroup(val key: String, val count: Int)

    /** 设备分组中间态。 */
    private data class DeviceGroup(
        val client: String,
        val label: String,
        val count: Int,
        val lastTs: Long,
    )

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

    /** `MM/dd HH:mm`；ts<=0 或异常返回空串（页面不显示假时间）。 */
    fun formatTime(ts: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        if (ts <= 0) return ""
        return runCatching {
            Instant.ofEpochMilli(ts).atZone(zoneId).format(TIME_FORMAT)
        }.getOrDefault("")
    }

    /** 端中文标签（契约 §4 固定表；空/未知 = 未记录）。 */
    fun clientLabel(client: String?): String = VisitClientStyle.labelOf(client)

    /** 版本展示文案：去空白，空 = 未记录。 */
    fun versionLabel(ver: String?): String =
        ver?.trim().orEmpty().ifEmpty { UNKNOWN_LABEL }

    /** 设备展示文案：合并连续空白，空 = 未记录（与写入侧同口径）。 */
    fun deviceLabel(dev: String?): String =
        dev?.trim().orEmpty().replace(WHITESPACE, " ").ifEmpty { UNKNOWN_LABEL }

    /**
     * 构建统计视图。
     *
     * @param rows 原始 visits 行（可包含匿名/测试/范围外行，内部过滤）
     * @param range 今天 / 近 7 天
     * @param nowMillis 当前时间（测试可注入，默认取调用时）
     * @param zoneId 本地时区（测试可注入）
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

        val filtered = rows.mapIndexedNotNull { index, row ->
            val sid = row.sid.trim()
            if (sid.isEmpty() || isTestAccount(sid)) return@mapIndexedNotNull null
            val ts = effectiveTs(row, zoneId)
            val day = dayOf(ts, zoneId)
            if (day.isBefore(firstDay) || day.isAfter(today)) return@mapIndexedNotNull null
            TimedRow(row = row, ts = ts, index = index)
        }

        val accounts = filtered
            .groupBy { it.row.sid.trim() }
            .mapNotNull { (sid, list) ->
                accountOf(sid, list, range, today, zoneId, nameOfSid)
            }
            .sortedWith(
                compareByDescending<VisitAccount> { it.count }
                    .thenByDescending { it.lastTs }
                    .thenBy { it.name },
            )

        return VisitsSnapshot(
            accounts = accounts,
            total = filtered.size,
            activeToday = accounts.count { it.activeToday },
            deviceTotal = distinctDeviceKeys(filtered).size,
            clients = clientStats(filtered, filtered.size),
            versions = versionStats(filtered, filtered.size),
            devices = deviceStats(filtered, filtered.size),
            trend = buildTrend(range, filtered, today, firstDay, zoneId),
        )
    }

    /**
     * 账号明细文案：`活跃 N/7 天 · 首次 MM/dd HH:mm · 最近 MM/dd HH:mm`
     * （今天范围省略活跃段，符合契约 §3.2）。
     */
    fun buildDetailText(
        range: VisitRange,
        activeDays: Int,
        firstTs: Long,
        lastTs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val parts = mutableListOf<String>()
        if (range == VisitRange.WEEK) parts.add("活跃 $activeDays/7 天")
        formatTime(firstTs, zoneId).takeIf { it.isNotEmpty() }?.let { parts.add("首次 $it") }
        formatTime(lastTs, zoneId).takeIf { it.isNotEmpty() }?.let { parts.add("最近 $it") }
        return parts.joinToString(" · ")
    }

    // ------------------------------------------------------------------ 账号聚合

    private fun accountOf(
        sid: String,
        list: List<TimedRow>,
        range: VisitRange,
        today: LocalDate,
        zoneId: ZoneId,
        nameOfSid: (String) -> String?,
    ): VisitAccount? {
        if (list.isEmpty()) return null
        // 最近：时间最大；同时间取输入顺序靠后者（契约 §3.2）。
        val latest = list.maxWithOrNull(compareBy<TimedRow> { it.ts }.thenBy { it.index }) ?: return null
        val earliest = list.minWithOrNull(compareBy<TimedRow> { it.ts }.thenBy { it.index }) ?: return null
        if (latest.ts <= 0) return null

        val days = list.mapTo(HashSet()) { dayOf(it.ts, zoneId) }
        val name = latestName(list) ?: rosterName(sid, nameOfSid) ?: UNKNOWN_NAME
        val total = list.size
        val firstTs = earliest.ts
        val lastTs = latest.ts

        return VisitAccount(
            sid = sid,
            name = name,
            count = total,
            firstTs = firstTs,
            lastTs = lastTs,
            firstText = formatTime(firstTs, zoneId),
            lastText = formatTime(lastTs, zoneId),
            client = VisitClientStyle.normalize(latest.row.client),
            clientText = VisitClientStyle.labelOf(latest.row.client),
            ver = latest.row.ver.trim(),
            verText = versionLabel(latest.row.ver),
            dev = normalizeDev(latest.row.dev),
            devText = deviceLabel(latest.row.dev),
            deviceCount = distinctDeviceKeys(list).size,
            activeDays = days.size,
            activeToday = days.contains(today),
            clients = clientStats(list, total),
            versions = versionStats(list, total),
            devices = deviceStats(list, total),
            detailText = buildDetailText(range, days.size, firstTs, lastTs, zoneId),
        )
    }

    /** 最近一条有姓名的行；同时间取输入顺序靠后者。 */
    private fun latestName(list: List<TimedRow>): String? = list
        .sortedWith(compareByDescending<TimedRow> { it.ts }.thenByDescending { it.index })
        .asSequence()
        .map { it.row.name.trim() }
        .firstOrNull { it.isNotEmpty() }

    private fun rosterName(sid: String, nameOfSid: (String) -> String?): String? =
        runCatching { nameOfSid(sid) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    // ------------------------------------------------------------------ 分组聚合

    /** 登录端分组：次数降序 → 标签升序；空/未知客户端归入「未记录」。 */
    private fun clientStats(rows: List<TimedRow>, total: Int): List<VisitClientStat> {
        val groups = rows
            .groupBy { VisitClientStyle.normalize(it.row.client) }
            .map { (code, items) -> code to items.size }
            .sortedWith(
                compareByDescending<Pair<String, Int>> { it.second }
                    .thenBy { VisitClientStyle.labelOf(it.first) },
            )
        val percents = percents(groups.map { it.second }, total)
        return groups.mapIndexed { index, (code, count) ->
            VisitClientStat(
                code = code,
                label = VisitClientStyle.labelOf(code),
                count = count,
                percent = percents[index],
            )
        }
    }

    /**
     * 版本分组：次数降序 → 版本号降序（空版本「未记录」在次数相同时排最后）；
     * 最多 6 组 + 「其他」。
     */
    private fun versionStats(rows: List<TimedRow>, total: Int): List<VisitVerStat> {
        val groups = rows
            .groupBy { it.row.ver.trim() }
            .map { (key, items) -> VersionGroup(key, items.size) }
            .sortedWith(
                compareByDescending<VersionGroup> { it.count }
                    .thenByDescending { it.key.isNotEmpty() }
                    .thenByDescending { it.key },
            )
        val capped = capVersions(groups)
        val percents = percents(capped.map { it.count }, total)
        return capped.mapIndexed { index, group ->
            VisitVerStat(
                label = if (group.key == OTHER_KEY) OTHER_LABEL else versionLabel(group.key),
                count = group.count,
                percent = percents[index],
            )
        }
    }

    private fun capVersions(groups: List<VersionGroup>): List<VersionGroup> {
        if (groups.size <= MAX_DISTRIBUTION_ROWS) return groups
        val head = groups.take(MAX_DISTRIBUTION_ROWS)
        val rest = groups.drop(MAX_DISTRIBUTION_ROWS)
        return head + VersionGroup(OTHER_KEY, rest.sumOf { it.count })
    }

    /**
     * 设备分组：`client|dev`，次数降序 → 最近时间降序；空 dev「未记录」；
     * 最多 6 组 + 「其他」。
     */
    private fun deviceStats(rows: List<TimedRow>, total: Int): List<VisitDeviceStat> {
        val groups = rows
            .groupBy { row ->
                VisitClientStyle.normalize(row.row.client) to deviceLabel(row.row.dev)
            }
            .map { (key, items) ->
                DeviceGroup(
                    client = key.first,
                    label = key.second,
                    count = items.size,
                    lastTs = items.maxOf { it.ts },
                )
            }
            .sortedWith(
                compareByDescending<DeviceGroup> { it.count }
                    .thenByDescending { it.lastTs }
                    .thenBy { it.label },
            )
        val capped = capDevices(groups)
        val percents = percents(capped.map { it.count }, total)
        return capped.mapIndexed { index, group ->
            VisitDeviceStat(
                client = group.client,
                clientLabel = if (group.client.isEmpty()) "" else VisitClientStyle.labelOf(group.client),
                label = group.label,
                count = group.count,
                lastTs = group.lastTs,
                percent = percents[index],
            )
        }
    }

    private fun capDevices(groups: List<DeviceGroup>): List<DeviceGroup> {
        if (groups.size <= MAX_DISTRIBUTION_ROWS) return groups
        val head = groups.take(MAX_DISTRIBUTION_ROWS)
        val rest = groups.drop(MAX_DISTRIBUTION_ROWS)
        return head + DeviceGroup(
            client = "",
            label = OTHER_LABEL,
            count = rest.sumOf { it.count },
            lastTs = rest.maxOfOrNull { it.lastTs } ?: 0,
        )
    }

    /**
     * 去重设备数：优先 `did` 非空的不同值；没有 did 的行按 `client|dev` 兜底
     * （契约 §3.2 `deviceCount`）。
     */
    private fun distinctDeviceKeys(rows: List<TimedRow>): Set<String> = rows.mapTo(HashSet()) { row ->
        val did = row.row.did.trim()
        if (did.isNotEmpty()) {
            "did:$did"
        } else {
            "dev:${VisitClientStyle.normalize(row.row.client)}|${deviceLabel(row.row.dev)}"
        }
    }

    // ------------------------------------------------------------------ 趋势

    private fun buildTrend(
        range: VisitRange,
        rows: List<TimedRow>,
        today: LocalDate,
        firstDay: LocalDate,
        zoneId: ZoneId,
    ): List<VisitTrendPoint> {
        if (range == VisitRange.TODAY) {
            val counts = IntArray(TREND_HOURS)
            rows.forEach { timed ->
                val time = Instant.ofEpochMilli(timed.ts).atZone(zoneId)
                if (time.toLocalDate() == today) counts[time.hour] += 1
            }
            return (0 until TREND_HOURS).map { hour ->
                VisitTrendPoint(
                    // 契约 §3.3：标签只显示 00/03/06/09/12/15/18/21/23，避免 24 根柱挤在一起。
                    label = if (hour % 3 == 0 || hour == TREND_HOURS - 1) {
                        hour.toString().padStart(2, '0')
                    } else {
                        ""
                    },
                    count = counts[hour],
                )
            }
        }

        val counts = IntArray(TREND_DAYS)
        rows.forEach { timed ->
            val day = dayOf(timed.ts, zoneId)
            val index = ChronoUnit.DAYS.between(firstDay, day).toInt()
            if (index in 0 until TREND_DAYS) counts[index] += 1
        }
        return (0 until TREND_DAYS).map { index ->
            val day = firstDay.plusDays(index.toLong())
            VisitTrendPoint(
                label = DAY_FORMAT.format(day),
                count = counts[index],
                highlighted = day == today,
            )
        }
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 百分比：每个值四舍五入到整数，最后一段用 `100 - 前面之和` 补齐到 100
     * （契约 §3.4）。[total] 为分母（当前范围过滤后的总行数）。
     */
    private fun percents(counts: List<Int>, total: Int): List<Int> {
        if (counts.isEmpty()) return emptyList()
        if (total <= 0) return List(counts.size) { 0 }
        val result = MutableList(counts.size) { 0 }
        var used = 0
        counts.indices.forEach { index ->
            val value = if (index == counts.lastIndex) {
                (100 - used).coerceAtLeast(0)
            } else {
                (counts[index].toDouble() * 100.0 / total).roundToInt().coerceIn(0, 100 - used)
            }
            result[index] = value
            used += value
        }
        return result
    }

    private fun normalizeDev(dev: String?): String =
        dev?.trim().orEmpty().replace(WHITESPACE, " ")

    private fun dayOf(ts: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(ts).atZone(zoneId).toLocalDate()

    private val WHITESPACE: Regex = Regex("\\s+")
}
