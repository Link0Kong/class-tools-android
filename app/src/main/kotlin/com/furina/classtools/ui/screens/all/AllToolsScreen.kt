package com.furina.classtools.ui.screens.all

import com.furina.classtools.ui.components.CtToastBus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import com.furina.classtools.ui.components.CtGlassIconButton
import com.furina.classtools.ui.components.CtRootHeader
import com.furina.classtools.ui.components.CtStickyHeaderBar
import com.furina.classtools.ui.components.ctStickyHeaderProgress
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.data.tool.ToolCatalog
import com.furina.classtools.data.tool.ToolCategory
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.SectionHeader
import com.furina.classtools.ui.components.reorderableSemantics
import com.furina.classtools.ui.theme.CtAnimatedEntrance
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.CtText
import com.furina.classtools.ui.theme.HarmonyTokens
import com.furina.classtools.ui.theme.rememberHarmonyColors
import com.furina.classtools.ui.theme.rememberReduceMotion
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.furina.classtools.ui.components.ctRootPageEdgeFade
import com.furina.classtools.ui.components.ctHeaderCollapseProgress

/**
 * 「全部工具」页（2026-11-17 按鸿蒙 `AllScreen.ets` 重做）：
 *
 * 视觉/结构：
 * - 标题 26 Bold「全部工具」+ 右侧「编辑首页 / 完成」（13 雾青文字操作）；
 * - 副标题 12：`N 个工具 · X 列`（编辑态换成「点工具加入或移出首页快捷（最多 4 个）」）；
 * - **按分类分组**：学习 / 班级事务 / 系统（只有存在工具的分类才显示），SectionHeader 19 Medium；
 * - ToolTile 网格：<600dp 2 列 / 600–899dp 3 列 / ≥900dp 4 列；卡片白 18 圆角、浅阴影、
 *   40dp tint 单字/图标块、15 名称、12 副标最多 2 行、minHeight 96、padding 12、按压 0.97；
 * - 内容最大宽 880（<900dp）/ 960（≥900），页面左右 20dp，顶部呼吸 8dp。
 *
 * 交互：
 * - 普通态：点卡片打开工具；
 * - **编辑态**（点右上「编辑首页」）：点卡片加入/移出首页快捷（≤4，✓ 角标，第 5 个 toast）；
 * - 编辑态同时保留**长按拖动排序**（原有 task-58/v8 能力）：拖动松手写 `prefs.toolOrder`；
 *   普通态按分类展示，分类内顺序 = 用户拖动后的顺序。
 *
 * 原有的顺序恢复/持久化、转场锚点、读屏语义、拖动几何全部保留。
 */
