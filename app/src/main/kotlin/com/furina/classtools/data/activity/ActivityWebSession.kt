package com.furina.classtools.data.activity

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 第二课堂 H5（`rcm.fjtcm.edu.cn/wechat/`）登录入口与 `localStorage.lgInfo` 解析。
 *
 * ## 2026-11-15 根因（真实错误「未找到对应编号的学校信息」）
 *
 * 活动页的登录 WebView 原来直接打开 `https://rcm.fjtcm.edu.cn/wechat/`（无路由 hash）。
 * 该 H5 在非微信环境（`isPlatform=false`）使用 **hash 路由**，路由定义是 `/:no/login`，
 * 首次导航时 `router.beforeEach` 会把 `localStorage.schoolNo` 写成路由参数；没有带
 * `#/<学校编号>/login` 时 `schoolNo` 变成字符串 `"undefined"`，随后页面调用
 * `GET https://stmapi.fjtcm.edu.cn/basic/third_party?sysNo=2nd_classroom&schoolNo=undefined`，
 * 后端返回 **HTTP 500 `{"message":"未找到对应编号的学校信息"}`**，由 H5 的错误拦截器弹给用户。
 *
 * 实测（本机直连学校 API，空 token 签名，2026-11-15）：
 * - `schoolNo=fjtcm` → 200；
 * - `schoolNo=undefined` → 500 + 上述 message（与本条 bug 完全一致）。
 *
 * 因此正确入口是 [SPA_LOGIN_URL]（带 `#/fjtcm/login`）。[parseLgInfo] 同时兼容两种会话结构：
 * - 微信/CAS 登录后 H5 `JSON.stringify(o.data)`：`{id, schoolId, token, studentFile:{classId}}`；
 * - CAS 跳转合并结构：`{userId, classId, token, user:{...}}`。
 *
 * 本文件不接触任何账号密码；`lgInfo` 只做内存解析与脱敏诊断。
 */
object ActivityWebSession {

    /** 学校在第二课堂平台的编号（H5 路由参数；`basic/school/no?no=fjtcm` 实测 200）。 */
    const val SCHOOL_NO: String = "fjtcm"

    /** H5 根地址（非微信普通 WebView 下为 hash 路由）。 */
    const val SPA_BASE: String = "https://rcm.fjtcm.edu.cn/wechat/"

    /** 正确的登录路由：缺学校编号时 H5 会请求 `schoolNo=undefined` 并弹「未找到对应编号的学校信息」。 */
    const val SPA_LOGIN_URL: String = SPA_BASE + "#/" + SCHOOL_NO + "/login"

    /** 读取 `localStorage.lgInfo` 原文（已是 JSON 文本；不打印内容）。 */
    const val LG_INFO_JS: String =
        "(function(){try{return localStorage.getItem('lgInfo')||''}catch(e){return ''}})()"

    /**
     * 兜底脚本：若 `schoolNo` 缺失/为 `undefined`/`null` 则写回 `fjtcm`。
     * 只在页面进入前/读取会话前调用，幂等；不会覆盖正确值。
     */
    const val ENSURE_SCHOOL_NO_JS: String =
        "(function(){try{var no=localStorage.getItem('schoolNo');" +
            "if(!no||no==='undefined'||no==='null'){localStorage.setItem('schoolNo','" +
            SCHOOL_NO + "');return 'fixed'}" +
            "return 'ok'}catch(e){return ''}})()"

    /** 页面 URL 是否被 H5 路由成了 `undefined` 学校编号（出现即需要纠正路由并重载）。 */
    fun hasUndefinedSchoolNo(url: String?): Boolean {
        val text = url?.trim().orEmpty()
        if (text.isEmpty()) return false
        return text.contains("/undefined/") ||
            text.contains("#/undefined") ||
            text.contains("no=undefined") ||
            text.contains("schoolNo=undefined")
    }

    // ---- 微信环境兼容（2026-11-15 用户反馈「活动页出现请在微信小程序打开」）----

