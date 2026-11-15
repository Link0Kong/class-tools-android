package com.furina.classtools.ui.screens.all

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.data.tool.ToolCatalog
import com.furina.classtools.ui.adaptive.rememberCtAdaptiveSpec
import com.furina.classtools.ui.components.CtBrandDots
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.reorderableSemantics
import com.furina.classtools.ui.theme.rememberCtHeroColors
import com.furina.classtools.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 全部工具 v7：渐变页头 + 2 列分类色卡片 + 长按拖动排序。
 *
 * 交互（与 `mp-class-tools/pages/all/all.js` 拖动 v2 同口径，**task-58 修复后**）：
 * - **长按 350ms** 进入拖动（[AllToolsDragGeometry.LONG_PRESS_TIMEOUT_MS]，与 mp 一致）；
 * - ghost 以指针为中心跟手；网格按「行 + x 中线」计算插入位置；
 * - 拖动中实时让位（被拖卡片半透明、ghost 覆盖）；靠近上下边缘**连续**自动滚动
 *   （[AllToolsDragGeometry.autoScrollDirection] + 80ms 节流，不再只在移动事件里滚一次）；
 * - 松手落位：**只有顺序真的变化时才**回调 [onToolOrderChange] 并（传入仓库时）写
 *   `userdata v6 prefs.toolOrder`（`at.prefs` 提升，等价 mp `toolOrderSetAt` 语义）；默认自然顺序不写；
 * - 拖动结束后 350ms 内的点击被抑制（拖动不触发打开工具，对齐 mp `_supTapUntil`）。
 *
 * 视觉（v7）：
 * - 页头渐变（品牌雾青→灰蓝）+ 工具数量（拖动中同一胶囊换成「松手保存」，**页头高度不变**，
 *   避免拖动开始瞬间把网格整体下推导致落位几何跳变）；
 * - 卡片 2/3/4 列随窗口（手机 2 列），图标 chip + 主标题 + **副标题最多 2 行**，按压缩放；
 * - 网格用自定义 [Layout] + `key(tool.id)` 保证拖动重排时卡片节点 identity 稳定
 *   （不会因顺序变化重建 `pointerInput` 而中断手势）。
 *
 * 拖动几何契约（task-58 根因，详见 [AllToolsDragGeometry]）：
 * - `gridLeft/gridTop` 是 `onGloballyPositioned` 上报的**实时视口坐标**（滚动已包含）；
 *   视口 ↔ 内容换算**不再加减 scroll**，否则页面滚动后幽灵偏移、跨行落位错一行；
 * - 指针 → 落位 → `ToolOrderLogic.gridTarget/moveToIndex`；顺序变化时 `key(tool.id)` 保持
 *   被拖卡片节点存活，手势不会在第一次重排时被取消。
 *
 * 接线（Lead 负责 MainNavHost）：
 * ```kotlin
 * AllToolsScreen(
 *     onOpenTool = { tool -> navController.navigate(Routes.tool(tool.id)) },
 *     userDataRepository = container.userDataRepository,
 *     onToolOrderChange = { order -> /* 可选：Lead 自行写 prefs.toolOrder */ },
 * )
 * ```
 * 若同时传了 [userDataRepository] 与 [onToolOrderChange]，屏幕会先写 userdata 再回调；
 * 只传回调时由 Lead 用 [ToolOrderPrefs.apply] 写（见 docs/api/NOTICE_DRAG_TT_IMPL.md）。
 *
 * @param initialOrder 预览/测试注入顺序（非空时不读 userdata）
 * @param skipRestore 预览/测试：跳过 userdata 读取
 * @param previewDraggingToolId 预览注入：渲染「拖动中」态（ghost + 半透明卡片）
 * @param previewDragOffset 预览注入：ghost 中心在容器内的位置（**dp**，与屏幕密度无关）
 */
