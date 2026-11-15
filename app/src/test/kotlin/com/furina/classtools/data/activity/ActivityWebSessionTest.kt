package com.furina.classtools.data.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 活动（第二课堂）H5 登录入口 / lgInfo 解析 / 错误文案单测。
 *
 * 2026-11-15 根因：登录 WebView 打开的是 `https://rcm.fjtcm.edu.cn/wechat/`（不带 hash 路由），
 * H5 在非微信环境用 hash 路由 `/:no/login`，缺学校编号时 `localStorage.schoolNo` 变成
 * `"undefined"` → `basic/third_party?schoolNo=undefined` 返回 HTTP 500
 * `{"message":"未找到对应编号的学校信息"}`。这里锁定「正确入口 + 兼容会话结构 + 可诊断错误」。
 *
 * **全部使用合成夹具（假 token/学号），不含任何真实账号数据**（红线：测试只用测试身份）。
 */
class ActivityWebSessionTest {

    /** 合成 lgInfo：微信/普通登录后 H5 `window.localStorage.setItem("lgInfo", JSON.stringify(o.data))`。 */
    private val wechatLgInfo = """
        {"id":"0123456789abcdef0123456789abcdef",
         "schoolId":"73e7a5155a2611eb9ded00163e0e0cc2",
         "category":1,
         "token":"fake-token-0000000000000000000001",
         "studentFile":{"classId":"fedcba9876543210fedcba9876543210","no":"TEST0001","name":"测试同学"}}
    """.trimIndent()

    /** 合成 lgInfo：CAS 跳转合并结构（顶层 userId/classId/token）。 */
    private val casLgInfo = """
        {"userId":"0123456789abcdef0123456789abcdef",
         "classId":"fedcba9876543210fedcba9876543210",
         "schoolNo":"fjtcm",
         "isRegister":1,
         "token":"fake-token-0000000000000000000002"}
    """.trimIndent()

    // ---- 入口 URL -----------------------------------------------------------

    @Test
    fun `登录入口必须带学校编号 hash 路由`() {
        // 旧入口（无 #/fjtcm/login）正是「未找到对应编号的学校信息」的直接原因。
        assertEquals("https://rcm.fjtcm.edu.cn/wechat/", ActivityWebSession.SPA_BASE)
        assertEquals(
            "https://rcm.fjtcm.edu.cn/wechat/#/fjtcm/login",
            ActivityWebSession.SPA_LOGIN_URL,
        )
        assertTrue(ActivityWebSession.SPA_LOGIN_URL.contains("#/fjtcm/login"))
        assertFalse(ActivityWebSession.SPA_LOGIN_URL.endsWith("/wechat/"))
    }

    @Test
    fun `兜底脚本写回学校编号且不含账号密码`() {
        val js = ActivityWebSession.ENSURE_SCHOOL_NO_JS
        assertTrue(js.contains("localStorage.setItem"))
        assertTrue(js.contains("schoolNo"))
        assertTrue(js.contains("fjtcm"))
        assertFalse(js.contains("password"))
        assertFalse(js.contains("token"))
    }

    @Test
    fun `识别路由成 undefined 的页面 URL`() {
        assertTrue(ActivityWebSession.hasUndefinedSchoolNo("https://rcm.fjtcm.edu.cn/wechat/#/undefined/login"))
        assertTrue(ActivityWebSession.hasUndefinedSchoolNo("https://rcm.fjtcm.edu.cn/wechat/#/undefined"))
        assertTrue(ActivityWebSession.hasUndefinedSchoolNo("https://stmapi.fjtcm.edu.cn/x?schoolNo=undefined"))
        assertFalse(ActivityWebSession.hasUndefinedSchoolNo("https://rcm.fjtcm.edu.cn/wechat/#/fjtcm/login"))
        assertFalse(ActivityWebSession.hasUndefinedSchoolNo("https://rcm.fjtcm.edu.cn/wechat/#/fjtcm/stdnt"))
        assertFalse(ActivityWebSession.hasUndefinedSchoolNo(null))
        assertFalse(ActivityWebSession.hasUndefinedSchoolNo(""))
    }

    // ---- lgInfo 解析 --------------------------------------------------------

    @Test
    fun `解析微信登录结构 id 加 studentFile classId`() {
        val token = ActivityWebSession.parseLgInfo(wechatLgInfo)
        assertNotNull(token)
        assertEquals("fake-token-0000000000000000000001", token!!.token)
        assertEquals("0123456789abcdef0123456789abcdef", token.userId)
        assertEquals("fedcba9876543210fedcba9876543210", token.classId)
        assertTrue(token.savedAt > 0)
    }

