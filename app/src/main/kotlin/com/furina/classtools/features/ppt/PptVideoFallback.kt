package com.furina.classtools.features.ppt

/**
 * 原样播放失败/卡顿降级状态机（契约 §11.3.1；任务 v1.2「error/卡顿 4s×2 → 重试 1 次 → 自动切分步 + Toast」）。
 *
 * 纯逻辑（时间由调用方传入），JVM 可单测：
 * - 播放错误：重试 1 次，仍失败 → 切分步；
 * - 卡顿（播放中 4s 无进度）累计 2 次 → 重试 1 次，再卡顿 → 切分步；
 * - 进度恢复即清零卡顿计数（避免把正常缓冲当失败）。
 */
class PptVideoHealth(
    private val stallTimeoutMs: Long = STALL_TIMEOUT_MS,
    private val maxStalls: Int = MAX_STALLS,
    private val maxRetries: Int = MAX_RETRIES,
) {
    enum class Action { NONE, RETRY, SWITCH_TO_STEPS }

    private var lastProgressAtMs: Long = 0L
    private var lastPositionMs: Long = Long.MIN_VALUE
    private var stalls: Int = 0
    private var retries: Int = 0

    /** 开始/重试后重置卡顿计时（错误重试时调用）。 */
    fun onStart(nowMs: Long) {
        lastProgressAtMs = nowMs
        lastPositionMs = Long.MIN_VALUE
        stalls = 0
    }

    /** 播放错误（onError）：重试一次，仍失败切分步。 */
    fun onError(): Action {
        if (retries < maxRetries) {
            retries += 1
            return Action.RETRY
        }
        return Action.SWITCH_TO_STEPS
    }

    /** 进度轮询：返回 NONE / RETRY / SWITCH_TO_STEPS。 */
    fun onTick(nowMs: Long, positionMs: Long, isPlaying: Boolean): Action {
        if (lastProgressAtMs <= 0L) lastProgressAtMs = nowMs
        if (positionMs != lastPositionMs) {
            lastPositionMs = positionMs
            lastProgressAtMs = nowMs
            stalls = 0
            return Action.NONE
        }
        if (!isPlaying) {
            // 暂停/加载中不算卡顿，重置计时，避免暂停后恢复误判。
            lastProgressAtMs = nowMs
            return Action.NONE
        }
        if (nowMs - lastProgressAtMs < stallTimeoutMs) return Action.NONE

        lastProgressAtMs = nowMs
        stalls += 1
        if (stalls < maxStalls) return Action.NONE

        stalls = 0
        return if (retries < maxRetries) {
            retries += 1
            Action.RETRY
        } else {
            Action.SWITCH_TO_STEPS
        }
    }

    companion object {
        const val STALL_TIMEOUT_MS: Long = 4_000L
        const val MAX_STALLS: Int = 2
        const val MAX_RETRIES: Int = 1
    }
}
