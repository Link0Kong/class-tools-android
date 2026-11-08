package com.furina.classtools.ui.screens.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.marks.MarkKind
import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.ui.theme.CtShapeTokens

/**
 * 「我的数据」指标模型（与小程序 `pages/me` 口径对齐 + Android 可选扩展指标）。
 *
 * 核心四项（mp 实际展示）：待交作业 / 已完成作业 / 抽签记录 / 体测记录；
 * 可选扩展（有数据才展示，避免伪造 0）：未处理通知 / 班级人数 / 关注宿舍剩余电量。
 */
data class MeStats(
    val pendingHomework: Int = 0,
    val doneHomework: Int = 0,
    val drawRecords: Int = 0,
    val fitRecords: Int = 0,
    val pendingNotices: Int? = null,
    val classMembers: Int? = null,
    val powerRemaining: Double? = null,
    val powerRoomLabel: String? = null,
)

/**
 * 提醒聚合状态（供「我的」页提醒入口展示）。
 *
 * 由 Lead 从 `ReminderSettingsStore.settings` + `PermissionController(NOTIFICATIONS)` 组合传入。
 */
data class ReminderStatus(
    val enabled: Boolean = false,
    val notificationPermission: Boolean = false,
    val homeworkEnabled: Boolean = true,
    val courseEnabled: Boolean = true,
    val homeworkLeadDays: Int = 1,
    val courseLeadMinutes: Int = 15,
) {
    val sections: List<String>
        get() = buildList {
            if (homeworkEnabled) add("作业")
            if (courseEnabled) add("课表")
        }

    /** 入口副标题：优先提示未开启原因，其次展示已开启范围。 */
    val label: String
        get() = when {
            !enabled -> "未开启 · 点击设置"
            !notificationPermission -> "通知权限未开启 · 点击设置"
            sections.isEmpty() -> "未选择提醒类型 · 点击设置"
            else -> "已开启 · ${sections.joinToString(" + ")}提醒"
        }

    val isWarning: Boolean
        get() = enabled && !notificationPermission
}

/** 指标纯逻辑（从本机缓存聚合 + 格式化；不新增网络请求）。 */
object MeStatsBuilder {

    /**
     * 从本机缓存聚合「我的数据」四项。
     *
     * - 待交作业 = 未完成且未被隐藏（隐藏项暂不参与；Android 暂无 hwHide 展示入口，预留参数）；
     * - 已完成作业 = marks `kind='hw'` 命中（唯一真源，与作业页一致）；
     * - 抽签记录/体测记录由调用方传入（分别来自抽签结果与 fitrecs 条数）。
     */
    fun fromCached(
        homeworkRows: List<HomeworkRow>,
        marks: MarksSnapshot,
        fitRecords: Int,
        drawRecords: Int,
        hiddenHomeworkIds: Set<String> = emptySet(),
    ): MeStats {
        var done = 0
        var pending = 0
        homeworkRows.forEach { row ->
            val isDone = marks.isDone(MarkKind.HW, row.id)
            if (isDone) {
                done += 1
            } else if (row.id !in hiddenHomeworkIds) {
                pending += 1
            }
        }
        return MeStats(
            pendingHomework = pending,
            doneHomework = done,
            drawRecords = drawRecords.coerceAtLeast(0),
            fitRecords = fitRecords.coerceAtLeast(0),
        )
    }

    /** 数字展示：0..999 原样，≥1000 显示 `999+`（与移动端紧凑卡片口径一致）。 */
    fun formatCount(value: Int): String = when {
        value <= 0 -> "0"
        value >= 1000 -> "999+"
        else -> value.toString()
    }

    /** 电量展示：`36.5 度`；无数据返回 null（不展示）。 */
    fun formatPower(remaining: Double?, roomLabel: String? = null): String? {
        if (remaining == null || !remaining.isFinite()) return null
        val value = String.format(java.util.Locale.CHINA, "%.1f", remaining)
        return if (roomLabel.isNullOrBlank()) "$value 度" else "$roomLabel · $value 度"
    }

    /** 可选扩展指标行（有数据才返回）。 */
    fun extraMetrics(stats: MeStats): List<Pair<String, String>> = buildList {
        stats.pendingNotices?.takeIf { it >= 0 }?.let { add("未处理通知" to formatCount(it)) }
        stats.classMembers?.takeIf { it > 0 }?.let { add("班级人数" to formatCount(it)) }
        stats.powerRemaining?.let { remaining ->
            formatPower(remaining, stats.powerRoomLabel)?.let { add("关注宿舍电量" to it) }
        }
    }

    /** 抽签记录数从最近一次结果取（0 或结果条数）。 */
    fun drawCountFrom(entries: Int?): Int = entries?.coerceAtLeast(0) ?: 0
}

/**
 * 「我的数据」指标卡 + 提醒设置入口（独立组件，由 Lead 嵌入 ProfileScreen）。
 *
 * 不依赖任何仓库/网络：`MeStats` 与 `ReminderStatus` 由调用方聚合传入；点击提醒入口回调
 * [onOpenReminder]，路由由 Lead 接线。
 *
 * 小屏/平板：组件本身为单卡片自适应宽度；Profile 的双栏布局由 design/Lead 控制。
 */
@Composable
fun MeStatsSection(
    stats: MeStats,
    reminder: ReminderStatus,
    onOpenReminder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "我的数据",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCell(
                    value = MeStatsBuilder.formatCount(stats.pendingHomework),
                    label = "待交作业",
                    modifier = Modifier.weight(1f),
                )
                MetricCell(
                    value = MeStatsBuilder.formatCount(stats.doneHomework),
                    label = "已完成作业",
                    modifier = Modifier.weight(1f),
                )
                MetricCell(
                    value = MeStatsBuilder.formatCount(stats.drawRecords),
                    label = "抽签记录",
                    modifier = Modifier.weight(1f),
                )
                MetricCell(
                    value = MeStatsBuilder.formatCount(stats.fitRecords),
                    label = "体测记录",
                    modifier = Modifier.weight(1f),
                )
            }
            val extras = MeStatsBuilder.extraMetrics(stats)
            if (extras.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    extras.forEach { (label, value) ->
                        Text(
                            text = "$label $value",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenReminder)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = if (reminder.isWarning) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "提醒设置",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = reminder.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (reminder.isWarning) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun MetricCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
