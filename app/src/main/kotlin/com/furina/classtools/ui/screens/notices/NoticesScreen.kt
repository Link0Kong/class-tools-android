@file:OptIn(ExperimentalLayoutApi::class)

package com.furina.classtools.ui.screens.notices

import com.furina.classtools.ui.components.light.ctLightMaterial
import com.furina.classtools.ui.theme.CtLightScene
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.furina.classtools.data.marks.MarkKind
import com.furina.classtools.data.marks.MarksRepository
import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.notices.ManualNotice
import com.furina.classtools.data.notices.NoticeBucket
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeRecurrenceDto
import com.furina.classtools.data.notices.NoticeStats
import com.furina.classtools.data.notices.NoticeStatus
import com.furina.classtools.data.notices.NoticeStatusMachine
import com.furina.classtools.data.notices.NoticesData
import com.furina.classtools.data.notices.NoticesLoadResult
import com.furina.classtools.data.notices.NoticesRepository
import com.furina.classtools.data.ppt.PptImageLoader
import com.furina.classtools.data.session.Session
import com.furina.classtools.ui.adaptive.rememberCtAdaptiveSpec
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.EmptyState
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.expressive.CtBottomSheet
import com.furina.classtools.ui.components.tool.CtToolSectionHeader
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtSpace
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
    /** `actionable !== false`（§2.4：false = 仅告知，不计待处理）。 */
    val actionable: Boolean = true,
    /** 最新发布排序键（组 `publishedAt` 优先，旧缓存回退行 `ts` 与组 `updatedAt` 较大值；「最新发布」/postedText 共用）。 */
    val publishedMillis: Long = 0L,
    /**
     * 发布时间（契约 2026-11-18 §1/§2：组 `publishedAt`，旧缓存由组内消息时间 / `updatedAt` 推算；
     * 0 = 无发布时间）。发布卡据此进入主列表、排序档 4 内倒序，并在状态胶囊显示「<时间> 发布」。
     */
    val publishedTsMillis: Long = 0L,
    /** 是否有任何时间锚点（§1.2 资料判定 / §2.4 待处理判定；发布时间不算锚点）。 */
    val hasTimeAnchor: Boolean = false,
    /** 资料 / 无时间兜底（2026-11-18 §3）：非过期、无有效 recurrence、无任何时间锚点**且无发布时间**；不进长期通知、不计待处理。 */
    val material: Boolean = false,
    /** 有效的长期安排（recurrence；已结束由 [longtermEnded] 标记）。 */
    val recurrence: NoticeRecurrenceDto? = null,
    /** 长期安排已结束（until 到期 / 下一次超出 until）→ 已过期区。 */
    val longtermEnded: Boolean = false,
    /** 期次发生时间（处于期次窗口内时非空；期次卡按它参与主列表排序）。 */
    val occurrenceMillis: Long? = null,
    /** 期次 marks ref：`<gid>@occ:<yyyyMMdd>`（本次已处理写这个 key）。 */
    val occurrenceKey: String? = null,
    /** 本期是否已处理（marks ntdone，ref = 期次 key）。 */
    val occurrenceDone: Boolean = false,
    /** 长期安排周期 label（如「每周三 17:30」；长期清单/详情用）。 */
    val recurLabel: String = "",
    /** 下次发生时间（长期清单排序用）。 */
    val recurNextMillis: Long? = null,
    /** 下次发生文案（`下次 M月D日（周X）HH:mm`；长期清单/详情用）。 */
    val recurNextText: String = "",
) {
    val isAuto: Boolean get() = auto != null
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

/** 列表卡片上的「什么时候发的」：今天 → HH:mm；昨天 → 昨天 HH:mm；更早 → `M月D日（相对周）`（§3）。 */
internal fun formatPosted(
    ts: Long,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    if (ts <= 0) return ""
    val date = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    val at = Instant.ofEpochMilli(ts).atZone(zone)
    return when {
        date == today -> clock.format(at)
        date == today.minusDays(1) -> "昨天 ${clock.format(at)}"
        else -> NoticeStatusMachine.fmtDateWeek(ts, nowMillis, zone)
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
    /** 分享通知（Lead 生成 5:4 分享卡并弹出分享层）。 */
    onShareNotice: ((NoticeGroupDto, Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    initialData: NoticesData? = null,
    initialMarks: MarksSnapshot? = null,
    initialFilter: NoticeFilter = NoticeFilter.ALL,
    initialQuery: String = "",
    initialExpiredExpanded: Boolean = false,
    initialLoading: Boolean = false,
    /**
     * OPPO PUSH 通知点击定位：组 id / rowId / 期次 key。
     * 数据加载完成后展开对应通知详情并消费一次（找不到时多加载一页再试，仍找不到则放弃）。
     */
    initialFocusNoticeId: String? = null,
    onFocusConsumed: () -> Unit = {},
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
    // 2026-11-19 P0 收口：取消「后台自动翻完所有页」。
    // light meta 一次拿全量（hasMore=false）；只有旧分页兜底链路才需要用户按需「加载更早的通知」。
    // 详情懒加载：记录已尝试拉单行 body 的 rowId，避免重复请求/失败后无限重试。
    val hydratedRowIds = remember { mutableStateMapOf<String, Boolean>() }
    var filterKey by rememberSaveable { mutableStateOf(initialFilter.name) }
    // 顶部 5 张可点数据卡的筛选口径（null = 未激活；与 chips 互斥，契约 §2.1）
    var cardFilterKey by rememberSaveable { mutableStateOf<String?>(null) }
    val noticeFilterOptions = NoticeFilter.entries
    // 左右滑动切筛选（跟手 + spring 吸附；reduceMotion 时禁用拖拽并直接用总开关的瞬时切换）
    val noticePagerState = rememberPagerState(
        initialPage = noticeFilterOptions.indexOf(initialFilter).coerceAtLeast(0),
        initialPageOffsetFraction = (previewPagerOffset ?: 0f).coerceIn(-0.5f, 0.5f),
    ) { noticeFilterOptions.size }
    LaunchedEffect(noticePagerState) {
        snapshotFlow { noticePagerState.currentPage to noticePagerState.isScrollInProgress }
            .collect { (page, scrolling) ->
                noticeFilterOptions.getOrNull(page)?.let { filterKey = it.name }
                // 滑动/程序化滚动停稳到非「全部」chips 页 → 顶部卡筛选取消（chips 与顶部卡互斥）
                if (!scrolling && page != noticeFilterOptions.indexOf(NoticeFilter.ALL)) {
                    cardFilterKey = null
                }
            }
    }
    val filter = remember(filterKey) {
        noticeFilterOptions.firstOrNull { it.name == filterKey } ?: NoticeFilter.ALL
    }
    val cardFilter = remember(cardFilterKey) {
        cardFilterKey?.let { key -> NoticeCardFilter.entries.firstOrNull { it.name == key } }
    }
    fun selectFilter(target: NoticeFilter) {
        cardFilterKey = null
        filterKey = target.name
        val index = noticeFilterOptions.indexOf(target).coerceAtLeast(0)
        scope.launch {
            // reduceMotion：直接切；正常：250–300ms spring 吸附
            if (reduceMotion) noticePagerState.scrollToPage(index) else noticePagerState.animateScrollToPage(index)
        }
    }

    /** 点顶部数据卡：激活该口径；再点同一张 → 取消恢复「全部」。 */
    fun selectCardFilter(target: NoticeCardFilter) {
        cardFilterKey = if (cardFilter == target) null else target.name
        filterKey = NoticeFilter.ALL.name
        scope.launch {
            if (reduceMotion) noticePagerState.scrollToPage(0) else noticePagerState.animateScrollToPage(0)
        }
    }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var searchVisible by rememberSaveable { mutableStateOf(initialQuery.isNotBlank()) }
    // 极简固定头方案：统计卡 / 分类筛选收进「筛选」底部弹层，单行筛选 chips 作为滚动内容第一项。
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    // 分类 chip 筛选（null = 全部；契约 §2.2：对所有视图生效）
    var categoryKey by rememberSaveable { mutableStateOf<String?>(null) }
    var expiredExpanded by rememberSaveable { mutableStateOf(initialExpiredExpanded) }
    // 「资料 / 无时间」栏常驻显示、默认展开（可折叠，契约 §2.2）
    var materialExpanded by rememberSaveable { mutableStateOf(true) }
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

    /**
     * 按需加载一页更早的通知（**仅旧分页兜底链路**会真的有下一页）。
     *
     * 2026-11-19 P0 收口：删除了后台「自动翻完所有页」的循环（旧行为会在首屏后连续拉 ~28 页 / 14MB）。
     * 现在只有用户下拉刷新或点底部「加载更早的通知」才发一次请求；
     * light meta 可用时 `hasMore=false`，本函数直接返回缓存不发请求。
     */
    fun loadMoreOnce() {
        if (skipLoad) return
        val repository = noticesRepository ?: return
        if (isLoadingMore) return
        scope.launch {
            isLoadingMore = true
            loadMoreError = null
            try {
                applyResult(repository.loadMore())
            } finally {
                isLoadingMore = false
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
        loadMoreError = null
        if (data == null) phase = Phase.LOADING
        applyResult(repository.load(force = reloadToken > 0))
        // 不再后台自动翻页：light meta 一次拿全量；旧链路交给用户「加载更早的通知」。
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
                loadMoreError = null
                noticesRepository?.let { applyResult(it.load(force = true)) }
                // 不再后台自动翻页（light meta 一次拿全量；旧链路由用户按需加载）。
                runCatching { marksRepository?.refresh(force = true) }
            } finally {
                isRefreshing = false
            }
        }
    }

    /** 标记已处理：普通通知 ref = 组 id；期次卡 ref = 期次 key（`<gid>@occ:<yyyyMMdd>`，§2.5）。 */
    fun toggleDone(item: NoticeUiItem) {
        val repository = marksRepository ?: run {
            notify("未登录，无法同步处理状态")
            return
        }
        val entry = item.auto ?: return
        val occurrenceKey = item.occurrenceKey
        val ref = occurrenceKey ?: entry.id
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val nextDone = !item.done
        scope.launch {
            when (repository.setMark(MarkKind.NT_DONE, ref, nextDone)) {
                is com.furina.classtools.data.marks.MarksUpdateResult.Success -> notify(
                    when {
                        occurrenceKey != null && nextDone -> "本期已处理"
                        occurrenceKey != null -> "本期已恢复未处理"
                        nextDone -> "已标记处理"
                        else -> "已恢复未处理"
                    },
                )

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
    // 分类 chips（数据里出现过的分类，按加载顺序去重；≤1 类时不显示分类行）
    val categories = remember(allItems) {
        allItems.map { it.cat.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    val pinnedCount = allItems.count { it.pinned }
    // §2.4 统计口径（5 张数据卡 / 与工作台同口径）：复用数据层 statsOf（期次 / until / 资料无时间）
    val overview = remember(data, marks, nowMillis) { buildOverview(data, marks, nowMillis) }
    val pendingCount = overview.pending
    val selectedItem = remember(allItems, selectedKey) {
        selectedKey?.let { key -> allItems.firstOrNull { it.key == key } }
    }
    val twoColumn = spec.widthDp >= 600

    // OPPO PUSH 点击定位：数据就绪后按组 id / rowId / 期次 key 找卡片 → 打开详情；
    // 仍未命中且旧分页链路还有更多时，**只按需加载一页**（不再后台翻完所有页），
    // 加载后本 effect 会重跑再找一次，仍找不到则静默放弃（停留在通知页）。
    var focusLoadMoreTried by remember(initialFocusNoticeId) { mutableStateOf(false) }
    LaunchedEffect(initialFocusNoticeId, phase, allItems, data?.hasMore) {
        val target = initialFocusNoticeId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (phase != Phase.READY) return@LaunchedEffect
        val match = allItems.firstOrNull { item ->
            item.key == target ||
                item.occurrenceKey == target ||
                item.auto?.id == target ||
                item.auto?.rowId == target ||
                item.manual?.id == target
        }
        if (match != null) {
            selectedKey = match.key
            onFocusConsumed()
            return@LaunchedEffect
        }
        if (!focusLoadMoreTried && data?.hasMore == true) {
            focusLoadMoreTried = true
            loadMoreOnce()
        } else {
            onFocusConsumed()
        }
    }

    // 详情懒加载（2026-11-19）：light meta 把 items 里的内联图片/附件置空并写 lazy=true，
    // 用户点开卡片时按 id 拉单行完整 body 合并回缓存（一次请求，重开不再请求）；
    // 失败只提示一次，图片仍有逐张的按需加载兜底，文件显示「待获取」。
    val selectedRowId = selectedItem?.auto?.rowId ?: selectedItem?.manual?.row?.id
    LaunchedEffect(selectedKey, selectedRowId) {
        val item = selectedItem ?: return@LaunchedEffect
        val rowId = selectedRowId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val entry = item.auto ?: return@LaunchedEffect
        if (!entry.hasLazyContent) return@LaunchedEffect
        if (hydratedRowIds[rowId] == true) return@LaunchedEffect
        val repository = noticesRepository ?: return@LaunchedEffect
        hydratedRowIds[rowId] = true
        when (val result = repository.hydrateRow(rowId)) {
            is NoticesLoadResult.Success -> data = result.data
            is NoticesLoadResult.Failure -> notify(result.message)
        }
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                // 极简固定头（56dp）：返回 + 标题 + 总数 + 搜索/筛选入口。
                // 统计卡与分类筛选收进「筛选」底部弹层；单行筛选 chips 移到滚动内容第一项。
                NoticeMinimalHeader(
                    totalCount = if (phase == Phase.LOADING) null else allItems.size,
                    searchVisible = searchVisible,
                    filterActive = cardFilter != null || categoryKey != null,
                    onBack = onBack,
                    onToggleSearch = {
                        searchVisible = !searchVisible
                        if (!searchVisible) query = ""
                    },
                    onOpenFilters = { showFilterSheet = true },
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
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val offline = data?.fromCache == true || (errorMessage != null && data != null)
                    val offlineMessage = errorMessage?.let { "刷新失败，显示本机缓存" }
                        ?: ("本机缓存 · 更新于 ${formatTs(data?.fetchedAt ?: 0L)}")
                    Column(
                        modifier = Modifier
                            .widthIn(max = spec.contentMaxWidth)
                            .fillMaxSize()
                            .padding(horizontal = spec.horizontalPadding),
                    ) {
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
                                    // 顶部卡只作用于「全部」chips 页（chips 与顶部卡互斥，§2.1）
                                    val pageCardFilter = if (pageFilter == NoticeFilter.ALL) cardFilter else null
                                    val content = remember(allItems, pageFilter, pageCardFilter, query, nowMillis, categoryKey) {
                                        viewContentOf(
                                            items = allItems,
                                            chipFilter = pageFilter,
                                            cardFilter = pageCardFilter,
                                            query = query,
                                            nowMillis = nowMillis,
                                            category = categoryKey,
                                        )
                                    }
                                    val hasMore = data?.hasMore == true
                                    val remaining = data?.remainingCount ?: 0
                                    // 滚动内容第一项：单行筛选 chips（固定头不再放 chips / 统计卡）。
                                    val cardChipKey = OUTSIDE_CARD_CHIP
                                    val categoryChipKey = OUTSIDE_CATEGORY_CHIP
                                    val allCountText = animatedStatText(allItems.size, reduceMotion)
                                    val pendingText = animatedStatText(pendingCount, reduceMotion)
                                    val pinnedText = animatedStatText(pinnedCount, reduceMotion)
                                    val pageChips = buildList {
                                        if (pageCardFilter != null) {
                                            add(NoticeChip(cardChipKey, "${pageCardFilter.label} ×"))
                                        } else {
                                            add(NoticeChip(NoticeFilter.ALL.name, "全部 $allCountText"))
                                            add(NoticeChip(NoticeFilter.PENDING.name, "未处理 $pendingText"))
                                            add(NoticeChip(NoticeFilter.PINNED.name, "置顶 $pinnedText"))
                                        }
                                        categoryKey?.let { add(NoticeChip(categoryChipKey, "$it ×")) }
                                    }
                                    val selectedChipKey = when {
                                        pageCardFilter != null -> cardChipKey
                                        categoryKey != null -> categoryChipKey
                                        else -> pageFilter.name
                                    }
                                    val onPageChipSelect: (String) -> Unit = { key ->
                                        when (key) {
                                            cardChipKey -> {
                                                cardFilterKey = null
                                                selectFilter(NoticeFilter.ALL)
                                            }

                                            categoryChipKey -> categoryKey = null
                                            else -> NoticeFilter.entries
                                                .firstOrNull { it.name == key }
                                                ?.let { selectFilter(it) }
                                        }
                                    }
                                    val pageContentHeader: @Composable () -> Unit = {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            // 数据卡与筛选按钮分块排列，中间固定 10dp 间距，避免叠在一起。
                                            NoticeOverviewCards(
                                                stats = overview,
                                                active = cardFilter,
                                                onPick = { selectCardFilter(it) },
                                                reduceMotion = reduceMotion,
                                            )
                                            NoticeFilterStrip(
                                                chips = pageChips,
                                                selectedKey = selectedChipKey,
                                                onSelect = onPageChipSelect,
                                                reduceMotion = reduceMotion,
                                            )
                                        }
                                    }
                                    val pageBanner: (@Composable () -> Unit)? = if (offline) {
                                        {
                                            NoticeOfflineBanner(
                                                message = offlineMessage,
                                                onRetry = { refresh() },
                                                modifier = Modifier.padding(bottom = 8.dp),
                                            )
                                        }
                                    } else {
                                        null
                                    }
                                    PullToRefreshBox(
                                        isRefreshing = isRefreshing,
                                        onRefresh = { refresh() },
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        if (content.isEmpty) {
                                            // 放进 LazyColumn 以保留下拉刷新（空态也能刷新 / 加载更多）
                                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                if (pageBanner != null) {
                                                    item(key = "offline-banner") { pageBanner() }
                                                }
                                                item(key = "filter-header") { pageContentHeader() }
                                                item(key = "empty") {
                                                    NoticeEmptyState(
                                                        filter = pageFilter,
                                                        cardFilter = pageCardFilter,
                                                        query = query,
                                                        category = categoryKey,
                                                        hasAny = allItems.isNotEmpty(),
                                                        onClear = {
                                                            query = ""
                                                            cardFilterKey = null
                                                            categoryKey = null
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
                                                            onLoadMore = { loadMoreOnce() },
                                                        )
                                                    }
                                                }
                                            }
                                        } else if (twoColumn) {
                                            NoticeGrid(
                                                content = content,
                                                materialExpanded = materialExpanded,
                                                onToggleMaterial = { materialExpanded = !materialExpanded },
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
                                                onLoadMore = { loadMoreOnce() },
                                                header = pageContentHeader,
                                                banner = pageBanner,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        } else {
                                            NoticeList(
                                                content = content,
                                                materialExpanded = materialExpanded,
                                                onToggleMaterial = { materialExpanded = !materialExpanded },
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
                                                onLoadMore = { loadMoreOnce() },
                                                header = pageContentHeader,
                                                banner = pageBanner,
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

            androidx.compose.foundation.layout.Box(
            modifier = Modifier.ctLightMaterial(
                CtLightScene.SNACKBAR,
                shape = CtShape.input,
                bodySafe = true,
            ),
        ) { SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            )
            }
        }
    }

    if (showFilterSheet) {
        NoticeFilterSheet(
            stats = overview,
            activeCard = cardFilter,
            filter = filter,
            categories = categories,
            categoryKey = categoryKey,
            allCount = allItems.size,
            pendingCount = pendingCount,
            pinnedCount = pinnedCount,
            hiddenCount = data?.hiddenCount ?: 0,
            reduceMotion = reduceMotion,
            onDismiss = { showFilterSheet = false },
            onPickCard = {
                selectCardFilter(it)
                showFilterSheet = false
            },
            onPickFilter = {
                selectFilter(it)
                showFilterSheet = false
            },
            onPickCategory = {
                categoryKey = it
                showFilterSheet = false
            },
            onOpenSearch = {
                searchVisible = true
                showFilterSheet = false
            },
            onRefresh = {
                refresh()
                showFilterSheet = false
            },
        )
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
            onShare = onShareNotice,
            nowMillis = nowMillis,
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
// 极简固定头（≤56dp）+ 筛选弹层
// -----------------------------------------------------------------------------

/** 滚动内容 chips 里的「当前卡片筛选」伪 key（点它 = 清除卡片筛选、恢复全部）。 */
private const val OUTSIDE_CARD_CHIP: String = "__card_filter"

/** 滚动内容 chips 里的「当前分类筛选」伪 key（点它 = 清除分类筛选）。 */
private const val OUTSIDE_CATEGORY_CHIP: String = "__category_filter"

/**
 * 极简固定头（2026-11-16 用户反馈「通知上部固定 UI 太大」）：
 *
 * 一行 = 返回 + 「班级通知」+ 总数胶囊 + 搜索/筛选入口，高 56dp；
 * 统计卡与分类筛选进 [NoticeFilterSheet]，单行筛选 chips 由滚动内容第一项承担。
 */
@Composable
private fun NoticeMinimalHeader(
    totalCount: Int?,
    searchVisible: Boolean,
    filterActive: Boolean,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onOpenFilters: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // 固定头使用实色背景并抬高 zIndex：即使列表/筛选内容在极端布局下顶到边缘，
    // 也不会与头部按钮在视觉上叠在一起。
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(1f),
        color = scheme.background,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Surface(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = scheme.surfaceContainerHigh.copy(alpha = 0.72f),
            contentColor = scheme.onSurface,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = "班级通知",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (totalCount != null) {
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = scheme.primary.copy(alpha = 0.12f),
                contentColor = scheme.primary,
            ) {
                Text(
                    text = totalCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        NoticeHeaderIconButton(
            icon = Icons.Rounded.Search,
            description = if (searchVisible) "关闭搜索" else "搜索通知",
            active = searchVisible,
            onClick = onToggleSearch,
        )
            NoticeHeaderIconButton(
                icon = Icons.Rounded.Tune,
                description = "筛选通知",
                active = filterActive,
                onClick = onOpenFilters,
            )
        }
    }
}

@Composable
private fun NoticeHeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 「筛选」底部弹层：统计卡（原固定区 5 张大卡）+ 状态 chips + 分类 chips + 搜索/刷新。
 * 选择任一项后立即生效并关闭（与固定头 ≤56dp 的口径配套）。
 */
@Composable
private fun NoticeFilterSheet(
    stats: NoticeStats,
    activeCard: NoticeCardFilter?,
    filter: NoticeFilter,
    categories: List<String>,
    categoryKey: String?,
    allCount: Int,
    pendingCount: Int,
    pinnedCount: Int,
    hiddenCount: Int,
    reduceMotion: Boolean,
    onDismiss: () -> Unit,
    onPickCard: (NoticeCardFilter) -> Unit,
    onPickFilter: (NoticeFilter) -> Unit,
    onPickCategory: (String?) -> Unit,
    onOpenSearch: () -> Unit,
    onRefresh: () -> Unit,
) {
    CtBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "筛选",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val subtitle = buildSubtitle(allCount, pendingCount, hiddenCount)
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 统计卡（原固定区大卡）：点选后立即筛选并关闭弹层。
                NoticeOverviewCards(
                    stats = stats,
                    active = activeCard,
                    onPick = onPickCard,
                    reduceMotion = reduceMotion,
                )
                Text(
                    text = "状态",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NoticeFilterChips(
                    chips = listOf(
                        NoticeChip(NoticeFilter.ALL.name, "全部 $allCount"),
                        NoticeChip(NoticeFilter.PENDING.name, "未处理 $pendingCount"),
                        NoticeChip(NoticeFilter.PINNED.name, "置顶 $pinnedCount"),
                    ),
                    selectedKey = if (activeCard != null) "" else filter.name,
                    onSelect = { key ->
                        NoticeFilter.entries.firstOrNull { it.name == key }?.let(onPickFilter)
                    },
                    reduceMotion = reduceMotion,
                )
                if (categories.size > 1) {
                    Text(
                        text = "分类",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NoticeFilterChips(
                        chips = listOf(NoticeChip("", "全部")) +
                            categories.map { NoticeChip(it, it) },
                        selectedKey = categoryKey.orEmpty(),
                        onSelect = { key -> onPickCategory(key.ifBlank { null }) },
                        reduceMotion = reduceMotion,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onOpenSearch,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = CtShapeTokens.pill,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("搜索通知", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onRefresh,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = CtShapeTokens.pill,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("刷新", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.height(CtSpace.sm))
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 顶部：5 张可点数据卡（待处理 / 24h 截止 / 7 天内 / 长期通知 / 最新发布）
// -----------------------------------------------------------------------------

/**
 * 顶部 5 张可点数据卡（契约 §2.1，与小程序/web 同构）：
 *
 * - 信息顺序固定：待处理 → 24h 截止 → 7 天内 → 长期通知 → 最新发布；数字口径见 §2.4；
 * - 手机 360dp 下 3 + 2 两行，无横向溢出；每张卡点按区 ≥ 64dp（> 40dp 红线）；
 * - 点卡 = 列表筛选到该口径；再点同一张 = 取消恢复「全部」；激活态 = primaryContainer 底 + 主色描边；
 * - 与下方筛选 chips 互斥（由页面状态机保证：激活卡片时 chips 全部取消）。
 */
@Composable
private fun NoticeOverviewCards(
    stats: NoticeStats,
    active: NoticeCardFilter?,
    onPick: (NoticeCardFilter) -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val firstRow = listOf(
        NoticeCardFilter.PENDING to animatedStatText(stats.pending, reduceMotion),
        NoticeCardFilter.URGENT to animatedStatText(stats.urgent, reduceMotion),
        NoticeCardFilter.WEEK to animatedStatText(stats.week, reduceMotion),
    )
    val secondRow = listOf(
        NoticeCardFilter.LONGTERM to animatedStatText(stats.longterm, reduceMotion),
        NoticeCardFilter.LATEST to stats.latestPostedText.ifBlank { "—" },
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        // 统计筛选卡可点击：两张一组，间隔按鸿蒙「控件间距」留 8vp（2026-11-20）
        verticalArrangement = Arrangement.spacedBy(CtSpace.controlGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CtSpace.controlGap),
        ) {
            firstRow.forEach { (filter, value) ->
                NoticeStatCard(
                    filter = filter,
                    value = value,
                    active = active == filter,
                    onClick = { onPick(filter) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CtSpace.controlGap),
        ) {
            secondRow.forEach { (filter, value) ->
                NoticeStatCard(
                    filter = filter,
                    value = value,
                    active = active == filter,
                    onClick = { onPick(filter) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 单张可点数据卡（激活态 + 语义；紧凑高度 ≥52dp，减少顶部占位）。 */
@Composable
private fun NoticeStatCard(
    filter: NoticeCardFilter,
    value: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val labelColor = if (active) scheme.onPrimaryContainer.copy(alpha = 0.72f) else scheme.onSurfaceVariant
    val valueColor = when {
        active -> scheme.onPrimaryContainer
        filter == NoticeCardFilter.URGENT -> scheme.error
        filter == NoticeCardFilter.LONGTERM -> scheme.secondary
        filter == NoticeCardFilter.LATEST -> scheme.tertiary
        else -> scheme.onSurface
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 52.dp)
            .semantics {
                role = Role.Button
                selected = active
                stateDescription = if (active) "已筛选" else "未筛选"
            },
        shape = RoundedCornerShape(CtRadius.cardCompact),
        color = if (active) scheme.primaryContainer else scheme.surfaceContainerLow,
        contentColor = if (active) scheme.onPrimaryContainer else scheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (active) {
                scheme.primary.copy(alpha = 0.6f)
            } else {
                scheme.outlineVariant.copy(alpha = 0.28f)
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text = value,
                style = if (value.length > 5) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = filter.label,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
        shape = RoundedCornerShape(CtRadius.cardCompact),
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
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CtRadius.cardCompact),
        color = scheme.surfaceContainerLow,
        contentColor = scheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = scheme.outlineVariant.copy(alpha = 0.35f),
        ),
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
                tint = scheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
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

/**
 * 列表中的一张通知卡（错峰入场 + 按压动效包装；[mode] 决定状态胶囊 / 操作区，见契约 §2.2/§2.3）。
 */
@Composable
private fun NoticeCardEntry(
    item: NoticeUiItem,
    mode: NoticeCardMode,
    index: Int,
    itemKey: String,
    reduceMotion: Boolean,
    appearedKeys: SnapshotStateMap<String, Boolean>,
    previewEntranceProgress: Float?,
    previewPressedKey: String?,
    previewExpandedKey: String?,
    selectedKey: String?,
    onOpen: (NoticeUiItem) -> Unit,
    onToggleDone: (NoticeUiItem) -> Unit,
    onTogglePin: (NoticeUiItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    NoticeEntrance(
        itemKey = itemKey,
        index = index,
        reduceMotion = reduceMotion,
        appearedKeys = appearedKeys,
        previewBaseProgress = previewEntranceProgress,
        modifier = modifier,
    ) {
        NoticeCard(
            item = item,
            mode = mode,
            reduceMotion = reduceMotion,
            expanded = selectedKey == item.key || previewExpandedKey == item.key,
            previewPressed = previewPressedKey == item.key,
            onOpen = { onOpen(item) },
            onToggleDone = { onToggleDone(item) },
            onTogglePin = { onTogglePin(item) },
        )
    }
}

/**
 * 通知列表（分区渲染；契约 §2.2）：
 * 主列表（含期次卡）→「资料 / 无时间」栏（默认展开）→「已过期」区（默认折叠）→ 底部「共 N 组」；
 * 「长期通知」筛选 = 长期安排清单；「最新发布」= 按发布时间倒序（过期卡样式仍按过期显示）。
 */
@Composable
private fun NoticeList(
    content: NoticesViewContent,
    materialExpanded: Boolean,
    onToggleMaterial: () -> Unit,
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
    /** 滚动内容第一项：单行筛选 chips（固定头不放筛选 chips）。 */
    header: (@Composable () -> Unit)? = null,
    /** 滚动内容顶部提示条（离线/缓存）；滚动而非固定在头部。 */
    banner: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (banner != null) {
            item(key = "offline-banner") {
                Box(modifier = Modifier.fillMaxWidth()) { banner() }
            }
        }
        if (header != null) {
            item(key = "filter-header") {
                Box(modifier = Modifier.fillMaxWidth()) { header() }
            }
        }
        if (content.longtermOnly || content.latestOnly) {
            val list = if (content.longtermOnly) content.longterm else content.latest
            itemsIndexed(items = list, key = { _, item -> "x-${item.key}" }) { index, item ->
                val mode = if (content.longtermOnly) {
                    NoticeCardMode.LONGTERM_DEF
                } else {
                    noticeLatestModeOf(item)
                }
                NoticeCardEntry(
                    item = item,
                    mode = mode,
                    index = index,
                    itemKey = "x-${item.key}",
                    reduceMotion = reduceMotion,
                    appearedKeys = appearedKeys,
                    previewEntranceProgress = previewEntranceProgress,
                    previewPressedKey = previewPressedKey,
                    previewExpandedKey = previewExpandedKey,
                    selectedKey = selectedKey,
                    onOpen = onOpen,
                    onToggleDone = onToggleDone,
                    onTogglePin = onTogglePin,
                    modifier = this.noticeAnimateItem(reduceMotion),
                )
            }
        } else {
            itemsIndexed(items = content.primary, key = { _, item -> "c-${item.key}" }) { index, item ->
                NoticeCardEntry(
                    item = item,
                    mode = noticeCardModeOf(item),
                    index = index,
                    itemKey = "c-${item.key}",
                    reduceMotion = reduceMotion,
                    appearedKeys = appearedKeys,
                    previewEntranceProgress = previewEntranceProgress,
                    previewPressedKey = previewPressedKey,
                    previewExpandedKey = previewExpandedKey,
                    selectedKey = selectedKey,
                    onOpen = onOpen,
                    onToggleDone = onToggleDone,
                    onTogglePin = onTogglePin,
                    modifier = this.noticeAnimateItem(reduceMotion),
                )
            }
            if (content.showMaterial && content.material.isNotEmpty()) {
                item(key = "material-header") {
                    NoticeGroupHeader(
                        title = "资料 / 无时间",
                        subtitle = "${content.material.size} 条",
                        expanded = materialExpanded,
                        onToggle = onToggleMaterial,
                        reduceMotion = reduceMotion,
                        modifier = this.noticeAnimateItem(reduceMotion),
                    )
                }
                if (materialExpanded) {
                    itemsIndexed(items = content.material, key = { _, item -> "m-${item.key}" }) { index, item ->
                        NoticeCardEntry(
                            item = item,
                            mode = NoticeCardMode.NORMAL,
                            index = content.primary.size + 1 + index,
                            itemKey = "m-${item.key}",
                            reduceMotion = reduceMotion,
                            appearedKeys = appearedKeys,
                            previewEntranceProgress = previewEntranceProgress,
                            previewPressedKey = previewPressedKey,
                            previewExpandedKey = previewExpandedKey,
                            selectedKey = selectedKey,
                            onOpen = onOpen,
                            onToggleDone = onToggleDone,
                            onTogglePin = onTogglePin,
                            modifier = this.noticeAnimateItem(reduceMotion),
                        )
                    }
                }
            }
            if (content.showExpiredSection && content.expired.isNotEmpty()) {
                item(key = "expired-header") {
                    NoticeGroupHeader(
                        title = "已过期",
                        subtitle = "${content.expired.size} 组",
                        expanded = expiredExpanded,
                        onToggle = onToggleExpired,
                        reduceMotion = reduceMotion,
                        modifier = this.noticeAnimateItem(reduceMotion),
                    )
                }
                if (expiredExpanded) {
                    itemsIndexed(items = content.expired, key = { _, item -> "e-${item.key}" }) { index, item ->
                        NoticeCardEntry(
                            item = item,
                            mode = NoticeCardMode.EXPIRED,
                            index = content.primary.size + 1 + index,
                            itemKey = "e-${item.key}",
                            reduceMotion = reduceMotion,
                            appearedKeys = appearedKeys,
                            previewEntranceProgress = previewEntranceProgress,
                            previewPressedKey = previewPressedKey,
                            previewExpandedKey = previewExpandedKey,
                            selectedKey = selectedKey,
                            onOpen = onOpen,
                            onToggleDone = onToggleDone,
                            onTogglePin = onTogglePin,
                            modifier = this.noticeAnimateItem(reduceMotion),
                        )
                    }
                }
            }
        }
        if (content.totalCount > 0) {
            item(key = "total-count") {
                NoticeTotalCountRow(count = content.totalCount)
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

/** 宽屏双列同 [NoticeList]（分区头/底部统计跨列）。 */
@Composable
private fun NoticeGrid(
    content: NoticesViewContent,
    materialExpanded: Boolean,
    onToggleMaterial: () -> Unit,
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
    /** 滚动内容第一项：单行筛选 chips（固定头不放筛选 chips）。 */
    header: (@Composable () -> Unit)? = null,
    /** 滚动内容顶部提示条（离线/缓存）；滚动而非固定在头部。 */
    banner: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (banner != null) {
            item(key = "offline-banner", span = { GridItemSpan(maxLineSpan) }) {
                Box(modifier = Modifier.fillMaxWidth()) { banner() }
            }
        }
        if (header != null) {
            item(key = "filter-header", span = { GridItemSpan(maxLineSpan) }) {
                Box(modifier = Modifier.fillMaxWidth()) { header() }
            }
        }
        if (content.longtermOnly || content.latestOnly) {
            val list = if (content.longtermOnly) content.longterm else content.latest
            itemsIndexed(items = list, key = { _, item -> "x-${item.key}" }) { index, item ->
                val mode = if (content.longtermOnly) NoticeCardMode.LONGTERM_DEF else noticeLatestModeOf(item)
                NoticeCardEntry(
                    item = item,
                    mode = mode,
                    index = index,
                    itemKey = "x-${item.key}",
                    reduceMotion = reduceMotion,
                    appearedKeys = appearedKeys,
                    previewEntranceProgress = previewEntranceProgress,
                    previewPressedKey = previewPressedKey,
                    previewExpandedKey = previewExpandedKey,
                    selectedKey = selectedKey,
                    onOpen = onOpen,
                    onToggleDone = onToggleDone,
                    onTogglePin = onTogglePin,
                    modifier = this.noticeAnimateItem(reduceMotion),
                )
            }
        } else {
            itemsIndexed(items = content.primary, key = { _, item -> "c-${item.key}" }) { index, item ->
                NoticeCardEntry(
                    item = item,
                    mode = noticeCardModeOf(item),
                    index = index,
                    itemKey = "c-${item.key}",
                    reduceMotion = reduceMotion,
                    appearedKeys = appearedKeys,
                    previewEntranceProgress = previewEntranceProgress,
                    previewPressedKey = previewPressedKey,
                    previewExpandedKey = previewExpandedKey,
                    selectedKey = selectedKey,
                    onOpen = onOpen,
                    onToggleDone = onToggleDone,
                    onTogglePin = onTogglePin,
                    modifier = this.noticeAnimateItem(reduceMotion),
                )
            }
            if (content.showMaterial && content.material.isNotEmpty()) {
                item(key = "material-header", span = { GridItemSpan(maxLineSpan) }) {
                    NoticeGroupHeader(
                        title = "资料 / 无时间",
                        subtitle = "${content.material.size} 条",
                        expanded = materialExpanded,
                        onToggle = onToggleMaterial,
                        reduceMotion = reduceMotion,
                        modifier = this.noticeAnimateItem(reduceMotion),
                    )
                }
                if (materialExpanded) {
                    itemsIndexed(items = content.material, key = { _, item -> "m-${item.key}" }) { index, item ->
                        NoticeCardEntry(
                            item = item,
                            mode = NoticeCardMode.NORMAL,
                            index = content.primary.size + 1 + index,
                            itemKey = "m-${item.key}",
                            reduceMotion = reduceMotion,
                            appearedKeys = appearedKeys,
                            previewEntranceProgress = previewEntranceProgress,
                            previewPressedKey = previewPressedKey,
                            previewExpandedKey = previewExpandedKey,
                            selectedKey = selectedKey,
                            onOpen = onOpen,
                            onToggleDone = onToggleDone,
                            onTogglePin = onTogglePin,
                            modifier = this.noticeAnimateItem(reduceMotion),
                        )
                    }
                }
            }
            if (content.showExpiredSection && content.expired.isNotEmpty()) {
                item(key = "expired-header", span = { GridItemSpan(maxLineSpan) }) {
                    NoticeGroupHeader(
                        title = "已过期",
                        subtitle = "${content.expired.size} 组",
                        expanded = expiredExpanded,
                        onToggle = onToggleExpired,
                        reduceMotion = reduceMotion,
                        modifier = this.noticeAnimateItem(reduceMotion),
                    )
                }
                if (expiredExpanded) {
                    itemsIndexed(items = content.expired, key = { _, item -> "e-${item.key}" }) { index, item ->
                        NoticeCardEntry(
                            item = item,
                            mode = NoticeCardMode.EXPIRED,
                            index = content.primary.size + 1 + index,
                            itemKey = "e-${item.key}",
                            reduceMotion = reduceMotion,
                            appearedKeys = appearedKeys,
                            previewEntranceProgress = previewEntranceProgress,
                            previewPressedKey = previewPressedKey,
                            previewExpandedKey = previewExpandedKey,
                            selectedKey = selectedKey,
                            onOpen = onOpen,
                            onToggleDone = onToggleDone,
                            onTogglePin = onTogglePin,
                            modifier = this.noticeAnimateItem(reduceMotion),
                        )
                    }
                }
            }
        }
        if (content.totalCount > 0) {
            item(key = "total-count", span = { GridItemSpan(maxLineSpan) }) {
                NoticeTotalCountRow(count = content.totalCount)
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

/** 最新发布视图的卡片模式：过期卡仍按过期显示；长期安排定义卡不显示「本次已处理」。 */
internal fun noticeLatestModeOf(item: NoticeUiItem): NoticeCardMode = when {
    item.expired -> NoticeCardMode.EXPIRED
    item.recurrence != null -> NoticeCardMode.LONGTERM_DEF
    else -> NoticeCardMode.NORMAL
}

/** 列表底部统计「共 N 组」（§2.2：主列表条目数；期次计 1 条；长期清单视图计长期条数）。 */
@Composable
private fun NoticeTotalCountRow(count: Int) {
    Text(
        text = "共 $count 组",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    )
}

/**
 * 底部「加载更早的通知」行（**按需加载**，2026-11-19 取代原「后台自动加载全部」）。
 *
 * - 常态（还有更早数据）：`加载更早的通知（还有 N 条）`，点按加载一页；不点就不请求；
 * - 加载中：`正在加载…（还有 N 条）`，整行不可点；
 * - 失败：错误文案 +「点此重试」，点按重新加载这一页；
 * - light meta（一次拿全量）链路下 `hasMore=false`，这一行不会出现。
 *
 * 触控 ≥52dp。
 */
@Composable
private fun NoticeLoadMoreRow(
    remainingCount: Int,
    loading: Boolean,
    error: String?,
    onLoadMore: () -> Unit,
) {
    val remainingText = if (remainingCount > 0) "（还有 $remainingCount 条）" else ""
    val idleText = "加载更早的通知$remainingText"
    val loadingText = "正在加载…$remainingText"
    Surface(
        onClick = { if (!loading) onLoadMore() },
        enabled = !loading,
        shape = RoundedCornerShape(CtRadius.cardCompact),
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
                    Text(
                        text = loadingText,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                error != null -> Text(
                    text = "$error（点此重试）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                else -> Text(
                    text = idleText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 分区头（「资料 / 无时间」「已过期」）：整行可点折叠/展开，箭头旋转动画（v6 CtToolSectionHeader）。 */
@Composable
private fun NoticeGroupHeader(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.pressSpring,
        label = "noticeSectionArrow",
    )
    CtToolSectionHeader(
        title = title,
        subtitle = subtitle,
        modifier = modifier
            .clip(RoundedCornerShape(CtRadius.cardCompact))
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

/**
 * 通知卡片（契约 §2.2/§2.3）：[mode] 决定状态胶囊与操作区。
 *
 * - 普通：标记已处理 / 置顶；已过期普通通知保留「标记已处理」（已过期区保持现状）；
 * - 期次：状态胶囊 `本次 M月D日（周X）HH:mm` + 操作「本次已处理」（写 marks ref = 期次 key）；
 * - 长期定义（长期清单）：状态「进行中」+ 周期 / 下次，**无已处理**；
 * - 长期安排已结束：状态「长期安排已结束」，无已处理。
 */
@Composable
private fun NoticeCard(
    item: NoticeUiItem,
    onOpen: () -> Unit,
    onToggleDone: () -> Unit,
    onTogglePin: () -> Unit,
    mode: NoticeCardMode = NoticeCardMode.NORMAL,
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
    // 长期清单视图：状态胶囊固定「进行中」（即使该安排当前处于期次窗口，见 §2.2）
    val displayStatus = if (mode == NoticeCardMode.LONGTERM_DEF) {
        NoticeStatus("recurring", "进行中", "recurring", false, NoticeBucket.RECURRING)
    } else {
        item.status
    }
    NoticePressableCard(
        onClick = onOpen,
        reduceMotion = reduceMotion,
        contentDescription = item.title.ifBlank { "通知" },
        expanded = expanded,
        previewPressed = previewPressed,
        pinned = item.pinned,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NoticeCatPill(cat = item.cat, tone = item.tone)
                // 未读点：待处理（未过期 / 非资料 / 未处理 / 有时间锚点）通知在分类徽标右侧显示小圆点。
                if (isPendingItem(item)) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(scheme.primary, CircleShape)
                            .semantics { contentDescription = "未读" },
                    )
                }
                if (displayStatus != null) {
                    Spacer(Modifier.width(8.dp))
                    StatusDotLine(
                        status = displayStatus,
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

            NoticeCardMeta(item = item, mode = mode)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    // 期次卡：本期单独已处理（marks ref = 期次 key，§2.3）
                    item.occurrenceKey != null -> DonePill(
                        done = item.done,
                        reduceMotion = reduceMotion,
                        onClick = onToggleDone,
                        doneText = "本期已处理",
                        undoneText = "本次已处理",
                    )

                    // 普通通知（含已过期普通通知）：沿用既有「标记已处理」
                    item.isAuto && item.recurrence == null -> DonePill(
                        done = item.done,
                        reduceMotion = reduceMotion,
                        onClick = onToggleDone,
                    )

                    // 长期安排定义卡 / 长期安排已结束：无已处理按钮（§2.3）
                    item.isAuto -> Unit

                    else -> PinPill(pinned = item.pinned, reduceMotion = reduceMotion, onClick = onTogglePin)
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

/** 卡片第三行：来源 / 发送者 / 长期安排（周期 + 下次）/ 附件数 / 多源合并 / 已更新 / 已隐藏（FlowRow 可换行）。 */
@Composable
private fun NoticeCardMeta(item: NoticeUiItem, mode: NoticeCardMode = NoticeCardMode.NORMAL) {
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
        // 「长期安排」徽标（期次卡 / 长期定义卡；§2.3）
        if (item.recurring) {
            ItemFlag(text = "长期安排", tone = NoticeTone.BLUE)
        }
        // 长期安排：周期 label（期次卡 / 长期定义卡都显示）
        if (item.recurLabel.isNotBlank()) {
            ItemFlag(text = item.recurLabel, tone = NoticeTone.BLUE)
        }
        // 「下次 …」只在长期定义卡显示（期次卡状态胶囊已显示「本次 …」，避免重复）
        if (mode == NoticeCardMode.LONGTERM_DEF && item.recurNextText.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = scheme.secondary,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = item.recurNextText,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item.attachmentCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AttachFile,
                    contentDescription = "附件 ${item.attachmentCount} 个",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = "附件 ${item.attachmentCount}",
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

/** 「标记已处理」胶囊（≥48dp；已处理=次要容器底）。期次卡文案为「本次已处理 / 本期已处理」。 */
@Composable
private fun DonePill(
    done: Boolean,
    reduceMotion: Boolean,
    onClick: () -> Unit,
    doneText: String = "已处理",
    undoneText: String = "标记已处理",
) {
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
                text = if (done) doneText else undoneText,
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
    cardFilter: NoticeCardFilter? = null,
    category: String? = null,
) {
    val filtered = query.isNotBlank() || filter != NoticeFilter.ALL || cardFilter != null ||
        !category.isNullOrBlank()
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
                    cardFilter == NoticeCardFilter.LONGTERM -> Icons.Rounded.Schedule
                    filter == NoticeFilter.PENDING -> Icons.Rounded.CheckCircle
                    filter == NoticeFilter.PINNED -> Icons.Rounded.PushPin
                    else -> Icons.Rounded.Notifications
                },
                title = when {
                    query.isNotBlank() -> "没有匹配的通知"
                    cardFilter == NoticeCardFilter.LONGTERM -> "暂无长期安排"
                    cardFilter == NoticeCardFilter.LATEST -> "暂无通知"
                    cardFilter == NoticeCardFilter.PENDING -> "没有待处理通知"
                    cardFilter == NoticeCardFilter.URGENT -> "24h 内没有截止"
                    cardFilter == NoticeCardFilter.WEEK -> "7 天内没有截止"
                    filter == NoticeFilter.PENDING -> "没有未处理通知"
                    filter == NoticeFilter.PINNED -> "没有置顶通知"
                    !hasAny -> "暂无通知"
                    else -> "没有符合条件的通知"
                },
                message = when {
                    query.isNotBlank() -> "换个关键词试试"
                    cardFilter == NoticeCardFilter.LONGTERM -> "长期安排会显示在这里"
                    cardFilter != null -> "再点一次顶部卡片可取消筛选"
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
            Spacer(Modifier.height(CtSpace.md))
            Text(
                text = "通知加载失败",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(CtSpace.xs))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(CtSpace.lg))
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
