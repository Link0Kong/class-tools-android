package com.furina.classtools.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

@Composable
internal fun rememberListTabs(sid: String?) = if (sid.isNullOrBlank() || LocalInspectionMode.current) null else {
    val context = LocalContext.current
    remember(sid, context) { context.getSharedPreferences("list_tabs_$sid", Context.MODE_PRIVATE) }
}
