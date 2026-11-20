@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.roster

import com.furina.classtools.ui.components.light.ctLightMaterial
import com.furina.classtools.ui.theme.CtLightScene
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.member.ClassLeagueRepository
import com.furina.classtools.data.member.ClassLeagueResult
import com.furina.classtools.data.member.ClassMembersRepository
import com.furina.classtools.data.member.MemberCategory
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.features.roster.RosterFilter
import com.furina.classtools.features.roster.RosterLogic
import com.furina.classtools.features.roster.RosterMember
import com.furina.classtools.features.roster.RosterRow
import com.furina.classtools.features.roster.RosterState
import com.furina.classtools.features.roster.RosterTab
import com.furina.classtools.ui.components.CtLoading
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.CtLightTopBar
import com.furina.classtools.ui.components.EmptyState
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.tool.CtToolCard
import com.furina.classtools.ui.components.tool.CtToolChip
import com.furina.classtools.ui.components.tool.CtToolField
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.rememberReduceMotion
import kotlinx.coroutines.launch

/**
 * 名单页（原生，2026-11-14 task-54 起只剩「名册」）。
 *
 * 契约：`docs/api/USERDATA_V6.md` 的 `roster` section + `docs/BACKEND_CONTRACT.md` §7.7：
 * - 名单来源 `class_members`（`ClassMembersRepository`，6h 缓存），只显示姓名/座号/职务/徽章，**不显示学号**；
 * - 「班委 / 团员 / 全班」三分类 = [HorizontalPager]：左右滑动跟手 + spring 吸附，
 *   顶部分类 pill 与 pager 双向联动（点击 `animateScrollToPage`；`currentPage/currentPageOffsetFraction`
 *   驱动选中与指示条跟手）；reduceMotion 时禁用拖拽、点击瞬时切；
 * - 每页保留搜索（跨分类共享）、只看点名名单筛选、勾选加入点名名单、全选点名、下拉刷新、
 *   空/加载/错误/刷新错误条；
 * - 勾选写 `userdata.roster`（`rosterSel/rollSel/rollSt/rollNames` 全部由 [RosterLogic]/[RosterCodec] 复用，
 *   与点名页 [RollCallScreen] 共用 [rememberRosterSync]），同步/姓名迁移语义不变；
 * - 隐私：不导出名单、不显示学号；搜索仅本机。
 *
 * 接线（Lead 负责 MainNavHost）：
 * ```kotlin
 * "roster" -> RosterScreen(
 *     onBack = { popTool(toolId) },
 *     session = session,
 *     classMembersRepository = container.classMembersRepository,
 *     userDataRepository = container.userDataRepository,
 *     classLeagueRepository = container.classLeagueRepository,
 * )
 * ```
 * 点名页见同包 [RollCallScreen]（`"roll"` 路由）。
 *
 * @param initialMembers 预览注入：班级名单
 * @param initialRoster 预览注入：roster section 内存态
 * @param initialCategory 预览注入：默认分类（= 初始 pager 页）
 * @param initialLeagueMembers 预览注入：团员名单
 * @param skipLeagueLoad 预览/测试：跳过团员名单请求
 * @param skipLoad 预览/测试：跳过班级名单请求
 * @param previewCategoryOffset 预览：三分类左右滑动的中间帧（0.42 = 停在「班委/团员」之间；null = 正常渲染）
 * @param forcedErrorMessage 预览注入：错误态
 * @param initialTab 兼容参数：拆分前的「名册 ↔ 点名」页签；点名已独立成 [RollCallScreen]，
 *   名册页不再切页，此参数不生效（仅为既有预览调用保持源码兼容）
 */
