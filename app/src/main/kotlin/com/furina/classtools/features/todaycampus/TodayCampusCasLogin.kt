package com.furina.classtools.features.todaycampus

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 今日校园/融合门户 **CAS HTTP 登录**（参考 HFUT-Schedule 的
 * `RedirectTicketInterceptor.kt` / `Starter.startWebUrlInner(cookie)` 思路）：
 *
 * `OkHttp` 完成 CAS 表单登录（GET 登录页解析 execution 隐藏域 → POST 学号+密码 →
 * 跟随 302 拿 authserver/portal Cookie）→ 把 Cookie 注入 WebView `CookieManager`
 * → WebView 打开 `https://e.fjtcm.edu.cn/index.html`（见 `TodayCampusWebLogic.PORTAL_HOME_URL`）。
 *
 * 为什么要 HTTP 登录 + Cookie 注入：App 内 WebView 直接走 CAS 表单，部分设备/版本会因
 * 跳转链、Cookie 属性、第三方 Cookie 策略而白屏或登录不完整；先在本机 HTTP 层完成登录、
 * 再把同一组 Cookie 交给 WebView，页面按「已登录」直接进入门户（保留 JS 填表作为兜底）。
 *
 * 安全：密码只在内存中用于请求体，不落盘、不打日志；请求体不在调试日志打印。
 */
object TodayCampusCasLogic {

    /** 学校统一身份认证 CAS 登录页（Apereo CAS；与教务同一套）。 */
    const val CAS_LOGIN_URL: String = "https://authserver.fjtcm.edu.cn/authserver/login"

    /** CAS 登录成功后回跳的门户入口（`service` 参数）。 */
    const val SERVICE_URL: String = "https://e.fjtcm.edu.cn/login2"

    /** 融合门户首页（HTTP 登录成功后 WebView 打开）。 */
    const val PORTAL_HOME_URL: String = TodayCampusWebLogic.PORTAL_HOME_URL

    /** 融合门户主机（成功判定用）。 */
    const val PORTAL_HOST: String = TodayCampusWebLogic.PORTAL_HOST

    /** 统一身份认证主机（失败/登录页判定用）。 */
    const val CAS_HOST: String = "authserver.fjtcm.edu.cn"

    /** HTTP 请求 UA（普通 Android 移动浏览器；门户按 Web 站点处理）。 */
    const val USER_AGENT: String =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    const val MSG_CAS_LOGGING_IN: String = "正在通过统一身份认证自动登录…"
    const val MSG_CAS_SUCCESS: String = "自动登录成功，正在打开融合门户…"
    const val MSG_CAS_FALLBACK: String = "自动登录未完成，已切换到页面登录（可手动完成）"
    const val MSG_CAS_NO_CREDENTIAL: String = "未获取到账号/密码，请在页面手动登录"

    /** CAS 登录页隐藏域（Apereo CAS 5/6 常见字段；缺 execution 视为页面结构变化）。 */
    val REQUIRED_HIDDEN_FIELDS: List<String> = listOf("execution")

    /** Apereo CAS 表单隐藏字段里需要原样回传的键（存在才回传）。 */
    private val HIDDEN_FIELD_KEYS: List<String> = listOf(
        "execution", "_eventId", "lt", "dllt", "cllt", "rmShown", "geolocation", "service",
    )

    /** 页面密码加密盐字段（`id="pwdEncryptSalt"`，不是 name 隐藏域；仅内部使用，不参与表单回传）。 */
    const val SALT_FIELD_KEY: String = "pwdEncryptSalt"

    /**
     * 随机字符串字符集（与学校 CAS 页面 `encrypt.js` 的 `$aes_chars` 完全一致）。
     * 密码加密算法：`AES/CBC/PKCS7`，明文 = randomString(64) + password，
     * key = pwdEncryptSalt（16 字节），iv = randomString(16)，输出 Base64（CryptoJS 默认格式）。
     */
    private const val AES_CHARS: String = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"

    // ---- 请求构造 -----------------------------------------------------------

    /** 学校 CAS 登录页 URL（带 service 参数，URL 编码）。 */
    fun buildLoginUrl(serviceUrl: String = SERVICE_URL): String =
        "$CAS_LOGIN_URL?service=" + URLEncoder.encode(serviceUrl, "UTF-8")

