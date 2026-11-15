package com.furina.classtools.data.todaycampus

import java.net.URI

/**
 * 「今日校园 / 融合门户」CAS 自动登录纯逻辑（无 Compose / Android 依赖，可直接 JVM 单测）。
 *
 * ## 链路（2026-11-15 只读核实，公开页面）
 * 门户登录入口 `https://e.fjtcm.edu.cn/login2` → 302 → 学校统一身份认证
 * `https://authserver.fjtcm.edu.cn/authserver/login?service=…`（Apereo CAS，与教务同一套页面）
 * → 自动填充学号 + 统一身份认证密码 → CAS 回跳门户 = 已进入。
 *
 * ## 安全红线
 * - 密码只作为 JS 字符串字面量写进**官方登录页**输入框，不打印、不落盘、不上传；
 * - 生成脚本只返回状态 token（`submitted` / `captcha` / `no-form` / `no-submit` / `error`），
 *   **不返回、不打印**账号密码；
 * - 验证码已出现或自动登录失败 → 一律回退人工（不绕过验证码、不循环重试）。
 */
object TodayCampusLoginLogic {

    // ---- 常量 ---------------------------------------------------------------

    /** 页面加载完成后延迟注入（等 CAS 页面自身脚本初始化完）。 */
    const val INJECT_DELAY_MS: Long = 300L

    /** 提交后复核延迟（判断错误提示 / 验证码）。 */
    const val VERIFY_DELAY_MS: Long = 800L

    /**
     * 同一会话自动尝试次数上限：**只 1 次**。
     * 密码过期/改版时第一次失败即回退人工，避免反复错误提交触发 CAS 风控/锁定。
     */
    const val MAX_INJECT_ATTEMPTS: Int = 1

    /** 密码最大长度（与 CAS 输入框 maxlength 一致；超长视为异常输入，不自动提交）。 */
    const val MAX_PASSWORD_LENGTH: Int = 32

    // ---- 文案 ---------------------------------------------------------------

    /** 正在打开门户。 */
    const val MSG_OPENING: String = "正在打开融合门户…"

    /** 自动登录进行中。 */
    const val MSG_AUTO_LOGIN: String = "正在自动登录…"

    /** 已进入门户（登录成功或已有会话）。 */
    const val MSG_ENTERED: String = "已进入融合门户"

    /** 自动登录失败（表单结构变化/服务端拒绝）→ 回退人工。 */
    const val MSG_NEED_MANUAL: String = "自动登录未完成，请在页面手动登录"

    /** 需要验证码 → 回退人工（不绕过验证码）。 */
    const val MSG_CAPTCHA: String = "需要验证码，请在页面手动完成登录"

    /** 本机没有可用密码 → 直接人工登录。 */
    const val MSG_NO_PASSWORD: String = "本机未保存密码，请在页面手动登录"

    /** 没拿到当前身份学号 → 直接人工登录。 */
    const val MSG_NO_ACCOUNT: String = "未获取到学号，请在页面手动登录"

    /** 主文档加载失败。 */
    const val MSG_LOAD_FAILED: String = "网页加载失败，请检查网络后重试"

    // ---- URL 解析 / allowlist ------------------------------------------------

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

    /** 取 URL 内嵌的 userinfo（`https://user:pass@host/`）；没有则 null。 */
    fun userInfoOf(url: String?): String? = runCatching {
        url?.trim()?.takeIf { it.isNotEmpty() }?.let { URI(it).userInfo }
    }.getOrNull()

    /** 只允许 http / https。 */
    fun isHttpUrl(url: String?): Boolean = schemeOf(url) == "http" || schemeOf(url) == "https"

    /** 是否校内域名（[TodayCampusCatalog.SCHOOL_DOMAIN] 根域或其子域）。 */
    fun isSchoolHost(host: String?): Boolean {
        val value = host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
        return value == TodayCampusCatalog.SCHOOL_DOMAIN ||
            value.endsWith(".${TodayCampusCatalog.SCHOOL_DOMAIN}")
    }

    /**
     * 内置 WebView 是否允许直接加载该 URL（allowlist）：
     * 仅 http(s) 且属于校内域（门户 `e.fjtcm.edu.cn` / 统一认证 `authserver.fjtcm.edu.cn` 等），
     * 且 URL 不得内嵌凭据。其它 http(s) 交系统浏览器、非 http(s) 一律阻止。
     */
    fun isAllowedNavigation(url: String?): Boolean =
        isHttpUrl(url) && isSchoolHost(hostOf(url)) && userInfoOf(url) == null

