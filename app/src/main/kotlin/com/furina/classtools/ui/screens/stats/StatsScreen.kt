@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.furina.classtools.ui.screens.stats

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.visits.VisitAccount
import com.furina.classtools.data.visits.VisitClientStat
import com.furina.classtools.data.visits.VisitClientStyle
import com.furina.classtools.data.visits.VisitDeviceStat
import com.furina.classtools.data.visits.VisitRange
import com.furina.classtools.data.visits.VisitRow
import com.furina.classtools.data.visits.VisitStats
import com.furina.classtools.data.visits.VisitTrendPoint
import com.furina.classtools.data.visits.VisitVerStat
import com.furina.classtools.data.visits.VisitsRepository
import com.furina.classtools.data.visits.VisitsResult
import com.furina.classtools.data.visits.VisitsSnapshot
import com.furina.classtools.ui.components.CtIconAction
import com.furina.classtools.ui.components.CtLoading
import com.furina.classtools.ui.components.EmptyState
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.ctTopBarRaw
import com.furina.classtools.ui.components.expressive.CtChipOption
import com.furina.classtools.ui.components.expressive.CtFilterChipRow
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.CtTypeScale
import com.furina.classtools.ui.theme.rememberCtColors
import java.time.LocalDate

private enum class StatsPhase { LOADING, READY, FAILED }

/** 占位符不显示空图表：趋势卡最小绘图高度（dp）。 */
private val TREND_PLOT_HEIGHT = 88.dp

