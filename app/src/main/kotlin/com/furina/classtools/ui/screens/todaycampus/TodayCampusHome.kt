package com.furina.classtools.ui.screens.todaycampus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AssignmentInd
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.todaycampus.TodayCampusCatalog
import com.furina.classtools.data.todaycampus.TodayCampusEntry
import com.furina.classtools.data.todaycampus.TodayCampusGlyph
import com.furina.classtools.data.todaycampus.TodayCampusSearch
import com.furina.classtools.ui.components.expressive.CtPressableCard
import com.furina.classtools.ui.theme.CtTokens
import com.furina.classtools.ui.theme.LocalCtDarkTheme

/**
 * 「今日校园」首页：搜索框 + 2 列服务入口网格（本地过滤、空态、点按 ≥48dp、浅/深色 token）。
 *
 * 纯展示 + 回调；搜索与入口数据来自 [TodayCampusSearch] / [TodayCampusCatalog]（可单测）。
 */
@Composable
internal fun TodayCampusHome(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenEntry: (TodayCampusEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = remember(query) { TodayCampusSearch.filter(TodayCampusCatalog.entries, query) }
    Column(modifier = modifier.fillMaxSize()) {
        TodayCampusSearchField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CtTokens.Spacing.pageHorizontalCompact, vertical = CtTokens.Spacing.sm),
        )
        if (filtered.isEmpty()) {
            TodayCampusNoMatch(
                query = query,
                onClear = { onQueryChange("") },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = CtTokens.Spacing.pageHorizontalCompact,
                    end = CtTokens.Spacing.pageHorizontalCompact,
                    top = CtTokens.Spacing.xs,
                    bottom = CtTokens.Spacing.xl,
                ),
                horizontalArrangement = Arrangement.spacedBy(CtTokens.Spacing.cardGapCompact),
                verticalArrangement = Arrangement.spacedBy(CtTokens.Spacing.cardGapCompact),
            ) {
                items(filtered, key = { it.id }) { entry ->
                    TodayCampusEntryCard(entry = entry, onClick = { onOpenEntry(entry) })
                }
            }
        }
    }
}

/** 搜索框（单行、48dp 触控、本机过滤）。 */
@Composable
private fun TodayCampusSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = query,
        onValueChange = { onQueryChange(it.take(TodayCampusSearch.MAX_QUERY_LENGTH)) },
        modifier = modifier
            .heightIn(min = CtTokens.Size.minTouchTarget)
            .semantics { contentDescription = TodayCampusCatalog.A11Y_SEARCH },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(CtTokens.Size.minTouchTarget),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = TodayCampusCatalog.BTN_CLEAR_SEARCH)
                }
            }
        },
        placeholder = { Text(TodayCampusCatalog.SEARCH_PLACEHOLDER) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaceContainerHigh,
            unfocusedContainerColor = scheme.surfaceContainerHigh.copy(alpha = 0.72f),
            focusedBorderColor = scheme.primary.copy(alpha = 0.55f),
            unfocusedBorderColor = Color.Transparent,
        ),
    )
}

/** 服务入口卡：图标色块 + 名称 + 一句说明（整卡点按；最小触控 48dp 由 CtPressableCard 保证）。 */
@Composable
private fun TodayCampusEntryCard(
    entry: TodayCampusEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalCtDarkTheme.current
    val accent = Color(entry.colorArgb)
    CtPressableCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CtTokens.Size.listItemThreeLine),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentDescription = "${entry.name}，${entry.desc}",
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CtTokens.Spacing.md, vertical = CtTokens.Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CtTokens.Spacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(CtTokens.Radius.medium))
                    .background(accent.copy(alpha = if (dark) 0.28f else 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = entry.glyph.icon(),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 搜索空态（一句话 + 清除搜索）。 */
@Composable
private fun TodayCampusNoMatch(
    query: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = CtTokens.Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(CtTokens.Spacing.xxxl))
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(CtTokens.Size.iconXLarge),
        )
        Spacer(modifier = Modifier.height(CtTokens.Spacing.md))
        Text(
            text = TodayCampusCatalog.MSG_NO_MATCH_TITLE,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(CtTokens.Spacing.xs))
        Text(
            text = if (query.isBlank()) {
                TodayCampusCatalog.MSG_NO_MATCH_DESC
            } else {
                "${TodayCampusCatalog.MSG_NO_MATCH_DESC}（$query）"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(CtTokens.Spacing.sm))
        TextButton(
            onClick = onClear,
            modifier = Modifier.heightIn(min = CtTokens.Size.minTouchTarget),
        ) {
            Text(TodayCampusCatalog.BTN_CLEAR_SEARCH)
        }
    }
}

/** 入口图标键 → Material 图标（UI 层映射；数据层保持无 Android 依赖）。 */
internal fun TodayCampusGlyph.icon(): ImageVector = when (this) {
    TodayCampusGlyph.STUDENT_AFFAIRS -> Icons.Rounded.AssignmentInd
    TodayCampusGlyph.SCHOLARSHIP -> Icons.Rounded.EmojiEvents
    TodayCampusGlyph.WORK_STUDY -> Icons.Rounded.Work
    TodayCampusGlyph.LEAVE -> Icons.Rounded.EventAvailable
    TodayCampusGlyph.AID -> Icons.Rounded.VolunteerActivism
    TodayCampusGlyph.DORM -> Icons.Rounded.Hotel
    TodayCampusGlyph.MENTAL -> Icons.Rounded.Psychology
    TodayCampusGlyph.CARD -> Icons.Rounded.CreditCard
    TodayCampusGlyph.GRADES -> Icons.Rounded.CalendarMonth
    TodayCampusGlyph.UTILITY -> Icons.Rounded.WaterDrop
    TodayCampusGlyph.REPAIR -> Icons.Rounded.Handyman
    TodayCampusGlyph.PORTAL -> Icons.Rounded.Public
}
