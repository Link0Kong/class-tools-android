package com.furina.classtools.data.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 活动登录「双主路径 + 诚实降级」纯逻辑单测（2026-11-16 task-73 模拟器实测结论）。
 *
 * 实测事实（emulator-5560）：
 * - 登录页 = 学校账号登录（上传头像 + 身份证后六位 + 学号），另有「我是教师」；
 * - 头像上传不是 `<input type=file>`，走微信 JSSDK chooseImage/uploadImage，App 内点击无反应；
 * - 不传头像点登录 → 客户端「请上传头像」；带头像 URL 点登录 → POST `/basic/user/student/bind`
 *   返回 HTTP 500「openId不能为空」→ 学校账号登录同样要求微信 openId；
 * - openId 只能由微信客户端内的学校 H5 微信授权（oauth2 authorize + code 换 token）取得。
 *
 * 断言重点：UI 必须呈现「在微信内打开」+「学校账号登录（受限）」双路径；不得出现
 * 「App 内可微信登录」的误导文案；不得包含伪造 openId/登录态的提示。
 *
 * 全部为纯逻辑断言，不联网、不接触任何真实账号。
 */
class ActivityWeChatLoginTest {

    @Test
    fun `微信内打开链接是带学校编号的活动 H5 登录入口`() {
        val url = ActivityWeChatLogin.shareUrl()
        assertTrue(url, url.startsWith("https://rcm.fjtcm.edu.cn/wechat/"))
        assertTrue(url, url.contains("#/fjtcm/login"))
        assertEquals(ActivityWebSession.SPA_LOGIN_URL, url)
    }

    @Test
    fun `微信客户端可见性三态映射`() {
        assertEquals(
            ActivityWeChatLogin.Presence.INSTALLED,
            ActivityWeChatLogin.presence(wechatResolved = true, sdkInt = 37),
        )
        assertEquals(
            ActivityWeChatLogin.Presence.NOT_INSTALLED,
            ActivityWeChatLogin.presence(wechatResolved = false, sdkInt = 29),
        )
        assertEquals(
            ActivityWeChatLogin.Presence.UNKNOWN,
            ActivityWeChatLogin.presence(wechatResolved = false, sdkInt = 30),
        )
        assertEquals(
            ActivityWeChatLogin.Presence.UNKNOWN,
            ActivityWeChatLogin.presence(wechatResolved = false, sdkInt = 37),
        )
    }

    @Test
    fun `检测状态文案不误报未安装`() {
        val unknown = ActivityWeChatLogin.statusText(ActivityWeChatLogin.Presence.UNKNOWN)
        assertTrue(unknown, unknown.contains("无法直接确认"))
        assertFalse("UNKNOWN 不能断言未安装", unknown.contains("未检测到"))
        assertTrue(
            ActivityWeChatLogin.statusText(ActivityWeChatLogin.Presence.INSTALLED).contains("微信"),
        )
        assertTrue(
            ActivityWeChatLogin.statusText(ActivityWeChatLogin.Presence.NOT_INSTALLED).contains("未检测到"),
        )
    }

    // ---- 双主路径文案 -------------------------------------------------------

    @Test
    fun `主路径一是在微信内打开且两种登录方式都说明`() {
        assertTrue(ActivityWeChatLogin.TITLE_OPEN_IN_WECHAT.contains("微信内打开"))
        val desc = ActivityWeChatLogin.DESC_OPEN_IN_WECHAT
        assertTrue(desc, desc.contains("微信身份"))
        assertTrue(desc, desc.contains("学校账号"))
        assertTrue(desc, desc.contains("头像"))
    }

    @Test
    fun `主路径二是学校账号登录且明确 App 内受限`() {
        assertTrue(ActivityWeChatLogin.TITLE_SCHOOL_LOGIN_LIMITED.contains("学校账号登录"))
        val desc = ActivityWeChatLogin.DESC_SCHOOL_LOGIN_LIMITED
        assertTrue(desc, desc.contains("openId"))
        assertTrue(desc, desc.contains("App 内无法"))
        assertTrue(desc, desc.contains("openId不能为空"))
        assertTrue(desc, desc.contains("请上传头像"))
        assertTrue(desc, desc.contains("在微信内打开"))
    }

