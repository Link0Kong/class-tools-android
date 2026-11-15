package com.furina.classtools.data.notices

import kotlinx.coroutines.delay

/**
 * 通知「自动全部加载」结果。
 *
 * - [Completed]：已无更多（[data] 为最后一页；null = 加载前就没有缓存）；
 * - [Failed]：自动重试一次后仍失败（调用方保留已加载页并展示「重试」）。
 */
sealed interface NoticesAutoLoadResult {
    data class Completed(val data: NoticesData?) : NoticesAutoLoadResult

    data class Failed(val message: String, val data: NoticesData?) : NoticesAutoLoadResult
}

/**
 * 通知「自动在后台连续加载剩余所有页」控制器（用户不再点「加载更多/加载全部」）。
 *
 * 口径（task-56 B）：
 * - 首屏成功后由 UI 调用 [loadAll]：循环 [NoticesRepository.loadMore] 直到 `hasMore=false`；
 * - **每页到来即回调** [onPage]（UI 逐页更新列表，不卡首屏）；
 * - 某页失败 → 等待 [retryDelayMs] **自动重试一次**；仍失败 → [NoticesAutoLoadResult.Failed]
 *   并保留已加载页（[NoticesAutoLoadResult.Failed.data]）；UI 提供重试行；
 * - 并发去重由 UI 保证（同一时刻只跑一个 loop）+ 仓库互斥（同一请求不会并发）；
 * - 签名变化（云端通知有增删/隐藏）时 [NoticesRepository.loadMore] 内部会重载第一页并返回新基线，
 *   本循环继续从新基线加载直到无更多（[maxPages] 仅作死循环保护）。
 *
 * @param repository 通知仓库（复用其 5/3/1 自适应分页与 30s 超时重试）
 * @param retryDelayMs 失败后自动重试前的等待（默认 400ms）
 * @param maxSleep 便于单测注入的 delay 实现（默认 [delay]）
 * @param maxPages 死循环保护：最多自动加载页数（正常情况下 `hasMore` 为 false 即提前结束）
 */
class NoticesAutoLoader(
    private val repository: NoticesRepository,
    private val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
    private val maxSleep: suspend (Long) -> Unit = { delay(it) },
    private val maxPages: Int = DEFAULT_MAX_PAGES,
) {

    /**
     * 自动加载剩余所有页；每成功一页调用 [onPage]（调用方在主线程/Compose scope 更新状态）。
     *
     * 返回 [NoticesAutoLoadResult.Completed] 表示已经加载到底（或加载前就无更多）；
     * 返回 [NoticesAutoLoadResult.Failed] 表示自动重试一次后仍失败（已加载页保留）。
     */
    suspend fun loadAll(onPage: (NoticesData) -> Unit): NoticesAutoLoadResult {
        var last: NoticesData? = null
        var retried = false
        var pages = 0
        while (pages < maxPages) {
            val current = last ?: repository.cached() ?: return NoticesAutoLoadResult.Completed(null)
            last = current
            if (!current.hasMore) return NoticesAutoLoadResult.Completed(current)
            when (val result = repository.loadMore()) {
                is NoticesLoadResult.Success -> {
                    last = result.data
                    onPage(result.data)
                    pages += 1
                    retried = false
                }

                is NoticesLoadResult.Failure -> {
                    if (!retried) {
                        retried = true
                        pages += 1
                        maxSleep(retryDelayMs)
                        continue
                    }
                    return NoticesAutoLoadResult.Failed(result.message, last)
                }
            }
        }
        // maxPages 保护触发：视为中断，交给 UI 显示「重试」而不是无限循环。
        return NoticesAutoLoadResult.Failed(STOPPED_MESSAGE, last)
    }

    companion object {
        const val DEFAULT_RETRY_DELAY_MS: Long = 400L

        /** 正常通知量远小于此（每页最多 20 条 → 保护上限 500 页）；签名反复变化时防死循环。 */
        const val DEFAULT_MAX_PAGES: Int = 500

        const val STOPPED_MESSAGE: String = "自动加载已暂停，请重试"
    }
}