@Composable
fun AllToolsScreen(
    onOpenTool: (AppTool) -> Unit,
    modifier: Modifier = Modifier,
    userDataRepository: UserDataRepository? = null,
    onToolOrderChange: ((List<String>) -> Unit)? = null,
    initialOrder: List<String> = emptyList(),
    skipRestore: Boolean = false,
    previewDraggingToolId: String? = null,
    previewDragOffset: Offset = Offset(190f, 340f),
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val reduceMotion = rememberReduceMotion()
    val naturalIds = remember { ToolCatalog.tools.map { it.id } }
    val spec = rememberCtAdaptiveSpec()
    val columns = spec.gridColumns

    var order by remember { mutableStateOf(ToolOrderLogic.normalize(initialOrder, naturalIds)) }
    var restored by remember { mutableStateOf(skipRestore || initialOrder.isNotEmpty()) }
    // 本次会话内用户是否已经手动改过顺序（拖动 / 读屏上移下移）——晚到的本机恢复不得覆盖。
    var userReordered by remember { mutableStateOf(false) }

    // 1) 恢复 prefs.toolOrder（只读；没有基线就显示自然顺序，绝不盲写）。
    //    与 ClassToolsRoot 的 hydrateFromLocal 都是启动首帧 LaunchedEffect，本页可能先读到空
    //    sections；此时等首个非空快照（最多 STARTUP_RESTORE_WAIT_MS），否则「松手保存」的顺序
    //    在重启后会退回自然序（task-58 实测发现）。
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

    // 2) 拖动状态
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

    // 长按阈值：Compose 默认 500ms，与微信小程序不一致（mp = 350ms）→ 局部覆盖 LocalViewConfiguration，
    // 只作用于本页子树，不改变其它页面的手势含义（滚动/点击不读取 longPressTimeout）。
    val baseViewConfiguration = LocalViewConfiguration.current
    val dragViewConfiguration = remember(baseViewConfiguration) {
        object : ViewConfiguration by baseViewConfiguration {
            override val longPressTimeoutMillis: Long = AllToolsDragGeometry.LONG_PRESS_TIMEOUT_MS
        }
    }

    // v7：卡高 96dp（副标最多 2 行）；2 列 48.6% 宽。拖动几何/幽灵高度/自动滚动共用同一 itemHeight 常量。
    val itemHeightDp = 96.dp
    val spacingDp = 10.dp
    val itemHeightPx = with(density) { itemHeightDp.toPx() }
    val spacingPx = with(density) { spacingDp.toPx() }
    val autoScrollTopEdgePx = with(density) { AllToolsDragGeometry.AUTO_SCROLL_TOP_EDGE_DP.dp.toPx() }
    val autoScrollBottomInsetPx = with(density) { AllToolsDragGeometry.AUTO_SCROLL_BOTTOM_INSET_DP.dp.toPx() }
    val autoScrollStepPx = with(density) { AllToolsDragGeometry.AUTO_SCROLL_STEP_DP.dp.toPx() }

    val effectiveDraggingId = draggingId ?: previewDraggingToolId
    // previewDragOffset 以 dp 计（与预览/设备密度无关），渲染「拖动中」态时 ghost 位置一致。
    val previewGhostPointer = with(density) {
        Offset(previewDragOffset.x.dp.toPx(), previewDragOffset.y.dp.toPx())
    }
    val effectiveGhostPointer = if (draggingId != null) dragPointer else previewGhostPointer

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
            // 被动取消（来电/系统打断）后同样抑制一次点击，避免「取消拖动反而打开工具」。
            lastDragEndAt = System.currentTimeMillis()
        }
    }

    /** TalkBack / 无障碍「上移 / 下移」：不依赖手势，走与拖动相同的排序 + 持久化路径。 */
    fun moveCardBy(id: String, delta: Int) {
        val newOrder = ToolOrderLogic.moveBy(order, id, delta)
        if (newOrder == order) return
        order = newOrder
        userReordered = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        persistOrder(newOrder)
    }

    /**
     * 用「当前视口指针」重算落位并实时让位。
     *
     * 视口 → 网格内容坐标只减实时 `gridLeft/gridTop`（**不再 +scroll**：gridTop 已含滚动偏移，
     * 旧实现会重复计算导致页面滚动后跨行落位错一行）。
     */
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

    // 边缘自动滚动：拖动中每 80ms 连续滚一小步（不是只在 MOVE 事件里滚一次）；
    // 每帧先按最新 gridTop（滚动后 onGloballyPositioned 已更新）重算落位，再决定是否继续滚。
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

    val toolsInOrder = remember(order) {
        order.mapNotNull { id -> ToolCatalog.byId(id) }
    }

    // 长按 350ms 的局部 ViewConfiguration（只包本页子树，影响 pointerInput 的长按判定）。
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
                        .widthIn(max = spec.contentMaxWidth)
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .verticalScroll(scrollState),
                ) {
                    AllToolsHeader(
                        modifier = Modifier.padding(
                            start = spec.horizontalPadding,
                            end = spec.horizontalPadding,
                            top = 8.dp,
                            bottom = 2.dp,
                        ),
                        toolCount = toolsInOrder.size,
                        dragging = effectiveDraggingId != null,
                    )

                    // 2/3/4 列网格：自定义 Layout 让卡片全量布局；每张卡 key(tool.id) 保持拖动重排时
                    // 节点 identity 稳定 —— 否则顺序一变，pointerInput(tool.id) 会在重排瞬间重建，
                    // 正在进行的拖动被取消（task-58 根因之一：第一次跨列就弹回）。
                    Layout(
                        content = {
                            toolsInOrder.forEachIndexed { displayIndex, tool ->
                                key(tool.id) {
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
                                                        // 实时 gridLeft/gridTop 是视口坐标（已含滚动偏移），直接相加；
                                                        // 不能像旧实现那样再减 scroll（会把滚动量算两次）。
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
                                                // 拖动中 / 刚松手（350ms 内，对齐 mp）不触发打开
                                                if (draggingId == null &&
                                                    now - lastDragEndAt > AllToolsDragGeometry.TAP_SUPPRESSION_MS
                                                ) {
                                                    onOpenTool(tool)
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
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spec.horizontalPadding, vertical = 8.dp)
                            .onGloballyPositioned { coordinates ->
                                // 实时视口坐标：页面滚动后这里会重新上报（滚动偏移已包含在 positionInRoot 里）。
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

                    // 底部留白：让最后一行卡片能滚到悬浮 dock 上方（不是被 dock 挡住）。
                    Spacer(modifier = Modifier.height(124.dp))
                }

                // 跟手 ghost（拖动中 / 预览注入）：以指针为中心，尺寸 = 与网格相同的 itemWidth/itemHeight。
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
            }
        }
    }
}

/**
 * v5 页头：渐变（品牌雾青→灰蓝）+ 工具数量 + 拖动提示。
 *
 * task-58：拖动提示从「额外一行」改为**同一胶囊文案替换**（「17 个」⇄「松手保存」），
 * 页头高度在拖动开始/结束时保持不变，避免把网格整体下推导致落位几何在拖动首帧跳变。
 *
 * 配色走主题共享 hero 口径：浅色保持原品牌渐变 + 白字；深色改中性 tonal 深色
 * （surfaceContainerHigh→surfaceContainer）+ onSurface 文字，品牌绿只做小面积点缀。
 */
@Composable
private fun AllToolsHeader(
    toolCount: Int,
    dragging: Boolean,
    modifier: Modifier = Modifier,
) {
    val hero = rememberCtHeroColors()
    val content = hero.content
    val accent = hero.accent

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        contentColor = content,
        border = BorderStroke(1.dp, hero.border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(colors = listOf(hero.start, hero.end))),
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CtBrandDots(dotSize = 6.dp, gap = 4.dp)
                    Spacer(modifier = Modifier.width(9.dp))
                    Text(
                        text = "全部工具",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.18f),
                        contentColor = accent,
                    ) {
                        Text(
                            // 拖动中同一胶囊换成「松手保存」：页头高度不变（几何不跳）。
                            text = if (dragging) "松手保存" else "$toolCount 个",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
