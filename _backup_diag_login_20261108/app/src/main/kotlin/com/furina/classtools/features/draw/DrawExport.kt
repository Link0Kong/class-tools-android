package com.furina.classtools.features.draw

import com.furina.classtools.features.export.ChipItem
import com.furina.classtools.features.export.ChipsBlock
import com.furina.classtools.features.export.ExportCardSpec
import com.furina.classtools.features.export.GroupsBlock
import com.furina.classtools.features.export.TextBlock
import com.furina.classtools.features.export.TextLine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/** 导出卡「参数配置」区的一行。 */
data class DrawExportField(val label: String, val value: String)

/** 导出卡 chip（抽签结果胶囊）。 */
data class DrawExportChip(val number: Int, val name: String?)

/** 导出卡内容块：本轮只产出「抽签结果」chips 块（导出实现由后续 task 做）。 */
data class DrawExportBlock(
    val label: String,
    val chips: List<DrawExportChip>,
)

/**
 * 导出 payload 占位（对齐 `export_card_v2_spec.md`：title/sub/file/rec/config/blocks/foot + 可选 qr/qrNote）。
 *
 * 后续导出实现（Canvas/分享）消费该 payload 即可，UI 层不关心绘制细节。
 */
data class DrawExportPayload(
    val title: String,
    val subtitle: String,
    val fileName: String,
    val recordId: String,
    val config: List<DrawExportField>,
    val blocks: List<DrawExportBlock>,
    /** 剔除重抽记录（结果单文本 + 未来二维码内容）。 */
    val removeLogLines: List<String>,
    /** 二维码可承载的文本（抽签剔除/重抽存在时非空；本轮只回传，不绘制二维码）。 */
    val qrText: String?,
    val footer: String,
    val generatedAtMillis: Long,
)

/**
 * 导出 payload 工厂（纯函数，可单测；时间/随机后缀可注入）。
 *
 * 命名严格对齐网页：`抽签结果_<YYYYMMDD-HHMM>_范围<lo>-<hi>_抽<n>个_<不重复|允许重复>_<排序>.png`。
 */
object DrawExportFactory {

    private val stampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.CHINA)
    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)

    fun create(
        result: DrawResult,
        nowMillis: Long = System.currentTimeMillis(),
        randomSuffix: String? = null,
    ): DrawExportPayload {
        val config = result.config
        val zoned = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())
        val stamp = stampFormatter.format(zoned)
        val suffix = (randomSuffix ?: randomSuffix()).uppercase(Locale.US)

        val logLines = result.log.mapIndexed { i, entry ->
            val removed = entry.removed.label
            val added = entry.added?.label ?: "（未补抽）"
            "#${i + 1} 剔除 $removed → 重抽 $added"
        }

        return DrawExportPayload(
            title = "随机抽签结果",
            subtitle = "Step 1 / 3",
            fileName = fileName(config, stamp),
            recordId = "DRAW-$stamp-$suffix",
            config = configFields(result),
            blocks = listOf(
                DrawExportBlock(
                    label = "抽签结果",
                    chips = result.entries.map { DrawExportChip(number = it.number, name = it.name) },
                ),
            ),
            removeLogLines = logLines,
            qrText = if (logLines.isEmpty()) null else buildQrText(config, logLines),
            footer = "生成时间 ${timeFormatter.format(zoned)}",
            generatedAtMillis = nowMillis,
        )
    }

    /** `抽签结果_20261009-2024_范围1-50_抽10个_不重复_按抽取顺序.png`（已 safeName）。 */
    fun fileName(config: DrawConfig, stamp: String): String {
        val raw = buildString {
            append("抽签结果")
            append('_').append(stamp)
            append("_范围").append(config.start).append('-').append(config.end)
            append("_抽").append(config.count).append("个")
            append('_').append(if (config.allowDuplicates) "允许重复" else "不重复")
            append('_').append(config.sort.label)
            append(".png")
        }
        return safeName(raw)
    }

    /** 参数配置区（顺序与网页 `exportCard` 的抽签参数一致）。 */
    fun configFields(result: DrawResult): List<DrawExportField> {
        val config = result.config
        val fields = mutableListOf(
            DrawExportField("范围", "${config.start} – ${config.end}"),
            DrawExportField("抽取个数", config.count.toString()),
            DrawExportField("允许重复", if (config.allowDuplicates) "是" else "否"),
            DrawExportField("排除序号", config.excludedText.ifBlank { "无" }),
            DrawExportField("结果排序", config.sort.label),
            DrawExportField("可用池", "共 ${result.pool.size} 个"),
        )
        if (config.poolSource == DrawPoolSource.ROSTER) {
            fields.add(DrawExportField("候选来源", DrawPoolSource.ROSTER.label))
        }
        return fields
    }

    /** 结果单/二维码文本（URL-friendly 的紧凑描述；本轮只回传不绘制）。 */
    fun buildQrText(config: DrawConfig, logLines: List<String>): String {
        val head = "tool.furina.fun 抽签剔除记录|范围${config.start}-${config.end} 抽${config.count}个|"
        return head + logLines.joinToString(";")
    }

    /** 与网页 `safeName` 同规则：`\/:*?"<>|` 与空白 → `-`，合并连字符并去首尾。 */
    fun safeName(raw: String): String = raw
        .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "-")
        .replace(Regex("-+"), "-")
        .replace(Regex("^-|-$"), "")

    /** 打印/调试用：结果一行文本。 */
    fun summaryLine(result: DrawResult): String =
        result.entries.joinToString("、") { it.label }.ifBlank { "—" }

    private fun randomSuffix(): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..4).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }
}

