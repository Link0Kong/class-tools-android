package com.furina.classtools.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.auth.StubAuthRepository
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.tool.ToolCatalog
import com.furina.classtools.ui.components.BrandMark
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.screens.all.AllToolsScreen
import com.furina.classtools.ui.screens.home.HomeScreen
import com.furina.classtools.ui.screens.identity.IdentityScreen
import com.furina.classtools.ui.screens.profile.ProfileScreen
import com.furina.classtools.ui.screens.tools.ToolPlaceholderScreen
import com.furina.classtools.ui.theme.ClassToolsTheme
import com.furina.classtools.ui.theme.CtShapeTokens

private val previewSession = Session(
    sid = "2024000001",
    name = "陈同学",
    token = "preview-token",
    role = "副团支部书记",
    badge = "班委",
)

@Preview(name = "01 身份门禁 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun IdentityLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        IdentityScreen(authRepository = StubAuthRepository(), onVerified = {})
    }
}

@Preview(name = "02 身份门禁 · 深色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun IdentityDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        IdentityScreen(authRepository = StubAuthRepository(), onVerified = {})
    }
}

@Preview(name = "03 工作台 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun HomeLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        HomeScreen(session = previewSession, onOpenTool = {}, onHomeToolsChange = {})
    }
}

@Preview(name = "04 工作台 · 深色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun HomeDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        HomeScreen(session = previewSession, onOpenTool = {}, onHomeToolsChange = {})
    }
}

@Preview(name = "05 全部工具", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun AllToolsPreview() {
    ClassToolsTheme(darkTheme = false) {
        AllToolsScreen(onOpenTool = {})
    }
}

@Preview(name = "06 我的", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun ProfilePreview() {
    ClassToolsTheme(darkTheme = false) {
        ProfileScreen(session = previewSession, onSignOut = {})
    }
}

@Preview(name = "07 工具占位 · 抽签", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun ToolPreview() {
    ClassToolsTheme(darkTheme = false) {
        ToolPlaceholderScreen(tool = ToolCatalog.tools.first(), onBack = {})
    }
}

@Preview(name = "08 设计令牌画廊", showBackground = true, widthDp = 412, heightDp = 1500)
@Composable
private fun DesignTokensPreview() {
    ClassToolsTheme(darkTheme = false) {
        ExpressiveBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(size = 44.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("班级工具站", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Material 3 · Expressive",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                TokenSection("颜色 · Color roles") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Swatch("primary", MaterialTheme.colorScheme.primary)
                        Swatch("secondary", MaterialTheme.colorScheme.secondary)
                        Swatch("tertiary", MaterialTheme.colorScheme.tertiary)
                        Swatch("primaryContainer", MaterialTheme.colorScheme.primaryContainer)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Swatch("surface", MaterialTheme.colorScheme.surface)
                        Swatch("surfaceContainer", MaterialTheme.colorScheme.surfaceContainer)
                        Swatch("surfaceHigh", MaterialTheme.colorScheme.surfaceContainerHigh)
                        Swatch("error", MaterialTheme.colorScheme.error)
                    }
                }

                TokenSection("字体 · Type scale") {
                    Text("Display", style = MaterialTheme.typography.displaySmall)
                    Text("Headline", style = MaterialTheme.typography.headlineMedium)
                    Text("Title", style = MaterialTheme.typography.titleLarge)
                    Text("Body · 中文行高更松，阅读更稳", style = MaterialTheme.typography.bodyLarge)
                    Text("Label", style = MaterialTheme.typography.labelMedium)
                }

                TokenSection("形状 · Shape") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ShapeSample(CtShapeTokens.toolCard, "toolCard")
                        ShapeSample(CtShapeTokens.pill, "pill")
                        ShapeSample(CtShapeTokens.hero, "hero")
                    }
                }

                TokenSection("组件 · 按钮与胶囊") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = {}, shape = CtShapeTokens.pill) { Text("主要") }
                        FilledTonalButton(onClick = {}, shape = CtShapeTokens.pill) { Text("次要") }
                        OutlinedButton(onClick = {}, shape = CtShapeTokens.pill) { Text("描边") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CtPill("已同步")
                        CtPill("42 人")
                        CtPill("本周")
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenSection(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun Swatch(label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(modifier = Modifier.size(52.dp), shape = RoundedCornerShape(16.dp), color = color) {}
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ShapeSample(shape: androidx.compose.ui.graphics.Shape, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(width = 72.dp, height = 52.dp),
            shape = shape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {}
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

