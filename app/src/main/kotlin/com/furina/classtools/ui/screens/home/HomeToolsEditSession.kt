package com.furina.classtools.ui.screens.home

import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.sync.SessionUserdataRepository
import com.furina.classtools.data.sync.UserdataSectionUpdate
import com.furina.classtools.features.home.HomeToolsDraft
import com.furina.classtools.features.home.HomeToolsEditor
import com.furina.classtools.ui.components.CtToastBus
import com.furina.classtools.ui.components.light.CtLightAlertDialog
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One editable workbench configuration shared by both pager tabs. */
class HomeToolsEditSession internal constructor(
    private val scope: CoroutineScope,
    private val saveDraft: suspend (HomeToolsDraft) -> UserdataSectionUpdate,
    initial: HomeToolsDraft? = null,
    private val notify: (String) -> Unit = { CtToastBus.show(it) },
) {
    var draft by mutableStateOf(initial)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var conflict by mutableStateOf<List<String>?>(null)
        private set
    private var afterLeaving by mutableStateOf<(() -> Unit)?>(null)
    val leaving: Boolean get() = afterLeaving != null
    val canEdit: Boolean get() = !saving && !leaving && conflict == null

    fun isEditing(page: Int): Boolean = draft?.page == page

    fun begin(page: Int, baseline: List<String>) {
        if (draft == null) draft = HomeToolsDraft.begin(page, baseline)
    }

    fun toggle(page: Int, id: String): Boolean {
        val current = draft?.takeIf { it.page == page && canEdit } ?: return false
        if (id !in current.ids && current.ids.size == HomeToolsEditor.MAX_QUICK_TOOLS) {
            notify("最多选择 ${HomeToolsEditor.MAX_QUICK_TOOLS} 个常用工具")
            return false
        }
        val next = current.toggle(id)
        draft = next
        return next != current
    }

    fun reorder(page: Int, order: List<String>) {
        val current = draft?.takeIf { it.page == page && canEdit } ?: return
        draft = current.reorder(order)
    }

    fun requestLeave(replacePending: Boolean = false, after: () -> Unit = {}) {
        if (!canEdit) {
            if (replacePending) afterLeaving = after
            return
        }
        if (draft?.dirty == true) {
            afterLeaving = after
        } else {
            dismissDraft()
            after()
        }
    }

    fun continueEditing() {
        if (!saving) {
            afterLeaving = null
            conflict = null
        }
    }

    fun discard() {
        if (saving) return
        val after = afterLeaving
        dismissDraft()
        after?.invoke()
    }

    fun loadLatest() {
        val latest = conflict ?: return
        val current = draft ?: return
        draft = HomeToolsDraft.begin(current.page, latest)
        continueEditing()
        error = null
    }

    fun overwrite() {
        val latest = conflict ?: return
        val current = draft ?: return
        draft = current.copy(baseline = latest)
        conflict = null
        save()
    }

    fun save() {
        val current = draft ?: return
        if (saving || conflict != null) return
        if (!current.dirty) {
            discard()
            return
        }

        error = null
        saving = true
        scope.launch {
            val result = try {
                saveDraft(current)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "本机保存失败，请重试。"
                return@launch
            } finally {
                saving = false
            }
            when (result) {
                is UserdataSectionUpdate.Saved -> {
                    val after = afterLeaving
                    dismissDraft()
                    notify(if (result.syncPending) "常用工具已保存到本机，等待云同步" else "常用工具已保存到本机")
                    after?.invoke()
                }
                is UserdataSectionUpdate.Conflict -> conflict = HomeToolsEditor.readSection(result.value)
                UserdataSectionUpdate.Inactive -> {
                    error = "当前登录状态已变化，草稿未保存。"
                    afterLeaving = null
                }
            }
        }
    }

    private fun dismissDraft() {
        draft = null
        afterLeaving = null
        conflict = null
        error = null
    }
}

