package com.furina.classtools.ui.screens.home

import com.furina.classtools.data.sync.UserdataSectionUpdate
import com.furina.classtools.features.home.HomeToolsDraft
import com.furina.classtools.features.home.HomeToolsEditor
import java.io.IOException
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeToolsEditSessionTest {
    @Test
    fun noChangesLeaveImmediatelyAndDiscardedChangesNeverSave() = runTest {
        val saved = mutableListOf<HomeToolsDraft>()
        val editor = HomeToolsEditSession(this, { saved += it; UserdataSectionUpdate.Saved(false) }, notify = {})
        var leaves = 0
        val initial = listOf("tt", "nt")

        editor.begin(0, initial)
        editor.requestLeave { leaves++ }
        assertEquals(1, leaves)
        assertNull(editor.draft)

        editor.begin(1, initial)
        editor.toggle(1, "draw")
        editor.reorder(1, listOf("draw", "nt", "tt"))
        editor.requestLeave { leaves++ }
        assertEquals(1, leaves)
        assertTrue(editor.leaving)
        assertTrue(saved.isEmpty())
        editor.discard()
        advanceUntilIdle()
        assertEquals(2, leaves)
        assertTrue(saved.isEmpty())
        assertNull(editor.draft)
    }

    @Test
    fun editsShareOneDraftAndOnlyDonePublishesTheOrder() = runTest {
        val saved = mutableListOf<HomeToolsDraft>()
        val editor = HomeToolsEditSession(this, { saved += it; UserdataSectionUpdate.Saved(false) }, notify = {})

        editor.begin(0, listOf("tt", "nt", "ft", "bz"))
        editor.begin(1, listOf("draw"))
        assertFalse(editor.toggle(1, "draw"))
        assertFalse(editor.toggle(0, "draw"))
        assertTrue(editor.isEditing(0))
        assertFalse(editor.isEditing(1))
        editor.reorder(0, listOf("roster", "tt", "nt", "fit"))
        advanceUntilIdle()
        assertTrue(saved.isEmpty())

        editor.save()
        editor.save()
        advanceUntilIdle()
        assertEquals(listOf("roster", "tt", "nt", "fit"), saved.single().ids)
        assertNull(editor.draft)
    }

    @Test
    fun saveAndLeaveWaitsForLocalSuccessAndKeepsDraftOnFailure() = runTest {
        var failure = true
        var leaves = 0
        val feedback = mutableListOf<String>()
        val editor = HomeToolsEditSession(this, {
            if (failure) throw IOException("test write failure")
            UserdataSectionUpdate.Saved(syncPending = true)
        }, notify = { feedback += it })

        editor.begin(1, listOf("tt"))
        editor.toggle(1, "nt")
        editor.requestLeave { leaves++ }
        editor.save()
        assertTrue(editor.saving)
        advanceUntilIdle()
        assertEquals(0, leaves)
        assertTrue(editor.leaving)
        assertEquals(listOf("tt", "nt"), editor.draft?.ids)
        assertEquals("本机保存失败，请重试。", editor.error)
        assertTrue(feedback.isEmpty())

        failure = false
        editor.save()
        advanceUntilIdle()
        assertEquals(1, leaves)
        assertNull(editor.draft)
        assertEquals("常用工具已保存到本机，等待云同步", feedback.single())
    }

    @Test
    fun newSyncedConfigurationRequiresExplicitConflictResolution() = runTest {
        val checked = mutableListOf<HomeToolsDraft>()
        val newest = listOf("fit", "roster")
        val editor = HomeToolsEditSession(this, { draft ->
            checked += draft
            if (draft.baseline != newest) UserdataSectionUpdate.Conflict(HomeToolsEditor.section(newest))
            else UserdataSectionUpdate.Saved(false)
        }, notify = {})

        editor.begin(0, listOf("tt"))
        editor.toggle(0, "nt")
        editor.save()
        advanceUntilIdle()
        assertEquals(newest, editor.conflict)
        assertEquals(listOf("tt", "nt"), editor.draft?.ids)

        editor.overwrite()
        advanceUntilIdle()
        assertEquals(2, checked.size)
        assertEquals(newest, checked.last().baseline)
        assertEquals(listOf("tt", "nt"), checked.last().ids)
        assertNull(editor.draft)
    }
}
