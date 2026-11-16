package com.furina.classtools.features.todaycampus

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 今日校园 CAS HTTP 登录纯逻辑单测（2026-11-16 用户反馈：今日校园还是打不开）。
 *
 * 参考 HFUT-Schedule `RedirectTicketInterceptor.kt` / `Starter.startWebUrlInner(cookie)` 思路：
 * OkHttp 完成 CAS 登录拿 Cookie → 注入 WebView → 打开门户首页。
 *
 * 覆盖：登录 URL 构造（service 编码）、CAS 隐藏域解析（execution 等）、表单字段构造、
 * 成功/失败判定、错误文案、Cookie 数据模型与 WebView 注入 URL/头、内存 CookieJar 保存/匹配。
 *
 * 全部为纯逻辑断言，不联网、不读取真实账号密码（表单只用假账号/假密码）。
 */
class TodayCampusCasLogicTest {

    private val casHtml = """
        <!doctype html><html><body>
        <form id="fm1" method="post" action="/authserver/login?service=x">
          <input type="text" name="username" id="username"/>
          <input type="password" name="passwordText" id="password"/>
          <input id="saltPassword" name="password" type="hidden" value=""/>
          <input type="hidden" name="execution" value="e1s1"/>
          <input type="hidden" name="_eventId" value="submit"/>
          <input type="hidden" name="cllt" value="userNameLogin"/>
          <input type="hidden" name="dllt" value="generalLogin"/>
          <input type="hidden" name="lt" value="LT-123"/>
          <input type="hidden" name="rmShown" value="1"/>
          <input type="hidden" id="pwdEncryptSalt" value="QCrRoBuceR4RpkfK"/>
          <input type="submit" name="submit" value="登录"/>
        </form>
        </body></html>
    """.trimIndent()

    @Test
    fun `CAS 登录 URL 带 URL 编码的 service`() {
        val url = TodayCampusCasLogic.buildLoginUrl()
        assertTrue(url.startsWith("https://authserver.fjtcm.edu.cn/authserver/login?service="))
        assertTrue(url.contains("https%3A%2F%2Fe.fjtcm.edu.cn%2Flogin2"))
        assertEquals("https://e.fjtcm.edu.cn/login2", TodayCampusCasLogic.SERVICE_URL)
    }

    @Test
    fun `解析 CAS 隐藏域 execution 等字段`() {
        val hidden = TodayCampusCasLogic.parseHiddenInputs(casHtml)
        assertEquals("e1s1", hidden["execution"])
        assertEquals("submit", hidden["_eventId"])
        assertEquals("LT-123", hidden["lt"])
        assertEquals("generalLogin", hidden["dllt"])
        assertEquals("userNameLogin", hidden["cllt"])
        assertEquals("1", hidden["rmShown"])
        // 密码盐（只有 id）单独提取，供 AES 加密用
        assertEquals("QCrRoBuceR4RpkfK", hidden[TodayCampusCasLogic.SALT_FIELD_KEY])
        // 账号/密码等非隐藏域不应被当成隐藏域收集
        assertFalse(hidden.containsKey("username"))
        assertFalse(hidden.containsKey("password"))
        assertFalse(hidden.containsKey("passwordText"))
    }

    @Test
    fun `隐藏域解析兼容属性顺序与单双引号`() {
        val html = """
            <input value='v1' name='execution'>
            <input type="hidden" value="v2" name="lt">
            <input name="execution2" value="ignored">
            <input id='pwdEncryptSalt' value='abc123DEF456ghi7'>
        """.trimIndent()
        val hidden = TodayCampusCasLogic.parseHiddenInputs(html)
        assertEquals("v1", hidden["execution"])
        assertEquals("v2", hidden["lt"])
        assertFalse(hidden.containsKey("execution2"))
        assertEquals("abc123DEF456ghi7", hidden[TodayCampusCasLogic.SALT_FIELD_KEY])
    }

    @Test
    fun `隐藏域空输入安全`() {
        assertTrue(TodayCampusCasLogic.parseHiddenInputs(null).isEmpty())
        assertTrue(TodayCampusCasLogic.parseHiddenInputs("").isEmpty())
        assertTrue(TodayCampusCasLogic.parseHiddenInputs("<html>没有表单</html>").isEmpty())
    }

