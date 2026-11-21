@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.furina.classtools.ui.screens.vote

import com.furina.classtools.ui.components.CtSnackbarHost

import com.furina.classtools.ui.components.light.ctLightMaterial
import com.furina.classtools.ui.theme.CtLightScene
import com.furina.classtools.ui.theme.CtShape
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.member.MemberCategory
import com.furina.classtools.data.member.MemberEntry
import com.furina.classtools.data.vote.Poll
import com.furina.classtools.data.vote.PollAudience
import com.furina.classtools.data.vote.PollDraft
import com.furina.classtools.data.vote.PollDetail
import com.furina.classtools.data.vote.PollManageAction
import com.furina.classtools.data.vote.PollManageResult
import com.furina.classtools.data.vote.PollMode
import com.furina.classtools.data.vote.PollOptionDraft
import com.furina.classtools.data.vote.PollResultVisibility
import com.furina.classtools.data.vote.PollSettings
import com.furina.classtools.data.vote.VoteError
import com.furina.classtools.data.vote.VoteField
import com.furina.classtools.data.vote.VoteLimits
import com.furina.classtools.data.vote.VoteLogic
import com.furina.classtools.data.vote.VoteRepository
import com.furina.classtools.data.vote.VoteResult
import com.furina.classtools.data.vote.VoteValidationError
import com.furina.classtools.ui.adaptive.rememberCtAdaptiveSpec
import com.furina.classtools.ui.components.CtLoading
import com.furina.classtools.ui.components.CtLightTopBar
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.tool.CtToolCard
import com.furina.classtools.ui.components.tool.CtToolChip
import com.furina.classtools.ui.components.tool.CtToolField
import com.furina.classtools.ui.components.tool.CtToolPrimaryButton
import com.furina.classtools.ui.components.tool.CtToolSegmented
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShapeTokens
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 截止时间类型。 */
private enum class DeadlineType { QUICK, AT, NONE }

/** 一个选项的编辑态。 */
private data class OptionForm(
    val id: String = "",
    val text: String = "",
    val desc: String = "",
    val img: String = "",
)

/** 发起/编辑表单状态（纯 UI 状态，可映射到 [PollDraft]）。 */
private data class VoteFormState(
    val title: String = "",
    val description: String = "",
    val deadlineType: DeadlineType = DeadlineType.QUICK,
    val quickMinutes: Int = VoteLimits.DEFAULT_QUICK_MINUTES,
    val deadlineAt: Long = 0L,
    val options: List<OptionForm> = listOf(OptionForm(), OptionForm()),
    val multi: Boolean = false,
    val maxChoices: Int = 2,
    val anonymous: Boolean = true,
    val allowChange: Boolean = true,
    val resultVisibility: PollResultVisibility = PollResultVisibility.AFTER_VOTE,
    val audience: PollAudience = PollAudience.ALL,
    val audienceSeats: Set<Int> = emptySet(),
) {
    fun toDraft(nowMillis: Long): PollDraft {
        val deadline = when (deadlineType) {
            DeadlineType.QUICK -> VoteLogic.quickDeadline(nowMillis, quickMinutes)
            DeadlineType.AT -> deadlineAt
            DeadlineType.NONE -> 0L
        }
        val settings = PollSettings(
            multi = multi,
            maxChoices = if (multi) maxChoices else 1,
            anonymous = anonymous,
            allowChange = allowChange,
            resultVisibility = resultVisibility,
        ).normalized(options.size)
        return PollDraft(
            title = title.trim(),
            description = description.trim(),
            mode = if (deadlineType == DeadlineType.QUICK) PollMode.QUICK else PollMode.LONG,
            options = options.map { option ->
                PollOptionDraft(
                    id = option.id,
                    text = option.text.trim(),
                    desc = option.desc.trim(),
                    img = option.img.trim(),
                )
            },
            settings = settings,
            deadline = deadline,
            audience = audience,
            audienceSeats = if (audience == PollAudience.CUSTOM) {
                audienceSeats.filter { it in 1..VoteLimits.MAX_SEAT }.distinct().sorted()
            } else {
                emptyList()
            },
        )
    }

    companion object {
        fun fromPoll(poll: Poll): VoteFormState = VoteFormState(
            title = poll.title,
            description = poll.description,
            deadlineType = if (poll.deadline > 0L) DeadlineType.AT else DeadlineType.NONE,
            deadlineAt = poll.deadline,
            options = poll.options
                .ifEmpty { emptyList() }
                .map { OptionForm(id = it.id, text = it.text, desc = it.desc, img = it.img) }
                .let { if (it.isEmpty()) listOf(OptionForm(), OptionForm()) else it },
            multi = poll.settings.multi,
            maxChoices = poll.settings.maxChoices.coerceAtLeast(2),
            anonymous = poll.settings.anonymous,
            allowChange = poll.settings.allowChange,
            resultVisibility = poll.settings.resultVisibility,
            audience = poll.audience,
            audienceSeats = poll.audienceSeats.toSet(),
        )
    }
}

