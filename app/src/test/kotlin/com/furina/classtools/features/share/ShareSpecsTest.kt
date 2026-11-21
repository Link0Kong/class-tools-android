package com.furina.classtools.features.share

import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeSegment
import com.furina.classtools.data.vote.Poll
import com.furina.classtools.data.vote.PollDetail
import com.furina.classtools.data.vote.PollMode
import com.furina.classtools.data.vote.PollOption
import com.furina.classtools.data.vote.PollResultVisibility
import com.furina.classtools.data.vote.PollSettings
import com.furina.classtools.features.export.ChipItem
import com.furina.classtools.features.export.ChipsBlock
import com.furina.classtools.features.export.ExportCardSpec
import com.furina.classtools.features.export.GroupsBlock
import com.furina.classtools.features.export.TextBlock
import com.furina.classtools.features.export.TextLine
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 分享卡/分享文案单测：内容、隐私口径（投票不出现结果）、通知字段优先级、链接锚点。 */
class ShareSpecsTest {

    private val baseUrl = "https://tool.furina.run"
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Long = LocalDateTime.of(2026, 11, 21, 12, 30).atZone(zone).toInstant().toEpochMilli()

    // ------------------------------------------------------------ 链接

    @Test
    fun `工具链接带 tab 锚点且不重复斜杠`() {
        assertEquals("$baseUrl/tools/index.html#tab=hw", ShareSpecs.toolUrl(baseUrl, "hw"))
        assertEquals("$baseUrl/tools/index.html#tab=hw", ShareSpecs.toolUrl("$baseUrl/", " hw "))
        assertEquals("$baseUrl/tools/index.html", ShareSpecs.toolUrl(baseUrl, ""))
    }

    @Test
    fun `分享链接包含标题 简介与链接`() {
        val link = ShareSpecs.link(ShareKind.NOTICE, baseUrl, title = "班级通知｜班会", text = "周三 18:00")
        assertEquals("班级通知｜班会", link.title)
        assertTrue(link.url.endsWith("#tab=nt"))
        assertEquals("班级通知｜班会\n周三 18:00\n${link.url}", link.message)
        assertEquals(ShareSpecs.DEFAULT_TITLE, ShareSpecs.link(ShareKind.APP, baseUrl).title)
    }

    // ------------------------------------------------------------ 导出卡 → 分享卡

    @Test
    fun `抽签导出卡转分享卡带结果数与内容行`() {
        val spec = ExportCardSpec(
            title = "随机抽签结果",
            sub = "STEP 3",
            file = "抽签.png",
            rec = "DRAW-20261121-1230",
            config = listOf("范围" to "1 – 70", "抽" to "2 个"),
            blocks = listOf(
                ChipsBlock(
                    label = "抽签结果",
                    chips = listOf(ChipItem(24, "小明"), ChipItem(3, "小红")),
                ),
            ),
            foot = "生成时间 11-21 12:30",
        )
        val view = ShareSpecs.fromExportCard(spec, ShareKind.DRAW, baseUrl, now, "11-21 12:30")
        assertEquals(ShareKind.DRAW, view.kind)
        assertEquals("抽签", view.badge)
        assertEquals("随机抽签结果", view.title)
        assertTrue(view.subtitle.contains("范围 1 – 70"))
        assertEquals("2", view.stats.first().value)
        assertTrue(view.lines.first().text.contains("24 小明"))
        assertEquals("生成时间 11-21 12:30", view.footerLeft)
        assertEquals("11-21 12:30", view.footerRight)
        assertTrue(view.link.url.endsWith("#tab=draw"))
        assertFalse(view.maskedResults)
    }

