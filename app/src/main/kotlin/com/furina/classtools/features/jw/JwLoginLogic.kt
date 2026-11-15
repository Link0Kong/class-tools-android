package com.furina.classtools.features.jw

import java.net.URI
import java.util.Base64

/**
 * 教务「直进 + 自动登录」纯逻辑（契约 `web/_audit/jw_entry_20261113/CONTRACT.md` §5.1/§5.3）。
 *
 * **本文件不依赖 Compose / Android，可直接 JVM 单测**（无 Robolectric）：
 * - URL / 跳转 allowlist（页类型判断、仅 http(s)、仅校内域）；
 * - 注入 JS 生成与字符串转义（账号/密码只作为 JS 字符串字面量，不落日志、不拼接命令）；
 * - 密码校验、账号掩码、登录结果解析与状态文案；
 * - 本机凭据的 Base64 编解码与「当前身份 sid 是否匹配」判断。
 *
 * 安全红线（契约 §5.0/§5.3）：
 * - 密码只允许本机加密保存（[com.furina.classtools.data.jw.JwCredentialStore]），绝不上传/同步/打日志；
 * - 本文件只做纯字符串处理，不接触账号密码存储。
 */
object JwLoginLogic {

    // ---- 契约常量 -----------------------------------------------------------

    /** 教务系统入口（学校只有 HTTP；统一身份认证随后跳 https authserver）。 */
    const val ENTRY_URL: String = "http://210.34.74.77/sso.jsp"

    /** 学校统一身份认证域名。 */
    const val AUTH_HOST: String = "authserver.fjtcm.edu.cn"

    /** 教务系统主机（登录成功后回到该域即视为已进入）。 */
    const val JW_HOST: String = "210.34.74.77"

    /** authserver 账号密码登录页路径前缀（契约 §5.1）。 */
    const val AUTH_LOGIN_PATH: String = "/authserver/login"

    /** sso.jsp 跳板路径（该 URL 出现在 210.34.74.77 上时还不算登录成功）。 */
    const val ENTRY_PATH: String = "/sso.jsp"

    /** 学校域名根（含子域；教务/统一认证页面都在此范围内）。 */
    const val SCHOOL_DOMAIN: String = "fjtcm.edu.cn"

    /** 页面加载完成后延迟注入（2026-11-16：300→700ms，放慢节奏避免 CAS 风控「服务器繁忙」）。 */
    const val INJECT_DELAY_MS: Long = 700

    /** 提交后复核延迟（契约 §5.3-4：800ms）。 */
    const val VERIFY_DELAY_MS: Long = 800

    /** 同一页面会话最多自动尝试注入次数（2026-11-16：2→1，失败即转人工，不触发风控循环）。 */
    const val MAX_INJECT_ATTEMPTS: Int = 1

    /** 密码最大长度（防御异常输入；不裁剪首尾空格）。 */
    const val MAX_PASSWORD_LENGTH: Int = 128

    // ---- 状态 / 文案（三端口径，安卓直进版）---------------------------------

    /** 顶栏标题。 */
    const val PAGE_TITLE: String = "教务"

    /** 自动登录进行中的状态文案。 */
    const val MSG_AUTO_LOGIN: String = "正在自动登录…"

    /** 已进入教务系统的状态文案。 */
    const val MSG_ENTERED: String = "已进入教务系统"

    /** 自动登录失败（验证码/表单变化）时的回退提示（契约 §5.3-4）。 */
    const val MSG_NEED_MANUAL: String = "自动登录失败，可能需验证码，请手动完成"

    /** 网页主文档加载失败。 */
    const val MSG_LOAD_FAILED: String = "网页加载失败，请检查网络后重试"

    /** 一次性密码输入弹层标题。 */
    const val PROMPT_TITLE: String = "登录教务"

    /** 主按钮：保存并登录。 */
    const val BTN_SAVE_LOGIN: String = "保存并登录"

