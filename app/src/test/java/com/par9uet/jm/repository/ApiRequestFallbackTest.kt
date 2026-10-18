package com.par9uet.jm.repository

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ApiRequestFallbackTest {
    @Test
    fun successfulRequestDoesNotWaitForProbe() {
        val result = requestWithApiFallback("GET", { "current" },
            { fail("healthy requests must not await probing") }, { false }) { "ok" }
        assertEquals("ok", result)
    }

    @Test
    fun failedGetRetriesOnceAfterBackgroundLineChange() {
        var current = "current"
        val calls = mutableListOf<String>()
        val result = requestWithApiFallback("GET", { current }, { current = "backup" }, { false }) {
            calls.add(it)
            if (it == "current") throw IOException("offline")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(listOf("current", "backup"), calls)
    }

    @Test
    fun postAndCanceledRequestsAreNeverReplayed() {
        for ((method, canceled) in listOf("POST" to false, "GET" to true)) {
            var calls = 0
            assertThrows(IOException::class.java) {
                requestWithApiFallback(method, { "current" },
                    { fail("must not wait") }, { canceled }) {
                    calls++
                    throw IOException("failed")
                }
            }
            assertEquals(1, calls)
        }
    }

    @Test
    fun unchangedLineDoesNotRetryAndFallbackFailureStops() {
        var calls = 0
        assertThrows(IOException::class.java) {
            requestWithApiFallback("GET", { "current" }, {}, { false }) {
                calls++
                throw IOException("offline")
            }
        }
        assertEquals(1, calls)
        var current = "current"
        calls = 0
        assertThrows(IOException::class.java) {
            requestWithApiFallback("GET", { current }, { current = "backup" }, { false }) {
                calls++
                throw IOException("offline")
            }
        }
        assertEquals(2, calls)
    }

    @Test
    fun alreadyChangedLineDoesNotTriggerAnotherProbe() {
        var current = "old"
        val calls = mutableListOf<String>()
        val result = requestWithApiFallback("GET", { current }, { fail("already switched") }, { false }) { api ->
            calls.add(api)
            if (api == "old") { current = "new"; throw IOException("offline") }
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(listOf("old", "new"), calls)
    }

    @Test
    fun cancellationDuringRecoveryDoesNotReplayTheRequest() {
        var canceled = false
        var current = "old"
        var calls = 0
        assertThrows(IOException::class.java) {
            requestWithApiFallback("GET", { current }, {
                assertEquals("old", it)
                current = "new"
                canceled = true
            }, { canceled }) { calls++; throw IOException("offline") }
        }
        assertEquals(1, calls)
    }

    @Test
    fun gatewayFailureClosesFirstResponseBeforeSingleRetry() {
        var current = "old"
        val calls = mutableListOf<String>()
        val closed = mutableListOf<Int>()
        val result = requestWithApiFallback("GET", { current }, { current = "new" }, { false },
            isRouteFailure = { status: Int -> status == 503 }, closeFailedResponse = { closed.add(it) }) { api ->
            if (api == "new") assertEquals(listOf(503), closed)
            calls.add(api)
            503
        }
        assertEquals(503, result)
        assertEquals(listOf("old", "new"), calls)
        assertEquals(listOf(503), closed)
    }

    @Test
    fun failedResponseRemainsReadableWhenNoBackupIsFound() {
        var probes = 0
        val result = requestWithApiFallback("GET", { "same" }, { probes++ }, { false },
            isRouteFailure = { _: Int -> true }, closeFailedResponse = { fail("response still needed") }) { 502 }
        assertEquals(502, result)
        assertEquals(1, probes)
    }

    @Test
    fun postAndOrdinaryHttpErrorsNeverTriggerRecovery() {
        for ((method, status) in listOf("POST" to 503, "GET" to 401, "GET" to 403, "GET" to 429)) {
            val result = requestWithApiFallback(method, { "same" }, { fail("must not probe") }, { false },
                isRouteFailure = { code: Int -> code in listOf(502, 503, 504) },
                closeFailedResponse = { fail("must not discard response") }) { status }
            assertEquals(status, result)
        }
    }

    @Test
    fun recoveryFailureClosesTheAbandonedHttpResponse() {
        var closed = false
        assertThrows(IOException::class.java) {
            requestWithApiFallback("GET", { "same" }, { throw IOException("probe failed") }, { false },
                isRouteFailure = { _: Int -> true }, closeFailedResponse = { closed = true }) { 504 }
        }
        assertTrue(closed)
    }
}
