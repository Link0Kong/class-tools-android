@file:OptIn(ExperimentalLayoutApi::class)

package com.furina.classtools.ui.screens.notices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.marks.MarkKind
import com.furina.classtools.data.marks.MarksRepository
import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.notices.ManualNotice
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeStatus
import com.furina.classtools.data.notices.NoticeStatusMachine
import com.furina.classtools.data.notices.NoticesData
import com.furina.classtools.data.notices.NoticesLoadResult
import com.furina.classtools.data.notices.NoticesRepository
import com.furina.classtools.data.ppt.PptImageLoader
import com.furina.classtools.data.session.Session
import com.furina.classtools.ui.adaptive.rememberCtAdaptiveSpec
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.EmptyState
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.tool.CtToolCard
import com.furina.classtools.ui.components.tool.CtToolSectionHeader
import com.furina.classtools.ui.components.tool.CtToolStat
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.rememberReduceMotion
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** 通知列表筛选（task 要求：全部/未处理/置顶）。 */
enum class NoticeFilter(val label: String) {
    ALL("全部"),
    PENDING("未处理"),
    PINNED("置顶"),
}

// -----------------------------------------------------------------------------
// UI 模型（数据层 → 卡片/详情渲染；不在 UI 侧做任何分组/合并，避免重复展示）
// -----------------------------------------------------------------------------

/**
 * 卡片语义色（类型/状态色点）。
 *
 * 只用 MaterialTheme 角色派生（浅/深自动跟随，不在页面硬编码色值）：
 * 团务=primary（雾青）/ 教务=secondary（灰蓝）/ 考试·时间=tertiary（暖沙）/ 缴费·紧急=error / 其它=outline。
 */
internal enum class NoticeTone { TEAL, BLUE, SAND, ROSE, GREY }

/** 通知来源类型（卡片来源色点与小圆点语义）。 */
internal enum class NoticeSourceKind { WECHAT, QQ, DOC, UNKNOWN }

/**
 * 通知列表项（自动通知 + 人工通知统一渲染模型）。
 *
 * **去重口径**：UI 严格 1:1 消费 `NoticesRepository` 返回的 `NoticesData.auto/manual`，
 * 不做任何本地分组/合并/去重；同一通知是否重复由数据层（task-38）负责。
 */
internal data class NoticeUiItem(
    val key: String,
    val auto: NoticeEntry? = null,
    val manual: ManualNotice? = null,
    val status: NoticeStatus? = null,
    val done: Boolean = false,
    val pinned: Boolean = false,
    val ts: Long = 0L,
    val title: String = "",
    val summary: String = "",
    val cat: String = "",
    val tone: NoticeTone = NoticeTone.GREY,
    val source: String = "",
    val sourceKind: NoticeSourceKind = NoticeSourceKind.UNKNOWN,
    val senderLine: String = "",
    /** 采集端给出的时间文案（group.timeLabel，如「报名 11-07 12:00」；详情里保留展示）。 */
    val timeLabel: String = "",
    val postedText: String = "",
    val whenMillis: Long? = null,
    val expired: Boolean = false,
    val attachmentCount: Int = 0,
    val mergeCount: Int = 0,
    val edited: Boolean = false,
    val hidden: Boolean = false,
    val recurring: Boolean = false,
) {
    val isAuto: Boolean get() = auto != null
}

/** 简报统计（小程序 nt 页「待处理简报」同口径）。 */
internal data class NoticeBriefStats(
    val pending: Int,
    val urgent: Int,
    val week: Int,
    val expired: Int,
)

private const val DAY_MS = 24L * 60L * 60L * 1000L

/** 数据 → UI 列表（纯函数；只做排序，不做合并/去重）。 */
internal fun buildUiItems(
    data: NoticesData?,
    marks: MarksSnapshot,
    nowMillis: Long,
): List<NoticeUiItem> {
    if (data == null) return emptyList()
    val autoItems = data.auto.map { entry ->
        val status = NoticeStatusMachine.statusOf(entry.group, nowMillis)
        val whenMillis = NoticeStatusMachine.whenInfo(entry.group, nowMillis)?.atMillis
        val summary = entry.group.aiSummary.ifBlank {
            entry.group.summary.ifBlank {
                com.furina.classtools.data.notices.NoticeBodyParser.firstTextOf(entry.items)
            }
        }
        NoticeUiItem(
            key = entry.rowId,
            auto = entry,
            status = status,
            done = marks.isDone(MarkKind.NT_DONE, entry.id),
            pinned = false,
            ts = entry.ts,
            title = entry.title,
            summary = summary.trim(),
            cat = entry.cat,
            tone = toneOfCat(entry.cat),
            source = entry.group.source,
            sourceKind = sourceKindOf(entry.group.source),
            senderLine = listOf(entry.group.senderRole, entry.group.senderName)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            timeLabel = entry.group.timeLabel,
            postedText = formatPosted(entry.ts, nowMillis),
            whenMillis = whenMillis,
            expired = status.expired,
            attachmentCount = com.furina.classtools.data.notices.NoticeBodyParser.attachmentCount(entry.items),
            mergeCount = entry.group.mergedSources.size,
            edited = entry.edited,
            hidden = entry.hidden,
            recurring = entry.group.recurrence != null,
        )
    }
    val manualItems = data.manual.map { notice ->
        val pinned = marks.isDone(MarkKind.NT, notice.id)
        NoticeUiItem(
            key = notice.id,
            manual = notice,
            pinned = pinned,
            ts = notice.ts,
            title = notice.row.title.ifBlank { "班级通知" },
            summary = notice.row.body.trim(),
            cat = "班级通知",
            tone = NoticeTone.GREY,
            postedText = formatPosted(notice.ts, nowMillis),
            attachmentCount = notice.attachments.size,
        )
    }
    // 置顶优先 → 未处理在前 → 时间倒序；人工/自动统一按时间交错（与小程序列表一致）。
    return (manualItems + autoItems).sortedWith(
        compareBy<NoticeUiItem> { if (it.pinned) 0 else 1 }
            .thenBy { if (it.done) 1 else 0 }
            .thenByDescending { it.ts },
    )
}

