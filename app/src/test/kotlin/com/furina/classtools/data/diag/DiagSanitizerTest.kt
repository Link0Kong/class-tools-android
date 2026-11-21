package com.furina.classtools.data.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 脱敏单测（契约 §2：上报 detail/device 不得含 IP、`C:\` 绝对路径、URL（含查询串）、token/密码等）。
 */
class DiagSanitizerTest {

    @Test
    fun sanitizeText_removesIpv4() {
        assertEquals("连接 [ip] 失败", DiagSanitizer.sanitizeText("连接 192.168.1.10 失败"))
        // 端口不是敏感字段，只替换 IP
        assertEquals("[ip]:8080", DiagSanitizer.sanitizeText("10.0.0.1:8080"))
    }

    @Test
    fun sanitizeText_removesWindowsAbsolutePaths() {
        assertEquals("[path]", DiagSanitizer.sanitizeText("""C:\Users\<用户>\secret\a.txt"""))
        assertEquals("日志 [path] 读取失败", DiagSanitizer.sanitizeText("""日志 D:\briefdesk\logs\agent.log 读取失败"""))
    }

    @Test
    fun sanitizeText_removesUrlsAndQueryStrings() {
        assertEquals("[url]", DiagSanitizer.sanitizeText("https://tool.example.com/api?a=1&token=abc"))
        // 相对路径 + 查询串整体替换（不保留查询参数）
        assertEquals("请求 [redacted] 超时", DiagSanitizer.sanitizeText("请求 /v1/rdb/rest?select=*&token=abc 超时"))
        assertEquals("http 与 https 都脱敏", DiagSanitizer.sanitizeText("http 与 https 都脱敏"))
    }

    @Test
    fun sanitizeText_redactsSecretKeyValues() {
        assertEquals("token=[redacted]", DiagSanitizer.sanitizeText("token=abc123"))
        assertEquals("pwd=[redacted]", DiagSanitizer.sanitizeText("pwd: hunter2"))
        assertEquals("Authorization=[redacted]", DiagSanitizer.sanitizeText("Authorization: Bearer abc.def.ghi"))
        assertEquals("cookie=[redacted]", DiagSanitizer.sanitizeText("cookie=sessionid=xyz"))
        assertEquals("password=[redacted]", DiagSanitizer.sanitizeText("password = 中文密码"))
    }

    @Test
    fun sanitizeText_redactsLongTokenLikeStrings() {
        val jwtLike = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhbm9uIn0.signaturepart"
        val sanitized = DiagSanitizer.sanitizeText("凭证 $jwtLike 已失效")
        assertFalse(sanitized.contains(jwtLike))
        assertTrue(sanitized.contains("[redacted]"))
    }

    @Test
    fun sanitizeText_collapsesWhitespaceAndTruncates() {
        assertEquals("第一行 第二行", DiagSanitizer.sanitizeText("第一行\n\t第二行"))
        val long = "错".repeat(500)
        val sanitized = DiagSanitizer.sanitizeText(long, 300)
        assertEquals(300, sanitized.length)
        assertTrue(sanitized.endsWith("…"))
        assertEquals("", DiagSanitizer.sanitizeText(null))
        assertEquals("", DiagSanitizer.sanitizeText("   "))
    }

    @Test
    fun sanitizeDisplay_sameRulesForHealthDetailAndSide() {
        val detail = "探测 http://10.1.2.3/health?token=abc 失败，路径 C:\\briefdesk\\health.log"
        val display = DiagSanitizer.sanitizeDisplay(detail)
        assertFalse(display.contains("10.1.2.3"))
        assertFalse(display.contains("C:\\briefdesk"))
        assertFalse(display.contains("token=abc"))
        assertTrue(display.contains("[url]"))
        assertTrue(display.contains("[path]"))
    }

    @Test
    fun fingerprint_stableAndSha256Prefix() {
        val first = DiagSanitizer.fingerprint("network", "error", "gateway", "网关延迟 5000ms")
        val second = DiagSanitizer.fingerprint("network", "error", "gateway", "网关延迟 5000ms")
        val different = DiagSanitizer.fingerprint("network", "warn", "gateway", "网关延迟 5000ms")
        assertEquals(first, second)
        assertFalse(first == different)
        assertEquals(32, first.length)
        assertTrue(first.all { it in "0123456789abcdef" })
    }

    @Test
    fun randomSuffix_isAlnumWithRequestedLength() {
        val suffix = DiagSanitizer.randomSuffix(16)
        assertEquals(16, suffix.length)
        assertTrue(suffix.all { it.isLetterOrDigit() })
    }
}