@Composable
fun AllToolsScreen(
    onOpenTool: (AppTool) -> Unit,
    modifier: Modifier = Modifier,
    /** 当前身份是否管理员：统计工具仅管理员可见（不进入普通成员「全部工具」/首页快捷）。 */
    isAdmin: Boolean = false,
    userDataRepository: UserDataRepository? = null,
    onToolOrderChange: ((List<String>) -> Unit)? = null,
    initialOrder: List<String> = emptyList(),
    skipRestore: Boolean = false,
    previewDraggingToolId: String? = null,
    previewDragOffset: Offset = Offset(190f, 340f),
    /** 当前首页快捷工具 id（编辑态 ✓ 角标；≤4）。 */
    quickToolIds: List<String> = emptyList(),
    /** 编辑态点卡片后回调新的快捷列表（由调用方写 userdata.homeTools）。 */
    onQuickToolsChange: ((List<String>) -> Unit)? = null,
    /** 预览/测试：初始进入编辑态。 */
    initialEditing: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val reduceMotion = rememberReduceMotion()
    // 列表错峰入场（系统「移除动画」/预览渲染时直接终态）
    val animateEntrance = !reduceMotion && !LocalInspectionMode.current
    val colors = rememberHarmonyColors()
    val naturalIds = remember(isAdmin) { ToolCatalog.visibleTools(isAdmin).map { it.id } }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    // 鸿蒙断点：<600 → 2 列；600–899 → 3 列；≥900 → 4 列。
    val columns = when {
        screenWidthDp >= 900 -> 4
        screenWidthDp >= 600 -> 3
        else -> 2
    }
    val contentMaxWidth = if (screenWidthDp >= 900) HarmonyTokens.ContentMaxWidthWide else HarmonyTokens.ContentMaxWidthPhone
    val pagePadding = HarmonyTokens.PagePadding

    var editing by remember { mutableStateOf(initialEditing) }
    var quickDraft by remember { mutableStateOf(quickToolIds) }
    LaunchedEffect(quickToolIds) { quickDraft = quickToolIds }

    var order by remember { mutableStateOf(ToolOrderLogic.normalize(initialOrder, naturalIds)) }
    var restored by remember { mutableStateOf(skipRestore || initialOrder.isNotEmpty()) }
    var userReordered by remember { mutableStateOf(false) }

    // 恢复 prefs.toolOrder（只读；无基线保持自然顺序）。
    LaunchedEffect(Unit) {
        if (restored) return@LaunchedEffect
        runCatching {
            val repository = userDataRepository
            if (repository != null) {
                var sections = repository.currentSections.first()
                if (sections.isEmpty()) {
                    sections = withTimeoutOrNull(AllToolsDragGeometry.STARTUP_RESTORE_WAIT_MS) {
                        repository.currentSections.first { it.isNotEmpty() }
                    } ?: sections
                }
                if (!userReordered) {
                    order = ToolOrderLogic.normalize(ToolOrderPrefs.read(sections), naturalIds)
                }
            }
        }
        restored = true
    }

    // ---- 拖动状态（编辑态使用；几何逻辑保持 task-58/v8 口径） ----
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragPointer by remember { mutableStateOf(Offset.Zero) }
    var dragStartOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastDragEndAt by remember { mutableLongStateOf(0L) }
    var itemWidthPx by remember { mutableFloatStateOf(0f) }
    var gridLeftPx by remember { mutableFloatStateOf(0f) }
    var gridTopPx by remember { mutableFloatStateOf(0f) }
    var containerOrigin by remember { mutableStateOf(Offset.Zero) }
    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    val scrollState = rememberScrollState()

    val baseViewConfiguration = LocalViewConfiguration.current
    val dragViewConfiguration = remember(baseViewConfiguration) {
        object : ViewConfiguration by baseViewConfiguration {
            override val longPressTimeoutMillis: Long = AllToolsDragGeometry.LONG_PRESS_TIMEOUT_MS
        }
    }

    // 大字号：卡片高度随 fontScale 增长，避免 200% 下标题/副标题被固定高度裁切。
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val itemHeightDp = (HarmonyTokens.TileMinHeight.value * fontScale.coerceIn(1f, 1.8f)).dp
    val spacingDp = HarmonyTokens.Gap
    val itemHeightPx = with(density) { itemHeightDp.toPx() }
    val spacingPx = with(density) { spacingDp.toPx() }
    val autoScrollTopEdgePx = with(density) { AllToolsDragGeometry.AUTO_SCROLL_TOP_EDGE_DP.dp.toPx() }
    val autoScrollBottomInsetPx = with(density) { AllToolsDragGeometry.AUTO_SCROLL_BOTTOM_INSET_DP.dp.toPx() }
    val autoScrollStepPx = with(density) { AllToolsDragGeometry.AUTO_SCROLL_STEP_DP.dp.toPx() }

    val effectiveDraggingId = draggingId ?: previewDraggingToolId
    val previewGhostPointer = with(density) {
        Offset(previewDragOffset.x.dp.toPx(), previewDragOffset.y.dp.toPx())
    }
    val effectiveGhostPointer = if (draggingId != null) dragPointer else previewGhostPointer
    val dragMode = editing || previewDraggingToolId != null

    fun persistOrder(newOrder: List<String>) {
        onToolOrderChange?.invoke(newOrder)
        val repository = userDataRepository ?: return
        val now = System.currentTimeMillis()
        scope.launch {
            runCatching {
                val sections = repository.currentSections.first()
                repository.updateCurrent(ToolOrderPrefs.apply(sections, newOrder, now))
            }
        }
    }

    fun finishDrag() {
        if (draggingId == null) return
        draggingId = null
        lastDragEndAt = System.currentTimeMillis()
        val newOrder = order
        if (newOrder != dragStartOrder && newOrder.firstOrNull() != null) {
            userReordered = true
            persistOrder(newOrder)
        }
        dragStartOrder = emptyList()
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun cancelDrag() {
        if (draggingId != null) {
            order = dragStartOrder
            draggingId = null
            dragStartOrder = emptyList()
            lastDragEndAt = System.currentTimeMillis()
        }
    }

    fun moveCardBy(id: String, delta: Int) {
        val newOrder = ToolOrderLogic.moveBy(order, id, delta)
        if (newOrder == order) return
        order = newOrder
        userReordered = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        persistOrder(newOrder)
    }

    fun updateDropTarget() {
        val id = draggingId ?: return
        val draggedIndex = order.indexOf(id)
        if (draggedIndex < 0 || itemWidthPx <= 0f) return
        val (contentX, contentY) = AllToolsDragGeometry.contentPoint(
            pointerX = dragPointer.x,
            pointerY = dragPointer.y,
            gridLeft = gridLeftPx,
            gridTop = gridTopPx,
        )
        val target = ToolOrderLogic.gridTarget(
            pointerX = contentX,
            pointerY = contentY,
            itemWidth = itemWidthPx,
            itemHeight = itemHeightPx,
            spacing = spacingPx,
            itemCount = order.size,
            draggedIndex = draggedIndex,
            columns = columns,
        )
        if (target != draggedIndex) {
            order = ToolOrderLogic.moveToIndex(order, id, target)
        }
    }

    fun onDragDelta(amount: Offset) {
        dragPointer += amount
        updateDropTarget()
    }

    // 边缘自动滚动（拖动中每 80ms 连续滚一小步）。
    LaunchedEffect(draggingId) {
        if (draggingId == null) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { }
            updateDropTarget()
            val direction = AllToolsDragGeometry.autoScrollDirection(
                pointerY = dragPointer.y,
                containerHeight = containerHeightPx,
                topEdge = autoScrollTopEdgePx,
                bottomInset = autoScrollBottomInsetPx,
                scrollValue = scrollState.value,
                maxScroll = scrollState.maxValue,
            )
            if (direction != 0) scrollState.dispatchRawDelta(direction * autoScrollStepPx)
            delay(AllToolsDragGeometry.AUTO_SCROLL_INTERVAL_MS)
        }
    }

    val toolsInOrder = remember(order, isAdmin) {
        order.mapNotNull { id ->
            ToolCatalog.byId(id)?.takeIf { isAdmin || !it.adminOnly }
        }
    }

    fun toggleQuick(tool: AppTool) {
        val current = quickDraft
        val next = if (current.contains(tool.id)) current - tool.id else current + tool.id
        if (next.size > HomeToolsEditorMax) {
            CtToastBus.show("首页最多 4 个")
            return
        }
        quickDraft = next
        onQuickToolsChange?.invoke(next)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // 2026-11-20 页面骨架 v2：常驻悬浮玻璃条（渐变模糊）需要页面级 Haze 源。
    val hazeState = rememberHazeState()

    CompositionLocalProvider(LocalViewConfiguration provides dragViewConfiguration) {
        ExpressiveBackground(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        containerOrigin = coordinates.positionInRoot()
                        containerHeightPx = coordinates.size.height.toFloat()
                    },
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .padding(horizontal = pagePadding)
                        .hazeSource(hazeState)
                        .ctRootPageEdgeFade(scrollState.value.toFloat())
                        .verticalScroll(scrollState),
                ) {
                    HarmonyAllToolsHeader(
                        collapse = ctHeaderCollapseProgress(scrollState.value.toFloat()),
                        editing = editing,
                        toolCount = toolsInOrder.size,
                        columns = columns,
                        dragging = effectiveDraggingId != null,
                        onToggleEdit = {
                            editing = !editing
                            if (!editing) {
                                draggingId = null
                            }
                        },
                    )

                    if (dragMode) {
                        // 编辑态：平铺网格 + 长按拖动排序 + 点选首页快捷（✓ 角标）。
                        Layout(
                            content = {
                                toolsInOrder.forEachIndexed { displayIndex, tool ->
                                    key(tool.id) {
                                        CtAnimatedEntrance(enabled = animateEntrance, index = displayIndex) {
                                        Box(
                                            modifier = Modifier
                                                .alpha(if (tool.id == effectiveDraggingId) 0.22f else 1f)
                                                .pointerInput(tool.id, columns) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = { local ->
                                                            if (draggingId != null) return@detectDragGesturesAfterLongPress
                                                            if (itemWidthPx <= 0f) return@detectDragGesturesAfterLongPress
                                                            val index = order.indexOf(tool.id)
                                                            if (index < 0) return@detectDragGesturesAfterLongPress
                                                            draggingId = tool.id
                                                            dragStartOrder = order
                                                            val (pointerX, pointerY) = AllToolsDragGeometry.pointerForItem(
                                                                index = index,
                                                                localX = local.x,
                                                                localY = local.y,
                                                                columns = columns,
                                                                itemWidth = itemWidthPx,
                                                                itemHeight = itemHeightPx,
                                                                spacing = spacingPx,
                                                                gridLeft = gridLeftPx,
                                                                gridTop = gridTopPx,
                                                            )
                                                            dragPointer = Offset(pointerX, pointerY)
                                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            if (draggingId != null) onDragDelta(dragAmount)
                                                        },
                                                        onDragEnd = { finishDrag() },
                                                        onDragCancel = { cancelDrag() },
                                                    )
                                                },
                                        ) {
                                            AllToolCard(
                                                tool = tool,
                                                onClick = {
                                                    val now = System.currentTimeMillis()
                                                    if (draggingId == null &&
                                                        now - lastDragEndAt > AllToolsDragGeometry.TAP_SUPPRESSION_MS
                                                    ) {
                                                        toggleQuick(tool)
                                                    }
                                                },
                                                modifier = Modifier.fillMaxSize(),
                                                contentSemantics = Modifier.reorderableSemantics(
                                                    index = displayIndex,
                                                    total = toolsInOrder.size,
                                                    itemLabel = tool.name,
                                                    dragging = tool.id == effectiveDraggingId,
                                                    onMoveUp = { moveCardBy(tool.id, -1) },
                                                    onMoveDown = { moveCardBy(tool.id, +1) },
                                                ),
                                                reduceMotion = reduceMotion,
                                                editing = editing,
                                                selected = quickDraft.contains(tool.id),
                                            )
                                        }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 8.dp)
                                .onGloballyPositioned { coordinates ->
                                    gridLeftPx = coordinates.positionInRoot().x - containerOrigin.x
                                    gridTopPx = coordinates.positionInRoot().y - containerOrigin.y
                                },
                        ) { measurables, constraints ->
                            val width = constraints.maxWidth
                            val cols = columns.coerceAtLeast(1)
                            itemWidthPx = ((width - spacingPx * (cols - 1)) / cols).coerceAtLeast(1f)
                            val placeables = measurables.map { measurable ->
                                measurable.measure(
                                    Constraints.fixed(
                                        width = itemWidthPx.roundToInt().coerceAtLeast(1),
                                        height = itemHeightPx.roundToInt(),
                                    ),
                                )
                            }
                            val rows = (placeables.size + cols - 1) / cols
                            val height = max(0, rows * itemHeightPx.roundToInt() + max(0, rows - 1) * spacingPx.roundToInt())
                            layout(width, height) {
                                placeables.forEachIndexed { index, placeable ->
                                    placeable.place(
                                        x = ((index % cols) * (itemWidthPx + spacingPx)).roundToInt(),
                                        y = ((index / cols) * (itemHeightPx + spacingPx)).roundToInt(),
                                    )
                                }
                            }
                        }
                    } else {
                        // 普通态：按鸿蒙分类分组展示（学习 / 班级事务 / 系统）。
                        val grouped = HarmonyAllToolCategories.mapNotNull { category ->
                            val list = toolsInOrder.filter { it.category == category }
                            if (list.isEmpty()) null else category to list
                        }
                        grouped.forEachIndexed { groupIndex, (category, tools) ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SectionHeader(
                                    title = category.label,
                                    modifier = Modifier.padding(top = if (groupIndex == 0) 4.dp else 12.dp),
                                )
                                CategoryGrid(
                                    tools = tools,
                                    columns = columns,
                                    spacing = spacingDp,
                                    itemHeight = itemHeightDp,
                                    reduceMotion = reduceMotion,
                                    editing = editing,
                                    quickIds = quickDraft,
                                    onOpenTool = onOpenTool,
                                    onToggleQuick = { toggleQuick(it) },
                                    onLongPress = {
                                        if (!editing) {
                                            editing = true
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(120.dp))
                }

                // 跟手 ghost（编辑态拖动/预览注入）
                val ghostTool = effectiveDraggingId?.let { ToolCatalog.byId(it) }
                if (ghostTool != null && itemWidthPx > 0f) {
                    val (ghostX, ghostY) = AllToolsDragGeometry.ghostTopLeft(
                        pointerX = effectiveGhostPointer.x,
                        pointerY = effectiveGhostPointer.y,
                        itemWidth = itemWidthPx,
                        itemHeight = itemHeightPx,
                    )
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(ghostX.roundToInt(), ghostY.roundToInt()) }
                            .width(with(density) { itemWidthPx.toDp() })
                            .height(itemHeightDp)
                            .zIndex(10f)
                            .graphicsLayer {
                                scaleX = 0.97f
                                scaleY = 0.97f
                                alpha = 0.96f
                                shadowElevation = 20f
                            },
                    ) {
                        AllToolCard(
                            tool = ghostTool,
                            onClick = {},
                            pressable = false,
                            reduceMotion = reduceMotion,
                        )
                    }
                }

                // 常驻悬浮玻璃条：滚动后由完全透明渐变到「材质 + 模糊」（官方渐变模糊）。
                CtStickyHeaderBar(
                    title = "全部工具",
                    progress = ctStickyHeaderProgress(scrollState.value.toFloat(), density.density),
                    hazeState = hazeState,
                    actions = {
                        CtGlassIconButton(
                            icon = if (editing) Icons.Rounded.Check else Icons.Rounded.Tune,
                            contentDescription = if (editing) "完成编辑" else "编辑首页快捷",
                            onClick = {
                                editing = !editing
                                if (!editing) draggingId = null
                            },
                        )
                    },
                )
            }
        }
    }
}

/** 首页快捷上限（与 userdata.homeTools 契约一致）。 */
private const val HomeToolsEditorMax: Int = 4

/** 鸿蒙端「全部工具」分类顺序：学习 → 班级事务 → 系统（无工具的分类不显示）。 */
private val HarmonyAllToolCategories = listOf(
    ToolCategory.STUDY,
    ToolCategory.CLASS,
    ToolCategory.SYSTEM,
)

/** 普通态分类网格（2/3/4 列，无拖动，点击打开工具）。 */
@Composable
private fun CategoryGrid(
    tools: List<AppTool>,
    columns: Int,
    spacing: androidx.compose.ui.unit.Dp,
    itemHeight: androidx.compose.ui.unit.Dp,
    reduceMotion: Boolean,
    editing: Boolean,
    quickIds: List<String>,
    onOpenTool: (AppTool) -> Unit,
    onToggleQuick: (AppTool) -> Unit,
    onLongPress: () -> Unit = {},
) {
    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    Layout(
        content = {
            tools.forEach { tool ->
                key(tool.id) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(tool.id) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    if (awaitLongPressOrCancellation(down.id) != null) onLongPress()
                                }
                            },
                    ) {
                        AllToolCard(
                            tool = tool,
                            onClick = {
                                if (editing) onToggleQuick(tool) else onOpenTool(tool)
                            },
                            modifier = Modifier.fillMaxSize(),
                            reduceMotion = reduceMotion,
                            editing = editing,
                            selected = quickIds.contains(tool.id),
                        )
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val cols = columns.coerceAtLeast(1)
        val itemWidth = ((width - spacingPx * (cols - 1)) / cols).coerceAtLeast(1f)
        val placeables = measurables.map { measurable ->
            measurable.measure(
                Constraints.fixed(
                    width = itemWidth.roundToInt().coerceAtLeast(1),
                    height = itemHeightPx.roundToInt(),
                ),
            )
        }
        val rows = (placeables.size + cols - 1) / cols
        val height = max(0, rows * itemHeightPx.roundToInt() + max(0, rows - 1) * spacingPx.roundToInt())
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                placeable.place(
                    x = ((index % cols) * (itemWidth + spacingPx)).roundToInt(),
                    y = ((index / cols) * (itemHeightPx + spacingPx)).roundToInt(),
                )
            }
        }
    }
}

/** 页头（2026-11-20 重做）：大标题「全部工具」+ 玻璃动作（编辑/完成）+ 副标题统计。 */
@Composable
private fun HarmonyAllToolsHeader(
    collapse: Float,
    editing: Boolean,
    toolCount: Int,
    columns: Int,
    dragging: Boolean,
    onToggleEdit: () -> Unit,
) {
    CtRootHeader(
        title = "全部工具",
        collapse = collapse,
        eyebrow = "工具库",
        subtitle = when {
            dragging -> "松手保存顺序"
            editing -> "点工具加入或移出首页快捷（最多 4 个）"
            else -> "$toolCount 个工具 · $columns 列 · 长按进入编辑后可拖动排序"
        },
        actions = {
            CtGlassIconButton(
                icon = if (editing) Icons.Rounded.Check else Icons.Rounded.Tune,
                contentDescription = if (editing) "完成编辑" else "编辑首页快捷",
                onClick = onToggleEdit,
                tint = if (editing) MaterialTheme.colorScheme.primary else null,
            )
        },
    )
}