internal fun buildBriefStats(items: List<NoticeUiItem>, nowMillis: Long): NoticeBriefStats {
    val in24h = nowMillis + DAY_MS
    val in7d = nowMillis + 7 * DAY_MS
    val pending = items.filter { it.isAuto && !it.done && !it.expired }
    val pendingWhen = pending.mapNotNull { it.whenMillis }.filter { it > nowMillis }
    return NoticeBriefStats(
        pending = pending.size,
        urgent = pendingWhen.count { it <= in24h },
        week = pendingWhen.count { it <= in7d },
        expired = items.count { it.isAuto && it.expired },
    )
}

/** 分类 → 语义色（与小程序 nt 页「教务雾蓝 / 团务睡莲青 / 考试暖沙 / 缴费灰粉 / 其它灰」同口径）。 */
internal fun toneOfCat(cat: String): NoticeTone {
    val text = cat.trim()
    if (text.isEmpty()) return NoticeTone.GREY
    return when {
        text.contains("团") || text.contains("党") || text.contains("组织") -> NoticeTone.TEAL
        text.contains("教务") || text.contains("教学") || text.contains("课程") ||
            text.contains("学籍") || text.contains("成绩") -> NoticeTone.BLUE
        text.contains("考试") || text.contains("报名") || text.contains("时间") ||
            text.contains("活动") || text.contains("会议") -> NoticeTone.SAND
        text.contains("缴费") || text.contains("紧急") || text.contains("安全") ||
            text.contains("规则") || text.contains("提醒") -> NoticeTone.ROSE
        else -> NoticeTone.GREY
    }
}

/** 来源字符串 → 来源类型（微信 / QQ / 文档 / 未知）。 */
internal fun sourceKindOf(source: String): NoticeSourceKind = when {
    source.contains("QQ", ignoreCase = true) -> NoticeSourceKind.QQ
    source.contains("微信", ignoreCase = true) || source.contains("wx", ignoreCase = true) ||
        source.contains("群", ignoreCase = true) -> NoticeSourceKind.WECHAT
    source.isNotBlank() -> NoticeSourceKind.DOC
    else -> NoticeSourceKind.UNKNOWN
}

/** 语义色三元组（点 / 容器底 / 容器字）。 */
internal data class NoticeToneColors(
    val dot: Color,
    val container: Color,
    val content: Color,
)

@Composable
internal fun toneColors(tone: NoticeTone): NoticeToneColors {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        NoticeTone.TEAL -> NoticeToneColors(scheme.primary, scheme.primaryContainer, scheme.onPrimaryContainer)
        NoticeTone.BLUE -> NoticeToneColors(scheme.secondary, scheme.secondaryContainer, scheme.onSecondaryContainer)
        NoticeTone.SAND -> NoticeToneColors(scheme.tertiary, scheme.tertiaryContainer, scheme.onTertiaryContainer)
        NoticeTone.ROSE -> NoticeToneColors(scheme.error, scheme.errorContainer, scheme.onErrorContainer)
        NoticeTone.GREY -> NoticeToneColors(scheme.outline, scheme.surfaceContainerHigh, scheme.onSurfaceVariant)
    }
}

@Composable
internal fun statusTone(status: NoticeStatus): NoticeTone = when (status.cls) {
    "urgent", "alert" -> NoticeTone.ROSE
    "expired" -> NoticeTone.GREY
    "extended" -> NoticeTone.SAND
    "recurring" -> NoticeTone.BLUE
    else -> NoticeTone.TEAL
}

/** `MM-dd HH:mm`（详情里的绝对时间）。 */
internal fun formatTs(ts: Long): String {
    if (ts <= 0) return ""
    return DateTimeFormatter
        .ofPattern("MM-dd HH:mm", Locale.CHINA)
        .format(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()))
}

/** 列表卡片上的「什么时候发的」：今天 → HH:mm；昨天 → 昨天 HH:mm；今年 → MM-dd；更早 → yyyy-MM-dd。 */
internal fun formatPosted(ts: Long, nowMillis: Long = System.currentTimeMillis()): String {
    if (ts <= 0) return ""
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    val at = Instant.ofEpochMilli(ts).atZone(zone)
    return when {
        date == today -> clock.format(at)
        date == today.minusDays(1) -> "昨天 ${clock.format(at)}"
        date.year == today.year -> DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA).format(at)
        else -> DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA).format(at)
    }
}

