package com.furina.classtools.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** App 级本机外观设置（不进云端 userdata：主题是设备偏好，与账号无关）。 */
class AppSettingsStore(private val context: Context) {

    private val store: DataStore<Preferences> = context.applicationContext.settingsDataStore

    val dynamicColor: Flow<Boolean> = store.data.map { prefs ->
        prefs[KEY_DYNAMIC_COLOR] ?: false
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { prefs -> prefs[KEY_DYNAMIC_COLOR] = enabled }
    }

    private companion object {
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")

        val Context.settingsDataStore by preferencesDataStore(name = "ct_settings")
    }
}
