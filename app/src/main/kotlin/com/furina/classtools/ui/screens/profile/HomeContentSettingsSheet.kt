package com.furina.classtools.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furina.classtools.data.settings.AppSettingsStore
import com.furina.classtools.features.home.HomeContentPreferences
import com.furina.classtools.features.home.HomeRegion
import com.furina.classtools.features.home.HomeTone
import com.furina.classtools.features.home.loadHomeRegions
import com.furina.classtools.ui.components.CtButton
import com.furina.classtools.ui.components.CtButtonStyle
import com.furina.classtools.ui.components.expressive.CtBottomSheet
import com.furina.classtools.ui.components.expressive.CtBottomSheetHeader
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.CtText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun HomeContentSettingsSheet(store: AppSettingsStore, account: String, onDismiss: () -> Unit) {
    val flow = remember(store, account) { store.homeContent(account) }
    val record by flow.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val assets = LocalContext.current.assets
    val regions by produceState<List<HomeRegion>?>(null, assets) {
        value = withContext(Dispatchers.IO) { loadHomeRegions(assets) }
    }
    var pickingRegion by rememberSaveable(account) { mutableStateOf(false) }
    val limit = LocalConfiguration.current.screenHeightDp.dp * 0.78f

    fun change(update: (HomeContentPreferences) -> HomeContentPreferences) {
        scope.launch { store.changeHomeContent(account, update) }
    }

    key(pickingRegion) {
        CtBottomSheet(onDismissRequest = { if (pickingRegion) pickingRegion = false else onDismiss() }) {
            CtBottomSheetHeader(title = if (pickingRegion) "选择天气地区" else "首页内容")
            val current = record?.preferences
            if (current == null) {
                Text("正在读取设置", Modifier.padding(CtSpace.cardPadding), style = CtText.body)
            } else if (pickingRegion) {
                HomeRegionChooser(regions, current.regionId, Modifier.heightIn(max = limit),
                    onBack = { pickingRegion = false },
                    onSelect = { region ->
                        change { it.copy(regionId = region.id.takeUnless { id -> id == HomeRegion.minhou.id }) }
                        pickingRegion = false
                    })
            } else {
                val region = regions?.firstOrNull { it.id == current.regionId }
                Column(
                    Modifier.heightIn(max = limit).verticalScroll(rememberScrollState()).padding(CtSpace.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(CtSpace.xs),
                ) {
                    HomeSettingSwitch("趣味内容", current.funContent, { value -> change { it.copy(funContent = value) } },
                        subtitle = if (current.funContent) null else "简洁模式")
                    if (current.funContent) {
                        HomeSettingSwitch("联网文案", current.onlineContent, { value -> change { it.copy(onlineContent = value) } })
                        HomeSettingSwitch("偶尔显示英语", current.english, { value -> change { it.copy(english = value) } })
                        HomeSettingSwitch("装饰动效", current.motion, { value -> change { it.copy(motion = value) } })
                        Text("语气", style = CtText.label, color = MaterialTheme.colorScheme.onSurface)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpace.xs)) {
                            HomeTone.entries.forEach { tone ->
                                FilterChip(selected = current.tone == tone, onClick = { change { it.copy(tone = tone) } },
                                    label = { Text(tone.label) }, modifier = Modifier.heightIn(min = 48.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val regionLabel = region?.label ?: if (current.regionId == null || regions != null) HomeRegion.minhou.label else "读取中"
                    Column(Modifier.fillMaxWidth().clickable { pickingRegion = true }.heightIn(min = 48.dp)
                        .padding(vertical = CtSpace.xs)) {
                        Text("天气地区", style = CtText.body, color = MaterialTheme.colorScheme.onSurface)
                        Text(regionLabel, style = CtText.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    CtButton("完成", onDismiss, compact = true, style = CtButtonStyle.Tonal)
                }
            }
        }
    }
}

@Composable
private fun HomeSettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpace.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = CtText.body, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, style = CtText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun HomeRegionChooser(
    regions: List<HomeRegion>?,
    selectedId: String?,
    modifier: Modifier,
    onBack: () -> Unit,
    onSelect: (HomeRegion) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(regions, query, selectedId) {
        val available = regions.orEmpty()
        val needle = query.trim()
        if (needle.isEmpty()) {
            (listOfNotNull(available.firstOrNull { it.id == selectedId }) + available.filter { it.parent == "福建省" })
                .distinctBy { it.id }.take(14)
        } else {
            available.filter { it.label.contains(needle, ignoreCase = true) }.take(80)
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = CtSpace.cardPadding),
        verticalArrangement = Arrangement.spacedBy(CtSpace.xs),
    ) {
        item {
            OutlinedTextField(value = query, onValueChange = { query = it.take(40) },
                label = { Text("搜索城市或区县") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            CtButton("恢复默认 · 福州市闽侯县", { onSelect(HomeRegion.minhou) },
                style = CtButtonStyle.Tonal, compact = true)
        }
        if (regions == null) {
            item { Text("正在读取地区", style = CtText.label) }
        } else if (query.isNotBlank() && results.isEmpty()) {
            item { Text("没有匹配的市县", style = CtText.body) }
        }
        items(results, key = { it.id }) { region ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                    if (selectedId == region.id) stateDescription = "已选"
                }.clickable { onSelect(region) }.padding(vertical = CtSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(region.label, color = MaterialTheme.colorScheme.onSurface, style = CtText.body)
            }
        }
        item {
            Text("地名资料来自 GeoNames（CC BY 4.0）", style = CtText.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            CtButton("返回首页内容", onBack, style = CtButtonStyle.Text, compact = true)
        }
    }
}
