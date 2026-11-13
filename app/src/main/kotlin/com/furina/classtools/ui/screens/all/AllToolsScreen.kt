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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import com.furina.classtools.ui.theme.rememberReduceMotion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 全部工具 v5：渐变页头 + 2 列分类色卡片 + 长按拖动排序。
 *
 * 交互（与 `mp-class-tools/pages/all/all.js` 拖动 v2 同口径，**逻辑保持 v4 完全不变**）：
 * - 长按约 350ms 进入拖动（触感反馈）；ghost 跟手，网格按「行 + x 中线」计算插入位置；
 * - 拖动中实时让位（被拖卡片半透明、ghost 覆盖）；靠近上下边缘自动滚动；
 * - 松手落位：**只有顺序真的变化时才**回调 [onToolOrderChange] 并（传入仓库时）写
 *   `userdata v6 prefs.toolOrder`（`at.prefs` 提升，等价 mp `toolOrderSetAt` 语义）；默认自然顺序不写；
 * - 拖动结束后 250ms 内的点击被抑制（拖动不触发打开工具）。
 *
 * 视觉（v5，只借鉴 HFUT-Schedule / xiaoying 设计语言，不复制代码/素材）：
 * - 页头渐变（品牌雾青→灰蓝）+ 工具数量 + 拖动提示（拖动中变成「松手保存顺序」）；
 * - 卡片 2/3/4 列随窗口（手机 2 列），分类色点缀 + 图标气泡 + 主/副标题，按压缩放；
 * - 网格仍用自定义 [Layout] 保证拖动重排时卡片节点 identity 稳定（13 张卡片全量布局，
 *   与旧实现相同，不引入 LazyGrid 让拖动状态被回收的性能/行为回退）。
 *
 * 「设置」卡片仍通过 [onOpenTool] 交给宿主（MainNavHost 把它导航到「我的」Tab），本页不改导航逻辑。
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

    // 1) 恢复 prefs.toolOrder（只读；没有基线就显示自然顺序，绝不盲写）
    LaunchedEffect(Unit) {
        if (restored) return@LaunchedEffect
        runCatching {
            userDataRepository?.currentSections?.first()?.let { sections ->
                order = ToolOrderLogic.normalize(ToolOrderPrefs.read(sections), naturalIds)
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

    // v5 卡片更高（图标气泡 + 分类标签 + 两行文字）；拖动几何随 itemHeight 参数自动一致。
    val itemHeightDp = 148.dp
    val spacingDp = 12.dp
    val itemHeightPx = with(density) { itemHeightDp.toPx() }
    val spacingPx = with(density) { spacingDp.toPx() }

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
        }
    }

    /** TalkBack / 无障碍「上移 / 下移」：不依赖手势，走与拖动相同的排序 + 持久化路径。 */
    fun moveCardBy(id: String, delta: Int) {
        val newOrder = ToolOrderLogic.moveBy(order, id, delta)
        if (newOrder == order) return
        order = newOrder
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        persistOrder(newOrder)
    }

    fun onDragDelta(amount: Offset) {
        dragPointer += amount
        // 靠近上下边缘自动滚动（ghost 跟手，内容在下方滚动）
        val topEdge = with(density) { 96.dp.toPx() }
        val bottomEdge = containerHeightPx - with(density) { 128.dp.toPx() }
        when {
            dragPointer.y < topEdge && scrollState.value > 0 ->
                scrollState.dispatchRawDelta(-18f)
            bottomEdge > topEdge && dragPointer.y > bottomEdge ->
                scrollState.dispatchRawDelta(18f)
        }
        val draggedIndex = order.indexOf(draggingId ?: return)
        if (draggedIndex < 0 || itemWidthPx <= 0f) return
        val contentX = dragPointer.x - gridLeftPx
        val contentY = dragPointer.y - gridTopPx + scrollState.value
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
            order = ToolOrderLogic.moveToIndex(order, draggingId ?: return, target)
        }
    }

    val toolsInOrder = remember(order) {
        order.mapNotNull { id -> ToolCatalog.byId(id) }
    }

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

                // 2 列网格：custom Layout 保证拖动重排时卡片节点 identity 稳定（不会中断手势）
                Layout(
                    content = {
                        toolsInOrder.forEachIndexed { displayIndex, tool ->
                            Box(
                                modifier = Modifier
                                    .alpha(if (tool.id == effectiveDraggingId) 0.22f else 1f)
                                    .pointerInput(tool.id, columns) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { local ->
                                                if (draggingId != null) return@detectDragGesturesAfterLongPress
                                                val index = order.indexOf(tool.id)
                                                if (index < 0) return@detectDragGesturesAfterLongPress
                                                draggingId = tool.id
                                                dragStartOrder = order
                                                val contentY = (index / columns) * (itemHeightPx + spacingPx) + local.y
                                                val contentX = (index % columns) * (itemWidthPx + spacingPx) + local.x
                                                dragPointer = Offset(
                                                    gridLeftPx + contentX,
                                                    gridTopPx + contentY - scrollState.value,
                                                )
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
                                        // 拖动中 / 刚松手 250ms 内不触发打开
                                        if (draggingId == null && now - lastDragEndAt > 250) {
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
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spec.horizontalPadding, vertical = 8.dp)
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

                Text(
                    text = "全部工具均为原生页面 · 数据随账号同步",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = spec.horizontalPadding, end = spec.horizontalPadding, top = 16.dp, bottom = 124.dp),
                )
            }

            // 跟手 ghost（拖动中 / 预览注入）
            val ghostTool = effectiveDraggingId?.let { ToolCatalog.byId(it) }
            if (ghostTool != null && itemWidthPx > 0f) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (effectiveGhostPointer.x - itemWidthPx / 2f).roundToInt(),
                                y = (effectiveGhostPointer.y - itemHeightPx / 2f).roundToInt(),
                            )
                        }
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

/**
 * v5 页头：渐变（品牌雾青→灰蓝）+ 工具数量 + 拖动提示。
 * 深色模式用 primaryContainer/secondaryContainer + 浅色文字，保持可读与品牌识别。
 */
@Composable
private fun AllToolsHeader(
    toolCount: Int,
    dragging: Boolean,
    modifier: Modifier = Modifier,
) {
    val dark = LocalCtDarkTheme.current
    val start = if (dark) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
    val end = if (dark) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.secondary
    val content = if (dark) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        contentColor = content,
        border = BorderStroke(1.dp, content.copy(alpha = 0.14f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(colors = listOf(start, end))),
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
                        color = content.copy(alpha = 0.18f),
                        contentColor = content,
                    ) {
                        Text(
                            text = "$toolCount 个",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                if (dragging) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "松手保存",
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.84f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
