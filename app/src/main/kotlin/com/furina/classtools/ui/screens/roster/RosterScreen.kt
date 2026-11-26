@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.roster

import com.furina.classtools.ui.components.CtPrimaryTabs


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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.member.ClassLeagueRepository
import com.furina.classtools.data.member.ClassLeagueResult
import com.furina.classtools.data.member.ClassMembersRepository
import com.furina.classtools.data.member.MemberCategory
import com.furina.classtools.data.session.Session
import com.furina.classtools.features.roster.RosterMember
import com.furina.classtools.ui.components.CtLoading
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.CtLightTopBar
import com.furina.classtools.ui.components.EmptyState
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.tool.CtToolCard
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.rememberReduceMotion
import kotlinx.coroutines.launch
import com.furina.classtools.data.member.RosterMemberSource
import com.furina.classtools.ui.components.RosterSearchField
import com.furina.classtools.ui.components.rememberRosterSearch

/** 班级名册只展示本班可见的成员资料。 */
@Composable
fun RosterScreen(
    onBack: () -> Unit,
    session: Session?,
    classMembersRepository: ClassMembersRepository?,
    memberSource: RosterMemberSource? = null,
    modifier: Modifier = Modifier,
    initialMembers: List<RosterMember> = emptyList(),
    initialCategory: MemberCategory = MemberCategory.ALL,
    initialLeagueMembers: List<RosterMember> = emptyList(),
    classLeagueRepository: ClassLeagueRepository? = null,
    skipLeagueLoad: Boolean = false,
    skipLoad: Boolean = false,
    previewCategoryOffset: Float? = null,
    forcedErrorMessage: String? = null,
) {
    val membersState = rememberRosterMembersState(
        session = session,
        repository = classMembersRepository,
        userDataRepository = null,
        initialMembers = initialMembers,
        skipLoad = skipLoad,
        forcedErrorMessage = forcedErrorMessage,
        unauthenticatedMessage = "请先完成身份验证后再查看名册",
    )
    val members = membersState.members

    var leagueMembers by remember(session?.sid, initialLeagueMembers) { mutableStateOf(initialLeagueMembers) }
    var leagueLoading by remember(session?.sid) { mutableStateOf(false) }
    var leagueError by remember(session?.sid) { mutableStateOf<String?>(null) }
    var leagueReloadToken by remember(session?.sid) { mutableIntStateOf(0) }

    val search = rememberRosterSearch(memberSource)
    val query = search.query
    val matches = search.matchedSeats

    val categories = MemberCategory.entries
    // 动效：系统「移除动画」时禁用 pager 拖拽并改成瞬时切页；预览注入滑动中间帧时不看系统开关。
    val systemReduceMotion = rememberReduceMotion()
    val reduceMotion = systemReduceMotion && previewCategoryOffset == null
    val categoryPagerState = rememberPagerState(
        initialPage = categories.indexOf(initialCategory).coerceAtLeast(0),
        initialPageOffsetFraction = (previewCategoryOffset ?: 0f).coerceIn(-0.5f, 0.5f),
    ) { categories.size }
    val category = categories.getOrNull(categoryPagerState.currentPage) ?: initialCategory

    // 团员名单：滑到「团员」分类时懒加载（失败降级缓存并提示；无 sid）。
    LaunchedEffect(session?.sid, category, leagueReloadToken, skipLeagueLoad) {
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

    val leagueDirectory = remember(members, leagueMembers) {
        val seats = leagueMembers.mapTo(hashSetOf()) { it.seat }
        members.filter { it.seat in seats }
    }
    val committeeCount = remember(members) { members.count { it.isCommittee } }

    // 沉浸光感顶栏：名册列表滚动量驱动顶栏浮层渐显。
    val listStates = categories.map { androidx.compose.foundation.lazy.rememberLazyListState() }
    val ctTopBarLazyState = listStates[categoryPagerState.currentPage]
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
                    title = "班级名单",
                    subtitle = buildString {
                        if (members.isNotEmpty()) {
                            append("${members.size} 人")
                            if (committeeCount > 0) append(" · 班委 $committeeCount")
                            if (leagueDirectory.isNotEmpty()) append(" · 团员 ${leagueDirectory.size}")
                        }
                        if (membersState.fromCache) {
                            if (isNotEmpty()) append(" · ")
                            append("本机缓存")
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
                    counts = listOf(committeeCount, leagueDirectory.size, members.size),
                    reduceMotion = reduceMotion,
                )

                RosterSearchField(search, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))

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
                                val rows = remember(pageCategory, members, leagueDirectory, query, matches) {
                                    val group = when (pageCategory) {
                                        MemberCategory.COMMITTEE -> members.filter { it.isCommittee }
                                        MemberCategory.LEAGUE -> leagueDirectory
                                        MemberCategory.ALL -> members
                                    }
                                    group.filter { it.asEntry().matches(query) || it.seat in matches }
                                }
                                RosterCategoryPage(
                                    category = pageCategory,
                                    rows = rows,
                                    query = query,
                                    leagueLoading = leagueLoading,
                                    leagueError = leagueError,
                                    onRetryLeague = { leagueReloadToken += 1 },
                                    listState = listStates[pageIndex],
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        CtPrimaryTabs(
            labels = categories.mapIndexed { index, option ->
                "${option.label} ${counts.getOrElse(index) { 0 }}"
            },
            selectedIndex = pagerState.currentPage,
            onSelect = { index ->
                scope.launch {
                    if (reduceMotion) pagerState.scrollToPage(index) else pagerState.animateScrollToPage(index)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// -----------------------------------------------------------------------------
// 单个分类页（列表 / 空态 / 团员加载与错误）
// -----------------------------------------------------------------------------

@Composable
private fun RosterCategoryPage(
    category: MemberCategory,
    rows: List<RosterMember>,
    query: String,
    leagueLoading: Boolean,
    leagueError: String?,
    onRetryLeague: () -> Unit,
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
                            else -> "名单为空"
                        },
                        message = when {
                            query.isNotBlank() -> "试试姓名、座号或职务"
                            category == MemberCategory.LEAGUE -> "团员名单同步后自动出现"
                            category == MemberCategory.COMMITTEE -> "班委角色来自班级名单"
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
                    items(rows, key = { it.seat }) { member ->
                        MemberRow(member)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(member: RosterMember) {
    val scheme = MaterialTheme.colorScheme
    CtToolCard(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.CircleShape,
                color = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Text(member.seat.toString().padStart(2, '0'), style = MaterialTheme.typography.titleSmall)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(member.name, style = MaterialTheme.typography.titleMedium)
            }
            val label = member.role.ifBlank { member.badge }
            if (label.isNotBlank()) CtPill(label, modifier = Modifier.widthIn(max = 116.dp))
        }
    }
}