    /**
     * 活动 H5 的微信 UA（保持 MicroMessenger / XWEB / Weixin 常用字段）。
     *
     * 2026-11-15 匿名实测（Chromium headless，`rcm.fjtcm.edu.cn/wechat/#/fjtcm/login`）：
     * - 普通 Android UA / 桌面 UA：H5 仍会跳转 `open.weixin.qq.com/connect/oauth2/authorize`，
     *   WeChat 页面显示「请在微信客户端打开链接」（用户看到的是同类提示「请在微信小程序打开」）；
     * - 仅换 UA 不能跳过 OAuth：真正触发跳转的是 H5 启动时缺少 `localStorage.wxUser` 的微信登录检查；
     * - 预置一个**只读环境桩** `wxUser`（随机 openId + wxNickname）后，H5 的 `basic/user/wechat`
     *   查询返回空（匿名接口实测 HTTP 200 空 body）→ H5 走 `status=3` 分支渲染学校登录页，
     *   **不再跳微信 OAuth**（这是本页修复的核心，见 [SEED_WX_SESSION_JS]）。
     */
    const val WECHAT_UA: String =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP2A.240905.003; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/116.0.0.0 " +
            "Mobile Safari/537.36 MicroMessenger/8.0.49.2600(0x2800313D) " +
            "WeChat/arm64 Weixin NetType/WIFI Language/zh_CN ABI/arm64 " +
            "XWEB/1375 MMWEBSDK/20240409"

    /**
     * 微信登录 OAuth 跳转地址（`open.weixin.qq.com/connect/oauth2/authorize`）。
     *
     * 该页面在非微信客户端会显示「请在微信客户端打开链接」；本 App 会拦截它并用环境桩重新加载，
     * 避免把用户丢到微信的提示页。
     */
    fun isWechatOauthUrl(url: String?): Boolean {
        val text = url?.trim().orEmpty()
        if (text.isEmpty()) return false
        val host = runCatching { java.net.URI(text).host?.lowercase() }.getOrNull()
        if (host == "open.weixin.qq.com") return text.contains("/connect/oauth2/authorize")
        return text.startsWith("weixin://")
    }

    /**
     * 最小只读微信环境桩（不伪造登录、不调起支付）：
     * - `window.__wxjs_environment='wechat'` + `__wxjs_is_wkwebview=false`（微信内普通网页环境）；
     * - `window.wx` / `jWeixin` 只提供 `ready/config/error` 与 `miniProgram` 的空实现，
     *   `miniProgram.getEnv` 返回 `{miniprogram:false}`，不执行任何登录/支付/跳转。
     *
     * 页面已加载后注入（WebView 无法在文档创建前注入）；H5 的启动逻辑主要看 `wxUser`，
     * 桩只是消除个别功能对 `wx` 存在性的硬依赖。
     */
    val WX_ENV_STUB_JS: String = """
        (function () {
          try {
            window.__wxjs_environment = 'wechat';
            window.__wxjs_is_wkwebview = false;
            var w = window.wx || {};
            var noop = function () {};
            if (!w.ready) { w.ready = function (cb) { try { cb && cb(); } catch (e) {} }; }
            if (!w.error) { w.error = noop; }
            if (!w.config) { w.config = noop; }
            if (!w.checkJsApi) { w.checkJsApi = noop; }
            if (!w.miniProgram) {
              w.miniProgram = {
                getEnv: function (cb) { try { cb && cb({ miniprogram: false }); } catch (e) {} },
                navigateTo: noop, redirectTo: noop, switchTab: noop, reLaunch: noop,
                navigateBack: noop, postMessage: noop
              };
            }
            window.wx = w;
            window.jWeixin = window.jWeixin || w;
            return 'stubbed';
          } catch (e) {
            return 'error';
          }
        })()
    """.trimIndent()

    /**
     * 预置微信登录检查所需的最小会话桩（**只读环境桩，不伪造登录/不写入 lgInfo**）：
     * - `localStorage.schoolNo = fjtcm`（避免学校编号 undefined 报错）；
     * - `localStorage.wxUser = {openId: "", wxNickname: "班级工具站", schoolNo: fjtcm}`
     *   仅当缺失/无效时写入，使 H5 跳过微信 OAuth 跳转、渲染学校自己的登录页；
     *   **openId 故意留空**（不伪造微信身份）：H5 用空 openId 调 `basic/user/wechat` 匿名实测
     *   返回 HTTP 200 空 body → H5 走 status=3 分支渲染登录页，不会绑定/冒充任何微信账号。
     * 幂等：已有合法 wxUser（wxNickname 非空）时不覆盖。
     */
    val SEED_WX_SESSION_JS: String = """
        (function () {
          try {
            var no = '$SCHOOL_NO';
            var cur = '';
            try { cur = localStorage.getItem('schoolNo') || ''; } catch (e) {}
            if (!cur || cur === 'undefined' || cur === 'null') {
              localStorage.setItem('schoolNo', no);
            }
            var valid = false;
            try {
              var raw = localStorage.getItem('wxUser');
              var u = raw ? JSON.parse(raw) : null;
              valid = !!(u && typeof u.wxNickname === 'string' && u.wxNickname);
            } catch (e) { valid = false; }
            if (!valid) {
              localStorage.setItem('wxUser', JSON.stringify({
                openId: '', wxNickname: '班级工具站', schoolNo: no
              }));
              return 'seeded';
            }
            return 'kept';
          } catch (e) {
            return 'error';
          }
        })()
    """.trimIndent()

