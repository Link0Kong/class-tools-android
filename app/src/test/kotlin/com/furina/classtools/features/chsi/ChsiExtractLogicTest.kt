package com.furina.classtools.features.chsi

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学信网提取/卡片纯逻辑单测（规格 `_audit/chsi_entry_20261115/CHSI_ENTRY_SPEC.md` §1/§3）。
 *
 * 覆盖：10 个官方工具常量与 URL、UA 切换常量、注入 JS 内容与安全（只读/不发请求/不碰 cookie）、
 * WebView 回调解析（双层 JSON 字符串）、宽容 JSON 解析、标签就近取 1–3 行、
 * 空态降级、身份证/手机号掩码、登录态 URL 判定、更新时间文案。
 *
 * **全部使用合成夹具，不含任何真实学信网数据/账号**（红线：禁止真实账号/真实数据跑测试）。
 */
class ChsiExtractLogicTest {

    // ---- 官方工具 10 项 -----------------------------------------------------

    @Test
    fun `官方工具 10 项且名称与 URL 与规格一致`() {
        val tools = ChsiCatalog.officialTools
        assertEquals(10, tools.size)
        assertEquals(10, tools.map { it.id }.distinct().size)
        val byTitle = tools.associateBy { it.title }
        assertEquals("https://my.chsi.com.cn/archive/index.jsp", byTitle.getValue("学信档案").url)
        assertEquals("https://www.chsi.com.cn/xlcx/lscx.jsp", byTitle.getValue("学籍/学历查询").url)
        assertEquals("https://www.chsi.com.cn/xwcx/index.jsp", byTitle.getValue("学位查询").url)
        assertEquals("https://my.chsi.com.cn/archive/bab/index.jsp", byTitle.getValue("在线验证报告").url)
        assertEquals("https://www.chsi.com.cn/xlcx/bgcx.jsp", byTitle.getValue("报告在线验证").url)
        assertEquals("https://www.chsi.com.cn/xlrz/index.jsp", byTitle.getValue("学历认证报告").url)
        assertEquals("https://my.chsi.com.cn/archive/xlzp/index.jsp", byTitle.getValue("照片校对").url)
        assertEquals("https://yz.chsi.com.cn/", byTitle.getValue("研招网").url)
        assertEquals("https://xz.chsi.com.cn/", byTitle.getValue("学职平台").url)
        assertEquals("https://www.chsi.com.cn/help/", byTitle.getValue("帮助中心").url)
    }

    @Test
    fun `需登录标记只打在档案类工具上`() {
        val needsLogin = ChsiCatalog.officialTools.filter { it.needsLogin }.map { it.title }
        assertEquals(listOf("学信档案", "在线验证报告", "照片校对"), needsLogin)
        assertFalse(ChsiCatalog.officialTools.first { it.title == "学籍/学历查询" }.needsLogin)
    }

    @Test
    fun `入口与登录 URL 常量与规格一致`() {
        assertEquals("https://my.chsi.com.cn/archive/wap/index.jsp", ChsiCatalog.ARCHIVE_ENTRY)
        assertEquals("https://my.chsi.com.cn/archive/index.jsp", ChsiCatalog.ARCHIVE_HOME)
        assertEquals("https://account.chsi.com.cn/passport/login", ChsiCatalog.LOGIN_URL)
        assertEquals("https://www.chsi.com.cn/", ChsiCatalog.HOME_URL)
    }

    @Test
    fun `4 张数据卡的目标官方页面`() {
        assertEquals(4, ChsiCatalog.dataCards.size)
        assertEquals(ChsiCatalog.ARCHIVE_ENTRY, ChsiCatalog.dataCardUrl(ChsiDataCardKind.ENROLLMENT))
        assertEquals(
            "https://www.chsi.com.cn/xlcx/lscx.jsp",
            ChsiCatalog.dataCardUrl(ChsiDataCardKind.EDUCATION),
        )
        assertEquals(
            "https://www.chsi.com.cn/xwcx/index.jsp",
            ChsiCatalog.dataCardUrl(ChsiDataCardKind.DEGREE),
        )
        assertEquals(
            "https://my.chsi.com.cn/archive/bab/index.jsp",
            ChsiCatalog.dataCardUrl(ChsiDataCardKind.VERIFY_REPORT),
        )
    }

