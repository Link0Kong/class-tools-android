package com.furina.classtools.data.notices

import java.time.ZoneId
import java.util.Locale

/**
 * 通知展示去重（数据层，UI 无感）。
 *
 * 背景：`NoticesRepository.buildData` 原本把每条非 archived 的 `notices` 行 1:1 映射成一张卡片；
 * 但云端 `notices` 可能对**同一逻辑通知**保留多行（历史未清理的旧 `auto_<旧gid>` 行、重新采集时
 * group id 变化但旧行未归档、跨源合并产生的重复行等）。Android 端只按行 id 展示，于是同一通知出现多条。
 *
 * 本类在数据层做两层稳定去重：
 * 1. **同一 group id**：直接合并（保留 supplies/items/mergedSources，选择编辑/时间更新的一行）；
 * 2. **不同 group id 但同一逻辑通知**：规范化标题 + 分类一致 + 时间窗口接近（≤2h）且存在强证据
 *    （同来源 / 条目内容重叠 / mergedSources 交集 / 同一发送人）才合并——宁可漏合，不误合；
 *    无结构化时间的通知要求更强证据（同来源 + 内容/合并来源重叠）；
 * 3. 合并后条目级按内容指纹去重（保留 supplements 追加顺序）；
 * 4. 人工通知按「规范化标题 + 正文 + 10 分钟内」保守去重（人工通知无 group 语义）。
 *
 * 不合并：分类不同、标题不同、时间相差 > 窗口、无强证据；**auto 与 manual 不互相合并**（不同形态）。
 */
object NoticeDedupe {

    /** 语义去重的时间窗口：同一逻辑通知重新采集时时间通常相同/相差几分钟；2h 内视为同一场。 */
    const val SAME_EVENT_WINDOW_MS: Long = 2L * 60 * 60 * 1000

    /** 人工通知去重窗口（同标题+正文）。 */
    const val MANUAL_WINDOW_MS: Long = 10L * 60 * 1000

    /** 合并后单卡条目上限（防极端重复行导致条目爆炸；parser 本身已限制单行条目数）。 */
    const val MAX_MERGED_ITEMS: Int = 64

    data class Result<T>(
        /** 去重后的列表（按 ts 倒序，与仓库输出一致）。 */
        val entries: List<T>,
        /** 被合并掉的重复条数（用于诊断/测试；不是 UI 数据）。 */
        val mergedCount: Int,
    )

    private val SPACES = Regex("""\s+""")

    // ------------------------------------------------------------ auto

    fun dedupeAuto(entries: List<NoticeEntry>, zone: ZoneId = ZoneId.systemDefault()): Result<NoticeEntry> {
        if (entries.size <= 1) return Result(entries, 0)
        // 新→旧处理：合并时优先保留更新/更完整的一行
        val sorted = entries.sortedWith(
            compareByDescending<NoticeEntry> { rank(it) }.thenByDescending { it.ts },
        )
        val out = ArrayList<NoticeEntry>(sorted.size)
        var merged = 0
        for (entry in sorted) {
            val hit = out.indexOfFirst { sameLogical(it, entry, zone) }
            if (hit >= 0) {
                out[hit] = merge(out[hit], entry)
                merged += 1
            } else {
                out += entry
            }
        }
        return Result(out.sortedByDescending { it.ts }, merged)
    }

    /** 自动通知是否同一逻辑（可单测）。 */
    fun sameLogical(a: NoticeEntry, b: NoticeEntry, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (a.id.isNotBlank() && a.id == b.id) return true
        val titleA = normalizeTitle(a.title)
        val titleB = normalizeTitle(b.title)
        if (titleA.isEmpty() || titleA != titleB) return false
        if (normalizeCat(a.cat) != normalizeCat(b.cat)) return false

        val anchorA = anchorOf(a.group, zone)
        val anchorB = anchorOf(b.group, zone)
        if (anchorA != null && anchorB != null) {
            if (kotlin.math.abs(anchorA - anchorB) > SAME_EVENT_WINDOW_MS) return false
        }

        val sameSource = sourceOf(a).isNotEmpty() && sourceOf(a) == sourceOf(b)
        val itemsOverlap = itemsOverlap(a.items, b.items)
        val sourcesOverlap = mergedSourcesOverlap(a.group, b.group)
        val sameSender = senderOf(a).isNotEmpty() && senderOf(a).equals(senderOf(b), ignoreCase = true)

        return when {
            // 两边都有时间：任一强证据即可（同一个活动/会议/材料）
            anchorA != null && anchorB != null -> sameSource || itemsOverlap || sourcesOverlap || sameSender
            // 无结构化时间：要求同来源 + 内容/合并来源重叠，避免把两条同名「通知」误合
            else -> sameSource && (itemsOverlap || sourcesOverlap)
        }
    }

