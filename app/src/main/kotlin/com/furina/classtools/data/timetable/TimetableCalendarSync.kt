package com.furina.classtools.data.timetable

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 本机可写日历（`CalendarContract.Calendars` 查询结果；用于让用户选择写入目标）。 */
data class WritableCalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val isPrimary: Boolean,
)

/** 上一次写入记录（本机 SharedPreferences 持久化；用于幂等清理与 UI 展示）。 */
data class CalendarSyncRecord(
    val calendarId: Long,
    val calendarName: String,
    val eventIds: List<Long>,
    val termStart: String,
    val syncedAt: Long,
    val eventCount: Int,
)

/** 写入系统日历的结果（UI 据此给出明确提示，不抛异常）。 */
sealed interface CalendarSyncOutcome {
    /** 写入成功；[inserted] 本次插入数，[total] 计划数（超上限时 [trimmed]=true）。 */
    data class Success(
        val inserted: Int,
        val calendarName: String,
        val total: Int,
        val trimmed: Boolean,
    ) : CalendarSyncOutcome

    /** 缺少 READ/WRITE_CALENDAR（运行时权限未授予）。 */
    data object NeedsPermission : CalendarSyncOutcome

    /** 有权限但本机没有可写日历（未登录日历账号）。 */
    data object NoWritableCalendar : CalendarSyncOutcome

    /** 课表为空/开学日未知/本学期已无未上课程。 */
    data object NoEvents : CalendarSyncOutcome

    /** 系统日历 API 写失败（异常转为可读文案）。 */
    data class Failed(val message: String) : CalendarSyncOutcome
}

/**
 * 把课表**原生写入系统日历**（`CalendarContract`）并添加课前提醒。
 *
 * 这是「手机能原生调用和提醒课程」的核心实现：
 * 1. `READ_CALENDAR`/`WRITE_CALENDAR` 运行时权限（Manifest 已声明；由 UI 申请）；
 * 2. 列出**可写日历**（`CALENDAR_ACCESS_LEVEL >= CONTRIBUTOR` 且可见），让用户选择；
 * 3. 按「当前教学周 → 学期末」展开真实日期/节次时间（[TimetableLink.planSessions]），逐条插入
 *    `Events` + `Reminders`（默认提前 15 分钟；时区 `Asia/Shanghai`）；
 * 4. **幂等**：写入前先删除上次记录的事件 id（事件 id 存在本机 SharedPreferences），
 *    并按描述标记 [TimetableLink.CALENDAR_TAG] 兜底清理（id 丢失/重装后也不会重复堆积）；
 * 5. 写入失败/中途异常：回滚本次已插入事件，返回 [CalendarSyncOutcome.Failed]。
 *
 * 注意：所有 ContentResolver 调用都在 IO 线程执行；UI 侧负责权限申请与日历选择。
 */
