package com.furina.classtools.ui.screens.feedback

import com.furina.classtools.ui.components.light.ctLightMaterial
import com.furina.classtools.ui.theme.CtLightScene
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.session.Session
import com.furina.classtools.features.feedback.DefaultFeedbackRepository
import com.furina.classtools.features.feedback.FeedbackDoneResult
import com.furina.classtools.features.feedback.FeedbackFilter
import com.furina.classtools.features.feedback.FeedbackLoadResult
import com.furina.classtools.features.feedback.FeedbackRepository
import com.furina.classtools.features.feedback.FeedbackRow
import com.furina.classtools.features.feedback.FeedbackRules
import com.furina.classtools.features.feedback.FeedbackSubmitResult
import com.furina.classtools.ui.components.CtLoading
import com.furina.classtools.ui.components.ctTopBarRaw
import com.furina.classtools.ui.components.EmptyState
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.expressive.CtChipOption
import com.furina.classtools.ui.components.expressive.CtFilterChipRow
import com.furina.classtools.ui.theme.CtShapeTokens
import kotlinx.coroutines.launch

/**
 * 反馈建议页（对齐小程序 `pages/fb`）。
 *
 * 云端契约：`docs/api/ENDPOINTS.md` E19/E20/E21（feedback 表）：
 * - 正文最多 500 字；**不收集联系方式**（contact 恒空串，页面不展示）；
 * - 普通用户只看「我的反馈」（RLS 已限制）；管理员可看 全部/未处理/已处理，并「标已处理」；
 * - 四态：加载 / 错误（重试）/ 空（还没有反馈）/ 数据；提交有提交中/成功/失败反馈；
 * - 隐私：页面不显示学号；管理员列表作者名由 Lead 注入 `nameOfSid`（用班级名单缓存），查不到回退「（未知成员）」。
 *
 * 接线（Lead）：
 * ```kotlin
 * "feedback" -> FeedbackScreen(
 *     onBack = { navController.popBackStack() },
 *     session = session,
 *     repository = container.feedbackRepository,   // DefaultFeedbackRepository(apiClient, currentSid)
 *     nameOfSid = { sid -> sessionMemorizedMembers.firstOrNull { it.sid == sid }?.name },
 * )
 * ```
 *
 * @param initialRows 预览注入：初始列表
 * @param forcedErrorMessage 预览注入：错误态
 * @param skipLoad 预览/测试：跳过加载
 */
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    session: Session?,
    repository: FeedbackRepository?,
    modifier: Modifier = Modifier,
    skipLoad: Boolean = false,
    initialRows: List<FeedbackRow> = emptyList(),
    forcedErrorMessage: String? = null,
    nameOfSid: (String) -> String? = { null },
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val isAdmin = session?.isAdmin == true
    val sid = session?.sid

    var rows by remember { mutableStateOf(initialRows) }
    var phase by remember {
        mutableStateOf(
            when {
                forcedErrorMessage != null -> FeedbackPhase.FAILED
                skipLoad -> FeedbackPhase.READY
                else -> FeedbackPhase.LOADING
            },
        )
    }
    var errorMessage by remember { mutableStateOf(forcedErrorMessage) }
    var filter by remember { mutableStateOf(if (isAdmin) FeedbackFilter.ALL else FeedbackFilter.MINE) }
    var text by remember { mutableStateOf("") }
    var textError by remember { mutableStateOf<String?>(null) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableStateOf(0) }

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    LaunchedEffect(reloadToken) {
        if (skipLoad || forcedErrorMessage != null) {
            phase = FeedbackPhase.READY
            return@LaunchedEffect
        }
        if (session == null) {
            errorMessage = "请先完成身份验证后再反馈"
            phase = FeedbackPhase.FAILED
            return@LaunchedEffect
        }
        val repo = repository
        if (repo == null) {
            phase = FeedbackPhase.READY
            return@LaunchedEffect
        }
        if (rows.isEmpty()) phase = FeedbackPhase.LOADING
        when (val result = repo.load(force = reloadToken > 0)) {
            is FeedbackLoadResult.Success -> {
                rows = result.rows
                errorMessage = null
                phase = FeedbackPhase.READY
            }

            is FeedbackLoadResult.Failure -> {
                errorMessage = result.message
                phase = if (rows.isEmpty()) FeedbackPhase.FAILED else FeedbackPhase.READY
            }
        }
    }

    fun submit() {
        val repo = repository
        if (repo == null) {
            submitError = "功能尚未接线"
            return
        }
        FeedbackRules.validateContent(text)?.let {
            textError = it
            return
        }
        scope.launch {
            submitting = true
            submitError = null
            try {
                when (val result = repo.submit(text)) {
                    is FeedbackSubmitResult.Success -> {
                        text = ""
                        textError = null
                        rows = listOf(result.row) + rows.filterNot { it.id == result.row.id }
                        if (!isAdmin) filter = FeedbackFilter.MINE
                        notify("反馈已提交，谢谢！")
                    }

                    is FeedbackSubmitResult.Failure -> {
                        submitError = result.message
                        notify(result.message)
                    }
                }
            } finally {
                submitting = false
            }
        }
    }

    fun markDone(row: FeedbackRow) {
        val repo = repository ?: return
        scope.launch {
            val result = repo.markDone(row.id)
            when (result) {
                FeedbackDoneResult.Success -> {
                    rows = rows.map { if (it.id == row.id) it.copy(done = true) else it }
                    notify("已标记处理")
                }

                is FeedbackDoneResult.Failure -> notify(result.message)
            }
        }
    }

    val filtered = FeedbackRules.filterRows(rows, sid, filter, isAdmin)

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                ctTopBarRaw(
                    subtitle = FeedbackRules.subtitle(rows.size, isAdmin),
                    title = "反馈建议",
                    onBack = onBack,
                )

                Box(modifier = Modifier.weight(1f)) {
                    when (phase) {
                        FeedbackPhase.LOADING -> CtLoading(
                            modifier = Modifier.align(Alignment.Center),
                            text = "加载反馈…",
                        )

                        FeedbackPhase.FAILED -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            EmptyState(
                                icon = Icons.Rounded.ErrorOutline,
                                title = "读取反馈失败",
                                message = errorMessage ?: "请稍后重试",
                            )
                            TextButton(onClick = { reloadToken += 1 }) { Text("重试") }
                        }

                        FeedbackPhase.READY -> FeedbackContent(
                            rows = filtered,
                            totalRows = rows.size,
                            isAdmin = isAdmin,
                            text = text,
                            textError = textError,
                            submitError = submitError,
                            submitting = submitting,
                            filter = filter,
                            session = session,
                            nameOfSid = nameOfSid,
                            onTextChange = {
                                text = it.take(FeedbackRules.MAX_CONTENT_LENGTH)
                                textError = null
                                submitError = null
                            },
                            onFilterChange = { filter = it },
                            onSubmit = { submit() },
                            onMarkDone = { markDone(it) },
                        )
                    }
                }
            }

            androidx.compose.foundation.layout.Box(
            modifier = Modifier.ctLightMaterial(
                CtLightScene.SNACKBAR,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                bodySafe = true,
            ),
        ) { SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            )
            }
        }
    }
}

