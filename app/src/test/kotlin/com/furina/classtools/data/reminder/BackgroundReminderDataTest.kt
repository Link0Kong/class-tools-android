package com.furina.classtools.data.reminder

import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpTransport
import com.furina.classtools.testApiClient
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundReminderDataTest {
    @Test
    fun coldScanReadsVisibleNoticeMetadataAndMarksWithStoredAccountToken() = runTest {
        val paths = CopyOnWriteArrayList<String>()
        val transport = HttpTransport { req ->
            assertEquals("session-for-9999", req.headers["X-Class-Token"])
            paths += req.url
            when {
                "notices?select=id,ts" in req.url -> ApiResponse(200, """[{"id":"manual1","ts":1,"hidden":false}]""")
                "class_notices_meta" in req.url -> ApiResponse(200, """[{"id":"manual1","title":"测试通知","body":"已发布","ts":1}]""")
                "seat=eq.3260799999" in req.url -> ApiResponse(200, "[]")
                else -> error("Unexpected feed")
            }
        }
        val result = loadReminderFeeds(testApiClient(transport, token = "session-for-9999"), "3260799999", setOf("nt"))
        assertEquals(setOf("nt"), result.available)
        assertEquals("测试通知", result.notices!!.manual.single().row.title)
        assertEquals(3, paths.size)
    }

    @Test
    fun aFailedMarksReadDoesNotEstablishABaselineOrAssumeItemsAreUnhandled() = runTest {
        val transport = HttpTransport { req ->
            if ("marks?" in req.url) ApiResponse(400, "{}") else ApiResponse(200, "[]")
        }
        val result = loadReminderFeeds(testApiClient(transport), "3260799999", setOf("nt", "hw"))
        assertTrue(result.available.isEmpty())
    }
}
