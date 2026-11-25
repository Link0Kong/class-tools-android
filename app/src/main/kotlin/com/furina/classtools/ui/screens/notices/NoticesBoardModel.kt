package com.furina.classtools.ui.screens.notices

import com.furina.classtools.data.marks.MarkKind
import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.notices.NoticeBodyParser
import com.furina.classtools.data.notices.NoticeBucket
import com.furina.classtools.data.notices.NoticeDigest
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeStatus
import com.furina.classtools.data.notices.NoticeStatusMachine
import com.furina.classtools.data.notices.NoticeStats
import com.furina.classtools.data.notices.NoticesData
import java.time.ZoneId

// =============================================================================
// 通知页纯逻辑模型（数据 → 视图；契约 `web/通知三端_长期通知与时间显示_契约_20261114.md` §2）
//
// 这里只放**无 Compose 依赖**的纯函数，Compose 侧只负责渲染，便于 JVM 单测直接覆盖：
//  - [buildUiItems]    数据 → UI 项（期次卡 / 长期定义 / 资料无时间 / 已结束 判定都在这里）；
//  - [buildOverview]   §2.4 统计口径（5 张数据卡 / 工作台同口径，复用数据层 statsOf）；
//  - [viewContentOf]   chips / 顶部卡 / 搜索 → 分区视图（主列表 / 资料栏 / 已过期 / 长期清单 / 最新发布）。
// =============================================================================

/** 顶部 5 张可点数据卡（契约 §2.1；点卡筛选、再点取消、与 chips 互斥）。 */
internal enum class NoticeCardFilter(val label: String) {
    PENDING("待处理"),
    URGENT("24h 截止"),
    WEEK("7 天内"),
    LONGTERM("长期通知"),
    LATEST("最新发布"),
}

/** 通知卡的展示模式（决定状态胶囊 / 操作区 / meta：契约 §2.2/§2.3）。 */
internal enum class NoticeCardMode {
    /** 普通通知（含人工）：标记已处理 / 置顶。 */
    NORMAL,

    /** 期次卡：「长期安排」徽标 + `本次 M月D日（周X）HH:mm` + 「本次已处理」。 */
    OCCURRENCE,

    /** 长期安排定义卡（长期清单视图）：周期 / 下次 / 进行中，**无已处理**。 */
    LONGTERM_DEF,

    /** 已过期（含「长期安排已结束」）：无已处理操作。 */
    EXPIRED,
}

/**
 * 页面视图内容（纯函数结果；Compose 只渲染）。
 *
 * - [primary]：主列表（普通通知 + 期次卡，或「最新发布」列表）；
 * - [material]：「资料 / 无时间」栏（§1.2；默认展开，可折叠）；
 * - [expired]：已过期区（默认折叠）；
 * - [longterm]：长期通知筛选 → 长期安排清单。
 */
internal data class NoticesViewContent(
    val primary: List<NoticeUiItem> = emptyList(),
    val material: List<NoticeUiItem> = emptyList(),
    val expired: List<NoticeUiItem> = emptyList(),
    val longterm: List<NoticeUiItem> = emptyList(),
    val latest: List<NoticeUiItem> = emptyList(),
    /** 是否展示「资料 / 无时间」栏（全部视图；仅当存在兜底卡时显示，契约 2026-11-18 §3）。 */
    val showMaterial: Boolean = false,
    /** 是否展示已过期区（全部 / 待处理 / 24h / 7 天视图；默认折叠）。 */
    val showExpiredSection: Boolean = false,
    /** 长期通知筛选视图（主区替换为长期安排清单；不显示期次卡/已处理）。 */
    val longtermOnly: Boolean = false,
    /** 最新发布筛选视图（按发布时间倒序；不分资料/长期区）。 */
    val latestOnly: Boolean = false,
    /** 当前筛选下主列表条目数（期次计 1 条；长期清单视图计长期条数）。 */
    val totalCount: Int = 0,
) {
    val isEmpty: Boolean
        get() = primary.isEmpty() && material.isEmpty() && expired.isEmpty() &&
            longterm.isEmpty() && latest.isEmpty()
}

