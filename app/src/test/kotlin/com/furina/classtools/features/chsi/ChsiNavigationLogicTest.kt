package com.furina.classtools.features.chsi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学信网「默认卡片页 + 自动恢复登录」导航纯逻辑单测（2026-11-15 用户需求）。
 *
 * 需求要点：
 * - 点「学信网」默认直接看到工具卡片（CARDS），**不强制跳登录页**；
 * - 官方工具 10 卡始终可见；数据卡未登录显示去登录；
 * - 后台加载档案页，已登录自动提取并刷新数据卡；
 * - 只有用户主动点「登录学信网」才进官方登录页；登录成功后自动回卡片页。
 *
 * 全部为纯逻辑断言，不联网、不接触真实学信网账号（红线：测试只用测试身份/合成夹具）。
 */
class ChsiNavigationLogicTest {

    private val archiveUrl = "https://my.chsi.com.cn/archive/wap/index.jsp"
    private val loginUrl = "https://account.chsi.com.cn/passport/login"

    @Test
    fun `默认进入卡片页而不是登录页`() {
        assertEquals(ChsiNavigationLogic.Mode.CARDS, ChsiNavigationLogic.initialMode())
        // 官方工具 10 卡在卡片页始终可见（数据源不变）
        assertEquals(10, ChsiCatalog.officialTools.size)
    }

    @Test
    fun `永不自动打开官方登录页`() {
        ChsiNavigationLogic.Mode.entries.forEach { mode ->
            assertFalse(
                "mode=$mode 不应自动跳登录页",
                ChsiNavigationLogic.shouldAutoOpenLoginPage(mode, pageIsLoginUrl = true),
            )
            assertFalse(ChsiNavigationLogic.shouldAutoOpenLoginPage(mode, pageIsLoginUrl = false))
        }
    }

    @Test
    fun `卡片页后台加载档案页时触发提取`() {
        assertTrue(ChsiNavigationLogic.shouldExtract(ChsiNavigationLogic.Mode.CARDS, archiveUrl))
        assertTrue(ChsiNavigationLogic.shouldExtract(ChsiNavigationLogic.Mode.LOGIN, archiveUrl))
        // 全屏官方页不提取；非档案域 URL 不提取。
        assertFalse(ChsiNavigationLogic.shouldExtract(ChsiNavigationLogic.Mode.WEB, archiveUrl))
        assertFalse(ChsiNavigationLogic.shouldExtract(ChsiNavigationLogic.Mode.CARDS, loginUrl))
        assertFalse(ChsiNavigationLogic.shouldExtract(ChsiNavigationLogic.Mode.CARDS, null))
    }

    @Test
    fun `自动恢复登录后保持在卡片页`() {
        assertEquals(
            ChsiNavigationLogic.Mode.CARDS,
            ChsiNavigationLogic.modeAfterExtract(ChsiNavigationLogic.Mode.CARDS, loggedIn = true),
        )
    }

    @Test
    fun `登录页完成登录后自动回卡片页`() {
        assertEquals(
            ChsiNavigationLogic.Mode.CARDS,
            ChsiNavigationLogic.modeAfterExtract(ChsiNavigationLogic.Mode.LOGIN, loggedIn = true),
        )
    }

    @Test
    fun `未登录提取后不强制离开当前模式`() {
        assertEquals(
            ChsiNavigationLogic.Mode.CARDS,
            ChsiNavigationLogic.modeAfterExtract(ChsiNavigationLogic.Mode.CARDS, loggedIn = false),
        )
        assertEquals(
            ChsiNavigationLogic.Mode.LOGIN,
            ChsiNavigationLogic.modeAfterExtract(ChsiNavigationLogic.Mode.LOGIN, loggedIn = false),
        )
    }

    @Test
    fun `登录页 URL 只在用户主动登录模式停留`() {
        assertTrue(ChsiNavigationLogic.staysOnLoginPage(ChsiNavigationLogic.Mode.LOGIN, true))
        assertFalse(ChsiNavigationLogic.staysOnLoginPage(ChsiNavigationLogic.Mode.CARDS, true))
        assertFalse(ChsiNavigationLogic.staysOnLoginPage(ChsiNavigationLogic.Mode.LOGIN, false))
    }

    @Test
    fun `未登录数据卡提示去登录`() {
        assertEquals(ChsiTexts.MSG_NEED_LOGIN, ChsiNavigationLogic.dataCardHint(loggedIn = false))
        assertTrue(ChsiNavigationLogic.dataCardHint(loggedIn = false).contains("登录"))
        assertEquals(ChsiTexts.MSG_EMPTY_FALLBACK, ChsiNavigationLogic.dataCardHint(loggedIn = true))
    }

