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
}
