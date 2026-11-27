package com.furina.classtools.ui.screens.home

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.data.tool.ToolCatalog
import com.furina.classtools.features.home.HomeToolsEditor
import com.furina.classtools.ui.components.glass.CtGlassCard
import com.furina.classtools.ui.components.reorderableSemantics
import kotlin.math.roundToInt

/** Remains mounted when a direct long press enters editing so the same pointer can finish dragging. */
@Composable
internal fun HomeQuickToolsRow(
    editor: HomeToolsEditSession,
    page: Int,
    baseline: List<String>,
    isPageActive: Boolean,
    reduceMotion: Boolean,
    onOpenTool: (AppTool) -> Unit,
) {
    val activeDraft = editor.draft?.takeIf { it.page == page }
    val ids = activeDraft?.ids ?: HomeToolsEditor.availableIds(baseline)
    var drag by remember { mutableStateOf<QuickToolDrag?>(null) }
    val visible = drag?.order ?: ids
    if (visible.isEmpty()) return
    val currentBaseline by rememberUpdatedState(baseline)
    val editing = activeDraft != null
    val canDrag = isPageActive && editor.canEdit && (editor.draft == null || editing)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { drag = null }

    fun moveBy(id: String, delta: Int) {
        if (!canDrag) return
        editor.begin(page, baseline)
        val draftIds = editor.draft?.ids ?: return
        val start = draftIds.indexOf(id)
        val target = (start + delta).coerceIn(0, draftIds.lastIndex)
        editor.reorder(page, draftIds.move(id, start, target))
    }

    CtGlassCard(shape = HomeCardShape) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
            val slots = HomeToolsEditor.MAX_QUICK_TOOLS
            val spacing = 2.dp
            val slotSize = with(density) { ((maxWidth + spacing) / slots).toPx() }.coerceAtLeast(1f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                visible.forEachIndexed { index, id ->
                    val tool = ToolCatalog.byId(id) ?: return@forEachIndexed
                    key(id) {
                        QuickToolTile(
                            tool = tool,
                            onClick = {
                                if (drag == null) {
                                    if (editing) editor.toggle(page, id) else onOpenTool(tool)
                                }
                            },
                            modifier = Modifier.weight(1f)
                                .alpha(if (drag?.id == id) 0.4f else 1f)
                                .reorderableSemantics(
                                    index = index,
                                    total = visible.size,
                                    itemLabel = tool.name,
                                    dragging = drag?.id == id,
                                    onMoveUp = { moveBy(id, -1) },
                                    onMoveDown = { moveBy(id, 1) },
                                )
                                .pointerInput(editor, id, canDrag, slotSize, direction) {
                                    if (!canDrag) return@pointerInput
                                    try {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                if (drag == null) {
                                                    editor.begin(page, currentBaseline)
                                                    val startIds = editor.draft?.ids.orEmpty()
                                                    if (id in startIds) {
                                                        drag = QuickToolDrag(id, startIds)
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                }
                                            },
                                            onDrag = { change, amount ->
                                                drag?.takeIf { it.id == id }?.let {
                                                    change.consume()
                                                    drag = it.copy(distance = it.distance + direction * amount.x / slotSize)
                                                }
                                            },
                                            onDragEnd = {
                                                drag?.takeIf { it.id == id }?.let {
                                                    editor.reorder(page, it.order)
                                                    drag = null
                                                }
                                            },
                                            onDragCancel = { if (drag?.id == id) drag = null },
                                        )
                                    } finally {
                                        if (drag?.id == id) drag = null
                                    }
                                },
                            reduceMotion = reduceMotion,
                        )
                    }
                }
                repeat((slots - visible.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private data class QuickToolDrag(val id: String, val start: List<String>, val distance: Float = 0f) {
    val order: List<String> get() {
        val source = start.indexOf(id)
        val target = (source + distance.roundToInt()).coerceIn(0, start.lastIndex)
        return start.move(id, source, target)
    }
}

private fun List<String>.move(id: String, from: Int, to: Int): List<String> =
    if (from < 0 || from == to) this else toMutableList().apply {
        removeAt(from)
        add(to, id)
    }