/**
 * 通知页（原生只读渲染，2026-11-12 以小程序 nt 页为基准重做）。
 *
 * 数据：`NoticesRepository.load()` 两级拉取（轻量签名 → 变化才拉整表），
 * `[[CLASS_NOTICE_V1]]` 解析、hidden 过滤、override 白名单、附件元信息。
 * 已处理 = `marks kind='ntdone'`（ref = 通知组 id）；置顶 = `marks kind='nt'`（仅人工通知有效）。
 *
 * 信息架构（对齐小程序 `pages/nt`）：
 * - 顶部：标题 + 待处理简报（待处理 / 24h 截止 / 7 天内 / 已过期）；
 * - 筛选 chips（全部 / 未处理 / 置顶）+ 搜索（标题 / 摘要，点顶栏放大镜展开）；
 * - 列表：分类色块 + 状态色点 + 标题 + 摘要 2 行 + 来源/附件/合并/已更新标记 +
 *   「标记已处理」/「置顶」操作；「已过期」默认折叠成一段（可展开）；
 * - 详情：底部弹层展示 AI 摘要 / 字段分段 / 图片网格 / 附件 / 链接 / 合并消息 / 人工通知正文，
 *   图片附件支持缩略图 + 全屏大图；未知附件统一「暂不支持预览」；
 * - 四态：骨架加载 / 空态 / 错误重试 / 离线缓存提示 + 下拉刷新。
 *
 * **不显示重复通知**：本页只消费数据层返回结果，UI 侧没有任何本地分组/合并逻辑。
 *
 * 接线（Lead 负责 MainNavHost）：
 * ```kotlin
 * "nt" -> NoticesScreen(
 *     onBack = { navController.popBackStack() },
 *     session = session,
 *     noticesRepository = container.noticesRepository,
 *     marksRepository = container.marksRepository,
 * )
 * ```
 *
 * @param rawAttachmentLoader 图片附件按需加载器（Lead 接线传
 *   `ApiNoticeRawAttachmentLoader(container.apiClient)`；为 null 时降级为元信息占位）
 * @param imageLoader 图片附件网络加载器（外置签名 URL 形态；传入 `container.pptImageLoader` 复用
 *   `data/ppt` 的 OkHttp + BitmapFactory + LRU 实现；为 null 时用内置单例兜底）
 * @param initialData 预览/测试注入数据（跳过网络）
 * @param initialMarks 预览/测试注入 marks
 * @param initialFilter 预览/测试注入初始筛选
 * @param initialQuery 预览/测试注入初始搜索词（非空时自动展开搜索栏）
 * @param initialExpiredExpanded 预览/测试注入「已过期」分组是否展开
 * @param initialLoading 预览/测试：直接停在骨架加载态
 * @param forcedErrorMessage 预览/测试：直接进入错误态（未注入数据时）
 * @param skipLoad 预览/测试：跳过加载与刷新
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticesScreen(
    onBack: () -> Unit,
    session: Session?,
    marksRepository: MarksRepository?,
    noticesRepository: NoticesRepository?,
    rawAttachmentLoader: NoticeRawAttachmentLoader? = null,
    /** v3 附件外置：图片 https 签名 URL 加载器（复用 data/ppt 实现，不引入新依赖）。 */
    imageLoader: PptImageLoader? = null,
    /** v2：把有明确时间的结构化通知写入系统日历（null = 不显示入口）。 */
    onAddToCalendar: ((NoticeGroupDto) -> Unit)? = null,
    /** v3：单条通知提醒（Lead 弹出提醒时间选项并排程）。 */
    onRemindNotice: ((NoticeGroupDto) -> Unit)? = null,
    modifier: Modifier = Modifier,
    initialData: NoticesData? = null,
    initialMarks: MarksSnapshot? = null,
    initialFilter: NoticeFilter = NoticeFilter.ALL,
    initialQuery: String = "",
    initialExpiredExpanded: Boolean = false,
    initialLoading: Boolean = false,
    /** 预览：列表错峰入场帧（0..1；null = 正常动画）。 */
    previewEntranceProgress: Float? = null,
    /** 预览：强制指定通知卡处于按压态（NoticeUiItem.key）。 */
    previewPressedKey: String? = null,
    /** 预览：强制指定通知卡处于「点开延伸」态（NoticeUiItem.key）。 */
    previewExpandedKey: String? = null,
    /** 预览：强制指定筛选 chip 处于弹性放大帧（NoticeFilter.name）。 */
    previewChipBounceKey: String? = null,
    /** 预览：左右滑动中间帧（0.5 = 停在两页之间；null = 正常渲染）。 */
    previewPagerOffset: Float? = null,
    forcedErrorMessage: String? = null,
    skipLoad: Boolean = false,
) {
    val spec = rememberCtAdaptiveSpec()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    // 动效：系统「移除动画」时全部退化为无动画；预览注入动画帧时按注入态渲染（不看系统开关）。
    val systemReduceMotion = rememberReduceMotion()
    val reduceMotion = systemReduceMotion &&
        previewEntranceProgress == null &&
        previewPressedKey == null &&
        previewExpandedKey == null &&
        previewChipBounceKey == null
    // 已入场 key（跨滚动/重组持久，避免滚回来重复播放错峰入场）
    val appearedKeys = remember { mutableStateMapOf<String, Boolean>() }
    val fallbackMarks = remember { MutableStateFlow(initialMarks ?: MarksSnapshot()) }
    val marksFlow = remember(marksRepository, initialMarks) { marksRepository?.marks ?: fallbackMarks }
    val marks: MarksSnapshot by marksFlow.collectAsState()

    var data by remember { mutableStateOf(initialData) }
    var phase by remember {
        mutableStateOf(
            when {
                initialData != null -> Phase.READY
                forcedErrorMessage != null -> Phase.FAILED
                initialLoading -> Phase.LOADING
                skipLoad -> Phase.READY
                else -> Phase.LOADING
            },
        )
    }
    var errorMessage by remember { mutableStateOf(forcedErrorMessage) }
    var isRefreshing by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var loadMoreError by remember { mutableStateOf<String?>(null) }
    var filterKey by rememberSaveable { mutableStateOf(initialFilter.name) }
    val noticeFilterOptions = NoticeFilter.entries
    // 左右滑动切筛选（跟手 + spring 吸附；reduceMotion 时禁用拖拽并直接用总开关的瞬时切换）
    val noticePagerState = rememberPagerState(
        initialPage = noticeFilterOptions.indexOf(initialFilter).coerceAtLeast(0),
        initialPageOffsetFraction = (previewPagerOffset ?: 0f).coerceIn(-0.5f, 0.5f),
    ) { noticeFilterOptions.size }
    LaunchedEffect(noticePagerState) {
        snapshotFlow { noticePagerState.currentPage }.collect { page ->
            noticeFilterOptions.getOrNull(page)?.let { filterKey = it.name }
        }
    }
    val filter = remember(filterKey) {
        noticeFilterOptions.firstOrNull { it.name == filterKey } ?: NoticeFilter.ALL
    }
    fun selectFilter(target: NoticeFilter) {
        filterKey = target.name
        val index = noticeFilterOptions.indexOf(target).coerceAtLeast(0)
        scope.launch {
            // reduceMotion：直接切；正常：250–300ms spring 吸附
            if (reduceMotion) noticePagerState.scrollToPage(index) else noticePagerState.animateScrollToPage(index)
        }
    }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var searchVisible by rememberSaveable { mutableStateOf(initialQuery.isNotBlank()) }
    var expiredExpanded by rememberSaveable { mutableStateOf(initialExpiredExpanded) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    suspend fun applyResult(result: NoticesLoadResult) {
        when (result) {
            is NoticesLoadResult.Success -> {
                data = result.data
                errorMessage = null
                phase = Phase.READY
            }

            is NoticesLoadResult.Failure -> {
                errorMessage = result.message
                phase = if (data == null) Phase.FAILED else Phase.READY
            }
        }
    }

    LaunchedEffect(reloadToken) {
        if (skipLoad || initialLoading) return@LaunchedEffect
        if (session == null) {
            errorMessage = "请先完成身份验证后再查看通知"
            phase = Phase.FAILED
            return@LaunchedEffect
        }
        val repository = noticesRepository
        if (repository == null) {
            phase = Phase.READY
            return@LaunchedEffect
        }
        if (data == null) phase = Phase.LOADING
        applyResult(repository.load(force = reloadToken > 0))
        runCatching { marksRepository?.refresh() }
    }

    // 状态文案（「N 分钟后 / 24h 截止」）随时间自然走，页面停留时每分钟刷新一次。
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMillis = System.currentTimeMillis()
        }
    }

    fun refresh() {
        if (skipLoad) return
        scope.launch {
            isRefreshing = true
            try {
                noticesRepository?.let { applyResult(it.load(force = true)) }
                runCatching { marksRepository?.refresh(force = true) }
            } finally {
                isRefreshing = false
            }
        }
    }

    /** 「加载更多」：按 ts 倒序追加下一页；失败时保留已加载内容并展示重试。 */
    fun loadMore() {
        if (skipLoad || isLoadingMore) return
        val repository = noticesRepository ?: return
        scope.launch {
            isLoadingMore = true
            loadMoreError = null
            try {
                when (val result = repository.loadMore()) {
                    is NoticesLoadResult.Success -> {
                        data = result.data
                        errorMessage = null
                    }

                    is NoticesLoadResult.Failure -> loadMoreError = result.message
                }
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun toggleDone(item: NoticeUiItem) {
        val repository = marksRepository ?: run {
            notify("未登录，无法同步处理状态")
            return
        }
        val entry = item.auto ?: return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch {
            when (repository.setMark(MarkKind.NT_DONE, entry.id, !item.done)) {
                is com.furina.classtools.data.marks.MarksUpdateResult.Success ->
                    notify(if (!item.done) "已标记处理" else "已恢复未处理")

                is com.furina.classtools.data.marks.MarksUpdateResult.Failure ->
                    notify("同步失败，稍后自动重试")
            }
        }
    }

    fun togglePin(item: NoticeUiItem) {
        val repository = marksRepository ?: run {
            notify("未登录，无法同步置顶状态")
            return
        }
        val notice = item.manual ?: return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch {
            when (repository.setMark(MarkKind.NT, notice.id, !item.pinned)) {
                is com.furina.classtools.data.marks.MarksUpdateResult.Success ->
                    notify(if (!item.pinned) "已置顶" else "已取消置顶")

                is com.furina.classtools.data.marks.MarksUpdateResult.Failure ->
                    notify("同步失败，稍后自动重试")
            }
        }
    }

    val allItems = remember(data, marks, nowMillis) { buildUiItems(data, marks, nowMillis) }

    val pendingCount = allItems.count { it.isAuto && !it.done && !it.expired }
    val pinnedCount = allItems.count { it.pinned }
    val briefStats = remember(allItems, nowMillis) { buildBriefStats(allItems, nowMillis) }
    val selectedItem = remember(allItems, selectedKey) {
        selectedKey?.let { key -> allItems.firstOrNull { it.key == key } }
    }
    val twoColumn = spec.widthDp >= 600

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                CtTopBar(
                    title = "班级通知",
                    subtitle = buildSubtitle(
                        allItemsCount = allItems.size,
                        pending = pendingCount,
                        hiddenCount = data?.hiddenCount ?: 0,
                    ),
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) query = ""
                        }) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = if (searchVisible) "关闭搜索" else "搜索通知",
                                tint = if (searchVisible) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        IconButton(onClick = { refresh() }) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "刷新通知",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = spec.contentMaxWidth)
                            .fillMaxSize()
                            .padding(horizontal = spec.horizontalPadding),
                    ) {
                        if (phase != Phase.FAILED && allItems.isNotEmpty()) {
                            NoticeBriefing(
                                stats = briefStats,
                                reduceMotion = reduceMotion,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }

                        NoticeFilterChips(
                            chips = listOf(
                                NoticeChip(
                                    NoticeFilter.ALL.name,
                                    "全部 " + animatedStatText(allItems.size, reduceMotion),
                                ),
                                NoticeChip(
                                    NoticeFilter.PENDING.name,
                                    "未处理 " + animatedStatText(pendingCount, reduceMotion),
                                ),
                                NoticeChip(
                                    NoticeFilter.PINNED.name,
                                    "置顶 " + animatedStatText(pinnedCount, reduceMotion),
                                ),
                            ),
                            selectedKey = filter.name,
                            onSelect = { key ->
                                NoticeFilter.entries.firstOrNull { it.name == key }?.let { selectFilter(it) }
                            },
                            reduceMotion = reduceMotion,
                            modifier = Modifier.padding(top = 2.dp),
                            previewBounceKey = previewChipBounceKey,
                        )

                        AnimatedVisibility(
                            visible = searchVisible,
                            enter = if (reduceMotion) {
                                EnterTransition.None
                            } else {
                                expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(160))
                            },
                            exit = if (reduceMotion) {
                                ExitTransition.None
                            } else {
                                shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(110))
                            },
                        ) {
                            NoticeSearchField(
                                query = query,
                                onQueryChange = { query = it },
                                onClose = {
                                    query = ""
                                    searchVisible = false
                                },
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }

                        val offline = data?.fromCache == true || (errorMessage != null && data != null)
                        AnimatedVisibility(
                            visible = offline && phase != Phase.FAILED,
                            enter = if (reduceMotion) {
                                EnterTransition.None
                            } else {
                                expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(160))
                            },
                            exit = if (reduceMotion) {
                                ExitTransition.None
                            } else {
                                shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(110))
                            },
                        ) {
                            NoticeOfflineBanner(
                                message = errorMessage?.let { "刷新失败，显示本机缓存" }
                                    ?: ("本机缓存 · 更新于 ${formatTs(data?.fetchedAt ?: 0L)}"),
                                onRetry = { refresh() },
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            when {
                                phase == Phase.LOADING -> NoticeSkeletonList(
                                    modifier = Modifier.fillMaxSize(),
                                )

                                phase == Phase.FAILED -> NoticeErrorState(
                                    message = errorMessage ?: "加载失败",
                                    onRetry = { reloadToken += 1 },
                                    modifier = Modifier.fillMaxSize(),
                                )

                                else -> HorizontalPager(
                                    state = noticePagerState,
                                    modifier = Modifier.fillMaxSize(),
                                    userScrollEnabled = !reduceMotion,
                                    key = { page -> noticeFilterOptions[page].name },
                                ) { pageIndex ->
                                    val pageFilter = noticeFilterOptions[pageIndex]
                                    val pageVisible = remember(allItems, pageFilter, query) {
                                        val base = when (pageFilter) {
                                            NoticeFilter.ALL -> allItems
                                            NoticeFilter.PENDING -> allItems.filter { it.isAuto && !it.done && !it.expired }
                                            NoticeFilter.PINNED -> allItems.filter { it.pinned }
                                        }
                                        val q = query.trim()
                                        if (q.isBlank()) {
                                            base
                                        } else {
                                            base.filter {
                                                it.title.contains(q, ignoreCase = true) ||
                                                    it.summary.contains(q, ignoreCase = true)
                                            }
                                        }
                                    }
                                    val pageExpiredSection = pageFilter == NoticeFilter.ALL && query.isBlank()
                                    val pagePrimary = remember(pageVisible, pageExpiredSection) {
                                        if (pageExpiredSection) pageVisible.filter { !it.expired } else pageVisible
                                    }
                                    val pageExpired = remember(pageVisible, pageExpiredSection) {
                                        if (pageExpiredSection) pageVisible.filter { it.expired } else emptyList()
                                    }
                                    val hasMore = data?.hasMore == true
                                    val remaining = data?.remainingCount ?: 0
                                    PullToRefreshBox(
                                        isRefreshing = isRefreshing,
                                        onRefresh = { refresh() },
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        val listEmpty = pagePrimary.isEmpty() && pageExpired.isEmpty()
                                        if (listEmpty) {
                                            // 放进 LazyColumn 以保留下拉刷新（空态也能刷新 / 加载更多）
                                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                item(key = "empty") {
                                                    NoticeEmptyState(
                                                        filter = pageFilter,
                                                        query = query,
                                                        hasAny = allItems.isNotEmpty(),
                                                        onClear = {
                                                            query = ""
                                                            selectFilter(NoticeFilter.ALL)
                                                        },
                                                        modifier = Modifier.fillParentMaxSize(),
                                                    )
                                                }
                                                if (hasMore || loadMoreError != null) {
                                                    item(key = "load-more") {
                                                        NoticeLoadMoreRow(
                                                            remainingCount = remaining,
                                                            loading = isLoadingMore,
                                                            error = loadMoreError,
                                                            onLoadMore = { loadMore() },
                                                        )
                                                    }
                                                }
                                            }
                                        } else if (twoColumn) {
                                            NoticeGrid(
                                                items = pagePrimary,
                                                expiredItems = if (expiredExpanded) pageExpired else emptyList(),
                                                expiredCount = pageExpired.size,
                                                expiredExpanded = expiredExpanded,
                                                onToggleExpired = { expiredExpanded = !expiredExpanded },
                                                onOpen = { selectedKey = it.key },
                                                onToggleDone = { toggleDone(it) },
                                                onTogglePin = { togglePin(it) },
                                                reduceMotion = reduceMotion,
                                                appearedKeys = appearedKeys,
                                                selectedKey = selectedKey,
                                                previewEntranceProgress = previewEntranceProgress,
                                                previewPressedKey = previewPressedKey,
                                                previewExpandedKey = previewExpandedKey,
                                                hasMore = hasMore,
                                                remainingCount = remaining,
                                                loadingMore = isLoadingMore,
                                                loadMoreError = loadMoreError,
                                                onLoadMore = { loadMore() },
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        } else {
                                            NoticeList(
                                                items = pagePrimary,
                                                expiredItems = if (expiredExpanded) pageExpired else emptyList(),
                                                expiredCount = pageExpired.size,
                                                expiredExpanded = expiredExpanded,
                                                onToggleExpired = { expiredExpanded = !expiredExpanded },
                                                onOpen = { selectedKey = it.key },
                                                onToggleDone = { toggleDone(it) },
                                                onTogglePin = { togglePin(it) },
                                                reduceMotion = reduceMotion,
                                                appearedKeys = appearedKeys,
                                                selectedKey = selectedKey,
                                                previewEntranceProgress = previewEntranceProgress,
                                                previewPressedKey = previewPressedKey,
                                                previewExpandedKey = previewExpandedKey,
                                                hasMore = hasMore,
                                                remainingCount = remaining,
                                                loadingMore = isLoadingMore,
                                                loadMoreError = loadMoreError,
                                                onLoadMore = { loadMore() },
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            )
        }
    }

    selectedItem?.let { item ->
        NoticeDetailSheet(
            item = item,
            rawAttachmentLoader = rawAttachmentLoader,
            imageLoader = imageLoader,
            onAddToCalendar = onAddToCalendar,
            onRemindNotice = onRemindNotice,
            onDismiss = { selectedKey = null },
            onToggleDone = { toggleDone(item) },
            onTogglePin = { togglePin(item) },
        )
    }
}

private fun buildSubtitle(
    allItemsCount: Int,
    pending: Int,
    hiddenCount: Int,
): String = buildString {
    if (allItemsCount > 0) {
        append("未处理 ")
        append(pending)
        append(" / 共 ")
        append(allItemsCount)
    }
    if (hiddenCount > 0) {
        if (isNotEmpty()) append(" · ")
        append("已隐藏 ")
        append(hiddenCount)
    }
}

// -----------------------------------------------------------------------------
// 顶部：简报 / 搜索
// -----------------------------------------------------------------------------

/** 待处理简报（v6：CtToolCard + CtToolStat；数字保留逐帧滚动，移除旧玻璃卡）。 */
@Composable
private fun NoticeBriefing(
    stats: NoticeBriefStats,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    CtToolCard(modifier = modifier, title = "待处理简报") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CtToolStat(
                value = animatedStatText(stats.pending, reduceMotion),
                label = "待处理",
                valueColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            CtToolStat(
                value = animatedStatText(stats.urgent, reduceMotion),
                label = "24h 截止",
                valueColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            CtToolStat(
                value = animatedStatText(stats.week, reduceMotion),
                label = "7 天内",
                valueColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
            )
            CtToolStat(
                value = animatedStatText(stats.expired, reduceMotion),
                label = "已过期",
                valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 数字滚动文本（简报/筛选计数）：reduceMotion 直接终值；切换时逐帧刷新，保留「有生命感」的计数。 */
@Composable
internal fun animatedStatText(value: Int, reduceMotion: Boolean): String {
    if (reduceMotion) return value.toString()
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = CtMotion.effects,
        label = "noticeStatValue",
    )
    return animated.roundToInt().toString()
}
/** 搜索栏（点顶栏放大镜展开；标题/摘要匹配，48dp 触控高度）。 */
@Composable
private fun NoticeSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    // 等展开动画完成首帧布局后再聚焦，避免 AnimatedVisibility 中节点未 attach 导致聚焦失败
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focusRequester.requestFocus() }
    }
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = query,
        onValueChange = { onQueryChange(it.take(40)) },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭搜索")
            }
        },
        placeholder = { Text("搜索通知标题 / 摘要") },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaceContainerHigh,
            unfocusedContainerColor = scheme.surfaceContainerHigh.copy(alpha = 0.72f),
            focusedBorderColor = scheme.primary.copy(alpha = 0.55f),
            unfocusedBorderColor = Color.Transparent,
        ),
    )
}