    /** 次按钮：仅本次打开、不保存密码。 */
    const val BTN_OPEN_ONLY: String = "仅打开不保存"

    /** 取消按钮。 */
    const val BTN_CANCEL: String = "取消"

    /** 顶栏动作：清除登录（密码 + Cookie）。 */
    const val BTN_CLEAR: String = "清除登录"

    /** 清除登录确认文案。 */
    const val CLEAR_CONFIRM_TITLE: String = "清除登录"
    const val CLEAR_CONFIRM_TEXT: String = "将清除本机保存的教务密码与网页登录状态（Cookie / 缓存），下次需重新输入。"

    /** 已清除完成 toast。 */
    const val MSG_CLEARED: String = "已清除本机登录信息"

    /** 保存失败提示。 */
    const val MSG_SAVE_FAILED: String = "密码保存失败，本次仍可登录"

    /** 密码为空 / 超长校验文案。 */
    const val MSG_PASSWORD_REQUIRED: String = "请输入密码"
    const val MSG_PASSWORD_TOO_LONG: String = "密码过长"

    /** 凭据保存提示（明确「仅本机」）。 */
    const val HINT_PASSWORD_LOCAL: String = "密码仅用系统加密保存在本机，可随时清除；不上传、不同步。"

    // ---- 网络探测 / WebVPN（校外先登录学校 WebVPN）---------------------------

    /** 进入页面时网络探测中。 */
    const val MSG_NETWORK_PROBING: String = "正在检测网络（校园网直连 / 学校 WebVPN）…"

    /** 自动探测到无法直连教务、已自动改用 WebVPN。 */
    const val MSG_DIRECT_UNAVAILABLE: String = "当前网络无法直连教务，已自动改用学校 WebVPN"

    /** 正在打开 WebVPN 登录页。 */
    const val MSG_WEBVPN_OPENING: String = "正在打开学校 WebVPN 登录页…"

    /** 正在自动填充 WebVPN 登录表单。 */
    const val MSG_WEBVPN_AUTO_LOGIN: String = "正在自动填充 WebVPN 登录…"

    /** WebVPN 自动登录失败（验证码/字段变化/服务端拒绝）→ 回退人工。 */
    const val MSG_WEBVPN_NEED_MANUAL: String = "WebVPN 自动登录未完成，请在页面手动登录"

    /** WebVPN 需要二次验证（短信 / TOTP）→ 回退人工。 */
    const val MSG_WEBVPN_TWO_STEP: String = "WebVPN 需要二次验证，请在页面手动完成"

    /** WebVPN 登录成功，正在自动进入教务。 */
    const val MSG_WEBVPN_LOGGED_IN: String = "WebVPN 已登录，正在进入教务…"

    /** 门户里没自动找到教务资源 → 提示手动点。 */
    const val MSG_WEBVPN_CLICK_MANUAL: String = "请在 WebVPN 页面点「本科教务」资源进入教务"

    /** 已通过 WebVPN 进入教务业务页。 */
    const val MSG_WEBVPN_ENTERED: String = "已通过 WebVPN 进入教务（官方页面）"

    /** 校外 WebVPN 模式下卡片数据不可用（直连 JSON 接口需要校园网）。 */
    const val MSG_WEBVPN_CARDS_UNAVAILABLE: String =
        "校外 WebVPN 模式：卡片数据需校园网直连获取，可打开官方页面查看"

    /** 手动入口按钮：强制直连。 */
    const val BTN_DIRECT: String = "直接打开教务"

    /** 手动入口按钮：强制走 WebVPN。 */
    const val BTN_WEBVPN: String = "走 WebVPN"

    // ---- URL / 跳转 allowlist ----------------------------------------------

    /** 页面类型（用于决定注入与状态）。 */
    enum class PageKind {
        /** authserver 账号密码登录页（可自动填充）；含 WebVPN 包装后的 authserver 登录页。 */
        AUTH_LOGIN,

        /** 210.34.74.77 上的 sso.jsp 跳板（等待跳统一认证）。 */
        JW_ENTRY,

