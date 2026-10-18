package com.par9uet.jm.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal class ApiLineRecovery(
    private val scope: CoroutineScope,
    private val currentApi: () -> String,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val refresh: suspend (String) -> Unit,
) {
    private val mutex = Mutex()
    private val lastAttempts = mutableMapOf<String, Long>()
    private val inFlight = mutableMapOf<String, Deferred<Unit>>()

    suspend fun recover(failedApi: String, isCanceled: () -> Boolean = { false }) {
        if (isCanceled() || currentApi() != failedApi) return
        val task = mutex.withLock {
            if (isCanceled() || currentApi() != failedApi) return
            inFlight[failedApi]?.takeIf { !it.isCompleted } ?: run {
                val now = nowMs()
                val previous = lastAttempts[failedApi]
                if (previous != null && now - previous < 30_000L) return
                lastAttempts[failedApi] = now
                scope.async {
                    withTimeoutOrNull(9000L) { refresh(failedApi) }
                    Unit
                }.also { inFlight[failedApi] = it }
            }
        }
        // 取消一个页面请求只停止其等待，不取消其他请求共用的恢复任务。
        withTimeoutOrNull(9000L) {
            while (!task.isCompleted) {
                if (isCanceled() || currentApi() != failedApi) return@withTimeoutOrNull
                delay(50L)
            }
            if (!isCanceled()) task.await()
        }
    }
}
