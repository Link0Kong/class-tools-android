package com.furina.classtools.features.jw

import java.net.URI

/**
 * 学校 WebVPN（网瑞达/WRD「资源访问控制网关」）入口与登录填充纯逻辑。
 *
 * ## 调研结论（2026-11-15，本机实测 + 学校官方文档）
 *
 * - 学校 WebVPN 入口：**`https://webvpn.fjtcm.edu.cn:4433/login`**（HTTP 200，页面标题
 *   「福建中医药大学WEBVPN」；`https://125.77.120.67:4433` 也会 302 到该域名）。
 *   官方文档：<https://metc.fjtcm.edu.cn/2023/0517/c1095a65136/page.htm>（「输入 webvpn.fjtcm.edu.cn:4433」
 *   「账号密码为学校统一身份认证账号密码」「可选择 CAS 统一身份认证登录」）。
 * - 登录页 DOM（本机抓取 `login.html` 51KB 实测）：用户名 `#user_name`、密码 `#form input[name=password]`、
 *   登录按钮 `#login`；密码由页面自身 CryptoJS/AES-CFB 加密后 POST `/do-login`（App 不接触加密）；
 *   存在「双重登录认证」（短信 / TOTP）与滑块验证码层，出现时**必须回退人工**。
 * - **未验证项（如实）**：登录后的资源页/教务包装 URL 具体格式需要真实账号登录才能确认
 *   （红线：学校账号只能用户本人登录，测试不得使用真实账号）。因此本文件**不猜加密 URL**，
 *   而是登录成功后注入只读 JS 在 WebVPN 门户里找「教务」资源链接并点击；
 *   找不到时提示用户在官方门户里手动点「本科教务」——这是可用的手动入口方案。
 *
 * 本文件不依赖 Compose / Android，可直接 JVM 单测。
 */
object JwWebVpn {

    /** WebVPN 主机（学校内网域名，属于 fjtcm.edu.cn 子域）。 */
    const val HOST: String = "webvpn.fjtcm.edu.cn"

    /** WebVPN 非标准 HTTPS 端口。 */
    const val PORT: Int = 4433

    /** WebVPN 根地址（含端口）。 */
    const val BASE_URL: String = "https://$HOST:$PORT"

    /** WebVPN 登录页（官方统一身份认证账号密码 / CAS 入口所在页）。 */
    const val LOGIN_URL: String = "$BASE_URL/login"

    /** WebVPN 登录后的门户首页（资源列表 / 我的收藏 / 最近访问）。 */
    const val HOME_URL: String = "$BASE_URL/"

    /** 直连校园网探测超时（短超时，避免进工具页卡住）。 */
    const val PROBE_TIMEOUT_MS: Long = 2500L

    /** 教务系统直连端口（HTTP）。 */
    const val JW_PROBE_PORT: Int = 80

    /** 入口选择（用户可手动覆盖自动探测结果）。 */
    enum class Route {
        /** 校园网内直连 210.34.74.77。 */
        DIRECT,

        /** 校外：先登录 WebVPN，再进包装后的教务地址。 */
        WEBVPN,
    }

    /**
     * 自动探测结果 + 用户手动覆盖 → 实际入口。
     * 用户显式选择优先；否则直连可达走直连，不可达自动走 WebVPN。
     */
    fun routeFor(directReachable: Boolean, userOverride: Route? = null): Route =
        userOverride ?: if (directReachable) Route.DIRECT else Route.WEBVPN

    // ---- URL 判定 -----------------------------------------------------------

    private fun hostOf(url: String?): String? = runCatching {
        url?.trim()?.takeIf { it.isNotEmpty() }?.let { URI(it).host?.lowercase() }
    }.getOrNull()

    private fun pathOf(url: String?): String? = runCatching {
        url?.trim()?.takeIf { it.isNotEmpty() }?.let { URI(it).path ?: "" }
    }.getOrNull()