/**
 * 访问统计页（五端统一页面结构：`web/统计多端_详细统计_契约_20261120.md` §4）。
 *
 * 从上到下：标题 → 今天/近 7 天 chips → 摘要 4 格 → 趋势卡 → 登录端卡 →
 * 版本卡/设备卡 → 账号明细（可展开）→ 脚注。
 *
 * 口径（契约 §2/§3）：
 * - 仅管理员可见（非管理员不发 `visits` 请求、显示「仅管理员可见」）；
 * - 默认今天；一次拉近 7 天，页面内切换不重新请求；
 * - 不显示学号/did，测试账号/匿名行被 [VisitStats] 过滤；
 * - 空态只显示摘要 0 + 文案，不渲染空图表。
 *
 * @param skipLoad 预览/单测：跳过加载
 * @param initialRows 预览注入
 * @param forcedErrorMessage 预览注入：失败态
 * @param initialRange 预览注入：初始范围（正式入口默认今天）
 */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    isAdmin: Boolean,
    repository: VisitsRepository?,
    modifier: Modifier = Modifier,
    skipLoad: Boolean = false,
    initialRows: List<VisitRow> = emptyList(),
    forcedErrorMessage: String? = null,
    initialRange: VisitRange = VisitRange.TODAY,
) {
    var rows by remember { mutableStateOf(initialRows) }
    var range by remember { mutableStateOf(initialRange) }
    var phase by remember {
        mutableStateOf(
            when {
                forcedErrorMessage != null -> StatsPhase.FAILED
                skipLoad -> StatsPhase.READY
                else -> StatsPhase.LOADING
            },
        )
    }
    var errorMessage by remember { mutableStateOf(forcedErrorMessage) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var expandedSid by remember { mutableStateOf<String?>(null) }

    // 每次进入页面重置「今天」：remember 初始化即今天；切换范围时收起展开行。
    LaunchedEffect(range) { expandedSid = null }

    LaunchedEffect(reloadToken, isAdmin) {
        if (!isAdmin) {
            rows = emptyList()
            phase = StatsPhase.READY
            return@LaunchedEffect
        }
        if (skipLoad || forcedErrorMessage != null) {
            phase = StatsPhase.READY
            return@LaunchedEffect
        }
        val repo = repository
        if (repo == null) {
            errorMessage = "功能尚未接线"
            phase = StatsPhase.FAILED
            return@LaunchedEffect
        }
        if (rows.isEmpty()) phase = StatsPhase.LOADING
        // 契约 §2：from 固定「今天 - 6 天」的 YYYY-MM-DD-00，一次拉 7 天。
        val from = LocalDate.now().minusDays(6).toString() + "-00"
        when (val result = repo.pull(from)) {
            is VisitsResult.Success -> {
                rows = result.rows
                errorMessage = null
                phase = StatsPhase.READY
            }

            is VisitsResult.Failure -> {
                errorMessage = result.message
                phase = if (rows.isEmpty()) StatsPhase.FAILED else StatsPhase.READY
            }
        }
    }

    val snapshot = remember(rows, range) { VisitStats.build(rows, range) }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            ctTopBarRaw(
                title = "访问统计",
                subtitle = "仅管理员 · 按访问次数 · 不显示学号",
                onBack = onBack,
                actions = {
                    CtIconAction(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "刷新",
                        onClick = { reloadToken += 1 },
                    )
                },
            )

            Box(modifier = Modifier.weight(1f)) {
                when {
                    !isAdmin -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            icon = Icons.Rounded.Lock,
                            title = "仅管理员可见",
                            message = "当前身份不是管理员，无法查看访问统计。",
                        )
                    }

                    phase == StatsPhase.LOADING -> CtLoading(
                        modifier = Modifier.align(Alignment.Center),
                        text = "加载访问统计…",
                    )

                    phase == StatsPhase.FAILED -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        EmptyState(
                            icon = Icons.Rounded.Insights,
                            title = "读取统计失败",
                            message = errorMessage ?: "请稍后重试",
                        )
                        TextButton(onClick = { reloadToken += 1 }) {
                            Text("重试")
                        }
                    }

                    else -> StatsContent(
                        snapshot = snapshot,
                        range = range,
                        onRangeChange = { range = it },
                        expandedSid = expandedSid,
                        onToggleExpand = { sid ->
                            expandedSid = if (expandedSid == sid) null else sid
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsContent(
    snapshot: VisitsSnapshot,
    range: VisitRange,
    onRangeChange: (VisitRange) -> Unit,
    expandedSid: String?,
    onToggleExpand: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 窄屏 2×2 摘要、版本/设备上下；宽屏 1×4 摘要、版本/设备双栏。
        val wide = maxWidth >= 600.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("range") {
                CtFilterChipRow(
                    options = listOf(
                        CtChipOption(VisitRange.TODAY.name, "今天"),
                        CtChipOption(VisitRange.WEEK.name, "近 7 天"),
                    ),
                    selectedKey = range.name,
                    onSelect = { key ->
                        VisitRange.entries.firstOrNull { it.name == key }?.let(onRangeChange)
                    },
                )
            }

            item("summary") {
                SummarySection(snapshot = snapshot, wide = wide)
            }

            if (!snapshot.hasData) {
                item("empty") {
                    EmptyState(
                        icon = Icons.Rounded.Insights,
                        title = if (range == VisitRange.TODAY) "今天暂无访问" else "近 7 天暂无访问",
                        message = "同学完成身份验证并打开工具后，这里会按账号出现统计。",
                    )
                }
            } else {
                item("trend") {
                    TrendCard(points = snapshot.trend, range = range)
                }

                item("clients") {
                    ClientsCard(clients = snapshot.clients)
                }

                item("distributions") {
                    if (wide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            VersionsCard(
                                versions = snapshot.versions,
                                modifier = Modifier.weight(1f),
                            )
                            DevicesCard(
                                devices = snapshot.devices,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            VersionsCard(versions = snapshot.versions)
                            DevicesCard(devices = snapshot.devices)
                        }
                    }
                }

                item("accounts-header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "账号明细",
                            style = CtTypeScale.title16,
                            color = rememberCtColors().textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${snapshot.accountCount} 个账号",
                            style = CtTypeScale.caption,
                            color = rememberCtColors().textTertiary,
                        )
                    }
                }

                itemsIndexed(snapshot.accounts, key = { _, account -> account.sid }) { index, account ->
                    AccountCard(
                        rank = index + 1,
                        account = account,
                        maxCount = snapshot.maxAccountCount.coerceAtLeast(1),
                        expanded = expandedSid == account.sid,
                        onToggle = { onToggleExpand(account.sid) },
                    )
                }
            }

            item("note") {
                Text(
                    text = "按访问次数排序（次数相同按最近访问）；同一账号同一登录端每小时记一次；" +
                        "设备/版本自 2026-11-20 起记录，旧数据为「未记录」；已忽略测试账号。",
                    style = CtTypeScale.caption,
                    color = rememberCtColors().textTertiary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
        }
    }
}

// --------------------------------------------------------------------------- 摘要

@Composable
private fun SummarySection(snapshot: VisitsSnapshot, wide: Boolean) {
    val cells = listOf(
        snapshot.accountCount.toString() to "访问账号",
        snapshot.total.toString() to "访问次数",
        snapshot.activeToday.toString() to "今日活跃",
        snapshot.deviceTotal.toString() to "访问设备",
    )
    if (wide) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            cells.forEach { (value, label) ->
                SummaryCell(value = value, label = label, modifier = Modifier.weight(1f))
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryCell(cells[0].first, cells[0].second, Modifier.weight(1f))
                SummaryCell(cells[1].first, cells[1].second, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryCell(cells[2].first, cells[2].second, Modifier.weight(1f))
                SummaryCell(cells[3].first, cells[3].second, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryCell(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = rememberCtColors()
    Surface(
        modifier = modifier,
        color = colors.surface,
        shape = CtShape.cardCompact,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = value,
                style = CtTypeScale.metric,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = CtTypeScale.caption,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

// --------------------------------------------------------------------------- 趋势

@Composable
private fun TrendCard(points: List<VisitTrendPoint>, range: VisitRange) {
    val colors = rememberCtColors()
    val maxCount = points.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val compact = points.size > 12
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = CtShape.card,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (range == VisitRange.TODAY) "今日时段分布" else "近 7 天趋势",
                    style = CtTypeScale.title16,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "合计 ${points.sumOf { it.count }} 次",
                    style = CtTypeScale.caption,
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.height(12.dp))
            // 固定高度 + Box/Row 内 height(dp) 画柱：不在 LazyColumn 里测量/滚动。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TREND_PLOT_HEIGHT + 40.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                points.forEach { point ->
                    TrendBar(
                        point = point,
                        maxCount = maxCount,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendBar(
    point: VisitTrendPoint,
    maxCount: Int,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = rememberCtColors()
    val ratio = point.count.toFloat() / maxCount.toFloat()
    val barHeight: Dp = when {
        point.count <= 0 -> 2.dp
        else -> (TREND_PLOT_HEIGHT * ratio).coerceAtLeast(3.dp)
    }
    val barColor = when {
        point.highlighted -> colors.primary
        point.count <= 0 -> colors.divider
        else -> colors.primary.copy(alpha = 0.55f)
    }
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            // 今天 24 根柱只给非零值标数字；7 天每根都标。
            text = if (point.count > 0 || !compact) point.count.toString() else "",
            style = CtTypeScale.micro,
            color = if (point.highlighted) colors.primary else colors.textTertiary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(barColor),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = point.label,
            style = CtTypeScale.micro,
            color = if (point.highlighted) colors.primary else colors.textTertiary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// --------------------------------------------------------------------------- 登录端

@Composable
private fun ClientsCard(clients: List<VisitClientStat>) {
    val colors = rememberCtColors()
    if (clients.isEmpty()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = CtShape.card,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "登录端分布",
                    style = CtTypeScale.title16,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "共 ${clients.sumOf { it.count }} 次",
                    style = CtTypeScale.caption,
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.height(12.dp))
            // 堆叠占比条：权重按次数，颜色用契约 §4 固定端色。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape),
            ) {
                clients.forEach { stat ->
                    Box(
                        modifier = Modifier
                            .weight(stat.count.toFloat())
                            .fillMaxHeight()
                            .background(Color(VisitClientStyle.colorOf(stat.code))),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                clients.forEach { stat ->
                    LegendItem(stat)
                }
            }
        }
    }
}

@Composable
private fun LegendItem(stat: VisitClientStat) {
    val colors = rememberCtColors()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(VisitClientStyle.colorOf(stat.code))),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "${stat.label} ${stat.count} · ${stat.percent}%",
            style = CtTypeScale.caption,
            color = colors.textSecondary,
            maxLines = 1,
        )
    }
}

// --------------------------------------------------------------------------- 版本 / 设备

/** 版本卡与设备卡共用的一行（标签 + 次数 + 占比条）。 */
private data class DistributionItem(
    val label: String,
    val tag: String?,
    val count: Int,
    val percent: Int,
    val color: Color,
)

@Composable
private fun VersionsCard(versions: List<VisitVerStat>, modifier: Modifier = Modifier) {
    val colors = rememberCtColors()
    val items = versions.map { stat ->
        DistributionItem(
            label = stat.label,
            tag = null,
            count = stat.count,
            percent = stat.percent,
            color = colors.primary,
        )
    }
    DistributionCard(title = "版本分布", items = items, modifier = modifier)
}

@Composable
private fun DevicesCard(devices: List<VisitDeviceStat>, modifier: Modifier = Modifier) {
    val items = devices.map { stat ->
        DistributionItem(
            label = stat.label,
            tag = stat.clientLabel.takeIf { it.isNotEmpty() },
            count = stat.count,
            percent = stat.percent,
            color = Color(VisitClientStyle.colorOf(stat.client)),
        )
    }
    DistributionCard(title = "设备分布", items = items, modifier = modifier)
}

@Composable
private fun DistributionCard(title: String, items: List<DistributionItem>, modifier: Modifier = Modifier) {
    val colors = rememberCtColors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        shape = CtShape.card,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = CtTypeScale.title16,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "共 ${items.sumOf { it.count }} 次",
                    style = CtTypeScale.caption,
                    color = colors.textTertiary,
                )
            }
            if (items.isEmpty()) {
                Text(
                    text = "暂无记录",
                    style = CtTypeScale.caption,
                    color = colors.textTertiary,
                )
            } else {
                items.forEach { item -> DistributionRow(item) }
            }
        }
    }
}

@Composable
private fun DistributionRow(item: DistributionItem) {
    val colors = rememberCtColors()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            item.tag?.let { tag ->
                MiniTag(text = tag, color = item.color)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = item.label,
                style = CtTypeScale.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${item.count}",
                style = CtTypeScale.captionMedium,
                color = colors.textPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${item.percent}%",
                style = CtTypeScale.micro,
                color = colors.textTertiary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(34.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        RatioBar(fraction = item.percent / 100f, color = item.color)
    }
}

// --------------------------------------------------------------------------- 账号明细

@Composable
private fun AccountCard(
    rank: Int,
    account: VisitAccount,
    maxCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = rememberCtColors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = CtMotion.spatialSize),
        color = colors.surface,
        shape = CtShape.card,
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                RankBadge(rank)

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = account.name,
                            style = CtTypeScale.title16,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${account.count} 次",
                            style = CtTypeScale.label,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary,
                        )
                    }

                    Spacer(Modifier.height(5.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        MiniTag(
                            text = account.clientText,
                            color = Color(VisitClientStyle.colorOf(account.client)),
                        )
                        MiniTag(
                            text = account.verText,
                            color = colors.textSecondary,
                            container = colors.containerHigh,
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = account.devText,
                        style = CtTypeScale.caption,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(8.dp))
                    // 次数条分母 = 当前范围最大账号次数（至少 1，契约 §3.4）。
                    RatioBar(
                        fraction = account.count.toFloat() / maxCount.toFloat(),
                        color = colors.primary,
                        height = 5.dp,
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "收起详情" else "展开详情",
                    tint = colors.textTertiary,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(20.dp),
                )
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = colors.divider)
                Spacer(Modifier.height(12.dp))
                AccountDetail(account)
            }
        }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    val colors = rememberCtColors()
    val top = rank <= 3
    Surface(
        modifier = Modifier.size(28.dp),
        shape = CircleShape,
        color = if (top) colors.primaryContainer else colors.containerHigh,
        contentColor = if (top) colors.onPrimaryContainer else colors.textSecondary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = rank.toString(),
                style = CtTypeScale.micro,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AccountDetail(account: VisitAccount) {
    val colors = rememberCtColors()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = account.detailText,
            style = CtTypeScale.caption,
            color = colors.textSecondary,
        )
        if (account.clients.isNotEmpty()) {
            DetailLine(
                label = "登录端",
                value = account.clients.joinToString(" · ") { "${it.label} ${it.count}" },
            )
        }
        if (account.versions.isNotEmpty()) {
            DetailLine(
                label = "版本",
                value = account.versions.joinToString(" · ") { "${it.label} ×${it.count}" },
            )
        }
        if (account.devices.isNotEmpty()) {
            Text(
                text = "设备",
                style = CtTypeScale.captionMedium,
                color = colors.textTertiary,
            )
            account.devices.forEach { device -> DeviceDetailRow(device) }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    val colors = rememberCtColors()
    Row {
        Text(
            text = "$label ",
            style = CtTypeScale.captionMedium,
            color = colors.textTertiary,
        )
        Text(
            text = value,
            style = CtTypeScale.caption,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun DeviceDetailRow(device: VisitDeviceStat) {
    val colors = rememberCtColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (device.clientLabel.isNotEmpty()) {
            MiniTag(
                text = device.clientLabel,
                color = Color(VisitClientStyle.colorOf(device.client)),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = device.label,
            style = CtTypeScale.caption,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${device.count} 次",
            style = CtTypeScale.micro,
            color = colors.textTertiary,
            maxLines = 1,
        )
        if (device.lastTs > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "最近 ${VisitStats.formatTime(device.lastTs)}",
                style = CtTypeScale.micro,
                color = colors.textTertiary,
                maxLines = 1,
            )
        }
    }
}

// --------------------------------------------------------------------------- 小组件

/** 小胶囊标签（端标签 / 版本标签）。 */
@Composable
private fun MiniTag(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    container: Color = color.copy(alpha = 0.12f),
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = container,
        contentColor = color,
    ) {
        Text(
            text = text,
            style = CtTypeScale.micro,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

/** 占比条 / 次数条（分母已由数据层算好；fraction 0..1）。 */
@Composable
private fun RatioBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val colors = rememberCtColors()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(colors.divider),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(color),
        )
    }
}