/**
 * 发起 / 编辑投票页（v4）。
 *
 * - 标题 1–80、说明 ≤500、选项 2–20（文字 1–80 + 说明 ≤200 + 可选配图）；
 * - 单选/多选 + 最多可选、匿名、允许改票、结果可见性；
 * - 可见范围：全班 / 班委 / 团员 / 指定成员（成员选择器仅显示姓名/座号，无学号）；
 * - 时限：限时快捷 5/10/30/60/120 分钟、指定截止、不限时；
 * - 即时校验 [VoteLogic.validateDraft]；提交失败保留同一 prepared draft（幂等重试）。
 *
 * 接线（Lead）：
 * ```kotlin
 * composable(Routes.VOTE_CREATE + "?pollId={pollId}") { entry ->
 *     VoteCreateScreen(
 *         repository = container.voteRepository,
 *         editingPollId = entry.arguments?.getString("pollId"),
 *         memberSource = container.voteMemberSource,
 *         onBack = { navController.popBackStack() },
 *         onFinished = { _, _ -> navController.popBackStack() },
 *     )
 * }
 * ```
 *
 * @param editingPollId 非空 = 编辑已有投票（仅 0 票可编辑；route 参数）
 * @param memberSource 指定成员选择器数据源（null 时「指定成员」不可选）
 * @param initialDraft 预览：直接用表单初始值（不联网）
 * @param initialEditDetail 预览：编辑态详情（配合 editingPollId）
 * @param initialMemberPickerOpen 预览：直接展开成员选择器
 * @param skipLoad 预览：跳过编辑详情加载
 */