    @Test
    fun `UA 切换常量区分桌面版与手机版`() {
        assertTrue(ChsiCatalog.DESKTOP_UA.contains("Windows"))
        assertTrue(ChsiCatalog.DESKTOP_UA.contains("Chrome"))
        assertFalse(ChsiCatalog.DESKTOP_UA.contains("Mobile"))
        assertTrue(ChsiCatalog.MOBILE_UA.contains("Mobile"))
        assertTrue(ChsiCatalog.MOBILE_UA.contains("Android"))
        assertTrue(ChsiCatalog.DESKTOP_UA != ChsiCatalog.MOBILE_UA)
    }

    // ---- 注入 JS ------------------------------------------------------------

    @Test
    fun `注入脚本只读页面且返回 JSON`() {
        val script = ChsiExtractLogic.buildExtractScript()
        // 必需内容
        assertTrue(script.contains("document.body.innerText"))
        assertTrue(script.contains("querySelectorAll('a[href]')"))
        assertTrue(script.contains("chsi.com.cn"))
        assertTrue(script.contains("JSON.stringify"))
        assertTrue(script.contains("location.href"))
        assertTrue(script.contains("document.title"))
        assertTrue(script.contains(ChsiExtractLogic.MAX_TEXT_LENGTH.toString()))
    }

    @Test
    fun `注入脚本不碰凭据不发请求`() {
        val script = ChsiExtractLogic.buildExtractScript()
        listOf(
            "document.cookie",
            "localStorage",
            "sessionStorage",
            "password",
            "XMLHttpRequest",
            "fetch(",
            "console.",
        ).forEach { forbidden ->
            assertFalse("脚本不应包含 $forbidden", script.contains(forbidden))
        }
    }

    // ---- WebView 回调解析 ---------------------------------------------------

    @Test
    fun `解码 WebView 回调的双层 JSON 字符串`() {
        val payloadJson = """{"url":"https://my.chsi.com.cn/archive/wap/index.jsp","title":"学信档案","text":"a","links":[]}"""
        val wrapped = Json.encodeToString(String.serializer(), payloadJson)
        assertEquals(payloadJson, ChsiExtractLogic.decodeWebViewResult(wrapped))
        // 非字符串包装（直接 JSON）原样返回
        assertEquals(payloadJson, ChsiExtractLogic.decodeWebViewResult(payloadJson))
        // null / 空
        assertNull(ChsiExtractLogic.decodeWebViewResult(null))
        assertNull(ChsiExtractLogic.decodeWebViewResult("null"))
        assertNull(ChsiExtractLogic.decodeWebViewResult("   "))
    }

    @Test
    fun `解码后解析出页面数据`() {
        val payloadJson =
            """{"url":"https://my.chsi.com.cn/archive/wap/index.jsp","title":"学信档案","text":"学籍信息\n学校 某某大学","links":[{"t":"在线验证报告","h":"https://my.chsi.com.cn/archive/bab/index.jsp"}]}"""
        val wrapped = Json.encodeToString(String.serializer(), payloadJson)
        val payload = ChsiExtractLogic.parseWebViewResult(wrapped)
        assertTrue(payload != null)
        assertEquals("https://my.chsi.com.cn/archive/wap/index.jsp", payload!!.url)
        assertEquals("学信档案", payload.title)
        assertTrue(payload.text.contains("某某大学"))
        assertEquals(1, payload.links.size)
        assertEquals("在线验证报告", payload.links.first().text)
    }

