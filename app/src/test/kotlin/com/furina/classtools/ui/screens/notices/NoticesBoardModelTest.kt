package com.furina.classtools.ui.screens.notices

import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.notices.ManualNotice
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeRecurrenceDto
import com.furina.classtools.data.notices.NoticeRow
import com.furina.classtools.data.notices.NoticesData
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知页纯逻辑模型单测（契约 `web/通知三端_长期通知与时间显示_契约_20261114.md` §2）：
 * 期次卡生成 / 资料·无时间栏 / 长期清单 / 已过期区 / 5 卡筛选 / 排序。
 */
class NoticesBoardModelTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Long = at(2026, 11, 14, 13, 31)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun entry(
        id: String,
        title: String = "通知 $id",
        recurrence: NoticeRecurrenceDto? = null,
        deadline: String = "",
        deadlineAction: String = "",
        eventAt: String = "",
        signupStart: String = "",
        signupEnd: String = "",
        expireAt: String = "",
        actionable: Boolean = true,
        alert: Boolean = false,
        hidden: Boolean = false,
        ts: Long = 0L,
    ): NoticeEntry = NoticeEntry(
        row = NoticeRow(id = "auto_$id", title = title, ts = ts),
        group = NoticeGroupDto(
            id = id,
            title = title,
            cat = "教务",
            deadline = deadline,
            deadlineAction = deadlineAction,
            eventAt = eventAt,
            signupStart = signupStart,
            signupEnd = signupEnd,
            expireAt = expireAt,
            recurrence = recurrence,
            actionable = actionable,
            alert = alert,
        ),
        items = emptyList(),
        hidden = hidden,
        edited = false,
    )

    private fun manualEntry(id: String, title: String, ts: Long): ManualNotice = ManualNotice(
        row = NoticeRow(id = id, title = title, body = "正文 $id", ts = ts),
        attachments = emptyList(),
    )

    private fun data(
        auto: List<NoticeEntry> = emptyList(),
        manual: List<ManualNotice> = emptyList(),
    ) = NoticesData(
        auto = auto,
        manual = manual,
        hiddenCount = 0,
        fetchedAt = now,
        fromCache = false,
    )

    private fun dailyUntil(until: String) = NoticeRecurrenceDto(
        freq = "daily",
        time = "07:30",
        startAt = "2026-11-10T07:30:00+08:00",
        until = until,
    )

    private val weekly = NoticeRecurrenceDto(
        freq = "weekly",
        weekday = 5,
        time = "19:00",
        startAt = "2026-11-06T19:00:00+08:00",
    )

    // ------------------------------------------------------------ buildUiItems 分类

    @Test
    fun `buildUiItems 分类 普通 资料 已过期 长期定义 期次 已结束`() {
        val normal = entry("normal", deadline = "2026-11-15T18:00:00+08:00", ts = 1000L)
        val material = entry("material", ts = 2000L)
        val expired = entry("expired", deadline = "2026-11-13T18:00:00+08:00", ts = 3000L)
        val def = entry("def", recurrence = weekly, ts = 4000L)
        // 每日 07:30 until 11-15：now=11-14 13:31 → 期次 11-15 07:30（窗口内）
        val inWindow = entry("occ", recurrence = dailyUntil("2026-11-15"), ts = 5000L)
        val ended = entry("ended", recurrence = dailyUntil("2026-11-10"), ts = 6000L)
        val items = buildUiItems(data(auto = listOf(normal, material, expired, def, inWindow, ended)), MarksSnapshot(), now, zone)
        assertEquals(6, items.size)

        val byId = items.associateBy { it.auto!!.id }
        assertTrue(isPrimaryItem(byId.getValue("normal")))
        assertFalse(byId.getValue("normal").material)
        assertFalse(byId.getValue("normal").expired)

        assertTrue(byId.getValue("material").material)
        assertFalse(isPrimaryItem(byId.getValue("material")))

        assertTrue(byId.getValue("expired").expired)
        assertFalse(isPrimaryItem(byId.getValue("expired")))

        // 长期定义（无期次窗口）
        val defItem = byId.getValue("def")
        assertNotNull(defItem.recurrence)
        assertNull(defItem.occurrenceKey)
        assertEquals(NoticeCardMode.LONGTERM_DEF, noticeCardModeOf(defItem))
        assertEquals("每周五 19:00", defItem.recurLabel)
        assertTrue(defItem.recurNextText.startsWith("下次 "))

        // 期次卡：key = gid@occ:YYYYMMDD，状态胶囊「本次 M月D日（周X）HH:mm」
        val occItem = byId.getValue("occ")
        assertNotNull(occItem.occurrenceKey)
        assertEquals("occ@occ:20261115", occItem.key)
        assertEquals(NoticeCardMode.OCCURRENCE, noticeCardModeOf(occItem))
        assertEquals("本次 11月15日（明天） 07:30", occItem.status?.label)
        assertTrue(isPrimaryItem(occItem))
        assertTrue(isPendingItem(occItem))
        assertFalse(occItem.material)

        // 长期安排已结束 → 已过期区
        val endedItem = byId.getValue("ended")
        assertTrue(endedItem.longtermEnded)
        assertTrue(endedItem.expired)
        assertEquals("长期安排已结束", endedItem.status?.label)
        assertFalse(isPrimaryItem(endedItem))
    }

    @Test
    fun `期次已处理 该期卡片不入主列表 但长期安排仍进行中`() {
        val inWindow = entry("occ", recurrence = dailyUntil("2026-11-15"), ts = 1000L)
        val marks = MarksSnapshot(ntDone = mapOf("occ@occ:20261115" to true))
        val items = buildUiItems(data(auto = listOf(inWindow)), marks, now, zone)
        val item = items.single()
        assertTrue(item.occurrenceDone)
        assertTrue(item.done)
        assertFalse(isPrimaryItem(item))
        assertFalse(isPendingItem(item))
        // 长期清单仍显示该进行中的安排（定义卡无 done）
        val content = viewContentOf(items, NoticeFilter.ALL, NoticeCardFilter.LONGTERM, "", now)
        assertEquals(1, content.longterm.size)
        assertEquals(NoticeCardMode.LONGTERM_DEF, noticeCardModeOf(content.longterm.single()))
    }

    @Test
    fun `人工通知按主列表普通卡处理`() {
        val items = buildUiItems(data(manual = listOf(manualEntry("m1", "人工通知", now))), MarksSnapshot(), now, zone)
        val item = items.single()
        assertFalse(item.isAuto)
        assertTrue(isPrimaryItem(item))
        assertFalse(isPendingItem(item)) // 人工通知不计待处理（无时间锚点）
    }

    // ------------------------------------------------------------ 视图分区（§2.2）

    private fun sampleItems(): List<NoticeUiItem> {
        val auto = listOf(
            entry("normal", deadline = "2026-11-15T18:00:00+08:00", ts = 1000L),
            entry("urgent", deadline = "2026-11-14T20:00:00+08:00", ts = 2000L),
            entry("week", deadline = "2026-11-17T18:00:00+08:00", ts = 3000L),
            entry("material", ts = 4000L),
            entry("expired", deadline = "2026-11-13T18:00:00+08:00", ts = 5000L),
            entry("def", recurrence = weekly, ts = 6000L),
            entry("occ", recurrence = dailyUntil("2026-11-15"), ts = 7000L),
            entry("ended", recurrence = dailyUntil("2026-11-10"), ts = 8000L),
        )
        val manual = listOf(manualEntry("m1", "人工通知", ts = 9000L))
        return buildUiItems(data(auto = auto, manual = manual), MarksSnapshot(), now, zone)
    }

    @Test
    fun `全部视图 主列表 资料栏 已过期区`() {
        val content = viewContentOf(sampleItems(), NoticeFilter.ALL, null, "", now)
        assertTrue(content.showMaterial)
        assertTrue(content.showExpiredSection)
        assertFalse(content.longtermOnly)
        assertFalse(content.latestOnly)
        // 主列表：普通有时间的 3 条 + 期次卡 1 + 人工 1；长期定义/资料/已过期不进主列表
        assertEquals(5, content.primary.size)
        assertTrue(content.primary.any { it.key == "occ@occ:20261115" })
        assertFalse(content.primary.any { it.auto?.id == "def" })
        assertFalse(content.primary.any { it.auto?.id == "material" })
        // 资料栏：无时间通知
        assertEquals(listOf("material"), content.material.map { it.auto!!.id })
        // 已过期区：过期普通 + 长期安排已结束
        assertEquals(setOf("expired", "ended"), content.expired.map { it.auto!!.id }.toSet())
        // 底部「共 N 组」= 主列表条目数
        assertEquals(content.primary.size, content.totalCount)
    }

    @Test
    fun `待处理筛选 只显示主列表 资料栏隐藏 已过期区保留折叠`() {
        val content = viewContentOf(sampleItems(), NoticeFilter.PENDING, null, "", now)
        assertTrue(content.showExpiredSection)
        assertFalse(content.showMaterial)
        assertTrue(content.material.isEmpty())
        assertTrue(content.primary.all { isPendingItem(it) })
        assertFalse(content.primary.any { it.key == "occ@occ:20261115" && it.occurrenceDone })
        // 已过期区内容仍可展开查看
        assertTrue(content.expired.isNotEmpty())
    }

    @Test
    fun `24h 与 7 天卡筛选`() {
        val items = sampleItems()
        val urgent = viewContentOf(items, NoticeFilter.ALL, NoticeCardFilter.URGENT, "", now)
        // urgent: deadline 20:00 (6.5h) + 期次 11-15 07:30 (18h)；7 天卡的 11-17 不算
        assertEquals(setOf("urgent", "occ@occ:20261115"), urgent.primary.map { it.key }.toSet())

        val week = viewContentOf(items, NoticeFilter.ALL, NoticeCardFilter.WEEK, "", now)
        assertEquals(setOf("normal", "urgent", "week", "occ@occ:20261115"), week.primary.map { it.key }.toSet())
    }

    @Test
    fun `长期通知筛选 进行中安排清单 按下次时间升序`() {
        val items = sampleItems()
        val content = viewContentOf(items, NoticeFilter.ALL, NoticeCardFilter.LONGTERM, "", now)
        assertTrue(content.longtermOnly)
        assertTrue(content.primary.isEmpty())
        // 只有 week / occ / def 中的进行中安排：occ(11-15) < def(11-20)；已结束 excluded
        assertEquals(
            listOf("occ", "def"),
            content.longterm.map { it.auto!!.id },
        )
        // 长期清单不显示期次卡、无 done（渲染固定用 LONGTERM_DEF；条目本身仍带 recurrence）
        assertTrue(content.longterm.all { it.recurrence != null })
        assertEquals(2, content.totalCount)
    }

    @Test
    fun `最新发布筛选 全部通知按发布时间倒序 含已过期`() {
        val items = sampleItems()
        val content = viewContentOf(items, NoticeFilter.ALL, NoticeCardFilter.LATEST, "", now)
        assertTrue(content.latestOnly)
        // 9 条全部（含人工 / 资料 / 长期 / 已过期）
        assertEquals(9, content.latest.size)
        val tsList = content.latest.map { it.ts }
        assertEquals(tsList.sortedDescending(), tsList)
        // 过期卡仍按过期样式（mode EXPIRED）；长期定义卡不显示本次已处理
        val expiredItem = content.latest.first { it.auto?.id == "expired" }
        assertEquals(NoticeCardMode.EXPIRED, noticeLatestModeOf(expiredItem))
        val occItem = content.latest.first { it.auto?.id == "occ" }
        assertEquals(NoticeCardMode.LONGTERM_DEF, noticeLatestModeOf(occItem))
    }

    @Test
    fun `置顶筛选 仅人工置顶通知`() {
        val manual = manualEntry("m1", "置顶通知", now)
        val items = buildUiItems(
            data(manual = listOf(manual)),
            MarksSnapshot(ntPin = mapOf("m1" to true)),
            now,
            zone,
        )
        val content = viewContentOf(items, NoticeFilter.PINNED, null, "", now)
        assertEquals(1, content.primary.size)
        assertTrue(content.primary.single().pinned)
        assertFalse(content.showMaterial)
        assertFalse(content.showExpiredSection)
    }

    @Test
    fun `搜索退回平铺列表 含已过期卡`() {
        val items = sampleItems()
        val content = viewContentOf(items, NoticeFilter.ALL, null, "通知", now)
        // 所有标题都含「通知」→ 平铺全部
        assertEquals(9, content.primary.size)
        assertFalse(content.showMaterial)
        assertFalse(content.showExpiredSection)
        assertTrue(content.primary.any { it.expired })
    }

    @Test
    fun `主列表按紧急度排序 期次按 next 参与 已处理最后`() {
        val alert = entry("alert", deadline = "2026-11-13T18:00:00+08:00", alert = true, ts = 10L)
        val urgent = entry("urgent", deadline = "2026-11-14T20:00:00+08:00", ts = 20L)
        val soon = entry("soon", deadline = "2026-11-16T18:00:00+08:00", ts = 30L)
        val active = entry("active", deadline = "2026-11-25T18:00:00+08:00", ts = 40L)
        val doneItem = entry("done", deadline = "2026-11-14T19:00:00+08:00", ts = 50L)
        val occ = entry("occ", recurrence = dailyUntil("2026-11-15"), ts = 60L)
        val items = buildUiItems(
            data(auto = listOf(active, soon, doneItem, urgent, alert, occ)),
            MarksSnapshot(ntDone = mapOf("done" to true)),
            now,
            zone,
        )
        val content = viewContentOf(items, NoticeFilter.ALL, null, "", now)
        val ids = content.primary.map { it.auto!!.id }
        assertEquals(listOf("alert", "urgent", "occ", "soon", "active", "done"), ids)
    }

    @Test
    fun `buildOverview 统计口径§2_4 与 hidden 过滤`() {
        val auto = listOf(
            entry("normal", deadline = "2026-11-14T20:00:00+08:00", ts = 1000L),
            entry("material", ts = 2000L),
            entry("expired", deadline = "2026-11-13T18:00:00+08:00", ts = 3000L),
            entry("def", recurrence = weekly, ts = 4000L),
            entry("occ", recurrence = dailyUntil("2026-11-15"), ts = 5000L),
            entry("hidden", deadline = "2026-11-14T20:00:00+08:00", hidden = true, ts = 9000L),
        )
        val manual = listOf(manualEntry("m1", "人工通知", ts = 9500L))
        val stats = buildOverview(data(auto = auto, manual = manual), MarksSnapshot(), now, zone)
        // normal + 期次(occ) = 2；hidden 不计
        assertEquals(2, stats.pending)
        assertEquals(2, stats.urgent)
        assertEquals(1, stats.expired)
        assertEquals(2, stats.longterm) // def + occ
        // 最新发布取未隐藏通知（人工 m1 9500）
        assertEquals(9500L, stats.latestPostedMillis)
        assertTrue(stats.latestPostedText.isNotBlank())
    }

    @Test
    fun `formatPosted 更早日期用 §3 格式`() {
        assertEquals("11月8日（上周日）", formatPosted(at(2026, 11, 8, 9, 5), now, zone))
        assertEquals("昨天 09:05", formatPosted(at(2026, 11, 13, 9, 5), now, zone))
        assertEquals("12:49", formatPosted(at(2026, 11, 14, 12, 49), now, zone))
    }
}