@Composable
fun VoteCreateScreen(
    repository: VoteRepository,
    modifier: Modifier = Modifier,
    editingPollId: String? = null,
    memberSource: VoteMemberSource? = null,
    onBack: () -> Unit = {},
    onFinished: (pollId: String, created: Boolean) -> Unit = { _, _ -> },
    nowMillis: Long = System.currentTimeMillis(),
    initialDraft: PollDraft? = null,
    initialEditDetail: PollDetail? = null,
    initialMemberPickerOpen: Boolean = false,
    skipLoad: Boolean = false,
) {
    val spec = rememberCtAdaptiveSpec()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val editing = !editingPollId.isNullOrBlank()
    var loading by remember { mutableStateOf(editing && initialEditDetail == null && !skipLoad) }
    var loadError by remember { mutableStateOf<VoteError?>(null) }
    var editDetail by remember(initialEditDetail) { mutableStateOf(initialEditDetail) }
    var editLoadToken by remember { mutableStateOf(0) }

    var form by remember(initialDraft) {
        mutableStateOf(
            initialDraft?.toFormState() ?: VoteFormState(),
        )
    }
    var preparedDraft by remember { mutableStateOf<PollDraft?>(null) }
    var errors by remember { mutableStateOf<List<VoteValidationError>>(emptyList()) }
    var submitting by remember { mutableStateOf(false) }
    var submittingImage by remember { mutableStateOf(false) }
    var showMemberPicker by remember { mutableStateOf(initialMemberPickerOpen) }
    var pendingImageIndex by remember { mutableStateOf<Int?>(null) }

    fun updateForm(transform: VoteFormState.() -> VoteFormState) {
        form = form.transform()
        preparedDraft = null
        if (errors.isNotEmpty()) {
            errors = VoteLogic.validateDraft(form.toDraft(System.currentTimeMillis()), System.currentTimeMillis())
        }
    }

    val inPreview = LocalInspectionMode.current
    val imagePicker = if (inPreview) {
        null
    } else {
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val index = pendingImageIndex
        pendingImageIndex = null
        if (uri == null || index == null) return@rememberLauncherForActivityResult
        scope.launch {
            submittingImage = true
            val dataUrl = withContext(Dispatchers.IO) {
                VoteImageCompressor.compressToDataUrl(context, uri)
            }
            submittingImage = false
            if (dataUrl == null) {
                snackbar.showSnackbar("图片处理失败，请换一张")
            } else {
                val currentTotal = form.options.sumOf { it.img.length }
                val replaced = form.options.getOrNull(index)?.img?.length ?: 0
                if (currentTotal - replaced + dataUrl.length > VoteLimits.MAX_TOTAL_IMAGE_CHARS) {
                    snackbar.showSnackbar("选项图片总大小超限")
                } else {
                    updateForm { copy(options = options.replaceAt(index) { it.copy(img = dataUrl) }) }
                }
            }
        }
        }
    }

    LaunchedEffect(editingPollId, editLoadToken, skipLoad) {
        val pollId = editingPollId ?: return@LaunchedEffect
        if (skipLoad) return@LaunchedEffect
        loading = true
        when (val result = repository.detail(pollId, force = editLoadToken > 0)) {
            is VoteResult.Success -> {
                editDetail = result.value
                form = VoteFormState.fromPoll(result.value.poll)
                loading = false
                if (result.value.total > 0) {
                    snackbar.showSnackbar("已有投票，不能修改内容")
                }
            }

            is VoteResult.Failure -> {
                loadError = result.error
                loading = false
            }
        }
    }

    fun submit() {
        if (submitting || submittingImage) return
        val now = System.currentTimeMillis()
        val draft = preparedDraft ?: VoteLogic.prepareDraft(form.toDraft(now), now).also { preparedDraft = it }
        val validation = VoteLogic.validateDraft(draft, now)
        errors = validation
        if (validation.isNotEmpty()) return
        scope.launch {
            submitting = true
            if (editing) {
                val pollId = editingPollId.orEmpty()
                when (val result = repository.manage(pollId, PollManageAction.Edit(draft))) {
                    is VoteResult.Success -> when (val value = result.value) {
                        is PollManageResult.Deleted -> onBack()
                        is PollManageResult.Updated -> onFinished(value.detail.poll.id, false)
                    }

                    is VoteResult.Failure -> {
                        snackbar.showSnackbar(result.error.message)
                        errors = listOf(VoteValidationError(VoteField.TITLE, "SUBMIT", result.error.message))
                    }
                }
            } else {
                when (val result = repository.create(draft)) {
                    is VoteResult.Success -> onFinished(result.value.poll.id, true)
                    is VoteResult.Failure -> {
                        snackbar.showSnackbar(result.error.message)
                        errors = listOf(VoteValidationError(VoteField.TITLE, "SUBMIT", result.error.message))
                    }
                }
            }
            submitting = false
        }
    }

    // 沉浸光感顶栏：列表滚动量驱动顶栏浮层渐显（首项滚出后直接满进度）。
    val ctTopBarLazyState = androidx.compose.foundation.lazy.rememberLazyListState()
    val ctTopBarScrollPx = if (ctTopBarLazyState.firstVisibleItemIndex > 0) {
        com.furina.classtools.ui.theme.CT_TOP_BAR_FULL_SCROLL_PX
    } else {
        ctTopBarLazyState.firstVisibleItemScrollOffset.toFloat()
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = spec.contentMaxWidth)
                    .fillMaxSize()
                    .padding(horizontal = spec.horizontalPadding),
            ) {
                // 沉浸光感顶栏：滚动量由表单 LazyColumn 上报。
                CtLightTopBar(
                    scrollPx = ctTopBarScrollPx,
                    title = if (editing) "编辑投票" else "发起投票",
                    subtitle = if (editing) "仅 0 票可编辑" else null,
                    onBack = onBack,
                )

                when {
                    loading -> CtLoading(text = "加载投票…", modifier = Modifier.padding(top = 40.dp))

                    loadError != null -> VoteErrorContent(
                        error = loadError!!,
                        onRetry = { editLoadToken += 1 },
                        modifier = Modifier.weight(1f),
                    )

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            state = ctTopBarLazyState,
                            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item(key = "basic") { VoteBasicSection(form, errors) { updateForm(it) } }
                            item(key = "deadline") { VoteDeadlineSection(form, errors, nowMillis) { updateForm(it) } }
                            item(key = "options") {
                                VoteOptionsSection(
                                    form = form,
                                    errors = errors,
                                    submittingImage = submittingImage,
                                    onPickImage = { index ->
                                        pendingImageIndex = index
                                        imagePicker?.launch("image/*")
                                    },
                                    onUpdate = { updateForm(it) },
                                )
                            }
                            item(key = "rules") { VoteRulesSection(form, errors) { updateForm(it) } }
                            item(key = "audience") {
                                VoteAudienceSection(
                                    form = form,
                                    errors = errors,
                                    memberSourceAvailable = memberSource != null,
                                    onOpenPicker = { showMemberPicker = true },
                                    onUpdate = { updateForm(it) },
                                )
                            }
                            if (errors.isNotEmpty()) {
                                item(key = "errors") {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(CtRadius.hero),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                text = "请先修改以下内容",
                                                style = MaterialTheme.typography.titleSmall,
                                            )
                                            errors.take(5).forEach { error ->
                                                Text(
                                                    text = "· ${error.message}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {

                                Button(
                                    onClick = { submit() },
                                    enabled = !submitting && !submittingImage,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp),
                                    shape = CtShapeTokens.pill,
                                ) {
                                    if (submitting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = when {
                                            submitting -> "提交中…"
                                            editing -> "保存修改"
                                            else -> "发起投票"
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            androidx.compose.foundation.layout.Box(
            modifier = Modifier.ctLightMaterial(
                CtLightScene.SNACKBAR,
                shape = CtShape.input,
                bodySafe = true,
            ),
        ) { CtSnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            )
            }
        }
    }

    if (showMemberPicker) {
        val source = memberSource
        if (source == null) {
            showMemberPicker = false
        } else {
            VoteMemberPickerSheet(
                source = source,
                initialSeats = form.audienceSeats,
                onDismiss = { showMemberPicker = false },
                onConfirm = { seats ->
                    showMemberPicker = false
                    updateForm {
                        copy(
                            audience = PollAudience.CUSTOM,
                            audienceSeats = seats,
                        )
                    }
                },
            )
        }
    }
}

// =============================================================================
// 表单区（各 section 独立 Composable，便于预览与维护）
// =============================================================================

@Composable
private fun VoteBasicSection(
    form: VoteFormState,
    errors: List<VoteValidationError>,
    onUpdate: (VoteFormState.() -> VoteFormState) -> Unit,
) {
    VoteSectionCard(title = "基本信息") {
        val titleError = voteErrorFor(errors, VoteField.TITLE)
        CtToolField(
            label = "标题",
            value = form.title,
            onValueChange = { value -> onUpdate { copy(title = value.take(VoteLimits.MAX_TITLE + 20)) } },
            placeholder = "例如：本周班会时间",
        )
        Text(
            text = "${form.title.length}/${VoteLimits.MAX_TITLE}" + (titleError?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = if (titleError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val descError = voteErrorFor(errors, VoteField.DESCRIPTION)
        CtToolField(
            label = "说明（可选）",
            value = form.description,
            onValueChange = { value -> onUpdate { copy(description = value.take(VoteLimits.MAX_DESCRIPTION + 100)) } },
            placeholder = "补充背景、注意事项…",
            singleLine = false,
        )
        Text(
            text = "${form.description.length}/${VoteLimits.MAX_DESCRIPTION}" + (descError?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = if (descError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VoteDeadlineSection(
    form: VoteFormState,
    errors: List<VoteValidationError>,
    nowMillis: Long,
    onUpdate: (VoteFormState.() -> VoteFormState) -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val deadlineError = voteErrorFor(errors, VoteField.DEADLINE)
    VoteSectionCard(title = "截止时间") {
        CtToolSegmented(
            options = listOf("限时", "指定", "不限"),
            selectedIndex = when (form.deadlineType) {
                DeadlineType.QUICK -> 0
                DeadlineType.AT -> 1
                DeadlineType.NONE -> 2
            },
            onSelect = { index ->
                onUpdate {
                    when (index) {
                        0 -> copy(deadlineType = DeadlineType.QUICK)
                        1 -> copy(
                            deadlineType = DeadlineType.AT,
                            deadlineAt = deadlineAt.takeIf { it > 0L }
                                ?: (nowMillis + 24L * 60 * 60 * 1000),
                        )

                        else -> copy(deadlineType = DeadlineType.NONE)
                    }
                }
            },
        )
        when (form.deadlineType) {
            DeadlineType.QUICK -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VoteLimits.QUICK_MINUTES.forEach { minutes ->
                        CtToolChip(
                            text = "$minutes 分钟",
                            selected = form.quickMinutes == minutes,
                            onClick = { onUpdate { copy(quickMinutes = minutes) } },
                        )
                    }
                }
                Text(
                    text = "将于 ${VoteLogic.formatAt(VoteLogic.quickDeadline(nowMillis, form.quickMinutes))} 截止",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DeadlineType.AT -> {
                val selected = form.deadlineAt > 0L
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val base = if (form.deadlineAt > 0L) {
                                Instant.ofEpochMilli(form.deadlineAt).atZone(zone).toLocalDateTime()
                            } else {
                                LocalDateTime.now(zone).plusDays(1)
                            }
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    val picked = LocalDateTime.of(
                                        year,
                                        month + 1,
                                        day,
                                        base.hour,
                                        base.minute,
                                    ).atZone(zone).toInstant().toEpochMilli()
                                    onUpdate { copy(deadlineAt = picked) }
                                },
                                base.year,
                                base.monthValue - 1,
                                base.dayOfMonth,
                            ).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = CtShapeTokens.pill,
                    ) {
                        Text(
                            text = if (selected) {
                                VoteLogic.formatAt(form.deadlineAt).takeWhile { it != ' ' }
                            } else {
                                "选择日期"
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val base = if (form.deadlineAt > 0L) {
                                Instant.ofEpochMilli(form.deadlineAt).atZone(zone).toLocalDateTime()
                            } else {
                                LocalDateTime.now(zone).plusDays(1)
                            }
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    val picked = base
                                        .withHour(hour)
                                        .withMinute(minute)
                                        .withSecond(0)
                                        .withNano(0)
                                        .atZone(zone)
                                        .toInstant()
                                        .toEpochMilli()
                                    onUpdate { copy(deadlineAt = picked) }
                                },
                                base.hour,
                                base.minute,
                                true,
                            ).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = CtShapeTokens.pill,
                    ) {
                        Text(
                            text = if (selected) {
                                VoteLogic.formatAt(form.deadlineAt).substringAfter(' ')
                            } else {
                                "选择时间"
                            },
                        )
                    }
                }
                Text(
                    text = if (selected) {
                        "将于 ${VoteLogic.formatAt(form.deadlineAt)} 截止"
                    } else {
                        "请选择截止日期与时间"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DeadlineType.NONE -> Text(
                text = "不设截止时间，可随时手动结束",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        VoteFieldError(deadlineError)
    }
}

@Composable
private fun VoteOptionsSection(
    form: VoteFormState,
    errors: List<VoteValidationError>,
    submittingImage: Boolean,
    onPickImage: (Int) -> Unit,
    onUpdate: (VoteFormState.() -> VoteFormState) -> Unit,
) {
    val optionsError = voteErrorFor(errors, VoteField.OPTIONS)
    VoteSectionCard(title = "选项（${form.options.size}/${VoteLimits.MAX_OPTIONS}）") {
        VoteFieldError(optionsError)
        form.options.forEachIndexed { index, option ->
            var descVisible by remember { mutableStateOf(option.desc.isNotBlank()) }
            CtToolCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(20.dp),
                    )
                    CtToolField(
                        label = "选项文字",
                        value = option.text,
                        onValueChange = { value ->
                            onUpdate { copy(options = options.replaceAt(index) { it.copy(text = value.take(100)) }) }
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = "选项文字",
                    )
                    IconButton(
                        onClick = {
                            if (index > 0) {
                                onUpdate { copy(options = options.swapped(index, index - 1)) }
                            }
                        },
                        enabled = index > 0,
                    ) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "上移第 ${index + 1} 个选项")
                    }
                    IconButton(
                        onClick = {
                            if (index < form.options.lastIndex) {
                                onUpdate { copy(options = options.swapped(index, index + 1)) }
                            }
                        },
                        enabled = index < form.options.lastIndex,
                    ) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "下移第 ${index + 1} 个选项")
                    }
                    IconButton(
                        onClick = {
                            if (form.options.size > VoteLimits.MIN_OPTIONS) {
                                onUpdate { copy(options = options.filterIndexed { i, _ -> i != index }) }
                            }
                        },
                        enabled = form.options.size > VoteLimits.MIN_OPTIONS,
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除第 ${index + 1} 个选项")
                    }
                }
                val textError = voteOptionError(errors, index, "OPTION_TEXT")
                val descError = voteOptionError(errors, index, "OPTION_DESC")
                val imageError = voteOptionError(errors, index, "OPTION_IMAGE") ?: voteErrorFor(errors, VoteField.OPTION_IMAGE)
                VoteFieldError(textError)
                if (descVisible) {
                    CtToolField(
                        label = "补充说明（可选）",
                        value = option.desc,
                        onValueChange = { value ->
                            onUpdate { copy(options = options.replaceAt(index) { it.copy(desc = value.take(240)) }) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                    )
                    VoteFieldError(descError)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { descVisible = !descVisible }) {
                        Text(if (descVisible) "收起说明" else "补充说明")
                    }
                    TextButton(
                        onClick = { onPickImage(index) },
                        enabled = !submittingImage,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (option.img.isBlank()) "添加配图" else "更换配图")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (option.img.isNotBlank()) {
                        Text(
                            text = "${option.img.length / 1000} K 文本",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                VoteFieldError(imageError)
                if (option.img.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                    ) {
                        VoteDataUrlImage(
                            dataUrl = option.img,
                            contentDescription = "第 ${index + 1} 个选项配图",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(CtRadius.cardCompact)),
                        )
                        IconButton(
                            onClick = {
                                onUpdate { copy(options = options.replaceAt(index) { it.copy(img = "") }) }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "移除第 ${index + 1} 个选项配图",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
                }
            }
        }
        TextButton(
            onClick = { onUpdate { copy(options = options + OptionForm()) } },
            enabled = form.options.size < VoteLimits.MAX_OPTIONS,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("添加选项")
        }
        Text(
            text = "配图会自动压缩到长边 640px 以内（单张 ≤150KB）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VoteRulesSection(
    form: VoteFormState,
    errors: List<VoteValidationError>,
    onUpdate: (VoteFormState.() -> VoteFormState) -> Unit,
) {
    val settingsError = voteErrorFor(errors, VoteField.SETTINGS)
    VoteSectionCard(title = "投票规则") {
        CtToolSegmented(
            options = listOf("单选", "多选"),
            selectedIndex = if (form.multi) 1 else 0,
            onSelect = { index ->
                onUpdate {
                    if (index == 1) {
                        copy(multi = true, maxChoices = maxChoices.coerceAtLeast(2))
                    } else {
                        copy(multi = false, maxChoices = 1)
                    }
                }
            },
        )
        if (form.multi) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "最多可选",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        onUpdate { copy(maxChoices = (maxChoices - 1).coerceAtLeast(VoteLimits.MIN_CHOICES)) }
                    },
                    enabled = form.maxChoices > VoteLimits.MIN_CHOICES,
                ) {
                    Text("−", style = MaterialTheme.typography.headlineSmall)
                }
                Text(
                    text = form.maxChoices.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(
                    onClick = {
                        onUpdate {
                            copy(
                                maxChoices = (maxChoices + 1)
                                    .coerceAtMost(minOf(VoteLimits.MAX_CHOICES, form.options.size)),
                            )
                        }
                    },
                    enabled = form.maxChoices < minOf(VoteLimits.MAX_CHOICES, form.options.size),
                ) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        VoteFieldError(settingsError)
        VoteSwitchRow(
            title = "匿名投票",
            subtitle = "投票人不显示姓名",
            checked = form.anonymous,
            onCheckedChange = { checked -> onUpdate { copy(anonymous = checked) } },
        )
        VoteSwitchRow(
            title = "允许改票",
            subtitle = "截止前可修改选择",
            checked = form.allowChange,
            onCheckedChange = { checked -> onUpdate { copy(allowChange = checked) } },
        )
        Text(
            text = "结果可见性",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        CtToolSegmented(
            options = listOf("始终", "投票后", "截止后"),
            selectedIndex = when (form.resultVisibility) {
                PollResultVisibility.ALWAYS -> 0
                PollResultVisibility.AFTER_VOTE -> 1
                PollResultVisibility.AFTER_CLOSE -> 2
            },
            onSelect = { index ->
                onUpdate {
                    copy(
                        resultVisibility = when (index) {
                            0 -> PollResultVisibility.ALWAYS
                            2 -> PollResultVisibility.AFTER_CLOSE
                            else -> PollResultVisibility.AFTER_VOTE
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun VoteAudienceSection(
    form: VoteFormState,
    errors: List<VoteValidationError>,
    memberSourceAvailable: Boolean,
    onOpenPicker: () -> Unit,
    onUpdate: (VoteFormState.() -> VoteFormState) -> Unit,
) {
    val audienceError = voteErrorFor(errors, VoteField.AUDIENCE)
    VoteSectionCard(title = "可见范围") {
        CtToolSegmented(
            options = listOf("全班", "班委", "团员", "指定"),
            selectedIndex = when (form.audience) {
                PollAudience.ALL -> 0
                PollAudience.COMMITTEE -> 1
                PollAudience.LEAGUE -> 2
                PollAudience.CUSTOM -> 3
            },
            onSelect = { index ->
                onUpdate {
                    when (index) {
                        1 -> copy(audience = PollAudience.COMMITTEE)
                        2 -> copy(audience = PollAudience.LEAGUE)
                        3 -> copy(audience = PollAudience.CUSTOM)
                        else -> copy(audience = PollAudience.ALL)
                    }
                }
            },
        )
        Text(
            text = when (form.audience) {
                PollAudience.ALL -> "全班成员可见并可投票"
                PollAudience.COMMITTEE -> "仅班委可见并可投票"
                PollAudience.LEAGUE -> "仅团员可见并可投票"
                PollAudience.CUSTOM -> "仅指定成员可见并可投票"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (form.audience == PollAudience.CUSTOM) {
            CtToolPrimaryButton(
                text = if (form.audienceSeats.isEmpty()) {
                    "选择可见成员"
                } else {
                    "已选 ${form.audienceSeats.size} 人 · 修改"
                },
                onClick = onOpenPicker,
                enabled = memberSourceAvailable,
            )
            if (!memberSourceAvailable) {
                Text(
                    text = "成员名单暂不可用，请稍后再试",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            VoteFieldError(audienceError)
        }
    }
}

@Composable
private fun VoteSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    // v6：统一卡片外壳 + 卡片标题；内部保持 10dp 字段间距
    CtToolCard(title = title) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun VoteSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.semantics { contentDescription = title })
    }
}

// ---------------------------------------------------------------- 小工具

private fun List<OptionForm>.replaceAt(index: Int, transform: (OptionForm) -> OptionForm): List<OptionForm> =
    mapIndexed { i, option -> if (i == index) transform(option) else option }

private fun List<OptionForm>.swapped(from: Int, to: Int): List<OptionForm> {
    if (from !in indices || to !in indices) return this
    val mutable = toMutableList()
    val item = mutable.removeAt(from)
    mutable.add(to, item)
    return mutable
}

/** 预览用：把简化 draft 转成表单（只用于初始值）。 */
private fun PollDraft.toFormState(): VoteFormState = VoteFormState(
    title = title,
    description = description,
    deadlineType = when {
        deadline > 0L && mode == PollMode.QUICK -> DeadlineType.QUICK
        deadline > 0L -> DeadlineType.AT
        else -> DeadlineType.NONE
    },
    deadlineAt = deadline,
    options = options.map { OptionForm(id = it.id, text = it.text, desc = it.desc, img = it.img) }
        .ifEmpty { listOf(OptionForm(), OptionForm()) },
    multi = settings.multi,
    maxChoices = settings.maxChoices.coerceAtLeast(2),
    anonymous = settings.anonymous,
    allowChange = settings.allowChange,
    resultVisibility = settings.resultVisibility,
    audience = audience,
    audienceSeats = audienceSeats.toSet(),
)

/** 预览/测试用：列出一些成员供「指定成员」选择（Lead 接线时不需要）。 */
@Suppress("unused")
internal fun previewMemberEntries(): List<MemberEntry> = listOf(
    MemberEntry(seat = 1, name = "张三", badge = "班长"),
    MemberEntry(seat = 2, name = "李四", badge = "团支书"),
    MemberEntry(seat = 3, name = "王五"),
    MemberEntry(seat = 4, name = "赵六"),
)

@Suppress("unused")
internal fun previewMemberCategories(): Map<MemberCategory, List<MemberEntry>> = mapOf(
    MemberCategory.COMMITTEE to previewMemberEntries().filter { it.badge.isNotBlank() },
    MemberCategory.LEAGUE to previewMemberEntries().drop(1),
    MemberCategory.ALL to previewMemberEntries(),
)

@Suppress("unused")
private val voteEmptyIconHint = Icons.Rounded.HowToVote