        /** 已回到 210.34.74.77 的其它页面 = 登录后进入教务系统。 */
        JW_ENTERED,

        /** 学校 WebVPN 登录页（校外入口，可自动填充账号密码）。 */
        WEBVPN_LOGIN,

        /** WebVPN 登录后的门户页（资源列表；自动找「本科教务」资源）。 */
        WEBVPN_PORTAL,

        /** 已通过 WebVPN 进入包装后的教务业务页。 */
        WEBVPN_ENTERED,

        /** 其它页面（登录过程中的中间页/外部页）。 */
        OTHER,
    }

    /** 取 URL 的 scheme（小写）；非法 URL 返回 null。 */
    fun schemeOf(url: String?): String? = runCatching {
        url?.trim()?.takeIf { it.isNotEmpty() }?.let { URI(it).scheme?.lowercase() }
    }.getOrNull()

    /** 取 URL 的 host（小写）；非法 URL 返回 null。 */
    fun hostOf(url: String?): String? = runCatching {
        url?.trim()?.takeIf { it.isNotEmpty() }?.let { URI(it).host?.lowercase() }
    }.getOrNull()

    /** 取 URL 的 path；非法 URL 返回 null。 */
    fun pathOf(url: String?): String? = runCatching {
        url?.trim()?.takeIf { it.isNotEmpty() }?.let { URI(it).path ?: "" }
    }.getOrNull()

    /** 取 URL 内嵌的 userinfo（`http://user:pass@host/`）；没有则 null。 */
    fun userInfoOf(url: String?): String? = runCatching {
        url?.trim()?.takeIf { it.isNotEmpty() }?.let { URI(it).userInfo }
    }.getOrNull()

    /** 只允许 http / https（契约 §5.3-3：WebView 仅允许 http(s)）。 */
    fun isHttpUrl(url: String?): Boolean = schemeOf(url) == "http" || schemeOf(url) == "https"

    /** 是否校内域名（[SCHOOL_DOMAIN] 根域或其子域，以及教务系统 IP 主机）。 */
    fun isSchoolHost(host: String?): Boolean {
        val h = host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
        if (h == JW_HOST) return true
        return h == SCHOOL_DOMAIN || h.endsWith(".$SCHOOL_DOMAIN")
    }

    /**
     * 内置 WebView 是否允许直接加载该 URL（allowlist）：
     * 仅 http(s) 且属于校内域（教务 IP / 统一认证 / *.fjtcm.edu.cn），且 URL 不得内嵌凭据。
     */
    fun isAllowedNavigation(url: String?): Boolean =
        isHttpUrl(url) && isSchoolHost(hostOf(url)) && userInfoOf(url) == null

    /** 是否 authserver 的账号密码登录页（直连或 WebVPN 包装后都算）。 */
    fun isAuthServerLoginUrl(url: String?): Boolean {
        if (!isHttpUrl(url)) return false
        if (hostOf(url) == AUTH_HOST && pathOf(url)?.startsWith(AUTH_LOGIN_PATH) == true) return true
        // 校外经由 WebVPN 包装后 host 变成 webvpn 域，但路径仍保留 /authserver/login：
        // 页面 DOM 与直连一致，自动填充脚本可直接复用。
        return JwWebVpn.isWrappedAuthServerLoginUrl(url)
    }

    /** 是否已回到教务系统（210.34.74.77 且不是 sso.jsp 跳板）= 视为登录成功。 */
    fun isJwEntered(url: String?): Boolean {
        if (!isHttpUrl(url)) return false
        if (hostOf(url) != JW_HOST) return false
        return pathOf(url) != ENTRY_PATH
    }

