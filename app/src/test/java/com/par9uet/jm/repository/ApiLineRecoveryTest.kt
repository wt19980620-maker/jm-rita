package com.par9uet.jm.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ApiLineRecoveryTest {
    @Test
    fun concurrentFailuresShareOneProbeAndCooldownSuppressesRepeats() = runBlocking {
        withTimeout(3000L) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var probes = 0
            var now = 1000L
            val recovery = ApiLineRecovery(this, { "current" }, { now }) {
                probes++
                started.complete(Unit)
                release.await()
            }
            val calls = List(8) { async { recovery.recover("current") } }
            started.await()
            assertEquals(1, probes)
            release.complete(Unit)
            calls.forEach { it.await() }
            recovery.recover("current")
            assertEquals(1, probes)
            now += 30_001L
            recovery.recover("current")
            assertEquals(2, probes)
        }
    }

    @Test
    fun canceledWaiterDoesNotCancelSharedRecovery() = runBlocking {
        withTimeout(3000L) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var finished = false
            val recovery = ApiLineRecovery(this, { "current" }) {
                started.complete(Unit)
                release.await()
                finished = true
            }
            val first = async { recovery.recover("current") }
            started.await()
            first.cancelAndJoin()
            val second = async { recovery.recover("current") }
            release.complete(Unit)
            second.await()
            assertTrue(finished)
        }
    }

    @Test
    fun callCancellationStopsWaitingWithoutStoppingOtherRequests() = runBlocking {
        withTimeout(3000L) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var canceled = false
            var finished = false
            val recovery = ApiLineRecovery(this, { "current" }) {
                started.complete(Unit)
                release.await()
                finished = true
            }
            val first = async { recovery.recover("current") { canceled } }
            started.await()
            canceled = true
            first.await()
            assertFalse(finished)
            release.complete(Unit)
            recovery.recover("current")
            assertTrue(finished)
        }
    }

    @Test
    fun switchedOrCanceledRequestsDoNotStartProbing() = runBlocking {
        val recovery = ApiLineRecovery(this, { "selected" }) { fail("must not probe") }
        recovery.recover("old")
        recovery.recover("selected") { true }
    }

    @Test
    fun cooldownIsTrackedSeparatelyForEachFailedLine() = runBlocking {
        var current = "a"
        val probes = mutableListOf<String>()
        val recovery = ApiLineRecovery(this, { current }) { probes.add(it) }
        recovery.recover("a")
        current = "b"
        recovery.recover("b")
        current = "a"
        recovery.recover("a")
        assertEquals(listOf("a", "b"), probes)
    }

    @Test
    fun failuresOnNewSelectionDoNotJoinRecoveryForOldLine() = runBlocking {
        withTimeout(3000L) {
            var current = "a"
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val probes = mutableListOf<String>()
            val recovery = ApiLineRecovery(this, { current }) { api ->
                probes.add(api)
                if (api == "a") { started.complete(Unit); release.await() }
            }
            val first = async { recovery.recover("a") }
            started.await()
            current = "b"
            recovery.recover("b")
            assertEquals(listOf("a", "b"), probes)
            release.complete(Unit)
            first.await()
        }
    }

    @Test
    fun manualSelectionStopsWaitingForOldRecovery() = runBlocking<Unit> {
        withTimeout(3000L) {
            var current = "old"
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var finished = false
            val recovery = ApiLineRecovery(this, { current }) {
                started.complete(Unit)
                release.await()
                finished = true
            }
            val request = async { recovery.recover("old") }
            started.await()
            current = "selected"
            request.await()
            assertFalse(finished)
            release.complete(Unit)
        }
    }
}