    /**
     * 是否 CAS 账号密码登录页（可自动填充）：
     * 校内域 + 路径以 `/authserver/login` 开头（`authserver.fjtcm.edu.cn` 为主；
     * 同服务器的 `e.fjtcm.edu.cn/authserver/login` 也按登录页处理）。
     */
    fun isCasLoginUrl(url: String?): Boolean {
        if (!isHttpUrl(url)) return false
        val host = hostOf(url) ?: return false
        if (!isSchoolHost(host)) return false
        return pathOf(url).orEmpty().startsWith(TodayCampusCatalog.CAS_LOGIN_PATH)
    }

    /** 是否融合门户页面（门户主机 + 非 CAS 登录页 = 登录成功后进入门户 / 门户首页）。 */
    fun isPortalUrl(url: String?): Boolean {
        if (!isHttpUrl(url)) return false
        if (hostOf(url) != TodayCampusCatalog.PORTAL_HOST) return false
        return !isCasLoginUrl(url)
    }

    /** 页面类型（决定是否触发自动填充与状态文案）。 */
    enum class PageKind {
        /** 统一身份认证 CAS 登录页（可自动填充）。 */
        AUTH_LOGIN,

        /** 融合门户页面（进入门户 = 登录成功 / 已有会话）。 */
        PORTAL,

        /** 其它页面（跳转中间页 / 外部页 / 非 http(s)）。 */
        OTHER,
    }

    /** 页面分类。 */
    fun pageKind(url: String?): PageKind {
        if (!isHttpUrl(url)) return PageKind.OTHER
        return when {
            isCasLoginUrl(url) -> PageKind.AUTH_LOGIN
            isPortalUrl(url) -> PageKind.PORTAL
            else -> PageKind.OTHER
        }
    }

    // ---- 账号 / 密码可用性 ---------------------------------------------------

    /** 自动填充可用性判断（不通过返回人工提示文案；可用返回 null）。 */
    fun validateAutomation(sid: String?, password: String?): String? = when {
        sid.isNullOrBlank() -> MSG_NO_ACCOUNT
        password.isNullOrEmpty() -> MSG_NO_PASSWORD
        password.length > MAX_PASSWORD_LENGTH -> MSG_NEED_MANUAL
        else -> null
    }

    // ---- JS 注入：字符串转义 -------------------------------------------------

    /**
     * 把任意字符串编码成安全的 JS 双引号字符串字面量。
     *
     * 覆盖 `\ " '`、换行/制表/控制字符、U+2028/U+2029（JS 行终止符），并把
     * `< > &` 转成 `\uXXXX`，即使嵌进 HTML/脚本上下文也不会提前闭合标签。
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

    // ---- JS 注入：填充并提交 -------------------------------------------------

    /**
     * 生成「显示账号密码登录区 → 填学号/密码（派发 input/change）→ 点登录」的注入脚本。
     *
     * 只返回状态 token，**不返回/不打印账号密码**；密码加密与提交由 CAS 登录页自身脚本完成。
     * 已验证码出现（上次失败 / 风控要求）时返回 `captcha` 且**不提交**，交回人工。
     */
    fun buildFillAndSubmitScript(account: String, password: String): String = """
        (function () {
          try {
            var box = document.getElementById('pwdLoginDiv');
            if (box && box.style) {
              box.style.setProperty('display', 'block', 'important');
            }
            // 账号密码登录区专属字段（页面可能同时存在手机动态码/FIDO 表单，用作用域查询避免填错表单）。
            var u = document.querySelector('#pwdLoginDiv #username') || document.getElementById('username');
            var p = document.querySelector('#pwdLoginDiv #password') || document.getElementById('password');
            if (!u || !p) { return 'no-form'; }
            // 学校 CAS 页面加载后会短暂给输入框加 readonly 防止浏览器自动填充，这里先解除。
            try {
              u.removeAttribute('readonly');
              u.removeAttribute('disabled');
              p.removeAttribute('readonly');
              p.removeAttribute('disabled');
            } catch (ignored) {}
            u.value = ${jsStringLiteral(account)};
            u.dispatchEvent(new Event('input', { bubbles: true }));
            u.dispatchEvent(new Event('change', { bubbles: true }));
            p.value = ${jsStringLiteral(password)};
            p.dispatchEvent(new Event('input', { bubbles: true }));
            p.dispatchEvent(new Event('change', { bubbles: true }));
            function visible(el) {
              if (!el) { return false; }
              var s = window.getComputedStyle ? window.getComputedStyle(el) : null;
              return (el.offsetWidth > 0 || el.offsetHeight > 0) &&
                (!s || (s.display !== 'none' && s.visibility !== 'hidden'));
            }
            // 图片验证码或滑块验证码（#sliderCaptchaDiv 由页面脚本动态注入）已出现 → 不提交，回退人工。
            var cap = document.getElementById('captchaDiv');
            var slider = document.getElementById('sliderCaptchaDiv');
            var sliderShown = !!slider && slider.children && slider.children.length > 0 && visible(slider);
            if (visible(cap) || sliderShown) { return 'captcha'; }
            var submit = document.querySelector('#pwdLoginDiv #login_submit') ||
              document.getElementById('login_submit');
            if (!submit) { return 'no-submit'; }
            submit.click();
            return 'submitted';
          } catch (e) {
            return 'error';
          }
        })()
    """.trimIndent()