    /** 页面分类（注入与状态机的输入）。 */
    fun pageKind(url: String?): PageKind {
        if (!isHttpUrl(url)) return PageKind.OTHER
        return when {
            isAuthServerLoginUrl(url) -> PageKind.AUTH_LOGIN
            hostOf(url) == JW_HOST && pathOf(url) == ENTRY_PATH -> PageKind.JW_ENTRY
            isJwEntered(url) -> PageKind.JW_ENTERED
            // 校外 WebVPN 链路：登录页 → 门户页 → 包装后的教务业务页。
            JwWebVpn.isWebVpnLoginUrl(url) -> PageKind.WEBVPN_LOGIN
            JwWebVpn.isWebVpnPortalUrl(url) -> PageKind.WEBVPN_PORTAL
            JwWebVpn.isWrappedJwEnteredUrl(url) -> PageKind.WEBVPN_ENTERED
            else -> PageKind.OTHER
        }
    }

    // ---- 密码校验 / 掩码 ----------------------------------------------------

    /** 校验密码（不裁剪首尾空格；空/超长返回错误文案，合法返回 null）。 */
    fun validatePassword(raw: String?): String? = when {
        raw.isNullOrEmpty() -> MSG_PASSWORD_REQUIRED
        raw.length > MAX_PASSWORD_LENGTH -> MSG_PASSWORD_TOO_LONG
        else -> null
    }

    /** 账号掩码展示：保留末 4 位，其余用圆点（短账号全掩码，避免整屏暴露学号）。 */
    fun maskAccount(account: String?): String {
        val value = account?.trim().orEmpty()
        if (value.isEmpty()) return ""
        val keep = 4
        if (value.length <= keep) return "•".repeat(value.length)
        return "•".repeat(value.length - keep) + value.takeLast(keep)
    }

    /** 当前身份 sid 是否与本机已存凭据匹配（不一致必须重新输入）。 */
    fun isCredentialCompatible(storedSid: String?, currentSid: String?): Boolean =
        !storedSid.isNullOrBlank() && storedSid == currentSid

    // ---- 本机凭据 Base64 编解码（java.util.Base64，JVM 可测）----------------

    fun encodeBase64(bytes: ByteArray): String? = runCatching {
        Base64.getEncoder().encodeToString(bytes)
    }.getOrNull()

    fun decodeBase64(text: String): ByteArray? = runCatching {
        Base64.getDecoder().decode(text)
    }.getOrNull()

    // ---- JS 注入 ------------------------------------------------------------

    /**
     * 把任意字符串编码成安全的 JS 双引号字符串字面量。
     *
     * 覆盖 `\ " '`、换行/制表、控制字符、U+2028/U+2029（JS 行终止符），并把
     * `< > &` 转成 `\uXXXX`，即使被嵌进 HTML/脚本上下文也不会提前闭合标签。
     */
    fun jsStringLiteral(raw: String): String = buildString(raw.length + 2) {
        append('"')
        raw.forEach { ch ->
            when {
                ch == '\\' -> append("\\\\")
                ch == '"' -> append("\\\"")
                ch == '\'' -> append("\\'")
                ch == '\n' -> append("\\n")
                ch == '\r' -> append("\\r")
                ch == '\t' -> append("\\t")
                ch == '\b' -> append("\\b")
                ch == '\u000C' -> append("\\f")
                ch == '<' -> append("\\u003c")
                ch == '>' -> append("\\u003e")
                ch == '&' -> append("\\u0026")
                ch == '\u2028' -> append("\\u2028")
                ch == '\u2029' -> append("\\u2029")
                ch.code < 0x20 || ch.code == 0x7F -> appendUnicodeEscape(this, ch.code)
                else -> append(ch)
            }
        }
        append('"')
    }

    private fun appendUnicodeEscape(sb: StringBuilder, code: Int) {
        sb.append("\\u")
        val hex = code.toString(16)
        repeat(4 - hex.length) { sb.append('0') }
        sb.append(hex)
    }

