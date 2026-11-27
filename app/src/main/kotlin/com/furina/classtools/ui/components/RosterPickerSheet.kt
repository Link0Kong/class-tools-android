package com.furina.classtools.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.member.MemberCategory
import com.furina.classtools.data.member.MemberEntry
import com.furina.classtools.data.member.RosterMemberSource
import com.furina.classtools.ui.components.expressive.CtBottomSheet
import com.furina.classtools.ui.components.expressive.CtBottomSheetFooter
import com.furina.classtools.ui.components.expressive.CtBottomSheetHeader
import com.furina.classtools.ui.components.tool.CtToolSegmented
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Stable
internal class RosterSearchState(
    private val source: RosterMemberSource?,
    private val scope: CoroutineScope,
) {
    var query by mutableStateOf("")
        private set
    var matchedSeats by mutableStateOf(emptySet<Int>())
        private set
    private var request: Job? = null

    fun update(value: String) {
        request?.cancel()
        request = null
        query = value
        matchedSeats = emptySet()
    }

    fun submit() {
        val directory = source ?: return
        val input = query
        if (input.isBlank() || request?.isActive == true) return
        request = scope.launch {
            val seats = directory.searchSeats(input)
            if (isActive && query == input) matchedSeats = seats
        }
    }
}

@Composable
internal fun rememberRosterSearch(source: RosterMemberSource?): RosterSearchState {
    return key(source) {
        val scope = rememberCoroutineScope()
        remember { RosterSearchState(source, scope) }
    }
}

@Composable
internal fun RosterSearchField(search: RosterSearchState, modifier: Modifier = Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    val submit: () -> Unit = {
        search.submit()
        keyboard?.hide()
    }
    OutlinedTextField(
        value = search.query, onValueChange = search::update, singleLine = true, modifier = modifier,
        label = { Text("搜索") }, placeholder = { Text("姓名、座号或职务") },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submit() }),
        trailingIcon = { IconButton(onClick = submit) { Icon(Icons.Rounded.Search, contentDescription = "搜索") } },
        colors = ctOutlinedFieldColors(),
    )
}

@Composable
internal fun RosterPickerSheet(
    source: RosterMemberSource?,
    initialSeats: Set<Int>,
    onConfirm: (Set<Int>) -> Unit,
    onDismiss: () -> Unit,
    title: String = "选择名单",
    fallbackMembers: List<MemberEntry> = emptyList(),
    allowEmpty: Boolean = true,
) {
    var category by rememberSaveable { mutableStateOf(MemberCategory.ALL) }
    var current by rememberSaveable(source) { mutableStateOf(ArrayList(initialSeats)) }
    val search = rememberRosterSearch(source)
    val query = search.query
    var retry by remember { mutableIntStateOf(0) }
    val loaded = remember(source) { mutableStateMapOf<MemberCategory, List<MemberEntry>>() }
    var error by remember(category, source) { mutableStateOf<String?>(null) }

    LaunchedEffect(source, category, retry) {
        if (source == null || loaded.containsKey(category)) return@LaunchedEffect
        error = null
        try {
            loaded[category] = source.load(category)
        } catch (failure: IOException) {
            error = failure.message ?: "名单加载失败"
        }
    }
    val members = if (source == null) fallbackMembers else loaded[category]
    val matches = search.matchedSeats
    val visible = remember(members, query, matches) {
        members.orEmpty().filter { it.matches(query) || it.seat in matches }
    }
    val seats = remember(visible) { visible.map { it.seat } }
    val selected = remember(current) { current.toSet() }

    CtBottomSheet(onDismissRequest = onDismiss) {
        CtBottomSheetHeader(title = title, subtitle = "已选 ${selected.size} 人")
        if (source != null) {
            CtToolSegmented(
                options = MemberCategory.entries.map { it.label },
                selectedIndex = MemberCategory.entries.indexOf(category),
                onSelect = { category = MemberCategory.entries[it] },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        RosterSearchField(search, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp))
        FlowRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            TextButton(enabled = seats.isNotEmpty(), onClick = { current = ArrayList(seats) }) { Text("只选当前") }
            TextButton(enabled = seats.any { it !in selected }, onClick = {
                current = ArrayList(selected + seats)
            }) { Text("追加当前") }
            TextButton(enabled = selected.isNotEmpty(), onClick = { current = arrayListOf() }) { Text("清空已选") }
        }
        Box(modifier = Modifier.weight(1f, fill = false).fillMaxWidth().heightIn(min = 64.dp, max = 360.dp)) {
            when {
                error != null -> TextButton(onClick = { retry++ }, modifier = Modifier.align(Alignment.Center)) {
                    Text("名单加载失败，重试")
                }
                members == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(24.dp))
                visible.isEmpty() -> Text(
                    if (query.isBlank()) "此分类暂无成员" else "没有匹配的成员",
                    modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(Modifier.fillMaxWidth()) {
                    items(visible, key = { it.seat }) { member ->
                        val checked = member.seat in selected
                        Row(
                            modifier = Modifier.fillMaxWidth().toggleable(
                                value = checked, role = Role.Checkbox,
                                onValueChange = { current = ArrayList(if (it) selected + member.seat else selected - member.seat) },
                            ).heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(member.seat.toString().padStart(2, '0'), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.weight(1f)) {
                                Text(member.name, style = MaterialTheme.typography.bodyLarge)
                                val detail = member.badge
                                if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        CtBottomSheetFooter(primaryText = "确定（${selected.size}）", onPrimary = { onConfirm(selected) },
            primaryEnabled = allowEmpty || selected.isNotEmpty(), secondaryText = "取消", onSecondary = onDismiss)
    }
}
