@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.roster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.member.ClassMembersRepository
import com.furina.classtools.data.member.ClassMembersResult
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.features.roster.RosterCodec
import com.furina.classtools.features.roster.RosterLogic
import com.furina.classtools.features.roster.RosterMember
import com.furina.classtools.features.roster.RosterState
import com.furina.classtools.ui.components.EmptyState
import com.furina.classtools.ui.components.tool.CtToolErrorState
import com.furina.classtools.ui.components.tool.CtToolPrimaryButton
import com.furina.classtools.ui.theme.CtRadius
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// =============================================================================
// 名单 / 点名共用状态与错误 UI
//
// 2026-11-14（task-54）：点名从「名单 · 点名」双页拆成独立页面（RollCallScreen），
// 名单页只展示三分类名册（RosterScreen）。
// - 只有点名使用 `userdata.roster` 读写（rollSel/rollSt/rollNames，RosterCodec 键序不变）；
// - `class_members` 加载（6h 缓存 / 失败降级旧缓存 / 下拉刷新）；
// - 空-加载-错误四态与「刷新失败但显示本机缓存」错误条文案。
// 拆分只移动 UI 结构，不改同步/姓名迁移/隐私语义。
// =============================================================================

/** 页面内部阶段（四态中的加载/错误两态由它承载）。 */
internal enum class RosterPhase { LOADING, READY, ERROR }

/**
 * `userdata.roster` 的点名内存态 + 写回句柄。
 *
 * 语义与拆分前 [`RosterScreen`] 内部实现完全一致：
 * - 操作即时显示，已拉取的名单和出勤在本机写入完成后反映到页面；
 * - 顺序保存修改的字段，并保留其它工具在同一 section 中的选择。
 */
internal data class RosterSync(
    val state: RosterState,
    val write: (RosterState) -> Unit,
)

@Composable
internal fun rememberRosterSync(
    userDataRepository: UserDataRepository?,
    initialRoster: RosterState?,
): RosterSync {
    val fallbackSections = remember { MutableStateFlow(JsonObject(emptyMap())) }
    val sectionsFlow = userDataRepository?.currentSections ?: fallbackSections
    val sections by sectionsFlow.collectAsState()
    var rosterState by remember(initialRoster) {
        mutableStateOf(initialRoster ?: RosterCodec.fromJson(sections["roster"]))
    }
    var pending by remember { mutableIntStateOf(0) }
    val writes = remember { Mutex() }
    LaunchedEffect(sections["roster"], pending) {
        if (userDataRepository != null && pending == 0 && sections["roster"] != null) {
            rosterState = RosterCodec.fromJson(userDataRepository.currentSections.value["roster"])
        }
    }
    val scope = rememberCoroutineScope()
    val write: (RosterState) -> Unit = { next ->
        val previous = RosterCodec.toJson(rosterState)
        val changes = RosterCodec.toJson(next).filter { (key, value) -> previous[key] != value }
        rosterState = next
        pending++
        scope.launch {
            try {
                writes.withLock {
                    val repository = userDataRepository ?: return@withLock
                    if (changes.isEmpty()) return@withLock
                    val current = repository.currentSections.value
                    val sectionsNext = LinkedHashMap<String, JsonElement>(current)
                    val existing = current["roster"] as? JsonObject ?: JsonObject(emptyMap())
                    sectionsNext["roster"] = JsonObject(existing + changes)
                    repository.updateCurrent(JsonObject(sectionsNext))
                }
            } catch (error: java.io.IOException) {
                // Keep the last persisted roster visible if local storage was unavailable.
                userDataRepository?.let { rosterState = RosterCodec.fromJson(it.currentSections.value["roster"]) }
            } finally {
                pending--
            }
        }
    }
    return RosterSync(state = rosterState, write = write)
}

/**
 * `class_members` 加载状态（名单页 / 点名页共用，语义与拆分前一致）：
 * - 6h 缓存；失败时若已有名单则保留并置 [errorMessage]（顶部刷新错误条），否则进入错误态；
 * - [reload] = 顶栏刷新 / 错误态重试（`force = true`，不亮下拉指示）；
 * - [refresh] = 下拉刷新（`force = true` + 亮起刷新指示）。
 *
 * @param unauthenticatedMessage 未完成身份验证时的错误文案（两页措辞不同）。
 */
internal data class RosterMembersState(
    val members: List<RosterMember>,
    val fromCache: Boolean,
    val phase: RosterPhase,
    val errorMessage: String?,
    val isRefreshing: Boolean,
    val reload: () -> Unit,
    val refresh: () -> Unit,
)

@Composable
internal fun rememberRosterMembersState(
    session: Session?,
    repository: ClassMembersRepository?,
    userDataRepository: UserDataRepository?,
    initialMembers: List<RosterMember>,
    skipLoad: Boolean,
    forcedErrorMessage: String?,
    unauthenticatedMessage: String,
): RosterMembersState {
    var members by remember(session?.sid, initialMembers) { mutableStateOf(initialMembers) }
    var fromCache by remember(session?.sid) { mutableStateOf(false) }
    var reloadToken by remember(session?.sid) { mutableIntStateOf(0) }
    var isRefreshing by remember(session?.sid) { mutableStateOf(false) }
    var phase by remember(session?.sid) {
        mutableStateOf(
            when {
                forcedErrorMessage != null -> RosterPhase.ERROR
                skipLoad -> RosterPhase.READY
                else -> RosterPhase.LOADING
            },
        )
    }
    var errorMessage by remember(session?.sid) { mutableStateOf(forcedErrorMessage) }

    LaunchedEffect(session?.sid, repository, reloadToken) {
        if (skipLoad || forcedErrorMessage != null) {
            phase = RosterPhase.READY
            isRefreshing = false
            return@LaunchedEffect
        }
        if (session == null) {
            errorMessage = unauthenticatedMessage
            phase = RosterPhase.ERROR
            isRefreshing = false
            return@LaunchedEffect
        }
        val classMembersRepository = repository
        if (classMembersRepository == null) {
            phase = RosterPhase.READY
            isRefreshing = false
            return@LaunchedEffect
        }
        if (members.isEmpty()) phase = RosterPhase.LOADING
        when (val result = classMembersRepository.load(force = reloadToken > 0)) {
            is ClassMembersResult.Success -> {
                members = RosterLogic.membersOf(result.members)
                fromCache = result.fromCache
                errorMessage = null
                phase = RosterPhase.READY
            }

            is ClassMembersResult.Failure -> {
                errorMessage = result.message
                phase = if (members.isEmpty()) RosterPhase.ERROR else RosterPhase.READY
            }
        }
        runCatching { userDataRepository?.pull(force = false) }
        isRefreshing = false
    }

    return RosterMembersState(
        members = members,
        fromCache = fromCache,
        phase = phase,
        errorMessage = errorMessage,
        isRefreshing = isRefreshing,
        reload = { reloadToken += 1 },
        refresh = {
            isRefreshing = true
            reloadToken += 1
        },
    )
}

/** 名单页 / 点名页共用：加载失败态（说明 + 重试）。 */
@Composable
internal fun RosterErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CtToolErrorState(
            title = "名单加载失败",
            message = message,
            onRetry = onRetry,
        )
    }
}

/** 名单页 / 点名页共用：刷新失败但保留本机缓存时的顶部错误条。 */
@Composable
internal fun RosterRefreshErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CtRadius.cardCompact),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "刷新失败：$message（显示本机缓存）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