    @Test
    fun `卡片页状态条覆盖检查中提取中已登录未登录`() {
        assertEquals(
            ChsiTexts.STATUS_CHECKING,
            ChsiNavigationLogic.statusText(loggedIn = false, refreshing = true, lastUpdated = null),
        )
        assertEquals(
            ChsiTexts.STATUS_EXTRACTING,
            ChsiNavigationLogic.statusText(loggedIn = true, refreshing = true, lastUpdated = "10:00"),
        )
        assertEquals(
            ChsiTexts.STATUS_READY_PREFIX + "10:00",
            ChsiNavigationLogic.statusText(loggedIn = true, refreshing = false, lastUpdated = "10:00"),
        )
        assertEquals(
            ChsiTexts.STATUS_NOT_LOGGED_IN,
            ChsiNavigationLogic.statusText(loggedIn = false, refreshing = false, lastUpdated = null),
        )
        assertEquals(
            "网络错误",
            ChsiNavigationLogic.statusText(loggedIn = false, refreshing = false, lastUpdated = null, globalError = "网络错误"),
        )
    }

    @Test
    fun `登录入口文案为登录学信网与返回卡片`() {
        assertEquals("登录学信网", ChsiTexts.BTN_LOGIN_ACTION)
        assertEquals("返回卡片", ChsiTexts.BTN_BACK_TO_CARDS)
        assertTrue(ChsiTexts.HINT_LOGIN_FROM_CARDS.contains("登录学信网"))
    }

    // ---- 2026-11-15 追加：登录后不切卡片（真实档案页无「退出/个人中心」强信号）---------

    /** 合成夹具：真实登录后的 my.chsi 档案页 —— 只有学籍/学历/学位等业务文本，没有「退出登录/个人中心」。 */
    private val realLoggedInPayload = ChsiPagePayload(
        url = archiveUrl,
        title = "学信档案",
        text = """
            学信档案
            学籍信息
            学校：福建中医药大学
            专业：中药学
            学籍状态：在籍（注册学籍）
            学历信息
            院校：福建中医药大学
            学历：本科
            学位信息
            学位：理学学士
        """.trimIndent(),
        links = listOf(
            ChsiLink(text = "学籍信息", href = "https://my.chsi.com.cn/archive/wap/enrollment.jsp"),
            ChsiLink(text = "学历信息", href = "https://my.chsi.com.cn/archive/wap/education.jsp"),
        ),
    )

    /** 合成夹具：未登录营销首页（档案域也可能返回，含「注册学信网账号/立即登录」）。 */
    private val marketingPayload = ChsiPagePayload(
        url = archiveUrl,
        title = "学信档案",
        text = """
            学信档案
            免费查看学籍、学历、学位信息
            注册学信网账号
            立即登录
        """.trimIndent(),
    )

    @Test
    fun `LOGIN 模式登录后无强信号也自动回卡片页`() {
        assertFalse("强信号判定应为 false（真实页面没有退出/个人中心）", ChsiExtractLogic.isLoggedInPayload(realLoggedInPayload))
        assertTrue(
            "宽松判定应为 true",
            ChsiExtractLogic.isLoginSuccessAfterLogin(realLoggedInPayload, archiveUrl),
        )
        assertTrue(
            ChsiNavigationLogic.loggedInAfterExtract(
                realLoggedInPayload,
                archiveUrl,
                ChsiNavigationLogic.Mode.LOGIN,
            ),
        )
        assertEquals(
            ChsiNavigationLogic.Mode.CARDS,
            ChsiNavigationLogic.modeAfterExtract(ChsiNavigationLogic.Mode.LOGIN, loggedIn = true),
        )
    }

    @Test
    fun `CARDS 模式后台恢复仍用强信号避免营销页误判`() {
        // 后台自动恢复（CARDS）不接受宽松判定：真实登录页无强信号时保持未登录，等待用户主动登录。
        assertFalse(
            ChsiNavigationLogic.loggedInAfterExtract(
                realLoggedInPayload,
                archiveUrl,
                ChsiNavigationLogic.Mode.CARDS,
            ),
        )
    }

    @Test
    fun `未登录营销页不会被宽松判定误判`() {
        assertFalse(ChsiExtractLogic.isLoginSuccessAfterLogin(marketingPayload, archiveUrl))
        assertFalse(
            ChsiNavigationLogic.loggedInAfterExtract(
                marketingPayload,
                archiveUrl,
                ChsiNavigationLogic.Mode.LOGIN,
            ),
        )
    }

    @Test
    fun `宽松判定要求档案域 URL 与非空数据`() {
        assertFalse(ChsiExtractLogic.isLoginSuccessAfterLogin(realLoggedInPayload, loginUrl))
        assertFalse(ChsiExtractLogic.isLoginSuccessAfterLogin(realLoggedInPayload, null))
        assertFalse(ChsiExtractLogic.isLoginSuccessAfterLogin(null, archiveUrl))
        // 文本过短（空白/错误页）不判定
        assertFalse(
            ChsiExtractLogic.isLoginSuccessAfterLogin(
                ChsiPagePayload(url = archiveUrl, text = "加载中"),
                archiveUrl,
            ),
        )
    }

