package com.furina.classtools.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.tool.ToolCatalog
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.screens.all.AllToolsScreen
import com.furina.classtools.ui.screens.notices.NoticeAttachmentThumb
import com.furina.classtools.ui.screens.notices.NoticeRawAttachmentLoader
import com.furina.classtools.ui.theme.ClassToolsTheme

/**
 * task-14 预览（通知附件预览 / 全部工具拖动中态）。
 *
 * 独立文件；全部走注入数据 + skipRestore，不读 userdata、不发网络请求。
 */
private const val PREVIEW_PNG_DATA_URI: String =
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAGAAAABACAYAAADlNHIOAAABRUlEQVR42u3coW4CQRCA4WXDs2GrSGUNAtequiocqnUIDJKgavtixbWKhFyA291wub3j+zWIm39nduaSm8nv8fgX0BtRCAggAAQQgH6YlvzpebMWuSvsl+9Zv5/ktKECf38RyQKawf9+/RDlBk9fq2wJSQLOgy/weSLaJLQKOAVf4MtF3JIQ1fzuuRXHpDbU6S8jJW7R6e83C6LT328WmISHOAlf6nnHVpuDd0HByzgQQAAI0AWZD2QAASCAABCgCzqxOxxEr8HLfC4DlCAQ8BB3QEm9gwwgAASYAx6pz5cBShAIMAdABhAAAsZ0B/x8Lqp9qNnbVgaAAALQ4R0wpDorA0DAYAUM+VOkUOH6gmQBuVs/EIqWd8Sxf5BX8+lvFSALul9dY1tK7dtS7AuqYF+Q/REVbMwiooKdcTAJEwACCMD9+Ad+mXqGYQC4eQAAAABJRU5ErkJggg=="

private val previewLoader = NoticeRawAttachmentLoader { _, ordinal, _ ->
    if (ordinal == 0) PREVIEW_PNG_DATA_URI else null
}

@Preview(name = "40 通知附件 图片预览 · 浅色", showBackground = true, widthDp = 412, heightDp = 720)
@Composable
private fun NoticeAttachmentPreviewLight() {
    ClassToolsTheme(darkTheme = false) {
        ExpressiveBackground(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "通知详情 · 图片附件",
                    style = MaterialTheme.typography.titleLarge,
                )
                NoticeAttachmentThumb(
                    loader = previewLoader,
                    rowId = "auto_preview",
                    ordinal = 0,
                    manual = false,
                    title = "图片",
                    caption = "班级活动照片",
                )
                Text(
                    text = "fallback：解码失败 / 无 data URI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NoticeAttachmentThumb(
                    loader = previewLoader,
                    rowId = "auto_preview",
                    ordinal = 1,
                    manual = false,
                    title = "图片",
                    caption = "远程图片（无 data URI，暂不支持预览）",
                )
                NoticeAttachmentThumb(
                    loader = null,
                    rowId = "auto_preview",
                    ordinal = 0,
                    manual = false,
                    title = "图片",
                    caption = "未接线按需加载器时的占位",
                )
            }
        }
    }
}

@Preview(name = "41 通知附件 图片预览 · 深色", showBackground = true, widthDp = 412, heightDp = 620)
@Composable
private fun NoticeAttachmentPreviewDark() {
    ClassToolsTheme(darkTheme = true) {
        ExpressiveBackground(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("通知详情 · 图片附件（深色）", style = MaterialTheme.typography.titleLarge)
                NoticeAttachmentThumb(
                    loader = previewLoader,
                    rowId = "manual_preview",
                    ordinal = 0,
                    manual = true,
                    title = "IMG_20261106_1042.png",
                )
            }
        }
    }
}

@Preview(name = "42 全部工具 拖动中 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun AllToolsDraggingLight() {
    ClassToolsTheme(darkTheme = false) {
        AllToolsScreen(
            onOpenTool = {},
            skipRestore = true,
            initialOrder = ToolCatalog.tools.map { it.id },
            previewDraggingToolId = "hw",
            previewDragOffset = Offset(210f, 420f),
        )
    }
}

@Preview(name = "43 全部工具 拖动中 · 深色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun AllToolsDraggingDark() {
    ClassToolsTheme(darkTheme = true) {
        AllToolsScreen(
            onOpenTool = {},
            skipRestore = true,
            initialOrder = listOf("hw", "draw", "nt", "tt", "fit", "group", "power", "water", "roster", "settings"),
            previewDraggingToolId = "draw",
            previewDragOffset = Offset(120f, 610f),
        )
    }
}

@Suppress("unused")
@Composable
private fun NoticeAttachmentPreviewSpacer() {
    Spacer(modifier = Modifier.fillMaxWidth().height(8.dp))
}