    /** 填充脚本结果。 */
    enum class FillOutcome {
        /** 已填写并点击登录（等待页面跳转/错误提示）。 */
        SUBMITTED,

        /** 验证码已出现：不提交，回退人工。 */
        CAPTCHA,

        /** 表单字段变化 / 脚本异常：回退人工。 */
        NEED_MANUAL,
    }

    /** 填充脚本 token 归一。 */
    fun fillOutcomeOf(token: String?): FillOutcome = when (token) {
        "submitted" -> FillOutcome.SUBMITTED
        "captcha" -> FillOutcome.CAPTCHA
        else -> FillOutcome.NEED_MANUAL
    }

    // ---- JS 注入：提交后复核 -------------------------------------------------

    /**
     * 提交后复核脚本：返回 `captcha`（验证码区可见）/ `error`（页面错误提示非空）/ `wait`。
     * 只读页面提示文本是否为空，不读取/不回传提示内容。
     */
    fun buildResultCheckScript(): String = """
        (function () {
          try {
            function visible(el) {
              if (!el) { return false; }
              var s = window.getComputedStyle ? window.getComputedStyle(el) : null;
              return (el.offsetWidth > 0 || el.offsetHeight > 0) &&
                (!s || (s.display !== 'none' && s.visibility !== 'hidden'));
            }
            if (visible(document.getElementById('captchaDiv'))) { return 'captcha'; }
            // 滑块验证码（#sliderCaptchaDiv 由页面脚本动态注入内容）→ 回退人工。
            var slider = document.getElementById('sliderCaptchaDiv');
            if (!!slider && slider.children && slider.children.length > 0 && visible(slider)) {
              return 'captcha';
            }
            var tip = document.getElementById('showErrorTip');
            var tipText = tip && tip.textContent ? String(tip.textContent).replace(/\s+/g, '') : '';
            if (tipText) { return 'error'; }
            var warn = document.getElementById('showWarnTip');
            var warnText = warn && warn.textContent ? String(warn.textContent).replace(/\s+/g, '') : '';
            if (warnText) { return 'error'; }
            return 'wait';
          } catch (e) {
            return 'wait';
          }
        })()
    """.trimIndent()

    /** 复核结果。 */
    enum class VerifyOutcome {
        /** 页面出现错误提示（密码错误/风控等）。 */
        ERROR,

        /** 需要验证码。 */
        CAPTCHA,

        /** 页面没有提示（可能正在跳转），继续等待页面跳转。 */
        WAIT,
    }

    /** 复核脚本 token 归一。 */
    fun verifyOutcomeOf(token: String?): VerifyOutcome = when (token) {
        "error" -> VerifyOutcome.ERROR
        "captcha" -> VerifyOutcome.CAPTCHA
        else -> VerifyOutcome.WAIT
    }

    // ---- evaluateJavascript 回调解析 ----------------------------------------

    /**
     * 解析 `WebView.evaluateJavascript` 的回调值（JSON 字符串 → Kotlin String；`null` → null）。
     * 页面脚本只返回短 token，这里做通用反转义以防御异常输出。
     */
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
}
