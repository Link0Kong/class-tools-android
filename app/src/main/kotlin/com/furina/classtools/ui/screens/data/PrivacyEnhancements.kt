package com.furina.classtools.ui.screens.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.privacy.LocalDataSnapshot
import com.furina.classtools.data.privacy.LocalDataStat
import com.furina.classtools.data.privacy.LocalDataStats
import com.furina.classtools.data.privacy.SyncScopeCatalog
import com.furina.classtools.data.privacy.SyncScopeMode
import com.furina.classtools.ui.theme.CtShapeTokens

/**
 * 数据与隐私页增强（v2.1.1，独立组件；由 Lead 接线进 [DataPrivacyScreen]）：
 * 1. [SyncScopeSectionCard]：同步范围清单（userdata v6 11 section + marks + 仅本机/设备偏好）；
 * 2. [LocalDataStatsCard]：本机数据 8 计数（mp 同口径）+ 额外汇总（同步配置/常用工具/名单缓存）。
 *
 * 数据不联网：本机快照由 Lead 从现有仓库聚合后传入（见 [LocalDataSnapshot]）。
 */

/** 同步范围清单卡（静态文案，来自 [SyncScopeCatalog]）。 */
@Composable
fun SyncScopeSectionCard(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "同步范围",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ScopeGroup(
                title = SyncScopeMode.SYNCED.label,
                items = SyncScopeCatalog.items.filter { it.mode == SyncScopeMode.SYNCED },
                showDetail = false,
            )
            ScopeGroup(
                title = SyncScopeMode.LOCAL_ONLY.label,
                items = SyncScopeCatalog.items.filter { it.mode == SyncScopeMode.LOCAL_ONLY },
                showDetail = true,
            )
            ScopeGroup(
                title = SyncScopeMode.DEVICE_ONLY.label,
                items = SyncScopeCatalog.items.filter { it.mode == SyncScopeMode.DEVICE_ONLY },
                showDetail = true,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = SyncScopeCatalog.FOOTER,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 本机数据 8 计数卡（只统计条数，不含内容；8 格口径与 mp 一致）。 */
@Composable
fun LocalDataStatsCard(
    snapshot: LocalDataSnapshot,
    modifier: Modifier = Modifier,
) {
    val primary = LocalDataStats.primaryStats(snapshot)
    val summary = LocalDataStats.summaryStats(snapshot)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "本机数据",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            primary.chunked(4).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { stat ->
                        LocalStatCell(stat = stat, modifier = Modifier.weight(1f))
                    }
                    repeat(4 - row.size) { Column(modifier = Modifier.weight(1f)) {} }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                summary.forEach { stat ->
                    Text(
                        text = "${stat.label} ${stat.value}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "只统计条数，不含内容；不联网。清除本机数据后这些计数会归零。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScopeGroup(
    title: String,
    items: List<com.furina.classtools.data.privacy.SyncScopeItem>,
    showDetail: Boolean,
) {
    if (items.isEmpty()) return
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    if (showDetail) {
        items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = item.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        // 同步项紧凑两列（避免长列表占满屏幕，与 mp 的 chips 展示一致）
        items.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { item ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = CtShapeTokens.chip,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                if (pair.size == 1) Column(modifier = Modifier.weight(1f)) {}
            }
        }
    }
}

@Composable
private fun LocalStatCell(stat: LocalDataStat, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stat.value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (stat.available) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
        Text(
            text = stat.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
