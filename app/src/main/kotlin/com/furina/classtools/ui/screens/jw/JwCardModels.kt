package com.furina.classtools.ui.screens.jw

import com.furina.classtools.data.jw.JwApiPage
import com.furina.classtools.data.jw.JwApiResult
import com.furina.classtools.data.jw.JwCardsSnapshot
import com.furina.classtools.data.jw.JwEnrollRow
import com.furina.classtools.data.jw.JwExamRow
import com.furina.classtools.data.jw.JwGradeRow
import com.furina.classtools.data.jw.JwNoticeRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 教务卡片页 UI 模型与映射（规格 §4，**纯 Kotlin，无 Compose/Android 依赖，可 JVM 单测**）。
 *
 * 数据卡内容来自官方接口（[JwCardsSnapshot]），这里只做「接口结果 → 卡片文案」的纯映射：
 * - 单卡失败/空态独立（不互相影响）；
 * - 卡片标题/入口路径来自规格 §3/§4 的实测清单；
 * - 不包含任何账号/密码/cookie 字段。
 */

/** 4 张数据卡的类别（点开对应官方页面，规格 §4）。 */
enum class JwDataCardKind(val title: String, val path: String) {
    GRADES("成绩", "/jsxsd/kscj/cjcx_frm"),
    EXAMS("考试", "/jsxsd/xsks/xsksap_query"),
    ENROLL("选课", "/jsxsd/xsxk/xklc_list"),
    NOTICES("公告", "/jsxsd/ggly/ysgg_query"),
}

/** 官方工具入口（规格 §3 官方「快捷菜单」实测清单，8 项）。 */
data class JwOfficialTool(
    val id: String,
    val title: String,
    val path: String,
)

/** 卡片页静态目录（官方 URL / 工具清单 / 文案，可单测）。 */
object JwCardCatalog {

    /** 教务系统根地址（只有 HTTP）。 */
    const val BASE_URL: String = "http://210.34.74.77"

    /** 「打开完整教务官网」：官方 SSO 入口（已登录时自动回主页）。 */
    const val SITE_URL: String = "http://210.34.74.77/sso.jsp"

    /** 规格 §3：官方工具入口 8 项（短名 + 官方页面路径）。 */
    val officialTools: List<JwOfficialTool> = listOf(
        JwOfficialTool("timetable", "学期理论课表", "/jsxsd/xskb/xskb_list.do"),
        JwOfficialTool("grade-query", "课程成绩查询", "/jsxsd/kscj/cjcx_frm"),
        JwOfficialTool("enroll-center", "学生选课中心", "/jsxsd/xsxk/xklc_list"),
        JwOfficialTool("exam-query", "考试安排查询", "/jsxsd/xsks/xsksap_query"),
        JwOfficialTool("train-plan", "培养方案明细", "/jsxsd/pyfa/topyfamx"),
        JwOfficialTool("notice-query", "已收公告", "/jsxsd/ggly/ysgg_query"),
        JwOfficialTool("delayed-exam", "缓考申请", "/jsxsd/kscj/hksq_query"),
        JwOfficialTool("social-exam", "社会考试报名", "/jsxsd/xsdjks/xsdjks_list"),
    )

    /** 数据卡顺序（两列布局：成绩/考试 一行，选课/公告 一行）。 */
    val dataCards: List<JwDataCardKind> = JwDataCardKind.entries.toList()

    /** 官方页面绝对 URL。 */
    fun url(path: String): String = BASE_URL + path

    /** 数据卡对应的官方页面 URL。 */
    fun dataCardUrl(kind: JwDataCardKind): String = url(kind.path)
}

/** 单张数据卡的渲染状态。 */
enum class JwDataCardStatus {
    /** 拉取中（骨架/「—」）。 */
    LOADING,

    /** 有数据。 */
    READY,

    /** 接口成功但官方 count/rows 为空（正常空态，规格 §2 实测态）。 */
    EMPTY,

    /** 该卡获取失败（单卡隔离，可重试/打开官网）。 */
    ERROR,
}

/** 单张数据卡的 UI 数据（纯数据，可直接单测映射结果）。 */
data class JwDataCardUi(
    val kind: JwDataCardKind,
    val targetUrl: String,
    val status: JwDataCardStatus,
    val headline: String = "",
    val lines: List<String> = emptyList(),
    val footnote: String = "",
)

/** 卡片页配置（规格 §1/§4 的数值口径）。 */
object JwCardConfig {
    /** 会话失效后自动重登最多尝试次数（规格 §1：重试自动登录 1 次）。 */
    const val MAX_RELOGIN_ATTEMPTS: Int = 1

