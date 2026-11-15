package com.furina.classtools.data.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 签名算法回归：用 2026-11-14 真实抓包样本锁定（30/30 吻合）。
 * 公式：base64(ts:nonce:md5(salt+完整URL+ts+nonce):token)
 */
class ActivitySignerTest {

    @Test
    fun matchesCapturedSample() {
        val header = ActivitySigner.sign(
            url = "https://stmapi.fjtcm.edu.cn/basic/school/no",
            token = "",
            ts = "1794668287",
            nonce = "I5Bn9M50lZq4AYAK",
        )
        assertEquals(
            "MTc5NDY2ODI4NzpJNUJuOU01MGxacTRBWUFLOmFhYTg2ZmE3YWU0MDc3NWZmNjFjYTU5N2JmYzQzMTlhOg==",
            header,
        )
    }

    @Test
    fun tokenIsAppendedOutsideHash() {
        val a = ActivitySigner.sign("https://x/y", "tokA", "1700000000", "abcdefghijklmnop")
        val b = ActivitySigner.sign("https://x/y", "tokB", "1700000000", "abcdefghijklmnop")
        assertNotEquals(a, b)
    }

    @Test
    fun differentUrlChangesSignature() {
        val a = ActivitySigner.sign("https://x/a", "t", "1700000000", "abcdefghijklmnop")
        val b = ActivitySigner.sign("https://x/b", "t", "1700000000", "abcdefghijklmnop")
        assertNotEquals(a, b)
    }

    @Test
    fun nonceHasExpectedShape() {
        val n = ActivitySigner.nonce()
        assertEquals(16, n.length)
        assertEquals(true, n.all { it.isLetterOrDigit() })
    }
}