private const val DAY_MS = 24L * 60L * 60L * 1000L

// -----------------------------------------------------------------------------
// 数据 → UI 项
// -----------------------------------------------------------------------------

/**
 * 数据 → UI 列表（纯函数；只做分类/排序，不做合并/去重）。
 *
 * 每个自动通知组产出 **1 个** UI 项（长期安排：处于期次窗口内时为「期次卡」形态，
 * 位置/已处理按该期次 key；长期定义与「长期安排已结束」由字段区分）。
 */
internal fun buildUiItems(
    data: NoticesData?,
    marks: MarksSnapshot,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): List<NoticeUiItem> {
    if (data == null) return emptyList()
    val autoItems = data.auto.map { entry -> autoUiItem(entry, marks, nowMillis, zone) }
    val manualItems = data.manual.map { notice ->
        val pinned = marks.isDone(MarkKind.NT, notice.id)
        val anchor = notice.ts.takeIf { it > 0L }
        NoticeUiItem(
            key = notice.id,
            manual = notice,
            hidden = notice.row.hidden,
            pinned = pinned,
            ts = notice.ts,
            title = NoticeStatusMachine.displayText(
                notice.row.title.ifBlank { "班级通知" },
                anchor,
                nowMillis,
                zone,
            ),
            summary = "",
            cat = "班级通知",
            tone = NoticeTone.GREY,
            postedText = formatPosted(notice.ts, nowMillis, zone),
            attachmentCount = notice.attachments.size,
            publishedMillis = notice.ts,
        )
    }
    return manualItems + autoItems
}

