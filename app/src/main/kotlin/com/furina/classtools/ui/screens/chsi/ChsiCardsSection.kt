package com.furina.classtools.ui.screens.chsi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.features.chsi.ChsiCardStatus
import com.furina.classtools.features.chsi.ChsiCatalog
import com.furina.classtools.features.chsi.ChsiDataCardKind
import com.furina.classtools.features.chsi.ChsiDataCardUi
import com.furina.classtools.features.chsi.ChsiNavigationLogic
import com.furina.classtools.features.chsi.ChsiOfficialTool
import com.furina.classtools.features.chsi.ChsiTexts
import com.furina.classtools.ui.theme.CtShapeTokens

/** 手机适配断点：宽度 ≥600dp 用 3 列，否则 2 列（规格 §3 硬指标）。 */
private val ChsiWideBreakpoint = 600.dp

/**
 * 学信网卡片页内容（规格 `_audit/chsi_entry_20261115/CHSI_ENTRY_SPEC.md` §3）。
 *
 * - 状态条：已登录 + 数据更新时间 / 未登录或会话过期 / 读取中（含「去登录」「刷新」）；
 * - 『数据』4 张卡（学籍/学历/学位/在线验证报告；提取不到 → 空态 + 打开官方页面查看）；
 * - 『官方工具』10 个入口卡（内置 WebView 打开官方页面）；
 * - 底部「打开完整学信档案」+「退出学信网登录」。
 *
 * 手机适配（验收硬指标）：窄屏 2 列 / ≥600dp 3 列；LazyColumn；点按 ≥48dp；
 * 卡片文字最多 3 行省略；全部颜色走深浅色 token。
 */
@Composable
fun ChsiCardsContent(
    cards: List<ChsiDataCardUi>,
    loggedIn: Boolean,
    refreshing: Boolean,
    lastUpdated: String?,
    globalError: String?,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onOpenCard: (ChsiDataCardUi) -> Unit,
    onOpenTool: (ChsiOfficialTool) -> Unit,
    onOpenArchive: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = if (maxWidth >= ChsiWideBreakpoint) 3 else 2
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "status") {
                ChsiStatusLine(
                    loggedIn = loggedIn,
                    refreshing = refreshing,
                    lastUpdated = lastUpdated,
                    globalError = globalError,
                    onLogin = onLogin,
                    onRefresh = onRefresh,
                )
            }
            item(key = "data-header") { ChsiSectionHeader(ChsiTexts.SECTION_DATA) }
            items(
                items = cards.chunked(columns),
                key = { row -> "data-" + row.joinToString("-") { it.kind.name } },
            ) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { card ->
                        ChsiDataCard(
                            card = card,
                            loggedIn = loggedIn,
                            modifier = Modifier.weight(1f),
                            onOpen = onOpenCard,
                            onLogin = onLogin,
                        )
                    }
                    repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
            item(key = "tools-header") { ChsiSectionHeader(ChsiTexts.SECTION_TOOLS) }
            items(
                items = ChsiCatalog.officialTools.chunked(columns),
                key = { row -> "tool-" + row.joinToString("-") { it.id } },
            ) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { tool ->
                        ChsiToolCard(
                            tool = tool,
                            modifier = Modifier.weight(1f),
                            onOpen = onOpenTool,
                        )
                    }
                    repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
            item(key = "footer") {
                ChsiFooter(
                    loggedIn = loggedIn,
                    onOpenArchive = onOpenArchive,
                    onLogout = onLogout,
                )
            }
        }
    }
}

/** 状态条：登录态 + 更新时间 + 去登录/刷新按钮。 */
@Composable
private fun ChsiStatusLine(
    loggedIn: Boolean,
    refreshing: Boolean,
    lastUpdated: String?,
    globalError: String?,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
) {
    val text = ChsiNavigationLogic.statusText(
        loggedIn = loggedIn,
        refreshing = refreshing,
        lastUpdated = lastUpdated,
        globalError = globalError,
    )
    val color = if (globalError != null) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (refreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!loggedIn) {
            TextButton(onClick = onLogin, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(ChsiTexts.BTN_LOGIN_ACTION)
            }
        } else {
            TextButton(
                onClick = onRefresh,
                enabled = !refreshing,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(ChsiTexts.BTN_REFRESH)
            }
        }
    }
}

@Composable
private fun ChsiSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** 单张数据卡（未登录 → 直达官方登录页；已登录 → 点开官方页面；空态也保留入口）。 */
@Composable
private fun ChsiDataCard(
    card: ChsiDataCardUi,
    loggedIn: Boolean,
    modifier: Modifier = Modifier,
    onOpen: (ChsiDataCardUi) -> Unit,
    onLogin: () -> Unit,
) {
    Surface(
        onClick = { if (loggedIn) onOpen(card) else onLogin() },
        modifier = modifier.heightIn(min = 112.dp),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = card.kind.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = card.kind.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (!loggedIn) {
                // 未登录：数据卡不显示假数据，统一显示「去登录」入口（点卡片直达官方登录页）。
                Text(
                    text = ChsiNavigationLogic.dataCardHint(loggedIn = false),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = ChsiTexts.BTN_LOGIN_ACTION,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                when (card.status) {
                    ChsiCardStatus.READY -> {
                        card.lines.take(3).forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    ChsiCardStatus.EMPTY -> {
                        Text(
                            text = card.lines.firstOrNull() ?: ChsiTexts.MSG_EMPTY_FALLBACK,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = ChsiTexts.BTN_OPEN_OFFICIAL,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 官方工具入口卡（点开内置 WebView 官方页面）。 */
@Composable
private fun ChsiToolCard(
    tool: ChsiOfficialTool,
    modifier: Modifier = Modifier,
    onOpen: (ChsiOfficialTool) -> Unit,
) {
    Surface(
        onClick = { onOpen(tool) },
        modifier = modifier.heightIn(min = 64.dp),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = if (tool.needsLogin) "${tool.desc} · 需登录" else tool.desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 底部：打开完整学信档案 + 退出学信网登录（未登录不显示退出）。 */
@Composable
private fun ChsiFooter(
    loggedIn: Boolean,
    onOpenArchive: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedButton(
            onClick = onOpenArchive,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = CtShapeTokens.pill,
        ) {
            Icon(
                imageVector = Icons.Rounded.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(ChsiTexts.BTN_OPEN_ARCHIVE)
        }
        if (loggedIn) {
            TextButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    text = ChsiTexts.BTN_LOGOUT,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** 数据卡图标（仅展示用）。 */
private fun ChsiDataCardKind.icon() = when (this) {
    ChsiDataCardKind.ENROLLMENT -> Icons.Rounded.School
    ChsiDataCardKind.EDUCATION -> Icons.Rounded.Badge
    ChsiDataCardKind.DEGREE -> Icons.Rounded.Verified
    ChsiDataCardKind.VERIFY_REPORT -> Icons.Rounded.Description
}
