package com.furina.classtools.ui.screens.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 法律文本完整版入口（2026-11-19）纯逻辑测试：
 * - 全文 URL 拼接口径（基址带/不带末尾 `/`、路径带/不带前导 `/`）；
 * - 版本口径与网页全页一致（V1.0 · 生效 2026-11-17）；
 * - 端内摘要不出现与全文冲突的旧版本日期，且明确指向「查看完整版」。
 */
class LegalLinksTest {

    @Test
    fun `URL 拼接：基址与路径的斜杠口径唯一`() {
        val path = "legal/android/privacy.html"
        assertEquals("https://tool.furina.fun/legal/android/privacy.html", LegalLinks.url("https://tool.furina.fun", path))
        assertEquals("https://tool.furina.fun/legal/android/privacy.html", LegalLinks.url("https://tool.furina.fun/", path))
        assertEquals("https://tool.furina.fun/legal/android/privacy.html", LegalLinks.url("https://tool.furina.fun", "/$path"))
        assertEquals("https://tool.furina.fun/legal/android/privacy.html", LegalLinks.url(" https://tool.furina.fun/ ", "/$path"))
        // 调试基址（ECS 明文 IP）同样适用：入口用系统浏览器打开，不由本 App 发请求。
        assertEquals("http://121.196.47.2/legal/android/terms.html", LegalLinks.url("http://121.196.47.2", "/legal/android/terms.html"))
    }

    @Test
    fun `URL 拼接：空基址与空路径的兜底`() {
        assertEquals("/legal/android/privacy.html", LegalLinks.url("", "legal/android/privacy.html"))
        assertEquals("https://tool.furina.fun", LegalLinks.url("https://tool.furina.fun/", ""))
    }

    @Test
    fun `隐私政策与用户协议路径固定（不随构建类型变化）`() {
        assertEquals("legal/android/privacy.html", LegalLinks.PATH_PRIVACY)
        assertEquals("legal/android/terms.html", LegalLinks.PATH_TERMS)
        assertTrue(LegalLinks.privacyUrl("https://example.test").endsWith("/" + LegalLinks.PATH_PRIVACY))
        assertTrue(LegalLinks.termsUrl("https://example.test").endsWith("/" + LegalLinks.PATH_TERMS))
    }

    @Test
    fun `展示路径去掉协议头（弹层与失败提示不打印 scheme）`() {
        assertEquals(
            "tool.furina.fun/legal/android/privacy.html",
            LegalLinks.displayPath("https://tool.furina.fun/legal/android/privacy.html"),
        )
        assertEquals(
            "121.196.47.2/legal/android/terms.html",
            LegalLinks.displayPath("http://121.196.47.2/legal/android/terms.html"),
        )
    }

    @Test
    fun `版本口径与网页全文一致：V1_0 生效 2026-11-17`() {
        assertEquals("V1.0", LegalLinks.DOC_VERSION)
        assertEquals("2026-11-17", LegalLinks.EFFECTIVE_DATE)
        assertEquals("V1.0 · 生效 2026-11-17", LegalLinks.VERSION_LABEL)
        // UI 里「完整版 " + VERSION_LABEL」不能出现重复的 V（真机截图曾出现 "VV1.0"）。
        assertFalse(LegalLinks.VERSION_LABEL.startsWith("VV"))
        assertFalse(PrivacyTexts.PRIVACY_POLICY.contains("VV1.0"))
        // 端内摘要的版本日期与全文对齐（之前是 2026-11-08）。
        assertEquals(LegalLinks.EFFECTIVE_DATE, PrivacyTexts.VERSION)
        assertEquals(LegalLinks.VERSION_LABEL, PrivacyTexts.VERSION_LABEL)
    }

    @Test
    fun `摘要不出现旧版本日期，且都指向查看完整版`() {
        assertFalse(PrivacyTexts.PRIVACY_POLICY.contains("2026-11-08"))
        assertFalse(PrivacyTexts.USER_AGREEMENT.contains("2026-11-08"))
        assertTrue(PrivacyTexts.PRIVACY_POLICY.contains("查看完整版"))
        assertTrue(PrivacyTexts.USER_AGREEMENT.contains("查看完整版"))
        assertTrue(PrivacyTexts.PRIVACY_POLICY.contains("2026-11-17"))
        assertTrue(PrivacyTexts.USER_AGREEMENT.contains("2026-11-17"))
    }

    @Test
    fun `摘要补齐全文关键条款：处理者 联系方式 第三方与跨境 权利与期限`() {
        val privacy = PrivacyTexts.PRIVACY_POLICY
        assertTrue(privacy.contains("陈子麒"))
        assertTrue(privacy.contains("Ko_Link@outlook.com"))
        assertTrue(privacy.contains("15 个工作日"))
        assertTrue(privacy.contains("中国香港"))
        assertTrue(privacy.contains("CloudBase"))
        assertTrue(privacy.contains("保存期限"))
        assertTrue(privacy.contains("OPPO"))
        // 不得把真实手机号/身份证号等写成示例（合规硬线）。
        assertFalse(privacy.contains("138"))
        assertTrue(PrivacyTexts.USER_AGREEMENT.contains("陈子麒"))
        assertTrue(PrivacyTexts.USER_AGREEMENT.contains("禁止内容清单"))
    }
}
