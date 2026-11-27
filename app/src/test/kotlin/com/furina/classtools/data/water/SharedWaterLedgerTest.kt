package com.furina.classtools.data.water

import com.furina.classtools.FakeTransport
import com.furina.classtools.testApiClient
import com.furina.classtools.data.network.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SharedWaterLedgerTest {
    private class Cloud {
        var reading: SharedWaterValue? = SharedWaterValue(3, 5.4)
        var persons: Int? = 3
        var offline = false
        var allowed = true
        var badPeople = false
        var lost = false
        var writes = 0
        val transport = FakeTransport { req ->
            val path = req.url.substringAfter("/rest/")
            if (offline) throw java.io.IOException()
            val body = when {
                path == "rpc/auth_ok" -> allowed.toString()
                req.method == HttpMethod.GET -> {
                    if (!allowed || "offset=0" !in path) "[]"
                    else if (path.startsWith("water_room")) if (persons == null) "[]" else
                        """[{"roomverify":"QA","persons":${if (badPeople) 9 else persons}}]"""
                    else reading?.let { """[{"roomverify":"QA","month":"2030-01","persons":${it.persons},"reading":"${it.reading}"}]""" } ?: "[]"
                }
                else -> {
                    writes++
                    if (req.method == HttpMethod.DELETE) reading = null
                    else {
                        val row = NetJson.parseToJsonElement(req.body!!).jsonObject
                        if (path.startsWith("water_room")) persons = row.getValue("persons").jsonPrimitive.int
                        else reading = SharedWaterValue(row.getValue("persons").jsonPrimitive.int, row.getValue("reading").jsonPrimitive.double)
                    }
                    if (lost) throw java.io.IOException()
                    ""
                }
            }
            ApiResponse(200, body)
        }
        fun device(): () -> SharedWaterLedger {
            var cache: String? = null
            return { SharedWaterLedger("QA", testApiClient(transport), { true }, { cache }, { cache = it }) }
        }
    }

    @Test fun twoAccountsSeeEditsDeletionAndIndependentPeopleWithoutWritesOnRead() = runTest {
        val cloud = Cloud()
        val a = cloud.device()()
        val b = cloud.device()()
        a.refresh(); b.refresh()
        assertEquals(3, b.state.value.room().persons)
        assertEquals(0, cloud.writes)
        a.edit("2030-01", null)
        b.refresh()
        assertTrue(b.state.value.room().records.isEmpty())
        assertEquals(1, cloud.writes)
        b.edit("2030-01", SharedWaterValue(3, 7.0))
        b.edit("persons", SharedWaterValue(6))
        a.refresh()
        assertEquals(6, a.state.value.room().persons)
        assertEquals(7.0, a.state.value.room().latest!!.reading, 0.0)
        cloud.badPeople = true
        cloud.reading = null
        a.refresh()
        assertNull(a.state.value.room().latest)
        assertEquals(6, a.state.value.room().persons)
        assertTrue("部分" in a.status.value)
        cloud.allowed = false
        a.refresh()
        assertTrue(a.state.value.denied)
        assertEquals(3, cloud.writes)
    }

    @Test fun restartWithLostAckAndConcurrentDeleteRequiresExplicitReconfirmation() = runTest {
        val cloud = Cloud()
        val launch = cloud.device()
        val a = launch()
        a.refresh()
        cloud.lost = true
        val target = SharedWaterValue(3, 12.0)
        a.edit("2030-01", target)
        assertEquals(1, cloud.writes)
        assertTrue(a.state.value.pending.getValue("2030-01").attempted)
        cloud.lost = false
        cloud.reading = null
        val b = launch()
        b.refresh()
        assertEquals(1, cloud.writes)
        assertTrue(b.state.value.pending.containsKey("2030-01"))
        b.edit("2030-01", target)
        assertEquals(2, cloud.writes)
        assertTrue(b.state.value.pending.isEmpty())
    }

    @Test fun offlineNewIntentReplaysOnlyWithKnownBaseAndLostAckCanConvergeReadOnly() = runTest {
        val cloud = Cloud()
        cloud.reading = null
        cloud.persons = null
        val a = cloud.device()()
        a.refresh()
        assertEquals(4, a.state.value.room().persons)
        cloud.offline = true
        a.edit("2030-01", SharedWaterValue(4, 0.0))
        cloud.offline = false
        a.refresh()
        assertEquals(1, cloud.writes)
        cloud.lost = true
        a.edit("persons", SharedWaterValue(4))
        cloud.lost = false
        a.refresh()
        assertTrue(a.state.value.pending.isEmpty())
        assertEquals(2, cloud.writes)
    }

    @Test fun lateRoomOrBackgroundCallbackCannotReplaceSnapshot() = runTest {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        var stored = 0
        val api = testApiClient(FakeTransport { req ->
            started.complete(Unit); gate.await()
            ApiResponse(200, if (req.url.endsWith("auth_ok")) "true" else "[]")
        })
        val ledger = SharedWaterLedger("QA", api, { true }, { null }, { stored++ })
        val old = async { ledger.refresh() }
        started.await()
        ledger.leave()
        gate.complete(Unit)
        old.await()
        assertEquals(0, stored)
        ledger.enter()
        ledger.refresh()
        assertTrue(ledger.state.value.peopleKnown)
    }
}