    @Test
    fun `宽容解析缺省字段与非法数据`() {
        val minimal = ChsiExtractLogic.parsePayload("""{"text":"a"}""")
        assertTrue(minimal != null)
        assertEquals("", minimal!!.url)
        assertEquals("", minimal.title)
        assertTrue(minimal.links.isEmpty())

        assertNull(ChsiExtractLogic.parsePayload(null))
        assertNull(ChsiExtractLogic.parsePayload(""))
        assertNull(ChsiExtractLogic.parsePayload("null"))
        assertNull(ChsiExtractLogic.parsePayload("not-a-json"))
        assertNull(ChsiExtractLogic.parsePayload("[]"))
        assertNull(ChsiExtractLogic.parseWebViewResult("\"not-a-json\""))
    }

    @Test
    fun `解析结果文本截断到上限`() {
        val longText = "a".repeat(ChsiExtractLogic.MAX_TEXT_LENGTH + 100)
        val payload = ChsiExtractLogic.parsePayload("""{"text":"$longText"}""")
        assertEquals(ChsiExtractLogic.MAX_TEXT_LENGTH, payload!!.text.length)
    }

    // ---- 敏感信息掩码 -------------------------------------------------------

    @Test
    fun `掩码 18 位身份证号`() {
        val masked = ChsiExtractLogic.maskSensitive("身份证号 350123200601011234")
        assertFalse(masked.contains("350123200601011234"))
        assertTrue(masked.contains("350123********1234"))
        // 字母结尾的身份证号
        val maskedX = ChsiExtractLogic.maskSensitive("证件号：35012320060101123X")
        assertFalse(maskedX.contains("35012320060101123X"))
        assertTrue(maskedX.contains("350123********123X"))
    }

    @Test
    fun `掩码 11 位手机号`() {
        val masked = ChsiExtractLogic.maskSensitive("手机号 13800138000")
        assertFalse(masked.contains("13800138000"))
        assertTrue(masked.contains("138****8000"))
        // 不合法的 11 位（非 1[3-9] 开头）不动
        val notPhone = ChsiExtractLogic.maskSensitive("编号 10000138000")
        assertTrue(notPhone.contains("10000138000"))
    }

    @Test
    fun `掩码不影响普通数字与多次出现`() {
        val text = "学分 4 金额 1234 手机 13800138000 备用 13911112222 身份证 350123200601011234"
        val masked = ChsiExtractLogic.maskSensitive(text)
        assertTrue(masked.contains("学分 4"))
        assertTrue(masked.contains("金额 1234"))
        assertTrue(masked.contains("138****8000"))
        assertTrue(masked.contains("139****2222"))
        assertTrue(masked.contains("350123********1234"))
        assertFalse(masked.contains("200601"))
        assertFalse(masked.contains("13800138000"))
    }

    // ---- 标签就近取行 / 数据卡 ---------------------------------------------

    @Test
    fun `学籍卡按标签就近取 1 到 3 行`() {
        val payload = syntheticPayload()
        val lines = ChsiExtractLogic.extractCardLines(payload, ChsiDataCardKind.ENROLLMENT)
        assertTrue(lines.isNotEmpty())
        assertTrue("最多 3 行：$lines", lines.size <= ChsiExtractLogic.MAX_CARD_LINES)
        assertTrue("应包含学籍相关行：$lines", lines.any { it.contains("学籍") || it.contains("学校") })
    }

    @Test
    fun `学历卡提取院校与学历文本`() {
        val lines = ChsiExtractLogic.extractCardLines(syntheticPayload(), ChsiDataCardKind.EDUCATION)
        assertTrue(lines.isNotEmpty())
        val joined = lines.joinToString("\n")
        assertTrue("应包含院校/学历：$joined", joined.contains("院校") || joined.contains("学历"))
    }

    @Test
    fun `学位卡提取学位与授予信息`() {
        val lines = ChsiExtractLogic.extractCardLines(syntheticPayload(), ChsiDataCardKind.DEGREE)
        assertTrue(lines.isNotEmpty())
        val joined = lines.joinToString("\n")
        assertTrue("应包含学位/授予：$joined", joined.contains("学位") || joined.contains("授予"))
    }

