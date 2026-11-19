package com.furina.classtools.ui.components.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.components.background.CtGlassSurface
import com.furina.classtools.ui.theme.CtElevation
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.rememberCtColors

/**
 * 规范卡片（旧名 `CtGlassCard` 保留）：**磨砂玻璃材质**（2026-11-19 起）。
 *
 * 材质来自 `ui/components/background/CtGlassSurface.kt`（唯一来源）：
 * 半透明底 + 顶部线性高光 + 1px 高光边 + 柔和外阴影；背景越花底越实（AA 红线）。
 *
 * 用法不变：`CtGlassCard(onClick = …) { content() }` 或 `CtGlassCard { content() }`。
 *
 * @param frosted false = 实色卡片（表单/列表等最需要稳可读性的场合）
 */
@Composable
fun CtGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = CtShape.card,
    onClick: (() -> Unit)? = null,
    frosted: Boolean = true,
    content: @Composable () -> Unit,
) {
    CtGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        onClick = onClick,
        frosted = frosted,
        body = true,
    ) {
        content()
    }
}


/** 兼容旧调用：圆角 20dp 的卡片形状。 */
@Composable
fun CtHarmonyCardShape(): Shape = CtShape.card