    @Test
    fun `分组导出卡转分享卡带组数与人数`() {
        val spec = ExportCardSpec(
            title = "随机分组结果",
            file = "分组.png",
            rec = "GROUP-1",
            blocks = listOf(
                GroupsBlock(
                    label = "分组结果",
                    groups = listOf(
                        listOf(ChipItem(1, "甲"), ChipItem(2, "乙")),
                        listOf(ChipItem(3, "丙")),
                    ),
                ),
            ),
        )
        val view = ShareSpecs.fromExportCard(spec, ShareKind.GROUP, baseUrl, now)
        assertEquals(listOf("2", "3"), view.stats.map { it.value })
        assertEquals("第 1 组（2）", view.lines.first().label)
        assertTrue(view.lines.first().text.contains("甲"))
        assertTrue(view.link.url.endsWith("#tab=group"))
    }

    @Test
    fun `文本块导出卡转分享卡最多六行`() {
        val spec = ExportCardSpec(
            title = "作业清单",
            file = "作业.png",
            rec = "HW-1",
            blocks = listOf(
                TextBlock(
                    label = "截止作业",
                    lines = (1..8).map { TextLine.of("作业$it") },
                ),
            ),
        )
        val view = ShareSpecs.fromExportCard(spec, ShareKind.HOMEWORK, baseUrl, now)
        assertTrue(view.lines.size <= ShareCardView.MAX_LINES)
        assertEquals("8", view.stats.first().value)
    }

    // ------------------------------------------------------------ 通知卡

    @Test
    fun `通知卡字段按 时间 地点 优先 最多两条`() {
        val input = ShareSpecs.NoticeShareInput(
            title = "图书馆闭馆通知",
            category = "教务",
            statusLabel = "截止 11月21日 18:00",
            summary = "周日全馆检修，暂停开放。",
            points = listOf("带校园卡", "不要占座", "第三点", "第四点"),
            segments = listOf(
                ShareLine("截止", "11月21日 18:00"),
                ShareLine("地点", "图书馆"),
                ShareLine("时间", "11月21日 08:00"),
            ),
            senderName = "陈玉霞",
            senderRole = "班长",
            source = "QQ 群「班级群」",
            timeLabel = "11-20 10:00",
            attachmentCount = 2,
        )
        val view = ShareSpecs.noticeCard(input, baseUrl)
        assertEquals(ShareKind.NOTICE, view.kind)
        assertEquals("图书馆闭馆通知", view.title)
        assertEquals(listOf("教务", "截止 11月21日 18:00"), view.pills)
        assertEquals(ShareCardView.MAX_FIELDS, view.fields.size)
        assertEquals("时间", view.fields[0].label)
        assertEquals("地点", view.fields[1].label)
        assertEquals(ShareCardView.MAX_POINTS, view.lines.size)
        assertTrue(view.note.contains("陈玉霞 · 班长"))
        assertTrue(view.note.contains("2 个附件"))
        assertTrue(view.footerLeft.contains("QQ 群"))
        assertTrue(view.link.title.startsWith("班级通知｜"))
    }

    @Test
    fun `通知映射从组字段取分类 摘要 要点与结构化字段`() {
        val group = NoticeGroupDto(
            id = "g1",
            title = "秋游报名",
            cat = "活动",
            source = "班级通知群",
            senderName = "陈玉霞",
            senderRole = "班长",
            summary = "本周六秋游",
            aiSummary = "本周六秋游，需在周四前报名",
            aiPoints = listOf("带好雨具", "统一乘车"),
            aiSegments = listOf(
                NoticeSegment(type = "时间", text = "周六 08:00"),
                NoticeSegment(type = "地点", text = "南门集合"),
            ),
            deadline = "2026-11-26T18:00:00+08:00",
            eventAt = "2026-11-28T08:00:00+08:00",
            timeLabel = "报名 11-21 12:00",
        )
        val input = ShareMappers.notice(group, attachmentCount = 1, nowMillis = now, zone = zone)
        assertEquals("秋游报名", input.title)
        assertEquals("活动", input.category)
        assertEquals("本周六秋游，需在周四前报名", input.summary)
        assertEquals(listOf("带好雨具", "统一乘车"), input.points)
        assertTrue(input.segments.any { it.label == "时间" && it.text == "周六 08:00" })
        assertTrue(input.segments.any { it.label == "地点" })
        assertEquals("陈玉霞", input.senderName)
        assertEquals(1, input.attachmentCount)
        assertTrue(input.statusLabel.isNotBlank())
    }