    @Test
    fun `解析 CAS 合并结构 userId classId`() {
        val token = ActivityWebSession.parseLgInfo(casLgInfo)
        assertNotNull(token)
        assertEquals("fake-token-0000000000000000000002", token!!.token)
        assertEquals("0123456789abcdef0123456789abcdef", token.userId)
        assertEquals("fedcba9876543210fedcba9876543210", token.classId)
    }

    @Test
    fun `解析 WebView 回调的双层转义字符串`() {
        // evaluateJavascript 回调形如 JSON 字符串字面量；旧实现只做简单 replace，转义多时容易坏。
        val raw = "\"{\\\"id\\\":\\\"u1\\\",\\\"token\\\":\\\"t1\\\",\\\"studentFile\\\":{\\\"classId\\\":\\\"c1\\\"}}\""
        val token = ActivityWebSession.parseLgInfo(raw)
        assertNotNull(token)
        assertEquals("u1", token!!.userId)
        assertEquals("c1", token.classId)
        assertEquals("t1", token.token)
    }

    @Test
    fun `解析 user 嵌套结构与 userFile classId`() {
        val raw = """
            {"token":"tk","user":{"id":"uid-9","userFile":{"classId":"cid-9"}}}
        """.trimIndent()
        val token = ActivityWebSession.parseLgInfo(raw)
        assertNotNull(token)
        assertEquals("uid-9", token!!.userId)
        assertEquals("cid-9", token.classId)
    }

    @Test
    fun `解析 data 包装结构`() {
        val raw = """{"data":{"token":"tk2","userId":"uid-2","classId":"cid-2"}}"""
        val token = ActivityWebSession.parseLgInfo(raw)
        assertNotNull(token)
        assertEquals("tk2", token!!.token)
        assertEquals("uid-2", token.userId)
        assertEquals("cid-2", token.classId)
    }

    @Test
    fun `未完成注册 isRegister 非 1 丢弃`() {
        val raw = """{"token":"tk","userId":"uid","classId":"cid","isRegister":0}"""
        assertNull(ActivityWebSession.parseLgInfo(raw))
        val ok = """{"token":"tk","userId":"uid","classId":"cid","isRegister":1}"""
        assertNotNull(ActivityWebSession.parseLgInfo(ok))
    }

    @Test
    fun `缺 userId 或 classId 不写入会话`() {
        assertNull(ActivityWebSession.parseLgInfo("""{"token":"tk","classId":"cid"}"""))
        assertNull(ActivityWebSession.parseLgInfo("""{"token":"tk","userId":"uid"}"""))
        assertNull(ActivityWebSession.parseLgInfo("""{"userId":"uid","classId":"cid"}"""))
        // classId 为空串（教师账号）也视为无效，避免拿空 classId 去请求列表。
        assertNull(ActivityWebSession.parseLgInfo("""{"token":"tk","userId":"uid","classId":"  "}"""))
    }

    @Test
    fun `非 JSON 输入安全返回 null`() {
        assertNull(ActivityWebSession.parseLgInfo(null))
        assertNull(ActivityWebSession.parseLgInfo(""))
        assertNull(ActivityWebSession.parseLgInfo("null"))
        assertNull(ActivityWebSession.parseLgInfo("not-json"))
        assertNull(ActivityWebSession.parseLgInfo("\"\""))
    }

    @Test
    fun `脱敏诊断只输出键名类型长度不输出值`() {
        val secret = "fake-token-0000000000000000000001"
        val shape = ActivityWebSession.describeLgInfo(wechatLgInfo)
        assertTrue(shape.startsWith("keys="))
        assertTrue(shape.contains("id:str:"))
        assertTrue(shape.contains("studentFile:obj:"))
        assertTrue(shape.contains("token:str:${secret.length}"))
        // 关键：token 值 / 学号值绝不出现在诊断串里（可安全写日志与 artifacts）。
        assertFalse(shape.contains(secret))
        assertFalse(shape.contains("TEST0001"))
        assertFalse(shape.contains("测试同学"))
        assertEquals("", ActivityWebSession.describeLgInfo(null))
        assertEquals("", ActivityWebSession.describeLgInfo("not-json"))
    }

    // ---- 错误文案（用户可见 = 服务器原文 + 状态，可诊断且不含敏感信息）--------

    @Test
    fun `学校信息未找到错误保留服务器原文与状态`() {
        val body = """{"timestamp":"2026-11-15T13:44:51.437+0000","status":500,"error":"Internal Server Error","message":"未找到对应编号的学校信息","path":"/third_party"}"""
        val message = activityErrorMessage(500, body, "活动列表")
        assertTrue(message.contains("服务器返回 500"))
        assertTrue(message.contains("活动列表"))
        assertTrue(message.contains("未找到对应编号的学校信息"))
        assertTrue(message.contains("请重新登录学校账号"))
    }

