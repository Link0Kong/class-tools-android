package com.furina.classtools.ui.components.expressive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.components.BrandMark

/**
 * 大标题折叠顶栏（M3 LargeTopAppBar + 品牌条）。
 *
 * 结构：
 * ```
 * ┌ 状态栏 insets（edge-to-edge）┐
 * │ ●●● 班级工具站              │  ← 品牌条（BrandMark + 站名；可关闭）
 * │ 大标题（滚动时折叠到 64dp）  │  ← LargeTopAppBar
 * │ 副标题                      │
 * └─────────────────────────────┘
 * ```
 * - insets：品牌条承担 statusBars；LargeTopAppBar 的 windowInsets 置 0，避免二次留白；
 * - 颜色：展开 surface、折叠后 surfaceContainer；
 * - edge-to-edge：调用方不需要再套 statusBarsPadding。
 *
 * 折叠有两种用法：
 * 1) **简单版**（本重载）：内部自动创建 scrollBehavior，但调用方无法把 scroll 连接挂到内容上，
 *    因此顶栏是静态大标题（不随滚动折叠）；
 * 2) **折叠版**（[CtTopAppBarLargeWithBehavior]）：调用方创建 scrollBehavior，并在滚动容器上
 *    挂 `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`，即可获得真实折叠效果。
 *
 * @param title 页面标题
 * @param subtitle 可选副标题（如「第 12 周 · 11/3-11/9」）
 * @param onBack 返回回调；null 表示根页面（不显示返回键）
 * @param showBrandBar 是否显示品牌条（二级工具页可关掉，仅根页面保留）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CtTopAppBarLarge(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    showBrandBar: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CtTopAppBarLargeImpl(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        onBack = onBack,
        showBrandBar = showBrandBar,
        scrollBehavior = null,
        actions = actions,
    )
}

/**
 * 大标题折叠顶栏 · 可折叠版。
 *
 * 用法：
 * ```
 * val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
 * Column(Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)) {
 *     CtTopAppBarLargeWithBehavior(title = "抽签", scrollBehavior = scrollBehavior, onBack = {})
 *     LazyColumn(Modifier.weight(1f)) { ... }
 * }
 * ```
 * 注意：本重载的参数含 material3 实验类型 [TopAppBarScrollBehavior]（1.4.0 尚未稳定），
 * 调用处需要 `@OptIn(ExperimentalMaterial3Api::class)`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CtTopAppBarLargeWithBehavior(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    showBrandBar: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CtTopAppBarLargeImpl(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        onBack = onBack,
        showBrandBar = showBrandBar,
        scrollBehavior = scrollBehavior,
        actions = actions,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CtTopAppBarLargeImpl(
    title: String,
    modifier: Modifier,
    subtitle: String?,
    onBack: (() -> Unit)?,
    showBrandBar: Boolean,
    scrollBehavior: TopAppBarScrollBehavior?,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showBrandBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BrandMark(size = 28.dp)
                Text(
                    text = "班级工具站",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        LargeTopAppBar(
            title = {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                }
            },
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            // 品牌条已消费状态栏 insets；这里置 0，避免重复留白（edge-to-edge 关键）。
            windowInsets = if (showBrandBar) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets,
        )
    }
}
