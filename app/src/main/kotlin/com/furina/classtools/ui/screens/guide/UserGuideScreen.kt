package com.furina.classtools.ui.screens.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.expressive.CtBottomSheetHeader
import com.furina.classtools.ui.components.expressive.CtBottomSheetWithState
import com.furina.classtools.ui.theme.CtShapeTokens
import kotlinx.coroutines.launch

/**
 * 使用说明（卡片化，对齐三端共享契约 `web/使用说明_卡片化重构契约_20261112.md` §5）。
 *
 * 结构（一屏一张卡）：
 * 1. 顶栏：返回 + 「使用说明」+ 副标题 + 右侧「总览」按钮（打开全部卡片清单）；
 * 2. 分类 chips（LazyRow，5 个；点 chip 跳到该分类首张卡，当前卡分类高亮）；
 * 3. `HorizontalPager` 轮播 20 张卡：卡头分类胶囊 + 页码、hero 自绘配图（11 scene）、
 *    标题 + 一句话作用、3 条步骤（①②③）、💡 提示；卡片内容超出时卡内纵向滚动；
 * 4. 底部：上一张 / 页码 / 下一张（≥48dp，首尾禁用、不循环）；
 * 5. 总览：`ModalBottomSheet` 分组清单（入门 / 学习 / 生活 / 协作 / 更多），
 *    点条目关闭弹层并滚到对应卡；返回键先关弹层。
 *
 * 纯本地静态内容：**零网络请求、零外部链接、零新增依赖**（配图全部 Compose 自绘）。
 * 纵向滚动不被劫持：卡片内部 `verticalScroll` 只处理纵向，横向手势交给 pager。
 *
 * 接线（MainNavHost）：
 * ```kotlin
 * "guide" -> UserGuideScreen(onBack = { navController.popBackStack() })
 * // 我的页入口：SettingActionRow(label = "使用说明", actionText = "查看", onAction = { nav.navigate("guide") })
 * ```
 */
@Composable
fun UserGuideScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cards = UserGuideContent.cards
    // 空列表兜底（正常恒为 20 张）：pager 至少一页，避免 coerceIn 越界。
    val pageCount = cards.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    var overviewVisible by rememberSaveable { mutableStateOf(false) }

    // 返回键：总览打开时先关总览（sheet 自身也处理返回，双保险）。
    BackHandler(enabled = overviewVisible) { overviewVisible = false }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            CtTopBar(
                title = "使用说明",
                subtitle = "左右滑动切换 · 一键总览",
                onBack = onBack,
                actions = {
                    TextButton(
                        onClick = { overviewVisible = true },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GridView,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = "总览", style = MaterialTheme.typography.labelLarge)
                    }
                },
            )

            GuideCategoryChips(
                current = cards.getOrNull(pagerState.currentPage)?.category,
                onSelect = { category ->
                    val index = UserGuideContent.firstIndexOf(category)
                    if (index >= 0) scope.launch { pagerState.animateScrollToPage(index) }
                },
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 18.dp),
                pageSpacing = 12.dp,
                beyondViewportPageCount = 1,
            ) { page ->
                cards.getOrNull(page)?.let { card ->
                    // 平板/桌面限宽居中，避免长句行宽过大；手机满宽。
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GuideCardPage(
                            card = card,
                            pageIndex = page,
                            pageCount = cards.size,
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxSize()
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            }

            GuidePagerBar(
                pageIndex = pagerState.currentPage.coerceIn(0, pageCount - 1),
                pageCount = cards.size,
                onPrevious = {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            (pagerState.currentPage - 1).coerceAtLeast(0),
                        )
                    }
                },
                onNext = {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            (pagerState.currentPage + 1).coerceAtMost(pageCount - 1),
                        )
                    }
                },
            )
        }
    }

    if (overviewVisible) {
        GuideOverviewSheet(
            cards = cards,
            onDismiss = { overviewVisible = false },
            onJump = { index ->
                overviewVisible = false
                scope.launch {
                    pagerState.animateScrollToPage(index.coerceIn(0, pageCount - 1))
                }
            },
        )
    }
}

// =============================================================================
// 分类 chips
// =============================================================================

@Composable
private fun GuideCategoryChips(
    current: GuideCategory?,
    onSelect: (GuideCategory) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = GuideCategory.entries, key = { it.id }) { category ->
            GuideCategoryChip(
                selected = category == current,
                label = category.label,
                onClick = { onSelect(category) },
            )
        }
    }
}

