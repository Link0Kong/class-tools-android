package com.furina.classtools.features.fit

import com.furina.classtools.features.export.ExportCardNaming
import com.furina.classtools.features.export.ExportCardSpec
import com.furina.classtools.features.export.ExportKind
import com.furina.classtools.features.export.TextBlock
import com.furina.classtools.features.export.TextLine
import java.util.Locale
import kotlin.random.Random

/** 导出卡「参数配置」区一行。 */
data class FitExportField(
    val label: String,
    val value: String,
)

/**
 * 体测导出 payload（对齐 export_card_v2_spec：参数配置区 + 文本块「各项得分」+ 页脚 + 编号）。
 *
 * `block` 为 [TextBlock]（逐行文本；导出绘制端直接消费），`toCardSpec()` 组装为通用
 * [ExportCardSpec] 交给 Lead 的导出协调器。
 */
data class FitExportPayload(
    val title: String,
    val subtitle: String,
    val fileName: String,
    val recordId: String,
    val config: List<FitExportField>,
    val block: TextBlock,
    val footer: String,
    val generatedAtMillis: Long,
) {
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
 * 体测导出工厂（纯函数，可单测；时间/编号后缀可注入）。
 *
 * 命名规则：`体测成绩_<YYYYMMDD-HHMM>_<男|女>_<大一大二|大三大四>_总分<总分>.png`。
 * 图内不含学号/姓名（本 payload 天然不含）。
 */
object FitExportFactory {

    private const val RECORD_PREFIX = "FIT"

    fun create(
        calc: FitCalc,
        nowMillis: Long = System.currentTimeMillis(),
        recordSuffix: String? = null,
    ): FitExportPayload {
        val suffix = recordSuffix ?: randomSuffix(nowMillis)
        return FitExportPayload(
            title = "体测成绩",
            subtitle = "${calc.sex.label} · ${calc.gradeBucket.label}",
            fileName = fileName(calc, nowMillis),
            recordId = recordId(nowMillis, suffix),
            config = configFields(calc),
            block = TextBlock(label = "各项得分", lines = scoreLines(calc)),
            footer = ExportCardNaming.footText(nowMillis),
            generatedAtMillis = nowMillis,
        )
    }

    /** 参数配置区（与 mp `exportImg` 的 config 一致）。 */
    fun configFields(calc: FitCalc): List<FitExportField> = listOf(
        FitExportField("标准总分", calc.std.toString()),
        FitExportField("加分", calc.plus.toString()),
        FitExportField("总评", calc.totalText),
        FitExportField("等级", calc.grade),
        FitExportField("缺项", calc.miss.toString()),
    )

    /** 各项得分文本行（格式与 mp `exportImg` 的 lines 一致）。 */
    fun scoreLines(calc: FitCalc): List<TextLine> = calc.rows.map { row ->
        val score = row.score?.toString() ?: "—"
        val plusText = if (row.plus > 0) "  +${row.plus}" else ""
        TextLine.of("${row.name}（${row.sub}  ${row.weight}%）：${row.value}  →  $score$plusText")
    }

    /** `体测成绩_<stamp>_<性别>_<年级>_总分<总分>.png`（已 safeName）。 */
    fun fileName(calc: FitCalc, nowMillis: Long = System.currentTimeMillis()): String =
        ExportCardNaming.fileName(
            kind = ExportKind.FITNESS,
            params = listOf(calc.sex.label, calc.gradeBucket.short, "总分${calc.totalText}"),
            now = nowMillis,
        )

    /** 唯一编号：`FIT-YYYYMMDD-HHMM-XXXX`。 */
    fun recordId(nowMillis: Long, suffix: String): String =
        RECORD_PREFIX + "-" + ExportCardNaming.datePart(nowMillis) + "-" +
            ExportCardNaming.timePart(nowMillis) + "-" + suffix.uppercase(Locale.ROOT).take(4)

    /** 复制的纯文本（粘贴给同学/群）：总评 + 各项。 */
    fun copyText(calc: FitCalc): String = buildString {
        append("体测成绩 · ${calc.sex.label} · ${calc.gradeBucket.short}\n")
        append("总评 ${calc.totalText}（${calc.grade}）· 标准分 ${calc.std} · 加分 ${calc.plus}")
        if (calc.miss > 0) append(" · 缺项 ${calc.miss}")
        append('\n')
        calc.rows.forEach { row ->
            val score = row.score?.toString() ?: "—"
            append("${row.name}：${row.value}（${row.sub}）→ $score")
            if (row.plus > 0) append("  +${row.plus}")
            append('\n')
        }
    }.trimEnd()

    /** 4 位随机后缀（可注入 seed 让测试稳定）。 */
    fun randomSuffix(seed: Long): String {
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        val random = Random(seed)
        return buildString { repeat(4) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }
}