/** 离线/缓存提示条（48dp 触控；带「重试」）。 */
@Composable
private fun NoticeOfflineBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("重试")
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 列表 / 空态 / 错误态 / 骨架
// -----------------------------------------------------------------------------

@Composable
private fun NoticeList(
    items: List<NoticeUiItem>,
    expiredItems: List<NoticeUiItem>,
    expiredCount: Int,
    expiredExpanded: Boolean,
    onToggleExpired: () -> Unit,
    onOpen: (NoticeUiItem) -> Unit,
    onToggleDone: (NoticeUiItem) -> Unit,
    onTogglePin: (NoticeUiItem) -> Unit,
    reduceMotion: Boolean,
    appearedKeys: SnapshotStateMap<String, Boolean>,
    selectedKey: String?,
    previewEntranceProgress: Float?,
    previewPressedKey: String?,
    previewExpandedKey: String?,
    hasMore: Boolean = false,
    remainingCount: Int = 0,
    loadingMore: Boolean = false,
    loadMoreError: String? = null,
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(items = items, key = { _, item -> "c-${item.key}" }) { index, item ->
            NoticeEntrance(
                itemKey = item.key,
                index = index,
                reduceMotion = reduceMotion,
                appearedKeys = appearedKeys,
                previewBaseProgress = previewEntranceProgress,
                modifier = this.noticeAnimateItem(reduceMotion),
            ) {
                NoticeCard(
                    item = item,
                    reduceMotion = reduceMotion,
                    expanded = selectedKey == item.key || previewExpandedKey == item.key,
                    previewPressed = previewPressedKey == item.key,
                    onOpen = { onOpen(item) },
                    onToggleDone = { onToggleDone(item) },
                    onTogglePin = { onTogglePin(item) },
                )
            }
        }
        if (expiredCount > 0) {
            item(key = "expired-header") {
                ExpiredHeader(
                    count = expiredCount,
                    expanded = expiredExpanded,
                    onToggle = onToggleExpired,
                    reduceMotion = reduceMotion,
                    modifier = this.noticeAnimateItem(reduceMotion),
                )
            }
        }
        if (expiredExpanded) {
            itemsIndexed(items = expiredItems, key = { _, item -> "e-${item.key}" }) { index, item ->
                NoticeEntrance(
                    itemKey = "e-${item.key}",
                    index = items.size + 1 + index,
                    reduceMotion = reduceMotion,
                    appearedKeys = appearedKeys,
                    previewBaseProgress = previewEntranceProgress,
                    modifier = this.noticeAnimateItem(reduceMotion),
                ) {
                    NoticeCard(
                        item = item,
                        reduceMotion = reduceMotion,
                        expanded = selectedKey == item.key || previewExpandedKey == item.key,
                        previewPressed = previewPressedKey == item.key,
                        onOpen = { onOpen(item) },
                        onToggleDone = { onToggleDone(item) },
                        onTogglePin = { onTogglePin(item) },
                    )
                }
            }
        }
        if (hasMore || loadMoreError != null) {
            item(key = "load-more") {
                NoticeLoadMoreRow(
                    remainingCount = remainingCount,
                    loading = loadingMore,
                    error = loadMoreError,
                    onLoadMore = onLoadMore,
                )
            }
        }
    }
}

