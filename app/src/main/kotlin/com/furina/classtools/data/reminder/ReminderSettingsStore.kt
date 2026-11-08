package com.furina.classtools.data.reminder

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 提醒设置（DataStore 持久化，设备级；不进云端 userdata）。 */
interface ReminderSettingsStore {
    val settings: Flow<ReminderSettings>

    suspend fun current(): ReminderSettings

    suspend fun update(transform: (ReminderSettings) -> ReminderSettings): ReminderSettings
}

class DataStoreReminderSettingsStore(
    context: Context,
) : ReminderSettingsStore {

    private val store: DataStore<Preferences> = context.applicationContext.reminderSettingsDataStore

    override val settings: Flow<ReminderSettings> = store.data.map { prefs ->
        ReminderSettings(
            enabled = prefs[KEY_ENABLED] ?: true,
            homeworkEnabled = prefs[KEY_HOMEWORK] ?: true,
            courseEnabled = prefs[KEY_COURSE] ?: true,
            noticeEnabled = prefs[KEY_NOTICE] ?: true,
            homeworkDigestHour = prefs[KEY_DIGEST_HOUR] ?: 20,
            homeworkDigestMinute = prefs[KEY_DIGEST_MINUTE] ?: 0,
            homeworkLeadDays = prefs[KEY_HOMEWORK_LEAD_DAYS] ?: 1,
            courseLeadMinutes = prefs[KEY_COURSE_LEAD_MINUTES] ?: 15,
            quietHoursEnabled = prefs[KEY_QUIET_ENABLED] ?: false,
            quietStartHour = prefs[KEY_QUIET_START] ?: 23,
            quietEndHour = prefs[KEY_QUIET_END] ?: 7,
        ).sanitized()
    }

    override suspend fun current(): ReminderSettings = settings.first()

    override suspend fun update(transform: (ReminderSettings) -> ReminderSettings): ReminderSettings {
        val next = transform(current()).sanitized()
        store.edit { prefs ->
            prefs[KEY_ENABLED] = next.enabled
            prefs[KEY_HOMEWORK] = next.homeworkEnabled
            prefs[KEY_COURSE] = next.courseEnabled
            prefs[KEY_NOTICE] = next.noticeEnabled
            prefs[KEY_DIGEST_HOUR] = next.homeworkDigestHour
            prefs[KEY_DIGEST_MINUTE] = next.homeworkDigestMinute
            prefs[KEY_HOMEWORK_LEAD_DAYS] = next.homeworkLeadDays
            prefs[KEY_COURSE_LEAD_MINUTES] = next.courseLeadMinutes
            prefs[KEY_QUIET_ENABLED] = next.quietHoursEnabled
            prefs[KEY_QUIET_START] = next.quietStartHour
            prefs[KEY_QUIET_END] = next.quietEndHour
        }
        return next
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("reminder_enabled")
        val KEY_HOMEWORK = booleanPreferencesKey("homework_enabled")
        val KEY_COURSE = booleanPreferencesKey("course_enabled")
        val KEY_NOTICE = booleanPreferencesKey("notice_enabled")
        val KEY_DIGEST_HOUR = intPreferencesKey("homework_digest_hour")
        val KEY_DIGEST_MINUTE = intPreferencesKey("homework_digest_minute")
        val KEY_HOMEWORK_LEAD_DAYS = intPreferencesKey("homework_lead_days")
        val KEY_COURSE_LEAD_MINUTES = intPreferencesKey("course_lead_minutes")
        val KEY_QUIET_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val KEY_QUIET_START = intPreferencesKey("quiet_start_hour")
        val KEY_QUIET_END = intPreferencesKey("quiet_end_hour")

        val Context.reminderSettingsDataStore by preferencesDataStore(name = "ct_reminder_settings")
    }
}
