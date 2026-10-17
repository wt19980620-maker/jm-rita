package com.par9uet.jm.store

import com.par9uet.jm.task.AppInitTask
import com.par9uet.jm.task.AppTaskInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections

class InitManagerTest {
    @Test
    fun localStateIsReadyBeforeNetworkRefreshFinishes() {
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            val refreshing = CompletableDeferred<Unit>()
            val events = Collections.synchronizedList(mutableListOf<String>())
            val tasks = listOf(
                task(2, { events.add("settings") }, { refreshing.complete(Unit); gate.await() }),
                task(1, { events.add("cookies") }, {}),
            )
            val manager = InitManager()
            val job = manager.start(tasks, this)
            try {
                withTimeout(5000) { refreshing.await() }
                assertTrue(manager.deferred.isCompleted)
                assertEquals(listOf("cookies", "settings"), events.toList())
                assertFalse(job.isCompleted)
                assertSame(job, manager.start(tasks, this))
            } finally {
                gate.complete(Unit)
                job.join()
            }
            assertEquals(2, events.size)
        }
    }

    @Test
    fun noNetworkTaskRunsUntilAllLocalTasksFinish() {
        runBlocking {
            val localStarted = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            var refreshed = false
            val manager = InitManager()
            val job = manager.start(listOf(task(1, {
                localStarted.complete(Unit)
                gate.await()
            }, { refreshed = true })), this)
            try {
                withTimeout(5000) { localStarted.await() }
                assertFalse(manager.deferred.isCompleted)
                assertFalse(refreshed)
            } finally {
                gate.complete(Unit)
                job.join()
            }
            assertTrue(refreshed)
        }
    }

    @Test
    fun configurationAndLoginWaitForLineSelectionThenRefreshIndependently() {
        runBlocking {
            val lineStarted = CompletableDeferred<Unit>()
            val lineReady = CompletableDeferred<Unit>()
            val configStarted = CompletableDeferred<Unit>()
            val configReady = CompletableDeferred<Unit>()
            val loginStarted = CompletableDeferred<Unit>()
            val line = object : AppInitTask {
                override suspend fun init() {}
                override suspend fun refresh() { lineStarted.complete(Unit); lineReady.await() }
                override fun getAppTaskInfo() = AppTaskInfo("line", 1, refreshStage = 0)
            }
            val manager = InitManager()
            val job = manager.start(listOf(line,
                task(2, {}, { configStarted.complete(Unit); configReady.await() }),
                task(3, {}, { loginStarted.complete(Unit) }),
            ), this)
            try {
                withTimeout(5000) { lineStarted.await() }
                assertTrue(manager.deferred.isCompleted)
                assertFalse(configStarted.isCompleted)
                assertFalse(loginStarted.isCompleted)
                lineReady.complete(Unit)
                withTimeout(5000) { configStarted.await(); loginStarted.await() }
                assertFalse(job.isCompleted)
            } finally {
                lineReady.complete(Unit)
                configReady.complete(Unit)
                job.join()
            }
        }
    }

    private fun task(sort: Int, local: suspend () -> Unit, network: suspend () -> Unit) =
        object : AppInitTask {
            override suspend fun init() = local()
            override suspend fun refresh() = network()
            override fun getAppTaskInfo() = AppTaskInfo("test", sort)
        }
}