@Composable
private fun GuideCategoryChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        shape = CtShapeTokens.pill,
        color = if (selected) scheme.primary else scheme.surfaceContainerHigh,
        contentColor = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

// =============================================================================
// 卡片
// =============================================================================

@Composable
private fun GuideCardPage(
    card: GuideCard,
    pageIndex: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // hero 约屏幕高度 25%（契约 §1.2：卡片上部约 30–36% 高度），并夹在可读区间。
    val heroHeight = (LocalConfiguration.current.screenHeightDp * 0.25f)
        .dp
        .coerceIn(132.dp, 196.dp)

    Surface(
        modifier = modifier,
        shape = CtShapeTokens.hero,
        color = scheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1) 卡头：分类胶囊 + 页码
            Row(verticalAlignment = Alignment.CenterVertically) {
                val palette = guideSceneColors(card.tone)
                CtPill(
                    text = card.category.label,
                    containerColor = palette.soft,
                    contentColor = palette.ink,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${pageIndex + 1}/$pageCount",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                )
            }

            // 2) hero 配图（Compose 自绘）
            GuideSceneArt(
                scene = card.scene,
                tone = card.tone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight),
            )

            // 3) 标题 + 一句话作用
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = card.subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                )
            }

            // 4) 上手步骤（①②③）
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                card.steps.forEachIndexed { index, step ->
                    GuideStepRow(index = index, text = step, tone = card.tone)
                }
            }

            // 5) 提示
            GuideTipRow(tip = card.tip, tone = card.tone)
        }
    }
}

/** 步骤行：① 序号 + 步骤文案。 */
@Composable
private fun GuideStepRow(
    index: Int,
    text: String,
    tone: GuideTone,
) {
    val palette = guideSceneColors(tone)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = CIRCLED_NUMBERS[index.coerceIn(0, CIRCLED_NUMBERS.lastIndex)],
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = palette.accent,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 提示行：💡 + 一句提示。 */
@Composable
private fun GuideTipRow(
    tip: String,
    tone: GuideTone,
) {
    val palette = guideSceneColors(tone)
    Surface(
        shape = CtShapeTokens.listItem,
        color = palette.soft.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "💡",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = tip,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.ink,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// =============================================================================
// 底部翻页栏
// =============================================================================

@Composable
private fun GuidePagerBar(
    pageIndex: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val hasPrevious = pageIndex > 0
    val hasNext = pageIndex < pageCount - 1
    Surface(color = scheme.surface.copy(alpha = 0.72f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onPrevious,
                enabled = hasPrevious,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronLeft,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(text = "上一张", style = MaterialTheme.typography.labelLarge)
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${pageIndex + 1} / $pageCount",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onNext,
                enabled = hasNext,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(text = "下一张", style = MaterialTheme.typography.labelLarge)
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// =============================================================================
// 一键总览（ModalBottomSheet）
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuideOverviewSheet(
    cards: List<GuideCard>,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    CtBottomSheetWithState(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        CtBottomSheetHeader(
            title = "全部说明 · ${cards.size} 张",
            subtitle = "按分类查看，点条目跳到对应卡",
        )
        LazyColumn(
            modifier = Modifier
                .heightIn(max = 540.dp)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        ) {
            GuideCategory.entries.forEach { category ->
                val indices = cards.indices.filter { cards[it].category == category }
                if (indices.isEmpty()) return@forEach
                item(key = "header-${category.id}") {
                    GuideOverviewGroupHeader(category = category, count = indices.size)
                }
                items(items = indices, key = { cards[it].id }) { index ->
                    GuideOverviewRow(
                        card = cards[index],
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onJump(index)
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 总览分组标题：分类名 + 张数。 */
@Composable
private fun GuideOverviewGroupHeader(
    category: GuideCategory,
    count: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = category.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "$count 张",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 总览条目：小场景图标 + 标题 + 一句话作用 + ›。 */
@Composable
private fun GuideOverviewRow(
    card: GuideCard,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val palette = guideSceneColors(card.tone)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CtShapeTokens.listItem)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CtShapeTokens.pill,
            color = palette.soft,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = guideSceneIcon(card.scene),
                    contentDescription = null,
                    tint = palette.ink,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Text(
                text = card.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 步骤序号字形（① ② ③ ④；契约要求 3 条，保留 ④ 兜底）。 */
private val CIRCLED_NUMBERS: List<String> = listOf("①", "②", "③", "④")
