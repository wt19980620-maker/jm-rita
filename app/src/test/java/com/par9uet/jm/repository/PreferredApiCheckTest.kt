package com.par9uet.jm.repository

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections

class PreferredApiCheckTest {
    @Test
    fun healthyPreferredLineSkipsAllOtherProbes() {
        runBlocking {
            val calls = mutableListOf<String>()
            val result = checkPreferredApi("current", listOf("current", "slow")) {
                calls.add(it)
                ApiLineCheck(it, true, 1)
            }
            assertEquals(listOf("current"), calls)
            assertEquals("current", ApiLineSelector.choose("current", result))
        }
    }

    @Test
    fun failedPreferredLineChecksUniqueAlternatives() {
        runBlocking {
            val calls = Collections.synchronizedList(mutableListOf<String>())
            val result = checkPreferredApi("current", listOf("current", "backup", "backup")) {
                calls.add(it)
                ApiLineCheck(it, it == "backup", 10)
            }
            assertEquals(listOf("current", "backup"), calls.toList())
            assertEquals("backup", ApiLineSelector.choose("current", result))
        }
    }

    @Test
    fun availableBackupReturnsWithoutWaitingForStalledProbe() = runBlocking {
        val stalled = CompletableDeferred<Unit>()
        var canceled = false
        val result = withTimeout(2000L) {
            checkPreferredApi("current", listOf("current", "stalled", "backup")) { api ->
                when (api) {
                    "current" -> ApiLineCheck(api, false)
                    "stalled" -> try {
                        stalled.complete(Unit)
                        awaitCancellation()
                    } finally {
                        canceled = true
                    }
                    else -> { stalled.await(); ApiLineCheck(api, true, 10) }
                }
            }
        }
        assertEquals("backup", ApiLineSelector.choose("current", result))
        assertTrue(canceled)
    }

    @Test
    fun allUnavailableLinesFinishWithoutSelectingOne() = runBlocking {
        val result = withTimeout(2000L) {
            checkPreferredApi("current", listOf("current", "a", "b", "a")) { ApiLineCheck(it, false) }
        }
        assertEquals(setOf("current", "a", "b"), result.map { it.api }.toSet())
        assertNull(ApiLineSelector.choose("current", result))
        assertTrue(checkAlternativeApis("current", listOf("current")) { fail("must not re-probe"); error("unused") }.isEmpty())
    }
}
