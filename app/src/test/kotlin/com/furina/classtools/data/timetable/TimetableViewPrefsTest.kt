package com.furina.classtools.data.timetable

import android.app.Application
import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 课表周/日视图偏好（2026-11-27）：只存本机，脏值按未保存处理。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, instrumentedPackages = ["com.vivo.push.sdk"])
class TimetableViewPrefsTest {

    private lateinit var context: Context
    private lateinit var prefs: TimetableViewPrefs

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(TimetableViewPrefs.FILE_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        prefs = TimetableViewPrefs(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(TimetableViewPrefs.FILE_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun read_neverSaved_isNull() {
        assertNull(prefs.read())
    }

    @Test
    fun writeThenRead_roundTripsBothModes() {
        prefs.write(TimetableViewMode.DAY)
        assertEquals(TimetableViewMode.DAY, TimetableViewPrefs(context).read())

        prefs.write(TimetableViewMode.WEEK)
        assertEquals(TimetableViewMode.WEEK, TimetableViewPrefs(context).read())
    }

    @Test
    fun read_unknownStoredValue_isNull() {
        context.getSharedPreferences(TimetableViewPrefs.FILE_NAME, Context.MODE_PRIVATE)
            .edit().putString(TimetableViewPrefs.KEY, "WEEK").commit()
        assertNull(prefs.read())
    }

    @Test
    fun fromStorage_onlyAcceptsContractValues() {
        assertEquals(TimetableViewMode.WEEK, TimetableViewMode.fromStorage("week"))
        assertEquals(TimetableViewMode.DAY, TimetableViewMode.fromStorage("day"))
        assertNull(TimetableViewMode.fromStorage(""))
        assertNull(TimetableViewMode.fromStorage("day "))
        assertNull(TimetableViewMode.fromStorage(null))
        assertEquals("week", TimetableViewMode.WEEK.storageValue)
        assertEquals("day", TimetableViewMode.DAY.storageValue)
    }
}