    /** 成绩/公告卡的列表预览条数（规格 §4：最近 3 条 / 2–3 条）。 */
    const val LIST_PREVIEW_LIMIT: Int = 3
}

/** 卡片页固定文案（规格 §4；纯常量便于单测与后续统一改口径）。 */
object JwCardTexts {
    const val SECTION_DATA: String = "数据"
    const val SECTION_TOOLS: String = "官方工具"
    const val BTN_OPEN_SITE: String = "打开完整教务官网"
    const val BTN_RETRY: String = "重试"
    const val BTN_REFRESH: String = "刷新"

    /** 状态条：已自动登录 + 数据更新时间。 */
    const val STATUS_READY_PREFIX: String = "已自动登录 · 数据更新 "

    /** 会话失效（页级错误，规格 §1）。 */
    const val MSG_LOGIN_EXPIRED: String = "登录失效，请重试"
    const val MSG_RELOGIN: String = "登录失效，正在重新登录…"

    /** 拉取失败（单卡错误态）。 */
    const val MSG_FETCH_FAILED: String = "获取失败 · 重试"
    const val MSG_PARSE_FAILED: String = "数据解析失败 · 重试"

    /** 官方接口无数据时的正常空态文案（规格 §4）。 */
    const val EMPTY_GRADES: String = "暂无成绩"
    const val EMPTY_EXAMS: String = "暂无考试安排"
    const val EMPTY_ENROLL: String = "暂无可选轮次"
    const val EMPTY_NOTICES: String = "暂无公告"

    /** 成绩卡标题前缀（「已出成绩 N 门」）。 */
    const val GRADES_HEADLINE_PREFIX: String = "已出成绩 "
    const val GRADES_HEADLINE_SUFFIX: String = " 门"

    /** 考试/选课/公告卡的 READY 标题。 */
    const val EXAMS_HEADLINE: String = "下一场考试"
    const val ENROLL_HEADLINE: String = "当前选课轮次"
    const val NOTICES_HEADLINE: String = "最近公告"
}

/** 接口结果 → 卡片 UI 的纯映射（无副作用）。 */
object JwCardMapper {

    /** 拉取中骨架：4 张卡都显示 LOADING。 */
    fun loadingCards(): List<JwDataCardUi> = JwCardCatalog.dataCards.map { kind ->
        JwDataCardUi(
            kind = kind,
            targetUrl = JwCardCatalog.dataCardUrl(kind),
            status = JwDataCardStatus.LOADING,
        )
    }

    /** 快照 → 4 张卡片（单卡独立成功/空/失败）。 */
    fun cards(snapshot: JwCardsSnapshot): List<JwDataCardUi> = listOf(
        gradesCard(snapshot.grades),
        examsCard(snapshot.exams),
        enrollCard(snapshot.enrollRounds),
        noticesCard(snapshot.notices),
    )

    fun gradesCard(result: JwApiResult<JwGradeRow>): JwDataCardUi = dataCard(
        kind = JwDataCardKind.GRADES,
        result = result,
        emptyText = JwCardTexts.EMPTY_GRADES,
    ) { page ->
        if (page.rows.isEmpty()) {
            empty(JwDataCardKind.GRADES, JwCardTexts.EMPTY_GRADES)
        } else {
            val count = if (page.count > 0) page.count else page.rows.size
            val first = page.rows.first()
            JwDataCardUi(
                kind = JwDataCardKind.GRADES,
                targetUrl = JwCardCatalog.dataCardUrl(JwDataCardKind.GRADES),
                status = JwDataCardStatus.READY,
                headline = JwCardTexts.GRADES_HEADLINE_PREFIX + count + JwCardTexts.GRADES_HEADLINE_SUFFIX,
                lines = page.rows.take(JwCardConfig.LIST_PREVIEW_LIMIT).map { row ->
                    "${row.kcMc.ifBlank { "—" }} · ${row.zcjstr.ifBlank { "—" }}"
                },
                footnote = listOfNotNull(
                    first.xnxqid.takeIf { it.isNotBlank() },
                    first.jd.takeIf { it.isNotBlank() }?.let { "绩点 $it" },
                ).joinToString(" · "),
            )
        }
    }

