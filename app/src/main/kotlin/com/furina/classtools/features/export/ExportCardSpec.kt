package com.furina.classtools.features.export

import kotlinx.serialization.Serializable

/**
 * 导出卡片 v2 数据模型（与网页 `exportCard()` / 小程序 `drawCard()` 共用同一规格）。
 *
 * 唯一真源：`web/tools/tests/export_card_v2_spec.md`；Android 实现说明见 `docs/api/EXPORT_CARD_V2.md`。
 * 硬约束：参数配置区 + 斜向平铺水印 + 页脚生成时间 + 唯一编号；图内不得出现手机号/学号/宿舍号。
 */
@Serializable
data class ExportCardSpec(
    val title: String,
    val sub: String = "",
    /** 最终文件名（含 .png），由 [ExportCardNaming] 生成。 */
    val file: String,
    /** 唯一编号：`PREFIX-YYYYMMDD-HHMM-XXXX`。 */
    val rec: String,
    /** 参数配置区：[[标签, 值], ...]。 */
    val config: List<Pair<String, String>> = emptyList(),
    val blocks: List<ExportBlock> = emptyList(),
    /** 页脚左侧文案（如「生成时间 11-07 22:50」）。 */
    val foot: String = "",
    /** 仅抽签「剔除重抽」使用；P2 实现二维码绘制。 */
    val qr: QrSpec? = null,
    val qrNote: List<String> = emptyList(),
)

@Serializable
sealed interface ExportBlock {
    val label: String
}

/** 文本块：行 = 纯文本（[TextLine.plain]）或若干 run。 */
@Serializable
data class TextBlock(
    override val label: String,
    val lines: List<TextLine> = emptyList(),
    /** 整体等宽强调（20px/800 primary，行高 30）。 */
    val mono: Boolean = false,
    /** 原子行：不拆行内换行。 */
    val atomic: Boolean = false,
) : ExportBlock

@Serializable
data class TextLine(
    val runs: List<TextRun> = emptyList(),
    val plain: String? = null,
) {
    companion object {
        fun of(text: String): TextLine = TextLine(plain = text)
        fun of(vararg runs: TextRun): TextLine = TextLine(runs = runs.toList())
    }
}

@Serializable
data class TextRun(
    val t: String,
    /** 次要：11.5px/600 ink3。 */
    val dim: Boolean = false,
    /** 强调：14px/800 ink（mono 块内用 primary）。 */
    val strong: Boolean = false,
    /** 与下一 run 不换行（网页排版用；Android 逐行绘制时忽略）。 */
    val glue: Boolean = false,
)

/** 胶囊块（抽签结果）。 */
@Serializable
data class ChipsBlock(
    override val label: String,
    val chips: List<ChipItem>,
) : ExportBlock

@Serializable
data class ChipItem(val n: Int, val nm: String = "")

/** 分组块（分组结果）。 */
@Serializable
data class GroupsBlock(
    override val label: String,
    val groups: List<List<ChipItem>>,
    /** 缺省「第 N 组」。 */
    val groupLabel: String? = null,
) : ExportBlock

/** 二维码规格（仅抽签剔除重抽记录）。 */
@Serializable
data class QrSpec(val n: Int)

/** 导出卡片的类型（用于文件命名前缀与页脚）。 */
enum class ExportKind(val label: String) {
    DRAW_STEP1("抽签结果"),
    DRAW_STEP2("分组结果"),
    DRAW_WORKFLOW("抽签工作流"),
    HOMEWORK("作业清单"),
    TIMETABLE("课表"),
    FITNESS("体测成绩"),
    ROSTER("点名记录"),
}
