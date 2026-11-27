package com.furina.classtools.data.applink

import java.net.URLDecoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/**
 * 跨端内容链接解析（契约：docs/app-links/CONTRACT.md，用例：docs/app-links/cases.json）。
 *
 * 只认 `https://tool.furina.fun/l/...`：
 * - 主页、`/showcase/`、`/legal/`、`/api/`、`/v1/`、`/att/`、`/drop/`、`/pptfiles/`、`/account/`、
 *   `/map/`、`/android/`、`/.well-known/`、`/board` 一律**不是**内容链接，交回浏览器；
 * - 严格校验 scheme/host/端口/路径段/ID/日期/节次/版本，失败只回报原因，不抛异常；
 * - 纯 JVM（不依赖 android.net.Uri），因此可以直接在 `test/` 里跑同一份共享用例；
 * - 手写解析而不是 java.net.URI：URI 对百分号编码比浏览器严格，会让"坏编码"得到
 *   与网页不同的结论（网页是 200 + 明确状态），这里保持与网页同一判定。
 */
sealed interface AppLinkResult {
    data class Draw(val recordId: String) : AppLinkResult
    data class Notice(val noticeId: String) : AppLinkResult

    /** 任务分享：载荷在片段里（不进入服务端日志），由任务页预览后确认导入。 */
    data class TaskShare(val payload: String) : AppLinkResult
    data class Course(val date: String, val start: Int, val end: Int, val name: String?) : AppLinkResult
    data class File(val fileId: String) : AppLinkResult

    /** 是本域名的 `/l/` 链接但参数不合法；页面提示原因，不导航到错误目标。 */
    data class Invalid(val reason: String) : AppLinkResult

    /** 不是内容链接（其它域名、主页、API、关联文件等），保持原浏览器行为。 */
    data object NotLink : AppLinkResult
}

object AppLink {
    const val HOST = "tool.furina.fun"
    const val ORIGIN = "https://$HOST"
    const val MAX_URL = 2048
    const val MAX_ID = 512
    const val MAX_FRAGMENT = 8192
    const val MAX_NAME = 60
    const val MAX_QUERY = 8
    const val PERIODS = 12

    private val DRAW_ID = Regex("^d[0-9a-z]{8,31}$", RegexOption.IGNORE_CASE)
    private val NOTICE_ID = Regex("^[A-Za-z0-9_-]{1,80}(@occ:[0-9]{8})?$")
    private val FILE_ID = Regex("^[A-Za-z0-9_-]{1,80}$")
    private val DATE = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
    private val RANGE = Regex("^(\\d{1,2})-(\\d{1,2})$")
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
    private val TYPES = setOf("draw", "notice", "task-share", "course", "file")

