package com.par9uet.jm.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.EventListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import kotlin.reflect.KClass

class ApiLineProbeTest {
    @Test
    fun validatesStatusContentTypeAndTopLevelApiCode() = runBlocking {
        for ((status, type, body, expected) in listOf(
            Fixture(200, "application/json; charset=utf-8;", "{\"code\":200}", true),
            Fixture(503, "application/json", "{\"code\":200}", false),
            Fixture(200, "text/html", "{\"code\":200}", false),
            Fixture(200, "application/json", "{\"code\":2000}", false),
            Fixture(200, "application/json", "{\"nested\":{\"code\":200}}", false),
            Fixture(200, "application/json", "invalid", false),
            Fixture(200, "application/json", " ".repeat(256 * 1024 + 1), false),
        )) {
            val probe = ApiLineProbe { request ->
                FakeCall(request) { call, callback ->
                    callback.onResponse(call, response(request, status, body.toResponseBody(type.toMediaType())))
                }
            }
            assertEquals(expected, probe.check("https://api.example.test").isAvailable)
        }
    }

    @Test
    fun ioFailureIsUnavailableAndInvalidUrlNeverCreatesACall() = runBlocking {
        val probe = ApiLineProbe { request -> FakeCall(request) { call, callback ->
            callback.onFailure(call, IOException("offline"))
        } }
        assertFalse(probe.check("https://api.example.test").isAvailable)
        val invalid = ApiLineProbe { fail("invalid URL must not create a call"); error("unused") }
        assertFalse(invalid.check("not a url").isAvailable)
    }

    @Test
    fun probeOnlySendsProtocolHeadersNotAccountData() = runBlocking {
        val probe = ApiLineProbe { request ->
            assertEquals("/setting", request.url.encodedPath)
            assertEquals("GET", request.method)
            assertNull(request.header("Cookie"))
            assertNull(request.header("Authorization"))
            assertEquals(setOf("token", "tokenparam"), request.headers.names())
            FakeCall(request) { call, callback -> callback.onFailure(call, IOException("fixture")) }
        }
        probe.check("https://api.example.test")
        Unit
    }

    @Test
    fun cancellationCancelsHttpCallAndClosesLateResponse() = runBlocking {
        withTimeout(3000L) {
            val queued = CompletableDeferred<Unit>()
            lateinit var pending: Callback
            lateinit var call: FakeCall
            val probe = ApiLineProbe { request ->
                FakeCall(request) { _, callback -> pending = callback; queued.complete(Unit) }.also { call = it }
            }
            val job = async { probe.check("https://api.example.test") }
            queued.await()
            job.cancelAndJoin()
            assertTrue(call.isCanceled())
            var closed = false
            val body = object : ResponseBody() {
                override fun contentType() = "application/json".toMediaType()
                override fun contentLength() = 0L
                override fun source(): BufferedSource = object : okio.ForwardingSource(Buffer()) {
                    override fun close() { closed = true; super.close() }
                }.buffer()
            }
            pending.onResponse(call, response(call.request(), 200, body))
            assertTrue(closed)
        }
    }

    private data class Fixture(val status: Int, val type: String, val body: String, val expected: Boolean)

    private fun response(request: Request, status: Int, body: ResponseBody) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(status).message("fixture").body(body).build()

    private class FakeCall(
        private val request: Request,
        private val enqueue: (FakeCall, Callback) -> Unit,
    ) : Call {
        private var canceled = false
        private var executed = false
        private val listeners = mutableListOf<EventListener>()
        private val tags = mutableMapOf<Class<*>, Any>()
        override fun request() = request
        override fun execute(): Response = error("probe must be asynchronous")
        override fun enqueue(responseCallback: Callback) { executed = true; enqueue(this, responseCallback) }
        override fun cancel() { canceled = true }
        override fun isExecuted() = executed
        override fun isCanceled() = canceled
        override fun timeout() = Timeout()
        override fun addEventListener(eventListener: EventListener) { listeners.add(eventListener) }
        override fun <T : Any> tag(type: KClass<T>): T? = tag(type.java)
        override fun <T> tag(type: Class<out T>): T? = type.cast(tags[type])
        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = tag(type.java, computeIfAbsent)
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T =
            requireNotNull(type.cast(tags.getOrPut(type) { computeIfAbsent() }))
        override fun clone(): Call = FakeCall(request, enqueue)
    }
}