@Composable
private fun NoticeGrid(
    items: List<NoticeUiItem>,
    expiredItems: List<NoticeUiItem>,
    expiredCount: Int,
    expiredExpanded: Boolean,
    onToggleExpired: () -> Unit,
    onOpen: (NoticeUiItem) -> Unit,
    onToggleDone: (NoticeUiItem) -> Unit,
    onTogglePin: (NoticeUiItem) -> Unit,
    reduceMotion: Boolean,
    appearedKeys: SnapshotStateMap<String, Boolean>,
    selectedKey: String?,
    previewEntranceProgress: Float?,
    previewPressedKey: String?,
    previewExpandedKey: String?,
    hasMore: Boolean = false,
    remainingCount: Int = 0,
    loadingMore: Boolean = false,
    loadMoreError: String? = null,
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(items = items, key = { _, item -> "c-${item.key}" }) { index, item ->
            NoticeEntrance(
                itemKey = item.key,
                index = index,
                reduceMotion = reduceMotion,
                appearedKeys = appearedKeys,
                previewBaseProgress = previewEntranceProgress,
                modifier = this.noticeAnimateItem(reduceMotion),
            ) {
                NoticeCard(
                    item = item,
                    reduceMotion = reduceMotion,
                    expanded = selectedKey == item.key || previewExpandedKey == item.key,
                    previewPressed = previewPressedKey == item.key,
                    onOpen = { onOpen(item) },
                    onToggleDone = { onToggleDone(item) },
                    onTogglePin = { onTogglePin(item) },
                )
            }
        }
        if (expiredCount > 0) {
            item(key = "expired-header", span = { GridItemSpan(maxLineSpan) }) {
                ExpiredHeader(
                    count = expiredCount,
                    expanded = expiredExpanded,
                    onToggle = onToggleExpired,
                    reduceMotion = reduceMotion,
                    modifier = this.noticeAnimateItem(reduceMotion),
                )
            }
        }
        if (expiredExpanded) {
            itemsIndexed(items = expiredItems, key = { _, item -> "e-${item.key}" }) { index, item ->
                NoticeEntrance(
                    itemKey = "e-${item.key}",
                    index = items.size + 1 + index,
                    reduceMotion = reduceMotion,
                    appearedKeys = appearedKeys,
                    previewBaseProgress = previewEntranceProgress,
                    modifier = this.noticeAnimateItem(reduceMotion),
                ) {
                    NoticeCard(
                        item = item,
                        reduceMotion = reduceMotion,
                        expanded = selectedKey == item.key || previewExpandedKey == item.key,
                        previewPressed = previewPressedKey == item.key,
                        onOpen = { onOpen(item) },
                        onToggleDone = { onToggleDone(item) },
                        onTogglePin = { onTogglePin(item) },
                    )
                }
            }
        }
        if (hasMore || loadMoreError != null) {
            item(key = "load-more", span = { GridItemSpan(maxLineSpan) }) {
                NoticeLoadMoreRow(
                    remainingCount = remainingCount,
                    loading = loadingMore,
                    error = loadMoreError,
                    onLoadMore = onLoadMore,
                )
            }
        }
    }
}