    @Test
    fun `登录页兜底按钮只在 LOGIN 模式且空闲时展示`() {
        assertTrue(ChsiNavigationLogic.showEnterCardsFallback(ChsiNavigationLogic.Mode.LOGIN, refreshing = false))
        assertFalse(ChsiNavigationLogic.showEnterCardsFallback(ChsiNavigationLogic.Mode.LOGIN, refreshing = true))
        // 2026-11-16：卡片页未登录且空闲时也显示（用户确认已登录可直接进入）。
        assertTrue(ChsiNavigationLogic.showEnterCardsFallback(ChsiNavigationLogic.Mode.CARDS, refreshing = false))
        assertFalse(ChsiNavigationLogic.showEnterCardsFallback(ChsiNavigationLogic.Mode.CARDS, refreshing = true))
        assertFalse(
            ChsiNavigationLogic.showEnterCardsFallback(
                ChsiNavigationLogic.Mode.CARDS,
                refreshing = false,
                loggedIn = true,
            ),
        )
        assertEquals("已登录，进入卡片页", ChsiTexts.BTN_ENTER_CARDS)
        assertTrue(ChsiTexts.HINT_ENTER_CARDS_FALLBACK.contains("卡片页"))
    }

    // ---- 2026-11-16：登录态延迟检测（延迟重试 + 数据信号 + Cookie 辅助）--------------

    /** 合成夹具：档案页有数据信号（学籍/姓名/专业）但没有「退出/个人中心」。 */
    private val dataSignalPayload = ChsiPagePayload(
        url = archiveUrl,
        title = "学信档案",
        text = "学信档案\n姓名：测试同学\n学籍信息\n专业：中药学",
    )

    /** 合成夹具：文本够长但没有任何学信数据信号，也没有营销标记（如账号设置页）。 */
    private val noSignalPayload = ChsiPagePayload(
        url = archiveUrl,
        title = "账号信息",
        text = "账号信息页面\n请先完善个人资料后再查看相关内容\n当前页面没有可展示的档案数据",
    )

    @Test
    fun `数据信号是宽松判定必要条件`() {
        assertTrue(ChsiExtractLogic.hasDataSignals(dataSignalPayload))
        assertFalse(ChsiExtractLogic.hasDataSignals(noSignalPayload))
        assertTrue(ChsiExtractLogic.isLoginSuccessAfterLogin(dataSignalPayload, archiveUrl))
        // 无数据信号、无 Cookie、无营销标记 → 不判定已登录（避免账号设置页误判）
        assertFalse(ChsiExtractLogic.isLoginSuccessAfterLogin(noSignalPayload, archiveUrl))
        // Cookie 辅助：session Cookie 存在时放宽
        assertTrue(
            ChsiExtractLogic.isLoginSuccessAfterLogin(
                noSignalPayload,
                archiveUrl,
                archiveCookiePresent = true,
            ),
        )
        // 营销页即使有 Cookie 也不判定
        assertFalse(
            ChsiExtractLogic.isLoginSuccessAfterLogin(
                marketingPayload,
                archiveUrl,
                archiveCookiePresent = true,
            ),
        )
    }

    @Test
    fun `my chsi 会话 Cookie 启发式识别`() {
        assertTrue(ChsiExtractLogic.hasArchiveSessionCookie("JSESSIONID=abcdef123456; other=1"))
        assertTrue(ChsiExtractLogic.hasArchiveSessionCookie("CHSI_SESSION=zzzzzzzzzzzz"))
        assertTrue(ChsiExtractLogic.hasArchiveSessionCookie("auth_token=0123456789abcdef"))
        // 短值 / 无关 cookie / 空 → false
        assertFalse(ChsiExtractLogic.hasArchiveSessionCookie("JSESSIONID=abc"))
        assertFalse(ChsiExtractLogic.hasArchiveSessionCookie("_ga=GA1.2.3.4; _gid=GA1.2.5.6"))
        assertFalse(ChsiExtractLogic.hasArchiveSessionCookie(""))
        assertFalse(ChsiExtractLogic.hasArchiveSessionCookie(null))
    }

    @Test
    fun `提取延迟重试常量与重试次数`() {
        assertEquals(3, ChsiNavigationLogic.EXTRACT_MAX_ATTEMPTS)
        assertEquals(1200L, ChsiNavigationLogic.EXTRACT_RETRY_INTERVAL_MS)
        assertEquals("https://my.chsi.com.cn", ChsiNavigationLogic.ARCHIVE_COOKIE_URL)
        assertTrue(ChsiNavigationLogic.shouldRetryExtract(0))
        assertTrue(ChsiNavigationLogic.shouldRetryExtract(1))
        assertFalse(ChsiNavigationLogic.shouldRetryExtract(2))
    }

    @Test
    fun `登录后 Cookie 辅助判定接入 loggedInAfterExtract`() {
        assertTrue(
            ChsiNavigationLogic.loggedInAfterExtract(
                payload = noSignalPayload,
                pageUrl = archiveUrl,
                mode = ChsiNavigationLogic.Mode.LOGIN,
                archiveCookiePresent = true,
            ),
        )
        assertFalse(
            ChsiNavigationLogic.loggedInAfterExtract(
                payload = noSignalPayload,
                pageUrl = archiveUrl,
                mode = ChsiNavigationLogic.Mode.LOGIN,
                archiveCookiePresent = false,
            ),
        )
    }
}