    private fun isHttp(url: String?): Boolean = runCatching {
        val scheme = url?.trim()?.takeIf { it.isNotEmpty() }?.let { URI(it).scheme?.lowercase() }
        scheme == "http" || scheme == "https"
    }.getOrDefault(false)

    /** 是否 WebVPN 主机（含端口无关；WebView 会省略默认端口）。 */
    fun isWebVpnHost(url: String?): Boolean = hostOf(url) == HOST

    /** 是否 WebVPN 登录页（`/login`；忽略 query）。 */
    fun isWebVpnLoginUrl(url: String?): Boolean {
        if (!isHttp(url) || !isWebVpnHost(url)) return false
        val path = pathOf(url).orEmpty().trimEnd('/')
        return path == "/login" || path.endsWith("/login")
    }

    /** 是否 WebVPN 登录后的门户页（非登录页、非被包装的内部页）。 */
    fun isWebVpnPortalUrl(url: String?): Boolean {
        if (!isHttp(url) || !isWebVpnHost(url) || isWebVpnLoginUrl(url)) return false
        return !isWrappedUrl(url)
    }

    /**
     * 是否「WebVPN 包装后的内网 URL」：网瑞达网关格式为 `/<协议>[-端口]/<加密主机>/<原路径>`，
     * 如 `/https/<hex>/authserver/login`、`/http/<hex>/sso.jsp`。这里只按路径前缀判断，不猜加密内容。
     */
    fun isWrappedUrl(url: String?): Boolean {
        if (!isHttp(url) || !isWebVpnHost(url)) return false
        val path = pathOf(url).orEmpty()
        return WRAPPED_PATH_REGEX.containsMatchIn(path)
    }

    /** 是否包装后的统一身份认证（CAS）登录页：路径保留原 `/authserver/login`。 */
    fun isWrappedAuthServerLoginUrl(url: String?): Boolean {
        if (!isWrappedUrl(url)) return false
        return pathOf(url).orEmpty().contains(JwLoginLogic.AUTH_LOGIN_PATH)
    }

    /** 是否包装后的教务 `sso.jsp` 跳板。 */
    fun isWrappedJwEntryUrl(url: String?): Boolean {
        if (!isWrappedUrl(url)) return false
        return pathOf(url).orEmpty().contains(JwLoginLogic.ENTRY_PATH)
    }

    /** 是否已通过 WebVPN 进入教务（包装后的非登录/非 sso.jsp 页面 = 登录后业务页）。 */
    fun isWrappedJwEnteredUrl(url: String?): Boolean {
        if (!isWrappedUrl(url)) return false
        if (isWrappedAuthServerLoginUrl(url) || isWrappedJwEntryUrl(url)) return false
        return true
    }

    /** 网关包装路径前缀：`/http/`、`/https/`、`/http-8080/`、`/https-8443/`。 */
    private val WRAPPED_PATH_REGEX = Regex("""^/(?:http|https)(?:-\d+)?/""", RegexOption.IGNORE_CASE)

    // ---- 注入脚本：WebVPN 登录页自动填充 ------------------------------------

    /**
     * WebVPN 登录页自动填充 + 提交（账号 = 统一身份认证学号，密码 = 本机 Keystore 保存的教务密码）。
     *
     * 只返回状态 token（`submitted` / `no-form` / `no-submit` / `error`），**不返回/不打印账号密码**；
     * 密码加密与 POST 由登录页自身脚本完成（App 只把值写进官方页面输入框）。
     */
    fun buildFillAndSubmitScript(account: String, password: String): String = """
        (function () {
          try {
            var u = document.getElementById('user_name');
            var p = document.querySelector('#form input[name="password"]') ||
                    document.querySelector('input[type="password"]');
            var submit = document.getElementById('login');
            if (!u || !p) { return 'no-form'; }
            if (!submit) { return 'no-submit'; }
            u.value = ${JwLoginLogic.jsStringLiteral(account)};
            u.dispatchEvent(new Event('input', { bubbles: true }));
            u.dispatchEvent(new Event('change', { bubbles: true }));
            p.value = ${JwLoginLogic.jsStringLiteral(password)};
            p.dispatchEvent(new Event('input', { bubbles: true }));
            p.dispatchEvent(new Event('change', { bubbles: true }));
            submit.click();
            return 'submitted';
          } catch (e) {
            return 'error';
          }
        })()
    """.trimIndent()

