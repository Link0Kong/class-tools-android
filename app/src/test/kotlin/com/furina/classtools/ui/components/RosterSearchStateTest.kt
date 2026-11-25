package com.furina.classtools.ui.components

import com.furina.classtools.data.member.MemberCategory
import com.furina.classtools.data.member.MemberEntry
import com.furina.classtools.data.member.RosterMemberSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RosterSearchStateTest {
    @Test
    fun editingOnlyFiltersLocalMembersAndDelayedRepliesCannotRestoreOldMatches() = runTest {
        val requests = mutableListOf<String>()
        val delayed = CompletableDeferred<Set<Int>>()
        val source = object : RosterMemberSource {
            override suspend fun load(category: MemberCategory) = emptyList<MemberEntry>()
            override suspend fun searchSeats(query: String): Set<Int> {
                requests += query
                return if (query == "晚到") withContext(NonCancellable) { delayed.await() } else setOf(3)
            }
        }
        val state = RosterSearchState(source, this)
        state.update("晚")
        state.update("晚到")
        runCurrent()
        assertTrue(requests.isEmpty())
        state.submit()
        runCurrent()
        state.update("新输入")
        assertTrue(state.matchedSeats.isEmpty())
        state.submit()
        runCurrent()
        assertEquals(setOf(3), state.matchedSeats)
        delayed.complete(setOf(7))
        runCurrent()
        assertEquals(setOf(3), state.matchedSeats)
        state.update("")
        state.submit()
        runCurrent()
        assertEquals(listOf("晚到", "新输入"), requests)
        assertTrue(state.matchedSeats.isEmpty())
    }

    @Test
    fun searchUsesTheDisplayedRoleMembershipNameAndPaddedSeat() {
        val member = MemberEntry(3, "李同学", "学习委员", "班委")
        assertTrue(member.matches("03 李"))
        assertTrue(member.matches("学习 班委"))
        assertFalse(member.matches("03 体育"))
    }
}
