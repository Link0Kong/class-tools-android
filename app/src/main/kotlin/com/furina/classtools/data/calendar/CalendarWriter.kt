package com.furina.classtools.data.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.furina.classtools.data.permission.PermissionController
import com.furina.classtools.data.permission.PermissionState
import com.furina.classtools.data.permission.PermissionType

/**
 * 系统日历写入器。
 *
 * - 有 READ+WRITE_CALENDAR：直接向 `CalendarContract.Events` 插入（标题/地点/描述/开始结束/时区）
 *   并写 `Reminders`（默认提前 [CalendarEvent.DEFAULT_REMINDER_MINUTES] 分钟）；
 * - 无权限：返回 [CalendarWriteResult.NeedsPermission]（由 Lead 走 `ACTION_INSERT` 系统兜底，
 *   见 [CalendarIntentFactory.insertIntent]）；
 * - 有权限但本机无写日历（未登录日历账号）：[CalendarWriteResult.NoCalendarAccount]，同样走系统兜底；
 * - 所有异常转成 [CalendarWriteResult.Failed] 可读文案，不抛给 UI。
 *
 * 注意：CalendarContract 必须在 IO 线程调用（本接口为 suspend，实现内部切 Dispatchers.IO）。
 */
interface CalendarWriter {
    suspend fun write(event: CalendarEvent): CalendarWriteResult
}

/** 可注入的日历 ContentResolver 访问层（便于替换/测试；真实实现见 [AndroidCalendarContentResolver]）。 */
interface CalendarAccountResolver {
    /** 第一个可写日历的 id；无则 null。 */
    suspend fun findWritableCalendarId(): Long?

    /** 插入事件，返回 Events 行 id；失败抛异常由调用方兜底。 */
    suspend fun insertEvent(calendarId: Long, event: CalendarEvent): Long

    /** 为事件添加提醒。 */
    suspend fun insertReminder(eventId: Long, minutes: Int)
}

class AndroidCalendarAccountResolver(
    private val context: Context,
) : CalendarAccountResolver {

    override suspend fun findWritableCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
        )
        val selection =
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.VISIBLE} = 1"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            args,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars._ID} ASC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }

    override suspend fun insertEvent(calendarId: Long, event: CalendarEvent): Long {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DESCRIPTION, event.description)
            put(CalendarContract.Events.EVENT_LOCATION, event.location)
            put(CalendarContract.Events.DTSTART, event.beginMillis)
            put(CalendarContract.Events.DTEND, event.endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, event.timeZone)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: error("日历写入失败（insert 返回 null）")
        return ContentUris.parseId(uri)
    }

    override suspend fun insertReminder(eventId: Long, minutes: Int) {
        val values = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, minutes)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
    }
}

class AndroidCalendarWriter(
    context: Context,
    private val permissionController: PermissionController,
    private val resolver: CalendarAccountResolver = AndroidCalendarAccountResolver(context),
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
) : CalendarWriter {

    private val appContext: Context = context.applicationContext

    override suspend fun write(event: CalendarEvent): CalendarWriteResult {
        CalendarWritePolicy.precheck(event, permissionController.state(PermissionType.CALENDAR))
            ?.let { return it }
        return kotlinx.coroutines.withContext(ioDispatcher) {
            runCatching {
                val calendarId = resolver.findWritableCalendarId()
                    ?: return@withContext CalendarWriteResult.NoCalendarAccount
                if (event.sourceKey.startsWith("reminder:")) {
                    val eventId = checkNotNull(ReminderCalendar.save(appContext, event, calendarId))
                    return@withContext CalendarWriteResult.Success(eventId, event.sourceKey)
                }
                val eventId = resolver.insertEvent(calendarId, event)
                if (event.reminderMinutes > 0) {
                    // 提醒失败不影响事件本身；下次写日历时会由用户重新生成
                    runCatching { resolver.insertReminder(eventId, event.reminderMinutes) }
                }
                CalendarWriteResult.Success(eventId = eventId, sourceKey = event.sourceKey)
            }.getOrElse { error ->
                CalendarWriteResult.Failed("写入系统日历失败：${error.message ?: "未知错误"}")
            }
        }
    }

}

/** `ACTION_INSERT` 系统兜底：无权限/无日历账号时拉起系统日历新建界面，由用户确认保存。 */
object CalendarIntentFactory {

    /**
     * 构造系统日历插入 Intent（Lead 在 `CalendarWriteResult.NeedsPermission/NoCalendarAccount`
     * 时 `startActivity` 即可；返回 null 表示系统无日历应用可处理）。
     */
    fun insertIntent(event: CalendarEvent): Intent? {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, event.title)
            putExtra(CalendarContract.Events.DESCRIPTION, event.description)
            putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.beginMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.allDay)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return intent
    }

    /** 供 UI 判断兜底是否可用（用 [android.content.pm.PackageManager.resolveActivity] 校验更佳）。 */
    fun fallbackHint(): String = "已打开系统日历，请确认保存该日程"
}

/** 兼容旧命名（Lead 接线时可直接用 [CalendarWritePolicy]）。 */
@Suppress("unused")
private val unusedDecision = CalendarWriteDecision.entries.toList()
