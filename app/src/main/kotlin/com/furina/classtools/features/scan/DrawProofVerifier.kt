package com.furina.classtools.features.scan

import java.security.MessageDigest

/**
 * 抽签存证复算（纯 Kotlin，可单测）。
 *
 * 与网页 `drawSeededInts / drawV1`（mulberry32 + Fisher–Yates）和 `recCheckHash`（SHA-256(canon)）
 * **逐字节一致**；服务端 `draw_record_get` 返回 canon/hash/pool/result/seed 即可在手机上独立核验。
 */
object DrawProofVerifier {

    /** `sha256(canon)` 小写 hex（与网页 `crypto.subtle.digest` 同字节语义）。 */
    fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        val out = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return out.toString()
    }

    /** canon 重新哈希后是否等于服务端 hash（任一为空 → false）。 */
    fun hashMatches(canon: String, expectedHash: String): Boolean {
        if (canon.isEmpty() || expectedHash.isEmpty()) return false
        return sha256Hex(canon) == expectedHash.trim().lowercase()
    }

    /**
     * 网页 `drawV1(pool,count,dup,seed)`：允许重复时逐个取；不重复时 Fisher–Yates 后取前 count 个。
     * 与网页完全一致（count 超过池大小时返回整个洗牌池）。
     */
    fun replay(pool: List<Int>, count: Int, allowDuplicates: Boolean, seed: String): List<Int> {
        if (pool.isEmpty() || count <= 0) return emptyList()
        val rng = Mulberry32(seed)
        if (allowDuplicates) {
            return List(count) { pool[rng.nextInt(pool.size)] }
        }
        val shuffled = pool.toMutableList()
        for (j in shuffled.lastIndex downTo 1) {
            val k = rng.nextInt(j + 1)
            val tmp = shuffled[j]
            shuffled[j] = shuffled[k]
            shuffled[k] = tmp
        }
        return shuffled.take(count)
    }

    /** 复算结果与服务端 result 是否逐项一致。 */
    fun replayMatches(
        pool: List<Int>,
        count: Int,
        allowDuplicates: Boolean,
        seed: String,
        result: List<Int>,
    ): Boolean = replay(pool, count, allowDuplicates, seed) == result

    /** 网页 `drawSeededInts`：mulberry32（UInt 运算 = JS `Math.imul` + `>>> 0` 的低 32 位语义）。 */
    private class Mulberry32(seedHex: String) {
        private var state: UInt = seedHex.take(8).toUIntOrNull(16) ?: 0u

        fun nextInt(bound: Int): Int {
            if (bound <= 0) return 0
            state += 0x6D2B79F5u
            var t = state
            t = (t xor (t shr 15)) * (t or 1u)
            t = t xor (t + ((t xor (t shr 7)) * (t or 61u)))
            val unit = (t xor (t shr 14)).toLong().toDouble() / 4294967296.0
            return (unit * bound).toInt()
        }
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
