package com.furina.classtools.features.chsi

import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 学信网页面提取纯逻辑（规格 `_audit/chsi_entry_20261115/CHSI_ENTRY_SPEC.md` §3）。
 *
 * **本文件不依赖 Compose / Android，可直接 JVM 单测**：
 * - 注入 JS 生成（返回 `{url,title,text≤6000,links}`，只读页面，不发请求/不碰 cookie）；
 * - 提取结果 JSON 解析（WebView 回调的 JSON 字符串双层解码 + 宽容字段解析）；
 * - 按标签就近取 1–3 行（学籍/学历/学位/在线验证报告），提取不到 → 空态兜底；
 * - 展示前掩码 18 位身份证 / 11 位手机号，不显示完整证件号；
 * - 官方 URL / 10 工具常量 / UA 切换常量的判定辅助。
 *
 * 红线：这里只做**内存文本处理**，不接触学信网账号密码、不保存任何提取结果、不发网络请求。
 */
object ChsiExtractLogic {

    /** 注入脚本最多返回的页面文本长度（规格 §3）。 */
    const val MAX_TEXT_LENGTH: Int = 6000

    /** 每张数据卡最多展示的行数（规格：就近取 1–3 行）。 */
    const val MAX_CARD_LINES: Int = 3

    /** 单行展示上限（避免超长行撑爆卡片；超出省略由 UI 处理）。 */
    const val MAX_LINE_LENGTH: Int = 80

    /** 官方页面链接列表上限（JS 侧最多收集 200 个，Kotlin 侧不再扩展）。 */
    const val MAX_LINKS: Int = 200

    /** `onPageFinished` 后延迟注入（等待 wap 页面渲染完成）。 */
    const val EXTRACT_DELAY_MS: Long = 400

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // ---- 注入 JS ------------------------------------------------------------

    /**
     * 生成提取脚本：返回 `JSON.stringify({url,title,text,links})`。
     *
     * - `text = document.body.innerText` 截断到 [MAX_TEXT_LENGTH]；
     * - `links` 只收 `chsi.com.cn` 域、文本 ≤20 字的 `<a>`（最多 [MAX_LINKS] 个）；
     * - **只读**：不发请求、不读写 cookie/localStorage、不打印页面内容。
     */
    fun buildExtractScript(): String = """
        (function () {
          try {
            var links = [];
            var anchors = document.querySelectorAll('a[href]');
            var limit = Math.min(anchors.length, $MAX_LINKS);
            for (var i = 0; i < limit; i++) {
              var a = anchors[i];
              var t = (a.innerText || a.textContent || '').trim();
              if (!t || t.length > 20) { continue; }
              var h = a.href || '';
              if (h.indexOf('chsi.com.cn') < 0) { continue; }
              links.push({ t: t, h: h });
            }
            var text = (document.body && document.body.innerText) ? document.body.innerText : '';
            if (text.length > $MAX_TEXT_LENGTH) { text = text.substring(0, $MAX_TEXT_LENGTH); }
            return JSON.stringify({
              url: String(location.href || ''),
              title: String(document.title || ''),
              text: text,
              links: links
            });
          } catch (e) {
            return JSON.stringify({ url: '', title: '', text: '', links: [] });
          }
        })()
    """.trimIndent()

    // ---- WebView 回调解析 ---------------------------------------------------

