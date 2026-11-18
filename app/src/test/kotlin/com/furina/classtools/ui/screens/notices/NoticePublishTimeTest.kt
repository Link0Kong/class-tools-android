package com.furina.classtools.ui.screens.notices

import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.notices.NOTICE_BODY_PREFIX
import com.furina.classtools.data.notices.NoticeBodyParser
import com.furina.classtools.data.notices.NoticeBucket
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeItemMeta
import com.furina.classtools.data.notices.NoticeRecurrenceDto
import com.furina.classtools.data.notices.NoticeRow
import com.furina.classtools.data.notices.NoticeStatusMachine
import com.furina.classtools.data.notices.NoticesData
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知「发布时间」统一契约单测（2026-11-18，`web/通知三端_发布时间统一_契约_20261118.md`）：
 *
 * - 无任何时间锚点的通知：时间 = 发布时间，动作 =「发布」，进入主列表并参与排序 / 统计；
 * - 「资料 / 无时间」栏只保留「既无时间锚点又无发布时间」的兜底卡（正常 0 条 → 栏隐藏）；
 * - 旧缓存无 `publishedAt` 时按组内 `items[].time` / `updatedAt` 现场推算（`HH:mm` 旧时间不算）；
 * - 排序档 4 内按发布时间倒序，无发布时间排最后；发布卡计入待处理但不进 24h / 7 天窗口。
 */
class NoticePublishTimeTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 契约基准时刻：2026-11-18 12:00（周三）。 */
    private val now: Long = at(2026, 11, 18, 12, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun rawTextItem(time: String): JsonObject = buildJsonObject {
        put("kind", JsonPrimitive("text"))
        put("text", JsonPrimitive("正文"))
        put("time", JsonPrimitive(time))
    }

    private fun group(
        id: String,
        publishedAt: String? = null,
        publishAction: String? = null,
        updatedAt: String = "",
        rawItemTimes: List<String> = emptyList(),
        deadline: String = "",
        eventAt: String = "",
        recurrence: NoticeRecurrenceDto? = null,
        actionable: Boolean = true,
    ): NoticeGroupDto = NoticeGroupDto(
        id = id,
        title = "通知 $id",
        cat = "教务",
        publishedAt = publishedAt,
        publishAction = publishAction,
        updatedAt = updatedAt,
        items = rawItemTimes.map { rawTextItem(it) },
        deadline = deadline,
        deadlineAction = if (deadline.isNotBlank()) "截止" else "",
        eventAt = eventAt,
        recurrence = recurrence,
        actionable = actionable,
    )

    private fun entry(
        group: NoticeGroupDto,
        ts: Long = at(2026, 11, 10, 9, 0),
        items: List<NoticeItemMeta> = emptyList(),
    ): NoticeEntry = NoticeEntry(
        row = NoticeRow(id = "auto_${group.id}", title = group.title, ts = ts),
        group = group,
        items = items,
        hidden = false,
        edited = false,
    )

    private fun data(vararg auto: NoticeEntry): NoticesData = NoticesData(
        auto = auto.toList(),
        manual = emptyList(),
        hiddenCount = 0,
        fetchedAt = now,
        fromCache = false,
    )

    private fun textItem(timeText: String): NoticeItemMeta.Text = NoticeItemMeta.Text(
        kind = "text",
        label = "原始消息",
        fromName = "班委",
        fromRole = "团支书",
        timeText = timeText,
        text = "正文",
        isFollow = false,
    )

    // ------------------------------------------------------------ DTO 解析

    @Test
    fun `DTO 解析 publishedAt 与 publishAction 旧 JSON 不报错`() {
        val body = NOTICE_BODY_PREFIX + """
            {"id":"g_dto","title":"军训鉴定表","publishedAt":"2026-11-16T22:15:00+08:00",
             "publishAction":"发布","unknownFutureField":{"x":1}}
        """.trimIndent()
        val g = NoticeBodyParser.parseGroup(body)
        assertNotNull(g)
        assertEquals("2026-11-16T22:15:00+08:00", g!!.publishedAt)
        assertEquals("发布", g.publishAction)

        // 旧缓存/旧字段：两个新字段缺失也必须正常解析
        val old = NoticeBodyParser.parseGroup(NOTICE_BODY_PREFIX + """{"id":"g_old","title":"旧通知"}""")
        assertNotNull(old)
        assertNull(old!!.publishedAt)
        assertNull(old.publishAction)
        assertEquals("none", NoticeStatusMachine.statusOf(old, now, zone).key)
    }

    // ------------------------------------------------------------ 状态机

    @Test
    fun `无时间锚点有发布时间 状态为 published 且带发布动作`() {
        val g = group(
            id = "pub",
            publishedAt = "2026-11-16T22:15:00+08:00",
            publishAction = "发布",
        )
        assertFalse(NoticeStatusMachine.hasTimeAnchor(g, zone))
        assertEquals(at(2026, 11, 16, 22, 15), NoticeStatusMachine.publishedTsOf(g, now, zone))
        assertNull(NoticeStatusMachine.whenOf(g, now, zone))

        val status = NoticeStatusMachine.statusOf(g, now, zone)
        assertEquals("published", status.key)
        assertEquals("active", status.cls)
        assertFalse(status.expired)
        assertEquals(NoticeBucket.PUBLISHED, status.bucket)
        // 发布时间是过去 → fmtWhen 走绝对格式：M月D日（相对周） HH:mm + 动作
        assertEquals("11月16日（本周一） 22:15 发布", status.label)
    }

    @Test
    fun `publishAction 缺失或空串时动作兜底为发布`() {
        val missing = NoticeStatusMachine.statusOf(
            group(id = "p1", publishedAt = "2026-11-15T20:00:00+08:00"),
            now,
            zone,
        )
        assertEquals("published", missing.key)
        assertTrue(missing.label.endsWith(" 发布"))

        val blank = NoticeStatusMachine.statusOf(
            group(id = "p2", publishedAt = "2026-11-15T20:00:00+08:00", publishAction = "  "),
            now,
            zone,
        )
        assertTrue(blank.label.endsWith(" 发布"))
    }

    @Test
    fun `真实时间锚点优先级高于发布时间`() {
        // 已过期仍显示过期（不因有发布时间变成发布卡）
        val expired = NoticeStatusMachine.statusOf(
            group(
                id = "exp",
                deadline = "2026-11-17T18:00:00+08:00",
                publishedAt = "2026-11-16T22:15:00+08:00",
            ),
            now,
            zone,
        )
        assertEquals("expired", expired.key)
        assertTrue(expired.expired)

        // 长期安排优先（契约 §2：长期安排 → 过期 → … → 发布时间 → 无时间）
        val recurring = NoticeStatusMachine.statusOf(
            group(
                id = "lt",
                recurrence = NoticeRecurrenceDto(
                    freq = "daily",
                    time = "07:30",
                    startAt = "2026-11-10T07:30:00+08:00",
                ),
                publishedAt = "2026-11-16T22:15:00+08:00",
            ),
            now,
            zone,
        )
        assertEquals("recurring", recurring.key)
    }

    // ------------------------------------------------------------ 旧数据回退

    @Test
    fun `旧缓存无 publishedAt 时回退 items time 与 updatedAt 的较晚者`() {
        val g = group(
            id = "legacy",
            updatedAt = "2026-11-15T09:00:00+08:00",
            rawItemTimes = listOf("11-16 21:30"),
        )
        assertEquals(at(2026, 11, 16, 21, 30), NoticeStatusMachine.publishedTsOf(g, now, zone))
        val status = NoticeStatusMachine.statusOf(g, now, zone)
        assertEquals("published", status.key)
        assertEquals("11月16日（本周一） 21:30 发布", status.label)
    }

    @Test
    fun `渲染元信息 items 兜底 数据层清空 group items 后仍是发布卡`() {
        // 生产路径：NoticesRepository 解析后 group.items 为空，时间只在 NoticeEntry.items 元信息里
        val legacy = entry(
            group = group(id = "meta"),
            items = listOf(textItem("11-14 20:30")),
        )
        val items = buildUiItems(data(legacy), MarksSnapshot(), now, zone)
        val item = items.single()
        assertEquals(at(2026, 11, 14, 20, 30), item.publishedTsMillis)
        assertEquals("published", item.status?.key)
        assertTrue(item.status!!.label.endsWith(" 发布"))
        assertFalse(item.material)
        assertTrue(isPrimaryItem(item))
        assertTrue(isPendingItem(item))
        assertEquals(4, noticeRankOf(item))
    }

    @Test
    fun `只有 HH_mm 的旧 item 时间不算发布时间 仍为无时间`() {
        // 2026-11-16 回归：item.time 常见只有当天时钟，不能算发布时间把历史通知顶到最前
        val g = group(id = "hhmm", rawItemTimes = listOf("21:30"))
        assertNull(NoticeStatusMachine.publishedTsOf(g, now, zone))
        assertEquals("none", NoticeStatusMachine.statusOf(g, now, zone).key)
    }

    @Test
    fun `消息时间无年份 晚于当前时间 60 天以上视为上一年`() {
        val earlyNow = at(2026, 1, 5, 12, 0)
        val g = group(id = "cross", rawItemTimes = listOf("12-30 20:00"))
        assertEquals(at(2025, 12, 30, 20, 0), NoticeStatusMachine.publishedTsOf(g, earlyNow, zone))
    }

    // ------------------------------------------------------------ 分类 / 资料栏

    @Test
    fun `发布卡进主列表 资料栏只保留既无锚点又无发布时间的兜底卡`() {
        val published = entry(
            group(id = "pub", publishedAt = "2026-11-16T22:15:00+08:00", publishAction = "发布"),
            ts = at(2026, 11, 10, 9, 0),
        )
        val fallback = entry(group(id = "none"), ts = at(2026, 11, 9, 9, 0))

        val items = buildUiItems(data(published, fallback), MarksSnapshot(), now, zone)
        val pubItem = items.first { it.auto?.id == "pub" }
        val noneItem = items.first { it.auto?.id == "none" }

        assertFalse(pubItem.material)
        assertTrue(isPrimaryItem(pubItem))
        assertTrue(isPendingItem(pubItem))
        assertTrue(noneItem.material)
        assertFalse(isPrimaryItem(noneItem))
        assertFalse(isPendingItem(noneItem))

        val all = viewContentOf(items, nowMillis = now)
        assertTrue(all.showMaterial)
        assertEquals(listOf("auto_pub"), all.primary.map { it.key })
        assertEquals(listOf("auto_none"), all.material.map { it.key })

        // 无兜底卡 → 资料栏不显示（正常数据 0 条）
        val onlyPublished = viewContentOf(
            buildUiItems(data(published), MarksSnapshot(), now, zone),
            nowMillis = now,
        )
        assertFalse(onlyPublished.showMaterial)
        assertTrue(onlyPublished.material.isEmpty())
        assertEquals(1, onlyPublished.primary.size)
    }

    // ------------------------------------------------------------ 排序（档 4）

    @Test
    fun `发布卡排序档 4 且按发布时间倒序 无发布时间排最后`() {
        val newer = entry(group(id = "newer", publishedAt = "2026-11-17T20:00:00+08:00"))
        val older = entry(group(id = "older", publishedAt = "2026-11-15T08:00:00+08:00"))
        val none = entry(group(id = "none"))
        val items = buildUiItems(data(newer, older, none), MarksSnapshot(), now, zone)

        items.forEach { assertEquals(4, noticeRankOf(it)) }
        // 主列表只剩发布卡；无发布时间兜底在资料栏
        val content = viewContentOf(items, nowMillis = now)
        assertEquals(listOf("newer", "older"), content.primary.map { it.auto!!.id })
        assertEquals(listOf("none"), content.material.map { it.auto!!.id })

        // 搜索/平铺视图把兜底卡放回同一列表时，它排在所有发布卡之后
        val flat = viewContentOf(items, query = "通知", nowMillis = now)
        assertEquals(listOf("newer", "older", "none"), flat.primary.map { it.auto!!.id })
    }

    // ------------------------------------------------------------ 统计

    @Test
    fun `发布卡按 actionable 计入待处理 但不进 24h 与 7 天窗口`() {
        val pub = entry(group(id = "pub", publishedAt = "2026-11-16T22:15:00+08:00"))
        val infoOnly = entry(
            group(id = "info", publishedAt = "2026-11-14T10:00:00+08:00", actionable = false),
        )
        val done = entry(group(id = "done", publishedAt = "2026-11-13T10:00:00+08:00"))
        val marks = MarksSnapshot(ntDone = mapOf("done" to true))

        val stats = buildOverview(data(pub, infoOnly, done), marks, now, zone)
        assertEquals(1, stats.pending)
        // 发布时间是过去 → 没有未来主显示时间，不算 24h / 7 天
        assertEquals(0, stats.urgent)
        assertEquals(0, stats.week)
        assertEquals(0, stats.expired)
    }

    // ------------------------------------------------------------ 最新发布

    @Test
    fun `最新发布优先 publishedAt 字段 缺字段回退 ts 与 updatedAt`() {
        val field = entry(
            group(
                id = "field",
                publishedAt = "2026-11-16T22:15:00+08:00",
                updatedAt = "2026-11-18T08:00:00+08:00",
            ),
            ts = at(2026, 11, 10, 9, 0),
        )
        assertEquals(at(2026, 11, 16, 22, 15), publishedAtOf(field, zone))

        val legacy = entry(
            group(id = "fallback", updatedAt = "2026-11-17T09:00:00+08:00"),
            ts = at(2026, 11, 10, 9, 0),
        )
        assertEquals(at(2026, 11, 17, 9, 0), publishedAtOf(legacy, zone))

        val newest = entry(group(id = "newest", publishedAt = "2026-11-18T10:00:00+08:00"))
        val items = buildUiItems(data(newest, field), MarksSnapshot(), now, zone)
        val latest = viewContentOf(items, cardFilter = NoticeCardFilter.LATEST, nowMillis = now)
        assertEquals(listOf("newest", "field"), latest.latest.map { it.auto!!.id })
        assertEquals(at(2026, 11, 18, 10, 0), buildOverview(data(newest, field), MarksSnapshot(), now, zone).latestPostedMillis)
    }
}
