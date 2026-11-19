package com.furina.classtools.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.components.glass.CtGlass
import com.furina.classtools.ui.components.glass.CtGlassSurface
import com.furina.classtools.ui.components.glass.ctDockGlass
import com.furina.classtools.ui.components.glass.ctGlassSurface
import com.furina.classtools.ui.theme.ClassToolsTheme
import com.furina.classtools.ui.theme.CtShapeTokens

/**
 * v3 液态玻璃组件预览：玻璃卡片（浅/深）、浮岛底栏玻璃（浅/深）、折射高光特写。
 */
@Composable
private fun GlassPreviewBackground(
    dark: Boolean,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val base = if (dark) Color(0xFF0E1211) else Color(0xFFF2F6F4)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(
                        if (dark) Color(0xFF1A2C28) else Color(0xFFDDEBE6),
                        base,
                    ),
                ),
            ),
        content = content,
    )
}

@Composable
private fun GlassCardPreviewFrame(dark: Boolean) {
    ClassToolsTheme(darkTheme = dark) {
        GlassPreviewBackground(dark = dark) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = "液态玻璃 · 卡片",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                CtGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CtShapeTokens.hero,
                ) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("班级工具站", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "半透明玻璃 + 顶部折射高光 + 1dp 描边",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                CtGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("玻璃容器 24dp 圆角", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun DockGlassPreviewFrame(dark: Boolean) {
    ClassToolsTheme(darkTheme = dark) {
        GlassPreviewBackground(dark = dark) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 14.dp)
                        .fillMaxWidth(0.9f)
                        .height(60.dp)
                        .ctDockGlass(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    repeat(3) { index ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(
                                        if (index == 0) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        RoundedCornerShape(6.dp),
                                    ),
                            )
                            Text(
                                text = listOf("工作台", "工具", "我的")[index],
                                style = MaterialTheme.typography.labelSmall,
                                color = if (index == 0) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RefractionCloseup(dark: Boolean) {
    ClassToolsTheme(darkTheme = dark) {
        GlassPreviewBackground(dark = dark) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "折射高光特写",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .ctGlassSurface(
                            tokens = if (dark) CtGlass.dark else CtGlass.light,
                            shape = CtShapeTokens.hero,
                        ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(84.dp)
                            .ctGlassSurface(
                                tokens = if (dark) CtGlass.dark else CtGlass.light,
                                shape = RoundedCornerShape(20.dp),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(84.dp)
                            .ctGlassSurface(
                                tokens = if (dark) CtGlass.dark else CtGlass.light,
                                shape = RoundedCornerShape(20.dp),
                            ),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Preview(name = "110 玻璃卡片 · 浅色", showBackground = true, widthDp = 412, heightDp = 720)
@Composable
private fun GlassCardLightPreview() {
    GlassCardPreviewFrame(dark = false)
}

@Preview(name = "111 玻璃卡片 · 深色", showBackground = true, widthDp = 412, heightDp = 720)
@Composable
private fun GlassCardDarkPreview() {
    GlassCardPreviewFrame(dark = true)
}

@Preview(name = "112 浮岛底栏玻璃 · 浅色", showBackground = true, widthDp = 412, heightDp = 260)
@Composable
private fun DockGlassLightPreview() {
    DockGlassPreviewFrame(dark = false)
}

@Preview(name = "113 浮岛底栏玻璃 · 深色", showBackground = true, widthDp = 412, heightDp = 260)
@Composable
private fun DockGlassDarkPreview() {
    DockGlassPreviewFrame(dark = true)
}

@Preview(name = "114 折射高光特写 · 浅色", showBackground = true, widthDp = 412, heightDp = 520)
@Composable
private fun GlassRefractionLightPreview() {
    RefractionCloseup(dark = false)
}

@Preview(name = "115 折射高光特写 · 深色", showBackground = true, widthDp = 412, heightDp = 520)
@Composable
private fun GlassRefractionDarkPreview() {
    RefractionCloseup(dark = true)
}

@Preview(name = "116 玻璃卡片 · 平板", showBackground = true, widthDp = 900, heightDp = 600)
@Composable
private fun GlassTabletPreview() {
    ClassToolsTheme(darkTheme = false) {
        GlassPreviewBackground(dark = false) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(30.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                CtGlassSurface(modifier = Modifier.weight(1f).height(360.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("玻璃卡 A", style = MaterialTheme.typography.titleMedium)
                    }
                }
                CtGlassSurface(modifier = Modifier.weight(1f).height(360.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("玻璃卡 B", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
