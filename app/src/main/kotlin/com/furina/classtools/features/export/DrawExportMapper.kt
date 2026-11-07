package com.furina.classtools.features.export

import com.furina.classtools.features.draw.DrawExportPayload

/**
 * 把抽签领域层的导出 payload 映射为通用导出卡片 spec（v2 三种块类型）。
 * 抽签本轮使用 chips 块；剔除重抽记录用文本块兜底（二维码为 P2）。
 */
fun DrawExportPayload.toCardSpec(): ExportCardSpec {
    val mappedBlocks = buildList {
        blocks.forEach { block ->
            add(
                ChipsBlock(
                    label = block.label,
                    chips = block.chips.map { chip -> ChipItem(n = chip.number, nm = chip.name.orEmpty()) },
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
