package com.furina.classtools.data.network

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpTransportCancellationTest {
    @Test
    fun cancellingTheRequestCancelsTheUnderlyingCallWhileItIsStillBlocked() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val wasCancelled = AtomicBoolean()
        val client = OkHttpClient.Builder()
            .eventListener(object : EventListener() {
                override fun canceled(call: Call) {
                    wasCancelled.set(true)
                    release.countDown()
                }
            })
            .addInterceptor {
                started.complete(Unit)
                check(release.await(5, TimeUnit.SECONDS))
                throw IOException("test call released")
            }
            .build()
        val transport = OkHttpTransport(client)
        val request = launch { transport.execute(ApiRequest(HttpMethod.GET, "http://localhost/")) }
        try {
            withTimeout(2_000) { started.await() }
            withTimeout(2_000) { request.cancelAndJoin() }
            assertTrue(wasCancelled.get())
        } finally {
            release.countDown()
        }
    }
}
