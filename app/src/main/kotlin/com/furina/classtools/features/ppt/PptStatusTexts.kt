package com.furina.classtools.features.ppt

/**
 * 列表页状态文案映射（契约 §4.4 展示规则 + §8.1 状态文案）。
 *
 * 三端文案必须一致；本对象是**唯一**映射入口（UI 不再手写状态字符串）。
 */
object PptStatusTexts {

    /** 转换机离线提示（§3.4：now - seen_at > 180s 视为离线）。 */
    const val AGENT_OFFLINE: String = "转换机离线 · 排队中"

    /** 空态标题 / 说明（§8.1）。 */
    const val EMPTY_TITLE: String = "还没有演示文件"
    const val EMPTY_HINT: String = "上传 PPT 后自动兼容处理，任意设备可下载"

    /** 状态语义分组（UI 决定颜色/是否可播放）。 */
    enum class Kind { UPLOADING, QUEUED, PROCESSING, READY, FAILED, UNKNOWN }

    /** 状态 → 语义分组。 */
    fun kindOf(status: String?): Kind = when (status?.trim()?.lowercase()) {
        "uploading" -> Kind.UPLOADING
        "queued" -> Kind.QUEUED
        "processing" -> Kind.PROCESSING
        "ready" -> Kind.READY
        "failed" -> Kind.FAILED
        else -> Kind.UNKNOWN
    }

    /** 是否可进入播放器（只有 ready）。 */
    fun isPlayable(status: String?): Boolean = kindOf(status) == Kind.READY

    /**
     * 列表卡片状态短文案。
     *
     * @param progress 转换进度（0..100；uploading 时可用本地上传进度）
     * @param agentOnline 转换机是否在线（null = 未取到心跳，不显示离线提示）
     */
    fun text(status: String?, progress: Int = 0, agentOnline: Boolean? = null): String {
        val p = progress.coerceIn(0, 100)
        return when (kindOf(status)) {
            Kind.UPLOADING -> if (p > 0) "上传中 $p%" else "上传中"
            Kind.QUEUED -> if (agentOnline == false) AGENT_OFFLINE else "排队转换"
            Kind.PROCESSING -> "转换中 $p%"
            Kind.READY -> "可播放"
            Kind.FAILED -> "转换失败"
            Kind.UNKNOWN -> "处理中"
        }
    }

    /** 失败原因子行（去掉过长/换行，列表最多两行）。 */
    fun errorLine(error: String?): String {
        val text = error.orEmpty()
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        return if (text.length <= 120) text else text.take(120) + "…"
    }

    /** 是否需要在列表页展示「转换机离线 · 任务排队中」横幅（有排队/转换中的任务且心跳判定离线）。 */
    fun shouldShowAgentOfflineBanner(decks: List<com.furina.classtools.data.ppt.PptDeck>, agentOnline: Boolean?): Boolean {
        if (agentOnline != false) return false
        return decks.any { kindOf(it.status) == Kind.QUEUED || kindOf(it.status) == Kind.PROCESSING }
    }

    /** 上传中/排队/转换中的任务是否置顶（仅本人）。 */
    fun isOwnInProgress(deck: com.furina.classtools.data.ppt.PptDeck, mySid: String?): Boolean =
        mySid != null && deck.ownerSid == mySid && kindOf(deck.status) in setOf(
            Kind.UPLOADING, Kind.QUEUED, Kind.PROCESSING,
        )
}
