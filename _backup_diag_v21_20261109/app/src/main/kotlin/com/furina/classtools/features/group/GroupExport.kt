package com.furina.classtools.features.group

import com.furina.classtools.features.export.ChipItem
import com.furina.classtools.features.export.ExportCardNaming
import com.furina.classtools.features.export.ExportCardSpec
import com.furina.classtools.features.export.ExportKind
import com.furina.classtools.features.export.GroupsBlock
import java.util.Locale
import kotlin.random.Random

/** 导出卡「参数配置」区一行（与 DrawExportField 同形，避免跨 feature 依赖）。 */
data class GroupExportField(
    val label: String,
    val value: String,
)

/**
 * 分组导出 payload（对齐 export_card_v2_spec：参数配置区 + groups 块 + 页脚 + 编号）。
 *
 * `block` 直接是 [GroupsBlock]（两列组卡：组头/人数由绘制端生成）；
 * `toCardSpec()` 把它组装成通用 [ExportCardSpec]，由 Lead 的导出协调器消费。
 */
data class GroupExportPayload(
    val title: String,
    val subtitle: String,
    val fileName: String,
    val recordId: String,
    val config: List<GroupExportField>,
    val block: GroupsBlock,
    val footer: String,
    val generatedAtMillis: Long,
) {
    /** 通用导出卡 spec（features/export 的绘制端直接消费）。 */
    fun toCardSpec(): ExportCardSpec = ExportCardSpec(
        title = title,
        sub = subtitle,
        file = fileName,
        rec = recordId,
        config = config.map { it.label to it.value },
        blocks = listOf(block),
        foot = footer,
    )
}

/**
 * 分组导出工厂（纯函数，可单测；时间/编号后缀可注入）。
 *
 * 命名规则（web/AGENTS.md §2）：`分组结果_<YYYYMMDD-HHMM>_<组数>组_<人数>人_<顺序|打乱>.png`。
 */
object GroupExportFactory {

    private const val RECORD_PREFIX = "GROUP"

    fun create(
        result: GroupResult,
        nowMillis: Long = System.currentTimeMillis(),
        recordSuffix: String? = null,
    ): GroupExportPayload {
        val suffix = recordSuffix ?: randomSuffix(nowMillis)
        val groups = result.groups.map { group ->
            group.members.map { member -> ChipItem(n = member.seat, nm = member.name) }
        }
        return GroupExportPayload(
            title = "分组结果",
            subtitle = "${result.snapshot.groupCount} 组 / ${result.totalMembers} 人",
            fileName = fileName(result, nowMillis),
            recordId = recordId(nowMillis, suffix),
            config = configFields(result),
            block = GroupsBlock(label = "分组结果", groups = groups),
            footer = ExportCardNaming.footText(nowMillis),
            generatedAtMillis = nowMillis,
        )
    }

    /** 参数配置区（顺序与网页/小程序导图 `config` 一致）。 */
    fun configFields(result: GroupResult): List<GroupExportField> {
        val snapshot = result.snapshot
        return listOf(
            GroupExportField("分组 · 成员来源", snapshot.sourceLabel),
            GroupExportField("分组 · 方式", snapshot.modeLabel),
            GroupExportField("分组 · 人数", snapshot.total.toString()),
            GroupExportField("分组 · 组数", snapshot.groupCount.toString()),
            GroupExportField("分组 · 成员分配", snapshot.innerLabel),
        )
    }

    /** `分组结果_<stamp>_<N>组_<M>人_<顺序|打乱>.png`（已 safeName）。 */
    fun fileName(result: GroupResult, nowMillis: Long = System.currentTimeMillis()): String {
        val params = listOf(
            "${result.snapshot.groupCount}组",
            "${result.totalMembers}人",
            if (result.config.shuffle) "打乱" else "顺序",
        )
        return ExportCardNaming.fileName(ExportKind.DRAW_STEP2, params, nowMillis)
    }

    /** 唯一编号：`GROUP-YYYYMMDD-HHMM-XXXX`。 */
    fun recordId(nowMillis: Long, suffix: String): String =
        RECORD_PREFIX + "-" + ExportCardNaming.datePart(nowMillis) + "-" +
            ExportCardNaming.timePart(nowMillis) + "-" + suffix.uppercase(Locale.ROOT).take(4)

    /** 复制的纯文本（微信/QQ 直接粘贴）：按组逐行。 */
    fun copyText(result: GroupResult): String {
        if (result.groups.isEmpty()) return ""
        return result.groups.joinToString("\n") { group ->
            "第 ${group.index} 组：" +
                group.members.joinToString("、") { member ->
                    if (member.name.isBlank()) member.seat.toString() else member.name
                }
        }
    }

    /** 4 位随机后缀（可注入 nowMillis 让测试稳定；仅用于编号，不影响业务）。 */
    fun randomSuffix(seed: Long): String {
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        val random = Random(seed)
        return buildString {
            repeat(4) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }
}
