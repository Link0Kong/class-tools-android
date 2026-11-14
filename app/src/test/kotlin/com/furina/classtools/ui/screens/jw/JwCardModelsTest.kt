package com.furina.classtools.ui.screens.jw

import com.furina.classtools.data.jw.JwApiPage
import com.furina.classtools.data.jw.JwApiResult
import com.furina.classtools.data.jw.JwCardsSnapshot
import com.furina.classtools.data.jw.JwEnrollRow
import com.furina.classtools.data.jw.JwExamRow
import com.furina.classtools.data.jw.JwGradeRow
import com.furina.classtools.data.jw.JwNoticeRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 教务卡片页 UI 映射单测（规格 §3/§4）。
 *
 * 覆盖：官方工具入口清单（8 项 + 路径）、数据卡 4 类的 READY/EMPTY/ERROR 映射、
 * 单卡失败文案、数据卡目标 URL、更新时间文案。
 * 全部使用**合成夹具**，不含真实个人数据。
 */
class JwCardModelsTest {

    // ---- 静态目录 -----------------------------------------------------------

    @Test
    fun `官方工具入口 8 项且路径与规格一致`() {
        val tools = JwCardCatalog.officialTools
        assertEquals(8, tools.size)
        val byTitle = tools.associateBy { it.title }
        assertEquals("/jsxsd/xskb/xskb_list.do", byTitle.getValue("学期理论课表").path)
        assertEquals("/jsxsd/kscj/cjcx_frm", byTitle.getValue("课程成绩查询").path)
        assertEquals("/jsxsd/xsxk/xklc_list", byTitle.getValue("学生选课中心").path)
        assertEquals("/jsxsd/xsks/xsksap_query", byTitle.getValue("考试安排查询").path)
        assertEquals("/jsxsd/pyfa/topyfamx", byTitle.getValue("培养方案明细").path)
        assertEquals("/jsxsd/ggly/ysgg_query", byTitle.getValue("已收公告").path)
        assertEquals("/jsxsd/kscj/hksq_query", byTitle.getValue("缓考申请").path)
        assertEquals("/jsxsd/xsdjks/xsdjks_list", byTitle.getValue("社会考试报名").path)
        // id 唯一
        assertEquals(8, tools.map { it.id }.distinct().size)
    }

    @Test
    fun `官方 URL 拼接与数据卡目标页`() {
        assertEquals("http://210.34.74.77/jsxsd/xsxk/xklc_list", JwCardCatalog.url("/jsxsd/xsxk/xklc_list"))
        assertEquals(
            "http://210.34.74.77/jsxsd/kscj/cjcx_frm",
            JwCardCatalog.dataCardUrl(JwDataCardKind.GRADES),
        )
        assertEquals(
            "http://210.34.74.77/jsxsd/xsks/xsksap_query",
            JwCardCatalog.dataCardUrl(JwDataCardKind.EXAMS),
        )
        assertEquals(
            "http://210.34.74.77/jsxsd/xsxk/xklc_list",
            JwCardCatalog.dataCardUrl(JwDataCardKind.ENROLL),
        )
        assertEquals(
            "http://210.34.74.77/jsxsd/ggly/ysgg_query",
            JwCardCatalog.dataCardUrl(JwDataCardKind.NOTICES),
        )
        assertEquals("http://210.34.74.77/sso.jsp", JwCardCatalog.SITE_URL)
    }

    @Test
    fun `加载中骨架 4 张卡都是 LOADING`() {
        val cards = JwCardMapper.loadingCards()
        assertEquals(4, cards.size)
        assertTrue(cards.all { it.status == JwDataCardStatus.LOADING })
        assertEquals(
            listOf("成绩", "考试", "选课", "公告"),
            cards.map { it.kind.title },
        )
    }

    // ---- 成绩卡 -------------------------------------------------------------

