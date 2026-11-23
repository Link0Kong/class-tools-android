package com.furina.classtools.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.furina.classtools.ui.components.expressive.CtBottomSheet
import com.furina.classtools.ui.components.expressive.CtBottomSheetFooter
import com.furina.classtools.ui.components.expressive.CtBottomSheetHeader
import com.furina.classtools.ui.components.expressive.CtChipGroup
import com.furina.classtools.ui.components.expressive.CtChipOption
import com.furina.classtools.ui.theme.CtSpace

@Composable
internal fun RosterPickerSheet(
    members: Map<Int, String>,
    selected: Set<Int>,
    onConfirm: (Set<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by rememberSaveable { mutableStateOf(ArrayList(selected)) }
    CtBottomSheet(onDismissRequest = onDismiss) {
        CtBottomSheetHeader(
            title = "选择名单",
            subtitle = "已选 ${current.size} / ${members.size} 人",
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CtSpace.lg).verticalScroll(rememberScrollState()),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpace.xs)) {
                TextButton(onClick = { current = ArrayList(members.keys) }) { Text("全选") }
                TextButton(onClick = { current = arrayListOf() }) { Text("清空") }
            }
            CtChipGroup(
                options = members.map { (seat, label) -> CtChipOption(key = seat.toString(), label = label) },
                selectedKeys = current.mapTo(mutableSetOf()) { it.toString() },
                onToggle = { key ->
                    val seat = key.toIntOrNull() ?: return@CtChipGroup
                    current = ArrayList(if (seat in current) current - seat else current + seat)
                },
                multiSelect = true,
            )
            CtBottomSheetFooter(primaryText = "确定", onPrimary = { onConfirm(current.toSet()) })
        }
    }
}
