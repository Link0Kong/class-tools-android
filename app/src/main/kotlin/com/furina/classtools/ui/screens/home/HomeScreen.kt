package com.furina.classtools.ui.screens.home

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.homework.HomeworkLoadResult
import com.furina.classtools.data.homework.HomeworkRepository
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.marks.MarkKind
import com.furina.classtools.data.marks.MarksRepository
import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.timetable.TtCalendar
import com.furina.classtools.data.timetable.TtLoadResult
import com.furina.classtools.data.timetable.TtPlan
import com.furina.classtools.data.timetable.TimetableRepository
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.data.tool.ToolCatalog
import com.furina.classtools.features.home.HomeToolsEditor
import com.furina.classtools.ui.components.BrandBar
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.SectionHeader
import com.furina.classtools.ui.components.ToolCard
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtTokens
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 工作台：品牌条 + 问候 + 常用工具（可编辑）+ 今日概要。
 * 对齐站点「身份后即点即用」：核验后直接看到工具，无欢迎弹窗。
 *
 * 常用工具编辑：点击品牌条右侧「编辑」进入，勾选 ≤4 个工具，完成后写 userdata v6 `homeTools`（随账号同步）。
 */
@Composable
fun HomeScreen(
    session: Session,
    onOpenTool: (AppTool) -> Unit,
    onHomeToolsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    quickToolIds: List<String> = ToolCatalog.defaultQuickIds,
    homeworkRepository: HomeworkRepository? = null,
    timetableRepository: TimetableRepository? = null,
    marksRepository: MarksRepository? = null,
) {
    val context = LocalContext.current
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf(quickToolIds) }

    // 今日概要：下一节课 + 待完成作业（首页首屏展示，进入对应工具后再看详情）。
    var ttPlan by remember { mutableStateOf<TtPlan?>(null) }
    var homeworkRows by remember { mutableStateOf<List<HomeworkRow>>(emptyList()) }
    val marksFlow = remember(marksRepository) {
        marksRepository?.marks ?: MutableStateFlow(MarksSnapshot())
    }
    val marksSnapshot by marksFlow.collectAsState()

    LaunchedEffect(timetableRepository) {
        val repo = timetableRepository ?: return@LaunchedEffect
        runCatching { repo.cached() }.getOrNull()?.let { snapshot ->
            ttPlan = runCatching { TtCalendar.plan(snapshot, System.currentTimeMillis()) }.getOrNull()
        }
        val result = runCatching { repo.load(force = false) }.getOrNull()
        if (result is TtLoadResult.Success) {
            ttPlan = runCatching {
                TtCalendar.plan(result.snapshot, System.currentTimeMillis())
            }.getOrNull()
        }
    }

    LaunchedEffect(homeworkRepository) {
        val repo = homeworkRepository ?: return@LaunchedEffect
        runCatching { repo.cached() }.getOrNull()?.let { homeworkRows = it }
        val result = runCatching { repo.load(force = false) }.getOrNull()
        if (result is HomeworkLoadResult.Success) homeworkRows = result.rows
    }

    val pendingHomework = homeworkRows.count { row ->
        !marksSnapshot.isDone(MarkKind.HW, row.id)
    }
    val nextClassText = ttPlan?.next?.let { next ->
        "${next.dayText} ${next.startText} ${next.name}"
    } ?: "暂无课程"
    val homeworkText = if (homeworkRows.isEmpty()) {
        "暂无作业"
    } else {
        "待完成 $pendingHomework / ${homeworkRows.size}"
    }

    // 云端/父级同步来的常用工具变化时，非编辑态自动跟随。
    LaunchedEffect(quickToolIds, editing) {
        if (!editing) draft = quickToolIds
    }

    val quickTools = ToolCatalog.quickTools(quickToolIds)
    val editableTools = ToolCatalog.tools.filter { it.id != "settings" }

    fun toggleTool(id: String) {
        draft = if (id in draft) {
            draft - id
        } else if (draft.size >= HomeToolsEditor.MAX_QUICK_TOOLS) {
            Toast.makeText(
                context,
                "最多选 ${HomeToolsEditor.MAX_QUICK_TOOLS} 个常用工具",
                Toast.LENGTH_SHORT,
            ).show()
            draft
        } else {
            draft + id
        }
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = CtTokens.Spacing.pageHorizontalCompact,
                end = CtTokens.Spacing.pageHorizontalCompact,
                top = 4.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(CtTokens.Spacing.cardGapCompact),
            horizontalArrangement = Arrangement.spacedBy(CtTokens.Spacing.cardGapCompact),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrandBar(
                    actions = {
                        if (editing) {
                            TextButton(
                                onClick = {
                                    onHomeToolsChange(draft.take(HomeToolsEditor.MAX_QUICK_TOOLS))
                                    editing = false
                                },
                            ) { Text("完成") }
                        } else {
                            TextButton(onClick = {
                                draft = quickToolIds
                                editing = true
                            }) { Text("编辑") }
                        }
                    },
                )
            }

            if (editing) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)) {
                        Text(
                            text = "选择首页快捷",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "点工具加入或移出 · 最多 ${HomeToolsEditor.MAX_QUICK_TOOLS} 个",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        CtPill(text = "已选 ${draft.size} / ${HomeToolsEditor.MAX_QUICK_TOOLS}")
                    }
                }
                items(editableTools, key = { it.id }, contentType = { "edit" }) { tool ->
                    SelectableToolCard(
                        tool = tool,
                        selected = tool.id in draft,
                        onClick = { toggleTool(tool.id) },
                        modifier = Modifier.height(142.dp),
                    )
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    androidx.compose.foundation.layout.Column(modifier = Modifier.padding(top = 18.dp)) {
                        Text(
                            text = "你好，${session.name.ifBlank { "同学" }}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CtPill(text = session.role.ifBlank { "信管 · 班级成员" })
                            CtPill(text = "Android 17")
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(title = "常用工具", modifier = Modifier.padding(top = 14.dp))
                }

                items(quickTools, key = { it.id }, contentType = { "quick" }) { tool ->
                    ToolCard(
                        tool = tool,
                        onClick = { onOpenTool(tool) },
                        modifier = Modifier.height(142.dp),
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(title = "今日", modifier = Modifier.padding(top = 14.dp))
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CtTokens.Spacing.cardGapCompact),
                    ) {
                        TodayTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.CalendarMonth,
                            label = "下一节课",
                            value = nextClassText,
                            onClick = { ToolCatalog.byId("tt")?.let(onOpenTool) },
                        )
                        TodayTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.AutoMirrored.Rounded.Assignment,
                            label = "待办",
                            value = homeworkText,
                            onClick = { ToolCatalog.byId("hw")?.let(onOpenTool) },
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "数据与网页 / 小程序互通 · 身份已核验",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableToolCard(
    tool: AppTool,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CtShapeTokens.toolCard,
            ),
    ) {
        ToolCard(tool = tool, onClick = onClick)
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "已选",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(22.dp),
            )
        }
    }
}

@Composable
private fun TodayTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    // 大字体（fontScale 1.3）下允许两行，避免「下一节课/待办」被截断
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