    /** 合并两条重复通知：主条目取更“新”的一行，条目/合并来源/延期记录取并集。 */
    fun merge(a: NoticeEntry, b: NoticeEntry): NoticeEntry {
        val primary = if (rank(a) >= rank(b)) a else b
        val secondary = if (primary === a) b else a
        val items = mergeItems(primary.items, secondary.items)
        val mergedSources = LinkedHashSet<String>()
        primary.group.mergedSources.forEach { if (it.isNotBlank()) mergedSources += it }
        secondary.group.mergedSources.forEach { if (it.isNotBlank()) mergedSources += it }
        listOf(primary, secondary).forEach { entry ->
            val source = sourceOf(entry)
            if (source.isNotBlank()) mergedSources += source
        }
        val mergedGroup = primary.group.copy(
            // 保留 primary 的语义字段（标题/分类/摘要/时间），仅补空 + 合并列表
            source = primary.group.source.ifBlank { secondary.group.source },
            senderName = primary.group.senderName.ifBlank { secondary.group.senderName },
            senderRole = primary.group.senderRole.ifBlank { secondary.group.senderRole },
            senderBadge = primary.group.senderBadge.ifBlank { secondary.group.senderBadge },
            summary = primary.group.summary.ifBlank { secondary.group.summary },
            aiSummary = primary.group.aiSummary.ifBlank { secondary.group.aiSummary },
            deadline = primary.group.deadline.ifBlank { secondary.group.deadline },
            deadlineAction = primary.group.deadlineAction.ifBlank { secondary.group.deadlineAction },
            eventAt = primary.group.eventAt.ifBlank { secondary.group.eventAt },
            eventAction = primary.group.eventAction.ifBlank { secondary.group.eventAction },
            expireAt = primary.group.expireAt.ifBlank { secondary.group.expireAt },
            timeLabel = primary.group.timeLabel.ifBlank { secondary.group.timeLabel },
            extensions = mergeExtensions(primary.group.extensions, secondary.group.extensions),
            aiPoints = (primary.group.aiPoints + secondary.group.aiPoints).distinct(),
            aiSegments = (primary.group.aiSegments + secondary.group.aiSegments).distinct(),
            mergedSources = mergedSources.toList(),
            crossSource = primary.group.crossSource ||
                secondary.group.crossSource ||
                (sourceOf(primary).isNotEmpty() && sourceOf(primary) != sourceOf(secondary)) ||
                mergedSources.size > 1,
            items = emptyList(),
        )
        val mergedRow = primary.row.copy(
            ts = maxOf(primary.ts, secondary.ts),
            hidden = primary.hidden && secondary.hidden,
            editedAt = maxOf(primary.row.editedAt, secondary.row.editedAt),
            editedBy = primary.row.editedBy.ifBlank { secondary.row.editedBy },
        )
        return primary.copy(
            row = mergedRow,
            group = mergedGroup,
            items = items,
            hidden = primary.hidden && secondary.hidden,
            edited = primary.edited || secondary.edited,
        )
    }

    // ------------------------------------------------------------ manual

    fun dedupeManual(
        notices: List<ManualNotice>,
        windowMs: Long = MANUAL_WINDOW_MS,
    ): Result<ManualNotice> {
        if (notices.size <= 1) return Result(notices, 0)
        val sorted = notices.sortedByDescending { it.ts }
        val out = ArrayList<ManualNotice>(sorted.size)
        var merged = 0
        for (notice in sorted) {
            val key = manualKey(notice)
            val hit = out.indexOfFirst { existing ->
                existing.id == notice.id ||
                    (manualKey(existing) == key && kotlin.math.abs(existing.ts - notice.ts) <= windowMs)
            }
            if (hit >= 0) {
                val existing = out[hit]
                // 保留附件更完整的一条；同 id 时用更新的。
                val primary = if (existing.attachments.size >= notice.attachments.size) existing else notice
                val secondary = if (primary === existing) notice else existing
                out[hit] = primary.copy(
                    row = primary.row.copy(ts = maxOf(primary.ts, secondary.ts)),
                    attachments = primary.attachments.ifEmpty { secondary.attachments },
                )
                merged += 1
            } else {
                out += notice
            }
        }
        return Result(out.sortedByDescending { it.ts }, merged)
    }

    // ------------------------------------------------------------ 条目级

