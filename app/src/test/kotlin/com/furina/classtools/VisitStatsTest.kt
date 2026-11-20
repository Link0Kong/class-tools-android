package com.furina.classtools

import com.furina.classtools.data.visits.VisitClientStyle
import com.furina.classtools.data.visits.VisitRange
import com.furina.classtools.data.visits.VisitRow
import com.furina.classtools.data.visits.VisitStats
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 访问统计纯逻辑单测（契约 `web/统计多端_详细统计_契约_20261120.md` §3）：
 * 过滤 / 排序 / 分钟时间 / 登录端 / 版本 / 设备 / 趋势 / 百分比 / 旧行未记录。
 *
 * 夹具只用官方测试身份 `3260799999`、9999 段 QA 身份与 9000 段虚构成员；不含真实学号。
 */
class VisitStatsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Long = LocalDateTime.of(2026, 11, 18, 21, 32)
        .atZone(zone).toInstant().toEpochMilli()
    private val today: String = "2026-11-18"
    private val yesterday: String = "2026-11-17"

    private fun ts(day: String, hour: Int, minute: Int): Long =
        LocalDateTime.of(
            day.substring(0, 4).toInt(),
            day.substring(5, 7).toInt(),
            day.substring(8, 10).toInt(),
            hour,
            minute,
        ).atZone(zone).toInstant().toEpochMilli()

    private fun visit(
        sid: String,
        name: String,
        day: String,
        hour: Int,
        minute: Int,
        client: String,
        ver: String = "",
        dev: String = "",
        did: String = "",
        tsOverride: Long? = null,
    ): VisitRow = VisitRow(
        slot = "$day-${hour.toString().padStart(2, '0')}",
        sid = sid,
        name = name,
        ts = tsOverride ?: ts(day, hour, minute),
        client = client,
        ver = ver,
        dev = dev,
        did = did,
    )

    /** 7 天窗口主夹具：3 个账号 / 4 行 + 测试/匿名行；覆盖端/版本/设备。 */
    private fun rows(): List<VisitRow> = listOf(
        visit(
            "9000000001", "张三", today, 10, 42, "web",
            ver = "2.0.0", dev = "Windows · Edge 131", did = "aaaa000000000001",
        ),
        visit(
            "9000000001", "张三", today, 11, 5, "android",
            ver = "1.0.0-beta14", dev = "Xiaomi 23127PN0CC · Android 15", did = "bbbb000000000002",
        ),
        visit(
            "9000000002", "李四", today, 9, 7, "mp",
            ver = "", dev = "Xiaomi 14 · Android 15", did = "cccc000000000003",
        ),
        visit(
            "9000000003", "王五", yesterday, 20, 15, "win",
            ver = "0.1.0", dev = "Windows 11 · x64",
        ),
        visit(
            "3260799999", "测试账号", today, 12, 30, "web",
            ver = "2.0.0", dev = "测试设备",
        ),
        visit(
            "9999000001", "测试同学", today, 12, 40, "mp",
            ver = "0.0.1", dev = "测试设备",
        ),
        visit("", "匿名", today, 8, 0, "web"),
    )

    // ------------------------------------------------------------ 过滤

    @Test
    fun isTestAccount_onlyOfficialAndQaRange() {
        assertTrue(VisitStats.isTestAccount("3260799999"))
        assertTrue(VisitStats.isTestAccount("9999000001"))
        assertFalse(VisitStats.isTestAccount("9000000001"))
        assertFalse(VisitStats.isTestAccount(""))
        assertFalse(VisitStats.isTestAccount(null))
    }

    @Test
    fun build_week_ignoresAnonymousAndTestAccounts() {
        val snapshot = VisitStats.build(rows(), VisitRange.WEEK, nowMillis = now, zoneId = zone)

        assertEquals(3, snapshot.accounts.size)
        assertEquals(4, snapshot.total)
        assertEquals(2, snapshot.activeToday)
    }

    @Test
    fun build_today_excludesYesterdayRows() {
        val snapshot = VisitStats.build(rows(), VisitRange.TODAY, nowMillis = now, zoneId = zone)

        assertEquals(2, snapshot.accounts.size)
        assertEquals(3, snapshot.total)
        assertTrue(snapshot.accounts.none { it.name == "王五" })
        assertTrue(snapshot.accounts.all { it.activeToday })
    }

    @Test
    fun build_excludesFutureRows() {
        val tomorrow = "2026-11-19"
        val snapshot = VisitStats.build(
            listOf(
                visit("9000000001", "甲", today, 10, 0, "web"),
                visit("9000000002", "乙", tomorrow, 9, 0, "mp"),
            ),
            VisitRange.WEEK,
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals(1, snapshot.accounts.size)
        assertEquals("9000000001", snapshot.accounts[0].sid)
        assertEquals(1, snapshot.total)
    }

    @Test
    fun build_emptyRange_returnsZeroSnapshotWithoutCharts() {
        val snapshot = VisitStats.build(emptyList(), VisitRange.WEEK, nowMillis = now, zoneId = zone)

        assertTrue(snapshot.accounts.isEmpty())
        assertEquals(0, snapshot.total)
        assertEquals(0, snapshot.activeToday)
        assertEquals(0, snapshot.deviceTotal)
        assertFalse(snapshot.hasData)
        assertTrue(snapshot.clients.isEmpty())
        assertTrue(snapshot.versions.isEmpty())
        assertTrue(snapshot.devices.isEmpty())
        assertEquals("空态不渲染空图表，但仍返回 7 个零值趋势点", 7, snapshot.trend.size)
        assertTrue(snapshot.trend.all { it.count == 0 })
    }

    // ------------------------------------------------------------ 账号聚合

    @Test
    fun build_week_aggregatesAccountFieldsAndSorts() {
        val snapshot = VisitStats.build(rows(), VisitRange.WEEK, nowMillis = now, zoneId = zone)

        val zhang = snapshot.accounts[0]
        assertEquals("张三", zhang.name)
        assertEquals(2, zhang.count)
        assertEquals("11/18 10:42", zhang.firstText)
        assertEquals("11/18 11:05", zhang.lastText)
        assertEquals("android", zhang.client)
        assertEquals("安卓", zhang.clientText)
        assertEquals("1.0.0-beta14", zhang.ver)
        assertEquals("1.0.0-beta14", zhang.verText)
        assertEquals("Xiaomi 23127PN0CC · Android 15", zhang.dev)
        assertEquals("Xiaomi 23127PN0CC · Android 15", zhang.devText)
        assertEquals(2, zhang.deviceCount)
        assertEquals(1, zhang.activeDays)
        assertTrue(zhang.activeToday)
        assertEquals(
            "活跃 1/7 天 · 首次 11/18 10:42 · 最近 11/18 11:05",
            zhang.detailText,
        )

        // 登录端：次数降序 → 标签升序（安卓 < 网页）。
        assertEquals(2, zhang.clients.size)
        assertEquals("安卓", zhang.clients[0].label)
        assertEquals("网页", zhang.clients[1].label)
        assertEquals(50, zhang.clients[0].percent)
        assertEquals(50, zhang.clients[1].percent)

        // 版本：次数相同按版本号降序。
        assertEquals(listOf("2.0.0", "1.0.0-beta14"), zhang.versions.map { it.label })

        // 设备：次数相同按最近时间降序。
        assertEquals(
            listOf("Xiaomi 23127PN0CC · Android 15", "Windows · Edge 131"),
            zhang.devices.map { it.label },
        )
        assertEquals("安卓", zhang.devices[0].clientLabel)

        // 次数相同（1 次）时今天访问更晚的排前。
        assertEquals("李四", snapshot.accounts[1].name)
        assertEquals("王五", snapshot.accounts[2].name)
    }

    @Test
    fun build_week_globalDistributionsAndTrend() {
        val snapshot = VisitStats.build(rows(), VisitRange.WEEK, nowMillis = now, zoneId = zone)

        // 4 个端各 1 次（mp 的 ver 为空 → 未记录）。
        assertEquals(4, snapshot.clients.size)
        assertEquals(listOf("Windows", "安卓", "小程序", "网页"), snapshot.clients.map { it.label })
        assertEquals(100, snapshot.clients.sumOf { it.percent })

        // 版本按次数降序 → 版本号降序，空版本「未记录」最后。
        assertEquals(listOf("2.0.0", "1.0.0-beta14", "0.1.0", "未记录"), snapshot.versions.map { it.label })
        assertEquals(100, snapshot.versions.sumOf { it.percent })

        // 全局设备按最近时间降序。
        assertEquals(4, snapshot.devices.size)
        assertEquals("Xiaomi 23127PN0CC · Android 15", snapshot.devices[0].label)
        assertEquals("Windows 11 · x64", snapshot.devices[3].label)
        assertEquals(100, snapshot.devices.sumOf { it.percent })

        // 趋势：7 个自然日，今天 3 次、昨天 1 次，今天高亮。
        assertEquals(7, snapshot.trend.size)
        assertEquals("11/12", snapshot.trend.first().label)
        assertEquals("11/18", snapshot.trend.last().label)
        assertEquals(3, snapshot.trend.last().count)
        assertTrue(snapshot.trend.last().highlighted)
        assertEquals(1, snapshot.trend[5].count)
        assertFalse(snapshot.trend[5].highlighted)
        assertEquals(4, snapshot.trend.sumOf { it.count })
    }

    @Test
    fun build_today_trendHas24HourBucketsAndAnchorLabels() {
        val snapshot = VisitStats.build(
            listOf(
                visit("9000000001", "甲", today, 0, 10, "web"),
                visit("9000000001", "甲", today, 9, 5, "web"),
                visit("9000000001", "甲", today, 9, 45, "android"),
                visit("9000000001", "甲", today, 23, 30, "mp"),
            ),
            VisitRange.TODAY,
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals(24, snapshot.trend.size)
        assertEquals(1, snapshot.trend[0].count)
        assertEquals(2, snapshot.trend[9].count)
        assertEquals(1, snapshot.trend[23].count)
        assertEquals("00", snapshot.trend[0].label)
        assertEquals("03", snapshot.trend[3].label)
        assertEquals("21", snapshot.trend[21].label)
        assertEquals("23", snapshot.trend[23].label)
        assertEquals("非 3 小时锚点不显示标签", "", snapshot.trend[1].label)
        assertEquals("", snapshot.trend[22].label)
        assertEquals(4, snapshot.trend.sumOf { it.count })
        assertTrue(snapshot.trend.none { it.highlighted })
    }

    @Test
    fun build_today_detailTextOmitsActiveDays() {
        val snapshot = VisitStats.build(
            listOf(visit("9000000001", "甲", today, 8, 0, "web")),
            VisitRange.TODAY,
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals("首次 11/18 08:00 · 最近 11/18 08:00", snapshot.accounts[0].detailText)
    }

    @Test
    fun build_week_detailTextCountsActiveDays() {
        val snapshot = VisitStats.build(
            listOf(
                visit("9000000001", "甲", today, 9, 30, "web"),
                visit("9000000001", "甲", yesterday, 9, 30, "mp"),
            ),
            VisitRange.WEEK,
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals(2, snapshot.accounts[0].activeDays)
        assertEquals(
            "活跃 2/7 天 · 首次 11/17 09:30 · 最近 11/18 09:30",
            snapshot.accounts[0].detailText,
        )
    }

    @Test
    fun build_latestRowProvidesClientVersionAndDevice() {
        val snapshot = VisitStats.build(
            listOf(
                visit("9000000001", "甲", today, 9, 0, "web", ver = "1.0.0", dev = "Windows · Edge 131"),
                visit("9000000001", "甲", today, 20, 0, "android", ver = "2.0.0", dev = "Xiaomi 14 · Android 15"),
            ),
            VisitRange.TODAY,
            nowMillis = now,
            zoneId = zone,
        )

        val account = snapshot.accounts[0]
        assertEquals("安卓", account.clientText)
        assertEquals("2.0.0", account.verText)
        assertEquals("Xiaomi 14 · Android 15", account.devText)
        assertEquals("首次 11/18 09:00 · 最近 11/18 20:00", account.detailText)
    }

    // ------------------------------------------------------------ 排序

    @Test
    fun build_countIsPrimaryEvenIfLatestVisitIsOlder() {
        val old = ts(today, 8, 0)
        val recent = ts(today, 20, 0)
        val snapshot = VisitStats.build(
            listOf(
                visit("9000000001", "甲", today, 8, 0, "web", tsOverride = old),
                visit("9000000001", "甲", today, 8, 30, "web", tsOverride = old + 60_000),
                visit("9000000002", "乙", today, 20, 0, "mp", tsOverride = recent),
            ),
            VisitRange.TODAY,
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals(2, snapshot.accounts.size)
        assertEquals("访问次数优先", "9000000001", snapshot.accounts[0].sid)
        assertEquals(2, snapshot.accounts[0].count)
        assertEquals(1, snapshot.accounts[1].count)
    }

    @Test
    fun build_equalCountBreaksByLatestVisit() {
        val snapshot = VisitStats.build(
            listOf(
                visit("9000000001", "甲", today, 9, 0, "web"),
                visit("9000000002", "乙", today, 20, 0, "mp"),
            ),
            VisitRange.TODAY,
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals("次数相同按最近访问", "9000000002", snapshot.accounts[0].sid)
    }

    @Test
    fun build_equalCountAndTimeBreaksByName() {
        val same = ts(today, 12, 0)
        val snapshot = VisitStats.build(
            listOf(
                visit("9000000001", "甲", today, 12, 0, "web", tsOverride = same),
                visit("9000000002", "乙", today, 12, 0, "mp", tsOverride = same),
            ),
            VisitRange.TODAY,
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals("次数/最近时间相同按姓名升序（乙 < 甲）", "乙", snapshot.accounts[0].name)
        assertEquals("甲", snapshot.accounts[1].name)
    }

    // ------------------------------------------------------------ 姓名兜底

    @Test
    fun build_namePrefersLatestNamedRowThenRosterThenUnknown() {
        val snapshot = VisitStats.build(
            listOf(
                visit("9000000001", "早名字", today, 9, 0, "web"),
                visit("9000000001", "   ", today, 11, 0, "web"),
                visit("9000000002", "", today, 10, 0, "mp"),
                visit("9000000003", "", today, 8, 0, "win"),
            ),
            VisitRange.TODAY,
            nowMillis = now,
            zoneId = zone,
            nameOfSid = { sid -> if (sid == "9000000002") "名单乙" else null },
        )

        assertEquals("早名字", snapshot.accounts[0].name)
        assertEquals("名单乙", snapshot.accounts[1].name)
        assertEquals("班级成员", snapshot.accounts[2].name)
    }

    // ------------------------------------------------------------ 旧行 / 未记录

    @Test
    fun build_legacyRowWithoutVerDevDidUsesSlotAndUnrecorded() {
        val snapshot = VisitStats.build(
            listOf(
                VisitRow(
                    slot = "$today-08",
                    sid = "9000000005",
                    name = "钱七",
                    ts = 0,
                    client = "android",
                ),
            ),
            VisitRange.TODAY,
            nowMillis = now,
            zoneId = zone,
        )

        val account = snapshot.accounts[0]
        assertEquals("11/18 08:00", account.lastText)
        assertEquals("11/18 08:00", account.firstText)
        assertEquals("未记录", account.verText)
        assertEquals("未记录", account.devText)
        assertEquals(1, account.deviceCount)
        assertEquals(listOf("未记录"), account.versions.map { it.label })
        assertEquals(listOf("未记录"), account.devices.map { it.label })
        assertEquals(100, account.devices[0].percent)
        assertEquals("安卓", account.devices[0].clientLabel)
        assertEquals(1, snapshot.deviceTotal)
    }

    @Test
    fun build_mixedLegacyAndNewRowsCountDevicesAndSortVersions() {
        val snapshot = VisitStats.build(
            listOf(
                VisitRow("$today-08", "9000000006", "钱七", 0, "android"),
                visit("9000000006", "钱七", today, 10, 0, "web", ver = "2.0.0", dev = "Windows · Edge 131"),
            ),
            VisitRange.TODAY,
            nowMillis = now,
            zoneId = zone,
        )

        val account = snapshot.accounts[0]
        assertEquals(2, account.count)
        assertEquals("11/18 08:00", account.firstText)
        assertEquals(2, account.deviceCount)
        assertEquals(listOf("2.0.0", "未记录"), account.versions.map { it.label })
        assertEquals(listOf("网页", "安卓"), account.devices.map { it.clientLabel })
        assertEquals(100, account.versions.sumOf { it.percent })
    }

    @Test
    fun build_unknownClientBecomesUnrecorded() {
        val snapshot = VisitStats.build(
            listOf(
                visit("9000000001", "甲", today, 9, 0, ""),
                visit("9000000002", "乙", today, 10, 0, "ios"),
            ),
            VisitRange.TODAY,
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals(1, snapshot.clients.size)
        assertEquals("", snapshot.clients[0].code)
        assertEquals("未记录", snapshot.clients[0].label)
        assertEquals(2, snapshot.clients[0].count)
        assertEquals(100, snapshot.clients[0].percent)
        assertEquals(VisitClientStyle.COLOR_UNKNOWN, VisitClientStyle.colorOf(snapshot.clients[0].code))
    }

    @Test
    fun clientStyle_matchesContractLabelsAndColors() {
        assertEquals("网页", VisitStats.clientLabel("web"))
        assertEquals("小程序", VisitStats.clientLabel("mp"))
        assertEquals("安卓", VisitStats.clientLabel("android"))
        assertEquals("Windows", VisitStats.clientLabel("win"))
        assertEquals("鸿蒙", VisitStats.clientLabel("harmony"))
        assertEquals("未记录", VisitStats.clientLabel(""))
        assertEquals("未记录", VisitStats.clientLabel(null))

        assertEquals(0xFF4E9082, VisitClientStyle.colorOf("web"))
        assertEquals(0xFF6E8CA8, VisitClientStyle.colorOf("mp"))
        assertEquals(0xFF5F8F5B, VisitClientStyle.colorOf("android"))
        assertEquals(0xFFB98F63, VisitClientStyle.colorOf("win"))
        assertEquals(0xFF8A6A92, VisitClientStyle.colorOf("harmony"))
        assertEquals(0xFF9AA5A1, VisitClientStyle.colorOf("unknown"))

        assertEquals("乱序大写也归一", "web", VisitClientStyle.normalize(" WEB "))
        assertEquals("未知端归一为空（未记录）", "", VisitClientStyle.normalize("ios"))
    }

    @Test
    fun versionAndDeviceLabels_trimAndCollapseWhitespace() {
        assertEquals("未记录", VisitStats.versionLabel("  "))
        assertEquals("未记录", VisitStats.deviceLabel(null))
        assertEquals(
            "Xiaomi 14 · Android 15",
            VisitStats.deviceLabel("  Xiaomi   14 ·\tAndroid 15 "),
        )
    }

    @Test
    fun effectiveTs_fallsBackToSlotHourForLegacyRow() {
        val legacy = VisitRow("$today-08", "9000000001", "张三", 0, "")
        val effective = VisitStats.effectiveTs(legacy, zone)
        assertEquals("11/18 08:00", VisitStats.formatTime(effective, zone))
    }

    // ------------------------------------------------------------ 百分比

    @Test
    fun build_clientPercentRoundsAndLastSegmentFillsTo100() {
        val snapshot = VisitStats.build(
            listOf(
                visit("9000000001", "甲", today, 8, 0, "web"),
                visit("9000000002", "乙", today, 8, 0, "web"),
                visit("9000000003", "丙", today, 8, 0, "web"),
                visit("9000000004", "丁", today, 8, 0, "android"),
                visit("9000000005", "戊", today, 8, 0, "android"),
                visit("9000000006", "己", today, 8, 0, "mp"),
                visit("9000000007", "庚", today, 8, 0, "win"),
            ),
            VisitRange.TODAY,
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals(listOf("网页", "安卓", "Windows", "小程序"), snapshot.clients.map { it.label })
        assertEquals(listOf(3, 2, 1, 1), snapshot.clients.map { it.count })
        assertEquals(43, snapshot.clients[0].percent)
        assertEquals(29, snapshot.clients[1].percent)
        assertEquals("最后一段补齐到 100", 100, snapshot.clients.sumOf { it.percent })
        assertEquals(100 - 43 - 29 - 14, snapshot.clients[3].percent)
    }

    @Test
    fun build_singleClientGetsFullPercent() {
        val snapshot = VisitStats.build(
            listOf(visit("9000000001", "甲", today, 8, 0, "web")),
            VisitRange.TODAY,
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals(100, snapshot.clients[0].percent)
        assertEquals(100, snapshot.versions[0].percent)
        assertEquals(100, snapshot.devices[0].percent)
    }

    // ------------------------------------------------------------ 6 + 其他

    @Test
    fun build_versionsCapAtSixGroupsPlusOther() {
        val versions = listOf(
            "8.0.0", "8.0.0",
            "7.0.0", "7.0.0",
            "6.0.0", "6.0.0",
            "5.0.0", "5.0.0",
            "4.0.0",
            "3.0.0",
            "2.0.0",
            "1.0.0",
        )
        val rows = versions.mapIndexed { index, ver ->
            visit("90000000${(10 + index).toString().padStart(2, '0')}", "成员$index", today, 9, index, "web", ver = ver)
        }
        val snapshot = VisitStats.build(rows, VisitRange.TODAY, nowMillis = now, zoneId = zone)

        assertEquals(7, snapshot.versions.size)
        assertEquals("8.0.0", snapshot.versions[0].label)
        assertEquals(2, snapshot.versions[0].count)
        assertEquals("其他", snapshot.versions[6].label)
        assertEquals(2, snapshot.versions[6].count)
        assertEquals(100, snapshot.versions.sumOf { it.percent })
    }

    @Test
    fun build_devicesGroupByClientAndDevThenCapAtSixPlusOther() {
        val devices = listOf(
            "Device A", "Device A",
            "Device B", "Device B",
            "Device C",
            "Device D",
            "Device E",
            "Device F",
            "Device G",
        )
        val rows = devices.mapIndexed { index, dev ->
            visit("90000000${(20 + index).toString().padStart(2, '0')}", "成员$index", today, 9, index, "web", dev = dev)
        }
        val snapshot = VisitStats.build(rows, VisitRange.TODAY, nowMillis = now, zoneId = zone)

        assertEquals(7, snapshot.devices.size)
        // 次数降序 → 最近时间降序：B(2, 09:03) 先于 A(2, 09:01)。
        assertEquals(listOf("Device B", "Device A"), snapshot.devices.take(2).map { it.label })
        assertEquals("其他", snapshot.devices[6].label)
        assertEquals("", snapshot.devices[6].clientLabel)
        assertEquals(1, snapshot.devices[6].count)
        assertEquals(100, snapshot.devices.sumOf { it.percent })
    }

    @Test
    fun build_accountVersionsAndDevicesAlsoCapAtSixPlusOther() {
        val versions = (1..8).map { "$it.0.0" }
        val rows = versions.mapIndexed { index, ver ->
            visit(
                "9000000030", "多端同学", today, 9, index, "web",
                ver = ver,
                dev = "Device $index",
                did = "dddd0000000000${(10 + index).toString().padStart(2, '0')}",
            )
        }
        val snapshot = VisitStats.build(rows, VisitRange.TODAY, nowMillis = now, zoneId = zone)

        val account = snapshot.accounts[0]
        assertEquals(7, account.versions.size)
        assertEquals("其他", account.versions[6].label)
        assertEquals("6+其他：8 组里最后 2 组合并", 2, account.versions[6].count)
        assertEquals(7, account.devices.size)
        assertEquals("其他", account.devices[6].label)
        assertEquals(2, account.devices[6].count)
        assertEquals(8, account.deviceCount)
        assertEquals(100, account.versions.sumOf { it.percent })
        assertEquals(100, account.devices.sumOf { it.percent })
    }

    // ------------------------------------------------------------ 设备数

    @Test
    fun build_deviceCountPrefersDidAndFallsBackToClientDev() {
        val snapshot = VisitStats.build(
            listOf(
                visit("9000000001", "甲", today, 9, 0, "web", dev = "Device A", did = "aaaa000000000001"),
                visit("9000000001", "甲", today, 9, 10, "web", dev = "Device A renamed", did = "aaaa000000000001"),
                visit("9000000001", "甲", today, 9, 20, "web", dev = "Device B"),
                visit("9000000001", "甲", today, 9, 30, "web", dev = "Device B"),
                visit("9000000001", "甲", today, 9, 40, "web", dev = "Device C", did = "bbbb000000000002"),
            ),
            VisitRange.TODAY,
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals("同 did 不同 dev 只算 1 台；无 did 按 client|dev", 3, snapshot.accounts[0].deviceCount)
        assertEquals(3, snapshot.deviceTotal)
    }

    @Test
    fun build_deviceTotalCountsSameDidAcrossAccountsOnce() {
        val snapshot = VisitStats.build(
            listOf(
                visit("9000000001", "甲", today, 9, 0, "web", dev = "Device A", did = "aaaa000000000001"),
                visit("9000000002", "乙", today, 10, 0, "mp", dev = "Device B", did = "aaaa000000000001"),
            ),
            VisitRange.TODAY,
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals(2, snapshot.accounts.size)
        assertEquals("同一台设备换账号登录只算 1 台", 1, snapshot.deviceTotal)
        assertEquals(1, snapshot.accounts[0].deviceCount)
        assertEquals(1, snapshot.accounts[1].deviceCount)
    }

    // ------------------------------------------------------------ 范围边界 / 同时间顺序

    @Test
    fun build_weekIncludesSixDaysAgoButExcludesSeventh() {
        val snapshot = VisitStats.build(
            listOf(
                visit("9000000001", "甲", today, 9, 0, "web"),
                visit("9000000002", "乙", "2026-11-12", 9, 0, "mp"),
                visit("9000000003", "丙", "2026-11-11", 9, 0, "win"),
            ),
            VisitRange.WEEK,
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals(2, snapshot.accounts.size)
        assertEquals(2, snapshot.total)
        assertEquals("11/12", snapshot.trend.first().label)
        assertEquals(1, snapshot.trend.first().count)
        assertTrue(snapshot.accounts.none { it.name == "丙" })
    }

    @Test
    fun build_sameTimestampUsesLaterInputRowForLatestClient() {
        val same = ts(today, 20, 0)
        val snapshot = VisitStats.build(
            listOf(
                visit("9000000001", "甲", today, 20, 0, "web", ver = "1.0.0", tsOverride = same),
                visit("9000000001", "甲", today, 20, 0, "mp", ver = "2.0.0", tsOverride = same),
            ),
            VisitRange.TODAY,
            nowMillis = now,
            zoneId = zone,
        )

        val account = snapshot.accounts[0]
        assertEquals("同时间取输入顺序靠后者", "mp", account.client)
        assertEquals("小程序", account.clientText)
        assertEquals("2.0.0", account.verText)
        assertEquals(2, account.count)
    }
}
