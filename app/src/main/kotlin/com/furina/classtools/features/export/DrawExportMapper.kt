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
        rec = receipt?.id ?: recordId,
        config = config.map { it.label to it.value } + if (receipt != null) listOf(
            "存证编号" to receipt.id,
            "存证时间" to java.time.Instant.ofEpochMilli(receipt.at).atZone(java.time.ZoneId.of("Asia/Shanghai"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            "当天抽签" to "第 ${receipt.seqDay} 次 · 共 ${receipt.todayCount} 次",
            "记录哈希" to receipt.hash.take(16)
        ) else listOf("存证状态" to "未获云端确认 · 仅本机结果"),
        blocks = mappedBlocks,
        foot = footer,
        qr = receipt?.let { QrSpec(url = "https://tool.furina.fun/#verify=" + it.id) },
        qrNote = listOf("扫码核验本轮抽签", "同班同学登录后可核对原始结果及调整记录"),
    )
}