    /** 生成 `$aes_chars` 字符集随机串（与页面 `randomString(n)` 等价）。 */
    fun randomString(length: Int, random: java.util.Random = java.security.SecureRandom()): String {
        val sb = StringBuilder(length)
        repeat(length) { sb.append(AES_CHARS[random.nextInt(AES_CHARS.length)]) }
        return sb.toString()
    }

    /**
     * 学校 CAS 密码加密（AES/CBC/PKCS7，Base64 输出）——用于 HTTP 登录时构造 `password` 字段。
     *
     * 与页面 `encryptPassword(password, $("#pwdEncryptSalt").val())` 同算法：
     * 明文 = 64 位随机串 + 密码；key = salt；iv = 16 位随机串。
     */
    fun encryptPassword(
        password: String,
        salt: String,
        random: java.util.Random = java.security.SecureRandom(),
    ): String = encryptPasswordWith(
        password = password,
        salt = salt,
        prefix64 = randomString(64, random),
        iv16 = randomString(16, random),
    )

    /** 可复现的密码加密（单测用固定 prefix/iv；生产用 [encryptPassword]）。 */
    fun encryptPasswordWith(
        password: String,
        salt: String,
        prefix64: String,
        iv16: String,
    ): String {
        val key = salt.trim()
        if (key.isEmpty()) return password
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        // AES key 必须是 16/24/32 字节；页面 salt 固定 16 字符，异常时回退明文（由调用方再兜底）。
        if (keyBytes.size != 16 && keyBytes.size != 24 && keyBytes.size != 32) return password
        val ivBytes = iv16.toByteArray(Charsets.UTF_8)
        if (ivBytes.size != 16) return password
        val plainBytes = (prefix64 + password).toByteArray(Charsets.UTF_8)
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(keyBytes, "AES"),
            javax.crypto.spec.IvParameterSpec(ivBytes),
        )
        return java.util.Base64.getEncoder().encodeToString(cipher.doFinal(plainBytes))
    }

    /**
     * 解析登录页 HTML 里的 `<input type="hidden" name value>`（含 `id="pwdEncryptSalt"` 盐）。
     *
     * 兼容 name/value 不同顺序与单双引号；只保留 [HIDDEN_FIELD_KEYS] 中的键（忽略 `undefined` 值）。
     */
    fun parseHiddenInputs(html: String?): Map<String, String> {
        if (html.isNullOrBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        val inputRegex = Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE)
        val attrRegex = Regex("""(name|value|id)\s*=\s*("([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
        inputRegex.findAll(html).forEach { match ->
            val tag = match.value
            var name: String? = null
            var value: String? = null
            var id: String? = null
            attrRegex.findAll(tag).forEach { attr ->
                val key = attr.groupValues[1].lowercase()
                val v = attr.groupValues[3].ifEmpty { attr.groupValues[4] }
                when (key) {
                    "name" -> name = v
                    "value" -> value = v
                    "id" -> id = v
                }
            }
            val field = name?.trim().orEmpty()
            val v = value.orEmpty()
            if (field.isNotEmpty() && HIDDEN_FIELD_KEYS.contains(field) && v != "undefined") {
                result[field] = v
            }
            // 盐字段只有 id 没有 name：单独提取给密码加密用。
            if (id?.trim() == SALT_FIELD_KEY && v.isNotEmpty()) {
                result[SALT_FIELD_KEY] = v
            }
        }
        return result
    }

    /**
     * 生成 CAS 表单字段（隐藏域 + 账号 + **加密后的密码** + 提交事件）。
     *
     * 学校 CAS 页面密码字段实际提交的是 `encryptPassword(password, pwdEncryptSalt)`，
     * 明文密码提交会被服务端拒绝；这里默认用 [encryptPassword]，单测可注入固定加密器。
     */
    fun buildFormBody(
        hidden: Map<String, String>,
        username: String,
        password: String,
        encryptor: (password: String, salt: String) -> String = ::encryptPassword,
    ): LinkedHashMap<String, String> {
        val form = LinkedHashMap<String, String>()
        hidden.forEach { (k, v) ->
            if (HIDDEN_FIELD_KEYS.contains(k)) form[k] = v
        }
        form["username"] = username
        val salt = hidden[SALT_FIELD_KEY].orEmpty()
        form["password"] = if (salt.isNotEmpty()) encryptor(password, salt) else password
        form["_eventId"] = "submit"
        // 学校页面 userNameLogin 密码登录的固定字段（页面隐藏域已带则保留原值）。
        if (!form.containsKey("cllt")) form["cllt"] = "userNameLogin"
        if (!form.containsKey("dllt")) form["dllt"] = "generalLogin"
        if (!form.containsKey("rmShown")) form["rmShown"] = "1"
        return form
    }

    // ---- 登录结果判定 -------------------------------------------------------

    private fun hostOf(url: String?): String? = runCatching {
        url?.trim()?.takeIf { it.isNotEmpty() }?.let { java.net.URI(it).host?.lowercase() }
    }.getOrNull()

    private fun isHttp(url: String?): Boolean = runCatching {
        val scheme = url?.trim()?.takeIf { it.isNotEmpty() }?.let { java.net.URI(it).scheme?.lowercase() }
        scheme == "http" || scheme == "https"
    }.getOrDefault(false)

    /** 是否为 CAS 登录页 URL（`/authserver/login`）。 */
    fun isCasLoginUrl(url: String?): Boolean {
        if (!isHttp(url)) return false
        return hostOf(url) == CAS_HOST && (url.orEmpty().contains("/authserver/login"))
    }

    /** 是否为融合门户 URL。 */
    fun isPortalUrl(url: String?): Boolean {
        if (!isHttp(url)) return false
        return hostOf(url) == PORTAL_HOST
    }

    /**
     * HTTP 登录是否成功：最终 URL 已离开 CAS 登录页，且落在门户域（或页面出现门户特征）。
     *
     * 注意：跟随 302 后的最终 URL 可能是门户首页/门户业务页；CAS 未登录时会停在
     * `authserver.../authserver/login`（重新渲染登录页）或带 `?error=`。
     */
    fun isLoginSuccess(finalUrl: String?, body: String?): Boolean {
        if (isCasLoginUrl(finalUrl)) return false
        if (!isHttp(finalUrl)) return false
        if (isPortalUrl(finalUrl)) return true
        // 少数部署会把最终页放在其它校内域（如统一门户跳板），以页面特征兜底。
        val text = body.orEmpty()
        return text.contains("网上办事") || text.contains("融合门户") || text.contains("办事大厅")
    }

    /** 登录失败时的页面文案（账号密码错误 / 验证码 / 锁定等），仅用于 UI 提示，不含密码。 */
    fun loginErrorOf(body: String?): String? {
        val text = body.orEmpty()
        return when {
            text.contains("验证码") -> "需要验证码，请在页面手动登录"
            text.contains("账号或密码") || text.contains("用户名或密码") || text.contains("密码错误") ->
                "账号或密码错误，请在页面手动登录"
            text.contains("锁定") || text.contains("冻结") -> "账号状态异常，请在页面手动登录"
            text.contains("服务器繁忙") || text.contains("系统繁忙") -> "统一身份认证繁忙，请稍后手动登录"
            else -> null
        }
    }

    /**
     * OkHttp `Cookie` → WebView `CookieManager.setCookie(url, "name=value")` 的 URL 与头。
     *
     * WebView CookieManager 按 URL 归属写入；这里用 Cookie 自身 domain/path 还原 URL。
     */
    fun cookieUrlOf(cookie: CasCookie): String {
        val host = cookie.domain.trim().removePrefix(".").ifEmpty { CAS_HOST }
        val path = cookie.path.ifBlank { "/" }
        val scheme = if (cookie.secure) "https" else "https"
        return "$scheme://$host$path"
    }

    fun cookieHeaderOf(cookie: CasCookie): String = "${cookie.name}=${cookie.value}"
}

/** 一条 CAS/门户 Cookie（纯数据，便于单测与 WebView 注入）。 */
data class CasCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/",
    val secure: Boolean = true,
    val hostOnly: Boolean = false,
)

/** CAS HTTP 登录结果（密码不进入结果对象/日志）。 */
data class CasLoginResult(
    val success: Boolean,
    val cookies: List<CasCookie> = emptyList(),
    val finalUrl: String? = null,
    val error: String? = null,
)

/** 内存 CookieJar：收集 CAS/门户 302 链路上的所有 Cookie，供 WebView 注入。 */
class MemoryCookieJar : CookieJar {

    private val cookies = LinkedHashMap<String, Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            val key = "${cookie.domain}|${cookie.path}|${cookie.name}"
            this.cookies[key] = cookie
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        cookies.values.filter { it.matches(url) }

    /** 当前收集到的 Cookie（纯数据）。 */
    fun snapshot(): List<CasCookie> = cookies.values.map { cookie ->
        CasCookie(
            name = cookie.name,
            value = cookie.value,
            domain = cookie.domain,
            path = cookie.path,
            secure = cookie.secure,
            hostOnly = cookie.hostOnly,
        )
    }
}

/**
 * CAS HTTP 登录客户端（OkHttp）。
 *
 * 流程：GET 登录页（拿 execution 等隐藏域）→ POST 账号密码（跟随 302）→ 再 GET 门户首页
 * （确保门户域 Cookie 下发）→ 返回收集到的 Cookie。
 *
 * 注意：**必须由用户本人触发**（使用本机 Keystore 保存的统一身份认证密码）；密码只在内存请求体里。
 */
class TodayCampusCasClient(
    private val baseClient: OkHttpClient = defaultClient(),
) {

    suspend fun login(username: String, password: String): CasLoginResult = withContext(Dispatchers.IO) {
        val jar = MemoryCookieJar()
        val http = baseClient.newBuilder().cookieJar(jar).build()
        try {
            val loginUrl = TodayCampusCasLogic.buildLoginUrl()
            val getRequest = Request.Builder()
                .url(loginUrl)
                .header("User-Agent", TodayCampusCasLogic.USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            val html: String
            val postUrl: HttpUrl
            http.newCall(getRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext CasLoginResult(
                        success = false,
                        error = "统一身份认证不可用（HTTP ${response.code}）",
                    )
                }
                postUrl = response.request.url
                html = response.body?.string().orEmpty()
            }
            val hidden = TodayCampusCasLogic.parseHiddenInputs(html)
            if (hidden.isEmpty() || TodayCampusCasLogic.REQUIRED_HIDDEN_FIELDS.any { hidden[it].isNullOrBlank() }) {
                return@withContext CasLoginResult(
                    success = false,
                    error = "登录页结构变化（未解析到 execution），已回退页面登录",
                )
            }
            val form = TodayCampusCasLogic.buildFormBody(hidden, username, password)
            val formBody = FormBody.Builder().apply {
                form.forEach { (key, value) -> add(key, value) }
            }.build()
            val postRequest = Request.Builder()
                .url(postUrl)
                .post(formBody)
                .header("User-Agent", TodayCampusCasLogic.USER_AGENT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", postUrl.toString())
                .build()
            val finalUrl: String
            val body: String
            http.newCall(postRequest).execute().use { response ->
                finalUrl = response.request.url.toString()
                body = response.body?.string().orEmpty()
            }
            if (!TodayCampusCasLogic.isLoginSuccess(finalUrl, body)) {
                return@withContext CasLoginResult(
                    success = false,
                    finalUrl = finalUrl,
                    error = TodayCampusCasLogic.loginErrorOf(body) ?: "自动登录未完成",
                )
            }
            // 再 GET 门户首页，确保门户域 Cookie 全部下发。
            runCatching {
                val home = Request.Builder()
                    .url(TodayCampusCasLogic.PORTAL_HOME_URL)
                    .header("User-Agent", TodayCampusCasLogic.USER_AGENT)
                    .build()
                http.newCall(home).execute().use { it.body?.string() }
            }
            CasLoginResult(
                success = true,
                cookies = jar.snapshot(),
                finalUrl = finalUrl,
            )
        } catch (e: Exception) {
            // 不打印密码/请求体；异常仅返回类型描述。
            CasLoginResult(
                success = false,
                error = "自动登录网络异常（${e.javaClass.simpleName}）",
            )
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(false)
            .build()
    }
}