    @Test
    fun `构造 CAS 登录表单字段`() {
        val hidden = TodayCampusCasLogic.parseHiddenInputs(casHtml)
        val form = TodayCampusCasLogic.buildFormBody(hidden, "2024000001", "fake-pwd")
        assertEquals("2024000001", form["username"])
        assertEquals("submit", form["_eventId"])
        assertEquals("e1s1", form["execution"])
        assertEquals("LT-123", form["lt"])
        assertEquals("userNameLogin", form["cllt"])
        assertEquals("generalLogin", form["dllt"])
        // 密码字段提交的是加密后的密文（学校 CAS 页面同算法），绝不能是明文
        assertTrue(form["password"] != "fake-pwd")
        assertTrue(form["password"]!!.isNotBlank())
        // 盐字段只用于加密，不参与表单回传
        assertFalse(form.containsKey(TodayCampusCasLogic.SALT_FIELD_KEY))
        // 缺省补充字段
        val minimal = TodayCampusCasLogic.buildFormBody(mapOf("execution" to "e"), "u", "p")
        assertEquals("userNameLogin", minimal["cllt"])
        assertEquals("generalLogin", minimal["dllt"])
        assertEquals("1", minimal["rmShown"])
        assertEquals("submit", minimal["_eventId"])
    }