    // ------------------------------------------------------------ 投票卡（隐私红线）

    @Test
    fun `投票卡不包含票数与百分比且标记结果已遮住`() {
        val input = ShareSpecs.VoteShareInput(
            title = "周三班会时间投票",
            status = "进行中",
            modeText = "长时",
            ownerText = "发起人 陈玉霞",
            deadlineText = "11-25 18:00",
            description = "选出本周班会时间",
            tags = listOf("单选", "匿名", "可改票"),
            optionTexts = listOf("周三 18:00", "周三 19:00", "周四 18:00", "周四 19:00", "第五项不应出现"),
            timeLabel = "11-21 12:30",
        )
        val view = ShareSpecs.voteCard(input, baseUrl)
        assertTrue(view.maskedResults)
        assertEquals(ShareKind.VOTE, view.kind)
        assertEquals("周三班会时间投票", view.title)
        assertEquals("结果请打开投票页查看", view.note)
        assertTrue(view.lines.any { it.text == "周三 18:00" })
        // 选项文字：说明 1 行 + 最多 4 项 + 发起/截止 2 行 = 7 行，绘制层再限制 6 行
        assertTrue(view.lines.none { it.text.contains("第五项") })
        val all = (listOf(view.title, view.subtitle, view.note, view.footerLeft, view.footerRight) +
            view.lines.map { it.text } + view.pills + view.stats.map { it.value }).joinToString("|")
        assertFalse(all.contains("%"))
        assertFalse(all.contains("票数"))
        assertFalse(Regex("[0-9]+\\s*票").containsMatchIn(all))
    }

    @Test
    fun `投票映射只取选项文字不读票数`() {
        val poll = Poll(
            id = "p1",
            title = "班服颜色",
            description = "选一个颜色",
            mode = PollMode.LONG,
            ownerName = "陈玉霞",
            settings = PollSettings(resultVisibility = PollResultVisibility.AFTER_CLOSE),
            options = listOf(PollOption(id = "o1", text = "藏青"), PollOption(id = "o2", text = "墨绿")),
            deadline = LocalDateTime.of(2026, 11, 25, 18, 0).atZone(zone).toInstant().toEpochMilli(),
            status = "open",
        )
        // counts / total 有值也不应进入分享输入
        val detail = PollDetail(
            poll = poll,
            total = 12,
            counts = mapOf("o1" to 7, "o2" to 5),
            hasVoted = true,
        )
        val input = ShareMappers.vote(poll, detail, nowMillis = now, zone = zone)
        assertEquals("班服颜色", input.title)
        assertEquals("进行中", input.status)
        assertEquals(listOf("藏青", "墨绿"), input.optionTexts)
        assertEquals("11-25 18:00", input.deadlineText)
        assertTrue(input.ownerText.contains("陈玉霞"))
        assertTrue(input.tags.any { it.contains("截止后可见") })
    }

    // ------------------------------------------------------------ 我的页分享卡

    @Test
    fun `我的页分享卡落到网页首页并带工具介绍`() {
        val view = ShareSpecs.appCard(baseUrl)
        assertEquals(ShareKind.APP, view.kind)
        assertEquals("班级工具站", view.title)
        assertEquals("$baseUrl/tools/index.html", view.link.url)
        assertTrue(view.stats.size <= ShareCardView.MAX_STATS)
        assertTrue(view.lines.any { it.label == "抽签" })
    }

    // ------------------------------------------------------------ 绘制层（记录型 surface）

    private class RecordingSurface : ShareCardSurface {
        val texts = mutableListOf<String>()
        override val width: Float get() = ShareCardSurface.LOGICAL_WIDTH
        override val height: Float get() = ShareCardSurface.LOGICAL_HEIGHT