    @Test
    fun `在线验证报告卡提取报告信息`() {
        val lines = ChsiExtractLogic.extractCardLines(syntheticPayload(), ChsiDataCardKind.VERIFY_REPORT)
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.joinToString().contains("报告"))
    }

    @Test
    fun `提取行的身份证与手机号已掩码`() {
        val payload = ChsiPayloadWithSensitiveData()
        val lines = ChsiExtractLogic.extractCardLines(payload, ChsiDataCardKind.ENROLLMENT)
        val joined = lines.joinToString("\n")
        assertFalse(joined.contains("350123200601011234"))
        assertFalse(joined.contains("13800138000"))
        assertTrue(joined.contains("350123********1234"))
        assertTrue(joined.contains("138****8000"))
    }

    @Test
    fun `提取不到时四张卡都为空态兜底`() {
        val empty = ChsiPagePayload(url = "https://my.chsi.com.cn/archive/wap/index.jsp", text = "")
        val cards = ChsiExtractLogic.extractCards(empty)
        assertEquals(4, cards.size)
        assertTrue(cards.all { it.status == ChsiCardStatus.EMPTY })
        assertTrue(cards.all { it.lines == listOf(ChsiTexts.MSG_EMPTY_FALLBACK) })
        assertTrue(cards.all { it.targetUrl.startsWith("https://") })
    }

    @Test
    fun `payload 为 null 时也返回 4 张空态卡不崩溃`() {
        val cards = ChsiExtractLogic.extractCards(null)
        assertEquals(4, cards.size)
        assertTrue(cards.all { it.status == ChsiCardStatus.EMPTY })
        assertEquals(
            listOf("学籍信息", "学历信息", "学位信息", "在线验证报告"),
            cards.map { it.kind.title },
        )
    }

    @Test
    fun `有报告链接时在线验证报告卡不算空态`() {
        val payload = ChsiPagePayload(
            text = "学信档案",
            links = listOf(ChsiLink(text = "在线验证报告", href = "https://my.chsi.com.cn/archive/bab/index.jsp")),
        )
        val card = ChsiExtractLogic.extractCards(payload)
            .first { it.kind == ChsiDataCardKind.VERIFY_REPORT }
        assertEquals(ChsiCardStatus.READY, card.status)
        assertTrue(card.lines.first().contains("在线验证报告"))
        // 其它卡仍为空态
        val enrollment = ChsiExtractLogic.extractCards(payload)
            .first { it.kind == ChsiDataCardKind.ENROLLMENT }
        assertEquals(ChsiCardStatus.EMPTY, enrollment.status)
    }

    @Test
    fun `有数据时单卡 READY 且最多 3 行`() {
        val cards = ChsiExtractLogic.extractCards(syntheticPayload())
        assertEquals(4, cards.size)
        cards.forEach { card ->
            assertTrue("${card.kind} 最多 3 行", card.lines.size <= ChsiExtractLogic.MAX_CARD_LINES)
            assertTrue("${card.kind} 行不为空", card.lines.isNotEmpty())
        }
        assertTrue(cards.any { it.status == ChsiCardStatus.READY })
    }

    // ---- URL / 登录态 -------------------------------------------------------

    @Test
    fun `仅允许 http(s) 导航`() {
        assertTrue(ChsiExtractLogic.isAllowedNavigation("https://my.chsi.com.cn/archive/wap/index.jsp"))
        assertTrue(ChsiExtractLogic.isAllowedNavigation("http://my.chsi.com.cn/"))
        assertFalse(ChsiExtractLogic.isAllowedNavigation("file:///sdcard/a.html"))
        assertFalse(ChsiExtractLogic.isAllowedNavigation("javascript:alert(1)"))
        assertFalse(ChsiExtractLogic.isAllowedNavigation("intent://x"))
        assertFalse(ChsiExtractLogic.isAllowedNavigation(null))
    }

    @Test
    fun `学信网域名与登录态判定`() {
        assertTrue(ChsiExtractLogic.isChsiHost("https://my.chsi.com.cn/archive/index.jsp"))
        assertTrue(ChsiExtractLogic.isChsiHost("https://account.chsi.com.cn/passport/login"))
        assertTrue(ChsiExtractLogic.isChsiHost("https://yz.chsi.com.cn/"))
        assertFalse(ChsiExtractLogic.isChsiHost("https://example.com/"))
        assertFalse(ChsiExtractLogic.isChsiHost("https://chsi.com.cn.evil.com/"))
        assertFalse(ChsiExtractLogic.isChsiHost(null))

        assertTrue(ChsiExtractLogic.isLoginUrl("https://account.chsi.com.cn/passport/login"))
        assertFalse(ChsiExtractLogic.isLoginUrl("https://my.chsi.com.cn/archive/index.jsp"))

        assertTrue(ChsiExtractLogic.isArchiveUrl("https://my.chsi.com.cn/archive/wap/index.jsp"))
        assertFalse(ChsiExtractLogic.isArchiveUrl("https://www.chsi.com.cn/"))
        assertTrue(ChsiExtractLogic.isLoggedInUrl("https://my.chsi.com.cn/archive/wap/index.jsp"))
        assertFalse(ChsiExtractLogic.isLoggedInUrl("https://account.chsi.com.cn/passport/login"))
    }

    // ---- 更新时间文案 -------------------------------------------------------

    @Test
    fun `更新时间文案为 HH 冒号 mm`() {
        val text = ChsiExtractLogic.updatedTimeText(1763000000000L)
        assertTrue(Regex("^\\d{2}:\\d{2}$").matches(text))
    }

    // ---- 合成夹具 -----------------------------------------------------------

    /** 合成学信档案页面文本（虚构数据，非真实个人数据）。 */
    private fun syntheticPayload(): ChsiPagePayload = ChsiPagePayload(
        url = "https://my.chsi.com.cn/archive/wap/index.jsp",
        title = "学信档案",
        text = """
            学信档案
            学籍信息
            姓名 张三
            学校 某某大学
            专业 计算机科学与技术
            学籍状态 在籍（注册学籍）
            入学时间 2026-09-01
            学历信息
            院校 某某大学
            专业 计算机科学与技术
            学历 本科
            毕结业结论 毕业
            学位信息
            学位 工学学士
            专业 计算机科学与技术
            授予时间 2030-06-30
            在线验证报告
            报告编号 ABC123456
            有效期至 2030-12-31
        """.trimIndent(),
        links = emptyList(),
    )

    /** 含（合成的）身份证/手机号的页面文本，验证卡片行掩码。 */
    private fun ChsiPayloadWithSensitiveData(): ChsiPagePayload = ChsiPagePayload(
        text = """
            学籍信息
            身份证号 350123200601011234
            手机号 13800138000
        """.trimIndent(),
    )

    // ---- 真实登录态判定（2026-11-15 修复：档案域未登录营销页误判）-------------

    @Test
    fun `未登录营销首页不判定为已登录`() {
        val marketing = ChsiPagePayload(
            url = "https://my.chsi.com.cn/archive/wap/index.jsp",
            title = "学信档案",
            text = """
                免费查看学籍、学历、学位信息
                实名注册后即可免费查看本人的高等教育学籍、学历、学位、考研等信息。
                登录 注册学信网账号
            """.trimIndent(),
            links = listOf(
                ChsiLink(text = "登录", href = "https://account.chsi.com.cn/passport/login"),
                ChsiLink(text = "注册学信网账号", href = "https://account.chsi.com.cn/passport/register"),
            ),
        )
        assertFalse(ChsiExtractLogic.isLoggedInPayload(marketing))
    }

    @Test
    fun `已登录档案页判定为已登录`() {
        val archive = ChsiPagePayload(
            url = "https://my.chsi.com.cn/archive/wap/index.jsp",
            title = "学信档案",
            text = """
                学信档案
                学籍信息 姓名 张三
                个人中心
                退出登录
            """.trimIndent(),
        )
        assertTrue(ChsiExtractLogic.isLoggedInPayload(archive))
    }

    @Test
    fun `空页面不判定为已登录`() {
        assertFalse(ChsiExtractLogic.isLoggedInPayload(null))
        assertFalse(ChsiExtractLogic.isLoggedInPayload(ChsiPagePayload()))
    }
}