    @Test
    fun `CAS 密码 AES-CBC-PKCS7 加密可解密回随机前缀加密码`() {
        // 与页面 encrypt.js 同算法：明文 = randomString(64) + password，key = salt，iv = randomString(16)
        val salt = "QCrRoBuceR4RpkfK"
        val prefix64 = "A".repeat(64)
        val iv16 = "ABCDEFGHJKMNPQRS"
        assertEquals(64, prefix64.length)
        assertEquals(16, iv16.length)
        val encrypted = TodayCampusCasLogic.encryptPasswordWith(
            password = "fake-pwd-123",
            salt = salt,
            prefix64 = prefix64,
            iv16 = iv16,
        )
        assertTrue(encrypted != "fake-pwd-123")
        // 用 JCE 解密验证格式正确（Base64 + AES/CBC/PKCS5）
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "AES"),
            javax.crypto.spec.IvParameterSpec(iv16.toByteArray(Charsets.UTF_8)),
        )
        val plain = String(
            cipher.doFinal(java.util.Base64.getDecoder().decode(encrypted)),
            Charsets.UTF_8,
        )
        assertEquals(prefix64 + "fake-pwd-123", plain)
        // 盐异常（空/长度不合法）时回退明文（保证不崩溃，仍可回退人工登录）
        assertEquals("pw", TodayCampusCasLogic.encryptPasswordWith("pw", "", "p".repeat(64), "i".repeat(16)))
        assertEquals("pw", TodayCampusCasLogic.encryptPasswordWith("pw", "short", "p".repeat(64), "i".repeat(16)))
        assertEquals("pw", TodayCampusCasLogic.encryptPasswordWith("pw", salt, "p".repeat(64), "bad-iv"))
    }

    @Test
    fun `随机字符串字符集与长度`() {
        val random = java.util.Random(42L)
        val value = TodayCampusCasLogic.randomString(64, random)
        assertEquals(64, value.length)
        val allowed = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"
        assertTrue(value.all { it in allowed })
        assertEquals(16, TodayCampusCasLogic.randomString(16, random).length)
    }

    @Test
    fun `buildFormBody 可注入加密器便于测试`() {
        val hidden = TodayCampusCasLogic.parseHiddenInputs(casHtml)
        val form = TodayCampusCasLogic.buildFormBody(
            hidden = hidden,
            username = "2024000001",
            password = "fake-pwd",
            encryptor = { password, salt -> "enc($password|$salt)" },
        )
        assertEquals("enc(fake-pwd|QCrRoBuceR4RpkfK)", form["password"])
    }

    @Test
    fun `登录成功判定要求离开 CAS 登录页`() {
        assertFalse(
            TodayCampusCasLogic.isLoginSuccess(
                "https://authserver.fjtcm.edu.cn/authserver/login?service=x",
                casHtml,
            ),
        )
        assertTrue(
            TodayCampusCasLogic.isLoginSuccess(
                "https://e.fjtcm.edu.cn/index.html",
                "网上办事服务大厅",
            ),
        )
        assertTrue(
            TodayCampusCasLogic.isLoginSuccess(
                "https://e.fjtcm.edu.cn/login2",
                "",
            ),
        )
        // 其它校内跳板页（非 authserver 登录页）用页面特征兜底
        assertTrue(
            TodayCampusCasLogic.isLoginSuccess(
                "https://e.fjtcm.edu.cn/portal",
                "融合门户",
            ),
        )
        assertFalse(TodayCampusCasLogic.isLoginSuccess(null, null))
        assertFalse(TodayCampusCasLogic.isLoginSuccess("file:///tmp/a.html", "融合门户"))
    }

    @Test
    fun `登录失败文案识别`() {
        assertEquals("需要验证码，请在页面手动登录", TodayCampusCasLogic.loginErrorOf("请输入验证码"))
        assertEquals(
            "账号或密码错误，请在页面手动登录",
            TodayCampusCasLogic.loginErrorOf("账号或密码错误"),
        )
        assertEquals(
            "账号或密码错误，请在页面手动登录",
            TodayCampusCasLogic.loginErrorOf("用户名或密码错误"),
        )
        assertEquals("账号状态异常，请在页面手动登录", TodayCampusCasLogic.loginErrorOf("账号已锁定"))
        assertEquals("统一身份认证繁忙，请稍后手动登录", TodayCampusCasLogic.loginErrorOf("服务器繁忙"))
        assertNull(TodayCampusCasLogic.loginErrorOf("正常页面"))
        assertNull(TodayCampusCasLogic.loginErrorOf(null))
    }

    @Test
    fun `Cookie 数据模型转 WebView 注入 URL 与请求头`() {
        val cookie = CasCookie(
            name = "JSESSIONID",
            value = "abc123",
            domain = ".fjtcm.edu.cn",
            path = "/",
            secure = true,
        )
        assertEquals("JSESSIONID=abc123", TodayCampusCasLogic.cookieHeaderOf(cookie))
        assertEquals("https://fjtcm.edu.cn/", TodayCampusCasLogic.cookieUrlOf(cookie))
        // 带子域/路径
        val authCookie = CasCookie(
            name = "CASTGC",
            value = "TGT-1",
            domain = "authserver.fjtcm.edu.cn",
            path = "/authserver",
            secure = true,
        )
        assertEquals(
            "https://authserver.fjtcm.edu.cn/authserver",
            TodayCampusCasLogic.cookieUrlOf(authCookie),
        )
    }

    @Test
    fun `内存 CookieJar 保存并按域匹配`() {
        val jar = MemoryCookieJar()
        val loginUrl = "https://authserver.fjtcm.edu.cn/authserver/login".toHttpUrl()
        val tunnelCookie = Cookie.parse(loginUrl, "CASTGC=TGT-123; Path=/; Secure")!!
        val portalCookie = Cookie.parse(
            "https://e.fjtcm.edu.cn/".toHttpUrl(),
            "JSESSIONID=portal-session; Path=/",
        )!!
        jar.saveFromResponse(loginUrl, listOf(tunnelCookie))
        jar.saveFromResponse("https://e.fjtcm.edu.cn/".toHttpUrl(), listOf(portalCookie))
        val snapshot = jar.snapshot()
        assertEquals(2, snapshot.size)
        val names = snapshot.map { it.name }.toSet()
        assertTrue(names.contains("CASTGC"))
        assertTrue(names.contains("JSESSIONID"))
        // 按域加载
        val forAuth = jar.loadForRequest("https://authserver.fjtcm.edu.cn/authserver/login?service=x".toHttpUrl())
        assertEquals(listOf("CASTGC"), forAuth.map { it.name })
        val forPortal = jar.loadForRequest("https://e.fjtcm.edu.cn/index.html".toHttpUrl())
        assertEquals(listOf("JSESSIONID"), forPortal.map { it.name })
        // 同 key 覆盖（不重复）
        jar.saveFromResponse(loginUrl, listOf(tunnelCookie))
        assertEquals(2, jar.snapshot().size)
    }

    @Test
    fun `CAS 结果对象不含密码且可构造`() {
        val ok = CasLoginResult(
            success = true,
            cookies = listOf(CasCookie("a", "b", "e.fjtcm.edu.cn")),
            finalUrl = "https://e.fjtcm.edu.cn/index.html",
        )
        assertTrue(ok.success)
        assertEquals(1, ok.cookies.size)
        assertNotNull(ok.finalUrl)
        val fail = CasLoginResult(success = false, error = "自动登录未完成")
        assertFalse(fail.success)
        assertTrue(fail.cookies.isEmpty())
        // 状态文案可诊断（不包含密码/账号）
        assertTrue(TodayCampusCasLogic.MSG_CAS_LOGGING_IN.contains("登录"))
        assertTrue(TodayCampusCasLogic.MSG_CAS_FALLBACK.contains("回退") || TodayCampusCasLogic.MSG_CAS_FALLBACK.contains("切换"))
        assertTrue(TodayCampusCasLogic.MSG_CAS_SUCCESS.contains("门户"))
    }
}
