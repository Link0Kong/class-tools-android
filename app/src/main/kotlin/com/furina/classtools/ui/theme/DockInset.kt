package com.furina.classtools.ui.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 浮岛底栏悬浮遮住的内容高度（v4 统一避让 token）。
 *
 * 顶层页面（工作台/全部/我的）当前底部留白 120dp；工具页不显示底栏。
 * 新页面/列表若需要「内容可滚动到玻璃下方但不被永久遮挡」，读取本值作为底部 spacer。
 *
 * 由 RootTabs 在顶层 Tab 提供（96dp）；默认 96dp 便于预览/单页复用。
 */
val LocalCtDockInset: ProvidableCompositionLocal<Dp> = compositionLocalOf { 96.dp }
