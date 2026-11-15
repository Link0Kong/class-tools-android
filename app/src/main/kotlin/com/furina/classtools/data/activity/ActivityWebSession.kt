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