    @Test
    fun `openId 限制与降级说明符合实测事实`() {
        val limit = ActivityWeChatLogin.HINT_OPENID_LIMIT
        assertTrue(limit, limit.contains("openId"))
        assertTrue(limit, limit.contains("微信客户端"))
        assertTrue(limit, limit.contains("oauth2"))
        assertTrue(limit, limit.contains("App 无法合法获取"))
        val degraded = ActivityWeChatLogin.HINT_DEGRADED
        assertTrue(degraded, degraded.contains("Cookie 不共享"))
        assertTrue(degraded, degraded.contains("不会自动变成已登录"))
        assertTrue(degraded, degraded.contains("不会伪造"))
    }

    @Test
    fun `不再出现误导性的 App 内微信登录按钮文案`() {
        // 旧版标题「微信登录」会让人以为 App 内能微信登录，已替换为「在微信内打开活动页」。
        assertFalse(ActivityWeChatLogin.TITLE_OPEN_IN_WECHAT == "微信登录")
        assertFalse(ActivityWeChatLogin.TITLE_OPEN_IN_WECHAT.startsWith("微信登录"))
        val allTexts = listOf(
            ActivityWeChatLogin.TITLE_OPEN_IN_WECHAT,
            ActivityWeChatLogin.DESC_OPEN_IN_WECHAT,
            ActivityWeChatLogin.TITLE_SCHOOL_LOGIN_LIMITED,
            ActivityWeChatLogin.DESC_SCHOOL_LOGIN_LIMITED,
            ActivityWeChatLogin.HINT_OPENID_LIMIT,
            ActivityWeChatLogin.HINT_DEGRADED,
            ActivityWeChatLogin.MSG_PAGE_REQUIRES_WECHAT,
        )
        allTexts.forEach { text ->
            assertFalse("不应宣称 App 内微信登录：$text", text.contains("App 内微信登录"))
            assertFalse("不应宣称已登录：$text", text.contains("已微信登录"))
        }
    }

    @Test
    fun `App 内检测到微信能力缺失时提示去微信内打开`() {
        val js = ActivityWeChatLogin.LOGIN_REQUIREMENT_JS
        assertTrue(js, js.contains("openId不能为空"))
        assertTrue(js, js.contains("请上传头像"))
        assertTrue(js, js.contains("return ''"))
        // 只读检测：不能包含网络请求/自动提交/写登录态。
        assertFalse(js.contains("fetch("))
        assertFalse(js.contains("XMLHttpRequest"))
        assertFalse(js.contains("localStorage.setItem"))
        val msg = ActivityWeChatLogin.MSG_PAGE_REQUIRES_WECHAT
        assertTrue(msg, msg.contains("微信"))
        assertTrue(msg, msg.contains("在微信中打开"))
        assertTrue(msg, msg.contains("App 内无法"))
    }

    @Test
    fun `按钮与复制链接文案齐全`() {
        listOf(
            ActivityWeChatLogin.BTN_OPEN_IN_WECHAT,
            ActivityWeChatLogin.BTN_COPY_LINK,
            ActivityWeChatLogin.BTN_OPEN_SCHOOL_LOGIN,
            ActivityWeChatLogin.MSG_LINK_COPIED,
            ActivityWeChatLogin.MSG_COPY_FAILED,
            ActivityWeChatLogin.MSG_WECHAT_OPENED,
            ActivityWeChatLogin.MSG_WECHAT_MISSING,
            ActivityWeChatLogin.MSG_WECHAT_UNKNOWN_AFTER_FAIL,
            ActivityWeChatLogin.MSG_OAUTH_ONLY_IN_WECHAT,
        ).forEach { text -> assertTrue("文案不能为空", text.isNotBlank()) }
        assertTrue(ActivityWeChatLogin.BTN_OPEN_IN_WECHAT.contains("微信"))
        assertTrue(ActivityWeChatLogin.BTN_COPY_LINK.contains("复制"))
        assertTrue(ActivityWeChatLogin.MSG_OAUTH_ONLY_IN_WECHAT.contains("不会假装"))
    }
}
