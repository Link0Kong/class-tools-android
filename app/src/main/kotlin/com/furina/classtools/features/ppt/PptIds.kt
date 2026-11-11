package com.furina.classtools.features.ppt

import kotlin.random.Random

/**
 * deckId 生成（契约 §3.1）：`d` + base36(unix ms) + 6 位随机小写字母数字。
 *
 * 纯 Kotlin，随机源可注入 → 单测可断言格式与长度。
 */
object PptIds {

    internal const val ALPHABET: String = "abcdefghijklmnopqrstuvwxyz0123456789"

    /** 6 位随机后缀长度（契约固定）。 */
    const val RANDOM_SUFFIX_LEN: Int = 6

    /**
     * @param nowMs 创建时间（unix 毫秒）
     * @param random 随机源（测试注入固定种子）
     */
    fun newDeckId(nowMs: Long, random: Random = Random.Default): String {
        val base36 = java.lang.Long.toString(nowMs.coerceAtLeast(0L), 36)
        val suffix = buildString(RANDOM_SUFFIX_LEN) {
            repeat(RANDOM_SUFFIX_LEN) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
        return "d$base36$suffix"
    }

    /** 合法 deckId 判定：`d` + 任意 base36 时间戳 + 6 位小写字母数字（宽松校验，用于路由/预览）。 */
    fun isDeckId(value: String?): Boolean {
        val v = value?.trim().orEmpty()
        if (v.length < 8 || v.length > 24) return false
        if (v[0] != 'd') return false
        return v.drop(1).all { c -> c in 'a'..'z' || c in '0'..'9' }
    }
}
