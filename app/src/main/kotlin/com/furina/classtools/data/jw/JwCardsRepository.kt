package com.furina.classtools.data.jw

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 教务卡片页数据仓库（规格 §2/§5）。
 *
 * - 并行拉 4 个官方查询接口（成绩/考试/选课/公告）；
 * - **单卡失败隔离**：任意一张失败只影响该卡，其余照常展示；
 * - 汇总会话失效标记（任一接口返回 HTML/登录页 → 页面层触发自动重登 1 次，规格 §1）；
 * - cookie/UA 只在调用时作为参数传入，仓库不保存；数据只交给 UI 内存展示。
 */
data class JwCardsSnapshot(
    val grades: JwApiResult<JwGradeRow>,
    val exams: JwApiResult<JwExamRow>,
    val enrollRounds: JwApiResult<JwEnrollRow>,
    val notices: JwApiResult<JwNoticeRow>,
) {

    /** 任一接口会话失效（需要重新登录）。 */
    val sessionExpired: Boolean
        get() = listOf(grades, exams, enrollRounds, notices).any { it is JwApiResult.SessionExpired }

    companion object {
        /** cookie 缺失/已清除时的占位快照：4 张卡都按会话失效处理。 */
        fun sessionExpired(): JwCardsSnapshot = JwCardsSnapshot(
            grades = JwApiResult.SessionExpired,
            exams = JwApiResult.SessionExpired,
            enrollRounds = JwApiResult.SessionExpired,
            notices = JwApiResult.SessionExpired,
        )
    }
}

/** 卡片数据仓库；[api] 可注入（单测用假 [com.furina.classtools.data.network.HttpTransport]）。 */
class JwCardsRepository(
    private val api: JwApiClient = JwApiClient(),
) {

    /**
     * 并行拉取 4 张卡片数据。
     *
     * @param cookie WebView 同域会话 cookie（空 → 直接判会话失效，不发任何请求）
     * @param userAgent WebView 的 UA（官方接口按 UA 返回同构内容）
     */
    suspend fun loadAll(cookie: String?, userAgent: String): JwCardsSnapshot {
        val sessionCookie = cookie?.trim().orEmpty()
        if (sessionCookie.isEmpty()) return JwCardsSnapshot.sessionExpired()
        val ua = userAgent.trim()
        return coroutineScope {
            val grades = async { api.fetchGrades(sessionCookie, ua) }
            val exams = async { api.fetchExams(sessionCookie, ua) }
            val enrollRounds = async { api.fetchEnrollRounds(sessionCookie, ua) }
            val notices = async { api.fetchNotices(sessionCookie, ua) }
            JwCardsSnapshot(
                grades = grades.await(),
                exams = exams.await(),
                enrollRounds = enrollRounds.await(),
                notices = notices.await(),
            )
        }
    }
}
