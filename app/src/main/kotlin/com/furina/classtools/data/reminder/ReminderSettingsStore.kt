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

/**
 * DataStore 实现（v4：模块化通知开关）。
 *
 * 兼容旧版本（v1–v3 只有 作业/课表/通知 三个总开关 + 提前天数）：
 * 新键缺失时从旧键派生，用户升级后原有偏好不丢：
 * - `homework_enabled=false` → 新作业与作业截止都关；
 * - `course_enabled=false` → 上课提醒关；
 * - `notice_enabled=false` → 新通知/活动截止/活动开抢都关；
 * - `homework_lead_days`（0/1/3）→ 统一提前分钟（0→10 分钟、1→1 天、3→3 天，仅接受合法档位）；
 * - `course_lead_minutes` → 上课提前（非法档位回退 10 分钟）。
 */
class DataStoreReminderSettingsStore(
    context: Context,
) : ReminderSettingsStore {

    private val store: DataStore<Preferences> = context.applicationContext.reminderSettingsDataStore

    override val settings: Flow<ReminderSettings> = store.data.map { prefs -> read(prefs) }

    override suspend fun current(): ReminderSettings = settings.first()

    override suspend fun update(transform: (ReminderSettings) -> ReminderSettings): ReminderSettings {
        val next = transform(current()).sanitized()
        store.edit { prefs ->
            prefs[KEY_ENABLED] = next.enabled
            prefs[KEY_HW_NEW] = next.homeworkNewEnabled
            prefs[KEY_HW_DUE] = next.homeworkDueEnabled
            prefs[KEY_CLASS] = next.classRemindEnabled
            prefs[KEY_NT_NEW] = next.noticeNewEnabled
            prefs[KEY_NT_DEADLINE] = next.noticeDeadlineEnabled
            prefs[KEY_NT_SIGNUP] = next.noticeSignupEnabled
            prefs[KEY_VOTE_NEW] = next.voteNewEnabled
            prefs[KEY_VOTE_RESULT] = next.voteResultEnabled
            prefs[KEY_WATER] = next.waterDeductEnabled
            prefs[KEY_DIGEST] = next.digestEnabled
            prefs[KEY_LEAD_MINUTES] = next.leadMinutes
            prefs[KEY_CLASS_LEAD_MINUTES] = next.classLeadMinutes
            prefs[KEY_BREAK_LEAD_MINUTES] = next.breakLeadMinutes
            prefs[KEY_DIGEST_HOUR] = next.homeworkDigestHour
            prefs[KEY_DIGEST_MINUTE] = next.homeworkDigestMinute
            prefs[KEY_QUIET_ENABLED] = next.quietHoursEnabled
            prefs[KEY_QUIET_START] = next.quietStartHour
            prefs[KEY_QUIET_END] = next.quietEndHour
            // 旧键同步写一份：回退到旧版本时行为仍与用户当前选择一致。
            prefs[KEY_LEGACY_HOMEWORK] = next.homeworkNewEnabled || next.homeworkDueEnabled
            prefs[KEY_LEGACY_COURSE] = next.classRemindEnabled
            prefs[KEY_LEGACY_NOTICE] = next.noticeNewEnabled || next.noticeDeadlineEnabled || next.noticeSignupEnabled
            prefs[KEY_LEGACY_HOMEWORK_DAYS] = (next.leadMinutes / 1440).coerceIn(0, 3)
            prefs[KEY_LEGACY_COURSE_MINUTES] = next.classLeadMinutes
        }
        return next
    }

    private fun read(prefs: Preferences): ReminderSettings {
        val legacyHomework = prefs[KEY_LEGACY_HOMEWORK]
        val legacyCourse = prefs[KEY_LEGACY_COURSE]
        val legacyNotice = prefs[KEY_LEGACY_NOTICE]
        val legacyDays = prefs[KEY_LEGACY_HOMEWORK_DAYS]
        val legacyCourseMinutes = prefs[KEY_LEGACY_COURSE_MINUTES]
        // 旧版「0/1/3 天」→ 新版统一提前档位（新版最长 1 天，3 天按 1 天处理）。
        val legacyLead = when (legacyDays) {
            0 -> 10
            1, 3 -> 1440
            else -> ReminderSettings.DEFAULT_LEAD_MINUTES
        }
        return ReminderSettings(
            enabled = prefs[KEY_ENABLED] ?: true,
            homeworkNewEnabled = prefs[KEY_HW_NEW] ?: (legacyHomework ?: true),
            homeworkDueEnabled = prefs[KEY_HW_DUE] ?: (legacyHomework ?: true),
            classRemindEnabled = prefs[KEY_CLASS] ?: (legacyCourse ?: true),
            noticeNewEnabled = prefs[KEY_NT_NEW] ?: (legacyNotice ?: true),
            noticeDeadlineEnabled = prefs[KEY_NT_DEADLINE] ?: (legacyNotice ?: true),
            noticeSignupEnabled = prefs[KEY_NT_SIGNUP] ?: (legacyNotice ?: true),
            voteNewEnabled = prefs[KEY_VOTE_NEW] ?: true,
            voteResultEnabled = prefs[KEY_VOTE_RESULT] ?: true,
            waterDeductEnabled = prefs[KEY_WATER] ?: true,
            digestEnabled = prefs[KEY_DIGEST] ?: true,
            leadMinutes = prefs[KEY_LEAD_MINUTES] ?: legacyLead,
            breakLeadMinutes = prefs[KEY_BREAK_LEAD_MINUTES] ?: ReminderSettings.DEFAULT_BREAK_LEAD_MINUTES,
            classLeadMinutes = prefs[KEY_CLASS_LEAD_MINUTES]
                ?: legacyCourseMinutes
                ?: ReminderSettings.DEFAULT_CLASS_LEAD_MINUTES,
            homeworkDigestHour = prefs[KEY_DIGEST_HOUR] ?: 20,
            homeworkDigestMinute = prefs[KEY_DIGEST_MINUTE] ?: 0,
            quietHoursEnabled = prefs[KEY_QUIET_ENABLED] ?: false,
            quietStartHour = prefs[KEY_QUIET_START] ?: 23,
            quietEndHour = prefs[KEY_QUIET_END] ?: 7,
        ).sanitized()
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("reminder_enabled")
        val KEY_HW_NEW = booleanPreferencesKey("hw_new_enabled")
        val KEY_HW_DUE = booleanPreferencesKey("hw_due_enabled")
        val KEY_CLASS = booleanPreferencesKey("class_remind_enabled")
        val KEY_NT_NEW = booleanPreferencesKey("nt_new_enabled")
        val KEY_NT_DEADLINE = booleanPreferencesKey("nt_deadline_enabled")
        val KEY_NT_SIGNUP = booleanPreferencesKey("nt_signup_enabled")
        val KEY_VOTE_NEW = booleanPreferencesKey("vote_new_enabled")
        val KEY_VOTE_RESULT = booleanPreferencesKey("vote_result_enabled")
        val KEY_WATER = booleanPreferencesKey("water_deduct_enabled")
        val KEY_DIGEST = booleanPreferencesKey("digest_enabled")
        val KEY_LEAD_MINUTES = intPreferencesKey("lead_minutes")
        val KEY_BREAK_LEAD_MINUTES = intPreferencesKey("break_lead_minutes")
        val KEY_CLASS_LEAD_MINUTES = intPreferencesKey("class_lead_minutes")
        val KEY_DIGEST_HOUR = intPreferencesKey("homework_digest_hour")
        val KEY_DIGEST_MINUTE = intPreferencesKey("homework_digest_minute")
        val KEY_QUIET_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val KEY_QUIET_START = intPreferencesKey("quiet_start_hour")
        val KEY_QUIET_END = intPreferencesKey("quiet_end_hour")

        // ---- 旧版键（v1–v3；只读派生 + 写回兼容） ----
        val KEY_LEGACY_HOMEWORK = booleanPreferencesKey("homework_enabled")
        val KEY_LEGACY_COURSE = booleanPreferencesKey("course_enabled")
        val KEY_LEGACY_NOTICE = booleanPreferencesKey("notice_enabled")
        val KEY_LEGACY_HOMEWORK_DAYS = intPreferencesKey("homework_lead_days")
        val KEY_LEGACY_COURSE_MINUTES = intPreferencesKey("course_lead_minutes")

        val Context.reminderSettingsDataStore by preferencesDataStore(name = "ct_reminder_settings")
    }
}
