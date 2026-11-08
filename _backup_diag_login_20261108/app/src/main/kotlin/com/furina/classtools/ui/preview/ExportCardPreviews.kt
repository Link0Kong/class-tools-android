package com.furina.classtools.ui.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.furina.classtools.features.export.ChipItem
import com.furina.classtools.features.export.ChipsBlock
import com.furina.classtools.features.export.ExportCardNaming
import com.furina.classtools.features.export.ExportCardRenderer
import com.furina.classtools.features.export.ExportCardSpec
import com.furina.classtools.features.export.ExportKind
import com.furina.classtools.features.export.GroupsBlock

/**
 * 导出卡 v2 的 @Preview：直接调用 android.graphics 渲染器生成 Bitmap 显示。
 * 用于 `android preview render` 无设备验收版式（水印/参数区/页脚/编号齐全）。
 */
@Preview(name = "09 导出卡 v2 · 抽签+分组", showBackground = true, widthDp = 412, heightDp = 1400)
@Composable
private fun ExportCardPreview() {
    val now = 1_760_000_000_000L
    val spec = remember {
        ExportCardSpec(
            title = "抽签工作流结果单",
            sub = "Step 1 + Step 2",
            file = ExportCardNaming.fileName(
                kind = ExportKind.DRAW_WORKFLOW,
                params = listOf("范围1-50", "抽10个", "不重复"),
                now = now,
            ),
            rec = ExportCardNaming.recordId("CT", now),
            config = listOf(
                "范围" to "1 - 50",
                "抽取个数" to "10",
                "允许重复" to "否",
                "排除序号" to "无",
                "结果排序" to "按抽取顺序",
                "可用池" to "50",
                "参与名单" to "班级名单（脱敏）",
            ),
            blocks = listOf(
                ChipsBlock(
                    label = "抽签结果",
                    chips = listOf(
                        ChipItem(7, ""),
                        ChipItem(23, ""),
                        ChipItem(41, ""),
                        ChipItem(8, ""),
                        ChipItem(15, ""),
                        ChipItem(33, ""),
                        ChipItem(2, ""),
                        ChipItem(47, ""),
                        ChipItem(19, ""),
                        ChipItem(30, ""),
                    ),
                ),
                GroupsBlock(
                    label = "分组结果",
                    groups = listOf(
                        listOf(ChipItem(1, "陈同学"), ChipItem(2, "林同学"), ChipItem(3, "王同学")),
                        listOf(ChipItem(4, "李同学"), ChipItem(5, "张同学")),
                        listOf(ChipItem(6, "黄同学"), ChipItem(7, "吴同学"), ChipItem(8, "郑同学")),
                    ),
                ),
            ),
            foot = ExportCardNaming.footText(now),
        )
    }
    val bitmap = remember(spec) {
        runCatching { ExportCardRenderer().render(spec) }.getOrNull()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7F6))
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "导出卡片预览",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