private fun autoUiItem(
    entry: NoticeEntry,
    marks: MarksSnapshot,
    nowMillis: Long,
    zone: ZoneId,
): NoticeUiItem {
    val group = entry.group
    // 发布时间（契约 2026-11-18 §1/§6）：优先组 publishedAt；旧缓存用渲染元信息里的 items[].time 兜底。
    val publishedTs = NoticeStatusMachine.publishedTsOf(group, entry.items, nowMillis, zone)
    val rawStatus = NoticeStatusMachine.statusOf(group, nowMillis, zone, publishedTs)
    val recurrence = NoticeStatusMachine.effectiveRecurrenceOf(group)
    // 长期安排已结束（until 到期 / 下一次超出 until）→ 归已过期区，不再产生期次（§1.1/§1.3）
    val longtermEnded = recurrence != null && rawStatus.expired
    val occurrence = if (recurrence != null && !longtermEnded) {
        NoticeStatusMachine.occurrenceOf(group, nowMillis, zone)
    } else {
        null
    }
    val occurrenceDone = occurrence?.let { marks.isDone(MarkKind.NT_DONE, it.key) } ?: false
    val status = when {
        longtermEnded -> rawStatus
        occurrence != null -> NoticeStatus(
            key = "occurrence",
            // 期次卡状态胶囊：`本次 M月D日（周X）HH:mm`（§2.3）
            label = NoticeStatusMachine.occurrenceLabelOf(occurrence, nowMillis, zone),
            cls = "recurring",
            expired = false,
            bucket = NoticeBucket.TODAY,
        )

        recurrence != null -> NoticeStatus(
            key = "recurring",
            label = "进行中",
            cls = "recurring",
            expired = false,
            bucket = NoticeBucket.RECURRING,
        )

        else -> rawStatus
    }
    val anchor = NoticeStatusMachine.anchorOf(group, entry.items, zone)
    val publishedMillis = entry.boardPublishedMillis.takeIf { it > 0 } ?: publishedAtOf(entry, zone)
    val summary = group.aiSummary.ifBlank { group.summary }
    val hasTimeAnchor = NoticeStatusMachine.hasTimeAnchor(group, zone)
    return NoticeUiItem(
        key = occurrence?.key ?: entry.rowId,
        auto = entry,
        status = status,
        done = if (recurrence != null) occurrenceDone else marks.isDone(MarkKind.NT_DONE, entry.id),
        pinned = false,
        ts = entry.ts,
        title = NoticeStatusMachine.displayText(entry.title, anchor, nowMillis, zone),
        summary = NoticeStatusMachine.displayText(summary.trim(), anchor, nowMillis, zone),
        cat = entry.cat,
        tone = toneOfCat(entry.cat),
        source = group.source,
        sourceKind = sourceKindOf(group.source),
        senderLine = listOf(group.senderRole, group.senderName)
            .filter { it.isNotBlank() }
            .joinToString(" · "),
        timeLabel = NoticeStatusMachine.displayText(group.timeLabel, anchor, nowMillis, zone),
        postedText = formatPosted(publishedMillis, nowMillis, zone),
        whenMillis = NoticeStatusMachine.whenInfo(group, nowMillis, zone)?.atMillis,
        expired = rawStatus.expired,
        attachmentCount = NoticeBodyParser.attachmentCount(entry.items),
        mergeCount = group.mergedSources.size,
        edited = entry.edited,
        hidden = entry.hidden,
        recurring = recurrence != null,
        actionable = group.actionable,
        publishedMillis = publishedMillis,
        publishedTsMillis = publishedTs ?: 0L,
        hasTimeAnchor = hasTimeAnchor,
        // §3（2026-11-18）：只有「既无时间锚点又无发布时间」才是资料栏兜底；发布卡进主列表
        material = recurrence == null && !rawStatus.expired && !hasTimeAnchor && (publishedTs ?: 0L) <= 0L,
        recurrence = recurrence,
        longtermEnded = longtermEnded,
        occurrenceMillis = occurrence?.nextMillis,
        occurrenceKey = occurrence?.key,
        occurrenceDone = occurrenceDone,
        recurLabel = if (recurrence != null) NoticeStatusMachine.recurLabelOf(group) else "",
        recurNextMillis = if (recurrence != null && !longtermEnded) {
            NoticeStatusMachine.recurNextOf(group, nowMillis, zone)
        } else {
            null
        },
        recurNextText = if (recurrence != null && !longtermEnded) {
            NoticeStatusMachine.recurNextTextOf(group, nowMillis, zone)
        } else {
            ""
        },
    )
}

/**
 * 真实发布时间（「最新发布」卡文案 / 筛选排序 / 卡片 postedText 共用同一键）：
 *
 * - 优先契约字段 `publishedAt`（2026-11-18 §1/§5；管线对所有组统一写入）；
 * - 旧缓存没有该字段时回退原推算：组 `updatedAt` 与行 `ts` 取较大值；都缺失时回退行 `ts`；
 * - **不再使用首条 item 的消息时间**：`item.time` 常见只有 `HH:mm`（当天时钟），按渲染日解析会
 *   把历史通知算成「今天」，导致「最新发布」排序把旧通知顶到最前（2026-11-16 用户反馈的根因）；
 * - **不使用**管理员 `edited_at`：编辑不是重新发布，不应改变发布顺序（编辑状态由「已更新」标签表达）。
 */
internal fun publishedAtOf(entry: NoticeEntry, zone: ZoneId = ZoneId.systemDefault()): Long {
    NoticeStatusMachine.parseTs(entry.group.publishedAt, zone)?.takeIf { it > 0L }?.let { return it }
    val ts = entry.ts
    val updated = NoticeStatusMachine.parseTs(entry.group.updatedAt, zone)?.takeIf { it > 0L }
    return when {
        updated == null -> ts
        ts > 0L -> maxOf(ts, updated)
        else -> updated
    }
}

// -----------------------------------------------------------------------------
// §2.4 统计（顶部 5 张数据卡 / 工作台同口径）
// -----------------------------------------------------------------------------