@Composable
fun RosterScreen(
    onBack: () -> Unit,
    session: Session?,
    classMembersRepository: ClassMembersRepository?,
    userDataRepository: UserDataRepository?,
    modifier: Modifier = Modifier,
    initialMembers: List<RosterMember> = emptyList(),
    initialRoster: RosterState? = null,
    initialCategory: MemberCategory = MemberCategory.ALL,
    initialLeagueMembers: List<RosterMember> = emptyList(),
    classLeagueRepository: ClassLeagueRepository? = null,
    skipLeagueLoad: Boolean = false,
    skipLoad: Boolean = false,
    previewCategoryOffset: Float? = null,
    forcedErrorMessage: String? = null,
    initialTab: RosterTab = RosterTab.ROSTER,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val (rosterState, writeRoster) = rememberRosterSync(
        userDataRepository = userDataRepository,
        initialRoster = initialRoster,
    )
    val membersState = rememberRosterMembersState(
        session = session,
        repository = classMembersRepository,
        userDataRepository = userDataRepository,
        initialMembers = initialMembers,
        skipLoad = skipLoad,
        forcedErrorMessage = forcedErrorMessage,
        unauthenticatedMessage = "请先完成身份验证后再查看名册",
    )
    val members = membersState.members

    var leagueMembers by remember(initialLeagueMembers) { mutableStateOf(initialLeagueMembers) }
    var leagueLoading by remember { mutableStateOf(false) }
    var leagueError by remember { mutableStateOf<String?>(null) }
    var leagueReloadToken by remember { mutableIntStateOf(0) }

    var showOnlyInRoll by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val categories = MemberCategory.entries
    // 动效：系统「移除动画」时禁用 pager 拖拽并改成瞬时切页；预览注入滑动中间帧时不看系统开关。
    val systemReduceMotion = rememberReduceMotion()
    val reduceMotion = systemReduceMotion && previewCategoryOffset == null
    val categoryPagerState = rememberPagerState(
        initialPage = categories.indexOf(initialCategory).coerceAtLeast(0),
        initialPageOffsetFraction = (previewCategoryOffset ?: 0f).coerceIn(-0.5f, 0.5f),
    ) { categories.size }
    val category = categories.getOrNull(categoryPagerState.currentPage) ?: initialCategory

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    // 团员名单：滑到「团员」分类时懒加载（失败降级缓存并提示；无 sid）。
    LaunchedEffect(category, leagueReloadToken, skipLeagueLoad) {
        if (skipLeagueLoad || category != MemberCategory.LEAGUE) return@LaunchedEffect
        val repository = classLeagueRepository
        if (repository == null) {
            leagueError = "团员名单未配置"
            return@LaunchedEffect
        }
        leagueLoading = true
        leagueError = null
        when (val result = repository.load(force = leagueReloadToken > 0)) {
            is ClassLeagueResult.Success -> {
                leagueMembers = result.members.map { member ->
                    RosterMember(seat = member.seat, name = member.name)
                }
                leagueError = result.warning
            }

            is ClassLeagueResult.Failure -> {
                val cached = runCatching { repository.cached() }.getOrNull().orEmpty()
                if (cached.isNotEmpty()) {
                    leagueMembers = cached.map { member ->
                        RosterMember(seat = member.seat, name = member.name)
                    }
                    leagueError = "显示缓存的团员名单：${result.message}"
                } else {
                    leagueError = result.message
                }
            }
        }
        leagueLoading = false
    }

    val committeeCount = remember(members) { members.count { it.isCommittee } }

    // 沉浸光感顶栏：名册列表滚动量驱动顶栏浮层渐显。
    val ctTopBarLazyState = androidx.compose.foundation.lazy.rememberLazyListState()
    val ctTopBarScrollPx = if (ctTopBarLazyState.firstVisibleItemIndex > 0) {
        com.furina.classtools.ui.theme.CT_TOP_BAR_FULL_SCROLL_PX
    } else {
        ctTopBarLazyState.firstVisibleItemScrollOffset.toFloat()
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                CtLightTopBar(
                    scrollPx = ctTopBarScrollPx,
                    title = "名单 · 名册",
                    subtitle = buildString {
                        if (members.isNotEmpty()) {
                            append("${members.size} 人")
                            if (committeeCount > 0) append(" · 班委 $committeeCount")
                            if (leagueMembers.isNotEmpty()) append(" · 团员 ${leagueMembers.size}")
                        }
                        if (membersState.fromCache) {
                            if (isNotEmpty()) append(" · ")
                            append("本机缓存")
                        }
                        val rollCount = rosterState.rollSel.size
                        if (rollCount > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("点名 $rollCount 人")
                        }
                    },
                    onBack = onBack,
                    actions = {
                        IconButton(
                            onClick = {
                                membersState.reload()
                                leagueReloadToken += 1
                            },
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新名册")
                        }
                    },
                )

                RosterCategoryTabs(
                    pagerState = categoryPagerState,
                    counts = listOf(committeeCount, leagueMembers.size, members.size),
                    reduceMotion = reduceMotion,
                )

                CtToolField(
                    label = "搜索",
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = "搜索姓名 / 座号",
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CtSpace.controlGap),
                ) {
                    CtToolChip(
                        text = "已加入点名 ${rosterState.rollSel.size}",
                        selected = showOnlyInRoll,
                        onClick = { showOnlyInRoll = !showOnlyInRoll },
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            writeRoster(RosterLogic.selectAll(rosterState, members))
                            notify("已全选加入点名名单")
                        },
                    ) { Text("全选点名") }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (membersState.phase) {
                        RosterPhase.LOADING -> CtLoading(
                            modifier = Modifier.align(Alignment.Center),
                            text = "加载班级名单…",
                        )

                        RosterPhase.ERROR -> RosterErrorContent(
                            message = membersState.errorMessage ?: "名单加载失败",
                            onRetry = { membersState.reload() },
                            modifier = Modifier.fillMaxSize(),
                        )

                        RosterPhase.READY -> PullToRefreshBox(
                            isRefreshing = membersState.isRefreshing,
                            onRefresh = {
                                membersState.refresh()
                                leagueReloadToken += 1
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            HorizontalPager(
                                state = categoryPagerState,
                                modifier = Modifier.fillMaxSize(),
                                userScrollEnabled = !reduceMotion,
                                key = { page -> categories[page].name },
                            ) { pageIndex ->
                                val pageCategory = categories[pageIndex]
                                val rows = remember(
                                    pageCategory,
                                    members,
                                    leagueMembers,
                                    query,
                                    showOnlyInRoll,
                                    rosterState,
                                ) {
                                    val base = when (pageCategory) {
                                        MemberCategory.COMMITTEE -> members.filter { it.isCommittee }
                                        MemberCategory.LEAGUE -> leagueMembers
                                        MemberCategory.ALL -> members
                                    }
                                    val filtered = RosterLogic.filterRows(base, query, RosterFilter.ALL, rosterState)
                                    if (showOnlyInRoll) filtered.filter { it.inRoll } else filtered
                                }
                                RosterCategoryPage(
                                    category = pageCategory,
                                    rows = rows,
                                    query = query,
                                    showOnlyInRoll = showOnlyInRoll,
                                    leagueLoading = leagueLoading,
                                    leagueError = leagueError,
                                    onRetryLeague = { leagueReloadToken += 1 },
                                    onToggleRoll = { seat ->
                                        writeRoster(RosterLogic.toggleMemberInRoll(rosterState, seat, members))
                                    },
                                    listState = ctTopBarLazyState,
                                )
                            }
                        }
                    }

                    if (membersState.phase == RosterPhase.READY && membersState.errorMessage != null) {
                        RosterRefreshErrorBanner(
                            message = membersState.errorMessage.orEmpty(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        )
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
}

// -----------------------------------------------------------------------------
// 分类 pill（班委 / 团员 / 全班）+ 跟手指示条（与 pager 双向联动）
// -----------------------------------------------------------------------------

@Composable
private fun RosterCategoryTabs(
    pagerState: PagerState,
    counts: List<Int>,
    reduceMotion: Boolean,
) {
    val scope = rememberCoroutineScope()
    val categories = MemberCategory.entries
    val total = categories.size
    // pager 的「连续页位置」：跟手拖动/spring 吸附期间指示条一起走。
    val progress = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, (total - 1).toFloat())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEachIndexed { index, option ->
                CtToolChip(
                    text = "${option.label} ${counts.getOrElse(index) { 0 }}",
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch {
                            // reduceMotion：立即切；正常：spring 吸附约 300ms。
                            if (reduceMotion) pagerState.scrollToPage(index) else pagerState.animateScrollToPage(index)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (total > 1) {
            Spacer(Modifier.height(3.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
            ) {
                val slot = maxWidth * (1f / total)
                Box(
                    modifier = Modifier
                        .offset(x = slot * progress)
                        .width(slot)
                        .height(3.dp)
                        .clip(CtShapeTokens.pill)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 单个分类页（列表 / 空态 / 团员加载与错误）
// -----------------------------------------------------------------------------

@Composable
private fun RosterCategoryPage(
    category: MemberCategory,
    rows: List<RosterRow>,
    query: String,
    showOnlyInRoll: Boolean,
    leagueLoading: Boolean,
    leagueError: String?,
    onRetryLeague: () -> Unit,
    onToggleRoll: (Int) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 880.dp)
                .fillMaxWidth(),
        ) {
            when {
                category == MemberCategory.LEAGUE && leagueLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CtLoading(text = "加载团员名单…")
                }

                category == MemberCategory.LEAGUE && leagueError != null && rows.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.height(CtSpace.sm))
                    Text(
                        text = "团员名单加载失败",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = leagueError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onRetryLeague) { Text("重试") }
                }

                rows.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Rounded.Groups,
                        title = when {
                            query.isNotBlank() -> "未找到匹配成员"
                            category == MemberCategory.LEAGUE -> "团员名单为空"
                            category == MemberCategory.COMMITTEE -> "暂无班委"
                            showOnlyInRoll -> "暂无点名成员"
                            else -> "名单为空"
                        },
                        message = when {
                            query.isNotBlank() -> "换个姓名/座号试试"
                            category == MemberCategory.LEAGUE -> "团员名单同步后自动出现"
                            category == MemberCategory.COMMITTEE -> "班委角色来自班级名单"
                            showOnlyInRoll -> "取消筛选查看全部成员"
                            else -> "请检查身份验证或稍后重试"
                        },
                    )
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (category == MemberCategory.LEAGUE && leagueError != null) {
                        item(key = "league-warning") {
                            Text(
                                text = leagueError,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                    items(rows, key = { it.member.seat }) { row ->
                        MemberRow(row = row, onToggleRoll = { onToggleRoll(row.member.seat) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    row: RosterRow,
    onToggleRoll: () -> Unit,
) {
    // v6：统一卡片外壳 + 两行布局（姓名行 + 职务/状态胶囊行，避免大字体/窄屏胶囊换行挤压）
    CtToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.member.seat.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(32.dp),
                )
                Text(
                    text = row.member.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Checkbox(
                    checked = row.inRoll,
                    onCheckedChange = { onToggleRoll() },
                    modifier = Modifier.size(48.dp),
                )
            }
            val hasTags = row.member.role.isNotBlank() || row.member.badge.isNotBlank() || row.inRoll
            if (hasTags) {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.padding(start = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (row.member.role.isNotBlank()) {
                        CtPill(
                            text = row.member.role,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    if (row.member.badge.isNotBlank()) {
                        CtPill(text = row.member.badge)
                    }
                    if (row.inRoll) {
                        CtPill(
                            text = "点名中",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}
