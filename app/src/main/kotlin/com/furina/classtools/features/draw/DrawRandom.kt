package com.furina.classtools.features.draw

/**
 * 随机数引擎抽象（可注入 seed，保证单测可复现）。
 *
 * 与网页 `Math.random()` 不同，Android 端用可注入引擎：生产环境每次进入页面用时间种子，
 * 测试用固定 seed 断言「同 seed 同结果」。
 */
fun interface DrawRandom {
    /** 返回 `0 until bound` 的随机整数；`bound <= 0` 时返回 0（调用方需保证 bound>0）。 */
    fun nextInt(bound: Int): Int
}

/** 基于 `java.util.Random` 的可复现实现。 */
class SeededDrawRandom(seed: Long) : DrawRandom {
    private val random = java.util.Random(seed)

    override fun nextInt(bound: Int): Int {
        if (bound <= 0) return 0
        return random.nextInt(bound)
    }
}

/** 默认引擎：时间种子（每次抽签不可复现，与网页行为一致）。 */
fun defaultDrawRandom(): DrawRandom = SeededDrawRandom(System.nanoTime())

/** 洗牌（Fisher-Yates，与网页实现同算法）；返回新列表。 */
fun DrawRandom.shuffledList(size: Int): List<Int> {
    val list = MutableList(size) { it }
    for (i in list.lastIndex downTo 1) {
        val j = nextInt(i + 1)
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
    }
    return list
}

/** 对任意列表做 Fisher-Yates 洗牌。 */
fun <T> DrawRandom.shuffled(items: List<T>): List<T> {
    val list = items.toMutableList()
    for (i in list.lastIndex downTo 1) {
        val j = nextInt(i + 1)
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
    }
    return list
}