/** §2.4 统计口径（复用数据层 [NoticeStatusMachine.statsOf]；hidden 行不计）。 */
internal fun buildOverview(
    data: NoticesData?,
    marks: MarksSnapshot,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): NoticeStats {
    if (data == null) return NoticeStats(pending = 0, urgent = 0, week = 0, expired = 0)
    val digests = data.auto
        .filterNot { it.hidden }
        .map { entry ->
            NoticeDigest(
                group = entry.group,
                done = marks.isDone(MarkKind.NT_DONE, entry.id),
                ts = entry.boardPublishedMillis.takeIf { it > 0 }
                    ?: maxOf(NoticeStatusMachine.latestBoardTime(entry.group, nowMillis, zone),
                        NoticeStatusMachine.publishedTsOf(entry.group, entry.items, nowMillis, zone) ?: 0L),
                publishedTs = NoticeStatusMachine.publishedTsOf(entry.group, entry.items, nowMillis, zone) ?: 0L,
            )
        }
    return NoticeStatusMachine.statsOf(
        digests = digests,
        nowMillis = nowMillis,
        zone = zone,
        doneRef = { ref -> marks.isDone(MarkKind.NT_DONE, ref) },
    )
}

// -----------------------------------------------------------------------------
// 分类 / 排序 / 视图分区（契约 §2.2）
// -----------------------------------------------------------------------------

/** 是否待处理（§2.4 + 2026-11-18 §5：未过期、非兜底资料、有时间锚点或发布时间 / 期次窗口内且未处理、未已处理、actionable）。 */
internal fun isPendingItem(item: NoticeUiItem): Boolean {
    if (!item.isAuto) return false
    if (item.expired || item.material) return false
    if (item.recurrence != null) {
        return item.occurrenceKey != null && !item.occurrenceDone
    }
    if (!item.actionable || item.done) return false
    return item.hasTimeAnchor || item.publishedTsMillis > 0L
}

/** 是否主列表条目（§2.2 + 2026-11-18 §3：非过期、非长期定义、有时间锚点或发布时间 + 窗口内未处理的期次卡 + 人工通知）。 */
internal fun isPrimaryItem(item: NoticeUiItem): Boolean {
    if (item.expired || item.material) return false
    if (item.recurrence != null) {
        return item.occurrenceKey != null && !item.occurrenceDone
    }
    if (item.isAuto) return item.hasTimeAnchor || item.publishedTsMillis > 0L
    return true
}

/** 卡片展示模式。 */
internal fun noticeCardModeOf(item: NoticeUiItem): NoticeCardMode = when {
    item.expired -> NoticeCardMode.EXPIRED
    item.occurrenceKey != null && !item.occurrenceDone -> NoticeCardMode.OCCURRENCE
    item.recurrence != null -> NoticeCardMode.LONGTERM_DEF
    else -> NoticeCardMode.NORMAL
}

/**
 * 主列表档位（与网页 `orderRank` / 小程序 `listRankOf` 同口径）：
 * 置顶 -1（安卓人工通知置顶扩展）→ alert 0 → 期次卡 1（按 next 参与，视作 24h 内档）→
 * urgent/today 1 → extended 2 → soon/active 3 → **published / none 4** → 长期定义 5 →
 * 延期已截止 7 → 已截止 8 → 已处理 9。
 */
internal fun noticeRankOf(item: NoticeUiItem): Int {
    if (item.pinned) return -1
    if (item.done) return 9
    if (item.occurrenceKey != null) return 1
    if (item.recurrence != null) return 5
    return when (item.status?.key) {
        "alert" -> 0
        "urgent", "today" -> 1
        "extended" -> 2
        "soon", "active" -> 3
        // 2026-11-18 契约 §4：发布卡排 active 之后；与无时间兜底同档，档内按发布时间倒序
        "published", "none" -> 4
        "extended_past" -> 7
        "expired" -> 8
        else -> 5
    }
}