    /** 合并条目列表：保留 primary 顺序，追加 secondary 中内容不重复的条目。 */
    fun mergeItems(primary: List<NoticeItemMeta>, secondary: List<NoticeItemMeta>): List<NoticeItemMeta> {
        if (secondary.isEmpty()) return primary.take(MAX_MERGED_ITEMS)
        val seen = HashSet<String>()
        val out = ArrayList<NoticeItemMeta>(minOf(primary.size + secondary.size, MAX_MERGED_ITEMS))
        (primary + secondary).forEach { item ->
            val fingerprint = fingerprintOf(item)
            if (fingerprint.isNotEmpty() && !seen.add(fingerprint)) return@forEach
            if (out.size >= MAX_MERGED_ITEMS) return@forEach
            out += item
        }
        return out
    }

    fun itemsOverlap(a: List<NoticeItemMeta>, b: List<NoticeItemMeta>): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val setA = a.map { fingerprintOf(it) }.filter { it.isNotEmpty() }.toSet()
        if (setA.isEmpty()) return false
        return b.any { fingerprintOf(it) in setA }
    }

    /** 条目内容指纹（不含 base64/签名 URL 参数；用于跨重复通知判断内容重叠/条目去重）。 */
    fun fingerprintOf(item: NoticeItemMeta): String = when (item) {
        is NoticeItemMeta.Text -> "text:" + normalizeContent(item.text)
        is NoticeItemMeta.Image -> "img:" + normalizeContent(item.caption) + "|" + item.approxBytes
        is NoticeItemMeta.File -> "file:" + normalizeContent(item.name) + "|" + normalizeContent(item.sizeText)
        is NoticeItemMeta.Form -> "form:" + normalizeContent(item.name) + "|" + normalizeContent(item.closeText)
        is NoticeItemMeta.Link -> "link:" + normalizeContent(item.name) + "|" + normalizeContent(item.host)
        is NoticeItemMeta.Merged -> "merged:" + normalizeContent(item.name) + "|" +
            item.messages.joinToString(";") { normalizeContent(it.text) }
        is NoticeItemMeta.Unknown -> "unknown:" + item.kind
    }

    // ------------------------------------------------------------ 工具

    fun normalizeTitle(raw: String): String = normalizeContent(raw)

    fun normalizeCat(raw: String): String = normalizeContent(raw).lowercase(Locale.CHINA)

    private fun normalizeContent(raw: String): String =
        SPACES.replace(raw.trim(), " ").lowercase(Locale.CHINA)

    private fun sourceOf(entry: NoticeEntry): String =
        normalizeContent(entry.group.source)

    private fun senderOf(entry: NoticeEntry): String =
        normalizeContent(entry.group.senderName)

    /** 语义时间锚：真实截止 > 活动时间 > 报名窗口 > expireAt；无结构化时间 → null。 */
    fun anchorOf(group: NoticeGroupDto, zone: ZoneId = ZoneId.systemDefault()): Long? {
        NoticeStatusMachine.deadlineOf(group, zone)?.let { return it }
        NoticeStatusMachine.eventAtOf(group, zone)?.let { return it }
        val signup = NoticeStatusMachine.signupOf(group, zone)
        signup.end?.let { return it }
        signup.start?.let { return it }
        return NoticeStatusMachine.archiveAtOf(group, zone)
    }

    /**
     * 排序权重：管理员编辑/override 更新优先；其次行 ts 最新。
     * 同分时条目更多者优先（合并时保留信息更全的一行）。
     */
    private fun rank(entry: NoticeEntry): Long {
        var score = 0L
        score += entry.row.editedAt * 4
        score += entry.ts * 2
        score += entry.items.size.toLong()
        if (!entry.hidden) score += 1L // 管理端同组重复时优先保留未隐藏行
        return score
    }

    private fun mergedSourcesOverlap(a: NoticeGroupDto, b: NoticeGroupDto): Boolean {
        val setA = a.mergedSources.map { normalizeContent(it) }.filter { it.isNotEmpty() }.toSet()
        if (setA.isEmpty()) return false
        return b.mergedSources.any { normalizeContent(it) in setA }
    }

    private fun mergeExtensions(
        first: List<NoticeExtension>,
        second: List<NoticeExtension>,
    ): List<NoticeExtension> =
        (first + second).filter { it.deadline.isNotBlank() }.distinctBy { normalizeContent(it.deadline) }

    private fun manualKey(notice: ManualNotice): String =
        normalizeTitle(notice.row.title) + "|" + normalizeContent(notice.row.body)
}
