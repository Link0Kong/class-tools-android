package com.furina.classtools.ui.screens.jw

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
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
import com.furina.classtools.ui.theme.CtShapeTokens

/**
 * 教务卡片页内容（规格 `_audit/jw_entry_20261113/JW_CARDS_V1_SPEC.md` §4）。
 *
 * 结构：状态条 →「数据」4 张官方接口卡（2 列）→「官方工具」8 张入口卡（2 列）→「打开完整教务官网」。
 *
 * 交互（由宿主 [JwEntryScreen] 提供回调）：
 * - 点数据卡/工具卡 = 在 App 内置 WebView 打开对应官方页面（只读浏览；写操作由用户在官方页面内完成）；
 * - 单卡失败态只在卡片内显示「获取失败 · 重试」，点「重试」重新拉全部 4 个接口（单卡失败不影响其它卡）；
 * - 加载态显示「—」，接口成功但无数据（官方 count 0）显示各卡空态文案（当前大一第一学期即此路径）。
 */
@Composable
fun JwCardsSection(
    cards: List<JwDataCardUi>,
    refreshing: Boolean,
    lastUpdated: String?,
    globalError: String?,
    onRefresh: () -> Unit,
    onOpenDataCard: (JwDataCardUi) -> Unit,
    onOpenTool: (JwOfficialTool) -> Unit,
    onOpenSite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        JwStatusLine(
            refreshing = refreshing,
            lastUpdated = lastUpdated,
            globalError = globalError,
            onRetry = onRefresh,
        )

        JwSectionHeader(text = JwCardTexts.SECTION_DATA)
        cards.chunked(2).forEach { rowCards ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowCards.forEach { card ->
                    JwDataCard(
                        card = card,
                        modifier = Modifier.weight(1f),
                        onOpen = onOpenDataCard,
                        onRetry = onRefresh,
                    )
                }
                if (rowCards.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }

        JwSectionHeader(text = JwCardTexts.SECTION_TOOLS)
        JwCardCatalog.officialTools.chunked(2).forEach { rowTools ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowTools.forEach { tool ->
                    JwOfficialToolCard(
                        tool = tool,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenTool,
                    )
                }
                if (rowTools.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }

        OutlinedButton(
            onClick = onOpenSite,
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
            Text(JwCardTexts.BTN_OPEN_SITE)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** 状态条：已自动登录 + 更新时间 / 拉取中 / 登录失效（可重试）。 */
@Composable
private fun JwStatusLine(
    refreshing: Boolean,
    lastUpdated: String?,
    globalError: String?,
    onRetry: () -> Unit,
) {
    val text = when {
        globalError != null -> globalError
        refreshing -> "正在获取教务数据…"
        lastUpdated != null -> JwCardTexts.STATUS_READY_PREFIX + lastUpdated
        else -> ""
    }
    val color = if (globalError != null) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        if (globalError != null && !refreshing) {
            TextButton(onClick = onRetry) { Text(JwCardTexts.BTN_REFRESH) }
        }
    }
}

@Composable
private fun JwSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** 单张数据卡（点开官方页面；失败态内嵌「重试」）。 */
@Composable
private fun JwDataCard(
    card: JwDataCardUi,
    modifier: Modifier = Modifier,
    onOpen: (JwDataCardUi) -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        onClick = { onOpen(card) },
        modifier = modifier.heightIn(min = 118.dp),
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
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            when (card.status) {
                JwDataCardStatus.LOADING -> {
                    Text(
                        text = "—",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "加载中…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                JwDataCardStatus.READY -> {
                    Text(
                        text = card.headline,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    card.lines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (card.footnote.isNotBlank()) {
                        Text(
                            text = card.footnote,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                JwDataCardStatus.EMPTY -> {
                    Text(
                        text = card.headline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                JwDataCardStatus.ERROR -> {
                    Text(
                        text = card.headline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = 40.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(JwCardTexts.BTN_RETRY, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** 官方工具入口卡（点开对应官方页面）。 */
@Composable
private fun JwOfficialToolCard(
    tool: JwOfficialTool,
    modifier: Modifier = Modifier,
    onClick: (JwOfficialTool) -> Unit,
) {
    Surface(
        onClick = { onClick(tool) },
        modifier = modifier.heightIn(min = 52.dp),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tool.title,
                style = MaterialTheme.typography.bodyMedium,
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
    }
}

/** 数据卡图标（仅展示用，失败也不影响数据逻辑）。 */
private fun JwDataCardKind.icon() = when (this) {
    JwDataCardKind.GRADES -> Icons.Rounded.School
    JwDataCardKind.EXAMS -> Icons.Rounded.Event
    JwDataCardKind.ENROLL -> Icons.Rounded.Badge
    JwDataCardKind.NOTICES -> Icons.Rounded.Notifications
}