// =============================================================================
// STEP3 结果单（chips + 可选分组块 + 剔除重抽记录文本块）
// =============================================================================

/** 结果单里的一组（已分组时）：组内成员即抽签结果条目的子集。 */
data class DrawSheetGroup(
    val members: List<DrawExportChip>,
)

/**
 * 抽签结果单导出 payload（STEP3）：
 * - `chips` = 抽签结果胶囊；
 * - `groups` = 已分组时的分组块（两列组卡；未分组传空）；
 * - `removeLogLines` = 剔除重抽记录（文本块）。
 *
 * 与抽签图共用绘制端：由 [toCardSpec] 组装成统一 [ExportCardSpec]。
 */
data class DrawResultSheetPayload(
    val title: String,
    val subtitle: String,
    val fileName: String,
    val recordId: String,
    val config: List<DrawExportField>,
    val chips: List<DrawExportChip>,
    val groups: List<DrawSheetGroup>,
    val removeLogLines: List<String>,
    val footer: String,
    val generatedAtMillis: Long,
) {
    /** 通用导出卡 spec（chips → groups → 剔除记录的块顺序固定）。 */
    fun toCardSpec(): ExportCardSpec {
        val mappedBlocks = buildList {
            add(
                ChipsBlock(
                    label = "抽签结果",
                    chips = chips.map { chip -> ChipItem(n = chip.number, nm = chip.name.orEmpty()) },
                ),
            )
            if (groups.isNotEmpty()) {
                add(
                    GroupsBlock(
                        label = "分组结果",
                        groups = groups.map { group ->
                            group.members.map { member -> ChipItem(n = member.number, nm = member.name.orEmpty()) }
                        },
                    ),
                )
            }
            if (removeLogLines.isNotEmpty()) {
                add(
                    TextBlock(
                        label = "剔除重抽记录",
                        lines = removeLogLines.map { TextLine.of(it) },
                    ),
                )
            }
        }
        return ExportCardSpec(
            title = title,
            sub = subtitle,
            file = fileName,
            rec = recordId,
            config = config.map { it.label to it.value },
            blocks = mappedBlocks,
            foot = footer,
            qr = null,
            qrNote = removeLogLines.take(3),
        )
    }
}

/**
 * 结果单工厂（纯函数，可单测；时间/随机后缀可注入）。
 * 文件名：`抽签结果单_<YYYYMMDD-HHMM>_范围<lo>-<hi>_抽<n>个_<不重复|允许重复>_<排序>.png`。
 */
object DrawSheetFactory {

    private val stampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.CHINA)
    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)

    fun create(
        result: DrawResult,
        groups: List<DrawSheetGroup> = emptyList(),
        nowMillis: Long = System.currentTimeMillis(),
        randomSuffix: String? = null,
    ): DrawResultSheetPayload {
        val config = result.config
        val zoned = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())
        val stamp = stampFormatter.format(zoned)
        val suffix = (randomSuffix ?: randomSuffix()).uppercase(Locale.US)
        val logLines = removeLogLines(result)
        return DrawResultSheetPayload(
            title = "抽签结果单",
            subtitle = "Step 3 / 3 · 结果单",
            fileName = fileName(config, stamp),
            recordId = recordId(stamp, suffix),
            config = DrawExportFactory.configFields(result),
            chips = result.entries.map { DrawExportChip(number = it.number, name = it.name) },
            groups = groups,
            removeLogLines = logLines,
            footer = "生成时间 ${timeFormatter.format(zoned)}",
            generatedAtMillis = nowMillis,
        )
    }

    /** 与抽签图一致的剔除重抽文本行。 */
    fun removeLogLines(result: DrawResult): List<String> = result.log.mapIndexed { i, entry ->
        val removed = entry.removed.label
        val added = entry.added?.label ?: "（未补抽）"
        "#${i + 1} 剔除 $removed → 重抽 $added"
    }

    /** `抽签结果单_<stamp>_范围lo-hi_抽n个_<不重复|允许重复>_<排序>.png`（已 safeName）。 */
    fun fileName(config: DrawConfig, stamp: String): String {
        val raw = buildString {
            append("抽签结果单")
            append('_').append(stamp)
            append("_范围").append(config.start).append('-').append(config.end)
            append("_抽").append(config.count).append("个")
            append('_').append(if (config.allowDuplicates) "允许重复" else "不重复")
            append('_').append(config.sort.label)
            append(".png")
        }
        return DrawExportFactory.safeName(raw)
    }

    /** 唯一编号：`SHEET-YYYYMMDD-HHMM-XXXX`。 */
    fun recordId(stamp: String, suffix: String): String =
        "SHEET-" + stamp + "-" + suffix.uppercase(Locale.ROOT).take(4)

    private fun randomSuffix(): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..4).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }
}
