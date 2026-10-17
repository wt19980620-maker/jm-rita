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
}