    fun examsCard(result: JwApiResult<JwExamRow>): JwDataCardUi = dataCard(
        kind = JwDataCardKind.EXAMS,
        result = result,
        emptyText = JwCardTexts.EMPTY_EXAMS,
    ) { page ->
        val next = page.rows.firstOrNull()
        if (next == null) {
            empty(JwDataCardKind.EXAMS, JwCardTexts.EMPTY_EXAMS)
        } else {
            val detail = listOfNotNull(
                next.kskcmc.takeIf { it.isNotBlank() },
                next.jsMc.takeIf { it.isNotBlank() },
                next.zwh.takeIf { it.isNotBlank() }?.let { "座位 $it" },
            ).joinToString(" · ")
            JwDataCardUi(
                kind = JwDataCardKind.EXAMS,
                targetUrl = JwCardCatalog.dataCardUrl(JwDataCardKind.EXAMS),
                status = JwDataCardStatus.READY,
                headline = JwCardTexts.EXAMS_HEADLINE,
                lines = listOfNotNull(
                    next.kssj.takeIf { it.isNotBlank() },
                    detail.takeIf { it.isNotBlank() },
                ),
                footnote = listOfNotNull(
                    next.ksccmc.takeIf { it.isNotBlank() },
                    next.ksxq.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
            )
        }
    }

    fun enrollCard(result: JwApiResult<JwEnrollRow>): JwDataCardUi = dataCard(
        kind = JwDataCardKind.ENROLL,
        result = result,
        emptyText = JwCardTexts.EMPTY_ENROLL,
    ) { page ->
        val current = page.rows.firstOrNull()
        if (current == null) {
            empty(JwDataCardKind.ENROLL, JwCardTexts.EMPTY_ENROLL)
        } else {
            JwDataCardUi(
                kind = JwDataCardKind.ENROLL,
                targetUrl = JwCardCatalog.dataCardUrl(JwDataCardKind.ENROLL),
                status = JwDataCardStatus.READY,
                headline = JwCardTexts.ENROLL_HEADLINE,
                lines = listOfNotNull(
                    listOf(current.xqmc, current.xklcMc).filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .takeIf { it.isNotBlank() },
                    current.xksj.takeIf { it.isNotBlank() }?.let { "选课时间：$it" },
                ),
                footnote = "",
            )
        }
    }

    fun noticesCard(result: JwApiResult<JwNoticeRow>): JwDataCardUi = dataCard(
        kind = JwDataCardKind.NOTICES,
        result = result,
        emptyText = JwCardTexts.EMPTY_NOTICES,
    ) { page ->
        if (page.rows.isEmpty()) {
            empty(JwDataCardKind.NOTICES, JwCardTexts.EMPTY_NOTICES)
        } else {
            JwDataCardUi(
                kind = JwDataCardKind.NOTICES,
                targetUrl = JwCardCatalog.dataCardUrl(JwDataCardKind.NOTICES),
                status = JwDataCardStatus.READY,
                headline = JwCardTexts.NOTICES_HEADLINE,
                lines = page.rows.take(JwCardConfig.LIST_PREVIEW_LIMIT).map { it.title.ifBlank { "（无标题）" } },
                footnote = page.rows.firstOrNull()?.gglbname.orEmpty(),
            )
        }
    }

    /** 把某类接口结果统一映射成卡片；成功但无数据走 EMPTY，失败走 ERROR（单卡隔离）。 */
    private fun <T> dataCard(
        kind: JwDataCardKind,
        result: JwApiResult<T>,
        emptyText: String,
        ready: (JwApiPage<T>) -> JwDataCardUi,
    ): JwDataCardUi = when (result) {
        is JwApiResult.Success -> {
            val card = ready(result.page)
            if (card.status == JwDataCardStatus.LOADING) empty(kind, emptyText) else card
        }

        is JwApiResult.ApiError -> errorCard(kind, JwCardTexts.MSG_FETCH_FAILED)
        JwApiResult.SessionExpired -> errorCard(kind, JwCardTexts.MSG_LOGIN_EXPIRED)
        JwApiResult.Malformed -> errorCard(kind, JwCardTexts.MSG_PARSE_FAILED)
    }

    private fun empty(kind: JwDataCardKind, text: String): JwDataCardUi = JwDataCardUi(
        kind = kind,
        targetUrl = JwCardCatalog.dataCardUrl(kind),
        status = JwDataCardStatus.EMPTY,
        headline = text,
    )

    private fun errorCard(kind: JwDataCardKind, message: String): JwDataCardUi = JwDataCardUi(
        kind = kind,
        targetUrl = JwCardCatalog.dataCardUrl(kind),
        status = JwDataCardStatus.ERROR,
        headline = message,
    )
}

/** 当前本地时间 `HH:mm`（状态条「数据更新 HH:mm」，JVM 可测）。 */
fun jwUpdatedTimeText(millis: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