@Composable
private fun FeedbackContent(
    rows: List<FeedbackRow>,
    totalRows: Int,
    isAdmin: Boolean,
    text: String,
    textError: String?,
    submitError: String?,
    submitting: Boolean,
    filter: FeedbackFilter,
    session: Session?,
    nameOfSid: (String) -> String?,
    onTextChange: (String) -> Unit,
    onFilterChange: (FeedbackFilter) -> Unit,
    onSubmit: () -> Unit,
    onMarkDone: (FeedbackRow) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                ) {
                    FormCard(
                        text = text,
                        textError = textError,
                        submitError = submitError,
                        submitting = submitting,
                        isAdmin = isAdmin,
                        onTextChange = onTextChange,
                        onSubmit = onSubmit,
                    )
                }
                Column(modifier = Modifier.weight(0.58f).fillMaxSize()) {
                    FeedbackList(
                        rows = rows,
                        totalRows = totalRows,
                        isAdmin = isAdmin,
                        filter = filter,
                        session = session,
                        nameOfSid = nameOfSid,
                        onFilterChange = onFilterChange,
                        onMarkDone = onMarkDone,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "form") {
                    FormCard(
                        text = text,
                        textError = textError,
                        submitError = submitError,
                        submitting = submitting,
                        isAdmin = isAdmin,
                        onTextChange = onTextChange,
                        onSubmit = onSubmit,
                    )
                }
                if (isAdmin) {
                    item(key = "filters") {
                        FilterRow(
                            isAdmin = true,
                            filter = filter,
                            totalRows = totalRows,
                            onFilterChange = onFilterChange,
                        )
                    }
                }
                if (rows.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Rounded.ChatBubbleOutline,
                            title = if (isAdmin) "还没有反馈" else "还没有反馈",
                            message = if (isAdmin) "全部用户反馈会显示在这里" else "你的反馈会显示在这里",
                        )
                    }
                } else {
                    items(rows, key = { it.id }) { row ->
                        FeedbackCard(
                            row = row,
                            author = FeedbackRules.displayAuthor(row, session?.sid, isAdmin, nameOfSid),
                            showDoneAction = isAdmin && !row.done,
                            onMarkDone = { onMarkDone(row) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormCard(
    text: String,
    textError: String?,
    submitError: String?,
    submitting: Boolean,
    isAdmin: Boolean,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "反馈内容",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                enabled = !submitting,
                isError = textError != null,
                placeholder = { Text("建议或问题，写在这里") },
                supportingText = {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = textError ?: "最多 ${FeedbackRules.MAX_CONTENT_LENGTH} 字",
                            color = if (textError != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${text.length}/${FeedbackRules.MAX_CONTENT_LENGTH}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
            if (submitError != null) {
                Text(
                    text = submitError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onSubmit,
                enabled = !submitting && text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = CtShapeTokens.pill,
            ) {
                Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (submitting) "提交中…" else "提交反馈")
            }
            Text(
                text = if (isAdmin) {
                    "管理员反馈仅本班管理员可见；不收集联系方式。"
                } else {
                    "不收集联系方式；反馈仅班级管理员可见，用于改进班级工具。"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeedbackList(
    rows: List<FeedbackRow>,
    totalRows: Int,
    isAdmin: Boolean,
    filter: FeedbackFilter,
    session: Session?,
    nameOfSid: (String) -> String?,
    onFilterChange: (FeedbackFilter) -> Unit,
    onMarkDone: (FeedbackRow) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (isAdmin) {
            FilterRow(
                isAdmin = true,
                filter = filter,
                totalRows = totalRows,
                onFilterChange = onFilterChange,
            )
        }
        if (rows.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.ChatBubbleOutline,
                title = "还没有反馈",
                message = if (isAdmin) "全部用户反馈会显示在这里" else "你的反馈会显示在这里",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.id }) { row ->
                    FeedbackCard(
                        row = row,
                        author = FeedbackRules.displayAuthor(row, session?.sid, isAdmin, nameOfSid),
                        showDoneAction = isAdmin && !row.done,
                        onMarkDone = { onMarkDone(row) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    isAdmin: Boolean,
    filter: FeedbackFilter,
    totalRows: Int,
    onFilterChange: (FeedbackFilter) -> Unit,
) {
    if (!isAdmin) return
    CtFilterChipRow(
        options = listOf(
            CtChipOption(FeedbackFilter.ALL.name, "全部 $totalRows"),
            CtChipOption(FeedbackFilter.TODO.name, "未处理"),
            CtChipOption(FeedbackFilter.DONE.name, "已处理"),
        ),
        selectedKey = filter.name,
        onSelect = { key ->
            FeedbackFilter.entries.firstOrNull { it.name == key }?.let(onFilterChange)
        },
        contentPadding = PaddingValues(horizontal = 0.dp),
    )
}

@Composable
private fun FeedbackCard(
    row: FeedbackRow,
    author: String,
    showDoneAction: Boolean,
    onMarkDone: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp),
        shape = CtShapeTokens.listItem,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (row.done) {
                    Surface(shape = CtShapeTokens.pill, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            text = "已处理",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            Text(
                text = row.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = FeedbackRules.formatTime(row.ts),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (showDoneAction) {
                    TextButton(onClick = onMarkDone) { Text("标已处理") }
                }
            }
        }
    }
}

/** 页面内部阶段（四态中的加载/错误两态由它承载）。 */
private enum class FeedbackPhase { LOADING, READY, FAILED }

@Suppress("unused")
private val defaultRepositoryHint = DefaultFeedbackRepository::class.simpleName
