package com.furina.classtools.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furina.classtools.data.visits.VisitAccount
import com.furina.classtools.data.visits.VisitRange
import com.furina.classtools.data.visits.VisitRow
import com.furina.classtools.data.visits.VisitStats
import com.furina.classtools.data.visits.VisitsRepository
import com.furina.classtools.data.visits.VisitsResult
import com.furina.classtools.data.visits.VisitsSnapshot
import com.furina.classtools.ui.components.CtLoading
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.EmptyState
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.expressive.CtChipOption
import com.furina.classtools.ui.components.expressive.CtFilterChipRow
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtText
import com.furina.classtools.ui.theme.CtType
import com.furina.classtools.ui.theme.rememberHarmonyColors
import java.time.LocalDate

private enum class StatsPhase { LOADING, READY, FAILED }

/**
 * 访问统计页（对齐网页 `#s-stats` / 小程序 `pages/stats` / Windows `StatsPage`）。
 *
 * 口径（唯一契约 `web/统计多端_最新登录_契约_20261118.md` §2）：
 * - 仅管理员可见（服务端 RLS 再校验 token；非管理员不发请求）；
 * - 默认今天；按访问次数倒序（次数相同按最近访问时间倒序）；
 * - 时间 `MM/dd HH:mm`；账号行显示最近登录端；
 * - 过滤官方/9999 段测试身份与匿名行；不显示学号。
 *
 * @param skipLoad 预览/单测：跳过加载
 * @param initialRows 预览注入
 * @param forcedErrorMessage 预览注入：失败态
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
) {
    val colors = rememberHarmonyColors()
    var rows by remember { mutableStateOf(initialRows) }
    var range by remember { mutableStateOf(VisitRange.TODAY) }
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
            CtTopBar(
                title = "统计",
                onBack = onBack,
                actions = {
                    com.furina.classtools.ui.components.CtIconAction(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "刷新",
                        onClick = { reloadToken += 1 },
                    )
                },
            )

            Box(modifier = Modifier.weight(1f)) {
                if (!isAdmin) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        EmptyState(
                            icon = Icons.Rounded.Lock,
                            title = "仅管理员可见",
                            message = "当前身份不是管理员，无法查看访问统计。",
                        )
                    }
                    return@Box
                }

                when (phase) {
                    StatsPhase.LOADING -> CtLoading(
                        modifier = Modifier.align(Alignment.Center),
                        text = "加载访问统计…",
                    )

                    StatsPhase.FAILED -> Column(
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
                        androidx.compose.material3.TextButton(onClick = { reloadToken += 1 }) {
                            Text("重试")
                        }
                    }

                    StatsPhase.READY -> StatsContent(
                        snapshot = snapshot,
                        range = range,
                        onRangeChange = { range = it },
                        colors = colors,
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
    colors: com.furina.classtools.ui.theme.HarmonyColors,
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryCell("${snapshot.accounts.size}", "访问账号", Modifier.weight(1f))
                SummaryCell("${snapshot.total}", "访问次数", Modifier.weight(1f))
                SummaryCell("${snapshot.activeToday}", "今日活跃", Modifier.weight(1f))
            }
        }

        if (snapshot.accounts.isEmpty()) {
            item("empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyState(
                        icon = Icons.Rounded.Insights,
                        title = if (range == VisitRange.TODAY) "今天暂无访问" else "近 7 天暂无访问",
                        message = "同学完成身份验证并打开工具后，这里会按账号出现统计。",
                    )
                }
            }
        } else {
            itemsIndexed(snapshot.accounts, key = { _, row -> row.sid }) { index, row ->
                AccountRow(index + 1, row)
            }
        }

        item("note") {
            Text(
                text = "按访问次数排序（次数相同按最近访问）；只统计完成班级身份验证后的访问，同一账号同一登录端每小时记一次；已忽略测试账号。",
                style = CtText.label,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun SummaryCell(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = rememberHarmonyColors()
    Surface(
        modifier = modifier,
        color = colors.card,
        shape = RoundedCornerShape(CtRadius.cardCompact),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(
                text = value,
                fontSize = CtType.titleSize,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
            )
            Text(
                text = label,
                style = CtText.label,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AccountRow(rank: Int, row: VisitAccount) {
    val colors = rememberHarmonyColors()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.card,
        shape = RoundedCornerShape(CtRadius.cardCompact),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = if (rank <= 3) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (rank <= 3) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$rank",
                        fontSize = CtType.captionSize,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = CtText.body,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = row.detail,
                    style = CtText.label,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = "${row.count} 次",
                fontSize = CtType.labelSize,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}