/**
 * 主列表比较：先档位；**档 4 内按发布时间倒序（新的在上），无发布时间（none 兜底）排最后**；
 * 其余档位按主显示时间（期次按 next）升序、无时间最后，再按发布时间倒序（稳定）。
 */
internal val noticePrimaryComparator: Comparator<NoticeUiItem> = Comparator { a, b ->
    val rankCompare = noticeRankOf(a).compareTo(noticeRankOf(b))
    if (rankCompare != 0) return@Comparator rankCompare
    if (noticeRankOf(a) == 4) {
        val pa = a.publishedTsMillis
        val pb = b.publishedTsMillis
        return@Comparator when {
            pa == pb -> 0
            pa <= 0L -> 1
            pb <= 0L -> -1
            else -> pb.compareTo(pa)
        }
    }
    val wa = a.occurrenceMillis ?: a.whenMillis ?: Long.MAX_VALUE
    val wb = b.occurrenceMillis ?: b.whenMillis ?: Long.MAX_VALUE
    if (wa != wb) return@Comparator wa.compareTo(wb)
    val pa = a.publishedMillis.takeIf { ts -> ts > 0L } ?: a.ts
    val pb = b.publishedMillis.takeIf { ts -> ts > 0L } ?: b.ts
    if (pa != pb) return@Comparator pb.compareTo(pa)
    a.key.compareTo(b.key)
}

/** 已过期 / 资料栏排序：发布时间倒序（稳定）。 */
private val noticePostedDescComparator: Comparator<NoticeUiItem> =
    compareByDescending<NoticeUiItem> { it.publishedMillis.takeIf { ts -> ts > 0L } ?: it.ts }
        .thenBy { it.key }

/** 长期安排清单排序：下次发生时间升序（算不出最后），再按发布时间倒序。 */
internal val noticeLongtermComparator: Comparator<NoticeUiItem> =
    compareBy<NoticeUiItem> { it.recurNextMillis ?: Long.MAX_VALUE }
        .thenByDescending { it.publishedMillis.takeIf { ts -> ts > 0L } ?: it.ts }
        .thenBy { it.key }

/**
 * chips / 顶部 5 卡 / 搜索 → 分区视图。
 *
 * 口径（契约 §2.2）：
 * - 「全部」：主列表（含期次卡）→「资料 / 无时间」栏（常驻、默认展开）→「已过期」区（默认折叠）；
 * - 「待处理」/ 24h / 7 天：只显示主列表（资料栏、长期定义卡不显示；已过期区保持折叠显示）；
 * - 「置顶」：仅置顶人工通知；
 * - 「长期通知」：主区 = 长期安排清单（进行中；周期 / 下次 / 进行中，无 done）；
 * - 「最新发布」：全部未隐藏通知按发布时间倒序（含已过期，过期卡样式仍按过期显示），不分资料/长期区；
 * - 有搜索词时退回平铺列表（标题/摘要匹配，含过期卡），不显示分区头。
 */