class TimetableCalendarSync(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val appContext: Context = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // =========================================================================
    // 权限 / 设置
    // =========================================================================

    fun hasReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun hasCalendarAccess(): Boolean = hasReadPermission() && hasWritePermission()

    /** 系统设置里的本应用详情页（永久拒绝日历权限后引导用户手动开启）。 */
    fun appSettingsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", appContext.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // =========================================================================
    // 可写日历
    // =========================================================================

    /** 列出可写日历（主日历优先；无权限/查询异常返回空列表，由调用方提示授权）。 */
    suspend fun writableCalendars(): List<WritableCalendarInfo> = withContext(ioDispatcher) {
        if (!hasReadPermission()) return@withContext emptyList()
        runCatching { queryWritableCalendars() }.getOrDefault(emptyList())
    }

    private fun queryWritableCalendars(): List<WritableCalendarInfo> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        val selection =
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.VISIBLE} = 1"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        val sort = "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars._ID} ASC"
        val out = ArrayList<WritableCalendarInfo>()
        appContext.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            args,
            sort,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val primaryIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() } ?: "日历 $id"
                out.add(
                    WritableCalendarInfo(
                        id = id,
                        displayName = name,
                        accountName = cursor.getString(accountIndex).orEmpty(),
                        isPrimary = cursor.getInt(primaryIndex) == 1,
                    ),
                )
            }
        }
        return out
    }

    // =========================================================================
    // 同步
    // =========================================================================

    /**
     * 写入系统日历（从当前教学周写到学期末；已有本 App 写入的事件先清理）。
     *
     * @param calendarId 目标日历（必须来自 [writableCalendars]）
     * @param reminderMinutes 提前提醒分钟数（0=不提醒）
     */
    suspend fun sync(
        snapshot: TtSnapshot,
        calendarId: Long,
        reminderMinutes: Int = TimetableLink.DEFAULT_REMINDER_MINUTES,
        nowMillis: Long = System.currentTimeMillis(),
    ): CalendarSyncOutcome = withContext(ioDispatcher) {
        if (!hasCalendarAccess()) return@withContext CalendarSyncOutcome.NeedsPermission
        val calendars = runCatching { queryWritableCalendars() }.getOrDefault(emptyList())
        val target = calendars.firstOrNull { it.id == calendarId }
            ?: return@withContext CalendarSyncOutcome.NoWritableCalendar

        val maxWeek = TimetableLink.semesterWeeks(snapshot.c)
        val termStart = TtCalendar.parseTermStart(snapshot.termStart)
        val today = Instant.ofEpochMilli(nowMillis).atZone(TimetableLink.CHINA_ZONE).toLocalDate()
        val rawWeek = TtCalendar.weekOf(termStart, today)
        // 学期已结束（当前周 > 最大周）→ 没有可提醒的未来课程
        if (rawWeek != null && rawWeek > maxWeek) return@withContext CalendarSyncOutcome.NoEvents
        val currentWeek = (rawWeek ?: 1).coerceIn(1, maxWeek)
        val planned = TimetableLink.planSessions(
            snapshot = snapshot,
            fromWeek = currentWeek,
            toWeek = maxWeek,
            limit = TimetableLink.MAX_CALENDAR_EVENTS + 1,
        )
        if (planned.isEmpty()) return@withContext CalendarSyncOutcome.NoEvents
        val trimmed = planned.size > TimetableLink.MAX_CALENDAR_EVENTS
        val sessions = planned.take(TimetableLink.MAX_CALENDAR_EVENTS)

        val resolver = appContext.contentResolver
        // 幂等：先清理本 App 之前写入的事件（记录 id + 描述标记兜底）。
        runCatching { deleteTrackedEvents(resolver, calendars.map { it.id }) }

        val inserted = ArrayList<Long>(sessions.size)
        try {
            sessions.forEach { session ->
                val eventId = insertSession(resolver, target.id, session, reminderMinutes)
                inserted.add(eventId)
            }
        } catch (error: Exception) {
            runCatching { deleteEventsByIds(resolver, inserted) }
            return@withContext CalendarSyncOutcome.Failed(
                "写入系统日历失败：" + (error.message?.takeIf { it.isNotBlank() } ?: "未知错误"),
            )
        }

        saveRecord(
            CalendarSyncRecord(
                calendarId = target.id,
                calendarName = target.displayName,
                eventIds = inserted,
                termStart = snapshot.termStart,
                syncedAt = nowMillis,
                eventCount = inserted.size,
            ),
        )
        CalendarSyncOutcome.Success(
            inserted = inserted.size,
            calendarName = target.displayName,
            total = planned.size.coerceAtMost(TimetableLink.MAX_CALENDAR_EVENTS),
            trimmed = trimmed,
        )
    }

    /** 清除本 App 写入系统日历的全部事件（返回删除行数；无权限返回 0）。 */
    suspend fun clearAll(): Int = withContext(ioDispatcher) {
        if (!hasCalendarAccess()) return@withContext 0
        val resolver = appContext.contentResolver
        val calendars = runCatching { queryWritableCalendars() }.getOrDefault(emptyList())
        val deleted = runCatching { deleteTrackedEvents(resolver, calendars.map { it.id }) }.getOrDefault(0)
        clearRecordPrefs()
        deleted
    }

    /** 上次写入记录；无记录返回 null。 */
    fun lastRecord(): CalendarSyncRecord? {
        val ids = prefs.getString(KEY_EVENT_IDS, null)?.takeIf { it.isNotBlank() }
            ?.split(',')?.mapNotNull { it.toLongOrNull() } ?: return null
        if (ids.isEmpty()) return null
        return CalendarSyncRecord(
            calendarId = prefs.getLong(KEY_CALENDAR_ID, -1L),
            calendarName = prefs.getString(KEY_CALENDAR_NAME, "").orEmpty(),
            eventIds = ids,
            termStart = prefs.getString(KEY_TERM_START, "").orEmpty(),
            syncedAt = prefs.getLong(KEY_SYNCED_AT, 0L),
            eventCount = prefs.getInt(KEY_EVENT_COUNT, ids.size),
        )
    }

    fun hasTrackedEvents(): Boolean = lastRecord() != null

    // =========================================================================
    // ContentResolver 细节
    // =========================================================================

    private fun insertSession(
        resolver: android.content.ContentResolver,
        calendarId: Long,
        session: TimetableLink.PlannedSession,
        reminderMinutes: Int,
    ): Long {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, session.courseName)
            put(CalendarContract.Events.DESCRIPTION, TimetableLink.calendarDescription(session))
            if (session.room.isNotBlank()) put(CalendarContract.Events.EVENT_LOCATION, session.room)
            put(CalendarContract.Events.DTSTART, session.startMillis)
            put(CalendarContract.Events.DTEND, session.endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimetableLink.CHINA_ZONE.id)
            put(CalendarContract.Events.HAS_ALARM, if (reminderMinutes > 0) 1 else 0)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
        }
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: error("日历写入失败（insert 返回 null）")
        val eventId = ContentUris.parseId(uri)
        if (reminderMinutes > 0) {
            val reminder = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, reminderMinutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            // 提醒失败不影响事件本身（下次同步会重建）
            runCatching { resolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder) }
        }
        return eventId
    }

    /** 删除上次记录的事件 id + 所有可写日历里带本 App 标记的事件。 */
    private fun deleteTrackedEvents(
        resolver: android.content.ContentResolver,
        writableCalendarIds: List<Long>,
    ): Int {
        var deleted = 0
        val record = lastRecord()
        if (record != null) {
            deleted += deleteEventsByIds(resolver, record.eventIds)
            clearRecordPrefs()
        }
        // 兜底：事件 id 记录丢失（清数据/重装）时按描述标记清理，避免重复日程堆积。
        val marker = "%" + TimetableLink.CALENDAR_TAG + "%"
        val selection = "${CalendarContract.Events.DESCRIPTION} LIKE ?"
        writableCalendarIds.forEach { calendarId ->
            deleted += runCatching {
                resolver.delete(
                    CalendarContract.Events.CONTENT_URI,
                    "$selection AND ${CalendarContract.Events.CALENDAR_ID} = ?",
                    arrayOf(marker, calendarId.toString()),
                )
            }.getOrDefault(0)
        }
        return deleted
    }

    private fun deleteEventsByIds(
        resolver: android.content.ContentResolver,
        eventIds: List<Long>,
    ): Int {
        var deleted = 0
        eventIds.forEach { eventId ->
            deleted += runCatching {
                resolver.delete(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                    null,
                    null,
                )
            }.getOrDefault(0)
        }
        return deleted
    }

    private fun saveRecord(record: CalendarSyncRecord) {
        prefs.edit()
            .putLong(KEY_CALENDAR_ID, record.calendarId)
            .putString(KEY_CALENDAR_NAME, record.calendarName)
            .putString(KEY_EVENT_IDS, record.eventIds.joinToString(","))
            .putString(KEY_TERM_START, record.termStart)
            .putLong(KEY_SYNCED_AT, record.syncedAt)
            .putInt(KEY_EVENT_COUNT, record.eventCount)
            .apply()
    }

    private fun clearRecordPrefs() {
        prefs.edit()
            .remove(KEY_CALENDAR_ID)
            .remove(KEY_CALENDAR_NAME)
            .remove(KEY_EVENT_IDS)
            .remove(KEY_TERM_START)
            .remove(KEY_SYNCED_AT)
            .remove(KEY_EVENT_COUNT)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "ct_tt_calendar_sync"
        const val KEY_CALENDAR_ID = "calendar_id"
        const val KEY_CALENDAR_NAME = "calendar_name"
        const val KEY_EVENT_IDS = "event_ids"
        const val KEY_TERM_START = "term_start"
        const val KEY_SYNCED_AT = "synced_at"
        const val KEY_EVENT_COUNT = "event_count"
    }
}
