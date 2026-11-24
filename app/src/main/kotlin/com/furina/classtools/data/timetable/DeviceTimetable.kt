package com.furina.classtools.data.timetable

import android.content.Context
import com.furina.classtools.data.sync.FileUserDataLocalStore
import com.furina.classtools.data.sync.UserdataV6
import java.io.File

/** Background system surfaces may start before the foreground account repository is ready. */
internal suspend fun deviceTimetable(context: Context, sid: String): TtSnapshot? {
    val store = FileUserDataLocalStore(File(context.filesDir, "userdata"), { sid })
    val current = store.readCurrent()
    val tt = if (current.isNotEmpty()) current[TtLocal.SECTION_KEY]
        else store.readCache()?.let { UserdataV6.sectionsOf(it)[TtLocal.SECTION_KEY] }
    return TtLocal.snapshotOf(tt)?.takeIf { it.c.isNotEmpty() }
        ?: FileTimetableCache(File(context.cacheDir, "timetable")).read()?.snapshot
}
