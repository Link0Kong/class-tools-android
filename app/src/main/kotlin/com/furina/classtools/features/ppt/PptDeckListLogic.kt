package com.furina.classtools.features.ppt

import com.furina.classtools.data.ppt.PptDeck

/**
 * 列表页过滤 / 排序（契约 §4.4）：
 * - `uploading` 且**不是本人**的记录不显示（别人传一半的文件对他没意义）；
 * - 排序：本人在传/转换中的置顶，其余按 `created_at desc`。
 *
 * 纯 Kotlin（JVM 单测覆盖）。
 */
object PptDeckListLogic {

    /** 过滤 + 排序。 */
    fun visible(decks: List<PptDeck>, mySid: String?): List<PptDeck> =
        decks.asSequence()
            .filter { deck -> deck.id.isNotBlank() }
            .filterNot { deck ->
                PptStatusTexts.kindOf(deck.status) == PptStatusTexts.Kind.UPLOADING &&
                    (mySid == null || deck.ownerSid != mySid)
            }
            .sortedWith(comparator(mySid))
            .toList()

    /** 排序规则：本人进行中 → 置顶；其余按创建时间倒序；同组内再按 id 稳定排序。 */
    fun comparator(mySid: String?): Comparator<PptDeck> =
        compareByDescending<PptDeck> { if (PptStatusTexts.isOwnInProgress(it, mySid)) 1 else 0 }
            .thenByDescending { it.createdAt }
            .thenBy { it.id }

    /** 列表页是否展示占位（无可见记录）。 */
    fun isEmpty(decks: List<PptDeck>, mySid: String?): Boolean = visible(decks, mySid).isEmpty()

    /** 本人记录（删除/重试入口只对我自己的卡片展示）。 */
    fun isMine(deck: PptDeck, mySid: String?): Boolean =
        mySid != null && deck.ownerSid == mySid

    /** 失败重试入口（仅本人 failed）。 */
    fun canRetry(deck: PptDeck, mySid: String?): Boolean =
        isMine(deck, mySid) && PptStatusTexts.kindOf(deck.status) == PptStatusTexts.Kind.FAILED

    /** 删除入口（仅本人；契约 §4.6「前端只对本人入口展示」）。 */
    fun canDelete(deck: PptDeck, mySid: String?): Boolean = isMine(deck, mySid)

    /** 上传者展示名：优先姓名，缺失退回「未知同学」。 */
    fun ownerLabel(deck: PptDeck): String =
        deck.ownerName.trim().ifBlank { deck.ownerSid.takeLast(2).let { if (it.isBlank()) "未知同学" else "同学 $it" } }

    /** 页数展示：`12 页 · 37 步`；未转换完成只显示 `12 页`。 */
    fun pageText(deck: PptDeck): String {
        val pages = deck.slideCount
        val steps = deck.stepCount
        return when {
            pages <= 0 -> "—"
            steps > 0 -> "$pages 页 · $steps 步"
            else -> "$pages 页"
        }
    }
}