@Composable
fun rememberHomeToolsEditSession(session: Session, repository: SessionUserdataRepository): HomeToolsEditSession {
    val scope = rememberCoroutineScope()
    val owner = remember(session, repository) { repository.boundTo(session) }
    val accountKey = remember(session) {
        // Validate a restored draft against its login without putting credentials in the saved state.
        val bytes = "${session.sid}\u0000${session.issuedAt}\u0000${session.debug}\u0000${session.token}".toByteArray()
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
    val saveDraft: suspend (HomeToolsDraft) -> UserdataSectionUpdate = { draft ->
        owner?.updateCurrentSection("homeTools") { value ->
            val latest = HomeToolsEditor.readSection(value)
            when (latest) {
                draft.ids, draft.baseline -> HomeToolsEditor.section(draft.ids)
                else -> null
            }
        } ?: UserdataSectionUpdate.Inactive
    }
    val saver = Saver<HomeToolsEditSession, Bundle>(
        save = { editor ->
            editor.draft?.let { draft ->
                Bundle().apply {
                    putString("account", accountKey)
                    putInt("page", draft.page)
                    putStringArrayList("baseline", ArrayList(draft.baseline))
                    putStringArrayList("draft", ArrayList(draft.ids))
                }
            }
        },
        restore = { bundle ->
            val initial = if (bundle.getString("account") == accountKey) {
                HomeToolsDraft(
                    page = bundle.getInt("page"),
                    baseline = bundle.getStringArrayList("baseline").orEmpty(),
                    ids = HomeToolsEditor.availableIds(bundle.getStringArrayList("draft").orEmpty()),
                )
            } else null
            HomeToolsEditSession(scope, saveDraft, initial)
        },
    )
    return rememberSaveable(accountKey, owner, saver = saver) { HomeToolsEditSession(scope, saveDraft) }
}

@Composable
internal fun rememberPreviewHomeToolsEditSession(
    page: Int,
    initialIds: List<String>,
    initialEditing: Boolean = false,
    onSaved: (List<String>) -> Unit,
): HomeToolsEditSession {
    val scope = rememberCoroutineScope()
    val save by rememberUpdatedState(onSaved)
    return remember(scope) {
        HomeToolsEditSession(
            scope = scope,
            saveDraft = { draft -> save(draft.ids); UserdataSectionUpdate.Saved(syncPending = false) },
            initial = HomeToolsDraft.begin(page, initialIds).takeIf { initialEditing },
        )
    }
}

@Composable
fun HomeToolsEditDialogs(editor: HomeToolsEditSession) {
    if (editor.conflict != null) {
        CtLightAlertDialog(
            onDismissRequest = editor::continueEditing,
            title = { Text("常用工具配置已更新") },
            text = { Text("当前配置与开始编辑时不同。可以载入最新配置，也可以确认用这次草稿保存。") },
            confirmButton = { TextButton(onClick = editor::overwrite) { Text("用草稿保存") } },
            dismissButton = { TextButton(onClick = editor::loadLatest) { Text("载入最新") } },
        )
    } else if (editor.leaving) {
        CtLightAlertDialog(
            onDismissRequest = editor::continueEditing,
            title = { Text("离开常用工具编辑？") },
            text = {
                Column {
                    Text(editor.error ?: "这次对首页常用工具的修改还未保存。")
                    TextButton(onClick = editor::continueEditing, enabled = !editor.saving) { Text("继续编辑") }
                }
            },
            confirmButton = {
                TextButton(onClick = editor::save, enabled = !editor.saving) {
                    Text(if (editor.saving) "正在保存" else "保存并离开")
                }
            },
            dismissButton = {
                TextButton(onClick = editor::discard, enabled = !editor.saving) { Text("放弃修改") }
            },
        )
    }
}

@Composable
internal fun HomeToolsEditHint(editor: HomeToolsEditSession, ids: List<String>) {
    val problem = editor.error ?: if (editor.draft?.baseline != ids) "配置已更新，草稿仍保留。" else null
    Text(
        text = problem ?: "首页快捷 ${editor.draft?.ids?.size ?: 0}/${HomeToolsEditor.MAX_QUICK_TOOLS} · 长按快捷项可排序",
        color = if (problem == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth(),
    )
}