    /** 页面出现了微信「请在微信…打开」提示（拦截失败/新版 H5 时的兜底识别，供 UI 提示）。 */
    const val WECHAT_PROMPT_MARKER: String = "请在微信"

    /** 拦截 OAuth 后的 App 内提示（不把用户丢到微信提示页）。 */
    const val MSG_OAUTH_BLOCKED: String =
        "已拦截微信授权跳转，正在用学校登录页继续；若仍提示微信打开，请点「重新加载」"

    /**
     * 解析 WebView 回调里的 `lgInfo` 文本 → [ActivityToken]。
     *
     * 兼容：
     * - `evaluateJavascript` 返回的双层 JSON 字符串（`"{...}"` 或 `"{\"id\":...}"`）；
     * - 顶层字段（`userId`/`id`、`classId`、`token`）；
     * - 嵌套字段（`user`/`userInfo`/`data`/`studentFile`/`userFile` 及其内部 studentFile）；
     * - `isRegister != 1` 的未完成注册会话（SPA 自己也会丢弃，这里保持一致）。
     *
     * 必需字段：token / userId / classId 三者都非空，否则返回 null（避免把半截会话写进 DataStore
     * 后拿错误 userId/classId 去请求，服务端只会返回业务错误）。
     */
    fun parseLgInfo(raw: String?): ActivityToken? {
        val text = decodeJsString(raw)
        if (text.isBlank() || !text.trimStart().startsWith("{")) return null
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null

        val isRegister = (root["isRegister"] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull
        if (isRegister != null && isRegister != 1) return null

        val candidates = linkedSetOf<JsonObject>()
        candidates += root
        NESTED_KEYS.forEach { key ->
            val nested = root[key] as? JsonObject ?: return@forEach
            candidates += nested
            NESTED_KEYS.forEach { inner -> (nested[inner] as? JsonObject)?.let { candidates += it } }
        }

        val token = firstString(candidates, listOf("token"))
        val userId = firstString(candidates, listOf("userId", "id"))
        // candidates 里已含 studentFile/userFile 对象，因此 classId 也覆盖 `studentFile.classId`。
        val classId = firstString(candidates, listOf("classId"))
        if (token.isBlank() || userId.isBlank() || classId.isBlank()) return null
        return ActivityToken(
            token = token,
            userId = userId,
            classId = classId,
            savedAt = System.currentTimeMillis(),
        )
    }

    /**
     * 脱敏诊断：只输出 `lgInfo` 的**键名 / 值类型 / 值长度**，绝不输出 token、学号等值。
     *
     * 例：`keys=id:str:32,schoolId:str:32,token:str:32,studentFile:obj:0,...`。
     * 用于排查「字段名/嵌套与后端要求不一致」，可安全写入 logcat 或验收 artifacts。
     */
    fun describeLgInfo(raw: String?): String {
        val text = decodeJsString(raw)
        if (text.isBlank() || !text.trimStart().startsWith("{")) return ""
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return "unparsable:${text.length}"
        val parts = root.keys.map { key ->
            val element = root[key]
            when (element) {
                is kotlinx.serialization.json.JsonObject -> "$key:obj:${element.size}"
                is kotlinx.serialization.json.JsonArray -> "$key:arr:${element.size}"
                is kotlinx.serialization.json.JsonPrimitive -> {
                    val literal = element.contentOrNull.orEmpty()
                    "$key:${if (element.isString) "str" else "prim"}:${literal.length}"
                }
                else -> "$key:null"
            }
        }
        return "keys=" + parts.joinToString(",")
    }

    private val NESTED_KEYS = listOf("user", "userInfo", "data", "studentFile", "userFile")

    private fun firstString(candidates: Collection<JsonObject>, keys: List<String>): String {
        candidates.forEach { obj ->
            keys.forEach { key ->
                val value = obj.str(key)
                if (value.isNotBlank()) return value
            }
        }
        return ""
    }

    private fun JsonObject.str(key: String): String =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty().trim()

    /**
     * 把 `evaluateJavascript` 回调还原成原始 JSON 文本：
     * 回调值是 JSON 字符串字面量（内部再转义），这里剥壳并反转义 `\n`/`\"`/`\\`/`\uXXXX`。
     */
    internal fun decodeJsString(raw: String?): String {
        if (raw == null) return ""
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "null") return ""
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