internal fun viewContentOf(
    items: List<NoticeUiItem>,
    chipFilter: NoticeFilter = NoticeFilter.ALL,
    cardFilter: NoticeCardFilter? = null,
    query: String = "",
    nowMillis: Long = System.currentTimeMillis(),
    /** 分类 chip 筛选（null/空 = 全部；契约 §2.2「分类（chip）筛选对所有视图生效」）。 */
    category: String? = null,
): NoticesViewContent {
    val keyword = query.trim()
    val activeCategory = category?.trim().orEmpty()
    val visible = items.filter { it.hidden == (chipFilter == NoticeFilter.HIDDEN) }
    val base = if (activeCategory.isEmpty()) visible else visible.filter { it.cat == activeCategory }
    val matched = if (keyword.isEmpty()) {
        base
    } else {
        base.filter {
            it.title.contains(keyword, ignoreCase = true) ||
                it.summary.contains(keyword, ignoreCase = true) ||
                it.manual?.row?.body?.contains(keyword, ignoreCase = true) == true
        }
    }
    val searching = keyword.isNotEmpty()

    // 「最新发布」：全部（含已过期）按发布时间倒序
    if (cardFilter == NoticeCardFilter.LATEST) {
        val latest = matched.filter { it.isAuto }.sortedWith(noticePostedDescComparator)
        return NoticesViewContent(latest = latest, latestOnly = true, totalCount = latest.size)
    }

    // 「长期通知」：进行中的长期安排清单
    if (cardFilter == NoticeCardFilter.LONGTERM) {
        val longterm = matched
            .filter { it.recurrence != null && !it.longtermEnded }
            .sortedWith(noticeLongtermComparator)
        return NoticesViewContent(longterm = longterm, longtermOnly = true, totalCount = longterm.size)
    }

    // 时间窗口筛选（待处理 / 24h / 7 天）：共用「待处理」集合
    val pendingPool = matched.filter { isPendingItem(it) }
    if (cardFilter != null || chipFilter == NoticeFilter.PENDING) {
        val scoped = when (cardFilter) {
            NoticeCardFilter.URGENT -> pendingPool.filter {
                withinWindow(it, nowMillis, DAY_MS)
            }

            NoticeCardFilter.WEEK -> pendingPool.filter {
                withinWindow(it, nowMillis, 7 * DAY_MS)
            }

            else -> pendingPool
        }.sortedWith(noticePrimaryComparator)
        return NoticesViewContent(
            primary = scoped,
            // 「已过期区保持现有折叠显示」（§2.2）；搜索时不显示分区
            expired = if (cardFilter == NoticeCardFilter.PENDING || cardFilter == null) {
                matched.filter { it.expired }.sortedWith(noticePostedDescComparator)
            } else {
                emptyList()
            },
            showExpiredSection = !searching &&
                (cardFilter == null || cardFilter == NoticeCardFilter.PENDING),
            totalCount = scoped.size,
        )
    }

    if (chipFilter == NoticeFilter.EXPIRED || chipFilter == NoticeFilter.HIDDEN) {
        val scoped = matched.filter { chipFilter == NoticeFilter.HIDDEN || it.expired }
            .sortedWith(noticePostedDescComparator)
        return NoticesViewContent(primary = scoped, totalCount = scoped.size)
    }

    // 「置顶」：仅置顶人工通知（沿用现有行为）
    if (chipFilter == NoticeFilter.PINNED) {
        val pinned = matched.filter { it.pinned }.sortedWith(noticePrimaryComparator)
        return NoticesViewContent(primary = pinned, totalCount = pinned.size)
    }

    // 「全部」：主列表 → 资料 / 无时间 → 已过期
    if (searching) {
        val flat = matched.sortedWith(noticePrimaryComparator)
        return NoticesViewContent(primary = flat, totalCount = flat.size)
    }
    val primary = matched.filter { isPrimaryItem(it) }.sortedWith(noticePrimaryComparator)
    val material = matched.filter { it.material }.sortedWith(noticePostedDescComparator)
    val expired = matched.filter { it.expired }.sortedWith(noticePostedDescComparator)
    return NoticesViewContent(
        primary = primary,
        material = material,
        expired = expired,
        // 2026-11-18 契约 §3：资料/无时间栏仅保留兜底卡；正常数据 0 条 → 整栏隐藏
        showMaterial = material.isNotEmpty(),
        showExpiredSection = true,
        totalCount = primary.size,
    )
}

/** 主显示时间是否落在 `[now, now + windowMs]`（期次按 next）。 */
private fun withinWindow(item: NoticeUiItem, nowMillis: Long, windowMs: Long): Boolean {
    val at = item.occurrenceMillis ?: item.whenMillis ?: return false
    return at >= nowMillis && at <= nowMillis + windowMs
}