    /**
     * 生成「确保账号密码登录区可见 → 填账号/密码（派发 input/change）→ 点 #login_submit」的注入脚本。
     *
     * 只返回状态 token（`submitted` / `no-form` / `no-submit` / `error`），**不返回/不打印账号密码**；
     * 加密与提交由登录页自身的 `startLogin → checkForm`（`encryptPassword`）完成。
     */
    fun buildFillAndSubmitScript(account: String, password: String): String = """
        (function () {
          try {
            var u = document.getElementById('username');
            var p = document.getElementById('password');
            var box = document.getElementById('pwdLoginDiv');
            if (!u || !p) { return 'no-form'; }
            if (box && box.style && box.style.display === 'none') {
              box.style.setProperty('display', 'block', 'important');
            }
            u.value = ${jsStringLiteral(account)};
            u.dispatchEvent(new Event('input', { bubbles: true }));
            u.dispatchEvent(new Event('change', { bubbles: true }));
            p.value = ${jsStringLiteral(password)};
            p.dispatchEvent(new Event('input', { bubbles: true }));
            p.dispatchEvent(new Event('change', { bubbles: true }));
            var submit = document.getElementById('login_submit');
            if (!submit) { return 'no-submit'; }
            submit.click();
            return 'submitted';
          } catch (e) {
            return 'error';
          }
        })()
    """.trimIndent()

    /**
     * 提交后复核脚本：`error`（#showErrorTip 有文字）/ `captcha`（#captchaDiv 可见）/ `wait`。
     */
    fun buildResultCheckScript(): String = """
        (function () {
          try {
            var tip = document.getElementById('showErrorTip');
            var tipText = tip && tip.textContent ? String(tip.textContent).replace(/\s+/g, '') : '';
            var cap = document.getElementById('captchaDiv');
            var capVisible = false;
            if (cap) {
              var style = window.getComputedStyle ? window.getComputedStyle(cap) : null;
              capVisible = (cap.offsetWidth > 0 || cap.offsetHeight > 0) &&
                (!style || (style.display !== 'none' && style.visibility !== 'hidden'));
            }
            if (tipText) { return 'error'; }
            if (capVisible) { return 'captcha'; }
            return 'wait';
          } catch (e) {
            return 'wait';
          }
        })()
    """.trimIndent()

    /** 注入结果（页面脚本返回 token 的归一）。 */
    enum class LoginOutcome {
        SUBMITTED,
        NEED_MANUAL,
    }

    /** 解析 `WebView.evaluateJavascript` 的回调值（JSON 字符串 → Kotlin String；`null` → null）。 */
    fun parseJsStringResult(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "null") return null
        if (trimmed.length < 2 || !trimmed.startsWith('"') || !trimmed.endsWith('"')) return trimmed
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

    /** 注入结果 token → 归一结果。 */
    fun loginOutcomeOf(token: String?): LoginOutcome =
        if (token == "submitted") LoginOutcome.SUBMITTED else LoginOutcome.NEED_MANUAL

    /** 复核结果。 */
    enum class VerifyOutcome {
        ERROR,
        CAPTCHA,
        WAIT,
    }

    /** 复核脚本 token → 归一结果。 */
    fun verifyOutcomeOf(token: String?): VerifyOutcome = when (token) {
        "error" -> VerifyOutcome.ERROR
        "captcha" -> VerifyOutcome.CAPTCHA
        else -> VerifyOutcome.WAIT
    }

    // ---- 2026-11-16：CAS「服务器繁忙」风控规避 + 慢速自动填充 -----------------

    /**
     * 教务/统一认证 WebView 使用的微信 UA（2026-11-16 用户反馈：微信内登录正常、App 内提示
     * 「服务器繁忙」；对比请求特征后统一为微信 UA，降低 CAS 风控概率）。
     */
    const val WECHAT_UA: String =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP2A.240905.003; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/116.0.0.0 " +
            "Mobile Safari/537.36 MicroMessenger/8.0.49.2600(0x2800313D) " +
            "WeChat/arm64 Weixin NetType/WIFI Language/zh_CN ABI/arm64 " +
            "XWEB/1375 MMWEBSDK/20240409"

