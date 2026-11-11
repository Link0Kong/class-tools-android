package com.furina.classtools.data.diag

import java.security.MessageDigest

/**
 * 诊断文本脱敏（契约 §2 铁律 + §2 显示脱敏）。
 *
 * 两类用途：
 * 1. **上报脱敏**（[sanitizeText]，上限 300 字）：detail/device 里不得出现姓名、学号、宿舍号、token、密码、
 *    cookie、完整 URL 查询串、绝对路径；
 * 2. **显示脱敏**（[sanitizeDisplay]）：服务健康 detail/side 等在页面上展示前，移除 IP、`C:\` 路径、URL、`token=`。
 *
 * 规则（纯字符串，可单测）：
 * - `https?://...` → `[url]`
 * - `[A-Za-z]:\\...`（Windows 绝对路径）→ `[path]`
 * - IPv4 → `[ip]`
 * - `token=…` / `password:…` / `Authorization:…` / `cookie=…` 等键值 → `键=[redacted]`
 * - 残留 `?...` 查询串 → `?[redacted]`
 * - 40+ 位连续 token 形状（base64/JWT 片段）→ `[redacted]`
 * - 换行/连续空白折叠为单个空格；超过 [maxLen] 截断并追加 `…`
 */
object DiagSanitizer {

    const val DEFAULT_MAX_LEN: Int = 300

    private val URL_REGEX = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private val WIN_PATH_REGEX = Regex("""[A-Za-z]:\\[^\s"']+""")
    private val IPV4_REGEX = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b""")
    private val SECRET_REGEX = Regex(
        """(?i)\b(token|pwd|passwd|password|pass|secret|api[_-]?key|key|authorization|cookie|bearer|session)\b\s*[=:]\s*(?:Bearer\s+)?[^\s&,;"']+""",
    )
    private val QUERY_REGEX = Regex("""\S*\?[^\s"'<>]*""")
    private val LONG_SECRET_REGEX = Regex("""\b[A-Za-z0-9_\-.]{40,}\b""")
    private val WHITESPACE_REGEX = Regex("""\s+""")

    /** 上报前脱敏（detail/device 值 / message）。 */
    fun sanitizeText(text: String?, maxLen: Int = DEFAULT_MAX_LEN): String {
        if (text.isNullOrBlank()) return ""
        var out = text
        out = URL_REGEX.replace(out, "[url]")
        out = WIN_PATH_REGEX.replace(out, "[path]")
        out = IPV4_REGEX.replace(out, "[ip]")
        out = SECRET_REGEX.replace(out) { match ->
            val key = match.groupValues[1]
            "$key=[redacted]"
        }
        out = QUERY_REGEX.replace(out, "[redacted]")
        out = LONG_SECRET_REGEX.replace(out, "[redacted]")
        out = WHITESPACE_REGEX.replace(out, " ").trim()
        return truncate(out, maxLen)
    }

    /** 页面展示前脱敏（服务健康 detail/side、AI 分析文本、修复结果等）。 */
    fun sanitizeDisplay(text: String?, maxLen: Int = 400): String = sanitizeText(text, maxLen)

    /** 截断（含省略号占位；maxLen 按字符计）。 */
    fun truncate(text: String, maxLen: Int): String {
        if (maxLen <= 0) return ""
        if (text.length <= maxLen) return text
        if (maxLen == 1) return "…"
        return text.substring(0, maxLen - 1) + "…"
    }

    /** 稳定指纹（sha-256 前 32 hex）：kind|level|checkId|message。 */
    fun fingerprint(kind: String, level: String, checkId: String, message: String): String {
        val raw = listOf(kind, level, checkId, message).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        val hex = buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xFF
                append(HEX[value ushr 4])
                append(HEX[value and 0x0F])
            }
        }
        return hex.take(32)
    }

    /** 客户端生成 id 的随机后缀（base36 形状，避免使用 SecureRandom 依赖）。 */
    fun randomSuffix(length: Int = 12): String {
        val alphabet = "0123456789abcdefghijklmnopqrstuvwxyz"
        val random = java.util.Random(System.nanoTime() xor System.currentTimeMillis())
        return buildString(length) {
            repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