    /** 注入结果。 */
    enum class LoginOutcome {
        SUBMITTED,
        NEED_MANUAL,
    }

    fun loginOutcomeOf(token: String?): LoginOutcome =
        if (token == "submitted") LoginOutcome.SUBMITTED else LoginOutcome.NEED_MANUAL

    /**
     * 登录提交后的复核脚本：`two-step`（双重认证层可见）/ `error`（错误提示可见）/
     * `logged-in`（登录表单已消失，页面已跳转）/ `wait`。
     */
    fun buildResultCheckScript(): String = """
        (function () {
          try {
            var layer = document.getElementById('second_login_layer');
            var layerTotp = document.getElementById('second_login_layer_totp');
            function visible(el) {
              if (!el) { return false; }
              var s = window.getComputedStyle ? window.getComputedStyle(el) : null;
              return (el.offsetWidth > 0 || el.offsetHeight > 0) &&
                (!s || (s.display !== 'none' && s.visibility !== 'hidden'));
            }
            if (visible(layer) || visible(layerTotp)) { return 'two-step'; }
            var err = document.getElementById('error-message');
            if (err && visible(err)) {
              var text = String(err.textContent || '').replace(/\s+/g, '');
              if (text.length > 0) { return 'error'; }
            }
            if (!document.getElementById('form')) { return 'logged-in'; }
            return 'wait';
          } catch (e) {
            return 'wait';
          }
        })()
    """.trimIndent()

    /** 复核结果。 */
    enum class VerifyOutcome {
        TWO_STEP,
        ERROR,
        LOGGED_IN,
        WAIT,
    }

    fun verifyOutcomeOf(token: String?): VerifyOutcome = when (token) {
        "two-step" -> VerifyOutcome.TWO_STEP
        "error" -> VerifyOutcome.ERROR
        "logged-in" -> VerifyOutcome.LOGGED_IN
        else -> VerifyOutcome.WAIT
    }

    // ---- 注入脚本：WebVPN 门户里自动进入教务资源 ----------------------------

    /**
     * 在 WebVPN 门户页只读扫描 `<a>`，命中「教务 / 本科教务 / 教学管理 / 210.34.74.77」等关键字的
     * 官方资源链接后点击，进入网关包装后的教务地址。
     *
     * 只返回 `clicked` / `none` / `error`，不读取页面文本内容、不打印链接。
     * 找不到时不猜 URL（网关加密格式无法离线确认），由用户手动点「本科教务」资源。
     */
    fun buildPortalJumpScript(): String = """
        (function () {
          try {
            var keywords = ['本科教务', '教务系统', '教务管理', '教务', '教学管理', '210.34.74.77', 'jwgl'];
            var anchors = document.querySelectorAll('a[href]');
            for (var i = 0; i < anchors.length; i++) {
              var a = anchors[i];
              if (!a.href) { continue; }
              var text = String(a.textContent || a.title || '').replace(/\s+/g, '');
              var href = String(a.getAttribute('href') || '');
              for (var k = 0; k < keywords.length; k++) {
                if (text.indexOf(keywords[k]) >= 0 || href.indexOf(keywords[k]) >= 0) {
                  a.click();
                  return 'clicked';
                }
              }
            }
            return 'none';
          } catch (e) {
            return 'error';
          }
        })()
    """.trimIndent()

    /** 门户跳转注入结果。 */
    enum class PortalJumpOutcome {
        CLICKED,
        NEED_MANUAL,
    }

    fun portalJumpOutcomeOf(token: String?): PortalJumpOutcome =
        if (token == "clicked") PortalJumpOutcome.CLICKED else PortalJumpOutcome.NEED_MANUAL
}
