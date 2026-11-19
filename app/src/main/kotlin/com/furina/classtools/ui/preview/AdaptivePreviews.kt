package com.furina.classtools.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.tool.ToolCatalog
import com.furina.classtools.ui.adaptive.CtAdaptive
import com.furina.classtools.ui.adaptive.CtAdaptiveContent
import com.furina.classtools.ui.adaptive.rememberCtAdaptiveSpec
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.screens.profile.ProfileScreen
import com.furina.classtools.ui.theme.ClassToolsTheme

/**
 * v2.0.0 自适应框架预览：compact / medium / expanded 断点 + 平板限宽双栏示例。
 */
@Preview(name = "70 自适应 · compact（手机 380dp）", showBackground = true, widthDp = 380, heightDp = 420)
@Composable
private fun AdaptiveCompactPreview() {
    ClassToolsTheme(darkTheme = false) {
        AdaptiveSpecPreviewFrame()
    }
}

@Preview(name = "71 自适应 · medium（折叠屏/横屏 700dp）", showBackground = true, widthDp = 700, heightDp = 420)
@Composable
private fun AdaptiveMediumPreview() {
    ClassToolsTheme(darkTheme = false) {
        AdaptiveSpecPreviewFrame()
    }
}

@Preview(name = "72 自适应 · expanded（平板 1000dp）", showBackground = true, widthDp = 1000, heightDp = 460)
@Composable
private fun AdaptiveExpandedPreview() {
    ClassToolsTheme(darkTheme = true) {
        AdaptiveSpecPreviewFrame()
    }
}

@Preview(name = "73 我的 · 平板限宽", showBackground = true, widthDp = 1100, heightDp = 900)
@Composable
private fun ProfileTabletPreview() {
    val session = Session(
        sid = "3260707024",
        name = "陈子麒",
        token = "preview-token",
        role = "副团支部书记",
        badge = "班委",
    )
    ClassToolsTheme(darkTheme = false) {
        val spec = rememberCtAdaptiveSpec()
        CtAdaptiveContent(spec = spec) {
            ProfileScreen(session = session, onSignOut = {})
        }
    }
}

@Composable
private fun AdaptiveSpecPreviewFrame() {
    val spec = rememberCtAdaptiveSpec()
    ExpressiveBackground(modifier = Modifier.fillMaxSize()) {
        CtAdaptiveContent(spec = spec) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "自适应规格 · ${spec.widthClass}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpecChip("宽", "${spec.widthDp}dp")
                        Spacer(Modifier.width(8.dp))
                        SpecChip("限宽", "${spec.contentMaxWidth.value.toInt()}dp")
                        Spacer(Modifier.width(8.dp))
                        SpecChip("边距", "${spec.horizontalPadding.value.toInt()}dp")
                        Spacer(Modifier.width(8.dp))
                        SpecChip("列", "${spec.gridColumns}")
                        Spacer(Modifier.width(8.dp))
                        SpecChip("双栏", if (spec.twoPane) "是" else "否")
                    }
                }
                Text(
                    text = "工具目录 ${ToolCatalog.tools.size} 个 · 内容宽 " +
                        "${CtAdaptive.effectiveContentWidthDp(spec.widthDp)}dp",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SpecChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