/** 「加载更多」行（分页：最新 20 + 旧数据按 20 条/页追加）。触控 ≥48dp。 */
@Composable
private fun NoticeLoadMoreRow(
    remainingCount: Int,
    loading: Boolean,
    error: String?,
    onLoadMore: () -> Unit,
) {
    Surface(
        onClick = { if (!loading) onLoadMore() },
        enabled = !loading,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            when {
                loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("加载中…", style = MaterialTheme.typography.labelLarge)
                }

                error != null -> Text(
                    text = "$error（点此重试）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                remainingCount > 0 -> Text(
                    text = "加载更多（还有 $remainingCount 条）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )

                else -> Text(
                    text = "加载更多",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 已过期分组头（v6：CtToolSectionHeader + 整行可点；箭头旋转动画保留）。 */
@Composable
private fun ExpiredHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.pressSpring,
        label = "expiredArrow",
    )
    CtToolSectionHeader(
        title = "已过期",
        subtitle = "$count 组",
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onToggle)
            .heightIn(min = 52.dp),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = arrowRotation },
                )
            }
        },
    )
}

// -----------------------------------------------------------------------------
// 卡片
// -----------------------------------------------------------------------------

@Composable
private fun NoticeCard(
    item: NoticeUiItem,
    onOpen: () -> Unit,
    onToggleDone: () -> Unit,
    onTogglePin: () -> Unit,
    reduceMotion: Boolean = false,
    expanded: Boolean = false,
    previewPressed: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    // 已处理：标题色/删除线过渡（位置变化由 Lazy animateItem 负责）；卡片底色按 v6 统一 CtToolCard
    val doneProgress by animateFloatAsState(
        targetValue = if (item.done) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.crossFade,
        label = "noticeCardDone",
    )
    val titleColor by animateColorAsState(
        targetValue = if (item.done) scheme.onSurfaceVariant else scheme.onSurface,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.colorSpec,
        label = "noticeCardTitle",
    )
    NoticePressableCard(
        onClick = onOpen,
        reduceMotion = reduceMotion,
        contentDescription = item.title.ifBlank { "通知" },
        expanded = expanded,
        previewPressed = previewPressed,
        pinned = item.pinned,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NoticeCatPill(cat = item.cat, tone = item.tone)
                if (item.status != null) {
                    Spacer(Modifier.width(8.dp))
                    StatusDotLine(
                        status = item.status,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.postedText,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                textDecoration = if (doneProgress > 0.45f) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (item.summary.isNotBlank()) {
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            NoticeCardMeta(item = item)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.isAuto) {
                    DonePill(done = item.done, reduceMotion = reduceMotion, onClick = onToggleDone)
                } else {
                    PinPill(pinned = item.pinned, reduceMotion = reduceMotion, onClick = onTogglePin)
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "详情",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** 卡片第三行：来源 / 发送者 / 附件数 / 多源合并 / 长期安排 / 已更新 / 已隐藏（可换行，大字体不截断）。 */
@Composable
private fun NoticeCardMeta(item: NoticeUiItem) {
    val scheme = MaterialTheme.colorScheme
    val sourceColors = toneColors(
        when (item.sourceKind) {
            NoticeSourceKind.WECHAT -> NoticeTone.TEAL
            NoticeSourceKind.QQ -> NoticeTone.BLUE
            NoticeSourceKind.DOC, NoticeSourceKind.UNKNOWN -> NoticeTone.GREY
        },
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (item.source.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(sourceColors.dot, CircleShape),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = item.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item.senderLine.isNotBlank()) {
            Text(
                text = item.senderLine,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.recurring) {
            ItemFlag(text = "长期安排", tone = NoticeTone.BLUE)
        }
        if (item.attachmentCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AttachFile,
                    contentDescription = "附件 ${item.attachmentCount} 个",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = item.attachmentCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (item.mergeCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Hub,
                    contentDescription = "多源合并 ${item.mergeCount} 个来源",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = "多源",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (item.edited) ItemFlag(text = "已更新", tone = NoticeTone.BLUE)
        if (item.hidden) ItemFlag(text = "已隐藏", tone = NoticeTone.GREY)
    }
}

@Composable
private fun ItemFlag(text: String, tone: NoticeTone) {
    val colors = toneColors(tone)
    Surface(shape = CtShapeTokens.chip, color = colors.container, contentColor = colors.content) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            maxLines = 1,
        )
    }
}

/** 分类色块（类型色点/标签；小程序 nt-cat 同口径）。 */
@Composable
internal fun NoticeCatPill(
    cat: String,
    tone: NoticeTone,
    modifier: Modifier = Modifier,
) {
    val colors = toneColors(tone)
    Surface(
        modifier = modifier,
        shape = CtShapeTokens.chip,
        color = colors.container,
        contentColor = colors.content,
    ) {
        Text(
            text = cat.ifBlank { "通知" },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 状态色点 + 状态文案（例：● 2 小时后 截止）。 */
@Composable
private fun StatusDotLine(
    status: NoticeStatus,
    modifier: Modifier = Modifier,
) {
    val colors = toneColors(statusTone(status))
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(colors.dot, CircleShape),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (status.expired) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                colors.dot
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 「标记已处理」胶囊（≥48dp；已处理=次要容器底）。 */
@Composable
private fun DonePill(done: Boolean, reduceMotion: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val pillContainer by animateColorAsState(
        targetValue = if (done) scheme.primaryContainer else scheme.surfaceContainerHigh,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.colorSpec,
        label = "donePillContainer",
    )
    val pillContent by animateColorAsState(
        targetValue = if (done) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.colorSpec,
        label = "donePillContent",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (done) 1f else 0.9f,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.pressSpring,
        label = "donePillIcon",
    )
    Surface(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.Checkbox
                stateDescription = if (done) "已处理" else "未处理"
            },
        shape = CtShapeTokens.pill,
        color = pillContainer,
        contentColor = pillContent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier
                    .size(17.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (done) "已处理" else "标记已处理",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

/** 「置顶」胶囊（≥48dp；仅人工通知可置顶）。 */
@Composable
private fun PinPill(
    pinned: Boolean,
    reduceMotion: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val pillContainer by animateColorAsState(
        targetValue = if (pinned) scheme.primaryContainer else scheme.surfaceContainerHigh,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.colorSpec,
        label = "pinPillContainer",
    )
    val pillContent by animateColorAsState(
        targetValue = if (pinned) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.colorSpec,
        label = "pinPillContent",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (pinned) 1f else 0.9f,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.pressSpring,
        label = "pinPillIcon",
    )
    Surface(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.Button
                stateDescription = if (pinned) "已置顶" else "未置顶"
            },
        shape = CtShapeTokens.pill,
        color = pillContainer,
        contentColor = pillContent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.PushPin,
                contentDescription = null,
                modifier = Modifier
                    .size(17.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (pinned) "已置顶" else "置顶",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

// -----------------------------------------------------------------------------
// 空 / 错误 / 加载态
// -----------------------------------------------------------------------------

@Composable
private fun NoticeEmptyState(
    filter: NoticeFilter,
    query: String,
    hasAny: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = query.isNotBlank() || filter != NoticeFilter.ALL
    // 页面背景不是 Surface，显式提供内容色（深色下默认黑色会不可读）
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            EmptyState(
                icon = when {
                    query.isNotBlank() -> Icons.Rounded.Search
                    filter == NoticeFilter.PENDING -> Icons.Rounded.CheckCircle
                    filter == NoticeFilter.PINNED -> Icons.Rounded.PushPin
                    else -> Icons.Rounded.Notifications
                },
                title = when {
                    query.isNotBlank() -> "没有匹配的通知"
                    filter == NoticeFilter.PENDING -> "没有未处理通知"
                    filter == NoticeFilter.PINNED -> "没有置顶通知"
                    !hasAny -> "暂无通知"
                    else -> "没有符合条件的通知"
                },
                message = when {
                    query.isNotBlank() -> "换个关键词试试"
                    filter == NoticeFilter.PENDING -> "全部通知都处理完了"
                    filter == NoticeFilter.PINNED -> "在人工通知卡片上点「置顶」"
                    !hasAny -> "班级通知会显示在这里"
                    else -> "切换筛选看看"
                },
            )
            if (filtered) {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("清除搜索与筛选")
                }
            }
        }
    }
}

@Composable
private fun NoticeErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier = modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "通知加载失败",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = CtShapeTokens.pill,
            ) {
                Text("重试")
            }
        }
    }
}

/** 加载骨架（与卡片同表面；系统移除动画时静态显示）。 */
@Composable
private fun NoticeSkeletonList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(3) { index ->
            NoticeSkeletonCard(index = index)
        }
    }
}

@Composable
private fun NoticeSkeletonCard(index: Int) {
    val reduceMotion = rememberReduceMotion()
    val alpha = if (reduceMotion) {
        0.55f
    } else {
        val transition = rememberInfiniteTransition(label = "noticeSkeleton")
        val animated by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "noticeSkeletonAlpha",
        )
        animated
    }
    val block = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(18.dp)
                        .background(block, CtShapeTokens.chip),
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .width(84.dp)
                        .height(13.dp)
                        .background(block, CircleShape),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(12.dp)
                        .background(block, CircleShape),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (index == 1) 0.72f else 0.88f)
                    .height(18.dp)
                    .background(block, CircleShape),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(block, CircleShape),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(14.dp)
                    .background(block, CircleShape),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .height(30.dp)
                        .background(block, CircleShape),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(12.dp)
                        .background(block, CircleShape),
                )
            }
        }
    }
}

/** 页面内部阶段（四态中的加载/错误两态由它承载）。 */
private enum class Phase { LOADING, READY, FAILED }
