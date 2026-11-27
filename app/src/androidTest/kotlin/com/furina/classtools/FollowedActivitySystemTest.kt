package com.furina.classtools

import android.app.NotificationManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.furina.classtools.data.notify.*
import com.furina.classtools.data.reminder.reminderOwner
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.session.SessionState
import com.furina.classtools.data.vote.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class FollowedActivitySystemTest {
    @Test fun concurrentWatchesKeepTheirObjectsAndSignupOpensOnlyOnce() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ClassToolsApp
        val session = Session("3260799999", "测试账号", token = "", debug = true)
        val previous = app.container.sessionStore.state.first { it != SessionState.Loading }
        assumeTrue(previous !is SessionState.SignedIn || previous.session.debug && previous.session.sid == session.sid)
        val owner = session.reminderOwner
        val manager = app.getSystemService(NotificationManager::class.java)
        val ids = listOf("qa-a", "qa-b")
        val now = System.currentTimeMillis()
        val signup = SignupCountdown(owner, "qa-signup", "测试报名", now + 8_000L)
        val polls = ids.map { PollDetail(poll = Poll(id = it, title = "测试投票 " + it, deadline = now + 180_000L)) }
        fun visible(tag: String) = manager.activeNotifications.any { it.tag == tag }
        fun await(check: () -> Boolean) {
            val until = SystemClock.elapsedRealtime() + 8_000
            while (!check() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100)
            assertTrue(check())
        }
        try {
            app.container.sessionStore.save(session)
            app.container.prepareSession(session)
            for (poll in polls) VoteWatches.follow(app, owner, poll)
            val first = VoteWatches.get(app, owner, ids.first())!!
            val second = VoteWatches.get(app, owner, ids.last())!!
            await { visible(first.tag) && visible(second.tag) }
            val uri = ActivityLinks.navigation(ActivityLinks.intent(app, owner, "vote", ids.last()))
            assertEquals("voteDetail/qa-b", uri?.route)
            assertTrue(uri!!.isFor(session))
            assertFalse(uri.isFor(session.copy(sid = "3260799998")))
            ActivityLinks.hide(app, owner, "vote", first.id, first.generation).send()
            await { !visible(first.tag) && visible(second.tag) }
            VoteWatches.restore(app, owner)
            assertTrue(VoteWatches.get(app, owner, first.id)!!.hidden)
            VoteWatches.remove(app, first)
            VoteWatches.follow(app, owner, polls.first())
            VoteWatches.hide(app, owner, first.id, first.generation)
            await { visible(first.tag) && visible(second.tag) }
            VoteWatches.refresh(app, second, polls.last().copy(closed = true))
            await { !visible(second.tag) }
            val watch = signup.copy(start = System.currentTimeMillis() + 5_000)
            assertTrue(SignupCountdowns.start(app, watch))
            val saved = SignupCountdowns.get(app, owner, watch.id)!!
            val timer = SignupCountdowns.timerIntent(app, owner, saved.id)
            assertEquals(saved.instance, timer.data!!.lastPathSegment)
            val tag = android.net.Uri.encode(owner) + "/" + android.net.Uri.encode(watch.id)
            await { manager.activeNotifications.any { it.tag == tag && it.id == 7420 } }
            SystemClock.sleep((watch.start - System.currentTimeMillis() + 100).coerceAtLeast(0))
            SignupCountdowns.onBoundary(app, owner, watch.id)
            assertTrue(SignupCountdowns.deliverOpening(app, owner, watch.id, watch.start))
            await { manager.activeNotifications.count { it.tag == tag } == 1 &&
                manager.activeNotifications.any { it.tag == tag && it.id == 7421 } }
            assertTrue(SignupCountdowns.get(app, owner, watch.id)!!.opened)
        } finally {
            SignupCountdowns.cancel(app, owner, signup.id)
            VoteWatches.all(app).filter { it.owner == owner && it.id in ids }.forEach { VoteWatches.remove(app, it) }
            if (previous is SessionState.SignedIn) app.container.sessionStore.save(previous.session)
            else app.container.sessionStore.clear()
        }
    }
}