    /** 填写账号密码后、点击提交前的停顿（模拟人工操作，避免风控）。 */
    const val PRE_SUBMIT_DELAY_MS: Long = 300

    /** CAS/登录页返回「服务器繁忙」类文案的标记（出现即停止自动重试）。 */
    val SERVER_BUSY_MARKERS: List<String> = listOf(
        "服务器繁忙",
        "系统繁忙",
        "请求过于频繁",
        "访问过于频繁",
        "操作过于频繁",
        "请稍后再试",
    )

    /** 页面文本是否包含「服务器繁忙」类标记。 */
    fun isServerBusyText(text: String?): Boolean {
        val value = text?.replace(Regex("\\s+"), "").orEmpty()
        if (value.isEmpty()) return false
        return SERVER_BUSY_MARKERS.any { value.contains(it.replace(" ", "")) }
    }

    /** 繁忙检测脚本：页面含繁忙文案返回 `busy`，否则 `ok`（只读）。 */
    fun buildBusyCheckScript(): String = """
        (function () {
          try {
            var text = String((document.body && document.body.innerText) || '').replace(/\s+/g, '');
            var markers = ['服务器繁忙', '系统繁忙', '请求过于频繁', '访问过于频繁', '操作过于频繁', '请稍后再试'];
            for (var i = 0; i < markers.length; i++) {
              if (text.indexOf(markers[i]) >= 0) { return 'busy'; }
            }
            return 'ok';
          } catch (e) {
            return 'ok';
          }
        })()
    """.trimIndent()

    /** 繁忙检测结果。 */
    enum class BusyOutcome {
        BUSY,
        OK,
    }

    fun busyOutcomeOf(token: String?): BusyOutcome =
        if (token == "busy") BusyOutcome.BUSY else BusyOutcome.OK

    /** CAS 服务器繁忙时的提示（停止自动重试，改手动/浏览器/微信打开）。 */
    const val MSG_SERVER_BUSY: String =
        "登录服务器繁忙（可能触发风控），已停止自动重试：请手动登录，或点「浏览器」用微信/浏览器打开"

    /** 顶栏/手动入口：用系统浏览器打开当前登录页。 */
    const val BTN_OPEN_BROWSER: String = "浏览器"

    /**
     * 第一步注入：只**填写**账号密码并派发 input/change（不提交）。
     * 返回 `filled` / `no-form` / `error`；不返回/不打印账号密码。
     */
    fun buildFillScript(account: String, password: String): String = """
        (function () {
          try {
            var u = document.getElementById('username');
            var p = document.getElementById('password');
            var box = document.getElementById('pwdLoginDiv');
            if (!u || !p) { return 'no-form'; }
            if (box && box.style && box.style.display === 'none') {
              box.style.setProperty('display', 'block', 'important');
            }
            u.value = ${jsStringLiteral(account)};
            u.dispatchEvent(new Event('input', { bubbles: true }));
            u.dispatchEvent(new Event('change', { bubbles: true }));
            p.value = ${jsStringLiteral(password)};
            p.dispatchEvent(new Event('input', { bubbles: true }));
            p.dispatchEvent(new Event('change', { bubbles: true }));
            return 'filled';
          } catch (e) {
            return 'error';
          }
        })()
    """.trimIndent()

    /** 第二步注入（填写后 [PRE_SUBMIT_DELAY_MS] 再点击提交）：返回 `submitted` / `no-submit` / `error`。 */
    fun buildSubmitScript(): String = """
        (function () {
          try {
            var submit = document.getElementById('login_submit');
            if (!submit) { return 'no-submit'; }
            submit.click();
            return 'submitted';
          } catch (e) {
            return 'error';
          }
        })()
    """.trimIndent()

    /** 填写结果。 */
    enum class FillOutcome {
        FILLED,
        NEED_MANUAL,
    }

    fun fillOutcomeOf(token: String?): FillOutcome =
        if (token == "filled") FillOutcome.FILLED else FillOutcome.NEED_MANUAL
}