        override fun measure(text: String, size: Float, weight: Int): Float =
            text.length * size * 0.55f

        override fun text(text: String, x: Float, y: Float, size: Float, weight: Int, color: Int, align: Int) {
            texts += text
        }

        override fun roundRect(
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            radius: Float,
            color: Int,
            stroke: Boolean,
            strokeWidth: Float,
        ) = Unit

        override fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, strokeWidth: Float) = Unit

        override fun circle(cx: Float, cy: Float, radius: Float, color: Int) = Unit
    }

    @Test
    fun `绘制投票卡不出现票数与百分比`() {
        val view = ShareSpecs.voteCard(
            ShareSpecs.VoteShareInput(
                title = "周三班会时间投票",
                status = "进行中",
                optionTexts = listOf("周三 18:00", "周三 19:00"),
            ),
            baseUrl,
        )
        val surface = RecordingSurface()
        ShareCardPainter.paint(view, surface)
        val all = surface.texts.joinToString("|")
        assertTrue(all.contains("周三班会时间投票"))
        assertTrue(all.contains("周三 18:00"))
        assertTrue(all.contains("结果请打开投票页查看"))
        assertFalse(all.contains("%"))
        assertFalse(all.contains("人已投"))
        assertFalse(Regex("[0-9]+\\s*票").containsMatchIn(all))
    }

    @Test
    fun `绘制通知卡包含分类 状态 来源与页脚`() {
        val view = ShareSpecs.noticeCard(
            ShareSpecs.NoticeShareInput(
                title = "图书馆闭馆通知",
                category = "教务",
                statusLabel = "截止 11月21日 18:00",
                summary = "周日全馆检修，暂停开放。",
                points = listOf("带校园卡", "不要占座"),
                segments = listOf(ShareLine("时间", "11月21日 08:00")),
                source = "QQ 群「班级群」",
                timeLabel = "11-20 10:00",
            ),
            baseUrl,
        )
        val surface = RecordingSurface()
        ShareCardPainter.paint(view, surface)
        val all = surface.texts.joinToString("|")
        assertTrue(all.contains("图书馆闭馆通知"))
        assertTrue(all.contains("教务"))
        assertTrue(all.contains("截止 11月21日 18:00"))
        assertTrue(all.contains("QQ 群「班级群」"))
        assertTrue(all.contains("班级工具"))
        assertTrue(all.contains("11-20 10:00"))
        assertTrue(all.contains("时间"))
    }

    @Test
    fun `绘制层限制统计块与信息行数量`() {
        val view = ShareSpecs.appCard(baseUrl).copy(
            stats = (1..5).map { ShareStat("k$it", "v$it") },
            lines = (1..9).map { ShareLine("L$it", "内容$it") },
        )
        val surface = RecordingSurface()
        ShareCardPainter.paint(view, surface)
        assertTrue(surface.texts.count { it.startsWith("内容") } <= ShareCardView.MAX_LINES)
        assertTrue(surface.texts.none { it == "v5" })
        assertFalse(surface.texts.any { it == "L9" })
    }

    @Test
    fun `长标题按两行绘制并截断`() {
        val view = ShareSpecs.appCard(baseUrl).copy(title = "非常长的分享标题".repeat(6))
        val surface = RecordingSurface()
        ShareCardPainter.paint(view, surface)
        val titleLines = surface.texts.filter { it.startsWith("非常长的分享标题") }
        assertTrue(titleLines.size <= 2)
        assertNotNull(titleLines.firstOrNull())
    }

    @Test
    fun `文件名不含敏感字段且按类型与时间生成`() {
        val view = ShareSpecs.appCard(baseUrl)
        val name = ShareFiles.fileName(view, now)
        assertTrue(name.startsWith("share_app_"))
        assertTrue(name.endsWith(".png"))
        assertFalse(name.contains("学号"))
    }
}
