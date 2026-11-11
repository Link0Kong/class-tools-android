package com.furina.classtools.features.ppt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** manifest URL 解析（契约 §5「URL 解析规则（三端一致）」）。 */
class PptUrlTest {

    private val base = "https://tool-furina-fun.oss-cn-hongkong.aliyuncs.com/ppt/a/dmg2k4x9ab12c/b_abc/"

    @Test
    fun absoluteUrls_usedAsIs() {
        assertEquals("https://x/y.png", PptUrl.resolve(base, "https://x/y.png"))
        assertEquals("http://x/y.png", PptUrl.resolve(base, "http://x/y.png"))
        assertEquals("HTTP://X/Y.PNG", PptUrl.resolve(base, "HTTP://X/Y.PNG"))
        assertEquals("data:image/png;base64,AAA", PptUrl.resolve(base, "data:image/png;base64,AAA"))
        assertTrue(PptUrl.isAbsolute("https://a"))
        assertTrue(PptUrl.isAbsolute("data:image/png;base64,x"))
        assertFalse(PptUrl.isAbsolute("s1-1.webp"))
    }

    @Test
    fun relative_joinedToBase() {
        assertEquals(base + "s1-1.webp", PptUrl.resolve(base, "s1-1.webp"))
        assertEquals(base + "t1.webp", PptUrl.resolve(base, "t1.webp"))
        assertEquals(base + "sub/s1.webp", PptUrl.resolve(base, "sub/s1.webp"))
        // 带前导斜杠也只拼一个
        assertEquals(base + "s1.webp", PptUrl.resolve(base, "/s1.webp"))
    }

    @Test
    fun baseWithoutTrailingSlash() {
        assertEquals(
            "https://x/ppt/a/b/s1.webp",
            PptUrl.resolve("https://x/ppt/a/b", "s1.webp"),
        )
    }

    @Test
    fun emptyInputs() {
        assertEquals("", PptUrl.resolve(base, null))
        assertEquals("", PptUrl.resolve(base, ""))
        assertEquals("", PptUrl.resolve(base, "   "))
        // base 空 → 原样相对路径（播放器会加载失败但不会崩）
        assertEquals("s1.webp", PptUrl.resolve(null, "s1.webp"))
        assertEquals("s1.webp", PptUrl.resolve("", "s1.webp"))
    }

    @Test
    fun trimsWhitespace() {
        assertEquals("https://x/a.png", PptUrl.resolve(base, "  https://x/a.png  "))
        assertEquals(base + "a.webp", PptUrl.resolve("  $base  ", " a.webp "))
    }
}