    /**
     * 解码 `WebView.evaluateJavascript` 的回调值：
     * 页面脚本返回的是 JSON 字符串，WebView 会再包一层 JSON 字符串字面量（`"{\"url\":...}"`），
     * 这里先剥壳/反转义得到原始 JSON 文本；`null`/空 → null。
     */
    fun decodeWebViewResult(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "null") return null
        if (trimmed.length < 2 || !trimmed.startsWith('"') || !trimmed.endsWith('"')) {
            return trimmed
        }
        val body = trimmed.substring(1, trimmed.length - 1)
        val sb = StringBuilder(body.length)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c != '\\' || i == body.length - 1) {
                sb.append(c)
                i += 1
                continue
            }
            i += 1
            when (val esc = body[i]) {
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                'b' -> sb.append('\b')
                'f' -> sb.append('\u000C')
                '"' -> sb.append('"')
                '\'' -> sb.append('\'')
                '\\' -> sb.append('\\')
                '/' -> sb.append('/')
                'u' -> {
                    val hex = body.substring(i + 1, minOf(i + 5, body.length))
                    val code = hex.toIntOrNull(16)
                    if (code != null && hex.length == 4) {
                        sb.append(code.toChar())
                        i += 4
                    } else {
                        sb.append(esc)
                    }
                }
                else -> sb.append(esc)
            }
            i += 1
        }
        return sb.toString()
    }

    /** 解析提取 JSON 文本为 [ChsiPagePayload]；非法/空 → null（页面层按空态降级）。 */
    fun parsePayload(jsonText: String?): ChsiPagePayload? {
        val text = jsonText?.trim().orEmpty()
        if (text.isEmpty() || text == "null") return null
        val root = runCatching { jsonConfig.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return null
        val links = (root["links"] as? JsonArray).orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.let { obj ->
                ChsiLink(text = obj.str("t"), href = obj.str("h"))
            }
        }
        return ChsiPagePayload(
            url = root.str("url"),
            title = root.str("title"),
            text = root.str("text").take(MAX_TEXT_LENGTH),
            links = links,
        )
    }

    /** WebView 回调（原始字符串）→ 页面数据；任何一步失败返回 null。 */
    fun parseWebViewResult(raw: String?): ChsiPagePayload? =
        parsePayload(decodeWebViewResult(raw))

    // ---- 数据卡提取（标签就近取 1–3 行）------------------------------------

    /** 各卡片的关键标签（规格 §3：学籍信息/学历信息/学位信息/在线验证报告等）。 */
    private val CARD_LABELS: Map<ChsiDataCardKind, List<String>> = mapOf(
        ChsiDataCardKind.ENROLLMENT to listOf(
            "学籍信息", "学籍状态", "学籍", "学校", "院校", "学院", "专业", "院系",
            "培养层次", "学习形式", "入学时间", "学号",
        ),
        ChsiDataCardKind.EDUCATION to listOf(
            "学历信息", "学历", "毕结业", "毕结业结论", "毕业", "结业", "肄业",
            "毕业时间", "院校", "学校", "专业",
        ),
        ChsiDataCardKind.DEGREE to listOf(
            "学位信息", "学位", "授予", "学位授予", "授予时间", "专业",
        ),
        ChsiDataCardKind.VERIFY_REPORT to listOf(
            "在线验证报告", "验证报告", "报告编号", "报告", "验证", "有效期",
        ),
    )

    /**
     * 提取某张数据卡的摘要行：
     * 优先从页面中的**分类标题行**（如「学籍信息/学历信息/学位信息/在线验证报告」）开始，
     * 取该行 + 后续最多 2 行（共 ≤[MAX_CARD_LINES] 行）；找不到标题时退化为按标签全页扫描。
     * 行内容先做敏感掩码；提取不到返回空列表（页面层显示空态兜底）。
     */
    fun extractCardLines(payload: ChsiPagePayload?, kind: ChsiDataCardKind): List<String> {
        if (payload == null) return emptyList()
        val lines = payload.text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { maskSensitive(it).take(MAX_LINE_LENGTH) }
        if (lines.isEmpty()) return emptyList()
        val labels = CARD_LABELS[kind].orEmpty()
        if (labels.isEmpty()) return emptyList()

        val result = LinkedHashSet<String>()
        // 优先从分类标题（= 卡片标题）所在行开始，避免把页面上方其它分类的通用标签行先抽走。
        val headerIndex = lines.indexOfFirst { it.contains(kind.title) }
        val scanStart = if (headerIndex >= 0) headerIndex else 0
        for (index in scanStart until lines.size) {
            if (result.size >= MAX_CARD_LINES) break
            val line = lines[index]
            if (labels.none { line.contains(it) }) continue
            result.add(line)
            var cursor = index + 1
            while (cursor < lines.size && result.size < MAX_CARD_LINES) {
                val next = lines[cursor]
                // 命中下一个标签/段落 → 不再往后吞行
                if (labels.any { next.contains(it) }) break
                if (next.length < 2) break
                result.add(next)
                cursor += 1
            }
        }
        // 在线验证报告额外兜底：页面上有报告相关官方链接 → 视为「有报告入口」。
        if (kind == ChsiDataCardKind.VERIFY_REPORT && result.isEmpty()) {
            val reportLinks = payload.links.count { link ->
                link.text.contains("验证报告") || link.href.contains("/archive/bab")
            }
            if (reportLinks > 0) {
                result.add("找到 $reportLinks 个在线验证报告入口")
            }
        }
        return result.toList()
    }

    /**
     * 提取结果 → 4 张数据卡（永远返回 4 张；提取不到的卡为 [ChsiCardStatus.EMPTY] 空态）。
     */
    fun extractCards(payload: ChsiPagePayload?): List<ChsiDataCardUi> = ChsiCatalog.dataCards.map { kind ->
        val lines = extractCardLines(payload, kind)
        if (lines.isEmpty()) {
            ChsiDataCardUi(
                kind = kind,
                status = ChsiCardStatus.EMPTY,
                lines = listOf(ChsiTexts.MSG_EMPTY_FALLBACK),
                targetUrl = ChsiCatalog.dataCardUrl(kind),
            )
        } else {
            ChsiDataCardUi(
                kind = kind,
                status = ChsiCardStatus.READY,
                lines = lines.take(MAX_CARD_LINES),
                targetUrl = ChsiCatalog.dataCardUrl(kind),
            )
        }
    }

    // ---- 敏感信息掩码 -------------------------------------------------------

    /** 18 位身份证：保留前 6 位 + 后 4 位，中间 8 位掩码（不显示完整证件号）。 */
    private val ID_CARD_REGEX = Regex("""(?<!\d)(\d{6})(\d{8})(\d{3}[\dXx])(?!\d)""")

    /** 11 位手机号：保留前 3 位 + 后 4 位，中间 4 位掩码。 */
    private val PHONE_REGEX = Regex("""(?<!\d)(1[3-9]\d)(\d{4})(\d{4})(?!\d)""")

    /** 展示前统一掩码（身份证 + 手机号）；其它数字（如学分/金额）保持不变。 */
    fun maskSensitive(text: String): String = maskPhones(maskIdCards(text))

    /** 掩码 18 位身份证号。 */
    fun maskIdCards(text: String): String = text.replace(ID_CARD_REGEX) { match ->
        match.groupValues[1] + "********" + match.groupValues[3]
    }

    /** 掩码 11 位手机号。 */
    fun maskPhones(text: String): String = text.replace(PHONE_REGEX) { match ->
        match.groupValues[1] + "****" + match.groupValues[3]
    }

    // ---- URL / 登录态判定 ---------------------------------------------------

    /** 取 URL scheme（小写）；非法 URL 返回 null。 */
    fun schemeOf(url: String?): String? = runCatching {
        url?.trim()?.takeIf { it.isNotEmpty() }?.let { URI(it).scheme?.lowercase() }
    }.getOrNull()

    /** 取 URL host（小写）；非法 URL 返回 null。 */
    fun hostOf(url: String?): String? = runCatching {
        url?.trim()?.takeIf { it.isNotEmpty() }?.let { URI(it).host?.lowercase() }
    }.getOrNull()

    /** 只允许 http(s)（规格 §3：WebView 仅允许 http(s) 导航）。 */
    fun isHttpUrl(url: String?): Boolean = schemeOf(url) == "http" || schemeOf(url) == "https"

    /** WebView 是否允许该 URL 在内部加载（仅 http(s)；非 http(s) 一律阻止）。 */
    fun isAllowedNavigation(url: String?): Boolean = isHttpUrl(url)

    /** 是否学信网域名（`chsi.com.cn` 根域或其子域）。 */
    fun isChsiHost(url: String?): Boolean {
        val host = hostOf(url)?.takeIf { it.isNotEmpty() } ?: return false
        return host == "chsi.com.cn" || host.endsWith(".chsi.com.cn")
    }

    /** 是否学信网账号登录页（account.chsi.com.cn）= 未登录。 */
    fun isLoginUrl(url: String?): Boolean = hostOf(url) == "account.chsi.com.cn"

    /** 是否学信档案域（my.chsi.com.cn，可访问档案页 = 已登录）。 */
    fun isArchiveUrl(url: String?): Boolean = hostOf(url) == "my.chsi.com.cn"

    /** 当前 WebView URL 是否表示「已登录学信网」（回到档案域即已登录）。 */
    fun isLoggedInUrl(url: String?): Boolean = isArchiveUrl(url)

    /**
     * 页面内容是否真的处于「已登录」状态。
     *
     * 为什么不能只看 URL：my.chsi.com.cn 档案域未登录时也会正常返回「登录 / 注册学信网账号」的
     * 营销首页，旧实现把任何档案域 URL 当已登录，会在未登录时误显示数据卡。
     * 这里用页面文本/链接里的强信号判断：出现「退出 / 个人中心」等已登录标记 → 已登录；
     * 出现「注册学信网账号 / 立即登录」等登录引导 → 未登录；无信号时按未登录处理（宁保守）。
     */
    fun isLoggedInPayload(payload: ChsiPagePayload?): Boolean {
        val text = payload?.text.orEmpty()
        val linkTexts = payload?.links.orEmpty().map { it.text }
        val haystack = text + "\n" + linkTexts.joinToString("\n")
        if (haystack.isBlank()) return false
        if (LOGGED_OUT_MARKERS.any { haystack.contains(it) }) return false
        return LOGGED_IN_MARKERS.any { haystack.contains(it) }
    }

    /** 未登录页面的强信号（官方营销首页底部「登录 注册学信网账号」）。 */
    private val LOGGED_OUT_MARKERS: List<String> = listOf(
        "注册学信网账号",
        "登录学信档案",
        "立即登录",
        "账号登录",
    )

    /** 已登录页面的强信号（档案页导航/个人中心入口）。 */
    private val LOGGED_IN_MARKERS: List<String> = listOf(
        "退出登录",
        "退出",
        "个人中心",
    )

    /** 登录后宽松判定所要求的最少页面文本长度（过滤空白/错误页）。 */
    const val MIN_LOGGED_IN_TEXT_LENGTH: Int = 20

    /**
     * 页面是否包含「未登录营销」强信号（注册学信网账号 / 立即登录 / 账号登录 …）。
     * 供登录完成后的宽松判定与单测复用。
     */
    fun hasLoggedOutMarkers(payload: ChsiPagePayload?): Boolean {
        val haystack = haystackOf(payload)
        if (haystack.isBlank()) return false
        return LOGGED_OUT_MARKERS.any { haystack.contains(it) }
    }

    /**
     * **LOGIN 模式登录完成后的宽松判定**（2026-11-15 用户反馈：登录后一直停在账号信息界面）。
     *
     * 真实登录后的 my.chsi 档案页并不一定包含「退出登录 / 个人中心」等强信号（页面改版/移动端精简版），
     * 旧实现 [isLoggedInPayload] 因此判定失败 → 一直不切卡片。
     * 规则（仅在用户主动登录流程里使用，后台自动恢复仍用强信号）：
     * 1. 当前 URL 已回到 my.chsi 档案域；
     * 2. 提取到的页面数据非空且文本长度 ≥ [MIN_LOGGED_IN_TEXT_LENGTH]；
     * 3. 不含「注册学信网账号 / 登录学信档案 / 立即登录 / 账号登录」等未登录营销标记。
     * 满足即视为登录成功（数据卡后续再用页面内容尽力提取，取不到走空态兜底）。
     */
    fun isLoginSuccessAfterLogin(payload: ChsiPagePayload?, pageUrl: String?): Boolean {
        if (!isLoggedInUrl(pageUrl)) return false
        if (payload == null) return false
        val text = payload.text.trim()
        if (text.length < MIN_LOGGED_IN_TEXT_LENGTH) return false
        if (hasLoggedOutMarkers(payload)) return false
        return true
    }

    private fun haystackOf(payload: ChsiPagePayload?): String {
        if (payload == null) return ""
        val linkTexts = payload.links.map { it.text }
        return payload.text + "\n" + linkTexts.joinToString("\n")
    }

    /** 当前本地时间 `HH:mm`（状态条「数据更新 HH:mm」，JVM 可测）。 */
    fun updatedTimeText(millis: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

    // ---- JSON 小工具 -------------------------------------------------------

    private fun JsonObject.str(key: String): String =
        (get(key) as? JsonPrimitive)?.contentOrNull ?: ""
}