    fun parse(raw: String?): AppLinkResult {
        if (raw.isNullOrBlank()) return AppLinkResult.NotLink
        val text = raw.trim()
        if (text.length > MAX_URL) return AppLinkResult.Invalid("too_long")

        val schemeEnd = text.indexOf("://")
        if (schemeEnd <= 0) return AppLinkResult.NotLink
        if (!text.substring(0, schemeEnd).equals("https", ignoreCase = true)) return AppLinkResult.NotLink
        val afterScheme = text.substring(schemeEnd + 3)
        val slash = afterScheme.indexOf('/')
        val authority = if (slash >= 0) afterScheme.substring(0, slash) else afterScheme
        if (authority.isEmpty()) return AppLinkResult.NotLink
        if (authority.contains('@')) return AppLinkResult.NotLink
        val host: String
        val port: String
        val colon = authority.lastIndexOf(':')
        if (colon >= 0 && !authority.substring(colon).contains(']')) {
            host = authority.substring(0, colon)
            port = authority.substring(colon + 1)
        } else {
            host = authority
            port = ""
        }
        if (!host.equals(HOST, ignoreCase = true)) return AppLinkResult.NotLink
        if (port.isNotEmpty() && port != "443") return AppLinkResult.NotLink
        if (slash < 0) return AppLinkResult.NotLink
        val rest = afterScheme.substring(slash)

        val hashAt = rest.indexOf('#')
        val beforeHash = if (hashAt >= 0) rest.substring(0, hashAt) else rest
        val fragment = if (hashAt >= 0) rest.substring(hashAt + 1) else ""
        val queryAt = beforeHash.indexOf('?')
        val path = if (queryAt >= 0) beforeHash.substring(0, queryAt) else beforeHash
        val rawQuery = if (queryAt >= 0) beforeHash.substring(queryAt + 1) else ""
        if (!path.startsWith("/l/")) return AppLinkResult.NotLink

        val query = parseQuery(rawQuery) ?: return AppLinkResult.Invalid("bad_path")
        if (query.size > MAX_QUERY) return AppLinkResult.Invalid("bad_path")
        val version = query["v"]
        if (version != null && version != "1") return AppLinkResult.Invalid("bad_version")

        val segments = path.substring(3).split('/')
        if (segments.any { it.isEmpty() }) return AppLinkResult.Invalid("bad_path")
        val type = decode(segments[0]) ?: return AppLinkResult.Invalid("bad_path")
        if (type !in TYPES) return AppLinkResult.Invalid("bad_path")

        return when (type) {
            "draw" -> {
                if (segments.size != 2) return AppLinkResult.Invalid("bad_path")
                val id = decode(segments[1]) ?: return AppLinkResult.Invalid("bad_id")
                if (id.length > MAX_ID || !DRAW_ID.matches(id)) return AppLinkResult.Invalid("bad_id")
                AppLinkResult.Draw(id.lowercase())
            }

            "notice" -> {
                if (segments.size != 2) return AppLinkResult.Invalid("bad_path")
                val id = decode(segments[1]) ?: return AppLinkResult.Invalid("bad_id")
                if (id.length > 92 || !NOTICE_ID.matches(id)) return AppLinkResult.Invalid("bad_id")
                AppLinkResult.Notice(id)
            }

            "file" -> {
                if (segments.size != 2) return AppLinkResult.Invalid("bad_path")
                val id = decode(segments[1]) ?: return AppLinkResult.Invalid("bad_id")
                if (id.length > MAX_ID || !FILE_ID.matches(id)) return AppLinkResult.Invalid("bad_id")
                AppLinkResult.File(id)
            }

            "course" -> {
                if (segments.size != 3) return AppLinkResult.Invalid("bad_path")
                val date = decode(segments[1]) ?: return AppLinkResult.Invalid("bad_date")
                if (!validDate(date)) return AppLinkResult.Invalid("bad_date")
                val range = decode(segments[2]) ?: return AppLinkResult.Invalid("bad_range")
                val match = RANGE.find(range) ?: return AppLinkResult.Invalid("bad_range")
                val start = match.groupValues[1].toInt()
                val end = match.groupValues[2].toInt()
                if (start < 1 || start > PERIODS || end < start || end > PERIODS) {
                    return AppLinkResult.Invalid("bad_range")
                }
                AppLinkResult.Course(date, start, end, query["name"]?.take(MAX_NAME))
            }

            else -> {
                if (segments.size != 1) return AppLinkResult.Invalid("bad_path")
                if (!fragment.startsWith("s=")) return AppLinkResult.Invalid("bad_payload")
                val payload = fragment.substring(2)
                if (payload.isEmpty() || payload.length > MAX_FRAGMENT) return AppLinkResult.Invalid("bad_payload")
                if (decode(payload) == null) return AppLinkResult.Invalid("bad_payload")
                AppLinkResult.TaskShare(payload)
            }
        }
    }

    private fun parseQuery(raw: String): Map<String, String>? {
        if (raw.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (part in raw.split('&')) {
            if (part.isEmpty()) continue
            val index = part.indexOf('=')
            val key = decode(if (index < 0) part else part.substring(0, index)) ?: return null
            val value = decode(if (index < 0) "" else part.substring(index + 1)) ?: return null
            out.putIfAbsent(key, value)
        }
        return out
    }

    private fun validDate(text: String): Boolean {
        val match = DATE.find(text) ?: return false
        val year = match.groupValues[1].toIntOrNull() ?: return false
        if (year !in 2000..2099) return false
        return runCatching { LocalDate.parse(text, DATE_FORMAT) }.isSuccess
    }

    /** 百分号解码：`+` 在这里不是空格（与网页一致）。 */
    private fun decode(value: String): String? =
        runCatching { URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrNull()
}