    @Test
    fun `成绩卡 READY 展示已出成绩门数与最近条目`() {
        val page = JwApiPage(
            rows = listOf(
                JwGradeRow(xnxqid = "2026-2027-1", kcMc = "高等数学", zcjstr = "92", jd = "4.2"),
                JwGradeRow(xnxqid = "2026-2027-1", kcMc = "大学英语", zcjstr = "88", jd = "3.6"),
                JwGradeRow(xnxqid = "2026-2027-1", kcMc = "程序设计", zcjstr = "95", jd = "4.5"),
                JwGradeRow(xnxqid = "2026-2027-1", kcMc = "体育", zcjstr = "90", jd = "4.0"),
            ),
            count = 8,
        )
        val card = JwCardMapper.gradesCard(JwApiResult.Success(page))
        assertEquals(JwDataCardStatus.READY, card.status)
        assertEquals("已出成绩 8 门", card.headline)
        assertEquals(3, card.lines.size) // 预览最多 3 条
        assertEquals("高等数学 · 92", card.lines.first())
        assertTrue(card.footnote.contains("2026-2027-1"))
        assertTrue(card.footnote.contains("绩点 4.2"))
        assertEquals("http://210.34.74.77/jsxsd/kscj/cjcx_frm", card.targetUrl)
    }

    @Test
    fun `成绩卡空数据是 EMPTY 而非失败`() {
        val card = JwCardMapper.gradesCard(
            JwApiResult.Success(JwApiPage(rows = emptyList<JwGradeRow>(), count = 0)),
        )
        assertEquals(JwDataCardStatus.EMPTY, card.status)
        assertEquals(JwCardTexts.EMPTY_GRADES, card.headline)
    }

    @Test
    fun `成绩卡失败态与解析失败态`() {
        val failed = JwCardMapper.gradesCard(JwApiResult.ApiError("网络错误"))
        assertEquals(JwDataCardStatus.ERROR, failed.status)
        assertEquals(JwCardTexts.MSG_FETCH_FAILED, failed.headline)

        val malformed = JwCardMapper.gradesCard(JwApiResult.Malformed)
        assertEquals(JwDataCardStatus.ERROR, malformed.status)
        assertEquals(JwCardTexts.MSG_PARSE_FAILED, malformed.headline)
    }

    // ---- 考试卡 -------------------------------------------------------------

    @Test
    fun `考试卡 READY 展示下一场时间课程教室座位`() {
        val page = JwApiPage(
            rows = listOf(
                JwExamRow(
                    kssj = "2026-01-10 09:00-11:00",
                    kskcmc = "高等数学",
                    jsMc = "1-101",
                    zwh = "12",
                    ksccmc = "上午",
                    ksxq = "旗山",
                ),
            ),
            count = 2,
        )
        val card = JwCardMapper.examsCard(JwApiResult.Success(page))
        assertEquals(JwDataCardStatus.READY, card.status)
        assertEquals(JwCardTexts.EXAMS_HEADLINE, card.headline)
        assertEquals("2026-01-10 09:00-11:00", card.lines[0])
        assertTrue(card.lines[1].contains("高等数学"))
        assertTrue(card.lines[1].contains("1-101"))
        assertTrue(card.lines[1].contains("座位 12"))
        assertTrue(card.footnote.contains("上午"))
    }

    @Test
    fun `考试卡空数据展示暂无考试安排`() {
        val card = JwCardMapper.examsCard(
            JwApiResult.Success(JwApiPage(rows = emptyList<JwExamRow>(), count = 0)),
        )
        assertEquals(JwDataCardStatus.EMPTY, card.status)
        assertEquals(JwCardTexts.EMPTY_EXAMS, card.headline)
    }

    // ---- 选课卡 -------------------------------------------------------------