    @Test
    fun `401 映射为登录过期提示`() {
        assertEquals("登录已过期，请重新登录学校账号", activityErrorMessage(401))
        assertEquals("请求被拒绝（登录可能已失效）", activityErrorMessage(408))
    }

    @Test
    fun `提取服务器 message 字段并压缩非 JSON 原文`() {
        assertEquals("未找到对应编号的学校信息", activityServerMessageOf("""{"message":"未找到对应编号的学校信息"}"""))
        assertEquals("服务繁忙", activityServerMessageOf("""{"msg":"服务繁忙"}"""))
        assertEquals("x", activityServerMessageOf("""{"error":"x"}"""))
        assertEquals("", activityServerMessageOf(""))
        val html = activityServerMessageOf("  <html>\n 502 Bad Gateway \n</html>")
        assertTrue(html.contains("<html> 502 Bad Gateway"))
        assertFalse(html.contains("\n"))
        // 正常业务错误（不含学校信息）不应追加重新登录提示。
        val normal = activityErrorMessage(400, """{"message":"报名已截止"}""", "报名")
        assertTrue(normal.contains("服务器返回 400（报名）：报名已截止"))
        assertFalse(normal.contains("请重新登录"))
    }

    // ---- 2026-11-15 追加：活动页「请在微信小程序打开」微信环境兼容 -------------

    @Test
    fun `微信 UA 含 MicroMessenger 与 XWEB 字段`() {
        val ua = ActivityWebSession.WECHAT_UA
        assertTrue(ua.contains("MicroMessenger/8.0.49"))
        assertTrue(ua.contains("XWEB/"))
        assertTrue(ua.contains("Weixin"))
        assertTrue(ua.contains("Android"))
        assertFalse(ua.contains("wv)") && !ua.contains("MicroMessenger"))
    }

    @Test
    fun `识别微信 OAuth 跳转地址`() {
        assertTrue(
            ActivityWebSession.isWechatOauthUrl(
                "https://open.weixin.qq.com/connect/oauth2/authorize?appid=wxcf&redirect_uri=x",
            ),
        )
        assertTrue(ActivityWebSession.isWechatOauthUrl("weixin://dl/business/?t=abc"))
        assertFalse(ActivityWebSession.isWechatOauthUrl("https://open.weixin.qq.com/"))
        assertFalse(ActivityWebSession.isWechatOauthUrl("https://rcm.fjtcm.edu.cn/wechat/#/fjtcm/login"))
        assertFalse(ActivityWebSession.isWechatOauthUrl(null))
        assertFalse(ActivityWebSession.isWechatOauthUrl(""))
    }

    @Test
    fun `微信环境桩只提供只读能力不伪造登录`() {
        val stub = ActivityWebSession.WX_ENV_STUB_JS
        assertTrue(stub.contains("__wxjs_environment"))
        assertTrue(stub.contains("miniprogram: false"))
        // 不得包含登录/支付/用户信息相关 API
        listOf(
            "login", "pay", "requestPayment", "getUserInfo", "getUserProfile",
            "localStorage", "lgInfo", "token", "openId",
        ).forEach { forbidden ->
            assertFalse("微信环境桩不应包含 $forbidden", stub.contains(forbidden))
        }
    }

    @Test
    fun `会话桩只写最小微信环境字段且 openId 留空`() {
        val seed = ActivityWebSession.SEED_WX_SESSION_JS
        assertTrue(seed.contains("wxUser"))
        assertTrue(seed.contains("wxNickname"))
        assertTrue(seed.contains("schoolNo"))
        // 关键：不伪造微信身份 → openId 必须为空字符串（不写入随机/真实 openId）。
        assertTrue(seed.contains("openId: ''"))
        assertFalse(seed.contains("Math.random"))
        // 不写 lgInfo / token，不接触账号密码。
        assertFalse(seed.contains("lgInfo"))
        assertFalse(seed.contains("token"))
        assertFalse(seed.contains("password"))
        // 幂等：先判断已有合法 wxUser 才决定是否写入。
        assertTrue(seed.contains("valid"))
    }

    @Test
    fun `拦截 OAuth 提示文案可诊断`() {
        assertTrue(ActivityWebSession.MSG_OAUTH_BLOCKED.contains("微信"))
        assertTrue(ActivityWebSession.MSG_OAUTH_BLOCKED.contains("重新加载"))
        assertEquals("请在微信", ActivityWebSession.WECHAT_PROMPT_MARKER)
    }
}