    @Test
    fun `选课卡 READY 展示学年学期轮次与选课时间`() {
        val page = JwApiPage(
            rows = listOf(
                JwEnrollRow(xqmc = "2026-2027-1", xklcMc = "第一轮选课", xksj = "2026-09-01 08:00"),
            ),
            count = 1,
        )
        val card = JwCardMapper.enrollCard(JwApiResult.Success(page))
        assertEquals(JwDataCardStatus.READY, card.status)
        assertEquals(JwCardTexts.ENROLL_HEADLINE, card.headline)
        assertTrue(card.lines[0].contains("2026-2027-1"))
        assertTrue(card.lines[0].contains("第一轮选课"))
        assertTrue(card.lines[1].contains("2026-09-01 08:00"))
    }

    @Test
    fun `选课卡空数据展示暂无可选轮次`() {
        val card = JwCardMapper.enrollCard(
            JwApiResult.Success(JwApiPage(rows = emptyList<JwEnrollRow>(), count = 0)),
        )
        assertEquals(JwDataCardStatus.EMPTY, card.status)
        assertEquals(JwCardTexts.EMPTY_ENROLL, card.headline)
    }

    // ---- 公告卡 -------------------------------------------------------------

    @Test
    fun `公告卡 READY 展示最近标题与类别`() {
        val page = JwApiPage(
            rows = listOf(
                JwNoticeRow(title = "关于选课的通知", gglbname = "教务通知"),
                JwNoticeRow(title = "考试安排通知", gglbname = "考试通知"),
                JwNoticeRow(title = "放假通知", gglbname = "综合"),
                JwNoticeRow(title = "第四条不应展示", gglbname = "综合"),
            ),
            count = 12,
        )
        val card = JwCardMapper.noticesCard(JwApiResult.Success(page))
        assertEquals(JwDataCardStatus.READY, card.status)
        assertEquals(JwCardTexts.NOTICES_HEADLINE, card.headline)
        assertEquals(3, card.lines.size)
        assertEquals("关于选课的通知", card.lines.first())
        assertEquals("教务通知", card.footnote)
    }

    @Test
    fun `公告卡空数据展示暂无公告`() {
        val card = JwCardMapper.noticesCard(
            JwApiResult.Success(JwApiPage(rows = emptyList<JwNoticeRow>(), count = 0)),
        )
        assertEquals(JwDataCardStatus.EMPTY, card.status)
        assertEquals(JwCardTexts.EMPTY_NOTICES, card.headline)
    }

    // ---- 整页快照 -----------------------------------------------------------

    @Test
    fun `整页快照映射为 4 张卡且单卡隔离`() {
        val snapshot = JwCardsSnapshot(
            grades = JwApiResult.ApiError("网络错误"),
            exams = JwApiResult.Success(JwApiPage(rows = emptyList<JwExamRow>(), count = 0)),
            enrollRounds = JwApiResult.Success(
                JwApiPage(rows = listOf(JwEnrollRow(xqmc = "2026-2027-1", xklcMc = "第一轮")), count = 1),
            ),
            notices = JwApiResult.SessionExpired,
        )
        val cards = JwCardMapper.cards(snapshot)
        assertEquals(4, cards.size)
        assertEquals(JwDataCardStatus.ERROR, cards[0].status) // 成绩失败
        assertEquals(JwDataCardStatus.EMPTY, cards[1].status) // 考试空
        assertEquals(JwDataCardStatus.READY, cards[2].status) // 选课有数据
        assertEquals(JwDataCardStatus.ERROR, cards[3].status) // 公告会话失效
        assertEquals(JwCardTexts.MSG_LOGIN_EXPIRED, cards[3].headline)
        // 每张卡都有对应官方页面 URL（失败也能打开官网）
        assertTrue(cards.all { it.targetUrl.startsWith("http://210.34.74.77/jsxsd/") })
    }

    @Test
    fun `更新时间文案为 HH 冒号 mm`() {
        val text = jwUpdatedTimeText(1763000000000L) // 固定时间戳，格式断言
        assertTrue(Regex("^\\d{2}:\\d{2}$").matches(text))
        assertFalse(text.contains("2026"))
    }
}
